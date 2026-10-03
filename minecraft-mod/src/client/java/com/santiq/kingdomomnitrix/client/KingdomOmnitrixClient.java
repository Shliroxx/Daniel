package com.santiq.kingdomomnitrix.client;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.client.combat.ClientLockOn;
import com.santiq.kingdomomnitrix.client.combat.CombatAnimations;
import com.santiq.kingdomomnitrix.client.combat.CombatInput;
import com.santiq.kingdomomnitrix.client.hud.HeroStatusHud;
import com.santiq.kingdomomnitrix.client.hud.LockOnHud;
import com.santiq.kingdomomnitrix.client.hud.OmnitrixHud;
import com.santiq.kingdomomnitrix.client.input.ModKeyBindings;
import com.santiq.kingdomomnitrix.client.command.CommandMenu;
import com.santiq.kingdomomnitrix.client.hud.HudManager;
import com.santiq.kingdomomnitrix.client.menu.MenuTabs;
import com.santiq.kingdomomnitrix.client.space.ShipHud;
import com.santiq.kingdomomnitrix.client.party.PartyHud;
import com.santiq.kingdomomnitrix.client.ui.UiDraw;
import com.santiq.kingdomomnitrix.client.vfx.ModParticleFactories;
import com.santiq.kingdomomnitrix.client.vfx.ScreenEffects;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import com.santiq.kingdomomnitrix.client.magic.MagicInput;
import com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixController;
import com.santiq.kingdomomnitrix.networking.OpenOmnitrixPayload;
import com.santiq.kingdomomnitrix.networking.OpenTerminalPayload;
import com.santiq.kingdomomnitrix.client.weapon.WeaponHud;
import com.santiq.kingdomomnitrix.client.gadget.GadgetHud;
import com.santiq.kingdomomnitrix.client.gadget.GadgetInput;
import com.santiq.kingdomomnitrix.client.gadget.GadgetScreen;
import com.santiq.kingdomomnitrix.client.gadget.SwingshotRopes;
import com.santiq.kingdomomnitrix.client.quest.QuestBookScreen;
import com.santiq.kingdomomnitrix.client.npc.NpcDialogScreen;
import com.santiq.kingdomomnitrix.client.arena.ArenaScreen;
import com.santiq.kingdomomnitrix.networking.OpenArenaPayload;
import com.santiq.kingdomomnitrix.client.npc.NpcRenderer;
import com.santiq.kingdomomnitrix.client.space.ShipClient;
import com.santiq.kingdomomnitrix.client.space.ShipRenderer;
import com.santiq.kingdomomnitrix.client.boss.NefariousRenderer;
import com.santiq.kingdomomnitrix.client.render.alien.AlienBodyRenderers;
import com.santiq.kingdomomnitrix.client.vfx.AlienAmbientVfx;
import com.santiq.kingdomomnitrix.client.space.SpaceDimensionEffects;
import com.santiq.kingdomomnitrix.client.space.SpaceRifts;
import com.santiq.kingdomomnitrix.client.space.SpaceSkyRenderer;
import com.santiq.kingdomomnitrix.space.SpaceTravel;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import com.santiq.kingdomomnitrix.networking.OpenNpcDialogPayload;
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
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class KingdomOmnitrixClient implements ClientModInitializer {
	/** GeckoLib-Renderer: Modell, Animation und Textur unter {geo,animations,textures}/entity/heartless/<name>;
	 *  die gelben Augen leuchten im Dunkeln (<name>_glowmask.png). */
	private static <T extends HeartlessEntity> void registerHeartless(EntityType<T> type, String name) {
		EntityRendererRegistry.register(type, context -> {
			GeoEntityRenderer<T> renderer = new GeoEntityRenderer<>(context,
					new DefaultedEntityGeoModel<T>(KingdomOmnitrix.id("heartless/" + name), true));
			renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
			return renderer;
		});
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
		EntityRendererRegistry.register(ModEntities.NPC, NpcRenderer::new);
		EntityRendererRegistry.register(ModEntities.SHIP, ShipRenderer::new);
		EntityRendererRegistry.register(ModEntities.NEFARIOUS, NefariousRenderer::new);
		DimensionRenderingRegistry.registerDimensionEffects(KingdomOmnitrix.id("space"), new SpaceDimensionEffects());
		DimensionRenderingRegistry.registerSkyRenderer(SpaceTravel.SPACE, new SpaceSkyRenderer());
		DimensionRenderingRegistry.registerCloudRenderer(SpaceTravel.SPACE, context -> {
		});
		EntityRendererRegistry.register(ModEntities.WRENCH_PROJECTILE, context -> new FlyingItemEntityRenderer<>(context, 1.5f, false));

		ModKeyBindings.register();
		// HUD: alle Anzeigen ueber den HudManager (verschieb- und skalierbar, Reihenfolge = Zeichenreihenfolge)
		HudManager.register(HeroStatusHud.INSTANCE);
		HudManager.register(CommandMenu.INSTANCE);
		HudManager.register(WeaponHud.INSTANCE);
		HudManager.register(OmnitrixHud.INSTANCE);
		com.santiq.kingdomomnitrix.client.alien.AlienMeters.register();
		HudManager.register(GadgetHud.INSTANCE);
		HudManager.register(ShipHud.INSTANCE);
		HudManager.register(PartyHud.INSTANCE);
		PartyHud.register();
		HudManager.registerLayer();
		CommandMenu.register();
		LockOnHud.register();
		MenuTabs.register();
		ModParticleFactories.register();
		ScreenEffects.register();
		AlienBodyRenderers.register();
		com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixWrist.register();
		com.santiq.kingdomomnitrix.client.gadget.HeliPackFeatureRenderer.register();
		com.santiq.kingdomomnitrix.client.combat.DamageNumbers.register();
		com.santiq.kingdomomnitrix.client.render.omnitrix.OmnitrixSteam.register();
		AlienAmbientVfx.register();
		com.santiq.kingdomomnitrix.client.vfx.TransformBubble.register();
		com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixClientState.register();
		com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixFeedback.register();
		com.santiq.kingdomomnitrix.client.omnitrix.AlienUnlockToast.register();
		com.santiq.kingdomomnitrix.client.omnitrix.OmnitrixHolo.register();
		com.santiq.kingdomomnitrix.client.omnitrix.MasteryHolo.register();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public net.minecraft.util.Identifier getFabricId() {
				return KingdomOmnitrix.id("ui_icons");
			}

			@Override
			public void reload(ResourceManager manager) {
				UiDraw.clearCache();
			}
		});
		HandledScreens.register(ModScreenHandlers.GADGET_BELT, GadgetScreen::new);
		ModelPredicateProviderRegistry.register(ModItems.HELI_PACK, KingdomOmnitrix.id("jet"),
				(stack, world, entity, seed) -> HeliPackItem.isJet(stack) ? 1.0f : 0.0f);
		CombatAnimations.register();

		ClientTickEvents.END_CLIENT_TICK.register(CombatInput::tick);
		ClientTickEvents.END_CLIENT_TICK.register(MagicInput::tick);
		ClientTickEvents.END_CLIENT_TICK.register(GadgetInput::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ShipClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(SpaceRifts::render);
		WorldRenderEvents.AFTER_ENTITIES.register(SwingshotRopes::render);
		WorldRenderEvents.AFTER_ENTITIES.register(com.santiq.kingdomomnitrix.client.vfx.TransformBubble::render);
		WorldRenderEvents.START.register(context -> ClientLockOn.updateCamera(MinecraftClient.getInstance()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientLockOn.reset();
			CombatInput.reset();
			MagicInput.reset();
			GadgetInput.reset();
			SwingshotRopes.reset();
			ShipClient.reset();
			CommandMenu.INSTANCE.reset();
			ScreenEffects.reset();
			PartyHud.reset();
		});

		ClientPlayNetworking.registerGlobalReceiver(OpenArenaPayload.ID, (payload, context) ->
				context.client().setScreen(new ArenaScreen(payload.terminal(), payload.running())));
		ClientPlayNetworking.registerGlobalReceiver(OpenNpcDialogPayload.ID, (payload, context) ->
				NpcDialogScreen.open(context.client(), payload.entityId(), payload.npc()));
		ClientPlayNetworking.registerGlobalReceiver(OpenQuestBookPayload.ID, (payload, context) -> QuestBookScreen.open(context.client()));
		ClientPlayNetworking.registerGlobalReceiver(SwingshotStatePayload.ID, (payload, context) -> SwingshotRopes.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenTerminalPayload.ID, (payload, context) ->
				context.client().setScreen(new WeaponTerminalScreen(payload.pos())));
		ClientPlayNetworking.registerGlobalReceiver(com.santiq.kingdomomnitrix.networking.OpenCalibrationPayload.ID, (payload, context) ->
				context.client().setScreen(new com.santiq.kingdomomnitrix.client.screen.OmnitrixCalibrationScreen(payload.pos())));
		ClientPlayNetworking.registerGlobalReceiver(OpenOmnitrixPayload.ID, (payload, context) -> {
			if (context.client().currentScreen == null) {
				OmnitrixController.open(context.client());
			}
		});
	}
}
