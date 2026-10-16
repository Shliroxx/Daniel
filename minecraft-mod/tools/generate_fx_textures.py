#!/usr/bin/env python3
"""Texturen der FX-Engine (Strahlen, Explosionen, Flammen, Schockwellen) — alle in Graustufen bzw. Weiss mit Alpha,
damit die Engine sie per Vertexfarbe in jeder Alien-Farbe einfaerbt (Feuer, Eis, Saeure, Energie ...).

Ausgabe nach assets/kingdomomnitrix/textures/fx/:
  glow.png         weicher Lichtpunkt (Halo)                       128x128
  flare.png        Stern-Blitz mit Strahlen (Aufprall, Muendung)    256x256
  ring.png         Schockwellen-Ring mit weichem Innenrand          256x256
  beam.png         Energiestrahl, laengs kachelbar (u laeuft)       256x64
  spark.png        Funken-Strich                                   64x64
  shard.png        Splitter/Kristall                               64x64
  flame.png        Flammenzunge, 16 Bilder (4x4)                    512x512
  explosion.png    Feuerball-Explosion, 16 Bilder (4x4)             512x512
  smoke.png        Rauchwolke, 16 Bilder (4x4)                      512x512
  scorch.png       Brandfleck/Riss am Boden                         128x128

Deterministisch (fester Seed). Aufruf aus minecraft-mod/:
    python tools/generate_fx_textures.py          # schreiben
    python tools/generate_fx_textures.py --check  # vorhanden?
"""
from __future__ import annotations

import argparse
import logging
import math
import sys
from pathlib import Path

try:
    import numpy as np
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("numpy und Pillow fehlen: pip install numpy pillow")

LOG = logging.getLogger("fx_textures")
OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix" / "textures" / "fx"


def noise(rng: np.random.Generator, size: int, cells: int, octaves: int = 4) -> np.ndarray:
    total = np.zeros((size, size))
    amp, norm = 1.0, 0.0
    ys, xs = np.mgrid[0:size, 0:size] / size
    for o in range(octaves):
        c = cells * 2 ** o
        lat = rng.random((c + 1, c + 1))
        fx, fy = xs * c, ys * c
        x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
        tx, ty = fx - x0, fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = lat[y0, x0] * (1 - tx) + lat[y0, x0 + 1] * tx
        b = lat[y0 + 1, x0] * (1 - tx) + lat[y0 + 1, x0 + 1] * tx
        total += amp * (a * (1 - ty) + b * ty)
        norm += amp
        amp *= 0.5
    return total / norm


def rgba(lum: np.ndarray, alpha: np.ndarray, premultiply: bool = True) -> Image.Image:
    """Leuchtbilder vormultipliziert (RGB = Helligkeit x Alpha), weil die Leucht-Ebene additiv mischt und Alpha
    ignoriert; Rauch und Brandfleck (normale Alpha-Mischung) nicht."""
    if premultiply:
        lum = np.clip(lum, 0, 1) * np.clip(alpha, 0, 1)
    l8 = (np.clip(lum, 0, 1) * 255).astype(np.uint8)
    a8 = (np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(np.dstack([l8, l8, l8, a8]), "RGBA")


def radial(size: int) -> tuple[np.ndarray, np.ndarray]:
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    c = (size - 1) / 2
    dx, dy = (xs - c) / c, (ys - c) / c
    return np.hypot(dx, dy), np.arctan2(dy, dx)


def glow() -> Image.Image:
    r, _ = radial(128)
    a = np.clip(1 - r, 0, 1) ** 2.2
    return rgba(np.ones_like(a), a)


def flare() -> Image.Image:
    r, ang = radial(256)
    core = np.clip(1 - r * 2.2, 0, 1) ** 1.5
    halo = np.clip(1 - r, 0, 1) ** 3 * 0.6
    rays = (np.abs(np.cos(ang * 4)) ** 40 + 0.6 * np.abs(np.cos(ang * 4 + math.pi / 4)) ** 60) * np.clip(1 - r, 0, 1) ** 1.5
    a = np.clip(core + halo + rays, 0, 1)
    return rgba(np.ones_like(a), a)


def ring() -> Image.Image:
    rng = np.random.default_rng(11)
    r, ang = radial(256)
    n = noise(rng, 256, 6, 3)
    edge = np.exp(-((r - 0.9) / 0.05) ** 2)
    inner = np.clip((r - 0.45) / 0.45, 0, 1) ** 2 * (r < 0.95) * 0.55
    a = np.clip((edge + inner) * (0.75 + 0.5 * n), 0, 1)
    return rgba(np.ones_like(a), a)


def beam() -> Image.Image:
    rng = np.random.default_rng(12)
    w, h = 256, 64
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    v = (ys - (h - 1) / 2) / ((h - 1) / 2)
    # laengs kachelbar: Rauschen periodisch ueber Sinus-Mischung
    streak = 0.5 + 0.25 * np.sin(xs / w * 2 * math.pi * 3 + v * 3) + 0.25 * np.sin(xs / w * 2 * math.pi * 7 - v * 5)
    core = np.exp(-(v / 0.18) ** 2)
    outer = np.exp(-(v / 0.6) ** 2) * (0.5 + 0.5 * streak)
    a = np.clip(core + outer * 0.8, 0, 1)
    lum = np.clip(0.6 + core, 0, 1)
    del rng
    return rgba(lum, a)


def spark() -> Image.Image:
    ys, xs = np.mgrid[0:64, 0:64].astype(np.float64)
    v = (ys - 31.5) / 31.5
    u = (xs - 31.5) / 31.5
    a = np.exp(-(v / 0.12) ** 2) * np.clip(1 - np.abs(u), 0, 1) ** 1.5
    return rgba(np.ones_like(a), a)


def shard() -> Image.Image:
    ys, xs = np.mgrid[0:64, 0:64].astype(np.float64)
    u = (xs - 31.5) / 31.5
    v = (ys - 31.5) / 31.5
    inside = np.abs(u) * 2.6 + np.abs(v) < 1.0
    facet = np.where(u > 0, 0.75, 1.0) * (1 - 0.3 * (v + 1) / 2)
    return rgba(facet, inside * 0.95)


def sheet(frames: list[np.ndarray], alphas: list[np.ndarray], size: int, premultiply: bool = True) -> Image.Image:
    out = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    for i, (lum, a) in enumerate(zip(frames, alphas)):
        out.paste(rgba(lum, a, premultiply), ((i % 4) * size, (i // 4) * size))
    return out


def flame() -> Image.Image:
    """Flammenzunge: Rauschen scrollt nach oben, Raender zuengeln, Spitzen reissen ab."""
    rng = np.random.default_rng(13)
    size = 128
    field = noise(rng, size * 4, 12, 4)
    field2 = noise(np.random.default_rng(31), size * 4, 4, 3)
    frames, alphas = [], []
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    u = (xs - 63.5) / 63.5
    v = ys / (size - 1)               # 0 oben, 1 unten
    for f in range(16):
        off = f * 24
        n = field[off:off + size, :size]
        w2 = field2[off:off + size, :size]
        sway = (w2 - 0.5) * 0.5 * (1 - v)
        width = 0.2 + 0.7 * v ** 0.7
        uu = (u + sway) / width
        # mehrere Zungen: Kosinus quer, phasenverschoben je Bild
        tongues = 0.5 + 0.5 * np.cos(uu * math.pi * 1.6 + f * 0.4 + (n - 0.5) * 3)
        height = 0.15 + 0.55 * tongues + (n - 0.5) * 0.35
        top = np.clip((v - (1 - height) * 0.85) * 5, 0, 1)
        side = np.clip((1 - np.abs(uu)) * 2.5, 0, 1)
        bottom = np.clip((1 - v) * 7, 0, 1)
        a = np.clip(top * side * bottom * (0.65 + 0.5 * n), 0, 1)
        lum = np.clip(0.45 + 0.6 * side * v + n * 0.2, 0, 1)
        frames.append(lum)
        alphas.append(a)
    return sheet(frames, alphas, size, premultiply=False)


def explosion() -> Image.Image:
    rng = np.random.default_rng(14)
    size = 128
    r, ang = radial(size)
    frames, alphas = [], []
    detail = noise(rng, size, 5, 5)
    for f in range(16):
        t = f / 15
        radius = 0.25 + 0.75 * (1 - (1 - t) ** 2.5)
        lumpy = radius * (0.85 + 0.3 * detail)
        body = np.clip((lumpy - r) / 0.15, 0, 1)
        hollow = np.clip((r - lumpy * t * 0.8) / 0.2, 0, 1) if t > 0.4 else 1.0
        a = body * hollow * (1 - t ** 3)
        heat = np.clip(1 - r / np.maximum(lumpy, 1e-3), 0, 1) * (1 - t)
        # Wolkenstruktur: helle Ballen, dunkle Taeler, Rand dunkler (Russ)
        billow = noise(np.random.default_rng(40 + f), size, 6, 4)
        rim = np.clip((r / np.maximum(lumpy, 1e-3) - 0.6) / 0.4, 0, 1)
        lum = np.clip(0.86 + heat * 0.3 + (billow - 0.5) * 0.45 - rim * 0.3 - t * 0.2, 0.15, 1)
        frames.append(lum)
        alphas.append(np.clip(a, 0, 1))
    return sheet(frames, alphas, size, premultiply=False)


def smoke() -> Image.Image:
    rng = np.random.default_rng(15)
    size = 128
    r, _ = radial(size)
    frames, alphas = [], []
    for f in range(16):
        t = f / 15
        n = noise(rng, size, 3, 5)
        a = np.clip((0.55 + 0.4 * t - r + (n - 0.5) * 0.5) / 0.3, 0, 1) * (1 - t) ** 1.2 * 0.85 * np.clip((1 - r) * 3, 0, 1)
        lum = 0.75 + n * 0.25
        frames.append(lum)
        alphas.append(a)
    return sheet(frames, alphas, size, premultiply=False)


def scorch() -> Image.Image:
    rng = np.random.default_rng(16)
    r, ang = radial(128)
    n = noise(rng, 128, 4, 5)
    blot = np.clip((0.75 + (n - 0.5) * 0.6 - r) / 0.2, 0, 1)
    cracks = (np.abs(np.sin(ang * 7 + n * 6)) < 0.08) & (r < 0.95)
    a = np.clip(blot * 0.8 + cracks * 0.6 * (1 - r), 0, 1)
    return rgba(np.full_like(a, 0.1) + n * 0.15, a, premultiply=False)


TEXTURES = {"glow": glow, "flare": flare, "ring": ring, "beam": beam, "spark": spark, "shard": shard, "flame": flame,
            "explosion": explosion, "smoke": smoke, "scorch": scorch}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if args.check:
        missing = [n for n in TEXTURES if not (OUT / f"{n}.png").is_file()]
        for n in missing:
            LOG.error("fehlt: textures/fx/%s.png", n)
        LOG.info("FX-Texturen: %d/%d vorhanden", len(TEXTURES) - len(missing), len(TEXTURES))
        return 1 if missing else 0
    try:
        OUT.mkdir(parents=True, exist_ok=True)
        for name, fn in TEXTURES.items():
            fn().save(OUT / f"{name}.png", optimize=True)
    except OSError as exc:
        LOG.error("Schreiben fehlgeschlagen: %s", exc)
        return 1
    LOG.info("FX-Texturen geschrieben: %d", len(TEXTURES))
    return 0


if __name__ == "__main__":
    sys.exit(main())
