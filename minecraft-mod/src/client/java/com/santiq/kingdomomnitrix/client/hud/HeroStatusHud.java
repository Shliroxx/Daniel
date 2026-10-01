package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Status-Panel oben links: Stufe, Erfahrungsbalken und Bolt-Konto.
 * Liest nur den synchronisierten {@link HeroData}-Stand, sendet nichts.
 */
public final class HeroStatusHud {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("hero_status");

	private static final int MARGIN = 4;
	private static final int PADDING = 3;
	private static final int BAR_HEIGHT = 3;
	private static final int PANEL_COLOR = 0x90101420;
	private static final int BAR_BACKGROUND = 0xFF2A2F3A;
	private static final int BAR_FILL = 0xFF4FC3FF;
	private static final int LEVEL_COLOR = 0xFFFFD84A;
	private static final int BOLT_COLOR = 0xFFE0C060;

	private HeroStatusHud() {
	}

	public static void register() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, IdentifiedLayer.of(LAYER_ID, HeroStatusHud::render)));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.options.hudHidden || client.inGameHud.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		HeroData data = HeroDataAccess.get(player);
		TextRenderer font = client.textRenderer;

		Text levelText = Text.translatable("hud.kingdomomnitrix.level", data.level());
		Text boltText = Text.translatable("hud.kingdomomnitrix.bolts", String.format("%,d", data.bolts()));
		int width = Math.max(font.getWidth(levelText), font.getWidth(boltText)) + PADDING * 2;
		width = Math.max(width, 80);
		int height = PADDING + font.fontHeight + 2 + BAR_HEIGHT + 3 + font.fontHeight + PADDING;

		int x = MARGIN;
		int y = MARGIN;
		context.fill(x, y, x + width, y + height, PANEL_COLOR);

		int textY = y + PADDING;
		context.drawTextWithShadow(font, levelText, x + PADDING, textY, LEVEL_COLOR);

		int barY = textY + font.fontHeight + 2;
		int barWidth = width - PADDING * 2;
		context.fill(x + PADDING, barY, x + PADDING + barWidth, barY + BAR_HEIGHT, BAR_BACKGROUND);
		float progress = data.isMaxLevel() ? 1.0f : (float) data.experience() / HeroData.experienceToNext(data.level());
		int filled = Math.round(barWidth * Math.min(1.0f, Math.max(0.0f, progress)));
		if (filled > 0) {
			context.fill(x + PADDING, barY, x + PADDING + filled, barY + BAR_HEIGHT, BAR_FILL);
		}

		context.drawTextWithShadow(font, boltText, x + PADDING, barY + BAR_HEIGHT + 3, BOLT_COLOR);
	}
}
