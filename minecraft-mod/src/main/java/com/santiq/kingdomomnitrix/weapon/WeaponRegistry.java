package com.santiq.kingdomomnitrix.weapon;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Datengetriebene Waffenwerte, synchronisiert an Clients (Terminal, Tooltip, HUD). */
public final class WeaponRegistry {
	public static final RegistryKey<Registry<WeaponDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("weapon"));

	private WeaponRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, WeaponDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> KingdomOmnitrix.LOGGER.info("{} Waffen aus Datenpaketen geladen: {}",
				sortedIds(server.getRegistryManager()).size(), sortedIds(server.getRegistryManager())));
	}

	public static Optional<WeaponDefinition> get(DynamicRegistryManager manager, Identifier id) {
		return manager.getOptional(KEY).flatMap(registry -> registry.getOrEmpty(id));
	}

	public static Optional<WeaponDefinition> forStack(@Nullable RegistryWrapper.WrapperLookup registries, ItemStack stack) {
		if (registries == null || stack.isEmpty()) {
			return Optional.empty();
		}
		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		return registries.getOptionalWrapper(KEY).flatMap(wrapper -> wrapper.streamEntries()
				.map(RegistryEntry::value)
				.filter(definition -> definition.item().equals(itemId))
				.findFirst());
	}

	public static List<Identifier> sortedIds(DynamicRegistryManager manager) {
		return manager.getOptional(KEY).map(registry -> registry.getIds().stream()
				.sorted(Comparator.<Identifier>comparingInt(id -> registry.getOrEmpty(id).map(WeaponDefinition::sortOrder).orElse(0))
						.thenComparing(Identifier::toString))
				.toList()).orElse(List.of());
	}
}
