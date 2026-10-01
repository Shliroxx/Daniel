package com.santiq.kingdomomnitrix.client.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Gemeinsame Zeichenhilfen, damit alle Menues und Anzeigen gleich aussehen. */
public final class UiDraw {
	public static final int ICON_SIZE = 16;
	private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();

	private UiDraw() {
	}

	/** Panel mit abgeschraegten Ecken, Rand und Lichtkante oben (KH-Stil). */
	public static void panel(DrawContext context, int x, int y, int width, int height, UiTheme theme) {
		if (width < 4 || height < 4) {
			return;
		}
		context.fill(x + 1, y, x + width - 1, y + height, theme.panel());
		context.fill(x, y + 1, x + 1, y + height - 1, theme.panel());
		context.fill(x + width - 1, y + 1, x + width, y + height - 1, theme.panel());
		// Rand ohne Eckpixel
		context.fill(x + 1, y, x + width - 1, y + 1, theme.border());
		context.fill(x + 1, y + height - 1, x + width - 1, y + height, theme.border());
		context.fill(x, y + 1, x + 1, y + height - 1, theme.border());
		context.fill(x + width - 1, y + 1, x + width, y + height - 1, theme.border());
		context.fill(x + 2, y + 1, x + width - 2, y + 2, 0x30FFFFFF);
	}

	/** Panel mit farbigem Streifen links (fuer HUD-Elemente). */
	public static void hudPanel(DrawContext context, int x, int y, int width, int height, UiTheme theme) {
		panel(context, x, y, width, height, theme);
		context.fill(x + 1, y + 2, x + 3, y + height - 2, theme.accent());
	}

	public static void bar(DrawContext context, int x, int y, int width, int height, float fraction, int color) {
		context.fill(x, y, x + width, y + height, UiTheme.BAR_BACK);
		int filled = Math.round(width * Math.max(0.0f, Math.min(1.0f, fraction)));
		if (filled > 0) {
			context.fill(x, y, x + filled, y + height, color);
			context.fill(x, y, x + filled, y + 1, 0x40FFFFFF);
		}
	}

	/** Zeichnet ein Symbol aus {@code textures/gui/icon/}; fehlt es, das Ersatzsymbol. */
	public static void icon(DrawContext context, Identifier texture, Identifier fallback, int x, int y, int size) {
		Identifier used = exists(texture) ? texture : fallback;
		context.drawTexture(used, x, y, size, size, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
	}

	private static boolean exists(Identifier texture) {
		return EXISTS.computeIfAbsent(texture, id -> MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent());
	}

	/** Nach Ressourcen-Neuladen (F3+T) neu pruefen. */
	public static void clearCache() {
		EXISTS.clear();
	}

	/** Kuerzt Text mit "…" auf eine Breite. */
	public static String trim(TextRenderer font, Text text, int maxWidth) {
		String value = text.getString();
		if (font.getWidth(value) <= maxWidth) {
			return value;
		}
		return font.trimToWidth(value, Math.max(0, maxWidth - font.getWidth("…"))) + "…";
	}
}
