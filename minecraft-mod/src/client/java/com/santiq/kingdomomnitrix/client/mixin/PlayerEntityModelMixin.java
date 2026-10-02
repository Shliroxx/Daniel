package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixRemote;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixWrist;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-Person: Hebt der Spieler das Omnitrix (Rad offen, Bestaetigen, Schlag), wandert der linke Arm vor die Brust,
 * Handgelenk nach oben — auch fuer andere Spieler sichtbar (Zustand kommt ueber den Server).
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void kingdomomnitrix$omnitrixArm(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity player) || OmnitrixWrist.isRenderingFirstPerson()) {
			return;
		}
		float raise = OmnitrixRemote.raise(player);
		if (raise <= 0.0f) {
			return;
		}
		@SuppressWarnings("unchecked")
		PlayerEntityModel<LivingEntity> model = (PlayerEntityModel<LivingEntity>) (Object) this;
		BipedEntityModel<LivingEntity> biped = model;
		biped.leftArm.pitch = MathHelper.lerp(raise, biped.leftArm.pitch, -1.45f);
		biped.leftArm.yaw = MathHelper.lerp(raise, biped.leftArm.yaw, 0.65f);
		biped.leftArm.roll = MathHelper.lerp(raise, biped.leftArm.roll, -0.25f);
		model.leftSleeve.copyTransform(biped.leftArm);
	}
}
