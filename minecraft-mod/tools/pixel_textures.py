#!/usr/bin/env python3
"""Vanilla-nahe 16x16-Pixeltexturen fuer die natuerlichen Bloecke der Welten (Planeten, Alien-Heimatwelten).

Stil wie Minecraft und grosse Dimensions-/Weltall-Mods (Ad Astra, The Aether, Blue Skies): 16x16 Pixel, je Textur nur
4–6 Farbtoene aus einer Palette (dunkel → hell), klare Pixel ohne Weichzeichnen, Licht von oben links (helle Kante
oben/links, dunkle unten/rechts), nahtlos kachelbar. Gleiche Funktionsnamen und Parameter wie ``planet_textures``
(die 128er-Generatoren), damit ``generate_planet_content`` die Welt-Bloecke nur umschalten muss.

Alle Funktionen sind deterministisch (Zufall aus dem Texturnamen) und liefern ``PIL.Image`` im Modus RGBA.
"""
from __future__ import annotations

import zlib
from typing import Sequence

import numpy as np
from PIL import Image

N = 16
Color = Sequence[float]


# --- Grundlagen -------------------------------------------------------------------------------------------------

def rng_for(name: str) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32(("px:" + name).encode("utf-8")))


def ramp(dark: Color, mid: Color, light: Color, steps: int = 5) -> list[np.ndarray]:
    """Palette von dunkel nach hell ueber die Mitte (dunkle Toene leicht kraeftiger, wie bei Vanilla)."""
    dark, mid, light = (np.asarray(c, dtype=np.float64) for c in (dark, mid, light))
    out = []
    for i in range(steps):
        t = i / (steps - 1)
        c = dark + (mid - dark) * (t * 2) if t <= 0.5 else mid + (light - mid) * ((t - 0.5) * 2)
        out.append(np.clip(c, 0, 1))
    return out


def tile_noise(rng: np.random.Generator, cells: int) -> np.ndarray:
    """Periodisches Wertrauschen 16x16 (cells teilt 16), Werte 0..1."""
    grid = rng.random((cells, cells))
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64) * cells / N
    x0 = np.floor(xs).astype(int)
    y0 = np.floor(ys).astype(int)
    fx = xs - x0
    fy = ys - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    x1 = (x0 + 1) % cells
    y1 = (y0 + 1) % cells
    a = grid[y0 % cells, x0 % cells]
    b = grid[y0 % cells, x1]
    c = grid[y1, x0 % cells]
    d = grid[y1, x1]
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy


def field(rng: np.random.Generator, grain: float = 0.45, blotch: float = 0.4, base: float = 0.5) -> np.ndarray:
    """Grundflaeche: grobe Flecken (4er-Raster) + feine Koernung je Pixel."""
    f = base + (tile_noise(rng, 4) - 0.5) * blotch * 2 + (tile_noise(rng, 8) - 0.5) * 0.25
    f += (rng.random((N, N)) - 0.5) * grain
    return np.clip(f, 0, 1)


def quantize(values: np.ndarray, steps: int) -> np.ndarray:
    return np.clip(np.floor(values * steps), 0, steps - 1).astype(int)


def render(indices: np.ndarray, palette: list[np.ndarray], alpha: np.ndarray | None = None) -> Image.Image:
    pal = np.asarray(palette)
    rgb = pal[np.clip(indices, 0, len(palette) - 1)]
    a = np.full((N, N), 255, dtype=np.uint8) if alpha is None else alpha.astype(np.uint8)
    data = np.dstack([np.round(rgb * 255).astype(np.uint8), a])
    return Image.fromarray(data, "RGBA")


def wrap(arr: np.ndarray, dy: int, dx: int) -> np.ndarray:
    return np.roll(np.roll(arr, dy, axis=0), dx, axis=1)


def voronoi_cells(rng: np.random.Generator, count: int) -> tuple[np.ndarray, np.ndarray]:
    """Kachelbares Voronoi: (Zellnummer, Randmaske) — Rand = Pixel, deren rechter/unterer Nachbar zu einer anderen
    Zelle gehoert."""
    pts = rng.random((count, 2)) * N
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64) + 0.5
    best = np.full((N, N), 1e9)
    idx = np.zeros((N, N), dtype=int)
    for i, (px, py) in enumerate(pts):
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        dx = np.minimum(dx, N - dx)
        dy = np.minimum(dy, N - dy)
        # leicht gestreckt: Steine eher breit als hoch
        d = (dx * 0.85) ** 2 + (dy * 1.1) ** 2
        better = d < best
        best = np.where(better, d, best)
        idx = np.where(better, i, idx)
    edge = (idx != wrap(idx, -1, 0)) | (idx != wrap(idx, 0, -1))
    return idx, edge


# --- Gestein und Boden --------------------------------------------------------------------------------------------

def sand(name: str, dark: Color, mid: Color, light: Color, ripples: float = 1.0) -> Image.Image:
    """Sand: feine Koernung, schraege Rippel-Streifen (heller Kamm, dunkle Senke), einzelne helle Koerner."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    f = field(rng, grain=0.3, blotch=0.15, base=0.55)
    if ripples > 0:
        ys, xs = np.mgrid[0:N, 0:N]
        phase = (xs + ys * 2 + np.round(tile_noise(rng, 2) * 3)) % 6
        f += np.where(phase == 0, 0.22, np.where(phase == 1, -0.16, 0.0)) * ripples
    idx = np.clip(quantize(f, 5), 1, 3)
    sparkle = rng.random((N, N)) < 0.025
    idx[sparkle] = 4
    return render(idx, pal)


def rock(name: str, dark: Color, mid: Color, light: Color, cells: int = 14, crack: float = 1.0,
         detail: float = 1.0) -> Image.Image:
    """Bruchstein (wie Cobblestone/Holystone): Steine mit dunklen Fugen, helle Kante oben links, Schatten unten rechts."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 6)
    # gleiche Parameter wie die 128er-Variante: dort Zellen auf 128 px, hier 5–9 Steine auf 16 px
    count = max(5, min(9, cells // 2))
    idx_map, edge = voronoi_cells(rng, count)
    tone = rng.random(count) * 0.3 + 0.45
    f = tone[idx_map] + (rng.random((N, N)) - 0.5) * 0.18 * detail
    out = quantize(np.clip(f, 0, 1), 6)
    out = np.clip(out, 2, 4)
    # Kanten: Pixel unter/links einer Fuge hell, ueber/rechts dunkel
    top_lit = wrap(edge, 1, 0) | wrap(edge, 0, 1)
    out[top_lit & ~edge] = 5
    shade = wrap(edge, -1, 0) & ~edge
    out[shade] = 1
    out[edge] = 0 if crack >= 1.2 else 1
    out[edge & (rng.random((N, N)) < 0.25 * crack)] = 0
    return render(out, pal)


def stone(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    """Glatter Stein (wie Vanilla-Stein): waagrecht gezogene helle und dunkle Flecken."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    f = field(rng, grain=0.35, blotch=0.3, base=0.55)
    streak = tile_noise(rng, 2)
    f += (np.roll(f, 1, axis=1) - f) * 0.4 + (streak - 0.5) * 0.1
    idx = np.clip(quantize(np.clip(f, 0, 1), 5), 1, 4)
    return render(idx, pal)


def porous(name: str, dark: Color, mid: Color, light: Color, holes: int = 26, hole_size: float = 5.0) -> Image.Image:
    """Poroeser Stein (Tuff, Korallenfels): Steinflaeche mit Loechern (dunkel, darunter heller Rand)."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    idx = np.clip(quantize(field(rng, grain=0.3, blotch=0.25, base=0.55), 5), 1, 3)
    count = max(4, holes // 4)
    for _ in range(count):
        x, y = rng.integers(0, N, 2)
        big = hole_size > 5 and rng.random() < 0.5
        pts = [(0, 0), (1, 0)] + ([(0, 1), (1, 1)] if big else [])
        for dx, dy in pts:
            idx[(y + dy) % N, (x + dx) % N] = 0
        idx[(y + (2 if big else 1)) % N, x % N] = 4
        idx[(y - 1) % N, x % N] = max(1, idx[(y - 1) % N, x % N] - 1)
    return render(idx, pal)


def slate(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    """Schiefer: waagrechte Platten (3–4 px) mit dunkler Fuge und heller Oberkante."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    out = np.zeros((N, N), dtype=int)
    y = 0
    rows = []
    while y < N:
        h = int(rng.integers(3, 5))
        rows.append((y, min(N, y + h)))
        y += h
    for y0, y1 in rows:
        tone = int(rng.integers(2, 4))
        out[y0:y1, :] = tone
        out[y0, :] = min(4, tone + 1)
        out[y1 - 1, :] = 0
        # versetzte senkrechte Fuge je Platte
        x = int(rng.integers(0, N))
        out[y0:y1, x] = 1
    grain = rng.random((N, N))
    out[(grain < 0.12) & (out >= 2)] -= 1
    out[(grain > 0.93) & (out >= 1) & (out <= 3)] += 1
    return render(out, pal)


def strata(name: str, bands: Sequence[Color], erosion: float = 1.0) -> Image.Image:
    """Schichtgestein: waagrechte Farbbaender (2–3 px), Kanten um ±1 px ausgefranst."""
    rng = rng_for(name)
    palette = [np.asarray(c, dtype=np.float64) for c in bands]
    out = np.zeros((N, N), dtype=int)
    y = 0
    order = []
    while y < N:
        h = int(rng.integers(2, 4))
        order.append((y, h, int(rng.integers(0, len(palette)))))
        y += h
    for y0, h, c in order:
        out[y0:min(N, y0 + h), :] = c
    # Baender wellen sich ueber die Breite (sonst wirkt die Kachel wie Bretter) und fransen pixelweise aus
    ys, xs = np.mgrid[0:N, 0:N]
    shift = np.round((tile_noise(rng, 2) - 0.5) * 3).astype(int)
    out = out[(ys + shift) % N, xs]
    jitter = rng.random((N, N)) < 0.22 * erosion
    out = np.where(jitter, wrap(out, 1, 0), out)
    return render(out, palette)


def basalt(name: str, dark: Color, mid: Color, glow: Color) -> Image.Image:
    """Saeulenbasalt: senkrechte Saeulen (4 px) mit dunklen Fugen und vereinzelten Glutpixeln."""
    rng = rng_for(name)
    pal = ramp(dark, mid, np.clip(np.asarray(mid) * 1.5, 0, 1), 4) + [np.asarray(glow, dtype=np.float64)]
    out = np.zeros((N, N), dtype=int)
    for col in range(0, N, 4):
        tone = int(rng.integers(1, 3))
        out[:, col:col + 4] = tone
        out[:, col] = 3
        out[:, col + 3] = 0
        cut = int(rng.integers(0, N))
        out[cut, col:col + 4] = 0
    noise = rng.random((N, N))
    out[(noise < 0.12) & (out == 1)] = 2
    out[(noise > 0.96) & (out == 0)] = 4
    return render(out, pal)


def soil(name: str, dark: Color, mid: Color, light: Color, pebble: Color) -> Image.Image:
    """Erde: koernige Flaeche in vier Toenen, einzelne Kiesel (hell mit dunklem Schatten)."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 4) + [np.asarray(pebble, dtype=np.float64)]
    idx = quantize(field(rng, grain=0.4, blotch=0.22, base=0.5), 4)
    for _ in range(5):
        x, y = rng.integers(0, N, 2)
        idx[y, x] = 4
        idx[(y + 1) % N, x] = 0
    return render(idx, pal)


def grass_top(name: str, dark: Color, mid: Color, light: Color, flowers: Sequence[Color] = ()) -> Image.Image:
    """Rasen/Moos von oben: dichte Halmstruktur (helle Halmspitzen), einzelne Bluetenpixel."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    idx = np.clip(quantize(field(rng, grain=0.45, blotch=0.2, base=0.5), 5), 1, 3)
    tips = rng.random((N, N)) < 0.1
    idx[tips] = 4
    idx[wrap(tips, 1, 0) & ~tips] = np.maximum(idx[wrap(tips, 1, 0) & ~tips] - 1, 0)
    flower_list = [np.asarray(c, dtype=np.float64) for c in flowers]
    pal = pal + flower_list
    for i in range(len(flower_list)):
        for _ in range(2):
            x, y = rng.integers(0, N, 2)
            idx[y, x] = 5 + i
    return render(idx, pal)


def grass_side(name: str, top: Image.Image, soil_img: Image.Image, depth: float = 26.0) -> Image.Image:
    """Seite eines Rasenblocks: Erde, oben 2–4 px Rasen mit unregelmaessig herabhaengenden Spitzen."""
    rng = rng_for(name)
    top_px = np.asarray(top.convert("RGBA").resize((N, N), Image.NEAREST)).copy()
    soil_px = np.asarray(soil_img.convert("RGBA").resize((N, N), Image.NEAREST)).copy()
    out = soil_px.copy()
    for x in range(N):
        hang = 2 + int(rng.integers(0, 3)) if rng.random() < 0.6 else 2
        out[0:hang, x] = top_px[0:hang, x]
        # dunkle Kante unter dem Rasen
        if hang < N:
            out[hang, x, :3] = (out[hang, x, :3] * 0.78).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


# --- Holz und Pflanzen --------------------------------------------------------------------------------------------

def bark(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    """Rinde: senkrechte Streifen, dunkle Risse, helle Grate."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    col_tone = rng.integers(1, 4, N)
    out = np.tile(col_tone, (N, 1))
    noise = rng.random((N, N))
    out = np.where(noise < 0.15, out - 1, np.where(noise > 0.88, out + 1, out))
    for _ in range(4):
        x = int(rng.integers(0, N))
        y = int(rng.integers(0, N))
        length = int(rng.integers(3, 7))
        for k in range(length):
            out[(y + k) % N, x] = 0
    return render(np.clip(out, 0, 4), pal)


def log_top(name: str, bark_dark: Color, wood_dark: Color, wood_light: Color) -> Image.Image:
    """Stammquerschnitt: Rindenrand, Jahresringe als eckige Ringe (wie Vanilla)."""
    pal = [np.asarray(bark_dark, dtype=np.float64)] + ramp(np.asarray(wood_dark) * 0.85, wood_dark, wood_light, 4)
    ys, xs = np.mgrid[0:N, 0:N]
    ring = np.maximum(np.abs(xs - 7.5), np.abs(ys - 7.5))
    out = np.where(ring > 6.6, 0, np.where(np.floor(ring) % 2 == 0, 2, 3))
    out = np.where(ring < 1.6, 4, out)
    out = np.where((ring > 5.6) & (ring <= 6.6), 1, out)
    return render(out, pal)


def planks(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    """Bretter: vier waagrechte Bretter (4 px), dunkle Fugen, versetzte Stoesse, Maserung."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    out = np.zeros((N, N), dtype=int)
    for b in range(4):
        y0 = b * 4
        out[y0:y0 + 4, :] = 2
        out[y0, :] = 3
        out[y0 + 3, :] = 0
        x = (b * 7 + int(rng.integers(0, 4))) % N
        out[y0:y0 + 3, x] = 1
        grain = rng.random((3, N)) < 0.2
        seg = out[y0:y0 + 3, :]
        seg[grain[:3] & (seg == 2)] = 1
        seg[(rng.random((3, N)) < 0.08) & (seg == 2)] = 4
    return render(out, pal)


def leaves(name: str, dark: Color, mid: Color, light: Color, accent: Color | None = None, density: int = 260) -> Image.Image:
    """Laub: gefleckte Blaetter in vier Toenen, ~18 % Luecken (durchsichtig), optional Bluetenpixel."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 4) + ([np.asarray(accent, dtype=np.float64)] if accent is not None else [])
    idx = quantize(field(rng, grain=0.7, blotch=0.3, base=0.5), 4)
    alpha = np.where(rng.random((N, N)) < 0.18, 0, 255)
    if accent is not None:
        spots = (rng.random((N, N)) < 0.05) & (alpha > 0)
        idx[spots] = 4
    return render(idx, pal, alpha)


def plant(name: str, stem: Color, leaf: Color, blossom: Color | None, style: str = "tuft") -> Image.Image:
    """Kreuzpflanze in Pixeln: Grasbueschel, Schilf mit Kolben, Dornenstrauch oder Bluete."""
    rng = rng_for(name)
    stem_c = np.asarray(stem, dtype=np.float64)
    leaf_c = np.asarray(leaf, dtype=np.float64)
    pal = [np.clip(stem_c * 0.7, 0, 1), stem_c, leaf_c, np.clip(leaf_c * 1.25 + 0.05, 0, 1)]
    if blossom is not None:
        bc = np.asarray(blossom, dtype=np.float64)
        pal += [bc, np.clip(bc * 0.5 + 0.5, 0, 1)]
    out = np.zeros((N, N), dtype=int)
    alpha = np.zeros((N, N))

    def put(x: int, y: int, c: int) -> None:
        if 0 <= x < N and 0 <= y < N:
            out[y, x] = c
            alpha[y, x] = 255

    if style == "tuft":
        for _ in range(9):
            x = int(rng.integers(1, N - 1))
            h = int(rng.integers(5, 12))
            lean = rng.choice([-1, 0, 0, 1])
            for k in range(h):
                xx = x + (lean if k > h * 0.6 else 0)
                put(xx, N - 1 - k, 3 if k == h - 1 else (2 if k > 1 else 1))
    elif style == "reed":
        for _ in range(5):
            x = int(rng.integers(2, N - 2))
            h = int(rng.integers(9, 15))
            for k in range(h):
                put(x, N - 1 - k, 1 if k < h - 3 else 2)
            if blossom is not None:
                put(x, N - h, 5)
                put(x, N - h + 1, 4)
                put(x, N - h + 2, 4)
    elif style == "thorn":
        for _ in range(3):
            x = int(rng.integers(3, N - 3))
            y = N - 1
            h = int(rng.integers(7, 12))
            for k in range(h):
                put(x, y - k, 1)
                if k > 2 and k % 3 == 0:
                    d = rng.choice([-1, 1])
                    put(x + d, y - k - 1, 0)
                    put(x + 2 * d, y - k - 2, 2)
            if blossom is not None:
                put(x, y - h, 4)
                put(x + 1, y - h, 5)
    else:  # bloom
        for _ in range(3):
            x = int(rng.integers(3, N - 3))
            h = int(rng.integers(6, 11))
            for k in range(h):
                put(x, N - 1 - k, 1)
            put(x - 1, N - 4, 2)
            put(x + 1, N - 6, 2)
            if blossom is not None:
                top = N - 1 - h
                for dx, dy in [(0, -1), (-1, 0), (1, 0), (0, 1)]:
                    put(x + dx, top + dy, 4)
                put(x, top, 5)
    return render(out, pal, alpha)


# --- Kristall, Technik-Gestein, Erz --------------------------------------------------------------------------------

def crystal(name: str, dark: Color, mid: Color, light: Color, frost: float = 0.0) -> Image.Image:
    """Kristall/Eis (wie Amethyst): Facetten in drei Toenen, helle Bruchkanten, Glanzpixel; Frost = helle Flecken."""
    rng = rng_for(name)
    pal = ramp(dark, mid, light, 5)
    pal.append(np.clip(np.asarray(light) * 0.5 + 0.5, 0, 1))
    idx_map, edge = voronoi_cells(rng, 6)
    facet = rng.integers(1, 4, 6)
    out = facet[idx_map].copy()
    # Lichtkante: innerhalb jeder Facette heller zur oberen linken Fuge
    lit = (wrap(edge, 1, 0) | wrap(edge, 0, 1)) & ~edge
    out[lit] = 4
    out[edge] = 0
    sparkle = (rng.random((N, N)) < 0.03) & ~edge
    out[sparkle] = 5
    if frost > 0:
        fr = (tile_noise(rng, 4) > 1 - frost * 0.5) & ~edge
        out[fr] = np.maximum(out[fr], 4)
    return render(out, pal)


def hex_tiles(name: str, dark: Color, mid: Color, seam_glow: Color, glow: float = 0.5) -> Image.Image:
    """Technik-Platten in Pixeln: versetzte Kacheln (8x4), dunkle Fugen, leuchtende Leiterbahn-Pixel."""
    rng = rng_for(name)
    pal = ramp(dark, mid, np.clip(np.asarray(mid) * 1.6 + 0.05, 0, 1), 4) + [np.asarray(seam_glow, dtype=np.float64)]
    out = np.full((N, N), 2, dtype=int)
    for row in range(4):
        y0 = row * 4
        out[y0, :] = 0
        out[y0 + 1, :] = 3
        offset = 0 if row % 2 == 0 else 4
        for x in range(offset, N + offset, 8):
            out[y0:y0 + 4, x % N] = 0
    noise = rng.random((N, N))
    out[(noise < 0.1) & (out == 2)] = 1
    # Leiterbahnen: kurze leuchtende Linien in den Fugen
    lines = int(4 + glow * 6)
    for _ in range(lines):
        y = int(rng.integers(0, 4)) * 4
        x = int(rng.integers(0, N))
        for k in range(int(rng.integers(2, 5))):
            out[y, (x + k) % N] = 4
    return render(out, pal)


def ore(name: str, stone_img: Image.Image, gem_dark: Color, gem: Color, gem_light: Color, clusters: int = 6,
        style: str = "crystal", glow: float = 0.0) -> Image.Image:
    """Erz: Gestein der Welt mit Edelstein-/Nugget-Flecken (dunkle Kante, heller Rand, Glanzpixel)."""
    rng = rng_for(name)
    base = np.asarray(stone_img.convert("RGBA").resize((N, N), Image.NEAREST)).copy()
    gd, gm, gl = (np.round(np.asarray(c) * 255).astype(np.uint8) for c in (gem_dark, gem, gem_light))
    white = np.round(np.clip(np.asarray(gem_light) * 0.4 + 0.6, 0, 1) * 255).astype(np.uint8)
    sprites = [[(0, 0), (1, 0), (0, 1), (1, 1)], [(0, 0), (1, 0), (2, 0), (1, 1)], [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)],
               [(0, 0), (1, 1)], [(0, 0), (1, 0), (1, 1), (2, 1)]]
    taken = np.zeros((N, N), dtype=bool)
    for _ in range(clusters):
        for _attempt in range(12):
            sprite = sprites[int(rng.integers(0, len(sprites)))]
            x, y = (int(v) for v in rng.integers(0, N - 3, 2))
            cells_xy = [(x + dx, y + dy) for dx, dy in sprite]
            if any(taken[cy, cx] for cx, cy in cells_xy):
                continue
            for i, (cx, cy) in enumerate(cells_xy):
                taken[cy, cx] = True
                colour = gl if i == 0 else (gd if (cx, cy) == cells_xy[-1] else gm)
                base[cy, cx, :3] = colour
            # Glanzpixel und Schatten im Stein
            base[cells_xy[0][1], cells_xy[0][0], :3] = white
            sx, sy = cells_xy[-1][0] + 1, cells_xy[-1][1] + 1
            if sx < N and sy < N and not taken[sy, sx]:
                base[sy, sx, :3] = (base[sy, sx, :3] * (0.85 if glow > 0 else 0.6)).astype(np.uint8)
            break
    return Image.fromarray(base, "RGBA")
