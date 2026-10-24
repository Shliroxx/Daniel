package com.santiq.kingdomomnitrix.client.render.alien;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Leucht-Aura am Alien-Koerper ({@link MeterAura}): der Koerper wird je Huelle noch einmal etwas groesser mit
 * wanderndem Energie-Muster gezeichnet, in voller Helligkeit (auch nachts). Fuer alle Spieler sichtbar.
 */
public class MeterAuraLayer extends GeoRenderLayer<AlienBodyAnimatable> {
	private final GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> owner;

	public MeterAuraLayer(GeoReplacedEntityRenderer<AbstractClientPlayerEntity, AlienBodyAnimatable> owner) {
		super(owner);
		this.owner = owner;
	}

	@Override
	public void render(MatrixStack poseStack, AlienBodyAnimatable animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
			VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		if (!(owner.getCurrentEntity() instanceof AbstractClientPlayerEntity player)) {
			return;
		}
		// Ich-Perspektive: die Huelle laege um die Kamera — dort zeigen die Arme die Aura (PlayerEntityRendererMixin)
		MinecraftClient client = MinecraftClient.getInstance();
		if (player == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) {
			return;
		}
		for (MeterAura.Shell shell : MeterAura.shells(player, partialTick)) {
			poseStack.push();
			// um die Koerpermitte vergroessern, nicht um die Fuesse
			poseStack.translate(0.0f, 1.0f, 0.0f);
			poseStack.scale(shell.scale(), shell.scale(), shell.scale());
			poseStack.translate(0.0f, -1.0f, 0.0f);
			getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, shell.layer(), bufferSource.getBuffer(shell.layer()),
					partialTick, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, shell.color());
			poseStack.pop();
		}
	}
}
