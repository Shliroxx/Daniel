#!/usr/bin/env python3
"""Erzeugt die GeckoLib-Koerper der Aliens: Geometrie (.geo.json), Animationen (.animation.json),
Texturen (.png) und Leuchtmasken (_glowmask.png) unter assets/kingdomomnitrix/{geo,animations,textures}/entity/alien/.

Zwei Bauweisen:
  * DETAILLIERT (Heatblast): eigenes Skelett mit Ellbogen/Knien, Gesteinsplatten, Flammenkopf aus mehreren Knochen,
    Materialien (Gestein mit gluehenden Rissen, Magma, Flamme) und Leuchtmaske. UVs werden automatisch gepackt.
  * EINFACH (XLR8, Vierarm, Diamondhead, Grey Matter): Spieler-Layout, Status PLACEHOLDER bis zu ihrem Rework.

Jedes Alien bekommt denselben Animationssatz, den der Client-Renderer erwartet:
  idle, walk, run, jump, fall, attack, ability_0, ability_1, ability_2, hit, transform, revert

Knochennamen sind fest: root, body, head, right_arm, left_arm, right_leg, left_leg (+ optional right_forearm,
left_forearm, right_shin, left_shin und alien-spezifische Extras). "head" dreht sich mit dem Blick.

Aufruf aus dem Ordner minecraft-mod/:

    python tools/generate_alien_models.py                  # alles neu schreiben
    python tools/generate_alien_models.py --only heatblast # nur ein Alien
    python tools/generate_alien_models.py --check          # nur pruefen, ob alles vorhanden ist
    python tools/generate_alien_models.py --preview DIR    # zusaetzlich vergroesserte Textur-Vorschau

Benoetigt Pillow (pip install pillow).
"""
from __future__ import annotations

import argparse
import json
import logging
import math
import random
import sys
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("alien_models")
ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
ANIMATIONS = ("idle", "walk", "run", "jump", "fall", "attack", "ability_0", "ability_1", "ability_2", "hit",
              "transform", "revert")

Color = tuple[int, int, int]


def rgb(value: str) -> Color:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def clamp(value: float, low: float = 0.0, high: float = 255.0) -> float:
    return max(low, min(high, value))


def mix(a: Color, b: Color, t: float) -> Color:
    t = clamp(t, 0.0, 1.0)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def scale(color: Color, factor: float) -> Color:
    return tuple(int(clamp(c * factor)) for c in color)


# --- Modell-Bausteine -----------------------------------------------------------------------------

@dataclass
class Part:
    """Wuerfel der detaillierten Bauweise: Material statt Farbe, UV optional (automatisch gepackt)."""
    origin: tuple[float, float, float]
    size: tuple[int, int, int]
    material: str
    uv: tuple[int, int] | None = None   # None = automatisch packen
    detail: str | None = None           # Front-Detail: eyes, visor, four_eyes, big_eyes, omnitrix, heat_face, …
    mirror: bool = False
    inflate: float = 0.0
    rotation: tuple[float, float, float] | None = None
    pivot: tuple[float, float, float] | None = None

    def to_json(self) -> dict:
        data: dict = {"origin": list(self.origin), "size": list(self.size), "uv": list(self.uv or (0, 0))}
        if self.mirror:
            data["mirror"] = True
        if self.inflate:
            data["inflate"] = self.inflate
        if self.rotation:
            data["rotation"] = list(self.rotation)
            data["pivot"] = list(self.pivot or self.origin)
        return data


@dataclass
class Cube:
    """Wuerfel der einfachen Bauweise (Farbe + Front-Detail, feste UV). Von den Generatoren fuer Herzlose, Boss,
    NPCs und Schiff importiert — Signatur und Bemalung bleiben deshalb unveraendert."""
    origin: tuple[float, float, float]
    size: tuple[int, int, int]
    uv: tuple[int, int]
    color: str
    front_detail: str | None = None  # "eyes", "visor", "four_eyes", "big_eyes", "omnitrix"
    mirror: bool = False

    def to_json(self) -> dict:
        data = {"origin": list(self.origin), "size": list(self.size), "uv": list(self.uv)}
        if self.mirror:
            data["mirror"] = True
        return data


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple[float, float, float]
    cubes: list = field(default_factory=list)  # Part oder Cube
    rotation: tuple[float, float, float] | None = None

    def to_json(self) -> dict:
        data: dict = {"name": self.name, "pivot": list(self.pivot)}
        if self.parent:
            data["parent"] = self.parent
        if self.rotation:
            data["rotation"] = list(self.rotation)
        if self.cubes:
            data["cubes"] = [c.to_json() for c in self.cubes]
        return data


@dataclass
class Alien:
    name: str
    texture_size: tuple[int, int]
    bones: list[Bone]
    accent: str
    style: str = "normal"   # normal | heat | fast | heavy | small (bestimmt Animations-Charakter)
    glow: bool = False      # Leuchtmaske schreiben
    arms: tuple[str, str] | None = None  # Vorlagen fuer die Ego-Sicht-Arme (rechts, links), Material-Schreibweise
    render_scale: float = 1.0  # Darstellungsgroesse (Trefferbox bleibt), z. B. fuer Vorlagen in Uebergroesse
    density: int = 1        # Pixel pro Modelleinheit in der PNG; die .geo.json nennt weiter die einfache Groesse,
                            # GeckoLib rechnet UVs normiert — so entsteht doppelt feine Textur ohne Geometrieaenderung


# --- Materialien ----------------------------------------------------------------------------------
# Ein Material malt eine Wuerfelseite. Rueckgabe je Pixel: (Farbe, Alpha, leuchtet)

FACE_SHADE = {"up": 1.12, "down": 0.62, "east": 0.84, "north": 1.0, "west": 0.84, "south": 0.78}

MAGMA_HOT = rgb("#FFE27A")
MAGMA_MID = rgb("#FF9A1F")
MAGMA_LOW = rgb("#E04A0C")
ROCK = {"rock": rgb("#5B2716"), "rock_dark": rgb("#3A170E"), "rock_light": rgb("#7A3820"), "face": rgb("#2A0F09")}
FLAME_TOP = rgb("#FF4A0A")
FLAME_MID = rgb("#FFA21A")
FLAME_CORE = rgb("#FFD84A")
OMNITRIX = rgb("#4DFF3A")


class Noise:
    """Glatte Wertrausch-Funktion (2 Oktaven), deterministisch pro Seed."""

    def __init__(self, seed: int) -> None:
        self.seed = seed

    def _hash(self, x: int, y: int) -> float:
        h = zlib.crc32(f"{self.seed}:{x}:{y}".encode()) & 0xFFFF
        return h / 0xFFFF

    def _smooth(self, x: float, y: float) -> float:
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self._hash(x0, y0) * (1 - fx) + self._hash(x0 + 1, y0) * fx
        b = self._hash(x0, y0 + 1) * (1 - fx) + self._hash(x0 + 1, y0 + 1) * fx
        return a * (1 - fy) + b * fy

    def __call__(self, x: float, y: float) -> float:
        return 0.65 * self._smooth(x / 3.0, y / 3.0) + 0.35 * self._smooth(x / 1.3, y / 1.3)


@dataclass
class Face:
    name: str
    x: int
    y: int
    w: int
    h: int
    d: int = 1   # Texturdichte: Pixel pro Modelleinheit


class Canvas:
    def __init__(self, size: tuple[int, int]) -> None:
        self.color = Image.new("RGBA", size, (0, 0, 0, 0))
        self.glow = Image.new("RGBA", size, (0, 0, 0, 0))

    def put(self, x: int, y: int, color: Color, alpha: int = 255, glow: bool = False) -> None:
        if not (0 <= x < self.color.width and 0 <= y < self.color.height):
            return
        self.color.putpixel((x, y), color + (alpha,))
        self.glow.putpixel((x, y), (color + (alpha,)) if glow and alpha else (0, 0, 0, 0))


def cracks(face: Face, rng: random.Random, density: float) -> set[tuple[int, int]]:
    """Zufaellige Risslinien (Random Walk vom Rand nach innen)."""
    result: set[tuple[int, int]] = set()
    if face.w * face.h < 10:
        return result
    count = max(1, int(round((face.w + face.h) * density / 6)))
    for _ in range(count):
        x, y = rng.randrange(face.w), rng.choice((0, face.h - 1)) if rng.random() < 0.6 else rng.randrange(face.h)
        dx, dy = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)))
        for _ in range(rng.randint(3, max(4, (face.w + face.h) // 2))):
            result.add((x, y))
            if rng.random() < 0.35:
                dx, dy = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1), (1, -1)))
            x, y = x + dx, y + dy
            if not (0 <= x < face.w and 0 <= y < face.h):
                break
    return result


def paint_rock(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    base = ROCK[cube.material]
    shade = FACE_SHADE[face.name]
    density = {"rock": 0.55, "rock_dark": 0.35, "rock_light": 0.45, "face": 0.0}[cube.material]
    lines = cracks(face, rng, density) if face.name not in ("down",) and face.w * face.h >= 16 and density else set()
    for py in range(face.h):
        for px in range(face.w):
            n = noise(face.x + px, face.y + py)
            color = scale(base, shade * (0.78 + 0.45 * n))
            edge = px in (0, face.w - 1) or py in (0, face.h - 1)
            if face.w > 2 and face.h > 2:
                if py == 0 or px == 0:
                    color = scale(color, 1.28)      # Fase oben/links fängt Licht
                elif py == face.h - 1 or px == face.w - 1:
                    color = scale(color, 0.62)      # Fase unten/rechts im Schatten
            if face.name == "up" and not edge and n > 0.62:
                color = scale(color, 1.18)          # Lichtkante oben
            if (px, py) in lines:
                heat = rng.random()
                canvas.put(face.x + px, face.y + py, mix(MAGMA_LOW, MAGMA_HOT, 0.35 + 0.65 * heat), glow=True)
                continue
            canvas.put(face.x + px, face.y + py, color)


def paint_magma(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    for py in range(face.h):
        for px in range(face.w):
            n = noise(face.x + px * 1.7, face.y + py * 1.7)
            core = 1.0 - abs((px + 0.5) / face.w - 0.5) * 1.2
            t = clamp(0.25 + 0.55 * n + 0.3 * core, 0.0, 1.0)
            color = mix(MAGMA_LOW, MAGMA_MID, t * 1.6) if t < 0.62 else mix(MAGMA_MID, MAGMA_HOT, (t - 0.62) / 0.38)
            if n < 0.22:
                color = scale(ROCK["rock_dark"], 1.1)   # erkaltete Kruste
                canvas.put(face.x + px, face.y + py, color)
                continue
            canvas.put(face.x + px, face.y + py, color, glow=True)


FLAME_Y = (26.0, 43.0)   # Hoehenbereich der Flammensaeule (Kern unten, Spitze oben)


def paint_flame(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    low, high = FLAME_Y
    for py in range(face.h):
        for px in range(face.w):
            if face.name == "up":
                y = cube.origin[1] + cube.size[1]
            elif face.name == "down":
                y = cube.origin[1]
            else:
                y = cube.origin[1] + cube.size[1] - (py + 0.5)
            heat = 1.0 - clamp((y - low) / (high - low), 0.0, 1.0)            # 1 = Kern (unten)
            tongue = noise(face.x + px * 3.0, face.y * 0.2 + py * 0.45)        # senkrechte Zungen
            t = clamp(heat * 0.95 + (tongue - 0.5) * 0.45, 0.0, 1.0)
            color = mix(FLAME_TOP, FLAME_MID, t * 1.8) if t < 0.55 else mix(FLAME_MID, FLAME_CORE, (t - 0.55) / 0.45)
            alpha = 255
            if face.name not in ("up", "down"):
                # gezackte Oberkante: Zungen ragen unterschiedlich hoch
                if py == 0 and tongue < 0.5:
                    alpha = 0
                elif py == 1 and tongue < 0.28:
                    alpha = 0
            canvas.put(face.x + px, face.y + py, color, alpha=alpha, glow=True)


def paint_flat(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    base = rgb(cube.material.split(":", 1)[1])
    for py in range(face.h):
        for px in range(face.w):
            jitter = rng.randint(-10, 10)
            color = tuple(int(clamp(c * FACE_SHADE[face.name] + jitter)) for c in base)
            canvas.put(face.x + px, face.y + py, color)


# XLR8: schwarzer Glanzpanzer mit blauer Lichtkante, blauer Anzug mit Nahtlinien, leuchtendes Visier
ARMOR = rgb("#15171F")
ARMOR_RIM = rgb("#3D7BFF")
SUIT = rgb("#1F5BD6")
SUIT_SEAM = rgb("#123A8F")
VISOR_LOW = rgb("#1C6DFF")
VISOR_HIGH = rgb("#9FE9FF")
WHEEL = rgb("#2A2D36")


def paint_armor(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    shade = FACE_SHADE[face.name]
    for py in range(face.h):
        for px in range(face.w):
            n = noise(face.x + px, face.y + py)
            color = scale(ARMOR, shade * (0.85 + 0.35 * n))
            if face.w > 2 and face.h > 2:
                if py == 0 or px == 0:
                    color = mix(color, ARMOR_RIM, 0.55 if face.name != "down" else 0.2)   # Lichtkante
                elif py == face.h - 1 or px == face.w - 1:
                    color = scale(color, 0.6)
            if face.name in ("up", "north") and (px + py) % 7 == 0 and n > 0.6:
                color = mix(color, (200, 220, 255), 0.35)                                  # Glanzpunkt
            canvas.put(face.x + px, face.y + py, color)


def paint_suit(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    shade = FACE_SHADE[face.name]
    for py in range(face.h):
        for px in range(face.w):
            n = noise(face.x + px, face.y + py)
            color = scale(SUIT, shade * (0.88 + 0.25 * n))
            if face.name not in ("up", "down") and face.w >= 4 and px in (1, face.w - 2):
                color = SUIT_SEAM                                                         # Nahtlinie
            canvas.put(face.x + px, face.y + py, color)


def paint_visor(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    for py in range(face.h):
        for px in range(face.w):
            t = 1.0 - (py + 0.5) / max(1, face.h)
            color = mix(VISOR_LOW, VISOR_HIGH, t * 0.8 + 0.2 * noise(face.x + px, face.y + py))
            if face.name == "north" and py == 0 and px in (1, 2):
                color = (235, 250, 255)                                                   # Spiegelung
            canvas.put(face.x + px, face.y + py, color, glow=True)


def paint_stripe(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    """Schwanzsegment: schwarz mit blauem Ring am vorderen Ende (Seiten laufen entlang z)."""
    shade = FACE_SHADE[face.name]
    for py in range(face.h):
        for px in range(face.w):
            along = px if face.name in ("east", "west") else py if face.name in ("up", "down") else -1
            ring = along in (0, 1) if face.name in ("east", "up") else along in (face.w - 2, face.w - 1) \
                if face.name == "west" else along in (face.h - 2, face.h - 1) if face.name == "down" else False
            color = scale(SUIT, shade) if ring else scale(ARMOR, shade * (0.9 + 0.3 * noise(face.x + px, face.y + py)))
            canvas.put(face.x + px, face.y + py, color)


def paint_wheel(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    shade = FACE_SHADE[face.name]
    for py in range(face.h):
        for px in range(face.w):
            color = scale(WHEEL, shade)
            if face.name in ("east", "west"):
                cx, cy = (face.w - 1) / 2, (face.h - 1) / 2
                r = math.hypot(px - cx, py - cy)
                if r < 0.8:
                    color = VISOR_LOW                                                     # Nabe
                    canvas.put(face.x + px, face.y + py, color, glow=True)
                    continue
                color = scale(color, 1.3 if r > 1.2 else 0.8)
            elif py % 2 == 0:
                color = scale(color, 1.35)                                                # Lauffläche
            canvas.put(face.x + px, face.y + py, color)


# --- Serien-Design (Stilvorgabe SANTIQ: saubere Farbflaechen, Serien-Kostuem) ----------------------
# Flaechen mit 3-Ton-Pixel-Dither statt Rauschen; Muster ueber Modellkoordinaten, damit sie ueber
# Wuerfelgrenzen weiterlaufen. Material-Schreibweise: "clean:#rrggbb", "lava", "fur", "crystal", "split",
# "panel", "shirt", "flame".

PYRO_SOURCE = ("#FFE49E", "#FFD567", "#DF7D4D", "#9E303B", "#7C2539", "#511527")   # Palette der Vorlage
CLEAN_SHADE = {"up": 1.06, "down": 0.74, "east": 0.9, "north": 1.0, "west": 0.9, "south": 0.86}
BADGE_K = rgb("#141414")
BADGE_W = rgb("#F2F2F2")


def dither(seed: int, x: float, y: float) -> float:
    """1.0 / heller / dunkler Pixel (70 / 20 / 10 %) — die weiche Koernung der Vorlagen ohne Rauschen."""
    h = (zlib.crc32(f"{seed}:{x:.1f}:{y:.1f}".encode()) & 0xFFFF) / 0xFFFF
    return 1.08 if h < 0.2 else 0.9 if h > 0.9 else 1.0


def model_xy(face: Face, cube: Part, px: int, py: int) -> tuple[float, float, float]:
    """Ungefaehre Modellkoordinate (x, y, z) eines Texturpixels — fuer durchgehende Muster."""
    ox, oy, oz = cube.origin
    w, h, d = cube.size
    k = face.d
    px, py = (px + 0.5) / k - 0.5, (py + 0.5) / k - 0.5
    if face.name == "north":
        return ox + w - px - 0.5, oy + h - py - 0.5, oz
    if face.name == "south":
        return ox + px + 0.5, oy + h - py - 0.5, oz + d
    if face.name == "east":
        return ox, oy + h - py - 0.5, oz + px + 0.5
    if face.name == "west":
        return ox + w, oy + h - py - 0.5, oz + d - px - 0.5
    if face.name == "up":
        return ox + w - px - 0.5, oy + h, oz + py + 0.5
    return ox + w - px - 0.5, oy, oz + py + 0.5


SHADE_CLUSTERS = Noise(4711)


def light_factor(face: Face, cube: Part, px: int, py: int, mx: float, my: float, mz: float) -> float:
    """Handgemalte Schattierung: Lichtverlauf von oben nach unten, Glanzkante oben, Kantenschatten unten und an den
    Seiten, dazu weiche Pixel-Cluster in drei Toenen (statt Zufallsrauschen)."""
    factor = CLEAN_SHADE[face.name]
    side = face.name in ("north", "south", "east", "west")
    if side:
        factor *= 1.07 - 0.18 * (py + 0.5) / max(1, face.h)
        if py == 0 and face.h > 2:
            factor *= 1.1                                 # Glanzkante
        elif py == face.h - 1 and face.h > 2:
            factor *= 0.82                                # Kontaktschatten unten
        if (px == 0 or px == face.w - 1) and face.w > 2:
            factor *= 0.92                                # weiche Seitenkante
    elif face.name == "up" and face.w > 2 and face.h > 2 and (px in (0, face.w - 1) or py in (0, face.h - 1)):
        factor *= 0.95
    cluster = SHADE_CLUSTERS(mx * 1.6 + mz * 1.1, my * 1.6)
    if cluster > 0.66:
        factor *= 1.06
    elif cluster < 0.32:
        factor *= 0.93
    return factor


def paint_design(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    mat = cube.material
    shade = CLEAN_SHADE[face.name]
    seed = zlib.crc32(mat.encode()) ^ (face.x * 131 + face.y)
    blobs = Noise(97)
    for py in range(face.h):
        for px in range(face.w):
            mx, my, mz = model_xy(face, cube, px, py)
            glow = False
            if mat.startswith("clean:"):
                base = rgb(mat[6:])
            elif mat == "lava":
                # dunkelrotes Gestein mit gelb-orangen Glutflecken (Flecken laufen ueber den ganzen Koerper)
                n = blobs(mx * 1.15 + mz * 0.7, my * 1.05)
                if n > 0.6:
                    base, glow = (rgb("#FFD24A") if n > 0.66 else rgb("#FFA22E")), True
                else:
                    base = rgb("#7A1D1D") if n > 0.38 else rgb("#5E1414")
            elif mat in ("pyro", "pyro_hot") and not PYRO:
                raise ValueError("PYRO-Palette nicht initialisiert")
            elif mat in ("pyro", "pyro_hot"):
                # Heatblast: dunkelrote Toene mit gelb-orangen, quer laufenden Glutbaendern (Palette aus der Vorlage)
                n = blobs(mx * 1.2 + mz * 0.9, my * 2.8)
                hot = 0.42 if mat == "pyro_hot" else 0.6
                if n > hot + 0.06:
                    base, glow = PYRO[0], True
                elif n > hot:
                    base = PYRO[1] if n > hot + 0.03 else PYRO[2]
                else:
                    t = SHADE_CLUSTERS(mx * 2.1, my * 2.1 + mz)
                    base = PYRO[3] if t > 0.6 else PYRO[5] if t < 0.32 else PYRO[4]
            elif mat == "pyro_flame":
                t = (my - cube.origin[1]) / max(1.0, cube.size[1])
                n = blobs(mx * 1.3 + mz, my * 0.9)
                base = PYRO[0] if n < 0.55 - t * 0.2 else PYRO[1] if n < 0.7 else PYRO[2]
            elif mat == "fur":
                # Vierarm-Arme: rotes Fell mit senkrechten Straehnen
                stripe = ((int(mx * 2 + mz * 3) % 5) == 0)
                base = rgb("#8E0E16") if stripe else rgb("#D21C24") if (int(my) + int(mx)) % 3 else rgb("#E9363C")
            elif mat == "crystal":
                # Diamondhead: hellgruene Facetten in schraegen Baendern
                band = (mx + my * 0.8 + mz) % 6
                base = rgb("#D4FFE6") if band < 0.9 else rgb("#9BF0BE") if band < 2.6 else rgb("#6FD69B")
            elif mat == "split":
                # Diamondhead-Anzug: rechte Koerperhaelfte schwarz, linke weiss
                base = rgb("#2A2A2E") if mx < 0 else rgb("#EDEDED")
            elif mat == "panel":
                # XLR8-Anzug: schwarz, weisses Brustpaneel vorn, grauer Guertelstreifen
                base = rgb("#232327")
                if face.name == "north" and abs(mx) < 2.6 and my > 14.0:
                    base = rgb("#EDEDED")
                if abs(my - 14.5) < 0.6 and face.name != "up":
                    base = rgb("#8F96A3")
            elif mat == "shirt":
                # Vierarm-Hemd: weiss mit schwarzem Mittelstreifen und schwarzem Saum
                base = rgb("#EEEEEE")
                if abs(mx) < 1.0 and face.name in ("north", "south"):
                    base = rgb("#1E1E22")
                if my < cube.origin[1] + 1.0:
                    base = rgb("#1E1E22")
            else:
                raise ValueError(f"unbekanntes Design-Material: {mat}")
            factor = light_factor(face, cube, px, py, mx, my, mz)
            color = scale(base, factor if not glow else min(1.0, factor + 0.08))
            canvas.put(face.x + px, face.y + py, color, glow=glow)


REFERENCE_DIR = Path(__file__).resolve().parent / "reference"


def ingame(color: Color, saturation: float = 1.2, value: float = 1.06) -> Color:
    """Vorlagenfarben sind ungeschattet gerendert; im Spiel dunkelt das Licht sie ab und Gelb wird beige.
    Sättigung und Helligkeit anheben, damit der Eindruck im Spiel der Vorlage entspricht."""
    import colorsys
    h, sat, val = colorsys.rgb_to_hsv(*(c / 255 for c in color))
    r, g, b = colorsys.hsv_to_rgb(h, min(1.0, sat * saturation), min(1.0, val * value))
    return int(r * 255), int(g * 255), int(b * 255)


def is_hot(color: Color) -> bool:
    """Gelbe und orange Glut leuchtet (Leuchtmaske) — sonst dunkelt das Seitenlicht von Minecraft das Blassgelb der
    Vorlage zu Khaki ab. Dunkelrot und Bordeaux bleiben normal beleuchtet."""
    return color[0] > 225 and color[1] > 140


def load_reference(spec: str) -> Image.Image:
    name, _, crop = spec.partition("@")
    path = REFERENCE_DIR / f"{name}.png"
    if not path.is_file():
        raise ValueError(f"Referenzbild fehlt: {path}")
    img = Image.open(path).convert("RGBA")
    if crop:
        x, y, w, h = (int(v) for v in crop.split(","))
        img = img.crop((x, y, x + w, y + h))
    return img


def paint_reference(canvas: Canvas, spec: str, face: Face) -> None:
    """Uebertraegt eine Referenz-Vorderseite (tools/reference/<name>.png) auf die Wuerfelseite.
    Schreibweise: "heatblast/torso" oder mit Ausschnitt "heatblast/leg_r@x,y,b,h". Transparente Pixel behalten das
    Material darunter. Leuchtende Farben (hell/gelb) kommen in die Leuchtmaske."""
    name, _, crop = spec.partition("@")
    path = REFERENCE_DIR / f"{name}.png"
    if not path.is_file():
        raise ValueError(f"Referenzbild fehlt: {path}")
    img = Image.open(path).convert("RGBA")
    if crop:
        x, y, w, h = (int(v) for v in crop.split(","))
        img = img.crop((x, y, x + w, y + h))
    img = img.resize((face.w, face.h), Image.NEAREST)
    for py in range(face.h):
        for px in range(face.w):
            r, g, b, a = img.getpixel((px, py))
            if a < 128:
                continue
            color = ingame((r, g, b))
            canvas.put(face.x + px, face.y + py, color, glow=is_hot(color))


def put_pattern(canvas: Canvas, fx: int, fy: int, rows: tuple[str, ...], palette: dict[str, Color],
                glow: str = "", scale: int = 1) -> None:
    """Zeichnet ein Pixelmuster ab (fx, fy); jedes Zeichen wird scale x scale Pixel gross. '.' bleibt unveraendert.
    Zeichen in `glow` leuchten."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == ".":
                continue
            for sy in range(scale):
                for sx in range(scale):
                    canvas.put(fx + dx * scale + sx, fy + dy * scale + sy, palette[ch], glow=ch in glow)


def paint_show_detail(canvas: Canvas, cube: Part, front: Face) -> bool:
    """Gesichter und Omnitrix-Logo der Serien-Designs. True, wenn das Detail bekannt war."""
    k = front.d
    fx, fy, w, h = front.x, front.y, front.w // k, front.h // k
    d = cube.detail
    if d == "badge":
        # Omnitrix-Logo: schwarzes Feld, weisses X (Sanduhr) — 5x5 mittig
        put_pattern(canvas, fx + ((w - 5) // 2) * k, fy + ((h - 5) // 2) * k,
                    ("KKKKK", "KWKWK", "KKWKK", "KWKWK", "KKKKK"), {"K": BADGE_K, "W": BADGE_W}, scale=k)
        return True
    if d == "badge6":
        # Omnitrix-Logo der Vorlage: schwarzer Rahmen, weisse Sanduhr (6x6 Pixel bei Dichte 2)
        put_pattern(canvas, fx, fy, ("KKKKKK", "KWWWWK", "KKWWKK", "KKWWKK", "KWWWWK", "KKKKKK"),
                    {"K": BADGE_K, "W": BADGE_W}, scale=max(1, k // 2))
        return True
    if d == "badge4":
        # Omnitrix-Logo als 8x8-Pixel-Feld: schwarzer Rahmen, weisses X
        put_pattern(canvas, fx, fy, ("KKKKKKKK", "KWKKKKWK", "KKWKKWKK", "KKKWWKKK", "KKKWWKKK", "KKWKKWKK",
                                     "KWKKKKWK", "KKKKKKKK"), {"K": BADGE_K, "W": BADGE_W}, scale=max(1, k // 2))
        return True
    if d == "pyro_face":
        # Flammengesicht: dunkelrote Brauen und Augenhoehlen, helle Pupillen, offener Mund mit Glut
        put_pattern(canvas, fx + ((w - 8) // 2) * k, fy + max(0, h - 8) * k, (
            "........",
            "RR....RR",
            "RWR..RWR",
            ".RR..RR.",
            "........",
            "..RRRR..",
            "..RYYR..",
            "...RR...")[-h:], {"R": rgb("#7A1414"), "W": rgb("#FFF6D0"), "Y": rgb("#FFB43A")}, glow="WY", scale=k)
        return True
    if d == "xlr8_face":
        # schwarzer Helm, tuerkise Gesichtsplatte mit gruenen Augen, schwarzer Mund
        put_pattern(canvas, fx, fy, (
            "KKKKKKKK",
            "KCCCCCCK",
            "GGCCCCGG",
            "GGCCCCGG",
            "KCCCCCCK",
            "KKCCCCKK",
            "KKKKKKKK",
            "KKKKKKKK")[:h], {"K": rgb("#18181C"), "C": rgb("#4FB6C9"), "G": rgb("#9BFF2E")}, glow="G", scale=k)
        return True
    if d == "tetra_face":
        # Vierarm: vier gelbe Augen, schwarzer Mund
        put_pattern(canvas, fx + ((w - 6) // 2) * k, fy, (
            "......",
            "YY..YY",
            "......",
            "YY..YY",
            "......",
            ".KKKK.")[:h], {"Y": rgb("#FFC21A"), "K": rgb("#1A0606")}, glow="Y", scale=k)
        return True
    if d == "petro_face":
        # Diamondhead: schmale gelbe Augen, kantige Wangen
        put_pattern(canvas, fx + ((w - 6) // 2) * k, fy, (
            "......",
            "......",
            "YY..YY",
            "KY..YK",
            "......",
            ".KKKK.")[:h], {"Y": rgb("#FFD21E"), "K": rgb("#2E6B4A")}, glow="Y", scale=k)
        return True
    if d == "galvan_eye":
        # Grey Matter: grosses gelbes Auge mit schwarzer Schlitzpupille und schwarzem Rahmen
        rows = ["K" * w] + ["K" + "Y" * (w - 2) + "K"] * (h - 2) + ["K" * w]
        mid = h // 2
        rows[mid] = "K" + "Y" + "K" * (w - 4) + "Y" + "K"
        put_pattern(canvas, fx, fy, tuple(rows), {"Y": rgb("#FFF4A3"), "K": rgb("#151515")}, glow="Y", scale=k)
        return True
    return False


def paint_ref_material(canvas: Canvas, face: Face, cube: Part, rng: random.Random, noise: Noise) -> None:
    """Material "ref:<name>[@x,y,b,h]": Vorderseite = Vorlage, Rueckseite = gespiegelt, Seiten = Randspalten der
    Vorlage gestreckt, oben/unten = oberste/unterste Zeile. Seiten werden leicht abgedunkelt (Tiefe)."""
    img = load_reference(cube.material[4:])
    w, h = img.size
    if face.name == "north":
        src = img
    elif face.name == "south":
        src = img.transpose(Image.FLIP_LEFT_RIGHT)
    elif face.name == "east":
        src = img.crop((0, 0, max(1, w // 3), h))
    elif face.name == "west":
        src = img.crop((w - max(1, w // 3), 0, w, h))
    elif face.name == "up":
        src = img.crop((0, 0, w, 1))
    else:
        src = img.crop((0, h - 1, w, h))
    src = src.resize((face.w, face.h), Image.NEAREST)
    shade = {"north": 1.0, "south": 0.9, "east": 0.88, "west": 0.88, "up": 1.04, "down": 0.75}[face.name]
    for py in range(face.h):
        for px in range(face.w):
            r, g, b, a = src.getpixel((px, py))
            if a < 128:
                r, g, b = PYRO_SOURCE_RGB[4] if PYRO_SOURCE_RGB else (120, 40, 50)
            base = ingame((r, g, b))
            hot = is_hot(base) and face.name != "down"
            canvas.put(face.x + px, face.y + py, base if hot else scale(base, shade), glow=hot)


def painter(material: str) -> Callable[[Canvas, Face, Cube, random.Random, Noise], None]:
    if material.startswith("ref:"):
        return paint_ref_material
    if material.startswith("flat:"):
        return paint_flat
    if material.startswith("clean:") or material in ("lava", "fur", "crystal", "split", "panel", "shirt", "pyro",
                                                     "pyro_hot", "pyro_flame"):
        return paint_design
    if material in ROCK:
        return paint_rock
    return {"magma": paint_magma, "flame": paint_flame, "armor": paint_armor, "suit": paint_suit, "visor": paint_visor,
            "stripe": paint_stripe, "wheel": paint_wheel}[material]


def paint_detail(canvas: Canvas, cube: Part, front: Face) -> None:
    fx, fy, w, h = front.x, front.y, front.w, front.h
    detail = cube.detail
    if detail == "heat_face":
        # dunkle Stirn, schraege leuchtende Augen, gluehender Mundspalt
        for px in range(w):
            canvas.put(fx + px, fy, scale(ROCK["face"], 0.7))
        eye = rgb("#FFF6C8")
        for ex in (1, w - 3):
            canvas.put(fx + ex, fy + 2, eye, glow=True)
            canvas.put(fx + ex + 1, fy + 2, eye, glow=True)
            canvas.put(fx + ex + (0 if ex == 1 else 1), fy + 1, scale(ROCK["face"], 0.5))   # Braue
        for px in range(2, w - 2):
            canvas.put(fx + px, fy + h - 2, mix(MAGMA_MID, MAGMA_HOT, 0.4), glow=True)
    elif detail == "omnitrix_badge":
        # 4x4-Abzeichen: dunkler Ring, gruene Sanduhr, heller Kern
        dark, green, core = rgb("#0E120E"), OMNITRIX, rgb("#E8FFE0")
        pattern = ("DGGD", "GCCG", "GCCG", "DGGD")
        for dy, row in enumerate(pattern):
            for dx, ch in enumerate(row):
                color = {"D": dark, "G": green, "C": core}[ch]
                canvas.put(fx + dx, fy + dy, color, glow=ch != "D")
    elif detail == "omnitrix":
        # Sanduhr-Symbol mit dunklem Ring, mittig auf der Brust
        cx, cy = fx + w // 2, fy + 2
        for dx in range(-2, 3):
            for dy in range(-1, 4):
                canvas.put(cx + dx, cy + dy, rgb("#101010"))
        for dx, dy in ((-1, 0), (1, 0), (0, 1), (-1, 2), (1, 2)):
            canvas.put(cx + dx, cy + dy, OMNITRIX, glow=True)
        canvas.put(cx, cy, rgb("#E8FFE0"), glow=True)
        canvas.put(cx, cy + 2, rgb("#E8FFE0"), glow=True)
    elif detail == "eyes":
        for ex in (1, w - 3):
            canvas.put(fx + ex, fy + h // 2 - 1, (255, 255, 255))
            canvas.put(fx + ex + 1, fy + h // 2 - 1, (30, 30, 30))
    elif detail == "visor":
        for x in range(fx, fx + w):
            canvas.put(x, fy + 2, (20, 20, 20))
            canvas.put(x, fy + 3, (90, 200, 255))
    elif detail == "four_eyes":
        for row in (2, 4):
            for ex in (1, w - 3):
                canvas.put(fx + ex, fy + row, (255, 230, 80))
                canvas.put(fx + ex + 1, fy + row, (255, 230, 80))
    elif detail == "big_eyes":
        for ex in (1, w - 4):
            for dx in range(3):
                for dy in range(3):
                    canvas.put(fx + ex + dx, fy + 3 + dy, (40, 255, 40) if (dx, dy) == (1, 1) else (10, 10, 10))
    elif detail == "omnitrix_dot":
        cx, cy = fx + w // 2 - 1, fy + 2
        for dx in range(2):
            for dy in range(2):
                canvas.put(cx + dx, cy + dy, (57, 255, 20))


def faces_of(cube: Part, k: int = 1) -> list[Face]:
    u, v = cube.uv
    w, h, d = cube.size
    return [
        Face("up", (u + d) * k, v * k, w * k, d * k, k),
        Face("down", (u + d + w) * k, v * k, w * k, d * k, k),
        Face("east", u * k, (v + d) * k, d * k, h * k, k),
        Face("north", (u + d) * k, (v + d) * k, w * k, h * k, k),
        Face("west", (u + d + w) * k, (v + d) * k, d * k, h * k, k),
        Face("south", (u + 2 * d + w) * k, (v + d) * k, w * k, h * k, k),
    ]


def pack_uvs(alien: Alien) -> None:
    """Regal-Packer fuer alle Wuerfel ohne feste UV. Wirft, wenn die Textur zu klein ist."""
    width, height = alien.texture_size
    cubes = [c for b in alien.bones for c in b.cubes if c.uv is None]
    cubes.sort(key=lambda c: -(c.size[2] + c.size[1]))
    x = y = shelf = 0
    for cube in cubes:
        w, h, d = cube.size
        fw, fh = 2 * (d + w), d + h
        if x + fw > width:
            x, y, shelf = 0, y + shelf, 0
        if y + fh > height or fw > width:
            raise ValueError(f"{alien.name}: Textur {width}x{height} zu klein fuer alle Wuerfel")
        cube.uv = (x, y)
        x += fw
        shelf = max(shelf, fh)


def build_arm_skin(alien: Alien) -> Image.Image:
    """Arm-Textur im Spieler-Skin-Layout (64x64-Raster, gemalt in 128x128 = doppelte Dichte) fuer die Ego-Sicht:
    rechter Arm bei UV (40,16), linker bei (32,48), jeweils 4x12x4. Der Client tauscht damit die Skin der Arme."""
    canvas = Canvas((128, 128))
    for material, uv in zip(alien.arms, ((40, 16), (32, 48))):
        part = Part((0, 0, 0), (4, 12, 4), material, uv=uv)
        for face in faces_of(part, 2):
            paint_ref_material(canvas, face, part, random.Random(0), Noise(0))
    return canvas.color


def build_textures(alien: Alien, seed: int) -> tuple[Image.Image, Image.Image]:
    """Farbtextur und Leuchtmaske der detaillierten Bauweise (Wuerfel vom Typ Part)."""
    k = alien.density
    canvas = Canvas((alien.texture_size[0] * k, alien.texture_size[1] * k))
    for b in alien.bones:
        for i, cube in enumerate(b.cubes):
            if cube.mirror and alien.style == "normal":
                continue  # einfache Aliens teilen die UV mit der gespiegelten Seite
            rng = random.Random(zlib.crc32(f"{seed}:{alien.name}:{b.name}:{i}".encode()))
            noise = Noise(zlib.crc32(f"{alien.name}:{b.name}:{i}".encode()))
            paint = painter(cube.material)
            faces = faces_of(cube, k)
            for face in faces:
                paint(canvas, face, cube, rng, noise)
            if cube.detail and cube.detail.startswith("ref:"):
                paint_reference(canvas, cube.detail[4:], faces[3])
            elif cube.detail and not paint_show_detail(canvas, cube, faces[3]):
                paint_detail(canvas, cube, faces[3])
    return canvas.color, canvas.glow


# --- Einfache Bauweise (Herzlose, Boss, NPCs, Schiff) ---------------------------------------------

def paint_cube(img: Image.Image, cube: Cube, rng: random.Random) -> None:
    u, v = cube.uv
    w, h, d = cube.size
    base = rgb(cube.color)
    width, height = img.size

    def put(x: int, y: int, color: tuple[int, int, int]) -> None:
        if 0 <= x < width and 0 <= y < height:
            img.putpixel((x, y), color + (255,))

    def shade(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
        jitter = rng.randint(-10, 10)
        return tuple(max(0, min(255, int(c * factor) + jitter)) for c in color)

    faces = [
        (u + d, v, w, d, 1.15),              # oben
        (u + d + w, v, w, d, 0.70),          # unten
        (u, v + d, d, h, 0.85),              # rechts
        (u + d, v + d, w, h, 1.00),          # vorne
        (u + d + w, v + d, d, h, 0.85),      # links
        (u + 2 * d + w, v + d, w, h, 0.80),  # hinten
    ]
    for fx, fy, fw, fh, factor in faces:
        for y in range(fy, fy + fh):
            for x in range(fx, fx + fw):
                put(x, y, shade(base, factor))

    front_x, front_y = u + d, v + d
    detail = cube.front_detail
    if detail == "eyes":
        for ex in (1, w - 3):
            put(front_x + ex, front_y + h // 2 - 1, (255, 255, 255))
            put(front_x + ex + 1, front_y + h // 2 - 1, (30, 30, 30))
    elif detail == "visor":
        for x in range(front_x, front_x + w):
            put(x, front_y + 2, (20, 20, 20))
            put(x, front_y + 3, (90, 200, 255))
    elif detail == "four_eyes":
        for row in (2, 4):
            for ex in (1, w - 3):
                put(front_x + ex, front_y + row, (255, 230, 80))
                put(front_x + ex + 1, front_y + row, (255, 230, 80))
    elif detail == "big_eyes":
        for ex in (1, w - 4):
            for dx in range(3):
                for dy in range(3):
                    put(front_x + ex + dx, front_y + 3 + dy, (40, 255, 40) if (dx, dy) == (1, 1) else (10, 10, 10))
    elif detail == "omnitrix":
        cx, cy = front_x + w // 2 - 1, front_y + 2
        for dx in range(2):
            for dy in range(2):
                put(cx + dx, cy + dy, (57, 255, 20))


def build_texture(alien: Alien, rng: random.Random) -> Image.Image:
    """Textur der einfachen Bauweise (Wuerfel vom Typ Cube) — gemeinsame API fuer die anderen Generatoren."""
    img = Image.new("RGBA", alien.texture_size, (0, 0, 0, 0))
    for b in alien.bones:
        for cube in b.cubes:
            if not cube.mirror:
                paint_cube(img, cube, rng)
    return img


# --- Aliens ---------------------------------------------------------------------------------------

def mirror_x(x: float, w: float, side: int) -> float:
    """Wuerfel-Ursprung fuer die linke Seite (+x) aus der rechten Seite (-x) spiegeln."""
    return x if side < 0 else -x - w


def limb_pair(arm: list[tuple], forearm: list[tuple], leg: list[tuple], shin: list[tuple], *,
              shoulder: tuple[float, float] = (5, 22), elbow: tuple[float, float] = (6, 17.5),
              hip: tuple[float, float] = (2, 12), knee: tuple[float, float] = (2, 6)) -> list[Bone]:
    """Arme und Beine mit Ellbogen/Knie fuer beide Seiten. Eintraege: (origin, size, material[, extra-kwargs])."""
    def parts(entries: list[tuple], side: int) -> list[Part]:
        result = []
        for entry in entries:
            (x, y, z), size, material = entry[0], entry[1], entry[2]
            extra = dict(entry[3]) if len(entry) > 3 else {}
            if "rotation" in extra and side > 0:
                rx, ry, rz = extra["rotation"]
                extra["rotation"] = (rx, -ry, -rz)
                px, py_, pz = extra["pivot"]
                extra["pivot"] = (-px, py_, pz)
            # Referenz-Vorderseiten nicht spiegeln (jede Seite hat ihre eigene Vorlage)
            mirrored = side > 0 and not str(extra.get("detail", "")).startswith("ref:")
            result.append(Part((mirror_x(x, size[0], side), y, z), size, material, mirror=mirrored, **extra))
        return result

    bones = []
    for side_name, side in (("right", -1), ("left", 1)):
        bones += [
            Bone(f"{side_name}_arm", "body", (shoulder[0] * side, shoulder[1], 0), parts(arm, side)),
            Bone(f"{side_name}_forearm", f"{side_name}_arm", (elbow[0] * side, elbow[1], 0), parts(forearm, side)),
            Bone(f"{side_name}_leg", "root", (hip[0] * side, hip[1], 0), parts(leg, side)),
            Bone(f"{side_name}_shin", f"{side_name}_leg", (knee[0] * side, knee[1], 0), parts(shin, side)),
        ]
    return bones


def finish(name: str, bones: list[Bone], accent: str, style: str, size: tuple[int, int] = (128, 64)) -> Alien:
    alien = Alien(name, size, bones, accent, style=style, glow=True)
    pack_uvs(alien)
    return alien


PYRO: tuple[Color, ...] = ()
PYRO_SOURCE_RGB: tuple[Color, ...] = tuple(rgb(c) for c in PYRO_SOURCE)


def flame_from_silhouette(spec: str, x_left: float, y_top: float, depth_base: int) -> list[Part]:
    """Baut die Kopfflamme Stufe fuer Stufe aus der Silhouette der Vorlage: je Einheit (2x2 Texel) gefuellt, wenn
    mindestens die Haelfte deckend ist; zusammenhaengende Laeufe einer Zeile werden zu einem Wuerfel, dessen
    Vorderseite den passenden Ausschnitt der Vorlage zeigt. Die Tiefe nimmt nach oben ab."""
    img = load_reference(spec)
    cols, rows = img.width // 2, img.height // 2
    parts: list[Part] = []
    for row in range(rows):
        filled = []
        for col in range(cols):
            alpha = sum(1 for dx in (0, 1) for dy in (0, 1) if img.getpixel((col * 2 + dx, row * 2 + dy))[3] > 128)
            filled.append(alpha >= 2)
        col = 0
        level = rows - 1 - row                                # 0 = direkt ueber dem Kopf
        depth = max(2, depth_base - level)
        while col < cols:
            if not filled[col]:
                col += 1
                continue
            start = col
            while col < cols and filled[col]:
                col += 1
            width = col - start
            x = x_left + start
            y = y_top - row - 1
            parts.append(Part((x, y, -depth / 2), (width, 1, depth), f"ref:{spec}@8,8,4,4",
                              detail=f"ref:{spec}@{start * 2},{row * 2},{width * 2},2"))
    return parts


def heatblast() -> Alien:
    """Heatblast (Pyronite), 1:1 nach der Alien-Evolution-Vorlage (Erlaubnis laut SANTIQ, Fanprojekt).

    Masse aus dem Vorlagen-Render (tools/sample_reference.py), Einheiten = 1/16 Block:
    Fuesse 2, Beine 13, Rumpf 17, Kopf 8, Flamme 6; Oberarm 10 (4 breit), Faust 10 (6 breit);
    Omnitrix-Logo 3x3 mittig oben auf der Brust; Seitenflammen links 4, rechts 3 hoch.
    Alle Flaechen aus den Vorlage-Pixeln (Material "ref:"), Kopfflamme aus der Silhouette gebaut."""
    global PYRO
    PYRO = tuple(ingame(rgb(c)) for c in PYRO_SOURCE)
    ref = "heatblast/"
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 14, 0), [
                 Part((-4.5, 14, -2.5), (9, 15, 5), "ref:" + ref + "torso"),
                 Part((-1.5, 24, -3), (3, 3, 1), "clean:#141414", detail="badge6"),
                 Part((-5, 28, -3.1), (10, 3, 6), "ref:" + ref + "collar"),
             ])]
    bones += limb_pair(
        arm=[((-9.5, 19, -2.5), (5, 10, 5), "ref:" + ref + "arm_r_upper")],
        forearm=[((-10, 9, -3), (6, 10, 6), "ref:" + ref + "arm_r_fist")],
        leg=[((-4.6, 8, -2), (4, 6, 4), "ref:" + ref + "leg_r@0,0,8,13")],
        shin=[((-4.6, 2, -2), (4, 6, 4), "ref:" + ref + "leg_r@0,13,8,13"),
              ((-5.1, 0, -3.5), (5, 2, 6), "ref:" + ref + "foot_r")],
        shoulder=(6.5, 28), elbow=(7, 19), hip=(2.6, 14), knee=(2.6, 8))
    # linke Seite: eigene Vorlagen (Arme/Beine/Fuss), nicht gespiegelt
    for bone in bones:
        if bone.name.startswith("left_"):
            for part in bone.cubes:
                part.material = part.material.replace("arm_r", "arm_l").replace("leg_r", "leg_l").replace("foot_r", "foot_l")
                part.mirror = False
        if bone.name in ("right_arm", "left_arm"):
            bone.rotation = (0, 0, 10 if bone.name == "right_arm" else -10)          # abgespreizt wie in der Vorlage
    bones += [
        Bone("head", "body", (0, 30, 0), [
            Part((-4.5, 30, -4.5), (9, 9, 9), "ref:" + ref + "head@1,9,3,5", detail="ref:" + ref + "head"),
            Part((-5.5, 35, -2.5), (1, 4, 5), "ref:" + ref + "head_wide@0,0,2,8"),               # Seitenflamme rechts
            Part((4.5, 35, -2.5), (1, 3, 5), "ref:" + ref + "head_wide@18,4,2,6"),               # Seitenflamme links
        ]),
        Bone("flame_base", "head", (0, 39, 0), flame_from_silhouette(ref + "flame", -5, 45, 7)),
    ]
    alien = Alien("heatblast", (96, 96), bones, "#FF6A00", style="heat", glow=True, density=2, render_scale=0.85,
                  arms=("ref:heatblast/arm_r_full", "ref:heatblast/arm_l_full"))
    pack_uvs(alien)
    return alien


def xlr8() -> Alien:
    """XLR8 (Kineceleran): schwarzer Anzug mit weissem Brustpaneel und Guertel, grosse Schulterpolster, tuerkise
    Arme mit Flossen und Streifen, Krallenhaende, Knieschuetzer, tuerkise Schienbeine mit Krallenfuessen, Helm mit
    Gesichtsplatte, Kamm und Nackenflosse, gestreifter Schwanz."""
    dark = "clean:#1C1C20"
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 12, 0), [
                 Part((-4, 12, -2), (8, 12, 4), "panel"),
                 Part((-2.5, 17.5, -2.6), (5, 5, 1), "clean:#141414", detail="badge"),
                 Part((-4.5, 13.5, -2.5), (9, 1, 5), "clean:#8F96A3"),                                    # Guertel
                 Part((-3.5, 15, 1.8), (7, 8, 1), "clean:#2A2A2E"),                                       # Rueckenplatte
             ], rotation=(6, 0, 0))]
    bones += limb_pair(
        arm=[((-8, 18, -2), (4, 6, 4), "clean:#56C4D6"),
             ((-9, 21, -3), (5, 3, 6), "clean:#2A2A2E"),                                                  # Schulterpolster
             ((-9.2, 20, -2.5), (1, 1, 5), "clean:#56C4D6")],                                             # Polsterkante
        forearm=[((-8, 12, -2), (4, 6, 4), "clean:#56C4D6"),
                 ((-8.25, 15, -2.25), (4, 1, 4), "clean:#3D9BB0", {"inflate": 0.1}),                     # Armstreifen
                 ((-9, 12.5, -0.5), (1, 5, 2), "clean:#3D9BB0"),                                          # Flosse aussen
                 ((-8.25, 9, -2.25), (4, 3, 4), dark, {"inflate": 0.25}),                                 # Hand
                 ((-8, 8, -2.5), (1, 1, 1), "clean:#D9E3E8"), ((-6, 8, -2.5), (1, 1, 1), "clean:#D9E3E8")],  # Krallen
        leg=[((-4, 6, -2), (4, 6, 4), "clean:#232327")],
        shin=[((-4, 1, -2), (4, 5, 4), "clean:#56C4D6"),
              ((-4.3, 4.5, -2.6), (4, 2, 1), "clean:#2A2A2E"),                                            # Knieschutz
              ((-4, 0, -3.5), (4, 1, 5), dark),                                                           # Fuss
              ((-4, 0, -4.5), (1, 1, 1), "clean:#D9E3E8"), ((-1, 0, -4.5), (1, 1, 1), "clean:#D9E3E8")],  # Zehenkrallen
        elbow=(6, 18))
    bones += [
        Bone("head", "body", (0, 24, 0), [
            Part((-4, 24, -4), (8, 8, 8), "clean:#1C1C20", detail="xlr8_face"),
            Part((-1, 32, -3), (2, 1, 6), "clean:#2A2A2E"),                                               # Helmkamm
            Part((-1, 28, 4), (2, 4, 2), "clean:#2A2A2E", rotation=(-20, 0, 0), pivot=(0, 30, 4)),       # Nackenflosse
            Part((-4.4, 25, -3), (1, 3, 4), "clean:#2A2A2E"), Part((3.4, 25, -3), (1, 3, 4), "clean:#2A2A2E"),  # Wangen
        ], rotation=(-6, 0, 0)),
        Bone("tail_1", "body", (0, 13, 2), [Part((-1.5, 11.5, 2), (3, 3, 5), "stripe")], rotation=(-12, 0, 0)),
        Bone("tail_2", "tail_1", (0, 13, 7), [Part((-1, 12, 7), (2, 2, 5), "stripe")], rotation=(-6, 0, 0)),
        Bone("tail_3", "tail_2", (0, 13, 12), [Part((-0.5, 12.5, 12), (1, 1, 5), "stripe")], rotation=(-4, 0, 0)),
    ]
    return finish("xlr8", bones, "#1E90FF", "fast")


def four_arms() -> Alien:
    """Vierarm (Tetramand): breiter Oberkoerper mit weissem Hemd, schwarzem Mittelstreifen und Guertel, schwarze
    Hose, vier rote Arme mit Fellbueschen, Muskelschultern, roter Kopf mit Stirnwulst und vier gelben Augen."""
    tuft = "clean:#9C1414"
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 12, 0), [
                 Part((-6, 12, -3), (12, 12, 6), "shirt"),
                 Part((-6.5, 22, -3.5), (13, 2, 7), "clean:#EEEEEE"),                                     # Schulterkante
                 Part((-6.5, 12, -3.5), (13, 1, 7), "clean:#1E1E22"),                                     # Guertel
                 Part((-1, 12, -3.8), (2, 1, 1), "clean:#C8A23A"),                                        # Schnalle
                 Part((-2, 24, -2), (4, 1, 4), "clean:#C8141E"),                                          # Nacken
             ])]
    bones += limb_pair(
        arm=[((-12, 17, -2.5), (5, 7, 5), "fur"),
             ((-12.5, 21, -3), (6, 3, 6), "fur"),                                                         # Muskelschulter
             ((-13, 18, -1), (1, 2, 2), tuft), ((-13, 20.5, 0.5), (1, 2, 2), tuft)],                      # Fellbueschel
        forearm=[((-12, 11, -2.5), (5, 6, 5), "fur"),
                 ((-13, 13, -1), (1, 2, 2), tuft), ((-12, 9, -2), (5, 2, 4), "clean:#3A0A0E")],
        leg=[((-5, 6, -2.5), (5, 6, 5), "clean:#1E1E22")],
        shin=[((-5, 2, -2.5), (5, 4, 5), "clean:#1E1E22"),
              ((-5, 0, -3.5), (5, 2, 6), "clean:#C8141E"),                                                # rote Fuesse
              ((-5, 0, -4), (1, 1, 1), "clean:#3A0A0E"), ((-2, 0, -4), (1, 1, 1), "clean:#3A0A0E")],     # Zehen
        shoulder=(7, 22), elbow=(9.5, 17), hip=(2.5, 12), knee=(2.5, 6))
    for side_name, side in (("right", -1), ("left", 1)):
        mirror = side > 0
        bones += [
            Bone(f"{side_name}_lower_arm", "body", (6 * side, 16.5, 1), [
                Part((mirror_x(-10, 4, side), 12, -1), (4, 5, 4), "fur", mirror=mirror),
                Part((mirror_x(-11, 1, side), 13, 0), (1, 2, 2), tuft, mirror=mirror)],
                rotation=(0, 0, 8 * -side)),
            Bone(f"{side_name}_lower_forearm", f"{side_name}_lower_arm", (8 * side, 12, 1), [
                Part((mirror_x(-10, 4, side), 7, -1), (4, 5, 4), "fur", mirror=mirror),
                Part((mirror_x(-10, 4, side), 5, -0.5), (4, 2, 3), "clean:#3A0A0E", mirror=mirror)]),
        ]
    for bone in bones:
        if bone.name in ("right_arm", "left_arm"):
            bone.rotation = (0, 0, 10 if bone.name == "right_arm" else -10)
    bones.append(Bone("head", "body", (0, 24, 0), [
        Part((-3, 24, -3), (6, 7, 6), "clean:#C8141E", detail="tetra_face"),
        Part((-3, 29.5, -3.4), (6, 1, 1), "clean:#9C1414"),                                              # Stirnwulst
        Part((-2, 31, -2), (4, 1, 4), "clean:#C8141E"),                                                   # Kopfwoelbung
    ]))
    return finish("four_arms", bones, "#C0392B", "heavy")


def diamondhead() -> Alien:
    """Diamondhead (Petrosapien): Anzug halb schwarz, halb weiss; Kristallkopf mit drei Spitzen, Schulter-
    Kristallbuendel aus je drei schraegen Spitzen, grosse Kristallarme mit aufgesetzten Facettenplatten."""
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 12, 0), [
                 Part((-4, 12, -2), (8, 12, 4), "split"),
                 Part((-1, 18.5, -2.6), (5, 5, 1), "clean:#141414", detail="badge"),
                 Part((-4.5, 13, -2.5), (9, 1, 5), "clean:#2A2A2E"),                                      # Guertel
             ])]
    bones += limb_pair(
        arm=[((-10, 16, -3), (6, 8, 6), "crystal"),
             ((-8.5, 23, -1.5), (3, 7, 3), "crystal", {"rotation": (0, 0, 22), "pivot": (-7, 23, 0)}),    # Schulterspitze
             ((-9, 22, -3), (2, 5, 2), "crystal", {"rotation": (-15, 0, 35), "pivot": (-8, 22, -2)}),
             ((-9, 22, 1), (2, 5, 2), "crystal", {"rotation": (15, 0, 30), "pivot": (-8, 22, 2)}),
             ((-10.5, 18, -2), (1, 4, 4), "crystal", {"rotation": (0, 45, 0), "pivot": (-10, 20, 0)})],    # Facettenplatte
        forearm=[((-10.5, 8, -3.5), (7, 8, 7), "crystal"),
                 ((-11, 10, -2), (1, 5, 4), "crystal", {"rotation": (0, 45, 0), "pivot": (-10.5, 12, 0)}),
                 ((-9, 7, -4), (4, 1, 1), "crystal")],
        leg=[((-4, 6, -2), (4, 6, 4), "split")],
        shin=[((-4, 0, -2), (4, 6, 4), "split"),
              ((-4.2, 0, -2.8), (4, 1, 1), "clean:#2A2A2E")],
        shoulder=(5, 22), elbow=(7, 16))
    bones.append(Bone("head", "body", (0, 24, 0), [
        Part((-3.5, 24, -3.5), (7, 7, 7), "crystal", detail="petro_face"),
        Part((-1, 31, -2), (2, 4, 4), "crystal"),                                                         # Mittelkamm
        Part((-3, 30.5, -1), (2, 3, 2), "crystal", rotation=(0, 0, 25), pivot=(-2, 31, 0)),               # Seitenspitzen
        Part((1, 30.5, -1), (2, 3, 2), "crystal", rotation=(0, 0, -25), pivot=(2, 31, 0)),
        Part((-3.9, 25, -2), (1, 4, 3), "crystal"), Part((2.9, 25, -2), (1, 4, 3), "crystal"),            # Wangenkanten
    ]))
    return finish("diamondhead", bones, "#2ECC71", "normal")


def grey_matter() -> Alien:
    """Grey Matter (Galvan): grosser grauer Kopf mit Stirnwulst und zwei grossen gelben Augen, weisser Anzug mit
    schwarzem Mittelstreifen und Kragen, schwarze Armbaender, grosse graue Haende mit Fingern, graue Fuesse."""
    grey = "clean:#A9B3B5"
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 12, 0), [
                 Part((-3.5, 12, -2), (7, 10, 4), "shirt"),
                 Part((-3, 21.5, -2.5), (6, 1, 5), "clean:#1E1E22"),                                      # Kragen
                 Part((-2.5, 16.5, -2.6), (5, 5, 1), "clean:#141414", detail="badge"),
             ])]
    bones += limb_pair(
        arm=[((-6.5, 16, -1.5), (3, 6, 3), "clean:#EEEEEE")],
        forearm=[((-6.5, 11, -1.5), (3, 5, 3), "clean:#EEEEEE"),
                 ((-6.5, 14, -1.5), (3, 1, 3), "clean:#1E1E22", {"inflate": 0.2}),                        # Armband
                 ((-7.5, 7, -2.5), (5, 5, 5), grey),                                                       # grosse Hand
                 ((-7.5, 6, -2.5), (1, 1, 1), grey), ((-5.5, 6, -2.5), (1, 1, 1), grey),
                 ((-3.5, 6, -2.5), (1, 1, 1), grey)],                                                      # Finger
        leg=[((-3.5, 6, -1.5), (3, 6, 3), "clean:#EEEEEE")],
        shin=[((-3.5, 1, -1.5), (3, 5, 3), "clean:#2A2A2E"),
              ((-4, 0, -2.5), (4, 1, 4), grey)],
        shoulder=(3.5, 21), elbow=(5, 16))
    bones += [
        Bone("head", "body", (0, 22, 0), [
            Part((-5, 22, -4), (10, 8, 8), grey),
            Part((-4, 30, -3), (8, 1, 6), grey),                                                          # Schaedelwoelbung
            Part((-4.5, 26, -4.4), (9, 1, 1), "clean:#8A9497"),                                           # Stirnwulst
            Part((-5.5, 27, -4.6), (4, 3, 1), "clean:#151515", detail="galvan_eye",
                 rotation=(0, 0, 12), pivot=(-3.5, 28.5, -4.6)),
            Part((1.5, 27, -4.6), (4, 3, 1), "clean:#151515", detail="galvan_eye",
                 rotation=(0, 0, -12), pivot=(3.5, 28.5, -4.6)),
            Part((-1.5, 23.5, -4.2), (3, 1, 1), "clean:#5A6366"),                                         # Mund
        ]),
    ]
    return finish("grey_matter", bones, "#95A5A6", "small")


def build_aliens() -> list[Alien]:
    return [heatblast(), xlr8(), four_arms(), diamondhead(), grey_matter()]


# --- Geometrie ------------------------------------------------------------------------------------

def build_geo(alien: Alien) -> dict:
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.kingdomomnitrix.{alien.name}",
                "texture_width": alien.texture_size[0],
                "texture_height": alien.texture_size[1],
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": [b.to_json() for b in alien.bones],
        }],
    }


# --- Animationen ----------------------------------------------------------------------------------
# Vorzeichen (GeckoLib/Blockbench): Arm/Bein nach vorn = negative X-Rotation.
FWD = -1

Track = dict[str, object]


def keys(*frames: tuple) -> Track:
    """frames: (zeit, [x, y, z]) oder (zeit, [x, y, z], easing)."""
    track: Track = {}
    for frame in frames:
        t, vec = frame[0], [round(float(c), 3) for c in frame[1]]
        track[f"{t:g}"] = {"vector": vec, "easing": frame[2]} if len(frame) > 2 else vec
    return track


def loop(length: float, amplitude: float, phase: float = 0.0, axis: int = 0, offset: float = 0.0, steps: int = 4) -> Track:
    """Sinus-Schwingung als Keyframes (geschlossene Schleife)."""
    frames = []
    for i in range(steps + 1):
        t = length * i / steps
        value = offset + amplitude * math.sin(2 * math.pi * (i / steps) + phase)
        vec = [0.0, 0.0, 0.0]
        vec[axis] = value
        frames.append((round(t, 3), vec, "easeinoutsine"))
    return keys(*frames)


def anim(length: float, bones: dict, loop_mode: bool | str = True) -> dict:
    return {"loop": loop_mode, "animation_length": length, "bones": bones}


def flame_tracks(length: float, intensity: float = 1.0) -> dict:
    """Flackern der Flammen-Knochen mit versetzten Phasen."""
    tracks = {}
    for i, name in enumerate(("flame_base", "flame_mid", "flame_tip", "flame_spike")):
        amp = (0.06 + 0.05 * i) * intensity
        steps = 4
        frames = []
        for k in range(steps + 1):
            p = 2 * math.pi * k / steps + i * 1.3
            frames.append((round(length * k / steps, 3),
                           [1 + amp * 0.5 * math.sin(p + 1), 1 + amp * math.sin(p), 1 + amp * 0.5 * math.sin(p + 2)],
                           "easeinoutsine"))
        tracks[name] = {"scale": keys(*frames), "rotation": loop(length, 3 + 3 * i * intensity, i * 0.9, axis=2)}
    return tracks


def build_animations(alien: Alien) -> dict:
    names = {b.name for b in alien.bones}
    heavy_limbs = {"right_forearm", "left_forearm"} <= names
    knees = {"right_shin", "left_shin"} <= names
    flames = "flame_base" in names
    tail = [n for n in ("tail_1", "tail_2", "tail_3") if n in names]
    fast = alien.style == "fast"
    A: dict[str, dict] = {}

    def tail_tracks(length: float, sway: float, lift: float = 0.0) -> dict:
        """Schwanz schwingt als Kette: jedes Glied etwas spaeter und staerker."""
        return {name: {"rotation": keys(*[(round(length * k / 4, 3),
                                             [lift * (i + 1) * 0.5, sway * (0.6 + 0.4 * i) * math.sin(2 * math.pi * k / 4 - i * 0.9), 0],
                                             "easeinoutsine") for k in range(5)])}
                for i, name in enumerate(tail)}

    # idle — Atmen, leicht schwebende Arme, Flammen
    idle = {
        "body": {"rotation": loop(2.4, 1.2, axis=0), "scale": keys((0, [1, 1, 1]), (1.2, [1.02, 1.01, 1.02], "easeinoutsine"),
                                                                    (2.4, [1, 1, 1], "easeinoutsine"))},
        "right_arm": {"rotation": loop(2.4, 2.0, axis=2, offset=6)},
        "left_arm": {"rotation": loop(2.4, -2.0, axis=2, offset=-6)},
        "head": {"rotation": loop(2.4, 1.5, 1.0, axis=0)},
    }
    if heavy_limbs:
        idle["right_forearm"] = {"rotation": keys((0, [FWD * 12, 0, 0]))}
        idle["left_forearm"] = {"rotation": keys((0, [FWD * 12, 0, 0]))}
    if flames:
        idle.update(flame_tracks(1.2))
    if tail:
        idle.update(tail_tracks(2.4, 6))
    if fast:
        # unruhig: wippt auf den Zehen, Kopf zuckt
        idle["root"] = {"position": loop(0.8, 0.25, axis=1, offset=0.25)}
        idle["head"] = {"rotation": keys((0, [0, 0, 0]), (0.9, [0, 12, 0], "easeoutquad"), (1.3, [0, 12, 0]),
                                         (1.6, [0, -8, 0], "easeoutquad"), (2.4, [0, 0, 0], "easeinoutsine"))}
    A["idle"] = anim(2.4, idle)

    # walk — Gegengleich, Knie beugen sich in der Schwungphase, Koerper wippt
    def gait(length: float, arm: float, leg: float, lean: float, bob: float) -> dict:
        g = {
            "root": {"position": loop(length, bob, -math.pi / 2, axis=1, offset=bob, steps=4) if bob else keys((0, [0, 0, 0]))},
            "body": {"rotation": keys((0, [FWD * -lean, 0, 0]))},
            "right_arm": {"rotation": loop(length, -arm, 0, axis=0)},
            "left_arm": {"rotation": loop(length, arm, 0, axis=0)},
            "right_leg": {"rotation": loop(length, leg, 0, axis=0)},
            "left_leg": {"rotation": loop(length, -leg, 0, axis=0)},
            "head": {"rotation": keys((0, [FWD * lean * 0.6, 0, 0]))},
        }
        if knees:
            # Knie beugt nur nach hinten (positive X = Unterschenkel nach hinten)
            g["right_shin"] = {"rotation": keys((0, [leg * 0.2, 0, 0]), (length * 0.25, [leg * 1.1, 0, 0], "easeinoutsine"),
                                                (length * 0.5, [leg * 0.1, 0, 0], "easeinoutsine"),
                                                (length, [leg * 0.2, 0, 0], "easeinoutsine"))}
            g["left_shin"] = {"rotation": keys((0, [leg * 0.1, 0, 0]), (length * 0.5, [leg * 0.2, 0, 0], "easeinoutsine"),
                                               (length * 0.75, [leg * 1.1, 0, 0], "easeinoutsine"),
                                               (length, [leg * 0.1, 0, 0], "easeinoutsine"))}
        if heavy_limbs:
            g["right_forearm"] = {"rotation": keys((0, [FWD * (15 + lean), 0, 0]))}
            g["left_forearm"] = {"rotation": keys((0, [FWD * (15 + lean), 0, 0]))}
        if flames:
            g.update(flame_tracks(length, 1.4))
            g["flame_base"]["rotation"] = keys((0, [lean * 0.8, 0, 0]))   # Flamme weht nach hinten
        if tail:
            g.update(tail_tracks(length, 8 if lean < 20 else 4, lift=-lean * 0.4))   # beim Sprint gestreckt
        return g

    if fast:
        A["walk"] = anim(0.6, gait(0.6, 22, 38, 8, 0.5))
        A["run"] = anim(0.3, gait(0.3, 0, 70, 32, 0.9))
        # Sprint: Arme flach nach hinten gelegt (aerodynamisch), Kopf nach vorn
        A["run"]["bones"]["right_arm"] = {"rotation": keys((0, [FWD * -55, 0, 8]))}
        A["run"]["bones"]["left_arm"] = {"rotation": keys((0, [FWD * -55, 0, -8]))}
    elif alien.style == "heavy":
        # schwer: langsamer, weit ausholender Gang mit deutlichem Auf und Ab
        A["walk"] = anim(1.1, gait(1.1, 24, 30, 4, 0.9))
        A["run"] = anim(0.7, gait(0.7, 40, 46, 12, 1.4))
    elif alien.style == "small":
        # klein und flink: kurze, schnelle Schritte
        A["walk"] = anim(0.5, gait(0.5, 30, 40, 4, 0.5))
        A["run"] = anim(0.32, gait(0.32, 50, 60, 14, 0.8))
    else:
        A["walk"] = anim(0.9, gait(0.9, 32, 34, 3, 0.4))
        A["run"] = anim(0.55, gait(0.55, 55, 52, 16, 0.8))
    if heavy_limbs:
        A["run"]["bones"]["right_forearm"] = {"rotation": keys((0, [FWD * 70, 0, 0]))}
        A["run"]["bones"]["left_forearm"] = {"rotation": keys((0, [FWD * 70, 0, 0]))}

    # jump / fall — Beine angezogen, Arme ausgebreitet
    air = {
        "right_arm": {"rotation": keys((0, [FWD * 20, 0, 25]))},
        "left_arm": {"rotation": keys((0, [FWD * 20, 0, -25]))},
        "right_leg": {"rotation": keys((0, [FWD * 30, 0, 0]))},
        "left_leg": {"rotation": keys((0, [FWD * 5, 0, 0]))},
    }
    if knees:
        air["right_shin"] = {"rotation": keys((0, [45, 0, 0]))}
        air["left_shin"] = {"rotation": keys((0, [20, 0, 0]))}
    if flames:
        air.update(flame_tracks(0.6, 1.8))
    A["jump"] = anim(0.6, air)
    fall = json.loads(json.dumps(air))
    fall["right_arm"] = {"rotation": loop(0.6, 6, axis=2, offset=55)}
    fall["left_arm"] = {"rotation": loop(0.6, -6, axis=2, offset=-55)}
    fall["right_leg"] = {"rotation": keys((0, [FWD * 10, 0, 0]))}
    A["fall"] = anim(0.6, fall)

    # attack — Ausholen, Schlag nach vorn mit Koerperdrehung, zurueck
    attack = {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.08, [0, 25, 0], "easeoutquad"), (0.16, [0, -20, 0], "easeinquad"),
                                  (0.35, [0, 0, 0], "easeinoutsine"))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.08, [FWD * -30, 0, 10], "easeoutquad"),
                                       (0.16, [FWD * 95, 0, 0], "easeinquad"), (0.35, [0, 0, 0], "easeinoutsine"))},
    }
    if heavy_limbs:
        attack["right_forearm"] = {"rotation": keys((0, [FWD * 12, 0, 0]), (0.08, [FWD * 80, 0, 0], "easeoutquad"),
                                                    (0.16, [FWD * 0, 0, 0], "easeinquad"), (0.35, [FWD * 12, 0, 0]))}
    A["attack"] = anim(0.35, attack, "hold_on_last_frame")

    # ability_0 — Feuerstoss: Arm schnellt nach vorn, Rueckstoss
    a0 = {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.1, [FWD * -6, 30, 0], "easeoutquad"), (0.2, [FWD * 8, -15, 0], "easeinquad"),
                                  (0.5, [0, 0, 0], "easeinoutsine"))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.1, [FWD * 60, 0, 30], "easeoutquad"), (0.2, [FWD * 90, 0, 0], "easeinquad"),
                                       (0.32, [FWD * 85, 0, 0]), (0.5, [0, 0, 0], "easeinoutsine"))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.2, [FWD * -25, 0, -10], "easeoutquad"), (0.5, [0, 0, 0], "easeinoutsine"))},
    }
    if heavy_limbs:
        a0["right_forearm"] = {"rotation": keys((0, [FWD * 12, 0, 0]), (0.1, [FWD * 60, 0, 0]), (0.2, [0, 0, 0], "easeinquad"),
                                                (0.5, [FWD * 12, 0, 0], "easeinoutsine"))}
    if flames:
        a0["flame_base"] = {"scale": keys((0, [1, 1, 1]), (0.2, [1.25, 1.4, 1.25], "easeoutback"), (0.5, [1, 1, 1], "easeinoutsine"))}
    A["ability_0"] = anim(0.5, a0, "hold_on_last_frame")

    # ability_1 — Feuerexplosion: Arme hoch, in die Hocke, Arme auseinander reissen
    a1 = {
        "root": {"position": keys((0, [0, 0, 0]), (0.25, [0, -2, 0], "easeoutquad"), (0.4, [0, 1, 0], "easeoutback"),
                                  (0.8, [0, 0, 0], "easeinoutsine"))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.25, [FWD * 20, 0, 0], "easeoutquad"), (0.4, [FWD * -15, 0, 0], "easeoutback"),
                                  (0.8, [0, 0, 0], "easeinoutsine"))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.25, [FWD * 150, 0, -20], "easeoutquad"), (0.4, [FWD * 20, 0, 95], "easeoutback"),
                                       (0.8, [0, 0, 0], "easeinoutsine"))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.25, [FWD * 150, 0, 20], "easeoutquad"), (0.4, [FWD * 20, 0, -95], "easeoutback"),
                                      (0.8, [0, 0, 0], "easeinoutsine"))},
    }
    if knees:
        a1["right_leg"] = {"rotation": keys((0, [0, 0, 0]), (0.25, [FWD * 30, 0, 8]), (0.4, [0, 0, 12], "easeoutback"), (0.8, [0, 0, 0]))}
        a1["left_leg"] = {"rotation": keys((0, [0, 0, 0]), (0.25, [FWD * 30, 0, -8]), (0.4, [0, 0, -12], "easeoutback"), (0.8, [0, 0, 0]))}
        a1["right_shin"] = {"rotation": keys((0, [0, 0, 0]), (0.25, [50, 0, 0]), (0.4, [0, 0, 0], "easeoutback"))}
        a1["left_shin"] = {"rotation": keys((0, [0, 0, 0]), (0.25, [50, 0, 0]), (0.4, [0, 0, 0], "easeoutback"))}
    if flames:
        a1["flame_base"] = {"scale": keys((0, [1, 1, 1]), (0.25, [0.85, 0.8, 0.85]), (0.4, [1.5, 1.7, 1.5], "easeoutback"),
                                          (0.8, [1, 1, 1], "easeinoutsine"))}
    A["ability_1"] = anim(0.8, a1, "hold_on_last_frame")

    # ability_2 — Flammensprung: tief in die Knie, Absprung mit Armen nach unten
    a2 = {
        "root": {"position": keys((0, [0, 0, 0]), (0.12, [0, -2.5, 0], "easeoutquad"), (0.25, [0, 0, 0], "easeoutback"))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 25, 0, 0]), (0.25, [FWD * -10, 0, 0], "easeoutback"),
                                  (0.6, [0, 0, 0], "easeinoutsine"))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 40, 0, 0]), (0.25, [FWD * -50, 0, 20], "easeoutback"),
                                       (0.6, [0, 0, 0], "easeinoutsine"))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 40, 0, 0]), (0.25, [FWD * -50, 0, -20], "easeoutback"),
                                      (0.6, [0, 0, 0], "easeinoutsine"))},
    }
    if knees:
        a2["right_leg"] = {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 45, 0, 0]), (0.25, [FWD * -10, 0, 0], "easeoutback"),
                                            (0.6, [0, 0, 0]))}
        a2["left_leg"] = {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 45, 0, 0]), (0.25, [FWD * -10, 0, 0], "easeoutback"),
                                           (0.6, [0, 0, 0]))}
        a2["right_shin"] = {"rotation": keys((0, [0, 0, 0]), (0.12, [70, 0, 0]), (0.25, [0, 0, 0], "easeoutback"))}
        a2["left_shin"] = {"rotation": keys((0, [0, 0, 0]), (0.12, [70, 0, 0]), (0.25, [0, 0, 0], "easeoutback"))}
    A["ability_2"] = anim(0.6, a2, "hold_on_last_frame")

    # hit — Zurueckzucken
    A["hit"] = anim(0.3, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.06, [FWD * -14, 0, 6], "easeoutquad"), (0.3, [0, 0, 0], "easeinoutsine"))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.06, [FWD * -18, 0, 0], "easeoutquad"), (0.3, [0, 0, 0], "easeinoutsine"))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.06, [FWD * 20, 0, 25]), (0.3, [0, 0, 0], "easeinoutsine"))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.06, [FWD * 20, 0, -25]), (0.3, [0, 0, 0], "easeinoutsine"))},
    }, "hold_on_last_frame")

    # transform — Koerper waechst aus dem Omnitrix-Blitz (gestaucht, ueberschwingend), Kraftpose, bereit
    tr = {
        "root": {"scale": keys((0, [0.05, 0.05, 0.05]), (0.15, [0.55, 1.35, 0.55], "easeoutquad"), (0.3, [1.18, 0.82, 1.18], "easeinquad"),
                               (0.45, [0.94, 1.06, 0.94], "easeoutquad"), (0.6, [1.0, 1.0, 1.0], "easeinoutsine")),
                 "rotation": keys((0, [0, -180, 0]), (0.3, [0, 0, 0], "easeoutcubic"))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [FWD * 18, 0, 0]), (0.55, [FWD * -12, 0, 0], "easeoutback"),
                                  (1.1, [FWD * -12, 0, 0]), (1.4, [0, 0, 0], "easeinoutsine"))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.55, [FWD * -22, 0, 0], "easeoutback"), (1.1, [FWD * -22, 0, 0]),
                                  (1.4, [0, 0, 0], "easeinoutsine"))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [FWD * 30, 0, -10]), (0.55, [FWD * 10, 0, 70], "easeoutback"),
                                       (1.1, [FWD * 10, 0, 65]), (1.4, [0, 0, 0], "easeinoutsine"))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [FWD * 30, 0, 10]), (0.55, [FWD * 10, 0, -70], "easeoutback"),
                                      (1.1, [FWD * 10, 0, -65]), (1.4, [0, 0, 0], "easeinoutsine"))},
    }
    if heavy_limbs:
        tr["right_forearm"] = {"rotation": keys((0, [0, 0, 0]), (0.55, [FWD * 50, 0, 0], "easeoutback"), (1.1, [FWD * 50, 0, 0]),
                                                (1.4, [FWD * 12, 0, 0], "easeinoutsine"))}
        tr["left_forearm"] = {"rotation": keys((0, [0, 0, 0]), (0.55, [FWD * 50, 0, 0], "easeoutback"), (1.1, [FWD * 50, 0, 0]),
                                               (1.4, [FWD * 12, 0, 0], "easeinoutsine"))}
    if flames:
        tr["flame_base"] = {"scale": keys((0, [0.2, 0.2, 0.2]), (0.45, [0.6, 0.5, 0.6]), (0.6, [1.6, 2.0, 1.6], "easeoutback"),
                                          (1.0, [1.2, 1.3, 1.2]), (1.4, [1, 1, 1], "easeinoutsine"))}
    A["transform"] = anim(1.4, tr, "hold_on_last_frame")

    # revert — Koerper zieht sich zusammen und verschwindet im roten Blitz
    rv = {
        "root": {"scale": keys((0, [1, 1, 1]), (0.12, [1.12, 0.9, 1.12], "easeoutquad"), (0.4, [0.05, 0.05, 0.05], "easeinback"))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 20, 0, 0]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 40, 0, 30]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.12, [FWD * 40, 0, -30]))},
    }
    A["revert"] = anim(0.4, rv, "hold_on_last_frame")

    # zweites Armpaar (Vierarm) bewegt sich wie das obere, leicht versetzt durch den eigenen Drehpunkt
    if {"right_lower_arm", "left_lower_arm"} <= names:
        for data in A.values():
            for side in ("right", "left"):
                if f"{side}_arm" in data["bones"]:
                    data["bones"][f"{side}_lower_arm"] = json.loads(json.dumps(data["bones"][f"{side}_arm"]))
                if f"{side}_forearm" in data["bones"]:
                    data["bones"][f"{side}_lower_forearm"] = json.loads(json.dumps(data["bones"][f"{side}_forearm"]))

    # nur Knochen behalten, die das Modell hat
    for data in A.values():
        data["bones"] = {k: v for k, v in data["bones"].items() if k in names}
    missing = [n for n in ANIMATIONS if n not in A]
    if missing:
        raise ValueError(f"{alien.name}: Animationen fehlen: {missing}")
    return {"format_version": "1.8.0", "animations": {n: A[n] for n in ANIMATIONS}}


# --- Ausgabe --------------------------------------------------------------------------------------

def outputs(alien: Alien, seed: int) -> dict[Path, object]:
    base = Path("entity") / "alien"
    color, glow = build_textures(alien, seed)
    files: dict[Path, object] = {
        ASSETS / "geo" / base / f"{alien.name}.geo.json": build_geo(alien),
        ASSETS / "animations" / base / f"{alien.name}.animation.json": build_animations(alien),
        ASSETS / "textures" / base / f"{alien.name}.png": color,
    }
    # Leuchtmaske nur mit mindestens einem Pixel — GeckoLib verweigert leere Masken (None = Datei entfernen)
    files[ASSETS / "textures" / base / f"{alien.name}_glowmask.png"] = glow if alien.glow and glow.getbbox() else None
    files[ASSETS / "alien_render" / f"{alien.name}.json"] = {"scale": alien.render_scale}
    files[ASSETS / "textures" / base / f"{alien.name}_arms.png"] = build_arm_skin(alien) if alien.arms else None
    return files


def write_preview(directory: Path, alien: Alien, files: dict[Path, object]) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for path, content in files.items():
        if isinstance(content, Image.Image):
            big = content.resize((content.width * 6, content.height * 6), Image.NEAREST)
            backdrop = Image.new("RGBA", big.size, (40, 40, 48, 255))
            backdrop.alpha_composite(big)
            backdrop.save(directory / f"preview_{path.stem}.png")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Dateien vorhanden sind")
    parser.add_argument("--only", help="nur dieses Alien schreiben (z. B. heatblast)")
    parser.add_argument("--preview", type=Path, help="vergroesserte Textur-Vorschau in diesen Ordner schreiben")
    parser.add_argument("--seed", type=int, default=4242, help="Zufalls-Seed fuer Rauschen und Risse")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    try:
        aliens = build_aliens()
    except ValueError as exc:
        LOG.error("%s", exc)
        return 1
    if args.only:
        aliens = [a for a in aliens if a.name == args.only]
        if not aliens:
            LOG.error("unbekanntes Alien: %s", args.only)
            return 1

    files: dict[Path, object] = {}
    per_alien: dict[str, dict[Path, object]] = {}
    for alien in aliens:
        per_alien[alien.name] = outputs(alien, args.seed)
        files.update(per_alien[alien.name])

    if args.check:
        missing = [p for p, c in files.items() if c is not None and not p.is_file()]
        missing += [p for p, c in files.items() if c is None and p.is_file()]  # veraltete Datei muss weg
        for p in missing:
            LOG.error("fehlt: %s", p.relative_to(ASSETS))
        stale = []
        for p, content in files.items():
            if p.suffix == ".json" and p.is_file() and content is not None:
                if json.loads(p.read_text(encoding="utf-8")) != json.loads(json.dumps(content)):
                    stale.append(p)
        for p in stale:
            LOG.error("veraltet (Generator neu ausfuehren): %s", p.relative_to(ASSETS))
        LOG.info("%d/%d Alien-Dateien vorhanden", len(files) - len(missing), len(files))
        return 1 if missing or stale else 0

    for alien in aliens:
        for path, content in per_alien[alien.name].items():
            try:
                path.parent.mkdir(parents=True, exist_ok=True)
                if content is None:
                    path.unlink(missing_ok=True)
                elif isinstance(content, Image.Image):
                    content.save(path)
                else:
                    path.write_text(json.dumps(content, indent=2) + "\n", encoding="utf-8")
            except OSError as exc:
                LOG.error("konnte %s nicht schreiben: %s", path, exc)
                return 1
            LOG.debug("geschrieben: %s", path.relative_to(ASSETS))
        if args.preview:
            write_preview(args.preview, alien, per_alien[alien.name])
    LOG.info("%d Alien-Dateien geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
