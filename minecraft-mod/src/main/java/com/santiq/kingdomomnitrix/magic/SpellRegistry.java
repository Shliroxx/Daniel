package com.santiq.kingdomomnitrix.magic;

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

/** Datengetriebene Zauber, synchronisiert an alle Clients. */
public final class SpellRegistry {
	public static final RegistryKey<Registry<SpellDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("spell"));

	private SpellRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, SpellDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				KingdomOmnitrix.LOGGER.info("{} Zauber aus Datenpaketen geladen: {}", sortedIds(server.getRegistryManager()).size(),
						sortedIds(server.getRegistryManager())));
	}

	public static Optional<SpellDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	public static List<Identifier> sortedIds(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.sorted(Comparator.<Identifier>comparingInt(id -> registry.getOrEmpty(id).map(SpellDefinition::sortOrder).orElse(0))
						.thenComparing(Identifier::toString))
				.toList()).orElse(List.of());
	}
}
