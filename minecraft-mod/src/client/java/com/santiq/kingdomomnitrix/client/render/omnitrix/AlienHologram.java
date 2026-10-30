package com.santiq.kingdomomnitrix.client.render.omnitrix;

import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Zeichnet das echte Alien-Modell (GeckoLib, Grundpose) als Hologramm: dieselbe Geometrie und Textur wie der
 * Spielkoerper, eingefaerbt und durchscheinend. Auswahl und Gameplay-Modell sind damit dasselbe Modell.
 *
 * <p>Die Knochen werden aus ihrer gespeicherten Grundpose gelesen, nicht aus dem aktuellen Animationsstand — das
 * Modell-Objekt teilen sich alle Spieler mit diesem Alien.</p>
 */
public final class AlienHologram {
	private AlienHologram() {
	}

	/** Modell-Datei des Aliens ({@code model} der Alien-Definition) */
	public static Identifier geoFile(Identifier model) {
		return Identifier.of(model.getNamespace(), "geo/entity/alien/" + model.getPath() + ".geo.json");
	}

	public static Identifier texture(Identifier model) {
		return Identifier.of(model.getNamespace(), "textures/entity/alien/" + model.getPath() + ".png");
	}

	/** Modellhoehe in Bloecken (fuer die Groesse im Rad); 2, wenn unbekannt */
	public static float height(Identifier model) {
		BakedGeoModel baked = GeckoLibCache.getBakedModels().get(geoFile(model));
		if (baked == null) {
			return 2.0f;
		}
		float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE};
		for (GeoBone bone : baked.topLevelBones()) {
			collectHeight(bone, bounds);
		}
		return bounds[1] > bounds[0] ? (bounds[1] - bounds[0]) * AlienBodyRenderers.renderScale(model) : 2.0f;
	}

	private static void collectHeight(GeoBone bone, float[] bounds) {
		for (GeoCube cube : bone.getCubes()) {
			for (GeoQuad quad : cube.quads()) {
				if (quad == null) {
					continue;
				}
				for (GeoVertex vertex : quad.vertices()) {
					bounds[0] = Math.min(bounds[0], vertex.position().y());
					bounds[1] = Math.max(bounds[1], vertex.position().y());
				}
			}
		}
		for (GeoBone child : bone.getChildBones()) {
			collectHeight(child, bounds);
		}
	}

	/**
	 * Zeichnet das Hologramm mit den Fuessen im Ursprung, Gesicht zur Kamera (+z).
	 *
	 * @param locked gesperrt: dunkle Silhouette statt gruenem Licht
	 * @return false, wenn das Modell (noch) nicht geladen ist
	 */
	public static boolean render(MatrixStack matrices, VertexConsumerProvider consumers, Identifier model, float red, float green,
			float blue, float alpha, boolean locked) {
		BakedGeoModel baked = GeckoLibCache.getBakedModels().get(geoFile(model));
		if (baked == null) {
			return false;
		}
		Identifier texture = texture(model);
		VertexConsumer buffer = consumers.getBuffer(locked ? RenderLayer.getEntityTranslucent(texture)
				: RenderLayer.getEntityTranslucentEmissive(texture));
		int light = locked ? 0 : 0xF000F0;
		matrices.push();
		float scale = AlienBodyRenderers.renderScale(model);
		matrices.scale(scale, scale, scale);
		// GeckoLib-Modelle schauen nach -z; zur Kamera drehen
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
		for (GeoBone bone : baked.topLevelBones()) {
			renderBone(matrices, buffer, bone, red, green, blue, alpha, light);
		}
		matrices.pop();
		return true;
	}

	/** Additiver Leuchtpass (Farbe = Helligkeit), lichtunabhaengig. */
	public static void renderGlow(MatrixStack matrices, VertexConsumerProvider consumers, Identifier model, float red, float green,
			float blue) {
		BakedGeoModel baked = GeckoLibCache.getBakedModels().get(geoFile(model));
		if (baked == null) {
			return;
		}
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEyes(texture(model)));
		matrices.push();
		float scale = AlienBodyRenderers.renderScale(model);
		matrices.scale(scale, scale, scale);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
		for (GeoBone bone : baked.topLevelBones()) {
			renderBone(matrices, buffer, bone, red, green, blue, 1.0f, 0xF000F0);
		}
		matrices.pop();
	}

	private static void renderBone(MatrixStack matrices, VertexConsumer buffer, GeoBone bone, float red, float green, float blue,
			float alpha, int light) {
		if (Boolean.TRUE.equals(bone.shouldNeverRender())) {
			return;
		}
		matrices.push();
		BoneSnapshot pose = bone.getInitialSnapshot();
		if (pose == null) {
			// noch nie animiert: aktuelle Werte sind die Grundpose (GeckoLib macht es beim ersten Animieren genauso)
			bone.saveInitialSnapshot();
			pose = bone.getInitialSnapshot();
		}
		RenderUtil.translateToPivotPoint(matrices, bone);
		if (pose.getRotZ() != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pose.getRotZ()));
		}
		if (pose.getRotY() != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation(pose.getRotY()));
		}
		if (pose.getRotX() != 0.0f) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(pose.getRotX()));
		}
		RenderUtil.translateAwayFromPivotPoint(matrices, bone);
		for (GeoCube cube : bone.getCubes()) {
			matrices.push();
			RenderUtil.translateToPivotPoint(matrices, cube);
			RenderUtil.rotateMatrixAroundCube(matrices, cube);
			RenderUtil.translateAwayFromPivotPoint(matrices, cube);
			MatrixStack.Entry entry = matrices.peek();
			Matrix4f pose4 = entry.getPositionMatrix();
			for (GeoQuad quad : cube.quads()) {
				if (quad == null) {
					continue;
				}
				Vector3f normal = quad.normal();
				for (GeoVertex vertex : quad.vertices()) {
					Vector3f p = vertex.position();
					buffer.vertex(pose4, p.x(), p.y(), p.z()).color(red, green, blue, alpha).texture(vertex.texU(), vertex.texV())
							.overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, normal.x(), normal.y(), normal.z());
				}
			}
			matrices.pop();
		}
		for (GeoBone child : bone.getChildBones()) {
			renderBone(matrices, buffer, child, red, green, blue, alpha, light);
		}
		matrices.pop();
	}
}
