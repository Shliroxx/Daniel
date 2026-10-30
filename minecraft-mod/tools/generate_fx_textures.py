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
  flame.png        Flammenzunge, 16 Bilder (4x4), Graustufen        1024x1024
  flame_hot.png    dieselbe Flamme in echten Feuerfarben            1024x1024
  explosion.png    Feuerball-Explosion, 16 Bilder, Graustufen       1024x1024
  explosion_hot.png  Feuerball in Feuerfarben (Weiss→Gelb→Rot→Russ)  1024x1024
  smoke.png        beleuchtete Rauchwolke, 16 Bilder (4x4)          1024x1024
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


FRAME = 256


def fbm(rng: np.random.Generator, size: int, cells: int, octaves: int = 5) -> np.ndarray:
    """Fraktales Rauschen 0..1 (Summe von Wertrauschen-Oktaven), auf vollen Kontrast gestreckt."""
    n = noise(rng, size, cells, octaves)
    lo, hi = np.percentile(n, 2), np.percentile(n, 98)
    return np.clip((n - lo) / max(hi - lo, 1e-6), 0, 1)


def warp(field: np.ndarray, wx: np.ndarray, wy: np.ndarray, amount: float) -> np.ndarray:
    """Domain-Warping: liest das Feld an verschobenen Stellen (wirbelige, organische Formen)."""
    h, w = field.shape
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    sx = np.clip(xs + (wx - 0.5) * amount * w, 0, w - 1).astype(int)
    sy = np.clip(ys + (wy - 0.5) * amount * h, 0, h - 1).astype(int)
    return field[sy, sx]


def shade(height: np.ndarray, light=(-0.55, -0.75), strength: float = 6.0) -> np.ndarray:
    """Beleuchtung aus dem Hoehenfeld (Licht von oben links): Ballen bekommen Licht- und Schattenseiten."""
    gy, gx = np.gradient(height)
    lit = -(gx * light[0] + gy * light[1]) * strength * height.shape[0] / 64.0
    return np.clip(0.5 + lit, 0, 1)


HEAT_STOPS = [(0.00, (0.10, 0.05, 0.04)), (0.22, (0.42, 0.06, 0.02)), (0.42, (0.85, 0.20, 0.03)), (0.60, (1.00, 0.48, 0.06)),
              (0.78, (1.00, 0.78, 0.25)), (0.92, (1.00, 0.95, 0.65)), (1.00, (1.00, 1.00, 0.95))]


def heat_ramp(h: np.ndarray) -> np.ndarray:
    """Hitze 0..1 → Farbe (Russ → Glut → Orange → Gelb → Weiss)."""
    h = np.clip(h, 0, 1)
    out = np.zeros(h.shape + (3,))
    for (a, ca), (b, cb) in zip(HEAT_STOPS, HEAT_STOPS[1:]):
        m = (h >= a) & (h <= b)
        t = ((h - a) / (b - a))[..., None]
        out[m] = (np.array(ca) * (1 - t) + np.array(cb) * t)[m]
    return out


def rgb_image(color: np.ndarray, alpha: np.ndarray) -> Image.Image:
    c8 = (np.clip(color, 0, 1) * 255).astype(np.uint8)
    a8 = (np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(np.dstack([c8, a8]), "RGBA")


def sheet_images(images: list[Image.Image], size: int) -> Image.Image:
    out = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    for i, im in enumerate(images):
        out.paste(im, ((i % 4) * size, (i // 4) * size))
    return out


def _flame_frames():
    """Flammenzunge, 16 Bilder: aufsteigendes, verwirbeltes Rauschen wird von einer Tropfenform maskiert; wo das
    Rauschen schwach ist, reisst die Flamme in einzelne Zungen auf. Liefert je Bild (Hitze 0..1, Alpha)."""
    size = FRAME
    big = fbm(np.random.default_rng(113), size * 6, 6, 6)
    fine = fbm(np.random.default_rng(117), size * 6, 18, 4)
    wx = fbm(np.random.default_rng(114), size * 6, 3, 3)
    wy = fbm(np.random.default_rng(115), size * 6, 3, 3)
    field = warp(big * 0.65 + fine * 0.35, wx, wy, 0.06)
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    u = (xs - (size - 1) / 2) / ((size - 1) / 2)
    v = ys / (size - 1)               # 0 oben, 1 unten
    out = []
    for f in range(16):
        t = f / 15
        life = min(1.0, 0.35 + t * 4) * (1 - max(0.0, (t - 0.65) / 0.35) ** 1.3)
        off = int((15 - f) * size * 0.22)          # Rauschen steigt pro Bild nach oben
        col = (f * 53) % (size * 4)
        n = field[off:off + size, col:col + size]
        sway = (n - 0.5) * 0.45 * (1 - v)
        width = 0.18 + 0.5 * v ** 0.9
        mask = np.exp(-((u + sway) / width) ** 2 * 1.6) * np.clip((1 - v) / 0.05, 0, 1)
        lift = 1.0 - v                             # 1 oben
        density = mask * (0.2 + 1.3 * n ** 1.3) * life - lift * (0.95 - 0.4 * life)
        a = np.clip(density * 2.6, 0, 1)
        heat = np.clip(density * 1.05 + mask * v * 0.15, 0, 1)
        out.append((heat, a))
    return out


def flame() -> Image.Image:
    """Graustufen-Flamme (wird je Alien eingefaerbt)."""
    return sheet_images([rgb_image(np.repeat((0.35 + 0.65 * h)[..., None], 3, axis=2), a) for h, a in _flame_frames()], FRAME)


def flame_hot() -> Image.Image:
    """Echte Feuerfarben (fuer warme Farben: Heatblast, Explosionen)."""
    return sheet_images([rgb_image(heat_ramp(h * 0.9 + 0.15), a) for h, a in _flame_frames()], FRAME)


def _explosion_frames():
    """Feuerball, 16 Bilder: weisser Blitzkern → brodelnde, beleuchtete Feuerballen → Glut → dunkler Rauchpilz.
    Liefert je Bild (Hitze 0..1, Helligkeit 0..1, Alpha)."""
    size = FRAME
    r, ang = radial(size)
    base = fbm(np.random.default_rng(214), size, 4, 6)
    wx = fbm(np.random.default_rng(215), size, 3, 4)
    wy = fbm(np.random.default_rng(216), size, 3, 4)
    out = []
    for f in range(16):
        t = f / 15
        radius = 0.22 + 0.72 * (1 - (1 - t) ** 2.2)
        billow = warp(fbm(np.random.default_rng(300 + f // 2), size, 5, 6) * 0.6 + base * 0.4, wx, wy, 0.1 + t * 0.15)
        edge = radius * (0.78 + 0.34 * billow)
        body = np.clip((edge - r) / (0.08 + 0.1 * t), 0, 1)
        # spaete Bilder: die Mitte reisst auf (Rauchring), Ballen loesen sich
        if t > 0.55:
            body *= np.clip((r / np.maximum(edge, 1e-3) - (t - 0.55) * 1.4 + billow * 0.5) * 2.5, 0, 1)
        a = np.clip(body * (1.15 - t ** 2.5), 0, 1)
        dome = np.sqrt(np.clip(1 - (r / np.maximum(edge, 1e-3)) ** 2, 0, 1))
        height = dome * 0.6 + billow * 0.5
        light = shade(height, strength=5.0)
        core = np.clip(1 - r / np.maximum(edge * 0.75, 1e-3), 0, 1)
        heat = np.clip((0.55 + core * 0.7 + (billow - 0.5) * 0.6) * (1.15 - t * 1.05), 0, 1)
        lum = np.clip(0.35 + light * 0.75 + heat * 0.25 - t * 0.25, 0.05, 1)
        out.append((heat, lum, a))
    return out


def explosion() -> Image.Image:
    return sheet_images([rgb_image(np.repeat(l[..., None], 3, axis=2), a) for h, l, a in _explosion_frames()], FRAME)


def explosion_hot() -> Image.Image:
    images = []
    for h, l, a in _explosion_frames():
        color = heat_ramp(h) * (0.55 + 0.6 * l)[..., None]
        images.append(rgb_image(color, a))
    return sheet_images(images, FRAME)


def smoke() -> Image.Image:
    """Rauchwolke, 16 Bilder: mehrere beleuchtete Ballen (oben hell, unten Schatten), die quellen, aufreissen und
    verwehen."""
    size = FRAME
    rng = np.random.default_rng(415)
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64) / (size - 1) * 2 - 1
    blobs = [(rng.uniform(-0.4, 0.4), rng.uniform(-0.3, 0.3), rng.uniform(0.32, 0.5)) for _ in range(11)]
    wx = fbm(np.random.default_rng(416), size, 3, 4)
    wy = fbm(np.random.default_rng(417), size, 3, 4)
    images = []
    for f in range(16):
        t = f / 15
        grow = 0.95 + 0.35 * t
        height = np.zeros((size, size))
        for bx, by, br in blobs:
            rr = br * grow
            d2 = ((xs - bx * grow) ** 2 + (ys - by * grow) ** 2) / (rr * rr)
            height = np.maximum(height, np.sqrt(np.clip(1 - d2, 0, 1)))
        n = warp(fbm(np.random.default_rng(420 + f // 2), size, 5, 6), wx, wy, 0.05 + t * 0.15)
        height = np.clip(height * (0.7 + 0.5 * n) - t * 0.25 * (1 - n), 0, 1)
        a = np.clip(height ** 0.8 * 1.5, 0, 1) * (0.45 + 0.55 * n) * (1 - t) ** 1.2 * 0.9
        light = shade(height, strength=3.5)
        lum = np.clip(0.6 + light * 0.4 + (n - 0.5) * 0.15, 0, 1)
        images.append(rgb_image(np.repeat(lum[..., None], 3, axis=2), a))
    return sheet_images(images, FRAME)


def scorch() -> Image.Image:
    rng = np.random.default_rng(16)
    r, ang = radial(128)
    n = noise(rng, 128, 4, 5)
    blot = np.clip((0.75 + (n - 0.5) * 0.6 - r) / 0.2, 0, 1)
    cracks = (np.abs(np.sin(ang * 7 + n * 6)) < 0.08) & (r < 0.95)
    a = np.clip(blot * 0.8 + cracks * 0.6 * (1 - r), 0, 1)
    return rgba(np.full_like(a, 0.1) + n * 0.15, a, premultiply=False)


TEXTURES = {"glow": glow, "flare": flare, "ring": ring, "beam": beam, "spark": spark, "shard": shard, "flame": flame,
            "explosion": explosion, "smoke": smoke, "scorch": scorch, "flame_hot": flame_hot, "explosion_hot": explosion_hot}


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
