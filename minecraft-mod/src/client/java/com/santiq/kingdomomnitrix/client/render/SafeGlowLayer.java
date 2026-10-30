package com.santiq.kingdomomnitrix.client.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Leuchtschicht wie {@link AutoGlowingGeoLayer}, die Modelle ohne _glowmask-Textur ueberspringt. GeckoLib bricht sonst
 * mit „Invalid glow layer texture“ ab, wenn ein Modell nichts Leuchtendes hat (z. B. ein NPC ohne Leuchtteile).
 */
public class SafeGlowLayer<T extends GeoAnimatable> extends AutoGlowingGeoLayer<T> {
	private static final Map<Identifier, Boolean> PRESENT = new HashMap<>();

	public SafeGlowLayer(GeoRenderer<T> renderer) {
		super(renderer);
	}

	/** Vergisst die gemerkten Ergebnisse (nach einem Ressourcen-Neuladen). */
	public static void clearCache() {
		PRESENT.clear();
	}

	@Override
	public void render(MatrixStack poseStack, T animatable, BakedGeoModel bakedModel, RenderLayer renderType, VertexConsumerProvider bufferSource,
			VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		Identifier texture = getTextureResource(animatable);
		Identifier mask = texture.withPath(path -> path.replace(".png", "_glowmask.png"));
		boolean present = PRESENT.computeIfAbsent(mask, id -> MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent());
		if (present) {
			super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
		}
	}
}
