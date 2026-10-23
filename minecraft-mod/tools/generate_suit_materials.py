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
            "elements": chunk, "display": ITEM_DISPLAY}
    return files


# --- 3D-Item-Modelle aller Zwischenstufen ----------------------------------------------------------------------------

SUITMAT = "item/suitmat"
# Sonderpaletten fuer Platten, die nicht die Grundfarbe des Materials tragen
PALETTE_OVERRIDE = {"carbonox_plate": ("1A1C20", "5A6068", "B8C0C8"), "hellfire_plate": ("3A1A08", "9A5A1E", "FFD488")}
GLOW_OVERRIDE = {"carbonox_plate": ("6A0A0A", "FF2A2A", "FFC0B0"), "hellfire_plate": ("6A1A06", "FF8A1A", "FFF0A0")}


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


def tex_dust(dark: str, mid: str, light: str, seed: int) -> Image.Image:
    rng = np.random.default_rng(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            px[x, y] = rgb(light) if r < 0.12 else rgb(dark) if r < 0.4 else rgb(mid)
    return img


def tex_glow(mid: str, light: str) -> Image.Image:
    img = Image.new("RGBA", (16, 16), rgb(mid))
    d = ImageDraw.Draw(img)
    for r, t in ((7, 0.0), (5, 0.4), (3, 0.8)):
        d.rectangle((8 - r, 8 - r, 7 + r, 7 + r), fill=mix(mid, light, t))
    d.rectangle((6, 6, 9, 9), fill=(255, 255, 255, 255))
    return img


def tex_cosmic(seed: int) -> Image.Image:
    rng = np.random.default_rng(seed)
    img = Image.new("RGBA", (16, 16), rgb("0A0618"))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.18:
                px[x, y] = rgb("2A1050")
            if rng.random() < 0.06:
                px[x, y] = (255, 255, 255, 255)
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


def ring(cy: float, r: float, t: float, tex: str, vertical: bool = False) -> list[dict]:
    """Viereckiger Ring um die Mitte (8, cy, 8): waagerecht oder senkrecht (in der x-y-Ebene)."""
    lo, hi = 8 - r, 8 + r
    if vertical:
        return [el((lo, cy - r, 8 - t / 2), (hi, cy - r + t, 8 + t / 2), tex, glow=True),
                el((lo, cy + r - t, 8 - t / 2), (hi, cy + r, 8 + t / 2), tex, glow=True),
                el((lo, cy - r, 8 - t / 2), (lo + t, cy + r, 8 + t / 2), tex, glow=True),
                el((hi - t, cy - r, 8 - t / 2), (hi, cy + r, 8 + t / 2), tex, glow=True)]
    return [el((lo, cy, lo), (hi, cy + t, lo + t), tex, glow=True), el((lo, cy, hi - t), (hi, cy + t, hi), tex, glow=True),
            el((lo, cy, lo), (lo + t, cy + t, hi), tex, glow=True), el((hi - t, cy, lo), (hi, cy + t, hi), tex, glow=True)]


def shape(form: str) -> list[dict]:
    if form == "dust":
        out = [el((3, 0, 3), (13, 2, 13), "#dust"), el((4, 2, 4), (12, 4, 12), "#dust"), el((5.5, 4, 5.5), (10.5, 5.5, 10.5), "#dust"),
               el((7, 5.5, 7), (9, 6.5, 9), "#dust")]
        for x, z in ((2, 7), (13.5, 9), (8, 2), (6, 13.5), (12, 3.5)):
            out.append(el((x, 0, z), (x + 1, 1, z + 1), "#gem", glow=True))
        return out
    if form == "crystal":
        return [el((5, 0, 5), (11, 1.5, 11), "#metal"),
                el((6.5, 1, 6.5), (9.5, 11, 9.5), "#gem", glow=True), el((7.2, 11, 7.2), (8.8, 14, 8.8), "#gem", glow=True),
                el((4.5, 1, 6.5), (6.5, 7, 8.5), "#gem", ("z", 22.5, (5.5, 1, 7.5)), True),
                el((9.5, 1, 7.5), (11.5, 6, 9.5), "#gem", ("z", -22.5, (10.5, 1, 8.5)), True),
                el((7, 1, 9.5), (9, 5, 11.5), "#gem", ("x", 22.5, (8, 1, 10.5)), True)]
    if form == "ingot":
        return [el((2, 0, 5), (14, 2.5, 11), "#metal"), el((3, 2.5, 6), (13, 4, 10), "#metal"),
                el((6, 4, 7.4), (10, 4.3, 8.6), "#glow", glow=True), el((7.4, 4, 6.4), (8.6, 4.3, 9.6), "#glow", glow=True)]
    if form == "gel":
        return [el((5.5, 0, 5.5), (10.5, 1.5, 10.5), "#metal"), el((5.5, 11, 5.5), (10.5, 12.5, 10.5), "#metal"),
                el((7, 12.5, 7), (9, 13.5, 9), "#metal"), el((6.2, 1.5, 6.2), (9.8, 11, 9.8), "#gem", glow=True),
                el((7, 4, 7), (8, 5, 8), "#glow", glow=True), el((8.2, 7, 8.2), (9, 7.8, 9), "#glow", glow=True)]
    if form == "weave":
        out = [el((2, 0, 2), (14, 1, 14), "#metal")]
        for i in range(5):
            c = 2.6 + i * 2.3
            out.append(el((2, 1, c), (14, 1.8, c + 1), "#gem", glow=True))
            out.append(el((c, 1.6, 2), (c + 1, 2.4, 14), "#metal"))
        out.append(el((6.5, 2.4, 6.5), (9.5, 3.4, 9.5), "#glow", glow=True))
        return out
    if form == "core":
        return ([el((5, 3, 5), (11, 9, 11), "#metal"), el((4, 4, 6), (12, 8, 10), "#metal"), el((6, 4, 4), (10, 8, 12), "#metal"),
                 el((6, 2, 6), (10, 10, 10), "#glow", glow=True), el((3.8, 5, 7), (12.2, 7, 9), "#glow", glow=True)]
                + ring(5.7, 6.5, 0.6, "#gem") + ring(6.0, 5.5, 0.6, "#gem", vertical=True))
    if form == "matrix":
        return ([el((5, 3, 5), (11, 9, 11), "#cosmic", ("y", 45, (8, 6, 8))), el((5.5, 3.5, 5.5), (10.5, 8.5, 10.5), "#cosmic", ("x", 45, (8, 6, 8))),
                 el((7, 5, 7), (9, 7, 9), "#glow", glow=True)] + ring(5.7, 6.5, 0.5, "#gem") + ring(6.0, 6.0, 0.5, "#gem", vertical=True))
    # Platten: gewoelbte Panzerplatte aus drei Segmenten (Mitte flach, Vorder- und Hinterteil abgeknickt),
    # erhabenes Mittelpaneel, leuchtende Naht, Nieten und Kern-Edelstein
    out = [el((1.5, 3, 5.5), (14.5, 5, 10.5), "#metal"), el((3.5, 5, 6.2), (12.5, 6, 9.8), "#metal"),
           el((6.5, 6, 6.8), (9.5, 7, 9.2), "#gem", glow=True), el((7.2, 7, 7.4), (8.8, 7.5, 8.6), "#glow", glow=True),
           el((1.5, 5, 7.6), (3.5, 5.4, 8.4), "#glow", glow=True), el((12.5, 5, 7.6), (14.5, 5.4, 8.4), "#glow", glow=True)]
    for x in (2.2, 13):
        out.append(el((x, 5, 5.9), (x + 0.8, 5.6, 6.7), "#gem", glow=True))
        out.append(el((x, 5, 9.3), (x + 0.8, 5.6, 10.1), "#gem", glow=True))
    for z0, z1, pivot, angle in ((1, 5.5, 5.5, -22.5), (10.5, 15, 10.5, 22.5)):
        rot = ("x", angle, (8, 3, pivot))
        out.append(el((1.5, 3, z0), (14.5, 5, z1), "#metal", rot))
        out.append(el((3, 5, z0 + 0.8), (13, 5.6, z1 - 0.8), "#metal", rot))
        edge = z0 if z0 < pivot else z1 - 0.6
        out.append(el((1.5, 4.4, edge), (14.5, 5.2, edge + 0.6), "#glow", rot, True))
    return out


ITEM_DISPLAY = {"gui": {"rotation": [25, -35, 0], "translation": [0, 0.5, 0], "scale": [1.08, 1.08, 1.08]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def item_model_files() -> tuple[dict[Path, dict], dict[Path, Image.Image]]:
    models: dict[Path, dict] = {}
    images: dict[Path, Image.Image] = {}
    for mat, (_, _, dark, mid, light) in MATERIALS.items():
        seed = sum(ord(c) for c in mat)
        images[ASSETS / "textures" / SUITMAT / f"{mat}_metal.png"] = tex_metal(dark, mid, light, seed)
        images[ASSETS / "textures" / SUITMAT / f"{mat}_dust.png"] = tex_dust(dark, mid, light, seed)
        images[ASSETS / "textures" / SUITMAT / f"{mat}_gem.png"] = gem_texture(mat)
        images[ASSETS / "textures" / SUITMAT / f"{mat}_glow.png"] = tex_glow(mid, light)
    images[ASSETS / "textures" / SUITMAT / "cosmic.png"] = tex_cosmic(7)
    images[ASSETS / "textures" / SUITMAT / "fusion_glow.png"] = tex_glow("3AE8FF", "FFFFFF")
    images[ASSETS / "textures" / SUITMAT / "machine_metal.png"] = tex_metal("2C3138", "6B7480", "A9B2BE", 11)
    images[ASSETS / "textures" / SUITMAT / "machine_dark.png"] = tex_metal("101216", "2A2E36", "4A505A", 12)
    for name, (_, _, glow, accent) in MACHINES.items():
        images[ASSETS / "textures" / SUITMAT / f"{name}_glow_on.png"] = tex_glow(glow, "FFFFFF")
        images[ASSETS / "textures" / SUITMAT / f"{name}_glow.png"] = tex_glow(accent, glow)
    for item, pal in PALETTE_OVERRIDE.items():
        images[ASSETS / "textures" / SUITMAT / f"{item}_metal.png"] = tex_metal(*pal, sum(ord(c) for c in item))
        g = GLOW_OVERRIDE[item]
        images[ASSETS / "textures" / SUITMAT / f"{item}_glow.png"] = tex_glow(g[1], g[2])
    for item, (form, mat, _, _) in ITEMS.items():
        if form == "raw":
            continue
        own = item in PALETTE_OVERRIDE
        textures = {"metal": f"{MOD}:{SUITMAT}/{item if own else mat}_metal", "gem": f"{MOD}:{SUITMAT}/{mat}_gem",
                    "dust": f"{MOD}:{SUITMAT}/{mat}_dust", "glow": f"{MOD}:{SUITMAT}/{item if own else mat}_glow",
                    "cosmic": f"{MOD}:{SUITMAT}/cosmic", "particle": f"{MOD}:item/{item}"}
        models[ASSETS / "models" / "item" / f"{item}.json"] = {"parent": "minecraft:block/block", "textures": textures,
                                                               "elements": shape(form.split("_")[0]), "display": ITEM_DISPLAY}
    return models, images


# --- Fusionsaltar ----------------------------------------------------------------------------------------------------

FUSION = {"fusion_core": ("Fusionskern", "Fusion Core"), "fusion_injector": ("Fusions-Injektor", "Fusion Injector")}
FUSION_MESSAGES = {
    "message.kingdomomnitrix.fusion.no_recipe": ("Kein Fusionsrezept fuer dieses Mittelstueck.", "No fusion recipe for this centerpiece."),
    "message.kingdomomnitrix.fusion.plates": ("Injektoren: %s/%s mit %s", "Injectors: %s/%s holding %s"),
    "message.kingdomomnitrix.fusion.cores": ("Energiekerne im Kern: %s/%s", "Energy cores in the core: %s/%s"),
    "message.kingdomomnitrix.fusion.start": ("Fusion gestartet: %s", "Fusion started: %s"),
}


def fusion_models() -> dict[Path, dict]:
    textures = {"metal": f"{MOD}:{SUITMAT}/machine_metal", "dark": f"{MOD}:{SUITMAT}/machine_dark",
                "glow": f"{MOD}:{SUITMAT}/fusion_glow", "gem": f"{MOD}:{SUITMAT}/celestium_gem",
                "particle": f"{MOD}:{SUITMAT}/machine_dark"}
    core = [el((0, 0, 0), (16, 3, 16), "#dark"), el((1, 3, 1), (15, 5, 15), "#metal"), el((2, 5, 2), (14, 6, 14), "#dark"),
            el((3, 6, 3), (13, 12, 13), "#metal"), el((4, 12, 4), (12, 13, 12), "#dark"),
            el((5, 6.5, 2.6), (11, 11.5, 3.1), "#glow", glow=True), el((5, 6.5, 12.9), (11, 11.5, 13.4), "#glow", glow=True),
            el((2.6, 6.5, 5), (3.1, 11.5, 11), "#glow", glow=True), el((12.9, 6.5, 5), (13.4, 11.5, 11), "#glow", glow=True)]
    for x, z in ((0, 0), (13, 0), (0, 13), (13, 13)):
        core += [el((x, 3, z), (x + 3, 9, z + 3), "#metal"), el((x + 1, 9, z + 1), (x + 2, 11, z + 2), "#gem", glow=True)]
    injector = [el((2, 0, 2), (14, 3, 14), "#dark"), el((3, 3, 3), (13, 4, 13), "#metal"), el((5, 4, 5), (11, 12, 11), "#metal"),
                el((4.5, 6, 4.5), (11.5, 7, 11.5), "#glow", glow=True), el((4.5, 9, 4.5), (11.5, 10, 11.5), "#glow", glow=True),
                el((3, 12, 3), (13, 14, 13), "#dark"), el((4, 14, 4), (12, 14.5, 12), "#glow", glow=True)]
    files = {}
    for name, elements in (("fusion_core", core), ("fusion_injector", injector)):
        files[ASSETS / "models" / "block" / f"{name}.json"] = {"parent": "minecraft:block/block", "textures": textures, "elements": elements,
                                                               "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0],
                                                                                   "scale": [0.6, 0.6, 0.6]}}}
        files[ASSETS / "models" / "item" / f"{name}.json"] = {"parent": f"{MOD}:block/{name}"}
        files[ASSETS / "blockstates" / f"{name}.json"] = {"variants": {"": {"model": f"{MOD}:block/{name}"}}}
        files[DATA / MOD / "loot_table" / "blocks" / f"{name}.json"] = {
            "type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}]}]}
    files[DATA / MOD / "recipe" / "fusion_core.json"] = shaped(["DND", "AFA", "DOD"], {
        "D": "minecraft:diamond_block", "N": "minecraft:nether_star", "A": f"{MOD}:alloy_forge", "F": "minecraft:beacon",
        "O": "minecraft:crying_obsidian"}, f"{MOD}:fusion_core")
    files[DATA / MOD / "recipe" / "fusion_injector.json"] = shaped([" E ", "RBR", "OOO"], {
        "E": "minecraft:end_rod", "R": f"{MOD}:raritanium_ingot", "B": "minecraft:diamond_block", "O": "minecraft:obsidian"},
        f"{MOD}:fusion_injector")
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
    item_models, item_images = item_model_files()
    for path, img in item_images.items():
        files[path] = png(img)
    for path, data in item_models.items():
        files[path] = (json.dumps(data, indent=1) + "\n").encode()
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
    for path, data in fusion_models().items():
        files[path] = (json.dumps(data, indent=1) + "\n").encode()
    machines = [f"{MOD}:{n}" for n in MACHINES] + [f"{MOD}:{n}" for n in FUSION]
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
