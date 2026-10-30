package com.santiq.kingdomomnitrix.client.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.combat.CombatFeedback.Kind;
import com.santiq.kingdomomnitrix.networking.DamageFeedbackPayload;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;

/**
 * Schadenszahlen und Treffer-Markierung (Kingdom-Hearts-Stil): Zahlen springen ueber dem Ziel auf, steigen und
 * verblassen; die Markierung am Fadenkreuz zeigt Treffer (weiss), schwere Treffer (orange) und Todesstoss (rotes X).
 * Abschaltbar in {@code config/kingdomomnitrix-combat.json}.
 */
public final class DamageNumbers {
	/** Spieler-Einstellungen (JSON); Werte werden beim Laden begrenzt. */
	public static final class Settings {
		public boolean damageNumbers = true;
		public boolean hitMarker = true;
		/** Groesse der Zahlen, 0,5–2 */
		public float numberScale = 1.0f;
	}

	private record Popup(Vec3d pos, Vec3d drift, String text, int color, float scale, int life, int[] age) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int LIFE = 26;
	private static final int LETHAL_LIFE = 34;
	private static final int MAX_POPUPS = 48;
	private static final int MARKER_TICKS = 6;
	private static final int KILL_MARKER_TICKS = 12;
	private static final List<Popup> POPUPS = new ArrayList<>();
	private static final Random RANDOM = Random.create();
	private static Settings settings;
	private static int markerTicks;
	private static int markerLength;
	private static int markerColor;
	private static boolean markerKill;

	private DamageNumbers() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DamageFeedbackPayload.ID, (payload, context) -> receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		WorldRenderEvents.AFTER_ENTITIES.register(DamageNumbers::renderWorld);
		HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerAfter(IdentifiedLayer.CROSSHAIR,
				IdentifiedLayer.of(KingdomOmnitrix.id("hit_marker"), DamageNumbers::renderMarker)));
	}

	// --- Empfang ---------------------------------------------------------------------------------

	private static void receive(DamageFeedbackPayload payload) {
		Kind kind = Kind.byOrdinal(payload.kind());
		boolean lethal = payload.lethal();
		Settings s = settings();
		if (s.damageNumbers) {
			if (POPUPS.size() >= MAX_POPUPS) {
				POPUPS.remove(0);
			}
			Vec3d pos = new Vec3d(payload.x() + (RANDOM.nextDouble() - 0.5) * 0.6, payload.y() + 0.2, payload.z() + (RANDOM.nextDouble() - 0.5) * 0.6);
			Vec3d drift = new Vec3d((RANDOM.nextDouble() - 0.5) * 0.03, 0.0, (RANDOM.nextDouble() - 0.5) * 0.03);
			POPUPS.add(new Popup(pos, drift, label(payload.amount(), kind), color(kind, lethal), scale(kind, lethal) * s.numberScale,
					lethal ? LETHAL_LIFE : LIFE, new int[] {0}));
		}
		if (s.hitMarker) {
			markerKill = lethal;
			markerTicks = lethal ? KILL_MARKER_TICKS : MARKER_TICKS;
			markerLength = markerTicks;
			markerColor = lethal ? 0xFF3030 : kind == Kind.HEAVY ? 0xFFA020 : kind == Kind.BLOCKED ? 0x9AA0AA : 0xFFFFFF;
		}
	}

	/** Anzeigetext: ganze Zahl ab 1 (wie in KH), darunter eine Nachkommastelle; Block als Wort. */
	static String label(float amount, Kind kind) {
		if (kind == Kind.BLOCKED) {
			return Text.translatable("hud.kingdomomnitrix.blocked").getString();
		}
		if (amount < 1.0f) {
			return String.format(Locale.ROOT, "%.1f", Math.max(0.1f, amount));
		}
		return Integer.toString(Math.round(amount));
	}

	private static int color(Kind kind, boolean lethal) {
		if (lethal) {
			return 0xFF4040;
		}
		return switch (kind) {
			case HEAVY -> 0xFFC230;
			case FIRE -> 0xFF7A1A;
			case MAGIC -> 0xC77DFF;
			case BLOCKED -> 0xA0A6B0;
			case NORMAL -> 0xFFFFFF;
		};
	}

	private static float scale(Kind kind, boolean lethal) {
		return lethal ? 1.45f : kind == Kind.HEAVY ? 1.3f : kind == Kind.BLOCKED ? 0.8f : 1.0f;
	}

	private static void tick() {
		Iterator<Popup> it = POPUPS.iterator();
		while (it.hasNext()) {
			Popup popup = it.next();
			if (++popup.age()[0] > popup.life()) {
				it.remove();
			}
		}
		if (markerTicks > 0) {
			markerTicks--;
		}
	}

	private static void clear() {
		POPUPS.clear();
		markerTicks = 0;
	}

	// --- Zeichnen in der Welt ------------------------------------------------------------------

	private static void renderWorld(WorldRenderContext context) {
		if (POPUPS.isEmpty() || context.matrixStack() == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer font = client.textRenderer;
		Camera camera = context.camera();
		Vec3d cam = camera.getPos();
		float tickDelta = context.tickCounter().getTickDelta(false);
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		for (Popup popup : POPUPS) {
			float age = popup.age()[0] + tickDelta;
			float t = age / popup.life();
			// steigt schnell an und bremst ab; seitliche Drift
			double rise = 0.9 * (1.0 - Math.pow(1.0 - Math.min(1.0, t * 1.4), 3));
			Vec3d at = popup.pos().add(popup.drift().multiply(age)).add(0.0, rise, 0.0);
			double distance = at.distanceTo(cam);
			if (distance > 48.0) {
				continue;
			}
			// Aufploppen: in den ersten Ticks groesser; mit der Entfernung leicht mitwachsen, damit lesbar
			float pop = age < 3.0f ? 1.0f + (3.0f - age) * 0.25f : 1.0f;
			float size = 0.04f * popup.scale() * pop * (float) MathHelper.clamp(0.6 + distance * 0.06, 0.8, 2.2);
			int alpha = t < 0.7f ? 255 : (int) MathHelper.clamp(255 * (1.0f - (t - 0.7f) / 0.3f), 16, 255);
			matrices.push();
			matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			matrices.multiply(camera.getRotation());
			matrices.scale(size, -size, size);
			OrderedText text = Text.literal(popup.text()).asOrderedText();
			float x = -font.getWidth(text) / 2.0f;
			int color = (alpha << 24) | popup.color();
			int outline = (alpha << 24) | 0x101018;
			font.drawWithOutline(text, x, 0.0f, color, outline, matrices.peek().getPositionMatrix(), consumers,
					LightmapTextureManager.MAX_LIGHT_COORDINATE);
			matrices.pop();
		}
		consumers.draw();
	}

	// --- Treffer-Markierung ----------------------------------------------------------------------

	private static void renderMarker(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (markerTicks <= 0 || client.options.hudHidden || client.options.getPerspective() != Perspective.FIRST_PERSON) {
			return;
		}
		float t = (markerTicks - tickCounter.getTickDelta(false)) / markerLength;
		int alpha = (int) MathHelper.clamp(255 * Math.min(1.0f, t * 1.6f), 0, 255);
		if (alpha <= 8) {
			return;
		}
		int cx = context.getScaledWindowWidth() / 2;
		int cy = context.getScaledWindowHeight() / 2;
		int color = (alpha << 24) | markerColor;
		// vier kurze Diagonalen, die beim Erscheinen etwas aufspringen; Todesstoss: laenger und dicker
		int gap = 3 + Math.round((1.0f - t) * 2.0f);
		int length = markerKill ? 5 : 3;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				for (int i = 0; i < length; i++) {
					int px = cx + sx * (gap + i);
					int py = cy + sy * (gap + i);
					context.fill(px, py, px + 1, py + 1, color);
					if (markerKill) {
						context.fill(px + (sx > 0 ? -1 : 1), py, px + (sx > 0 ? 0 : 2), py + 1, color);
					}
				}
			}
		}
	}

	// --- Einstellungen ---------------------------------------------------------------------------

	public static Settings settings() {
		if (settings != null) {
			return settings;
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve("kingdomomnitrix-combat.json");
		Settings loaded = null;
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				loaded = GSON.fromJson(reader, Settings.class);
			} catch (IOException | RuntimeException e) {
				KingdomOmnitrix.LOGGER.error("{} unlesbar, nutze Standard", file, e);
			}
		}
		if (loaded == null) {
			loaded = new Settings();
			try {
				Files.createDirectories(file.getParent());
				try (Writer writer = Files.newBufferedWriter(file)) {
					GSON.toJson(loaded, writer);
				}
			} catch (IOException e) {
				KingdomOmnitrix.LOGGER.warn("{} konnte nicht angelegt werden", file, e);
			}
		}
		loaded.numberScale = MathHelper.clamp(loaded.numberScale, 0.5f, 2.0f);
		settings = loaded;
		return settings;
	}
}
