package com.santiq.kingdomomnitrix.client.screen;

import com.santiq.kingdomomnitrix.omnitrix.workbench.OmnitrixWorkbenchScreenHandler;
import com.santiq.kingdomomnitrix.omnitrix.workbench.OmnitrixWorkbenchScreenHandler.InputSlot;
import com.santiq.kingdomomnitrix.omnitrix.workbench.OmnitrixWorkbenchScreenHandler.OutputSlot;
import com.santiq.kingdomomnitrix.omnitrix.workbench.WorkbenchRecipe;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Omnitrix-Werkbank: dunkles Klempner-Paneel mit gruenen Leuchtkanten, ohne Hintergrundtextur gezeichnet. Mitte: Pfeil
 * als Sanduhr, die sich mit dem Fortschritt fuellt; ueber Huelle und Konsole, links vom Kern ihr Name.
 */
public class OmnitrixWorkbenchScreen extends HandledScreen<OmnitrixWorkbenchScreenHandler> {
	private static final int PANEL = 0xFF23272E;
	private static final int PANEL_LIGHT = 0xFF3A4048;
	private static final int EDGE = 0xFF39FF14;
	private static final int EDGE_DIM = 0xFF1C6A10;
	private static final int SLOT = 0xFF14171B;
	private static final int TEXT = 0xFFB8FFB0;

	public OmnitrixWorkbenchScreen(OmnitrixWorkbenchScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 166;
		playerInventoryTitleY = backgroundHeight - 94;
		titleY = 6;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, titleX, titleY, TEXT, false);
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFF9AA0A8, false);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = x;
		int top = y;
		context.fill(left, top, left + backgroundWidth, top + backgroundHeight, PANEL);
		context.fill(left + 3, top + 16, left + backgroundWidth - 3, top + 70, PANEL_LIGHT);
		border(context, left, top, backgroundWidth, backgroundHeight, EDGE_DIM);
		border(context, left + 3, top + 16, backgroundWidth - 6, 54, EDGE);
		for (Slot slot : handler.slots) {
			int sx = left + slot.x - 1;
			int sy = top + slot.y - 1;
			boolean bench = slot instanceof InputSlot || slot instanceof OutputSlot;
			context.fill(sx, sy, sx + 18, sy + 18, bench ? EDGE : 0xFF0A0C0E);
			context.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
		}
		label(context, left + OmnitrixWorkbenchScreenHandler.SHELL_X + 8, top + OmnitrixWorkbenchScreenHandler.INPUT_Y - 10,
				Text.translatable("screen.kingdomomnitrix.workbench.shell"));
		label(context, left + OmnitrixWorkbenchScreenHandler.CONSOLE_X + 8, top + OmnitrixWorkbenchScreenHandler.INPUT_Y - 10,
				Text.translatable("screen.kingdomomnitrix.workbench.console"));
		Text core = Text.translatable("screen.kingdomomnitrix.workbench.core");
		context.drawText(textRenderer, core, left + OmnitrixWorkbenchScreenHandler.CORE_X - 4 - textRenderer.getWidth(core),
				top + OmnitrixWorkbenchScreenHandler.CORE_Y + 4, 0xFF8AE08A, false);
		// Sanduhr mittig auf Hoehe der Ausgabe, Pfeil fuehrt zur Ausgabe
		hourglass(context, left + 108, top + OmnitrixWorkbenchScreenHandler.OUTPUT_Y + 8 - 12, handler.progress(), handler.working());
		if (!handler.working() && !handler.getSlot(WorkbenchRecipe.OUTPUT).hasStack()) {
			Text hint = Text.translatable("screen.kingdomomnitrix.workbench.hint");
			context.drawText(textRenderer, hint, left + backgroundWidth - 7 - textRenderer.getWidth(hint), top + 60, 0xFF6A7A6A, false);
		}
	}

	/** Sanduhr zwischen Eingaben und Ausgabe: obere Haelfte leert sich, untere fuellt sich im Fortschritt. */
	private void hourglass(DrawContext context, int cx, int top, float progress, boolean working) {
		int half = 12;
		for (int row = 0; row < half; row++) {
			int width = (half - row) * 2;
			boolean upperFull = row >= half * progress;
			context.fill(cx - width / 2, top + row, cx + width / 2, top + row + 1, working && upperFull ? EDGE : 0xFF0E1A0E);
			int lowerRow = half - 1 - row;
			int lowerWidth = (half - lowerRow) * 2;
			boolean lowerFull = row < half * progress;
			context.fill(cx - lowerWidth / 2, top + half + lowerRow, cx + lowerWidth / 2, top + half + lowerRow + 1,
					lowerFull ? EDGE : 0xFF0E1A0E);
		}
		context.fill(cx + 15, top + half - 1, cx + 30, top + half + 1, working ? EDGE : EDGE_DIM);
	}

	private void label(DrawContext context, int centerX, int y, Text text) {
		context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, 0xFF8AE08A, false);
	}

	private static void border(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}
}
