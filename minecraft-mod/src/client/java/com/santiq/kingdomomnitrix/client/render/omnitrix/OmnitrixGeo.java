package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Omnitrix-Modell der Originalserie (Prototyp, aus Alien Evolution importiert: {@code tools/import_omnitrix_model.py})
 * am linken Arm. Die Knochen werden nicht ueber GeckoLib-Animationen bewegt, sondern direkt aus unserem Bedien-Ablauf —
 * wie AEs Animationen {@code open}/{@code close}/{@code spin}, nur stufenlos und mit Feder:
 *
 * <ul>
 *   <li>{@code core}: faehrt beim Oeffnen 0,75 px heraus (mit Ueberschwingen), Drehverriegelung beim Ausfahren.</li>
 *   <li>{@code a_01 … a_05}: Pfeile blitzen beim Oeffnen nacheinander auf, dann wechselt die Sanduhr
 *       ({@code a_closed}) auf die offenen Pfeile ({@code a_open}).</li>
 *   <li>{@code cylinder} senkt sich, {@code dial} dreht beim Weiterschalten.</li>
 * </ul>
 *
 * Zwei Durchgaenge: Gehaeuse (Grundtextur, Licht der Umgebung) und Leuchtschicht (Graustufe, gefaerbt im
 * Geraete-Zustand bzw. Farbmodul, additiv und lichtunabhaengig).
 */
public final class OmnitrixGeo {
	private static final Identifier GEO = KingdomOmnitrix.id("geo/omnitrix/prototype_omnitrix.geo.json");
	private static final Identifier GEO_SLIM = KingdomOmnitrix.id("geo/omnitrix/prototype_omnitrix_slim.geo.json");
	private static final Identifier TEXTURE = KingdomOmnitrix.id("textures/omnitrix/prototype_omnitrix.png");
	private static final Identifier GLOW = KingdomOmnitrix.id("textures/omnitrix/prototype_omnitrix_glow.png");
	/** Zifferblatt-Mitte im AE-Modell (Bedrock-Koordinaten, breiter Arm; schmal: x − 1) */
	public static final float DIAL_X = 8.8f;
	public static final float DIAL_Y = 15.25f;
	/** halbe Kantenlaenge des Zifferblatts (Pixel) */
	public static final float DIAL_HALF = 1.25f;
	private static final float CORE_TRAVEL = 0.75f;

	/** Zusatzbewegung je Knochen: Lage (Bedrock-Pixel) und Drehung (Grad). */
	private record Offset(float x, float y, float z, float rotX) {
	}

	private OmnitrixGeo() {
	}

	public static boolean available(boolean slim) {
		return GeckoLibCache.getBakedModels().get(slim ? GEO_SLIM : GEO) != null;
	}

	/**
	 * Zeichnen. Matrix: Armraum nach {@code leftArm.rotate} (Ursprung am Schultergelenk, y nach unten, Pixel/16).
	 *
	 * @param lift      Oeffnung 0..1 (Kern faehrt aus)
	 * @param dialDeg   Drehung des Zifferblatts (Grad, Weiterschalten)
	 * @param lockDeg   Drehverriegelung beim Ausfahren (Grad)
	 * @param glow      Lichtfarbe der Leuchtschicht (bereits mit Helligkeit verrechnet)
	 */
	public static boolean render(MatrixStack matrices, VertexConsumerProvider consumers, boolean slim, int light, float lift,
			float dialDeg, float lockDeg, float[] glow) {
		BakedGeoModel baked = GeckoLibCache.getBakedModels().get(slim ? GEO_SLIM : GEO);
		if (baked == null) {
			return false;
		}
		GeoBone root = baked.getBone("os_omnitrix").orElse(null);
		if (root == null) {
			return false;
		}
		Map<String, Offset> offsets = offsets(lift, dialDeg, lockDeg);
		matrices.push();
		// Bedrock (x, y nach oben) → Armraum (x, y nach unten): J = (bx − 5, 22 − by, bz); GeckoLib spiegelt x beim Backen
		matrices.translate(-5.0f / 16.0f, 22.0f / 16.0f, 0.0f);
		matrices.scale(-1.0f, -1.0f, 1.0f);
		VertexConsumer solid = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		renderBone(matrices, solid, root, offsets, 1.0f, 1.0f, 1.0f, light);
		VertexConsumer eyes = consumers.getBuffer(RenderLayer.getEyes(GLOW));
		renderBone(matrices, eyes, root, offsets, glow[0], glow[1], glow[2], 0xF000F0);
		matrices.pop();
		return true;
	}

	private static Map<String, Offset> offsets(float lift, float dialDeg, float lockDeg) {
		Map<String, Offset> offsets = new HashMap<>();
		float open = MathHelper.clamp(lift, 0.0f, 1.0f);
		offsets.put("core", new Offset(OmnitrixPolish.overshoot(open) * CORE_TRAVEL, 0.0f, 0.0f, lockDeg));
		offsets.put("dial", new Offset(0.0f, 0.0f, 0.0f, dialDeg));
		offsets.put("cylinder", new Offset(-0.1f * open, 0.0f, 0.0f, 0.0f));
		// Pfeile: nacheinander kurz vor (wie AEs Stufen-Animation), danach offen statt Sanduhr
		for (int i = 1; i <= 5; i++) {
			float start = 0.1f + (i - 1) * 0.12f;
			boolean flash = open > start && open < start + 0.2f && open < 0.98f;
			offsets.put("a_0" + i, new Offset(flash ? 0.25f : 0.0f, 0.0f, 0.0f, 0.0f));
		}
		boolean opened = open >= 0.7f;
		offsets.put("a_closed", new Offset(opened ? -0.25f : 0.0f, 0.0f, 0.0f, 0.0f));
		offsets.put("a_open", new Offset(opened ? 0.25f : 0.0f, 0.0f, 0.0f, 0.0f));
		return offsets;
	}

	private static void renderBone(MatrixStack matrices, VertexConsumer buffer, GeoBone bone, Map<String, Offset> offsets, float red,
			float green, float blue, int light) {
		if (Boolean.TRUE.equals(bone.shouldNeverRender())) {
			return;
		}
		matrices.push();
		Offset offset = offsets.get(bone.getName());
		if (offset != null) {
			// GeckoLib-Raum: x gespiegelt
			matrices.translate(-offset.x() / 16.0f, offset.y() / 16.0f, offset.z() / 16.0f);
		}
		RenderUtil.translateToPivotPoint(matrices, bone);
		if (bone.getRotZ() != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(bone.getRotZ()));
		}
		if (bone.getRotY() != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation(bone.getRotY()));
		}
		if (bone.getRotX() != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(bone.getRotX()));
		}
		if (offset != null && offset.rotX() != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(offset.rotX()));
		}
		RenderUtil.translateAwayFromPivotPoint(matrices, bone);
		for (GeoCube cube : bone.getCubes()) {
			matrices.push();
			RenderUtil.translateToPivotPoint(matrices, cube);
			RenderUtil.rotateMatrixAroundCube(matrices, cube);
			RenderUtil.translateAwayFromPivotPoint(matrices, cube);
			MatrixStack.Entry entry = matrices.peek();
			Matrix4f pose = entry.getPositionMatrix();
			for (GeoQuad quad : cube.quads()) {
				if (quad == null) {
					continue;
				}
				Vector3f normal = quad.normal();
				for (GeoVertex vertex : quad.vertices()) {
					Vector3f p = vertex.position();
					buffer.vertex(pose, p.x(), p.y(), p.z()).color(red, green, blue, 1.0f).texture(vertex.texU(), vertex.texV())
							.overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, normal.x(), normal.y(), normal.z());
				}
			}
			matrices.pop();
		}
		for (GeoBone child : bone.getChildBones()) {
			renderBone(matrices, buffer, child, offsets, red, green, blue, light);
		}
		matrices.pop();
	}
}
