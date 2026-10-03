package com.santiq.kingdomomnitrix.client.weapon;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.hud.HudAnchor;
import com.santiq.kingdomomnitrix.client.hud.HudElement;
import com.santiq.kingdomomnitrix.client.hud.OmnitrixHud;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.weapon.WeaponItem;
import com.santiq.kingdomomnitrix.weapon.WeaponRegistry;
import com.santiq.kingdomomnitrix.weapon.WeaponState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Waffen-Anzeige (Standard rechts ueber dem Omnitrix): Name, Stufe und Munition der gehaltenen R&C-Waffe. */
public final class WeaponHud implements HudElement {
	public static final WeaponHud INSTANCE = new WeaponHud();
	private static final Identifier ID = KingdomOmnitrix.id("weapon");
	private static final int WIDTH = 120;
	private static final int HEIGHT = 30;

	private WeaponHud() {
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.weapon");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.TOP_RIGHT;
	}

	@Override
	public int defaultX() {
		return -4;
	}

	@Override
	public int defaultY() {
		// direkt unter der Omnitrix-Anzeige (deren Hoehe wechselt zwischen bereit und verwandelt)
		MinecraftClient client = MinecraftClient.getInstance();
		OmnitrixHud omnitrix = OmnitrixHud.INSTANCE;
		return OmnitrixHud.TOP_OFFSET + (omnitrix.isActive(client) ? omnitrix.height(client) + 4 : 0);
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		return client.player != null && client.world != null && client.player.getMainHandStack().getItem() instanceof WeaponItem;
	}

	@Override
	public int width(MinecraftClient client) {
		return WIDTH;
	}

	@Override
	public int height(MinecraftClient client) {
		return HEIGHT;
	}

	@Override
	public int previewWidth() {
		return WIDTH;
	}

	@Override
	public int previewHeight() {
		return HEIGHT;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		if (client.player == null || client.world == null) {
			return;
		}
		ItemStack stack = client.player.getMainHandStack();
		TextRenderer font = client.textRenderer;
		WeaponState state = WeaponItem.state(stack);
		int maxLevel = WeaponRegistry.forStack(client.world.getRegistryManager(), stack).map(d -> d.maxLevel()).orElse(state.level());
		Text level = state.level() >= maxLevel && maxLevel > 1
				? Text.translatable("tooltip.kingdomomnitrix.weapon.max")
				: Text.translatable("hud.kingdomomnitrix.weapon_level", state.level());
		UiDraw.hudPanel(context, 0, 0, WIDTH, HEIGHT, UiTheme.TECH);
		context.drawItem(stack, 5, 7);
		context.drawTextWithShadow(font, UiDraw.trim(font, stack.getName(), WIDTH - 30), 24, 4, UiTheme.TEXT);
		context.drawTextWithShadow(font, level, 24, 16, UiTheme.TECH.accent());
		String ammo = state.maxAmmo() > 0 ? state.ammo() + " / " + state.maxAmmo() : "∞";
		int ammoColor = state.maxAmmo() > 0 && state.ammo() <= state.maxAmmo() / 5 ? 0xFFFF5555 : UiTheme.TECH.highlight();
		context.drawTextWithShadow(font, ammo, WIDTH - 5 - font.getWidth(ammo), 16, ammoColor);
	}
}
