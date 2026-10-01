package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Merkt sich den Renderer-Kontext, damit die Alien-Koerper-Renderer gebaut werden koennen. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void kingdomomnitrix$captureContext(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
		AlienBodyRenderers.onRendererReload(ctx);
	}
}
