package com.santiq.kingdomomnitrix.client.menu;

import com.santiq.kingdomomnitrix.client.hud.HudAnchor;
import com.santiq.kingdomomnitrix.client.hud.HudElement;
import com.santiq.kingdomomnitrix.client.hud.HudLayout;
import com.santiq.kingdomomnitrix.client.hud.HudManager;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.ui.UiTheme;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * HUD-Editor: jede Anzeige mit der Maus verschieben, mit dem Mausrad vergroessern/verkleinern und mit Rechtsklick
 * aus- oder einblenden. Gespeichert wird beim Schliessen in {@code config/kingdomomnitrix-hud.json}.
 */
public class HudEditorScreen extends Screen {
	private static final int FRAME = 0xFFFFFFFF;
	private static final int FRAME_SOFT = 0x80FFFFFF;
	private static final int FRAME_HIDDEN = 0xC0FF5555;

	private HudElement dragging;
	private double grabX;
	private double grabY;

	public HudEditorScreen() {
		super(Text.translatable("menu.kingdomomnitrix.tab.hud"));
	}

	@Override
	protected void init() {
		// ohne Reiter-Spalte: sie laege ueber den Anzeigen am linken Rand und finge deren Klicks ab
		HudManager.setEditing(true);
		int y = height / 2 + 30;
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.kingdomomnitrix.hud.reset"), b -> {
			for (HudElement element : HudManager.elements()) {
				HudLayout.reset(element);
			}
		}).dimensions(width / 2 - 82, y, 80, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(width / 2 + 2, y, 80, 20).build());
	}

	@Override
	public void removed() {
		HudManager.setEditing(false);
		HudLayout.save();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// keine Unschaerfe: die Spielwelt soll sichtbar bleiben, damit man die Anordnung beurteilen kann
		context.fill(0, 0, width, height, 0x50000000);
	}

	private int[] bounds(HudElement element) {
		HudLayout.Entry entry = HudLayout.get(element);
		boolean active = client != null && element.isActive(client);
		int w = active ? element.width(client) : element.previewWidth();
		int h = active ? element.height(client) : element.previewHeight();
		return HudManager.bounds(element, entry, w, h, width, height);
	}

	private HudElement elementAt(double mouseX, double mouseY) {
		List<HudElement> elements = HudManager.elements();
		for (int i = elements.size() - 1; i >= 0; i--) {
			int[] box = bounds(elements.get(i));
			if (mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3]) {
				return elements.get(i);
			}
		}
		return null;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (client == null) {
			return;
		}
		HudElement hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
		for (HudElement element : HudManager.elements()) {
			HudLayout.Entry entry = HudLayout.get(element);
			int[] box = bounds(element);
			if (element.isActive(client) && !entry.hidden()) {
				HudManager.draw(context, client, element, entry, delta);
			} else {
				// Platzhalter: Element hat gerade nichts zu zeigen oder ist ausgeblendet
				UiDraw.panel(context, box[0], box[1], box[2], box[3], UiTheme.HERO);
			}
			int color = entry.hidden() ? FRAME_HIDDEN : element == hovered ? FRAME : FRAME_SOFT;
			context.drawBorder(box[0] - 1, box[1] - 1, box[2] + 2, box[3] + 2, color);
			Text label = element.name().copy().append(Text.literal(String.format("  %d%%", Math.round(entry.scale() * 100))));
			if (entry.hidden()) {
				label = label.copy().append(Text.literal(" · ").append(Text.translatable("screen.kingdomomnitrix.hud.hidden"))
						.formatted(Formatting.RED));
			}
			// Name nur fuer das Element unter der Maus und fuer ausgeblendete, sonst ueberlappen sich die Beschriftungen
			if (element == hovered || entry.hidden()) {
				int labelY = box[1] > 10 ? box[1] - 10 : box[1] + box[3] + 2;
				context.drawTextWithShadow(textRenderer, label, box[0], labelY, element == hovered ? UiTheme.TEXT : UiTheme.TEXT_SOFT);
			}
		}
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.hud.help"), width / 2, height / 2 + 6,
				UiTheme.TEXT);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.kingdomomnitrix.hud.help2"), width / 2, height / 2 + 17,
				UiTheme.TEXT_SOFT);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		HudElement element = elementAt(mouseX, mouseY);
		if (element == null) {
			return false;
		}
		if (button == 1) {
			HudLayout.Entry entry = HudLayout.get(element);
			HudLayout.set(element, entry.withHidden(!entry.hidden()));
			return true;
		}
		if (button == 0) {
			int[] box = bounds(element);
			dragging = element;
			grabX = mouseX - box[0];
			grabY = mouseY - box[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging == null || button != 0) {
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
		int[] box = bounds(dragging);
		int x = (int) Math.round(mouseX - grabX);
		int y = (int) Math.round(mouseY - grabY);
		// Bezugspunkt = naechste Ecke/Kante; so bleibt die Anordnung bei anderer Fenstergroesse sinnvoll
		HudAnchor anchor = HudAnchor.nearest(x + box[2] / 2.0, y + box[3] / 2.0, width, height);
		int offsetX = x - anchor.baseX(width, box[2]);
		int offsetY = y - anchor.baseY(height, box[3]);
		HudLayout.set(dragging, HudLayout.get(dragging).withPosition(anchor, offsetX, offsetY));
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging != null) {
			dragging = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		HudElement element = elementAt(mouseX, mouseY);
		if (element == null || verticalAmount == 0) {
			return false;
		}
		HudLayout.Entry entry = HudLayout.get(element);
		HudLayout.set(element, entry.withScale(entry.scale() + (verticalAmount > 0 ? 0.1f : -0.1f)));
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
