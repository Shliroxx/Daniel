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
    style: str = "normal"   # normal | heat (bestimmt Animations-Charakter)
    glow: bool = False      # Leuchtmaske schreiben


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


def painter(material: str) -> Callable[[Canvas, Face, Cube, random.Random, Noise], None]:
    if material.startswith("flat:"):
        return paint_flat
    if material in ROCK:
        return paint_rock
    return {"magma": paint_magma, "flame": paint_flame}[material]


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


def faces_of(cube: Part) -> list[Face]:
    u, v = cube.uv
    w, h, d = cube.size
    return [
        Face("up", u + d, v, w, d),
        Face("down", u + d + w, v, w, d),
        Face("east", u, v + d, d, h),
        Face("north", u + d, v + d, w, h),
        Face("west", u + d + w, v + d, d, h),
        Face("south", u + 2 * d + w, v + d, w, h),
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


def build_textures(alien: Alien, seed: int) -> tuple[Image.Image, Image.Image]:
    """Farbtextur und Leuchtmaske der detaillierten Bauweise (Wuerfel vom Typ Part)."""
    canvas = Canvas(alien.texture_size)
    for b in alien.bones:
        for i, cube in enumerate(b.cubes):
            if cube.mirror and alien.style == "normal":
                continue  # einfache Aliens teilen die UV mit der gespiegelten Seite
            rng = random.Random(zlib.crc32(f"{seed}:{alien.name}:{b.name}:{i}".encode()))
            noise = Noise(zlib.crc32(f"{alien.name}:{b.name}:{i}".encode()))
            paint = painter(cube.material)
            faces = faces_of(cube)
            for face in faces:
                paint(canvas, face, cube, rng, noise)
            if cube.detail:
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

def humanoid(skin: str, head: str, legs: str, *, body_uv=(16, 16), head_cube: Part | None = None,
             head_detail: str = "eyes") -> list[Bone]:
    """Grundgeruest im Spieler-Layout (64x64), Fuesse auf y=0, Kopf bis y=32."""
    skin, head, legs = f"flat:{skin}", f"flat:{head}", f"flat:{legs}"
    return [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 24, 0), [Part((-4, 12, -2), (8, 12, 4), skin, body_uv, "omnitrix_dot")]),
        Bone("head", "body", (0, 24, 0), [head_cube or Part((-4, 24, -4), (8, 8, 8), head, (0, 0), head_detail)]),
        Bone("right_arm", "body", (-5, 22, 0), [Part((-8, 12, -2), (4, 12, 4), skin, (40, 16))]),
        Bone("left_arm", "body", (5, 22, 0), [Part((4, 12, -2), (4, 12, 4), skin, (40, 16), mirror=True)]),
        Bone("right_leg", "root", (-1.9, 12, 0), [Part((-3.9, 0, -2), (4, 12, 4), legs, (0, 16))]),
        Bone("left_leg", "root", (1.9, 12, 0), [Part((-0.1, 0, -2), (4, 12, 4), legs, (0, 16), mirror=True)]),
    ]


def heatblast() -> Alien:
    """Heatblast: schlanker Koerper aus Magmagestein, Platten mit gluehenden Rissen, Kopf als lodernde Flamme."""
    bones = [Bone("root", None, (0, 0, 0)),
             Bone("body", "root", (0, 12, 0), [
                 Part((-3.5, 10.5, -2), (7, 3, 4), "rock_dark"),                 # Becken
                 Part((-3, 13, -1.5), (6, 4, 3), "magma"),                      # Taille (Glut zwischen den Platten)
                 Part((-4.5, 17, -2.5), (9, 7, 5), "rock"),                     # Brustkorb
                 Part((-2, 19, -3.7), (4, 4, 1), "rock_dark", detail="omnitrix_badge"),  # Omnitrix-Symbol
                 Part((-4, 18.5, -3.2), (4, 4, 1), "rock_light", rotation=(0, 8, 0), pivot=(-2, 20, -3)),
                 Part((0, 18.5, -3.2), (4, 4, 1), "rock_light", rotation=(0, -8, 0), pivot=(2, 20, -3)),
                 Part((-2.5, 14, -2.2), (5, 2, 1), "rock_dark"),                # Bauchplatte
                 Part((-3.5, 16.5, 2.2), (7, 7, 1), "rock_dark"),               # Rueckenplatte
                 Part((-1, 17, 3), (2, 6, 1), "magma"),                         # Glutnaht am Ruecken
                 Part((-1.5, 24, -1.5), (3, 1, 3), "magma"),                    # Hals
             ])]
    for side, s in (("right", -1), ("left", 1)):
        mirror = side == "left"
        sx = lambda x, w: x if s < 0 else -x - w  # noqa: E731 — x-Spiegelung fuer Wuerfel-Ursprung
        bones += [
            Bone(f"{side}_arm", "body", (5.5 * s, 22.5, 0), [
                Part((sx(-9, 5), 20.5, -2.5), (5, 4, 5), "rock_light", rotation=(0, 0, -12 * s), pivot=(6.5 * s, 22.5, 0),
                     mirror=mirror),                                            # Schulterpanzer
                Part((sx(-7.5, 3), 16, -1.5), (3, 6, 3), "rock", mirror=mirror),  # Oberarm
            ]),
            Bone(f"{side}_forearm", f"{side}_arm", (6 * s, 16.5, 0), [
                Part((sx(-7.75, 3), 11, -1.75), (3, 6, 3), "rock_dark", inflate=0.3, mirror=mirror),   # Armschiene
                Part((sx(-7.5, 3), 8.5, -1.5), (3, 3, 3), "magma", mirror=mirror),                    # gluehende Faust
            ]),
            Bone(f"{side}_leg", "root", (2 * s, 11, 0), [
                Part((sx(-3.6, 3), 5.5, -1.5), (3, 6, 3), "rock", mirror=mirror),                     # Oberschenkel
                Part((sx(-3.85, 3), 5, -2.3), (3, 2, 1), "rock_light", inflate=0.2, mirror=mirror),   # Knieplatte
            ]),
            Bone(f"{side}_shin", f"{side}_leg", (2 * s, 5.5, 0), [
                Part((sx(-3.6, 3), 1.5, -1.5), (3, 4, 3), "rock_dark", mirror=mirror),                # Schienbein
                Part((sx(-3.6, 3), 3, -1.2), (3, 2, 2), "magma", inflate=-0.1, mirror=mirror),        # Glutfuge
                Part((sx(-4.1, 4), 0, -3.2), (4, 2, 5), "rock", mirror=mirror),                       # Fuss
            ]),
        ]
    bones += [
        Bone("head", "body", (0, 25, 0), [
            Part((-3.5, 25, -3.5), (7, 5, 6), "face", detail="heat_face"),     # Gesicht
            Part((-2.5, 24.5, -3.8), (5, 1, 1), "magma"),                      # Kinnglut
        ]),
        Bone("flame_base", "head", (0, 29.5, 1), [
            Part((-4, 29.5, -2.5), (8, 3, 7), "flame"),                                   # Krone
            Part((-3, 29, -3.9), (6, 2, 2), "flame"),                                     # Stirnflamme
            Part((-5.2, 26.5, -1.5), (2, 5, 4), "flame", rotation=(0, 0, 14), pivot=(-4.2, 27, 0)),
            Part((3.2, 26.5, -1.5), (2, 5, 4), "flame", rotation=(0, 0, -14), pivot=(4.2, 27, 0), mirror=True),
            Part((-3, 25.5, 2.4), (6, 6, 2), "flame"),                                    # Hinterkopf
        ]),
        Bone("flame_mid", "flame_base", (0, 32, 2), [
            Part((-3, 32, -0.5), (6, 4, 5), "flame"),
            Part((-4.2, 33, 0.5), (2, 3, 2), "flame", rotation=(0, 0, 22), pivot=(-3.2, 33, 1.5)),
            Part((2.2, 33, 0.5), (2, 3, 2), "flame", rotation=(0, 0, -22), pivot=(3.2, 33, 1.5), mirror=True),
        ], rotation=(-20, 0, 0)),
        Bone("flame_tip", "flame_mid", (0, 35.5, 3), [Part((-2, 35.5, 1), (4, 4, 4), "flame")], rotation=(-18, 0, 0)),
        Bone("flame_spike", "flame_tip", (0, 39, 4), [Part((-1, 39, 2), (2, 3, 2), "flame")], rotation=(-22, 0, 0)),
    ]
    alien = Alien("heatblast", (128, 64), bones, "#FF6A00", style="heat", glow=True)
    pack_uvs(alien)
    return alien


def build_aliens() -> list[Alien]:
    aliens = [heatblast()]

    # XLR8: schlank, Visier, Schwanz (PLACEHOLDER bis zum Rework)
    b = humanoid("#1B3C8C", "#14213D", "#0B1A3A", head_detail="visor")
    b.append(Bone("visor", "head", (0, 28, -4), [Part((-4, 26, -6), (8, 3, 2), "flat:#7FD4FF", (32, 0))]))
    b.append(Bone("tail", "body", (0, 13, 2), [Part((-1, 12, 2), (2, 2, 9), "flat:#1B3C8C", (0, 32))]))
    aliens.append(Alien("xlr8", (64, 64), b, "#1E90FF"))

    # Vierarm: breiter Rumpf, vier Arme, vier Augen (128x64)
    b = [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 24, 0), [Part((-5, 12, -2.5), (10, 12, 5), "flat:#B22A1E", (64, 0), "omnitrix_dot")]),
        Bone("head", "body", (0, 24, 0), [Part((-4, 24, -4), (8, 8, 8), "flat:#C0392B", (0, 0), "four_eyes")]),
        Bone("right_arm", "body", (-6, 23, 0), [Part((-10, 13, -2), (4, 11, 4), "flat:#C0392B", (40, 16))]),
        Bone("left_arm", "body", (6, 23, 0), [Part((6, 13, -2), (4, 11, 4), "flat:#C0392B", (40, 16), mirror=True)]),
        Bone("right_lower_arm", "body", (-5.5, 18, 0), [Part((-8.5, 9, -1.5), (3, 9, 3), "flat:#A93226", (64, 32))]),
        Bone("left_lower_arm", "body", (5.5, 18, 0), [Part((5.5, 9, -1.5), (3, 9, 3), "flat:#A93226", (64, 32), mirror=True)]),
        Bone("right_leg", "root", (-2.4, 12, 0), [Part((-4.9, 0, -2.5), (5, 12, 5), "flat:#1C1C1C", (0, 16))]),
        Bone("left_leg", "root", (2.4, 12, 0), [Part((-0.1, 0, -2.5), (5, 12, 5), "flat:#1C1C1C", (0, 16), mirror=True)]),
    ]
    aliens.append(Alien("four_arms", (128, 64), b, "#C0392B"))

    # Diamondhead: Kristallkoerper mit Kopf- und Schulterspitzen
    b = humanoid("#2ECC71", "#58F0A0", "#1E9E5E")
    b.append(Bone("crystal_crest", "head", (0, 32, 0), [Part((-2, 32, -2), (4, 6, 4), "flat:#A8FFD4", (32, 0))]))
    b.append(Bone("right_spike", "right_arm", (-6, 24, 0), [Part((-7, 24, -1), (2, 4, 2), "flat:#A8FFD4", (0, 32))]))
    b.append(Bone("left_spike", "left_arm", (6, 24, 0), [Part((5, 24, -1), (2, 4, 2), "flat:#A8FFD4", (0, 32), mirror=True)]))
    aliens.append(Alien("diamondhead", (64, 64), b, "#2ECC71"))

    # Grey Matter: grosser Kopf, grosse Augen (128x64)
    b = humanoid("#95A5A6", "#A9B7B8", "#7F8C8D",
                 head_cube=Part((-5, 24, -5), (10, 9, 10), "flat:#A9B7B8", (64, 0), "big_eyes"))
    aliens.append(Alien("grey_matter", (128, 64), b, "#95A5A6"))
    return aliens


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
    A: dict[str, dict] = {}

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
        return g

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
    if alien.glow:
        files[ASSETS / "textures" / base / f"{alien.name}_glowmask.png"] = glow
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
        missing = [p for p in files if not p.is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p.relative_to(ASSETS))
        stale = []
        for p, content in files.items():
            if p.suffix == ".json" and p.is_file():
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
                if isinstance(content, Image.Image):
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
