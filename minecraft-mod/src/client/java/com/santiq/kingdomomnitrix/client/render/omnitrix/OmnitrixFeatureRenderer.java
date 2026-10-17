package com.santiq.kingdomomnitrix.client.render.omnitrix;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Third-Person: Omnitrix am linken Handgelenk jedes Spielers, der eins traegt (nicht verwandelt). */
public class OmnitrixFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public OmnitrixFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (player.isInvisible() || !OmnitrixWrist.wears(player)) {
			return;
		}
		OmnitrixWrist.renderOnArm(matrices, vertexConsumers, light, getContextModel(), player,
				player == MinecraftClient.getInstance().player);
	}
}
