package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.combat.ClientLockOn;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Lock-On-Anzeige oben in der Mitte (unter der Bossleiste): Name, Lebensbalken, Entfernung. */
public final class LockOnHud {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("lock_on");

	private static final int WIDTH = 140;
	private static final int TOP = 24;
	private static final int BAR_HEIGHT = 4;

	private LockOnHud() {
	}

	public static void register() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.BOSS_BAR, IdentifiedLayer.of(LAYER_ID, LockOnHud::render)));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		LivingEntity target = ClientLockOn.target(client);
		if (target == null) {
			return;
		}
		TextRenderer font = client.textRenderer;
		int x = (context.getScaledWindowWidth() - WIDTH) / 2;
		int y = TOP;
		int height = 4 + font.fontHeight + 3 + BAR_HEIGHT + 4;
		context.fill(x, y, x + WIDTH, y + height, 0xA0101420);
		context.drawBorder(x, y, WIDTH, height, 0xFFFFD84A);

		Text name = target.getDisplayName();
		String distance = String.format("%.1f m", Math.sqrt(client.player.squaredDistanceTo(target)));
		context.drawTextWithShadow(font, Text.literal("◎ ").append(name), x + 4, y + 4, 0xFFFFFFFF);
		context.drawTextWithShadow(font, distance, x + WIDTH - 4 - font.getWidth(distance), y + 4, 0xFFAAAAAA);

		int barY = y + 4 + font.fontHeight + 3;
		int barWidth = WIDTH - 8;
		float health = target.getMaxHealth() > 0 ? target.getHealth() / target.getMaxHealth() : 0.0f;
		context.fill(x + 4, barY, x + 4 + barWidth, barY + BAR_HEIGHT, 0xFF2A2F3A);
		int filled = Math.round(barWidth * Math.max(0.0f, Math.min(1.0f, health)));
		if (filled > 0) {
			context.fill(x + 4, barY, x + 4 + filled, barY + BAR_HEIGHT, health > 0.3f ? 0xFF4CD964 : 0xFFFF5555);
		}
	}
}
