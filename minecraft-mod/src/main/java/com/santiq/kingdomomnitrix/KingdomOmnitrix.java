package com.santiq.kingdomomnitrix;

import com.santiq.kingdomomnitrix.alien.Alien;
import com.santiq.kingdomomnitrix.command.HeroCommand;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModBlocks;
import com.santiq.kingdomomnitrix.registry.ModEffects;
import com.santiq.kingdomomnitrix.registry.ModEntities;
import com.santiq.kingdomomnitrix.registry.ModItemGroup;
import com.santiq.kingdomomnitrix.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
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
		ModEffects.register();
		ModEntities.register();
		ModBlocks.register();
		ModItems.register();
		ModItemGroup.register();
		HeroCommand.register();
		registerEvents();
		LOGGER.info("Kingdom Omnitrix geladen.");
	}

	private static void registerEvents() {
		// Alien-Resistenzen: Heatblast brennt nicht, Diamondhead prallt Geschosse ab, XLR8 faengt Stuerze ab.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Alien alien = Alien.activeOn(entity);
			if (alien == null) {
				return true;
			}
			return switch (alien) {
				case HEATBLAST -> !source.isIn(DamageTypeTags.IS_FIRE);
				case DIAMONDHEAD -> !source.isIn(DamageTypeTags.IS_PROJECTILE);
				case XLR8 -> !source.isIn(DamageTypeTags.IS_FALL);
				default -> true;
			};
		});

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
