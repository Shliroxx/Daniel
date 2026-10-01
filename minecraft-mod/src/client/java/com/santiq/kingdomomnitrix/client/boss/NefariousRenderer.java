package com.santiq.kingdomomnitrix.client.boss;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.boss.NefariousEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Nefarious-Mech mit GeckoLib; Kern, Augen und Kanone leuchten im Dunkeln (Glowmask-Textur). */
public class NefariousRenderer extends GeoEntityRenderer<NefariousEntity> {
	public NefariousRenderer(EntityRendererFactory.Context context) {
		super(context, new DefaultedEntityGeoModel<>(KingdomOmnitrix.id("boss/nefarious_mech")));
		this.shadowRadius = 1.6f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}
}
