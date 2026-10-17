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


def b(name: str) -> str:
    return f"{MOD}:{name}"


def state(name: str, **props) -> dict:
    s = {"Name": b(name)}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


PLANETS = {
    "veldin": {
        "stone": "veldin_rock", "top": "veldin_sand", "under": "veldin_sand", "steep": "veldin_sandstone", "sea": False,
        "ore": "solar_copper", "ore_count": 14, "ore_size": 9, "ore_max": 110,
        "blobs": [("veldin_sandstone", 10, 40)],
        "biomes": {
            "dunes": {"sky": 0xF0B070, "fog": 0xF2C8A0, "water": 0x3F76E4, "particle": None,
                      "plants": [("veldin_scrub", 2, 24)], "trees": [], "high_top": "veldin_sandstone"},
            "canyons": {"sky": 0xE89860, "fog": 0xE0A880, "water": 0x3F76E4, "particle": ("minecraft:white_ash", 0.004),
                        "plants": [("veldin_scrub", 1, 12)], "trees": [], "top": "veldin_sandstone"},
        },
        "monsters": [("shadow", 60, 1, 3), ("soldier", 25, 1, 2)],
    },
    "kerwan": {
        "stone": "kerwan_slate", "top": "kerwan_turf", "under": "kerwan_soil", "steep": "kerwan_slate", "sea": False,
        "ore": "azurite", "ore_count": 12, "ore_size": 8, "ore_max": 90, "blobs": [],
        "biomes": {
            "meadows": {"sky": 0x7FB8FF, "fog": 0xA8D0F0, "water": 0x3FA0D0, "particle": None,
                        "plants": [("kerwan_reed", 3, 32)], "trees": [("kerwan_spiral_tree", 1)]},
            "spires": {"sky": 0x6AA0F0, "fog": 0x90C0E8, "water": 0x3FA0D0, "particle": None,
                       "plants": [("kerwan_reed", 2, 24)], "trees": [("kerwan_spiral_tree", 4)]},
        },
        "monsters": [("soldier", 40, 1, 2), ("air_soldier", 25, 1, 2)],
    },
    "novalis": {
        "stone": "novalis_limestone", "top": "novalis_moss", "under": "novalis_loam", "steep": "novalis_limestone", "sea": False,
        "ore": "viridium", "ore_count": 10, "ore_size": 7, "ore_max": 70, "blobs": [],
        "biomes": {
            "fields": {"sky": 0x9AC8FF, "fog": 0xD8E8F8, "water": 0x4AB0E0, "particle": None,
                       "plants": [("novalis_bloom", 6, 48)], "trees": [("novalis_bell_tree", 1)]},
            "grove": {"sky": 0xB0B8FF, "fog": 0xF0D8F0, "water": 0x4AB0E0, "particle": ("minecraft:spore_blossom_air", 0.006),
                      "plants": [("novalis_bloom", 3, 32)], "trees": [("novalis_bell_tree", 4)]},
        },
        "monsters": [("shadow", 50, 1, 3)],
    },
    "rilgar": {
        "stone": "rilgar_coralstone", "top": "rilgar_sand", "under": "rilgar_sand", "steep": "rilgar_coralstone", "sea": True,
        "ore": "aquarine", "ore_count": 9, "ore_size": 7, "ore_max": 60, "blobs": [],
        "biomes": {
            "reef": {"sky": 0x78D8F0, "fog": 0xB8F0F8, "water": 0x2AD0C8, "particle": None,
                     "plants": [("rilgar_seagrass", 2, 24)], "trees": []},
            "mangroves": {"sky": 0x80D0E8, "fog": 0xA8E0E8, "water": 0x30B8B0, "particle": None,
                          "plants": [("rilgar_seagrass", 4, 32)], "trees": [("rilgar_mangrove", 4)]},
        },
        "monsters": [("shadow", 30, 1, 2), ("darkball", 10, 1, 1)],
    },
    "torren_iv": {
        "stone": "torren_basalt", "top": "torren_dust", "under": "torren_dust", "steep": "torren_basalt", "sea": False,
        "ore": "pyronite", "ore_count": 8, "ore_size": 6, "ore_max": 50, "blobs": [("torren_crust", 8, 34)],
        "biomes": {
            "plains": {"sky": 0xD07050, "fog": 0xC88068, "water": 0x8A4A3A, "particle": ("minecraft:ash", 0.02),
                       "plants": [("torren_thorn", 2, 20)], "trees": [], "crust": True},
            "wastes": {"sky": 0xB05040, "fog": 0xA86050, "water": 0x8A4A3A, "particle": ("minecraft:ash", 0.04),
                       "plants": [("torren_thorn", 1, 10)], "trees": [], "top": "torren_crust"},
        },
        "monsters": [("large_body", 20, 1, 1), ("darkball", 30, 1, 2), ("shadow", 40, 2, 3)],
    },
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
        if "top" in bc:
            top_rules.append(cond(biome_is(planet, biome), block_rule(bc["top"])))
        if bc.get("crust"):
            top_rules.append(cond(biome_is(planet, biome), cond(
                {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.15, "max_threshold": 10.0},
                block_rule("torren_crust"))))
        if "high_top" in bc:
            top_rules.append(cond(biome_is(planet, biome), cond(
                {"type": "minecraft:y_above", "anchor": {"absolute": 100}, "surface_depth_multiplier": 0, "add_stone_depth": False},
                block_rule(bc["high_top"]))))
    top_rules.append(block_rule(cfg["top"]))
    surface = {"type": "minecraft:sequence", "sequence": [
        cond(floor(False), {"type": "minecraft:sequence", "sequence": top_rules}),
        cond(floor(True), block_rule(cfg["under"])),
    ]}
    core = cond({"type": "minecraft:vertical_gradient", "random_name": f"{MOD}:{planet}_core",
                 "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, block_rule("planet_core"))
    return {"type": "minecraft:sequence", "sequence": [core, cond({"type": "minecraft:above_preliminary_surface"}, surface)]}


def noise_settings(planet: str, cfg: dict, template: dict) -> dict:
    data = copy.deepcopy(template)
    data["default_block"] = {"Name": b(cfg["stone"])}
    data["ore_veins_enabled"] = False
    data["aquifers_enabled"] = False
    if cfg["sea"]:
        data["sea_level"] = 63
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
    features: list[list[str]] = [[] for _ in range(11)]
    features[6] = feature_ids["ores"]
    features[9] = [f"{MOD}:{planet}_{t}" for t, _ in bc["trees"]] + [f"{MOD}:{planet}_{name}_{p}" for p, _, _ in bc["plants"]]
    monsters = [{"type": f"{MOD}:{mob}", "weight": w, "minCount": lo, "maxCount": hi} for mob, w, lo, hi in cfg["monsters"]]
    return {"has_precipitation": False, "temperature": 0.8, "downfall": 0.1, "effects": effects,
            "spawners": {"monster": monsters, "creature": [], "ambient": [], "water_creature": [], "water_ambient": [],
                         "underground_water_creature": [], "axolotls": [], "misc": []},
            "spawn_costs": {}, "carvers": {"air": []}, "features": features}


def dimension(planet: str, cfg: dict) -> dict:
    entries = []
    for (lo, hi), name in zip(SPLIT, cfg["biomes"]):
        entries.append({"biome": b(f"{planet}/{name}"), "parameters": {
            "temperature": [lo, hi], "humidity": [-1.0, 1.0], "continentalness": [-1.2, 1.0], "erosion": [-1.0, 1.0],
            "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}})
    return {"type": "minecraft:overworld", "generator": {"type": "minecraft:noise", "settings": b(planet),
                                                           "biome_source": {"type": "minecraft:multi_noise", "biomes": entries}}}


def outputs() -> dict[Path, dict]:
    template = json.loads(TEMPLATE.read_text(encoding="utf-8"))
    files: dict[Path, dict] = {}
    wg = DATA / "worldgen"
    for tree, t in TREES.items():
        files[wg / "configured_feature" / f"{tree}.json"] = tree_feature(t)
    for planet, cfg in PLANETS.items():
        files[wg / "noise_settings" / f"{planet}.json"] = noise_settings(planet, cfg, template)
        ores = []
        ore = f"ore_{cfg['ore']}"
        files[wg / "configured_feature" / f"{ore}.json"] = ore_feature(cfg["stone"], f"{cfg['ore']}_ore", cfg["ore_size"])
        files[wg / "placed_feature" / f"{ore}.json"] = ore_placed(ore, cfg["ore_count"], cfg["ore_max"])
        ores.append(f"{MOD}:{ore}")
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
    return files


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
