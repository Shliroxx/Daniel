#!/usr/bin/env python3
"""Erzeugt die GeckoLib-Modelle der NPCs (Yen Sid, Max Tennyson, Clank): Geometrie, Textur, Leuchtmaske und
Animationen (idle, walk, talk, wave) unter assets/kingdomomnitrix/{geo,animations,textures}/entity/npc/.

Aufbau wie bei den Ruestungen und Waffen: jede Quaderflaeche bekommt ein eigenes Feld in der Textur (6 Pixel je
Modelleinheit) und wird nach Material bemalt. Zu den Metall-, Leucht- und Edelstein-Materialien aus
generate_armor_models.py kommen hier Haut, Gesichter, Stoff mit Falten, Hawaiihemd, Haar und Bart.

    python tools/generate_npc_models.py           # schreiben
    python tools/generate_npc_models.py --check   # pruefen, ob die Dateien aktuell sind
    python tools/generate_npc_models.py --preview out.png
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
from generate_armor_models import FACES, Atlas, C, Cube, Palette, face_dims, hexc, mirror, paint_face  # noqa: E402

LOG = logging.getLogger("npc_models")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
DENSITY = 6
CHARACTER_MATS = {"skin", "cloth", "hawaii", "hair", "beard", "leather"}


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple[float, float, float]
    cubes: list[Cube] = field(default_factory=list)


@dataclass
class Npc:
    name: str
    pal: Palette
    bones: list[Bone]
    face: str            # Gesichtsart: "sage", "grandpa", "robot"
    skin: str = "E3B98F"

    def bone(self, name: str) -> Bone:
        return next(b for b in self.bones if b.name == name)


def box(cx: float, cy: float, cz: float, sx: float, sy: float, sz: float, mat: str, **kw) -> Cube:
    return C((cx - sx / 2, cy - sy / 2, cz - sz / 2), (sx, sy, sz), mat, **kw)


def octo(cx: float, cy: float, cz: float, size: float, length: float, axis: str, mat: str, **kw) -> list[Cube]:
    side = size * 0.83
    if axis == "z":
        return [box(cx, cy, cz, size, size, length, mat, **kw),
                C((cx - side / 2, cy - side / 2, cz - length / 2), (side, side, length), mat, rot=(0, 0, 45), pivot=(cx, cy, cz), **kw)]
    if axis == "y":
        return [box(cx, cy, cz, size, length, size, mat, **kw),
                C((cx - side / 2, cy - length / 2, cz - side / 2), (side, length, side), mat, rot=(0, 45, 0), pivot=(cx, cy, cz), **kw)]
    return [box(cx, cy, cz, length, size, size, mat, **kw),
            C((cx - length / 2, cy - side / 2, cz - side / 2), (length, side, side), mat, rot=(45, 0, 0), pivot=(cx, cy, cz), **kw)]


def diamond(cx: float, cy: float, cz: float, side: float, depth: float, mat: str, **kw) -> Cube:
    return C((cx - side / 2, cy - side / 2, cz - depth / 2), (side, side, depth), mat, rot=(0, 0, 45), pivot=(cx, cy, cz), seams=False, **kw)


# --- Malerei fuer Figuren -----------------------------------------------------------------------------------------------

def paint_character(npc: Npc, cube: Cube, face: str, w: int, h: int, seed: int) -> tuple[np.ndarray, np.ndarray]:
    """Haut, Stoff, Haar usw.; alles andere uebernimmt der Ruestungs-Maler."""
    if cube.mat not in CHARACTER_MATS and cube.mat != "head":
        return paint_face(npc.pal, cube, face, w, h, seed)
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:h, 0:w]
    shade = {"up": 1.08, "down": 0.72, "north": 1.0, "south": 0.9, "east": 0.94, "west": 0.94}[face]
    glow = np.zeros((h, w), dtype=bool)
    mat = cube.mat
    if mat in ("skin", "head"):
        base = hexc(npc.skin)
        rgb = base * np.linspace(1.08, 0.9, h)[:, None, None] + rng.normal(0, 2.5, (h, w, 1))
        if mat == "head" and face == "north":
            rgb = paint_face_features(npc, rgb, w, h)
        elif mat == "head" and face == "up" and npc.face == "sage":
            rgb = rgb * 1.06  # Glatze glaenzt
            rgb[h // 3:h // 3 + 2, w // 4:3 * w // 4] = np.minimum(rgb[h // 3:h // 3 + 2, w // 4:3 * w // 4] * 1.12, 255)
    elif mat == "cloth":
        base = hexc(cube.tint or npc.pal.suit)
        folds = 0.9 + 0.1 * np.sin(xx * (2 * math.pi / max(6, w / 3)) + seed % 5)
        rgb = base * folds[..., None] * np.linspace(1.06, 0.82, h)[:, None, None] + rng.normal(0, 3.0, (h, w, 1))
        weave = ((xx + yy) % 3 == 0)
        rgb[weave] *= 0.95
    elif mat == "hawaii":
        base = hexc(cube.tint or "C0392B")
        rgb = np.ones((h, w, 3)) * base * np.linspace(1.05, 0.85, h)[:, None, None]
        for _ in range(max(2, (w * h) // 60)):
            cx, cy = int(rng.integers(0, w)), int(rng.integers(0, h))
            col = hexc("FFE47A") if rng.random() < 0.5 else hexc("FFFFFF")
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):
                if 0 <= cx + dx < w and 0 <= cy + dy < h:
                    rgb[cy + dy, cx + dx] = col if (dx, dy) != (0, 0) else hexc("F08A1A")
            for dx, dy in ((2, 2), (-2, -2), (3, 1)):
                if 0 <= cx + dx < w and 0 <= cy + dy < h:
                    rgb[cy + dy, cx + dx] = hexc("2E8B57")
    elif mat in ("hair", "beard"):
        base = hexc(cube.tint or "B8B8B8")
        strands = 0.82 + 0.18 * np.sin(xx * 1.7 + np.sin(yy * 0.6 + seed % 7) * 2.0)
        rgb = base * strands[..., None] + rng.normal(0, 5.0, (h, w, 1))
        if mat == "beard":
            rgb *= np.linspace(1.08, 0.86, h)[:, None, None]
    else:  # leather
        base = hexc(cube.tint or "5A3A22")
        rgb = base * np.linspace(1.12, 0.78, h)[:, None, None] + rng.normal(0, 4.0, (h, w, 1))
        if h >= 3:
            rgb[-1, :] *= 0.6
    rgb = np.clip(rgb * shade, 0, 255)
    color = np.concatenate([rgb, np.full((h, w, 1), 255.0)], axis=2).astype(np.uint8)
    mask = np.zeros((h, w, 4), dtype=np.uint8)
    mask[glow] = color[glow]
    return color, mask


def paint_face_features(npc: Npc, rgb: np.ndarray, w: int, h: int) -> np.ndarray:
    """Gesicht auf der Vorderseite des Kopfes (w x h Pixel, 8 x 8 Einheiten)."""
    u = w / 8.0

    def rect(x0: float, y0: float, x1: float, y1: float, color: str) -> None:
        rgb[int(y0 * u):max(int(y0 * u) + 1, int(y1 * u)), int(x0 * u):max(int(x0 * u) + 1, int(x1 * u))] = hexc(color)

    if npc.face == "sage":
        # tiefliegende dunkle Augen unter buschigen Brauen (die Brauen selbst sind eigene Quader), schmale Nase
        rect(1.6, 3.4, 3.0, 4.3, "F4F4F4")
        rect(5.0, 3.4, 6.4, 4.3, "F4F4F4")
        rect(2.1, 3.5, 2.8, 4.3, "1A1A2A")
        rect(5.2, 3.5, 5.9, 4.3, "1A1A2A")
        rect(1.4, 4.4, 3.2, 4.6, "B08A6A")
        rect(4.8, 4.4, 6.6, 4.6, "B08A6A")
        rect(3.6, 3.6, 4.4, 5.6, "D0A07A")
        rect(3.6, 5.4, 4.4, 5.7, "A87A5A")
    elif npc.face == "grandpa":
        rect(1.7, 3.3, 3.1, 4.2, "FFFFFF")
        rect(4.9, 3.3, 6.3, 4.2, "FFFFFF")
        rect(2.3, 3.4, 3.0, 4.2, "3A2A1A")
        rect(5.0, 3.4, 5.7, 4.2, "3A2A1A")
        rect(1.5, 2.6, 3.3, 3.0, "9A9A9A")
        rect(4.7, 2.6, 6.5, 3.0, "9A9A9A")
        rect(3.5, 3.6, 4.5, 5.2, "D29A78")
        rect(2.4, 6.0, 5.6, 6.4, "8A3A2A")
        rect(2.1, 5.8, 2.5, 6.1, "8A3A2A")
        rect(5.5, 5.8, 5.9, 6.1, "8A3A2A")
        rect(1.2, 1.6, 6.8, 1.8, "C89A78")
    elif npc.face == "vilgax":
        # Vilgax: rote, schraeg stehende Augen unter wulstigen Brauen, kein Mund (die Tentakel sind eigene Quader)
        rect(1.2, 3.2, 3.2, 4.0, "2A0808")
        rect(4.8, 3.2, 6.8, 4.0, "2A0808")
        rect(1.5, 3.4, 3.0, 3.9, "FF2A1A")
        rect(5.0, 3.4, 6.5, 3.9, "FF2A1A")
        rect(1.0, 2.6, 3.4, 3.1, "2E5A22")
        rect(4.6, 2.6, 7.0, 3.1, "2E5A22")
        rect(3.6, 4.2, 4.4, 5.0, "3E7A30")
    elif npc.face == "kevin":
        # Kevin Levin: dunkle Augen unter gesenkten Brauen, schmales schiefes Grinsen
        rect(1.7, 3.6, 3.1, 4.4, "FFFFFF")
        rect(4.9, 3.6, 6.3, 4.4, "FFFFFF")
        rect(2.2, 3.7, 3.0, 4.4, "1A1210")
        rect(5.0, 3.7, 5.8, 4.4, "1A1210")
        rect(1.5, 3.0, 3.3, 3.4, "1A1210")
        rect(4.7, 3.0, 6.5, 3.4, "1A1210")
        rect(3.6, 4.4, 4.4, 5.6, "C89878")
        rect(2.8, 6.2, 5.6, 6.5, "6A3A2A")
        rect(5.4, 6.0, 5.8, 6.3, "6A3A2A")
    return rgb


# --- Figuren ----------------------------------------------------------------------------------------------------------

def humanoid(arm_x: float = 5.0) -> list[Bone]:
    return [Bone("root", None, (0, 0, 0)), Bone("body", "root", (0, 24, 0)), Bone("head", "body", (0, 24, 0)),
            Bone("right_arm", "body", (-arm_x, 22, 0)), Bone("left_arm", "body", (arm_x, 22, 0)),
            Bone("right_leg", "root", (-1.9, 12, 0)), Bone("left_leg", "root", (1.9, 12, 0))]


def mirror_bone(npc: Npc, right: str, left: str) -> None:
    npc.bone(left).cubes = [mirror(c, 0, 0) for c in npc.bone(right).cubes]


def yen_sid() -> Npc:
    """Yen Sid (Kingdom Hearts): kahlkoepfiger Meister mit riesigen weissen Brauen und langem grauem Bart, tiefblaue
    Robe bis zum Boden mit Goldsaum, dunkler Schaerpe und weiten Aermeln, dunkelblaue Unterrobe am Ausschnitt."""
    robe, under, gold, sash = "2A3570", "141A3A", "C8A040", "1A1A2A"
    pal = Palette(robe, "0E1430", "4A5AA0", robe, gold, "6AA0FF", "E0F0FF", "3A6AFF", "6AA0FF")
    npc = Npc("yen_sid", pal, humanoid(5.4), "sage", skin="D9AE8A")
    b = npc.bone("body").cubes
    b += [box(0, 18.0, 0, 9.0, 12.0, 5.0, "cloth", tint=robe), box(0, 22.0, -2.55, 3.0, 4.0, 0.2, "cloth", tint=under),
          box(0, 13.6, 0, 9.4, 1.6, 5.4, "cloth", tint=sash), box(0, 13.6, -2.75, 1.6, 1.8, 0.2, "trim")]
    for side in (-1, 1):
        b.append(box(side * 1.8, 18.0, -2.55, 0.5, 12.0, 0.15, "trim", seams=False))
    # Robe bis zum Boden (am Koerper, damit sie beim Gehen nicht aufreisst)
    b += [box(0, 9.0, 0, 9.6, 6.0, 5.8, "cloth", tint=robe), box(0, 3.4, 0, 10.4, 5.6, 6.6, "cloth", tint=robe),
          box(0, 0.6, 0, 10.8, 1.0, 7.0, "trim", seams=False)]
    for side in (-1, 1):
        b.append(box(side * 1.8, 6.5, -3.35, 0.5, 11.0, 0.15, "trim", seams=False))
    npc.bone("right_leg").cubes = [box(-1.9, 0.6, -0.6, 3.0, 1.2, 4.0, "leather", tint="1A1A22")]
    npc.bone("left_leg").cubes = [box(1.9, 0.6, -0.6, 3.0, 1.2, 4.0, "leather", tint="1A1A22")]
    npc.bone("right_arm").cubes = [box(-6.6, 18.0, 0, 4.2, 9.0, 4.4, "cloth", tint=robe), box(-6.8, 12.4, 0, 5.4, 3.0, 5.6, "cloth", tint=robe),
                                   box(-6.8, 11.1, 0, 5.6, 0.6, 5.8, "trim", seams=False), box(-6.6, 10.2, 0, 3.0, 2.2, 3.0, "skin")]
    mirror_bone(npc, "right_arm", "left_arm")
    h = npc.bone("head").cubes
    h += [box(0, 28.0, 0, 8.0, 8.0, 8.0, "head"), box(-4.2, 27.6, 0.4, 0.6, 1.8, 1.4, "skin"), box(4.2, 27.6, 0.4, 0.6, 1.8, 1.4, "skin")]
    # buschige, nach oben geschwungene Brauen
    for side in (-1, 1):
        h += [C((side * 2.2 - 1.6, 28.8, -4.5), (3.2, 0.9, 0.8), "beard", tint="F0F0F0", rot=(0, 0, side * -14),
                pivot=(side * 2.2, 29.2, -4.1), seams=False),
              C((side * 4.0 - 0.6, 28.9, -4.4), (1.2, 1.6, 0.7), "beard", tint="F0F0F0", rot=(0, 0, side * -40),
                pivot=(side * 4.0, 29.2, -4.0), seams=False)]
    # Schnurrbart und langer Bart bis zur Brust
    h += [box(0, 25.4, -4.3, 5.4, 1.2, 0.8, "beard", tint="D8D8D8"), box(0, 24.0, -4.4, 6.6, 3.0, 1.0, "beard", tint="D0D0D0"),
          box(0, 20.6, -3.4, 5.2, 4.4, 1.6, "beard", tint="C8C8C8"), box(0, 17.6, -3.2, 3.4, 2.4, 1.4, "beard", tint="C0C0C0"),
          box(0, 15.8, -3.0, 1.6, 1.6, 1.2, "beard", tint="B8B8B8")]
    for side in (-1, 1):
        h.append(box(side * 3.6, 25.6, -2.0, 1.0, 3.6, 4.0, "beard", tint="D0D0D0"))
    return npc


def max_tennyson() -> Npc:
    """Max Tennyson (Ben 10): kraeftiger Opa mit Bauch, rotes Hawaiihemd mit Blumen und Kragen, Khaki-Hose,
    braune Schuhe, kurze graue Haare, grosse Nase, freundliches Laecheln."""
    shirt, khaki = "C0392B", "9A8A62"
    pal = Palette(shirt, "6A1A12", "E06A5A", khaki, "D8D0C0", "FFE47A", "FFFFFF", "FFE47A", "FFE47A")
    npc = Npc("max", pal, humanoid(5.6), "grandpa", skin="E0B08A")
    b = npc.bone("body").cubes
    b += [box(0, 18.0, 0, 9.4, 12.0, 5.4, "hawaii", tint=shirt), box(0, 16.0, -3.2, 7.4, 6.0, 1.4, "hawaii", tint=shirt),
          box(0, 23.4, -2.6, 4.0, 1.0, 0.6, "cloth", tint="E8E0D0"), box(0, 12.4, 0, 9.0, 0.8, 5.2, "leather", tint="4A3020"),
          box(0, 12.4, -2.7, 1.4, 1.0, 0.2, "trim"), diamond(-2.6, 12.4, -2.75, 1.2, 0.2, "glow"),
          box(-2.6, 12.4, -2.8, 0.4, 0.8, 0.1, "suit")]  # Klempner-Abzeichen am Guertel
    for side in (-1, 1):
        b.append(C((side * 1.4 - 0.9, 22.2, -2.95), (1.8, 2.0, 0.4), "hawaii", tint="A82E22", rot=(0, 0, side * 25),
                   pivot=(side * 1.4, 24.0, -2.8)))
    npc.bone("right_leg").cubes = [box(-2.1, 7.0, 0, 4.4, 10.0, 4.4, "cloth", tint=khaki), box(-2.1, 1.2, -0.5, 4.6, 2.4, 5.4, "leather")]
    npc.bone("left_leg").cubes = [box(2.1, 7.0, 0, 4.4, 10.0, 4.4, "cloth", tint=khaki), box(2.1, 1.2, -0.5, 4.6, 2.4, 5.4, "leather")]
    npc.bone("right_arm").cubes = [box(-7.2, 20.5, 0, 4.4, 5.0, 4.6, "hawaii", tint=shirt), box(-7.0, 15.0, 0, 3.8, 7.0, 3.8, "skin"),
                                   box(-7.0, 10.8, 0, 3.6, 1.8, 3.6, "skin")]
    mirror_bone(npc, "right_arm", "left_arm")
    h = npc.bone("head").cubes
    h += [box(0, 28.0, 0, 8.4, 8.0, 8.4, "head"), box(0, 32.3, 0.6, 8.6, 1.0, 7.6, "hair", tint="C8C8C8"),
          box(0, 30.0, 4.1, 8.6, 5.0, 0.8, "hair", tint="B8B8B8"), box(-4.25, 30.4, 1.0, 0.6, 3.6, 6.0, "hair", tint="B8B8B8"),
          box(4.25, 30.4, 1.0, 0.6, 3.6, 6.0, "hair", tint="B8B8B8"), box(0, 27.2, -4.5, 1.2, 1.6, 0.8, "skin"),
          box(-4.4, 27.8, 0.4, 0.6, 1.8, 1.4, "skin"), box(4.4, 27.8, 0.4, 0.6, 1.8, 1.4, "skin")]
    return npc


def clank() -> Npc:
    """Clank (Ratchet & Clank): kleiner Roboter mit grossem, abgerundetem Kopf, gruen leuchtenden Augen, Mundgitter,
    Ohrscheiben, Antenne mit roter Leuchtkugel, gruenem Brustlicht, duennen dunklen Gliedern und runden Haenden."""
    pal = Palette("B8C0C8", "4A5058", "E8F0F4", "2A3038", "5A626A", "3AFF6A", "E8FFE8", "FF3A2A", "3AFF6A")
    npc = Npc("clank", pal, [Bone("root", None, (0, 0, 0)), Bone("body", "root", (0, 5, 0)), Bone("head", "body", (0, 11, 0)),
                             Bone("antenna", "head", (0, 17.5, 0)), Bone("right_arm", "body", (-3.4, 10, 0)),
                             Bone("left_arm", "body", (3.4, 10, 0)), Bone("right_leg", "root", (-1.4, 5, 0)),
                             Bone("left_leg", "root", (1.4, 5, 0))], "robot")
    npc.bone("body").cubes += octo(0, 8.0, 0, 5.2, 6.0, "y", "plate") + [box(0, 8.4, -2.45, 2.6, 2.6, 0.3, "trim"),
                                                                      diamond(0, 8.4, -2.65, 1.6, 0.3, "glow"),
                                                                      box(0, 5.2, 0, 4.0, 0.8, 3.0, "trim")]
    h = npc.bone("head").cubes
    h += octo(0, 14.2, 0.2, 7.0, 9.0, "x", "plate") + [box(0, 14.2, 0.2, 9.4, 5.8, 6.4, "plate", seams=False)]
    h += [box(0, 14.6, -3.1, 7.6, 3.4, 0.3, "suit"), box(0, 12.0, -3.05, 3.0, 0.9, 0.3, "trim"), box(0, 11.0, 0.2, 3.0, 0.8, 3.0, "trim")]
    for side in (-1, 1):
        h += octo(side * 1.9, 14.7, -3.35, 2.2, 0.3, "z", "glow") + octo(side * 1.9, 14.7, -3.45, 1.0, 0.2, "z", "visor")
    for side in (-1, 1):
        h += octo(side * 4.9, 14.2, 0.2, 2.6, 0.6, "x", "trim") + [box(side * 5.25, 14.2, 0.2, 0.15, 1.2, 1.2, "glow")]
    npc.bone("antenna").cubes = [box(0, 19.2, 0.2, 0.5, 3.4, 0.5, "suit"), box(0, 21.4, 0.2, 1.4, 1.4, 1.4, "gem"),
                                 diamond(0, 21.4, 0.2, 1.2, 1.5, "gem")]
    npc.bone("right_arm").cubes = [box(-3.6, 7.8, 0, 0.9, 4.4, 0.9, "suit"), box(-3.6, 5.2, 0, 1.6, 1.4, 1.6, "trim")]
    mirror_bone(npc, "right_arm", "left_arm")
    npc.bone("right_leg").cubes = [box(-1.4, 2.8, 0, 1.0, 4.0, 1.0, "suit"), box(-1.4, 0.5, -0.4, 2.0, 1.0, 2.6, "plate")]
    mirror_bone(npc, "right_leg", "left_leg")
    return npc


NPCS = {"yen_sid": yen_sid, "max": max_tennyson, "clank": clank}


# --- Ausgabe ----------------------------------------------------------------------------------------------------------

def build(npc: Npc) -> tuple[dict, Image.Image, Image.Image]:
    atlas = Atlas(512)
    placed = []
    for bi, bone in enumerate(npc.bones):
        for ci, cube in enumerate(bone.cubes):
            uv = {}
            for face in FACES:
                fw, fh = face_dims(cube.size, face)
                pw = max(1, int(round(fw * DENSITY)))
                ph = max(1, int(round(fh * DENSITY)))
                x, y = atlas.place(pw, ph)
                uv[face] = (x, y, pw, ph)
                placed.append((bi, ci, face, x, y, pw, ph))
            cube.uv = uv  # type: ignore[attr-defined]
    tw, th = atlas.width, atlas.height
    color = np.zeros((th, tw, 4), dtype=np.uint8)
    mask = np.zeros((th, tw, 4), dtype=np.uint8)
    seed_base = sum(ord(c) for c in npc.name) * 7919
    for bi, ci, face, x, y, pw, ph in placed:
        cube = npc.bones[bi].cubes[ci]
        c, m = paint_character(npc, cube, face, pw, ph, seed_base + zlib.crc32(f"{bi}/{ci}/{face}".encode()) % 100000)
        color[y:y + ph, x:x + pw] = c
        mask[y:y + ph, x:x + pw] = m
    bones = []
    for bone in npc.bones:
        entries = []
        for cube in bone.cubes:
            entry = {"origin": [round(v, 4) for v in cube.origin], "size": [round(v, 4) for v in cube.size],
                     "uv": {f: {"uv": [cube.uv[f][0], cube.uv[f][1]], "uv_size": [cube.uv[f][2], cube.uv[f][3]]} for f in FACES}}
            if cube.rotation:
                entry["rotation"] = [round(v, 4) for v in cube.rotation]
                p = cube.pivot or [cube.origin[0] + cube.size[0] / 2, cube.origin[1], cube.origin[2] + cube.size[2] / 2]
                entry["pivot"] = [round(v, 4) for v in p]
            entries.append(entry)
        out = {"name": bone.name, "pivot": list(bone.pivot), "cubes": entries}
        if bone.parent:
            out["parent"] = bone.parent
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.kingdomomnitrix.npc_{npc.name}", "texture_width": tw, "texture_height": th,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    return geo, Image.fromarray(color, "RGBA"), Image.fromarray(mask, "RGBA")


def track(*keys: tuple[float, tuple[float, float, float]]) -> dict:
    return {f"{t:.2f}": list(v) for t, v in keys}


def build_animations(npc: Npc) -> dict:
    names = {b.name for b in npc.bones}
    idle = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.5, (1.5, 0, 0)), (3.0, (0, 0, 0)))},
            "head": {"rotation": track((0.0, (0, 0, 0)), (1.5, (0, 6, 0)), (3.0, (0, 0, 0)))},
            "right_arm": {"rotation": track((0.0, (0, 0, 2)), (1.5, (0, 0, 5)), (3.0, (0, 0, 2)))},
            "left_arm": {"rotation": track((0.0, (0, 0, -2)), (1.5, (0, 0, -5)), (3.0, (0, 0, -2)))}}
    if "antenna" in names:
        idle["antenna"] = {"rotation": track((0.0, (0, 0, -8)), (1.5, (0, 0, 8)), (3.0, (0, 0, -8)))}
    walk: dict = {"body": {"rotation": track((0.0, (0, 2, 0)), (0.5, (0, -2, 0)), (1.0, (0, 2, 0)))}}
    for side, sign in (("right", 1), ("left", -1)):
        walk[f"{side}_arm"] = {"rotation": track((0.0, (25 * sign, 0, 0)), (0.5, (-25 * sign, 0, 0)), (1.0, (25 * sign, 0, 0)))}
        walk[f"{side}_leg"] = {"rotation": track((0.0, (-25 * sign, 0, 0)), (0.5, (25 * sign, 0, 0)), (1.0, (-25 * sign, 0, 0)))}
    talk = {
        "head": {"rotation": track((0.0, (0, 0, 0)), (0.3, (8, 0, 0)), (0.6, (-3, 0, 0)), (0.9, (6, 0, 0)), (1.5, (0, 0, 0)))},
        "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-50, 0, 15)), (1.0, (-40, 0, 25)), (1.5, (0, 0, 0)))},
    }
    wave = {"right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.25, (0, 0, 150)), (0.5, (0, 0, 120)),
                                             (0.75, (0, 0, 150)), (1.0, (0, 0, 120)), (1.3, (0, 0, 0)))}}

    def anim(length: float, bones: dict, loop: bool) -> dict:
        return {"loop": loop, "animation_length": length, "bones": {k: v for k, v in bones.items() if k in names}}

    return {"format_version": "1.8.0", "animations": {
        "idle": anim(3.0, idle, True),
        "walk": anim(1.0, walk, True),
        "talk": anim(1.5, talk, False),
        "wave": anim(1.3, wave, False),
    }}


def png(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def outputs() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    base = Path("entity") / "npc"
    for name, make in NPCS.items():
        npc = make()
        geo, color, mask = build(npc)
        files[ASSETS / "geo" / base / f"{name}.geo.json"] = (json.dumps(geo, indent=1) + "\n").encode()
        files[ASSETS / "animations" / base / f"{name}.animation.json"] = (json.dumps(build_animations(npc), indent=2) + "\n").encode()
        files[ASSETS / "textures" / base / f"{name}.png"] = png(color)
        # nur mit Leuchtpixeln: GeckoLib bricht bei einer leeren Leuchtmaske ab (SafeGlowLayer ueberspringt fehlende)
        if np.asarray(mask)[..., 3].any():
            files[ASSETS / "textures" / base / f"{name}_glowmask.png"] = png(mask)
    return files


def preview(out: Path) -> None:
    base = ASSETS / "{}" / "entity" / "npc"
    specs = [f"{str(base).format('geo')}/{n}.geo.json:{str(base).format('textures')}/{n}.png" for n in NPCS]
    labels = sum((["--label", n] for n in NPCS), [])
    subprocess.run([sys.executable, str(ROOT / "tools" / "render_geo.py"), str(out), *specs, "--views", "front,three_quarter,side",
                    "--scale", "10", "--background", "#2A3038", *labels], check=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    parser.add_argument("--preview", type=Path, help="Vorschaubild schreiben")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    files = outputs()
    if args.check:
        stale = [p for p, data in files.items() if not p.is_file() or p.read_bytes() != data]
        for path in stale:
            LOG.error("veraltet oder fehlt: %s", path.relative_to(ASSETS))
        LOG.info("%d/%d NPC-Dateien aktuell", len(files) - len(stale), len(files))
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    LOG.info("%d NPC-Dateien geschrieben", len(files))
    if args.preview:
        preview(args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
