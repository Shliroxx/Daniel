package com.santiq.kingdomomnitrix.client.render;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.ability.AttachedPropEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Modell an einem Gegner ({@link AttachedPropEntity}): Dateien je Art unter {@code geo|textures|animations/entity/prop},
 * in der Groesse des Ziels und mit dessen Blickrichtung gezeichnet.
 */
public class AttachedPropRenderer extends GeoEntityRenderer<AttachedPropEntity> {
	public AttachedPropRenderer(EntityRendererFactory.Context context) {
		super(context, new GeoModel<>() {
			@Override
			public Identifier getModelResource(AttachedPropEntity prop) {
				return KingdomOmnitrix.id("geo/entity/prop/" + kindOf(prop) + ".geo.json");
			}

			@Override
			public Identifier getTextureResource(AttachedPropEntity prop) {
				return KingdomOmnitrix.id("textures/entity/prop/" + kindOf(prop) + ".png");
			}

			@Override
			public Identifier getAnimationResource(AttachedPropEntity prop) {
				return KingdomOmnitrix.id("animations/entity/prop/" + kindOf(prop) + ".animation.json");
			}
		});
		this.shadowRadius = 0.0f;
	}

	private static String kindOf(AttachedPropEntity prop) {
		String kind = prop.kind();
		return kind.isEmpty() ? "crystal_spike" : kind;
	}

	@Override
	public void render(AttachedPropEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource,
			int packedLight) {
		if (entity.kind().isEmpty()) {
			return;
		}
		poseStack.push();
		var target = entity.target();
		if (target != null) {
			// am gezeichneten (interpolierten) Ziel kleben, nicht einen Tick hinterher — wichtig bei schnellen Zielen (Flammenbrett)
			var at = target.getLerpedPos(partialTick).subtract(entity.getLerpedPos(partialTick));
			poseStack.translate(at.x, at.y, at.z);
		}
		if (entity.kind().startsWith("flame_")) {
			packedLight = net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;   // Feuer leuchtet selbst
		}
		float scale = entity.targetScale();
		poseStack.scale(scale, scale, scale);
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		poseStack.pop();
	}

	@Override
	protected void applyRotations(AttachedPropEntity animatable, MatrixStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
			float nativeScale) {
		var target = animatable.target();
		float yaw = target == null ? animatable.getYaw() : target.getBodyYaw();
		poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
	}
}
