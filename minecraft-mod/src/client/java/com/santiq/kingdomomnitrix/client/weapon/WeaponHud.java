package com.santiq.kingdomomnitrix.client.weapon;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.weapon.WeaponItem;
import com.santiq.kingdomomnitrix.weapon.WeaponRegistry;
import com.santiq.kingdomomnitrix.weapon.WeaponState;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Waffen-Anzeige am rechten Rand (ueber dem Omnitrix-HUD): Name, Stufe und Munition der gehaltenen R&C-Waffe. */
public final class WeaponHud {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("weapon");

	private WeaponHud() {
	}

	public static void register() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, IdentifiedLayer.of(LAYER_ID, WeaponHud::render)));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden || client.inGameHud.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		ItemStack stack = client.player.getMainHandStack();
		if (!(stack.getItem() instanceof WeaponItem)) {
			return;
		}
		TextRenderer font = client.textRenderer;
		WeaponState state = WeaponItem.state(stack);
		int maxLevel = WeaponRegistry.forStack(client.world.getRegistryManager(), stack).map(d -> d.maxLevel()).orElse(state.level());
		Text level = state.level() >= maxLevel && maxLevel > 1
				? Text.translatable("tooltip.kingdomomnitrix.weapon.max")
				: Text.translatable("hud.kingdomomnitrix.weapon_level", state.level());
		String ammo = state.maxAmmo() > 0 ? state.ammo() + " / " + state.maxAmmo() : "∞";
		int width = 120;
		int height = 26;
		// Rechter Rand, oberhalb des Omnitrix-Bereichs unten rechts
		int x = context.getScaledWindowWidth() - width - 4;
		int y = context.getScaledWindowHeight() - height - 64;
		context.fill(x, y, x + width, y + height, 0xA0101420);
		context.drawTextWithShadow(font, stack.getName(), x + 4, y + 3, 0xFFFFFFFF);
		context.drawTextWithShadow(font, level, x + 4, y + 14, 0xFF4FC3FF);
		int ammoColor = state.maxAmmo() > 0 && state.ammo() <= state.maxAmmo() / 5 ? 0xFFFF5555 : 0xFFFFD84A;
		context.drawTextWithShadow(font, ammo, x + width - 4 - font.getWidth(ammo), y + 14, ammoColor);
	}
}
