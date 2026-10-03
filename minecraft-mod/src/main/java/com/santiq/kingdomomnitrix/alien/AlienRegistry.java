package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

/**
 * Datengetriebene Alien-Registry. Wird beim Laden der Welt aus den Datenpaketen gelesen
 * und von Fabric automatisch an jeden Client synchronisiert.
 */
public final class AlienRegistry {
	public static final RegistryKey<Registry<AlienDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("alien"));

	private AlienRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, AlienDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> KingdomOmnitrix.LOGGER.info("{} Aliens aus Datenpaketen geladen: {}",
				sortedIds(server.getRegistryManager()).size(), sortedIds(server.getRegistryManager())));
	}

	public static Optional<Registry<AlienDefinition>> registry(DynamicRegistryManager manager) {
		return manager.getOptional(KEY);
	}

	public static Optional<AlienDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return registry(manager).flatMap(registry -> registry.getOrEmpty(id));
	}

	/** Alle Aliens, sortiert nach {@code sort_order} und dann nach ID. */
	public static List<Identifier> sortedIds(DynamicRegistryManager manager) {
		return registry(manager).map(registry -> registry.getIds().stream()
				.sorted(Comparator.<Identifier>comparingInt(id -> registry.getOrEmpty(id).map(AlienDefinition::sortOrder).orElse(0))
						.thenComparing(Identifier::toString))
				.toList()).orElse(List.of());
	}
}
