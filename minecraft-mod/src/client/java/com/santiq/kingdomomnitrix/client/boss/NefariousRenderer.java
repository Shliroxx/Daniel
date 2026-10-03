package com.santiq.kingdomomnitrix.client.boss;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.boss.NefariousEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Dr. Nefarious mit GeckoLib. Durchscheinend gerendert, damit man durch die gruene Glaskuppel Zahnraeder und
 * Satellitenschuessel sieht; Augen, Kern, Laser, Raketenschacht und Tentakelspitzen leuchten (Glowmask-Textur).
 */
public class NefariousRenderer extends GeoEntityRenderer<NefariousEntity> {
	public NefariousRenderer(EntityRendererFactory.Context context) {
		super(context, new DefaultedEntityGeoModel<>(KingdomOmnitrix.id("boss/nefarious_mech")));
		this.shadowRadius = 1.4f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	public RenderLayer getRenderType(NefariousEntity animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource, float partialTick) {
		return RenderLayer.getEntityTranslucent(texture);
	}
}
