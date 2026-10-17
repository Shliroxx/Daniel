package com.santiq.kingdomomnitrix.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Vektor-Zeichenhilfen fuer das HUD: Kreisboegen/Ringe (Abklingzeit, Restzeit), abgerundete Flaechen, Verlaeufe,
 * weiche Leuchtkanten. Boegen werden als Dreiecke (entartete Vierecke) in die GUI-Ebene geschrieben.
 */
public final class HudGfx {
	private HudGfx() {
	}

	/** Ringsegment (rInner = 0: Tortenstueck). Winkel in Bogenmass, 0 = oben, im Uhrzeigersinn. */
	public static void arc(DrawContext context, float cx, float cy, float rOuter, float rInner, float start, float sweep, int argb) {
		if (sweep <= 0.0f || rOuter <= 0.0f) {
			return;
		}
		VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
		Matrix4f m = context.getMatrices().peek().getPositionMatrix();
		int steps = Math.max(3, (int) Math.ceil(sweep / (MathHelper.TAU / 64)));
		float step = sweep / steps;
		for (int i = 0; i < steps; i++) {
			float a0 = start + i * step;
			float a1 = a0 + step;
			float s0 = MathHelper.sin(a0);
			float c0 = -MathHelper.cos(a0);
			float s1 = MathHelper.sin(a1);
			float c1 = -MathHelper.cos(a1);
			// Viereck: innen0, aussen0, aussen1, innen1 (Gegen-Uhrzeigersinn fuer die GUI)
			buffer.vertex(m, cx + s0 * rInner, cy + c0 * rInner, 0).color(argb);
			buffer.vertex(m, cx + s1 * rInner, cy + c1 * rInner, 0).color(argb);
			buffer.vertex(m, cx + s1 * rOuter, cy + c1 * rOuter, 0).color(argb);
			buffer.vertex(m, cx + s0 * rOuter, cy + c0 * rOuter, 0).color(argb);
			// Rueckseite ebenfalls (die GUI-Ebene verwirft je nach Drehsinn)
			buffer.vertex(m, cx + s0 * rOuter, cy + c0 * rOuter, 0).color(argb);
			buffer.vertex(m, cx + s1 * rOuter, cy + c1 * rOuter, 0).color(argb);
			buffer.vertex(m, cx + s1 * rInner, cy + c1 * rInner, 0).color(argb);
			buffer.vertex(m, cx + s0 * rInner, cy + c0 * rInner, 0).color(argb);
		}
		context.draw();
	}

	public static void ring(DrawContext context, float cx, float cy, float rOuter, float rInner, int argb) {
		arc(context, cx, cy, rOuter, rInner, 0.0f, MathHelper.TAU, argb);
	}

	public static void disc(DrawContext context, float cx, float cy, float r, int argb) {
		arc(context, cx, cy, r, 0.0f, 0.0f, MathHelper.TAU, argb);
	}

	/** Flaeche mit abgeschnittenen Ecken (Radius 1–3 px), wirkt abgerundet. */
	public static void rounded(DrawContext context, int x, int y, int w, int h, int r, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		context.fill(x + r, y, x + w - r, y + h, argb);
		for (int i = 0; i < r; i++) {
			int inset = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
			context.fill(x + i, y + inset, x + i + 1, y + h - inset, argb);
			context.fill(x + w - 1 - i, y + inset, x + w - i, y + h - inset, argb);
		}
	}

	/** Senkrechter Verlauf in einer abgerundeten Flaeche. */
	public static void roundedGradient(DrawContext context, int x, int y, int w, int h, int r, int top, int bottom) {
		for (int row = 0; row < h; row++) {
			float t = h <= 1 ? 0.0f : row / (float) (h - 1);
			int c = lerpColor(top, bottom, t);
			int inset = 0;
			if (row < r) {
				inset = r - (int) Math.round(Math.sqrt(r * r - (r - row - 0.5) * (r - row - 0.5)));
			} else if (row >= h - r) {
				int k = h - 1 - row;
				inset = r - (int) Math.round(Math.sqrt(r * r - (r - k - 0.5) * (r - k - 0.5)));
			}
			context.fill(x + inset, y + row, x + w - inset, y + row + 1, c);
		}
	}

	/** Rahmen einer abgerundeten Flaeche (1 px). */
	public static void roundedBorder(DrawContext context, int x, int y, int w, int h, int argb) {
		context.fill(x + 2, y, x + w - 2, y + 1, argb);
		context.fill(x + 2, y + h - 1, x + w - 2, y + h, argb);
		context.fill(x, y + 2, x + 1, y + h - 2, argb);
		context.fill(x + w - 1, y + 2, x + w, y + h - 2, argb);
		context.fill(x + 1, y + 1, x + 2, y + 2, argb);
		context.fill(x + w - 2, y + 1, x + w - 1, y + 2, argb);
		context.fill(x + 1, y + h - 2, x + 2, y + h - 1, argb);
		context.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, argb);
	}

	/** Weiche Leuchtkante: Kantenstreifen nach aussen mit abnehmender Deckkraft (Ecken bleiben frei, wirkt rund). */
	public static void glow(DrawContext context, int x, int y, int w, int h, int rgb, float strength, int spread) {
		for (int i = 1; i <= spread; i++) {
			int alpha = (int) (strength * 140 * (1.0f - (i - 1) / (float) spread));
			if (alpha <= 0) {
				continue;
			}
			int c = alpha << 24 | rgb & 0xFFFFFF;
			context.fill(x + 2, y - i, x + w - 2, y - i + 1, c);
			context.fill(x + 2, y + h + i - 1, x + w - 2, y + h + i, c);
			context.fill(x - i, y + 2, x - i + 1, y + h - 2, c);
			context.fill(x + w + i - 1, y + 2, x + w + i, y + h - 2, c);
		}
	}

	/**
	 * Segmentierter Balken mit Verlauf (dunkel → hell), Glanzlinie und Teilstrichen; {@code ghost} zeigt einen
	 * nachlaufenden Rest (z. B. eben verbrauchte Energie) blass an.
	 */
	public static void segmentedBar(DrawContext context, int x, int y, int w, int h, float fraction, float ghost, int rgb, int segments) {
		rounded(context, x, y, w, h, 1, 0xC0101418);
		int inner = w - 2;
		int ghostW = Math.round(inner * MathHelper.clamp(ghost, 0.0f, 1.0f));
		int fill = Math.round(inner * MathHelper.clamp(fraction, 0.0f, 1.0f));
		if (ghostW > fill) {
			context.fill(x + 1 + fill, y + 1, x + 1 + ghostW, y + h - 1, 0x90FFFFFF);
		}
		if (fill > 0) {
			int dark = 0xFF000000 | mul(rgb, 0.55f);
			int light = 0xFF000000 | mix(rgb, 0xFFFFFF, 0.25f);
			context.fillGradient(x + 1, y + 1, x + 1 + fill, y + h - 1, light, dark);
			context.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0x60FFFFFF);
		}
		for (int i = 1; i < segments; i++) {
			int sx = x + 1 + Math.round(inner * i / (float) segments);
			context.fill(sx, y + 1, sx + 1, y + h - 1, 0x70000000);
		}
	}

	public static int lerpColor(int a, int b, float t) {
		int aa = a >>> 24;
		int ba = b >>> 24;
		return (int) (aa + (ba - aa) * t) << 24 | mix(a, b, t);
	}

	/** RGB mischen (ohne Alpha). */
	public static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return r << 16 | g << 8 | bl;
	}

	public static int mul(int rgb, float f) {
		return mix(0x000000, rgb, f);
	}

	public static int withAlpha(int rgb, int alpha) {
		return MathHelper.clamp(alpha, 0, 255) << 24 | rgb & 0xFFFFFF;
	}
}
