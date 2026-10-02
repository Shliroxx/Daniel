package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Omniverse-Auswahlscheibe: eine gruen durchscheinende Hologramm-Scheibe, die direkt aus dem Zifferblatt aufklappt.
 * Pro Alien ein Segment mit seiner Silhouette (aus dem echten Spielmodell, flach projiziert); das gewaehlte Segment
 * steht oben, ist hell und traegt eine helle Silhouette. Ueber dem oberen Rand dreht sich das 3D-Hologramm des
 * gewaehlten Aliens. Die Scheibe dreht sich physisch mit der Rad-Stellung; die Segmentzahl ergibt sich aus dem Roster.
 *
 * <p>Koordinaten: Modellraum des Omnitrix am Arm ({@link OmnitrixWrist}), Zifferblatt-Mitte bei (13, 3, 8) Pixel,
 * Scheibenebene senkrecht zu +x (Blickrichtung des Zifferblatts).</p>
 */
public final class OmnitrixDisc {
	private static final float PLANE_X = 13.1f;
	private static final float CENTER_Y = 3.0f;
	private static final float CENTER_Z = 8.0f;
	private static final float INNER = 2.6f;
	private static final float OUTER = 9.5f;
	private static final float SLOT_RADIUS = 6.4f;
	private static final float ICON_HEIGHT = 3.4f;
	private static final float HOLOGRAM_HEIGHT = 9.0f;
	/** Segmente, die andere Spieler sehen (ihr Roster kennt der Client nicht) */
	private static final int REMOTE_SLOTS = 6;
	private static final Map<Identifier, Float> HEIGHTS = new HashMap<>();

	private OmnitrixDisc() {
	}

	public static void clearCache() {
		HEIGHTS.clear();
	}

	private static float heightOf(Identifier model) {
		return HEIGHTS.computeIfAbsent(model, AlienHologram::height);
	}

	/**
	 * @param open    Aufklapp-Grad 0..1
	 * @param local   eigener Spieler: Roster, Silhouetten und Hologramm; sonst nur die Scheibe
	 * @param topDeg  Winkel, unter dem das gewaehlte Segment steht (abhaengig von der Armhaltung)
	 */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, float open, boolean local, float topDeg) {
		if (open <= 0.01f) {
			return;
		}
		List<OmnitrixController.Entry> entries = local ? OmnitrixController.entries() : List.of();
		int count = entries.isEmpty() ? REMOTE_SLOTS : entries.size();
		float display = entries.isEmpty() ? 0.0f : OmnitrixController.display();
		float step = MathHelper.TAU / count;
		float top = topDeg * MathHelper.RADIANS_PER_DEGREE;
		float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f;
		// Aufklappen: Radius waechst mit leichtem Ueberschwingen, Scheibe dreht sich dabei ein Stueck ein
		float unfold = easeOutBack(open);
		float outer = INNER + (OUTER - INNER) * unfold;
		float spin = (1.0f - open) * -0.9f;
		float confirm = local && OmnitrixController.phase() == OmnitrixPhase.CONFIRMING
				? MathHelper.clamp(OmnitrixController.phaseTime() / 0.42f, 0.0f, 1.0f) : 0.0f;
		float deny = local ? OmnitrixController.deny() : 0.0f;

		matrices.push();
		matrices.translate(PLANE_X / 16.0f, CENTER_Y / 16.0f, CENTER_Z / 16.0f);
		matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
		if (deny != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(deny * 0.06f));
		}

		VertexConsumer fill = consumers.getBuffer(RenderLayer.getDebugQuads());
		Matrix4f m = matrices.peek().getPositionMatrix();
		float gap = Math.min(0.06f, step * 0.08f);
		for (int k = 0; k < count; k++) {
			float offset = wrap(k - display, count);
			float a = top + spin + offset * step;
			float focus = entries.isEmpty() ? 0.0f : MathHelper.clamp(1.0f - Math.abs(offset), 0.0f, 1.0f);
			float alpha = (0.30f + 0.28f * focus + 0.25f * confirm) * open;
			float r = 0.55f + 0.35f * focus + 0.3f * confirm;
			float g = 1.0f;
			float b = 0.22f + 0.5f * focus + 0.4f * confirm;
			sector(fill, m, a - step / 2 + gap, a + step / 2 - gap, INNER + 0.4f, outer, r, g, b, alpha, 0.0f);
		}
		// helle Raender: innerer Ring am Zifferblatt, aeusserer Rand, Speichen
		VertexConsumer glow = consumers.getBuffer(RenderLayer.getLightning());
		float rim = (0.45f + 0.35f * confirm) * open;
		ring(glow, m, INNER + 0.2f, INNER + 0.55f, rim, 0.03f);
		ring(glow, m, outer - 0.35f, outer, rim * (0.8f + 0.2f * MathHelper.sin(time * 5.0f)), 0.03f);
		for (int k = 0; k < count; k++) {
			float a = top + spin + (wrap(k - display, count) + 0.5f) * step;
			sector(glow, m, a - 0.012f, a + 0.012f, INNER + 0.5f, outer - 0.3f, 0.6f, 1.0f, 0.5f, rim * 0.6f, 0.04f);
		}

		// Silhouetten in den Segmenten
		for (int k = 0; k < entries.size(); k++) {
			OmnitrixController.Entry entry = entries.get(k);
			float offset = wrap(k - display, count);
			float a = top + spin + offset * step;
			float focus = MathHelper.clamp(1.0f - Math.abs(offset), 0.0f, 1.0f);
			float radius = INNER + (SLOT_RADIUS - INNER) * unfold;
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(a));
			float height = ICON_HEIGHT * (0.85f + 0.3f * focus) * open;
			matrices.translate(0.08f, radius - height / 2.0f, 0.0f);
			renderSilhouette(matrices, consumers, entry, height, focus, open);
			matrices.pop();
		}

		// 3D-Hologramm des gewaehlten Aliens ueber dem oberen Rand, dreht sich um die eigene Achse
		if (local) {
			OmnitrixController.focused().filter(OmnitrixController.Entry::unlocked).ifPresent(entry -> {
				float pulse = 1.0f + 0.1f * confirm * MathHelper.sin(OmnitrixController.phaseTime() * 40.0f);
				float flicker = 0.9f + 0.1f * MathHelper.sin(time * 23.0f) * MathHelper.sin(time * 7.0f);
				matrices.push();
				matrices.multiply(RotationAxis.POSITIVE_X.rotation(top));
				matrices.translate(1.5f, outer + 0.6f, 0.0f);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f + time * 50.0f % 360.0f));
				float height = HOLOGRAM_HEIGHT * open * pulse;
				Identifier model = entry.alien().model();
				float scale = height / Math.max(0.5f, heightOf(model));
				matrices.scale(scale, scale, scale);
				float alpha = (0.6f + 0.3f * confirm) * open * flicker;
				AlienHologram.render(matrices, consumers, model, 0.4f, 1.0f, 0.55f, alpha, false);
				float shine = alpha * (0.5f + 0.4f * confirm);
				AlienHologram.renderGlow(matrices, consumers, model, 0.35f * shine, shine, 0.5f * shine);
				matrices.pop();
			});
		}
		matrices.pop();
	}

	/** Flache Silhouette: Modell zur Kamera (+x) gedreht und in Blickrichtung flachgedrueckt. */
	private static void renderSilhouette(MatrixStack matrices, VertexConsumerProvider consumers, OmnitrixController.Entry entry,
			float height, float focus, float open) {
		Identifier model = entry.alien().model();
		float scale = height / Math.max(0.5f, heightOf(model));
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
		matrices.scale(scale, scale, scale * 0.12f);
		if (!entry.unlocked()) {
			AlienHologram.render(matrices, consumers, model, 0.02f, 0.05f, 0.03f, 0.55f * open, true);
		} else if (focus > 0.5f) {
			AlienHologram.render(matrices, consumers, model, 0.9f, 1.0f, 0.92f, 0.95f * open, false);
			AlienHologram.renderGlow(matrices, consumers, model, 0.25f * focus, 0.5f * focus, 0.3f * focus);
		} else {
			AlienHologram.render(matrices, consumers, model, 0.03f, 0.1f, 0.05f, 0.9f * open, true);
		}
		matrices.pop();
	}

	/** Ringsegment in der Scheibenebene (x = lift), beidseitig */
	private static void sector(VertexConsumer buffer, Matrix4f m, float a0, float a1, float r0, float r1, float red, float green,
			float blue, float alpha, float lift) {
		int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 0.12f));
		for (int i = 0; i < steps; i++) {
			float s0 = a0 + (a1 - a0) * i / steps;
			float s1 = a0 + (a1 - a0) * (i + 1) / steps;
			float[] p0 = {MathHelper.cos(s0) * r0, MathHelper.sin(s0) * r0};
			float[] p1 = {MathHelper.cos(s1) * r0, MathHelper.sin(s1) * r0};
			float[] p2 = {MathHelper.cos(s1) * r1, MathHelper.sin(s1) * r1};
			float[] p3 = {MathHelper.cos(s0) * r1, MathHelper.sin(s0) * r1};
			// Rand heller als die Mitte: radialer Verlauf
			quad(buffer, m, p0, p1, p2, p3, lift, red, green, blue, alpha * 0.75f, alpha);
		}
	}

	private static void ring(VertexConsumer buffer, Matrix4f m, float r0, float r1, float alpha, float lift) {
		sector(buffer, m, 0.0f, MathHelper.TAU, r0, r1, 0.6f, 1.0f, 0.5f, alpha, lift);
	}

	private static void quad(VertexConsumer buffer, Matrix4f m, float[] p0, float[] p1, float[] p2, float[] p3, float lift,
			float red, float green, float blue, float innerAlpha, float outerAlpha) {
		// Punkte (y, z) in der Ebene x = lift; beide Wickelrichtungen, damit die Scheibe von hinten nicht verschwindet
		v(buffer, m, lift, p0, red, green, blue, innerAlpha);
		v(buffer, m, lift, p1, red, green, blue, innerAlpha);
		v(buffer, m, lift, p2, red, green, blue, outerAlpha);
		v(buffer, m, lift, p3, red, green, blue, outerAlpha);
		v(buffer, m, lift, p3, red, green, blue, outerAlpha);
		v(buffer, m, lift, p2, red, green, blue, outerAlpha);
		v(buffer, m, lift, p1, red, green, blue, innerAlpha);
		v(buffer, m, lift, p0, red, green, blue, innerAlpha);
	}

	private static void v(VertexConsumer buffer, Matrix4f m, float x, float[] p, float red, float green, float blue, float alpha) {
		buffer.vertex(m, x, p[0], p[1]).color(red, green, blue, MathHelper.clamp(alpha, 0.0f, 1.0f));
	}

	private static float easeOutBack(float t) {
		float c = 1.4f;
		float u = t - 1.0f;
		return 1.0f + (c + 1.0f) * u * u * u + c * u * u;
	}

	/** Abstand in Eintraegen, auf (-size/2, size/2] gefaltet */
	private static float wrap(float offset, int size) {
		float r = offset % size;
		if (r > size / 2.0f) {
			r -= size;
		} else if (r <= -size / 2.0f) {
			r += size;
		}
		return r;
	}
}
