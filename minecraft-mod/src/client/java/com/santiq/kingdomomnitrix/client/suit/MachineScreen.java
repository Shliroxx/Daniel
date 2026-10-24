package com.santiq.kingdomomnitrix.client.suit;

import com.santiq.kingdomomnitrix.suit.forge.MachineKind;
import com.santiq.kingdomomnitrix.suit.forge.MachineScreenHandler;
import com.santiq.kingdomomnitrix.suit.forge.SuitForge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Menue der Ruestungs-Maschinen, ohne Hintergrundtextur gezeichnet: dunkles Paneel mit Leuchtrand in der Farbe der
 * Maschine, Fortschrittsbalken zwischen Eingabe und Ausgabe, Brennstoff-Flamme unter der Eingabe.
 */
public class MachineScreen extends HandledScreen<MachineScreenHandler> {
	private static final int PANEL = 0xFF1C1F25;
	private static final int PANEL_LIGHT = 0xFF2C3038;
	private static final int SLOT = 0xFF101216;

	public MachineScreen(MachineScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 166;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	public static void register() {
		for (MachineKind kind : MachineKind.values()) {
			HandledScreens.register(SuitForge.screenType(kind), MachineScreen::new);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, titleX, titleY, handler.kind().accent(), false);
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFF9AA0A8, false);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int accent = handler.kind().accent();
		int dim = (accent & 0x00FFFFFF) | 0x60000000;
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
		context.fill(x + 3, y + 14, x + backgroundWidth - 3, y + 78, PANEL_LIGHT);
		border(context, x, y, backgroundWidth, backgroundHeight, dim);
		border(context, x + 3, y + 14, backgroundWidth - 6, 64, accent);
		for (Slot slot : handler.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			boolean own = slot.id < handler.kind().size();
			context.fill(sx, sy, sx + 18, sy + 18, own ? accent : 0xFF0A0C0E);
			context.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
		}
		// Fortschrittspfeil zur Ausgabe
		int ax = x + 96;
		int ay = y + MachineScreenHandler.OUTPUT_Y + 6;
		context.fill(ax, ay, ax + 22, ay + 5, 0xFF0A0C0E);
		context.fill(ax, ay, ax + Math.round(22 * handler.progress()), ay + 5, accent);
		// Brennstoff: Balken links neben dem Brennstoff-Slot, fuellt sich von unten
		int fx = x + MachineScreenHandler.FUEL_X - 10;
		int fy = y + MachineScreenHandler.FUEL_Y;
		context.fill(fx, fy, fx + 5, fy + 16, 0xFF0A0C0E);
		int filled = Math.round(16 * handler.fuel());
		context.fill(fx, fy + 16 - filled, fx + 5, fy + 16, 0xFFFF9A2A);
	}

	private static void border(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}
}
