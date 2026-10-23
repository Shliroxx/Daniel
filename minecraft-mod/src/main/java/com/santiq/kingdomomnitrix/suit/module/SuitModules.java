package com.santiq.kingdomomnitrix.suit.module;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.suit.SuitArmorItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

/**
 * Registrierung und Daten der Upgrade-Module: Modul-Items, leere Platine und die Sockelbelegung im Ruestungsteil
 * (Custom Data "SuitModules", ein Eintrag je Sockel, leer = ""). Plaetze je Teil = Stufe des Anzugs. Ein- und
 * ausgebaut wird im Anzug-Konfigurator ({@link SuitConfigurator}), hergestellt in der Legierungsschmiede.
 */
public final class SuitModules {
	private static final String KEY = "SuitModules";
	private static final Map<SuitModule, ModuleItem> ITEMS = new EnumMap<>(SuitModule.class);
	private static final List<Item> ALL = new ArrayList<>();

	public static final Item BLANK = register("module_blank", new Item(new Item.Settings().rarity(Rarity.UNCOMMON)));

	static {
		for (SuitModule module : SuitModule.values()) {
			ModuleItem item = new ModuleItem(module, new Item.Settings().rarity(Rarity.RARE).maxCount(16));
			register(moduleId(module), item);
			ITEMS.put(module, item);
		}
	}

	private SuitModules() {
	}

	private static String moduleId(SuitModule module) {
		return switch (module) {
			case SHIELD_CAPACITY -> "module_shield_capacity";
			case SHIELD_RECHARGE -> "module_shield_recharge";
			case MOBILITY -> "module_mobility";
			case SPEED -> "module_speed";
			case STRENGTH -> "module_strength";
			case VITALITY -> "module_vitality";
		};
	}

	private static <T extends Item> T register(String id, T item) {
		Registry.register(Registries.ITEM, KingdomOmnitrix.id(id), item);
		ALL.add(item);
		return item;
	}

	public static List<Item> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static ModuleItem item(SuitModule module) {
		return ITEMS.get(module);
	}

	/** Plaetze eines Ruestungsteils (Stufe des Anzugs), 0 fuer andere Gegenstaende. */
	public static int slots(ItemStack stack) {
		return stack.getItem() instanceof SuitArmorItem armor ? armor.set().tier() : 0;
	}

	/** Eingebaute Module eines Ruestungsteils (ohne leere Sockel). */
	public static List<SuitModule> installed(ItemStack stack) {
		List<SuitModule> out = new ArrayList<>();
		for (SuitModule module : sockets(stack)) {
			if (module != null) {
				out.add(module);
			}
		}
		return out;
	}

	/** Sockelbelegung eines Ruestungsteils (Laenge = Plaetze, null = leer). */
	public static List<SuitModule> sockets(ItemStack stack) {
		int slots = slots(stack);
		List<SuitModule> out = new ArrayList<>(Collections.nCopies(slots, null));
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		NbtList list = nbt.getList(KEY, NbtElement.STRING_TYPE);
		for (int i = 0; i < Math.min(slots, list.size()); i++) {
			out.set(i, SuitModule.byId(list.getString(i)));
		}
		return out;
	}

	/** Setzt die Sockelbelegung (null = leer); leere Belegung entfernt den Eintrag ganz. */
	public static void setSockets(ItemStack stack, List<SuitModule> sockets) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		if (sockets.stream().allMatch(m -> m == null)) {
			nbt.remove(KEY);
		} else {
			NbtList list = new NbtList();
			sockets.forEach(m -> list.add(NbtString.of(m == null ? "" : m.id())));
			nbt.put(KEY, list);
		}
		if (nbt.isEmpty()) {
			stack.remove(DataComponentTypes.CUSTOM_DATA);
		} else {
			stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		}
	}

	/** Summe der Module ueber mehrere Teile, je Art auf {@link SuitModule#MAX_PER_SUIT} begrenzt. */
	public static Map<SuitModule, Integer> count(Iterable<ItemStack> pieces) {
		Map<SuitModule, Integer> counts = new EnumMap<>(SuitModule.class);
		for (ItemStack piece : pieces) {
			for (SuitModule module : installed(piece)) {
				counts.merge(module, 1, Integer::sum);
			}
		}
		counts.replaceAll((module, n) -> Math.min(n, SuitModule.MAX_PER_SUIT));
		return counts;
	}

	public static void register() {
		SuitConfigurator.register();
		KingdomOmnitrix.LOGGER.debug("Upgrade-Module registriert: {}", ALL.size());
	}
}
