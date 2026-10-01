package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.magic.MagicState;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Status-Panel (Standard oben links): Heldenstufe, EP-Balken, Bolt-Konto und MP-Leiste (waehrend der MP-Ladezeit violett).
 * Liest nur den synchronisierten {@link HeroData}-Stand, sendet nichts.
 */
public final class HeroStatusHud implements HudElement {
	public static final HeroStatusHud INSTANCE = new HeroStatusHud();
	private static final Identifier ID = KingdomOmnitrix.id("hero_status");

	private static final int PADDING = 4;
	private static final int BAR_HEIGHT = 3;
	private static final int MIN_WIDTH = 84;
	private static final int BOLT_COLOR = 0xFFE0C060;
	private static final int MP_COLOR = 0xFF3D7BFF;
	private static final int MP_CHARGE_COLOR = 0xFFE040FB;

	private HeroStatusHud() {
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.hero_status");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.TOP_LEFT;
	}

	@Override
	public int defaultX() {
		return 4;
	}

	@Override
	public int defaultY() {
		return 4;
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		return client.player != null;
	}

	private static Text levelText(HeroData data) {
		return Text.translatable("hud.kingdomomnitrix.level", data.level());
	}

	private static Text boltText(HeroData data) {
		return Text.translatable("hud.kingdomomnitrix.bolts", String.format("%,d", data.bolts()));
	}

	@Override
	public int width(MinecraftClient client) {
		if (client.player == null) {
			return MIN_WIDTH;
		}
		HeroData data = HeroDataAccess.get(client.player);
		TextRenderer font = client.textRenderer;
		return Math.max(MIN_WIDTH, Math.max(font.getWidth(levelText(data)), font.getWidth(boltText(data))) + PADDING * 2 + 2);
	}

	@Override
	public int height(MinecraftClient client) {
		int font = client.textRenderer.fontHeight;
		return PADDING + font + 2 + BAR_HEIGHT + 3 + font + 2 + BAR_HEIGHT + PADDING;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		HeroData data = HeroDataAccess.get(player);
		TextRenderer font = client.textRenderer;
		int width = width(client);
		UiDraw.hudPanel(context, 0, 0, width, height(client), UiTheme.HERO);

		int x = PADDING + 2;
		int barWidth = width - x - PADDING;
		int textY = PADDING;
		context.drawTextWithShadow(font, levelText(data), x, textY, UiTheme.HERO.highlight());
		int barY = textY + font.fontHeight + 2;
		float progress = data.isMaxLevel() ? 1.0f : (float) data.experience() / HeroData.experienceToNext(data.level());
		UiDraw.bar(context, x, barY, barWidth, BAR_HEIGHT, progress, UiTheme.HERO.accent());

		context.drawTextWithShadow(font, boltText(data), x, barY + BAR_HEIGHT + 3, BOLT_COLOR);

		// MP wie in Kingdom Hearts immer sichtbar; waehrend der Aufladung violett
		int mpY = barY + BAR_HEIGHT + 3 + font.fontHeight + 2;
		long now = player.getWorld().getTime();
		MagicState magic = MagicManager.get(player);
		boolean charging = magic.isCharging(now);
		float mpFraction = charging ? magic.chargeProgress(now, MagicManager.chargeTicks(player))
				: MagicManager.currentMp(player) / MagicManager.maxMp(player);
		UiDraw.bar(context, x, mpY, barWidth, BAR_HEIGHT, mpFraction, charging ? MP_CHARGE_COLOR : MP_COLOR);
	}
}
