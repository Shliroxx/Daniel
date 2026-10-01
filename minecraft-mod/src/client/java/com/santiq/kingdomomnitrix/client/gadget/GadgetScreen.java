package com.santiq.kingdomomnitrix.client.gadget;

import com.santiq.kingdomomnitrix.gadget.GadgetScreenHandler;
import com.santiq.kingdomomnitrix.gadget.GadgetScreenHandler.GadgetSlotView;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/** Gadget-Guertel: gezeichnet ohne Hintergrundtextur, im Stil der Vanilla-Inventare. */
public class GadgetScreen extends HandledScreen<GadgetScreenHandler> {
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int GADGET_ACCENT = 0xFF4FC3FF;

	public GadgetScreen(GadgetScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 166;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = x;
		int top = y;
		context.fill(left, top, left + backgroundWidth, top + backgroundHeight, PANEL);
		context.fill(left, top, left + backgroundWidth - 1, top + 1, LIGHT);
		context.fill(left, top, left + 1, top + backgroundHeight - 1, LIGHT);
		context.fill(left + 1, top + backgroundHeight - 1, left + backgroundWidth, top + backgroundHeight, SHADOW);
		context.fill(left + backgroundWidth - 1, top + 1, left + backgroundWidth, top + backgroundHeight, SHADOW);
		for (Slot slot : handler.slots) {
			int sx = left + slot.x - 1;
			int sy = top + slot.y - 1;
			boolean gadget = slot instanceof GadgetSlotView;
			context.fill(sx, sy, sx + 18, sy + 18, gadget ? GADGET_ACCENT : SLOT_DARK);
			context.fill(sx + 1, sy + 1, sx + 18, sy + 18, LIGHT);
			context.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
		}
		for (Slot slot : handler.slots) {
			if (slot instanceof GadgetSlotView view) {
				Text label = Text.translatable(view.type().translationKey());
				int labelX = left + slot.x + 8 - textRenderer.getWidth(label) / 2;
				context.drawText(textRenderer, label, labelX, top + slot.y + 20, 0xFF404040, false);
			}
		}
	}
}
