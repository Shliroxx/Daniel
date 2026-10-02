package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.render.alien.AlienArms;
import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Merkt sich den Renderer-Kontext fuer die Alien-Koerper und tauscht in der Ego-Sicht die Arm-Textur verwandelter
 * Spieler gegen die Alien-Arme aus.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void kingdomomnitrix$captureContext(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
		AlienBodyRenderers.onRendererReload(ctx);
	}

	@Redirect(method = "renderArm", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/util/SkinTextures;texture()Lnet/minecraft/util/Identifier;"))
	private Identifier kingdomomnitrix$alienArm(SkinTextures skin, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve) {
		return AlienArms.armTexture(player).orElseGet(skin::texture);
	}
}
