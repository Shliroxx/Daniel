package com.santiq.kingdomomnitrix.client.render.omnitrix;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Feinschliff des Omnitrix-Modells (Phase N), gezeichnet im Modellraum des {@link OmnitrixWrist} (Pixel/16, Zifferblatt
 * blickt nach +x, Mitte (13, 3, 8)):
 *
 * <ul>
 *   <li>Glas: schraeger Glanzstreifen, der langsam ueber das Zifferblatt wandert.</li>
 *   <li>Kern-Halo: weicher additiver Lichtkranz um das Zifferblatt in der Lichtfarbe des Kerns.</li>
 *   <li>Metallglanz: ein heller Streifen laeuft alle paar Sekunden ueber die Fassung.</li>
 *   <li>Mechanik: Ausfahren mit Ueberschwingen und kurzer Drehverriegelung des Kerns.</li>
 * </ul>
 */
public final class OmnitrixPolish {
	private static final float FACE_X = 12.95f;
	private static final float CENTER_Y = 3.0f;
	private static final float CENTER_Z = 8.0f;
	private static final float FACE_HALF = 1.7f;
	/** Drehverriegelung: groesster Winkel waehrend des Ausfahrens (Grad) */
	private static final float LOCK_DEGREES = 22.0f;
	private static final float PERIOD_SHINE = 4.5f;

	private OmnitrixPolish() {
	}

	private static float seconds() {
		return (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f;
	}

	/** Feder-Ueberschwingen (easeOutBack): 0 → ~1,1 → 1. */
	public static float overshoot(float t) {
		t = MathHelper.clamp(t, 0.0f, 1.0f);
		float c1 = 1.70158f;
		float c3 = c1 + 1.0f;
		float u = t - 1.0f;
		return 1.0f + c3 * u * u * u + c1 * u * u;
	}

	/** Drehverriegelung: dreht beim Ausfahren ein und rastet oben wieder auf 0 (Sinusbogen). */
	public static float lockTwist(float lift) {
		return MathHelper.sin(MathHelper.clamp(lift, 0.0f, 1.0f) * MathHelper.PI) * LOCK_DEGREES;
	}

	/** Um die Zifferblatt-Achse (x) durch die Kern-Mitte drehen. */
	public static void rotateAboutCore(MatrixStack matrices, float degrees) {
		if (Math.abs(degrees) < 0.01f) {
			return;
		}
		matrices.translate(0.0f, CENTER_Y / 16.0f, CENTER_Z / 16.0f);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
		matrices.translate(0.0f, -CENTER_Y / 16.0f, -CENTER_Z / 16.0f);
	}

	/** Glas ueber dem Zifferblatt: wandernder Glanzstreifen (additiv). */
	public static void renderGlass(MatrixStack matrices, VertexConsumerProvider consumers) {
		Matrix4f m = matrices.peek().getPositionMatrix();
		float x = (FACE_X + 0.14f) / 16.0f;
		// nur additiv (kein Tiefenschreiben): eine getoente Scheibe wuerde die Rauten-Anzeige darunter verdecken
		// Glanzstreifen: schraeges Band, wandert diagonal ueber das Glas und verschwindet am Rand
		float phase = (seconds() / 3.0f) % 1.0f;
		float offset = -FACE_HALF * 1.6f + phase * FACE_HALF * 3.2f;
		VertexConsumer glint = consumers.getBuffer(RenderLayer.getLightning());
		float width = 0.35f;
		float y0 = CENTER_Y - FACE_HALF;
		float y1 = CENTER_Y + FACE_HALF;
		float z0 = CENTER_Z + offset - FACE_HALF * 0.5f;
		float z1 = CENTER_Z + offset + FACE_HALF * 0.5f;
		float alpha = 0.22f * MathHelper.sin(phase * MathHelper.PI);
		float zMin = CENTER_Z - FACE_HALF;
		float zMax = CENTER_Z + FACE_HALF;
		// Band als Parallelogramm, auf die Glasflaeche begrenzt
		float a0 = MathHelper.clamp(z0, zMin, zMax);
		float a1 = MathHelper.clamp(z0 + width, zMin, zMax);
		float b0 = MathHelper.clamp(z1, zMin, zMax);
		float b1 = MathHelper.clamp(z1 + width, zMin, zMax);
		float gx = x + 0.002f;
		glint.vertex(m, gx, y0 / 16.0f, a0 / 16.0f).color(1.0f, 1.0f, 1.0f, alpha);
		glint.vertex(m, gx, y0 / 16.0f, a1 / 16.0f).color(1.0f, 1.0f, 1.0f, alpha);
		glint.vertex(m, gx, y1 / 16.0f, b1 / 16.0f).color(1.0f, 1.0f, 1.0f, alpha);
		glint.vertex(m, gx, y1 / 16.0f, b0 / 16.0f).color(1.0f, 1.0f, 1.0f, alpha);
	}

	/** Lichtkranz um das Zifferblatt (additiv), Staerke folgt der Kernhelligkeit. */
	public static void renderCoreHalo(MatrixStack matrices, VertexConsumerProvider consumers, float[] glow) {
		float strength = Math.max(glow[0], Math.max(glow[1], glow[2]));
		if (strength < 0.05f) {
			return;
		}
		Matrix4f m = matrices.peek().getPositionMatrix();
		VertexConsumer halo = consumers.getBuffer(RenderLayer.getLightning());
		float x = (FACE_X + 0.08f) / 16.0f;
		float pulse = 0.85f + 0.15f * MathHelper.sin(seconds() * 3.0f);
		// zwei schmale Ringe direkt am Zifferblatt, nach aussen schwaecher: weicher Kranz ohne eigene Textur
		for (int ring = 0; ring < 2; ring++) {
			float inner = FACE_HALF + 0.05f + ring * 0.16f;
			float outer = inner + 0.16f;
			float a = (0.16f - ring * 0.08f) * pulse;
			frame(halo, m, x, inner, outer, glow[0], glow[1], glow[2], a);
		}
	}

	/** Metallglanz: ein heller Streifen laeuft alle {@link #PERIOD_SHINE} s ueber die Fassung (Front des Gehaeuses). */
	public static void renderCasingShine(MatrixStack matrices, VertexConsumerProvider consumers) {
		float t = (seconds() % PERIOD_SHINE) / 0.9f;
		if (t > 1.0f) {
			return;
		}
		Matrix4f m = matrices.peek().getPositionMatrix();
		VertexConsumer shine = consumers.getBuffer(RenderLayer.getLightning());
		float x = 12.115f / 16.0f;
		float z = 5.4f + t * 5.2f;
		float alpha = 0.35f * MathHelper.sin(t * MathHelper.PI);
		// schmaler Streifen ueber die volle Hoehe der Fassung
		quad(shine, m, x, 1.1f, z, 4.9f, Math.min(10.6f, z + 0.3f), 1.0f, 1.0f, 1.0f, alpha);
	}

	/** Rechteck in der Ebene x (Pixelkoordinaten y/z), beidseitig. */
	private static void quad(VertexConsumer buffer, Matrix4f m, float x, float y0, float z0, float y1, float z1, float r, float g, float b,
			float a) {
		float[][] p = {{y0, z0}, {y0, z1}, {y1, z1}, {y1, z0}};
		for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
			for (int i : order) {
				buffer.vertex(m, x, p[i][0] / 16.0f, p[i][1] / 16.0f).color(r, g, b, a);
			}
		}
	}

	/** Quadratischer Rahmen zwischen inner und outer (halbe Kantenlaenge, Pixel) um die Zifferblatt-Mitte. */
	private static void frame(VertexConsumer buffer, Matrix4f m, float x, float inner, float outer, float r, float g, float b, float a) {
		quad(buffer, m, x, CENTER_Y + inner, CENTER_Z - outer, CENTER_Y + outer, CENTER_Z + outer, r, g, b, a);
		quad(buffer, m, x, CENTER_Y - outer, CENTER_Z - outer, CENTER_Y - inner, CENTER_Z + outer, r, g, b, a);
		quad(buffer, m, x, CENTER_Y - inner, CENTER_Z - outer, CENTER_Y + inner, CENTER_Z - inner, r, g, b, a);
		quad(buffer, m, x, CENTER_Y - inner, CENTER_Z + inner, CENTER_Y + inner, CENTER_Z + outer, r, g, b, a);
	}
}
