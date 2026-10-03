package com.santiq.kingdomomnitrix.client.boss;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.boss.NefariousEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Schadenszustaende: ab Phase 2 (≤ 60 % Leben) Risse in Kuppel und Panzer, ab Phase 3 (≤ 25 %) tiefe Risse und
 * Brandflecken. Die Overlays haben dieselben UVs wie die Haupttextur (tools/generate_boss_models.py).
 */
public class NefariousDamageLayer extends GeoRenderLayer<NefariousEntity> {
	private static final Identifier LIGHT = KingdomOmnitrix.id("textures/entity/boss/nefarious_mech_damage1.png");
	private static final Identifier HEAVY = KingdomOmnitrix.id("textures/entity/boss/nefarious_mech_damage2.png");

	public NefariousDamageLayer(GeoRenderer<NefariousEntity> renderer) {
		super(renderer);
	}

	/** 0 = heil, 1 = beschaedigt, 2 = schwer beschaedigt; Grenzen wie die Kampfphasen. */
	public static int stage(NefariousEntity entity) {
		float fraction = entity.getHealth() / entity.getMaxHealth();
		return fraction <= NefariousEntity.PHASE3_FRACTION ? 2 : fraction <= NefariousEntity.PHASE2_FRACTION ? 1 : 0;
	}

	@Override
	public void render(MatrixStack poseStack, NefariousEntity animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
			VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		int stage = stage(animatable);
		if (stage == 0 || animatable.isDead()) {
			return;
		}
		RenderLayer layer = RenderLayer.getEntityTranslucent(stage == 2 ? HEAVY : LIGHT);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, layer, bufferSource.getBuffer(layer), partialTick,
				packedLight, packedOverlay, 0xFFFFFFFF);
	}
}
