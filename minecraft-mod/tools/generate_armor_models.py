#!/usr/bin/env python3
"""Erzeugt die 3D-Endgame-Ruestungen (GeckoLib-Armor) — Modell, Textur und Leuchtmaske je Set.

Zwei Linien mit je drei Stufen:
    Ben 10:            Taydenit -> Galvan-Biotech -> Celestialsapien
    Ratchet & Clank:   Magnaplate -> Carbonox -> Infernox

Jedes Set ist aus Quadern aufgebaut (Helm, Brustpanzer, Schulterstuecke, Arm-, Bein- und Stiefelplatten, Finnen,
Hoerner, Kern). Jede Quaderflaeche bekommt ein eigenes Feld in der Textur (2 Pixel je Modelleinheit) und wird nach
Material bemalt: Platte mit Fase und Fugen, Unteranzug, Zierleiste, Leuchtlinie, Kern-Edelstein, Visier, Sternenhimmel.
Leuchtende Pixel landen zusaetzlich in der _glowmask (GeckoLib AutoGlowingGeoLayer), damit sie im Dunkeln strahlen.

    python tools/generate_armor_models.py            # schreibt alle Sets
    python tools/generate_armor_models.py --check    # prueft, ob die Dateien aktuell sind
    python tools/generate_armor_models.py --preview out.png   # Vorschau aller Sets (tools/render_geo.py)
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

LOG = logging.getLogger("generate_armor_models")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
GEO_DIR = ASSETS / "geo" / "armor"
TEX_DIR = ASSETS / "textures" / "armor"
DENSITY = 4  # Texturpixel je Modelleinheit (doppelt so fein wie Vanilla-Ruestung)

# GeckoLib-Armor-Bones mit den Drehpunkten des Spielermodells
BONES = {
    "armorHead": [0, 24, 0], "armorBody": [0, 24, 0],
    "armorRightArm": [-5, 22, 0], "armorLeftArm": [5, 22, 0],
    "armorRightLeg": [-1.9, 12, 0], "armorLeftLeg": [1.9, 12, 0],
    "armorRightBoot": [-1.9, 12, 0], "armorLeftBoot": [1.9, 12, 0],
}
MIRROR = {"armorRightArm": "armorLeftArm", "armorRightLeg": "armorLeftLeg", "armorRightBoot": "armorLeftBoot"}
FACES = ("north", "south", "east", "west", "up", "down")


def hexc(value: str) -> np.ndarray:
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


@dataclass
class Palette:
    plate: str        # Hauptplatte (mittlerer Ton)
    plate_dark: str   # Schatten/Fugen
    plate_light: str  # Fase/Glanzkante
    suit: str         # Unteranzug
    trim: str         # Zierleiste (Metall)
    glow: str         # Leuchtfarbe
    glow_core: str    # heller Kern der Leuchtlinie
    gem: str          # Kern-Edelstein
    visor: str        # Visier
    cosmic: bool = False  # Sternenhimmel statt Platte (Celestialsapien)


@dataclass
class Cube:
    origin: list[float]
    size: list[float]
    mat: str = "plate"                        # plate, suit, trim, glow, gem, visor
    lines: list[tuple] = field(default_factory=list)   # Leuchtlinien: ("h"|"v", Anteil 0..1, Flaechen)
    edge: tuple[str, ...] = ()                # Flaechen mit leuchtendem Rand
    rotation: list[float] | None = None
    pivot: list[float] | None = None
    seams: bool = True
    tint: str | None = None                   # eigene Grundfarbe statt der Palette (z. B. weisse Uniformhaelfte)
    cracks: bool = False                      # gluehende Risse ueber dem Material (Glut, Energie)


# --- Textur-Malerei -------------------------------------------------------------------------------------------------

class Atlas:
    """Regal-Packer: legt Flaechen zeilenweise in die Textur, vergroessert sie bei Bedarf."""

    def __init__(self, width: int = 256):
        self.width = width
        self.x = 0
        self.y = 0
        self.row = 0
        self.rects: list[tuple[int, int, int, int]] = []

    def place(self, w: int, h: int) -> tuple[int, int]:
        if self.x + w > self.width:
            self.x = 0
            self.y += self.row
            self.row = 0
        pos = (self.x, self.y)
        self.x += w
        self.row = max(self.row, h)
        return pos

    @property
    def height(self) -> int:
        height = self.y + self.row
        return max(16, 1 << (height - 1).bit_length())


def paint_face(pal: Palette, cube: Cube, face: str, w: int, h: int, seed: int) -> tuple[np.ndarray, np.ndarray]:
    """Bemalt eine Flaeche (w x h Pixel). Rueckgabe: RGBA-Farbe und Leuchtmaske (RGBA, Alpha = leuchtet)."""
    rng = np.random.default_rng(seed)
    rgb = np.zeros((h, w, 3))
    glow = np.zeros((h, w), dtype=bool)
    shade = {"up": 1.08, "down": 0.75, "north": 1.0, "south": 0.92, "east": 0.95, "west": 0.95}[face]
    mat = cube.mat
    if mat == "plate" and pal.cosmic:
        mat = "cosmic"

    if mat == "gloss":
        # Glanzlack (Kuppelhelme): kraeftiger Verlauf, heller Glanzstreifen oben, dunkler Rand unten
        base = hexc(cube.tint) if cube.tint else hexc(pal.plate)
        grad = np.linspace(1.35, 0.6, h)[:, None]
        rgb[:] = base * grad[..., None] + rng.normal(0, 2.0, (h, w, 1))
        if h >= 6:
            band = max(1, h // 6)
            rgb[band:band * 2, 1:-1] = rgb[band:band * 2, 1:-1] * 0.4 + 255 * 0.6
        if w >= 3:
            rgb[:, 0] *= 0.8
            rgb[:, -1] *= 0.8
    elif mat == "omni":
        # Omnitrix-Symbol: gruener Kreis, schwarze Sanduhr, heller Ring
        yy, xx = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = np.hypot((xx - cx) / max(1, w / 2), (yy - cy) / max(1, h / 2))
        rgb[:] = hexc("101010")
        rgb[r < 0.95] = hexc("3AFF5A")
        rgb[r < 0.8] = hexc("6AFF7A")
        hour = (np.abs(xx - cx) / max(1, w / 2) <= np.abs(yy - cy) / max(1, h / 2) * 0.95) & (r < 0.85)
        rgb[hour] = hexc("0A0A0A")
        glow |= (r < 0.95) & ~hour
    elif mat in ("plate", "trim", "suit"):
        base = {"plate": hexc(pal.plate), "trim": hexc(pal.trim), "suit": hexc(pal.suit)}[mat]
        if cube.tint:
            base = hexc(cube.tint)
        noise = rng.normal(0, 4.0 if mat != "suit" else 6.0, (h, w))
        rgb[:] = base
        rgb += noise[..., None]
        if mat == "trim":
            # Metall: senkrechter Glanzverlauf
            grad = np.linspace(1.18, 0.82, h)[:, None, None]
            rgb *= grad
        if mat == "suit":
            # Gewebe: feines Rautenmuster
            yy, xx = np.mgrid[0:h, 0:w]
            rgb[((xx + yy) % 4 == 0) | ((xx - yy) % 4 == 0)] *= 0.86
        if mat == "plate":
            light = base * 1.35 if cube.tint else hexc(pal.plate_light)
            dark = base * 0.45 if cube.tint else hexc(pal.plate_dark)
            # Licht von oben: Verlauf ueber die Flaeche, doppelte Fase (2 px), Glanzkante oben
            rgb *= np.linspace(1.12, 0.82, h)[:, None, None]
            if w >= 6 and h >= 6:
                rgb[0, :] = np.minimum(light * 1.15, 255)
                rgb[1, 1:-1] = (rgb[1, 1:-1] + light) / 2
                rgb[:, 0] = light * 0.96
                rgb[1:-1, 1] = (rgb[1:-1, 1] + light * 0.9) / 2
                rgb[-1, :] = dark
                rgb[-2, 1:-1] = (rgb[-2, 1:-1] + dark) / 2
                rgb[:, -1] = dark * 1.05
                rgb[1:-1, -2] = (rgb[1:-1, -2] + dark) / 2
            elif w >= 3 and h >= 3:
                rgb[0, :] = light
                rgb[-1, :] = dark
            if w >= 14 and h >= 12:
                # eingelassenes Innenpaneel: dunkle Fuge 3 px innen, helle Kante darunter (Platte in der Platte)
                y0, y1, x0, x1 = 3, h - 4, 3, w - 4
                rgb[y0, x0:x1 + 1] = dark
                rgb[y0:y1 + 1, x0] = dark
                rgb[y1, x0:x1 + 1] = light
                rgb[y0:y1 + 1, x1] = light * 0.95
                rgb[y0 + 1:y1, x0 + 1:x1] *= 0.92
            if cube.seams and w >= 10 and h >= 10:
                # Plattenfugen: eine waagerechte und eine senkrechte Fuge, versetzt je Flaeche
                sy = int(h * (0.35 + 0.3 * rng.random()))
                sx = int(w * (0.3 + 0.4 * rng.random()))
                rgb[sy, 1:-1] = dark
                rgb[sy + 1, 1:-1] = (light + hexc(pal.plate)) / 2
                rgb[1:sy, sx] = dark
                rgb[1:sy, sx + 1] = (light + hexc(pal.plate)) / 2
                # Nieten an den Ecken grosser Flaechen
                if w >= 14 and h >= 14:
                    for ry, rx in ((2, 2), (2, w - 3), (h - 3, 2), (h - 3, w - 3)):
                        rgb[ry, rx] = light * 1.1
                        rgb[ry + 1, rx] = dark
    elif mat == "cosmic":
        # Sternenhimmel: tiefes Violett-Schwarz, Nebelschleier, einzelne helle Sterne (leuchten)
        yy, xx = np.mgrid[0:h, 0:w]
        neb = (np.sin(xx * 0.45 + seed % 7) + np.cos(yy * 0.38 + seed % 5) + rng.normal(0, 0.5, (h, w))) * 0.5
        rgb[:] = hexc("0A0618") + neb[..., None] * hexc("2A1050") * 0.6
        stars = rng.random((h, w)) < 0.05
        rgb[stars] = hexc("FFFFFF") * (0.7 + 0.3 * rng.random((stars.sum(), 1)))
        glow |= stars
        big = rng.random((h, w)) < 0.008
        for y, x in zip(*np.nonzero(big)):
            for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                if 0 <= y + dy < h and 0 <= x + dx < w:
                    rgb[y + dy, x + dx] = hexc(pal.glow)
                    glow[y + dy, x + dx] = True
        if w >= 4 and h >= 4:
            rgb[0, :] = hexc(pal.plate_light)
            rgb[-1, :] = hexc(pal.plate_light) * 0.7
            rgb[:, 0] = hexc(pal.plate_light) * 0.85
            rgb[:, -1] = hexc(pal.plate_light) * 0.7
            glow[0, :] = glow[:, 0] = True
    elif mat == "glow":
        yy, xx = np.mgrid[0:h, 0:w]
        dist = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
        t = np.clip(dist / max(1.0, min(w, h) / 2 - 0.5), 0, 1)[..., None]
        rgb[:] = hexc(pal.glow) * (1 - t) + hexc(pal.glow_core) * t
        glow[:] = True
    elif mat == "gem":
        yy, xx = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        d = (np.abs(xx - cx) / max(1, w / 2) + np.abs(yy - cy) / max(1, h / 2))
        facet = ((xx < cx) ^ (yy < cy)).astype(float)[..., None]
        rgb[:] = hexc(pal.gem) * (1.25 - 0.6 * np.clip(d, 0, 1)[..., None]) * (0.85 + 0.25 * facet)
        rgb[d < 0.35] = hexc(pal.glow_core)
        rgb[0, :] = rgb[-1, :] = hexc(pal.trim)
        rgb[:, 0] = rgb[:, -1] = hexc(pal.trim)
        glow[1:-1, 1:-1] = True
    elif mat == "visor":
        grad = np.linspace(1.3, 0.55, h)[:, None, None]
        rgb[:] = hexc(pal.visor) * grad
        if h >= 2:
            rgb[max(0, h // 3), 1:-1] = hexc(pal.glow_core)
        glow[:] = True

    if mat == "trim" and w >= 12 and h >= 10:
        # Gravur: geschwungene Ranke ueber Zierleisten (wie Prunkruestungen)
        xx = np.arange(w)
        for row, phase in ((h // 3, 0.0), (2 * h // 3, math.pi)):
            ys = (row + np.sin(xx / max(2.0, w / 6) + phase) * max(1.0, h / 8)).astype(int)
            for x, y in zip(xx, ys):
                if 1 <= y < h - 1:
                    rgb[y, x] = rgb[y, x] * 0.55
                    rgb[y - 1, x] = np.minimum(rgb[y - 1, x] * 1.25, 255)
    if cube.cracks and w >= 4 and h >= 4:
        # gluehende Risse: verzweigte Zufallspfade von den Raendern nach innen
        for _ in range(max(1, (w * h) // 90)):
            y, x = int(rng.integers(0, h)), int(rng.integers(0, w))
            for _ in range(int(rng.integers(w // 3 + 2, w + h))):
                rgb[y, x] = hexc(pal.glow_core) if rng.random() < 0.35 else hexc(pal.glow)
                glow[y, x] = True
                y = int(np.clip(y + rng.integers(-1, 2), 0, h - 1))
                x = int(np.clip(x + rng.integers(-1, 2), 0, w - 1))

    # Leuchtlinien und leuchtende Raender
    if face in cube.edge and w >= 3 and h >= 3:
        rgb[0, :] = rgb[-1, :] = hexc(pal.glow)
        rgb[:, 0] = rgb[:, -1] = hexc(pal.glow)
        glow[0, :] = glow[-1, :] = glow[:, 0] = glow[:, -1] = True
    for direction, frac, faces in cube.lines:
        if face not in faces:
            continue
        if direction == "h":
            y = min(h - 1, max(0, int(round(frac * (h - 1)))))
            rgb[y, :] = hexc(pal.glow_core)
            glow[y, :] = True
            for yy in (y - 1, y + 1):
                if 0 <= yy < h:
                    rgb[yy, :] = rgb[yy, :] * 0.4 + hexc(pal.glow) * 0.6
                    glow[yy, :] = True
        else:
            x = min(w - 1, max(0, int(round(frac * (w - 1)))))
            rgb[:, x] = hexc(pal.glow_core)
            glow[:, x] = True
            for xx in (x - 1, x + 1):
                if 0 <= xx < w:
                    rgb[:, xx] = rgb[:, xx] * 0.4 + hexc(pal.glow) * 0.6
                    glow[:, xx] = True

    lit = rgb.copy()
    lit[~glow] *= shade
    color = np.concatenate([np.clip(lit, 0, 255), np.full((h, w, 1), 255.0)], axis=2).astype(np.uint8)
    mask = np.zeros((h, w, 4), dtype=np.uint8)
    mask[glow] = color[glow]
    return color, mask


def face_dims(size: list[float], face: str) -> tuple[float, float]:
    w, h, d = size
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}[face]


LAYERED = ("plate", "gloss")
OUTER_X = {"armorRightArm": -1, "armorLeftArm": 1}   # Aussenseite der Arme (fuer das Seitenpaneel)


def layer_pass(bones: dict[str, list[Cube]]) -> None:
    """Gibt jeder groesseren Platte Tiefe wie echter Plattenpanzer: abgesetzte Randleiste oben und unten, ein
    erhabenes Paneel auf der Vorderseite und bei Armen ein Paneel auf der Aussenseite. Gedrehte Teile, Leuchtteile,
    Stoff und Edelsteine bleiben, wie sie sind."""
    for bone, cubes in bones.items():
        extra: list[Cube] = []
        for cube in cubes:
            if cube.mat not in LAYERED or cube.rotation or not getattr(cube, "layer", True):
                continue
            (x, y, z), (w, h, d) = cube.origin, cube.size
            if min(w, h) < 2.5 or d < 1.5:
                if d < 1.5 and w >= 3 and h >= 3:
                    extra.append(Cube([x + 0.5, y + 0.5, z - 0.25], [w - 1.0, h - 1.0, 0.3], cube.mat, [], (), None, None, False, cube.tint))
                continue
            rim = Cube([x - 0.2, y + h - 0.55, z - 0.2], [w + 0.4, 0.55, d + 0.4], "trim", [], (), None, None, False, None)
            base_rim = Cube([x - 0.15, y, z - 0.15], [w + 0.3, 0.45, d + 0.3], "trim", [], (), None, None, False, None)
            panel = Cube([x + 0.6, y + h * 0.18, z - 0.3], [w - 1.2, h * 0.5, 0.35], cube.mat, [], (), None, None, False, cube.tint)
            for c in (rim, base_rim):
                c.layer = False  # type: ignore[attr-defined]
            extra += [rim, base_rim, panel]
            side = OUTER_X.get(bone)
            if side and h >= 3:
                px = x - 0.3 if side < 0 else x + w - 0.05
                extra.append(Cube([px, y + h * 0.2, z + 0.6], [0.35, h * 0.55, d - 1.2], cube.mat, [], (), None, None, False, cube.tint))
        cubes.extend(extra)


def build(name: str, pal: Palette, bones: dict[str, list[Cube]], subbones: dict[str, tuple[str, list[float]]]
          ) -> tuple[dict, Image.Image, Image.Image]:
    layer_pass(bones)
    atlas = Atlas(512)
    placed = []
    for bone, cubes in bones.items():
        for index, cube in enumerate(cubes):
            uv = {}
            for face in FACES:
                fw, fh = face_dims(cube.size, face)
                pw = max(1, int(round(fw * DENSITY)))
                ph = max(1, int(round(fh * DENSITY)))
                x, y = atlas.place(pw, ph)
                uv[face] = (x, y, pw, ph)
                placed.append((bone, index, face, x, y, pw, ph))
            cube.uv = uv  # type: ignore[attr-defined]
    tw, th = atlas.width, atlas.height
    color = np.zeros((th, tw, 4), dtype=np.uint8)
    mask = np.zeros((th, tw, 4), dtype=np.uint8)
    seed_base = sum(ord(c) for c in name) * 7919
    for bone, index, face, x, y, pw, ph in placed:
        cube = bones[bone][index]
        c, m = paint_face(pal, cube, face, pw, ph, seed_base + zlib.crc32(f"{bone}/{index}/{face}".encode()) % 100000)
        color[y:y + ph, x:x + pw] = c
        mask[y:y + ph, x:x + pw] = m

    geo_bones = []
    all_bones = [(bone, None, pivot) for bone, pivot in BONES.items()]
    all_bones += [(bone, parent, pivot) for bone, (parent, pivot) in subbones.items()]
    for bone, parent, pivot in all_bones:
        cubes = []
        for cube in bones.get(bone, []):
            entry = {"origin": cube.origin, "size": cube.size,
                     "uv": {f: {"uv": [cube.uv[f][0], cube.uv[f][1]], "uv_size": [cube.uv[f][2], cube.uv[f][3]]}
                            for f in FACES}}
            if cube.rotation:
                entry["rotation"] = cube.rotation
                entry["pivot"] = cube.pivot or [cube.origin[0] + cube.size[0] / 2, cube.origin[1], cube.origin[2] + cube.size[2] / 2]
            cubes.append(entry)
        entry_bone = {"name": bone, "pivot": pivot, "cubes": cubes}
        if parent:
            entry_bone["parent"] = parent
        geo_bones.append(entry_bone)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.kingdomomnitrix.armor.{name}", "texture_width": tw, "texture_height": th,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": geo_bones}]}
    return geo, Image.fromarray(color, "RGBA"), Image.fromarray(mask, "RGBA")


# --- Bausteine -------------------------------------------------------------------------------------------------------

def mirror(cube: Cube, pivot_x_from: float, pivot_x_to: float) -> Cube:
    """Spiegelt einen Quader der rechten Koerperseite auf die linke (an x = 0)."""
    x0 = -(cube.origin[0] + cube.size[0])
    rot = None
    piv = None
    if cube.rotation:
        rot = [cube.rotation[0], -cube.rotation[1], -cube.rotation[2]]
        p = cube.pivot or [cube.origin[0] + cube.size[0] / 2, cube.origin[1], cube.origin[2] + cube.size[2] / 2]
        piv = [-p[0], p[1], p[2]]
    lines = []
    for direction, frac, faces in cube.lines:
        swapped = tuple({"east": "west", "west": "east"}.get(f, f) for f in faces)
        lines.append((direction, 1 - frac if direction == "v" and any(f in ("north", "south", "up", "down") for f in faces) else frac,
                      swapped))
    edge = tuple({"east": "west", "west": "east"}.get(f, f) for f in cube.edge)
    return Cube([x0, cube.origin[1], cube.origin[2]], list(cube.size), cube.mat, lines, edge, rot, piv, cube.seams, cube.tint,
                cube.cracks)


def C(o, s, mat="plate", lines=(), edge=(), rot=None, pivot=None, seams=True, tint=None, cracks=False) -> Cube:
    """Quader. 'rot' wird so angegeben: x negativ = Oberkante kippt nach hinten, z positiv = Oberkante kippt nach -x
    (auf der rechten Koerperseite also nach aussen); Bedrock speichert z mit umgekehrtem Vorzeichen."""
    rotation = [rot[0], rot[1], -rot[2]] if rot else None
    return Cube(list(o), list(s), mat, list(lines), tuple(edge), rotation, list(pivot) if pivot else None, seams, tint, cracks)


def pair(cube: Cube) -> list[Cube]:
    """Quader und sein Spiegelbild (an x = 0) — fuer symmetrische Teile an Helm und Rumpf."""
    return [cube, mirror(cube, 0, 0)]


FRONT = ("north",)
SIDES = ("east", "west")
ALL4 = ("north", "south", "east", "west")


def symmetric(bones: dict[str, list[Cube]]) -> dict[str, list[Cube]]:
    for right, left in MIRROR.items():
        bones[left] = [mirror(c, 0, 0) for c in bones.get(right, [])]
    return bones


def shards(base_x: float, base_y: float, base_z: float, spec: list[tuple], mat: str = "gem", tint: str | None = None) -> list[Cube]:
    """Buendel schraeger Kristalle/Finnen: (dx, dz, breite, hoehe, rot_x, rot_z) relativ zum Fusspunkt."""
    out = []
    for dx, dz, w, h, rx, rz in spec:
        o = (base_x + dx - w / 2, base_y, base_z + dz - w / 2)
        out.append(C(o, (w, h, w), mat, rot=(rx, 0, rz), pivot=(base_x + dx, base_y, base_z + dz), seams=False, tint=tint))
    return out


def chain(start: tuple[float, float, float], segments: list[tuple[float, float, float]], mat: str = "plate",
          tip: str | None = None, tint: str | None = None) -> list[Cube]:
    """Gliederkette (Schwanz): jedes Glied (Laenge, Dicke, Neigung nach hinten in Grad) setzt am Ende des vorigen an."""
    out = []
    x, y, z = start
    for i, (length, thick, tilt) in enumerate(segments):
        mat_i = tip if tip and i == len(segments) - 1 else mat
        out.append(C((x - thick / 2, y - length, z - thick / 2), (thick, length, thick), mat_i, rot=(-tilt, 0, 0), pivot=(x, y, z),
                     seams=False, tint=None if mat_i == tip else tint))
        rad = math.radians(tilt)
        y -= math.cos(rad) * length
        z += math.sin(rad) * length
    return out


def halo(y: float, z: float, r: float, mat: str = "glow") -> list[Cube]:
    """Ring hinter dem Kopf aus vier duennen Balken."""
    t = 0.6
    return [C((-r, y + r - t, z), (2 * r, t, t), mat), C((-r, y - r, z), (2 * r, t, t), mat),
            C((-r, y - r, z), (t, 2 * r, t), mat), C((r - t, y - r, z), (t, 2 * r, t), mat)]


def dome(radius: float, y0: float, height: float, tint: str, mat: str = "gloss", layers: int = 4) -> list[Cube]:
    """Kuppelhelm aus gestapelten, nach oben schmaler werdenden Lagen (Ratchet-Helme)."""
    out = []
    y = y0
    for i in range(layers):
        r = radius * math.sqrt(max(0.15, 1 - (i / layers) ** 2.2))
        h = height * 0.5 if i == 0 else height * 0.5 / (layers - 1)
        out.append(C((-r, y, -r), (2 * r, h, 2 * r), mat, tint=tint, seams=False))
        y += h
    return out


def lames(o: tuple[float, float, float], s: tuple[float, float, float], count: int = 3, mat: str = "plate",
          tint: str | None = None, flare: float = 0.25) -> list[Cube]:
    """Ueberlappende Schienen (Lamellen) wie bei Plattenruestung: von oben nach unten, jede etwas breiter als die
    darueber und leicht ueberlappend."""
    out = []
    step = s[1] / count
    for i in range(count):
        grow = flare * i
        c = C((o[0] - grow / 2, o[1] + s[1] - (i + 1) * step - 0.15, o[2] - grow / 2), (s[0] + grow, step + 0.3, s[2] + grow),
              mat, tint=tint, seams=False)
        c.layer = False  # type: ignore[attr-defined]
        out.append(c)
    return out


def pauldron(top_y: float, mat: str = "plate", tint: str | None = None, spread: float = 1.0) -> list[Cube]:
    """Geschuppter Schulterpanzer der rechten Schulter aus drei Lagen, nach aussen und unten gefaechert."""
    out = [C((-9.6, top_y - 3.2, -3.4), (5.8, 3.4, 6.8), mat, tint=tint, edge=("up",), seams=False),
           C((-10.2, top_y - 4.6, -3.2), (5.0, 2.2, 6.4), mat, tint=tint, rot=(0, 0, 14 * spread), pivot=(-5.4, top_y - 2.4, 0.0),
             seams=False),
           C((-10.6, top_y - 5.8, -3.0), (4.4, 1.8, 6.0), mat, tint=tint, rot=(0, 0, 26 * spread), pivot=(-5.4, top_y - 3.2, 0.0),
             seams=False),
           C((-9.8, top_y - 0.6, -3.6), (6.2, 0.7, 7.2), "trim")]
    for c in out[:1]:
        c.layer = False  # type: ignore[attr-defined]
    return out


# --- Ratchet & Clank: Grundgeruest (klobig wie im Spiel: grosse Handschuhe, grosse Stiefel) -----------------------------

def rc_frame(suit: str, plate: str, dark: str, sole: str) -> dict[str, list[Cube]]:
    body = [
        C((-4.4, 12.4, -2.4), (8.8, 12.0, 4.8), "suit", tint=suit),
        C((-4.3, 16.4, -3.2), (8.6, 7.2, 1.0), tint=plate),                                          # Brustpanzer
        C((-4.6, 22.6, -3.0), (9.2, 1.8, 6.0), "trim", tint=dark),                                   # Kragen
        C((-4.4, 14.2, 2.2), (8.8, 9.6, 1.0), tint=plate),                                           # Rueckenplatte
        C((-4.7, 11.4, -2.9), (9.4, 1.8, 5.8), "trim", tint=dark),                                   # Guertel
        C((-4.9, 10.2, -1.6), (1.4, 2.4, 2.6), "trim", tint=dark), C((3.5, 10.2, -1.6), (1.4, 2.4, 2.6), "trim", tint=dark),
    ]
    arm = [
        C((-8.2, 16.4, -2.2), (4.0, 7.6, 4.4), "suit", tint=suit),
        C((-9.4, 20.2, -3.0), (5.4, 3.8, 6.0), tint=plate),                                          # Schulter (Basis)
        C((-9.6, 10.6, -3.2), (5.8, 6.4, 6.4), tint=plate),                                          # Riesenhandschuh
        C((-9.8, 16.4, -3.4), (6.2, 1.2, 6.8), "trim", tint=dark),                                   # Stulpe
        C((-9.2, 10.2, -2.6), (5.0, 1.0, 5.2), "trim", tint=dark),                                   # Fingerkante
    ] + pauldron(24.4, tint=plate)
    leg = [
        C((-4.1, 4.4, -2.3), (4.2, 7.8, 4.6), "suit", tint=suit),
        C((-4.3, 8.2, -2.7), (4.6, 3.8, 5.4), tint=plate),                                           # Oberschenkel
        *lames((-4.4, 7.6, -2.9), (4.8, 4.4, 5.8), 3, tint=plate),
        C((-3.9, 4.6, -3.4), (3.8, 3.2, 1.2), tint=plate),                                           # Knie
    ]
    boot = [
        C((-4.7, -0.2, -3.4), (5.4, 5.2, 6.6), tint=plate),                                          # Stiefel
        C((-4.8, -0.4, -3.7), (5.6, 1.0, 7.2), "trim", tint=sole),                                   # Sohle
        C((-4.5, -0.4, -4.9), (5.0, 2.6, 1.6), tint=plate),                                          # Kappe
        C((-4.8, 4.6, -3.6), (5.6, 1.0, 7.0), "trim", tint=dark),                                    # Schaftrand
    ]
    return {"armorHead": [], "armorBody": body, "armorRightArm": arm, "armorRightLeg": leg, "armorRightBoot": boot}


LOOP = 8.0  # Laenge der Leerlauf-Animation in Sekunden (alle Bewegungen wiederholen sich darin nahtlos)


@dataclass
class ArmorSet:
    pal: Palette
    bones: dict[str, list[Cube]]
    subbones: dict[str, tuple[str, list[float]]] = field(default_factory=dict)   # Name -> (Eltern-Bone, Drehpunkt)
    anim: dict[str, dict] = field(default_factory=dict)                           # Bone -> Kanaele (Bedrock-Animation)

    def sub(self, name: str, parent: str, pivot: list[float], cubes: list[Cube]) -> None:
        self.subbones[name] = (parent, pivot)
        self.bones.setdefault(name, []).extend(cubes)


def spin(axis: int, turns: int = 1) -> dict:
    """Gleichmaessige Drehung um eine Achse, 'turns' volle Umdrehungen je Schleife."""
    keys = {}
    for i in range(9):
        t = LOOP * i / 8
        rot = [0.0, 0.0, 0.0]
        rot[axis] = 360.0 * turns * i / 8
        keys[f"{t:.2f}"] = rot
    return {"rotation": keys}


def wave(channel: str, axis: int, amplitude: float, periods: int = 2, base: float = 0.0) -> dict:
    """Sinus-Bewegung (Wehen, Schweben, Pulsieren) als Stuetzpunkte; 'scale' pulsiert um 1."""
    keys = {}
    for i in range(17):
        t = LOOP * i / 16
        value = base + amplitude * math.sin(2 * math.pi * periods * i / 16)
        if channel == "scale":
            keys[f"{t:.2f}"] = [1 + value] * 3
        else:
            vec = [0.0, 0.0, 0.0]
            vec[axis] = round(value, 3)
            keys[f"{t:.2f}"] = vec
    return {channel: keys}


def merge(*channels: dict) -> dict:
    out: dict = {}
    for c in channels:
        out.update(c)
    return out


def ring_xz(cx: float, cy: float, cz: float, r: float, t: float = 0.6, mat: str = "glow") -> list[Cube]:
    """Waagerechter Ring aus vier Balken (um Arme, Koerper)."""
    return [C((cx - r, cy, cz - r), (2 * r, t, t), mat), C((cx - r, cy, cz + r - t), (2 * r, t, t), mat),
            C((cx - r, cy, cz - r), (t, t, 2 * r), mat), C((cx + r - t, cy, cz - r), (t, t, 2 * r), mat)]


def cape(top: float, length: float, z: float, tint: str, edge_mat: str = "glow", cracks: bool = False, width: float = 8.6) -> list[Cube]:
    """Umhang am Ruecken mit leuchtendem Saum und gezackten Enden."""
    out = [C((-width / 2, top - length, z), (width, length, 0.5), "suit", tint=tint, cracks=cracks, seams=False),
           C((-width / 2 - 0.2, top - length - 0.4, z - 0.1), (width + 0.4, 0.8, 0.7), edge_mat),
           C((-width / 2 - 0.3, top - length, z - 0.1), (0.5, length, 0.7), edge_mat),
           C((width / 2 - 0.2, top - length, z - 0.1), (0.5, length, 0.7), edge_mat)]
    for i in range(4):
        x = -width / 2 + 0.6 + i * (width - 1.6) / 3
        out.append(C((x, top - length - 2.2, z), (0.9, 2.2, 0.5), edge_mat, rot=(0, 0, 10 if i % 2 else -10)))
    return out


def magnaplate() -> ArmorSet:
    """Magnaplate (R&C, Stufe 1): schwarzer Glanz-Kuppelhelm mit weissem Visier und Antenne, Marineblau mit Silberrahmen,
    blauer Kern, Riesenhandschuhe mit blauen Ringen — und Ratchets Heli-Pack mit drehendem Rotor auf dem Ruecken."""
    pal = Palette("1E2A4A", "0A1020", "6A7A9A", "1A2238", "B8C0C8", "3A9AFF", "E0F2FF", "3A9AFF", "D0D8E4")
    b = rc_frame("1A2238", "1E2A4A", "101216", "5A5E66")
    b["armorHead"] = dome(5.3, 23.0, 9.4, "1A1A20") + [
        C((-3.8, 25.2, -5.6), (7.6, 3.8, 0.5), "gloss", tint="D0D8E4"),
        C((-2.8, 29.8, -5.4), (5.6, 0.5, 0.4), "glow"),
        C((-5.6, 24.0, -1.8), (0.8, 3.6, 3.6), "trim"), C((4.8, 24.0, -1.8), (0.8, 3.6, 3.6), "trim"),
        C((4.9, 26.0, -0.4), (0.4, 7.0, 0.4), "trim"),                                               # Antenne
    ]
    b["armorBody"] += [
        C((-4.0, 16.8, -3.5), (8.0, 6.4, 0.6), "trim", seams=False),
        C((-1.8, 18.6, -4.1), (3.6, 3.6, 0.6), "gem"),
        C((-2.4, 18.0, -3.9), (4.8, 4.8, 0.3), "trim", tint="101216"),
        C((-3.4, 13.6, 3.0), (6.8, 8.8, 3.2), "gloss", tint="22262E"),                               # Heli-Pack
        C((-1.0, 22.0, 4.0), (2.0, 3.0, 1.6), "trim"),                                               # Rotormast
        C((-3.0, 14.4, 6.2), (2.0, 2.0, 0.4), "glow"), C((1.0, 14.4, 6.2), (2.0, 2.0, 0.4), "glow"),
    ]
    b["armorRightArm"][2].lines += [("h", 0.25, ALL4), ("h", 0.5, ALL4), ("h", 0.75, ALL4)]
    b["armorRightArm"][2].tint = "141418"
    b["armorRightArm"] += [C((-9.0, 23.4, -2.4), (4.4, 1.2, 4.8), "trim", lines=[("h", 0.5, ALL4)])]
    b["armorRightLeg"][2].mat, b["armorRightLeg"][2].tint = "gloss", "1A1A20"
    b["armorRightBoot"][0].tint = "141418"
    b["armorRightBoot"][2].tint = "141418"
    b["armorRightBoot"] += [C((-4.0, 1.2, -5.0), (3.8, 0.6, 0.3), "glow")]
    armor = ArmorSet(pal, symmetric(b))
    armor.sub("antennaTip", "armorHead", [5.1, 33.0, -0.2], [C((4.6, 33.0, -0.7), (1.0, 1.0, 1.0), "glow")])
    armor.sub("rotor", "armorBody", [0.0, 25.2, 4.8], [
        C((-0.6, 25.0, 4.2), (1.2, 0.8, 1.2), "trim"),
        C((-9.0, 25.6, 4.4), (18.0, 0.3, 0.8), "trim", tint="2A2E36"),                               # Rotorblaetter
        C((-0.4, 25.6, -4.2), (0.8, 0.3, 18.0), "trim", tint="2A2E36"),
        C((-9.0, 25.6, 4.4), (1.2, 0.35, 0.8), "glow"), C((7.8, 25.6, 4.4), (1.2, 0.35, 0.8), "glow"),
    ])
    armor.anim = {"rotor": spin(1, 4), "antennaTip": wave("scale", 0, 0.25, 4)}
    return armor


def carbonox() -> ArmorSet:
    """Carbonox (R&C, Stufe 2): grauer Vollpanzer mit riesigen Lombax-Ohren, Gitter-Gesicht mit roten Augen,
    rote Kern- und Kniescheinwerfer, gepanzerter Schwanz, Doppel-Schubduesen und Armklingen."""
    pal = Palette("6A7078", "2A2E34", "B8C0C8", "1C1E22", "3A3E44", "FF2A2A", "FFC0B0", "FF2A2A", "FF2A2A")
    b = rc_frame("1C1E22", "6A7078", "2A2E34", "1A1A1C")
    b["armorHead"] = [
        C((-5.0, 23.2, -5.0), (10.0, 8.4, 10.0)),
        C((-4.2, 31.6, -4.2), (8.4, 1.4, 8.4)),
        C((-4.2, 23.8, -5.7), (8.4, 6.4, 0.8), "trim", tint="1A1C20"),
        C((-3.2, 24.4, -6.0), (6.4, 0.5, 0.3), "trim", tint="5A5E66"),
        C((-3.2, 25.4, -6.0), (6.4, 0.5, 0.3), "trim", tint="5A5E66"),
        C((-0.6, 31.0, -5.4), (1.2, 2.6, 1.4), "glow"),
        C((-0.7, 32.8, -3.8), (1.4, 1.2, 7.0), lines=[("h", 0.5, SIDES)]),
    ]
    b["armorHead"] += pair(C((-3.4, 27.6, -6.0), (2.4, 1.2, 0.4), "glow"))
    b["armorBody"] += [
        C((-1.4, 18.8, -4.0), (2.8, 2.8, 0.6), "gem"),
        C((-3.6, 13.0, -3.0), (7.2, 0.8, 0.4), "trim", lines=[("h", 0.5, FRONT)]),
        C((-3.6, 14.4, -3.0), (7.2, 0.8, 0.4), "trim", lines=[("h", 0.5, FRONT)]),
        C((-4.2, 14.0, 3.0), (3.0, 9.4, 3.0), lines=[("h", 0.8, ALL4)]),                             # Schubduesen
        C((1.2, 14.0, 3.0), (3.0, 9.4, 3.0), lines=[("h", 0.8, ALL4)]),
        C((-3.8, 12.8, 3.4), (2.2, 1.2, 2.2), "glow"), C((1.6, 12.8, 3.4), (2.2, 1.2, 2.2), "glow"),
    ]
    b["armorBody"] += pair(C((-4.6, 17.0, -3.6), (4.4, 6.2, 1.0), rot=(0, 12, 0), pivot=(-0.2, 17.0, -3.6)))
    b["armorRightArm"][1].lines.append(("h", 0.5, ALL4))
    b["armorRightArm"] += [C((-9.9, 12.6, -1.0), (0.4, 2.4, 2.0), "glow"),
                           C((-10.2, 9.0, -0.3), (0.6, 7.0, 0.6), "trim", rot=(0, 0, 6), pivot=(-9.9, 16.0, 0.0)),   # Armklinge
                           C((-10.6, 20.6, -3.4), (1.4, 3.0, 6.8), lines=[("h", 0.5, ("west",))])]
    b["armorRightLeg"] += [C((-2.8, 5.4, -3.7), (1.6, 1.6, 0.4), "glow")]
    b["armorRightBoot"] += [C((-3.4, 2.8, -5.0), (2.4, 0.8, 0.3), "glow")]
    armor = ArmorSet(pal, symmetric(b))
    ear = C((3.6, 29.6, -1.6), (1.8, 8.6, 3.2), rot=(18, 0, -28), pivot=(4.5, 29.6, 0.0))
    tip = C((3.9, 34.6, -1.0), (1.2, 4.2, 2.0), "trim", rot=(18, 0, -28), pivot=(4.5, 29.6, 0.0), tint="2A2E34")
    armor.sub("earL", "armorHead", [4.5, 29.6, 0.0], [ear, tip])
    armor.sub("earR", "armorHead", [-4.5, 29.6, 0.0], [mirror(ear, 0, 0), mirror(tip, 0, 0)])
    armor.sub("tail", "armorBody", [0.0, 12.6, 2.6],
              chain((0.0, 12.6, 2.6), [(4.2, 1.8, 35), (3.6, 1.6, 55), (3.0, 1.4, 75), (2.0, 1.2, 85)], tip="glow"))
    armor.anim = {"earL": wave("rotation", 2, 4, 1), "earR": wave("rotation", 2, -4, 1), "tail": wave("rotation", 1, 14, 2)}
    return armor


def infernox() -> ArmorSet:
    """Infernox (R&C, Endstufe): Bronze und Gold mit gluehenden Rissen, Flammenkrone, Lombax-Ohren, Flammen-Emblem,
    gluehende Schienbein-Flammen, Hoellenfeuer-Generator mit drehendem Glutring, wehender Glut-Umhang und Schwanz."""
    pal = Palette("8A4A16", "2A1006", "E8A04A", "3A140C", "D8A03A", "FF6A0A", "FFF0A0", "FFB02A", "FFC02A")
    b = rc_frame("3A140C", "8A4A16", "4A2A10", "2A1408")
    b["armorHead"] = [
        C((-5.0, 23.2, -5.0), (10.0, 8.4, 10.0), cracks=True),
        C((-0.6, 31.4, -5.0), (1.2, 1.8, 9.0), "trim"),
        C((-1.8, 24.0, -6.3), (3.6, 2.2, 0.6), "suit", tint="1A0A06", lines=[("v", 0.33, FRONT), ("v", 0.66, FRONT)]),
    ]
    b["armorHead"] += pair(C((-4.8, 24.0, -5.9), (4.8, 6.6, 1.0), rot=(0, -14, 0), pivot=(0.0, 24.0, -5.9), cracks=True))
    b["armorHead"] += pair(C((-3.8, 27.4, -6.6), (2.8, 1.0, 0.4), "glow", rot=(0, 0, 14), pivot=(-2.4, 27.9, -6.4)))
    b["armorHead"] += pair(C((3.4, 29.4, -1.8), (2.0, 10.0, 3.4), "trim", rot=(14, 0, -20), pivot=(4.4, 29.4, 0.0)))
    b["armorHead"] += pair(C((3.8, 30.4, -2.3), (1.2, 7.6, 0.6), "suit", rot=(14, 0, -20), pivot=(4.4, 29.4, 0.0), tint="2A0A04"))
    b["armorBody"] += [
        C((-1.9, 18.0, -4.1), (3.8, 3.8, 0.7), "gem", rot=(0, 0, 45), pivot=(0.0, 19.9, -4.1)),
        C((-2.8, 17.1, -3.7), (5.6, 5.6, 0.4), "trim", rot=(0, 0, 45), pivot=(0.0, 19.9, -3.7)),
        C((-3.4, 13.0, -2.9), (6.8, 0.6, 0.4), "glow"), C((-3.0, 14.2, -2.9), (6.0, 0.6, 0.4), "glow"),
        C((-2.8, 14.4, 3.0), (5.6, 7.4, 2.8), "trim", tint="5A3410"),
        C((-1.4, 16.4, 5.7), (2.8, 3.4, 0.4), "glow"),
    ]
    b["armorBody"][1].cracks = True
    b["armorBody"] += pair(C((-4.6, 17.0, -3.6), (4.4, 6.2, 1.0), rot=(0, 12, 0), pivot=(-0.2, 17.0, -3.6),
                             lines=[("h", 0.85, FRONT)], cracks=True))
    b["armorRightArm"] += [C((-9.8, 20.6, -2.6), (5.8, 2.6, 5.2), "trim", rot=(0, 0, 12), pivot=(-6.7, 21.9, 0.0)),
                           C((-10.6, 21.6, -2.0), (4.8, 2.0, 4.0), "trim", rot=(0, 0, 26), pivot=(-6.7, 22.6, 0.0)),
                           C((-9.9, 11.4, -1.4), (0.4, 4.6, 2.8), "glow")]
    b["armorRightArm"] += shards(-8.6, 24.0, 0, [(0, -1.4, 0.9, 3.6, -20, 30), (0, 0.2, 1.0, 4.4, 0, 32), (0, 1.8, 0.9, 3.4, 20, 30)],
                                 "glow")
    b["armorRightArm"][1].cracks = True
    b["armorRightArm"][2].cracks = True
    b["armorRightArm"][2].lines += [("h", 0.3, ALL4), ("h", 0.7, ALL4)]
    b["armorRightLeg"][2].mat = "trim"
    b["armorRightBoot"][0].cracks = True
    b["armorRightBoot"] += [C((-3.6, 2.6, -5.1), (1.0, 4.6, 0.4), "glow"), C((-1.8, 2.6, -5.1), (1.0, 3.6, 0.4), "glow"),
                            C((-4.4, -0.4, -5.0), (4.8, 1.4, 1.4), "trim")]
    armor = ArmorSet(pal, symmetric(b))
    crown = []
    for i in range(7):
        angle = math.radians(-90 + i * 30)
        x, z = math.cos(angle) * 3.8, math.sin(angle) * 3.8
        crown += shards(x, 31.6, z, [(0, 0, 0.9, 2.4 + (1.6 if i == 3 else 0.6 * (i % 2)), -math.sin(angle) * 20, math.cos(angle) * 20)],
                        "glow")
    armor.sub("crown", "armorHead", [0.0, 31.6, 0.0], crown)
    armor.sub("hellRing", "armorBody", [0.0, 18.1, 6.2], halo(18.1, 6.0, 2.6))
    armor.sub("cape", "armorBody", [0.0, 23.6, 6.0], cape(23.6, 15.0, 6.0, "3A0A06", cracks=True, width=9.4))
    armor.sub("tail", "armorBody", [0.0, 12.6, 2.8],
              chain((0.0, 12.6, 2.8), [(4.4, 2.0, 30), (3.8, 1.8, 50), (3.2, 1.6, 70), (2.4, 1.4, 85)], tip="glow"))
    armor.anim = {"crown": wave("scale", 0, 0.08, 4), "hellRing": spin(2, 2), "cape": wave("rotation", 0, 6, 2, base=-8),
                  "tail": wave("rotation", 1, 16, 2)}
    return armor


# --- Ben 10: Ruestung in der Form der Aliens (mit Omnitrix-Symbol auf der Brust) -------------------------------------

def ben_uniform(white: str, black: str) -> list[Cube]:
    """Zweifarbiger Alien-Anzug wie bei Diamondhead/Upgrade: eine Haelfte hell, eine dunkel, Omnitrix-Symbol mittig."""
    return [C((-4.4, 12.4, -2.4), (4.4, 12.0, 4.8), "suit", tint=white), C((0.0, 12.4, -2.4), (4.4, 12.0, 4.8), "suit", tint=black),
            C((-2.0, 19.0, -3.2), (4.0, 4.0, 0.6), "omni"), C((-2.4, 18.6, -3.0), (4.8, 4.8, 0.4), "trim", tint="1A1A1A")]


def taydenite() -> ArmorSet:
    """Taydenit (Ben 10, Stufe 1) in Diamondhead-Form: Kristallkopf mit grosser Diamantklinge, Kristallbuendel auf
    Schultern und Ruecken, Armklingen, schwarz-weisser Anzug mit Omnitrix-Symbol — und Kristallsplitter, die um den
    Traeger kreisen."""
    pal = Palette("2ACA8A", "0E4A34", "B8FFE0", "1A1A1A", "E8F0EC", "3AFFB0", "F0FFF8", "5AF0C0", "3AFFB0")
    b: dict[str, list[Cube]] = {}
    b["armorHead"] = [
        C((-4.8, 23.4, -4.8), (9.6, 7.2, 9.6)),
        C((-3.4, 26.4, -5.3), (6.8, 2.2, 0.5), "suit", tint="06140E"),
        C((-2.4, 30.4, -2.8), (4.8, 3.2, 5.6), rot=(-10, 0, 0), pivot=(0, 30.4, 0)),
        C((-1.4, 33.2, -2.0), (2.8, 5.4, 3.6), "gem", rot=(-16, 0, 0), pivot=(0, 30.4, 0)),
        C((-0.7, 38.2, -1.0), (1.4, 2.4, 1.8), "gem", rot=(-22, 0, 0), pivot=(0, 30.4, 0)),
    ]
    b["armorHead"] += pair(C((-2.8, 27.0, -5.5), (1.8, 0.8, 0.3), "glow"))
    b["armorHead"] += pair(C((3.6, 24.0, -4.4), (1.8, 6.0, 5.0), rot=(0, -18, 8), pivot=(4.5, 24.0, -2.0)))
    b["armorHead"] += shards(3.4, 29.6, 1.0, [(0, 0, 1.2, 3.6, 20, 40)]) + shards(-3.4, 29.6, 1.0, [(0, 0, 1.2, 3.6, 20, -40)])
    b["armorBody"] = ben_uniform("E8ECEA", "141414") + [
        C((-4.6, 22.4, -3.0), (9.2, 1.6, 6.0), "trim", tint="141414"),
        C((-4.6, 11.4, -2.9), (9.2, 1.6, 5.8), "trim", tint="141414", lines=[("h", 0.5, ALL4)]),
        C((-4.2, 16.0, -3.0), (2.0, 6.4, 0.8), rot=(0, 0, 8)), C((2.2, 16.0, -3.0), (2.0, 6.4, 0.8), rot=(0, 0, -8)),
    ]
    b["armorBody"] += shards(0, 21.0, 2.6, [(-2.6, 0, 1.8, 8.0, -30, 22), (2.6, 0, 1.8, 8.0, -30, -22), (0, 0.4, 2.4, 10.0, -24, 0),
                                            (-1.4, 0.2, 1.4, 5.6, -40, 10), (1.4, 0.2, 1.4, 5.6, -40, -10),
                                            (-3.4, 0.2, 1.2, 4.0, -20, 40), (3.4, 0.2, 1.2, 4.0, -20, -40)])
    b["armorRightArm"] = [
        C((-8.2, 16.4, -2.2), (4.0, 7.6, 4.4), "suit", tint="E8ECEA"),
        C((-9.8, 19.6, -3.4), (6.0, 4.4, 6.8)),
        C((-9.4, 11.0, -3.0), (5.4, 6.2, 6.0)),
        C((-9.4, 7.0, -0.6), (0.9, 9.0, 1.4), "gem", rot=(0, 0, 8), pivot=(-9.0, 16.0, 0.0)),
        *pauldron(24.6),
        *lames((-9.6, 11.6, -3.2), (5.8, 5.4, 6.4), 3),
    ]
    b["armorRightArm"] += shards(-7.4, 24.0, 0, [(0, 0, 2.4, 8.4, 0, 20), (-1.6, -1.8, 1.8, 6.0, -20, 34), (-1.6, 1.8, 1.8, 5.6, 22, 32),
                                                 (1.2, 0.6, 1.4, 4.0, 8, -10), (0.4, -2.2, 1.2, 3.4, -30, 8), (-2.6, 0, 1.2, 3.6, 0, 55)])
    b["armorRightLeg"] = [C((-4.1, 4.4, -2.3), (4.2, 7.8, 4.6), "suit", tint="E8ECEA"),
                          *lames((-4.4, 7.4, -2.9), (4.8, 4.8, 5.8), 3),
                          C((-3.6, 4.6, -3.4), (3.2, 3.0, 1.2), "gem")]
    b["armorRightLeg"] += shards(-2.0, 6.8, -3.6, [(0, 0, 1.2, 3.0, -55, 0)])
    b["armorRightBoot"] = [C((-4.6, -0.2, -3.2), (5.2, 4.8, 6.2)), C((-4.4, -0.4, -4.6), (4.8, 2.2, 1.6), "gem")]
    b = symmetric(b)
    for cube in b["armorLeftArm"] + b["armorLeftLeg"]:
        if cube.mat == "suit":
            cube.tint = "141414"
    armor = ArmorSet(pal, b)
    orbit = []
    for i in range(6):
        angle = math.radians(i * 60)
        x, z = math.cos(angle) * 10.0, math.sin(angle) * 10.0
        y = 14.0 + (i % 3) * 4.0
        orbit += shards(x, y, z, [(0, 0, 1.4, 3.6 + (i % 2) * 1.4, 15 * math.sin(angle), 15 * math.cos(angle))])
    armor.sub("orbit", "armorBody", [0.0, 18.0, 0.0], orbit)
    armor.anim = {"orbit": merge(spin(1, 1), wave("position", 1, 0.8, 2))}
    return armor


def galvan() -> ArmorSet:
    """Galvan-Biotech (Ben 10, Stufe 2) in Upgrade-Form: glatter schwarzer Kuppelkopf mit grossem pulsierendem Kreisauge,
    gruene Schaltkreis-Baender, silberweisser Anzug, Omnitrix-Symbol, schwebende Datenringe um die Unterarme und
    vier Kabel-Tentakel auf dem Ruecken."""
    pal = Palette("121416", "050607", "4A5058", "D8DCE0", "1A1C20", "3AFF5A", "E8FFE0", "3AFF5A", "3AFF5A")
    circuit = [("v", 0.3, ("north", "south")), ("v", 0.7, ("north", "south"))]
    b: dict[str, list[Cube]] = {}
    b["armorHead"] = dome(5.0, 23.2, 9.2, "121416", layers=5) + [
        C((-2.9, 25.0, -5.5), (5.8, 5.8, 0.5), "trim", tint="050607"),
        C((-5.1, 26.0, -3.0), (0.4, 0.6, 6.0), "glow"), C((4.7, 26.0, -3.0), (0.4, 0.6, 6.0), "glow"),
        C((-0.3, 30.6, -4.8), (0.6, 2.2, 9.6), "glow"),
    ]
    b["armorHead"] += pair(C((2.6, 32.2, -0.8), (1.0, 2.4, 1.0), "trim", tint="121416", rot=(0, 0, -20), pivot=(3.1, 32.2, -0.3)))
    b["armorBody"] = [C((-4.4, 12.4, -2.4), (8.8, 12.0, 4.8), "suit"),
                      C((-4.6, 20.0, -3.0), (9.2, 4.2, 6.0), lines=circuit),
                      C((-4.6, 12.0, -3.0), (9.2, 2.4, 6.0), lines=circuit),
                      C((-1.6, 14.4, -3.0), (3.2, 5.6, 0.6), lines=[("v", 0.5, FRONT)]),
                      C((-2.0, 16.0, -3.6), (4.0, 4.0, 0.6), "omni"),
                      C((-2.6, 15.0, 2.4), (5.2, 7.0, 1.6), lines=circuit)]
    b["armorRightArm"] = [C((-8.2, 12.4, -2.2), (4.0, 11.6, 4.4), "suit"),
                          *pauldron(24.4),
                          C((-9.0, 15.4, -2.6), (4.8, 2.0, 5.2), lines=circuit),
                          C((-9.4, 10.8, -3.0), (5.4, 3.6, 6.0), lines=circuit),
                          C((-9.6, 12.2, -1.0), (0.4, 1.0, 2.0), "glow")]
    b["armorRightLeg"] = [C((-4.1, 4.4, -2.3), (4.2, 7.8, 4.6), "suit"),
                          *lames((-4.4, 7.6, -2.9), (4.8, 4.2, 5.8), 3),
                          C((-4.3, 4.8, -2.7), (4.6, 2.0, 5.4), lines=circuit)]
    b["armorRightBoot"] = [C((-4.5, -0.2, -3.0), (5.0, 4.4, 6.0), lines=circuit), C((-4.3, -0.4, -4.2), (4.6, 2.0, 1.4))]
    armor = ArmorSet(pal, symmetric(b))
    armor.sub("eye", "armorHead", [0.0, 27.9, -5.9], [C((-2.3, 25.6, -5.9), (4.6, 4.6, 0.5), "gem"),
                                                     C((-0.7, 27.2, -6.1), (1.4, 1.4, 0.3), "trim", tint="050607")])
    armor.sub("ringR", "armorRightArm", [-6.6, 14.0, 0.0], ring_xz(-6.6, 14.0, 0.0, 4.4) + ring_xz(-6.6, 11.6, 0.0, 3.8))
    armor.sub("ringL", "armorLeftArm", [6.6, 14.0, 0.0], ring_xz(6.6, 14.0, 0.0, 4.4) + ring_xz(6.6, 11.6, 0.0, 3.8))
    tendrils = []
    for side, tilt in ((-1, 30), (1, 30), (-1, 60), (1, 60)):
        x0 = side * (1.6 if tilt == 30 else 2.8)
        tendrils += [C((x0 - 0.5, 22.0, 3.0), (1.0, 9.0, 1.0), "trim", tint="121416", rot=(-25, 0, side * -tilt), pivot=(x0, 22.0, 3.5)),
                     C((x0 - 0.6, 30.6, 2.9), (1.2, 1.4, 1.2), "glow", rot=(-25, 0, side * -tilt), pivot=(x0, 22.0, 3.5))]
    armor.sub("tendrils", "armorBody", [0.0, 22.0, 3.5], tendrils)
    armor.anim = {"eye": wave("scale", 0, 0.12, 4), "ringR": spin(1, 2), "ringL": spin(1, -2),
                  "tendrils": wave("rotation", 0, 8, 2)}
    return armor


def celestial() -> ArmorSet:
    """Celestialsapien (Ben 10, Endstufe) in Alien-X-Form: Sternenhimmel-Koerper, grosse weisse Hoerner, gruene Augen,
    Omnitrix-Symbol, drehender Heiligenschein, schlagende Lichtschwingen, Sternen-Umhang mit leuchtendem Saum und
    kreisende Sternfunken."""
    pal = Palette("0A0618", "000000", "E8E8FF", "06040E", "F4F4FF", "C8E8FF", "FFFFFF", "FFFFFF", "FFFFFF", cosmic=True)
    b: dict[str, list[Cube]] = {}
    b["armorHead"] = [C((-4.7, 23.4, -4.7), (9.4, 8.6, 9.4))]
    b["armorHead"] += pair(C((-3.2, 27.0, -5.0), (2.2, 1.4, 0.4), "glow"))
    b["armorHead"] += shards(2.6, 31.4, -1.4, [(0, 0, 2.0, 6.4, -14, -12)], "trim")
    b["armorHead"] += shards(-2.6, 31.4, -1.4, [(0, 0, 2.0, 6.4, -14, 12)], "trim")
    b["armorHead"] += shards(3.2, 37.4, -2.8, [(0, 0, 1.4, 3.6, -28, 4)], "trim")
    b["armorHead"] += shards(-3.2, 37.4, -2.8, [(0, 0, 1.4, 3.6, -28, -4)], "trim")
    b["armorBody"] = [C((-4.6, 12.2, -2.6), (9.2, 12.4, 5.2)),
                      C((-2.0, 18.4, -3.2), (4.0, 4.0, 0.6), "omni"),
                      C((-4.8, 22.6, -3.0), (9.6, 1.4, 6.0), "trim"),
                      C((-4.8, 11.6, -3.0), (9.6, 1.0, 6.0), "trim")]
    b["armorRightArm"] = [C((-8.4, 11.2, -2.4), (4.4, 12.6, 4.8)),
                          *pauldron(24.6),
                          C((-10.6, 21.4, -2.4), (2.4, 4.6, 4.8), "trim", rot=(0, 0, 25), pivot=(-8.0, 22.0, 0.0)),
                          C((-9.0, 11.0, -2.8), (5.0, 1.0, 5.6), "trim")]
    b["armorRightLeg"] = [C((-4.2, 3.6, -2.4), (4.4, 8.6, 4.8)), C((-4.0, 5.0, -3.0), (3.6, 2.4, 0.8), "trim"),
                          *lames((-4.5, 7.4, -2.8), (5.0, 4.6, 5.6), 3)]
    b["armorRightBoot"] = [C((-4.4, -0.2, -2.8), (4.8, 4.0, 5.6)), C((-4.5, -0.4, -3.2), (5.0, 0.8, 6.4), "trim")]
    armor = ArmorSet(pal, symmetric(b))
    armor.sub("halo", "armorHead", [0.0, 30.0, 6.2], halo(30.0, 6.2, 6.4))
    for name, side in (("wingL", 1), ("wingR", -1)):
        wing = []
        for length, angle in ((15.0, 28), (13.0, 50), (10.0, 72), (7.0, 94)):
            wing.append(C((side * 1.5 - 0.5, 20.0, 3.0), (1.0, length, 0.6), "glow", rot=(-12, 0, -side * angle), pivot=(side * 1.5, 20.0, 3.0)))
        armor.sub(name, "armorBody", [side * 1.5, 20.0, 3.0], wing)
    armor.sub("cape", "armorBody", [0.0, 23.6, 3.0], cape(23.6, 15.0, 3.0, "0A0618", edge_mat="glow"))
    for c in armor.bones["cape"][:1]:
        c.mat = "plate"                                                                              # Sternenhimmel statt Stoff
    motes = []
    for i in range(8):
        angle = math.radians(i * 45)
        motes.append(C((math.cos(angle) * 9 - 0.4, 10.0 + (i % 4) * 4.0, math.sin(angle) * 9 - 0.4), (0.8, 0.8, 0.8), "glow"))
    armor.sub("motes", "armorBody", [0.0, 18.0, 0.0], motes)
    armor.anim = {"halo": spin(2, 1), "wingL": wave("rotation", 2, -10, 2), "wingR": wave("rotation", 2, 10, 2),
                  "cape": wave("rotation", 0, 5, 2, base=-6), "motes": merge(spin(1, -1), wave("position", 1, 1.0, 4))}
    return armor


SETS = {"taydenite": taydenite, "galvan": galvan, "celestial": celestial,
        "magnaplate": magnaplate, "carbonox": carbonox, "infernox": infernox}
ANIM_DIR = ASSETS / "animations" / "armor"


def animation(name: str, armor: ArmorSet) -> dict:
    bones = {bone: channels for bone, channels in armor.anim.items()}
    return {"format_version": "1.8.0", "animations": {f"animation.kingdomomnitrix.armor.{name}.idle": {
        "loop": True, "animation_length": LOOP, "bones": bones}}}


def outputs() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for name, make in SETS.items():
        armor = make()
        geo, color, mask = build(name, armor.pal, armor.bones, armor.subbones)
        files[GEO_DIR / f"{name}.geo.json"] = (json.dumps(geo, indent=1) + "\n").encode()
        files[ANIM_DIR / f"{name}.animation.json"] = (json.dumps(animation(name, armor), indent=1) + "\n").encode()
        for path, image in ((TEX_DIR / f"{name}.png", color), (TEX_DIR / f"{name}_glowmask.png", mask)):
            buffer = io.BytesIO()
            image.save(buffer, "PNG", optimize=True)
            files[path] = buffer.getvalue()
    return files


ITEM_TEX = ASSETS / "textures" / "item"
ITEM_MODELS = ASSETS / "models" / "item"
LANG = ASSETS / "lang"
PIECES = {"helmet": ("armorHead",), "chestplate": ("armorBody", "armorRightArm", "armorLeftArm"),
          "leggings": ("armorRightLeg", "armorLeftLeg"), "boots": ("armorRightBoot", "armorLeftBoot")}
NAMES = {"taydenite": ("Taydenit", "Taydenite"), "galvan": ("Galvan-Biotech", "Galvan Biotech"),
         "celestial": ("Celestialsapien", "Celestialsapien"), "magnaplate": ("Magnaplate", "Magnaplate"),
         "carbonox": ("Carbonox", "Carbonox"), "infernox": ("Infernox", "Infernox")}
PIECE_NAMES = {"helmet": ("Helm", "Helmet"), "chestplate": ("Brustpanzer", "Chestplate"), "leggings": ("Beinschutz", "Leggings"),
               "boots": ("Stiefel", "Boots")}
TOOLTIPS = {
    "taydenite": ("Diamondhead-Form: Kristallklingen, kreisende Taydenit-Splitter.", "Diamondhead form: crystal blades, orbiting taydenite shards."),
    "galvan": ("Upgrade-Form: Mechamorph-Biotech mit Schaltkreisen und Datenringen.", "Upgrade form: Mechamorph biotech with circuits and data rings."),
    "celestial": ("Alien-X-Form: Sternenhimmel, Heiligenschein und Lichtschwingen.", "Alien X form: starfield, halo and wings of light."),
    "magnaplate": ("Kuppelhelm, Riesenhandschuhe und Heli-Pack mit Rotor.", "Dome helmet, giant gauntlets and a heli-pack with rotor."),
    "carbonox": ("Megacorp-Vollpanzer mit Lombax-Ohren, Schubduesen und Armklingen.", "Megacorp full armor with Lombax ears, thrusters and arm blades."),
    "infernox": ("Hoellenfeuer-Generator, Glutrisse, Flammenkrone und Glut-Umhang.", "Hellfire generator, ember cracks, flame crown and ember cape."),
}


def icon(name: str, armor: ArmorSet, piece: str, geo: dict, tex_path: Path, work: Path) -> Image.Image:
    """Inventarbild aus dem 3D-Modell: nur die Bones des Teils, schraeg von vorn, auf 32x32 verkleinert."""
    keep = set(PIECES[piece])
    keep |= {bone for bone, (parent, _) in armor.subbones.items() if parent in keep}
    data = json.loads(json.dumps(geo))
    data["minecraft:geometry"][0]["bones"] = [b for b in data["minecraft:geometry"][0]["bones"] if b["name"] in keep]
    geo_path = work / f"{name}_{piece}.geo.json"
    geo_path.write_text(json.dumps(data))
    out = work / f"{name}_{piece}.png"
    subprocess.run([sys.executable, str(ROOT / "tools" / "render_geo.py"), str(out), f"{geo_path}:{tex_path}", "--views",
                    "three_quarter", "--scale", "6", "--background", "#FF00FF"], check=True, capture_output=True)
    img = np.asarray(Image.open(out).convert("RGBA")).copy()
    bg = (img[..., 0] > 240) & (img[..., 1] < 20) & (img[..., 2] > 240)
    img[bg] = 0
    img[:24, :] = 0
    img[:, :16] = 0
    ys, xs = np.nonzero(img[..., 3])
    crop = Image.fromarray(img[ys.min():ys.max() + 1, xs.min():xs.max() + 1], "RGBA")
    side = max(crop.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(crop, ((side - crop.width) // 2, (side - crop.height) // 2))
    small = square.resize((32, 32), Image.LANCZOS)
    arr = np.asarray(small).copy()
    arr[..., 3] = np.where(arr[..., 3] > 100, 255, 0)
    return Image.fromarray(arr, "RGBA")


def item_outputs() -> dict[Path, bytes]:
    """Inventarbilder, Item-Modelle und Uebersetzungen der 24 Ruestungsteile (ergaenzt die Sprachdateien)."""
    import tempfile
    files: dict[Path, bytes] = {}
    lang = {code: json.loads((LANG / f"{code}.json").read_text(encoding="utf-8")) for code in ("de_de", "en_us")}
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        for name, make in SETS.items():
            armor = make()
            geo, color, _ = build(name, armor.pal, armor.bones, armor.subbones)
            tex_path = work / f"{name}.png"
            color.save(tex_path)
            for piece in PIECES:
                item = f"{name}_{piece}"
                buffer = io.BytesIO()
                icon(name, armor, piece, geo, tex_path, work).save(buffer, "PNG", optimize=True)
                files[ITEM_TEX / f"{item}.png"] = buffer.getvalue()
                files[ITEM_MODELS / f"{item}.json"] = (json.dumps({"parent": "minecraft:item/generated", "textures": {
                    "layer0": f"kingdomomnitrix:item/{item}"}}, indent=2) + "\n").encode()
                lang["de_de"][f"item.kingdomomnitrix.{item}"] = f"{NAMES[name][0]}-{PIECE_NAMES[piece][0]}"
                lang["en_us"][f"item.kingdomomnitrix.{item}"] = f"{NAMES[name][1]} {PIECE_NAMES[piece][1]}"
            lang["de_de"][f"tooltip.kingdomomnitrix.suit.{name}"] = TOOLTIPS[name][0]
            lang["en_us"][f"tooltip.kingdomomnitrix.suit.{name}"] = TOOLTIPS[name][1]
    lang["de_de"]["tooltip.kingdomomnitrix.suit.line.ben10"] = "Ben-10-Linie: grosser Energieschild, wenig Grundpanzerung"
    lang["en_us"]["tooltip.kingdomomnitrix.suit.line.ben10"] = "Ben 10 line: large energy shield, light base armor"
    lang["de_de"]["tooltip.kingdomomnitrix.suit.line.ratchet"] = "Ratchet-Linie: schwere Panzerung, kleiner Schild"
    lang["en_us"]["tooltip.kingdomomnitrix.suit.line.ratchet"] = "Ratchet line: heavy armor, small shield"
    lang["de_de"]["itemGroup.kingdomomnitrix.suits"] = "Kingdom Omnitrix: Endgame-Ruestungen"
    lang["en_us"]["itemGroup.kingdomomnitrix.suits"] = "Kingdom Omnitrix: Endgame Armor"
    for code, data in lang.items():
        files[LANG / f"{code}.json"] = (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode()
    return files


def preview(out: Path, names: list[str], labels: list[str]) -> None:
    """Vorschau wie im Spiel bei Daemmerung: Grundbild abgedunkelt, Leuchtmaske voll hell plus Schein (Bloom)."""
    from PIL import ImageFilter
    tmp = out.parent / f".{out.stem}_glowtex"
    tmp.mkdir(exist_ok=True)
    specs, glow_specs = [], []
    for name in names:
        tex = Image.open(TEX_DIR / f"{name}.png").convert("RGBA")
        mask = np.asarray(Image.open(TEX_DIR / f"{name}_glowmask.png").convert("RGBA"))
        only = np.zeros_like(mask)
        only[..., 3] = np.asarray(tex)[..., 3]
        only[mask[..., 3] > 0] = mask[mask[..., 3] > 0]
        Image.fromarray(only, "RGBA").save(tmp / f"{name}.png")
        specs.append(f"{GEO_DIR / (name + '.geo.json')}:{TEX_DIR / (name + '.png')}")
        glow_specs.append(f"{GEO_DIR / (name + '.geo.json')}:{tmp / (name + '.png')}")
    common = ["--views", "front,three_quarter,back", "--scale", "9", *sum((["--label", label] for label in labels), [])]
    render = [sys.executable, str(ROOT / "tools" / "render_geo.py")]
    subprocess.run(render + [str(tmp / "base.png"), *specs, "--background", "#2A3038", *common], check=True, capture_output=True)
    subprocess.run(render + [str(tmp / "glow.png"), *glow_specs, "--background", "#000000", *common], check=True, capture_output=True)
    base = np.asarray(Image.open(tmp / "base.png").convert("RGB")).astype(np.float64)
    glow_img = Image.open(tmp / "glow.png").convert("RGB")
    glow = np.asarray(glow_img).astype(np.float64)
    bloom = np.asarray(glow_img.filter(ImageFilter.GaussianBlur(6))).astype(np.float64)
    lit = glow.max(axis=2, keepdims=True) > 8
    result = np.where(lit, glow, base * 0.8) + bloom * 1.4
    Image.fromarray(np.clip(result, 0, 255).astype(np.uint8)).save(out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    parser.add_argument("--preview", type=Path, help="Vorschaubild aller Sets schreiben")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    files = outputs()
    files.update(item_outputs())
    if args.check:
        stale = [p for p, data in files.items() if not p.exists() or p.read_bytes() != data]
        for path in stale:
            LOG.error("veraltet: %s", path.relative_to(ROOT))
        LOG.info("Ruestungsmodelle: %d Dateien, %d veraltet", len(files), len(stale))
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    LOG.info("Ruestungsmodelle geschrieben: %d Sets, %d Dateien", len(SETS), len(files))
    if args.preview:
        preview(args.preview, list(SETS), list(SETS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
