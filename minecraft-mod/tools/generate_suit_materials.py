#!/usr/bin/env python3
"""Assets der Ruestungs-Materialkette: Item-Bilder (Rohstoff, Staub, veredelt, Panzerplatte), die drei Maschinen
(Blocktexturen im Stil der anderen Maschinen, Modelle, Blockstates mit Ausrichtung und Leuchtzustand, Beute,
Rezepte, Werkzeug-Tags) und die Uebersetzungen.

Java-Gegenstuecke: suit/forge/ForgeMaterials (Items), suit/forge/SuitForge (Maschinen), suit/forge/MachineRecipes.

    python tools/generate_suit_materials.py          # schreiben
    python tools/generate_suit_materials.py --check  # pruefen, ob alles aktuell ist
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_machine_textures as mt  # noqa: E402
import planet_textures as tx  # noqa: E402
import suit_icons  # noqa: E402

LOG = logging.getLogger("generate_suit_materials")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
DATA = ROOT / "src" / "main" / "resources" / "data"
MOD = "kingdomomnitrix"
H = tx.hexc

# Material -> (Deutsch, Englisch, dunkel, mittel, hell)
MATERIALS = {
    "taydenite": ("Taydenit", "Taydenite", "0E5A3A", "3AE89A", "D8FFE8"),
    "biotech": ("Biotech", "Biotech", "0A2A12", "3AFF5A", "D8FFD8"),
    "celestium": ("Celestium", "Celestium", "2A1A5A", "B8A8FF", "FFFFFF"),
    "raritanium": ("Raritanium", "Raritanium", "3A1A6A", "A86AFF", "F0E0FF"),
    "infernite": ("Infernit", "Infernite", "5A0806", "FF6A1A", "FFE08A"),
}
# Item -> (Form, Material, Deutsch, Englisch)
ITEMS = {
    "raw_taydenite": ("raw", "taydenite", "Rohes Taydenit", "Raw Taydenite"),
    "taydenite_dust": ("dust", "taydenite", "Taydenit-Staub", "Taydenite Dust"),
    "taydenite_crystal": ("crystal", "taydenite", "Geschliffener Taydenit", "Cut Taydenite"),
    "taydenite_plate": ("plate", "taydenite", "Taydenit-Panzerplatte", "Taydenite Armor Plate"),
    "raw_biotech": ("raw", "biotech", "Mechamorph-Masse", "Mechamorph Mass"),
    "biotech_dust": ("dust", "biotech", "Biotech-Staub", "Biotech Dust"),
    "biotech_gel": ("gel", "biotech", "Biotech-Gel", "Biotech Gel"),
    "biotech_weave": ("weave", "biotech", "Biotech-Gewebe", "Biotech Weave"),
    "raw_celestium": ("raw", "celestium", "Rohes Celestium", "Raw Celestium"),
    "celestium_dust": ("dust", "celestium", "Celestium-Staub", "Celestium Dust"),
    "celestium_ingot": ("ingot", "celestium", "Celestium-Barren", "Celestium Ingot"),
    "star_matrix": ("matrix", "celestium", "Sternenmatrix", "Star Matrix"),
    "raritanium_dust": ("dust", "raritanium", "Raritanium-Staub", "Raritanium Dust"),
    "raritanium_ingot": ("ingot", "raritanium", "Raritanium-Barren", "Raritanium Ingot"),
    "magnaplate_plating": ("plate", "raritanium", "Magnaplate-Panzerung", "Magnaplate Plating"),
    "carbonox_plate": ("plate_dark", "raritanium", "Carbonox-Platte", "Carbonox Plate"),
    "raw_infernite": ("raw", "infernite", "Rohes Infernit", "Raw Infernite"),
    "infernite_dust": ("dust", "infernite", "Infernit-Staub", "Infernite Dust"),
    "infernite_core": ("core", "infernite", "Infernit-Kern", "Infernite Core"),
    "hellfire_plate": ("plate_hot", "infernite", "Hoellenfeuer-Platte", "Hellfire Plate"),
}
# Maschine -> (Deutsch, Englisch, Leuchtfarbe, Akzent)
MACHINES = {
    "molecular_crusher": ("Molekular-Zerkleinerer", "Molecular Crusher", "3AFF8A", "2A8A5A"),
    "plasma_smelter": ("Plasmaschmelze", "Plasma Smelter", "FF7A2A", "8A3A10"),
    "alloy_forge": ("Legierungsschmiede", "Alloy Forge", "4AB8FF", "1A5A8A"),
}
ORE_NAMES = {}   # die Erzbloecke kommen aus generate_planet_content.py (mit Uebersetzung)


def rgb(hexcode: str, alpha: int = 255) -> tuple[int, int, int, int]:
    h = hexcode.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha


def mix(a: str, b: str, t: float) -> tuple[int, int, int, int]:
    ca, cb = rgb(a), rgb(b)
    return tuple(int(ca[i] * (1 - t) + cb[i] * t) for i in range(3)) + (255,)


# --- Maschinen --------------------------------------------------------------------------------------------------------

def crusher_content(d, s):
    # zwei Mahlwalzen mit Zaehnen
    for cx in (40, 88):
        d.ellipse(((cx - 20) * s, 44 * s, (cx + 20) * s, 84 * s), outline=(255, 255, 255), width=3 * s)
        for k in range(10):
            a = k * math.pi / 5
            d.line(((cx + math.cos(a) * 12) * s, (64 + math.sin(a) * 12) * s, (cx + math.cos(a) * 20) * s,
                    (64 + math.sin(a) * 20) * s), fill=(255, 255, 255), width=3 * s)


def smelter_content(d, s):
    d.polygon([(64 * s, 34 * s), (84 * s, 70 * s), (74 * s, 92 * s), (54 * s, 92 * s), (44 * s, 70 * s)], fill=(255, 255, 255))
    d.polygon([(64 * s, 56 * s), (74 * s, 76 * s), (64 * s, 90 * s), (54 * s, 76 * s)], fill=(120, 120, 120))


def forge_content(d, s):
    d.polygon([(34 * s, 54 * s), (94 * s, 54 * s), (86 * s, 66 * s), (74 * s, 66 * s), (78 * s, 84 * s), (50 * s, 84 * s),
               (54 * s, 66 * s), (42 * s, 66 * s)], fill=(255, 255, 255))
    for k in range(3):
        x = 50 + k * 14
        d.line((x * s, 34 * s, (x + 6) * s, 46 * s, (x - 2) * s, 50 * s), fill=(255, 255, 255), width=3 * s)


CONTENT = {"molecular_crusher": crusher_content, "plasma_smelter": smelter_content, "alloy_forge": forge_content}


def machine_textures() -> dict[str, Image.Image]:
    out = {}
    for name, (_, _, glow, accent) in MACHINES.items():
        out[f"{name}_front_on"] = mt.terminal_side(f"{name}_on", glow, CONTENT[name], accent)
        dim = "#" + "".join(f"{int(int(glow[i:i + 2], 16) * 0.35):02X}" for i in (0, 2, 4))
        out[f"{name}_front"] = mt.terminal_side(name, dim, CONTENT[name], accent)
        out[f"{name}_side"] = mt.terminal_side(f"{name}_side", "#1A1E24", lambda d, s: None, accent)
        out[f"{name}_top"] = mt.terminal_top(f"{name}_top", glow, mt.omnitrix_emblem if name != "plasma_smelter" else mt.trophy_emblem)
    return out


def machine_parts(name: str) -> list[dict]:
    """3D-Aufbauten auf dem Gehaeuse (Gehaeuse 13 px hoch, Front zeigt nach Norden)."""
    if name == "molecular_crusher":
        # Einfuelltrichter mit zwei Mahlwalzen und Kolben an den Seiten
        return ([el((2, 13, 2), (14, 14.5, 14), "#metal"), el((3, 14.5, 3), (13, 15.5, 13), "#metal"), el((4.5, 15.5, 4.5), (11.5, 16, 11.5), "#dark"),
                 el((4, 14.6, 5.5), (12, 15.8, 7), "#glow", glow=True), el((4, 14.6, 9), (12, 15.8, 10.5), "#glow", glow=True)]
                + [el((x, 4, 4), (x + 1.5, 10, 12), "#metal") for x in (-1.5, 16)]
                + [el((x, 6, 6.5), (x + 0.5, 8, 9.5), "#glow", glow=True) for x in (-2, 17.5)])
    if name == "plasma_smelter":
        # Plasmakuppel mit Ringen, Schornstein, Kuehlrippen
        return ([el((3, 13, 3), (11, 14, 11), "#metal"), el((4, 14, 4), (10, 16.5, 10), "#glow", glow=True), el((5, 16.5, 5), (9, 17.5, 9), "#glow", glow=True),
                 el((3.5, 15, 3.5), (10.5, 15.6, 10.5), "#metal"), el((11.5, 13, 11.5), (14.5, 19, 14.5), "#metal"),
                 el((11, 18, 11), (15, 19, 15), "#dark")]
                + [el((16, 2 + i * 2.5, 3), (17, 3 + i * 2.5, 13), "#metal") for i in range(4)])
    # Legierungsschmiede: Amboss auf dem Deckel, zwei Lichtbogen-Saeulen
    return ([el((3, 13, 5), (13, 14, 11), "#metal"), el((5, 14, 6), (11, 15.5, 10), "#metal"), el((1.5, 15.5, 5.5), (14.5, 17.5, 10.5), "#metal"),
             el((14.5, 16, 7), (16.5, 17, 9), "#metal")]
            + [el((x, 13, 1), (x + 1.5, 19, 2.5), "#dark") for x in (1, 13.5)]
            + [el((x + 0.3, 19, 1.3), (x + 1.2, 20, 2.2), "#glow", glow=True) for x in (1, 13.5)]
            + [el((2.5, 18.6, 1.5), (13.5, 19.2, 2.1), "#glow", glow=True)])


def machine_json(name: str) -> dict[Path, dict]:
    files: dict[Path, dict] = {}
    glow = MACHINES[name][2]
    for lit in (False, True):
        model = f"{name}_on" if lit else name
        base = {"from": [0, 0, 0], "to": [16, 13, 16], "faces": {
            "north": {"uv": [0, 3, 16, 16], "texture": "#front", "cullface": "north"},
            "south": {"uv": [0, 3, 16, 16], "texture": "#side", "cullface": "south"},
            "east": {"uv": [0, 3, 16, 16], "texture": "#side", "cullface": "east"},
            "west": {"uv": [0, 3, 16, 16], "texture": "#side", "cullface": "west"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "down"}}}
        files[ASSETS / "models" / "block" / f"{model}.json"] = {
            "parent": "minecraft:block/block",
            "textures": {"front": f"{MOD}:block/{name}_front{'_on' if lit else ''}", "side": f"{MOD}:block/{name}_side",
                         "top": f"{MOD}:block/{name}_top", "particle": f"{MOD}:block/{name}_side",
                         "metal": f"{MOD}:{SUITMAT}/machine_metal", "dark": f"{MOD}:{SUITMAT}/machine_dark",
                         "glow": f"{MOD}:{SUITMAT}/{name}_glow{'_on' if lit else ''}"},
            "elements": [base] + machine_parts(name),
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]}}}
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            entry = {"model": f"{MOD}:block/{name}{'_on' if lit == 'true' else ''}"}
            if y:
                entry["y"] = y
            variants[f"facing={facing},lit={lit}"] = entry
    files[ASSETS / "blockstates" / f"{name}.json"] = {"variants": variants}
    files[ASSETS / "models" / "item" / f"{name}.json"] = {"parent": f"{MOD}:block/{name}"}
    files[DATA / MOD / "loot_table" / "blocks" / f"{name}.json"] = {
        "type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
                                              "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    return files


def shaped(pattern: list[str], keys: dict[str, str], result: str, count: int = 1) -> dict:
    return {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: {"item": v} for k, v in keys.items()}, "result": {"id": result, "count": count}}


RECIPES = {
    "molecular_crusher": shaped(["IRI", "PDP", "IBI"], {"I": "minecraft:iron_ingot", "R": f"{MOD}:raritanium", "P": "minecraft:piston",
                                                         "D": "minecraft:diamond", "B": "minecraft:iron_block"}, f"{MOD}:molecular_crusher"),
    "plasma_smelter": shaped(["RER", "MFM", "RCR"], {"R": f"{MOD}:raritanium", "E": "minecraft:blaze_rod", "M": "minecraft:magma_block",
                                                      "F": "minecraft:blast_furnace", "C": f"{MOD}:molecular_crusher"},
                             f"{MOD}:plasma_smelter"),
    "alloy_forge": shaped(["TAT", "SFS", "ROR"], {"T": f"{MOD}:raritanium_ingot", "A": "minecraft:anvil", "S": "minecraft:smithing_table",
                                                   "F": f"{MOD}:plasma_smelter", "R": f"{MOD}:raritanium", "O": "minecraft:obsidian"},
                          f"{MOD}:alloy_forge"),
}


# --- 3D-Erze und 3D-Rohstoffe -------------------------------------------------------------------------------------------

ALL_FACES = ("north", "south", "east", "west", "up", "down")


def element(frm, to, tex: str, rot: tuple | None = None) -> dict:
    """Quader; Kristallflaechen zeigen die ganze Kristalltextur und bleiben unschattiert (wirken leuchtend)."""
    gem = tex == "#gem"
    faces = {f: {"uv": [0, 0, 16, 16] if gem else [4, 4, 12, 12], "texture": tex} for f in ALL_FACES}
    out = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if gem:
        out["shade"] = False
    if rot:
        axis, angle, origin = rot
        out["rotation"] = {"origin": [round(v, 3) for v in origin], "axis": axis, "angle": angle}
    return out


def crystal(face: str, a: float, b: float, w: float, h: float, angle: float, tex: str = "#gem") -> list[dict]:
    """Kristall, der aus einer Blockseite ragt (Fuss 1 px im Block versenkt), mit schmalerer Spitze."""
    w, h = w * 1.25, h * 1.35
    t = w * 0.7
    i0, i1 = w * 0.22, w * 0.78
    if face == "up":
        o = (a + w / 2, 15, b + w / 2)
        axis = "x" if int(a + b) % 2 else "z"
        return [element((a, 15, b), (a + w, 15 + h, b + w), tex, (axis, angle, o)),
                element((a + i0, 15 + h, b + i0), (a + i1, 15 + h + t, b + i1), tex, (axis, angle, o))]
    if face == "north":
        o = (a + w / 2, b + w / 2, 1)
        return [element((a, b, 1 - h), (a + w, b + w, 1), tex, ("y", angle, o)),
                element((a + i0, b + i0, 1 - h - t), (a + i1, b + i1, 1 - h), tex, ("y", angle, o))]
    if face == "south":
        o = (a + w / 2, b + w / 2, 15)
        return [element((a, b, 15), (a + w, b + w, 15 + h), tex, ("y", angle, o)),
                element((a + i0, b + i0, 15 + h), (a + i1, b + i1, 15 + h + t), tex, ("y", angle, o))]
    if face == "east":
        o = (15, b + w / 2, a + w / 2)
        return [element((15, b, a), (15 + h, b + w, a + w), tex, ("z", angle, o)),
                element((15 + h, b + i0, a + i0), (15 + h + t, b + i1, a + i1), tex, ("z", angle, o))]
    o = (1, b + w / 2, a + w / 2)   # west
    return [element((1 - h, b, a), (1, b + w, a + w), tex, ("z", angle, o)),
            element((1 - h - t, b + i0, a + i0), (1 - h, b + i1, a + i1), tex, ("z", angle, o))]


def stud(face: str, a: float, b: float, size: float, height: float) -> list[dict]:
    """Biotech-Knoten: Sockel aus Gestein mit leuchtender Kappe (wie Schaltkreis-Bolzen)."""
    if face == "up":
        return [element((a, 15, b), (a + size, 15 + height, b + size), "#all"),
                element((a + 0.5, 15 + height, b + 0.5), (a + size - 0.5, 15 + height + 0.8, b + size - 0.5), "#gem")]
    if face == "north":
        return [element((a, b, 1 - height), (a + size, b + size, 1), "#all"),
                element((a + 0.5, b + 0.5, 1 - height - 0.8), (a + size - 0.5, b + size - 0.5, 1 - height), "#gem")]
    if face == "east":
        return [element((15, b, a), (15 + height, b + size, a + size), "#all"),
                element((15 + height, b + 0.5, a + 0.5), (15 + height + 0.8, b + size - 0.5, a + size - 0.5), "#gem")]
    if face == "south":
        return [element((a, b, 15), (a + size, b + size, 15 + height), "#all"),
                element((a + 0.5, b + 0.5, 15 + height), (a + size - 0.5, b + size - 0.5, 15 + height + 0.8), "#gem")]
    return [element((1 - height, b, a), (1, b + size, a + size), "#all"),
            element((1 - height - 0.8, b + 0.5, a + 0.5), (1 - height, b + size - 0.5, a + size - 0.5), "#gem")]


def base_cube() -> dict:
    return {"from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": f} for f in ALL_FACES}}


ORE_SHAPES = {
    # Taydenit: gruene Kristalle wie Diamondheads, oben zwei grosse, an jeder Seite einer
    "taydenite_ore": (crystal("up", 3, 4, 3.5, 6, 22.5) + crystal("up", 9, 8, 3, 4.5, -22.5) + crystal("north", 9, 4, 2.5, 3.5, 22.5)
                      + crystal("south", 3, 8, 2.5, 3, -22.5) + crystal("east", 4, 5, 2.5, 3.5, 22.5) + crystal("west", 10, 3, 2, 3, -22.5)),
    # Mechamorph-Knoten: leuchtende Schaltkreis-Bolzen auf allen Seiten
    "biotech_node": (stud("up", 3, 3, 3, 1.5) + stud("up", 10, 9, 2.5, 1) + stud("north", 10, 5, 3, 1.5) + stud("south", 3, 9, 2.5, 1.2)
                     + stud("east", 6, 3, 3, 1.4) + stud("west", 4, 10, 2.5, 1)),
    # Infernit: gezackte Glutsplitter, stark geneigt
    "infernite_ore": (crystal("up", 4, 3, 3, 5, 45) + crystal("up", 9, 9, 2.5, 4, -45) + crystal("north", 4, 9, 2.5, 3, -45)
                      + crystal("east", 9, 4, 2.5, 3.5, 45) + crystal("south", 10, 3, 2, 3, 45) + crystal("west", 3, 6, 2, 2.5, -22.5)),
    # Celestium: lange, duenne Sternspitzen
    "celestium_ore": (crystal("up", 6.5, 6.5, 2.5, 8, 0) + crystal("up", 2, 2, 1.5, 5, -22.5) + crystal("up", 12, 11, 1.5, 4.5, 22.5)
                      + crystal("north", 7, 8, 1.5, 5, 0) + crystal("east", 3, 4, 1.5, 4, 22.5) + crystal("west", 11, 9, 1.5, 4, -22.5)
                      + crystal("south", 6, 3, 1.5, 3.5, 0)),
}
ORE_GEMS = {"taydenite_ore": "taydenite", "biotech_node": "biotech", "infernite_ore": "infernite", "celestium_ore": "celestium"}


def gem_texture(mat: str) -> Image.Image:
    """Kristallflaeche 16x16 in kraeftiger Farbe: Verlauf mittel -> dunkel, helle Glanzkante, zwei Facettenlinien."""
    _, _, dark, mid, light = MATERIALS[mat]
    img = Image.new("RGBA", (16, 16), rgb(mid))
    d = ImageDraw.Draw(img)
    for y in range(16):
        d.line((0, y, 15, y), fill=mix(mid, dark, (y / 15) ** 1.4 * 0.75))
    d.line((0, 0, 15, 0), fill=rgb(light))
    d.line((0, 1, 15, 1), fill=mix(light, mid, 0.5))
    d.line((4, 0, 6, 15), fill=mix(light, mid, 0.35))
    d.line((11, 0, 10, 15), fill=mix(dark, mid, 0.5))
    d.line((1, 2, 1, 13), fill=mix(light, mid, 0.6))
    return img


def ore_models() -> dict[Path, dict]:
    files: dict[Path, dict] = {}
    for ore, shape in ORE_SHAPES.items():
        files[ASSETS / "models" / "block" / f"{ore}_3d.json"] = {
            "parent": "minecraft:block/block", "textures": {"gem": f"{MOD}:block/{ore}_gem"},
            "elements": [base_cube()] + shape}
    return files


# --- Texturen der 3D-Aufbauten (Maschinen, Fusionsaltar) ----------------------------------------------------------------------------

SUITMAT = "item/suitmat"


def tex_metal(dark: str, mid: str, light: str, seed: int) -> Image.Image:
    """Gebuerstetes Metall: senkrechter Verlauf, feine Querstriche, helle Oberkante."""
    rng = np.random.default_rng(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            t = y / 15
            base = mix(light, mid, min(1.0, t * 1.6)) if t < 0.5 else mix(mid, dark, (t - 0.5) * 1.6)
            n = int(rng.integers(-8, 9))
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base[:3]) + (255,)
    for x in range(16):
        px[x, 0] = rgb(light)
        px[x, 15] = rgb(dark)
    return img


def tex_glow(mid: str, light: str) -> Image.Image:
    img = Image.new("RGBA", (16, 16), rgb(mid))
    d = ImageDraw.Draw(img)
    for r, t in ((7, 0.0), (5, 0.4), (3, 0.8)):
        d.rectangle((8 - r, 8 - r, 7 + r, 7 + r), fill=mix(mid, light, t))
    d.rectangle((6, 6, 9, 9), fill=(255, 255, 255, 255))
    return img


def el(frm, to, tex: str, rot: tuple | None = None, glow: bool = False) -> dict:
    out = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
           "faces": {f: {"uv": [0, 0, 16, 16], "texture": tex} for f in ALL_FACES}}
    if glow:
        out["shade"] = False
    if rot:
        axis, angle, origin = rot
        out["rotation"] = {"origin": [round(v, 3) for v in origin], "axis": axis, "angle": angle}
    return out


def suitmat_textures() -> dict[Path, Image.Image]:
    """Texturen der Maschinen-Aufbauten und des Fusionsaltars."""
    images = {ASSETS / "textures" / SUITMAT / "celestium_gem.png": gem_texture("celestium"),
              ASSETS / "textures" / SUITMAT / "fusion_glow.png": tex_glow("3AE8FF", "FFFFFF"),
              ASSETS / "textures" / SUITMAT / "machine_metal.png": tex_metal("2C3138", "6B7480", "A9B2BE", 11),
              ASSETS / "textures" / SUITMAT / "machine_dark.png": tex_metal("101216", "2A2E36", "4A505A", 12)}
    for name, (_, _, glow, accent) in MACHINES.items():
        images[ASSETS / "textures" / SUITMAT / f"{name}_glow_on.png"] = tex_glow(glow, "FFFFFF")
        images[ASSETS / "textures" / SUITMAT / f"{name}_glow.png"] = tex_glow(accent, glow)
    return images


# --- Upgrade-Module ---------------------------------------------------------------------------------------------------

# Modul -> (Deutsch, Englisch, Wirkung Deutsch, Wirkung Englisch); hergestellt in der Legierungsschmiede (MachineRecipes)
MODULES = {
    "module_shield_capacity": ("Modul: Schildkapazitaet", "Module: Shield Capacity", "+25 % Schildkapazitaet", "+25% shield capacity"),
    "module_shield_recharge": ("Modul: Schildlader", "Module: Shield Recharge", "+30 % Nachladen, -15 % Pause",
                               "+30% recharge, -15% delay"),
    "module_mobility": ("Modul: Antrieb", "Module: Propulsion", "+20 % Fluggeschwindigkeit, +40 % Treibstoff",
                        "+20% flight speed, +40% fuel"),
    "module_speed": ("Modul: Sprint", "Module: Sprint", "+5 % Lauftempo", "+5% movement speed"),
    "module_strength": ("Modul: Kraft", "Module: Strength", "+1,5 Angriffsschaden", "+1.5 attack damage"),
    "module_vitality": ("Modul: Vitalitaet", "Module: Vitality", "+1 Herz", "+1 heart"),
}
MODULE_LANG = {
    "item.kingdomomnitrix.module_blank": ("Leere Modulplatine", "Blank Module Board"),
    "tooltip.kingdomomnitrix.module.usage": ("Im Anzug-Konfigurator in einen Sockel stecken. Hergestellt in der Legierungsschmiede.",
                                             "Plug into a socket of the Suit Configurator. Made in the Alloy Forge."),
    "tooltip.kingdomomnitrix.module.limit": ("Wirkt nur im vollstaendigen Anzug, je Art hoechstens %s.",
                                             "Only works in a complete suit, at most %s of each kind."),
    "tooltip.kingdomomnitrix.suit.modules": ("Module: %s/%s (Ein- und Ausbau im Anzug-Konfigurator)",
                                             "Modules: %s/%s (install and remove in the Suit Configurator)"),
    "block.kingdomomnitrix.suit_configurator": ("Anzug-Konfigurator", "Suit Configurator"),
    "screen.kingdomomnitrix.configurator.piece": ("Anzugteil", "Suit piece"),
    "screen.kingdomomnitrix.configurator.empty": ("Anzugteil einlegen", "Insert a suit piece"),
    "screen.kingdomomnitrix.configurator.slots": ("Sockel belegt: %s/%s", "Sockets used: %s/%s"),
}


def configurator_files() -> tuple[dict[Path, dict], dict[Path, Image.Image]]:
    """Anzug-Konfigurator: Holo-Tisch (Sockel, Saeule, Tischplatte mit Leuchtring, Projektorlinse)."""
    textures = {"metal": f"{MOD}:{SUITMAT}/machine_metal", "dark": f"{MOD}:{SUITMAT}/machine_dark",
                "glow": f"{MOD}:{SUITMAT}/fusion_glow", "gem": f"{MOD}:{SUITMAT}/celestium_gem", "particle": f"{MOD}:{SUITMAT}/machine_dark"}
    elements = [el((1, 0, 1), (15, 3, 15), "#dark"), el((2, 3, 2), (14, 4, 14), "#metal"), el((4, 4, 4), (12, 10, 12), "#metal"),
                el((3.6, 6, 3.6), (12.4, 7, 12.4), "#glow", glow=True), el((2, 10, 2), (14, 12, 14), "#dark"),
                el((1.5, 12, 1.5), (14.5, 13, 14.5), "#metal"), el((6, 13, 6), (10, 14, 10), "#gem", glow=True)]
    for x0, z0, x1, z1 in ((2, 2, 14, 3), (2, 13, 14, 14), (2, 3, 3, 13), (13, 3, 14, 13)):
        elements.append(el((x0, 13, z0), (x1, 13.4, z1), "#glow", glow=True))
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        elements.append(el((x, 3, z), (x + 2, 11, z + 2), "#metal"))
        elements.append(el((x + 0.5, 11, z + 0.5), (x + 1.5, 12.5, z + 1.5), "#glow", glow=True))
    name = "suit_configurator"
    files: dict[Path, dict] = {
        ASSETS / "models" / "block" / f"{name}.json": {"parent": "minecraft:block/block", "textures": textures, "elements": elements,
                                                       "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0],
                                                                           "scale": [0.6, 0.6, 0.6]}}},
        ASSETS / "models" / "item" / f"{name}.json": {"parent": f"{MOD}:block/{name}"},
        ASSETS / "blockstates" / f"{name}.json": {"variants": {f"facing={d}": {"model": f"{MOD}:block/{name}", "y": y}
                                                                for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}},
        DATA / MOD / "loot_table" / "blocks" / f"{name}.json": {
            "type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}]}]},
        DATA / MOD / "recipe" / f"{name}.json": shaped(["GEG", "RCR", "DAD"], {
            "G": "minecraft:glass", "E": "minecraft:ender_eye", "R": f"{MOD}:raritanium_ingot", "C": f"{MOD}:taydenite_crystal",
            "D": "minecraft:polished_deepslate", "A": f"{MOD}:alloy_forge"}, f"{MOD}:{name}"),
    }
    return files, {}


# --- Endgame-Waffen (Java: suit/weapon) -------------------------------------------------------------------------------

# Waffe -> (Deutsch, Englisch, Spezialangriff Deutsch, Spezialangriff Englisch)
WEAPONS = {
    "taydenite_blade": ("Taydenit-Klinge", "Taydenite Blade", "Rechtsklick: Kristallspur - Diamondhead-Kristalle brechen in einer Linie aus dem Boden",
                        "Right-click: Crystal Trail - Diamondhead crystals burst from the ground in a line"),
    "galvan_rifle": ("Galvan-Biotech-Gewehr", "Galvan Biotech Rifle", "Rechtsklick: durchdringender Biotech-Strahl (32 Bloecke)",
                     "Right-click: piercing biotech beam (32 blocks)"),
    "star_sword": ("Sternenschwert", "Star Sword", "Rechtsklick: Sternenschlag - fuenf Meteore stuerzen auf den Zielpunkt",
                   "Right-click: Starfall - five meteors crash onto the target point"),
    "magnaplate_hammer": ("Magnaplate-Hammer", "Magnaplate Hammer", "Rechtsklick am Boden: Schockwellen-Schlag",
                          "Right-click on the ground: shockwave slam"),
    "carbonox_launcher": ("Carbonox-Werfer", "Carbonox Launcher", "Rechtsklick: explosive Plasmabolzen", "Right-click: explosive plasma bolts"),
    "infernox_ryno": ("Infernox-R.Y.N.O.", "Infernox R.Y.N.O.", "Rechtsklick: Salve aus sechs Raketen", "Right-click: volley of six rockets"),
}


# --- Fusionsaltar ----------------------------------------------------------------------------------------------------

# Bloecke des Altars: id -> (Deutsch, Englisch)
FUSION = {
    "fusion_core": ("Fusionskern", "Fusion Core"),
    "fusion_injector": ("Fusions-Injektor", "Fusion Injector"),
    "fusion_injector_galvan": ("Galvan-Injektor", "Galvan Injector"),
    "fusion_injector_stellar": ("Sternen-Injektor", "Stellar Injector"),
    "fusion_rune": ("Fusionsrune", "Fusion Rune"),
    "fusion_pylon": ("Altarsaeule", "Altar Pylon"),
    "resonance_capstone": ("Resonanzkristall", "Resonance Capstone"),
    "celestial_capstone": ("Sternenkrone", "Celestial Capstone"),
    "chaos_focus": ("Chaos-Fokus", "Chaos Focus"),
    "cryo_cooler": ("Kryo-Kuehler", "Cryo Cooler"),
}
# Gegenstaende des Altars (Icons aus suit_icons): id -> (Deutsch, Englisch)
FUSION_ITEMS = {
    "resonance_shard": ("Resonanz-Splitter", "Resonance Shard"),
    "nefarious_chip": ("Nefarious-Chip", "Nefarious Chip"),
    "chaos_heart": ("Chaos-Herz", "Chaos Heart"),
    "cryo_cell": ("Kryo-Zelle", "Cryo Cell"),
    "fusion_codex": ("Fusions-Kodex", "Fusion Codex"),
}
# Injektor-Stufen: Block -> Leuchtfarbe (mittel, hell), Aufbau-Stufe
INJECTORS = {"fusion_injector": ("2A9AB8", "3AE8FF", 1), "fusion_injector_galvan": ("1A8A2A", "4BFF6A", 2),
             "fusion_injector_stellar": ("5A4A9A", "D8CCFF", 3)}
FUSION_MESSAGES = {
    "message.kingdomomnitrix.fusion.no_recipe": ("Kein Fusionsrezept fuer dieses Mittelstueck.", "No fusion recipe for this centerpiece."),
    "message.kingdomomnitrix.fusion.plates": ("Injektoren: %s/%s mit %s", "Injectors: %s/%s holding %s"),
    "message.kingdomomnitrix.fusion.cores": ("Energiekerne im Kern: %s/%s", "Energy cores in the core: %s/%s"),
    "message.kingdomomnitrix.fusion.start": ("Fusion gestartet: %s", "Fusion started: %s"),
    "message.kingdomomnitrix.fusion.status": ("Altar: %s | Stabilitaet %s gegen Instabilitaet %s | Kuehlung %s",
                                              "Altar: %s | stability %s against instability %s | cooling %s"),
    "message.kingdomomnitrix.fusion.altar_tier": ("Dafuer braucht es den %s (dein Altar: %s). Sieh im Fusions-Kodex nach.",
                                                  "This needs the %s (your altar: %s). Check the Fusion Codex."),
    "message.kingdomomnitrix.fusion.injector_tier": ("Die Injektoren sind zu schwach: Stufe %s noetig.",
                                                     "The injectors are too weak: tier %s required."),
    "message.kingdomomnitrix.fusion.damaged": ("%s Injektor(en) durchgebrannt - mit einem Raritanium-Barren reparieren.",
                                               "%s injector(s) burned out - repair them with a Raritanium Ingot."),
    "message.kingdomomnitrix.fusion.repaired": ("Injektor repariert.", "Injector repaired."),
    "message.kingdomomnitrix.fusion.coolant": ("Kryo-Zelle eingesetzt: Hitze -%s", "Cryo Cell used: heat -%s"),
    "message.kingdomomnitrix.fusion.researched": ("Erforscht: %s", "Researched: %s"),
    "message.kingdomomnitrix.fusion.warn_heat": ("Der Kern glueht: %s %% Hitze - Kryo-Zelle einsetzen!",
                                                 "The core is glowing: %s %% heat - use a Cryo Cell!"),
    "message.kingdomomnitrix.fusion.event_spark": ("Funkenschlag! Die Fusion ist instabil.", "Sparks! The fusion is unstable."),
    "message.kingdomomnitrix.fusion.event_eject": ("Eine Platte wurde herausgeschleudert - leg sie schnell zurueck!",
                                                   "A plate was flung out - put it back quickly!"),
    "message.kingdomomnitrix.fusion.event_lightning": ("Energieentladung! Der Kern heizt sich auf.",
                                                       "Energy discharge! The core heats up."),
    "message.kingdomomnitrix.fusion.event_damage": ("Ein Injektor ist durchgebrannt! Mit einem Raritanium-Barren reparieren.",
                                                    "An injector burned out! Repair it with a Raritanium Ingot."),
    "message.kingdomomnitrix.fusion.fail_instability": ("Die Fusion ist kollabiert! Platten und Energiekerne sind verloren.",
                                                        "The fusion collapsed! Plates and energy cores are lost."),
    "message.kingdomomnitrix.fusion.fail_heat": ("Der Kern ist ueberhitzt! Platten und Energiekerne sind verloren.",
                                                 "The core overheated! Plates and energy cores are lost."),
    "message.kingdomomnitrix.fusion.fail_stalled": ("Die Fusion stand zu lange still und ist zusammengebrochen.",
                                                    "The fusion stalled for too long and broke down."),
    "hud.kingdomomnitrix.fusion.progress": ("Fusion %s %%", "Fusion %s %%"),
    "hud.kingdomomnitrix.fusion.heat": ("Hitze %s %%", "Heat %s %%"),
    "hud.kingdomomnitrix.fusion.instability": ("Instabilitaet %s", "Instability %s"),
    "hud.kingdomomnitrix.fusion.stalled": ("STOCKT - %s s", "STALLED - %s s"),
    "tooltip.kingdomomnitrix.fusion_codex.progress": ("Erforscht: %s/%s Altarstufen", "Researched: %s/%s altar tiers"),
    "tooltip.kingdomomnitrix.fusion_codex.usage": ("Benutzen: lesen. Schluesselgegenstand in der Zweithand: erforschen.",
                                                   "Use: read. Key item in the other hand: research."),
    "codex.kingdomomnitrix.locked": ("Nicht erforscht", "Not researched"),
    "codex.kingdomomnitrix.locked.hint": ("Nimm diesen Gegenstand in die Zweithand und benutze den Kodex.",
                                          "Hold this item in your other hand and use the codex."),
    "codex.kingdomomnitrix.intro.title": ("Der Fusionsaltar", "The Fusion Altar"),
    "codex.kingdomomnitrix.intro.text": (
        "Der Fusionsaltar verschmilzt Panzerplatten mit einem Ruestungsteil. Leg das Teil und die Energiekerne in den "
        "Kern, je eine Platte auf die Injektoren und starte mit Shift + Rechtsklick auf den Kern.\n\n"
        "Jede Ruestungsstufe braucht einen Altar und Injektoren derselben Stufe. Hoehere Altare entstehen, wenn du "
        "um den Kern herum baust - die Bauplaene stehen in diesem Kodex, sobald du ihren Schluessel erforscht hast.\n\n"
        "Blaettere weiter fuer die vier Stufen und die Gefahren.",
        "The Fusion Altar fuses armor plates into a piece of armor. Put the piece and the energy cores into the core, "
        "one plate on each injector, and start with Shift + right-click on the core.\n\n"
        "Every armor tier needs an altar and injectors of the same tier. Higher altars are built around the core - "
        "their blueprints appear in this codex once you research their key.\n\n"
        "Turn the page for the four tiers and the dangers."),
    "codex.kingdomomnitrix.tier1.title": ("Grund-Altar", "Basic Altar"),
    "codex.kingdomomnitrix.tier1.text": (
        "Fusionskern und bis zu acht Fusions-Injektoren im Umkreis von 8 Bloecken. Stell die Injektoren "
        "symmetrisch auf: jedes gegenueberliegende Paar mit gleicher Platte beruhigt die Fusion, ein Injektor ohne "
        "Partner macht sie unruhiger.\n\nReicht fuer: Taydenit und Magnaplate.",
        "Fusion core and up to eight fusion injectors within 8 blocks. Place injectors symmetrically: every opposite "
        "pair with the same plate calms the fusion, an injector without a partner makes it wilder.\n\n"
        "Enough for: Taydenite and Magnaplate."),
    "codex.kingdomomnitrix.tier2.title": ("Galvan-Altar", "Galvan Altar"),
    "codex.kingdomomnitrix.tier2.text": (
        "Acht Fusionsrunen im Boden rund um den Kern (Abstand 2, eine Ebene tiefer) und an den vier Ecken "
        "(Abstand 3) je eine Saeule aus zwei Altarsaeulen mit Resonanzkristall obenauf.\n\nSchluessel: "
        "Resonanz-Splitter aus den Petrosapien-Ruinen auf Petropia.\nInjektoren: Galvan-Injektoren.\n"
        "Reicht fuer: Galvan und Carbonox.",
        "Eight fusion runes in the floor around the core (distance 2, one level lower) and at the four corners "
        "(distance 3) a column of two altar pylons topped with a resonance capstone.\n\nKey: Resonance Shard from the "
        "Petrosapien ruins on Petropia.\nInjectors: Galvan Injectors.\nEnough for: Galvan and Carbonox."),
    "codex.kingdomomnitrix.tier3.title": ("Sternen-Altar", "Stellar Altar"),
    "codex.kingdomomnitrix.tier3.text": (
        "Ein zweiter Runenring (Abstand 4, eine Ebene tiefer - die Injektoren stehen darauf) und auf den Achsen "
        "(Abstand 6) vier Saeulen aus drei Altarsaeulen mit Sternenkrone.\n\nSchluessel: Nefarious-Chip - "
        "besiege Dr. Nefarious.\nInjektoren: Sternen-Injektoren.\nReicht fuer: Celestial und Infernox. Diese "
        "Fusionen werden heiss - ohne Kuehlung schmilzt der Kern.",
        "A second rune ring (distance 4, one level lower - the injectors stand on it) and on the axes (distance 6) "
        "four columns of three altar pylons with a celestial capstone.\n\nKey: Nefarious Chip - defeat Dr. Nefarious."
        "\nInjectors: Stellar Injectors.\nEnough for: Celestial and Infernox. These fusions run hot - without cooling "
        "the core melts."),
    "codex.kingdomomnitrix.tier4.title": ("Chaos-Altar", "Chaos Altar"),
    "codex.kingdomomnitrix.tier4.text": (
        "Der Chaos-Fokus schwebt fuenf Bloecke ueber dem Kern und zwingt die Energie in die Mitte. Der Chaos-Altar "
        "ist so stabil, dass selbst Stufe-3-Fusionen kaum noch ausbrechen.\n\nSchluessel: Chaos-Herz - bezwinge den "
        "Koloss am Ende der Geheimen Wasserstrasse.",
        "The Chaos Focus floats five blocks above the core and forces the energy into the center. The Chaos Altar is "
        "so stable that even tier 3 fusions rarely break out.\n\nKey: Chaos Heart - defeat the Colossus at the end of "
        "the Secret Waterway."),
    "codex.kingdomomnitrix.dangers.title": ("Gefahren", "Dangers"),
    "codex.kingdomomnitrix.dangers.text": (
        "Instabilitaet: Rezept gegen Stabilitaet (Altarstufe, Symmetrie). Ist sie hoch, schlaegt es Funken, Platten "
        "fliegen heraus (zuruecklegen!), Blitze fahren in die Injektoren, Injektoren brennen durch (Raritanium-Barren "
        "repariert sie) - oder die Fusion kollabiert.\n\nHitze: steigt mit der Stufe. Eis, Packeis, Blaues Eis und "
        "Kryo-Kuehler in 3 Bloecken um den Kern kuehlen, eine Kryo-Zelle (Rechtsklick auf den Kern) kuehlt sofort.\n\n"
        "Stockt die Fusion 15 Sekunden, bricht sie zusammen. Fehlschlag: Platten und Kerne sind weg, das Teil bleibt.",
        "Instability: recipe against stability (altar tier, symmetry). When it is high, sparks fly, plates get thrown "
        "out (put them back!), lightning hits the injectors, injectors burn out (a Raritanium Ingot repairs them) - or "
        "the fusion collapses.\n\nHeat: rises with the tier. Ice, packed ice, blue ice and cryo coolers within 3 "
        "blocks cool the core, a Cryo Cell (right-click the core) cools it at once.\n\nIf the fusion stalls for 15 "
        "seconds it breaks down. Failure: plates and cores are lost, the piece stays."),
}


def rune_texture() -> Image.Image:
    """Oberseite der Fusionsrune: dunkle Platte mit leuchtendem Runenkreis (32x32, wird ohne Schatten gezeichnet)."""
    img = Image.new("RGBA", (32, 32), rgb("101318"))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 31, 31), outline=rgb("2A2E36"))
    d.rectangle((2, 2, 29, 29), outline=rgb("1A1E24"))
    d.ellipse((5, 5, 26, 26), outline=rgb("7A6AFF"))
    d.ellipse((7, 7, 24, 24), outline=rgb("3A2E8A"))
    glyph = [(16, 8), (22, 16), (16, 24), (10, 16), (16, 8)]
    d.line(glyph, fill=rgb("C8BCFF"))
    d.line([(16, 11), (16, 21)], fill=rgb("FFFFFF"))
    d.line([(12, 16), (20, 16)], fill=rgb("C8BCFF"))
    for x, y in ((16, 4), (28, 16), (16, 28), (4, 16)):
        d.point((x, y), fill=rgb("C8BCFF"))
    return img


def faces(frm, to, textures: dict[str, str], glow: bool = False) -> dict:
    """Element mit eigener Textur je Seite (fehlende Seiten: "side")."""
    out = {"from": list(frm), "to": list(to),
           "faces": {f: {"uv": [0, 0, 16, 16], "texture": textures.get(f, textures["side"])} for f in ALL_FACES}}
    if glow:
        out["shade"] = False
    return out


def injector_elements(tier: int) -> list[dict]:
    out = [el((2, 0, 2), (14, 3, 14), "#dark"), el((3, 3, 3), (13, 4, 13), "#metal"), el((5, 4, 5), (11, 12, 11), "#metal"),
           el((4.5, 6, 4.5), (11.5, 7, 11.5), "#glow", glow=True), el((4.5, 9, 4.5), (11.5, 10, 11.5), "#glow", glow=True),
           el((3, 12, 3), (13, 14, 13), "#dark"), el((4, 14, 4), (12, 14.5, 12), "#glow", glow=True)]
    if tier >= 2:
        # Galvan: vier Greifarme um die Plattform
        for x, z in ((2, 2), (12.5, 2), (2, 12.5), (12.5, 12.5)):
            out += [el((x, 10, z), (x + 1.5, 16, z + 1.5), "#metal"), el((x + 0.3, 15, z + 0.3), (x + 1.2, 16.5, z + 1.2), "#glow", glow=True)]
    if tier >= 3:
        # Sternen: Kristallspitzen an den Seiten
        for x0, z0, x1, z1 in ((0.5, 7, 2, 9), (14, 7, 15.5, 9), (7, 0.5, 9, 2), (7, 14, 9, 15.5)):
            out.append(el((x0, 2, z0), (x1, 9, z1), "#gem", glow=True))
    return out


def capstone_elements(spikes: int) -> list[dict]:
    out = [el((2, 0, 2), (14, 2, 14), "#dark"), el((3, 2, 3), (13, 4, 13), "#metal"),
           el((6, 4, 6), (10, 14, 10), "#gem", glow=True), el((6.8, 14, 6.8), (9.2, 16, 9.2), "#gem", glow=True)]
    side = (("z", 22.5, (5, 4, 8), (3.5, 4, 7), (5.5, 11, 9)), ("z", -22.5, (11, 4, 8), (10.5, 4, 7), (12.5, 11, 9)),
            ("x", 22.5, (8, 4, 11), (7, 4, 10.5), (9, 11, 12.5)), ("x", -22.5, (8, 4, 5), (7, 4, 3.5), (9, 11, 5.5)))
    for axis, angle, origin, frm, to in side[:spikes]:
        out.append(el(frm, to, "#gem", (axis, angle, origin), True))
    return out


def fusion_models() -> tuple[dict[Path, dict], dict[Path, Image.Image]]:
    base = {"metal": f"{MOD}:{SUITMAT}/machine_metal", "dark": f"{MOD}:{SUITMAT}/machine_dark",
            "glow": f"{MOD}:{SUITMAT}/fusion_glow", "gem": f"{MOD}:{SUITMAT}/celestium_gem", "particle": f"{MOD}:{SUITMAT}/machine_dark"}
    images = {ASSETS / "textures" / SUITMAT / "fusion_rune_top.png": rune_texture(),
              ASSETS / "textures" / SUITMAT / "fusion_glow_damaged.png": tex_glow("8A0A06", "FF5A2A"),
              ASSETS / "textures" / SUITMAT / "chaos_glow.png": tex_glow("6A0A2A", "FF3A6A"),
              ASSETS / "textures" / SUITMAT / "cryo_glow.png": tex_glow("1A6A9A", "C8F4FF"),
              ASSETS / "textures" / SUITMAT / "taydenite_gem.png": gem_texture("taydenite")}
    for name, (mid, light, _) in INJECTORS.items():
        if name != "fusion_injector":
            images[ASSETS / "textures" / SUITMAT / f"{name}_glow.png"] = tex_glow(mid, light)
    core = [el((0, 0, 0), (16, 3, 16), "#dark"), el((1, 3, 1), (15, 5, 15), "#metal"), el((2, 5, 2), (14, 6, 14), "#dark"),
            el((3, 6, 3), (13, 12, 13), "#metal"), el((4, 12, 4), (12, 13, 12), "#dark"),
            el((5, 6.5, 2.6), (11, 11.5, 3.1), "#glow", glow=True), el((5, 6.5, 12.9), (11, 11.5, 13.4), "#glow", glow=True),
            el((2.6, 6.5, 5), (3.1, 11.5, 11), "#glow", glow=True), el((12.9, 6.5, 5), (13.4, 11.5, 11), "#glow", glow=True)]
    for x, z in ((0, 0), (13, 0), (0, 13), (13, 13)):
        core += [el((x, 3, z), (x + 3, 9, z + 3), "#metal"), el((x + 1, 9, z + 1), (x + 2, 11, z + 2), "#gem", glow=True)]
    rune_tex = {"side": "#dark", "up": "#rune"}
    pylon = [el((3, 0, 3), (13, 2, 13), "#dark"), el((4, 2, 4), (12, 14, 12), "#metal"), el((3, 14, 3), (13, 16, 13), "#dark"),
             el((3.6, 5, 3.6), (12.4, 6, 12.4), "#glow", glow=True), el((3.6, 10, 3.6), (12.4, 11, 12.4), "#glow", glow=True)]
    focus = [el((4, 4, 4), (12, 12, 12), "#glow", ("y", 45, (8, 8, 8)), True), el((4.5, 4.5, 4.5), (11.5, 11.5, 11.5), "#dark", ("x", 45, (8, 8, 8))),
             el((5, 5, 5), (11, 11, 11), "#glow", ("z", 45, (8, 8, 8)), True), el((7, 1, 7), (9, 15, 9), "#gem", glow=True),
             el((1, 7, 7), (15, 9, 9), "#gem", glow=True)]
    cooler = [faces((0, 0, 0), (16, 16, 16), {"side": "#metal", "up": "#dark", "down": "#dark"})]
    for x0, z0, x1, z1 in ((3, -0.3, 13, 0.2), (3, 15.8, 13, 16.3), (-0.3, 3, 0.2, 13), (15.8, 3, 16.3, 13)):
        for y in (3, 7, 11):
            cooler.append(el((x0, y, z0), (x1, y + 2, z1), "#glow", glow=True))
    models = {
        "fusion_core": (core, base),
        "fusion_rune": ([faces((0, 0, 0), (16, 16, 16), rune_tex)], dict(base, rune=f"{MOD}:{SUITMAT}/fusion_rune_top")),
        "fusion_pylon": (pylon, base),
        "resonance_capstone": (capstone_elements(2), dict(base, gem=f"{MOD}:{SUITMAT}/taydenite_gem",
                                                          glow=f"{MOD}:{SUITMAT}/fusion_injector_galvan_glow")),
        "celestial_capstone": (capstone_elements(4), base),
        "chaos_focus": (focus, dict(base, glow=f"{MOD}:{SUITMAT}/chaos_glow", gem=f"{MOD}:{SUITMAT}/chaos_glow")),
        "cryo_cooler": (cooler, dict(base, glow=f"{MOD}:{SUITMAT}/cryo_glow")),
    }
    files: dict[Path, dict] = {}
    display = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}}
    for name, (elements, textures) in models.items():
        files[ASSETS / "models" / "block" / f"{name}.json"] = {"parent": "minecraft:block/block", "textures": textures,
                                                               "elements": elements, "display": display}
        files[ASSETS / "blockstates" / f"{name}.json"] = {"variants": {"": {"model": f"{MOD}:block/{name}"}}}
    for name, (_, _, tier) in INJECTORS.items():
        glow = f"{MOD}:{SUITMAT}/fusion_glow" if tier == 1 else f"{MOD}:{SUITMAT}/{name}_glow"
        gem = f"{MOD}:{SUITMAT}/celestium_gem"
        for damaged in (False, True):
            model = f"{name}_damaged" if damaged else name
            textures = dict(base, glow=f"{MOD}:{SUITMAT}/fusion_glow_damaged" if damaged else glow, gem=gem)
            files[ASSETS / "models" / "block" / f"{model}.json"] = {"parent": "minecraft:block/block", "textures": textures,
                                                                    "elements": injector_elements(tier), "display": display}
        files[ASSETS / "blockstates" / f"{name}.json"] = {"variants": {
            "damaged=false": {"model": f"{MOD}:block/{name}"}, "damaged=true": {"model": f"{MOD}:block/{name}_damaged"}}}
    for name in FUSION:
        files[ASSETS / "models" / "item" / f"{name}.json"] = {"parent": f"{MOD}:block/{name}"}
        files[DATA / MOD / "loot_table" / "blocks" / f"{name}.json"] = {
            "type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}]}]}
    for path, data in fusion_recipes().items():
        files[path] = data
    return files, images


def fusion_recipes() -> dict[Path, dict]:
    m = MOD
    recipes = {
        "fusion_core": shaped(["DND", "AFA", "DOD"], {"D": "minecraft:diamond_block", "N": "minecraft:nether_star", "A": f"{m}:alloy_forge",
                                                     "F": "minecraft:beacon", "O": "minecraft:crying_obsidian"}, f"{m}:fusion_core"),
        "fusion_injector": shaped([" E ", "RBR", "OOO"], {"E": "minecraft:end_rod", "R": f"{m}:raritanium_ingot",
                                                         "B": "minecraft:diamond_block", "O": "minecraft:obsidian"}, f"{m}:fusion_injector"),
        "fusion_injector_galvan": shaped([" S ", "WIW", "GBG"], {"S": f"{m}:resonance_shard", "W": f"{m}:biotech_weave",
                                                                "I": f"{m}:fusion_injector", "G": f"{m}:biotech_gel",
                                                                "B": "minecraft:gold_block"}, f"{m}:fusion_injector_galvan"),
        "fusion_injector_stellar": shaped([" N ", "CIC", "CSC"], {"N": "minecraft:nether_star", "C": f"{m}:celestium_ingot",
                                                                 "I": f"{m}:fusion_injector_galvan", "S": f"{m}:resonance_shard"},
                                          f"{m}:fusion_injector_stellar"),
        "fusion_rune": shaped(["DTD", "RCR", "DTD"], {"D": "minecraft:polished_deepslate", "T": f"{m}:taydenite_crystal",
                                                     "R": f"{m}:raritanium_dust", "C": "minecraft:chiseled_deepslate"}, f"{m}:fusion_rune", 4),
        "fusion_pylon": shaped(["PIP", "PRP", "PIP"], {"P": "minecraft:deepslate_tiles", "I": "minecraft:iron_ingot",
                                                      "R": "minecraft:redstone_block"}, f"{m}:fusion_pylon", 3),
        "resonance_capstone": shaped([" S ", "STS", "GPG"], {"S": f"{m}:resonance_shard", "T": f"{m}:taydenite_plate",
                                                            "G": "minecraft:gold_ingot", "P": f"{m}:fusion_pylon"}, f"{m}:resonance_capstone"),
        "celestial_capstone": shaped([" N ", "CMC", "CPC"], {"N": f"{m}:nefarious_chip", "C": f"{m}:celestium_ingot",
                                                            "M": f"{m}:star_matrix", "P": f"{m}:fusion_pylon"}, f"{m}:celestial_capstone"),
        "chaos_focus": shaped(["IEI", "EHE", "IEI"], {"I": f"{m}:celestium_ingot", "E": "minecraft:end_crystal", "H": f"{m}:chaos_heart"},
                              f"{m}:chaos_focus"),
        "cryo_cooler": shaped(["IGI", "GBG", "IGI"], {"I": f"{m}:raritanium_ingot", "G": f"{m}:biotech_gel", "B": "minecraft:blue_ice"},
                              f"{m}:cryo_cooler"),
        "cryo_cell": shaped([" P ", "GBG", " P "], {"P": "minecraft:packed_ice", "G": "minecraft:glass", "B": f"{m}:biotech_gel"},
                            f"{m}:cryo_cell", 4),
        "fusion_codex": {"type": "minecraft:crafting_shapeless", "category": "misc",
                         "ingredients": [{"item": "minecraft:book"}, {"item": f"{m}:taydenite_crystal"}], "result": {"id": f"{m}:fusion_codex", "count": 1}},
    }
    return {DATA / MOD / "recipe" / f"{name}.json": data for name, data in recipes.items()}


def merged_tag(path: Path, values: list[str]) -> dict:
    """Tag-Datei ergaenzen, ohne fremde Eintraege anzufassen (andere Generatoren schreiben dieselben Tags)."""
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    existing = data.get("values", [])
    for v in values:
        if v not in existing:
            existing.append(v)
    return {"replace": False, "values": existing}


def png(img: Image.Image) -> bytes:
    buffer = io.BytesIO()
    img.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def outputs() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for item in ITEMS:
        img, frames = suit_icons.render(item)
        files[ASSETS / "textures" / "item" / f"{item}.png"] = png(img)
        if frames > 1:
            files[ASSETS / "textures" / "item" / f"{item}.png.mcmeta"] = (json.dumps(
                {"animation": {"frametime": 2, "interpolate": False}}, indent=2) + "\n").encode()
        files[ASSETS / "models" / "item" / f"{item}.json"] = (json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{item}"}}, indent=2) + "\n").encode()
    for path, img in suitmat_textures().items():
        files[path] = png(img)
    for ore, mat in ORE_GEMS.items():
        files[ASSETS / "textures" / "block" / f"{ore}_gem.png"] = png(gem_texture(mat))
    for path, data in ore_models().items():
        files[path] = (json.dumps(data, indent=1) + "\n").encode()
    for name, img in machine_textures().items():
        files[ASSETS / "textures" / "block" / f"{name}.png"] = png(img)
    for name in MACHINES:
        for path, data in machine_json(name).items():
            files[path] = (json.dumps(data, indent=2) + "\n").encode()
        files[DATA / MOD / "recipe" / f"{name}.json"] = (json.dumps(RECIPES[name], indent=2) + "\n").encode()
    fusion_files, fusion_images = fusion_models()
    for path, data in fusion_files.items():
        files[path] = (json.dumps(data, indent=1) + "\n").encode()
    for path, img in fusion_images.items():
        files[path] = png(img)
    config_files, _ = configurator_files()
    for path, data in config_files.items():
        files[path] = (json.dumps(data, indent=1) + "\n").encode()
    for item in list(FUSION_ITEMS) + ["module_blank"] + list(MODULES) + list(WEAPONS):
        img, frames = suit_icons.render(item)
        files[ASSETS / "textures" / "item" / f"{item}.png"] = png(img)
        if frames > 1:
            files[ASSETS / "textures" / "item" / f"{item}.png.mcmeta"] = (json.dumps(
                {"animation": {"frametime": 2, "interpolate": False}}, indent=2) + "\n").encode()
        parent = "minecraft:item/handheld" if item in WEAPONS else "minecraft:item/generated"
        files[ASSETS / "models" / "item" / f"{item}.json"] = (json.dumps(
            {"parent": parent, "textures": {"layer0": f"{MOD}:item/{item}"}}, indent=2) + "\n").encode()
    machines = [f"{MOD}:{n}" for n in MACHINES] + [f"{MOD}:{n}" for n in FUSION] + [f"{MOD}:suit_configurator"]
    for tag in ("mineable/pickaxe", "needs_iron_tool"):
        path = DATA / "minecraft" / "tags" / "block" / f"{tag}.json"
        files[path] = (json.dumps(merged_tag(path, machines), indent=2) + "\n").encode()
    for code, index in (("de_de", 0), ("en_us", 1)):
        path = ASSETS / "lang" / f"{code}.json"
        lang = json.loads(path.read_text(encoding="utf-8"))
        for item, (_, _, de, en) in ITEMS.items():
            lang[f"item.{MOD}.{item}"] = (de, en)[index]
        for name, (de, en, _, _) in MACHINES.items():
            lang[f"block.{MOD}.{name}"] = (de, en)[index]
        for name, (de, en) in FUSION.items():
            lang[f"block.{MOD}.{name}"] = (de, en)[index]
        for name, (de, en) in FUSION_ITEMS.items():
            lang[f"item.{MOD}.{name}"] = (de, en)[index]
        for name, (de, en, effect_de, effect_en) in MODULES.items():
            lang[f"item.{MOD}.{name}"] = (de, en)[index]
            lang[f"item.{MOD}.{name}.effect"] = (effect_de, effect_en)[index]
        for key, (de, en) in MODULE_LANG.items():
            lang[key] = (de, en)[index]
        for name, (de, en, special_de, special_en) in WEAPONS.items():
            lang[f"item.{MOD}.{name}"] = (de, en)[index]
            lang[f"tooltip.{MOD}.weapon.{name}"] = (special_de, special_en)[index]
        lang[f"tooltip.{MOD}.weapon.cooldown"] = ("Abklingzeit: %s s", "Cooldown: %s s")[index]
        for key, (de, en) in FUSION_MESSAGES.items():
            lang[key] = (de, en)[index]
        files[path] = (json.dumps(lang, indent=2, ensure_ascii=False) + "\n").encode()
    return files


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    files = outputs()
    if args.check:
        stale = [p for p, data in files.items() if not p.exists() or p.read_bytes() != data]
        for path in stale:
            LOG.error("veraltet: %s", path.relative_to(ROOT))
        LOG.info("Ruestungs-Materialien: %d Dateien, %d veraltet", len(files), len(stale))
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    LOG.info("Ruestungs-Materialien geschrieben: %d Items, %d Maschinen, %d Dateien", len(ITEMS), len(MACHINES), len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
