#!/usr/bin/env python3
"""Erzeugt die Energie-Textur fuer die Alien-Aura (textures/entity/aura_swirl.png).

Graustufen auf Schwarz: der Aura-Renderer mischt additiv und faerbt ueber die Vertex-Farbe, darum darf die Textur
selbst keine Farbe haben (die Creeper-Textur von Minecraft ist blaeulich und verfaelscht Rot/Orange). Schraege,
unterbrochene Energiebaender, nahtlos kachelbar (64 x 64).

    python tools/generate_aura_texture.py          # schreibt die Textur
    python tools/generate_aura_texture.py --check  # prueft, ob die Datei aktuell ist
"""
from __future__ import annotations

import argparse
import io
import logging
import math
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("generate_aura_texture")
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/kingdomomnitrix/textures/entity/aura_swirl.png"
SIZE = 64


def build() -> Image.Image:
    img = Image.new("RGB", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            # zwei gegenlaeufige Baender (kachelbar: ganze Perioden ueber 64 Pixel), dazu feines Flimmern
            a = math.sin(2 * math.pi * (x + y) * 3 / SIZE)
            b = math.sin(2 * math.pi * (x * 2 - y) / SIZE + 1.3)
            c = math.sin(2 * math.pi * (x * 5 + y * 7) / SIZE)
            v = max(0.0, a) ** 3 * 0.75 + max(0.0, b) ** 6 * 0.45 + max(0.0, c) ** 8 * 0.2
            g = int(min(1.0, v) * 255)
            px[x, y] = (g, g, g)
    return img


def encoded(img: Image.Image) -> bytes:
    buffer = io.BytesIO()
    img.save(buffer, format="PNG")
    return buffer.getvalue()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Textur aktuell ist")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    data = encoded(build())
    if args.check:
        if not OUT.is_file() or OUT.read_bytes() != data:
            LOG.error("veraltet oder fehlt: %s", OUT)
            return 1
        LOG.info("Aura-Textur aktuell")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(data)
    LOG.info("geschrieben: %s", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
