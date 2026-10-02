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
from copy import deepcopy
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
    uniforms: dict[str, dict[str, str]] | None = None
                            # Uniform-ID → Farbrolle → Farbe (Material „role:NAME“). Erste Uniform = <name>.png,
                            # weitere = <name>_<id>.png (+ Leuchtmaske, Ego-Arme). Reihenfolge: classic, evo, ultimate


UNIFORM_IDS = ("classic", "evo", "ultimate")


def with_uniform(alien: Alien, uniform: str) -> Alien:
    """Kopie mit aufgeloesten Farbrollen („role:SKIN“ → „clean:#…“) fuer eine Uniform."""
    palette = (alien.uniforms or {}).get(uniform, {})

    def resolve(material: str) -> str:
        for prefix in ("lava:", "flame:"):
            if material.startswith(prefix):
                return prefix + ",".join(c if c.startswith("#") else palette[c] for c in material[len(prefix):].split(","))
        if material.startswith("role:"):
            role = material[5:]
            if role not in palette:
                raise ValueError(f"{alien.name}/{uniform}: Farbrolle {role} fehlt")
            return "clean:" + palette[role]
        return material

    copy = deepcopy(alien)
    for bone in copy.bones:
        for part in bone.cubes:
            if isinstance(part, Part):
                part.material = resolve(part.material)
    if copy.arms:
        copy.arms = (resolve(copy.arms[0]), resolve(copy.arms[1]))
    return copy


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
ARMOR = rgb("#25252A")
ARMOR_RIM = rgb("#3D7BFF")
SUIT = rgb("#56C4D6")
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
            elif mat.startswith("lava:"):
                # Gestein mit Glutrissen in Uniform-Farben: lava:#gestein,#gestein_dunkel,#glut,#glut_rand
                rock, rock_dark, hot, hot_edge = (rgb(c) for c in mat[5:].split(","))
                # Risslinien = Hoehenlinien des Rauschens (verzweigtes Netz statt Flecken, wie bei Alien Evolution)
                n = blobs(mx * 0.9 + mz * 0.7, my * 1.1)
                if abs(n - 0.5) < 0.028:
                    base, glow = hot, True
                elif abs(n - 0.5) < 0.05:
                    base, glow = hot_edge, True
                else:
                    t = SHADE_CLUSTERS(mx * 2.1, my * 2.1 + mz)
                    base = rock if t > 0.45 else rock_dark
            elif mat.startswith("flame:"):
                # Flamme mit Verlauf nach oben: flame:#kern,#mitte,#spitze (leuchtet)
                core, mid, tip = (rgb(c) for c in mat[6:].split(","))
                t = (my - cube.origin[1]) / max(1.0, cube.size[1])
                n = blobs(mx * 1.3 + mz, my * 0.9)
                base = core if n < 0.62 - t * 0.35 else mid if n < 0.78 - t * 0.2 else tip
                glow = True
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
    yellow = color[0] > 225 and color[1] > 140
    green_eye = color[1] > 200 and color[0] < 210 and color[2] < 120
    return yellow or green_eye


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
    """Material "ref:<name>[@x,y,b,h][|plainback]": Vorderseite = Vorlage, Rueckseite = gespiegelt (oder mit
    "|plainback" aus den Randspalten, z. B. wenn vorn ein Brustpaneel ist), Seiten = Randspalten gestreckt,
    oben/unten = oberste/unterste Zeile. Transparente Vorlagenpixel bekommen die Durchschnittsfarbe der Vorlage."""
    spec, _, option = cube.material[4:].partition("|")
    img = load_reference(spec)
    w, h = img.size
    opaque = [px[:3] for px in img.get_flattened_data() if px[3] >= 128]
    fallback = tuple(sum(c[i] for c in opaque) // len(opaque) for i in range(3)) if opaque else (60, 60, 60)
    if face.name == "north":
        src = img
    elif face.name == "south":
        src = img.crop((0, 0, max(1, w // 4), h)) if option == "plainback" else img.transpose(Image.FLIP_LEFT_RIGHT)
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
                r, g, b = fallback
            base = ingame((r, g, b))
            hot = is_hot(base) and face.name != "down"
            canvas.put(face.x + px, face.y + py, base if hot else scale(base, shade), glow=hot)


def painter(material: str) -> Callable[[Canvas, Face, Cube, random.Random, Noise], None]:
    if material.startswith("ref:"):
        return paint_ref_material
    if material.startswith("flat:"):
        return paint_flat
    if material.startswith(("clean:", "lava:", "flame:")) or material in ("lava", "fur", "crystal", "split", "panel", "shirt", "pyro",
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
        paint = paint_ref_material if material.startswith("ref:") else painter(material)
        for face in faces_of(part, 2):
            paint(canvas, face, part, random.Random(0), Noise(0))
    return canvas.color


def build_textures(alien: Alien, seed: int) -> tuple[Image.Image, Image.Image]:
    """Farbtextur und Leuchtmaske der detaillierten Bauweise (Wuerfel vom Typ Part)."""
    k = alien.density
    canvas = Canvas((alien.texture_size[0] * k, alien.texture_size[1] * k))
    painted_uvs: set[tuple[int, int]] = set()
    for b in alien.bones:
        for i, cube in enumerate(b.cubes):
            if cube.mirror and cube.uv in painted_uvs:
                continue  # gespiegelte Seite teilt sich die UV mit der schon bemalten Gegenseite
            painted_uvs.add(cube.uv)
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


HEATBLAST_UNIFORMS = {
    # Original-Serie: dunkelrotes Gestein mit gelb-orangen Glutrissen, gelb-rote Kopfflamme
    "classic": {"ROCK": "#7A1D1D", "ROCK_DARK": "#4F1320", "HOT": "#FFD24A", "HOT2": "#FFA22E", "FACE": "#2A0F09",
                "EYE": "#FFE14D", "FLAME_CORE": "#FFE27A", "FLAME_MID": "#FF9A1F", "FLAME_TIP": "#FF4A0A"},
    # Alien-Evolution-Look: fast schwarzes Gestein, blassgelbe Risse, hellgelbe Flamme
    "evo": {"ROCK": "#4F1320", "ROCK_DARK": "#290911", "HOT": "#FFF8B1", "HOT2": "#FFCC58", "FACE": "#1E0A14",
            "EYE": "#FFFEF7", "FLAME_CORE": "#FFFEDC", "FLAME_MID": "#FFEF73", "FLAME_TIP": "#FFCC58"},
    # Ultimate: blaues Feuer
    "ultimate": {"ROCK": "#1C2A6B", "ROCK_DARK": "#10183E", "HOT": "#9AE8FF", "HOT2": "#3AB0FF", "FACE": "#0A0F28",
                 "EYE": "#E8FBFF", "FLAME_CORE": "#E8FBFF", "FLAME_MID": "#7FD4FF", "FLAME_TIP": "#2A7BE6"},
}
HB_ROCK = "lava:ROCK,ROCK_DARK,HOT,HOT2"
HB_FLAME = "flame:FLAME_CORE,FLAME_MID,FLAME_TIP"


def heatblast() -> Alien:
    """Heatblast (Pyronite) nach Alien Evolution (Erlaubnis laut SANTIQ, Fanprojekt): schlanker Lava-Koerper mit
    Glutrissen, schmale Brust ueber schmaler Taille, duenne Arme mit grossen glimmenden Faeusten, duenne Beine mit
    schmalen Knoecheln, Rautenkopf mit zweiteiliger Maske und grosse, lodernde Kopfflamme. Einheiten = 1/16 Block,
    vorn = -z; drei Uniformen (ultimate = blaues Feuer)."""
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 14, 0), [
                 Part((-2, 14, -1), (4, 2, 5), HB_ROCK),                                                # Becken
                 Part((-3, 16, -1), (6, 5, 5), HB_ROCK),                                                # Taille
             ]),
             Bone("chest", "body", (0, 21, 1), [
                 Part((-4, 21, -1.5), (8, 6, 6), HB_ROCK),                                              # Brust
                 Part((-1.5, 23.5, -2.3), (3, 3, 1), "clean:#141414", detail="badge6"),                 # Omnitrix
                 Part((-3, 26.5, -1), (6, 2, 5), HB_ROCK, rotation=(-15, 0, 0), pivot=(0, 27, 1.5)),     # Nacken
             ], rotation=(-3, 0, 0)),
             Bone("head", "chest", (0, 28.5, 0.5), [
                 Part((-2, 28.5, -1.5), (4, 6, 4), HB_ROCK, rotation=(0, 45, 0), pivot=(0, 31.5, 0.5)),  # Rautenkopf
                 Part((-2.6, 29.5, -2.6), (2, 4, 1), "role:FACE", rotation=(0, 30, 0), pivot=(-0.6, 31.5, -2.1)),  # Maske
                 Part((0.6, 29.5, -2.6), (2, 4, 1), "role:FACE", rotation=(0, -30, 0), pivot=(0.6, 31.5, -2.1)),
                 Part((-1.9, 31.6, -3.1), (1, 1, 1), "role:EYE"), Part((0.9, 31.6, -3.1), (1, 1, 1), "role:EYE"),  # Augen
             ]),
             Bone("flame_base", "head", (0, 34, 0.5), [
                 Part((-2.5, 34, -2), (5, 3, 5), HB_FLAME),                                             # Flammenfuss
                 Part((-2, 37, -1.5), (4, 3, 4), HB_FLAME),
                 Part((-1.5, 40, -1), (2, 3, 2), HB_FLAME, rotation=(0, 0, 8), pivot=(-0.5, 40, 0)),    # Zungen
                 Part((0.5, 39.5, 0), (2, 2, 2), HB_FLAME, rotation=(0, 0, -12), pivot=(1.5, 39.5, 1)),
                 Part((-3.5, 35, -1), (1, 3, 2), HB_FLAME, rotation=(0, 0, 18), pivot=(-3, 35, 0)),
                 Part((2.5, 35.5, 0), (1, 2, 2), HB_FLAME, rotation=(0, 0, -18), pivot=(3, 35.5, 1)),
                 Part((-1, 37.5, 1.5), (2, 4, 2), HB_FLAME, rotation=(-25, 0, 0), pivot=(0, 37.5, 2.5)),  # Hinterflamme
             ]),
             ]
    for side_name, side in (("right", -1), ("left", 1)):
        bones += [
            Bone(f"{side_name}_arm", "chest", (4 * side, 26, 1), side_parts([
                ((-7, 18, -0.5), (3, 8, 3), HB_ROCK, {"inflate": 0.1}),                                 # Oberarm
            ], side), rotation=(5, 0, 10 * -side)),
            Bone(f"{side_name}_forearm", f"{side_name}_arm", (5.5 * side, 18.5, 1), side_parts([
                ((-8, 12.5, -1), (4, 6, 4), HB_ROCK),                                                   # Unterarm
                ((-8, 8.5, -1), (4, 4, 4), "lava:HOT2,ROCK,HOT,HOT2"),                                  # glimmende Faust
            ], side), rotation=(-15, 0, 0)),
            Bone(f"{side_name}_leg", "root", (1.5 * side, 15, 1), side_parts([
                ((-3, 8.5, 0), (3, 7, 3), HB_ROCK, {"inflate": 0.2}),                                   # Oberschenkel
            ], side)),
            Bone(f"{side_name}_shin", f"{side_name}_leg", (1.7 * side, 9, 1), side_parts([
                ((-3.2, 5.5, -0.5), (3, 4, 3), HB_ROCK),                                                # Wade
                ((-2.8, 1.5, 0), (2, 4, 2), HB_ROCK),                                                   # Knoechel
                ((-3.6, 0, -2.5), (3, 2, 5), "role:FACE"),                                              # Fuss
            ], side)),
        ]
    alien = Alien("heatblast", (128, 128), bones, "#FF6A00", style="heat", glow=True, density=2, render_scale=1.0,
                  arms=(HB_ROCK, HB_ROCK), uniforms=HEATBLAST_UNIFORMS)
    pack_uvs(alien)
    return alien


def side_parts(entries: list[tuple], side: int) -> list[Part]:
    """Wuerfel der rechten Seite (-x) fuer beide Seiten: links gespiegelt samt Wuerfel-Drehung und Pivot.
    Eintraege: (origin, size, material[, extra-kwargs])."""
    result = []
    for entry in entries:
        (x, y, z), size, material = entry[0], entry[1], entry[2]
        extra = dict(entry[3]) if len(entry) > 3 else {}
        if side > 0 and "rotation" in extra:
            rx, ry, rz = extra["rotation"]
            extra["rotation"] = (rx, -ry, -rz)
            px, py_, pz = extra.get("pivot", (x, y, z))
            extra["pivot"] = (-px, py_, pz)
        result.append(Part((mirror_x(x, size[0], side), y, z), size, material, mirror=side > 0, **extra))
    return result


XLR8_SUIT = "role:SUIT"          # Anzug/Panzer
XLR8_SKIN = "role:SKIN"          # Haut an Armen und Beinen
XLR8_LIGHT = "role:LIGHT"        # helle Kante/Flossen
XLR8_HELMET = "role:HELMET"
XLR8_CLAW = "role:CLAW"
XLR8_UNIFORMS = {
    # Original-Serie: blaue Haut, schwarzer Anzug mit weisser Brustlinie, schwarzer Helm
    "classic": {"SUIT": "#16171C", "SKIN": "#2D7FE6", "LIGHT": "#7FC4FF", "HELMET": "#101115", "CLAW": "#D8E0E6",
                "STRIPE": "#E8ECEF", "TAIL": "#2D7FE6", "CHIN": "#2D7FE6"},
    # Alien-Evolution-Look: stahlblaue Haut, schwarzer Anzug, gruene Akzente
    "evo": {"SUIT": "#1A1818", "SKIN": "#55717E", "LIGHT": "#B3FF40", "HELMET": "#171515", "CLAW": "#C8D2D6",
            "STRIPE": "#3A4D5E", "TAIL": "#55717E", "CHIN": "#698890"},
    # Ultimate: schwarz-weiss mit tuerkisen Linien
    "ultimate": {"SUIT": "#121316", "SKIN": "#D9DEE3", "LIGHT": "#39D3FF", "HELMET": "#0E0F12", "CLAW": "#39D3FF",
                 "STRIPE": "#39D3FF", "TAIL": "#D9DEE3", "CHIN": "#D9DEE3"},
}


def xlr8() -> Alien:
    """XLR8 (Kineceleran) als Raptor nach Alien Evolution (Erlaubnis laut SANTIQ, Fanprojekt): vorgebeugter Rumpf
    aus gekippten Teilen, Kopf weit vorn mit spitzem Helm und Visier, viergliedriger Schwanz, angewinkelte Arme
    mit Krallen, digitigrade Beine (Oberschenkel nach vorn, Mittelfuss waagerecht zurueck, steiles Schienbein,
    Krallenfuss). Einheiten = 1/16 Block, vorn = -z; Hoehe ca. 29 (Spieler 32)."""
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 13, -1), [
                 Part((-3, 11, -3), (6, 5, 6), XLR8_SUIT, rotation=(-8, 0, 0), pivot=(0, 13, 0)),        # Huefte
                 Part((-3, 14, -5), (6, 6, 5), XLR8_SUIT, rotation=(25, 0, 0), pivot=(0, 15, -2.5)),     # Bauch
                 Part((-3.5, 18, -8), (7, 5, 6), XLR8_SUIT, rotation=(20, 0, 0), pivot=(0, 20, -5)),     # Brust
                 Part((-1, 18.4, -8.6), (2, 4, 1), "role:STRIPE", rotation=(20, 0, 0), pivot=(0, 20, -5)),  # Brustlinie
                 Part((-1.5, 19, -8.9), (3, 3, 1), "clean:#141414", detail="badge6", rotation=(20, 0, 0),
                      pivot=(0, 20, -5)),                                                              # Omnitrix
                 Part((-1, 21, -9.5), (2, 3, 3), XLR8_SUIT, rotation=(35, 0, 0), pivot=(0, 22, -8)),     # Hals
             ]),
             Bone("head", "body", (0, 23, -9), [
                 Part((-2.5, 23, -13), (5, 4, 5), XLR8_HELMET),                                          # Helm
                 Part((-2, 23.5, -14.5), (4, 3, 4), XLR8_HELMET, rotation=(0, 45, 0), pivot=(0, 25, -12.5)),  # Helmspitze
                 Part((-0.5, 27, -13), (1, 1, 7), "clean:#24262C"),                                       # Helmkamm
                 Part((-2, 23.5, -13.4), (4, 2, 1), "clean:#0A0B0E", detail="eyes"),                     # Visier
                 Part((-1.5, 22.3, -12.5), (3, 1, 3), "role:CHIN"),                                      # Kinn
             ]),
             Bone("tail_1", "body", (0, 14, 2), [
                 Part((-2, 12, 2), (4, 4, 4), XLR8_SUIT), Part((-2, 12, 6), (4, 4, 1), "role:TAIL", inflate=0.1)],
                 rotation=(-6, 0, 0)),
             Bone("tail_2", "tail_1", (0, 14.5, 7), [
                 Part((-1.5, 13, 7), (3, 3, 4), XLR8_SUIT), Part((-1.5, 13, 11), (3, 3, 1), "role:TAIL", inflate=0.1)],
                 rotation=(-6, 0, 0)),
             Bone("tail_3", "tail_2", (0, 15, 12), [
                 Part((-1, 13.5, 12), (2, 2, 4), XLR8_SUIT), Part((-1, 13.5, 16), (2, 2, 1), "role:TAIL", inflate=0.1)],
                 rotation=(-5, 0, 0)),
             Bone("tail_4", "tail_3", (0, 15.5, 17), [
                 Part((-1, 14, 17), (2, 2, 4), XLR8_SUIT)], rotation=(-4, 0, 0)),
             ]
    for side_name, side in (("right", -1), ("left", 1)):
        bones += [
            Bone(f"{side_name}_arm", "body", (4.5 * side, 20, -6), side_parts([
                ((-6, 19, -7.5), (3, 3, 3), XLR8_SUIT, {"inflate": 0.25}),                              # Schulter
                ((-6, 15, -7), (2, 5, 2), XLR8_SKIN, {"rotation": (-25, 0, 8), "pivot": (-5, 19, -6)}),  # Oberarm
            ], side), rotation=(0, 0, 5 * -side)),
            Bone(f"{side_name}_forearm", f"{side_name}_arm", (5.5 * side, 16, -7), side_parts([
                ((-6.5, 14.5, -12), (2, 2, 5), XLR8_SKIN),                                              # Unterarm nach vorn
                ((-7, 15.5, -10.5), (1, 1, 3), XLR8_LIGHT),                                             # Flosse
                ((-7, 13.5, -14), (3, 3, 2), XLR8_SUIT),                                                # Hand
                ((-7, 13.5, -15), (1, 1, 1), XLR8_CLAW), ((-5.5, 13.5, -15), (1, 1, 1), XLR8_CLAW),
                ((-6.25, 15.5, -15), (1, 1, 1), XLR8_CLAW),                                            # Krallen
            ], side), rotation=(45, 0, 0)),
            Bone(f"{side_name}_leg", "root", (2.5 * side, 13, -1), side_parts([
                ((-4, 7, -4), (3, 6, 4), XLR8_SUIT, {"rotation": (-22, 0, 0), "pivot": (-2.5, 13, -1)}),  # Oberschenkel
            ], side)),
            Bone(f"{side_name}_shin", f"{side_name}_leg", (2.5 * side, 8, -4), side_parts([
                ((-3.5, 6, -4.5), (2, 2, 5), XLR8_SKIN, {"rotation": (-15, 0, 0), "pivot": (-2.5, 7, -4)}),  # Mittelfuss
                ((-3.5, 1.5, -0.5), (2, 5, 2), XLR8_SKIN, {"rotation": (12, 0, 0), "pivot": (-2.5, 6, 0)}),    # Schienbein
                ((-4, 0, -4), (3, 2, 5), XLR8_SUIT),                                                    # Fuss
                ((-4, 0, -5), (1, 1, 1), XLR8_CLAW), ((-2, 0, -5), (1, 1, 1), XLR8_CLAW),               # Zehenkrallen
            ], side)),
        ]
    alien = Alien("xlr8", (96, 96), bones, "#1E90FF", style="fast", glow=True, density=2, render_scale=1.0,
                  arms=(XLR8_SKIN, XLR8_SKIN), uniforms=XLR8_UNIFORMS)
    pack_uvs(alien)
    return alien


FOUR_ARMS_UNIFORMS = {
    # Original-Serie: rote Haut, weisses Hemd mit schwarzem Mittelstreifen, schwarze Hose, schwarze Baender
    "classic": {"SKIN": "#C8141E", "SKIN_DARK": "#8A0E14", "SHIRT": "#E8ECEF", "STRIPE": "#1A1A1E", "SLEEVE": "#E8ECEF",
                "STRAP": "#E8ECEF", "BAND": "#1A1A1E", "BELT": "#1A1A1E", "PANTS": "#1E1E22", "FOOT": "#C8141E"},
    # Alien-Evolution-Look: ganz rot, goldene X-Gurte und Baender, dunkle Hose
    "evo": {"SKIN": "#BE2E30", "SKIN_DARK": "#8A1B28", "SHIRT": "#BE2E30", "STRIPE": "#BE2E30", "SLEEVE": "#BE2E30",
            "STRAP": "#E8A23A", "BAND": "#E8A23A", "BELT": "#4F1320", "PANTS": "#8A1B28", "FOOT": "#E8A23A"},
    # Ultimate: schwarzer Anzug mit weissem Streifen und weissen Schultern
    "ultimate": {"SKIN": "#C8141E", "SKIN_DARK": "#8A0E14", "SHIRT": "#18181C", "STRIPE": "#E8ECEF", "SLEEVE": "#E8ECEF",
                 "STRAP": "#18181C", "BAND": "#E8ECEF", "BELT": "#E8ECEF", "PANTS": "#18181C", "FOOT": "#18181C"},
}


def four_arms() -> Alien:
    """Vierarm (Tetramand) nach Alien Evolution (Erlaubnis laut SANTIQ, Fanprojekt): breite, leicht vorgekippte Brust
    ueber schmaler Taille, kleiner Kopf mit vier Augen und Nackenflosse, wuchtige Schultern, Unterarme mit Baendern
    und Fellflossen, zweites Armpaar am Rumpf, kraeftige Beine. Einheiten = 1/16 Block, vorn = -z; Farben ueber
    Rollen, drei Uniformen. Darstellung 1,3-fach (Spielergroesse ca. 2,5 Bloecke)."""
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 13, 0), [
                 Part((-3.5, 12, -2), (7, 3, 5), "role:PANTS"),                                         # Becken
                 Part((-4, 15, -2.5), (8, 6, 6), "role:SKIN"),                                          # Bauch
                 Part((-4, 14.5, -2.5), (8, 1, 6), "role:BELT", inflate=0.3),                           # Guertel
             ]),
             Bone("chest", "body", (0, 21, 0.5), [
                 Part((-6, 21, -3), (12, 7, 7), "role:SHIRT"),                                          # Brust
                 Part((-1, 21, -3.2), (2, 7, 1), "role:STRIPE"),                                        # Mittelstreifen
                 Part((-6, 24, -3.4), (12, 1, 1), "role:STRAP", rotation=(0, 0, 32), pivot=(0, 24.5, -3)),   # X-Gurt
                 Part((-6, 24, -3.5), (12, 1, 1), "role:STRAP", rotation=(0, 0, -32), pivot=(0, 24.5, -3)),
                 Part((-1.5, 23.5, -3.8), (3, 3, 1), "clean:#141414", detail="badge6"),                 # Omnitrix
                 Part((-3, 28, -1.5), (6, 1, 4), "role:SKIN"),                                          # Nacken
             ], rotation=(-8, 0, 0)),
             Bone("head", "chest", (0, 29, 0), [
                 Part((-2, 29, -2), (4, 5, 4), "role:SKIN", detail="four_eyes"),
                 Part((-2, 28.5, -2.4), (4, 1, 2), "role:SKIN_DARK"),                                   # Kiefer
                 Part((-0.5, 31, 1.5), (1, 3, 2), "role:SKIN_DARK", rotation=(55, 0, 0), pivot=(0, 31, 2)),  # Nackenflosse
             ]),
             ]
    for side_name, side in (("right", -1), ("left", 1)):
        bones += [
            Bone(f"{side_name}_arm", "chest", (5.5 * side, 26, 0.5), side_parts([
                ((-10, 22, -2.5), (5, 5, 5), "role:SLEEVE", {"rotation": (12, 0, -17), "pivot": (-7.5, 24.5, 0)}),  # Schulter
                ((-10, 17, -1.5), (4, 6, 4), "role:SKIN", {"rotation": (8, 0, 20), "pivot": (-7.5, 22, 0)}),      # Bizeps
            ], side)),
            Bone(f"{side_name}_forearm", f"{side_name}_arm", (8.5 * side, 18, 0.5), side_parts([
                ((-11.5, 11, -1.5), (4, 7, 4), "role:SKIN"),                                            # Unterarm
                ((-11.5, 12, -1.5), (4, 2, 4), "role:BAND", {"inflate": 0.3}),                          # Armband
                ((-12.5, 13, 0), (1, 4, 1), "role:SKIN_DARK", {"rotation": (0, 0, -15), "pivot": (-12, 15, 0.5)}),  # Flossen
                ((-12.5, 14, -1.5), (1, 3, 1), "role:SKIN_DARK", {"rotation": (0, 0, -15), "pivot": (-12, 15, -1)}),
                ((-11.5, 7.5, -2), (4, 4, 5), "role:SKIN", {"inflate": 0.2}),                           # Faust
            ], side)),
            Bone(f"{side_name}_lower_arm", "body", (4.5 * side, 20.5, 0.5), side_parts([
                ((-8, 15.5, -1.5), (4, 5, 4), "role:SKIN", {"rotation": (7, 0, 15), "pivot": (-5, 20.5, 0)}),
            ], side)),
            Bone(f"{side_name}_lower_forearm", f"{side_name}_lower_arm", (6.5 * side, 16, 0.5), side_parts([
                ((-9, 9.5, -1.5), (4, 6, 4), "role:SKIN"),
                ((-9, 10.5, -1.5), (4, 2, 4), "role:BAND", {"inflate": 0.3}),
                ((-9, 6.5, -2), (4, 3, 4), "role:SKIN", {"inflate": 0.2}),                               # Faust
            ], side)),
            Bone(f"{side_name}_leg", "root", (2 * side, 13, 0.5), side_parts([
                ((-4, 7, -1.5), (4, 7, 4), "role:PANTS", {"rotation": (-5, 0, 3), "pivot": (-2, 13, 0.5)}),   # Oberschenkel
            ], side)),
            Bone(f"{side_name}_shin", f"{side_name}_leg", (2.2 * side, 7.5, 0.5), side_parts([
                ((-4, 1.5, -1.5), (3, 6, 3), "role:PANTS", {"inflate": 0.1}),                           # Wade
                ((-4, 6, -1.5), (3, 1, 3), "role:BAND", {"inflate": 0.35}),                              # Knieband
                ((-4.5, 0, -2.5), (4, 2, 5), "role:FOOT"),                                              # Fuss
            ], side)),
        ]
    alien = Alien("four_arms", (128, 128), bones, "#C0392B", style="heavy", glow=True, density=2, render_scale=1.3,
                  arms=("role:SKIN", "role:SKIN"), uniforms=FOUR_ARMS_UNIFORMS)
    pack_uvs(alien)
    return alien


DIAMONDHEAD_UNIFORMS = {
    # Original-Serie: gruene Kristalle, Anzug halb schwarz (rechts) / halb weiss (links)
    "classic": {"CRYSTAL": "#5FD89A", "CRYSTAL_DARK": "#2E9A63", "SUIT_R": "#18181C", "SUIT_L": "#E8ECEF",
                "LEG_R": "#18181C", "LEG_L": "#E8ECEF", "EYE": "#FFE14D"},
    # Alien-Evolution-Look: tuerkise Kristalle, dunkelvioletter Anzug
    "evo": {"CRYSTAL": "#86D6C2", "CRYSTAL_DARK": "#58A980", "SUIT_R": "#2E2643", "SUIT_L": "#2E2643",
            "LEG_R": "#221A31", "LEG_L": "#221A31", "EYE": "#B3FF40"},
    # Ultimate: gruene Kristalle, schwarzer Anzug, weisse Beine
    "ultimate": {"CRYSTAL": "#5FD89A", "CRYSTAL_DARK": "#2E9A63", "SUIT_R": "#18181C", "SUIT_L": "#18181C",
                 "LEG_R": "#E8ECEF", "LEG_L": "#E8ECEF", "EYE": "#FFE14D"},
}


def diamondhead() -> Alien:
    """Diamondhead (Petrosapien) nach Alien Evolution (Erlaubnis laut SANTIQ, Fanprojekt): Rauten-Kristallkopf mit
    Kamm, breite Brust ueber schmalerem Unterleib, Kristallstacheln am Ruecken, schraeg abstehende Schulterkristalle,
    riesige Kristall-Unterarme, Anzug in zwei Haelften (Uniform classic: schwarz/weiss). Einheiten = 1/16 Block,
    vorn = -z; drei Uniformen; Darstellung 1,15-fach."""
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 15, 0), [
                 Part((-4, 15, -2), (4, 6, 6), "role:SUIT_R"),                                          # Unterleib rechts
                 Part((0, 15, -2), (4, 6, 6), "role:SUIT_L"),                                           # Unterleib links
             ]),
             Bone("chest", "body", (0, 21, 0.5), [
                 Part((-6, 21, -3), (6, 7, 8), "role:SUIT_R"),                                          # Brust rechts
                 Part((0, 21, -3), (6, 7, 8), "role:SUIT_L"),                                           # Brust links
                 Part((1, 24, -3.8), (3, 3, 1), "clean:#141414", detail="badge6"),                      # Omnitrix
                 Part((-3, 27, -2), (6, 3, 6), "role:CRYSTAL_DARK", rotation=(0, 45, 0), pivot=(0, 28, 1)),  # Hals
                 *side_parts([
                     ((-3, 22, 3), (2, 2, 5), "role:CRYSTAL", {"rotation": (22.5, -12.5, 0), "pivot": (-2, 23, 4)}),
                     ((-3, 18.5, 2), (2, 2, 4), "role:CRYSTAL", {"rotation": (22.5, -12.5, 0), "pivot": (-2, 19.5, 3.5)}),
                     ((-5, 27, 2.5), (2, 3, 9), "role:CRYSTAL", {"rotation": (40, -8, -12), "pivot": (-4.5, 28.5, 4)}),
                 ], -1),
                 *side_parts([
                     ((-3, 22, 3), (2, 2, 5), "role:CRYSTAL", {"rotation": (22.5, -12.5, 0), "pivot": (-2, 23, 4)}),
                     ((-3, 18.5, 2), (2, 2, 4), "role:CRYSTAL", {"rotation": (22.5, -12.5, 0), "pivot": (-2, 19.5, 3.5)}),
                     ((-5, 27, 2.5), (2, 3, 9), "role:CRYSTAL", {"rotation": (40, -8, -12), "pivot": (-4.5, 28.5, 4)}),
                 ], 1),                                                                                  # Rueckenstacheln
             ], rotation=(-5, 0, 0)),
             Bone("head", "chest", (0, 29.5, 0), [
                 Part((-2, 29.5, -2), (4, 4, 4), "role:CRYSTAL", rotation=(0, 45, 0), pivot=(0, 31.5, 0)),   # Rautenkopf
                 Part((-2, 33.5, -2), (4, 1, 4), "role:CRYSTAL_DARK", inflate=0.3, rotation=(0, 45, 0),
                      pivot=(0, 34, 0)),                                                                 # Kamm
                 Part((-1, 32, 1), (2, 2, 4), "role:CRYSTAL_DARK", rotation=(15, 0, 0), pivot=(0, 33, 1)),  # Hinterkamm
                 Part((-1.8, 31.3, -2.9), (1, 1, 1), "role:EYE"), Part((0.8, 31.3, -2.9), (1, 1, 1), "role:EYE"),  # Augen
             ]),
             ]
    for side_name, side in (("right", -1), ("left", 1)):
        suit, leg = ("role:SUIT_R", "role:LEG_R") if side < 0 else ("role:SUIT_L", "role:LEG_L")
        bones += [
            Bone(f"{side_name}_arm", "chest", (6.5 * side, 26, 0.5), side_parts([
                ((-14, 25, -3), (7, 5, 7), "role:CRYSTAL", {"rotation": (0, 0, -35), "pivot": (-7, 26, 0.5)}),  # Schulterkristall
                ((-10, 17.5, -2), (5, 8, 5), "role:CRYSTAL_DARK"),                                       # Oberarm
            ], side), rotation=(2.5, 0, 10 * -side)),
            Bone(f"{side_name}_forearm", f"{side_name}_arm", (7.5 * side, 18, 0.5), side_parts([
                ((-10.5, 8.5, -2.5), (6, 10, 6), "role:CRYSTAL", {"inflate": 0.15}),                    # Kristall-Unterarm
                ((-10, 4.5, -2), (5, 4, 5), "role:CRYSTAL_DARK"),                                        # Hand
                ((-9, 8, -3.3), (3, 3, 1), "role:CRYSTAL_DARK"),                                         # Kristallsplitter
            ], side), rotation=(-12, 0, 0)),
            Bone(f"{side_name}_leg", "root", (2.4 * side, 15, 0.5), side_parts([
                ((-4.4, 8.5, -1.5), (4, 7, 4), leg, {"inflate": 0.1}),                                   # Oberschenkel
            ], side)),
            Bone(f"{side_name}_shin", f"{side_name}_leg", (2.4 * side, 9, 0.5), side_parts([
                ((-4.4, 2, -1.5), (4, 7, 4), leg),                                                      # Wade
                ((-4.9, 0, -3), (5, 3, 7), suit),                                                        # Fuss
            ], side)),
        ]
    alien = Alien("diamondhead", (128, 128), bones, "#2ECC71", style="heavy", glow=True, density=2, render_scale=1.15,
                  arms=("role:CRYSTAL", "role:CRYSTAL"), uniforms=DIAMONDHEAD_UNIFORMS)
    pack_uvs(alien)
    return alien


GREY_MATTER_UNIFORMS = {
    # Original-Serie: grau, weisser Anzug mit schwarzem Streifen, orange Schulterpolster, gelbgruene Augen
    "classic": {"SKIN": "#9AAAA8", "SKIN_DARK": "#6C7F82", "SUIT": "#E8ECEF", "SUIT_DARK": "#1A1A1E",
                "ACCENT": "#E08A2A", "EYE": "#F8FF98", "PUPIL": "#141414"},
    # Alien-Evolution-Look: gruener Anzug, dunkle Hose, leuchtend gruene Augen
    "evo": {"SKIN": "#9AAAA8", "SKIN_DARK": "#6C7F82", "SUIT": "#40A009", "SUIT_DARK": "#2C2828",
            "ACCENT": "#2C2828", "EYE": "#B3FF40", "PUPIL": "#141414"},
    # Ultimate: schwarzer Anzug mit weissen Akzenten
    "ultimate": {"SKIN": "#AEBAB7", "SKIN_DARK": "#839695", "SUIT": "#1A1A1E", "SUIT_DARK": "#E8ECEF",
                 "ACCENT": "#E8ECEF", "EYE": "#F8FF98", "PUPIL": "#141414"},
}


def grey_matter() -> Alien:
    """Grey Matter (Galvan) nach Alien Evolution (Erlaubnis laut SANTIQ, Fanprojekt): grosser Kopf mit seitlich
    hervorquellenden Froschaugen (leuchtend), schmaler Anzug, duenne Arme mit grossen Haenden, duenne Beine mit langen
    Fuessen. Modell in Spielergroesse gebaut wie in der Vorlage, Darstellung 0,45-fach (ca. 1 Block hoch).
    Einheiten = 1/16 Block, vorn = -z; drei Uniformen."""
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 10, 0), [
                 Part((-4, 10, -1), (8, 3, 5), "role:SUIT_DARK"),                                       # Huefte
                 Part((-4, 13, -1), (8, 7, 5), "role:SUIT"),                                            # Bauch
                 Part((-1, 13, -1.3), (2, 7, 1), "role:SUIT_DARK"),                                     # Streifen
             ]),
             Bone("chest", "body", (0, 20, 1), [
                 Part((-4.5, 20, -1.5), (9, 6, 6), "role:SUIT"),                                        # Brust
                 Part((-1.5, 22, -2.3), (3, 3, 1), "clean:#141414", detail="badge6"),                   # Omnitrix
                 Part((-5, 24.5, -1.5), (3, 2, 6), "role:ACCENT"),                                      # Schulterpolster
                 Part((2, 24.5, -1.5), (3, 2, 6), "role:ACCENT"),
                 Part((-1.5, 25, -1.5), (3, 3, 3), "role:SKIN", rotation=(20, 0, 0), pivot=(0, 26, 0)),  # Hals
             ], rotation=(-8, 0, 0)),
             Bone("head", "chest", (0, 27, 0), [
                 Part((-4, 27, -3), (8, 8, 5), "role:SKIN", inflate=-0.1),                              # Kopf
                 Part((-2, 28, -3.3), (4, 2, 1), "role:SKIN_DARK"),                                     # Mund
                 *side_parts([
                     ((-7, 30, -3.5), (5, 5, 5), "role:SKIN", {"rotation": (0, 0, 12.5), "pivot": (-4.3, 33, -2.6)}),  # Augenhoehle
                     ((-6, 31, -4.3), (3, 3, 3), "role:EYE", {"rotation": (0, 0, 12.5), "pivot": (-4.3, 33, -2.6)}),   # Auge
                     ((-5.2, 32, -4.6), (1, 1, 1), "role:PUPIL", {"rotation": (0, 0, 12.5), "pivot": (-4.3, 33, -2.6)}),
                     ((-6, 34.2, -3.8), (3, 1, 1), "role:SKIN_DARK", {"rotation": (0, 0, -5), "pivot": (-4.5, 34.5, -3.3)}),  # Braue
                 ], -1),
                 *side_parts([
                     ((-7, 30, -3.5), (5, 5, 5), "role:SKIN", {"rotation": (0, 0, 12.5), "pivot": (-4.3, 33, -2.6)}),
                     ((-6, 31, -4.3), (3, 3, 3), "role:EYE", {"rotation": (0, 0, 12.5), "pivot": (-4.3, 33, -2.6)}),
                     ((-5.2, 32, -4.6), (1, 1, 1), "role:PUPIL", {"rotation": (0, 0, 12.5), "pivot": (-4.3, 33, -2.6)}),
                     ((-6, 34.2, -3.8), (3, 1, 1), "role:SKIN_DARK", {"rotation": (0, 0, -5), "pivot": (-4.5, 34.5, -3.3)}),
                 ], 1),
             ]),
             ]
    for side_name, side in (("right", -1), ("left", 1)):
        bones += [
            Bone(f"{side_name}_arm", "chest", (4.5 * side, 24, 1), side_parts([
                ((-8, 16.5, 0), (3, 9, 3), "role:SKIN", {"rotation": (5, 0, 12), "pivot": (-4.5, 24, 1)}),   # Oberarm
            ], side)),
            Bone(f"{side_name}_forearm", f"{side_name}_arm", (7 * side, 17, 1), side_parts([
                ((-9, 12, -0.5), (4, 6, 4), "role:SUIT_DARK", {"inflate": 0.15}),                        # Handschuh
                ((-10.5, 7.5, -2), (6, 5, 6), "role:SKIN", {"inflate": -0.1}),                           # grosse Hand
            ], side), rotation=(-5, 0, 0)),
            Bone(f"{side_name}_leg", "root", (2.1 * side, 13.5, 1), side_parts([
                ((-3.5, 7, -0.5), (3, 7, 3), "role:SUIT_DARK"),                                         # Oberschenkel
            ], side)),
            Bone(f"{side_name}_shin", f"{side_name}_leg", (2.1 * side, 7, 1), side_parts([
                ((-3.5, 2, -0.5), (3, 5, 3), "role:SUIT_DARK", {"inflate": -0.05}),                     # Wade
                ((-5.5, 0, -4.5), (5, 2, 8), "role:SKIN", {"inflate": -0.1}),                           # langer Fuss
            ], side)),
        ]
    alien = Alien("grey_matter", (128, 128), bones, "#95A5A6", style="small", glow=True, density=2, render_scale=0.45,
                  arms=("role:SKIN", "role:SKIN"), uniforms=GREY_MATTER_UNIFORMS)
    pack_uvs(alien)
    return alien


# Diese Aliens kommen inzwischen 1:1 aus Alien Evolution (tools/import_alienevo.py) — der Generator darf ihre Dateien
# nicht mehr ueberschreiben. Die Bauplaene bleiben als Referenz/Fallback (--legacy).
IMPORTED = {"heatblast", "xlr8", "four_arms", "diamondhead", "grey_matter"}


def build_aliens(legacy: bool = False) -> list[Alien]:
    aliens = [heatblast(), xlr8(), four_arms(), diamondhead(), grey_matter()]
    return aliens if legacy else [a for a in aliens if a.name not in IMPORTED]


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
    tail = [n for n in ("tail_1", "tail_2", "tail_3", "tail_4") if n in names]
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
    files: dict[Path, object] = {
        ASSETS / "geo" / base / f"{alien.name}.geo.json": build_geo(alien),
        ASSETS / "animations" / base / f"{alien.name}.animation.json": build_animations(alien),
    }
    render: dict = {"scale": alien.render_scale}
    variants = list(alien.uniforms) if alien.uniforms else [None]
    for index, uniform in enumerate(variants):
        variant = with_uniform(alien, uniform) if uniform else alien
        suffix = "" if index == 0 else f"_{uniform}"
        color, glow = build_textures(variant, seed)
        files[ASSETS / "textures" / base / f"{alien.name}{suffix}.png"] = color
        # Leuchtmaske nur mit mindestens einem Pixel — GeckoLib verweigert leere Masken (None = Datei entfernen)
        files[ASSETS / "textures" / base / f"{alien.name}{suffix}_glowmask.png"] = glow if alien.glow and glow.getbbox() else None
        files[ASSETS / "textures" / base / f"{alien.name}{suffix}_arms.png"] = build_arm_skin(variant) if variant.arms else None
    if alien.uniforms:
        render["uniforms"] = variants
    files[ASSETS / "alien_render" / f"{alien.name}.json"] = render
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
    parser.add_argument("--legacy", action="store_true",
                        help="auch die eigenen Bauplaene der importierten Aliens schreiben (ueberschreibt den AE-Import)")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    try:
        aliens = build_aliens(args.legacy)
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
