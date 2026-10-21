#!/usr/bin/env python3
"""Ben-10-Inhalte: Corrodium (End), Omnitrix-Werkbank mit Reaktor-Stabilisatoren, Omnitrix-Teile, Energiekerne.

Einzige Quelle fuer Bloecke und Items der Ben-10-Story (eigener Kreativ-Reiter). Nutzt die Bausteine von
tools/generate_planet_content.py (Blockzustaende, Modelle, Loot, Rezepte, Tags) und die Texturen aus
tools/ben10_textures.py. Erzeugt:
  * Texturen (Bloecke 128x128, Items 64x64, Reaktor-Feld/-Ring/-Plasma der Werkbank), Blockzustaende, Block-/Item-Modelle, Loot-Tabellen, Rezepte, Tags
  * Weltgenerierung: Corrodium-Erz im End (configured/placed feature; eingefuegt per BiomeModifications in Java)
  * Uebersetzungen (de_de, en_us)
  * Java: world/content/Ben10Blocks.java und Ben10Items.java (nicht von Hand aendern)

Story: Die Omnitrix-Huelle und -Konsole stammen aus dem abgestuerzten Klempner-Schiff. Zusammengesetzt werden sie in der
Omnitrix-Werkbank (Corrodium aus dem End) mit einem Energiekern; die Farbe des Kerns ist die Farbe des Omnitrix.

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_ben10_content.py            # alles schreiben
    python tools/generate_ben10_content.py --check    # Java aktuell? Dateien vorhanden?
    python tools/generate_ben10_content.py --sheet out.png
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ben10_textures as bx  # noqa: E402
import generate_planet_content as gpc  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow numpy")

LOG = logging.getLogger("ben10_content")
MOD = gpc.MOD
ROOT = gpc.ROOT
ASSETS = gpc.ASSETS
DATA = gpc.DATA
JAVA = gpc.JAVA
HEADER = "// Erzeugt von tools/generate_ben10_content.py — nicht von Hand aendern.\n"


@dataclass
class Ben10Block(gpc.BlockDef):
    ctor: str | None = None      # Java-Ausdruck fuer eigene Blockklassen (sonst Standard aus dem Planeten-Generator)
    model: str | None = None     # "bottom_top": eigene Ober-/Unterseite


@dataclass
class ItemDef:
    name: str
    de: str
    en: str
    texture: Callable[[], Image.Image]
    rarity: str = "COMMON"
    max_count: int = 64


def single(name: str, fn: Callable[[str], Image.Image]):
    return lambda: {name: fn(name)}


BLOCKS: list[Ben10Block] = [
    Ben10Block("corrodium_ore", "Corrodium-Erz", "Corrodium Ore", "ore", single("corrodium_ore", bx.corrodium_ore),
               "LIME", 4.0, 9.0, "AMETHYST_BLOCK", tier="iron", light=7, drop="ore:raw_corrodium", xp=(3, 7), planet="end"),
    Ben10Block("corrodium_block", "Corrodium-Block", "Block of Corrodium", "storage", single("corrodium_block", bx.corrodium_block),
               "LIME", 5.0, 9.0, "AMETHYST_BLOCK", tier="iron", light=9, planet="end", tags=["beacon"]),
    Ben10Block("omnitrix_workbench", "Omnitrix-Werkbank", "Omnitrix Workbench", "block",
               lambda: {"omnitrix_workbench": bx.workbench_side("omnitrix_workbench"),
                        "omnitrix_workbench_top": bx.workbench_top("omnitrix_workbench_top"),
                        "omnitrix_workbench_bottom": bx.workbench_bottom("omnitrix_workbench_bottom")},
               "LIME", 4.0, 9.0, "METAL", tier="stone", light=6, planet="plumber",
               ctor="new com.santiq.kingdomomnitrix.omnitrix.workbench.OmnitrixWorkbenchBlock("
                    "base(MapColor.LIME, 4.0f, 9.0f, BlockSoundGroup.METAL, 6, true).nonOpaque())",
               model="bottom_top"),
    Ben10Block("omnitrix_stabilizer", "Omnitrix-Stabilisator", "Omnitrix Stabilizer", "block",
               lambda: {"omnitrix_stabilizer": bx.stabilizer_side("omnitrix_stabilizer"),
                        "omnitrix_stabilizer_top": bx.stabilizer_top("omnitrix_stabilizer_top"),
                        "omnitrix_stabilizer_bottom": bx.stabilizer_top("omnitrix_stabilizer_bottom")},
               "LIME", 4.0, 9.0, "METAL", tier="stone", light=8, planet="plumber", model="bottom_top"),
]

# Klempner-Kreuzer "Proctor" (Wrack): Rumpf, Deck, Licht, Konsole und die beiden Containment-Kapseln mit den
# Omnitrix-Teilen (unzerstoerbar, geben jedem Spieler sein Teil einmal; Block-Entity in Java)
_POD = ("new com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock(AbstractBlock.Settings.create().mapColor(MapColor.LIME)"
        ".strength(-1.0f, 3600000.0f).dropsNothing().luminance(state -> 10).sounds(BlockSoundGroup.METAL), "
        "com.santiq.kingdomomnitrix.world.plumber.ContainmentPodBlock.Part.{part})")
BLOCKS += [
    Ben10Block("plumber_hull", "Klempner-Rumpfplatte", "Plumber Hull Plate", "block", single("plumber_hull", bx.plumber_hull),
               "LIGHT_GRAY", 5.0, 12.0, "METAL", tier="iron", planet="plumber"),
    Ben10Block("plumber_hull_dark", "Dunkle Klempner-Rumpfplatte", "Dark Plumber Hull Plate", "block",
               single("plumber_hull_dark", bx.plumber_hull_dark), "BLACK", 5.0, 12.0, "METAL", tier="iron", light=3, planet="plumber"),
    Ben10Block("plumber_hull_scorched", "Ausgebrannte Rumpfplatte", "Scorched Hull Plate", "block",
               single("plumber_hull_scorched", bx.plumber_hull_scorched), "BLACK", 4.0, 10.0, "METAL", tier="stone", planet="plumber"),
    Ben10Block("plumber_floor", "Klempner-Deckplatte", "Plumber Deck Plate", "block", single("plumber_floor", bx.plumber_floor),
               "IRON_GRAY", 4.0, 10.0, "METAL", tier="stone", planet="plumber"),
    Ben10Block("plumber_light", "Klempner-Leuchtpaneel", "Plumber Light Panel", "light", single("plumber_light", bx.plumber_light),
               "LIME", 1.5, 4.0, "GLASS", light=15, planet="plumber"),
    Ben10Block("plumber_console", "Klempner-Bordkonsole", "Plumber Console", "block",
               lambda: {"plumber_console": bx.plumber_console("plumber_console"),
                        "plumber_console_top": bx.containment_pod_top("plumber_console_top"),
                        "plumber_console_bottom": bx.plumber_floor("plumber_console_bottom")},
               "BLACK", 3.0, 8.0, "METAL", tier="stone", light=7, planet="plumber", model="bottom_top"),
    Ben10Block("containment_pod_shell", "Containment-Kapsel (Hülle)", "Containment Pod (Shell)", "block",
               lambda: {"containment_pod_shell": bx.containment_pod("containment_pod_shell", "shell"),
                        "containment_pod_shell_top": bx.containment_pod_top("containment_pod_shell_top"),
                        "containment_pod_shell_bottom": bx.containment_pod_top("containment_pod_shell_bottom")},
               "LIME", -1.0, 3600000.0, "METAL", tool=None, light=10, drop="none", planet="plumber", model="bottom_top",
               ctor=_POD.format(part="SHELL")),
    Ben10Block("containment_pod_console", "Containment-Kapsel (Konsole)", "Containment Pod (Console)", "block",
               lambda: {"containment_pod_console": bx.containment_pod("containment_pod_console", "console"),
                        "containment_pod_console_top": bx.containment_pod_top("containment_pod_console_top"),
                        "containment_pod_console_bottom": bx.containment_pod_top("containment_pod_console_bottom")},
               "LIME", -1.0, 3600000.0, "METAL", tool=None, light=10, drop="none", planet="plumber", model="bottom_top",
               ctor=_POD.format(part="CONSOLE")),
]

# Reaktor-Texturen fuer die Werkbank-Animation (vom Block-Entity-Renderer gezeichnet, Graustufe/weiss zum Einfaerben)
REACTOR_TEXTURES = {
    "field": lambda: bx.reactor_field("reactor_field"),
    "ring": lambda: bx.reactor_ring("reactor_ring"),
    "plasma": lambda: bx.reactor_plasma("reactor_plasma"),
}

ITEMS: list[ItemDef] = [
    ItemDef("raw_corrodium", "Roh-Corrodium", "Raw Corrodium", lambda: bx.raw_corrodium("raw_corrodium"), "UNCOMMON"),
    ItemDef("corrodium_crystal", "Corrodium-Kristall", "Corrodium Crystal", lambda: bx.corrodium_crystal("corrodium_crystal"), "UNCOMMON"),
    ItemDef("omnitrix_shell", "Omnitrix-Hülle", "Omnitrix Shell", lambda: bx.omnitrix_shell("omnitrix_shell"), "EPIC", 1),
    ItemDef("omnitrix_console", "Omnitrix-Konsole", "Omnitrix Console", lambda: bx.omnitrix_console("omnitrix_console"), "EPIC", 1),
    ItemDef("energy_core", "Basis-Energiekern", "Base Energy Core", lambda: bx.energy_core("energy_core", None), "RARE", 16),
]
CORE_NAMES = {
    "green": ("Grün", "Green", "minecraft:green_dye"),
    "blue": ("Blau", "Blue", "minecraft:blue_dye"),
    "red": ("Rot", "Red", "minecraft:red_dye"),
    "yellow": ("Gelb", "Yellow", "minecraft:yellow_dye"),
    "purple": ("Lila", "Purple", "minecraft:purple_dye"),
    "white": ("Weiß", "White", "minecraft:white_dye"),
    "orange": ("Orange", "Orange", "minecraft:orange_dye"),
    "pink": ("Pink", "Pink", "minecraft:pink_dye"),
    "cyan": ("Türkis", "Cyan", "minecraft:cyan_dye"),
}
for _color, (_de, _en, _dye) in CORE_NAMES.items():
    ITEMS.append(ItemDef(f"energy_core_{_color}", f"Energiekern ({_de})", f"{_en} Energy Core",
                         (lambda c=_color: bx.energy_core(f"energy_core_{c}", c)), "RARE", 16))

TOOLTIPS = {
    "raw_corrodium": ("Giftig. Nur im End zu finden.", "Toxic. Found only in the End."),
    "omnitrix_shell": ("Aus dem Klempner-Schiff. In der Omnitrix-Werkbank mit Konsole und Energiekern zusammensetzen.",
                       "From the Plumber ship. Assemble it with the console and an energy core in the Omnitrix Workbench."),
    "omnitrix_console": ("Aus dem Klempner-Schiff. In der Omnitrix-Werkbank mit Hülle und Energiekern zusammensetzen.",
                         "From the Plumber ship. Assemble it with the shell and an energy core in the Omnitrix Workbench."),
    "energy_core": ("Mit einem Farbstoff einfärben. Der Kern bestimmt die Farbe des Omnitrix.",
                    "Dye it with a dye. The core sets the colour of the Omnitrix."),
}
for _color in CORE_NAMES:
    TOOLTIPS[f"energy_core_{_color}"] = ("In der Omnitrix-Werkbank einsetzen, um das Omnitrix umzufärben.",
                                         "Place it in the Omnitrix Workbench to recolour the Omnitrix.")


# --- Assets -----------------------------------------------------------------------------------------------------

def block_models(b: Ben10Block) -> dict[str, dict]:
    if b.model == "bottom_top":
        tex = lambda t: f"{MOD}:block/{t}"  # noqa: E731
        return {b.name: {"parent": "minecraft:block/cube_bottom_top",
                         "textures": {"top": tex(f"{b.name}_top"), "side": tex(b.name), "bottom": tex(f"{b.name}_bottom")}}}
    return gpc.block_model_files(b)


def texture_names(b: Ben10Block) -> list[str]:
    if b.model == "bottom_top":
        return [b.name, f"{b.name}_top", f"{b.name}_bottom"]
    return gpc._texture_names(b)


def worldgen() -> dict[Path, dict]:
    configured = {"type": "minecraft:ore", "config": {"size": 4, "discard_chance_on_air_exposure": 0.3, "targets": [
        {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"},
         "state": {"Name": f"{MOD}:corrodium_ore"}}]}}
    placed = {"feature": f"{MOD}:ore_corrodium", "placement": [
        {"type": "minecraft:count", "count": 5},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                      "min_inclusive": {"absolute": 10}, "max_inclusive": {"absolute": 90}}},
        {"type": "minecraft:biome"}]}
    return {DATA / "worldgen" / "configured_feature" / "ore_corrodium.json": configured,
            DATA / "worldgen" / "placed_feature" / "ore_corrodium.json": placed}


def recipes() -> dict[str, dict]:
    m_ = lambda n: f"{MOD}:{n}"  # noqa: E731
    r: dict[str, dict] = {
        "corrodium_crystal_from_smelting": gpc.cooking("smelting", m_("raw_corrodium"), m_("corrodium_crystal"), 1.0, 200),
        "corrodium_crystal_from_blasting": gpc.cooking("blasting", m_("raw_corrodium"), m_("corrodium_crystal"), 1.0, 100),
        "corrodium_block": gpc.shaped(["XXX", "XXX", "XXX"], {"X": m_("corrodium_crystal")}, m_("corrodium_block"), category="building"),
        "corrodium_crystal_from_block": gpc.shapeless([m_("corrodium_block")], m_("corrodium_crystal"), 9),
        "omnitrix_workbench": gpc.shaped(["CCC", "ITI", "III"], {"C": m_("corrodium_crystal"), "I": "minecraft:iron_ingot",
                                                                "T": "minecraft:crafting_table"}, m_("omnitrix_workbench")),
        "omnitrix_stabilizer": gpc.shaped(["ICI", "CEC", "ICI"], {"C": m_("corrodium_crystal"), "I": "minecraft:iron_ingot",
                                                                 "E": "minecraft:ender_pearl"}, m_("omnitrix_stabilizer")),
        "energy_core": gpc.shaped([" C ", "CEC", " C "], {"C": m_("corrodium_crystal"), "E": "minecraft:ender_eye"}, m_("energy_core")),
    }
    for color, (_, _, dye) in CORE_NAMES.items():
        r[f"energy_core_{color}"] = gpc.shapeless([m_("energy_core"), dye], m_(f"energy_core_{color}"))
    return r


def _entry(item: str, weight: int, low: int = 1, high: int = 1) -> dict:
    e: dict = {"type": "minecraft:item", "name": item, "weight": weight}
    if high > 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]
    return e


# Truhen im Klempner-Wrack (Bereich -> Wuerfe min/max, Eintraege); Bau in world/plumber/PlumberShipBuilder.java
SHIP_LOOT = {
    "quarters": ((3, 5), [("minecraft:bread", 10, 2, 5), ("minecraft:cooked_beef", 8, 1, 4), ("minecraft:golden_carrot", 4, 1, 3),
                          ("minecraft:paper", 6, 2, 6), ("minecraft:book", 4, 1, 2), ("kingdomomnitrix:hi_potion", 3, 1, 2),
                          ("minecraft:compass", 2), ("minecraft:clock", 2), ("minecraft:emerald", 3, 1, 4)]),
    "armory": ((4, 6), [("minecraft:iron_ingot", 10, 2, 7), ("minecraft:arrow", 8, 8, 24), ("minecraft:crossbow", 3),
                        ("minecraft:iron_chestplate", 2), ("minecraft:iron_helmet", 2), ("minecraft:iron_sword", 3),
                        ("minecraft:tnt", 3, 1, 3), ("minecraft:diamond", 2, 1, 2), ("minecraft:shield", 3),
                        ("minecraft:golden_apple", 1)]),
    "lab": ((3, 5), [(f"{MOD}:corrodium_crystal", 8, 1, 4), (f"{MOD}:raw_corrodium", 8, 2, 5), (f"{MOD}:energy_core", 3),
                     ("minecraft:ender_pearl", 6, 1, 3), ("minecraft:redstone", 8, 4, 12), ("minecraft:glowstone_dust", 6, 2, 8),
                     ("minecraft:experience_bottle", 5, 2, 6), ("minecraft:amethyst_shard", 5, 2, 6)]),
    "cargo": ((5, 8), [("minecraft:iron_ingot", 10, 2, 8), ("minecraft:copper_ingot", 10, 4, 12), ("minecraft:gold_ingot", 6, 1, 5),
                       ("minecraft:coal", 8, 4, 12), ("minecraft:redstone", 6, 4, 10), ("minecraft:quartz", 5, 2, 8),
                       (f"{MOD}:raw_corrodium", 3, 1, 3), ("minecraft:emerald", 3, 1, 3), ("minecraft:name_tag", 1)]),
    "engineering": ((3, 5), [(f"{MOD}:corrodium_crystal", 8, 2, 5), (f"{MOD}:corrodium_block", 1), (f"{MOD}:energy_core", 4),
                             ("minecraft:ender_eye", 3, 1, 2), ("minecraft:blaze_rod", 4, 1, 3), ("minecraft:redstone_block", 4, 1, 3),
                             ("minecraft:iron_block", 2), ("minecraft:diamond", 2, 1, 2)]),
}


def ship_loot() -> dict[Path, dict]:
    out = {}
    for area, ((low, high), entries) in SHIP_LOOT.items():
        pool = {"rolls": {"type": "minecraft:uniform", "min": low, "max": high}, "bonus_rolls": 0,
                "entries": [_entry(*e) for e in entries]}
        out[DATA / "loot_table" / "chests" / f"plumber_ship_{area}.json"] = {
            "type": "minecraft:chest", "pools": [pool], "random_sequence": f"{MOD}:chests/plumber_ship_{area}"}
    return out


def our_tags() -> dict[str, dict[str, list[str]]]:
    block: dict[str, list[str]] = {}
    item: dict[str, list[str]] = {}
    for b in BLOCKS:
        bid = f"{MOD}:{b.name}"
        if b.tool:
            block.setdefault(f"minecraft:mineable/{b.tool}", []).append(bid)
        if b.tier:
            block.setdefault(f"minecraft:needs_{b.tier}_tool", []).append(bid)
        if "beacon" in b.tags:
            block.setdefault("minecraft:beacon_base_blocks", []).append(bid)
    item[f"{MOD}:energy_cores"] = [f"{MOD}:energy_core_{c}" for c in CORE_NAMES]
    return {"block": block, "item": item}


def translations() -> dict[str, dict[str, str]]:
    de = {"itemGroup.kingdomomnitrix.ben10": "Kingdom Omnitrix – Ben 10"}
    en = {"itemGroup.kingdomomnitrix.ben10": "Kingdom Omnitrix – Ben 10"}
    for b in BLOCKS:
        de[f"block.{MOD}.{b.name}"] = b.de
        en[f"block.{MOD}.{b.name}"] = b.en
    for i in ITEMS:
        de[f"item.{MOD}.{i.name}"] = i.de
        en[f"item.{MOD}.{i.name}"] = i.en
    for name, (d, e) in TOOLTIPS.items():
        de[f"item.{MOD}.{name}.tooltip"] = d
        en[f"item.{MOD}.{name}.tooltip"] = e
    return {"de_de": de, "en_us": en}


# --- Java -------------------------------------------------------------------------------------------------------

def java_blocks() -> str:
    lines = [HEADER, "package com.santiq.kingdomomnitrix.world.content;", "",
             "import com.santiq.kingdomomnitrix.KingdomOmnitrix;",
             "import net.minecraft.block.AbstractBlock;",
             "import net.minecraft.block.Block;",
             "import net.minecraft.block.ExperienceDroppingBlock;",
             "import net.minecraft.block.MapColor;",
             "import net.minecraft.block.enums.NoteBlockInstrument;",
             "import net.minecraft.registry.Registries;",
             "import net.minecraft.registry.Registry;",
             "import net.minecraft.sound.BlockSoundGroup;",
             "import net.minecraft.util.math.intprovider.UniformIntProvider;", "",
             "/** Bloecke der Ben-10-Story (Corrodium, Omnitrix-Werkbank, Klempner-Technik). */",
             "public final class Ben10Blocks {"]
    for b in BLOCKS:
        ctor = b.ctor or gpc.java_block_ctor(b)
        lines.append(f"\t/** {b.de} */")
        lines.append(f"\tpublic static final Block {gpc.const(b.name)} = register(\"{b.name}\", {ctor});")
    lines += ["",
              "\tprivate Ben10Blocks() {",
              "\t}",
              "",
              "\tpublic static void register() {",
              "\t\tKingdomOmnitrix.LOGGER.debug(\"Ben-10-Bloecke registriert: {}\", Registries.BLOCK.getId(CORRODIUM_ORE));",
              "\t}",
              "",
              "\tprivate static Block register(String name, Block block) {",
              "\t\treturn Registry.register(Registries.BLOCK, KingdomOmnitrix.id(name), block);",
              "\t}",
              "",
              "\tprivate static AbstractBlock.Settings base(MapColor color, float hardness, float resistance, BlockSoundGroup sound, int light,",
              "\t\t\tboolean tool) {",
              "\t\tAbstractBlock.Settings settings = AbstractBlock.Settings.create().mapColor(color).strength(hardness, resistance).sounds(sound)",
              "\t\t\t\t.luminance(state -> light);",
              "\t\treturn tool ? settings.requiresTool() : settings;",
              "\t}",
              "}", ""]
    text = "\n".join(lines)
    # Standard-Konstruktoren des Planeten-Generators nutzen dieselbe base(...)-Hilfe; NoteBlockInstrument nur fuer Erze
    if "NoteBlockInstrument" not in text.split("public final class", 1)[1]:
        text = text.replace("import net.minecraft.block.enums.NoteBlockInstrument;\n", "")
    if "ExperienceDroppingBlock(" not in text:
        text = text.replace("import net.minecraft.block.ExperienceDroppingBlock;\n", "")
        text = text.replace("import net.minecraft.util.math.intprovider.UniformIntProvider;\n", "")
    return text


def java_items() -> str:
    lines = [HEADER, "package com.santiq.kingdomomnitrix.world.content;", "",
             "import com.santiq.kingdomomnitrix.KingdomOmnitrix;",
             "import com.santiq.kingdomomnitrix.registry.ModItems;",
             "import java.util.ArrayList;",
             "import java.util.LinkedHashMap;",
             "import java.util.List;",
             "import java.util.Map;",
             "import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;",
             "import net.minecraft.item.BlockItem;",
             "import net.minecraft.item.Item;",
             "import net.minecraft.item.ItemGroup;",
             "import net.minecraft.item.ItemStack;",
             "import net.minecraft.registry.Registries;",
             "import net.minecraft.registry.Registry;",
             "import net.minecraft.text.Text;",
             "import net.minecraft.util.Rarity;", "",
             "/** Items der Ben-10-Story: Corrodium, Omnitrix-Teile, Energiekerne (eigener Kreativ-Reiter). */",
             "public final class Ben10Items {",
             "\tprivate static final List<Item> ALL = new ArrayList<>();", ""]
    for b in BLOCKS:
        if b.item:
            lines.append(f"\tpublic static final Item {gpc.const(b.name)} = register(\"{b.name}\", new BlockItem(Ben10Blocks.{gpc.const(b.name)}, "
                         f"new Item.Settings()));")
    lines.append("")
    for i in ITEMS:
        tip = f"\"item.{MOD}.{i.name}.tooltip\"" if i.name in TOOLTIPS else "null"
        lines.append(f"\tpublic static final Item {gpc.const(i.name)} = register(\"{i.name}\", new Ben10Item(new Item.Settings()"
                     f".maxCount({i.max_count}).rarity(Rarity.{i.rarity}), {tip}));")
    lines += ["",
              "\t/** Farbige Energiekerne und ihre Omnitrix-Farbe (siehe OmnitrixColors). */",
              "\tpublic static final Map<Item, String> CORE_COLORS = coreColors();",
              "",
              "\t/** Eigener Kreativ-Reiter mit allen Ben-10-Inhalten. */",
              "\tpublic static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, KingdomOmnitrix.id(\"ben10\"),",
              "\t\t\tFabricItemGroup.builder()",
              "\t\t\t\t\t.icon(() -> new ItemStack(OMNITRIX_CONSOLE))",
              "\t\t\t\t\t.displayName(Text.translatable(\"itemGroup.kingdomomnitrix.ben10\"))",
              "\t\t\t\t\t.entries((context, entries) -> {",
              "\t\t\t\t\t\tentries.add(ModItems.OMNITRIX);",
              "\t\t\t\t\t\tALL.forEach(entries::add);",
              "\t\t\t\t\t})",
              "\t\t\t\t\t.build());",
              "",
              "\tprivate Ben10Items() {",
              "\t}",
              "",
              "\tpublic static void register() {",
              "\t\tKingdomOmnitrix.LOGGER.debug(\"Ben-10-Items registriert: {}\", ALL.size());",
              "\t}",
              "",
              "\tpublic static List<Item> all() {",
              "\t\treturn List.copyOf(ALL);",
              "\t}",
              "",
              "\tprivate static Map<Item, String> coreColors() {",
              "\t\tMap<Item, String> map = new LinkedHashMap<>();"]
    for color in CORE_NAMES:
        lines.append(f"\t\tmap.put(ENERGY_CORE_{color.upper()}, \"{color}\");")
    lines += ["\t\treturn Map.copyOf(map);",
              "\t}",
              "",
              "\tprivate static Item register(String name, Item item) {",
              "\t\tItem registered = Registry.register(Registries.ITEM, KingdomOmnitrix.id(name), item);",
              "\t\tALL.add(registered);",
              "\t\treturn registered;",
              "\t}",
              "}", ""]
    return "\n".join(lines)


# --- Schreiben --------------------------------------------------------------------------------------------------

def outputs(with_images: bool = True) -> dict[Path, object]:
    files: dict[Path, object] = {}
    for b in BLOCKS:
        files[ASSETS / "blockstates" / f"{b.name}.json"] = gpc.blockstate(b)
        for name, model in block_models(b).items():
            files[ASSETS / "models" / "block" / f"{name}.json"] = model
        if b.item:
            files[ASSETS / "models" / "item" / f"{b.name}.json"] = gpc.item_model(b)
        table = gpc.loot_table(b)
        if table is not None:
            files[DATA / "loot_table" / "blocks" / f"{b.name}.json"] = table
        if with_images:
            for tname, img in b.textures().items():
                files[ASSETS / "textures" / "block" / f"{tname}.png"] = img
    if with_images:
        for tname, fn in REACTOR_TEXTURES.items():
            files[ASSETS / "textures" / "entity" / "omnitrix_reactor" / f"{tname}.png"] = fn()
    for i in ITEMS:
        files[ASSETS / "models" / "item" / f"{i.name}.json"] = {"parent": "minecraft:item/generated",
                                                                "textures": {"layer0": f"{MOD}:item/{i.name}"}}
        if with_images:
            files[ASSETS / "textures" / "item" / f"{i.name}.png"] = i.texture()
    for name, recipe in recipes().items():
        files[DATA / "recipe" / f"{name}.json"] = recipe
    files.update(worldgen())
    files.update(ship_loot())
    all_ours = {f"{MOD}:{b.name}" for b in BLOCKS} | {f"{MOD}:{i.name}" for i in ITEMS}
    for kind, entries in our_tags().items():
        for tag, values in entries.items():
            path = gpc.tag_path(kind, tag)
            files[path] = gpc.merged_tag(path, values, all_ours)
    files[JAVA / "Ben10Blocks.java"] = java_blocks()
    files[JAVA / "Ben10Items.java"] = java_items()
    return files


def write_lang() -> None:
    for code, entries in translations().items():
        path = ASSETS / "lang" / f"{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(entries)
        path.write_text(gpc.json_text(data), encoding="utf-8")


def contact_sheet(path: Path) -> None:
    tiles = [img for b in BLOCKS for img in b.textures().values()]
    tiles += [i.texture().resize((128, 128), Image.NEAREST) for i in ITEMS]
    cols = 8
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 132, rows * 132), (40, 40, 48, 255))
    for n, img in enumerate(tiles):
        bg = Image.new("RGBA", (128, 128), (90, 90, 100, 255))
        bg.alpha_composite(img.convert("RGBA"))
        sheet.paste(bg, ((n % cols) * 132 + 2, (n // cols) * 132 + 2))
    sheet.save(path)


def check() -> int:
    errors = 0
    for path, content in outputs(with_images=False).items():
        if not path.is_file():
            LOG.error("fehlt: %s", path.relative_to(ROOT))
            errors += 1
        elif path.suffix == ".java" and path.read_text(encoding="utf-8") != content:
            LOG.error("veraltet (Generator neu laufen lassen): %s", path.relative_to(ROOT))
            errors += 1
    textures = [f"textures/block/{n}.png" for b in BLOCKS for n in texture_names(b)]
    textures += [f"textures/item/{i.name}.png" for i in ITEMS]
    textures += [f"textures/entity/omnitrix_reactor/{n}.png" for n in REACTOR_TEXTURES]
    for rel in textures:
        p = ASSETS / rel
        if not p.is_file():
            LOG.error("fehlt: %s", rel)
            errors += 1
    lang = {code: json.loads((ASSETS / "lang" / f"{code}.json").read_text(encoding="utf-8")) for code in ("de_de", "en_us")}
    for code, entries in translations().items():
        for key in entries:
            if key not in lang[code]:
                LOG.error("Uebersetzung fehlt (%s): %s", code, key)
                errors += 1
    LOG.info("Ben-10-Inhalte: %d Bloecke, %d Items, %d Fehler", len(BLOCKS), len(ITEMS), errors)
    return 1 if errors else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen (Java aktuell, alle Dateien vorhanden)")
    parser.add_argument("--sheet", type=Path, help="Uebersicht aller Texturen als PNG schreiben")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if args.sheet:
        contact_sheet(args.sheet)
        return 0
    if args.check:
        return check()
    try:
        for path, content in outputs().items():
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(content, Image.Image):
                content.save(path, optimize=True)
            elif isinstance(content, str):
                path.write_text(content, encoding="utf-8")
            else:
                path.write_text(gpc.json_text(content), encoding="utf-8")
        write_lang()
    except OSError as exc:
        LOG.error("Schreiben fehlgeschlagen: %s", exc)
        return 1
    LOG.info("Ben-10-Inhalte geschrieben: %d Bloecke, %d Items, %d Rezepte", len(BLOCKS), len(ITEMS), len(recipes()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
