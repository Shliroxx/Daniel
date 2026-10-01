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
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.networking.ModNetworking;
import com.santiq.kingdomomnitrix.registry.ModComponents;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItemGroup;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModScreenHandlers;
import com.santiq.kingdomomnitrix.gadget.GadgetManager;
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
		AlienRegistry.register();
		KeybladeRegistry.register();
		SpellRegistry.register();
		RiftRegistry.register();
		WeaponRegistry.register();
		MagicManager.register();
		AbilityRegistry.registerBuiltins();
		ModEntities.register();
		ModBlocks.register();
		ModItems.register();
		ModItemGroup.register();
		ModScreenHandlers.register();
		GadgetManager.register();
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
