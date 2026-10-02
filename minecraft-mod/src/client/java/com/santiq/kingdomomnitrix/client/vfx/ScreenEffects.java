package com.santiq.kingdomomnitrix.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Bildschirm-Effekte (Entscheidung SANTIQ: Blitz und Vignette, immer voll): gruener Blitz beim Verwandeln,
 * roter beim Zurueckverwandeln, pulsierende rote Vignette bei wenig Leben. Liegt unter dem HUD.
 */
public final class ScreenEffects {
	private static final Identifier LAYER_ID = KingdomOmnitrix.id("screen_effects");
	private static final Identifier VIGNETTE = KingdomOmnitrix.id("textures/gui/vignette.png");
	private static final int VIGNETTE_SIZE = 128;
	private static final int FLASH_TICKS = 14;
	private static final float LOW_HEALTH = 0.3f;

	private static Optional<Identifier> lastAlien = Optional.empty();
	private static boolean known;
	private static int flashColor;
	private static long flashStart = Long.MIN_VALUE / 2;
	private static long lastEnd = Long.MAX_VALUE;
	private static float flashStrength = 1.0f;

	private ScreenEffects() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ScreenEffects::tick);
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.MISC_OVERLAYS, IdentifiedLayer.of(LAYER_ID, ScreenEffects::render)));
	}

	public static void reset() {
		known = false;
		lastAlien = Optional.empty();
		flashStart = Long.MIN_VALUE / 2;
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		var state = TransformationManager.get(player);
		Optional<Identifier> alien = state.activeAlien();
		long now = client.world.getTime();
		// erster Stand nach dem Einloggen ist kein Wechsel; Zurueckverwandeln: rot nur bei Zeitablauf (wie AE)
		if (known && !alien.equals(lastAlien)) {
			flash(alien.isPresent() ? 0x39FF14 : (now >= lastEnd - 2 ? 0xFF3A2A : 0xB8FFA0), now);
		}
		if (alien.isPresent()) {
			lastEnd = state.endTick();
		}
		lastAlien = alien;
		known = true;
	}

	public static void flash(int rgb, long now) {
		flash(rgb, now, 1.0f);
	}

	/** Blitz mit Staerke 0..1 (Omnitrix-Rueckmeldungen nutzen schwaechere Blitze). */
	public static void flash(int rgb, long now, float strength) {
		flashColor = rgb;
		flashStart = now;
		flashStrength = MathHelper.clamp(strength, 0.0f, 1.0f);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.options.hudHidden) {
			return;
		}
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		float time = client.world.getTime() + tickCounter.getTickDelta(false);

		// Blitz: kurz hell, dann ausblendend; die Vignette in derselben Farbe haelt etwas laenger
		float flash = 1.0f - (time - flashStart) / FLASH_TICKS;
		if (flash > 0.0f) {
			int alpha = Math.round(MathHelper.clamp(flash * flash * 0.55f * flashStrength, 0.0f, 1.0f) * 255);
			context.fill(0, 0, width, height, alpha << 24 | flashColor);
			vignette(context, width, height, flashColor, Math.min(1.0f, flash * 1.4f) * flashStrength);
		}

		// wenig Leben: rote Vignette, pulsiert schneller je weniger Leben
		float health = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
		if (player.isAlive() && !player.isCreative() && !player.isSpectator() && health <= LOW_HEALTH) {
			float danger = 1.0f - health / LOW_HEALTH;
			float speed = 0.15f + danger * 0.25f;
			float pulse = 0.55f + 0.45f * MathHelper.sin(time * speed);
			vignette(context, width, height, 0xC80000, (0.35f + danger * 0.5f) * pulse);
		}
	}

	private static void vignette(DrawContext context, int width, int height, int rgb, float strength) {
		RenderSystem.enableBlend();
		context.setShaderColor(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f,
				MathHelper.clamp(strength, 0.0f, 1.0f));
		context.drawTexture(VIGNETTE, 0, 0, width, height, 0, 0, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE);
		context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.disableBlend();
	}
}
