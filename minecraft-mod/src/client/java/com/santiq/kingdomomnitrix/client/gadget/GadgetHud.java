package com.santiq.kingdomomnitrix.client.gadget;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.hud.HudAnchor;
import com.santiq.kingdomomnitrix.client.hud.HudElement;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import com.santiq.kingdomomnitrix.gadget.GadgetLoadout;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Ausgeruestete Gadgets (Standard links neben der Hotbar, hinter dem Zweithand-Platz) mit ihrer Taste. */
public final class GadgetHud implements HudElement {
	public static final GadgetHud INSTANCE = new GadgetHud();
	private static final Identifier ID = KingdomOmnitrix.id("gadgets");
	private static final int BOX = 20;
	private static final int WIDTH = 2 * BOX + 2;
	/** Abstand der rechten Kante zur Bildschirmmitte: halbe Hotbar (91) + Zweithand-Platz (29) + 6. */
	private static final int CENTER_GAP = 91 + 29 + 6;

	private GadgetHud() {
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.gadgets");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.BOTTOM_CENTER;
	}

	@Override
	public int defaultX() {
		return -CENTER_GAP - WIDTH / 2;
	}

	@Override
	public int defaultY() {
		return -1;
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		return client.player != null && !client.player.isSpectator() && !GadgetManager.get(client.player).isEmpty();
	}

	@Override
	public int width(MinecraftClient client) {
		return WIDTH;
	}

	@Override
	public int height(MinecraftClient client) {
		return BOX;
	}

	@Override
	public int previewWidth() {
		return WIDTH;
	}

	@Override
	public int previewHeight() {
		return BOX;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		if (client.player == null) {
			return;
		}
		GadgetLoadout loadout = GadgetManager.get(client.player);
		box(context, client, loadout.back(), ModKeyBindings.PACK_MODE, 0, false);
		box(context, client, loadout.tool(), ModKeyBindings.USE_GADGET, BOX + 2, GadgetInput.isSwinging());
	}

	private static void box(DrawContext context, MinecraftClient client, ItemStack stack, KeyBinding key, int x, boolean active) {
		UiDraw.panel(context, x, 0, BOX, BOX, UiTheme.TECH);
		if (active) {
			context.fill(x + 1, 1, x + BOX - 1, BOX - 1, UiTheme.TECH.accent(0x90));
		}
		if (stack.isEmpty()) {
			return;
		}
		context.drawItem(stack, x + 2, 2);
		String label = key.getBoundKeyLocalizedText().getString();
		if (label.length() > 3) {
			label = label.substring(0, 3);
		}
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 200);
		context.drawTextWithShadow(client.textRenderer, label, x + BOX - client.textRenderer.getWidth(label), -4, UiTheme.TECH.highlight());
		context.getMatrices().pop();
	}
}
