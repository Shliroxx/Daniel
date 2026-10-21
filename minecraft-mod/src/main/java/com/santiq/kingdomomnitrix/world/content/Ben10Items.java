// Erzeugt von tools/generate_ben10_content.py — nicht von Hand aendern.

package com.santiq.kingdomomnitrix.world.content;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

/** Items der Ben-10-Story: Corrodium, Omnitrix-Teile, Energiekerne (eigener Kreativ-Reiter). */
public final class Ben10Items {
	private static final List<Item> ALL = new ArrayList<>();

	public static final Item CORRODIUM_ORE = register("corrodium_ore", new BlockItem(Ben10Blocks.CORRODIUM_ORE, new Item.Settings()));
	public static final Item CORRODIUM_BLOCK = register("corrodium_block", new BlockItem(Ben10Blocks.CORRODIUM_BLOCK, new Item.Settings()));
	public static final Item OMNITRIX_WORKBENCH = register("omnitrix_workbench", new BlockItem(Ben10Blocks.OMNITRIX_WORKBENCH, new Item.Settings()));
	public static final Item OMNITRIX_STABILIZER = register("omnitrix_stabilizer", new BlockItem(Ben10Blocks.OMNITRIX_STABILIZER, new Item.Settings()));
	public static final Item PLUMBER_HULL = register("plumber_hull", new BlockItem(Ben10Blocks.PLUMBER_HULL, new Item.Settings()));
	public static final Item PLUMBER_HULL_DARK = register("plumber_hull_dark", new BlockItem(Ben10Blocks.PLUMBER_HULL_DARK, new Item.Settings()));
	public static final Item PLUMBER_HULL_SCORCHED = register("plumber_hull_scorched", new BlockItem(Ben10Blocks.PLUMBER_HULL_SCORCHED, new Item.Settings()));
	public static final Item PLUMBER_FLOOR = register("plumber_floor", new BlockItem(Ben10Blocks.PLUMBER_FLOOR, new Item.Settings()));
	public static final Item PLUMBER_LIGHT = register("plumber_light", new BlockItem(Ben10Blocks.PLUMBER_LIGHT, new Item.Settings()));
	public static final Item PLUMBER_CONSOLE = register("plumber_console", new BlockItem(Ben10Blocks.PLUMBER_CONSOLE, new Item.Settings()));
	public static final Item CONTAINMENT_POD_SHELL = register("containment_pod_shell", new BlockItem(Ben10Blocks.CONTAINMENT_POD_SHELL, new Item.Settings()));
	public static final Item CONTAINMENT_POD_CONSOLE = register("containment_pod_console", new BlockItem(Ben10Blocks.CONTAINMENT_POD_CONSOLE, new Item.Settings()));

	public static final Item RAW_CORRODIUM = register("raw_corrodium", new Ben10Item(new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON), "item.kingdomomnitrix.raw_corrodium.tooltip"));
	public static final Item CORRODIUM_CRYSTAL = register("corrodium_crystal", new Ben10Item(new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON), null));
	public static final Item OMNITRIX_SHELL = register("omnitrix_shell", new Ben10Item(new Item.Settings().maxCount(1).rarity(Rarity.EPIC), "item.kingdomomnitrix.omnitrix_shell.tooltip"));
	public static final Item OMNITRIX_CONSOLE = register("omnitrix_console", new Ben10Item(new Item.Settings().maxCount(1).rarity(Rarity.EPIC), "item.kingdomomnitrix.omnitrix_console.tooltip"));
	public static final Item ENERGY_CORE = register("energy_core", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core.tooltip"));
	public static final Item PLUMBER_PLATING = register("plumber_plating", new Ben10Item(new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON), "item.kingdomomnitrix.plumber_plating.tooltip"));
	public static final Item PLUMBER_BLASTER = register("plumber_blaster", new com.santiq.kingdomomnitrix.world.plumber.PlumberBlasterItem(new Item.Settings().rarity(Rarity.RARE).maxDamage(320), "item.kingdomomnitrix.plumber_blaster.tooltip"));
	public static final Item PLUMBER_BADGE = register("plumber_badge", new com.santiq.kingdomomnitrix.world.plumber.PlumberBadgeItem(new Item.Settings().rarity(Rarity.RARE), "item.kingdomomnitrix.plumber_badge.tooltip"));
	public static final Item PLUMBER_HELMET = register("plumber_helmet", new com.santiq.kingdomomnitrix.world.plumber.PlumberArmorItem(net.minecraft.item.ArmorItem.Type.HELMET, new Item.Settings().rarity(Rarity.RARE).maxDamage(net.minecraft.item.ArmorItem.Type.HELMET.getMaxDamage(30)), "item.kingdomomnitrix.plumber_helmet.tooltip"));
	public static final Item PLUMBER_CHESTPLATE = register("plumber_chestplate", new com.santiq.kingdomomnitrix.world.plumber.PlumberArmorItem(net.minecraft.item.ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.RARE).maxDamage(net.minecraft.item.ArmorItem.Type.CHESTPLATE.getMaxDamage(30)), "item.kingdomomnitrix.plumber_chestplate.tooltip"));
	public static final Item PLUMBER_LEGGINGS = register("plumber_leggings", new com.santiq.kingdomomnitrix.world.plumber.PlumberArmorItem(net.minecraft.item.ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.RARE).maxDamage(net.minecraft.item.ArmorItem.Type.LEGGINGS.getMaxDamage(30)), "item.kingdomomnitrix.plumber_leggings.tooltip"));
	public static final Item PLUMBER_BOOTS = register("plumber_boots", new com.santiq.kingdomomnitrix.world.plumber.PlumberArmorItem(net.minecraft.item.ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.RARE).maxDamage(net.minecraft.item.ArmorItem.Type.BOOTS.getMaxDamage(30)), "item.kingdomomnitrix.plumber_boots.tooltip"));
	public static final Item ENERGY_CORE_GREEN = register("energy_core_green", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_green.tooltip"));
	public static final Item ENERGY_CORE_BLUE = register("energy_core_blue", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_blue.tooltip"));
	public static final Item ENERGY_CORE_RED = register("energy_core_red", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_red.tooltip"));
	public static final Item ENERGY_CORE_YELLOW = register("energy_core_yellow", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_yellow.tooltip"));
	public static final Item ENERGY_CORE_PURPLE = register("energy_core_purple", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_purple.tooltip"));
	public static final Item ENERGY_CORE_WHITE = register("energy_core_white", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_white.tooltip"));
	public static final Item ENERGY_CORE_ORANGE = register("energy_core_orange", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_orange.tooltip"));
	public static final Item ENERGY_CORE_PINK = register("energy_core_pink", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_pink.tooltip"));
	public static final Item ENERGY_CORE_CYAN = register("energy_core_cyan", new Ben10Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.kingdomomnitrix.energy_core_cyan.tooltip"));

	/** Farbige Energiekerne und ihre Omnitrix-Farbe (siehe OmnitrixColors). */
	public static final Map<Item, String> CORE_COLORS = coreColors();

	/** Eigener Kreativ-Reiter mit allen Ben-10-Inhalten. */
	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, KingdomOmnitrix.id("ben10"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(OMNITRIX_CONSOLE))
					.displayName(Text.translatable("itemGroup.kingdomomnitrix.ben10"))
					.entries((context, entries) -> {
						entries.add(ModItems.OMNITRIX);
						ALL.forEach(entries::add);
					})
					.build());

	private Ben10Items() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Ben-10-Items registriert: {}", ALL.size());
	}

	public static List<Item> all() {
		return List.copyOf(ALL);
	}

	private static Map<Item, String> coreColors() {
		Map<Item, String> map = new LinkedHashMap<>();
		map.put(ENERGY_CORE_GREEN, "green");
		map.put(ENERGY_CORE_BLUE, "blue");
		map.put(ENERGY_CORE_RED, "red");
		map.put(ENERGY_CORE_YELLOW, "yellow");
		map.put(ENERGY_CORE_PURPLE, "purple");
		map.put(ENERGY_CORE_WHITE, "white");
		map.put(ENERGY_CORE_ORANGE, "orange");
		map.put(ENERGY_CORE_PINK, "pink");
		map.put(ENERGY_CORE_CYAN, "cyan");
		return Map.copyOf(map);
	}

	private static Item register(String name, Item item) {
		Item registered = Registry.register(Registries.ITEM, KingdomOmnitrix.id(name), item);
		ALL.add(registered);
		return registered;
	}
}
