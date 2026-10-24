package com.santiq.kingdomomnitrix.keyblade;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Datengetriebene Keyblade-Werte, synchronisiert an alle Clients (fuer Tooltips und HUD). */
public final class KeybladeRegistry {
	public static final RegistryKey<Registry<KeybladeDefinition>> KEY = RegistryKey.ofRegistry(KingdomOmnitrix.id("keyblade"));

	private KeybladeRegistry() {
	}

	public static void register() {
		DynamicRegistries.registerSynced(KEY, KeybladeDefinition.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getRegistryManager().getOptional(KEY).ifPresent(registry ->
				KingdomOmnitrix.LOGGER.info("{} Keyblades aus Datenpaketen geladen: {}", registry.size(), registry.getIds())));
	}

	/** Definition zum Item des Stacks, falls ein Datenpaket eine liefert. */
	public static Optional<KeybladeDefinition> forStack(@Nullable RegistryWrapper.WrapperLookup registries, ItemStack stack) {
		if (registries == null || stack.isEmpty()) {
			return Optional.empty();
		}
		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		return registries.getOptionalWrapper(KEY).flatMap(wrapper -> wrapper.streamEntries()
				.map(RegistryEntry::value)
				.filter(definition -> definition.item().equals(itemId))
				.findFirst());
	}
}
