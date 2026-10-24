#!/usr/bin/env python3
"""Weltgenerierung der Ratchet-&-Clank-Planeten nur aus eigenen Bloecken.

Je Landplanet (Veldin, Kerwan, Novalis, Rilgar, Torren IV):
  * Noise-Einstellungen: Gelaende wie die Oberwelt, aber Grundgestein = Planetenstein, Boden = Planetenkern
    (unzerstoerbar), keine Erzadern und Grundwasserleiter aus Minecraft; Oberflaeche aus Planetenbloecken.
    Landwelten ohne Meer (Becken statt Ozean); Rilgar mit Meer (Wasser ist die einzige Ausnahme).
  * Zwei eigene Biome mit Himmels-/Nebelfarben, Partikeln, Erz-, Baum- und Pflanzen-Features, ohne Hoehlen-Carver
    und ohne Features aus Minecraft.
  * Dimension mit Multi-Noise-Biomquelle.
Die Nefarious-Station bleibt eine Leere (nur die gebaute Station).

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_planet_worlds.py           # schreiben
    python tools/generate_planet_worlds.py --check   # nur pruefen
"""
from __future__ import annotations

import argparse
import copy
import json
import logging
import sys
from pathlib import Path

LOG = logging.getLogger("planet_worlds")
MOD = "kingdomomnitrix"
ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src" / "main" / "resources" / "data" / MOD
TEMPLATE = DATA / "worldgen" / "noise_settings" / "dry_planet.json"
VANILLA = ROOT / "tools" / "reference" / "vanilla_worldgen"


def b(name: str) -> str:
    return name if ":" in name else f"{MOD}:{name}"


def state(name: str, **props) -> dict:
    s = {"Name": b(name)}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


# --- Feature-Baukasten (wie die grossen Weltgenerierungs-Mods: Vanilla-Feature-Typen, geschickt kombiniert) --------
# Jedes Feature: (Generierungsschritt, configured_feature, placed_feature). Schritte wie Vanilla: 1 Seen,
# 2 lokale Veraenderungen (Geoden, Felsbrocken), 6 Erze/Bodenflecken, 8 Quellen, 9 Vegetation.
# Die Reihenfolge der Definition ist die feste Reihenfolge in allen Biomen (Minecraft verlangt eine einheitliche).
KIT: dict[str, tuple[int, dict, dict]] = {}


def simple(name: str, **props) -> dict:
    return {"type": "minecraft:simple_state_provider", "state": state(name, **props)}


def _kit(fid: str, step: int, configured: dict, placement: list[dict]) -> str:
    KIT[fid] = (step, configured, {"feature": b(fid), "placement": placement})
    return fid


def _surface(count: int, heightmap: str = "MOTION_BLOCKING") -> list[dict]:
    return [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": heightmap}]


def _air_on_solid() -> dict:
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
        {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
        {"type": "minecraft:solid", "offset": [0, -1, 0]}]}}


def kit_bundle(fid: str, crystal: str, tip: str, placement: dict, height: tuple[int, int], count: tuple[int, int],
               width: float, lean: float) -> str:
    """Kristallbuendel in Diamondhead-Optik (eigenes Feature kingdomomnitrix:crystal_bundle): schraege, spitze Kristalle."""
    return _kit(fid, 9, {"type": f"{MOD}:crystal_bundle", "config": {
        "crystal": simple(crystal), "tip": simple(tip),
        "count": {"type": "minecraft:uniform", "min_inclusive": count[0], "max_inclusive": count[1]},
        "height": {"type": "minecraft:uniform", "min_inclusive": height[0], "max_inclusive": height[1]},
        "width": width, "lean": lean}},
        [placement, {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
         {"type": "minecraft:biome"}])


def kit_cluster_patch(fid: str, block: str, count: int, tries: int) -> str:
    """Bueschel (Kristallspitzen, Steinchen) verstreut auf festem Boden."""
    return _kit(fid, 9, {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": 6, "y_spread": 2, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": simple(block)}},
        "placement": [_air_on_solid()]}}},
        _surface(count) + [{"type": "minecraft:biome"}])


def kit_boulder(fid: str, block: str, count: int) -> str:
    """Felsbrocken (Vanilla forest_rock)."""
    return _kit(fid, 2, {"type": "minecraft:forest_rock", "config": {"state": state(block)}},
                _surface(count) + [{"type": "minecraft:biome"}])


def kit_geode(fid: str, outer: str, middle: str, inner: str, cluster: str, rarity: int, ymin: int, ymax: int) -> str:
    """Geode wie Amethyst: aussen Gestein, Mittelschicht, Kristallschale mit Buescheln innen."""
    return _kit(fid, 2, {"type": "minecraft:geode", "config": {
        "blocks": {"filling_provider": simple("minecraft:air"), "inner_layer_provider": simple(inner),
                   "alternate_inner_layer_provider": simple(inner), "middle_layer_provider": simple(middle),
                   "outer_layer_provider": simple(outer), "inner_placements": [state(cluster)],
                   "cannot_replace": "#minecraft:features_cannot_replace", "invalid_blocks": "#minecraft:geode_invalid_blocks"},
        "layers": {"filling": 1.7, "inner_layer": 2.2, "middle_layer": 3.2, "outer_layer": 4.2},
        "crack": {"generate_crack_chance": 0.95, "base_crack_size": 2.0, "crack_point_offset": 2},
        "use_potential_placements_chance": 0.35, "use_alternate_layer0_chance": 0.083, "placements_require_layer0_alternate": False,
        "outer_wall_distance": {"type": "minecraft:uniform", "min_inclusive": 4, "max_inclusive": 6},
        "distribution_points": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 4},
        "point_offset": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2},
        "min_gen_offset": -16, "max_gen_offset": 16, "noise_multiplier": 0.05, "invalid_blocks_threshold": 1}},
        [{"type": "minecraft:rarity_filter", "chance": rarity}, {"type": "minecraft:in_square"},
         {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": ymin},
                                                       "max_inclusive": {"absolute": ymax}}},
         {"type": "minecraft:biome"}])


def kit_disk(fid: str, block: str, targets: list[str], count: int, rmin: int, rmax: int) -> str:
    """Bodenfleck: ersetzt die Oberflaeche rund um einen Punkt (wie Kies-/Sandflecken)."""
    return _kit(fid, 6, {"type": "minecraft:disk", "config": {
        "state_provider": {"fallback": simple(block), "rules": []},
        "target": {"type": "minecraft:matching_blocks", "blocks": [b(t) for t in targets]},
        "radius": {"type": "minecraft:uniform", "min_inclusive": rmin, "max_inclusive": rmax}, "half_height": 2}},
        _surface(count, "WORLD_SURFACE_WG") + [{"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1},
                                               {"type": "minecraft:biome"}])


def kit_spring(fid: str, fluid: str, valid: list[str], count: int) -> str:
    """Quelle in Felswaenden (Wasserfaelle)."""
    return _kit(fid, 8, {"type": "minecraft:spring_feature", "config": {
        "state": {"Name": fluid, "Properties": {"falling": "true"}}, "requires_block_below": True, "rock_count": 4,
        "hole_count": 1, "valid_blocks": [b(v) for v in valid]}},
        [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
         {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 50},
                                                       "max_inclusive": {"absolute": 200}}},
         {"type": "minecraft:biome"}])


def carvers(planet: str) -> dict[str, dict]:
    """Hoehlen und Schluchten in den eigenen Gesteinen (Vanilla-Werte, eigene ersetzbare Bloecke)."""
    debug = {"air_state": {"Name": "minecraft:crimson_button", "Properties": {"face": "wall", "facing": "north", "powered": "false"}}}
    cave = {"type": "minecraft:cave", "config": {
        "debug_settings": debug, "floor_level": {"type": "minecraft:uniform", "min_inclusive": -1.0, "max_exclusive": -0.4},
        "horizontal_radius_multiplier": {"type": "minecraft:uniform", "min_inclusive": 0.7, "max_exclusive": 1.4},
        "lava_level": {"above_bottom": 8}, "probability": 0.15, "replaceable": f"#{MOD}:carver_replaceables",
        "vertical_radius_multiplier": {"type": "minecraft:uniform", "min_inclusive": 0.8, "max_exclusive": 1.3},
        "y": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 8}, "max_inclusive": {"absolute": 180}},
        "yScale": {"type": "minecraft:uniform", "min_inclusive": 0.1, "max_exclusive": 0.9}}}
    canyon = {"type": "minecraft:canyon", "config": {
        "debug_settings": debug, "lava_level": {"above_bottom": 8}, "probability": 0.02, "replaceable": f"#{MOD}:carver_replaceables",
        "shape": {"distance_factor": {"type": "minecraft:uniform", "min_inclusive": 0.75, "max_exclusive": 1.0},
                  "horizontal_radius_factor": {"type": "minecraft:uniform", "min_inclusive": 0.75, "max_exclusive": 1.0},
                  "thickness": {"type": "minecraft:trapezoid", "min": 0.0, "max": 6.0, "plateau": 2.0},
                  "vertical_radius_center_factor": 0.0, "vertical_radius_default_factor": 1.0, "width_smoothness": 3},
        "vertical_rotation": {"type": "minecraft:uniform", "min_inclusive": -0.125, "max_exclusive": 0.125},
        "y": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 10}, "max_inclusive": {"absolute": 120}}, "yScale": 3.0}}
    return {f"{planet}_cave": cave, f"{planet}_canyon": canyon}


# Petropia (Pilotwelt): Kristallwelt mit sechs Biomen
PETROPIA_KIT = {
    # Kristalle wie Diamondheads: kleine Buendel verstreut, grosse Buendel selten, auf den Gipfeln dicht
    "spires_sparse": kit_bundle("petropia_bundles_small", "petropia_giant_crystal", "petropia_cluster",
                                {"type": "minecraft:count", "count": 1}, (3, 6), (2, 4), 0.8, 0.35),
    "bundles_giant": kit_bundle("petropia_bundles_giant", "petropia_giant_crystal", "petropia_cluster",
                                {"type": "minecraft:rarity_filter", "chance": 3}, (9, 14), (4, 6), 1.8, 0.4),
    "spires_dense": kit_bundle("petropia_bundles_dense", "petropia_giant_crystal", "petropia_cluster",
                               {"type": "minecraft:count", "count": 3}, (6, 12), (3, 5), 1.3, 0.3),
    "clusters": kit_cluster_patch("petropia_clusters", "petropia_cluster", 3, 20),
    "clusters_dense": kit_cluster_patch("petropia_clusters_dense", "petropia_cluster", 8, 32),
    "boulders": kit_boulder("petropia_boulders", "petropia_rock", 2),
    "disk_smooth": kit_disk("petropia_disk_smooth", "petropia_smooth_rock", ["petropia_shards", "petropia_rock"], 3, 2, 5),
    "disk_shards": kit_disk("petropia_disk_shards", "petropia_shards", ["petropia_rock"], 2, 2, 4),
    "geode_surface": kit_geode("petropia_geode_surface", "petropia_rock", "petropia_smooth_rock", "petropia_giant_crystal",
                               "petropia_giant_crystal", 3, 60, 110),
    "geode_deep": kit_geode("petropia_geode_deep", "petropia_rock", "petropia_smooth_rock", "petropia_giant_crystal",
                            "petropia_giant_crystal", 10, 6, 50),
    "spring": kit_spring("petropia_spring", "minecraft:water", ["petropia_rock", "petropia_smooth_rock"], 18),
}


# Ratchet-Planeten (Welten-Ausbau): Hoehlen und Schluchten, Felsbrocken, Bodenflecken, Quellen und Geoden je Welt,
# damit jede Welt mehr Landschaft hat als Oberflaeche plus Pflanzen (wie Petropia und die grossen Weltgenerierungs-Mods)
VELDIN_KIT = {
    "boulders": kit_boulder("veldin_boulders", "veldin_rock", 1),
    "disk_sandstone": kit_disk("veldin_disk_sandstone", "veldin_sandstone", ["veldin_sand"], 2, 2, 5),
    "geode": kit_geode("veldin_geode", "veldin_rock", "veldin_sandstone", "solar_copper_ore", "solar_copper_ore", 14, 10, 60),
}
KERWAN_KIT = {
    "boulders": kit_boulder("kerwan_boulders", "kerwan_slate", 2),
    "disk_soil": kit_disk("kerwan_disk_soil", "kerwan_soil", ["kerwan_turf"], 1, 2, 4),
    "spring": kit_spring("kerwan_spring", "minecraft:water", ["kerwan_slate"], 14),
}
NOVALIS_KIT = {
    "boulders": kit_boulder("novalis_boulders", "novalis_limestone", 1),
    "disk_loam": kit_disk("novalis_disk_loam", "novalis_loam", ["novalis_moss"], 1, 2, 3),
    "spring": kit_spring("novalis_spring", "minecraft:water", ["novalis_limestone"], 10),
}
RILGAR_KIT = {
    "boulders": kit_boulder("rilgar_boulders", "rilgar_coralstone", 1),
    "disk_coral": kit_disk("rilgar_disk_coral", "rilgar_coralstone", ["rilgar_sand"], 2, 2, 4),
}
TORREN_KIT = {
    "boulders": kit_boulder("torren_boulders", "torren_basalt", 2),
    "disk_crust": kit_disk("torren_disk_crust", "torren_crust", ["torren_dust"], 2, 2, 5),
    "lava_spring": kit_spring("torren_lava_spring", "minecraft:lava", ["torren_basalt"], 8),
}

PLANETS = {
    "veldin": {
        "terrain": "large_biomes",
        "stone": "veldin_rock", "top": "veldin_sand", "under": "veldin_sand", "steep": "veldin_sandstone", "sea": False,
        "ore": "solar_copper", "ore_count": 14, "ore_size": 9, "ore_max": 110,
        "blobs": [("veldin_sandstone", 10, 40)],
        "caves": True, "kit": VELDIN_KIT, "extras": ["boulders", "geode"],
        "biomes": {
            "dunes": {"sky": 0xF0B070, "fog": 0xF2C8A0, "water": 0x3F76E4, "particle": None,
                      "plants": [("veldin_scrub", 2, 24)], "trees": [], "high_top": "veldin_sandstone", "extras": ["disk_sandstone"]},
            "canyons": {"sky": 0xE89860, "fog": 0xE0A880, "water": 0x3F76E4, "particle": ("minecraft:white_ash", 0.004),
                        "plants": [("veldin_scrub", 1, 12)], "trees": [], "top": "veldin_sandstone"},
        },
        "monsters": [("shadow", 60, 1, 3), ("soldier", 25, 1, 2)],
        # Tierwelt (frueher DNA-Quellen; Cannonbolt und Humungousaur kommen jetzt von ihren Heimatwelten)
        "life": {"creature": [("minecraft:armadillo", 12, 2, 3), ("minecraft:camel", 6, 1, 2)]},
    },
    "kerwan": {
        "terrain": "amplified",
        "stone": "kerwan_slate", "top": "kerwan_turf", "under": "kerwan_soil", "steep": "kerwan_slate", "sea": False,
        "ore": "azurite", "ore_count": 12, "ore_size": 8, "ore_max": 90, "blobs": [],
        "caves": True, "kit": KERWAN_KIT, "extras": ["boulders", "spring"],
        "biomes": {
            "meadows": {"sky": 0x7FB8FF, "fog": 0xA8D0F0, "water": 0x3FA0D0, "particle": None,
                        "plants": [("kerwan_reed", 3, 32)], "trees": [("kerwan_spiral_tree", 1)], "extras": ["disk_soil"]},
            "spires": {"sky": 0x6AA0F0, "fog": 0x90C0E8, "water": 0x3FA0D0, "particle": None,
                       "plants": [("kerwan_reed", 2, 24)], "trees": [("kerwan_spiral_tree", 4)]},
        },
        "monsters": [("soldier", 40, 1, 2), ("air_soldier", 25, 1, 2)],
        # DNA-Quelle (Scan): Upgrade — die gepanzerten Soldier-Herzlosen (oben unter "monsters")
    },
    "novalis": {
        "terrain": "overworld",
        "stone": "novalis_limestone", "top": "novalis_moss", "under": "novalis_loam", "steep": "novalis_limestone", "sea": False,
        "ore": "viridium", "ore_count": 10, "ore_size": 7, "ore_max": 70, "blobs": [],
        "caves": True, "kit": NOVALIS_KIT, "extras": ["boulders", "spring"],
        "biomes": {
            "fields": {"sky": 0x9AC8FF, "fog": 0xD8E8F8, "water": 0x4AB0E0, "particle": None,
                       "plants": [("novalis_bloom", 6, 48)], "trees": [("novalis_bell_tree", 1)], "extras": ["disk_loam"]},
            "grove": {"sky": 0xB0B8FF, "fog": 0xF0D8F0, "water": 0x4AB0E0, "particle": ("minecraft:spore_blossom_air", 0.006),
                      "plants": [("novalis_bloom", 3, 32)], "trees": [("novalis_bell_tree", 4)]},
        },
        "monsters": [("shadow", 50, 1, 3)],
        # Tierwelt (Rath, Goop und Swampfire kommen jetzt von ihren Heimatwelten)
        "life": {"creature": [("minecraft:fox", 10, 2, 3)], "monster": [("minecraft:slime", 12, 1, 2), ("minecraft:bogged", 12, 1, 2)]},
        "slime": True,
    },
    "rilgar": {
        "terrain": "overworld", "noise_tweaks": {"continentalness": {"first_octave": 1}},
        "stone": "rilgar_coralstone", "top": "rilgar_sand", "under": "rilgar_sand", "steep": "rilgar_coralstone", "sea": True,
        "ore": "aquarine", "ore_count": 9, "ore_size": 7, "ore_max": 60, "blobs": [],
        "caves": True, "kit": RILGAR_KIT, "extras": ["boulders", "disk_coral"],
        "biomes": {
            "reef": {"sky": 0x78D8F0, "fog": 0xB8F0F8, "water": 0x2AD0C8, "particle": None,
                     "plants": [("rilgar_seagrass", 2, 24)], "trees": []},
            "mangroves": {"sky": 0x80D0E8, "fog": 0xA8E0E8, "water": 0x30B8B0, "particle": None,
                          "plants": [("rilgar_seagrass", 4, 32)], "trees": [("rilgar_mangrove", 4)]},
        },
        "monsters": [("shadow", 30, 1, 2), ("darkball", 10, 1, 1)],
        # Tierwelt (Ripjaws, Jetray und Brainstorm kommen jetzt von ihren Heimatwelten)
        "life": {"water_creature": [("minecraft:dolphin", 4, 1, 2)],
                 "water_ambient": [("minecraft:cod", 15, 3, 5), ("minecraft:tropical_fish", 20, 3, 6)]},
    },
    "torren_iv": {
        "terrain": "amplified",
        "stone": "torren_basalt", "top": "torren_dust", "under": "torren_dust", "steep": "torren_basalt", "sea": False,
        "ore": "pyronite", "ore_count": 8, "ore_size": 6, "ore_max": 50, "blobs": [("torren_crust", 8, 34)],
        "extra_ores": [("infernite_ore", 3, 4, 24)],
        "caves": True, "kit": TORREN_KIT, "extras": ["boulders", "lava_spring"],
        "biomes": {
            "plains": {"sky": 0xD07050, "fog": 0xC88068, "water": 0x8A4A3A, "particle": ("minecraft:ash", 0.02),
                       "plants": [("torren_thorn", 2, 20)], "trees": [], "crust": True, "extras": ["disk_crust"]},
            "wastes": {"sky": 0xB05040, "fog": 0xA86050, "water": 0x8A4A3A, "particle": ("minecraft:ash", 0.04),
                       "plants": [("torren_thorn", 1, 10)], "trees": [], "top": "torren_crust"},
        },
        "monsters": [("large_body", 20, 1, 1), ("darkball", 30, 1, 2), ("shadow", 40, 2, 3)],
        # Tierwelt; Verwuester liefern Way-Big-DNA (Big Chill und Diamondhead kommen von ihren Heimatwelten)
        "life": {"creature": [("minecraft:polar_bear", 5, 1, 2), ("minecraft:allay", 6, 1, 2)],
                 "monster": [("minecraft:stray", 15, 1, 2), ("minecraft:ravager", 3, 1, 1)]},
    },
}


# --- Alien-Heimatwelten (Welle 1) -----------------------------------------------------------------------------------
# Keine Herzlosen: dort leben die wilden Aliens der jeweiligen Art (eigener Spawner, Alien-Definition "dna.species").
ALIEN_WORLDS = {
    "sonorosia": {
        "terrain": "large_biomes",
        "stone": "sonorosia_stone", "top": "sonorosia_sand", "under": "sonorosia_sand", "steep": "sonorosia_stone", "sea": False,
        "ore": None, "blobs": [("sonorosia_resonite", 8, 22)],
        "biomes": {
            "echo_dunes": {"sky": 0x5A3A8A, "fog": 0x8A7AB0, "water": 0x6A5AC8, "particle": ("minecraft:white_ash", 0.004),
                           "plants": [("sonorosia_reed", 3, 32)], "trees": []},
            "resonance_fields": {"sky": 0x3A2A6A, "fog": 0x6A5A98, "water": 0x6A5AC8, "particle": ("minecraft:white_ash", 0.008),
                                 "plants": [("sonorosia_reed", 1, 12)], "trees": [], "top": "sonorosia_stone", "high_top": "sonorosia_resonite"},
        },
        "monsters": [],
    },
    "petropia": {
        "terrain": "amplified", "caves": True,
        "stone": "petropia_rock", "top": "petropia_shards", "under": "petropia_shards", "steep": "petropia_rock", "sea": False,
        "ore": None, "blobs": [("petropia_crystal", 12, 28), ("petropia_smooth_rock", 8, 40)],
        # in allen Biomen: tiefe Geoden, Quellen in Felswaenden
        "extras": ["geode_deep", "spring"], "kit": PETROPIA_KIT,
        "extra_ores": [("taydenite_ore", 6, 6, 70)],
        # Siedlungen der Petrosapiens (Java: world/structure/PetrosapienSettlement): Doerfer auf ebenem Grund, Ruinen ueberall
        "structures": [
            {"id": "petrosapien_village", "type": "petrosapien_settlement", "config": {"ruin": False}, "spacing": 30, "separation": 12,
             "salt": 61803398, "biomes": ["crystal_plains", "crystal_forest", "geode_fields", "shattered_basin"]},
            {"id": "petrosapien_ruin", "type": "petrosapien_settlement", "config": {"ruin": True}, "spacing": 22, "separation": 8,
             "salt": 31415926, "biomes": ["shard_wastes", "geode_fields", "prism_peaks", "shattered_basin"]},
        ],
        "biomes": {
            "crystal_plains": {"sky": 0xB8A8F0, "fog": 0xD8E8F0, "water": 0x3AD0B0, "particle": ("minecraft:end_rod", 0.0015),
                               "plants": [("petropia_sprout", 3, 28)], "trees": [], "high_top": "petropia_crystal",
                               "patches": [("petropia_lichen", "minecraft:surface", 0.05, 10.0),
                                           ("petropia_gravel", "minecraft:gravel", -0.12, 0.12)],
                               "climate": {"temperature": [-1.0, -0.2], "humidity": [-1.0, 0.1]},
                               "extras": ["spires_sparse", "bundles_giant", "clusters", "boulders"], "chime": 0.004},
            "crystal_forest": {"sky": 0xA890E8, "fog": 0xC8D0F0, "water": 0x3AD0B0, "particle": ("minecraft:end_rod", 0.003),
                               "plants": [("petropia_sprout", 4, 32)], "top": "petropia_lichen",
                               "patches": [("petropia_shards", "minecraft:patch", 0.35, 10.0),
                                           ("petropia_gravel", "minecraft:gravel", -0.08, 0.08)],
                               "trees": [("petropia_crystal_tree", 7), ("petropia_shard_tree", 2)],
                               "climate": {"temperature": [-1.0, -0.2], "humidity": [0.1, 1.0]},
                               "extras": ["clusters"], "chime": 0.008},
            "shard_wastes": {"sky": 0x8A78C8, "fog": 0xA8B8C8, "water": 0x3AD0B0, "particle": None,
                             "plants": [("petropia_sprout", 1, 12)], "trees": [], "top": "petropia_rock",
                             "patches": [("petropia_gravel", "minecraft:surface", -0.25, 0.25),
                                         ("petropia_shards", "minecraft:patch", 0.3, 10.0)],
                             "climate": {"temperature": [-0.2, 0.5], "humidity": [-1.0, -0.1]},
                             "extras": ["spires_sparse", "bundles_giant", "boulders", "disk_smooth", "disk_shards"], "chime": 0.002},
            "geode_fields": {"sky": 0xC090E8, "fog": 0xD8C8F0, "water": 0x3AD0B0, "particle": ("minecraft:end_rod", 0.002),
                             "plants": [("petropia_sprout", 2, 20)], "trees": [("petropia_shard_tree", 1)],
                             "patches": [("petropia_smooth_rock", "minecraft:surface", 0.3, 10.0),
                                         ("petropia_lichen", "minecraft:patch", 0.4, 10.0)],
                             "climate": {"temperature": [-0.2, 0.5], "humidity": [-0.1, 1.0]},
                             "extras": ["geode_surface", "spires_sparse", "bundles_giant", "clusters_dense"], "chime": 0.01},
            "shattered_basin": {"sky": 0xC8B8FF, "fog": 0xE0F0F0, "water": 0x2AC0C8, "particle": None,
                                "plants": [("petropia_sprout", 3, 24)], "trees": [("petropia_crystal_tree", 2)], "top": "petropia_gravel",
                                "patches": [("petropia_lichen", "minecraft:surface", 0.1, 10.0),
                                            ("petropia_smooth_rock", "minecraft:patch", 0.4, 10.0)],
                                "climate": {"temperature": [0.5, 1.0], "humidity": [-1.0, 1.0]},
                                "extras": ["disk_smooth", "clusters", "boulders"], "chime": 0.004},
            "prism_peaks": {"sky": 0xD8C8FF, "fog": 0xF0F0FF, "water": 0x3AD0B0, "particle": ("minecraft:end_rod", 0.004),
                            "plants": [("petropia_sprout", 1, 8)], "trees": [], "top": "petropia_rock", "high_top": "petropia_crystal",
                            "patches": [("petropia_smooth_rock", "minecraft:surface", 0.2, 10.0)],
                            "climate": {"erosion": [-1.0, -0.6]},
                            "extras": ["spires_dense", "bundles_giant", "clusters"], "chime": 0.012},
        },
        "monsters": [],
    },
    "galvan_prime": {
        "terrain": "overworld",
        "stone": "galvan_slate", "top": "galvan_moss", "under": "galvan_mud", "steep": "galvan_slate", "sea": True,
        "ore": None, "blobs": [],
        "biomes": {
            "marsh": {"sky": 0xB8D86A, "fog": 0xD8E8A8, "water": 0x4A8A5A, "particle": ("minecraft:spore_blossom_air", 0.004),
                      "plants": [("galvan_fern", 6, 48)], "trees": []},
            "uplands": {"sky": 0x9AC85A, "fog": 0xC8E098, "water": 0x4A8A5A, "particle": None,
                        "plants": [("galvan_fern", 3, 32)], "trees": []},
        },
        "monsters": [],
    },
    "galvan_b": {
        "terrain": "overworld", "noise_tweaks": {"erosion": {"amplitude_scale": 0.6}},
        "stone": "mechamorph_circuit", "top": "mechamorph_plate", "under": "mechamorph_circuit", "steep": "mechamorph_circuit",
        "sea": False, "ore": None, "blobs": [], "extra_ores": [("biotech_node", 7, 5, 120)],
        "biomes": {
            "grid": {"sky": 0x0A1A10, "fog": 0x1A3A22, "water": 0x3AFF5A, "particle": ("minecraft:electric_spark", 0.003),
                     "plants": [("mechamorph_vine", 3, 28)], "trees": []},
            "core_rifts": {"sky": 0x06100A, "fog": 0x10281A, "water": 0x3AFF5A, "particle": ("minecraft:electric_spark", 0.008),
                           "plants": [("mechamorph_vine", 2, 20)], "trees": [], "top": "mechamorph_circuit"},
        },
        "monsters": [],
    },
    "kylmyys": {
        "terrain": "amplified",
        "stone": "kylmyys_permafrost", "top": "kylmyys_snow", "under": "kylmyys_snow", "steep": "kylmyys_ice", "sea": False,
        "ore": None, "blobs": [("kylmyys_ice", 10, 30)],
        "biomes": {
            "frost_plains": {"sky": 0x8AB8E8, "fog": 0xC8E0F8, "water": 0x8AC8F0, "particle": ("minecraft:snowflake", 0.01),
                             "plants": [("kylmyys_frostfern", 2, 24)], "trees": []},
            "glacier": {"sky": 0x2A4A7A, "fog": 0x6A88B8, "water": 0x8AC8F0, "particle": ("minecraft:snowflake", 0.02),
                        "plants": [("kylmyys_frostfern", 1, 12)], "trees": [], "high_top": "kylmyys_ice"},
        },
        "monsters": [],
    },
    "vulpin": {
        "terrain": "large_biomes",
        "stone": "vulpin_rubble", "top": "vulpin_ash", "under": "vulpin_ash", "steep": "vulpin_rubble", "sea": False,
        "ore": None, "blobs": [("vulpin_sludge", 10, 30)],
        "biomes": {
            "ash_wastes": {"sky": 0xA8602A, "fog": 0x8A5A30, "water": 0x8AB83A, "particle": ("minecraft:ash", 0.03),
                           "plants": [("vulpin_thorn", 2, 24)], "trees": []},
            "sludge_flats": {"sky": 0x7A5A1A, "fog": 0x6A6A22, "water": 0x8AB83A, "particle": ("minecraft:ash", 0.05),
                             "plants": [("vulpin_thorn", 1, 12)], "trees": [], "top": "vulpin_sludge"},
        },
        "monsters": [],
    },
    "piscciss": {
        "terrain": "overworld", "noise_tweaks": {"continentalness": {"first_octave": 1}},
        "stone": "piscciss_reefstone", "top": "piscciss_sand", "under": "piscciss_sand", "steep": "piscciss_reefstone", "sea": True,
        "sea_level": 78, "ore": None, "blobs": [],
        "biomes": {
            "open_sea": {"sky": 0x5AC8D8, "fog": 0x9AE0E8, "water": 0x1A8A9A, "particle": None,
                         "plants": [("piscciss_grass", 2, 24)], "trees": []},
            "reef_isles": {"sky": 0x78D8E0, "fog": 0xB0EEF0, "water": 0x2AB0B8, "particle": None,
                           "plants": [("piscciss_grass", 4, 32)], "trees": [], "high_top": "piscciss_reefstone"},
        },
        "monsters": [],
    },
}


def _simple_world(terrain: str, stone: str, top: str, under: str, blob: tuple[str, int, int] | None, plant: str, sea: bool,
                  biomes: dict[str, tuple[int, int, int, tuple[str, float] | None, int, str | None]], **extra) -> dict:
    """Heimatwelt der Welle 2: zwei Biome je Welt (Himmel, Nebel, Wasser, Partikel, Pflanzendichte, eigener Oberboden)."""
    world = {"terrain": terrain, "stone": stone, "top": top, "under": under, "steep": stone, "sea": sea, "ore": None,
             "blobs": [blob] if blob else [], "biomes": {}, "monsters": []}
    for name, (sky, fog, water, particle, plants, biome_top) in biomes.items():
        biome = {"sky": sky, "fog": fog, "water": water, "particle": particle, "plants": [(plant, plants, 16 + plants * 6)], "trees": []}
        if biome_top:
            biome["top"] = biome_top
        world["biomes"][name] = biome
    world.update(extra)
    return world


# --- Alien-Heimatwelten (Welle 2) -----------------------------------------------------------------------------------
ALIEN_WORLDS_2 = {
    "arburia": _simple_world("amplified", "arburia_rock", "arburia_sandstone", "arburia_sandstone", ("arburia_amber", 8, 22), "arburia_scrub",
                             False, {"canyons": (0xE0A860, 0xE8C890, 0x5A9AA8, ("minecraft:white_ash", 0.002), 2, None),
                                     "amber_mesas": (0xD08A40, 0xD8B070, 0x5A9AA8, None, 1, "arburia_rock")}, caves=True),
    "lepidopterra": _simple_world("overworld", "lepidopterra_stone", "lepidopterra_moss", "lepidopterra_peat", ("lepidopterra_slime", 8, 26),
                                  "lepidopterra_stalk", False,
                                  {"stink_marsh": (0xA8B83A, 0xC8D06A, 0x6A8A1A, ("minecraft:spore_blossom_air", 0.006), 5, None),
                                   "hive_hills": (0x98A830, 0xB8C060, 0x6A8A1A, None, 2, "lepidopterra_stone")}),
    "anur_phaetos": _simple_world("large_biomes", "phaetos_stone", "phaetos_dust", "phaetos_dust", ("phaetos_soulstone", 9, 24), "phaetos_wisp",
                                  False, {"grave_plains": (0x14101C, 0x2A2434, 0x3A5A6A, ("minecraft:soul", 0.004), 2, None),
                                          "soul_spires": (0x0A0812, 0x1E1A28, 0x3A5A6A, ("minecraft:soul_fire_flame", 0.003), 1, "phaetos_stone")},
                                  caves=True),
    "appoplexia": _simple_world("overworld", "appoplexia_rock", "appoplexia_turf", "appoplexia_soil", None, "appoplexia_claw", False,
                                {"rage_steppe": (0xF0A060, 0xF0C090, 0x4A8AA8, None, 5, None),
                                 "brawl_badlands": (0xD07A40, 0xE0A070, 0x4A8AA8, ("minecraft:white_ash", 0.003), 2, "appoplexia_soil")},
                                caves=True),
    "aranhaschimmia": _simple_world("overworld", "aranha_stone", "aranha_turf", "aranha_loam", ("aranha_silk", 6, 30), "aranha_fern", False,
                                    {"web_jungle": (0x6AA87A, 0x9AC8A0, 0x2A6A5A, ("minecraft:white_ash", 0.004), 6, None),
                                     "silk_hollows": (0x5A8A6A, 0x8AB090, 0x2A6A5A, ("minecraft:white_ash", 0.008), 3, None)}),
    "terradino": _simple_world("overworld", "terradino_basalt", "terradino_turf", "terradino_clay", ("terradino_amber", 7, 24), "terradino_cycad",
                               False, {"fern_plains": (0x9AC8E0, 0xC8E0E8, 0x3A7A8A, None, 4, None),
                                      "lava_fields": (0xC87A4A, 0xA86A4A, 0x3A7A8A, ("minecraft:ash", 0.02), 1, "terradino_basalt")},
                               caves=True),
    "aeropela": _simple_world("overworld", "aeropela_coralstone", "aeropela_sand", "aeropela_sand", ("aeropela_glowcoral", 8, 26), "aeropela_kelp",
                              True, {"sky_sea": (0x7AC8F8, 0xB8E0FF, 0x1A6AC8, None, 2, None),
                                     "coral_isles": (0x9AD8FF, 0xC8EEFF, 0x2A8AD8, None, 4, "aeropela_coralstone")},
                              noise_tweaks={"continentalness": {"first_octave": 1}}, sea_level=74),
    "methanos": _simple_world("overworld", "methanos_stone", "methanos_peat", "methanos_peat", ("methanos_vent", 8, 24), "methanos_bloom", False,
                              {"methane_bog": (0x6A7A3A, 0x8A9A5A, 0x3A4A1A, ("minecraft:flame", 0.002), 4, None),
                               "vent_fields": (0x5A5A2A, 0x7A7A4A, 0x3A4A1A, ("minecraft:smoke", 0.01), 2, "methanos_stone")}),
    "viscosia": _simple_world("large_biomes", "viscosia_crust", "viscosia_gel", "viscosia_gel", ("viscosia_ooze", 10, 28), "viscosia_bubble",
                              False, {"gel_flats": (0x8AE04A, 0xB8F08A, 0x5AD02A, ("minecraft:item_slime", 0.004), 3, None),
                                      "ooze_basin": (0x6AC83A, 0x9AE06A, 0x5AD02A, ("minecraft:item_slime", 0.01), 2, "viscosia_crust")}),
    "encephalonus": _simple_world("overworld", "encephalonus_shellstone", "encephalonus_sand", "encephalonus_sand",
                                  ("encephalonus_synapse", 8, 24), "encephalonus_sprig", True,
                                  {"thinking_shores": (0xC8A8E8, 0xE0D0F0, 0x4A6AC8, ("minecraft:electric_spark", 0.002), 3, None),
                                   "synapse_reefs": (0xA888D8, 0xC8B8E8, 0x4A6AC8, ("minecraft:electric_spark", 0.005), 2, "encephalonus_shellstone")},
                                  sea_level=66),
}
ALIEN_WORLDS.update(ALIEN_WORLDS_2)
PLANETS.update(ALIEN_WORLDS)

# Galaxiekarte (Reiter "Alien-Heimatwelten"): Lage, Farbe, Groesse, Reihenfolge
ALIEN_CHART = {
    "sonorosia": ([0.18, 0.30], 0x8A6AE0, 1.0, 100),
    "petropia": ([0.40, 0.18], 0x3AE0A8, 1.1, 101),
    "galvan_prime": ([0.62, 0.40], 0x9AC83A, 1.3, 102),
    "galvan_b": ([0.74, 0.30], 0x2A4A2A, 0.7, 103),
    "kylmyys": ([0.30, 0.66], 0x9AD0FF, 1.0, 104),
    "vulpin": ([0.54, 0.74], 0xC0703A, 1.0, 105),
    "piscciss": ([0.82, 0.70], 0x2AB0C0, 1.2, 106),
    # Welle 2
    "arburia": ([0.10, 0.50], 0xD8963A, 1.1, 107),
    "lepidopterra": ([0.26, 0.10], 0xA8C83A, 1.0, 108),
    "anur_phaetos": ([0.50, 0.30], 0x3A3448, 0.9, 109),
    "appoplexia": ([0.88, 0.16], 0xE0702A, 1.0, 110),
    "aranhaschimmia": ([0.12, 0.84], 0x3A8A4A, 1.0, 111),
    "terradino": ([0.42, 0.52], 0x7A9A3A, 1.3, 112),
    "aeropela": ([0.68, 0.56], 0x2A8AD8, 1.2, 113),
    "methanos": ([0.92, 0.44], 0x5A6A2A, 0.9, 114),
    "viscosia": ([0.40, 0.88], 0x7AE03A, 0.9, 115),
    "encephalonus": ([0.70, 0.88], 0xA888D8, 1.0, 116),
}

TREES = {
    "kerwan_spiral_tree": {
        "log": "kerwan_log", "leaves": "kerwan_leaves", "soil": "kerwan_soil",
        "trunk": {"type": "minecraft:straight_trunk_placer", "base_height": 8, "height_rand_a": 4, "height_rand_b": 2},
        "foliage": {"type": "minecraft:spruce_foliage_placer", "radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 3},
                    "offset": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 2},
                    "trunk_height": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2}},
        "size": {"type": "minecraft:two_layers_feature_size", "limit": 2, "lower_size": 0, "upper_size": 2},
    },
    "novalis_bell_tree": {
        "log": "novalis_log", "leaves": "novalis_leaves", "soil": "novalis_loam",
        "trunk": {"type": "minecraft:fancy_trunk_placer", "base_height": 5, "height_rand_a": 5, "height_rand_b": 0},
        "foliage": {"type": "minecraft:fancy_foliage_placer", "radius": 2, "offset": 4, "height": 4},
        "size": {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0, "min_clipped_height": 4},
    },
    "petropia_crystal_tree": {
        "log": "petropia_crystal_log", "leaves": "petropia_crystal_leaves", "soil": "petropia_shards",
        "trunk": {"type": "minecraft:straight_trunk_placer", "base_height": 5, "height_rand_a": 3, "height_rand_b": 1},
        "foliage": {"type": "minecraft:blob_foliage_placer", "radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 3},
                    "offset": 0, "height": 3},
        "size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
    },
    "petropia_shard_tree": {
        "log": "petropia_crystal_log", "leaves": "petropia_crystal_leaves", "soil": "petropia_shards",
        "trunk": {"type": "minecraft:forking_trunk_placer", "base_height": 4, "height_rand_a": 2, "height_rand_b": 2},
        "foliage": {"type": "minecraft:acacia_foliage_placer", "radius": 2, "offset": 0},
        "size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2},
    },
    "rilgar_mangrove": {
        "log": "rilgar_log", "leaves": "rilgar_leaves", "soil": "rilgar_sand",
        "trunk": {"type": "minecraft:forking_trunk_placer", "base_height": 5, "height_rand_a": 2, "height_rand_b": 2},
        "foliage": {"type": "minecraft:acacia_foliage_placer", "radius": 2, "offset": 0},
        "size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2},
    },
}

# Klima-Aufteilung der beiden Biome je Planet (Temperatur), wie bei den bisherigen Planeten-Dimensionen
SPLIT = [(-1.0, 0.0), (0.0, 1.0)]


# --- Oberflaechenregeln -----------------------------------------------------------------------------------------

def block_rule(name: str) -> dict:
    return {"type": "minecraft:block", "result_state": {"Name": b(name)}}


def cond(if_true: dict, then_run: dict) -> dict:
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then_run}


def floor(add_surface_depth: bool) -> dict:
    return {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": add_surface_depth, "secondary_depth_range": 0,
            "surface_type": "floor"}


def biome_is(planet: str, *biomes: str) -> dict:
    return {"type": "minecraft:biome", "biome_is": [b(f"{planet}/{name}") for name in biomes]}


def surface_rule(planet: str, cfg: dict) -> dict:
    top_rules = [cond({"type": "minecraft:steep"}, block_rule(cfg["steep"]))]
    for biome, bc in cfg["biomes"].items():
        if bc.get("crust"):
            top_rules.append(cond(biome_is(planet, biome), cond(
                {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.15, "max_threshold": 10.0},
                block_rule("torren_crust"))))
        if "high_top" in bc:
            top_rules.append(cond(biome_is(planet, biome), cond(
                {"type": "minecraft:y_above", "anchor": {"absolute": 100}, "surface_depth_multiplier": 0, "add_stone_depth": False},
                block_rule(bc["high_top"]))))
        # Bodenflecken (Terralith-Prinzip): Rauschfeld-Baender mischen mehrere Boeden innerhalb eines Bioms
        for block, noise, lo, hi in bc.get("patches", ()):
            top_rules.append(cond(biome_is(planet, biome), cond(
                {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": lo, "max_threshold": hi},
                block_rule(block))))
        if "top" in bc:
            top_rules.append(cond(biome_is(planet, biome), block_rule(bc["top"])))
    top_rules.append(block_rule(cfg["top"]))
    surface = {"type": "minecraft:sequence", "sequence": [
        cond(floor(False), {"type": "minecraft:sequence", "sequence": top_rules}),
        cond(floor(True), block_rule(cfg["under"])),
    ]}
    core = cond({"type": "minecraft:vertical_gradient", "random_name": f"{MOD}:{planet}_core",
                 "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, block_rule("planet_core"))
    return {"type": "minecraft:sequence", "sequence": [core, cond({"type": "minecraft:above_preliminary_surface"}, surface)]}


# --- Eigenes Gelaende je Welt -----------------------------------------------------------------------------------
# Minecraft leitet jedes Rauschfeld aus seinem Namen ab. Jede Welt bekommt eigene, umbenannte Kopien der Vanilla-
# Dichtefunktionen und Rauschfelder (tools/reference/vanilla_worldgen) -> voellig anderes Gelaende je Welt. Das Profil
# waehlt die Vanilla-Grundform (overworld, amplified = schroffe Gebirge, large_biomes = weite Landschaften);
# "noise_tweaks" verschiebt einzelne Rauschfelder (z. B. firstOctave der Kontinentalitaet: Inselwelt vs. Kontinente).

def _vanilla_ids(kind: str) -> set[str]:
    root = VANILLA / kind
    return {"minecraft:" + p.relative_to(root).with_suffix("").as_posix() for p in root.rglob("*.json")}


def _vanilla(kind: str, ident: str) -> dict:
    return json.loads((VANILLA / kind / (ident.split(":", 1)[1] + ".json")).read_text(encoding="utf-8"))


def terrain(planet: str, cfg: dict) -> tuple[dict, dict[Path, dict]]:
    """Noise-Settings-Vorlage der Welt (Router mit eigenen IDs) und die zugehoerigen Dichtefunktionen/Rauschfelder."""
    df_ids = _vanilla_ids("density_function")
    noise_ids = _vanilla_ids("noise")
    prefix = f"{MOD}:{planet}/"

    def rename(obj):
        if isinstance(obj, dict):
            return {k: rename(v) for k, v in obj.items()}
        if isinstance(obj, list):
            return [rename(v) for v in obj]
        if isinstance(obj, str) and (obj in df_ids or obj in noise_ids):
            return prefix + obj.split(":", 1)[1]
        return obj

    settings = rename(_vanilla("noise_settings", "minecraft:" + cfg.get("terrain", "overworld")))
    files: dict[Path, dict] = {}
    pending = [settings]
    seen: set[str] = set()
    while pending:
        node = pending.pop()
        stack = [node]
        while stack:
            o = stack.pop()
            if isinstance(o, dict):
                stack.extend(o.values())
            elif isinstance(o, list):
                stack.extend(o)
            elif isinstance(o, str) and o.startswith(prefix) and o not in seen:
                seen.add(o)
                vanilla_id = "minecraft:" + o[len(prefix):]
                rel = o[len(prefix):]
                if vanilla_id in df_ids:
                    data = rename(_vanilla("density_function", vanilla_id))
                    files[DATA / "worldgen" / "density_function" / planet / f"{rel}.json"] = data
                    pending.append(data)
                if vanilla_id in noise_ids:
                    data = _vanilla("noise", vanilla_id)
                    tweak = cfg.get("noise_tweaks", {}).get(rel)
                    if tweak:
                        data = dict(data)
                        data["firstOctave"] = data["firstOctave"] + tweak.get("first_octave", 0)
                        if "amplitude_scale" in tweak:
                            data["amplitudes"] = [round(a * tweak["amplitude_scale"], 4) for a in data["amplitudes"]]
                    files[DATA / "worldgen" / "noise" / planet / f"{rel}.json"] = data
    return settings, files


def noise_settings(planet: str, cfg: dict, template: dict) -> dict:
    data = copy.deepcopy(template)
    data["default_block"] = {"Name": b(cfg["stone"])}
    data["ore_veins_enabled"] = False
    data["aquifers_enabled"] = False
    if cfg["sea"]:
        data["sea_level"] = cfg.get("sea_level", 63)
        data["default_fluid"] = {"Name": "minecraft:water", "Properties": {"level": "0"}}
    else:
        data["sea_level"] = -64
    data["surface_rule"] = surface_rule(planet, cfg)
    return data


# --- Features ---------------------------------------------------------------------------------------------------

def ore_feature(target: str, ore: str, size: int) -> dict:
    return {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": 0.0, "targets": [
        {"target": {"predicate_type": "minecraft:block_match", "block": b(target)}, "state": {"Name": b(ore)}}]}}


def ore_placed(feature: str, count: int, max_y: int) -> dict:
    return {"feature": b(feature), "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"above_bottom": 4},
                                                       "max_inclusive": {"absolute": max_y}}},
        {"type": "minecraft:biome"}]}


def tree_feature(t: dict) -> dict:
    return {"type": "minecraft:tree", "config": {
        "trunk_provider": {"type": "minecraft:simple_state_provider", "state": state(t["log"], axis="y")},
        "trunk_placer": t["trunk"],
        "foliage_provider": {"type": "minecraft:simple_state_provider",
                             "state": state(t["leaves"], distance=7, persistent=False, waterlogged=False)},
        "foliage_placer": t["foliage"],
        "minimum_size": t["size"],
        "dirt_provider": {"type": "minecraft:simple_state_provider", "state": state(t["soil"])},
        "decorators": [],
        "ignore_vines": True,
        "force_dirt": False}}


def tree_placed(feature: str, count: int) -> dict:
    return {"feature": b(feature), "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0],
                                                                  "tag": f"{MOD}:alien_soil"}},
        {"type": "minecraft:biome"}]}


def patch_feature(plant: str, tries: int) -> dict:
    return {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": 7, "y_spread": 3, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider",
                                                                            "state": state(plant)}}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:would_survive", "state": state(plant)}]}}]}}}


def patch_placed(feature: str, count: int) -> dict:
    return {"feature": b(feature), "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
        {"type": "minecraft:biome"}]}


# --- Biome und Dimensionen ----------------------------------------------------------------------------------------

def biome(planet: str, name: str, cfg: dict, bc: dict, feature_ids: dict) -> dict:
    effects = {"sky_color": bc["sky"], "fog_color": bc["fog"], "water_color": bc["water"], "water_fog_color": 0x0A2A3A,
               "grass_color": 0x59AE30, "foliage_color": 0x59AE30,
               "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}}
    if bc["particle"]:
        effects["particle"] = {"options": {"type": bc["particle"][0]}, "probability": bc["particle"][1]}
    if "chime" in bc:
        # leise Zusatzklaenge (Kristallklingen u. a.), wie Nether-/End-Biome ihre Atmosphaere bekommen
        effects["additions_sound"] = {"sound": bc.get("ambient_sound", "minecraft:block.amethyst_block.chime"), "tick_chance": bc["chime"]}
    features: list[list[str]] = [[] for _ in range(11)]
    features[6] = list(feature_ids["ores"])
    trees = sorted((t for t, _ in bc["trees"]), key=list(TREES).index)
    features[9] = [f"{MOD}:{planet}_{t}" for t in trees]
    # Baukasten-Features: fuer die ganze Welt + fuer dieses Biom, in fester globaler Reihenfolge
    kit_map = cfg.get("kit", {})
    chosen = {kit_map[k] for k in cfg.get("extras", []) + bc.get("extras", [])}
    for fid in KIT:
        if fid in chosen:
            features[KIT[fid][0]].append(b(fid))
    features[9] += [f"{MOD}:{planet}_{name}_{p}" for p, _, _ in bc["plants"]]
    monsters = [{"type": f"{MOD}:{mob}", "weight": w, "minCount": lo, "maxCount": hi} for mob, w, lo, hi in cfg["monsters"]]
    spawners = {"monster": monsters, "creature": [], "ambient": [], "water_creature": [], "water_ambient": [],
                "underground_water_creature": [], "axolotls": [], "misc": []}
    # Lebewesen, deren DNA man auf diesem Planeten scannen kann (Alien-Definitionen: "dna")
    for group, mobs in cfg.get("life", {}).items():
        spawners[group] += [{"type": mob, "weight": w, "minCount": lo, "maxCount": hi} for mob, w, lo, hi in mobs]
    return {"has_precipitation": False, "temperature": 0.8, "downfall": 0.1, "effects": effects,
            "spawners": spawners,
            "spawn_costs": {}, "carvers": {"air": [b(f"{planet}_cave"), b(f"{planet}_canyon")] if cfg.get("caves") else []},
            "features": features}


def dimension(planet: str, cfg: dict) -> dict:
    """Biomverteilung nach Klima (eigene Rauschfelder der Welt). Ohne "climate": zwei Biome nach Temperatur."""
    full = {"temperature": [-1.0, 1.0], "humidity": [-1.0, 1.0], "continentalness": [-1.2, 1.0], "erosion": [-1.0, 1.0],
            "weirdness": [-1.0, 1.0]}
    claimed_erosion = [bc["climate"]["erosion"] for bc in cfg["biomes"].values() if "erosion" in bc.get("climate", {})]
    if claimed_erosion:
        # Biome mit eigener Erosion (Gipfel) bekommen diese Zone; alle anderen den Rest
        full["erosion"] = [max(e[1] for e in claimed_erosion), 1.0]
    entries = []
    for i, (name, bc) in enumerate(cfg["biomes"].items()):
        params = dict(full)
        if "climate" in bc:
            params.update(bc["climate"])
        elif i < len(SPLIT):
            params["temperature"] = list(SPLIT[i])
        params.update({"depth": 0.0, "offset": 0.0})
        entries.append({"biome": b(f"{planet}/{name}"), "parameters": params})
    return {"type": "minecraft:overworld", "generator": {"type": "minecraft:noise", "settings": b(planet),
                                                           "biome_source": {"type": "minecraft:multi_noise", "biomes": entries}}}


def structure(planet: str, st: dict) -> dict[Path, dict]:
    """Struktur, Strukturset (Abstand im Raster) und Biom-Tag (wo sie stehen darf)."""
    sid = st["id"]
    tag = f"has_structure/{sid}"
    return {
        DATA / "worldgen" / "structure" / f"{sid}.json": {
            "type": f"{MOD}:{st['type']}", "biomes": f"#{MOD}:{tag}", "step": "surface_structures",
            "spawn_overrides": {}, "terrain_adaptation": "beard_thin", **st["config"]},
        DATA / "worldgen" / "structure_set" / f"{sid}.json": {
            "structures": [{"structure": f"{MOD}:{sid}", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": st["spacing"], "separation": st["separation"],
                          "salt": st["salt"]}},
        DATA / "tags" / "worldgen" / "biome" / f"{tag}.json": {
            "replace": False, "values": [f"{MOD}:{planet}/{name}" for name in st["biomes"]]},
    }


def outputs() -> dict[Path, dict]:
    template = json.loads(TEMPLATE.read_text(encoding="utf-8"))
    files: dict[Path, dict] = {}
    wg = DATA / "worldgen"
    for tree, t in TREES.items():
        files[wg / "configured_feature" / f"{tree}.json"] = tree_feature(t)
    for fid, (_, configured, placed) in KIT.items():
        files[wg / "configured_feature" / f"{fid}.json"] = configured
        files[wg / "placed_feature" / f"{fid}.json"] = placed
    files[DATA / "tags" / "block" / "carver_replaceables.json"] = {
        "replace": False, "values": [f"#{MOD}:planet_stone", f"#{MOD}:alien_soil"]}
    for planet, cfg in PLANETS.items():
        world_template, terrain_files = terrain(planet, cfg)
        files.update(terrain_files)
        files[wg / "noise_settings" / f"{planet}.json"] = noise_settings(planet, cfg, world_template)
        ores = []
        if cfg["ore"]:
            ore = f"ore_{cfg['ore']}"
            files[wg / "configured_feature" / f"{ore}.json"] = ore_feature(cfg["stone"], f"{cfg['ore']}_ore", cfg["ore_size"])
            files[wg / "placed_feature" / f"{ore}.json"] = ore_placed(ore, cfg["ore_count"], cfg["ore_max"])
            ores.append(f"{MOD}:{ore}")
        for block, count, size, max_y in cfg.get("extra_ores", ()):
            fid = f"{planet}_ore_{block}"
            files[wg / "configured_feature" / f"{fid}.json"] = ore_feature(cfg["stone"], block, size)
            files[wg / "placed_feature" / f"{fid}.json"] = ore_placed(fid, count, max_y)
            ores.append(f"{MOD}:{fid}")
        for blob, count, size in cfg["blobs"]:
            fid = f"{planet}_blob_{blob}"
            files[wg / "configured_feature" / f"{fid}.json"] = ore_feature(cfg["stone"], blob, size)
            files[wg / "placed_feature" / f"{fid}.json"] = ore_placed(fid, count, 200)
            ores.append(f"{MOD}:{fid}")
        for name, bc in cfg["biomes"].items():
            for tree, count in bc["trees"]:
                files[wg / "placed_feature" / f"{planet}_{tree}.json"] = tree_placed(tree, count)
            for plant, count, tries in bc["plants"]:
                fid = f"{planet}_{name}_{plant}"
                files[wg / "configured_feature" / f"{fid}.json"] = patch_feature(plant, tries)
                files[wg / "placed_feature" / f"{fid}.json"] = patch_placed(fid, count)
            files[wg / "biome" / planet / f"{name}.json"] = biome(planet, name, cfg, bc, {"ores": ores})
        files[DATA / "dimension" / f"{planet}.json"] = dimension(planet, cfg)
        for st in cfg.get("structures", ()):
            files.update(structure(planet, st))
        if cfg.get("caves"):
            for cid, carver in carvers(planet).items():
                files[wg / "configured_carver" / f"{cid}.json"] = carver
    for planet, (pos, color, size, order) in ALIEN_CHART.items():
        files[DATA / MOD / "planet" / f"{planet}.json"] = {"dimension": f"{MOD}:{planet}", "system": "homeworlds", "chart": "aliens",
                                                            "map": pos, "color": color, "size": size, "order": order,
                                                            "landing": [0, 0]}
    files.update(spawn_tags())
    return files


def spawn_tags() -> dict[Path, dict]:
    """Planetenboeden als Spawnboden fuer die DNA-Lebewesen (Vanilla prueft den Block unter dem Tier)."""
    mc = ROOT / "src" / "main" / "resources" / "data" / "minecraft" / "tags"
    ground = [f"{MOD}:{cfg[k]}" for cfg in PLANETS.values() for k in ("top", "steep")]
    ground += [f"{MOD}:{bc[k]}" for cfg in PLANETS.values() for bc in cfg["biomes"].values() for k in ("top", "high_top") if k in bc]
    ground += [f"{MOD}:{patch[0]}" for cfg in PLANETS.values() for bc in cfg["biomes"].values() for patch in bc.get("patches", ())]
    ground = sorted(set(ground))
    sand = [f"{MOD}:{PLANETS['veldin'][k]}" for k in ("top", "steep")]
    slime_biomes = sorted(f"{MOD}:{planet}/{name}" for planet, cfg in PLANETS.items() if cfg.get("slime") for name in cfg["biomes"])
    return {
        mc / "block" / "animals_spawnable_on.json": {"replace": False, "values": ground},
        mc / "block" / "armadillo_spawnable_on.json": {"replace": False, "values": sand},
        mc / "block" / "foxes_spawnable_on.json": {"replace": False, "values": [f"{MOD}:{PLANETS['novalis']['top']}"]},
        mc / "block" / "polar_bears_spawnable_on_alternate.json": {"replace": False, "values": sorted(
            {f"{MOD}:{PLANETS['torren_iv']['top']}", f"{MOD}:torren_crust"})},
        mc / "worldgen" / "biome" / "allows_surface_slime_spawns.json": {"replace": False, "values": slime_biomes},
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Dateien aktuell sind")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    try:
        files = outputs()
    except (OSError, json.JSONDecodeError) as exc:
        LOG.error("Vorlage %s nicht lesbar: %s", TEMPLATE, exc)
        return 1
    if args.check:
        stale = [p for p, data in files.items() if not p.is_file() or json.loads(p.read_text(encoding="utf-8")) != data]
        for p in stale:
            LOG.error("fehlt oder veraltet: %s", p.relative_to(ROOT))
        LOG.info("Planeten-Weltgenerierung: %d Dateien, %d veraltet", len(files), len(stale))
        return 1 if stale else 0
    for path, data in files.items():
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        except OSError as exc:
            LOG.error("konnte %s nicht schreiben: %s", path, exc)
            return 1
    LOG.info("Planeten-Weltgenerierung geschrieben: %d Dateien", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
