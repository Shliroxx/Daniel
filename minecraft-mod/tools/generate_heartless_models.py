#!/usr/bin/env python3
"""Erzeugt die GeckoLib-Modelle der Herzlosen: Geometrie, Animationen (idle, walk, attack, special, emerge)
und Texturen unter assets/kingdomomnitrix/{geo,animations,textures}/entity/heartless/.

Platzhalter-Geometrie (Status PLACEHOLDER), in Blockbench verfeinerbar. Nutzt die Bausteine aus
generate_alien_models.py (gleiche Box-UV-Bemalung).

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_heartless_models.py           # schreiben
    python tools/generate_heartless_models.py --check   # nur pruefen
"""
from __future__ import annotations

import argparse
import json
import logging
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_alien_models import ASSETS, Alien, Bone, Cube, build_geo, build_texture  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("heartless_models")
EYES = "eyes"


def shadow() -> Alien:
    """Klein und geduckt, lange Fuehler, gelbe Augen."""
    black, dark = "#14101C", "#0B0910"
    return Alien("shadow", (64, 64), [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 6, 0), [Cube((-3, 5, -1.5), (6, 6, 3), (0, 16), black)]),
        Bone("head", "body", (0, 11, 0), [Cube((-3, 11, -3), (6, 5, 6), (0, 0), black, EYES)]),
        Bone("left_antenna", "head", (2, 16, 0), [Cube((1.5, 16, -0.5), (1, 6, 1), (24, 0), dark)]),
        Bone("right_antenna", "head", (-2, 16, 0), [Cube((-2.5, 16, -0.5), (1, 6, 1), (24, 0), dark, mirror=True)]),
        Bone("right_arm", "body", (-3.5, 10, 0), [Cube((-5, 2, -1), (2, 9, 2), (28, 0), black)]),
        Bone("left_arm", "body", (3.5, 10, 0), [Cube((3, 2, -1), (2, 9, 2), (28, 0), black, mirror=True)]),
        Bone("right_leg", "root", (-1.5, 5, 0), [Cube((-2.5, 0, -1), (2, 5, 2), (36, 0), dark)]),
        Bone("left_leg", "root", (1.5, 5, 0), [Cube((0.5, 0, -1), (2, 5, 2), (36, 0), dark, mirror=True)]),
    ], "#FFD800")


def soldier() -> Alien:
    """Ruestung, Helm mit Herzlosen-Emblem."""
    armor, dark, helmet = "#1B2A4A", "#101828", "#3A4C70"
    return Alien("soldier", (64, 64), [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 24, 0), [Cube((-4, 12, -2), (8, 12, 4), (16, 16), armor, "emblem")]),
        Bone("head", "body", (0, 24, 0), [Cube((-4, 24, -4), (8, 8, 8), (0, 0), "#0C0C12", EYES)]),
        Bone("helmet", "head", (0, 32, 0), [Cube((-4.5, 29, -4.5), (9, 4, 9), (32, 0), helmet),
                                            Cube((-1, 33, -1), (2, 3, 2), (0, 32), helmet)]),
        Bone("right_arm", "body", (-5, 22, 0), [Cube((-8, 12, -2), (4, 12, 4), (40, 16), armor)]),
        Bone("left_arm", "body", (5, 22, 0), [Cube((4, 12, -2), (4, 12, 4), (40, 16), armor, mirror=True)]),
        Bone("right_leg", "root", (-1.9, 12, 0), [Cube((-3.9, 0, -2), (4, 12, 4), (0, 16), dark)]),
        Bone("left_leg", "root", (1.9, 12, 0), [Cube((-0.1, 0, -2), (4, 12, 4), (0, 16), dark, mirror=True)]),
    ], "#C9CDD3")


def large_body() -> Alien:
    """Riesiger Bauch (die gepanzerte Front), kleiner Kopf mit Hut."""
    belly, skin, hat = "#3B2A5A", "#26183E", "#E0A030"
    return Alien("large_body", (128, 64), [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 8, 0), [Cube((-10, 6, -8), (20, 22, 16), (0, 0), belly, "belly")]),
        Bone("head", "body", (0, 28, 0), [Cube((-4, 28, -4), (8, 7, 8), (72, 0), skin, EYES)]),
        Bone("hat", "head", (0, 35, 0), [Cube((-4.5, 35, -4.5), (9, 5, 9), (72, 16), hat)]),
        Bone("right_arm", "body", (-10, 24, 0), [Cube((-14, 12, -2.5), (4, 13, 5), (104, 0), skin)]),
        Bone("left_arm", "body", (10, 24, 0), [Cube((10, 12, -2.5), (4, 13, 5), (104, 0), skin, mirror=True)]),
        Bone("right_leg", "root", (-5, 7, 0), [Cube((-8, 0, -3), (6, 7, 6), (72, 32), skin)]),
        Bone("left_leg", "root", (5, 7, 0), [Cube((2, 0, -3), (6, 7, 6), (72, 32), skin, mirror=True)]),
    ], "#E0A030")


def air_soldier() -> Alien:
    """Fliegender Soldat: Fluegel, Schwanz statt Beinen."""
    green, dark, wing = "#2A6E3A", "#164022", "#6CC58A"
    return Alien("air_soldier", (64, 64), [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 14, 0), [Cube((-3.5, 8, -2), (7, 10, 4), (16, 16), green, "emblem")]),
        Bone("head", "body", (0, 18, 0), [Cube((-3.5, 18, -3.5), (7, 7, 7), (0, 0), "#0C0C12", EYES)]),
        Bone("helmet", "head", (0, 25, 0), [Cube((-4, 23, -4), (8, 3, 8), (28, 0), dark)]),
        Bone("right_wing", "body", (-2, 16, 2), [Cube((-12, 12, 2), (10, 7, 1), (0, 32), wing)]),
        Bone("left_wing", "body", (2, 16, 2), [Cube((2, 12, 2), (10, 7, 1), (0, 32), wing, mirror=True)]),
        Bone("right_arm", "body", (-4.5, 17, 0), [Cube((-6.5, 8, -1.5), (3, 9, 3), (40, 16), green)]),
        Bone("left_arm", "body", (4.5, 17, 0), [Cube((3.5, 8, -1.5), (3, 9, 3), (40, 16), green, mirror=True)]),
        Bone("tail", "body", (0, 8, 0), [Cube((-1.5, 2, -1.5), (3, 6, 3), (0, 16), dark)]),
    ], "#FFD800")


def darkball() -> Alien:
    """Schwebende Kugel aus Dunkelheit mit Maul."""
    dark, ink = "#0A0A12", "#2B1A40"
    return Alien("darkball", (64, 64), [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 8, 0), [Cube((-6, 2, -6), (12, 12, 12), (0, 0), dark, "maw"),
                                         Cube((-5, 1, -5), (10, 14, 10), (0, 24), ink)]),
        Bone("head", "body", (0, 8, 0), []),
        Bone("tendril_left", "body", (4, 4, 0), [Cube((4, -3, -1), (2, 6, 2), (48, 0), ink)]),
        Bone("tendril_right", "body", (-4, 4, 0), [Cube((-6, -3, -1), (2, 6, 2), (48, 0), ink, mirror=True)]),
    ], "#8E44AD")


def paint_details(alien: Alien, img: Image.Image) -> None:
    """Augen und Embleme, die der allgemeine Maler nicht kennt."""
    for bone in alien.bones:
        for cube in bone.cubes:
            if cube.mirror or cube.front_detail is None:
                continue
            u, v = cube.uv
            w, h, d = cube.size
            fx, fy = u + d, v + d
            yellow = (255, 216, 0, 255)
            if cube.front_detail == EYES:
                eye_y = fy + max(1, h // 2 - 1)
                for ex in (1, w - 3):
                    for dx in range(2):
                        img.putpixel((fx + ex + dx, eye_y), yellow)
            elif cube.front_detail == "emblem":
                cx, cy = fx + w // 2 - 1, fy + 2
                for dx, dy in [(0, 0), (1, 0), (-1, 1), (2, 1), (0, 2), (1, 2), (0, 3), (1, 3)]:
                    img.putpixel((cx + dx, cy + dy), (200, 30, 40, 255))
            elif cube.front_detail == "belly":
                for x in range(fx + 3, fx + w - 3):
                    img.putpixel((x, fy + h // 2), (90, 70, 120, 255))
                for ex in (4, w - 6):
                    img.putpixel((fx + ex, fy + 3), yellow)
                    img.putpixel((fx + ex + 1, fy + 3), yellow)
            elif cube.front_detail == "maw":
                for ex in (2, w - 4):
                    for dx in range(2):
                        img.putpixel((fx + ex + dx, fy + 3), yellow)
                for x in range(fx + 2, fx + w - 2):
                    img.putpixel((x, fy + h - 4), (200, 30, 60, 255))


def track(*keys: tuple[float, tuple[float, float, float]]) -> dict:
    return {f"{t:.2f}": list(v) for t, v in keys}


def build_animations(alien: Alien) -> dict:
    names = {b.name for b in alien.bones}
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


def outputs(alien: Alien, rng: random.Random) -> dict[Path, object]:
    texture = build_texture(alien, rng)
    paint_details(alien, texture)
    base = Path("entity") / "heartless"
    return {
        ASSETS / "geo" / base / f"{alien.name}.geo.json": build_geo(alien),
        ASSETS / "animations" / base / f"{alien.name}.animation.json": build_animations(alien),
        ASSETS / "textures" / base / f"{alien.name}.png": texture,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alles vorhanden ist")
    parser.add_argument("--seed", type=int, default=777)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    rng = random.Random(args.seed)
    files: dict[Path, object] = {}
    for builder in (shadow, soldier, large_body, air_soldier, darkball):
        files.update(outputs(builder(), rng))
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
