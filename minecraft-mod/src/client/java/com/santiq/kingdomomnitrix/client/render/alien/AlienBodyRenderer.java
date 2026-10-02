package com.santiq.kingdomomnitrix.client.render.alien;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer fuer Alien-Koerper. Besonderheit: In der Leuchtschicht zeigen alle Normalen nach oben. Minecrafts
 * Emissive-Shader ignoriert zwar die Lichtkarte, dunkelt aber Seiten und Rueckseite weiterhin ab — leuchtende Flaechen
 * (Heatblasts Flammenkopf, Glut) blieben sonst von hinten khakifarben statt hell.
 */
public class AlienBodyRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> {
	private static final Vector3f UP = new Vector3f(0.0f, 1.0f, 0.0f);
	private boolean glowPass;

	public AlienBodyRenderer(EntityRendererFactory.Context context, GeoModel<AlienBodyAnimatable> model) {
		super(context, model, new AlienBodyAnimatable());
	}

	/** Leuchtschicht mit gleichmaessiger Helligkeit von allen Seiten. */
	public AlienBodyRenderer withEvenGlow() {
		addRenderLayer(new AutoGlowingGeoLayer<>(this) {
			@Override
			public void render(MatrixStack poseStack, AlienBodyAnimatable animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
					VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
				glowPass = true;
				try {
					super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
				} finally {
					glowPass = false;
				}
			}
		});
		return this;
	}

	/**
	 * GeckoLib 4.9.3 ruft {@code EntityRenderer.render} (Namensschild, Feuer) sowohl hier als auch in
	 * {@code renderFinal} auf — Spieler hatten als Alien zwei Namensschilder uebereinander. Nur {@code renderFinal}
	 * (nach dem Zuruecksetzen der Pose, richtige Hoehe) bleibt.
	 */
	@Override
	public void postRender(MatrixStack poseStack, AlienBodyAnimatable animatable, BakedGeoModel model, VertexConsumerProvider bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
	}

	@Override
	public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight,
			int packedOverlay, int colour) {
		super.createVerticesOfQuad(quad, poseState, glowPass ? UP : normal, buffer, packedLight, packedOverlay, colour);
	}
}
