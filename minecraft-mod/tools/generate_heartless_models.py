#!/usr/bin/env python3
"""Erzeugt die GeckoLib-Modelle der Herzlosen: Geometrie, Animationen (idle, walk, attack, special, emerge)
Texturen und Leucht-Masken unter assets/kingdomomnitrix/{geo,animations,textures}/entity/heartless/.

Nach den Kingdom-Hearts-Vorlagen: Shadow, Soldier, Large Body, Air Soldier, Darkball mit gelb leuchtenden Augen
(Glowmask), Herzlosen-Emblem und Zickzack-Muster. Nutzt Packer und Maler aus generate_boss_models.py
(4 Pixel je Einheit, Materialien, Leucht-Maske).

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_heartless_models.py           # schreiben
    python tools/generate_heartless_models.py --check   # nur pruefen
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_alien_models import ASSETS  # noqa: E402
from generate_boss_models import DENSITY, Box, Joint, build_nefarious_geo, pack, paint_box  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("heartless_models")

# Vorlage: Kingdom Hearts. Alle Herzlosen haben runde, gelb leuchtende Augen ohne Pupille in einem schwarzen Gesicht;
# Soldaten tragen das Herzlosen-Emblem (Herz, oben schwarz, unten rot, rotes Kreuz) und Zickzack-Muster. Vorn ist -Z.

PALETTE = {
    "black": ("#08060C", "#16121F", "#262035", "#3E3556"),
    "navy": ("#0F1430", "#222C58", "#34407E", "#5563A6"),
    "silver": ("#545A66", "#A9B1BE", "#CDD4DE", "#F4F7FB"),
    "purple": ("#1E1033", "#36205A", "#4E3280", "#7356AE"),
    "green": ("#0F2A1A", "#1D4A30", "#2B6A45", "#4C9468"),
    "wing": ("#5A140C", "#B3301A", "#DD5428", "#FF915A"),
    "gold": ("#5E4410", "#B8902C", "#E0B848", "#FFF0A0"),
    "ink": ("#040308", "#0D0A15", "#1C1428", "#352650"),
    "dark": ("#0A0A0E", "#18181F", "#26262F", "#3A3A46"),
    "orange": ("#7A3A0C", "#D9741E", "#F29A3A", "#FFD08A"),
}
YELLOW = (255, 222, 40)
RED = (205, 28, 40)


@dataclass
class Heartless:
    name: str
    joints: list
    texture: tuple[int, int] = (64, 64)


def eyes(W: int, H: int, px: int, py: int, size: float, height: float, spread: float):
    """Zwei runde gelbe Augen ohne Pupille (KH); gibt die Farbe zurueck oder None."""
    for side in (-1, 1):
        cx, cy = W / 2 + side * W * spread, H * height
        r = ((px - cx) ** 2 + ((py - cy) * 1.15) ** 2) ** 0.5
        if r < W * size:
            return (255, 250, 200) if r < W * size * 0.35 else YELLOW
    return None


def emblem(W: int, H: int, px: int, py: int, scale: float = 0.3):
    """Herzlosen-Emblem: Herz (oben schwarz, unten rot) vor einem duennen roten Kreuz."""
    cx, cy, r = W / 2, H * 0.45, min(W, H) * scale
    x, y = (px - cx) / r, -(py - cy) / r
    heart = (x * x + y * y - 1) ** 3 - x * x * y ** 3 <= 0
    if heart:
        return (24, 18, 30) if y > 0.05 else RED
    # Kreuz liegt hinter dem Herz und ragt nur an den vier Ecken heraus
    if (abs(x - y) < 0.11 or abs(x + y) < 0.11) and abs(x) < 1.3 and abs(y) < 1.3:
        return RED
    return None


def heartless_detail(b, face: str, px: int, py: int, W: int, H: int, color):
    d = b.detail
    if d in ("eyes", "eyes_big") and face == "front":
        c = eyes(W, H, px, py, 0.17 if d == "eyes_big" else 0.13, 0.45 if d == "eyes_big" else 0.5, 0.22)
        return ((c, 255, True) if c else ((12, 10, 16), 255, False))
    if d == "emblem" and face == "front":
        c = emblem(W, H, px, py)
        return (c, 255, False) if c else None
    if d == "belly" and face == "front":
        c = emblem(W, H, px, py, 0.2)
        if c:
            return c, 255, False
        if abs(py - H * 0.82) < 3:
            return (200, 205, 215), 255, False
        return None
    if d == "zigzag" and face in ("front", "back", "east", "west"):
        period = 8
        wave = abs((px % period) - period / 2) * 0.9
        if abs(py - (H * 0.45 + wave)) < 1.5:
            return (190, 196, 206), 255, False
        return None
    if d == "goggles" and face == "front":
        for side in (-1, 1):
            r = ((px - (W / 2 + side * W * 0.25)) ** 2 + (py - H / 2) ** 2) ** 0.5
            if r < H * 0.42:
                return ((255, 140, 40) if r < H * 0.25 else (60, 60, 70)), 255, r < H * 0.25
        return None
    if d == "membrane" and face in ("front", "back"):
        # gezackte Unterkante (Fledermausfluegel), Rippen dunkel
        scallop = abs((px % 16) - 8) * 0.6
        if py > H - 1 - scallop:
            return (0, 0, 0), 0, False
        if px % 16 == 0:
            return tuple(round(c * 0.55) for c in color), 255, False
        return None
    if d == "maw" and face == "front":
        c = eyes(W, H, px, py, 0.1, 0.22, 0.2)
        if c:
            return c, 255, True
        mx, my = (px - W / 2) / (W * 0.42), (py - H * 0.62) / (H * 0.3)
        if mx * mx + my * my < 1:
            tooth = 5 - abs((px % 8) - 4) * 1.2
            if py < H * 0.62 - H * 0.3 * (1 - mx * mx) ** 0.5 + tooth + 2 or py > H * 0.62 + H * 0.3 * (1 - mx * mx) ** 0.5 - tooth - 2:
                return (235, 230, 220), 255, False
            return (110, 10, 30), 255, py > H * 0.55
        return None
    if d == "wisp":
        return tuple(round(c * (0.6 + 0.4 * py / max(1, H))) for c in color), 255, False
    return None


def B(origin, size, material, detail=None, rotation=None, pivot=None):
    return Box(origin, size, material, detail, rotation, pivot)


def shadow() -> Heartless:
    """Shadow: klein, geduckt, schwarz; lange, nach hinten abknickende Fuehler; riesige gelbe Augen; Krallen."""
    def antenna(side: int):
        x = 1.0 if side == 1 else -2.0
        boxes = [B((x, 15, -0.5), (1, 6, 1), "black"),
                 B((x, 20, 0.5), (1, 1, 3.5), "black"),
                 B((x, 17.5, 3.5), (1, 3, 1), "black", "wisp")]
        return Joint(("left" if side == 1 else "right") + "_antenna", "head", (x + 0.5, 15, 0), boxes, (-25, 0, -18 * side))

    def arm(side: int):
        s = side
        x = 2.5 if s == 1 else -4.0
        return Joint(("left" if s == 1 else "right") + "_arm", "body", (3 * s, 9.5, 0), [
            B((x, 4.5, -0.75), (1.5, 5.5, 1.5), "black"),
            B((x - 0.25 if s == 1 else x - 0.25, 2.5, -1), (2, 2, 2), "black"),
            B((x - 0.25, 1, -1), (0.5, 1.5, 0.5), "black"), B((x + 0.5, 1, -1), (0.5, 1.5, 0.5), "black"),
            B((x + 1.25, 1, -1), (0.5, 1.5, 0.5), "black"),
        ], (0, 0, 8 * s))

    def leg(side: int):
        x = 0.5 if side == 1 else -2.0
        return Joint(("left" if side == 1 else "right") + "_leg", "root", (1.25 * side, 5, 0), [
            B((x, 2, -0.75), (1.5, 3, 1.5), "black"),
            B((x - 0.25, 0, -2.5), (2, 2, 3.5), "black"),
            B((x + 0.25, 0, -3.5), (1, 1, 1), "black"),
        ])

    return Heartless("shadow", [
        Joint("root", None, (0, 0, 0)),
        Joint("body", "root", (0, 5, 0), [B((-2.5, 5, -2), (5, 5, 4), "black"),
                                          B((-2, 4, -1.5), (4, 1.5, 3), "black")], (18, 0, 0)),
        Joint("head", "body", (0, 10, -0.5), [B((-3, 10, -3.5), (6, 5, 6), "black", "eyes_big")], (-12, 0, 0)),
        antenna(1), antenna(-1), arm(1), arm(-1), leg(1), leg(-1),
    ])


def soldier() -> Heartless:
    """Soldier: dunkelblaue Ruestung mit Zickzack, Emblem auf der Brust, Spitzhelm mit silberner Krempe,
    silberne Krallenhandschuhe, Stiefel mit eingerollter Spitze."""
    def arm(side: int):
        x = 4 if side == 1 else -7
        return Joint(("left" if side == 1 else "right") + "_arm", "body", (5 * side, 22, 0), [
            B((x, 19.5, -2), (3, 3.5, 4), "navy"),
            B((x + 0.25, 13.5, -1.5), (2.5, 6, 3), "navy", "zigzag"),
            B((x - 0.25, 10, -2), (3.5, 4, 4), "silver"),
            B((x - 0.25, 8, -2), (1, 2, 1), "silver"), B((x + 1, 8, -2), (1, 2, 1), "silver"),
            B((x + 2.25, 8, -2), (1, 2, 1), "silver"),
        ])

    def leg(side: int):
        x = 0.25 if side == 1 else -3.75
        return Joint(("left" if side == 1 else "right") + "_leg", "root", (2 * side, 12, 0), [
            B((x + 0.25, 6, -1.5), (3, 6, 3), "navy", "zigzag"),
            B((x, 0, -2), (3.5, 6, 4), "navy"),
            B((x + 0.75, 0, -4.5), (2, 2, 2.5), "navy"),
            B((x + 1.25, 2, -5), (1, 1.5, 1), "silver"),
        ])

    return Heartless("soldier", [
        Joint("root", None, (0, 0, 0)),
        Joint("body", "root", (0, 12, 0), [
            B((-3.5, 12, -2), (7, 3, 4), "navy", "trim_top"),
            B((-4, 15, -2.5), (8, 7, 5), "navy", "emblem"),
            B((-4.5, 22, -3), (9, 2, 6), "silver", "zigzag"),
        ]),
        Joint("head", "body", (0, 24, 0), [B((-3.5, 24, -3.5), (7, 5.5, 7), "black", "eyes")]),
        Joint("helmet", "head", (0, 29, 0), [
            B((-4.5, 28, -4.5), (9, 1, 9), "silver"),
            B((-4, 29, -4), (8, 3.5, 8), "navy", "zigzag"),
            B((-2, 32.5, -2), (4, 2, 4), "navy"),
            B((-1, 34.5, -1), (2, 2, 2), "navy"),
            B((-0.5, 36.5, -0.5), (1, 1.5, 1), "silver"),
        ]),
        arm(1), arm(-1), leg(1), leg(-1),
    ])


def large_body() -> Heartless:
    """Large Body: riesiger runder Bauch mit Emblem und Guertel (vorn gepanzert), kleiner Kopf, Zipfelhut,
    Stummelarme und -beine."""
    def arm(side: int):
        x = 10 if side == 1 else -14
        return Joint(("left" if side == 1 else "right") + "_arm", "body", (10 * side, 25, 0), [
            B((x, 14, -2.5), (4, 11, 5), "purple", "zigzag"),
            B((x - 0.5, 10, -3), (5, 4, 6), "black"),
        ], (0, 0, 12 * side))

    def leg(side: int):
        x = 2 if side == 1 else -8
        return Joint(("left" if side == 1 else "right") + "_leg", "root", (5 * side, 7, 0), [
            B((x, 2, -3), (6, 5, 6), "purple"),
            B((x - 0.5, 0, -5), (7, 2, 8), "black"),
        ])

    return Heartless("large_body", [
        Joint("root", None, (0, 0, 0)),
        Joint("body", "root", (0, 8, 0), [
            B((-10, 6, -9), (20, 22, 17), "purple", "belly"),
            B((-11, 9, -7), (22, 16, 14), "purple"),
            B((-8, 28, -6), (16, 2, 12), "purple"),
            B((-8, 5, -7), (16, 1, 13), "purple"),
        ]),
        Joint("head", "body", (0, 30, 0), [B((-3.5, 30, -4.5), (7, 5.5, 7), "black", "eyes")]),
        Joint("hat", "head", (0, 35, 0), [
            B((-5.5, 35, -6), (11, 1, 11), "purple"),
            B((-4.5, 36, -5), (9, 3, 9), "purple", "zigzag"),
            B((-2.5, 39, -3), (5, 2, 5), "purple"),
            B((-1, 41, -1.5), (2, 2, 2), "purple"),
            B((-0.5, 43, -1), (1, 1, 1), "gold"),
        ], (-10, 0, 0)),
        arm(1), arm(-1), leg(1), leg(-1),
    ], (128, 128))


def air_soldier() -> Heartless:
    """Air Soldier: gruene Ruestung mit Emblem, Fliegermuetze mit Brille, rote Fledermausfluegel, Schwanz statt Beinen."""
    def wing(side: int):
        x0 = 2 if side == 1 else -12
        return Joint(("left" if side == 1 else "right") + "_wing", "body", (2 * side, 15, 2), [
            B((x0, 13, 2), (10, 1.5, 1), "wing"),
            B((x0 + (8.5 if side == 1 else 0), 9, 2), (1.5, 4, 1), "wing"),
            B((x0, 7, 2.25), (10, 6, 0.5), "wing", "membrane"),
        ], (0, 15 * side, 10 * side))

    def arm(side: int):
        x = 3 if side == 1 else -5
        return Joint(("left" if side == 1 else "right") + "_arm", "body", (4 * side, 15, 0), [
            B((x, 9.5, -1), (2, 5.5, 2), "green", "zigzag"),
            B((x - 0.5, 7, -1.5), (3, 2.5, 3), "silver"),
        ])

    return Heartless("air_soldier", [
        Joint("root", None, (0, 0, 0)),
        Joint("body", "root", (0, 14, 0), [
            B((-3, 9, -2), (6, 6.5, 4), "green", "emblem"),
            B((-3.5, 15, -2.5), (7, 2, 5), "silver", "zigzag"),
        ]),
        Joint("tail", "body", (0, 9, 0), [
            B((-1.5, 5, -1.5), (3, 4, 3), "green", "zigzag"),
            B((-1, 2, -1), (2, 3, 2), "green"),
            B((-0.5, 0, -0.5), (1, 2, 1), "wing"),
        ], (15, 0, 0)),
        Joint("head", "body", (0, 17, 0), [B((-3, 17, -3), (6, 5, 6), "black", "eyes")]),
        Joint("helmet", "head", (0, 21, 0), [
            B((-3.5, 20.5, -3.5), (7, 2.5, 7), "green"),
            B((-3, 21, -3.75), (6, 1.5, 0.5), "silver", "goggles"),
            B((-3.75, 18, -1.5), (0.5, 3, 3), "green"), B((3.25, 18, -1.5), (0.5, 3, 3), "green"),
        ]),
        wing(1), wing(-1), arm(1), arm(-1),
    ])


def darkball() -> Heartless:
    """Darkball: schwebende Kugel aus Dunkelheit, riesiges Maul mit Zaehnen, kleine gelbe Augen, Schwaden oben und unten."""
    def tendril(side: int):
        x = 3 if side == 1 else -5
        return Joint("tendril_" + ("left" if side == 1 else "right"), "body", (4 * side, 4, 0), [
            B((x, -1, -1), (2, 5, 2), "ink", "wisp"),
            B((x + 0.5, -3, -0.5), (1, 2, 1), "ink", "wisp"),
        ], (0, 0, 15 * side))

    return Heartless("darkball", [
        Joint("root", None, (0, 0, 0)),
        Joint("body", "root", (0, 8, 0), [
            # Kugel aus Schichten: Kern plus je eine schmalere Platte pro Achse, Maul vorn
            B((-4, 4, -6.5), (8, 8, 13), "ink", "maw"),
            B((-5, 3, -5), (10, 10, 10), "ink"),
            B((-6, 4, -4), (12, 8, 8), "ink"),
            B((-4, 2, -4), (8, 12, 8), "ink"),
        ]),
        Joint("head", "body", (0, 13, 0), [
            B((-2.5, 13.5, -1), (1.5, 4, 1.5), "ink", "wisp", (0, 0, 20), (-2, 13.5, 0)),
            B((1, 13.5, -1), (1.5, 4, 1.5), "ink", "wisp", (0, 0, -20), (2, 13.5, 0)),
            B((-0.5, 14, 1), (1, 3, 1), "ink", "wisp", (-25, 0, 0), (0, 14, 1)),
        ]),
        tendril(1), tendril(-1),
    ])


def paint(model: Heartless) -> tuple[Image.Image, Image.Image]:
    for size in (model.texture, (128, 64), (128, 128), (256, 128)):
        try:
            pack(model.joints, size)
            model.texture = size
            break
        except ValueError:
            continue
    else:
        raise ValueError(f"{model.name}: Textur reicht nicht")
    px = (model.texture[0] * DENSITY, model.texture[1] * DENSITY)
    texture = Image.new("RGBA", px, (0, 0, 0, 0))
    glow = Image.new("RGBA", px, (0, 0, 0, 0))
    for joint in model.joints:
        for box in joint.boxes:
            paint_box(texture, glow, box, PALETTE, heartless_detail)
    return texture, glow


def geo(model: Heartless) -> dict:
    data = build_nefarious_geo(model.joints)
    description = data["minecraft:geometry"][0]["description"]
    description["identifier"] = f"geometry.kingdomomnitrix.heartless_{model.name}"
    description["texture_width"], description["texture_height"] = model.texture
    description["visible_bounds_width"], description["visible_bounds_height"] = 3, 3.5
    description["visible_bounds_offset"] = [0, 1.25, 0]
    return data


def track(*keys: tuple[float, tuple[float, float, float]]) -> dict:
    return {f"{t:.2f}": list(v) for t, v in keys}


def build_animations(alien: Heartless) -> dict:
    names = {j.name for j in alien.joints}
    flying = alien.name in ("air_soldier", "darkball")
    bob = {"position": track((0.0, (0, 0, 0)), (1.0, (0, 1.5, 0)), (2.0, (0, 0, 0)))}
    idle = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.0, (3, 0, 0)), (2.0, (0, 0, 0)))}}
    walk: dict = {}
    if flying:
        idle["root"] = bob
        walk["root"] = bob
        walk["body"] = {"rotation": track((0.0, (15, 0, 0)), (0.5, (18, 0, 0)), (1.0, (15, 0, 0)))}
    for side, sign in (("right", 1), ("left", -1)):
        if f"{side}_arm" in names:
            walk[f"{side}_arm"] = {"rotation": track((0.0, (30 * sign, 0, 0)), (0.4, (-30 * sign, 0, 0)), (0.8, (30 * sign, 0, 0)))}
        if f"{side}_leg" in names:
            walk[f"{side}_leg"] = {"rotation": track((0.0, (-30 * sign, 0, 0)), (0.4, (30 * sign, 0, 0)), (0.8, (-30 * sign, 0, 0)))}
        if f"{side}_wing" in names:
            flap = {"rotation": track((0.0, (0, 0, -20 * sign)), (0.15, (0, 0, 25 * sign)), (0.3, (0, 0, -20 * sign)))}
            idle[f"{side}_wing"] = flap
            walk[f"{side}_wing"] = flap
        if f"{side}_antenna" in names:
            idle[f"{side}_antenna"] = {"rotation": track((0.0, (0, 0, 5 * sign)), (1.0, (0, 0, 15 * sign)), (2.0, (0, 0, 5 * sign)))}
    for tendril, sign in (("tendril_left", 1), ("tendril_right", -1)):
        if tendril in names:
            wiggle = {"rotation": track((0.0, (0, 0, 10 * sign)), (0.5, (0, 0, -10 * sign)), (1.0, (0, 0, 10 * sign)))}
            idle[tendril] = wiggle
            walk[tendril] = wiggle

    attack = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.1, (-15, 0, 0)), (0.25, (25, 0, 0)), (0.4, (0, 0, 0)))}}
    if "right_arm" in names:
        attack["right_arm"] = {"rotation": track((0.0, (0, 0, 0)), (0.1, (-120, 0, 0)), (0.25, (20, 0, 0)), (0.4, (0, 0, 0)))}
        attack["left_arm"] = {"rotation": track((0.0, (0, 0, 0)), (0.1, (-110, 0, 0)), (0.25, (10, 0, 0)), (0.4, (0, 0, 0)))}
    special = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.25, (0, 360, 0)), (0.5, (0, 720, 0)))}}
    if alien.name == "large_body":
        special = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.6, (25, 0, 0)), (1.0, (0, 0, 0)))}}
    emerge = {"root": {"position": track((0.0, (0, -16, 0)), (0.5, (0, 0, 0))),
                       "scale": track((0.0, (1, 0.2, 1)), (0.5, (1, 1, 1)))}}

    def anim(length: float, bones: dict, loop: bool) -> dict:
        return {"loop": loop, "animation_length": length, "bones": {k: v for k, v in bones.items() if k in names}}

    return {"format_version": "1.8.0", "animations": {
        "idle": anim(2.0, idle, True),
        "walk": anim(0.8 if not flying else 2.0, walk or idle, True),
        "attack": anim(0.4, attack, False),
        "special": anim(1.0 if alien.name == "large_body" else 0.5, special, False),
        "emerge": anim(0.5, emerge, False),
    }}


def outputs(model: Heartless) -> dict[Path, object]:
    texture, glow = paint(model)
    base = Path("entity") / "heartless"
    return {
        ASSETS / "geo" / base / f"{model.name}.geo.json": geo(model),
        ASSETS / "animations" / base / f"{model.name}.animation.json": build_animations(model),
        ASSETS / "textures" / base / f"{model.name}.png": texture,
        ASSETS / "textures" / base / f"{model.name}_glowmask.png": glow,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alles vorhanden ist")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    files: dict[Path, object] = {}
    for builder in (shadow, soldier, large_body, air_soldier, darkball):
        files.update(outputs(builder()))
    if args.check:
        missing = [p for p in files if not p.is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p.relative_to(ASSETS))
        LOG.info("%d/%d Herzlosen-Dateien vorhanden", len(files) - len(missing), len(files))
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
    LOG.info("%d Herzlosen-Dateien geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
