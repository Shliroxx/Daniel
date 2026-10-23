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
DENSITY = 2  # Texturpixel je Modelleinheit

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
            if w >= 4 and h >= 4:
                rgb[0, :] = light
                rgb[:, 0] = light * 0.96
                rgb[-1, :] = dark
                rgb[:, -1] = dark * 1.05
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


def build(name: str, pal: Palette, bones: dict[str, list[Cube]]) -> tuple[dict, Image.Image, Image.Image]:
    atlas = Atlas(256)
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
    for bone, pivot in BONES.items():
        cubes = []
        for cube in bones.get(bone, []):
            entry = {"origin": cube.origin, "size": cube.size,
                     "uv": {f: {"uv": [cube.uv[f][0], cube.uv[f][1]], "uv_size": [cube.uv[f][2], cube.uv[f][3]]}
                            for f in FACES}}
            if cube.rotation:
                entry["rotation"] = cube.rotation
                entry["pivot"] = cube.pivot or [cube.origin[0] + cube.size[0] / 2, cube.origin[1], cube.origin[2] + cube.size[2] / 2]
            cubes.append(entry)
        geo_bones.append({"name": bone, "pivot": pivot, "cubes": cubes})
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
    return Cube([x0, cube.origin[1], cube.origin[2]], list(cube.size), cube.mat, lines, edge, rot, piv, cube.seams, cube.tint)


def C(o, s, mat="plate", lines=(), edge=(), rot=None, pivot=None, seams=True, tint=None) -> Cube:
    """Quader. 'rot' wird so angegeben: x negativ = Oberkante kippt nach hinten, z positiv = Oberkante kippt nach -x
    (auf der rechten Koerperseite also nach aussen); Bedrock speichert z mit umgekehrtem Vorzeichen."""
    rotation = [rot[0], rot[1], -rot[2]] if rot else None
    return Cube(list(o), list(s), mat, list(lines), tuple(edge), rotation, list(pivot) if pivot else None, seams, tint)


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
        C((-9.4, 20.2, -3.0), (5.4, 3.8, 6.0), tint=plate),                                          # Schulter
        C((-9.6, 10.6, -3.2), (5.8, 6.4, 6.4), tint=plate),                                          # Riesenhandschuh
        C((-9.8, 16.4, -3.4), (6.2, 1.2, 6.8), "trim", tint=dark),                                   # Stulpe
        C((-9.2, 10.2, -2.6), (5.0, 1.0, 5.2), "trim", tint=dark),                                   # Fingerkante
    ]
    leg = [
        C((-4.1, 4.4, -2.3), (4.2, 7.8, 4.6), "suit", tint=suit),
        C((-4.3, 8.2, -2.7), (4.6, 3.8, 5.4), tint=plate),                                           # Oberschenkel
        C((-3.9, 4.6, -3.4), (3.8, 3.2, 1.2), tint=plate),                                           # Knie
    ]
    boot = [
        C((-4.7, -0.2, -3.4), (5.4, 5.2, 6.6), tint=plate),                                          # Stiefel
        C((-4.8, -0.4, -3.7), (5.6, 1.0, 7.2), "trim", tint=sole),                                   # Sohle
        C((-4.5, -0.4, -4.9), (5.0, 2.6, 1.6), tint=plate),                                          # Kappe
        C((-4.8, 4.6, -3.6), (5.6, 1.0, 7.0), "trim", tint=dark),                                    # Schaftrand
    ]
    return {"armorHead": [], "armorBody": body, "armorRightArm": arm, "armorRightLeg": leg, "armorRightBoot": boot}


def magnaplate() -> tuple[Palette, dict]:
    """Magnaplate (R&C, Stufe 1): schwarzer Glanz-Kuppelhelm mit weissem Visier, Marineblau, Silberrahmen,
    blauer Kern und blaue Leuchtstreifen auf den riesigen Handschuhen."""
    pal = Palette("1E2A4A", "0A1020", "6A7A9A", "1A2238", "B8C0C8", "3A9AFF", "E0F2FF", "3A9AFF", "D0D8E4")
    b = rc_frame("1A2238", "1E2A4A", "101216", "5A5E66")
    b["armorHead"] = dome(5.3, 23.0, 9.4, "1A1A20") + [
        C((-3.8, 25.2, -5.6), (7.6, 3.8, 0.5), "gloss", tint="D0D8E4"),                              # weisses Visier
        C((-2.8, 29.8, -5.4), (5.6, 0.5, 0.4), "glow"),                                              # Lichtleiste
        C((-0.6, 33.2, -2.0), (1.2, 0.4, 4.0), "glow"),                                              # Scheitel-Logo
        C((-5.4, 24.0, -1.6), (0.6, 3.2, 3.2), "trim"), C((4.8, 24.0, -1.6), (0.6, 3.2, 3.2), "trim"),   # Ohrkapseln
    ]
    b["armorBody"] += [
        C((-4.0, 16.8, -3.5), (8.0, 6.4, 0.6), "trim", seams=False),                                 # Silberrahmen
        C((-1.8, 18.6, -4.1), (3.6, 3.6, 0.6), "gem"),                                               # blauer Kern
        C((-2.4, 18.0, -3.9), (4.8, 4.8, 0.3), "trim", tint="101216"),
    ]
    b["armorRightArm"][2].lines += [("h", 0.25, ALL4), ("h", 0.5, ALL4), ("h", 0.75, ALL4)]
    b["armorRightArm"][2].tint = "141418"
    b["armorRightLeg"][2].mat, b["armorRightLeg"][2].tint = "gloss", "1A1A20"
    b["armorRightBoot"][0].tint = "141418"
    b["armorRightBoot"][2].tint = "141418"
    return pal, symmetric(b)


def carbonox() -> tuple[Palette, dict]:
    """Carbonox (R&C, Stufe 2): grauer Vollpanzer mit riesigen Lombax-Ohren, Gitter-Gesicht mit roten Augen,
    roter Kern, rote Kniescheinwerfer und gepanzerter Schwanz."""
    pal = Palette("6A7078", "2A2E34", "B8C0C8", "1C1E22", "3A3E44", "FF2A2A", "FFC0B0", "FF2A2A", "FF2A2A")
    b = rc_frame("1C1E22", "6A7078", "2A2E34", "1A1A1C")
    b["armorHead"] = [
        C((-5.0, 23.2, -5.0), (10.0, 8.4, 10.0)),                                                    # Helmschale
        C((-4.2, 31.6, -4.2), (8.4, 1.4, 8.4)),                                                      # Haube
        C((-4.2, 23.8, -5.7), (8.4, 6.4, 0.8), "trim", tint="1A1C20"),                               # Gesichtsgitter
        C((-3.2, 24.4, -6.0), (6.4, 0.5, 0.3), "trim", tint="5A5E66"),
        C((-3.2, 25.4, -6.0), (6.4, 0.5, 0.3), "trim", tint="5A5E66"),
        C((-0.6, 31.0, -5.4), (1.2, 2.6, 1.4), "glow"),                                              # Stirnlicht
    ]
    b["armorHead"] += pair(C((-3.4, 27.6, -6.0), (2.4, 1.2, 0.4), "glow"))                           # rote Augen
    b["armorHead"] += pair(C((3.6, 29.6, -1.6), (1.8, 8.6, 3.2), rot=(18, 0, -28), pivot=(4.5, 29.6, 0.0)))   # Lombax-Ohren
    b["armorHead"] += pair(C((3.9, 34.6, -1.0), (1.2, 4.2, 2.0), "trim", rot=(18, 0, -28), pivot=(4.5, 29.6, 0.0), tint="2A2E34"))
    b["armorBody"] += [
        C((-1.4, 18.8, -4.0), (2.8, 2.8, 0.6), "gem"),                                               # roter Kern
        C((-3.6, 13.0, -3.0), (7.2, 0.8, 0.4), "trim", lines=[("h", 0.5, FRONT)]),
        C((-3.6, 14.4, -3.0), (7.2, 0.8, 0.4), "trim", lines=[("h", 0.5, FRONT)]),
    ]
    b["armorBody"] += pair(C((-4.6, 17.0, -3.6), (4.4, 6.2, 1.0), rot=(0, 12, 0), pivot=(-0.2, 17.0, -3.6)))
    b["armorBody"] += chain((0.0, 12.6, 2.6), [(4.2, 1.8, 35), (3.6, 1.6, 55), (3.0, 1.4, 75), (2.0, 1.2, 85)], tip="glow")
    b["armorRightArm"][1].lines.append(("h", 0.5, ALL4))
    b["armorRightArm"] += [C((-9.9, 12.6, -1.0), (0.4, 2.4, 2.0), "glow")]
    b["armorRightLeg"] += [C((-2.8, 5.4, -3.7), (1.6, 1.6, 0.4), "glow")]                            # Kniescheinwerfer
    b["armorRightBoot"] += [C((-3.4, 2.8, -5.0), (2.4, 0.8, 0.3), "glow")]
    return pal, symmetric(b)


def infernox() -> tuple[Palette, dict]:
    """Infernox (R&C, Endstufe): Bronze und Gold mit spitzen Lombax-Ohren, gluehenden Augenschlitzen, Flammen-Emblem,
    gluehenden Schienbein-Flammen, Hoellenfeuer-Generator auf dem Ruecken und gluehender Schwanzspitze."""
    pal = Palette("9A5A1E", "3A1A08", "E8A04A", "4A1A10", "D8A03A", "FF8A1A", "FFF0A0", "FFB02A", "FFC02A")
    b = rc_frame("3A140C", "9A5A1E", "4A2A10", "2A1408")
    b["armorHead"] = [
        C((-5.0, 23.2, -5.0), (10.0, 8.4, 10.0)),
        C((-0.6, 31.4, -5.0), (1.2, 1.8, 9.0), "trim"),                                              # Goldkamm
        C((-1.8, 24.0, -6.3), (3.6, 2.2, 0.6), "suit", tint="1A0A06", lines=[("v", 0.33, FRONT), ("v", 0.66, FRONT)]),  # Maul
    ]
    b["armorHead"] += pair(C((-4.8, 24.0, -5.9), (4.8, 6.6, 1.0), rot=(0, -14, 0), pivot=(0.0, 24.0, -5.9)))   # V-Gesicht
    b["armorHead"] += pair(C((-3.8, 27.4, -6.6), (2.8, 1.0, 0.4), "glow", rot=(0, 0, 14), pivot=(-2.4, 27.9, -6.4)))  # Augen
    b["armorHead"] += pair(C((3.4, 29.4, -1.8), (2.0, 10.0, 3.4), "trim", rot=(14, 0, -20), pivot=(4.4, 29.4, 0.0)))  # Ohren
    b["armorHead"] += pair(C((3.8, 30.4, -2.3), (1.2, 7.6, 0.6), "suit", rot=(14, 0, -20), pivot=(4.4, 29.4, 0.0), tint="2A0A04"))
    b["armorBody"] += [
        C((-1.9, 18.0, -4.1), (3.8, 3.8, 0.7), "gem", rot=(0, 0, 45), pivot=(0.0, 19.9, -4.1)),     # Flammen-Emblem
        C((-2.8, 17.1, -3.7), (5.6, 5.6, 0.4), "trim", rot=(0, 0, 45), pivot=(0.0, 19.9, -3.7)),
        C((-3.4, 13.0, -2.9), (6.8, 0.6, 0.4), "glow"), C((-3.0, 14.2, -2.9), (6.0, 0.6, 0.4), "glow"),
        C((-2.6, 14.6, 3.0), (5.2, 7.0, 2.6), "trim", tint="5A3410"),                                # Hoellenfeuer-Generator
        C((-1.4, 16.2, 5.5), (2.8, 3.6, 0.4), "glow"),
    ]
    b["armorBody"] += pair(C((-4.6, 17.0, -3.6), (4.4, 6.2, 1.0), rot=(0, 12, 0), pivot=(-0.2, 17.0, -3.6), lines=[("h", 0.85, FRONT)]))
    b["armorBody"] += chain((0.0, 12.6, 2.8), [(4.4, 2.0, 30), (3.8, 1.8, 50), (3.2, 1.6, 70), (2.4, 1.4, 85)], tip="glow")
    b["armorRightArm"] += [C((-9.8, 20.6, -2.6), (5.8, 2.6, 5.2), "trim", rot=(0, 0, 12), pivot=(-6.7, 21.9, 0.0)),   # Goldschulter
                           C((-9.9, 11.4, -1.4), (0.4, 4.6, 2.8), "glow")]
    b["armorRightArm"][2].lines += [("h", 0.3, ALL4), ("h", 0.7, ALL4)]
    b["armorRightLeg"][2].mat = "trim"
    b["armorRightBoot"] += [C((-3.6, 2.6, -5.1), (1.0, 4.6, 0.4), "glow"), C((-1.8, 2.6, -5.1), (1.0, 3.6, 0.4), "glow"),  # Flammen
                            C((-4.4, -0.4, -5.0), (4.8, 1.4, 1.4), "trim")]
    return pal, symmetric(b)


# --- Ben 10: Ruestung in der Form der Aliens (mit Omnitrix-Symbol auf der Brust) -------------------------------------

def ben_uniform(white: str, black: str) -> list[Cube]:
    """Zweifarbiger Alien-Anzug wie bei Diamondhead/Upgrade: eine Haelfte hell, eine dunkel, Omnitrix-Symbol mittig."""
    return [C((-4.4, 12.4, -2.4), (4.4, 12.0, 4.8), "suit", tint=white), C((0.0, 12.4, -2.4), (4.4, 12.0, 4.8), "suit", tint=black),
            C((-2.0, 19.0, -3.2), (4.0, 4.0, 0.6), "omni"), C((-2.4, 18.6, -3.0), (4.8, 4.8, 0.4), "trim", tint="1A1A1A")]


def taydenite() -> tuple[Palette, dict]:
    """Taydenit (Ben 10, Stufe 1) in Diamondhead-Form: Kristallkopf mit grosser Diamantklinge, Kristallbuendel auf
    Schultern und Ruecken, Kristallklingen an den Unterarmen, schwarz-weisser Anzug mit Omnitrix-Symbol."""
    pal = Palette("2ACA8A", "0E4A34", "B8FFE0", "1A1A1A", "E8F0EC", "3AFFB0", "F0FFF8", "5AF0C0", "3AFFB0")
    b: dict[str, list[Cube]] = {"armorHead": [], "armorBody": [], "armorRightArm": [], "armorRightLeg": [], "armorRightBoot": []}
    b["armorHead"] = [
        C((-4.8, 23.4, -4.8), (9.6, 7.2, 9.6)),                                                      # Kristallschaedel
        C((-3.4, 26.4, -5.3), (6.8, 2.2, 0.5), "suit", tint="06140E"),                               # Gesichtsschlitz
        C((-2.4, 30.4, -2.8), (4.8, 3.2, 5.6), rot=(-10, 0, 0), pivot=(0, 30.4, 0)),                 # Diamantklinge (Basis)
        C((-1.4, 33.2, -2.0), (2.8, 5.4, 3.6), "gem", rot=(-16, 0, 0), pivot=(0, 30.4, 0)),          # Diamantklinge (Spitze)
        C((-0.7, 38.2, -1.0), (1.4, 2.4, 1.8), "gem", rot=(-22, 0, 0), pivot=(0, 30.4, 0)),
    ]
    b["armorHead"] += pair(C((-2.8, 27.0, -5.5), (1.8, 0.8, 0.3), "glow"))                           # gruene Augen
    b["armorHead"] += pair(C((3.6, 24.0, -4.4), (1.8, 6.0, 5.0), rot=(0, -18, 8), pivot=(4.5, 24.0, -2.0)))   # Wangenfacetten
    b["armorBody"] = ben_uniform("E8ECEA", "141414") + [
        C((-4.6, 22.4, -3.0), (9.2, 1.6, 6.0), "trim", tint="141414"),
        C((-4.6, 11.4, -2.9), (9.2, 1.6, 5.8), "trim", tint="141414", lines=[("h", 0.5, ALL4)]),
    ]
    b["armorBody"] += shards(0, 21.0, 2.6, [(-2.6, 0, 1.8, 7.0, -30, 22), (2.6, 0, 1.8, 7.0, -30, -22), (0, 0.4, 2.2, 8.6, -24, 0),
                                            (-1.4, 0.2, 1.4, 5.0, -40, 10), (1.4, 0.2, 1.4, 5.0, -40, -10)])
    b["armorRightArm"] = [
        C((-8.2, 16.4, -2.2), (4.0, 7.6, 4.4), "suit", tint="E8ECEA"),
        C((-9.6, 19.8, -3.2), (5.6, 4.2, 6.4)),                                                      # Kristallschulter
        C((-9.4, 11.0, -3.0), (5.4, 6.2, 6.0)),                                                      # Kristallhandschuh
        C((-9.4, 8.2, -0.5), (0.8, 8.0, 1.2), "gem", rot=(0, 0, 8), pivot=(-9.0, 16.0, 0.0)),        # Armklinge
    ]
    b["armorRightArm"] += shards(-7.4, 23.8, 0, [(0, 0, 2.2, 7.6, 0, 20), (-1.6, -1.8, 1.6, 5.4, -20, 34), (-1.6, 1.8, 1.6, 5.0, 22, 32),
                                                 (1.2, 0.6, 1.4, 3.6, 8, -10), (0.4, -2.0, 1.2, 3.0, -30, 8)])
    b["armorRightLeg"] = [C((-4.1, 4.4, -2.3), (4.2, 7.8, 4.6), "suit", tint="E8ECEA"),
                          C((-4.3, 8.6, -2.7), (4.6, 3.4, 5.4)),
                          C((-3.6, 4.6, -3.4), (3.2, 3.0, 1.2), "gem")]
    b["armorRightLeg"] += shards(-2.0, 6.8, -3.6, [(0, 0, 1.2, 3.0, -55, 0)])
    b["armorRightBoot"] = [C((-4.6, -0.2, -3.2), (5.2, 4.8, 6.2)), C((-4.4, -0.4, -4.6), (4.8, 2.2, 1.6), "gem")]
    b = symmetric(b)
    for cube in b["armorLeftArm"] + b["armorLeftLeg"]:
        if cube.mat == "suit":
            cube.tint = "141414"
    return pal, b


def galvan() -> tuple[Palette, dict]:
    """Galvan-Biotech (Ben 10, Stufe 2) in Upgrade-Form: glatter schwarzer Kuppelkopf mit einem grossen gruenen
    Kreisauge, gruene Schaltkreise ueber dem ganzen Koerper, silberweisser Grundanzug, Omnitrix-Symbol."""
    pal = Palette("121416", "050607", "4A5058", "D8DCE0", "1A1C20", "3AFF5A", "E8FFE0", "3AFF5A", "3AFF5A")
    circuit = [("v", 0.3, ("north", "south")), ("v", 0.7, ("north", "south"))]
    b: dict[str, list[Cube]] = {}
    b["armorHead"] = dome(5.0, 23.2, 9.2, "121416", layers=5) + [
        C((-2.9, 25.0, -5.5), (5.8, 5.8, 0.5), "trim", tint="050607"),                               # Augenring
        C((-2.3, 25.6, -5.9), (4.6, 4.6, 0.5), "gem"),                                               # Kreisauge
        C((-0.7, 27.2, -6.1), (1.4, 1.4, 0.3), "trim", tint="050607"),                               # Pupille
        C((-5.1, 26.0, -3.0), (0.4, 0.6, 6.0), "glow"), C((4.7, 26.0, -3.0), (0.4, 0.6, 6.0), "glow"),
        C((-0.3, 30.6, -4.8), (0.6, 2.2, 9.6), "glow"),                                              # Scheitelbahn
    ]
    b["armorHead"] += pair(C((2.6, 32.2, -0.8), (1.0, 2.4, 1.0), "trim", tint="121416", rot=(0, 0, -20), pivot=(3.1, 32.2, -0.3)))
    b["armorBody"] = [C((-4.4, 12.4, -2.4), (8.8, 12.0, 4.8), "suit"),
                      C((-4.6, 20.0, -3.0), (9.2, 4.2, 6.0), lines=circuit),                         # Brustband
                      C((-4.6, 12.0, -3.0), (9.2, 2.4, 6.0), lines=circuit),                         # Hueftband
                      C((-1.6, 14.4, -3.0), (3.2, 5.6, 0.6), lines=[("v", 0.5, FRONT)]),
                      C((-2.0, 16.0, -3.6), (4.0, 4.0, 0.6), "omni")]
    b["armorRightArm"] = [C((-8.2, 12.4, -2.2), (4.0, 11.6, 4.4), "suit"),
                          C((-9.2, 20.2, -2.8), (5.2, 3.8, 5.6), lines=circuit),
                          C((-9.0, 15.4, -2.6), (4.8, 2.0, 5.2), lines=circuit),
                          C((-9.4, 10.8, -3.0), (5.4, 3.6, 6.0), lines=circuit),
                          C((-9.6, 12.2, -1.0), (0.4, 1.0, 2.0), "glow")]
    b["armorRightLeg"] = [C((-4.1, 4.4, -2.3), (4.2, 7.8, 4.6), "suit"),
                          C((-4.3, 8.8, -2.7), (4.6, 2.6, 5.4), lines=circuit),
                          C((-4.3, 4.8, -2.7), (4.6, 2.0, 5.4), lines=circuit)]
    b["armorRightBoot"] = [C((-4.5, -0.2, -3.0), (5.0, 4.4, 6.0), lines=circuit), C((-4.3, -0.4, -4.2), (4.6, 2.0, 1.4))]
    return pal, symmetric(b)


def celestial() -> tuple[Palette, dict]:
    """Celestialsapien (Ben 10, Endstufe) in Alien-X-Form: Sternenhimmel-Koerper, zwei grosse weisse Hoerner,
    gruene Augen, Omnitrix-Symbol, Heiligenschein und Lichtschwingen."""
    pal = Palette("0A0618", "000000", "E8E8FF", "06040E", "F4F4FF", "C8E8FF", "FFFFFF", "FFFFFF", "FFFFFF", cosmic=True)
    b: dict[str, list[Cube]] = {}
    b["armorHead"] = [C((-4.7, 23.4, -4.7), (9.4, 8.6, 9.4))]
    b["armorHead"] += pair(C((-3.2, 27.0, -5.0), (2.2, 1.4, 0.4), "glow"))                           # Augen
    b["armorHead"] += shards(2.6, 31.4, -1.4, [(0, 0, 2.0, 6.4, -14, -12)], "trim")                  # Hoerner
    b["armorHead"] += shards(-2.6, 31.4, -1.4, [(0, 0, 2.0, 6.4, -14, 12)], "trim")
    b["armorHead"] += shards(3.2, 37.4, -2.8, [(0, 0, 1.4, 3.6, -28, 4)], "trim")
    b["armorHead"] += shards(-3.2, 37.4, -2.8, [(0, 0, 1.4, 3.6, -28, -4)], "trim")
    b["armorHead"] += halo(30.0, 6.2, 6.4)
    b["armorBody"] = [C((-4.6, 12.2, -2.6), (9.2, 12.4, 5.2)),
                      C((-2.0, 18.4, -3.2), (4.0, 4.0, 0.6), "omni"),
                      C((-4.8, 22.6, -3.0), (9.6, 1.4, 6.0), "trim"),
                      C((-4.8, 11.6, -3.0), (9.6, 1.0, 6.0), "trim")]
    for side in (-1, 1):
        for length, angle in ((15.0, 28), (13.0, 50), (10.0, 72), (7.0, 94)):
            b["armorBody"].append(C((side * 1.5 - 0.5, 20.0, 3.0), (1.0, length, 0.6), "glow", rot=(-12, 0, -side * angle),
                                    pivot=(side * 1.5, 20.0, 3.0)))
    b["armorRightArm"] = [C((-8.4, 11.2, -2.4), (4.4, 12.6, 4.8)),
                          C((-9.4, 20.4, -3.0), (5.4, 3.6, 6.0), "trim"),
                          C((-9.0, 11.0, -2.8), (5.0, 1.0, 5.6), "trim")]
    b["armorRightLeg"] = [C((-4.2, 3.6, -2.4), (4.4, 8.6, 4.8)), C((-4.0, 5.0, -3.0), (3.6, 2.4, 0.8), "trim")]
    b["armorRightBoot"] = [C((-4.4, -0.2, -2.8), (4.8, 4.0, 5.6)), C((-4.5, -0.4, -3.2), (5.0, 0.8, 6.4), "trim")]
    return pal, symmetric(b)


SETS = {"taydenite": taydenite, "galvan": galvan, "celestial": celestial,
        "magnaplate": magnaplate, "carbonox": carbonox, "infernox": infernox}


def outputs() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for name, make in SETS.items():
        pal, bones = make()
        geo, color, mask = build(name, pal, bones)
        files[GEO_DIR / f"{name}.geo.json"] = (json.dumps(geo, indent=1) + "\n").encode()
        for path, image in ((TEX_DIR / f"{name}.png", color), (TEX_DIR / f"{name}_glowmask.png", mask)):
            buffer = io.BytesIO()
            image.save(buffer, "PNG", optimize=True)
            files[path] = buffer.getvalue()
    return files


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    parser.add_argument("--preview", type=Path, help="Vorschaubild aller Sets schreiben")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    files = outputs()
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
        specs = []
        for name in SETS:
            specs.append(f"{GEO_DIR / (name + '.geo.json')}:{TEX_DIR / (name + '.png')}")
        cmd = [sys.executable, str(ROOT / "tools" / "render_geo.py"), str(args.preview), *specs, "--views", "front,three_quarter,back",
               "--scale", "6", *sum((["--label", n] for n in SETS), [])]
        subprocess.run(cmd, check=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
