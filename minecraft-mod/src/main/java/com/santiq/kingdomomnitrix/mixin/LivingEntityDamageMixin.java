package com.santiq.kingdomomnitrix.mixin;

import com.santiq.kingdomomnitrix.ability.passive.PassiveHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Passive Alien-Kraefte veraendern Schaden nach Ruestung und Verzauberungen (vor Absorption und Lebenspunkten). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
	private void kingdomomnitrix$passives(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		float modified = PassiveHooks.modifyDamage((LivingEntity) (Object) this, source, cir.getReturnValueF());
		if (modified != cir.getReturnValueF()) {
			cir.setReturnValue(modified);
		}
	}
}
