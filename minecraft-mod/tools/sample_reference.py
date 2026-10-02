#!/usr/bin/env python3
"""Tastet Vorderseiten aus einem Referenz-Render ab (Alien-Evolution-Vorlagen, Erlaubnis siehe reference/README.md).

Zwei Verfahren:
  * Rechteck: festes Raster (x0, y0, Texelbreite, Texelhoehe, Spalten, Zeilen) — fuer Kopf, Rumpf, Kragen.
  * Umriss:   je Texelzeile das sichtbare Segment des Koerperteils suchen und gleichmaessig in Spalten teilen —
              fuer schraeg stehende Arme und perspektivisch verjuengte Beine.
Je Zelle zaehlt der Median der inneren Pixel; transparente Zellen bleiben transparent.

Aufruf aus dem Ordner minecraft-mod/:

    python tools/sample_reference.py heatblast        # schreibt tools/reference/heatblast/*.png
    python tools/sample_reference.py heatblast --check
"""
from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path
from statistics import median

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("sample_reference")
ROOT = Path(__file__).resolve().parent / "reference"
Px = tuple[int, int, int, int]


def cell(im: Image.Image, x0: float, x1: float, y0: float, y1: float) -> Px:
    pts = []
    for fx in (0.3, 0.4, 0.5, 0.6, 0.7):
        for fy in (0.3, 0.4, 0.5, 0.6, 0.7):
            x = min(im.width - 1, max(0, int(x0 + (x1 - x0) * fx)))
            y = min(im.height - 1, max(0, int(y0 + (y1 - y0) * fy)))
            pts.append(im.getpixel((x, y)))
    px = tuple(int(median(p[k] for p in pts)) for k in range(4))
    return px if px[3] > 128 else (0, 0, 0, 0)


def rect(im: Image.Image, x0: float, y0: float, x1: float, y1: float, cols: int, rows: int) -> Image.Image:
    out = Image.new("RGBA", (cols, rows))
    tw, th = (x1 - x0) / cols, (y1 - y0) / rows
    for j in range(rows):
        for i in range(cols):
            out.putpixel((i, j), cell(im, x0 + i * tw, x0 + (i + 1) * tw, y0 + j * th, y0 + (j + 1) * th))
    return out


def segment(im: Image.Image, y: int, xmin: int, xmax: int) -> tuple[int, int] | None:
    xs = [x for x in range(max(0, xmin), min(im.width, xmax)) if im.getpixel((x, y))[3] > 20]
    if not xs:
        return None
    # laengstes zusammenhaengendes Stueck
    best, start, prev = (xs[0], xs[0]), xs[0], xs[0]
    for x in xs[1:]:
        if x != prev + 1:
            start = x
        if x - start > best[1] - best[0]:
            best = (start, x)
        prev = x
    return best


def outline(im: Image.Image, y0: float, y1: float, xmin: int, xmax: int, cols: int, rows: int) -> Image.Image:
    out = Image.new("RGBA", (cols, rows))
    th = (y1 - y0) / rows
    for j in range(rows):
        yc = int(y0 + (j + 0.5) * th)
        seg = segment(im, yc, xmin, xmax)
        if seg is None:
            continue
        tw = (seg[1] + 1 - seg[0]) / cols
        for i in range(cols):
            out.putpixel((i, j), cell(im, seg[0] + i * tw, seg[0] + (i + 1) * tw, y0 + j * th, y0 + (j + 1) * th))
    return out


def inpaint(img: Image.Image, box: tuple[int, int, int, int]) -> Image.Image:
    """Bereich (z. B. unscharfes Omnitrix-Logo) mit den Farben der Nachbarzeilen ausfuellen."""
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        above, below = img.getpixel((x, max(0, y0 - 1))), img.getpixel((x, min(img.height - 1, y1)))
        for y in range(y0, y1):
            img.putpixel((x, y), above if (y - y0) < (y1 - y0) / 2 else below)
    return img


def heatblast(src: Image.Image) -> dict[str, Image.Image]:
    out = {
        # Flammen ueber dem Kopf: 20 Spalten (inkl. Seitenflammen-Spalten), 12 Zeilen = 6 Einheiten
        "flame": rect(src, 142.6, 0, 334, 118, 20, 12),
        # Kopf mit Seitenflammen: 20 x 16 (Spalten 2..17 = Gesicht)
        "head_wide": rect(src, 142.6, 118, 334, 275, 20, 16),
        "head": rect(src, 162, 118, 315, 275, 16, 16),
        "collar": rect(src, 159, 248, 317, 298, 16, 5),
        "torso": inpaint(rect(src, 157, 298, 319, 630, 16, 34), (5, 4, 11, 11)),
        # Arme: Bild-links = rechter Arm des Aliens
        "arm_r_upper": outline(src, 296, 512, 0, 150, 8, 20),
        "arm_r_fist": outline(src, 512, 736, 0, 150, 12, 20),
        "arm_l_upper": outline(src, 296, 512, 330, 480, 8, 20),
        "arm_l_fist": outline(src, 512, 736, 330, 480, 12, 20),
        "leg_r": outline(src, 632, 882, 120, 238, 8, 26),
        "leg_l": outline(src, 632, 882, 240, 360, 8, 26),
        "foot_r": outline(src, 882, 918, 100, 238, 10, 4),
        "foot_l": outline(src, 882, 918, 240, 380, 10, 4),
    }
    # ganzer Arm (Oberarm + Faust) fuer die Ego-Sicht
    for side in ("r", "l"):
        upper = out[f"arm_{side}_upper"].resize((12, 20), Image.NEAREST)
        full = Image.new("RGBA", (12, 40))
        full.paste(upper, (0, 0))
        full.paste(out[f"arm_{side}_fist"], (0, 20))
        out[f"arm_{side}_full"] = full
    return out


def xlr8(src: Image.Image) -> dict[str, Image.Image]:
    """XLR8 (Kineceleran), Vorlage KineceleranOS: ~24,5 px pro Einheit, Texel 12,25 px (doppelte Dichte)."""
    out = {
        "head": rect(src, 227, 14, 423, 196, 16, 16),
        "torso": inpaint(inpaint(rect(src, 218, 210, 432, 574, 20, 30), (7, 9, 13, 15)), (0, 12, 2, 19)),
        "pad_r": rect(src, 105, 212, 204, 336, 8, 10),
        "pad_l": rect(src, 446, 212, 545, 336, 8, 10),
        # Arme: Bild-rechts (ohne verdeckenden Schwanz) fuer beide Seiten
        "arm_upper": outline(src, 336, 490, 470, 640, 8, 12),
        "hand": outline(src, 490, 650, 500, 640, 10, 8),
        "thigh_r": outline(src, 574, 760, 165, 300, 10, 15),
        "thigh_l": outline(src, 574, 760, 355, 495, 10, 15),
        "shin_r": outline(src, 760, 910, 165, 300, 8, 12),
        "shin_l": outline(src, 760, 910, 355, 495, 8, 12),
        "foot_r": outline(src, 910, 1000, 130, 290, 10, 7),
        "foot_l": outline(src, 910, 1000, 360, 520, 10, 7),
    }
    upper = out["arm_upper"].resize((10, 24), Image.NEAREST)
    full = Image.new("RGBA", (10, 32))
    full.paste(upper, (0, 0))
    full.paste(out["hand"], (0, 24))
    out["arm_full"] = full
    return out


ALIENS = {"heatblast": heatblast, "xlr8": xlr8}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("alien", choices=sorted(ALIENS))
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    source = ROOT / "source" / f"{args.alien}.png"
    if not source.is_file():
        LOG.error("Vorlage fehlt: %s", source)
        return 1
    images = ALIENS[args.alien](Image.open(source).convert("RGBA"))
    target = ROOT / args.alien
    stale = 0
    for name, img in images.items():
        path = target / f"{name}.png"
        if args.check:
            if not path.is_file() or Image.open(path).convert("RGBA").tobytes() != img.tobytes():
                LOG.error("veraltet: %s", path.relative_to(ROOT))
                stale += 1
            continue
        target.mkdir(parents=True, exist_ok=True)
        img.save(path)
    LOG.info("%s: %d Teile %s", args.alien, len(images), "geprueft" if args.check else "geschrieben")
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
