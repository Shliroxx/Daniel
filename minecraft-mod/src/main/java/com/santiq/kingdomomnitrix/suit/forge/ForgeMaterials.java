package com.santiq.kingdomomnitrix.suit.forge;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

/**
 * Rohstoffe und Zwischenstufen der Endgame-Ruestungen. Jede Kette: Erz -> Rohstoff -> (Zerkleinerer) Staub ->
 * (Plasmaschmelze) veredelt -> (Legierungsschmiede) Panzerplatte -> (Fusionsaltar) Ruestungsteil.
 */
public final class ForgeMaterials {
	private static final List<Item> ALL = new ArrayList<>();

	// Ben-10-Linie
	public static final Item RAW_TAYDENITE = register("raw_taydenite", Rarity.UNCOMMON);
	public static final Item TAYDENITE_DUST = register("taydenite_dust", Rarity.UNCOMMON);
	public static final Item TAYDENITE_CRYSTAL = register("taydenite_crystal", Rarity.UNCOMMON);
	public static final Item TAYDENITE_PLATE = register("taydenite_plate", Rarity.RARE);
	public static final Item RAW_BIOTECH = register("raw_biotech", Rarity.UNCOMMON);
	public static final Item BIOTECH_DUST = register("biotech_dust", Rarity.UNCOMMON);
	public static final Item BIOTECH_GEL = register("biotech_gel", Rarity.RARE);
	public static final Item BIOTECH_WEAVE = register("biotech_weave", Rarity.RARE);
	public static final Item RAW_CELESTIUM = register("raw_celestium", Rarity.RARE);
	public static final Item CELESTIUM_DUST = register("celestium_dust", Rarity.RARE);
	public static final Item CELESTIUM_INGOT = register("celestium_ingot", Rarity.EPIC);
	public static final Item STAR_MATRIX = register("star_matrix", Rarity.EPIC);
	// Ratchet-Linie
	public static final Item RARITANIUM_DUST = register("raritanium_dust", Rarity.UNCOMMON);
	public static final Item RARITANIUM_INGOT = register("raritanium_ingot", Rarity.UNCOMMON);
	public static final Item MAGNAPLATE_PLATING = register("magnaplate_plating", Rarity.RARE);
	public static final Item CARBONOX_PLATE = register("carbonox_plate", Rarity.RARE);
	public static final Item RAW_INFERNITE = register("raw_infernite", Rarity.RARE);
	public static final Item INFERNITE_DUST = register("infernite_dust", Rarity.RARE);
	public static final Item INFERNITE_CORE = register("infernite_core", Rarity.EPIC);
	public static final Item HELLFIRE_PLATE = register("hellfire_plate", Rarity.EPIC);

	private ForgeMaterials() {
	}

	private static Item register(String name, Rarity rarity) {
		Item.Settings settings = new Item.Settings().rarity(rarity);
		if (rarity == Rarity.EPIC) {
			settings = settings.fireproof();
		}
		Item item = Registry.register(Registries.ITEM, KingdomOmnitrix.id(name), new Item(settings));
		ALL.add(item);
		return item;
	}

	public static List<Item> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Ruestungs-Materialien registriert: {}", ALL.size());
	}
}
