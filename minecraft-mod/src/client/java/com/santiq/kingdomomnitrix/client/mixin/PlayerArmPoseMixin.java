package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.gear.GunPose;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Schusswaffen in der Hand: beide Arme im Anschlag wie bei einer geladenen Armbrust (siehe {@link GunPose}). */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerArmPoseMixin {
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private static void kingdomomnitrix$gunPose(AbstractClientPlayerEntity player, Hand hand,
			CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
		if (hand == Hand.MAIN_HAND && !player.handSwinging && GunPose.isGun(player.getStackInHand(hand))) {
			cir.setReturnValue(BipedEntityModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
