package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.magic.MagicInput;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mausrad dreht durch die Zauber, solange die Magie-Taste gehalten wird. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void kingdomomnitrix$spellScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (MagicInput.onScroll(vertical)) {
			ci.cancel();
		}
	}
}
