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

/** Planeten der Galaxiekarte, an Clients synchronisiert (die Karte zeichnet sie). */
public final class PlanetRegistry {
	public static final RegistryKey<Registry<Planet>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("planet"));

	private PlanetRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, Planet.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<Map.Entry<Identifier, Planet>> planets = all(server.getRegistryManager());
			long missing = planets.stream().filter(e -> server.getWorld(e.getValue().worldKey()) == null).count();
			KingdomOmnitrix.LOGGER.info("{} Planeten geladen ({} ohne Welt)", planets.size(), missing);
		});
	}

	/** Alle Planeten, nach {@code order} und Name sortiert. */
	public static List<Map.Entry<Identifier, Planet>> all(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.<Map.Entry<Identifier, Planet>>map(id -> Map.entry(id, registry.get(id)))
				.sorted(Comparator.<Map.Entry<Identifier, Planet>>comparingInt(e -> e.getValue().order())
						.thenComparing(e -> e.getKey().toString()))
				.toList()).orElse(List.of());
	}

	public static Optional<Planet> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> Optional.ofNullable(registry.get(id)));
	}

	/** Der Planet, zu dem eine Welt gehoert (fuer „Du bist hier“ auf der Karte). */
	public static Optional<Identifier> ofWorld(DynamicRegistryManager manager, RegistryKey<World> world) {
		return all(manager).stream().filter(e -> e.getValue().worldKey().equals(world)).map(Map.Entry::getKey).findFirst();
	}
}
