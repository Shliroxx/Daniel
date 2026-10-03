package com.santiq.kingdomomnitrix.npc;

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

/** Datengetriebene NPCs, an Clients synchronisiert (Modell, Hitbox, Dialog). */
public final class NpcRegistry {
	public static final RegistryKey<Registry<NpcDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("npc"));

	private NpcRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, NpcDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> KingdomOmnitrix.LOGGER.info("{} NPCs aus Datenpaketen geladen: {}",
				sortedIds(server.getRegistryManager()).size(), sortedIds(server.getRegistryManager())));
	}

	public static Optional<NpcDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	public static List<Identifier> sortedIds(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.sorted(Comparator.comparing(Identifier::toString)).toList()).orElse(List.of());
	}
}
