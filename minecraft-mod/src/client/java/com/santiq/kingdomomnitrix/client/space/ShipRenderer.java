package com.santiq.kingdomomnitrix.client.space;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.space.ShipEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Die Aphelion mit GeckoLib; neigt die Nase mit dem Steigwinkel des Schiffs. */
public class ShipRenderer extends GeoEntityRenderer<ShipEntity> {
	public ShipRenderer(EntityRendererFactory.Context context) {
		super(context, new DefaultedEntityGeoModel<>(KingdomOmnitrix.id("ship/aphelion")));
		this.shadowRadius = 1.4f;
	}

	@Override
	protected void applyRotations(ShipEntity ship, MatrixStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
		super.applyRotations(ship, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
		float pitch = MathHelper.lerp(partialTick, ship.prevPitch, ship.getPitch());
		poseStack.translate(0.0, 0.6, 0.0);
		poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		poseStack.translate(0.0, -0.6, 0.0);
	}
}
