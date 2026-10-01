package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ersetzt das Spielermodell verwandelter Spieler durch den Alien-Koerper. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void kingdomomnitrix$renderAlienBody(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (entity instanceof AbstractClientPlayerEntity player
				&& AlienBodyRenderers.render(player, yaw, tickDelta, matrices, vertexConsumers, light)) {
			ci.cancel();
		}
	}
}
