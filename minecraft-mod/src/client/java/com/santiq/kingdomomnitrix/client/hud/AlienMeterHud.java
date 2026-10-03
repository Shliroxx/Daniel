package com.santiq.kingdomomnitrix.client.hud;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.alien.AlienMeters;
import com.santiq.kingdomomnitrix.networking.AlienMeterPayload;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Tacho fuer Alien-Anzeigen: XLR8-Tempo als Geschwindigkeitsmesser, Heatblast-Kernhitze als Hitzeuhr. Segmentierter
 * Bogen ueber 240 Grad mit Farbverlauf und Leuchtsaum, Skalenstriche, Nadel mit Nachleuchten, grosse Zahl in der Mitte
 * und die Stufe darunter. Ab der ersten Stufe ziehen Tempo-Striche an den Seiten vorbei, bei 100 % pulsiert alles.
 * Verschiebbar im HUD-Editor wie alle Elemente.
 */
public final class AlienMeterHud implements HudElement {
	public static final AlienMeterHud INSTANCE = new AlienMeterHud();
	private static final Identifier ID = KingdomOmnitrix.id("alien_meter");

	private static final int WIDTH = 84;
	private static final int HEIGHT = 62;
	private static final int CX = WIDTH / 2;
	private static final int CY = 36;
	private static final int RADIUS = 31;
	private static final int THICKNESS = 6;
	private static final int SEGMENTS = 40;
	/** Bogen: von links unten (210 Grad) ueber oben nach rechts unten (-30 Grad) */
	private static final float START = 210.0f;
	private static final float SWEEP = 240.0f;
	/** Stufen-Grenzen (Prozent) */
	private static final float STAGE_ONE = 50.0f;
	private static final float STAGE_MAX = 100.0f;

	/** Farbverlauf je Anzeige: Stuetzfarben von 0 bis 100 % */
	private static final int[][] GRADIENT = {
			{0xFF0B3D91, 0xFF1E6FFF, 0xFF33B5FF, 0xFF6FF0FF, 0xFFFFFFFF},
			{0xFF6A1200, 0xFFD03A00, 0xFFFF7A10, 0xFFFFC23A, 0xFF7FE9FF},
			{0xFF3A0608, 0xFF8E0E14, 0xFFD3201E, 0xFFFF5A2A, 0xFFFFE07A}};
	private static final int[] ACCENT = {0xFF4FC3FF, 0xFFFF8A2A, 0xFFFF3B30};

	/** Beschriftung und Stufen-Namen je Anzeige */
	private static final String[] LABELS = {"message.kingdomomnitrix.xlr8_tempo", "message.kingdomomnitrix.heatblast_heat",
			"message.kingdomomnitrix.four_arms_rage"};
	private static final String[][] STAGES = {
			{"hud.kingdomomnitrix.meter.blur", "hud.kingdomomnitrix.meter.sound_barrier"},
			{"hud.kingdomomnitrix.meter.glowing", "hud.kingdomomnitrix.meter.overheated"},
			{"hud.kingdomomnitrix.meter.angry", "hud.kingdomomnitrix.meter.rampage"}};

	private AlienMeterHud() {
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public Text name() {
		return Text.translatable("hud.kingdomomnitrix.element.alien_meter");
	}

	@Override
	public HudAnchor defaultAnchor() {
		return HudAnchor.BOTTOM_CENTER;
	}

	/** rechts neben der Schnellleiste (182 breit) */
	@Override
	public int defaultX() {
		return 91 + 6 + WIDTH / 2;
	}

	@Override
	public int defaultY() {
		return -2;
	}

	@Override
	public boolean isActive(MinecraftClient client) {
		return client.player != null && AlienMeters.meterOf(client.player).isPresent();
	}

	@Override
	public int width(MinecraftClient client) {
		return WIDTH;
	}

	@Override
	public int height(MinecraftClient client) {
		return HEIGHT;
	}

	@Override
	public int previewWidth() {
		return WIDTH;
	}

	@Override
	public int previewHeight() {
		return HEIGHT;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, float tickDelta) {
		Optional<Integer> meter = AlienMeters.meterOf(client.player);
		if (meter.isEmpty()) {
			return;
		}
		int m = meter.get();
		float value = AlienMeters.value(client.player.getId(), m, tickDelta);
		float fraction = MathHelper.clamp(value / 100.0f, 0.0f, 1.0f);
		float time = client.player.age + tickDelta;
		boolean max = value >= STAGE_MAX - 0.5f;
		float pulse = max ? 0.5f + 0.5f * MathHelper.sin(time * 0.6f) : 0.0f;
		MatrixStack matrices = context.getMatrices();

		matrices.push();
		if (max) {
			// leichtes Zittern bei voller Anzeige
			matrices.translate(MathHelper.sin(time * 2.7f) * 0.6f, MathHelper.cos(time * 3.1f) * 0.4f, 0.0f);
		}
		disc(context, 0x9A0A0E16);
		if (value >= STAGE_ONE) {
			streaks(context, m, fraction, time);
		}
		// Leuchtsaum hinter den gefuellten Segmenten, staerker je voller
		int filled = Math.round(fraction * SEGMENTS);
		for (int i = 0; i < SEGMENTS; i++) {
			float f = i / (float) (SEGMENTS - 1);
			float angle = START - f * SWEEP;
			if (i < filled) {
				int color = gradient(m, f);
				int glowAlpha = (int) (40 + 60 * fraction + 80 * pulse);
				segment(context, angle, RADIUS + 2, THICKNESS + 4, 3, ColorHelper.Argb.withAlpha(Math.min(255, glowAlpha), color));
				segment(context, angle, RADIUS, THICKNESS, 2, max ? lighten(color, pulse * 0.5f) : color);
			} else {
				segment(context, angle, RADIUS, THICKNESS, 2, 0x66303A4C);
			}
		}
		// Skalenstriche alle 20 %, die Stufen-Grenzen laenger
		for (int t = 0; t <= 5; t++) {
			float angle = START - t * (SWEEP / 5.0f);
			boolean stage = t == 0 || t == 5 || t * 20 == (int) STAGE_ONE;
			segment(context, angle, RADIUS - THICKNESS - 1, stage ? 5 : 3, 1, stage ? 0xFFE6EEF8 : 0xFF8A96A8);
		}
		needle(context, START - fraction * SWEEP, m, fraction, pulse);
		matrices.pop();

		TextRenderer font = client.textRenderer;
		// grosse Zahl in der Mitte
		String number = String.valueOf(Math.round(value));
		matrices.push();
		matrices.translate(CX, CY - 13, 0);
		matrices.scale(1.6f, 1.6f, 1.0f);
		int numberColor = max ? lighten(ACCENT[m], pulse) : 0xFFFFFFFF;
		context.drawText(font, number, -font.getWidth(number) / 2, 0, numberColor, true);
		matrices.pop();
		Text label = Text.translatable(LABELS[m]);
		matrices.push();
		matrices.translate(CX, CY + 2, 0);
		matrices.scale(0.6f, 0.6f, 1.0f);
		context.drawText(font, label, -font.getWidth(label) / 2, 0, 0xFFB0B8C4, false);
		matrices.pop();
		// Stufe unter dem Bogen
		Text stage = stageText(m, value);
		if (stage != null) {
			int color = max ? lighten(ACCENT[m], pulse) : ACCENT[m];
			matrices.push();
			matrices.translate(CX, HEIGHT - 9, 0);
			matrices.scale(0.75f, 0.75f, 1.0f);
			context.drawText(font, stage, -font.getWidth(stage) / 2, 0, color, true);
			matrices.pop();
		}
	}

	private static Text stageText(int meter, float value) {
		if (value >= STAGE_MAX - 0.5f) {
			return Text.translatable(STAGES[meter][1]);
		}
		if (value >= STAGE_ONE) {
			return Text.translatable(STAGES[meter][0]);
		}
		return null;
	}

	/** Dunkle Scheibe als Hintergrund: gedrehte, schwache Streifen ueberlagern sich zur Mitte hin dichter (Vignette). */
	private static void disc(DrawContext context, int color) {
		MatrixStack matrices = context.getMatrices();
		for (int i = 0; i < 36; i++) {
			matrices.push();
			matrices.translate(CX, CY, 0);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * 5.0f));
			context.fill(-(RADIUS + 3), -3, RADIUS + 3, 3, ColorHelper.Argb.withAlpha(ColorHelper.Argb.getAlpha(color) / 9, color));
			matrices.pop();
		}
	}

	/** Ein Segment des Bogens: radial nach aussen bei {@code angle} (Grad, mathematisch), {@code width} quer. */
	private static void segment(DrawContext context, float angle, int outer, int length, int width, int color) {
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(CX, CY, 0);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-angle));
		context.fill(outer - length, -width / 2 - (width % 2), outer, width / 2, color);
		matrices.pop();
	}

	private static void needle(DrawContext context, float angle, int meter, float fraction, float pulse) {
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(CX, CY, 0);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-angle));
		int glow = ColorHelper.Argb.withAlpha((int) (70 + 100 * fraction + 60 * pulse), ACCENT[meter]);
		context.fill(-3, -2, RADIUS - THICKNESS + 1, 2, glow);
		context.fill(-3, -1, RADIUS - THICKNESS + 1, 1, 0xFFFFFFFF);
		matrices.pop();
		context.fill(CX - 2, CY - 2, CX + 2, CY + 2, 0xFF1A2230);
		context.fill(CX - 1, CY - 1, CX + 1, CY + 1, ACCENT[meter]);
	}

	/** Tempo-Striche (XLR8) bzw. aufsteigende Glut (Heatblast) an den Seiten, schneller je voller. */
	private static void streaks(DrawContext context, int meter, float fraction, float time) {
		int count = 3 + (int) (fraction * 5);
		for (int i = 0; i < count; i++) {
			float phase = (time * (0.08f + fraction * 0.12f) + i * 0.37f) % 1.0f;
			int alpha = (int) (200 * (1.0f - phase) * fraction);
			int color = ColorHelper.Argb.withAlpha(alpha, ACCENT[meter]);
			int row = 8 + (i * 13) % (HEIGHT - 16);
			if (meter == AlienMeterPayload.TEMPO) {
				int x = (int) (phase * 18);
				int length = 4 + (int) (fraction * 8);
				context.fill(x, row, x + length, row + 1, color);
				context.fill(WIDTH - x - length, row + 3, WIDTH - x, row + 4, color);
			} else {
				int y = HEIGHT - 6 - (int) (phase * (HEIGHT - 10));
				int x = (i % 2 == 0) ? 3 + i % 5 : WIDTH - 5 - i % 5;
				context.fill(x, y, x + 2, y + 2, color);
			}
		}
	}

	private static int gradient(int meter, float f) {
		int[] stops = GRADIENT[meter];
		float scaled = f * (stops.length - 1);
		int index = Math.min(stops.length - 2, (int) scaled);
		return lerpColor(stops[index], stops[index + 1], scaled - index);
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	private static int lighten(int color, float amount) {
		return lerpColor(color, 0xFFFFFFFF, MathHelper.clamp(amount, 0.0f, 1.0f));
	}
}
