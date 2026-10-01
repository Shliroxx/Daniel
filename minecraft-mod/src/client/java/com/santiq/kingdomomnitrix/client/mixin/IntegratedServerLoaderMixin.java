package com.santiq.kingdomomnitrix.client.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.server.integrated.IntegratedServerLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Gegenstueck beim Erstellen einer neuen Welt: keine Experimentell-Warnung wegen der Mod-Dimensionen. */
@Mixin(IntegratedServerLoader.class)
public abstract class IntegratedServerLoaderMixin {
	@ModifyVariable(method = "tryLoad", at = @At("HEAD"), argsOnly = true)
	private static Lifecycle kingdomomnitrix$stableLifecycle(Lifecycle lifecycle) {
		return lifecycle == Lifecycle.experimental() ? Lifecycle.stable() : lifecycle;
	}
}
