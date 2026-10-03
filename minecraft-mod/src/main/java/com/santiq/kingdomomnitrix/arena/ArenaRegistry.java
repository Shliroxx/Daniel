package com.santiq.kingdomomnitrix.arena;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

/** Arena-Herausforderungen, an Clients synchronisiert (Auswahl am Arena-Terminal). */
public final class ArenaRegistry {
	public static final RegistryKey<Registry<ArenaChallenge>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("arena_challenge"));

	private ArenaRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, ArenaChallenge.CODEC);
	}

	public static Optional<ArenaChallenge> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	public static List<Identifier> sortedIds(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.sorted(Comparator.<Identifier>comparingInt(id -> registry.getOrEmpty(id).map(ArenaChallenge::sortOrder).orElse(0))
						.thenComparing(Identifier::toString))
				.toList()).orElse(List.of());
	}
}
