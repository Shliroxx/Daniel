package com.santiq.kingdomomnitrix.client.render;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.BoulderEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Vierarms Felsbrocken im Flug: AE-Modell (16 Pixel = 1 Block) auf die Brockengroesse skaliert, in Flugrichtung
 * gedreht; das Ueberschlagen kommt aus der Animation „spin“.
 */
public class BoulderRenderer extends GeoEntityRenderer<BoulderEntity> {
	public BoulderRenderer(EntityRendererFactory.Context context) {
		super(context, new DefaultedEntityGeoModel<>(KingdomOmnitrix.id("boulder")));
		this.shadowRadius = 0.6f;
	}

	@Override
	public void render(BoulderEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource,
			int packedLight) {
		poseStack.push();
		float size = entity.size();
		// Mitte des Modells liegt 8 Pixel ueber dem Ursprung: um die Mitte skalieren, Hitbox-Mitte bleibt Mitte
		poseStack.translate(0.0, entity.getHeight() / 2.0 - 0.5 * size, 0.0);
		poseStack.scale(size, size, size);
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		poseStack.pop();
	}

	@Override
	protected void applyRotations(BoulderEntity animatable, MatrixStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
			float nativeScale) {
		poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerp(partialTick, animatable.prevYaw, animatable.getYaw())));
	}
}
