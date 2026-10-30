#!/usr/bin/env python3
"""Heatblasts Flammenbrett (Alien Evolution 2.0): GeckoLib-Modell, Textur und Schwebe-Animation.

Eigenes Bauteil (AE surft nur auf Partikeln): ein Brett aus Magma mit gluehender Mittelnaht, Rissen und drei
Flammenzungen am Heck. Wird als {@code AttachedPropEntity} der Art {@code flame_board} unter den Fuessen gezeichnet
(voll hell, siehe AttachedPropRenderer).

    python3 tools/generate_flame_board.py           # schreiben
    python3 tools/generate_flame_board.py --check   # nur pruefen, ob die Dateien aktuell sind (CI)
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import math
import random
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
NAME = "flame_board"
GEO = ROOT / "geo" / "entity" / "prop" / f"{NAME}.geo.json"
TEXTURE = ROOT / "textures" / "entity" / "prop" / f"{NAME}.png"
ANIMATION = ROOT / "animations" / "entity" / "prop" / f"{NAME}.animation.json"
LOG = logging.getLogger("flame_board")
WIDTH, HEIGHT = 128, 64

# Wuerfel: (Knochen, Ursprung, Groesse, UV) — Einheiten 1/16 Block, y = 0 sind die Fuesse
DECK = ([-7, -3, -14], [14, 3, 28], [0, 0])
NOSE = ([-5, -3, -18], [10, 3, 4], [0, 31])
FLAMES = (([-4, -1, 13], [0, 10, 10], [32, 31]), ([0, -1, 14], [0, 13, 12], [56, 31]), ([4, -1, 13], [0, 10, 10], [32, 31]))


def geometry() -> dict:
    def cube(origin, size, uv):
        return {"origin": origin, "size": size, "uv": uv}

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "board", "parent": "root", "pivot": [0, -1.5, 0], "cubes": [cube(*DECK), cube(*NOSE)]},
    ]
    for i, flame in enumerate(FLAMES):
        bones.append({"name": f"flame_{i}", "parent": "board", "pivot": [flame[0][0], -1, flame[0][2]], "cubes": [cube(*flame)]})
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{NAME}", "texture_width": WIDTH, "texture_height": HEIGHT,
                        "visible_bounds_width": 3, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0, 0]},
        "bones": bones}]}


def lerp(a: tuple, b: tuple, t: float) -> tuple:
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


MAGMA_DARK = (92, 22, 8, 255)
MAGMA = (190, 54, 14, 255)
EMBER = (255, 138, 32, 255)
CORE = (255, 236, 150, 255)


def texture() -> Image.Image:
    rng = random.Random(1)
    img = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    px = img.load()

    def box(u, v, w, h, d, top):
        # Seiten: dunkles Magma mit Glutschimmer zur Oberkante
        for x in range(u, u + 2 * (w + d)):
            for y in range(v + d, v + d + h):
                t = (y - v - d) / max(1, h - 1)
                px[x, y] = lerp(EMBER, MAGMA_DARK, t) if rng.random() > 0.15 else MAGMA
        # Ober- und Unterseite
        for x in range(u + d, u + d + w):
            for y in range(v, v + d):
                px[x, y] = top(x - u - d, y - v, w, d)
        for x in range(u + d + w, u + d + 2 * w):
            for y in range(v, v + d):
                px[x, y] = lerp(MAGMA_DARK, MAGMA, rng.random() * 0.4)

    def deck_top(x, y, w, d):
        # gluehende Mittelnaht, zum Rand hin abkuehlendes Magma
        center = abs(x - (w - 1) / 2) / ((w - 1) / 2)
        base = lerp(CORE, EMBER, min(1.0, center * 2.2)) if center < 0.45 else lerp(EMBER, MAGMA, (center - 0.45) / 0.55)
        return base

    box(*DECK[2], DECK[1][0], DECK[1][1], DECK[1][2], deck_top)
    box(*NOSE[2], NOSE[1][0], NOSE[1][1], NOSE[1][2], lambda x, y, w, d: lerp(CORE, EMBER, abs(x - (w - 1) / 2) / ((w - 1) / 2)))
    # Risse quer ueber das Deck (dunkle Adern mit heller Kante)
    draw = ImageDraw.Draw(img)
    u0, v0 = DECK[2][0] + DECK[1][2], DECK[2][1]
    for i in range(7):
        y = v0 + 2 + i * 4 + rng.randint(-1, 1)
        side = rng.choice((0, 1))
        x0 = u0 + (0 if side == 0 else 10)
        draw.line([(x0, y), (x0 + 3, y + rng.choice((-1, 1)))], fill=MAGMA_DARK, width=1)
    # Flammenzungen (seitliche Flaechen der flachen Wuerfel): Flammenform mit Transparenz
    for (origin, size, uv) in FLAMES[:2]:
        u, v = uv
        dpt, h = size[2], size[1]
        for face in (0, 1):
            fx = u + face * (dpt + 0) if face == 0 else u + dpt
            for x in range(dpt):
                for y in range(h):
                    nx = x / max(1, dpt - 1)          # 0 vorn (am Brett) .. 1 hinten (Spitze)
                    ny = 1.0 - y / max(1, h - 1)      # 0 oben .. 1 unten
                    width = (1.0 - nx) * 0.9 + 0.1 * math.sin(nx * 9.0)
                    if ny > 1.0 - width:
                        heat = min(1.0, (ny - (1.0 - width)) / max(0.01, width))
                        px[fx + x, v + dpt + y] = lerp(EMBER, CORE, heat) if heat > 0.4 else lerp((255, 70, 10, 255), EMBER, heat / 0.4)
    return img


def animation() -> dict:
    return {"format_version": "1.8.0", "animations": {"play": {
        "loop": True,
        "animation_length": 1.0,
        "bones": {
            "board": {"position": {"0.0": [0, 0, 0], "0.5": [0, 0.6, 0], "1.0": [0, 0, 0]},
                      "rotation": {"0.0": [0, 0, -2], "0.5": [1.5, 0, 2], "1.0": [0, 0, -2]}},
            "flame_0": {"scale": {"0.0": [1, 1, 1], "0.25": [1, 1.25, 1.15], "0.5": [1, 0.9, 1], "0.75": [1, 1.2, 1.1], "1.0": [1, 1, 1]}},
            "flame_1": {"scale": {"0.0": [1, 1.2, 1.1], "0.25": [1, 0.9, 1], "0.5": [1, 1.3, 1.2], "0.75": [1, 1, 1], "1.0": [1, 1.2, 1.1]}},
            "flame_2": {"scale": {"0.0": [1, 0.95, 1], "0.25": [1, 1.2, 1.1], "0.5": [1, 1, 1], "0.75": [1, 1.3, 1.15], "1.0": [1, 0.95, 1]}},
        }}}}


def outputs() -> dict[Path, bytes]:
    buf = io.BytesIO()
    texture().save(buf, format="PNG", optimize=True)
    return {GEO: (json.dumps(geometry(), indent=2) + "\n").encode(), ANIMATION: (json.dumps(animation(), indent=2) + "\n").encode(),
            TEXTURE: buf.getvalue()}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, nichts schreiben")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    stale = 0
    for path, data in outputs().items():
        if args.check:
            if not path.is_file() or (path.suffix == ".json" and path.read_bytes() != data):
                LOG.error("veraltet oder fehlt: %s", path.relative_to(ROOT))
                stale += 1
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        LOG.info("geschrieben: %s", path.relative_to(ROOT))
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
