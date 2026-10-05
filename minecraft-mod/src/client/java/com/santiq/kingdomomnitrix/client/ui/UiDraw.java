package com.santiq.kingdomomnitrix.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
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
		// Omnitrix-Symbole folgen dem Farbmodul
		Identifier used = IconTint.apply(exists(texture) ? texture : fallback);
		context.drawTexture(used, x, y, size, size, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
	}

	/** Groesse der Alien-Symbole und -Silhouetten in der Textur. */
	public static final int ALIEN_ICON_SIZE = 64;

	/**
	 * Farbiges Alien-Symbol in {@code size} Pixeln; {@code false}, wenn das Alien (z. B. aus einem fremden Datenpaket)
	 * kein Symbol mitbringt — der Aufrufer zeigt dann nur den Namen.
	 */
	public static boolean alienIcon(DrawContext context, Identifier alien, int x, int y, int size, float alpha) {
		Identifier texture = Icons.alien(alien);
		if (!exists(texture)) {
			return false;
		}
		tinted(context, texture, x, y, size, 0xFFFFFF, alpha);
		return true;
	}

	/** Silhouette in einer Farbe (Omnitrix-Zustand, Alien-Farbe, gesperrt grau); {@code false} ohne Symbol. */
	public static boolean alienSilhouette(DrawContext context, Identifier alien, int x, int y, int size, int rgb, float alpha) {
		Identifier texture = Icons.alienSilhouette(alien);
		if (!exists(texture)) {
			return false;
		}
		tinted(context, texture, x, y, size, rgb, alpha);
		return true;
	}

	private static void tinted(DrawContext context, Identifier texture, int x, int y, int size, int rgb, float alpha) {
		RenderSystem.enableBlend();
		context.setShaderColor(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f,
				Math.max(0.0f, Math.min(1.0f, alpha)));
		context.drawTexture(texture, x, y, size, size, 0, 0, ALIEN_ICON_SIZE, ALIEN_ICON_SIZE, ALIEN_ICON_SIZE, ALIEN_ICON_SIZE);
		context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.disableBlend();
	}

	private static boolean exists(Identifier texture) {
		return EXISTS.computeIfAbsent(texture, id -> MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent());
	}

	/** Nach Ressourcen-Neuladen (F3+T) neu pruefen. */
	public static void clearCache() {
		EXISTS.clear();
		IconTint.clear();
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
