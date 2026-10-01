package com.santiq.kingdomomnitrix.client.gadget;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.gadget.GadgetLoadout;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Ausgeruestete Gadgets links neben der Hotbar (hinter dem Zweithand-Platz) mit ihrer Taste. */
public final class GadgetHud {
	public static final Identifier LAYER_ID = KingdomOmnitrix.id("gadgets");
	private static final int BOX = 20;

	private GadgetHud() {
	}

	public static void register() {
		HudLayerRegistrationCallback.EVENT.register(drawer ->
				drawer.attachLayerAfter(IdentifiedLayer.HOTBAR_AND_BARS, IdentifiedLayer.of(LAYER_ID, GadgetHud::render)));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden || client.player.isSpectator()) {
			return;
		}
		GadgetLoadout loadout = GadgetManager.get(client.player);
		if (loadout.isEmpty()) {
			return;
		}
		// Links von Hotbar (91) und Zweithand-Platz (29) plus Abstand
		int right = context.getScaledWindowWidth() / 2 - 91 - 29 - 6;
		int y = context.getScaledWindowHeight() - BOX - 1;
		int x = right - 2 * BOX - 2;
		if (x < 2) {
			return;
		}
		box(context, client, loadout.back(), ModKeyBindings.PACK_MODE, x, y, false);
		box(context, client, loadout.tool(), ModKeyBindings.USE_GADGET, x + BOX + 2, y, GadgetInput.isSwinging());
	}

	private static void box(DrawContext context, MinecraftClient client, ItemStack stack, KeyBinding key, int x, int y, boolean active) {
		context.fill(x, y, x + BOX, y + BOX, active ? 0xC04FC3FF : 0xA0101420);
		if (stack.isEmpty()) {
			return;
		}
		context.drawItem(stack, x + 2, y + 2);
		String label = key.getBoundKeyLocalizedText().getString();
		if (label.length() > 3) {
			label = label.substring(0, 3);
		}
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 200);
		context.drawTextWithShadow(client.textRenderer, label, x + BOX - client.textRenderer.getWidth(label), y - 4, 0xFFFFD84A);
		context.getMatrices().pop();
	}
}
