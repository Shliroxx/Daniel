#!/usr/bin/env python3
"""Erzeugt das GeckoLib-Modell des ersten Bosses: Dr. Nefarious (Ratchet & Clank), nach der Vorlage der Spiele —
skelettartiger Roboter mit grosser gruener Glaskuppel (Zahnraeder, Satellitenschuessel), Schaedelgesicht mit rot
gluehenden Augen und silbernem Gebiss, violette Panzerung mit orangen Akzenten, Tentakel am Ruecken.
Geometrie, Animationen (idle, walk, laser, rockets, stomp, phase, stunned, overload, death), Textur (4 Pixel je
Einheit) und Leucht-Maske (Augen, Kern, Laser, Raketenschacht, Tentakelspitzen) unter
assets/kingdomomnitrix/{geo,animations,textures}/entity/boss/. Vorn ist -Z.

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_boss_models.py           # schreiben
    python tools/generate_boss_models.py --check   # nur pruefen
"""
from __future__ import annotations

import argparse
import math
import json
import logging
import random
import sys
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_alien_models import ASSETS, rgb  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("boss_models")

# --- Dr. Nefarious (Ratchet & Clank) -------------------------------------------------------------------
# Vorlage (Ratchet & Clank Wiki): humanoider, skelettartiger Roboter; riesiger Kopf mit grauem, schaedelartigem
# Gesicht, grossem silbernem Gebiss und rot gluehenden Augen; darueber eine grosse, gruene, durchsichtige
# Eier-Kuppel mit Zahnraedern, Kolben und einer sich drehenden Satellitenschuessel; violette Ganzkoerper-Panzerung
# mit orangen Akzenten an Knien und Ellbogen; mechanische Tentakel am Ruecken wie Fluegel. Vorn ist -Z.

UNIT_TEXTURE = (128, 128)   # logische Texturgroesse in der .geo.json
DENSITY = 4                 # Pixel je Einheit in der PNG (GeckoLib rechnet UVs normiert)

PALETTE = {
    "armor": ("#2C1243", "#4E2275", "#6E35A3", "#9A62D0"),     # dunkel, Grund, hell, Glanz
    "orange": ("#7A3A0C", "#D9741E", "#F29A3A", "#FFD08A"),
    "metal": ("#3C424B", "#7D8692", "#A9B2BE", "#E1E7EE"),
    "dark": ("#121318", "#24262E", "#373A45", "#5A5E6C"),
    "silver": ("#5C636E", "#B7BFCA", "#D7DEE8", "#FFFFFF"),
    "gold": ("#5E4410", "#B8902C", "#E0B848", "#FFF0A0"),
}
GLASS = (110, 255, 150)
EYE = (255, 40, 30)
CORE_GLOW = (255, 120, 30)
TIP_GLOW = (210, 90, 255)


@dataclass
class Box:
    """Quader: Lage/Groesse in Modell-Einheiten, Material, optionales Front-Detail, Drehung um eigenen Drehpunkt."""
    origin: tuple[float, float, float]
    size: tuple[int, int, int]
    material: str
    detail: str | None = None
    rotation: tuple[float, float, float] | None = None
    pivot: tuple[float, float, float] | None = None
    inflate: float = 0.0
    uv: tuple[int, int] = (0, 0)


@dataclass
class Joint:
    name: str
    parent: str | None
    pivot: tuple[float, float, float]
    boxes: list = field(default_factory=list)
    rotation: tuple[float, float, float] | None = None


def mirror(boxes: list[Box]) -> list[Box]:
    """Spiegelt Quader an x = 0 (linke Koerperseite aus der rechten)."""
    out = []
    for b in boxes:
        x0 = -(b.origin[0] + b.size[0])
        rot = None if b.rotation is None else (b.rotation[0], -b.rotation[1], -b.rotation[2])
        piv = None if b.pivot is None else (-b.pivot[0], b.pivot[1], b.pivot[2])
        out.append(Box((x0, b.origin[1], b.origin[2]), b.size, b.material, b.detail, rot, piv, b.inflate))
    return out


def arm(side: int) -> tuple[list[Box], list[Box]]:
    """Schulterplatte und Arm einer Seite (side = 1 links/+x, −1 rechts)."""
    shoulder = [Box((7, 40, -3.5), (6, 5, 7), "armor", "trim_bottom")]
    limb = [
        Box((8.5, 31, -1.5), (3, 10, 3), "dark"),                      # Oberarm (duenn, skelettartig)
        Box((8, 28.5, -2), (4, 3, 4), "orange", "rivets"),            # Ellbogen
        Box((8, 20, -2), (4, 9, 4), "armor", "plates"),               # Unterarm
        Box((8, 16, -2), (4, 4, 4), "metal"),                         # Hand
        Box((8, 12, -2), (1, 4, 1), "metal"),                         # Finger
        Box((9.5, 12, -2), (1, 4, 1), "metal"),
        Box((11, 12, -2), (1, 4, 1), "metal"),
    ]
    if side == 1:
        limb.append(Box((9, 16.5, -5), (2, 2, 3), "dark", "barrel"))   # Laser-Emitter in der linken Hand
    else:
        limb.append(Box((8.5, 21, -2.6), (3, 6, 1), "dark", "vents"))  # Raketenschacht am rechten Unterarm
    if side == -1:
        return mirror(shoulder), mirror(limb)
    return shoulder, limb


def leg(side: int) -> list[Box]:
    boxes = [
        Box((1.5, 15, -2), (4, 11, 4), "armor", "plates"),            # Oberschenkel
        Box((1.5, 11.5, -2.5), (4, 3, 5), "orange", "rivets"),        # Knie
        Box((2, 2, -1.5), (3, 10, 3), "dark"),                        # Schienbein
        Box((1.5, 0, -4.5), (4, 2, 7), "metal", "toes"),              # Fuss (nach vorn)
    ]
    return mirror(boxes) if side == -1 else boxes


def tendril(side: int, upper: bool) -> list[Joint]:
    """Zwei Gelenke je Tentakel; leuchtende Spitze."""
    sign = 1 if side == 1 else -1
    base = "tendril_%s%d" % ("l" if side == 1 else "r", 1 if upper else 2)
    y = 41 if upper else 36
    tilt = (-40 if upper else -55, 0, sign * (35 if upper else 60))
    root = Joint(base, "body", (sign * 3, y, 4), [Box((sign * 3 - 1, y, 3.5), (2, 9, 2), "dark", "segments")], tilt)
    tip = Joint(base + "_tip", base, (sign * 3, y + 9, 4.5), [
        Box((sign * 3 - 1, y + 9, 3.5), (2, 7, 2), "armor", "segments"),
        Box((sign * 3 - 0.5, y + 16, 4), (1, 3, 1), "dark", "tip"),
    ], (-20, 0, sign * -25))
    return [root, tip]


def nefarious() -> list[Joint]:
    l_shoulder, l_arm = arm(1)
    r_shoulder, r_arm = arm(-1)
    joints = [
        Joint("root", None, (0, 0, 0)),
        Joint("body", "root", (0, 26, 0), [
            Box((-4.5, 24, -3), (9, 4, 6), "armor", "trim_top"),          # Becken
            Box((-1.5, 28, -1.5), (3, 6, 3), "dark", "segments"),         # Wirbelsaeule (sichtbar)
            Box((-6, 34, -3.5), (12, 10, 7), "armor", "ribs"),            # Brustpanzer
            Box((-7.5, 43, -4), (15, 2, 8), "dark", "trim_bottom"),       # Kragen
            Box((-1.5, 44, -1.5), (3, 4, 3), "dark", "segments"),         # Hals
        ]),
        Joint("core", "body", (0, 39, 4.5), [Box((-2.5, 36, 3.5), (5, 6, 2), "dark", "core")]),
        Joint("left_shoulder", "body", (10, 42, 0), l_shoulder),
        Joint("left_arm", "left_shoulder", (10, 41, 0), l_arm),
        Joint("right_shoulder", "body", (-10, 42, 0), r_shoulder),
        Joint("right_arm", "right_shoulder", (-10, 41, 0), r_arm),
        Joint("left_leg", "root", (3.5, 26, 0), leg(1)),
        Joint("right_leg", "root", (-3.5, 26, 0), leg(-1)),
        Joint("cockpit", "body", (0, 47, 0), [
            Box((-6, 48, -5), (12, 8, 10), "metal", "skull"),             # grosses Schaedelgesicht
            Box((-7, 50, -1.5), (1, 3, 3), "dark", "bolt"),               # Schlaefenbolzen
            Box((6, 50, -1.5), (1, 3, 3), "dark", "bolt"),
            Box((-6.5, 55.5, -5.5), (13, 1, 11), "dark", "trim_top"),     # Kuppelfassung
            Box((-3.5, 57, -2.5), (1, 6, 6), "gold", "gear"),             # Zahnraeder in der Kuppel
            Box((2.5, 58, -1.5), (1, 5, 5), "gold", "gear"),
            Box((-1, 59, 2), (2, 2, 1), "gold", "gear"),
            Box((-0.5, 56.5, 1), (1, 7, 1), "metal", "segments"),         # Kolben
            Box((1.5, 56.5, -3), (1, 4, 1), "metal", "segments"),
            # Eier-Kuppel aus Schichten (unten schmal, Mitte am breitesten, oben spitz zulaufend)
            Box((-6, 56, -5.5), (12, 2, 11), "glass", "glass"),
            Box((-7, 58, -6.5), (14, 6, 13), "glass", "glass"),
            Box((-6, 64, -5.5), (12, 3, 11), "glass", "glass"),
            Box((-4.5, 67, -4), (9, 2, 8), "glass", "glass"),
            Box((-2.5, 69, -2), (5, 1, 4), "glass", "glass"),
        ]),
        Joint("jaw", "cockpit", (0, 49, 3), [
            Box((-6.5, 44.5, -6), (13, 4, 10), "silver", "teeth"),        # grosses silbernes Gebiss
        ]),
        Joint("antenna", "cockpit", (0, 63, 0), [
            Box((-0.5, 62, -0.5), (1, 3, 1), "metal"),                    # Schuessel-Stiel
            Box((-2.5, 65, -2.5), (5, 1, 5), "metal", "dish"),            # Satellitenschuessel
        ]),
    ]
    for side in (1, -1):
        for upper in (True, False):
            joints += tendril(side, upper)
    return joints


def pack(joints: list[Joint]) -> None:
    """Box-UV je Quader (Breite 2·(t+b), Hoehe t+h), Regal-Packer in UNIT_TEXTURE."""
    boxes = [b for j in joints for b in j.boxes]
    x = y = shelf = 0
    for b in sorted(boxes, key=lambda b: -(b.size[2] + b.size[1])):
        w = 2 * (b.size[2] + b.size[0])
        h = b.size[2] + b.size[1]
        if x + w > UNIT_TEXTURE[0]:
            x, y, shelf = 0, y + shelf, 0
        if y + h > UNIT_TEXTURE[1]:
            raise ValueError("Nefarious-Textur zu klein")
        b.uv = (x, y)
        x += w
        shelf = max(shelf, h)


def shade(color: str, amount: float) -> tuple[int, int, int]:
    r, g, b = rgb(color)
    return tuple(max(0, min(255, round(c * amount))) for c in (r, g, b))


def face_rects(b: Box) -> dict[str, tuple[float, float, float, float]]:
    """Box-UV-Flaechen eines Quaders: Name -> (x, y, Breite, Hoehe) in Einheiten."""
    w, h, d = b.size
    u, v = b.uv
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "front": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(img: Image.Image, glow: Image.Image, b: Box) -> None:
    """Malt alle sechs Flaechen eines Quaders (Box-UV) mit Material und Front-Detail."""
    k = DENSITY
    faces = face_rects(b)
    rng = random.Random(f"{b.origin}{b.size}{b.material}")
    for name, (fx, fy, fw, fh) in faces.items():
        X0, Y0, W, H = int(fx * k), int(fy * k), int(fw * k), int(fh * k)
        light = {"top": 1.15, "bottom": 0.6, "front": 1.0, "back": 0.8, "east": 0.85, "west": 0.85}[name]
        for py in range(H):
            for px in range(W):
                color, alpha, lit = surface(b, name, px, py, W, H, light, rng)
                img.putpixel((X0 + px, Y0 + py), color + (alpha,))
                if lit:
                    glow.putpixel((X0 + px, Y0 + py), color + (255,))


def surface(b: Box, face: str, px: int, py: int, W: int, H: int, light: float, rng: random.Random):
    """Farbe eines Pixels: Material (Verlauf, Kanten, Platten) plus Front-Details. -> (rgb, alpha, leuchtet)"""
    k = DENSITY
    if b.material == "glass":
        edge = px < 1 or px >= W - 1 or (face in ("top", "bottom") and (py < 1 or py >= H - 1))
        streak = face in ("front", "east", "top") and abs((px - py * 0.6) - W * 0.25) < 3
        if edge:
            return (40, 130, 70), 220, False
        if streak:
            return (215, 255, 225), 170, False
        return tuple(round(c * (0.75 + 0.25 * (py / max(1, H)))) for c in GLASS), 95, False
    dark, base, high, shine = PALETTE[b.material]
    # Verlauf: oben heller, unten dunkler; Kanten dunkel, Oberkante Glanz
    t = 1.0 - py / max(1, H - 1)
    color = shade(base, light * (0.82 + 0.3 * t))
    if py == 0 and face != "bottom":
        color = shade(shine, light)
    if px == 0 or px == W - 1 or py == H - 1:
        color = shade(dark, light)
    if rng.random() < 0.04:
        color = tuple(min(255, c + 12) for c in color)
    lit = False
    detail = b.detail
    if detail == "plates" and face in ("front", "east", "west", "back") and py % (3 * k) == 0:
        color = shade(dark, light)
    if detail == "plates" and face == "front" and py % (3 * k) == 1:
        color = shade(high, light)
    if detail == "ribs" and face == "front":
        # Rippenpanzer: waagerechte Rippen, Mittelgrat, Herz-Emblem dunkel
        if py % (2 * k) == 0 and abs(px - W / 2) > k:
            color = shade(dark, 1.0)
        if abs(px - W / 2) < k * 0.5:
            color = shade(high, 1.1)
    if detail == "trim_bottom" and py >= H - k:
        color = shade(PALETTE["orange"][1], light)
    if detail == "trim_top" and py < k:
        color = shade(PALETTE["orange"][1], light)
    if detail == "rivets" and (px % (2 * k), py % (2 * k)) == (k, k):
        color = shade(PALETTE["dark"][0], 1.0)
    if detail == "segments" and py % (2 * k) < 2:
        color = shade(dark, 0.8)
    if detail == "toes" and face == "front" and px % (k + 1) == 0:
        color = shade(dark, 1.0)
    if detail == "vents" and face == "front":
        color = (25, 25, 30) if py % 3 else shade("#FF6A1A", 1.0)
        lit = py % 3 == 0
    if detail == "barrel" and face == "front":
        r = ((px - W / 2) ** 2 + (py - H / 2) ** 2) ** 0.5
        if r < W * 0.4:
            color, lit = (255, 60, 50) if r > W * 0.15 else (255, 220, 200), True
    if detail == "core" and face == "back":
        r = ((px - W / 2) ** 2 + (py - H / 2) ** 2) ** 0.5
        if r < min(W, H) * 0.45:
            color, lit = CORE_GLOW if r > min(W, H) * 0.18 else (255, 230, 170), True
    if detail == "tip":
        color, lit = TIP_GLOW, True
    if detail == "gear":
        cx, cy = W / 2, H / 2
        r = ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5
        tooth = (int(px / 2) + int(py / 2)) % 2 == 0
        if r > min(W, H) * 0.48 or (r > min(W, H) * 0.38 and not tooth):
            return (0, 0, 0), 0, False
        if r < min(W, H) * 0.12:
            color = shade(dark, 1.0)
    if detail == "dish" and face == "top":
        r = ((px - W / 2) ** 2 + (py - H / 2) ** 2) ** 0.5
        color = shade(shine if r < W * 0.15 else high if r < W * 0.35 else base, 1.0)
    if detail == "skull" and face == "front":
        color, lit = skull(px, py, W, H, color)
    if detail == "teeth" and face == "front":
        # silbernes Gebiss: zwei Zahnreihen, dunkle Fugen, Grinsen
        gap = H // 2
        if abs(py - gap) <= 1:
            color = (20, 20, 24)
        elif px % (k + 2) == 0:
            color = shade(dark, 1.0)
        else:
            color = shade(shine if py < gap else high, 1.0)
    return color, 255, lit


def skull(px: int, py: int, W: int, H: int, color: tuple[int, int, int]):
    """Schaedelgesicht: tiefe Augenhoehlen, rot gluehende Augen, schraege wuetende Brauen, Nasenschlitz, Wangennaehte."""
    k = DENSITY
    for side in (-1, 1):
        cx = W / 2 + side * W * 0.24
        cy = H * 0.42
        # Braue: schraeg nach innen unten (wuetend)
        brow_y = H * 0.18 + (abs(px - W / 2) / (W / 2)) * -k * 0.9 + k * 1.2
        if abs(px - cx) < W * 0.2 and abs(py - brow_y) < k * 0.6:
            return (35, 36, 42), False
        rx, ry = (px - cx) / (W * 0.16), (py - cy) / (H * 0.17)
        d = rx * rx + ry * ry
        if d < 0.35:
            return EYE if d > 0.08 else (255, 200, 170), True
        if d < 1.0:
            return (18, 18, 22), False
    if abs(px - W / 2) < k * 0.5 and H * 0.62 < py < H * 0.78:
        return (25, 25, 30), False
    if py > H * 0.8 and (px < k or px > W - k):
        return tuple(round(c * 0.75) for c in color), False
    return color, False


DAMAGE_MATERIALS = {"glass", "armor", "metal", "silver", "orange"}


def paint_damage(img: Image.Image, b: Box, stage: int, rng: random.Random) -> None:
    """Schadens-Overlay (gleiche UVs wie die Textur, sonst durchsichtig): Risse in Glas und Panzer, Brandflecken,
    blanke Kratzer. Stufe 2 enthaelt Stufe 1 (gleicher Zufall) und legt mehr und tiefere Schaeden darauf."""
    if b.material not in DAMAGE_MATERIALS:
        return
    k = DENSITY
    glass = b.material == "glass"
    for name, (fx, fy, fw, fh) in face_rects(b).items():
        X0, Y0, W, H = int(fx * k), int(fy * k), int(fw * k), int(fh * k)
        if W < 6 or H < 6:
            continue
        area = W * H
        cracks = max(1, area // (900 if glass else 1400))
        for level in (1, 2):
            count = cracks * (1 if level == 1 else 2)
            for _ in range(count):
                start = (rng.randrange(W), rng.randrange(H))
                length = rng.randint(min(W, H) // 2, max(W, H))
                scorch = not glass and rng.random() < 0.35
                if level > stage:
                    continue  # Zufall trotzdem verbrauchen, damit Stufe 2 die Risse von Stufe 1 behaelt
                if scorch:
                    scorch_mark(img, X0, Y0, W, H, start, length // 3, rng)
                else:
                    crack(img, X0, Y0, W, H, start, length, glass, rng)


def crack(img: Image.Image, X0: int, Y0: int, W: int, H: int, start: tuple[int, int], length: int, glass: bool,
          rng: random.Random) -> None:
    """Zufalls-Riss mit Abzweigungen: Glas hell gesplittert, Metall dunkle Kerbe mit heller Kante."""
    x, y = start
    angle = rng.uniform(0, 6.283)
    for step in range(length):
        angle += rng.uniform(-0.6, 0.6)
        x += round(math.cos(angle))
        y += round(math.sin(angle))
        if not (0 <= x < W and 0 <= y < H):
            return
        if glass:
            img.putpixel((X0 + x, Y0 + y), (235, 255, 240, 230))
        else:
            img.putpixel((X0 + x, Y0 + y), (14, 12, 16, 235))
            if y + 1 < H:
                img.putpixel((X0 + x, Y0 + y + 1), (200, 200, 210, 150))
        if step > 3 and rng.random() < 0.06:
            crack(img, X0, Y0, W, H, (x, y), length // 3, glass, rng)


def scorch_mark(img: Image.Image, X0: int, Y0: int, W: int, H: int, center: tuple[int, int], radius: int,
                rng: random.Random) -> None:
    """Russiger Brandfleck mit ausgefranstem Rand."""
    radius = max(2, radius)
    cx, cy = center
    for py in range(max(0, cy - radius), min(H, cy + radius + 1)):
        for px in range(max(0, cx - radius), min(W, cx + radius + 1)):
            d = ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5 / radius
            if d < 1 and rng.random() > d * 0.8:
                img.putpixel((X0 + px, Y0 + py), (20, 16, 14, round(200 * (1 - d * 0.6))))


def build_nefarious_geo(joints: list[Joint]) -> dict:
    def cube_json(b: Box) -> dict:
        data = {"origin": [round(c, 3) for c in b.origin], "size": list(b.size), "uv": list(b.uv)}
        if b.inflate:
            data["inflate"] = b.inflate
        if b.rotation:
            data["rotation"] = list(b.rotation)
            data["pivot"] = list(b.pivot or b.origin)
        return data

    bones = []
    for j in joints:
        bone: dict = {"name": j.name, "pivot": list(j.pivot)}
        if j.parent:
            bone["parent"] = j.parent
        if j.rotation:
            bone["rotation"] = list(j.rotation)
        if j.boxes:
            bone["cubes"] = [cube_json(b) for b in j.boxes]
        bones.append(bone)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.kingdomomnitrix.boss_nefarious_mech", "texture_width": UNIT_TEXTURE[0],
                        "texture_height": UNIT_TEXTURE[1], "visible_bounds_width": 6, "visible_bounds_height": 6,
                        "visible_bounds_offset": [0, 2, 0]},
        "bones": bones}]}


def track(*keys: tuple[float, tuple[float, float, float]]) -> dict:
    return {f"{t:.2f}": list(v) for t, v in keys}


def build_animations() -> dict:
    """Kampf-Animationen (Namen vom NefariousEntity erwartet) plus Leerlauf-Details: Schuessel dreht, Tentakel wiegen."""
    def anim(length: float, bones: dict, loop: bool) -> dict:
        return {"loop": loop, "animation_length": length, "bones": bones}

    def tendrils(amount: float, length: float) -> dict:
        out = {}
        for name, sign in (("tendril_l1", 1), ("tendril_l2", 1), ("tendril_r1", -1), ("tendril_r2", -1)):
            out[name] = {"rotation": track((0.0, (0, 0, 0)), (length / 2, (amount, 0, sign * amount)), (length, (0, 0, 0)))}
            out[name + "_tip"] = {"rotation": track((0.0, (0, 0, 0)), (length / 2, (-amount * 1.5, 0, 0)), (length, (0, 0, 0)))}
        return out

    idle = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.5, (2, 0, 0)), (3.0, (0, 0, 0)))},
            "cockpit": {"rotation": track((0.0, (0, 0, 0)), (1.5, (-3, 4, 0)), (3.0, (0, 0, 0)))},
            "jaw": {"rotation": track((0.0, (0, 0, 0)), (1.4, (6, 0, 0)), (1.6, (0, 0, 0)), (3.0, (0, 0, 0)))},
            "core": {"scale": track((0.0, (1, 1, 1)), (0.75, (1.08, 1.08, 1.08)), (1.5, (1, 1, 1)))},
            "antenna": {"rotation": track((0.0, (0, 0, 0)), (3.0, (0, 360, 0)))},
            **tendrils(8, 3.0)}
    walk = {"left_leg": {"rotation": track((0.0, (-22, 0, 0)), (0.6, (22, 0, 0)), (1.2, (-22, 0, 0)))},
            "right_leg": {"rotation": track((0.0, (22, 0, 0)), (0.6, (-22, 0, 0)), (1.2, (22, 0, 0)))},
            "left_arm": {"rotation": track((0.0, (15, 0, 0)), (0.6, (-15, 0, 0)), (1.2, (15, 0, 0)))},
            "right_arm": {"rotation": track((0.0, (-15, 0, 0)), (0.6, (15, 0, 0)), (1.2, (-15, 0, 0)))},
            "body": {"rotation": track((0.0, (4, 0, -3)), (0.6, (4, 0, 3)), (1.2, (4, 0, -3))),
                     "position": track((0.0, (0, 0, 0)), (0.3, (0, -1, 0)), (0.6, (0, 0, 0)), (0.9, (0, -1, 0)), (1.2, (0, 0, 0)))},
            "antenna": {"rotation": track((0.0, (0, 0, 0)), (1.2, (0, 360, 0)))},
            **tendrils(5, 1.2)}
    laser = {"left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-85, 0, 0)), (2.0, (-85, 0, 0)), (2.5, (0, 0, 0)))},
             "jaw": {"rotation": track((0.0, (0, 0, 0)), (0.4, (18, 0, 0)), (2.0, (18, 0, 0)), (2.5, (0, 0, 0)))},
             "body": {"rotation": track((0.0, (0, 0, 0)), (1.5, (0, -8, 0)), (2.0, (0, 8, 0)), (2.5, (0, 0, 0)))}}
    rockets = {"right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-70, 0, 0)), (1.6, (-70, 0, 0)), (2.0, (0, 0, 0)))},
               "right_shoulder": {"position": track((1.5, (0, 0, 0)), (1.6, (0, 0, 2)), (1.8, (0, 0, 0)))},
               **tendrils(-12, 2.0)}
    stomp = {"body": {"position": track((0.0, (0, 0, 0)), (0.6, (0, 4, 0)), (0.8, (0, -3, 0)), (1.1, (0, 0, 0)))},
             "left_leg": {"rotation": track((0.0, (0, 0, 0)), (0.6, (-40, 0, 0)), (0.8, (5, 0, 0)), (1.1, (0, 0, 0)))},
             "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.6, (-30, 0, -40)), (1.1, (0, 0, 0)))},
             "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.6, (-30, 0, 40)), (1.1, (0, 0, 0)))}}
    phase = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.5, (-15, 0, 0)), (1.5, (-15, 180, 0)), (2.0, (0, 360, 0)))},
             "core": {"scale": track((0.0, (1, 1, 1)), (1.0, (1.6, 1.6, 1.6)), (2.0, (1, 1, 1)))},
             "jaw": {"rotation": track((0.0, (0, 0, 0)), (0.5, (25, 0, 0)), (1.8, (25, 0, 0)), (2.0, (0, 0, 0)))},
             **tendrils(-25, 2.0)}
    stunned = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.3, (20, 0, 8)), (1.0, (18, 0, -8)), (2.0, (20, 0, 8)))},
               "cockpit": {"rotation": track((0.0, (0, 0, 0)), (0.5, (0, 0, 15)), (1.0, (0, 0, -15)), (2.0, (0, 0, 0)))},
               "jaw": {"rotation": track((0.0, (12, 0, 0)), (1.0, (20, 0, 0)), (2.0, (12, 0, 0)))},
               "antenna": {"rotation": track((0.0, (0, 0, 0)), (2.0, (0, 1080, 0)))}}
    overload = {"core": {"scale": track((0.0, (1, 1, 1)), (1.5, (1.4, 1.4, 1.4)), (3.0, (1.9, 1.9, 1.9)))},
                "left_arm": {"rotation": track((0.0, (0, 0, 0)), (1.0, (0, 0, 60)), (3.0, (0, 0, 70)))},
                "right_arm": {"rotation": track((0.0, (0, 0, 0)), (1.0, (0, 0, -60)), (3.0, (0, 0, -70)))},
                "jaw": {"rotation": track((0.0, (0, 0, 0)), (1.0, (30, 0, 0)), (3.0, (30, 0, 0)))},
                **tendrils(-35, 3.0)}
    death = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.0, (25, 0, 15)), (2.0, (80, 0, 20))),
                      "position": track((0.0, (0, 0, 0)), (2.0, (0, -16, 0)))},
             "jaw": {"rotation": track((0.0, (0, 0, 0)), (1.0, (35, 0, 0)))}}
    return {"format_version": "1.8.0", "animations": {
        "idle": anim(3.0, idle, True), "walk": anim(1.2, walk, True), "laser": anim(2.5, laser, False),
        "rockets": anim(2.0, rockets, False), "stomp": anim(1.1, stomp, False), "phase": anim(2.0, phase, False),
        "stunned": anim(2.0, stunned, True), "overload": anim(3.0, overload, False), "death": anim(2.0, death, False),
    }}


def outputs(rng: random.Random) -> dict[Path, object]:
    joints = nefarious()
    pack(joints)
    size = (UNIT_TEXTURE[0] * DENSITY, UNIT_TEXTURE[1] * DENSITY)
    texture = Image.new("RGBA", size, (0, 0, 0, 0))
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    for joint in joints:
        for box in joint.boxes:
            paint_box(texture, glow, box)
    damage = []
    for stage in (1, 2):
        overlay = Image.new("RGBA", size, (0, 0, 0, 0))
        damage_rng = random.Random(4242)
        for joint in joints:
            for box in joint.boxes:
                paint_damage(overlay, box, stage, damage_rng)
        damage.append(overlay)
    base = Path("entity") / "boss"
    return {
        ASSETS / "textures" / base / "nefarious_mech_damage1.png": damage[0],
        ASSETS / "textures" / base / "nefarious_mech_damage2.png": damage[1],
        ASSETS / "geo" / base / "nefarious_mech.geo.json": build_nefarious_geo(joints),
        ASSETS / "animations" / base / "nefarious_mech.animation.json": build_animations(),
        ASSETS / "textures" / base / "nefarious_mech.png": texture,
        ASSETS / "textures" / base / "nefarious_mech_glowmask.png": glow,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alles vorhanden ist")
    parser.add_argument("--seed", type=int, default=1313)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    files = outputs(random.Random(args.seed))
    if args.check:
        missing = [p for p in files if not p.is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p.relative_to(ASSETS))
        LOG.info("%d/%d Boss-Dateien vorhanden", len(files) - len(missing), len(files))
        return 1 if missing else 0
    for path, content in files.items():
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(content, Image.Image):
                content.save(path)
            else:
                path.write_text(json.dumps(content, indent=2) + "\n", encoding="utf-8")
        except OSError as exc:
            LOG.error("konnte %s nicht schreiben: %s", path, exc)
            return 1
    LOG.info("%d Boss-Dateien geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
