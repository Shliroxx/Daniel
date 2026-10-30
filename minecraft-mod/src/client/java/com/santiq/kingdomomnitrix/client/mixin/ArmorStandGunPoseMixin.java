package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.gear.GunPose;
import net.minecraft.client.render.entity.model.ArmorStandArmorEntityModel;
import net.minecraft.client.render.entity.model.ArmorStandEntityModel;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Schusswaffen auf Ruestungsstaendern: beide Arme im Anschlag wie beim Spieler (Armbrust-Haltung), statt die Waffe
 * senkrecht nach unten haengen zu lassen.
 */
@Mixin(ArmorStandEntityModel.class)
public abstract class ArmorStandGunPoseMixin extends ArmorStandArmorEntityModel {
	private ArmorStandGunPoseMixin() {
		super(null);
	}

	@Inject(method = "setAngles(Lnet/minecraft/entity/decoration/ArmorStandEntity;FFFFF)V", at = @At("TAIL"))
	private void kingdomomnitrix$gunPose(ArmorStandEntity stand, float limbAngle, float limbDistance, float age, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (GunPose.isGun(stand.getMainHandStack())) {
			CrossbowPosing.hold(rightArm, leftArm, head, true);
		}
	}
}
