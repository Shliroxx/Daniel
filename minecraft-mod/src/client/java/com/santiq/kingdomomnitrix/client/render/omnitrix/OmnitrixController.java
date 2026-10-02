package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixClientState;
import com.santiq.kingdomomnitrix.alien.AlienDefinition;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.AlienUniforms;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback;
import com.santiq.kingdomomnitrix.client.screen.OmnitrixScreen;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;
import com.santiq.kingdomomnitrix.networking.OmnitrixPhasePayload;
import com.santiq.kingdomomnitrix.networking.RevertRequestPayload;
import com.santiq.kingdomomnitrix.networking.SetUniformPayload;
import com.santiq.kingdomomnitrix.networking.TransformRequestPayload;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Omnitrix-Zustandsmaschine des eigenen Spielers (siehe {@link OmnitrixPhase}). Haelt das Alien-Roster fuer das
 * Hologramm-Rad, die Rad-Drehung und alle weich nachgefuehrten Darstellungswerte (Arm, Kern, Energie, Rad).
 *
 * <p>Das Roster entsteht aus den registrierten Alien-Definitionen ({@code data/.../alien/*.json}); freigeschaltet ist,
 * was der Spieler an DNA besitzt. Neue Aliens erscheinen damit ohne Code-Aenderung im Rad.</p>
 */
public final class OmnitrixController {
	/** Eintrag im Rad */
	public record Entry(Identifier id, AlienDefinition alien, boolean unlocked) {
	}

	// Phasendauern in Sekunden
	private static final float ACTIVATING_TIME = 0.22f;
	private static final float OPENING_TIME = 0.34f;
	/** Energieaufbau nach dem Bestaetigen — aus dem Geraete-Profil (Master Control: fast sofort) */
	private static float confirmTime = 0.42f;
	private static final float IMPACT_TIME = 0.28f;
	private static final float TRANSFORMATION_TIMEOUT = 1.5f;
	private static final float REVERT_TIME = 0.6f;
	/** Schritte, ab denen die Drehung als „steht“ gilt */
	private static final float SETTLED = 0.02f;

	private static OmnitrixPhase phase = OmnitrixPhase.IDLE;
	private static OmnitrixPhase sentPhase = OmnitrixPhase.IDLE;
	private static long phaseStart = System.nanoTime();
	private static final List<Entry> ENTRIES = new ArrayList<>();
	/** Ziel-Index (unbegrenzt, Modulo beim Lesen) und angezeigte Rad-Stellung (weich) */
	private static int target;
	private static float display;
	private static float velocity;
	private static float raise;
	private static float lift;
	private static float wheel;
	private static float energy;
	/** Ablehnungs-Wackeln (gesperrtes Alien, Abklingzeit) */
	private static long denyStart = Long.MIN_VALUE;
	private static long lastFrame;
	private static Identifier pending;
	private static boolean wasTransformed;

	private OmnitrixController() {
	}

	public static OmnitrixPhase phase() {
		return phase;
	}

	public static List<Entry> entries() {
		return ENTRIES;
	}

	public static float raise() {
		return raise;
	}

	public static float lift() {
		return lift;
	}

	public static float wheel() {
		return wheel;
	}

	public static float energy() {
		return energy;
	}

	/** Angezeigte Rad-Stellung in Eintraegen (0 = erster Eintrag vorn). */
	public static float display() {
		return display;
	}

	/** Rest-Drehung der Rad-Feder in Eintraegen (0 = steht); treibt den Dreh des Kerns beim Weiterschalten. */
	public static float twist() {
		return target - display;
	}

	public static int focusedIndex() {
		return ENTRIES.isEmpty() ? -1 : Math.floorMod(target, ENTRIES.size());
	}

	public static Optional<Entry> focused() {
		int index = focusedIndex();
		return index < 0 ? Optional.empty() : Optional.of(ENTRIES.get(index));
	}

	/** Sekunden seit Beginn der aktuellen Phase */
	public static float phaseTime() {
		return (System.nanoTime() - phaseStart) / 1.0e9f;
	}

	/** Ablehnungs-Ausschlag 0..1 (klingt ab) */
	public static float deny() {
		if (denyStart == Long.MIN_VALUE) {
			return 0.0f;
		}
		float t = (System.nanoTime() - denyStart) / 1.0e9f;
		return t > 0.4f ? 0.0f : MathHelper.sin(t * 50.0f) * (1.0f - t / 0.4f);
	}

	public static boolean isInteractive() {
		return phase.showsWheel() && phase != OmnitrixPhase.CONFIRMING;
	}

	/** Omnitrix-Taste / Rechtsklick mit dem Item: Arm heben, Geraet oeffnen, Rad zeigen. */
	public static void open(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || !OmnitrixItem.hasOmnitrix(player)) {
			return;
		}
		if (phase.isArmRaised()) {
			return;
		}
		ENTRIES.clear();
		HeroData data = HeroDataAccess.get(player);
		var manager = client.world.getRegistryManager();
		for (Identifier id : AlienRegistry.sortedIds(manager)) {
			AlienRegistry.get(manager, id).ifPresent(alien -> ENTRIES.add(new Entry(id, alien, data.hasAlien(id))));
		}
		TransformationState state = TransformationManager.get(player);
		Identifier start = state.activeAlien().or(state::selectedAlien).orElse(null);
		target = 0;
		for (int i = 0; i < ENTRIES.size(); i++) {
			if (ENTRIES.get(i).id().equals(start)) {
				target = i;
			}
		}
		display = target;
		velocity = 0.0f;
		setPhase(OmnitrixPhase.ACTIVATING);
		OmnitrixFeedback.play(OmnitrixCue.ACTIVATE);
		client.setScreen(new OmnitrixScreen());
	}

	/** Rad schliessen ohne Auswahl (Esc, Rechtsklick). */
	public static void cancel() {
		if (phase.isArmRaised() && phase != OmnitrixPhase.IMPACT && phase != OmnitrixPhase.CONFIRMING) {
			OmnitrixFeedback.play(OmnitrixCue.CANCEL);
			setPhase(basePhase());
		}
	}

	/** Rad um {@code steps} Eintraege drehen. */
	public static void rotate(int steps) {
		if (!isInteractive() || ENTRIES.size() < 2 || steps == 0) {
			return;
		}
		target += steps;
		setPhase(OmnitrixPhase.ROTATING);
		OmnitrixFeedback.play(OmnitrixCue.NAVIGATE);
	}

	/** Direkt zu einem Eintrag drehen (Zifferntasten); dreht den kuerzeren Weg. */
	public static void rotateTo(int index) {
		if (!isInteractive() || index < 0 || index >= ENTRIES.size()) {
			return;
		}
		int size = ENTRIES.size();
		int delta = Math.floorMod(index - focusedIndex(), size);
		if (delta > size / 2) {
			delta -= size;
		}
		rotate(delta);
	}

	/** Uniform des gewaehlten Aliens weiterschalten (classic → evo → ultimate), nur freigeschaltete Aliens. */
	public static void cycleUniform() {
		MinecraftClient client = MinecraftClient.getInstance();
		Optional<Entry> entry = focused();
		if (client.player == null || entry.isEmpty() || !entry.get().unlocked() || !isInteractive()) {
			return;
		}
		List<String> available = AlienBodyRenderers.uniformsOf(entry.get().alien().model());
		if (available.size() < 2) {
			refuseInput();
			return;
		}
		String current = AlienUniforms.get(client.player, entry.get().id());
		String next = available.get((Math.max(0, available.indexOf(current)) + 1) % available.size());
		ClientPlayNetworking.send(new SetUniformPayload(entry.get().id(), next));
		OmnitrixFeedback.play(OmnitrixCue.SELECT);
	}

	/** Auswahl bestaetigen: verwandeln (oder zurueckverwandeln, wenn schon verwandelt). */
	public static void confirm() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || !isInteractive()) {
			return;
		}
		TransformationState state = TransformationManager.get(player);
		if (state.isTransformed()) {
			ClientPlayNetworking.send(RevertRequestPayload.INSTANCE);
			setPhase(OmnitrixPhase.REVERT);
			OmnitrixFeedback.play(OmnitrixCue.DETRANSFORM);
			client.setScreen(null);
			return;
		}
		Optional<Entry> entry = focused();
		if (entry.isEmpty()) {
			return;
		}
		// Geraet verweigert sofort sichtbar: gesperrt, ueberhitzt, zu heiss, Nachladen oder fehlende DNA
		OmnitrixStatus device = OmnitrixClientState.status(player);
		boolean tooHot = OmnitrixClientState.heat(player) + OmnitrixClientState.profile(player).heatPerTransform()
				* OmnitrixCore.state(player).heatFactor(OmnitrixClientState.profile(player)) >= 1.0f;
		if (!entry.get().unlocked() || state.rechargeRemaining(player.getWorld().getTime()) > 0
				|| device == OmnitrixStatus.LOCKED || device == OmnitrixStatus.OVERHEATED || tooHot) {
			refuseInput();
			return;
		}
		pending = entry.get().id();
		confirmTime = OmnitrixCore.confirmSeconds(player);
		setPhase(OmnitrixPhase.CONFIRMING);
		OmnitrixFeedback.play(OmnitrixCue.CONFIRM);
	}

	/**
	 * Sichtbarer Geraete-Zustand: Sperre und Ueberhitzung vom Server gehen vor, sonst legt der eigene Spieler seine
	 * Bedien-Phase darueber (aktiviert, Auswahl, Verwandlung); andere Spieler zeigen ihren geteilten Zustand.
	 */
	public static OmnitrixStatus displayStatus(net.minecraft.entity.player.PlayerEntity player) {
		OmnitrixStatus base = OmnitrixClientState.status(player);
		if (base == OmnitrixStatus.LOCKED || base == OmnitrixStatus.OVERHEATED || base == OmnitrixStatus.IDLE) {
			return base;
		}
		boolean local = player == MinecraftClient.getInstance().player;
		if (local) {
			return switch (phase) {
				case ACTIVATING, OPENING -> OmnitrixStatus.ACTIVE;
				case SELECTING, ROTATING, ALIEN_SELECTED -> OmnitrixStatus.SELECTING;
				case CONFIRMING, IMPACT, TRANSFORMATION -> OmnitrixStatus.TRANSFORMING;
				default -> base;
			};
		}
		return OmnitrixRemote.lift(player) > 0.5f ? OmnitrixStatus.SELECTING : base;
	}

	/** Geraet verweigert: Zifferblatt wackelt, Fehlerklang. */
	private static void refuseInput() {
		denyStart = System.nanoTime();
		OmnitrixFeedback.play(OmnitrixCue.ERROR);
	}

	/** Energieaufbau-Dauer der laufenden Bestaetigung (Sekunden). */
	public static float confirmTime() {
		return confirmTime;
	}

	/** Einmal pro Bild: Phasen weiterschalten, Werte nachfuehren. */
	public static void frame() {
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null) {
			phase = OmnitrixPhase.IDLE;
			raise = lift = wheel = energy = 0.0f;
			return;
		}
		advance(client, player);

		// Rad-Drehung als gedaempfte Feder: dreht physisch, schwingt kaum nach
		float stiffness = 140.0f;
		float damping = 2.0f * MathHelper.sqrt(stiffness) * 0.92f;
		velocity += ((target - display) * stiffness - velocity * damping) * dt;
		display += velocity * dt;
		if (phase == OmnitrixPhase.ROTATING && Math.abs(target - display) < SETTLED && Math.abs(velocity) < 0.2f) {
			display = target;
			velocity = 0.0f;
			setPhase(OmnitrixPhase.ALIEN_SELECTED);
			OmnitrixFeedback.play(OmnitrixCue.SELECT);
		}

		float targetRaise = phase.isArmRaised() ? 1.0f : 0.0f;
		float targetLift = phase.isOpen() ? (phase == OmnitrixPhase.OPENING ? MathHelper.clamp(phaseTime() / OPENING_TIME, 0.0f, 1.0f) : 1.0f) : 0.0f;
		float targetWheel = phase.showsWheel() ? 1.0f : 0.0f;
		float targetEnergy = switch (phase) {
			case OPENING -> MathHelper.clamp(phaseTime() / OPENING_TIME, 0.0f, 1.0f) * 0.7f;
			case SELECTING, ROTATING, ALIEN_SELECTED -> 0.7f;
			case CONFIRMING -> 0.7f + 0.3f * MathHelper.clamp(phaseTime() / confirmTime, 0.0f, 1.0f);
			case IMPACT -> 1.0f;
			default -> 0.0f;
		};
		raise = approach(raise, targetRaise, dt, targetRaise > raise ? 10.0f : 6.0f);
		lift = approach(lift, targetLift, dt, phase == OmnitrixPhase.IMPACT ? 40.0f : 12.0f);
		wheel = approach(wheel, targetWheel, dt, targetWheel > wheel ? 7.0f : 14.0f);
		energy = approach(energy, targetEnergy, dt, phase == OmnitrixPhase.IMPACT ? 30.0f : 6.0f);
	}

	private static void advance(MinecraftClient client, ClientPlayerEntity player) {
		TransformationState state = TransformationManager.get(player);
		boolean transformed = state.isTransformed();
		float t = phaseTime();
		switch (phase) {
			case ACTIVATING -> {
				if (t >= ACTIVATING_TIME) {
					setPhase(OmnitrixPhase.OPENING);
					OmnitrixFeedback.play(OmnitrixCue.OPEN);
				}
			}
			case OPENING -> {
				if (t >= OPENING_TIME) {
					setPhase(OmnitrixPhase.ALIEN_SELECTED);
				}
			}
			case SELECTING, ROTATING, ALIEN_SELECTED -> {
				if (!(client.currentScreen instanceof OmnitrixScreen)) {
					setPhase(basePhase());
				}
			}
			case CONFIRMING -> {
				if (t >= confirmTime) {
					setPhase(OmnitrixPhase.IMPACT);
					if (pending != null) {
						ClientPlayNetworking.send(new TransformRequestPayload(pending));
					}
					if (client.currentScreen instanceof OmnitrixScreen) {
						client.setScreen(null);
					}
				}
			}
			case IMPACT -> {
				if (t >= IMPACT_TIME) {
					setPhase(OmnitrixPhase.TRANSFORMATION);
				}
			}
			case TRANSFORMATION -> {
				if (transformed) {
					setPhase(OmnitrixPhase.ACTIVE_ALIEN);
					OmnitrixFeedback.play(OmnitrixCue.TRANSFORM);
				} else if (t >= TRANSFORMATION_TIMEOUT) {
					setPhase(basePhase());
				}
			}
			case REVERT -> {
				if (t >= REVERT_TIME && !transformed) {
					setPhase(basePhase());
				}
			}
			default -> {
				if (wasTransformed && !transformed) {
					setPhase(OmnitrixPhase.REVERT);
					OmnitrixFeedback.play(OmnitrixCue.DETRANSFORM);
				} else {
					OmnitrixPhase base = basePhase();
					if (base != phase) {
						setPhase(base);
					}
				}
			}
		}
		wasTransformed = transformed;
	}

	/** Ruhezustand je nach Lage: verwandelt, Abklingzeit, getragen oder ohne Omnitrix. */
	private static OmnitrixPhase basePhase() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return OmnitrixPhase.IDLE;
		}
		TransformationState state = TransformationManager.get(player);
		if (state.isTransformed()) {
			return OmnitrixPhase.ACTIVE_ALIEN;
		}
		if (!OmnitrixItem.hasOmnitrix(player)) {
			return OmnitrixPhase.IDLE;
		}
		return state.rechargeRemaining(player.getWorld().getTime()) > 0 ? OmnitrixPhase.COOLDOWN : OmnitrixPhase.EQUIPPED;
	}

	private static void setPhase(OmnitrixPhase next) {
		phase = next;
		phaseStart = System.nanoTime();
		// sichtbare Zustaende an andere Spieler; ROTATING/ALIEN_SELECTED sehen von aussen aus wie SELECTING
		OmnitrixPhase shared = switch (next) {
			case ROTATING, ALIEN_SELECTED -> OmnitrixPhase.SELECTING;
			case TRANSFORMATION, ACTIVE_ALIEN, REVERT, COOLDOWN -> OmnitrixPhase.IDLE;
			default -> next;
		};
		if (shared.isShared() && shared != sentPhase && ClientPlayNetworking.canSend(OmnitrixPhasePayload.ID)) {
			sentPhase = shared;
			ClientPlayNetworking.send(new OmnitrixPhasePayload(shared));
		}
	}

	private static float approach(float value, float target, float dt, float speed) {
		float next = value + (target - value) * Math.min(1.0f, dt * speed);
		return Math.abs(next - target) < 0.002f ? target : next;
	}
}
