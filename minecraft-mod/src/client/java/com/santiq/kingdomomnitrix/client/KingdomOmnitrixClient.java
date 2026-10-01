package com.santiq.kingdomomnitrix.client;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.combat.ClientLockOn;
import com.santiq.kingdomomnitrix.client.combat.CombatAnimations;
import com.santiq.kingdomomnitrix.client.combat.CombatInput;
import com.santiq.kingdomomnitrix.client.hud.HeroStatusHud;
import com.santiq.kingdomomnitrix.client.hud.LockOnHud;
import com.santiq.kingdomomnitrix.client.hud.OmnitrixHud;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.magic.MagicHud;
import com.santiq.kingdomomnitrix.client.magic.MagicInput;
import com.santiq.kingdomomnitrix.client.screen.OmnitrixWheelScreen;
import com.santiq.kingdomomnitrix.networking.OpenOmnitrixPayload;
import com.santiq.kingdomomnitrix.networking.OpenTerminalPayload;
import com.santiq.kingdomomnitrix.client.weapon.WeaponHud;
import com.santiq.kingdomomnitrix.client.gadget.GadgetHud;
import com.santiq.kingdomomnitrix.client.gadget.GadgetInput;
import com.santiq.kingdomomnitrix.client.gadget.GadgetScreen;
import com.santiq.kingdomomnitrix.client.gadget.SwingshotRopes;
import com.santiq.kingdomomnitrix.client.quest.QuestBookScreen;
import com.santiq.kingdomomnitrix.networking.OpenQuestBookPayload;
import com.santiq.kingdomomnitrix.gadget.HeliPackItem;
import com.santiq.kingdomomnitrix.networking.SwingshotStatePayload;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModScreenHandlers;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import com.santiq.kingdomomnitrix.client.weapon.WeaponTerminalScreen;
import com.santiq.kingdomomnitrix.enemy.HeartlessEntity;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.entity.EntityType;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KingdomOmnitrixClient implements ClientModInitializer {
	/** GeckoLib-Renderer: Modell, Animation und Textur unter {geo,animations,textures}/entity/heartless/<name>. */
	private static <T extends HeartlessEntity> void registerHeartless(EntityType<T> type, String name) {
		EntityRendererRegistry.register(type, context -> new GeoEntityRenderer<>(context,
				new DefaultedEntityGeoModel<T>(KingdomOmnitrix.id("heartless/" + name), true)));
	}

	@Override
	public void onInitializeClient() {
		registerHeartless(ModEntities.SHADOW, "shadow");
		registerHeartless(ModEntities.SOLDIER, "soldier");
		registerHeartless(ModEntities.LARGE_BODY, "large_body");
		registerHeartless(ModEntities.AIR_SOLDIER, "air_soldier");
		registerHeartless(ModEntities.DARKBALL, "darkball");
		// Der Riss besteht nur aus Partikeln (serverseitig) und der Bossleiste.
		EntityRendererRegistry.register(ModEntities.DARKNESS_RIFT, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.HERO_PROJECTILE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.FUSION_GRENADE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.WRENCH_PROJECTILE, context -> new FlyingItemEntityRenderer<>(context, 1.5f, false));

		ModKeyBindings.register();
		HeroStatusHud.register();
		OmnitrixHud.register();
		LockOnHud.register();
		MagicHud.register();
		WeaponHud.register();
		GadgetHud.register();
		HandledScreens.register(ModScreenHandlers.GADGET_BELT, GadgetScreen::new);
		ModelPredicateProviderRegistry.register(ModItems.HELI_PACK, KingdomOmnitrix.id("jet"),
				(stack, world, entity, seed) -> HeliPackItem.isJet(stack) ? 1.0f : 0.0f);
		CombatAnimations.register();

		ClientTickEvents.END_CLIENT_TICK.register(CombatInput::tick);
		ClientTickEvents.END_CLIENT_TICK.register(MagicInput::tick);
		ClientTickEvents.END_CLIENT_TICK.register(GadgetInput::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(SwingshotRopes::render);
		WorldRenderEvents.START.register(context -> ClientLockOn.updateCamera(MinecraftClient.getInstance()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientLockOn.reset();
			CombatInput.reset();
			MagicInput.reset();
			GadgetInput.reset();
			SwingshotRopes.reset();
		});

		ClientPlayNetworking.registerGlobalReceiver(OpenQuestBookPayload.ID, (payload, context) -> QuestBookScreen.open(context.client()));
		ClientPlayNetworking.registerGlobalReceiver(SwingshotStatePayload.ID, (payload, context) -> SwingshotRopes.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenTerminalPayload.ID, (payload, context) ->
				context.client().setScreen(new WeaponTerminalScreen(payload.pos())));
		ClientPlayNetworking.registerGlobalReceiver(OpenOmnitrixPayload.ID, (payload, context) -> {
			if (context.client().currentScreen == null) {
				OmnitrixWheelScreen.openFromItem(context.client());
			}
		});
	}
}
