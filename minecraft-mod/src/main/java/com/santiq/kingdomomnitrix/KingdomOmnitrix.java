package com.santiq.kingdomomnitrix;

import com.santiq.kingdomomnitrix.ability.AbilityRegistry;
import com.santiq.kingdomomnitrix.alien.AlienRegistry;
import com.santiq.kingdomomnitrix.alien.DnaDrops;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.combat.CombatManager;
import com.santiq.kingdomomnitrix.command.HeroCommand;
import com.santiq.kingdomomnitrix.enemy.RiftRegistry;
import com.santiq.kingdomomnitrix.enemy.RiftSpawner;
import com.santiq.kingdomomnitrix.keyblade.KeybladeRegistry;
import com.santiq.kingdomomnitrix.weapon.WeaponRegistry;
import com.santiq.kingdomomnitrix.magic.MagicManager;
import com.santiq.kingdomomnitrix.magic.SpellRegistry;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.progression.AlienMasteryManager;
import com.santiq.kingdomomnitrix.progression.ExperienceSources;
import com.santiq.kingdomomnitrix.progression.HeroAbilityRegistry;
import com.santiq.kingdomomnitrix.progression.ProgressionManager;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.networking.ModNetworking;
import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModParticles;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItemGroup;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModScreenHandlers;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
import com.santiq.kingdomomnitrix.quest.QuestManager;
import com.santiq.kingdomomnitrix.quest.QuestRegistry;
import com.santiq.kingdomomnitrix.npc.NpcRegistry;
import com.santiq.kingdomomnitrix.registry.ModFeatures;
import com.santiq.kingdomomnitrix.registry.ModBlockEntities;
import com.santiq.kingdomomnitrix.arena.ArenaManager;
import com.santiq.kingdomomnitrix.arena.ArenaRegistry;
import com.santiq.kingdomomnitrix.world.TraverseTown;
import com.santiq.kingdomomnitrix.space.SpaceRouteRegistry;
import com.santiq.kingdomomnitrix.space.SpaceTravel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KingdomOmnitrix — Crossover aus Kingdom Hearts, Ben 10 und Ratchet & Clank.
 */
public class KingdomOmnitrix implements ModInitializer {
	public static final String MOD_ID = "kingdomomnitrix";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		HeroDataAccess.register();
		ModComponents.register();
		ModParticles.register();
		AlienRegistry.register();
		KeybladeRegistry.register();
		SpellRegistry.register();
		RiftRegistry.register();
		WeaponRegistry.register();
		QuestRegistry.register();
		NpcRegistry.register();
		SpaceRouteRegistry.register();
		ArenaRegistry.register();
		HeroAbilityRegistry.register();
		MagicManager.register();
		AbilityRegistry.registerBuiltins();
		ModEntities.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModItemGroup.register();
		ModScreenHandlers.register();
		GadgetManager.register();
		QuestManager.register();
		ProgressionManager.register();
		AlienMasteryManager.register();
		ExperienceSources.register();
		ModFeatures.register();
		SpaceTravel.register();
		ArenaManager.register();
		TraverseTown.register();
		TransformationManager.register();
		DnaDrops.register();
		CombatManager.register();
		RiftSpawner.register();
		ModNetworking.register();
		HeroCommand.register();
		registerEvents();
		LOGGER.info("Kingdom Omnitrix geladen.");
	}

	private static void registerEvents() {
		// Ratchet & Clank: Jedes von einem Spieler besiegte Monster laesst Bolts fallen.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.getWorld().isClient() || !(entity instanceof Monster)) {
				return;
			}
			if (!(source.getAttacker() instanceof PlayerEntity)) {
				return;
			}
			int count = 1 + entity.getRandom().nextInt(4);
			entity.dropStack(new ItemStack(ModItems.BOLT, count));
		});
	}
}
