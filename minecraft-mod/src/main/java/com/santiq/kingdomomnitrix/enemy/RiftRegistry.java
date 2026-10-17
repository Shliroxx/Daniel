package com.santiq.kingdomomnitrix.enemy;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** Datengetriebene Riss-Definitionen (nur Server). */
public final class RiftRegistry {
	public static final RegistryKey<Registry<RiftDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("rift"));

	private RiftRegistry() {
	}

	public static void register() {
		DynamicRegistries.register(KEY, RiftDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getRegistryManager().getOptional(KEY).ifPresent(registry ->
				KingdomOmnitrix.LOGGER.info("{} Riss-Definitionen geladen: {}", registry.size(), registry.getIds())));
	}

	public static Optional<RiftDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	/** Gewichtete Zufallswahl unter allen Rissen, deren Mindeststufe erreicht ist. */
	public static Optional<Identifier> pick(DynamicRegistryManager manager, int playerLevel, Random random) {
		Optional<Registry<RiftDefinition>> registry = manager.getOptional(KEY);
		if (registry.isEmpty()) {
			return Optional.empty();
		}
		List<Map.Entry<Identifier, RiftDefinition>> candidates = registry.get().getIds().stream()
				.map(id -> Map.entry(id, registry.get().get(id)))
				.filter(entry -> entry.getValue() != null && entry.getValue().minLevel() <= playerLevel)
				.toList();
		int total = candidates.stream().mapToInt(entry -> entry.getValue().weight()).sum();
		if (total <= 0) {
			return Optional.empty();
		}
		int roll = random.nextInt(total);
		for (Map.Entry<Identifier, RiftDefinition> entry : candidates) {
			roll -= entry.getValue().weight();
			if (roll < 0) {
				return Optional.of(entry.getKey());
			}
		}
		return Optional.empty();
	}
}
