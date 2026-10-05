package com.santiq.kingdomomnitrix.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Liest, ob ein Lebewesen gerade die Sprungtaste haelt (Steigflug des Raumschiffs). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumping")
	boolean kingdomomnitrix$isJumping();
}
