#!/usr/bin/env python3
"""Erzeugt die Inventarbilder, Item-Modelle und Rezepte der Omnitrix-Boost-Module (Omnitrix-Upgrades).

Jedes Modul ist ein kleiner Chip im Omnitrix-Stil: dunkles Gehaeuse mit Kontaktbeinchen, gruene Leiterbahnen, in der
Mitte ein Kern in der Farbe des Moduls mit dem Sanduhr-Zeichen des Omnitrix. 32 x 32 Pixel.

    python tools/generate_boost_icons.py           # schreiben
    python tools/generate_boost_icons.py --check   # pruefen, ob die Dateien aktuell sind
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import sys
from pathlib import Path

import numpy as np
from PIL import Image

LOG = logging.getLogger("boost_icons")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
DATA = ROOT / "src" / "main" / "resources" / "data" / "kingdomomnitrix"
SIZE = 32

# Modul -> (Kernfarbe, Zutat im Rezept)
BOOSTS = {
    "power": ("FF5A3A", "minecraft:blaze_powder"),
    "guard": ("5AA0FF", "minecraft:iron_ingot"),
    "swift": ("FFE04A", "minecraft:sugar"),
    "duration": ("3AFF6A", "minecraft:clock"),
    "cooling": ("8AE8FF", "minecraft:packed_ice"),
    "recharge": ("C05AFF", "minecraft:redstone_block"),
}


def rgb(hex_color: str) -> np.ndarray:
    return np.array([int(hex_color[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def icon(core_hex: str) -> Image.Image:
    img = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    core = rgb(core_hex)

    def put(x0: int, y0: int, x1: int, y1: int, color: np.ndarray, alpha: int = 255) -> None:
        img[y0:y1, x0:x1, :3] = np.clip(color, 0, 255)
        img[y0:y1, x0:x1, 3] = alpha

    dark, edge, light = rgb("14181E"), rgb("3A424C"), rgb("6A7480")
    pin, trace = rgb("B8C0C8"), rgb("2ACF5A")
    # Kontaktbeinchen an allen vier Seiten
    for i in range(4):
        p = 9 + i * 4
        put(p, 3, p + 2, 7, pin)
        put(p, 25, p + 2, 29, pin)
        put(3, p, 7, p + 2, pin)
        put(25, p, 29, p + 2, pin)
    # Gehaeuse mit Fase
    put(6, 6, 26, 26, edge)
    put(7, 7, 25, 25, dark)
    put(7, 7, 25, 8, light)
    put(7, 7, 8, 25, light * 0.8)
    put(7, 24, 25, 25, dark * 0.5)
    put(24, 7, 25, 25, dark * 0.6)
    # Leiterbahnen
    for x0, y0, x1, y1 in ((9, 10, 13, 11), (19, 10, 23, 11), (9, 21, 13, 22), (19, 21, 23, 22), (10, 11, 11, 14), (21, 18, 22, 21)):
        put(x0, y0, x1, y1, trace)
    # Kern: Fassung in Modulfarbe, dunkles Feld, darin die Sanduhr des Omnitrix in Modulfarbe mit hellem Kern
    put(11, 11, 21, 21, core * 0.6)
    put(12, 12, 20, 20, rgb("0A0C10"))
    for y in range(13, 19):
        half = 1 + int(abs(y - 15.5))
        put(16 - half, y, 16 + half, y + 1, core)
    put(15, 15, 17, 17, np.minimum(core * 0.4 + 255 * 0.6, 255))
    put(13, 13, 15, 14, np.minimum(core * 0.3 + 255 * 0.7, 255))
    return Image.fromarray(img, "RGBA")


def png(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def text(data: dict) -> bytes:
    return (json.dumps(data, indent=2) + "\n").encode()


def outputs() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for name, (core, ingredient) in BOOSTS.items():
        item = f"boost_{name}"
        files[ASSETS / "textures" / "item" / f"{item}.png"] = png(icon(core))
        files[ASSETS / "models" / "item" / f"{item}.json"] = text({"parent": "minecraft:item/generated",
                                                                    "textures": {"layer0": f"kingdomomnitrix:item/{item}"}})
        files[DATA / "recipe" / f"{item}.json"] = text({
            "type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": [" M ", "RXR", " M "],
            "key": {"M": {"item": "kingdomomnitrix:mythril_shard"}, "R": {"item": "kingdomomnitrix:raritanium"},
                    "X": {"item": ingredient}},
            "result": {"id": f"kingdomomnitrix:{item}", "count": 1}})
    return files


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    files = outputs()
    if args.check:
        stale = [p for p, data in files.items() if not p.is_file() or p.read_bytes() != data]
        for path in stale:
            LOG.error("veraltet oder fehlt: %s", path.relative_to(ROOT))
        LOG.info("%d/%d Boost-Dateien aktuell", len(files) - len(stale), len(files))
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    LOG.info("%d Boost-Dateien geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
