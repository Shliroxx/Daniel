package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Kurzer FOV-Impuls bei Omnitrix-Rueckmeldungen (nur Welt-Sicht, nicht die Hand). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void kingdomomnitrix$omnitrixFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		float multiplier = OmnitrixFeedback.fovMultiplier();
		if (changingFov && multiplier != 1.0f) {
			cir.setReturnValue(cir.getReturnValueD() * multiplier);
		}
	}
}
