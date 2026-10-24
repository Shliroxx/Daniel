package com.santiq.kingdomomnitrix.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft stuft jede Welt mit Dimensionen aus Datenpaketen als „experimentell“ ein und warnt bei jedem Laden.
 * Die Mod bringt eigene Dimensionen (All, Traverse Town) mit; die Warnung waere dauerhaft und ohne Nutzen.
 * Gleiche Loesung wie verbreitete Hilfs-Mods: experimentell wird als stabil gemeldet (veraltet bleibt veraltet).
 */
@Mixin(LevelProperties.class)
public abstract class LevelPropertiesMixin {
	@Inject(method = "getLifecycle", at = @At("RETURN"), cancellable = true)
	private void kingdomomnitrix$stableLifecycle(CallbackInfoReturnable<Lifecycle> cir) {
		if (cir.getReturnValue() == Lifecycle.experimental()) {
			cir.setReturnValue(Lifecycle.stable());
		}
	}
}
