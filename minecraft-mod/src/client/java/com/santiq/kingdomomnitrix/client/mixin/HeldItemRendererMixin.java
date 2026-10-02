package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixController;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixWrist;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ego-Sicht: linker Arm mit Omnitrix und Auswahlscheibe, solange das Omnitrix gehoben ist (siehe {@link OmnitrixWrist}). */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	/** Solange das Omnitrix gehoben ist, gehoert das Bild dem linken Arm: normale Hand-/Item-Darstellung aus. */
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void kingdomomnitrix$hideHands(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
			ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (OmnitrixController.raise() > 0.3f) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At("TAIL"))
	private void kingdomomnitrix$omnitrixArm(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		OmnitrixWrist.renderFirstPerson(matrices, vertexConsumers, player, light);
	}
}
