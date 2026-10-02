package com.santiq.kingdomomnitrix.omnitrix;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.OmnitrixItem;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.alien.TransformationState;
import com.santiq.kingdomomnitrix.networking.OmnitrixCuePayload;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.progression.AlienMastery;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Kern des Omnitrix: Geraete-Zustand, Profil, Hitze/Ueberhitzung, Sperre, Master Control, Status von Geraet und Aliens
 * und die Rueckmeldungen ({@link OmnitrixCue}). Verwandlung und Faehigkeiten fuehrt weiter der
 * {@link TransformationManager} aus — er fragt hier, ob das Geraet bereit ist, und meldet Wechsel zurueck.
 *
 * <p>Kein eigener Tick: Hitze wird aus Zeitstempeln berechnet ({@link OmnitrixState#heatAt}); geprueft wird nur fuer
 * verwandelte Spieler im ohnehin laufenden Ablauf des {@link TransformationManager} (Warnung, Ueberhitzung).</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class OmnitrixCore {
	public static final RegistryKey<Registry<OmnitrixProfile>> PROFILES = RegistryKey.ofRegistry(KingdomOmnitrix.id("omnitrix_profile"));

	public static final AttachmentType<OmnitrixState> STATE = AttachmentRegistry.create(KingdomOmnitrix.id("omnitrix"), builder -> builder
			.persistent(OmnitrixState.CODEC)
			.initializer(() -> OmnitrixState.EMPTY)
			.copyOnDeath()
			.syncWith(OmnitrixState.PACKET_CODEC, AttachmentSyncPredicate.all()));

	/** Warum das Geraet eine Verwandlung ablehnt */
	public enum Refusal {
		LOCKED, OVERHEATED, TOO_HOT
	}

	private OmnitrixCore() {
	}

	public static void register() {
		OmnitrixMalfunction.register();
		DynamicRegistries.registerSynced(PROFILES, OmnitrixProfile.CODEC);
		ScanRule.register();
	}

	// --- Lesen (Server und Client) --------------------------------------------------------------

	public static OmnitrixState state(PlayerEntity player) {
		OmnitrixState state = player.getAttached(STATE);
		return state != null ? state : OmnitrixState.EMPTY;
	}

	public static OmnitrixProfile profile(PlayerEntity player) {
		return profile(player.getWorld().getRegistryManager(), state(player).profile());
	}

	public static OmnitrixProfile profile(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(PROFILES).flatMap(registry -> registry.getOrEmpty(id)).orElse(OmnitrixProfile.DEFAULT);
	}

	public static float heat(PlayerEntity player) {
		return state(player).heatAt(player.getWorld().getTime(), TransformationManager.get(player).isTransformed(), profile(player));
	}

	/**
	 * Geraete-Zustand aus Server-Sicht (ohne Bedien-Phase): Sperre vor Ueberhitzung vor Verwandlung vor Nachladen.
	 * Der Client legt Auswahl/Verwandlungs-Ablauf darueber.
	 */
	public static OmnitrixStatus status(PlayerEntity player) {
		if (!OmnitrixItem.hasOmnitrix(player)) {
			return OmnitrixStatus.IDLE;
		}
		long now = player.getWorld().getTime();
		OmnitrixState device = state(player);
		TransformationState transformation = TransformationManager.get(player);
		if (device.isLocked(now)) {
			return OmnitrixStatus.LOCKED;
		}
		if (device.isOverheated(now)) {
			return OmnitrixStatus.OVERHEATED;
		}
		if (transformation.isTransformed()) {
			return heat(player) >= profile(player).heatWarning() ? OmnitrixStatus.WARNING : OmnitrixStatus.TRANSFORMED;
		}
		if (transformation.rechargeRemaining(now) > 0) {
			return OmnitrixStatus.COOLDOWN;
		}
		if (heat(player) >= profile(player).heatWarning()) {
			return OmnitrixStatus.WARNING;
		}
		return device.masterControl() ? OmnitrixStatus.MASTER_CONTROL : OmnitrixStatus.READY;
	}

	/** Status eines Aliens fuer Rad und Alien-Seite. */
	public static AlienStatus alienStatus(PlayerEntity player, Identifier alien) {
		if (!HeroDataAccess.get(player).hasAlien(alien)) {
			return AlienStatus.LOCKED;
		}
		TransformationState transformation = TransformationManager.get(player);
		if (transformation.activeAlien().filter(alien::equals).isPresent()) {
			return AlienStatus.ACTIVE;
		}
		boolean last = transformation.selectedAlien().filter(alien::equals).isPresent();
		if (last && transformation.rechargeRemaining(player.getWorld().getTime()) > 0) {
			return AlienStatus.COOLDOWN;
		}
		if (AlienMasteryManager.get(player).level(alien) >= AlienMastery.MAX_LEVEL) {
			return AlienStatus.MASTERED;
		}
		return last ? AlienStatus.SELECTED : AlienStatus.UNLOCKED;
	}

	/** Faktor auf die Verwandlungsdauer (Profil, Master Control). */
	public static float durationFactor(PlayerEntity player) {
		OmnitrixProfile profile = profile(player);
		return profile.durationMultiplier() * (state(player).masterControl() ? profile.masterControl().durationMultiplier() : 1.0f);
	}

	/** Faktor auf die Nachladezeit (Profil, Master Control). */
	public static float cooldownFactor(PlayerEntity player) {
		OmnitrixProfile profile = profile(player);
		return profile.cooldownMultiplier() * (state(player).masterControl() ? profile.masterControl().cooldownMultiplier() : 1.0f);
	}

	/** Energieaufbau-Zeit des Bedien-Ablaufs (Client). */
	public static float confirmSeconds(PlayerEntity player) {
		OmnitrixProfile profile = profile(player);
		return state(player).masterControl() ? profile.masterControl().confirmSeconds() : profile.confirmSeconds();
	}

	// --- Server: Pruefen und Wechsel melden -----------------------------------------------------

	/** Meisterschaftsstufe eines Aliens (fuer Hitze-Faktoren). */
	private static int mastery(ServerPlayerEntity player, Identifier alien) {
		return com.santiq.kingdomomnitrix.progression.AlienMasteryManager.get(player).level(alien);
	}

	/** Kann das Geraet jetzt in dieses Alien verwandeln? Leer = ja (Meisterschaft senkt den Hitze-Aufschlag). */
	public static Optional<Refusal> checkTransform(ServerPlayerEntity player, Identifier alien) {
		long now = player.getWorld().getTime();
		OmnitrixState device = state(player);
		if (device.isLocked(now)) {
			return Optional.of(Refusal.LOCKED);
		}
		if (device.isOverheated(now)) {
			return Optional.of(Refusal.OVERHEATED);
		}
		OmnitrixProfile profile = profile(player);
		float add = profile.heatPerTransform() * deviceFactor(device, profile)
				* com.santiq.kingdomomnitrix.progression.AlienMastery.transformHeatFactor(mastery(player, alien));
		if (add > 0.0f && heat(player) + add >= 1.0f) {
			return Optional.of(Refusal.TOO_HOT);
		}
		return Optional.empty();
	}

	/** Master-Control-Faktor des Geraets (ohne Alien-Anteil). */
	private static float deviceFactor(OmnitrixState device, OmnitrixProfile profile) {
		return device.masterControl() ? profile.masterControl().heatMultiplier() : 1.0f;
	}

	/** Kann das Geraet jetzt verwandeln? Leer = ja. */
	public static Optional<Refusal> checkTransform(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		OmnitrixState device = state(player);
		if (device.isLocked(now)) {
			return Optional.of(Refusal.LOCKED);
		}
		if (device.isOverheated(now)) {
			return Optional.of(Refusal.OVERHEATED);
		}
		OmnitrixProfile profile = profile(player);
		if (heat(player) + profile.heatPerTransform() * device.heatFactor(profile) >= 1.0f) {
			return Optional.of(Refusal.TOO_HOT);
		}
		return Optional.empty();
	}

	/** Schnellwechsel: wie Verwandeln, aber der Aufschlag ist der Schnellwechsel-Aufschlag. */
	public static Optional<Refusal> checkQuickChange(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		OmnitrixState device = state(player);
		if (device.isLocked(now)) {
			return Optional.of(Refusal.LOCKED);
		}
		if (device.isOverheated(now)) {
			return Optional.of(Refusal.OVERHEATED);
		}
		OmnitrixProfile profile = profile(player);
		if (!device.masterControl() && heat(player) + profile.quickChangeHeat() >= 1.0f) {
			return Optional.of(Refusal.TOO_HOT);
		}
		return Optional.empty();
	}

	/** Schnellwechsel ausgefuehrt: Hitze bis jetzt festschreiben, Aufschlag (Master Control/gemeistert: keiner). */
	public static void onQuickChange(ServerPlayerEntity player, Identifier alien) {
		long now = player.getWorld().getTime();
		OmnitrixProfile profile = profile(player);
		int level = mastery(player, alien);
		float add = profile.quickChangeHeat() * com.santiq.kingdomomnitrix.progression.AlienMastery.transformHeatFactor(level);
		update(player, s -> s.withHeat(now, true, profile, s.masterControl() ? 0.0f : add)
				.withAlienHeatFactor(com.santiq.kingdomomnitrix.progression.AlienMastery.activeHeatFactor(level)));
	}

	/** Notfall-Verwandlung moeglich? (Geraet nicht gesperrt, Abklingzeit vorbei) */
	public static boolean canFailsafe(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		OmnitrixState device = state(player);
		return !device.isLocked(now) && now >= device.failsafeReadyAt();
	}

	/** Notfall-Verwandlung ausgefuehrt: Hitze hoch, Abklingzeit setzen, Alarm-Rueckmeldung. */
	public static void onFailsafe(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		OmnitrixProfile profile = profile(player);
		update(player, s -> {
			OmnitrixState settled = s.withHeat(now, true, profile, 0.0f);
			return settled.withHeat(now, true, profile, Math.max(0.0f, profile.failsafeHeat() - settled.heat()))
					.withFailsafeReadyAt(now + profile.failsafeCooldownSeconds() * 20L);
		});
		cue(player, OmnitrixCue.EMERGENCY);
	}

	/** Favorit im gewaehlten Set umschalten (nur freigeschaltete Aliens). */
	public static void toggleFavorite(ServerPlayerEntity player, Identifier alien) {
		if (HeroDataAccess.get(player).hasAlien(alien)) {
			update(player, s -> s.toggleFavorite(alien));
		}
	}

	public static void setActiveSet(ServerPlayerEntity player, int set) {
		update(player, s -> s.withActiveSet(set));
	}

	/** Abgelehnte Verwandlung: Meldung und Fehler-Rueckmeldung. */
	public static void refuse(ServerPlayerEntity player, Refusal refusal) {
		long now = player.getWorld().getTime();
		OmnitrixState device = state(player);
		net.minecraft.text.MutableText text = switch (refusal) {
			case LOCKED -> Text.translatable("message.kingdomomnitrix.omnitrix_locked", seconds(device.lockedUntil() - now));
			case OVERHEATED -> Text.translatable("message.kingdomomnitrix.omnitrix_overheated", seconds(device.overheatedUntil() - now));
			case TOO_HOT -> Text.translatable("message.kingdomomnitrix.omnitrix_too_hot");
		};
		player.sendMessage(text.formatted(Formatting.RED), true);
		cue(player, OmnitrixCue.ERROR);
	}

	/** Verwandlung gestartet: Hitze aufschlagen (Meisterschaft senkt Aufschlag und laufende Hitze des Aliens). */
	public static void onTransform(ServerPlayerEntity player, Identifier alien) {
		long now = player.getWorld().getTime();
		OmnitrixProfile profile = profile(player);
		int level = mastery(player, alien);
		update(player, s -> s.withHeat(now, false, profile, profile.heatPerTransform() * deviceFactor(s, profile)
						* com.santiq.kingdomomnitrix.progression.AlienMastery.transformHeatFactor(level))
				.withAlienHeatFactor(com.santiq.kingdomomnitrix.progression.AlienMastery.activeHeatFactor(level)));
	}

	/** Rueckverwandlung: Hitze festschreiben (ab jetzt kuehlt das Geraet ab), Alien-Faktor zuruecksetzen. */
	public static void onRevert(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		OmnitrixProfile profile = profile(player);
		update(player, s -> s.withHeat(now, true, profile, 0.0f).withAlienHeatFactor(1.0f));
	}

	/**
	 * Jeden Tick fuer verwandelte Spieler (aus dem Ablauf des {@link TransformationManager}): Warnung beim Ueberschreiten
	 * der Schwelle, Ueberhitzung bei voller Hitze. Liefert true, wenn die Verwandlung wegen Ueberhitzung enden muss.
	 */
	public static boolean tickTransformed(ServerPlayerEntity player) {
		OmnitrixState device = state(player);
		OmnitrixProfile profile = profile(player);
		float heat = heat(player);
		if (heat >= 1.0f) {
			return true;
		}
		if (heat >= profile.heatWarning() && !device.warned()) {
			update(player, s -> s.withWarned(true));
			player.sendMessage(Text.translatable("message.kingdomomnitrix.omnitrix_warning").formatted(Formatting.GOLD), true);
			cue(player, OmnitrixCue.WARNING);
		}
		return false;
	}

	/** Ueberhitzung: nach dem Zurueckverwandeln sperren (Hitze bleibt voll und kuehlt danach ab). */
	public static void overheat(ServerPlayerEntity player) {
		long now = player.getWorld().getTime();
		OmnitrixProfile profile = profile(player);
		update(player, s -> s.withHeat(now, true, profile, 0.0f).withOverheatedUntil(now + profile.overheatLockSeconds() * 20L));
		player.sendMessage(Text.translatable("message.kingdomomnitrix.omnitrix_overheat").formatted(Formatting.RED), true);
		cue(player, OmnitrixCue.OVERHEAT);
	}

	/** Sicherheitssperre fuer {@code ticks} (0 = aufheben). */
	public static void lock(ServerPlayerEntity player, long ticks) {
		long now = player.getWorld().getTime();
		update(player, s -> s.withLockedUntil(ticks > 0 ? now + ticks : 0L));
		cue(player, ticks > 0 ? OmnitrixCue.LOCK : OmnitrixCue.READY);
	}

	public static void setMasterControl(ServerPlayerEntity player, boolean enabled) {
		update(player, s -> s.withMasterControl(enabled));
		if (enabled) {
			cue(player, OmnitrixCue.MASTER_CONTROL);
		}
	}

	public static void setProfile(ServerPlayerEntity player, Identifier profile) {
		update(player, s -> s.withProfile(profile));
	}

	/** Hitze direkt setzen (Befehl, Tests). */
	public static void setHeat(ServerPlayerEntity player, float heat) {
		long now = player.getWorld().getTime();
		boolean transformed = TransformationManager.get(player).isTransformed();
		OmnitrixProfile profile = profile(player);
		update(player, s -> {
			OmnitrixState settled = s.withHeat(now, transformed, profile, 0.0f);
			return settled.withHeat(now, transformed, profile, heat - settled.heat()).withWarned(false);
		});
	}

	public static boolean hasProfile(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(PROFILES).map(registry -> registry.containsId(id)).orElse(false);
	}

	/** Neue DNA im Omnitrix. */
	public static void onUnlock(ServerPlayerEntity player) {
		cue(player, OmnitrixCue.UNLOCK);
	}

	public static void cue(ServerPlayerEntity player, OmnitrixCue cue) {
		if (ServerPlayNetworking.canSend(player, OmnitrixCuePayload.ID)) {
			ServerPlayNetworking.send(player, new OmnitrixCuePayload(cue));
		}
	}

	private static void update(ServerPlayerEntity player, UnaryOperator<OmnitrixState> change) {
		OmnitrixState before = state(player);
		OmnitrixState after = change.apply(before);
		if (!after.equals(before)) {
			player.setAttached(STATE, after);
		}
	}

	private static long seconds(long ticks) {
		return Math.max(1L, (ticks + 19) / 20);
	}
}
