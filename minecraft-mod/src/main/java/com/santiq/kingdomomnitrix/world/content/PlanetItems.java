// Erzeugt von tools/generate_planet_content.py — nicht von Hand aendern.

package com.santiq.kingdomomnitrix.world.content;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

/** Items der Ratchet-&-Clank-Welten: Block-Items, Rohstoffe, Barren, Werkzeuge, Schwerter, Ruestungen. */
public final class PlanetItems {
	private static final List<Item> ALL = new ArrayList<>();

	public static final Item VELDIN_SAND = register("veldin_sand", new BlockItem(PlanetBlocks.VELDIN_SAND, new Item.Settings()));
	public static final Item VELDIN_SANDSTONE = register("veldin_sandstone", new BlockItem(PlanetBlocks.VELDIN_SANDSTONE, new Item.Settings()));
	public static final Item VELDIN_ROCK = register("veldin_rock", new BlockItem(PlanetBlocks.VELDIN_ROCK, new Item.Settings()));
	public static final Item VELDIN_SCRUB = register("veldin_scrub", new BlockItem(PlanetBlocks.VELDIN_SCRUB, new Item.Settings()));
	public static final Item KERWAN_TURF = register("kerwan_turf", new BlockItem(PlanetBlocks.KERWAN_TURF, new Item.Settings()));
	public static final Item KERWAN_SOIL = register("kerwan_soil", new BlockItem(PlanetBlocks.KERWAN_SOIL, new Item.Settings()));
	public static final Item KERWAN_SLATE = register("kerwan_slate", new BlockItem(PlanetBlocks.KERWAN_SLATE, new Item.Settings()));
	public static final Item KERWAN_LOG = register("kerwan_log", new BlockItem(PlanetBlocks.KERWAN_LOG, new Item.Settings()));
	public static final Item KERWAN_PLANKS = register("kerwan_planks", new BlockItem(PlanetBlocks.KERWAN_PLANKS, new Item.Settings()));
	public static final Item KERWAN_LEAVES = register("kerwan_leaves", new BlockItem(PlanetBlocks.KERWAN_LEAVES, new Item.Settings()));
	public static final Item KERWAN_REED = register("kerwan_reed", new BlockItem(PlanetBlocks.KERWAN_REED, new Item.Settings()));
	public static final Item NOVALIS_MOSS = register("novalis_moss", new BlockItem(PlanetBlocks.NOVALIS_MOSS, new Item.Settings()));
	public static final Item NOVALIS_LOAM = register("novalis_loam", new BlockItem(PlanetBlocks.NOVALIS_LOAM, new Item.Settings()));
	public static final Item NOVALIS_LIMESTONE = register("novalis_limestone", new BlockItem(PlanetBlocks.NOVALIS_LIMESTONE, new Item.Settings()));
	public static final Item NOVALIS_LOG = register("novalis_log", new BlockItem(PlanetBlocks.NOVALIS_LOG, new Item.Settings()));
	public static final Item NOVALIS_PLANKS = register("novalis_planks", new BlockItem(PlanetBlocks.NOVALIS_PLANKS, new Item.Settings()));
	public static final Item NOVALIS_LEAVES = register("novalis_leaves", new BlockItem(PlanetBlocks.NOVALIS_LEAVES, new Item.Settings()));
	public static final Item NOVALIS_BLOOM = register("novalis_bloom", new BlockItem(PlanetBlocks.NOVALIS_BLOOM, new Item.Settings()));
	public static final Item RILGAR_SAND = register("rilgar_sand", new BlockItem(PlanetBlocks.RILGAR_SAND, new Item.Settings()));
	public static final Item RILGAR_CORALSTONE = register("rilgar_coralstone", new BlockItem(PlanetBlocks.RILGAR_CORALSTONE, new Item.Settings()));
	public static final Item RILGAR_LOG = register("rilgar_log", new BlockItem(PlanetBlocks.RILGAR_LOG, new Item.Settings()));
	public static final Item RILGAR_PLANKS = register("rilgar_planks", new BlockItem(PlanetBlocks.RILGAR_PLANKS, new Item.Settings()));
	public static final Item RILGAR_LEAVES = register("rilgar_leaves", new BlockItem(PlanetBlocks.RILGAR_LEAVES, new Item.Settings()));
	public static final Item RILGAR_SEAGRASS = register("rilgar_seagrass", new BlockItem(PlanetBlocks.RILGAR_SEAGRASS, new Item.Settings()));
	public static final Item TORREN_DUST = register("torren_dust", new BlockItem(PlanetBlocks.TORREN_DUST, new Item.Settings()));
	public static final Item TORREN_CRUST = register("torren_crust", new BlockItem(PlanetBlocks.TORREN_CRUST, new Item.Settings()));
	public static final Item TORREN_BASALT = register("torren_basalt", new BlockItem(PlanetBlocks.TORREN_BASALT, new Item.Settings()));
	public static final Item TORREN_THORN = register("torren_thorn", new BlockItem(PlanetBlocks.TORREN_THORN, new Item.Settings()));
	public static final Item SONOROSIA_SAND = register("sonorosia_sand", new BlockItem(PlanetBlocks.SONOROSIA_SAND, new Item.Settings()));
	public static final Item SONOROSIA_STONE = register("sonorosia_stone", new BlockItem(PlanetBlocks.SONOROSIA_STONE, new Item.Settings()));
	public static final Item SONOROSIA_RESONITE = register("sonorosia_resonite", new BlockItem(PlanetBlocks.SONOROSIA_RESONITE, new Item.Settings()));
	public static final Item SONOROSIA_REED = register("sonorosia_reed", new BlockItem(PlanetBlocks.SONOROSIA_REED, new Item.Settings()));
	public static final Item PETROPIA_SHARDS = register("petropia_shards", new BlockItem(PlanetBlocks.PETROPIA_SHARDS, new Item.Settings()));
	public static final Item PETROPIA_ROCK = register("petropia_rock", new BlockItem(PlanetBlocks.PETROPIA_ROCK, new Item.Settings()));
	public static final Item PETROPIA_CRYSTAL = register("petropia_crystal", new BlockItem(PlanetBlocks.PETROPIA_CRYSTAL, new Item.Settings()));
	public static final Item PETROPIA_SPROUT = register("petropia_sprout", new BlockItem(PlanetBlocks.PETROPIA_SPROUT, new Item.Settings()));
	public static final Item PETROPIA_CLUSTER = register("petropia_cluster", new BlockItem(PlanetBlocks.PETROPIA_CLUSTER, new Item.Settings()));
	public static final Item PETROPIA_SMOOTH_ROCK = register("petropia_smooth_rock", new BlockItem(PlanetBlocks.PETROPIA_SMOOTH_ROCK, new Item.Settings()));
	public static final Item PETROPIA_CRYSTAL_LOG = register("petropia_crystal_log", new BlockItem(PlanetBlocks.PETROPIA_CRYSTAL_LOG, new Item.Settings()));
	public static final Item PETROPIA_CRYSTAL_LEAVES = register("petropia_crystal_leaves", new BlockItem(PlanetBlocks.PETROPIA_CRYSTAL_LEAVES, new Item.Settings()));
	public static final Item GALVAN_MOSS = register("galvan_moss", new BlockItem(PlanetBlocks.GALVAN_MOSS, new Item.Settings()));
	public static final Item GALVAN_MUD = register("galvan_mud", new BlockItem(PlanetBlocks.GALVAN_MUD, new Item.Settings()));
	public static final Item GALVAN_SLATE = register("galvan_slate", new BlockItem(PlanetBlocks.GALVAN_SLATE, new Item.Settings()));
	public static final Item GALVAN_FERN = register("galvan_fern", new BlockItem(PlanetBlocks.GALVAN_FERN, new Item.Settings()));
	public static final Item MECHAMORPH_PLATE = register("mechamorph_plate", new BlockItem(PlanetBlocks.MECHAMORPH_PLATE, new Item.Settings()));
	public static final Item MECHAMORPH_CIRCUIT = register("mechamorph_circuit", new BlockItem(PlanetBlocks.MECHAMORPH_CIRCUIT, new Item.Settings()));
	public static final Item MECHAMORPH_VINE = register("mechamorph_vine", new BlockItem(PlanetBlocks.MECHAMORPH_VINE, new Item.Settings()));
	public static final Item KYLMYYS_SNOW = register("kylmyys_snow", new BlockItem(PlanetBlocks.KYLMYYS_SNOW, new Item.Settings()));
	public static final Item KYLMYYS_ICE = register("kylmyys_ice", new BlockItem(PlanetBlocks.KYLMYYS_ICE, new Item.Settings()));
	public static final Item KYLMYYS_PERMAFROST = register("kylmyys_permafrost", new BlockItem(PlanetBlocks.KYLMYYS_PERMAFROST, new Item.Settings()));
	public static final Item KYLMYYS_FROSTFERN = register("kylmyys_frostfern", new BlockItem(PlanetBlocks.KYLMYYS_FROSTFERN, new Item.Settings()));
	public static final Item VULPIN_ASH = register("vulpin_ash", new BlockItem(PlanetBlocks.VULPIN_ASH, new Item.Settings()));
	public static final Item VULPIN_RUBBLE = register("vulpin_rubble", new BlockItem(PlanetBlocks.VULPIN_RUBBLE, new Item.Settings()));
	public static final Item VULPIN_SLUDGE = register("vulpin_sludge", new BlockItem(PlanetBlocks.VULPIN_SLUDGE, new Item.Settings()));
	public static final Item VULPIN_THORN = register("vulpin_thorn", new BlockItem(PlanetBlocks.VULPIN_THORN, new Item.Settings()));
	public static final Item PISCCISS_SAND = register("piscciss_sand", new BlockItem(PlanetBlocks.PISCCISS_SAND, new Item.Settings()));
	public static final Item PISCCISS_REEFSTONE = register("piscciss_reefstone", new BlockItem(PlanetBlocks.PISCCISS_REEFSTONE, new Item.Settings()));
	public static final Item PISCCISS_GRASS = register("piscciss_grass", new BlockItem(PlanetBlocks.PISCCISS_GRASS, new Item.Settings()));
	public static final Item HULL_PLATE = register("hull_plate", new BlockItem(PlanetBlocks.HULL_PLATE, new Item.Settings()));
	public static final Item HULL_PLATE_DARK = register("hull_plate_dark", new BlockItem(PlanetBlocks.HULL_PLATE_DARK, new Item.Settings()));
	public static final Item HULL_PLATE_ORANGE = register("hull_plate_orange", new BlockItem(PlanetBlocks.HULL_PLATE_ORANGE, new Item.Settings()));
	public static final Item HAZARD_PLATE = register("hazard_plate", new BlockItem(PlanetBlocks.HAZARD_PLATE, new Item.Settings()));
	public static final Item TECH_GLASS = register("tech_glass", new BlockItem(PlanetBlocks.TECH_GLASS, new Item.Settings()));
	public static final Item LIGHT_PANEL = register("light_panel", new BlockItem(PlanetBlocks.LIGHT_PANEL, new Item.Settings()));
	public static final Item GRATE = register("grate", new BlockItem(PlanetBlocks.GRATE, new Item.Settings()));
	public static final Item STATION_PLATE = register("station_plate", new BlockItem(PlanetBlocks.STATION_PLATE, new Item.Settings()));
	public static final Item STATION_TRIM = register("station_trim", new BlockItem(PlanetBlocks.STATION_TRIM, new Item.Settings()));
	public static final Item PAD_PLATE = register("pad_plate", new BlockItem(PlanetBlocks.PAD_PLATE, new Item.Settings()));
	public static final Item PAD_MARKING = register("pad_marking", new BlockItem(PlanetBlocks.PAD_MARKING, new Item.Settings()));
	public static final Item PLANET_CORE = register("planet_core", new BlockItem(PlanetBlocks.PLANET_CORE, new Item.Settings()));
	public static final Item SOLAR_COPPER_ORE = register("solar_copper_ore", new BlockItem(PlanetBlocks.SOLAR_COPPER_ORE, new Item.Settings()));
	public static final Item SOLAR_COPPER_BLOCK = register("solar_copper_block", new BlockItem(PlanetBlocks.SOLAR_COPPER_BLOCK, new Item.Settings()));
	public static final Item AZURITE_ORE = register("azurite_ore", new BlockItem(PlanetBlocks.AZURITE_ORE, new Item.Settings()));
	public static final Item AZURITE_BLOCK = register("azurite_block", new BlockItem(PlanetBlocks.AZURITE_BLOCK, new Item.Settings()));
	public static final Item VIRIDIUM_ORE = register("viridium_ore", new BlockItem(PlanetBlocks.VIRIDIUM_ORE, new Item.Settings()));
	public static final Item VIRIDIUM_BLOCK = register("viridium_block", new BlockItem(PlanetBlocks.VIRIDIUM_BLOCK, new Item.Settings()));
	public static final Item AQUARINE_ORE = register("aquarine_ore", new BlockItem(PlanetBlocks.AQUARINE_ORE, new Item.Settings()));
	public static final Item AQUARINE_BLOCK = register("aquarine_block", new BlockItem(PlanetBlocks.AQUARINE_BLOCK, new Item.Settings()));
	public static final Item PYRONITE_ORE = register("pyronite_ore", new BlockItem(PlanetBlocks.PYRONITE_ORE, new Item.Settings()));
	public static final Item PYRONITE_BLOCK = register("pyronite_block", new BlockItem(PlanetBlocks.PYRONITE_BLOCK, new Item.Settings()));

	public static final Item RAW_SOLAR_COPPER = register("raw_solar_copper", new Item(new Item.Settings()));
	public static final Item SOLAR_COPPER_INGOT = register("solar_copper_ingot", new Item(new Item.Settings().rarity(Rarity.COMMON)));
	public static final Item SOLAR_COPPER_SWORD = register("solar_copper_sword", new SwordItem(PlanetMaterials.Tool.SOLAR_COPPER, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(SwordItem.createAttributeModifiers(PlanetMaterials.Tool.SOLAR_COPPER, 3, -2.4f))));
	public static final Item SOLAR_COPPER_PICKAXE = register("solar_copper_pickaxe", new PickaxeItem(PlanetMaterials.Tool.SOLAR_COPPER, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.SOLAR_COPPER, 1.0f, -2.8f))));
	public static final Item SOLAR_COPPER_AXE = register("solar_copper_axe", new AxeItem(PlanetMaterials.Tool.SOLAR_COPPER, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.SOLAR_COPPER, 5.5f, -3.0f))));
	public static final Item SOLAR_COPPER_SHOVEL = register("solar_copper_shovel", new ShovelItem(PlanetMaterials.Tool.SOLAR_COPPER, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.SOLAR_COPPER, 1.5f, -3.0f))));
	public static final Item SOLAR_COPPER_HELMET = register("solar_copper_helmet", new ArmorItem(PlanetMaterials.SOLAR_COPPER_ARMOR, ArmorItem.Type.HELMET, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.HELMET.getMaxDamage(16))));
	public static final Item SOLAR_COPPER_CHESTPLATE = register("solar_copper_chestplate", new ArmorItem(PlanetMaterials.SOLAR_COPPER_ARMOR, ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(16))));
	public static final Item SOLAR_COPPER_LEGGINGS = register("solar_copper_leggings", new ArmorItem(PlanetMaterials.SOLAR_COPPER_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(16))));
	public static final Item SOLAR_COPPER_BOOTS = register("solar_copper_boots", new ArmorItem(PlanetMaterials.SOLAR_COPPER_ARMOR, ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(16))));

	public static final Item RAW_AZURITE = register("raw_azurite", new Item(new Item.Settings()));
	public static final Item AZURITE_INGOT = register("azurite_ingot", new Item(new Item.Settings().rarity(Rarity.COMMON)));
	public static final Item AZURITE_SWORD = register("azurite_sword", new SwordItem(PlanetMaterials.Tool.AZURITE, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(SwordItem.createAttributeModifiers(PlanetMaterials.Tool.AZURITE, 3, -2.4f))));
	public static final Item AZURITE_PICKAXE = register("azurite_pickaxe", new PickaxeItem(PlanetMaterials.Tool.AZURITE, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.AZURITE, 1.0f, -2.8f))));
	public static final Item AZURITE_AXE = register("azurite_axe", new AxeItem(PlanetMaterials.Tool.AZURITE, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.AZURITE, 5.5f, -3.0f))));
	public static final Item AZURITE_SHOVEL = register("azurite_shovel", new ShovelItem(PlanetMaterials.Tool.AZURITE, new Item.Settings().rarity(Rarity.COMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.AZURITE, 1.5f, -3.0f))));
	public static final Item AZURITE_HELMET = register("azurite_helmet", new ArmorItem(PlanetMaterials.AZURITE_ARMOR, ArmorItem.Type.HELMET, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.HELMET.getMaxDamage(20))));
	public static final Item AZURITE_CHESTPLATE = register("azurite_chestplate", new ArmorItem(PlanetMaterials.AZURITE_ARMOR, ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(20))));
	public static final Item AZURITE_LEGGINGS = register("azurite_leggings", new ArmorItem(PlanetMaterials.AZURITE_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(20))));
	public static final Item AZURITE_BOOTS = register("azurite_boots", new ArmorItem(PlanetMaterials.AZURITE_ARMOR, ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.COMMON).maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(20))));

	public static final Item RAW_VIRIDIUM = register("raw_viridium", new Item(new Item.Settings()));
	public static final Item VIRIDIUM_INGOT = register("viridium_ingot", new Item(new Item.Settings().rarity(Rarity.UNCOMMON)));
	public static final Item VIRIDIUM_SWORD = register("viridium_sword", new SwordItem(PlanetMaterials.Tool.VIRIDIUM, new Item.Settings().rarity(Rarity.UNCOMMON).attributeModifiers(SwordItem.createAttributeModifiers(PlanetMaterials.Tool.VIRIDIUM, 3, -2.4f))));
	public static final Item VIRIDIUM_PICKAXE = register("viridium_pickaxe", new PickaxeItem(PlanetMaterials.Tool.VIRIDIUM, new Item.Settings().rarity(Rarity.UNCOMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.VIRIDIUM, 1.0f, -2.8f))));
	public static final Item VIRIDIUM_AXE = register("viridium_axe", new AxeItem(PlanetMaterials.Tool.VIRIDIUM, new Item.Settings().rarity(Rarity.UNCOMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.VIRIDIUM, 5.5f, -3.0f))));
	public static final Item VIRIDIUM_SHOVEL = register("viridium_shovel", new ShovelItem(PlanetMaterials.Tool.VIRIDIUM, new Item.Settings().rarity(Rarity.UNCOMMON).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.VIRIDIUM, 1.5f, -3.0f))));
	public static final Item VIRIDIUM_HELMET = register("viridium_helmet", new ArmorItem(PlanetMaterials.VIRIDIUM_ARMOR, ArmorItem.Type.HELMET, new Item.Settings().rarity(Rarity.UNCOMMON).maxDamage(ArmorItem.Type.HELMET.getMaxDamage(28))));
	public static final Item VIRIDIUM_CHESTPLATE = register("viridium_chestplate", new ArmorItem(PlanetMaterials.VIRIDIUM_ARMOR, ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.UNCOMMON).maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(28))));
	public static final Item VIRIDIUM_LEGGINGS = register("viridium_leggings", new ArmorItem(PlanetMaterials.VIRIDIUM_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.UNCOMMON).maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(28))));
	public static final Item VIRIDIUM_BOOTS = register("viridium_boots", new ArmorItem(PlanetMaterials.VIRIDIUM_ARMOR, ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.UNCOMMON).maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(28))));

	public static final Item RAW_AQUARINE = register("raw_aquarine", new Item(new Item.Settings()));
	public static final Item AQUARINE_INGOT = register("aquarine_ingot", new Item(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item AQUARINE_SWORD = register("aquarine_sword", new SwordItem(PlanetMaterials.Tool.AQUARINE, new Item.Settings().rarity(Rarity.RARE).attributeModifiers(SwordItem.createAttributeModifiers(PlanetMaterials.Tool.AQUARINE, 3, -2.4f))));
	public static final Item AQUARINE_PICKAXE = register("aquarine_pickaxe", new PickaxeItem(PlanetMaterials.Tool.AQUARINE, new Item.Settings().rarity(Rarity.RARE).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.AQUARINE, 1.0f, -2.8f))));
	public static final Item AQUARINE_AXE = register("aquarine_axe", new AxeItem(PlanetMaterials.Tool.AQUARINE, new Item.Settings().rarity(Rarity.RARE).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.AQUARINE, 5.5f, -3.0f))));
	public static final Item AQUARINE_SHOVEL = register("aquarine_shovel", new ShovelItem(PlanetMaterials.Tool.AQUARINE, new Item.Settings().rarity(Rarity.RARE).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.AQUARINE, 1.5f, -3.0f))));
	public static final Item AQUARINE_HELMET = register("aquarine_helmet", new ArmorItem(PlanetMaterials.AQUARINE_ARMOR, ArmorItem.Type.HELMET, new Item.Settings().rarity(Rarity.RARE).maxDamage(ArmorItem.Type.HELMET.getMaxDamage(33))));
	public static final Item AQUARINE_CHESTPLATE = register("aquarine_chestplate", new ArmorItem(PlanetMaterials.AQUARINE_ARMOR, ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.RARE).maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(33))));
	public static final Item AQUARINE_LEGGINGS = register("aquarine_leggings", new ArmorItem(PlanetMaterials.AQUARINE_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.RARE).maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(33))));
	public static final Item AQUARINE_BOOTS = register("aquarine_boots", new ArmorItem(PlanetMaterials.AQUARINE_ARMOR, ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.RARE).maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(33))));

	public static final Item RAW_PYRONITE = register("raw_pyronite", new Item(new Item.Settings()));
	public static final Item PYRONITE_INGOT = register("pyronite_ingot", new Item(new Item.Settings().rarity(Rarity.EPIC)));
	public static final Item PYRONITE_SWORD = register("pyronite_sword", new SwordItem(PlanetMaterials.Tool.PYRONITE, new Item.Settings().rarity(Rarity.EPIC).attributeModifiers(SwordItem.createAttributeModifiers(PlanetMaterials.Tool.PYRONITE, 3, -2.4f))));
	public static final Item PYRONITE_PICKAXE = register("pyronite_pickaxe", new PickaxeItem(PlanetMaterials.Tool.PYRONITE, new Item.Settings().rarity(Rarity.EPIC).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.PYRONITE, 1.0f, -2.8f))));
	public static final Item PYRONITE_AXE = register("pyronite_axe", new AxeItem(PlanetMaterials.Tool.PYRONITE, new Item.Settings().rarity(Rarity.EPIC).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.PYRONITE, 5.5f, -3.0f))));
	public static final Item PYRONITE_SHOVEL = register("pyronite_shovel", new ShovelItem(PlanetMaterials.Tool.PYRONITE, new Item.Settings().rarity(Rarity.EPIC).attributeModifiers(MiningToolItem.createAttributeModifiers(PlanetMaterials.Tool.PYRONITE, 1.5f, -3.0f))));
	public static final Item PYRONITE_HELMET = register("pyronite_helmet", new ArmorItem(PlanetMaterials.PYRONITE_ARMOR, ArmorItem.Type.HELMET, new Item.Settings().rarity(Rarity.EPIC).maxDamage(ArmorItem.Type.HELMET.getMaxDamage(38))));
	public static final Item PYRONITE_CHESTPLATE = register("pyronite_chestplate", new ArmorItem(PlanetMaterials.PYRONITE_ARMOR, ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.EPIC).maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(38))));
	public static final Item PYRONITE_LEGGINGS = register("pyronite_leggings", new ArmorItem(PlanetMaterials.PYRONITE_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.EPIC).maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(38))));
	public static final Item PYRONITE_BOOTS = register("pyronite_boots", new ArmorItem(PlanetMaterials.PYRONITE_ARMOR, ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.EPIC).maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(38))));

	/** Eigener Kreativ-Reiter mit allen Planeten-Inhalten. */
	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, KingdomOmnitrix.id("planets"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(PYRONITE_SWORD))
					.displayName(Text.translatable("itemGroup.kingdomomnitrix.planets"))
					.entries((context, entries) -> ALL.forEach(entries::add))
					.build());

	private PlanetItems() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Planeten-Items registriert: {}", ALL.size());
	}

	public static List<Item> all() {
		return List.copyOf(ALL);
	}

	private static Item register(String name, Item item) {
		Item registered = Registry.register(Registries.ITEM, KingdomOmnitrix.id(name), item);
		ALL.add(registered);
		return registered;
	}
}
