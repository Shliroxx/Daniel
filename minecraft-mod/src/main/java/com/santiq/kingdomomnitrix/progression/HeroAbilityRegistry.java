package com.santiq.kingdomomnitrix.progression;

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

/** Datengetriebene Helden-Faehigkeiten ({@code data/<ns>/kingdomomnitrix/hero_ability/}), an Clients synchronisiert. */
public final class HeroAbilityRegistry {
	public static final RegistryKey<Registry<HeroAbilityDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("hero_ability"));

	private HeroAbilityRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, HeroAbilityDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				KingdomOmnitrix.LOGGER.info("{} Helden-Faehigkeiten aus Datenpaketen geladen", sortedIds(server.getRegistryManager()).size()));
	}

	public static Optional<HeroAbilityDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	/** Anzeige-Reihenfolge: Freischaltstufe, dann {@code sort_order}, dann ID. */
	public static List<Identifier> sortedIds(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.sorted(Comparator.<Identifier>comparingInt(id -> registry.getOrEmpty(id).map(HeroAbilityDefinition::unlockLevel).orElse(0))
						.thenComparingInt(id -> registry.getOrEmpty(id).map(HeroAbilityDefinition::sortOrder).orElse(0))
						.thenComparing(Identifier::toString))
				.toList()).orElse(List.of());
	}
}
