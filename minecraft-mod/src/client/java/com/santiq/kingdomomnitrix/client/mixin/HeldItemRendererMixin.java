package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixWrist;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ego-Sicht: linker Arm mit Omnitrix, solange das Alien-Rad offen ist (siehe {@link OmnitrixWrist}). */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At("TAIL"))
	private void kingdomomnitrix$omnitrixArm(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		OmnitrixWrist.renderFirstPerson(matrices, vertexConsumers, player, light);
	}
}
