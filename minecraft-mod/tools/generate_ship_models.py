#!/usr/bin/env python3
"""Erzeugt das GeckoLib-Modell des Raumschiffs Aphelion (Ratchet & Clank 3): Geometrie, Animationen
(idle, flight) und Textur unter assets/kingdomomnitrix/{geo,animations,textures}/entity/ship/.

Platzhalter-Geometrie (Status PLACEHOLDER), in Blockbench verfeinerbar. Die Nase zeigt nach -Z (vorn).

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_ship_models.py           # schreiben
    python tools/generate_ship_models.py --check   # nur pruefen
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

LOG = logging.getLogger("ship_models")
PURPLE, WHITE, DARK, GLASS, GLOW, GOLD = "#6A4FB3", "#E4E4EE", "#3A3550", "#7FD4FF", "#FF9A2E", "#F5C542"


def aphelion() -> Alien:
    return Alien("aphelion", (256, 128), [
        Bone("root", None, (0, 0, 0)),
        Bone("hull", "root", (0, 8, 0), [
            Cube((-6, 4, -30), (12, 8, 52), (0, 0), PURPLE, "stripe"),
            Cube((-4, 5, -40), (8, 6, 10), (128, 0), WHITE),
        ]),
        Bone("cockpit", "hull", (0, 12, -15), [Cube((-4, 12, -24), (8, 4, 14), (128, 16), GLASS, "glass")]),
        Bone("left_wing", "hull", (6, 7, 8), [Cube((6, 6, -2), (20, 2, 22), (0, 64), WHITE, "wing")]),
        Bone("right_wing", "hull", (-6, 7, 8), [Cube((-26, 6, -2), (20, 2, 22), (0, 64), WHITE, mirror=True)]),
        Bone("fin", "hull", (0, 12, 16), [Cube((-1, 12, 10), (2, 10, 12), (88, 64), PURPLE)]),
        Bone("left_engine", "hull", (4.5, 7.5, 27), [Cube((2, 5, 22), (5, 5, 10), (128, 40), DARK)]),
        Bone("right_engine", "hull", (-4.5, 7.5, 27), [Cube((-7, 5, 22), (5, 5, 10), (128, 40), DARK, mirror=True)]),
        Bone("left_glow", "left_engine", (4.5, 7.5, 32), [Cube((2.5, 5.5, 32), (4, 4, 1), (128, 60), GLOW, "glow")]),
        Bone("right_glow", "right_engine", (-4.5, 7.5, 32), [Cube((-6.5, 5.5, 32), (4, 4, 1), (128, 60), GLOW, mirror=True)]),
    ], PURPLE)


def paint_details(alien: Alien, img: Image.Image) -> None:
    """Goldstreifen auf dem Rumpf, Spiegelung im Cockpit, Flaechenmuster der Fluegel, gluehende Duesen."""
    width, height = img.size
    for bone in alien.bones:
        for cube in bone.cubes:
            if cube.mirror or cube.front_detail is None:
                continue
            u, v = cube.uv
            w, h, d = cube.size
            box_w = 2 * (d + w)
            if cube.front_detail == "stripe":
                # Laengsstreifen auf der Oberseite (Feld u+d .. u+d+w, v .. v+d)
                for y in range(v, v + d):
                    for x in (u + d + w // 2 - 1, u + d + w // 2):
                        img.putpixel((x, y), (245, 197, 66, 255))
            elif cube.front_detail == "glass":
                for x in range(u, u + box_w):
                    for y in range(v, v + d + h):
                        if (x + y) % 7 == 0 and 0 <= x < width and 0 <= y < height:
                            img.putpixel((x, y), (220, 245, 255, 255))
            elif cube.front_detail == "wing":
                for x in range(u + d, u + d + w):
                    img.putpixel((x, v + 3), (106, 79, 179, 255))
                    img.putpixel((x, v + d - 4), (245, 197, 66, 255))
            elif cube.front_detail == "glow":
                for x in range(u, u + box_w):
                    for y in range(v, v + d + h):
                        if 0 <= x < width and 0 <= y < height:
                            img.putpixel((x, y), (255, 210, 120, 255) if (x + y) % 2 else (255, 140, 40, 255))


def track(*keys: tuple[float, tuple[float, float, float]]) -> dict:
    return {f"{t:.2f}": list(v) for t, v in keys}


def build_animations() -> dict:
    idle = {"root": {"position": track((0.0, (0, 0, 0)), (1.5, (0, 0.6, 0)), (3.0, (0, 0, 0)))}}
    pulse = track((0.0, (1, 1, 1)), (0.1, (1.25, 1.25, 1.4)), (0.2, (1, 1, 1)))
    flight = {"root": {"position": track((0.0, (0, 0, 0)), (0.5, (0, 0.3, 0)), (1.0, (0, 0, 0)))},
              "left_glow": {"scale": pulse}, "right_glow": {"scale": pulse}}
    return {"format_version": "1.8.0", "animations": {
        "idle": {"loop": True, "animation_length": 3.0, "bones": idle},
        "flight": {"loop": True, "animation_length": 1.0, "bones": flight},
    }}


def outputs(rng: random.Random) -> dict[Path, object]:
    ship = aphelion()
    texture = build_texture(ship, rng)
    paint_details(ship, texture)
    geo = build_geo(ship)
    description = geo["minecraft:geometry"][0]["description"]
    description["identifier"] = "geometry.kingdomomnitrix.ship_aphelion"
    description["visible_bounds_width"] = 6
    description["visible_bounds_height"] = 3
    base = Path("entity") / "ship"
    return {
        ASSETS / "geo" / base / "aphelion.geo.json": geo,
        ASSETS / "animations" / base / "aphelion.animation.json": build_animations(),
        ASSETS / "textures" / base / "aphelion.png": texture,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alles vorhanden ist")
    parser.add_argument("--seed", type=int, default=1203)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    files = outputs(random.Random(args.seed))
    if args.check:
        missing = [p for p in files if not p.is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p.relative_to(ASSETS))
        LOG.info("%d/%d Schiffs-Dateien vorhanden", len(files) - len(missing), len(files))
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
    LOG.info("%d Schiffs-Dateien geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
