#!/usr/bin/env python3
"""Erzeugt die Partikel-Texturen von Kingdom Omnitrix (animierte 16x16-Bilder) und die passenden
Partikel-Definitionen (assets/kingdomomnitrix/particles/<name>.json). Dazu die Vignette fuer
Bildschirm-Effekte. Aufruf aus minecraft-mod/:

    python tools/generate_particles.py            # schreibt die Dateien
    python tools/generate_particles.py --check    # prueft nur, ob alles vorhanden und aktuell ist

Benoetigt Pillow (pip install pillow).
"""
from __future__ import annotations

import argparse
import json
import logging
import math
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - Hinweis fuer den Nutzer
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("particles")
DEFAULT_ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
SIZE = 16

RGB = tuple[int, int, int]


def hexc(value: str) -> RGB:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def blend(a: RGB, b: RGB, t: float) -> RGB:
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))  # type: ignore[return-value]


def canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def put(img: Image.Image, x: int, y: int, color: RGB, alpha: float) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE and alpha > 0.04:
        old = img.getpixel((x, y))
        a = max(old[3], int(255 * min(1.0, alpha)))
        img.putpixel((x, y), (*color, a))


# --- Formen ------------------------------------------------------------------------------------

def glow_orb(core: RGB, rim: RGB, radius: float) -> Image.Image:
    img = canvas()
    c = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - c, y - c) / radius
            if d <= 1.0:
                put(img, x, y, blend(core, rim, d), 1.0 - d * d * 0.85)
    return img


def star(core: RGB, rim: RGB, arms: int, length: float, width: float, rotation: float = 0.0) -> Image.Image:
    """Leuchtender Stern: helle Mitte, spitze Strahlen."""
    img = canvas()
    c = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            angle = math.atan2(dy, dx) - rotation
            # Abstand zum naechsten Strahl
            sector = (2 * math.pi) / arms
            off = abs(((angle + sector / 2) % sector) - sector / 2)
            ray = max(0.0, 1.0 - r / length) * max(0.0, 1.0 - off * r / max(0.3, width))
            core_glow = max(0.0, 1.0 - r / (length * 0.32))
            alpha = max(ray, core_glow)
            if alpha > 0:
                put(img, x, y, blend(core, rim, r / length), alpha)
    return img


def ring(color: RGB, radius: float, thickness: float) -> Image.Image:
    img = canvas()
    c = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            d = abs(math.hypot(x - c, y - c) - radius)
            if d < thickness:
                put(img, x, y, color, 1.0 - d / thickness)
    return img


def ember(core: RGB, rim: RGB, size: float, flicker: int) -> Image.Image:
    """Glut: unregelmaessige, unten breitere Flammenzunge."""
    img = canvas()
    c = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            dy = (y - c - 1) / size
            dx = (x - c + (math.sin(y * 0.9 + flicker) * 0.8)) / (size * (0.55 + 0.45 * max(0.0, dy + 0.6)))
            d = math.hypot(dx, dy * 0.8)
            if d < 1.0:
                put(img, x, y, blend(core, rim, min(1.0, d * 1.7)), 1.0 - d ** 3)
    return img


def shard(color: RGB, light: RGB, length: float, angle: float) -> Image.Image:
    """Eissplitter: schmale Raute mit heller Kante."""
    img = canvas()
    c = (SIZE - 1) / 2
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - c, y - c
            u = dx * cos_a + dy * sin_a
            v = -dx * sin_a + dy * cos_a
            d = abs(u) / length + abs(v) / (length * 0.38)
            if d < 1.0:
                put(img, x, y, blend(light, color, abs(v) / (length * 0.38) + 0.2), 1.0 - d * 0.6)
    return img


def zigzag(core: RGB, rim: RGB, seed: int) -> Image.Image:
    """Blitzfunke: gezackte Linie mit Leuchten."""
    img = canvas()
    points = []
    x = 3 + seed % 3
    for y in range(1, SIZE - 1, 3):
        points.append((x, y))
        x = 12 - x if (y // 3 + seed) % 2 else x + (2 if x < 8 else -2)
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        steps = max(abs(x1 - x0), abs(y1 - y0)) * 2 + 1
        for i in range(steps + 1):
            px = round(x0 + (x1 - x0) * i / steps)
            py = round(y0 + (y1 - y0) * i / steps)
            put(img, px, py, core, 1.0)
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(img, px + ox, py + oy, rim, 0.45)
    return img


def leaf(color: RGB, light: RGB, angle: float) -> Image.Image:
    img = canvas()
    c = (SIZE - 1) / 2
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - c, y - c
            u = dx * cos_a + dy * sin_a
            v = -dx * sin_a + dy * cos_a
            width = 3.2 * max(0.0, 1.0 - (u / 6.0) ** 2)
            if abs(u) < 6.0 and abs(v) < width:
                put(img, x, y, light if abs(v) < 0.7 else color, 1.0 - abs(u) / 8.0)
    return img


def streak(color: RGB, light: RGB, progress: float) -> Image.Image:
    """Leuchtstreifen fuer Schwung-Spuren: viele davon hintereinander ergeben einen durchgehenden Bogen."""
    img = canvas()
    c = (SIZE - 1) / 2
    half_length = 7.5 - progress * 2.0
    half_width = 2.6 - progress * 0.9
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot((x - c) / half_length, (y - c) / half_width)
            if d < 1.0:
                put(img, x, y, blend(light, color, d), (1.0 - d * d) * (1.0 - progress * 0.4))
    return img


def swirl(color: RGB, phase: float) -> Image.Image:
    img = canvas()
    c = (SIZE - 1) / 2
    for i in range(60):
        t = i / 60
        angle = phase + t * math.pi * 3
        r = 1.5 + t * 5.5
        put(img, round(c + math.cos(angle) * r), round(c + math.sin(angle) * r), color, 1.0 - t * 0.7)
    return img


# --- Partikel ----------------------------------------------------------------------------------

def particles() -> dict[str, list[Image.Image]]:
    green, green_light, white = hexc("39FF14"), hexc("D8FFD0"), hexc("FFFFFF")
    gold, gold_light = hexc("FFC94A"), hexc("FFF6D0")
    return {
        "omnitrix_flash": [star(white, green, 4, 7.5 - i * 0.8, 1.6, i * 0.2) for i in range(4)],
        "dna_helix": [glow_orb(green_light, green, 3.5), glow_orb(white, green, 2.8)],
        "omnitrix_revert": [star(hexc("FFD0C8"), hexc("FF3A2A"), 4, 6.0 - i, 1.2, 0.4 * i) for i in range(3)],
        "keyblade_spark": [star(white, gold, 4, 6.5 - i * 1.2, 1.1, math.pi / 4 * (i % 2)) for i in range(4)],
        "hit_spark": [star(white, hexc("FFE14A"), 8, 3.5 + i * 2.0, 0.9, 0.2 * i) for i in range(3)],
        "slash": [streak(hexc("9FD4FF"), white, i / 3) for i in range(3)],
        "finisher_ring": [ring(gold_light, 2.5 + i * 1.9, max(1.3, 1.7 - i * 0.15)) for i in range(4)],
        "fire_ember": [ember(hexc("FFFBE0"), hexc("FF7A1A"), 5.5 - i * 0.6, i) for i in range(3)],
        "ice_shard": [shard(hexc("8FD8FF"), white, 6.5, a) for a in (0.6, 1.4)],
        "thunder_spark": [zigzag(hexc("FFF6A0"), hexc("FFC800"), s) for s in range(3)],
        "cure_leaf": [leaf(hexc("5CE65C"), hexc("D8FFD0"), a) for a in (0.5, 1.2)],
        "plasma": [glow_orb(white, hexc("3DA8FF"), 5.5), glow_orb(hexc("BFE6FF"), hexc("2A6AFF"), 4.5)],
        "muzzle_flash": [star(hexc("FFF6D0"), hexc("FF9A3C"), 6, 7.0 - i * 1.5, 1.4, 0.3 * i) for i in range(2)],
        "rotor_wind": [swirl(white, p) for p in (0.0, 2.1, 4.2)],
    }


def vignette() -> Image.Image:
    """Weisse Vignette (Alpha nach aussen), wird im Spiel eingefaerbt."""
    size = 128
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot((x - c) / c, (y - c) / c) / math.sqrt(2)
            alpha = max(0.0, (d - 0.45) / 0.55) ** 1.6
            img.putpixel((x, y), (255, 255, 255, int(255 * min(1.0, alpha))))
    return img


def build_all() -> tuple[dict[str, Image.Image], dict[str, dict]]:
    images: dict[str, Image.Image] = {}
    definitions: dict[str, dict] = {}
    for name, frames in particles().items():
        textures = []
        for i, frame in enumerate(frames):
            images[f"textures/particle/{name}_{i}.png"] = frame
            textures.append(f"kingdomomnitrix:{name}_{i}")
        definitions[f"particles/{name}.json"] = {"textures": textures}
    images["textures/gui/vignette.png"] = vignette()
    return images, definitions


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="assets/kingdomomnitrix-Ordner (Standard: %(default)s)")
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alles vorhanden und aktuell ist")
    parser.add_argument("--sheet", type=Path, help="zusaetzlich eine vergroesserte Uebersicht speichern")
    parser.add_argument("-v", "--verbose", action="store_true", help="jede Datei protokollieren")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    images, definitions = build_all()
    if args.check:
        problems = [p for p in images if not (args.root / p).is_file()]
        for rel, data in definitions.items():
            target = args.root / rel
            if not target.is_file() or json.loads(target.read_text(encoding="utf-8")) != data:
                problems.append(rel)
        for p in problems:
            LOG.error("fehlt oder veraltet: %s", p)
        LOG.info("%d/%d Partikel-Dateien in Ordnung", len(images) + len(definitions) - len(problems), len(images) + len(definitions))
        return 1 if problems else 0

    try:
        for rel, img in images.items():
            target = args.root / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            img.save(target)
        for rel, data in definitions.items():
            target = args.root / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    except OSError as exc:
        LOG.error("konnte nicht schreiben: %s", exc)
        return 1
    LOG.info("%d Texturen und %d Partikel-Definitionen nach %s geschrieben", len(images), len(definitions), args.root)

    if args.sheet:
        frames = [img for rel, img in images.items() if rel.startswith("textures/particle/")]
        columns, scale = 12, 5
        rows = (len(frames) + columns - 1) // columns
        sheet = Image.new("RGBA", (columns * 18 * scale, rows * 18 * scale), (20, 24, 34, 255))
        for i, img in enumerate(frames):
            sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST),
                                  ((i % columns) * 18 * scale + scale, (i // columns) * 18 * scale + scale))
        sheet.save(args.sheet)
        LOG.info("Uebersicht: %s", args.sheet)
    return 0


if __name__ == "__main__":
    sys.exit(main())
