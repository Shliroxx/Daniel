#!/usr/bin/env python3
"""Erzeugt die 3D-Modelle der sechs Endgame-Waffen (GeckoLib-Items) — Modell, Textur, Leuchtmaske, Animation, Item-Modell.

Vorbilder: Schusswaffen-Mods (MrCrayfish, TaCZ) mit Koerper, Griff, Magazin, Laufmantel, Visier; Nahkampf-Mods (Spartan,
Simply Swords) mit klarer Silhouette aus Klinge, Parierstange, Griff und Knauf; Ratchet-&-Clank-Waffen mit wuchtigen
Rahmen, Energiezellen und drehenden Teilen. Jede Waffe hat bewegte Teile (kreisende Splitter, Spulenringe, Kolben,
Raketentrommel), die als Leerlauf-Animation laufen, und leuchtende Teile (Glowmask).

Koordinaten in Modellpixeln (16 = ein Block). Nahkampfwaffen stehen senkrecht (Griff unten, Klinge nach +y),
Schusswaffen liegen waagerecht mit der Muendung nach -z. Die Haltung in der Hand steht im Item-Modell (display).
Im Inventar zeigt die Waffe ihr gemaltes Symbol (item/<name>_icon), ueberall sonst das 3D-Modell.

    python tools/generate_weapon_models.py            # schreibt alle Waffen
    python tools/generate_weapon_models.py --check    # prueft, ob die Dateien aktuell sind
    python tools/generate_weapon_models.py --preview out.png   # Vorschau (tools/render_geo.py)
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import math
import subprocess
import sys
import zlib
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_armor_models import (FACES, LOOP, Atlas, C, Cube, Palette, face_dims, merge, mirror,  # noqa: E402
                                   paint_face, spin, wave)

LOG = logging.getLogger("generate_weapon_models")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
GEO_DIR = ASSETS / "geo" / "item" / "weapon"
TEX_DIR = ASSETS / "textures" / "item" / "weapon"
ANIM_DIR = ASSETS / "animations" / "item" / "weapon"
MODEL_DIR = ASSETS / "models" / "item"
DENSITY = 6  # Texturpixel je Modelleinheit (Waffen sind klein und werden nah gesehen)


@dataclass
class Weapon:
    pal: Palette
    kind: str                                                  # "blade", "hammer" oder "gun"
    cubes: list[Cube] = field(default_factory=list)            # Hauptteil (Bone "root")
    subbones: dict[str, tuple[list[float], list[Cube]]] = field(default_factory=dict)  # Name -> (Drehpunkt, Quader)
    anim: dict[str, dict] = field(default_factory=dict)

    def sub(self, name: str, pivot: list[float], cubes: list[Cube]) -> None:
        self.subbones[name] = (pivot, cubes)


# --- Bausteine -------------------------------------------------------------------------------------------------------

def box(cx: float, cy: float, cz: float, sx: float, sy: float, sz: float, mat: str = "plate", **kw) -> Cube:
    """Quader um einen Mittelpunkt (bequemer fuer Waffen als Ursprung + Groesse)."""
    return C((cx - sx / 2, cy - sy / 2, cz - sz / 2), (sx, sy, sz), mat, **kw)


def wrap(cx: float, cz: float, y0: float, y1: float, thick: float, mat: str = "trim", step: float = 1.4,
         tint: str | None = None) -> list[Cube]:
    """Griffwicklung: duenne Ringe entlang eines senkrechten Griffs."""
    out = []
    y = y0 + 0.4
    while y < y1 - 0.4:
        out.append(box(cx, y, cz, thick, 0.45, thick, mat, seams=False, tint=tint))
        y += step
    return out


def ring_xy(cx: float, cy: float, z: float, r: float, t: float = 0.5, depth: float = 0.6, mat: str = "glow") -> list[Cube]:
    """Ring quer zur z-Achse (um einen Lauf) aus vier Balken."""
    return [C((cx - r, cy + r - t, z), (2 * r, t, depth), mat, seams=False), C((cx - r, cy - r, z), (2 * r, t, depth), mat, seams=False),
            C((cx - r, cy - r, z), (t, 2 * r, depth), mat, seams=False), C((cx + r - t, cy - r, z), (t, 2 * r, depth), mat, seams=False)]


def ring_xz(cx: float, cy: float, cz: float, r: float, t: float = 0.5, mat: str = "glow") -> list[Cube]:
    """Waagerechter Ring um eine senkrechte Klinge."""
    return [C((cx - r, cy, cz - r), (2 * r, t, t), mat, seams=False), C((cx - r, cy, cz + r - t), (2 * r, t, t), mat, seams=False),
            C((cx - r, cy, cz - r), (t, t, 2 * r), mat, seams=False), C((cx + r - t, cy, cz - r), (t, t, 2 * r), mat, seams=False)]


def blade(cy0: float, length: float, width: float, thick: float, mat: str, edge_mat: str, tip_mat: str, **kw) -> list[Cube]:
    """Klinge aus einem Stueck: Kernplatte, abgeschraegte Schneiden (duenner) zu beiden Seiten, Spitze in drei Stufen."""
    body = length * 0.82
    cy = cy0 + body / 2
    out = [box(0, cy, 0, width, body, thick, mat, **kw)]
    for side in (-1, 1):
        out.append(box(side * (width / 2 + 0.3), cy - 0.6, 0, 0.6, body - 1.2, thick * 0.45, edge_mat, seams=False))
    rest = length - body
    y = cy0 + body
    for frac, w in ((0.45, width * 0.72), (0.33, width * 0.42), (0.22, width * 0.16)):
        h = rest * frac
        out.append(box(0, y + h / 2, 0, w, h + 0.02, thick * 0.8, tip_mat, seams=False))
        y += h
    return out


def shard(x: float, y: float, z: float, w: float, h: float, tilt_x: float, tilt_z: float, mat: str = "gem") -> list[Cube]:
    """Kristallsplitter: Schaft und Spitze, gekippt um den Fusspunkt."""
    pivot = (x, y, z)
    return [C((x - w / 2, y, z - w / 2), (w, h * 0.7, w), mat, rot=(tilt_x, 0, tilt_z), pivot=pivot, seams=False),
            C((x - w * 0.3, y + h * 0.7, z - w * 0.3), (w * 0.6, h * 0.3, w * 0.6), mat, rot=(tilt_x, 0, tilt_z), pivot=pivot, seams=False)]


# --- Die sechs Waffen ------------------------------------------------------------------------------------------------

def taydenite_blade() -> Weapon:
    """Diamondhead-Kristallschwert: facettierte Kristallklinge mit Leuchtader, Parierstange mit Kristallzacken,
    schwarz-weiss gewickelter Griff, Omnitrix-Knauf, drei kreisende Splitter um die Parierstange."""
    pal = Palette("2ACA8A", "0E4A34", "B8FFE0", "141414", "E8F0EC", "3AFFB0", "F0FFF8", "5AF0C0", "3AFFB0")
    w = Weapon(pal, "blade")
    w.cubes += [box(0, 1.2, 0, 2.6, 2.4, 2.6, "omni"), box(0, 2.7, 0, 1.8, 0.6, 1.8, "trim")]
    w.cubes += [box(0, 5.7, 0, 1.6, 5.4, 1.6, "suit")] + wrap(0, 0, 3.0, 8.4, 1.9, tint="E8F0EC")
    w.cubes += [box(0, 9.0, 0, 7.0, 1.4, 2.2, "plate", lines=[("h", 0.5, ("north", "south"))]),
                box(0, 9.9, 0, 3.4, 0.6, 1.6, "trim")]
    w.cubes += shard(-3.2, 9.0, 0, 1.1, 3.6, 0, 50) + shard(3.2, 9.0, 0, 1.1, 3.6, 0, -50)
    w.cubes += blade(10.2, 24.0, 2.6, 1.0, "gem", "glow", "gem")
    w.cubes += [box(0, 21.0, -0.5, 0.45, 20.0, 0.2, "glow"), box(0, 21.0, 0.5, 0.45, 20.0, 0.2, "glow")]
    for i, y in enumerate((14.0, 19.0, 24.0)):
        side = 1 if i % 2 == 0 else -1
        w.cubes += shard(side * 1.3, y, 0, 0.6, 2.6, 0, -side * 55)
    orbit = []
    for i in range(3):
        a = math.radians(i * 120)
        orbit += shard(math.cos(a) * 4.2, 8.6 + i * 0.8, math.sin(a) * 4.2, 0.7, 2.2, 15 * math.sin(a), 15 * math.cos(a))
    w.sub("orbit", [0, 9.0, 0], orbit)
    w.anim = {"orbit": merge(spin(1, 2), wave("position", 1, 0.5, 4))}
    return w


def galvan_rifle() -> Weapon:
    """Galvan-Biotech-Gewehr: schwarzer Kantenkoerper mit gruenen Kanaelen, Zielfernrohr, langer Lauf mit drei
    kreisenden Spulenringen, Energiezelle als Magazin, schraeger Griff, Skelettschaft."""
    pal = Palette("16191C", "050607", "4A5058", "C8CCD0", "2A2E34", "3AFF5A", "E8FFE0", "3AFF5A", "3AFF5A")
    w = Weapon(pal, "gun")
    green = (("north", "south", "east", "west"))
    w.cubes += [box(0, 6.0, 0, 3.0, 3.6, 11.0, "plate", lines=[("h", 0.55, ("east", "west"))]),
                box(0, 8.2, -1.0, 2.2, 0.8, 8.0, "trim"),
                box(0, 4.0, -2.5, 2.4, 0.8, 5.0, "plate")]
    w.cubes += [box(0, 9.6, 0.5, 1.4, 1.6, 6.0, "trim"), box(0, 9.6, -2.7, 1.8, 2.0, 0.6, "trim"),
                box(0, 9.6, -3.05, 1.2, 1.2, 0.2, "visor"), box(0, 9.6, 3.7, 1.6, 1.8, 0.6, "trim")]
    w.cubes += [box(0, 6.4, -11.0, 1.5, 1.5, 11.0, "trim"), box(0, 6.4, -6.6, 2.2, 2.2, 2.2, "plate"),
                box(0, 6.4, -16.7, 2.2, 2.2, 1.0, "plate", edge=("north",)), box(0, 6.4, -17.25, 1.0, 1.0, 0.2, "glow")]
    w.cubes += [box(0, 2.6, -1.6, 1.8, 3.0, 2.0, "glow", edge=green), box(0, 1.0, -1.6, 2.0, 0.5, 2.2, "trim")]
    w.cubes += [C((-0.8, 0.2, 2.2), (1.6, 4.2, 1.8), "suit", rot=(-18, 0, 0), pivot=(0, 4.2, 3.1))]
    w.cubes += [box(0, 6.6, 7.6, 1.2, 1.0, 4.4, "trim"), box(0, 4.4, 8.2, 1.2, 1.0, 3.4, "trim", rot=(20, 0, 0)),
                box(0, 5.5, 10.4, 1.8, 4.0, 1.2, "plate", lines=[("v", 0.5, ("north", "south"))])]
    for side in (-1, 1):
        w.cubes.append(box(side * 1.55, 6.0, -0.5, 0.2, 0.6, 9.0, "glow"))
    coils = ring_xy(0, 6.4, -9.0, 1.9) + ring_xy(0, 6.4, -11.8, 1.6) + ring_xy(0, 6.4, -14.4, 1.3)
    w.sub("coils", [0, 6.4, -11.0], coils)
    w.anim = {"coils": merge(spin(2, 3), wave("scale", 0, 0.06, 4))}
    return w


def star_sword() -> Weapon:
    """Sternenschwert (Celestialsapien): Klinge aus Sternenhimmel mit weisser Leuchtkante, weisse Hoernerparierstange,
    Sternknauf, kreisender Heiligenschein und schwebende Sternfunken."""
    pal = Palette("0A0618", "000000", "E8E8FF", "06040E", "F4F4FF", "C8E8FF", "FFFFFF", "FFFFFF", "FFFFFF", cosmic=True)
    w = Weapon(pal, "blade")
    w.cubes += [box(0, 1.0, 0, 2.2, 2.0, 2.2, "gem"), box(0, 2.3, 0, 1.4, 0.6, 1.4, "trim")]
    w.cubes += [box(0, 5.6, 0, 1.5, 6.0, 1.5, "suit")] + wrap(0, 0, 2.6, 8.6, 1.8)
    w.cubes += [box(0, 9.2, 0, 4.0, 1.6, 2.2, "trim"), box(0, 9.2, 0, 1.6, 1.6, 2.4, "gem")]
    for side in (-1, 1):
        w.cubes += [C((side * 2.0 - (0.0 if side > 0 else 3.6), 8.8, -0.7), (3.6, 1.2, 1.4), "trim", rot=(0, 0, side * 20),
                      pivot=(side * 2.0, 9.4, 0)),
                    C((side * 5.2 - 0.5, 9.6, -0.5), (1.0, 3.4, 1.0), "trim", rot=(0, 0, -side * 35), pivot=(side * 5.2, 9.6, 0))]
    w.cubes += blade(10.0, 27.0, 3.0, 1.0, "plate", "glow", "glow", seams=False)
    halo = ring_xz(0, 0, 0, 3.6, 0.45)
    w.sub("halo", [0, 12.0, 0], [C((c.origin[0], c.origin[1] + 12.0, c.origin[2]), c.size, "glow", seams=False) for c in halo])
    motes = [box(math.cos(math.radians(a)) * 3.0, 16.0 + i * 4.5, math.sin(math.radians(a)) * 3.0, 0.6, 0.6, 0.6, "glow")
             for i, a in enumerate((0, 90, 180, 270))]
    w.sub("motes", [0, 20.0, 0], motes)
    w.anim = {"halo": merge(spin(1, 1), wave("position", 1, 0.6, 2)), "motes": merge(spin(1, -2), wave("position", 1, 1.0, 4))}
    return w


def magnaplate_hammer() -> Weapon:
    """Magnaplate-Hammer (Ratchet): Stahlschaft mit Gummigriff und Knaufkappe, wuchtiger Kopf mit Schlagflaechen,
    blauem Energiefenster, Kuehlrippen, Klauenruecken wie ein OmniWrench und zwei arbeitenden Kolben."""
    pal = Palette("1E2A4A", "0A1020", "6A7A9A", "141A2A", "B8C0C8", "3A9AFF", "E0F2FF", "3A9AFF", "3A9AFF")
    w = Weapon(pal, "hammer")
    w.cubes += [box(0, 0.8, 0, 2.4, 1.6, 2.4, "plate"), box(0, 1.9, 0, 1.6, 0.6, 1.6, "trim")]
    w.cubes += [box(0, 11.0, 0, 1.5, 18.0, 1.5, "trim")]
    w.cubes += [box(0, 5.0, 0, 2.0, 6.0, 2.0, "suit")] + wrap(0, 0, 2.2, 8.0, 2.2, "plate")
    w.cubes += [box(0, 17.0, 0, 2.2, 1.2, 2.2, "plate", lines=[("h", 0.5, ("north", "south", "east", "west"))])]
    w.cubes += [box(0, 22.5, 0, 9.0, 7.0, 6.0, "plate"),
                box(-5.2, 22.5, 0, 1.4, 7.8, 6.8, "trim"), box(-6.3, 22.5, 0, 0.8, 6.4, 5.4, "plate"),
                box(0, 22.5, -3.05, 3.6, 3.6, 0.2, "gem"), box(0, 22.5, 3.05, 3.6, 3.6, 0.2, "gem")]
    for y in (19.6, 25.4):
        w.cubes.append(box(0, y, 0, 9.4, 0.5, 6.4, "trim", seams=False))
    w.cubes += [C((4.5, 20.6, -2.0), (3.8, 1.2, 1.4), "plate", rot=(0, 0, -15), pivot=(4.5, 21.2, -1.3)),
                C((4.5, 20.6, 0.6), (3.8, 1.2, 1.4), "plate", rot=(0, 0, -15), pivot=(4.5, 21.2, 1.3)),
                C((4.5, 23.4, -2.0), (3.8, 1.2, 1.4), "plate", rot=(0, 0, 15), pivot=(4.5, 24.0, -1.3)),
                C((4.5, 23.4, 0.6), (3.8, 1.2, 1.4), "plate", rot=(0, 0, 15), pivot=(4.5, 24.0, 1.3))]
    pistons = [box(-2.2, 27.0, 0, 1.2, 2.0, 1.2, "trim"), box(2.2, 27.0, 0, 1.2, 2.0, 1.2, "trim"),
               box(-2.2, 28.2, 0, 1.6, 0.5, 1.6, "glow"), box(2.2, 28.2, 0, 1.6, 0.5, 1.6, "glow")]
    w.sub("pistons", [0, 26.0, 0], pistons)
    w.anim = {"pistons": wave("position", 1, 0.6, 4, base=-0.4)}
    return w


def carbonox_launcher() -> Weapon:
    """Carbonox-Plasmawerfer (Ratchet, Combuster-Art): Panzerkoerper mit Seitenschlitzen, dicker Lauf mit
    Kuehlrippen und Gluehmuendung, roter Plasmatank mit Rissen obenauf, Tragebuegel, Griff und Vordergriff."""
    pal = Palette("6A7078", "2A2E34", "B8C0C8", "1C1E22", "3A3E44", "FF2A2A", "FFC0B0", "FF2A2A", "FF2A2A")
    w = Weapon(pal, "gun")
    w.cubes += [box(0, 6.0, 0.5, 4.2, 4.6, 10.0, "plate", lines=[("h", 0.3, ("east", "west"))]),
                box(0, 3.4, -1.0, 3.0, 1.0, 6.0, "trim"), box(0, 6.0, 6.2, 3.4, 4.0, 1.6, "plate")]
    w.cubes += [box(0, 6.4, -8.0, 3.2, 3.2, 7.0, "trim")]
    for z in (-5.6, -7.2, -8.8, -10.4):
        w.cubes.append(box(0, 6.4, z, 4.4, 4.4, 0.4, "plate", seams=False))
    w.cubes += [box(0, 6.4, -12.0, 3.8, 3.8, 1.2, "plate", edge=("north",)), box(0, 6.4, -12.65, 2.0, 2.0, 0.2, "glow")]
    w.cubes += [box(0, 9.6, 0.0, 2.2, 1.8, 6.0, "glow", cracks=True, edge=("east", "west")),
                box(0, 9.6, -3.2, 2.6, 2.2, 0.6, "trim"), box(0, 9.6, 3.2, 2.6, 2.2, 0.6, "trim")]
    w.cubes += [box(0, 11.4, -0.6, 1.0, 0.6, 5.0, "trim"), box(0, 10.8, -3.0, 1.0, 1.0, 0.6, "trim"),
                box(0, 10.8, 1.8, 1.0, 1.0, 0.6, "trim")]
    w.cubes += [C((-0.8, 0.0, 2.6), (1.6, 3.8, 1.8), "suit", rot=(-15, 0, 0), pivot=(0, 3.8, 3.5)),
                box(0, 2.4, -4.0, 1.4, 3.2, 1.4, "suit")]
    for side in (-1, 1):
        for z in (-2.0, 0.0, 2.0):
            w.cubes.append(box(side * 2.15, 6.6, z, 0.2, 2.0, 1.0, "glow"))
    tank = [box(0, 9.6, 0.0, 2.3, 1.9, 6.1, "glow")]
    w.sub("tank", [0, 9.6, 0.0], tank)
    w.anim = {"tank": wave("scale", 0, 0.05, 4)}
    return w


def infernox_ryno() -> Weapon:
    """Infernox-R.Y.N.O. (Ratchet): Werfer mit drehender Sechsfach-Raketentrommel (gluehende Sprengkoepfe),
    Gehaeuse mit Hitzerissen, Trommelmagazin an der Seite, Zielsensor, Griff, Vordergriff und Schulterstuetze."""
    pal = Palette("8A4A16", "2A1006", "E8A04A", "3A140C", "D8A03A", "FF6A0A", "FFF0A0", "FFB02A", "FFC02A")
    w = Weapon(pal, "gun")
    dark, steel = "34383E", "C8CCD0"
    w.cubes += [box(0, 6.0, 2.0, 5.0, 5.0, 9.0, "plate", cracks=True, tint=dark), box(0, 6.0, -3.2, 6.0, 6.0, 1.4, "trim", tint=steel),
                box(0, 6.0, -12.6, 6.4, 6.4, 1.0, "trim", edge=("north",), tint=steel),
                box(-1.6, 8.55, 2.0, 0.5, 0.2, 9.1, "glow", seams=False), box(1.6, 8.55, 2.0, 0.5, 0.2, 9.1, "glow", seams=False)]
    w.cubes += [box(0, 9.2, 3.0, 1.6, 1.4, 4.0, "trim"), box(0, 9.2, 0.8, 1.2, 1.2, 0.2, "visor")]
    w.cubes += [box(3.3, 5.6, 2.2, 1.6, 3.6, 3.6, "trim"), box(4.15, 5.6, 2.2, 0.1, 2.4, 2.4, "gem")]
    w.cubes += [C((-0.8, 0.0, 4.2), (1.6, 4.0, 1.8), "suit", rot=(-15, 0, 0), pivot=(0, 4.0, 5.1)),
                box(0, 2.0, -1.2, 1.4, 3.0, 1.4, "suit"), box(0, 5.4, 8.6, 4.0, 4.4, 1.2, "plate", tint=dark),
                box(0, 3.0, 9.0, 3.2, 1.0, 2.0, "trim")]
    drum = [box(0, 6.0, -8.0, 1.6, 1.6, 8.0, "trim")]
    for i in range(6):
        a = math.radians(i * 60 + 30)
        x, y = math.cos(a) * 2.0, 6.0 + math.sin(a) * 2.0
        drum += [box(x, y, -7.9, 1.5, 1.5, 9.0, "trim", seams=False, tint="9AA0A8"), box(x, y, -12.5, 1.1, 1.1, 0.4, "glow")]
    w.sub("drum", [0, 6.0, -8.0], drum)
    w.anim = {"drum": spin(2, 2)}
    return w


WEAPONS = {"taydenite_blade": taydenite_blade, "galvan_rifle": galvan_rifle, "star_sword": star_sword,
           "magnaplate_hammer": magnaplate_hammer, "carbonox_launcher": carbonox_launcher, "infernox_ryno": infernox_ryno}

# Haltung je Waffenart (Vanilla-Item-Display). Nahkampf: senkrecht modelliert, schraeg nach vorn in der Faust;
# Schusswaffen: Muendung nach vorn, Kolben zur Schulter.
DISPLAY = {
    "blade": {
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 1.0], "scale": [0.62, 0.62, 0.62]},
        "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 1.5, 1.0], "scale": [0.62, 0.62, 0.62]},
        "firstperson_righthand": {"rotation": [-30, 0, 0], "translation": [1.0, -1.0, 0.0], "scale": [0.36, 0.36, 0.36]},
        "firstperson_lefthand": {"rotation": [-30, 0, 0], "translation": [1.0, -1.0, 0.0], "scale": [0.36, 0.36, 0.36]},
        "ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 0, -45], "translation": [-2, -2, 0], "scale": [0.42, 0.42, 0.42]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.5, 0.5, 0.5]},
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    },
    "hammer": {
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 0.5, 1.0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 0.5, 1.0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_righthand": {"rotation": [-30, 0, 0], "translation": [1.0, -1.5, 0.0], "scale": [0.38, 0.38, 0.38]},
        "firstperson_lefthand": {"rotation": [-30, 0, 0], "translation": [1.0, -1.5, 0.0], "scale": [0.38, 0.38, 0.38]},
        "ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 0, -45], "translation": [-1, -1, 0], "scale": [0.45, 0.45, 0.45]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.5, 0.5, 0.5]},
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    },
    "gun": {
        "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 3.0, -1.5], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0, 3.0, -1.5], "scale": [0.6, 0.6, 0.6]},
        "firstperson_righthand": {"rotation": [0, -10, 0], "translation": [2.5, -0.5, -1.5], "scale": [0.46, 0.46, 0.46]},
        "firstperson_lefthand": {"rotation": [0, 10, 0], "translation": [2.5, -0.5, -1.5], "scale": [0.46, 0.46, 0.46]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, -2, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.6, 0.6, 0.6]},
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    },
}


# --- Ausgabe ---------------------------------------------------------------------------------------------------------

def build(name: str, weapon: Weapon) -> tuple[dict, Image.Image, Image.Image]:
    groups = [("root", [0.0, 0.0, 0.0], weapon.cubes)] + [(n, p, c) for n, (p, c) in weapon.subbones.items()]
    width = 256
    atlas = Atlas(width)
    placed = []
    for gi, (_, _, cubes) in enumerate(groups):
        for ci, cube in enumerate(cubes):
            uv = {}
            for face in FACES:
                fw, fh = face_dims(cube.size, face)
                pw = max(1, int(round(fw * DENSITY)))
                ph = max(1, int(round(fh * DENSITY)))
                x, y = atlas.place(pw, ph)
                uv[face] = (x, y, pw, ph)
                placed.append((gi, ci, face, x, y, pw, ph))
            cube.uv = uv  # type: ignore[attr-defined]
    tw, th = atlas.width, atlas.height
    color = np.zeros((th, tw, 4), dtype=np.uint8)
    mask = np.zeros((th, tw, 4), dtype=np.uint8)
    seed_base = sum(ord(c) for c in name) * 7919
    for gi, ci, face, x, y, pw, ph in placed:
        cube = groups[gi][2][ci]
        c, m = paint_face(weapon.pal, cube, face, pw, ph, seed_base + zlib.crc32(f"{gi}/{ci}/{face}".encode()) % 100000)
        color[y:y + ph, x:x + pw] = c
        mask[y:y + ph, x:x + pw] = m
    bones = []
    for gname, pivot, cubes in groups:
        entries = []
        for cube in cubes:
            entry = {"origin": [round(v, 4) for v in cube.origin], "size": [round(v, 4) for v in cube.size],
                     "uv": {f: {"uv": [cube.uv[f][0], cube.uv[f][1]], "uv_size": [cube.uv[f][2], cube.uv[f][3]]} for f in FACES}}
            if cube.rotation:
                entry["rotation"] = [round(v, 4) for v in cube.rotation]
                p = cube.pivot or [cube.origin[0] + cube.size[0] / 2, cube.origin[1], cube.origin[2] + cube.size[2] / 2]
                entry["pivot"] = [round(v, 4) for v in p]
            entries.append(entry)
        bone = {"name": gname, "pivot": [round(v, 4) for v in pivot], "cubes": entries}
        if gname != "root":
            bone["parent"] = "root"
        bones.append(bone)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.kingdomomnitrix.weapon.{name}", "texture_width": tw, "texture_height": th,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}
    return geo, Image.fromarray(color, "RGBA"), Image.fromarray(mask, "RGBA")


def animation(name: str, weapon: Weapon) -> dict:
    return {"format_version": "1.8.0", "animations": {f"animation.kingdomomnitrix.weapon.{name}.idle": {
        "loop": True, "animation_length": LOOP, "bones": dict(weapon.anim)}}}


def png(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def outputs() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for name, make in WEAPONS.items():
        weapon = make()
        geo, color, mask = build(name, weapon)
        files[GEO_DIR / f"{name}.geo.json"] = (json.dumps(geo, indent=1) + "\n").encode()
        files[ANIM_DIR / f"{name}.animation.json"] = (json.dumps(animation(name, weapon), indent=1) + "\n").encode()
        files[TEX_DIR / f"{name}.png"] = png(color)
        files[TEX_DIR / f"{name}_glowmask.png"] = png(mask)
        model = {"parent": "minecraft:builtin/entity", "gui_light": "front",
                 "textures": {"particle": f"kingdomomnitrix:item/{name}"}, "display": DISPLAY[weapon.kind]}
        files[MODEL_DIR / f"{name}.json"] = (json.dumps(model, indent=2) + "\n").encode()
    return files


def preview(out: Path) -> None:
    """Vorschau aller Waffen nebeneinander (Grundbild, ohne Leuchten)."""
    specs = [f"{GEO_DIR / (n + '.geo.json')}:{TEX_DIR / (n + '.png')}" for n in WEAPONS]
    labels = sum((["--label", n] for n in WEAPONS), [])
    subprocess.run([sys.executable, str(ROOT / "tools" / "render_geo.py"), str(out), *specs, "--views", "side,three_quarter",
                    "--scale", "10", "--background", "#2A3038", *labels], check=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    parser.add_argument("--preview", type=Path, help="Vorschaubild aller Waffen schreiben")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    files = outputs()
    if args.check:
        stale = [p for p, data in files.items() if not p.exists() or p.read_bytes() != data]
        for path in stale:
            LOG.error("veraltet: %s", path.relative_to(ROOT))
        LOG.info("Waffenmodelle: %d Dateien, %d veraltet", len(files), len(stale))
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    LOG.info("Waffenmodelle geschrieben: %d Waffen, %d Dateien", len(WEAPONS), len(files))
    if args.preview:
        preview(args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
