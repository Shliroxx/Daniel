package com.santiq.kingdomomnitrix.space;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Weltraumrisse, an Clients synchronisiert (Darstellung im All und Navigation im Schiffs-HUD). */
public final class SpaceRouteRegistry {
	public static final RegistryKey<Registry<SpaceRoute>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("space_route"));

	private SpaceRouteRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, SpaceRoute.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			for (Map.Entry<Identifier, SpaceRoute> entry : all(server.getRegistryManager())) {
				if (server.getWorld(entry.getValue().destinationKey()) == null) {
					KingdomOmnitrix.LOGGER.warn("Weltraumriss {} fuehrt in unbekannte Dimension {}", entry.getKey(), entry.getValue().destination());
				}
			}
			KingdomOmnitrix.LOGGER.info("{} Weltraumrisse geladen", all(server.getRegistryManager()).size());
		});
	}

	public static List<Map.Entry<Identifier, SpaceRoute>> all(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.sorted(Comparator.comparing(Identifier::toString))
				.<Map.Entry<Identifier, SpaceRoute>>map(id -> Map.entry(id, registry.get(id)))
				.toList()).orElse(List.of());
	}

	/** Der Riss, der in eine bestimmte Welt fuehrt (fuer den Rueckweg ins All). */
	public static Optional<Map.Entry<Identifier, SpaceRoute>> toWorld(DynamicRegistryManager manager, RegistryKey<World> world) {
		return all(manager).stream().filter(entry -> entry.getValue().destinationKey().equals(world)).findFirst();
	}
}
