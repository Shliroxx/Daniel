package com.santiq.kingdomomnitrix.client.render.toon;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyAnimatable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * Zeichnet einen Alien-Koerper im Ben-10-Zeichentrickstil: GeckoLib liefert nur Skelett und Animation, gezeichnet
 * werden die runden Cartoon-Netze ({@link ToonMesh}) je Knochen mit
 * <ul>
 *   <li>Cel-Shading: drei harte Helligkeitsstufen, Licht relativ zur Kamera (die dem Betrachter zugewandte Seite
 *       ist immer hell, wie im Zeichentrick),</li>
 *   <li>schwarzem Umriss nach dem Inverted-Hull-Verfahren (nach aussen verschobene Rueckseiten),</li>
 *   <li>Leuchtflaechen mit voller Helligkeit (Glut, Augen, Omnitrix).</li>
 * </ul>
 * Ohne Netz faellt der Renderer auf die Wuerfel der .geo.json zurueck.
 */
public class ToonAlienRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> {
	private static final Identifier WHITE = KingdomOmnitrix.id("textures/entity/alien/toon_white.png");
	/** Umrissbreite in Modell-Einheiten (1/16 Block) */
	private static final float OUTLINE = 0.38f;
	private static final int OUTLINE_COLOR = 0x140C0A;
	/** Lichtrichtung im Blickraum: von oben links vorn */
	private static final Vector3f VIEW_LIGHT = new Vector3f(-0.45f, 0.75f, 0.5f).normalize();
	private static final float FULL_BRIGHT_BAND = 0.4f;
	private static final float MID_BAND = -0.15f;

	private final Identifier meshId;
	private final Vector3f light = new Vector3f();
	private final Vector3f scratch = new Vector3f();

	public ToonAlienRenderer(EntityRendererFactory.Context context, GeoModel<AlienBodyAnimatable> model, Identifier meshId) {
		super(context, model, new AlienBodyAnimatable());
		this.meshId = meshId;
	}

	private ToonMesh mesh() {
		return ToonMeshes.get(meshId);
	}

	@Override
	public RenderLayer getRenderType(AlienBodyAnimatable animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource,
			float partialTick) {
		if (mesh() == null) {
			return super.getRenderType(animatable, texture, bufferSource, partialTick);
		}
		// Rueckseiten-Culling ist Pflicht fuer den Umriss
		return RenderLayer.getEntityCutout(WHITE);
	}

	@Override
	public void preRender(MatrixStack poseStack, AlienBodyAnimatable animatable, BakedGeoModel model, @Nullable VertexConsumerProvider bufferSource,
			@Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		// Licht folgt der Kamera: Blickraum -> Weltraum
		light.set(VIEW_LIGHT);
		MinecraftClient.getInstance().gameRenderer.getCamera().getRotation().transform(light);
	}

	@Override
	public void applyRenderLayers(MatrixStack poseStack, AlienBodyAnimatable animatable, BakedGeoModel model, @Nullable RenderLayer renderType,
			VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		// Die Leuchtschicht gehoert zu den Wuerfeln; das Netz leuchtet selbst
		if (mesh() == null) {
			super.applyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
		}
	}

	@Override
	public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		ToonMesh mesh = mesh();
		if (mesh == null) {
			super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
			return;
		}
		if (bone.isHidden()) {
			return;
		}
		ToonMesh.Part part = mesh.part(bone.getName());
		if (part == null) {
			return;
		}
		MatrixStack.Entry entry = poseStack.peek();
		Matrix4f pose = entry.getPositionMatrix();
		Matrix3f normalMatrix = entry.getNormalMatrix();
		int alpha = (colour >>> 24) & 0xFF;
		float[] pos = part.positions();
		float[] nrm = part.normals();
		for (int t = 0; t < part.count(); t++) {
			int base = t * 9;
			// Cel-Shading mit der gemittelten Normalen des Dreiecks
			float nx = -(nrm[base] + nrm[base + 3] + nrm[base + 6]);
			float ny = nrm[base + 1] + nrm[base + 4] + nrm[base + 7];
			float nz = nrm[base + 2] + nrm[base + 5] + nrm[base + 8];
			scratch.set(nx, ny, nz).normalize().mul(normalMatrix);
			float d = scratch.dot(light);
			float band = part.glow()[t] ? 1.0f : d > FULL_BRIGHT_BAND ? 1.0f : d > MID_BAND ? 0.72f : 0.52f;
			int rgb = part.colors()[t];
			int r = Math.round(((rgb >> 16) & 0xFF) * band);
			int g = Math.round(((rgb >> 8) & 0xFF) * band);
			int b = Math.round((rgb & 0xFF) * band);
			int vertexLight = part.glow()[t] ? LightmapTextureManager.MAX_LIGHT_COORDINATE : packedLight;
			// x gespiegelt (Bedrock -> Minecraft) dreht die Wicklung: Ecken 0, 2, 1
			emit(buffer, entry, pose, pos, nrm, base, 0, 0f, r, g, b, alpha, packedOverlay, vertexLight);
			emit(buffer, entry, pose, pos, nrm, base, 2, 0f, r, g, b, alpha, packedOverlay, vertexLight);
			emit(buffer, entry, pose, pos, nrm, base, 1, 0f, r, g, b, alpha, packedOverlay, vertexLight);
			emit(buffer, entry, pose, pos, nrm, base, 1, 0f, r, g, b, alpha, packedOverlay, vertexLight);
		}
		// Umriss: nach aussen verschobene Huelle mit umgekehrter Wicklung — nur die Rueckseite bleibt sichtbar
		int or = (OUTLINE_COLOR >> 16) & 0xFF;
		int og = (OUTLINE_COLOR >> 8) & 0xFF;
		int ob = OUTLINE_COLOR & 0xFF;
		for (int t = 0; t < part.count(); t++) {
			int base = t * 9;
			emit(buffer, entry, pose, pos, nrm, base, 0, OUTLINE, or, og, ob, alpha, packedOverlay, packedLight);
			emit(buffer, entry, pose, pos, nrm, base, 1, OUTLINE, or, og, ob, alpha, packedOverlay, packedLight);
			emit(buffer, entry, pose, pos, nrm, base, 2, OUTLINE, or, og, ob, alpha, packedOverlay, packedLight);
			emit(buffer, entry, pose, pos, nrm, base, 2, OUTLINE, or, og, ob, alpha, packedOverlay, packedLight);
		}
	}

	private static void emit(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f pose, float[] pos, float[] nrm, int base, int corner,
			float push, int r, int g, int b, int a, int overlay, int light) {
		int i = base + corner * 3;
		float x = pos[i] + nrm[i] * push;
		float y = pos[i + 1] + nrm[i + 1] * push;
		float z = pos[i + 2] + nrm[i + 2] * push;
		buffer.vertex(pose, -x / 16.0f, y / 16.0f, z / 16.0f)
				.color(r, g, b, a)
				.texture(0.5f, 0.5f)
				.overlay(overlay)
				.light(light)
				// Normale nach oben: die Vanilla-Beleuchtung bleibt gleichmaessig, das Cel-Shading steckt in der Farbe
				.normal(entry, 0.0f, 1.0f, 0.0f);
	}
}
