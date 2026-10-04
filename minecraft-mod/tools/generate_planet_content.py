#!/usr/bin/env python3
"""Eigene Bloecke, Erze, Materialien, Werkzeuge, Waffen und Ruestungen der Ratchet-&-Clank-Welten.

Einzige Quelle fuer alles, was die Planeten an Inhalten brauchen. Erzeugt:
  * Texturen: Bloecke 128x128 (nahtlos), Items 64x64, Ruestungsschichten 128x64 (tools/planet_textures.py)
  * Blockzustaende, Block- und Item-Modelle, Loot-Tabellen, Rezepte, Tags
  * Uebersetzungen (de_de, en_us; bestehende Schluessel bleiben erhalten)
  * Java: world/content/PlanetBlocks.java und PlanetItems.java (Registrierung; nicht von Hand aendern)

Welten und ihre Bloecke:
  Veldin       Duenensand, Schichtsandstein, Veldin-Fels, Duerrstrauch; Erz Sonnenkupfer
  Kerwan       Kerwan-Rasen, Kerwan-Erde, Schiefer, Spiralbaum (Stamm, Bretter, Laub), Lichtschilf; Erz Azurit
  Novalis      Novalis-Moos, Lehm, Kalkstein, Glockenbaum, Sternbluete; Erz Viridium
  Rilgar       Riffsand, Korallenfels, Mangrove, Seegras; Erz Aquarin
  Torren IV    Rotstaub, Brandkruste, Saeulenbasalt, Glutdorn; Erz Pyronit
  Technik      Rumpfplatten (hell, dunkel, Gadgetron-Orange), Warnplatte, Panzerglas, Lichtpaneel, Gitterrost,
               Stationsplatten und -leisten (Nefarious), Landefeld und Markierung, Planetenkern (unzerstoerbar)

Materialien (Erz -> Rohstoff -> Barren -> Block, Werkzeuge, Schwert, Ruestung), aufsteigend:
  Sonnenkupfer (Eisen-Stufe) < Azurit < Viridium (Diamant-Stufe) < Aquarin < Pyronit (Netherit-Stufe)

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_planet_content.py            # alles schreiben
    python tools/generate_planet_content.py --check    # Java aktuell? Dateien vorhanden?
    python tools/generate_planet_content.py --sheet out.png   # Uebersicht aller Texturen
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

sys.path.insert(0, str(Path(__file__).resolve().parent))
import planet_textures as tx  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow numpy")

LOG = logging.getLogger("planet_content")
MOD = "kingdomomnitrix"
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / MOD
DATA = RES / "data" / MOD
MC_TAGS = RES / "data" / "minecraft" / "tags"
JAVA = ROOT / "src" / "main" / "java" / "com" / "santiq" / MOD / "world" / "content"
H = tx.hexc


# --- Definitionen -----------------------------------------------------------------------------------------------

@dataclass
class BlockDef:
    name: str
    de: str
    en: str
    kind: str                    # block, natural, turf, pillar, leaves, plant, ore, glass, light, grate, core, storage
    textures: Callable[[], dict[str, Image.Image]]
    map_color: str = "STONE_GRAY"
    hardness: float = 1.5
    resistance: float = 6.0
    sound: str = "STONE"
    tool: str | None = "pickaxe"  # pickaxe, shovel, axe, hoe oder None
    tier: str | None = None       # stone, iron, diamond
    light: int = 0
    drop: str = "self"           # self, ore:<item>, silk, shears, none
    xp: tuple[int, int] = (0, 0)
    planet: str = "tech"
    tags: list[str] = field(default_factory=list)


@dataclass
class Material:
    name: str
    de: str
    en: str
    colors: tuple                # dunkel, mitte, hell
    accent: tuple
    ore_planet: str
    tool_tier: str               # Java-Konstante fuer getInverseTag
    durability: int
    speed: float
    attack: float
    enchant: int
    armor: tuple[int, int, int, int]   # Helm, Brust, Beine, Stiefel
    armor_multiplier: int
    toughness: float
    knockback: float
    ore_tier: str


MATERIALS = [
    Material("solar_copper", "Sonnenkupfer", "Solar Copper", (H("7A3010"), H("E07A2A"), H("FFD090")), H("FFE07A"),
             "veldin", "INCORRECT_FOR_IRON_TOOL", 320, 6.5, 2.0, 16, (2, 6, 5, 2), 16, 0.5, 0.0, "stone"),
    Material("azurite", "Azurit", "Azurite", (H("102A66"), H("2A6AE0"), H("A8D8FF")), H("7AF0FF"),
             "kerwan", "INCORRECT_FOR_IRON_TOOL", 540, 7.0, 2.5, 18, (3, 6, 5, 2), 20, 1.0, 0.0, "iron"),
    Material("viridium", "Viridium", "Viridium", (H("0A4A2A"), H("20C070"), H("B8FFD8")), H("FFF27A"),
             "novalis", "INCORRECT_FOR_DIAMOND_TOOL", 1050, 8.0, 3.0, 12, (3, 7, 6, 3), 28, 2.0, 0.0, "iron"),
    Material("aquarine", "Aquarin", "Aquarine", (H("0A5060"), H("30D0E0"), H("E8FFFF")), H("FFFFFF"),
             "rilgar", "INCORRECT_FOR_DIAMOND_TOOL", 1500, 8.5, 3.5, 20, (3, 8, 6, 3), 33, 2.5, 0.05, "diamond"),
    Material("pyronite", "Pyronit", "Pyronite", (H("5A0806"), H("FF4A1A"), H("FFD488")), H("FFE08A"),
             "torren_iv", "INCORRECT_FOR_NETHERITE_TOOL", 2100, 9.5, 4.5, 15, (4, 9, 7, 4), 38, 3.5, 0.1, "diamond"),
]

TOOLS = [("sword", "Schwert", "Sword"), ("pickaxe", "Spitzhacke", "Pickaxe"), ("axe", "Axt", "Axe"), ("shovel", "Schaufel", "Shovel")]
ARMOR = [("helmet", "Helm", "Helmet", "HELMET"), ("chestplate", "Brustpanzer", "Chestplate", "CHESTPLATE"),
         ("leggings", "Beinschutz", "Leggings", "LEGGINGS"), ("boots", "Stiefel", "Boots", "BOOTS")]


def _cache(fn):
    store = {}

    def wrapper():
        if "v" not in store:
            store["v"] = fn()
        return store["v"]
    return wrapper


# Texturen (zwischengespeichert, weil Erze und Grasseiten auf Stein- und Erdtexturen aufbauen)
VELDIN_SAND = _cache(lambda: tx.sand("veldin_sand", H("8A4A20"), H("D08A4A"), H("F6C784")))
VELDIN_SANDSTONE = _cache(lambda: tx.strata("veldin_sandstone", [H("B8642E"), H("D98A4A"), H("8F4A26"), H("E8B070"),
                                                                    H("C4703A"), H("7A3C22"), H("D4A060")]))
VELDIN_ROCK = _cache(lambda: tx.rock("veldin_rock", H("2E1812"), H("6E3A26"), H("A8683E"), cells=16))
KERWAN_SOIL = _cache(lambda: tx.soil("kerwan_soil", H("1E1824"), H("44384A"), H("6E5E70"), H("8A8098")))
KERWAN_TURF = _cache(lambda: tx.grass_top("kerwan_turf_top", H("124A4C"), H("2A8A7E"), H("86D8B8")))
KERWAN_SLATE = _cache(lambda: tx.slate("kerwan_slate", H("1E2432"), H("44506A"), H("8090AC")))
NOVALIS_LOAM = _cache(lambda: tx.soil("novalis_loam", H("2E1E10"), H("6A4A28"), H("9C7A48"), H("C8B488")))
NOVALIS_MOSS = _cache(lambda: tx.grass_top("novalis_moss_top", H("1E4A12"), H("58A02A"), H("C0EA60"),
                                           flowers=[H("FF78C0"), H("FFE36A"), H("FFFFFF")]))
NOVALIS_LIMESTONE = _cache(lambda: tx.porous("novalis_limestone", H("8A8468"), H("CFC6A4"), H("F4EED6"), holes=24, hole_size=4.0))
RILGAR_SAND = _cache(lambda: tx.sand("rilgar_sand", H("8AB0A8"), H("D2EAE0"), H("FFFFFF"), ripples=0.7))
RILGAR_CORAL = _cache(lambda: tx.porous("rilgar_coralstone", H("103E4A"), H("2A8A9A"), H("8AE0DA"), holes=34, hole_size=5.5))
TORREN_DUST = _cache(lambda: tx.sand("torren_dust", H("4A140C"), H("9A3A20"), H("D87040"), ripples=0.5))
TORREN_CRUST = _cache(lambda: tx.rock("torren_crust", H("3A120A"), H("8A3020"), H("C85A32"), cells=9, crack=1.4))
TORREN_BASALT = _cache(lambda: tx.basalt("torren_basalt", H("120E10"), H("3E2E2E"), H("FF5A1A")))


def turf_textures(name: str, top, soil) -> dict[str, Image.Image]:
    return {f"{name}_top": top(), f"{name}_side": tx.grass_side(f"{name}_side", top(), soil()), f"{name}_bottom": soil()}


def log_textures(name: str, bark, wood_dark, wood_light):
    return lambda: {name: tx.bark(name, *bark), f"{name}_top": tx.log_top(f"{name}_top", bark[1], wood_dark, wood_light)}


def single(name: str, fn: Callable[[], Image.Image]):
    return lambda: {name: fn()}


BLOCKS: list[BlockDef] = [
    # Veldin
    BlockDef("veldin_sand", "Veldin-Duenensand", "Veldin Dune Sand", "natural", single("veldin_sand", VELDIN_SAND),
             "ORANGE", 0.5, 0.5, "SAND", "shovel", planet="veldin", tags=["alien_soil"]),
    BlockDef("veldin_sandstone", "Schichtsandstein", "Layered Sandstone", "natural", single("veldin_sandstone", VELDIN_SANDSTONE),
             "TERRACOTTA_ORANGE", 1.2, 4.0, "STONE", planet="veldin", tags=["planet_stone"]),
    BlockDef("veldin_rock", "Veldin-Fels", "Veldin Rock", "natural", single("veldin_rock", VELDIN_ROCK),
             "TERRACOTTA_RED", 1.6, 6.0, "STONE", planet="veldin", tags=["planet_stone"]),
    BlockDef("veldin_scrub", "Duerrstrauch", "Dry Scrub", "plant",
             single("veldin_scrub", lambda: tx.plant("veldin_scrub", H("8A6234"), H("A88A4A"), None, "thorn")),
             "ORANGE", 0.0, 0.0, "GRASS", None, drop="shears", planet="veldin"),
    # Kerwan
    BlockDef("kerwan_turf", "Kerwan-Rasen", "Kerwan Turf", "turf", lambda: turf_textures("kerwan_turf", KERWAN_TURF, KERWAN_SOIL),
             "CYAN", 0.7, 0.7, "GRASS", "shovel", planet="kerwan", tags=["alien_soil"]),
    BlockDef("kerwan_soil", "Kerwan-Erde", "Kerwan Soil", "natural", single("kerwan_soil", KERWAN_SOIL),
             "PURPLE", 0.6, 0.6, "GRAVEL", "shovel", planet="kerwan", tags=["alien_soil"]),
    BlockDef("kerwan_slate", "Kerwan-Schiefer", "Kerwan Slate", "natural", single("kerwan_slate", KERWAN_SLATE),
             "BLUE", 1.6, 6.0, "DEEPSLATE", planet="kerwan", tags=["planet_stone"]),
    BlockDef("kerwan_log", "Spiralbaum-Stamm", "Spiral Tree Log", "pillar",
             log_textures("kerwan_log", (H("1E1E2E"), H("46466A"), H("7E7EA8")), H("5A4A72"), H("B0A0D0")),
             "PURPLE", 2.0, 2.0, "WOOD", "axe", planet="kerwan", tags=["logs"]),
    BlockDef("kerwan_planks", "Spiralbaum-Bretter", "Spiral Tree Planks", "block",
             single("kerwan_planks", lambda: tx.planks("kerwan_planks", H("4A3E62"), H("7C6CA0"), H("B4A6D6"))),
             "PURPLE", 2.0, 3.0, "WOOD", "axe", planet="kerwan", tags=["planks"]),
    BlockDef("kerwan_leaves", "Spiralbaum-Laub", "Spiral Tree Leaves", "leaves",
             single("kerwan_leaves", lambda: tx.leaves("kerwan_leaves", H("0E4A66"), H("2A9AC0"), H("9AE8FF"), accent=H("E0FFFF"))),
             "CYAN", 0.2, 0.2, "GRASS", "hoe", drop="silk", planet="kerwan", tags=["leaves"]),
    BlockDef("kerwan_reed", "Lichtschilf", "Glow Reed", "plant",
             single("kerwan_reed", lambda: tx.plant("kerwan_reed", H("C8A8F0"), H("2A9A9A"), None, "reed")),
             "CYAN", 0.0, 0.0, "GRASS", None, light=6, drop="shears", planet="kerwan"),
    # Novalis
    BlockDef("novalis_moss", "Novalis-Moos", "Novalis Moss", "turf", lambda: turf_textures("novalis_moss", NOVALIS_MOSS, NOVALIS_LOAM),
             "GREEN", 0.7, 0.7, "MOSS_BLOCK", "shovel", planet="novalis", tags=["alien_soil"]),
    BlockDef("novalis_loam", "Novalis-Lehm", "Novalis Loam", "natural", single("novalis_loam", NOVALIS_LOAM),
             "BROWN", 0.6, 0.6, "ROOTED_DIRT", "shovel", planet="novalis", tags=["alien_soil"]),
    BlockDef("novalis_limestone", "Novalis-Kalkstein", "Novalis Limestone", "natural", single("novalis_limestone", NOVALIS_LIMESTONE),
             "PALE_YELLOW", 1.4, 6.0, "STONE", planet="novalis", tags=["planet_stone"]),
    BlockDef("novalis_log", "Glockenbaum-Stamm", "Bell Tree Log", "pillar",
             log_textures("novalis_log", (H("3A2416"), H("7A5034"), H("B0825A")), H("B88A52"), H("F2D49A")),
             "BROWN", 2.0, 2.0, "WOOD", "axe", planet="novalis", tags=["logs"]),
    BlockDef("novalis_planks", "Glockenbaum-Bretter", "Bell Tree Planks", "block",
             single("novalis_planks", lambda: tx.planks("novalis_planks", H("8A5E30"), H("C8945A"), H("F0CC8E"))),
             "BROWN", 2.0, 3.0, "WOOD", "axe", planet="novalis", tags=["planks"]),
    BlockDef("novalis_leaves", "Glockenbaum-Laub", "Bell Tree Leaves", "leaves",
             single("novalis_leaves", lambda: tx.leaves("novalis_leaves", H("7A1E50"), H("D0589A"), H("FFB8DC"), accent=H("FFE36A"))),
             "PINK", 0.2, 0.2, "CHERRY_LEAVES", "hoe", drop="silk", planet="novalis", tags=["leaves"]),
    BlockDef("novalis_bloom", "Sternbluete", "Star Bloom", "plant",
             single("novalis_bloom", lambda: tx.plant("novalis_bloom", H("2E6A22"), H("58A02A"), H("FF70B8"), "bloom")),
             "PINK", 0.0, 0.0, "GRASS", None, drop="shears", planet="novalis"),
    # Rilgar
    BlockDef("rilgar_sand", "Riffsand", "Reef Sand", "natural", single("rilgar_sand", RILGAR_SAND),
             "WHITE", 0.5, 0.5, "SAND", "shovel", planet="rilgar", tags=["alien_soil"]),
    BlockDef("rilgar_coralstone", "Korallenfels", "Coral Stone", "natural", single("rilgar_coralstone", RILGAR_CORAL),
             "CYAN", 1.4, 6.0, "STONE", planet="rilgar", tags=["planet_stone"]),
    BlockDef("rilgar_log", "Mangroven-Stamm", "Mangrove Trunk", "pillar",
             log_textures("rilgar_log", (H("2A2016"), H("5A4A30"), H("8A7A54")), H("A88A5A"), H("E2CA94")),
             "BROWN", 2.0, 2.0, "WOOD", "axe", planet="rilgar", tags=["logs"]),
    BlockDef("rilgar_planks", "Pfahlholz", "Stilt Planks", "block",
             single("rilgar_planks", lambda: tx.planks("rilgar_planks", H("3E2E1E"), H("6E5436"), H("A28660"))),
             "BROWN", 2.0, 3.0, "WOOD", "axe", planet="rilgar", tags=["planks"]),
    BlockDef("rilgar_leaves", "Mangroven-Laub", "Mangrove Leaves", "leaves",
             single("rilgar_leaves", lambda: tx.leaves("rilgar_leaves", H("0E4A2A"), H("2A9A5A"), H("9AE89A"))),
             "GREEN", 0.2, 0.2, "GRASS", "hoe", drop="silk", planet="rilgar", tags=["leaves"]),
    BlockDef("rilgar_seagrass", "Riffgras", "Reef Grass", "plant",
             single("rilgar_seagrass", lambda: tx.plant("rilgar_seagrass", H("2A8A6A"), H("3AB08A"), None, "tuft")),
             "CYAN", 0.0, 0.0, "GRASS", None, drop="shears", planet="rilgar"),
    # Torren IV
    BlockDef("torren_dust", "Rotstaub", "Red Dust", "natural", single("torren_dust", TORREN_DUST),
             "RED", 0.5, 0.5, "SAND", "shovel", planet="torren_iv", tags=["alien_soil"]),
    BlockDef("torren_crust", "Brandkruste", "Scorched Crust", "natural", single("torren_crust", TORREN_CRUST),
             "TERRACOTTA_RED", 1.0, 3.0, "TUFF", "pickaxe", planet="torren_iv", tags=["alien_soil", "planet_stone"]),
    BlockDef("torren_basalt", "Saeulenbasalt", "Column Basalt", "natural", single("torren_basalt", TORREN_BASALT),
             "BLACK", 1.8, 6.0, "BASALT", light=2, planet="torren_iv", tags=["planet_stone"]),
    BlockDef("torren_thorn", "Glutdorn", "Ember Thorn", "plant",
             single("torren_thorn", lambda: tx.plant("torren_thorn", H("5A1A10"), H("7A2A18"), H("FFB030"), "thorn")),
             "RED", 0.0, 0.0, "GRASS", None, light=4, drop="shears", planet="torren_iv"),
    # Technik
    BlockDef("hull_plate", "Rumpfplatte", "Hull Plate", "block", single("hull_plate", lambda: tx.hull("hull_plate", H("8E949C"))),
             "IRON_GRAY", 3.0, 6.0, "METAL", tier="stone"),
    BlockDef("hull_plate_dark", "Dunkle Rumpfplatte", "Dark Hull Plate", "block",
             single("hull_plate_dark", lambda: tx.hull("hull_plate_dark", H("3C4048"), wear=1.3)), "GRAY", 3.0, 6.0, "METAL", tier="stone"),
    BlockDef("hull_plate_orange", "Gadgetron-Platte", "Gadgetron Plate", "block",
             single("hull_plate_orange", lambda: tx.hull("hull_plate_orange", H("C8682A"), accent=H("F2C230"))),
             "ORANGE", 3.0, 6.0, "METAL", tier="stone"),
    BlockDef("hazard_plate", "Warnplatte", "Hazard Plate", "block", single("hazard_plate", lambda: tx.hazard("hazard_plate")),
             "YELLOW", 3.0, 6.0, "METAL", tier="stone"),
    BlockDef("tech_glass", "Panzerglas", "Armored Glass", "glass",
             single("tech_glass", lambda: tx.glass("tech_glass", H("5A6068"), H("7AD8FF"))), "LIGHT_BLUE", 1.2, 8.0, "GLASS",
             "pickaxe", drop="silk"),
    BlockDef("light_panel", "Lichtpaneel", "Light Panel", "light",
             single("light_panel", lambda: tx.light_panel("light_panel", H("3A3E46"), H("C4F4FF"))), "WHITE", 1.5, 4.0, "GLASS",
             light=15),
    BlockDef("grate", "Gitterrost", "Grate", "grate", single("grate", lambda: tx.grate("grate", H("7A7E86"))),
             "IRON_GRAY", 2.5, 6.0, "METAL", tier="stone"),
    BlockDef("station_plate", "Stationsplatte", "Station Plate", "block",
             single("station_plate", lambda: tx.hex_tiles("station_plate", H("120E1A"), H("3A2A52"), H("B050FF"))),
             "PURPLE", 4.0, 9.0, "NETHERITE", tier="iron"),
    BlockDef("station_trim", "Stationsleiste", "Station Trim", "block",
             single("station_trim", lambda: tx.trim("station_trim", H("24202C"), H("C070FF"))), "PURPLE", 4.0, 9.0, "NETHERITE",
             tier="iron", light=10),
    BlockDef("pad_plate", "Landefeld", "Landing Deck", "block", single("pad_plate", lambda: tx.pad("pad_plate", H("7E8288"))),
             "LIGHT_GRAY", 3.0, 6.0, "METAL", tier="stone"),
    BlockDef("pad_marking", "Landefeld-Markierung", "Landing Marking", "block",
             single("pad_marking", lambda: tx.pad("pad_marking", H("7E8288"), H("F2C230"))), "YELLOW", 3.0, 6.0, "METAL", tier="stone"),
    BlockDef("planet_core", "Planetenkern", "Planet Core", "core", single("planet_core", lambda: tx.magma_core("planet_core")),
             "BLACK", -1.0, 3600000.0, "STONE", None, light=3, drop="none"),
]

# Erze und Speicherbloecke je Material
STONE_OF = {"veldin": VELDIN_ROCK, "kerwan": KERWAN_SLATE, "novalis": NOVALIS_LIMESTONE, "rilgar": RILGAR_CORAL,
            "torren_iv": TORREN_BASALT}
STONE_ID = {"veldin": "veldin_rock", "kerwan": "kerwan_slate", "novalis": "novalis_limestone", "rilgar": "rilgar_coralstone",
            "torren_iv": "torren_basalt"}
for m in MATERIALS:
    stone = STONE_OF[m.ore_planet]
    BLOCKS.append(BlockDef(f"{m.name}_ore", f"{m.de}-Erz", f"{m.en} Ore", "ore",
                           (lambda m=m, stone=stone: {f"{m.name}_ore": tx.ore(f"{m.name}_ore", stone(), *m.colors,
                                                                               style="metal" if m.name == "solar_copper" else "crystal")}),
                           "STONE_GRAY", 3.0, 3.0, "STONE", tier=m.ore_tier, light=5 if m.name == "pyronite" else 0,
                           drop=f"ore:raw_{m.name}", xp=(2, 6), planet=m.ore_planet))
    BLOCKS.append(BlockDef(f"{m.name}_block", f"{m.de}-Block", f"Block of {m.en}", "storage",
                           (lambda m=m: {f"{m.name}_block": tx.metal_block(f"{m.name}_block", *m.colors)}),
                           "ORANGE", 5.0, 6.0, "METAL", tier=m.ore_tier, planet="tech", tags=["beacon"]))

PLANT_SOIL = "alien_soil"


# --- Ausgaben: Assets ---------------------------------------------------------------------------------------------

def block_model_files(b: BlockDef) -> dict[str, dict]:
    tex = lambda t: f"{MOD}:block/{t}"  # noqa: E731
    if b.kind == "turf":
        return {b.name: {"parent": "minecraft:block/cube_bottom_top",
                         "textures": {"top": tex(f"{b.name}_top"), "side": tex(f"{b.name}_side"), "bottom": tex(f"{b.name}_bottom")}}}
    if b.kind == "pillar":
        return {b.name: {"parent": "minecraft:block/cube_column", "textures": {"end": tex(f"{b.name}_top"), "side": tex(b.name)}},
                f"{b.name}_horizontal": {"parent": "minecraft:block/cube_column_horizontal",
                                         "textures": {"end": tex(f"{b.name}_top"), "side": tex(b.name)}}}
    if b.kind == "leaves":
        return {b.name: {"parent": "minecraft:block/leaves", "textures": {"all": tex(b.name)}}}
    if b.kind == "plant":
        return {b.name: {"parent": "minecraft:block/cross", "textures": {"cross": tex(b.name)}},
                f"{b.name}_inventory": {"parent": "minecraft:item/generated", "textures": {"layer0": tex(b.name)}}}
    return {b.name: {"parent": "minecraft:block/cube_all", "textures": {"all": tex(b.name)}}}


def blockstate(b: BlockDef) -> dict:
    model = f"{MOD}:block/{b.name}"
    if b.kind == "pillar":
        return {"variants": {"axis=y": {"model": model}, "axis=z": {"model": f"{model}_horizontal", "x": 90},
                             "axis=x": {"model": f"{model}_horizontal", "x": 90, "y": 90}}}
    if b.kind in ("natural", "turf") and b.sound != "SAND":   # Sand: Rippel laufen ueber Blockgrenzen weiter
        return {"variants": {"": [{"model": model}, {"model": model, "y": 90}, {"model": model, "y": 180}, {"model": model, "y": 270}]}}
    return {"variants": {"": {"model": model}}}


def item_model(b: BlockDef) -> dict:
    if b.kind == "plant":
        return {"parent": f"{MOD}:block/{b.name}_inventory"}
    return {"parent": f"{MOD}:block/{b.name}"}


def silk_condition() -> dict:
    return {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def shears_condition() -> dict:
    return {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}


def loot_table(b: BlockDef) -> dict | None:
    item = f"{MOD}:{b.name}"
    pool: dict
    if b.drop == "none":
        return None
    if b.drop == "self":
        pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}
    elif b.drop == "silk":
        pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item}],
                "conditions": [{"condition": "minecraft:any_of", "terms": [silk_condition(), shears_condition()]}]}
    elif b.drop == "shears":
        pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item}], "conditions": [shears_condition()]}
    else:
        raw = f"{MOD}:{b.drop.split(':', 1)[1]}"
        pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": item, "conditions": [silk_condition()]},
            {"type": "minecraft:item", "name": raw, "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}
    return {"type": "minecraft:block", "pools": [pool], "random_sequence": f"{MOD}:blocks/{b.name}"}


def item_textures() -> dict[str, Image.Image]:
    out = {}
    for m in MATERIALS:
        dark, mid, light = m.colors
        out[f"raw_{m.name}"] = tx.item_raw(f"raw_{m.name}", dark, mid, light)
        out[f"{m.name}_ingot"] = tx.item_ingot(dark, mid, light)
        for t, _, _ in TOOLS:
            out[f"{m.name}_{t}"] = tx.item_tool(t, dark, mid, light, accent=m.accent)
        for a, _, _, _ in ARMOR:
            out[f"{m.name}_{a}"] = tx.item_armor(a, dark, mid, light, accent=m.accent)
    return out


def armor_textures() -> dict[str, Image.Image]:
    out = {}
    for m in MATERIALS:
        out[f"{m.name}_layer_1"] = tx.armor_layer(f"{m.name}_layer_1", *m.colors, m.accent, legs=False)
        out[f"{m.name}_layer_2"] = tx.armor_layer(f"{m.name}_layer_2", *m.colors, m.accent, legs=True)
    return out


def material_items() -> list[tuple[str, str, str]]:
    """(id, de, en) aller Material-Items."""
    items = []
    for m in MATERIALS:
        items.append((f"raw_{m.name}", f"Roh-{m.de}", f"Raw {m.en}"))
        items.append((f"{m.name}_ingot", f"{m.de}-Barren", f"{m.en} Ingot"))
        for t, de, en in TOOLS:
            items.append((f"{m.name}_{t}", f"{m.de}-{de}", f"{m.en} {en}"))
        for a, de, en, _ in ARMOR:
            items.append((f"{m.name}_{a}", f"{m.de}-{de}", f"{m.en} {en}"))
    return items


# --- Rezepte ----------------------------------------------------------------------------------------------------

def shaped(pattern: list[str], key: dict[str, str], result: str, count: int = 1, category: str = "misc") -> dict:
    keys = {}
    for k, v in key.items():
        keys[k] = {"tag": v[1:]} if v.startswith("#") else {"item": v}
    return {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": keys,
            "result": {"id": result, "count": count}}


def shapeless(ingredients: list[str], result: str, count: int = 1) -> dict:
    return {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in ingredients],
            "result": {"id": result, "count": count}}


def cooking(kind: str, ingredient: str, result: str, xp: float, time: int) -> dict:
    return {"type": f"minecraft:{kind}", "category": "misc", "ingredient": {"item": ingredient}, "result": {"id": result},
            "experience": xp, "cookingtime": time}


def recipes() -> dict[str, dict]:
    r: dict[str, dict] = {}
    m_ = lambda n: f"{MOD}:{n}"  # noqa: E731
    stick = "minecraft:stick"
    for m in MATERIALS:
        ingot, raw, block, ore = m_(f"{m.name}_ingot"), m_(f"raw_{m.name}"), m_(f"{m.name}_block"), m_(f"{m.name}_ore")
        r[f"{m.name}_ingot_from_smelting"] = cooking("smelting", raw, ingot, 0.8, 200)
        r[f"{m.name}_ingot_from_blasting"] = cooking("blasting", raw, ingot, 0.8, 100)
        r[f"{m.name}_ingot_from_ore_smelting"] = cooking("smelting", ore, ingot, 0.8, 200)
        r[f"{m.name}_block"] = shaped(["XXX", "XXX", "XXX"], {"X": ingot}, block, category="building")
        r[f"{m.name}_ingot_from_block"] = shapeless([block], ingot, 9)
        r[f"{m.name}_sword"] = shaped(["X", "X", "|"], {"X": ingot, "|": stick}, m_(f"{m.name}_sword"), category="equipment")
        r[f"{m.name}_pickaxe"] = shaped(["XXX", " | ", " | "], {"X": ingot, "|": stick}, m_(f"{m.name}_pickaxe"), category="equipment")
        r[f"{m.name}_axe"] = shaped(["XX", "X|", " |"], {"X": ingot, "|": stick}, m_(f"{m.name}_axe"), category="equipment")
        r[f"{m.name}_shovel"] = shaped(["X", "|", "|"], {"X": ingot, "|": stick}, m_(f"{m.name}_shovel"), category="equipment")
        r[f"{m.name}_helmet"] = shaped(["XXX", "X X"], {"X": ingot}, m_(f"{m.name}_helmet"), category="equipment")
        r[f"{m.name}_chestplate"] = shaped(["X X", "XXX", "XXX"], {"X": ingot}, m_(f"{m.name}_chestplate"), category="equipment")
        r[f"{m.name}_leggings"] = shaped(["XXX", "X X", "X X"], {"X": ingot}, m_(f"{m.name}_leggings"), category="equipment")
        r[f"{m.name}_boots"] = shaped(["X X", "X X"], {"X": ingot}, m_(f"{m.name}_boots"), category="equipment")
    for wood in ("kerwan", "novalis", "rilgar"):
        r[f"{wood}_planks"] = shapeless([m_(f"{wood}_log")], m_(f"{wood}_planks"), 4)
    stone = f"#{MOD}:planet_stone"
    copper = m_("solar_copper_ingot")
    r["hull_plate"] = shaped(["SSS", "SXS", "SSS"], {"S": stone, "X": copper}, m_("hull_plate"), 8, "building")
    r["hull_plate_dark"] = shaped(["HHH", "HBH", "HHH"], {"H": m_("hull_plate"), "B": m_("torren_basalt")}, m_("hull_plate_dark"), 8, "building")
    r["hull_plate_orange"] = shaped(["HHH", "HRH", "HHH"], {"H": m_("hull_plate"), "R": m_("raw_solar_copper")},
                                    m_("hull_plate_orange"), 8, "building")
    r["hazard_plate"] = shaped(["HD", "DH"], {"H": m_("hull_plate_orange"), "D": m_("hull_plate_dark")}, m_("hazard_plate"), 4, "building")
    r["tech_glass_from_veldin_sand"] = cooking("smelting", m_("veldin_sand"), m_("tech_glass"), 0.1, 200)
    r["tech_glass_from_rilgar_sand"] = cooking("smelting", m_("rilgar_sand"), m_("tech_glass"), 0.1, 200)
    r["tech_glass_from_torren_dust"] = cooking("smelting", m_("torren_dust"), m_("tech_glass"), 0.1, 200)
    r["light_panel"] = shaped([" G ", "GAG", " G "], {"G": m_("tech_glass"), "A": m_("azurite_ingot")}, m_("light_panel"), 4, "building")
    r["grate"] = shaped(["X X", " X ", "X X"], {"X": copper}, m_("grate"), 8, "building")
    r["station_plate"] = shaped(["DDD", "DQD", "DDD"], {"D": m_("hull_plate_dark"), "Q": m_("aquarine_ingot")}, m_("station_plate"), 8,
                                "building")
    r["station_trim"] = shaped(["PP", "PA"], {"P": m_("station_plate"), "A": m_("azurite_ingot")}, m_("station_trim"), 4, "building")
    r["pad_plate"] = shaped(["HH", "HH"], {"H": m_("hull_plate")}, m_("pad_plate"), 4, "building")
    r["pad_marking"] = shaped(["PPP", "PRP", "PPP"], {"P": m_("pad_plate"), "R": m_("raw_solar_copper")}, m_("pad_marking"), 8, "building")
    return r


# --- Tags -------------------------------------------------------------------------------------------------------

def our_tags() -> dict[str, dict[str, list[str]]]:
    """{'block'|'item': {tag-pfad: [ids]}}; Pfade mit 'minecraft:' landen im Minecraft-Namensraum."""
    block: dict[str, list[str]] = {}
    item: dict[str, list[str]] = {}

    def add(store, tag, value):
        store.setdefault(tag, [])
        if value not in store[tag]:
            store[tag].append(value)

    for b in BLOCKS:
        bid = f"{MOD}:{b.name}"
        if b.tool:
            add(block, f"minecraft:mineable/{b.tool}", bid)
        if b.tier:
            add(block, f"minecraft:needs_{b.tier}_tool", bid)
        if "logs" in b.tags:
            add(block, "minecraft:logs", bid)
            add(item, "minecraft:logs", bid)
            add(block, "minecraft:logs_that_burn", bid)
            add(item, "minecraft:logs_that_burn", bid)
        if "leaves" in b.tags:
            add(block, "minecraft:leaves", bid)
            add(item, "minecraft:leaves", bid)
        if "planks" in b.tags:
            add(block, "minecraft:planks", bid)
            add(item, "minecraft:planks", bid)
        if "planet_stone" in b.tags:
            add(item, f"{MOD}:planet_stone", bid)
            add(item, "minecraft:stone_crafting_materials", bid)
            add(item, "minecraft:stone_tool_materials", bid)
            add(block, f"{MOD}:planet_stone", bid)
        if "alien_soil" in b.tags:
            add(block, f"{MOD}:{PLANT_SOIL}", bid)
        if "beacon" in b.tags:
            add(block, "minecraft:beacon_base_blocks", bid)
        if b.kind == "plant":
            add(block, "minecraft:replaceable_by_trees", bid)
            add(block, "minecraft:sword_efficient", bid)
    for m in MATERIALS:
        add(item, "minecraft:beacon_payment_items", f"{MOD}:{m.name}_ingot")
        add(item, "minecraft:swords", f"{MOD}:{m.name}_sword")
        add(item, "minecraft:pickaxes", f"{MOD}:{m.name}_pickaxe")
        add(item, "minecraft:axes", f"{MOD}:{m.name}_axe")
        add(item, "minecraft:shovels", f"{MOD}:{m.name}_shovel")
        for a, _, _, _ in ARMOR:
            add(item, {"helmet": "minecraft:head_armor", "chestplate": "minecraft:chest_armor",
                       "leggings": "minecraft:leg_armor", "boots": "minecraft:foot_armor"}[a], f"{MOD}:{m.name}_{a}")
        add(item, "minecraft:trimmable_armor", f"{MOD}:{m.name}_helmet")
    return {"block": block, "item": item}


def tag_path(kind: str, tag: str) -> Path:
    namespace, _, path = tag.partition(":")
    folder = "block" if kind == "block" else "item"
    return RES / "data" / namespace / "tags" / folder / f"{path}.json"


def merged_tag(path: Path, ours: list[str], all_ours: set[str]) -> dict:
    existing: list = []
    if path.is_file():
        try:
            existing = json.loads(path.read_text(encoding="utf-8")).get("values", [])
        except (OSError, json.JSONDecodeError) as exc:
            LOG.warning("Tag %s nicht lesbar (%s), wird neu geschrieben", path, exc)
    kept = [v for v in existing if (v if isinstance(v, str) else v.get("id")) not in all_ours]
    return {"replace": False, "values": kept + ours}


# --- Java -------------------------------------------------------------------------------------------------------

HEADER = "// Erzeugt von tools/generate_planet_content.py — nicht von Hand aendern.\n"


def const(name: str) -> str:
    return name.upper()


def java_blocks() -> str:
    lines = [HEADER, "package com.santiq.kingdomomnitrix.world.content;", "",
             "import com.santiq.kingdomomnitrix.KingdomOmnitrix;",
             "import java.util.List;",
             "import net.minecraft.block.AbstractBlock;",
             "import net.minecraft.block.Block;",
             "import net.minecraft.block.Blocks;",
             "import net.minecraft.block.ExperienceDroppingBlock;",
             "import net.minecraft.block.LeavesBlock;",
             "import net.minecraft.block.MapColor;",
             "import net.minecraft.block.PillarBlock;",
             "import net.minecraft.block.enums.NoteBlockInstrument;",
             "import net.minecraft.block.piston.PistonBehavior;",
             "import net.minecraft.registry.Registries;",
             "import net.minecraft.registry.Registry;",
             "import net.minecraft.sound.BlockSoundGroup;",
             "import net.minecraft.util.math.intprovider.UniformIntProvider;", "",
             "/** Bloecke der Ratchet-&-Clank-Welten (Natur, Erze, Technik). */",
             "public final class PlanetBlocks {"]
    for b in BLOCKS:
        lines.append(f"\t/** {b.de} */")
        lines.append(f"\tpublic static final Block {const(b.name)} = register(\"{b.name}\", {java_block_ctor(b)});")
    cutout = [const(b.name) for b in BLOCKS if b.kind in ("plant", "grate")]
    mipped = [const(b.name) for b in BLOCKS if b.kind == "leaves"]
    translucent = [const(b.name) for b in BLOCKS if b.kind == "glass"]
    lines += ["",
              "\t/** Render-Ebenen fuer den Client (Pflanzen und Gitter mit Loechern, Laub, Glas). */",
              f"\tpublic static final List<Block> CUTOUT = List.of({', '.join(cutout)});",
              f"\tpublic static final List<Block> CUTOUT_MIPPED = List.of({', '.join(mipped)});",
              f"\tpublic static final List<Block> TRANSLUCENT = List.of({', '.join(translucent)});",
              "",
              "\tprivate PlanetBlocks() {",
              "\t}",
              "",
              "\tpublic static void register() {",
              "\t\tKingdomOmnitrix.LOGGER.debug(\"Planeten-Bloecke registriert: {}\", Registries.BLOCK.getId(HULL_PLATE));",
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
    return "\n".join(lines)


def java_block_ctor(b: BlockDef) -> str:
    color = f"MapColor.{b.map_color}"
    sound = f"BlockSoundGroup.{b.sound}"
    needs_tool = b.tier is not None or b.kind in ("ore",) or (b.tool == "pickaxe" and b.kind not in ("glass", "light"))
    settings = f"base({color}, {b.hardness}f, {b.resistance}f, {sound}, {b.light}, {str(needs_tool).lower()})"
    if b.kind == "pillar":
        return f"new PillarBlock({settings}.instrument(NoteBlockInstrument.BASS).burnable())"
    if b.kind == "leaves":
        return f"new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES).mapColor({color}).sounds({sound}))"
    if b.kind == "plant":
        return (f"new AlienPlantBlock(AbstractBlock.Settings.create().mapColor({color}).noCollision().breakInstantly()"
                f".sounds({sound}).luminance(state -> {b.light}).offset(AbstractBlock.OffsetType.XZ).replaceable()"
                f".pistonBehavior(PistonBehavior.DESTROY))")
    if b.kind == "ore":
        return f"new ExperienceDroppingBlock(UniformIntProvider.create({b.xp[0]}, {b.xp[1]}), {settings}.instrument(NoteBlockInstrument.BASEDRUM))"
    if b.kind == "glass":
        return f"new TechGlassBlock(AbstractBlock.Settings.copy(Blocks.GLASS).mapColor({color}).strength({b.hardness}f, {b.resistance}f))"
    if b.kind == "grate":
        return f"new Block({settings}.nonOpaque())"
    if b.kind == "core":
        return (f"new Block(AbstractBlock.Settings.create().mapColor({color}).strength(-1.0f, 3600000.0f).dropsNothing()"
                f".allowsSpawning((state, world, pos, type) -> false).luminance(state -> {b.light}).sounds({sound}))")
    return f"new Block({settings})"


def java_items() -> str:
    lines = [HEADER, "package com.santiq.kingdomomnitrix.world.content;", "",
             "import com.santiq.kingdomomnitrix.KingdomOmnitrix;",
             "import java.util.ArrayList;",
             "import java.util.List;",
             "import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;",
             "import net.minecraft.item.ArmorItem;",
             "import net.minecraft.item.AxeItem;",
             "import net.minecraft.item.BlockItem;",
             "import net.minecraft.item.Item;",
             "import net.minecraft.item.ItemGroup;",
             "import net.minecraft.item.ItemStack;",
             "import net.minecraft.item.MiningToolItem;",
             "import net.minecraft.item.PickaxeItem;",
             "import net.minecraft.item.ShovelItem;",
             "import net.minecraft.item.SwordItem;",
             "import net.minecraft.registry.Registries;",
             "import net.minecraft.registry.Registry;",
             "import net.minecraft.text.Text;",
             "import net.minecraft.util.Rarity;", "",
             "/** Items der Ratchet-&-Clank-Welten: Block-Items, Rohstoffe, Barren, Werkzeuge, Schwerter, Ruestungen. */",
             "public final class PlanetItems {",
             "\tprivate static final List<Item> ALL = new ArrayList<>();", ""]
    for b in BLOCKS:
        lines.append(f"\tpublic static final Item {const(b.name)} = register(\"{b.name}\", new BlockItem(PlanetBlocks.{const(b.name)}, "
                     f"new Item.Settings()));")
    lines.append("")
    rarity = {"solar_copper": "COMMON", "azurite": "COMMON", "viridium": "UNCOMMON", "aquarine": "RARE", "pyronite": "EPIC"}
    for m in MATERIALS:
        tool = f"PlanetMaterials.Tool.{const(m.name)}"
        armor = f"PlanetMaterials.{const(m.name)}_ARMOR"
        r = f"Rarity.{rarity[m.name]}"
        lines.append(f"\tpublic static final Item RAW_{const(m.name)} = register(\"raw_{m.name}\", new Item(new Item.Settings()));")
        lines.append(f"\tpublic static final Item {const(m.name)}_INGOT = register(\"{m.name}_ingot\", new Item(new Item.Settings().rarity({r})));")
        lines.append(f"\tpublic static final Item {const(m.name)}_SWORD = register(\"{m.name}_sword\", new SwordItem({tool}, new Item.Settings()"
                     f".rarity({r}).attributeModifiers(SwordItem.createAttributeModifiers({tool}, 3, -2.4f))));")
        lines.append(f"\tpublic static final Item {const(m.name)}_PICKAXE = register(\"{m.name}_pickaxe\", new PickaxeItem({tool}, "
                     f"new Item.Settings().rarity({r}).attributeModifiers(MiningToolItem.createAttributeModifiers({tool}, 1.0f, -2.8f))));")
        lines.append(f"\tpublic static final Item {const(m.name)}_AXE = register(\"{m.name}_axe\", new AxeItem({tool}, new Item.Settings()"
                     f".rarity({r}).attributeModifiers(MiningToolItem.createAttributeModifiers({tool}, 5.5f, -3.0f))));")
        lines.append(f"\tpublic static final Item {const(m.name)}_SHOVEL = register(\"{m.name}_shovel\", new ShovelItem({tool}, "
                     f"new Item.Settings().rarity({r}).attributeModifiers(MiningToolItem.createAttributeModifiers({tool}, 1.5f, -3.0f))));")
        for a, _, _, typ in ARMOR:
            lines.append(f"\tpublic static final Item {const(m.name)}_{a.upper()} = register(\"{m.name}_{a}\", new ArmorItem({armor}, "
                         f"ArmorItem.Type.{typ}, new Item.Settings().rarity({r}).maxDamage(ArmorItem.Type.{typ}.getMaxDamage({m.armor_multiplier}))));")
        lines.append("")
    lines += ["\t/** Eigener Kreativ-Reiter mit allen Planeten-Inhalten. */",
              "\tpublic static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, KingdomOmnitrix.id(\"planets\"),",
              "\t\t\tFabricItemGroup.builder()",
              "\t\t\t\t\t.icon(() -> new ItemStack(PYRONITE_SWORD))",
              "\t\t\t\t\t.displayName(Text.translatable(\"itemGroup.kingdomomnitrix.planets\"))",
              "\t\t\t\t\t.entries((context, entries) -> ALL.forEach(entries::add))",
              "\t\t\t\t\t.build());",
              "",
              "\tprivate PlanetItems() {",
              "\t}",
              "",
              "\tpublic static void register() {",
              "\t\tKingdomOmnitrix.LOGGER.debug(\"Planeten-Items registriert: {}\", ALL.size());",
              "\t}",
              "",
              "\tpublic static List<Item> all() {",
              "\t\treturn List.copyOf(ALL);",
              "\t}",
              "",
              "\tprivate static Item register(String name, Item item) {",
              "\t\tItem registered = Registry.register(Registries.ITEM, KingdomOmnitrix.id(name), item);",
              "\t\tALL.add(registered);",
              "\t\treturn registered;",
              "\t}",
              "}", ""]
    return "\n".join(lines)


# --- Uebersetzungen -----------------------------------------------------------------------------------------------

def translations() -> dict[str, dict[str, str]]:
    de: dict[str, str] = {"itemGroup.kingdomomnitrix.planets": "Kingdom Omnitrix – Welten"}
    en: dict[str, str] = {"itemGroup.kingdomomnitrix.planets": "Kingdom Omnitrix – Worlds"}
    for b in BLOCKS:
        de[f"block.{MOD}.{b.name}"] = b.de
        en[f"block.{MOD}.{b.name}"] = b.en
    for item, d, e in material_items():
        de[f"item.{MOD}.{item}"] = d
        en[f"item.{MOD}.{item}"] = e
    return {"de_de": de, "en_us": en}


# --- Schreiben ----------------------------------------------------------------------------------------------------

def json_text(data) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def outputs(with_images: bool = True) -> dict[Path, object]:
    files: dict[Path, object] = {}
    for b in BLOCKS:
        files[ASSETS / "blockstates" / f"{b.name}.json"] = blockstate(b)
        for name, model in block_model_files(b).items():
            files[ASSETS / "models" / "block" / f"{name}.json"] = model
        files[ASSETS / "models" / "item" / f"{b.name}.json"] = item_model(b)
        table = loot_table(b)
        if table is not None:
            files[DATA / "loot_table" / "blocks" / f"{b.name}.json"] = table
        if with_images:
            for tname, img in b.textures().items():
                files[ASSETS / "textures" / "block" / f"{tname}.png"] = img
    for item, _, _ in material_items():
        files[ASSETS / "models" / "item" / f"{item}.json"] = {
            "parent": "minecraft:item/handheld" if any(item.endswith(t) for t, _, _ in TOOLS) else "minecraft:item/generated",
            "textures": {"layer0": f"{MOD}:item/{item}"}}
    if with_images:
        for name, img in item_textures().items():
            files[ASSETS / "textures" / "item" / f"{name}.png"] = img
        for name, img in armor_textures().items():
            files[ASSETS / "textures" / "models" / "armor" / f"{name}.png"] = img
    for name, recipe in recipes().items():
        files[DATA / "recipe" / f"{name}.json"] = recipe
    tags = our_tags()
    all_ours = {f"{MOD}:{b.name}" for b in BLOCKS} | {f"{MOD}:{i}" for i, _, _ in material_items()}
    for kind, entries in tags.items():
        for tag, values in entries.items():
            path = tag_path(kind, tag)
            files[path] = merged_tag(path, values, all_ours)
    files[JAVA / "PlanetBlocks.java"] = java_blocks()
    files[JAVA / "PlanetItems.java"] = java_items()
    return files


def write_lang() -> None:
    for code, entries in translations().items():
        path = ASSETS / "lang" / f"{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(entries)
        path.write_text(json_text(data), encoding="utf-8")


def contact_sheet(path: Path) -> None:
    tiles = []
    for b in BLOCKS:
        for name, img in b.textures().items():
            tiles.append((name, img))
    for name, img in item_textures().items():
        tiles.append((name, img.resize((128, 128), Image.NEAREST)))
    cols = 10
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 132, rows * 132), (40, 40, 48, 255))
    for i, (_, img) in enumerate(tiles):
        bg = Image.new("RGBA", (128, 128), (90, 90, 100, 255))
        bg.alpha_composite(img.convert("RGBA"))
        sheet.paste(bg, ((i % cols) * 132 + 2, (i // cols) * 132 + 2))
    sheet.save(path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen (Java aktuell, alle Dateien vorhanden)")
    parser.add_argument("--sheet", type=Path, help="Uebersicht aller Texturen als PNG schreiben")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    if args.sheet:
        contact_sheet(args.sheet)
        LOG.info("Uebersicht: %s", args.sheet)
        return 0

    if args.check:
        errors = 0
        for path, content in outputs(with_images=False).items():
            if not path.is_file():
                LOG.error("fehlt: %s", path.relative_to(ROOT))
                errors += 1
            elif path.suffix == ".java" and path.read_text(encoding="utf-8") != content:
                LOG.error("veraltet (Generator neu laufen lassen): %s", path.relative_to(ROOT))
                errors += 1
        textures = [f"textures/block/{n}.png" for b in BLOCKS for n in _texture_names(b)]
        textures += [f"textures/item/{i}.png" for i, _, _ in material_items()]
        for rel in textures:
            p = ASSETS / rel
            if not p.is_file():
                LOG.error("fehlt: %s", rel)
                errors += 1
            elif "block" in rel:
                with Image.open(p) as img:
                    if img.size[0] < 128 or img.size[1] < 128:
                        LOG.error("Blocktextur kleiner als 128x128: %s %s", rel, img.size)
                        errors += 1
        LOG.info("Planeten-Inhalte: %d Bloecke, %d Items, %d Fehler", len(BLOCKS), len(BLOCKS) + len(material_items()), errors)
        return 1 if errors else 0

    try:
        for path, content in outputs().items():
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(content, Image.Image):
                content.save(path, optimize=True)
            elif isinstance(content, str):
                path.write_text(content, encoding="utf-8")
            else:
                path.write_text(json_text(content), encoding="utf-8")
        write_lang()
    except OSError as exc:
        LOG.error("Schreiben fehlgeschlagen: %s", exc)
        return 1
    LOG.info("Planeten-Inhalte geschrieben: %d Bloecke, %d Material-Items, %d Rezepte", len(BLOCKS), len(material_items()),
             len(recipes()))
    return 0


def _texture_names(b: BlockDef) -> list[str]:
    if b.kind == "turf":
        return [f"{b.name}_top", f"{b.name}_side", f"{b.name}_bottom"]
    if b.kind == "pillar":
        return [b.name, f"{b.name}_top"]
    return [b.name]


if __name__ == "__main__":
    sys.exit(main())
