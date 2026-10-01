package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.combat.ClientLockOn;
import com.santiq.kingdomomnitrix.client.combat.CombatInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mit einer Combo-Waffe uebernimmt {@link CombatInput} den Linksklick: kein Vanilla-Schlag, kein Abbauen.
 * Das Lock-On-Ziel bekommt die Leuchtumrandung.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void kingdomomnitrix$replaceAttack(CallbackInfoReturnable<Boolean> cir) {
		if (CombatInput.holdsComboWeapon((MinecraftClient) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void kingdomomnitrix$noBlockBreaking(boolean breaking, CallbackInfo ci) {
		if (CombatInput.holdsComboWeapon((MinecraftClient) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
	private void kingdomomnitrix$lockOnOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ClientLockOn.isTarget(entity)) {
			cir.setReturnValue(true);
		}
	}
}
