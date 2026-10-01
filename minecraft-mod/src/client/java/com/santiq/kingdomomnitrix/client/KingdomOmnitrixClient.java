package com.santiq.kingdomomnitrix.client;

import com.santiq.kingdomomnitrix.client.combat.ClientLockOn;
import com.santiq.kingdomomnitrix.client.combat.CombatAnimations;
import com.santiq.kingdomomnitrix.client.combat.CombatInput;
import com.santiq.kingdomomnitrix.client.hud.HeroStatusHud;
import com.santiq.kingdomomnitrix.client.hud.LockOnHud;
import com.santiq.kingdomomnitrix.client.hud.OmnitrixHud;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.render.ShadowRenderer;
import com.santiq.kingdomomnitrix.client.screen.OmnitrixWheelScreen;
import com.santiq.kingdomomnitrix.networking.OpenOmnitrixPayload;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class KingdomOmnitrixClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SHADOW, ShadowRenderer::new);
		EntityRendererRegistry.register(ModEntities.HERO_PROJECTILE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.FUSION_GRENADE, FlyingItemEntityRenderer::new);

		ModKeyBindings.register();
		HeroStatusHud.register();
		OmnitrixHud.register();
		LockOnHud.register();
		CombatAnimations.register();

		ClientTickEvents.END_CLIENT_TICK.register(CombatInput::tick);
		WorldRenderEvents.START.register(context -> ClientLockOn.updateCamera(MinecraftClient.getInstance()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientLockOn.reset();
			CombatInput.reset();
		});

		ClientPlayNetworking.registerGlobalReceiver(OpenOmnitrixPayload.ID, (payload, context) -> {
			if (context.client().currentScreen == null) {
				OmnitrixWheelScreen.openFromItem(context.client());
			}
		});
	}
}
