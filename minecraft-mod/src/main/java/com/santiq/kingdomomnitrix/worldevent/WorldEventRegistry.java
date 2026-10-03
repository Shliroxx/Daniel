package com.santiq.kingdomomnitrix.worldevent;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Welt-Ereignisse aus Datenpaketen (nur Server: Clients kennen sie erst, wenn sie stattfinden). */
public final class WorldEventRegistry {
	public static final RegistryKey<Registry<WorldEventDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("world_event"));

	private WorldEventRegistry() {
	}

	public static void register() {
		DynamicRegistries.register(KEY, WorldEventDefinition.CODEC);
	}

	public static Optional<WorldEventDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	public static List<Identifier> ids(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream().sorted().toList()).orElse(List.of());
	}

	/** Gewichtete Zufallswahl unter den Ereignissen, die zu Stufe, Welt und Schwierigkeit passen. */
	public static Optional<Identifier> pick(DynamicRegistryManager manager, int level, RegistryKey<World> world, boolean peaceful,
			Random random) {
		Optional<Registry<WorldEventDefinition>> registry = manager.getOptional(KEY);
		if (registry.isEmpty()) {
			return Optional.empty();
		}
		List<Identifier> candidates = new ArrayList<>();
		int total = 0;
		for (Identifier id : registry.get().getIds()) {
			WorldEventDefinition definition = registry.get().get(id);
			if (definition != null && definition.minLevel() <= level && definition.allows(world)
					&& !(peaceful && definition.type().needsMonsters())) {
				candidates.add(id);
				total += definition.weight();
			}
		}
		if (total <= 0) {
			return Optional.empty();
		}
		int roll = random.nextInt(total);
		for (Identifier id : candidates.stream().sorted().toList()) {
			roll -= registry.get().get(id).weight();
			if (roll < 0) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}
}
