package com.santiq.kingdomomnitrix.client.mixin;

import com.santiq.kingdomomnitrix.client.render.alien.AlienArms;
import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import com.santiq.kingdomomnitrix.client.render.alien.MeterAura;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
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
 * Spieler gegen die Alien-Arme aus; zeichnet dort auch die Aura der Alien-Anzeige.
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

	/** Aura an den Ego-Armen: dieselben Huellen wie am Koerper, nur ueber die Arm-Teile gezeichnet. */
	@Inject(method = "renderArm", at = @At("TAIL"))
	private void kingdomomnitrix$auraArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		List<MeterAura.Shell> shells = MeterAura.shells(player, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false));
		if (shells.isEmpty()) {
			return;
		}
		float x = arm.xScale;
		float y = arm.yScale;
		float z = arm.zScale;
		try {
			for (MeterAura.Shell shell : shells) {
				// Arme sind schmal: die Huelle etwas staerker aufblasen als am Koerper
				float grow = 1.0f + (shell.scale() - 1.0f) * 1.6f;
				arm.xScale = x * grow;
				arm.yScale = y * (1.0f + (grow - 1.0f) * 0.3f);
				arm.zScale = z * grow;
				arm.render(matrices, vertexConsumers.getBuffer(shell.layer()), LightmapTextureManager.MAX_LIGHT_COORDINATE,
						OverlayTexture.DEFAULT_UV, shell.color());
			}
		} finally {
			arm.xScale = x;
			arm.yScale = y;
			arm.zScale = z;
		}
	}
}
