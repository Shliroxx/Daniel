package com.santiq.kingdomomnitrix.client;

import com.santiq.kingdomomnitrix.client.hud.HeroStatusHud;
import com.santiq.kingdomomnitrix.client.render.ShadowRenderer;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class KingdomOmnitrixClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SHADOW, ShadowRenderer::new);
		EntityRendererRegistry.register(ModEntities.HERO_PROJECTILE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.FUSION_GRENADE, FlyingItemEntityRenderer::new);
		HeroStatusHud.register();
	}
}
