package com.daniel.heroverse.client;

import com.daniel.heroverse.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class HeroverseClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SHADOW, ShadowRenderer::new);
		EntityRendererRegistry.register(ModEntities.HERO_PROJECTILE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.FUSION_GRENADE, FlyingItemEntityRenderer::new);
	}
}
