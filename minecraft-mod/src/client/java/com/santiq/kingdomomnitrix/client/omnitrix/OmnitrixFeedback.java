package com.santiq.kingdomomnitrix.client.omnitrix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.vfx.CameraShake;
import com.santiq.kingdomomnitrix.client.vfx.ScreenEffects;
import com.santiq.kingdomomnitrix.networking.OmnitrixCuePayload;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCue;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Fuehrt Omnitrix-Rueckmeldungen aus: jede Bedienung und jeder Geraete-Wechsel (siehe {@link OmnitrixCue}) bekommt
 * Klang, Licht-Puls am Geraet, optional Bildschirmblitz, Kamera-Stoss und kurzen FOV-Impuls — INPUT → VISUAL → AUDIO.
 *
 * <p>Was ein Cue tut, steht in {@code assets/kingdomomnitrix/omnitrix/feedback.json} (Ressourcenpaket-faehig). Wie stark
 * Kamera und Blitze wirken, stellt der Spieler in {@code config/kingdomomnitrix-omnitrix.json} ein (0 = aus).</p>
 *
 * <p>Zustandswechsel, die der Client selbst sieht (Nachladen beendet → bereit), loest {@link #tick} aus, nur bei
 * Aenderung — kein Dauerbetrieb.</p>
 */
public final class OmnitrixFeedback {
	private static final Identifier FILE = KingdomOmnitrix.id("omnitrix/feedback.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Wirkung eines Cues (aus feedback.json) */
	public record Effect(Optional<Identifier> sound, float volume, float pitch, float pitchJitter, float light, int flash,
			float flashStrength, float shake, int shakeTicks, float fov, int fovTicks) {
		static final Effect NONE = new Effect(Optional.empty(), 0.0f, 1.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f, 0, 0.0f, 0);
	}

	/** Spieler-Einstellung */
	public static final class Settings {
		public float cameraShake = 1.0f;
		public float fovEffects = 1.0f;
		public float screenFlash = 1.0f;
		public float volume = 1.0f;
		/** Omnitrix-OS-Meldungen als Hologramm (false = schlicht in der Aktionsleiste). */
		public boolean holoMessages = true;
	}

	private static final Map<OmnitrixCue, Effect> EFFECTS = new EnumMap<>(OmnitrixCue.class);
	private static boolean loaded;
	private static Settings settings;
	private static float light;
	private static long lightTime;
	private static float fovKick;
	private static long fovStart;
	private static int fovTicks;
	private static OmnitrixStatus lastStatus;

	private OmnitrixFeedback() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OmnitrixCuePayload.ID, (payload, context) -> play(payload.cue()));
		ClientTickEvents.END_CLIENT_TICK.register(OmnitrixFeedback::tick);
	}

	/** Bei Ressourcen-Neuladen (F3+T) feedback.json neu lesen. */
	public static void reload() {
		loaded = false;
	}

	public static void play(OmnitrixCue cue) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		Effect effect = effect(cue);
		Settings config = settings();
		effect.sound().flatMap(id -> Optional.ofNullable(Registries.SOUND_EVENT.get(id))).ifPresent(sound -> {
			float pitch = effect.pitch() + (effect.pitchJitter() > 0.0f ? (player.getRandom().nextFloat() * 2.0f - 1.0f) * effect.pitchJitter() : 0.0f);
			playSound(player, sound, effect.volume() * config.volume, pitch);
		});
		if (effect.light() > 0.0f) {
			light = Math.max(currentLight(), effect.light());
			lightTime = System.nanoTime();
		}
		if (effect.flashStrength() > 0.0f && config.screenFlash > 0.0f) {
			ScreenEffects.flash(effect.flash(), client.world.getTime(), effect.flashStrength() * config.screenFlash);
		}
		if (effect.shake() > 0.0f && config.cameraShake > 0.0f) {
			CameraShake.start(effect.shake() * config.cameraShake, effect.shakeTicks(), 0);
		}
		if (effect.fov() != 0.0f && config.fovEffects > 0.0f) {
			fovKick = effect.fov() * config.fovEffects;
			fovStart = System.nanoTime();
			fovTicks = Math.max(1, effect.fovTicks());
		}
	}

	/** Licht-Puls am Geraet 0..1 (klingt in ~0,4 s ab); Renderer addieren ihn auf die Zustands-Helligkeit. */
	public static float currentLight() {
		float seconds = (System.nanoTime() - lightTime) / 1.0e9f;
		return light * Math.max(0.0f, 1.0f - seconds / 0.4f);
	}

	/** FOV-Faktor (1 = unveraendert): kurzer Impuls, weich hinein und heraus. */
	public static float fovMultiplier() {
		if (fovKick == 0.0f) {
			return 1.0f;
		}
		float t = (System.nanoTime() - fovStart) / 1.0e9f / (fovTicks / 20.0f);
		if (t >= 1.0f) {
			fovKick = 0.0f;
			return 1.0f;
		}
		return 1.0f + fovKick * MathHelper.sin(t * MathHelper.PI);
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			lastStatus = null;
			return;
		}
		OmnitrixStatus status = OmnitrixClientState.status(player);
		if (lastStatus != null && status != lastStatus) {
			if ((lastStatus == OmnitrixStatus.COOLDOWN || lastStatus == OmnitrixStatus.OVERHEATED || lastStatus == OmnitrixStatus.LOCKED)
					&& (status == OmnitrixStatus.READY || status == OmnitrixStatus.MASTER_CONTROL)) {
				play(OmnitrixCue.READY);
			} else if (status == OmnitrixStatus.COOLDOWN && lastStatus != OmnitrixStatus.OVERHEATED) {
				play(OmnitrixCue.COOLDOWN);
			}
		}
		lastStatus = status;
	}

	private static Effect effect(OmnitrixCue cue) {
		if (!loaded) {
			load();
		}
		return EFFECTS.getOrDefault(cue, Effect.NONE);
	}

	private static void load() {
		loaded = true;
		EFFECTS.clear();
		var resource = MinecraftClient.getInstance().getResourceManager().getResource(FILE);
		if (resource.isEmpty()) {
			KingdomOmnitrix.LOGGER.warn("{} fehlt — Omnitrix ohne Rueckmeldungen", FILE);
			return;
		}
		try (Reader reader = resource.get().getReader()) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (OmnitrixCue cue : OmnitrixCue.values()) {
				JsonElement element = root.get(cue.key());
				if (element != null && element.isJsonObject()) {
					EFFECTS.put(cue, parse(element.getAsJsonObject()));
				}
			}
		} catch (IOException | RuntimeException e) {
			KingdomOmnitrix.LOGGER.error("{} unlesbar — Omnitrix ohne Rueckmeldungen", FILE, e);
		}
	}

	private static Effect parse(JsonObject json) {
		Optional<Identifier> sound = json.has("sound") && !json.get("sound").getAsString().isEmpty()
				? Optional.ofNullable(Identifier.tryParse(json.get("sound").getAsString())) : Optional.empty();
		int flash = json.has("flash") ? Integer.parseInt(json.get("flash").getAsString().replace("#", ""), 16) : 0x39FF14;
		return new Effect(sound, number(json, "volume", 1.0f), number(json, "pitch", 1.0f), number(json, "pitch_jitter", 0.0f),
				number(json, "light", 0.0f), flash, number(json, "flash_strength", 0.0f), number(json, "shake", 0.0f),
				(int) number(json, "shake_ticks", 8.0f), number(json, "fov", 0.0f), (int) number(json, "fov_ticks", 8.0f));
	}

	private static float number(JsonObject json, String key, float fallback) {
		return json.has(key) ? json.get(key).getAsFloat() : fallback;
	}

	public static Settings settings() {
		if (settings != null) {
			return settings;
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve("kingdomomnitrix-omnitrix.json");
		Settings loadedSettings = null;
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				loadedSettings = GSON.fromJson(reader, Settings.class);
			} catch (IOException | RuntimeException e) {
				KingdomOmnitrix.LOGGER.error("{} unlesbar, nutze Standard", file, e);
			}
		}
		if (loadedSettings == null) {
			loadedSettings = new Settings();
			try {
				Files.createDirectories(file.getParent());
				try (Writer writer = Files.newBufferedWriter(file)) {
					GSON.toJson(loadedSettings, writer);
				}
			} catch (IOException e) {
				KingdomOmnitrix.LOGGER.warn("{} konnte nicht angelegt werden", file, e);
			}
		}
		loadedSettings.cameraShake = MathHelper.clamp(loadedSettings.cameraShake, 0.0f, 2.0f);
		loadedSettings.fovEffects = MathHelper.clamp(loadedSettings.fovEffects, 0.0f, 2.0f);
		loadedSettings.screenFlash = MathHelper.clamp(loadedSettings.screenFlash, 0.0f, 1.0f);
		loadedSettings.volume = MathHelper.clamp(loadedSettings.volume, 0.0f, 2.0f);
		settings = loadedSettings;
		return settings;
	}

	private static void playSound(ClientPlayerEntity player, SoundEvent sound, float volume, float pitch) {
		if (volume > 0.0f) {
			player.playSound(sound, volume, pitch);
		}
	}
}
