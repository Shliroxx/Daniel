#!/usr/bin/env python3
"""Erzeugt das GeckoLib-Modell des ersten Bosses: Dr. Nefarious im Kampf-Mech (Ratchet & Clank).
Geometrie, Animationen (idle, walk, laser, rockets, stomp, phase, stunned, overload, death), Textur und
Leucht-Maske (Kern im Ruecken, Augen, Kanone) unter assets/kingdomomnitrix/{geo,animations,textures}/entity/boss/.

Platzhalter-Geometrie (Status PLACEHOLDER), in Blockbench verfeinerbar. Vorn ist -Z.

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_boss_models.py           # schreiben
    python tools/generate_boss_models.py --check   # nur pruefen
"""
from __future__ import annotations

import argparse
import json
import logging
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_alien_models import ASSETS, Alien, Bone, Cube, build_geo, build_texture, rgb  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("boss_models")
HULL, DARK, TRIM, GLASS, SKIN, CORE, RED = "#4A4F5C", "#2A2D35", "#B83A3A", "#7FE0C8", "#9FD08A", "#FF5A1F", "#E83A3A"


def nefarious_mech() -> Alien:
    """Breiter Mech: Rumpf mit Glaskuppel (Nefarious' Kopf darin), Laserarm links, Raketenarm rechts, Kern im Ruecken."""
    return Alien("nefarious_mech", (256, 128), [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 30, 0), [
            Cube((-12, 26, -9), (24, 22, 18), (0, 0), HULL, "plates"),
            Cube((-9, 20, -7), (18, 6, 14), (84, 0), DARK),
        ]),
        Bone("core", "body", (0, 38, 9), [Cube((-4, 32, 9), (8, 10, 3), (148, 0), CORE, "core")]),
        Bone("cockpit", "body", (0, 48, -2), [
            Cube((-6, 48, -8), (12, 9, 12), (0, 40), GLASS, "dome"),
            Cube((-3, 49, -5), (6, 6, 6), (48, 40), SKIN, "face"),
        ]),
        Bone("left_shoulder", "body", (13, 44, 0), [Cube((12, 40, -6), (8, 8, 12), (72, 40), TRIM)]),
        Bone("left_arm", "left_shoulder", (16, 40, 0), [
            Cube((13, 24, -5), (6, 16, 10), (112, 40), HULL),
            Cube((14, 22, -18), (4, 4, 14), (144, 40), DARK, "barrel"),
        ]),
        Bone("right_shoulder", "body", (-13, 44, 0), [Cube((-20, 40, -6), (8, 8, 12), (72, 64), TRIM)]),
        Bone("right_arm", "right_shoulder", (-16, 40, 0), [
            Cube((-21, 22, -6), (10, 18, 12), (176, 40), HULL, "pods"),
        ]),
        Bone("left_leg", "root", (7, 22, 0), [
            Cube((4, 10, -4), (7, 12, 8), (0, 72), DARK),
            Cube((3, 0, -7), (9, 10, 12), (30, 72), HULL),
        ]),
        Bone("right_leg", "root", (-7, 22, 0), [
            Cube((-11, 10, -4), (7, 12, 8), (72, 88), DARK),
            Cube((-12, 0, -7), (9, 10, 12), (102, 88), HULL),
        ]),
        Bone("antenna", "cockpit", (5, 57, 2), [Cube((4.5, 57, 1.5), (1, 6, 1), (240, 0), DARK),
                                                Cube((4, 63, 1), (2, 2, 2), (240, 8), RED, "light")]),
    ], TRIM)


GLOW_DETAILS = {"core", "light", "barrel", "face"}


def paint_details(alien: Alien, img: Image.Image, glow: Image.Image) -> None:
    """Panzerplatten, Raketenmuendungen, Gesicht hinter Glas; leuchtende Teile zusaetzlich in die Glowmask."""
    width, height = img.size
    rng = random.Random(alien.name)
    for bone in alien.bones:
        for cube in bone.cubes:
            if cube.mirror or cube.front_detail is None:
                continue
            u, v = cube.uv
            w, h, d = cube.size
            fx, fy = u + d, v + d
            box_w, box_h = 2 * (d + w), d + h
            detail = cube.front_detail
            if detail == "plates":
                for x in range(u, u + box_w):
                    for y in (v + d + 5, v + d + 13):
                        img.putpixel((x, y), rgb(TRIM) + (255,))
                for _ in range(40):
                    img.putpixel((u + rng.randrange(box_w), v + d + rng.randrange(h)), (130, 138, 150, 255))
            elif detail == "pods":
                for row in range(3):
                    for col in range(3):
                        x, y = fx + 2 + col * 3, fy + 3 + row * 4
                        img.putpixel((x, y), (20, 20, 24, 255))
                        img.putpixel((x + 1, y), (255, 120, 40, 255))
                        glow.putpixel((x + 1, y), (255, 120, 40, 255))
            elif detail == "dome":
                for x in range(u, u + box_w):
                    for y in range(v, v + box_h):
                        if (x + y) % 6 == 0:
                            img.putpixel((x, y), (210, 255, 240, 200))
            elif detail == "face":
                # boese gruene Augen
                for ex in (1, w - 3):
                    for dx in range(2):
                        img.putpixel((fx + ex + dx, fy + 2), (200, 255, 80, 255))
                        glow.putpixel((fx + ex + dx, fy + 2), (200, 255, 80, 255))
                for x in range(fx + 1, fx + w - 1):
                    img.putpixel((x, fy + 4), (60, 90, 50, 255))
            if detail in ("core", "light", "barrel"):
                base = rgb(CORE) if detail == "core" else rgb(RED) if detail == "light" else (255, 80, 60)
                for x in range(u, u + box_w):
                    for y in range(v, v + box_h):
                        if 0 <= x < width and 0 <= y < height and img.getpixel((x, y))[3] > 0:
                            shade = 1.0 if (x + y) % 3 else 0.75
                            color = tuple(int(c * shade) for c in base) + (255,)
                            if detail == "barrel" and not (fx <= x < fx + w and fy <= y < fy + h):
                                continue  # nur die Muendung leuchtet
                            img.putpixel((x, y), color)
                            glow.putpixel((x, y), color)


def track(*keys: tuple[float, tuple[float, float, float]]) -> dict:
    return {f"{t:.2f}": list(v) for t, v in keys}


def build_animations() -> dict:
    def anim(length: float, bones: dict, loop: bool) -> dict:
        return {"loop": loop, "animation_length": length, "bones": bones}

    sway = 8
    idle = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.5, (2, 0, 0)), (3.0, (0, 0, 0)))},
            "core": {"scale": track((0.0, (1, 1, 1)), (0.75, (1.08, 1.08, 1.08)), (1.5, (1, 1, 1)))},
            "antenna": {"rotation": track((0.0, (0, 0, -5)), (1.5, (0, 0, 5)), (3.0, (0, 0, -5)))}}
    walk = {"left_leg": {"rotation": track((0.0, (-20, 0, 0)), (0.6, (20, 0, 0)), (1.2, (-20, 0, 0)))},
            "right_leg": {"rotation": track((0.0, (20, 0, 0)), (0.6, (-20, 0, 0)), (1.2, (20, 0, 0)))},
            "body": {"rotation": track((0.0, (0, 0, -3)), (0.6, (0, 0, 3)), (1.2, (0, 0, -3))),
                     "position": track((0.0, (0, 0, 0)), (0.3, (0, -1, 0)), (0.6, (0, 0, 0)), (0.9, (0, -1, 0)), (1.2, (0, 0, 0)))}}
    laser = {"left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-80, 0, 0)), (2.0, (-80, 0, 0)), (2.5, (0, 0, 0)))},
             "body": {"rotation": track((0.0, (0, 0, 0)), (1.5, (0, -sway, 0)), (2.0, (0, sway, 0)), (2.5, (0, 0, 0)))}}
    rockets = {"right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-60, 0, 0)), (1.6, (-60, 0, 0)), (2.0, (0, 0, 0)))},
               "right_shoulder": {"position": track((1.5, (0, 0, 0)), (1.6, (0, 0, 2)), (1.8, (0, 0, 0)))}}
    stomp = {"body": {"position": track((0.0, (0, 0, 0)), (0.6, (0, 4, 0)), (0.8, (0, -3, 0)), (1.1, (0, 0, 0)))},
             "left_leg": {"rotation": track((0.0, (0, 0, 0)), (0.6, (-35, 0, 0)), (0.8, (5, 0, 0)), (1.1, (0, 0, 0)))}}
    phase = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.5, (-15, 0, 0)), (1.5, (-15, 180, 0)), (2.0, (0, 360, 0)))},
             "core": {"scale": track((0.0, (1, 1, 1)), (1.0, (1.6, 1.6, 1.6)), (2.0, (1, 1, 1)))}}
    stunned = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.3, (20, 0, 8)), (1.0, (18, 0, -8)), (2.0, (20, 0, 8)))},
               "cockpit": {"rotation": track((0.0, (0, 0, 0)), (0.5, (0, 0, 15)), (1.0, (0, 0, -15)), (2.0, (0, 0, 0)))}}
    overload = {"core": {"scale": track((0.0, (1, 1, 1)), (1.5, (1.4, 1.4, 1.4)), (3.0, (1.9, 1.9, 1.9)))},
                "left_arm": {"rotation": track((0.0, (0, 0, 0)), (1.0, (0, 0, 60)), (3.0, (0, 0, 70)))},
                "right_arm": {"rotation": track((0.0, (0, 0, 0)), (1.0, (0, 0, -60)), (3.0, (0, 0, -70)))}}
    death = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.0, (25, 0, 15)), (2.0, (80, 0, 20))),
                      "position": track((0.0, (0, 0, 0)), (2.0, (0, -16, 0)))}}
    return {"format_version": "1.8.0", "animations": {
        "idle": anim(3.0, idle, True), "walk": anim(1.2, walk, True), "laser": anim(2.5, laser, False),
        "rockets": anim(2.0, rockets, False), "stomp": anim(1.1, stomp, False), "phase": anim(2.0, phase, False),
        "stunned": anim(2.0, stunned, True), "overload": anim(3.0, overload, False), "death": anim(2.0, death, False),
    }}


def outputs(rng: random.Random) -> dict[Path, object]:
    mech = nefarious_mech()
    texture = build_texture(mech, rng)
    glow = Image.new("RGBA", texture.size, (0, 0, 0, 0))
    paint_details(mech, texture, glow)
    geo = build_geo(mech)
    description = geo["minecraft:geometry"][0]["description"]
    description["identifier"] = "geometry.kingdomomnitrix.boss_nefarious_mech"
    description["visible_bounds_width"] = 6
    description["visible_bounds_height"] = 6
    base = Path("entity") / "boss"
    return {
        ASSETS / "geo" / base / "nefarious_mech.geo.json": geo,
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
