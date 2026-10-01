package com.santiq.kingdomomnitrix.alien;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Laesst DNA-Proben fallen, wie es die {@code dna_sources} der Alien-Definitionen vorgeben. */
public final class DnaDrops {
	private DnaDrops() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(DnaDrops::onDeath);
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (entity.getWorld().isClient() || !(source.getAttacker() instanceof ServerPlayerEntity)) {
			return;
		}
		Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
		AlienRegistry.registry(entity.getWorld().getRegistryManager()).ifPresent(registry -> roll(entity, typeId, registry));
	}

	private static void roll(LivingEntity entity, Identifier typeId, Registry<AlienDefinition> registry) {
		for (Identifier alienId : registry.getIds()) {
			AlienDefinition alien = registry.get(alienId);
			if (alien == null) {
				continue;
			}
			for (AlienDefinition.DnaSource dna : alien.dnaSources()) {
				if (dna.entity().equals(typeId) && entity.getRandom().nextFloat() < dna.chance()) {
					entity.dropStack(DnaSampleItem.create(alienId));
				}
			}
		}
	}
}
