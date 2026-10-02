package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixClientState;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixStatus;

import com.santiq.kingdomomnitrix.alien.OmnitrixPhase;
import java.util.HashMap;
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
 * Auswahl-Anzeige auf dem Zifferblatt (wie Alien Evolution / Omniverse): Im Auswahlmodus leuchtet auf dem
 * hochgefahrenen Kern eine gelbgruene Raute mit der schwarzen Silhouette des gewaehlten Aliens — aus dessen echtem
 * Spielmodell, flach projiziert. Beim Weiterschalten dreht sich die Raute mit dem Kern; gesperrte Aliens erscheinen
 * als matte graue Raute mit dunkler Silhouette, beim Bestaetigen blitzt die Raute weiss auf.
 *
 * <p>Koordinaten: Modellraum des Omnitrix ({@link OmnitrixWrist}), Zifferblatt-Mitte (13, 3, 8) Pixel, Blickrichtung +x.</p>
 */
public final class OmnitrixDialDisplay {
	private static final float FACE_X = 13.06f;
	private static final float CENTER_Y = 3.0f;
	private static final float CENTER_Z = 8.0f;
	/** halbe Diagonale der Raute und Hoehe der Silhouette (Pixel) */
	private static final float DIAMOND = 1.3f;
	private static final float SILHOUETTE = 1.75f;
	private static final Map<Identifier, Float> HEIGHTS = new HashMap<>();

	private OmnitrixDialDisplay() {
	}

	public static void clearCache() {
		HEIGHTS.clear();
	}

	/**
	 * @param show   Einblendung 0..1 (Auswahlmodus)
	 * @param upDeg  Winkel um die Zifferblatt-Achse, unter dem „oben“ liegt (haengt von der Armhaltung ab)
	 */
	public static void render(MatrixStack matrices, VertexConsumerProvider consumers, float show, boolean local, float upDeg) {
		if (show <= 0.01f) {
			return;
		}
		OmnitrixController.Entry entry = local ? OmnitrixController.focused().orElse(null) : null;
		boolean unlocked = entry == null || entry.unlocked();
		float confirm = local && OmnitrixController.phase() == OmnitrixPhase.CONFIRMING
				? MathHelper.clamp(OmnitrixController.phaseTime() / OmnitrixController.confirmTime(), 0.0f, 1.0f) : 0.0f;
		float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f;
		// Kern-Dreh beim Weiterschalten: Rest der Rad-Feder als Viertel-Drehung
		float twist = local ? OmnitrixController.twist() * MathHelper.HALF_PI : 0.0f;
		float deny = local ? OmnitrixController.deny() : 0.0f;

		matrices.push();
		matrices.translate(FACE_X / 16.0f, CENTER_Y / 16.0f, CENTER_Z / 16.0f);
		matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
		// lokales y = Bildschirm-oben auf dem Zifferblatt
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(upDeg));
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(twist + deny * 0.25f));

		float size = DIAMOND * (0.6f + 0.4f * show) * (1.0f + 0.08f * confirm * MathHelper.sin(OmnitrixController.phaseTime() * 45.0f));
		float pulse = 0.88f + 0.12f * MathHelper.sin(time * 6.0f);
		float r;
		float g;
		float b;
		// Geraete-Zustand geht vor: gesperrt grau, ueberhitzt rot, Warnung gelb (blinkt mit dem Zustands-Puls)
		var player = net.minecraft.client.MinecraftClient.getInstance().player;
		OmnitrixStatus device = local && player != null ? OmnitrixClientState.status(player) : OmnitrixStatus.READY;
		if (device == OmnitrixStatus.LOCKED || device == OmnitrixStatus.OVERHEATED || device == OmnitrixStatus.WARNING) {
			float light = 0.45f + 0.55f * device.light(time) / Math.max(0.01f, device.brightness());
			r = ((device.color() >> 16) & 0xFF) / 255.0f * light;
			g = ((device.color() >> 8) & 0xFF) / 255.0f * light;
			b = (device.color() & 0xFF) / 255.0f * light;
		} else if (!unlocked) {
			r = 0.32f;
			g = 0.38f;
			b = 0.32f;
		} else {
			r = MathHelper.lerp(confirm, 0.78f, 1.0f) * pulse;
			g = MathHelper.lerp(confirm, 1.0f, 1.0f) * pulse;
			b = MathHelper.lerp(confirm, 0.12f, 0.85f) * pulse;
		}
		Matrix4f m = matrices.peek().getPositionMatrix();
		VertexConsumer fill = consumers.getBuffer(RenderLayer.getDebugQuads());
		// dunkler Rahmen, dann die leuchtende Raute
		diamond(fill, m, size + 0.3f, 0.0f, 0.03f, 0.05f, 0.03f, show);
		diamond(fill, m, size, 0.02f, r, g, b, show);
		if (unlocked) {
			VertexConsumer glow = consumers.getBuffer(RenderLayer.getLightning());
			diamond(glow, m, size + 0.15f, 0.03f, 0.5f * r, 0.6f * g, 0.2f * b, (0.35f + 0.5f * confirm) * show);
		}

		if (entry != null) {
			Identifier model = entry.alien().model();
			float height = SILHOUETTE * (0.6f + 0.4f * show);
			float scale = height / Math.max(0.5f, HEIGHTS.computeIfAbsent(model, AlienHologram::height));
			matrices.push();
			matrices.translate(0.08f, -height / 2.0f, 0.0f);
			// Modell schaut nach +z → zum Betrachter (+x) drehen und flachdruecken
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
			matrices.scale(scale, scale, scale * 0.08f);
			AlienHologram.render(matrices, consumers, model, 0.01f, 0.02f, 0.01f, (unlocked ? 0.95f : 0.8f) * show, true);
			matrices.pop();
		}
		matrices.pop();
	}

	/** Raute (auf der Spitze stehendes Quadrat) in der Ebene x = lift, beidseitig */
	private static void diamond(VertexConsumer buffer, Matrix4f m, float half, float lift, float red, float green, float blue,
			float alpha) {
		float[][] p = {{0.0f, half}, {half, 0.0f}, {0.0f, -half}, {-half, 0.0f}};
		for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
			for (int i : order) {
				buffer.vertex(m, lift, p[i][0], p[i][1]).color(red, green, blue, MathHelper.clamp(alpha, 0.0f, 1.0f));
			}
		}
	}
}
