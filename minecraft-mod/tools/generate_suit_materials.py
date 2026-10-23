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
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_machine_textures as mt  # noqa: E402
import planet_textures as tx  # noqa: E402

LOG = logging.getLogger("generate_suit_materials")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
DATA = ROOT / "src" / "main" / "resources" / "data"
MOD = "kingdomomnitrix"
H = tx.hexc
S = 4          # Ueberabtastung der Item-Bilder
N = 64         # Item-Bildgroesse (wie die anderen Planeten-Items)

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


# --- Item-Bilder -------------------------------------------------------------------------------------------------------

def canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (N * S, N * S), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def finish(img: Image.Image) -> Image.Image:
    """Verkleinern, harte Alphakante, dunkle Umrandung wie bei Vanilla-Items."""
    small = img.resize((N, N), Image.LANCZOS)
    arr = np.asarray(small).copy()
    arr[..., 3] = np.where(arr[..., 3] > 110, 255, 0)
    solid = arr[..., 3] > 0
    outline = np.zeros_like(solid)
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        outline |= np.roll(np.roll(solid, dy, 0), dx, 1)
    outline &= ~solid
    arr[outline] = (20, 16, 24, 255)
    return Image.fromarray(arr, "RGBA")


def facet_poly(draw, points, dark, mid, light):
    draw.polygon(points, fill=rgb(mid))
    cx = sum(p[0] for p in points) / len(points)
    cy = sum(p[1] for p in points) / len(points)
    for i in range(len(points)):
        a, b = points[i], points[(i + 1) % len(points)]
        shade = light if (a[1] + b[1]) / 2 < cy else dark
        draw.polygon([a, b, (cx, cy)], fill=mix(mid, shade, 0.45))


def item_image(form: str, mat: str, name: str) -> Image.Image:
    _, _, dark, mid, light = MATERIALS[mat]
    rng = np.random.default_rng(sum(ord(c) for c in name))
    img, d = canvas()
    s = S
    if form == "raw":
        return tx.item_raw(name, H(dark), H(mid), H(light))
    if form == "ingot":
        return tx.item_ingot(H(dark), H(mid), H(light))
    if form == "dust":
        # Haeufchen aus Koernern mit hellen Glanzkoernern
        d.ellipse((10 * s, 30 * s, 54 * s, 56 * s), fill=rgb(dark))
        d.ellipse((14 * s, 26 * s, 50 * s, 50 * s), fill=rgb(mid))
        d.ellipse((22 * s, 20 * s, 42 * s, 40 * s), fill=mix(mid, light, 0.35))
        for _ in range(60):
            x, y = rng.uniform(14, 50), rng.uniform(22, 52)
            r = rng.uniform(0.8, 1.8)
            col = rgb(light) if rng.random() < 0.3 else (rgb(dark) if rng.random() < 0.5 else rgb(mid))
            d.ellipse(((x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s), fill=col)
        return finish(img)
    if form == "crystal":
        facet_poly(d, [(32 * s, 6 * s), (48 * s, 22 * s), (42 * s, 56 * s), (22 * s, 56 * s), (16 * s, 22 * s)], dark, mid, light)
        d.line((32 * s, 6 * s, 32 * s, 56 * s), fill=rgb(light), width=s)
        return finish(img)
    if form == "gel":
        d.ellipse((12 * s, 18 * s, 52 * s, 56 * s), fill=rgb(dark))
        d.ellipse((15 * s, 20 * s, 49 * s, 52 * s), fill=rgb(mid, 255))
        for i in range(4):
            x0 = 18 + i * 8
            d.line((x0 * s, 30 * s, (x0 + 4) * s, 46 * s), fill=rgb(light), width=s)
        d.ellipse((20 * s, 24 * s, 28 * s, 30 * s), fill=(255, 255, 255, 255))
        return finish(img)
    if form == "weave":
        d.rounded_rectangle((10 * s, 10 * s, 54 * s, 54 * s), radius=6 * s, fill=rgb("16181C"))
        for i in range(6):
            c = 13 + i * 7
            d.line((c * s, 12 * s, c * s, 52 * s), fill=rgb(mid), width=2 * s)
            d.line((12 * s, c * s, 52 * s, c * s), fill=mix(dark, mid, 0.6), width=s)
        d.ellipse((26 * s, 26 * s, 38 * s, 38 * s), fill=rgb(light))
        return finish(img)
    if form == "core":
        for r, col in ((24, dark), (20, mid), (12, light)):
            d.ellipse(((32 - r) * s, (32 - r) * s, (32 + r) * s, (32 + r) * s), fill=rgb(col))
        for k in range(8):
            a = k * math.pi / 4
            d.line(((32 + math.cos(a) * 14) * s, (32 + math.sin(a) * 14) * s, (32 + math.cos(a) * 24) * s,
                    (32 + math.sin(a) * 24) * s), fill=rgb("FFF0A0"), width=s)
        return finish(img)
    if form == "matrix":
        d.polygon([(32 * s, 4 * s), (58 * s, 32 * s), (32 * s, 60 * s), (6 * s, 32 * s)], fill=rgb("0A0618"))
        for _ in range(40):
            x, y = rng.uniform(14, 50), rng.uniform(14, 50)
            if abs(x - 32) + abs(y - 32) < 24:
                d.point((x * s, y * s), fill=(255, 255, 255, 255))
                d.ellipse(((x - 0.6) * s, (y - 0.6) * s, (x + 0.6) * s, (y + 0.6) * s), fill=(255, 255, 255, 255))
        d.polygon([(32 * s, 4 * s), (58 * s, 32 * s), (32 * s, 60 * s), (6 * s, 32 * s)], outline=rgb(mid), width=2 * s)
        d.ellipse((26 * s, 26 * s, 38 * s, 38 * s), fill=rgb(light))
        return finish(img)
    # Panzerplatten: geschwungene Platte mit Fase, Nieten und Emblem
    base = {"plate": mid, "plate_dark": "3A3E44", "plate_hot": "8A4A16"}[form]
    edge = {"plate": light, "plate_dark": "B8C0C8", "plate_hot": "FFD488"}[form]
    glow = {"plate": light, "plate_dark": "FF2A2A", "plate_hot": "FF8A1A"}[form]
    pts = [(10 * s, 12 * s), (54 * s, 12 * s), (56 * s, 40 * s), (32 * s, 58 * s), (8 * s, 40 * s)]
    d.polygon(pts, fill=rgb(base))
    inner = [(16 * s, 17 * s), (48 * s, 17 * s), (50 * s, 38 * s), (32 * s, 51 * s), (14 * s, 38 * s)]
    d.polygon(inner, fill=mix(base, "000000", 0.25))
    d.line(pts + [pts[0]], fill=rgb(edge), width=2 * s)
    for x, y in ((14, 16), (50, 16), (12, 38), (52, 38)):
        d.ellipse(((x - 1.5) * s, (y - 1.5) * s, (x + 1.5) * s, (y + 1.5) * s), fill=rgb(edge))
    d.polygon([(32 * s, 24 * s), (40 * s, 32 * s), (32 * s, 42 * s), (24 * s, 32 * s)], fill=rgb(glow))
    return finish(img)


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


def machine_json(name: str) -> dict[Path, dict]:
    files: dict[Path, dict] = {}
    for lit in (False, True):
        model = f"{name}_on" if lit else name
        files[ASSETS / "models" / "block" / f"{model}.json"] = {
            "parent": "minecraft:block/orientable",
            "textures": {"front": f"{MOD}:block/{name}_front{'_on' if lit else ''}", "side": f"{MOD}:block/{name}_side",
                         "top": f"{MOD}:block/{name}_top"}}
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


def shaped(pattern: list[str], keys: dict[str, str], result: str) -> dict:
    return {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: {"item": v} for k, v in keys.items()}, "result": {"id": result, "count": 1}}


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
RAW_OF = {"raw_taydenite": "taydenite_ore", "raw_biotech": "biotech_node", "raw_infernite": "infernite_ore", "raw_celestium": "celestium_ore"}
GUI_DISPLAY = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
               "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
               "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
               "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
               "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}


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
    for raw, ore in RAW_OF.items():
        # Rohstoff als kleiner 3D-Brocken: Gesteinssplitter mit drei Kristallen
        chunk = [element((4, 0, 4), (12, 5, 12), "#all"), element((5, 5, 5), (11, 7, 10), "#all")]
        if ore == "biotech_node":
            chunk += [element((5, 7, 5), (8, 8.5, 8), "#gem"), element((9, 5, 6), (11, 9, 8), "#gem"),
                      element((5.5, 5, 8.5), (7.5, 8, 10.5), "#gem")]
        else:
            chunk += [element((7, 5, 7), (9.5, 14, 9.5), "#gem", ("x", 22.5, (8, 5, 8))),
                      element((4.5, 4, 5), (6.5, 10, 7), "#gem", ("z", 22.5, (5.5, 4, 6))),
                      element((9.5, 4, 8.5), (11.5, 9, 10.5), "#gem", ("z", -22.5, (10.5, 4, 9.5)))]
        files[ASSETS / "models" / "item" / f"{raw}.json"] = {
            "parent": "minecraft:block/block",
            "textures": {"all": f"{MOD}:block/{ore}", "gem": f"{MOD}:block/{ore}_gem", "particle": f"{MOD}:item/{raw}"},
            "elements": chunk, "display": GUI_DISPLAY}
    return files


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
    for item, (form, mat, _, _) in ITEMS.items():
        files[ASSETS / "textures" / "item" / f"{item}.png"] = png(item_image(form, mat, item))
        files[ASSETS / "models" / "item" / f"{item}.json"] = (json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{item}"}}, indent=2) + "\n").encode()
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
    machines = [f"{MOD}:{n}" for n in MACHINES]
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
