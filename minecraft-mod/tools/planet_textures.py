"""Prozedurale Texturen fuer die Planeten-Bloecke und -Items (128x128 Bloecke, 64x64 Items).

Alle Blocktexturen sind nahtlos kachelbar (periodisches Rauschen, periodisches Voronoi, Ableitungen mit np.roll).
Jede Textur entsteht aus einer Hoehenkarte und einer Farbkarte; die Hoehenkarte liefert Normalen, daraus Licht von
oben links, Kanten-Glanz und Hohlraum-Schatten — so wirken Fugen, Kristalle und Platten plastisch.

Deterministisch: Der Zufall haengt nur vom Texturnamen ab (CRC32), gleiche Eingabe -> gleiche Pixel.
"""
from __future__ import annotations

import math
import zlib
from typing import Callable, Sequence

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

N = 128          # Blocktexturen
ITEM = 64        # Item-Symbole
LIGHT = np.array([-0.55, -0.62, 0.56])   # Licht von oben links (Bild-x nach rechts, Bild-y nach unten, z zum Betrachter)
LIGHT = LIGHT / np.linalg.norm(LIGHT)

Color = Sequence[float]


def rng_for(name: str) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32(name.encode("utf-8")))


def hexc(value: str) -> np.ndarray:
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255.0


# --- Rauschen (periodisch) -------------------------------------------------------------------------------------

def _smooth(t: np.ndarray) -> np.ndarray:
    return t * t * t * (t * (t * 6 - 15) + 10)


def value_noise(rng: np.random.Generator, cells_x: int, cells_y: int | None = None, size: int = N) -> np.ndarray:
    """Periodisches Wertrauschen 0..1 mit cells_x x cells_y Gitterzellen ueber size Pixel."""
    cells_y = cells_y or cells_x
    lattice = rng.random((cells_y, cells_x))
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    fx = xs * cells_x / size
    fy = ys * cells_y / size
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    tx = _smooth(fx - x0)
    ty = _smooth(fy - y0)
    x0 %= cells_x
    y0 %= cells_y
    x1 = (x0 + 1) % cells_x
    y1 = (y0 + 1) % cells_y
    a = lattice[y0, x0] * (1 - tx) + lattice[y0, x1] * tx
    b = lattice[y1, x0] * (1 - tx) + lattice[y1, x1] * tx
    return a * (1 - ty) + b * ty


def fbm(rng: np.random.Generator, cells: int, octaves: int = 4, persistence: float = 0.5, size: int = N,
        stretch: tuple[float, float] = (1.0, 1.0)) -> np.ndarray:
    """Fraktales Rauschen 0..1; stretch staucht x bzw. y (fuer Maserung, Rinde)."""
    total = np.zeros((size, size))
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        cx = max(1, int(round(cells * (2 ** o) * stretch[0])))
        cy = max(1, int(round(cells * (2 ** o) * stretch[1])))
        total += amp * value_noise(rng, cx, cy, size)
        norm += amp
        amp *= persistence
    total /= norm
    lo, hi = total.min(), total.max()
    return (total - lo) / max(hi - lo, 1e-9)


def ridged(rng: np.random.Generator, cells: int, octaves: int = 4, size: int = N) -> np.ndarray:
    n = fbm(rng, cells, octaves, size=size)
    return 1.0 - np.abs(n * 2 - 1)


def voronoi(rng: np.random.Generator, count: int, size: int = N, points: np.ndarray | None = None,
            aspect: tuple[float, float] = (1.0, 1.0)):
    """Periodisches Voronoi: (F1, F2, Zellindex, Punkte). Abstaende in Pixeln."""
    pts = points if points is not None else rng.random((count, 2)) * size
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    f1 = np.full((size, size), np.inf)
    f2 = np.full((size, size), np.inf)
    idx = np.zeros((size, size), dtype=int)
    for i, (px, py) in enumerate(pts):
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        dx = np.minimum(dx, size - dx) * aspect[0]
        dy = np.minimum(dy, size - dy) * aspect[1]
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < f1
        f2 = np.where(closer, f1, np.minimum(f2, d))
        idx = np.where(closer, i, idx)
        f1 = np.where(closer, d, f1)
    return f1, f2, idx, pts


def warp(field: np.ndarray, dx: np.ndarray, dy: np.ndarray) -> np.ndarray:
    """Feld periodisch um (dx, dy) Pixel verschieben (bilinear)."""
    size = field.shape[0]
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    sx = (xs + dx) % size
    sy = (ys + dy) % size
    x0 = np.floor(sx).astype(int)
    y0 = np.floor(sy).astype(int)
    tx = sx - x0
    ty = sy - y0
    x1 = (x0 + 1) % size
    y1 = (y0 + 1) % size
    return (field[y0, x0] * (1 - tx) * (1 - ty) + field[y0, x1] * tx * (1 - ty) + field[y1, x0] * (1 - tx) * ty
            + field[y1, x1] * tx * ty)


# --- Licht und Farbe ----------------------------------------------------------------------------------------------

def normals(height: np.ndarray, strength: float) -> np.ndarray:
    gx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) * 0.5 * strength
    gy = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) * 0.5 * strength
    n = np.dstack([-gx, -gy, np.ones_like(height)])
    return n / np.linalg.norm(n, axis=-1, keepdims=True)


def lighting(height: np.ndarray, strength: float = 6.0, ambient: float = 0.55,
             spec: float = 0.0, shininess: float = 24.0) -> tuple[np.ndarray, np.ndarray]:
    """(Lichtfaktor, Glanz) aus einer Hoehenkarte. Licht 1.0 = flache Flaeche."""
    n = normals(height, strength)
    flat = float(np.dot(np.array([0, 0, 1.0]), LIGHT))
    lam = np.clip(n @ LIGHT, 0, 1)
    light = ambient + (1 - ambient) * lam / flat
    half = LIGHT + np.array([0, 0, 1.0])
    half /= np.linalg.norm(half)
    gloss = spec * np.clip(n @ half, 0, 1) ** shininess
    return light, gloss


def cavity(height: np.ndarray, radius: int = 3) -> np.ndarray:
    """Hohlraum-Schatten: Pixel unter dem lokalen Mittel werden dunkler (0..1, 1 = kein Schatten)."""
    blur = height.copy()
    for _ in range(radius):
        blur = (blur + np.roll(blur, 1, 0) + np.roll(blur, -1, 0) + np.roll(blur, 1, 1) + np.roll(blur, -1, 1)) / 5
    return np.clip(1.0 - np.clip(blur - height, 0, None) * 3.0, 0.35, 1.0)


def gradient_map(t: np.ndarray, stops: Sequence[tuple[float, Color]]) -> np.ndarray:
    """Farbverlauf: t (0..1) -> RGB ueber Stuetzstellen [(pos, rgb), ...]."""
    t = np.clip(t, 0, 1)
    pos = np.array([p for p, _ in stops])
    cols = np.array([np.asarray(c, dtype=np.float64) for _, c in stops])
    out = np.zeros(t.shape + (3,))
    for ch in range(3):
        out[..., ch] = np.interp(t, pos, cols[:, ch])
    return out


def finish(rgb: np.ndarray, alpha: np.ndarray | None = None) -> Image.Image:
    rgb = np.clip(rgb, 0, 1)
    rgb = rgb ** (1 / 1.0)
    data = (rgb * 255 + 0.5).astype(np.uint8)
    if alpha is None:
        return Image.fromarray(data, "RGB").convert("RGBA")
    a = (np.clip(alpha, 0, 1) * 255 + 0.5).astype(np.uint8)
    return Image.fromarray(np.dstack([data, a]), "RGBA")


def compose(color: np.ndarray, height: np.ndarray, strength: float = 6.0, spec: float = 0.0, shininess: float = 20.0,
            ao: float = 1.0, ambient: float = 0.55) -> np.ndarray:
    light, gloss = lighting(height, strength, ambient=ambient, spec=spec, shininess=shininess)
    cav = cavity(height) ** ao
    return color * light[..., None] * cav[..., None] + gloss[..., None]


def speckle(rng: np.random.Generator, density: float, size: int = N) -> np.ndarray:
    return (rng.random((size, size)) < density).astype(np.float64)


# --- Natur ------------------------------------------------------------------------------------------------------

def sand(name: str, dark: Color, mid: Color, light: Color, ripples: float = 1.0) -> Image.Image:
    rng = rng_for(name)
    base = fbm(rng, 4, 5)
    grain = rng.random((N, N))
    w = fbm(rng, 3, 3) * 18
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    rip = (np.sin((ys + w + xs * 0.12) * 2 * math.pi * 6 / N) * 0.5 + 0.5) ** 1.6 * ripples
    height = base * 0.6 + rip * 0.45 + grain * 0.12
    t = base * 0.55 + rip * 0.25 + grain * 0.35
    color = gradient_map(t, [(0.0, dark), (0.5, mid), (1.0, light)])
    pebbles = speckle(rng, 0.004)
    color = color * (1 - 0.35 * pebbles[..., None])
    return finish(compose(color, height, strength=4.0, ambient=0.62))


def strata(name: str, bands: Sequence[Color], erosion: float = 1.0) -> Image.Image:
    """Geschichtetes Gestein (Tafelberge): horizontale Baender, verwittert, kachelbar."""
    rng = rng_for(name)
    thickness = rng.integers(6, 18, size=64)
    edges = np.cumsum(thickness)
    edges = edges[edges < N]
    edges = np.concatenate([[0], edges, [N]])
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    wobble = (fbm(rng, 3, 3) - 0.5) * 6
    yy = (ys + wobble) % N
    band = np.searchsorted(edges, yy, side="right") - 1
    band_color = np.array([np.asarray(bands[i % len(bands)]) for i in range(len(edges))])
    color = band_color[band]
    jitter = fbm(rng, 8, 4)
    color = color * (0.82 + 0.3 * jitter[..., None])
    # Kanten zwischen Schichten als Rillen, Erosionsloecher
    frac = (yy - edges[band]) / np.maximum(edges[band + 1] - edges[band], 1)
    groove = np.exp(-((frac - 0.0) ** 2) / 0.004) + np.exp(-((frac - 1.0) ** 2) / 0.004)
    pits = np.clip(fbm(rng, 10, 3) - 0.68, 0, 1) * 3 * erosion
    height = jitter * 0.5 - groove * 0.6 - pits
    color = color * (1 - 0.25 * groove[..., None])
    return finish(compose(color, height, strength=7.0))


def rock(name: str, dark: Color, mid: Color, light: Color, cells: int = 14, crack: float = 1.0, detail: float = 1.0,
         veins: Color | None = None) -> Image.Image:
    rng = rng_for(name)
    f1, f2, idx, _ = voronoi(rng, cells)
    edge = np.clip((f2 - f1) / 3.0, 0, 1)
    tone = rng.random(cells)[idx]
    detail_n = fbm(rng, 6, 5)
    height = edge ** 0.6 * 0.8 + detail_n * 0.5 * detail + tone * 0.15
    t = 0.25 + tone * 0.3 + detail_n * 0.45
    color = gradient_map(t, [(0.0, dark), (0.55, mid), (1.0, light)])
    color = color * (1 - 0.55 * crack * (1 - edge[..., None]) ** 3)
    if veins is not None:
        v = ridged(rng, 3, 4)
        mask = np.clip((v - 0.9) * 12, 0, 1)
        color = color * (1 - mask[..., None]) + np.asarray(veins) * mask[..., None]
    return finish(compose(color, height, strength=7.0))


def basalt(name: str, dark: Color, mid: Color, glow: Color) -> Image.Image:
    """Saeulenbasalt von oben/seitlich: sechseckige Zellen, gluehende Adern dazwischen."""
    rng = rng_for(name)
    pts = []
    rows = 6
    for r in range(rows):
        for c in range(6):
            x = (c + 0.5 * (r % 2)) * N / 6 + rng.normal(0, 2)
            y = r * N / rows + rng.normal(0, 2)
            pts.append((x % N, y % N))
    f1, f2, idx, _ = voronoi(rng, 0, points=np.array(pts))
    edge = np.clip((f2 - f1) / 4.0, 0, 1)
    tone = rng.random(len(pts))[idx]
    d = fbm(rng, 8, 4)
    height = edge ** 0.5 + d * 0.3
    color = gradient_map(0.2 + tone * 0.4 + d * 0.4, [(0, dark), (1, mid)])
    seam = (1 - edge) ** 4
    hot = np.clip(fbm(rng, 3, 3) * 1.6 - 0.6, 0, 1)
    color = color * (1 - seam[..., None] * 0.7) + np.asarray(glow) * (seam * hot)[..., None] * 1.2
    return finish(compose(color, height, strength=6.0, spec=0.25, shininess=30))


def porous(name: str, dark: Color, mid: Color, light: Color, holes: int = 26, hole_size: float = 5.0) -> Image.Image:
    rng = rng_for(name)
    base = fbm(rng, 5, 5)
    f1, _, idx, _ = voronoi(rng, holes)
    r = (hole_size * (0.6 + rng.random(holes)))[idx]
    hole = np.clip(1 - f1 / r, 0, 1)
    height = base * 0.6 - hole ** 0.7 * 1.2
    color = gradient_map(base, [(0, dark), (0.5, mid), (1, light)])
    color = color * (1 - 0.6 * hole[..., None] ** 0.8)
    return finish(compose(color, height, strength=8.0))


def slate(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    rng = rng_for(name)
    rows = 8
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    row = (ys // (N / rows)).astype(int)
    offset = rng.random(rows) * N
    seg_len = rng.integers(24, 64, size=rows)
    seg = ((xs + offset[row]) // seg_len[row]).astype(int)
    seg_id = (row * 37 + seg * 11) % 97
    tone = rng.random(97)[seg_id]
    fy = (ys % (N / rows)) / (N / rows)
    fx = ((xs + offset[row]) % seg_len[row]) / seg_len[row]
    plate = np.minimum(np.minimum(fy, 1 - fy) * 6, 1) * np.minimum(np.minimum(fx, 1 - fx) * 10, 1)
    grain = fbm(rng, 6, 4, stretch=(2.5, 0.6))
    height = plate * 0.7 + grain * 0.35 + tone * 0.2
    color = gradient_map(0.2 + tone * 0.35 + grain * 0.45, [(0, dark), (0.5, mid), (1, light)])
    return finish(compose(color, height, strength=6.0, spec=0.12, shininess=18))


def soil(name: str, dark: Color, mid: Color, light: Color, pebble: Color) -> Image.Image:
    """Lockere Erde: Kruemel, feines Korn, eingestreute Kiesel und Wurzelfasern."""
    rng = rng_for(name)
    crumbs = fbm(rng, 16, 4, persistence=0.55)
    clods = fbm(rng, 5, 3)
    grain = rng.random((N, N))
    height = crumbs * 0.6 + clods * 0.4 + grain * 0.15
    color = gradient_map(0.1 + crumbs * 0.45 + clods * 0.3 + grain * 0.2, [(0, dark), (0.55, mid), (1, light)])
    pf1, _, pidx, _ = voronoi(rng_for(name + "p"), 22)
    pr = 1.5 + rng_for(name + "r").random(22)[pidx] * 2.8
    peb = np.clip(1 - pf1 / pr, 0, 1)
    color = color * (1 - peb[..., None] ** 0.5) + np.asarray(pebble) * peb[..., None] ** 0.5 * (0.75 + 0.5 * crumbs[..., None])
    height = height + peb * 0.9
    roots = np.clip(ridged(rng_for(name + "w"), 3, 3) - 0.95, 0, 1) * 14
    color = color * (1 - 0.35 * roots[..., None])
    return finish(compose(color, height, strength=6.0))


def grass_top(name: str, dark: Color, mid: Color, light: Color, flowers: Sequence[Color] = ()) -> Image.Image:
    rng = rng_for(name)
    blades = fbm(rng, 24, 3, persistence=0.6, stretch=(1.0, 1.0))
    clumps = fbm(rng, 4, 4)
    fine = rng.random((N, N))
    height = blades * 0.6 + fine * 0.3 + clumps * 0.3
    t = blades * 0.5 + clumps * 0.35 + fine * 0.25
    color = gradient_map(t, [(0, dark), (0.5, mid), (1, light)])
    if flowers:
        f1, _, idx, _ = voronoi(rng_for(name + "f"), 10)
        bloom = np.clip(1 - f1 / 2.4, 0, 1)
        pick = rng_for(name + "c").integers(0, len(flowers), 10)[idx]
        fc = np.array([np.asarray(c) for c in flowers])[pick]
        color = color * (1 - bloom[..., None]) + fc * bloom[..., None]
        height = height + bloom
    return finish(compose(color, height, strength=5.0, ambient=0.6))


def grass_side(name: str, top: Image.Image, soil_img: Image.Image, depth: float = 26.0) -> Image.Image:
    """Erdseite mit ueberhaengender Grasschicht (ausgefranster Rand, nur seitlich kachelbar)."""
    rng = rng_for(name)
    top_arr = np.asarray(top.convert("RGB"), dtype=np.float64) / 255
    soil_arr = np.asarray(soil_img.convert("RGB"), dtype=np.float64) / 255
    xs = np.arange(N)
    edge = depth * 0.55 + value_noise(rng, 16, 1)[0] * depth * 0.45 + value_noise(rng, 5, 1)[0] * depth * 0.35
    drips = (rng.random(N) < 0.08) * rng.random(N) * depth * 0.6
    edge = edge + np.maximum(drips, np.roll(drips, 1) * 0.6)
    ys = np.arange(N)[:, None]
    mask = (ys < edge[None, :]).astype(np.float64)
    shadow = np.clip(1 - (ys - edge[None, :]) / 6, 0, 1) * (1 - mask)
    out = soil_arr * (1 - mask[..., None]) + top_arr * mask[..., None]
    out = out * (1 - 0.45 * shadow[..., None])
    rim = np.clip(1 - np.abs(ys - edge[None, :] + 1) / 1.5, 0, 1)
    out = out * (1 - 0.3 * rim[..., None])
    return finish(out)


def bark(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    """Rinde: senkrechte Furchen (gestreckter Grat), Querrisse, Flechtenflecken."""
    rng = rng_for(name)
    ridges = fbm(rng, 9, 4, stretch=(1.0, 0.12))
    furrow = 1 - np.abs(fbm(rng_for(name + "f"), 7, 3, stretch=(1.0, 0.1)) * 2 - 1)
    furrow = np.clip((furrow - 0.75) * 4, 0, 1)
    cross = np.clip(fbm(rng_for(name + "x"), 2, 3, stretch=(3.0, 1.0)) - 0.7, 0, 1) * 3
    height = ridges * 0.8 - furrow * 0.9 - cross * 0.2
    color = gradient_map(0.15 + ridges * 0.75, [(0, dark), (0.5, mid), (1, light)])
    color = color * (1 - 0.55 * furrow[..., None]) * (1 - 0.2 * cross[..., None])
    return finish(compose(color, height, strength=10.0))


def log_top(name: str, bark_dark: Color, wood_dark: Color, wood_light: Color) -> Image.Image:
    rng = rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    cx = cy = N / 2 - 0.5
    r = np.hypot(xs - cx, ys - cy) + (fbm(rng, 4, 3) - 0.5) * 6
    rings = np.sin(r * 2 * math.pi / 7.5) * 0.5 + 0.5
    t = 0.3 + rings * 0.5 + fbm(rng, 10, 3) * 0.2
    color = gradient_map(t, [(0, wood_dark), (1, wood_light)])
    outer = r > N / 2 - 9
    noise = fbm(rng_for(name + "b"), 12, 3)
    color = np.where(outer[..., None], gradient_map(noise, [(0, np.asarray(bark_dark) * 0.7), (1, bark_dark)]), color)
    height = rings * 0.3 + outer * (noise * 0.6)
    edge_ring = np.clip(1 - np.abs(r - (N / 2 - 9)) / 1.5, 0, 1)
    color = color * (1 - 0.4 * edge_ring[..., None])
    return finish(compose(color, height, strength=5.0))


def planks(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    rng = rng_for(name)
    boards = 4
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    board = (ys // (N / boards)).astype(int)
    shift = rng.random(boards) * N
    grain = np.zeros((N, N))
    g = fbm(rng, 3, 4, stretch=(0.35, 3.0))
    for b in range(boards):
        rows = board == b
        grain[rows] = np.roll(g, int(shift[b]), axis=1)[rows]
    tone = rng.random(boards)[board]
    fy = (ys % (N / boards)) / (N / boards)
    bevel = np.minimum(fy, 1 - fy)
    seam = np.clip(1 - bevel * 12, 0, 1)
    height = grain * 0.4 + np.minimum(bevel * 8, 1) * 0.8
    color = gradient_map(0.15 + grain * 0.6 + tone * 0.25, [(0, dark), (0.5, mid), (1, light)])
    color = color * (1 - 0.55 * seam[..., None])
    # Naegel
    for b in range(boards):
        for nx in (10, N - 11):
            cy = (b + 0.5) * N / boards
            d = np.hypot(xs - nx, ys - cy)
            nail = np.clip(1 - d / 2.2, 0, 1)
            color = color * (1 - nail[..., None] * 0.6) + nail[..., None] * 0.15
            height = height + nail * 0.5
    return finish(compose(color, height, strength=6.0))


def leaves(name: str, dark: Color, mid: Color, light: Color, accent: Color | None = None, density: int = 260) -> Image.Image:
    """Blaetter (mit Loechern, Cutout): viele kleine Blattformen, kachelbar gezeichnet."""
    rng = rng_for(name)
    ss = 2
    size = N * ss
    color = np.zeros((size, size, 3))
    alpha = np.zeros((size, size))
    height = np.zeros((size, size))
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    for _ in range(density):
        cx, cy = rng.random(2) * size
        ang = rng.random() * math.pi
        length = (5 + rng.random() * 7) * ss
        width = length * (0.38 + rng.random() * 0.15)
        dx = xs - cx
        dy = ys - cy
        dx = (dx + size / 2) % size - size / 2
        dy = (dy + size / 2) % size - size / 2
        u = dx * math.cos(ang) + dy * math.sin(ang)
        v = -dx * math.sin(ang) + dy * math.cos(ang)
        shape = (u / length) ** 2 + (v / width) ** 2
        inside = shape < 1
        if not inside.any():
            continue
        tone = rng.random()
        c = gradient_map(np.full(1, tone), [(0, dark), (0.5, mid), (1, light)])[0]
        if accent is not None and rng.random() < 0.08:
            c = np.asarray(accent)
        shade = 0.75 + 0.35 * (1 - shape) - 0.2 * (v / width).clip(-1, 1)
        vein = np.exp(-(v / (width * 0.12)) ** 2) * 0.15
        col = c * (shade - vein)[..., None]
        color = np.where(inside[..., None], col, color)
        alpha = np.where(inside, 1.0, alpha)
        height = np.where(inside, 1 - shape + tone * 0.2, height)
    img = finish(color, alpha).resize((N, N), Image.LANCZOS)
    arr = np.asarray(img).copy()
    arr[..., 3] = np.where(arr[..., 3] > 127, 255, 0)
    return Image.fromarray(arr, "RGBA")


def plant(name: str, stem: Color, leaf: Color, blossom: Color | None, style: str = "tuft") -> Image.Image:
    """Kreuzpflanze (Cutout): Grasbueschel, Schilf, Bluete oder Dornenstrauch."""
    rng = rng_for(name)
    ss = 4
    size = N * ss
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    def col(c, f=1.0):
        c = np.clip(np.asarray(c) * f, 0, 1)
        return tuple(int(v * 255) for v in c) + (255,)

    base_y = size - 2 * ss
    if style in ("tuft", "reed", "thorn"):
        count = {"tuft": 16, "reed": 9, "thorn": 7}[style]
        for i in range(count):
            x0 = size * (0.15 + 0.7 * rng.random())
            h = size * ({"tuft": 0.45, "reed": 0.85, "thorn": 0.65}[style]) * (0.55 + 0.45 * rng.random())
            lean = (rng.random() - 0.5) * size * (0.35 if style == "tuft" else 0.15)
            w = size * (0.035 if style != "reed" else 0.025)
            pts_l, pts_r = [], []
            for s in np.linspace(0, 1, 12):
                x = x0 + lean * s * s
                y = base_y - h * s
                ww = w * (1 - s) ** 0.8
                pts_l.append((x - ww, y))
                pts_r.append((x + ww, y))
            f = 0.75 + 0.45 * rng.random()
            draw.polygon(pts_l + pts_r[::-1], fill=col(leaf if style != "thorn" else stem, f))
            if style == "reed":
                tip = (x0 + lean, base_y - h)
                draw.ellipse([tip[0] - w * 1.6, tip[1] - w * 1.0, tip[0] + w * 1.6, tip[1] + w * 5.5], fill=col(stem, 0.9 * f))
            if style == "thorn":
                for s in np.linspace(0.2, 0.9, 4):
                    x = x0 + lean * s * s
                    y = base_y - h * s
                    side = 1 if rng.random() < 0.5 else -1
                    draw.polygon([(x, y - w), (x, y + w), (x + side * w * 4, y - w * 2.5)], fill=col(stem, 1.1))
                if blossom is not None:
                    tip = (x0 + lean, base_y - h)
                    draw.ellipse([tip[0] - w * 1.8, tip[1] - w * 1.8, tip[0] + w * 1.8, tip[1] + w * 1.8], fill=col(blossom))
    elif style == "bloom":
        for i in range(4):
            x0 = size * (0.25 + 0.5 * rng.random())
            h = size * (0.55 + 0.3 * rng.random())
            w = size * 0.02
            draw.polygon([(x0 - w, base_y), (x0 + w, base_y), (x0 + w * 0.6, base_y - h), (x0 - w * 0.6, base_y - h)], fill=col(stem))
            for s in (0.35, 0.6):
                y = base_y - h * s
                side = 1 if i % 2 else -1
                xa, xb = sorted((x0 + side * w * 1, x0 + side * w * 9))
                draw.ellipse([xa, y - w * 3, xb, y + w * 1.5], fill=col(leaf, 0.9))
            cx, cy = x0, base_y - h
            petals = 7
            r = size * 0.075
            for p in range(petals):
                a = 2 * math.pi * p / petals + rng.random() * 0.3
                px, py = cx + math.cos(a) * r * 0.9, cy + math.sin(a) * r * 0.9
                draw.ellipse([px - r * 0.62, py - r * 0.62, px + r * 0.62, py + r * 0.62], fill=col(blossom, 0.85 + 0.3 * rng.random()))
            draw.ellipse([cx - r * 0.45, cy - r * 0.45, cx + r * 0.45, cy + r * 0.45], fill=col((1.0, 0.92, 0.55)))
    img = img.filter(ImageFilter.GaussianBlur(ss * 0.35)).resize((N, N), Image.LANCZOS)
    arr = np.asarray(img).astype(np.float64)
    # leichte Schattierung von unten nach oben
    shade = np.linspace(0.75, 1.1, N)[:, None, None]
    arr[..., :3] = np.clip(arr[..., :3] * shade, 0, 255)
    arr[..., 3] = np.where(arr[..., 3] > 110, 255, 0)
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def ore(name: str, stone: Image.Image, gem_dark: Color, gem: Color, gem_light: Color, clusters: int = 6,
        style: str = "crystal") -> Image.Image:
    """Erz: Gestein mit eingewachsenen Kristallen (facettiert, Glanzpunkte) oder Metallnestern."""
    rng = rng_for(name)
    base = np.asarray(stone.convert("RGB"), dtype=np.float64) / 255
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    mask = np.zeros((N, N))
    centers = rng.random((clusters, 2)) * N
    for cx, cy in centers:
        dx = np.abs(xs - cx)
        dy = np.abs(ys - cy)
        dx = np.minimum(dx, N - dx)
        dy = np.minimum(dy, N - dy)
        r = 13 + rng.random() * 9
        mask = np.maximum(mask, np.clip(1 - np.hypot(dx, dy) / r, 0, 1))
    blob = mask * (0.75 + 0.5 * fbm(rng, 10, 3))
    inside = blob > 0.35
    f1, f2, idx, pts = voronoi(rng_for(name + "x"), 55)
    facet = np.clip((f2 - f1) / 2.5, 0, 1)
    # Facetten: Helligkeit je Zelle aus zufaelliger Kippung
    tilt = rng.random((55, 2)) * 2 - 1
    dxp = xs - pts[idx, 0]
    dyp = ys - pts[idx, 1]
    dxp = (dxp + N / 2) % N - N / 2
    dyp = (dyp + N / 2) % N - N / 2
    shade = 0.5 + 0.5 * np.tanh((tilt[idx, 0] * dxp + tilt[idx, 1] * dyp) / 5)
    if style == "metal":
        t = shade * 0.6 + fbm(rng, 12, 3) * 0.4
    else:
        t = shade
    gem_col = gradient_map(t, [(0, gem_dark), (0.55, gem), (1, gem_light)])
    gem_col = gem_col * (0.65 + 0.35 * facet[..., None])
    sparkle = (rng.random((N, N)) < 0.012) & inside & (shade > 0.7)
    gem_col = np.where(sparkle[..., None], 1.0, gem_col)
    rim = (blob > 0.25) & ~inside
    out = np.where(inside[..., None], gem_col, base)
    out = np.where(rim[..., None], base * 0.45, out)
    height = np.where(inside, 0.6 + shade * 0.4, 0.0)
    light, gloss = lighting(height, 4.0, ambient=0.8)
    out = out * np.where(inside, light, 1.0)[..., None] + (np.where(inside, gloss, 0))[..., None]
    return finish(out)


# --- Technik ------------------------------------------------------------------------------------------------------

def panel_height(size: int, panels: int, bevel: float = 3.0) -> tuple[np.ndarray, np.ndarray]:
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    p = size / panels
    fx = (xs % p)
    fy = (ys % p)
    d = np.minimum(np.minimum(fx, p - 1 - fx), np.minimum(fy, p - 1 - fy))
    h = np.clip(d / bevel, 0, 1)
    pid = (ys // p).astype(int) * panels + (xs // p).astype(int)
    return h, pid


def rivets_layer(size: int, panels: int, inset: float = 6.0, radius: float = 2.2) -> np.ndarray:
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    p = size / panels
    fx = xs % p
    fy = ys % p
    out = np.zeros((size, size))
    for cx in (inset, p - 1 - inset):
        for cy in (inset, p - 1 - inset):
            d = np.hypot(fx - cx, fy - cy)
            out = np.maximum(out, np.clip(1 - d / radius, 0, 1))
    return out


def scratches(rng: np.random.Generator, count: int, size: int = N) -> np.ndarray:
    img = Image.new("L", (size * 2, size * 2), 0)
    draw = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = rng.random(2) * size * 2
        a = rng.random() * math.pi
        length = (6 + rng.random() * 26) * 2
        draw.line([(x, y), (x + math.cos(a) * length, y + math.sin(a) * length)], fill=int(90 + rng.random() * 120), width=1)
    small = np.asarray(img.resize((size, size), Image.LANCZOS), dtype=np.float64) / 255
    return small


def hull(name: str, base: Color, panels: int = 2, accent: Color | None = None, wear: float = 1.0) -> Image.Image:
    rng = rng_for(name)
    h, pid = panel_height(N, panels, bevel=4.0)
    tone = rng.random(panels * panels)[pid]
    brushed = fbm(rng, 4, 4, stretch=(0.2, 4.0))
    grime = fbm(rng_for(name + "g"), 3, 4)
    edge_grime = (1 - h) * 0.6 + np.clip(grime - 0.55, 0, 1) * 1.6 * wear
    riv = rivets_layer(N, panels)
    sc = scratches(rng, int(40 * wear))
    color = np.asarray(base) * (0.88 + 0.12 * tone[..., None] + 0.08 * brushed[..., None])
    if accent is not None:
        stripe = (np.abs(((np.mgrid[0:N, 0:N][0]) % (N / panels)) - N / panels / 2) < 3)
        color = np.where(stripe[..., None], np.asarray(accent) * (0.9 + 0.1 * brushed[..., None]), color)
    color = color * (1 - 0.35 * np.clip(edge_grime, 0, 1)[..., None])
    color = color + sc[..., None] * 0.25
    height = h * 0.9 + riv * 0.8 + brushed * 0.08
    return finish(compose(color, height, strength=5.0, spec=0.35, shininess=28, ambient=0.6))


def hazard(name: str) -> Image.Image:
    rng = rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    stripe = ((xs + ys) // 16 % 2).astype(bool)
    yellow = hexc("F2C230")
    black = hexc("1E1C1A")
    color = np.where(stripe[..., None], yellow, black)
    chips = fbm(rng, 10, 4)
    worn = chips > 0.72
    metal = hexc("8A8C90") * (0.8 + 0.3 * fbm(rng, 20, 2)[..., None])
    color = np.where(worn[..., None], metal, color)
    h, _ = panel_height(N, 1, bevel=6.0)
    color = color * (0.75 + 0.25 * h[..., None])
    height = h + (~worn) * 0.15 + scratches(rng, 50) * 0.2
    return finish(compose(color, height, strength=4.0, spec=0.3))


def glass(name: str, frame: Color, tint: Color) -> Image.Image:
    rng = rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    border = 8
    d = np.minimum(np.minimum(xs, N - 1 - xs), np.minimum(ys, N - 1 - ys))
    is_frame = d < border
    fh = np.clip(d / 3, 0, 1) * is_frame
    frame_col = np.asarray(frame) * (0.8 + 0.25 * fbm(rng, 8, 3)[..., None])
    streak = np.exp(-((xs - ys - 20) / 7) ** 2) * 0.55 + np.exp(-((xs - ys + 26) / 3) ** 2) * 0.35
    glass_col = np.asarray(tint) * (0.7 + 0.3 * (1 - ys / N))[..., None] + streak[..., None]
    color = np.where(is_frame[..., None], frame_col, glass_col)
    riv = np.zeros((N, N))
    for cx in (4, N - 5):
        for cy in (4, N - 5):
            riv = np.maximum(riv, np.clip(1 - np.hypot(xs - cx, ys - cy) / 2, 0, 1))
    shaded = compose(color, fh + riv, strength=5.0, spec=0.3)
    alpha = np.where(is_frame, 1.0, 0.32 + streak * 0.5)
    return finish(np.where(is_frame[..., None], shaded, color), alpha)


def light_panel(name: str, frame: Color, glow: Color) -> Image.Image:
    rng = rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    cells = 4
    p = N / cells
    fx = xs % p
    fy = ys % p
    d = np.minimum(np.minimum(fx, p - 1 - fx), np.minimum(fy, p - 1 - fy))
    grid = d < 3
    center = np.hypot(fx - p / 2, fy - p / 2) / (p / 2)
    bright = np.clip(1.15 - center * 0.55, 0, 1)
    color = np.asarray(glow) * bright[..., None] + (1 - np.asarray(glow)) * np.clip(0.6 - center, 0, 1)[..., None] * 0.8
    color = np.where(grid[..., None], np.asarray(frame) * (0.8 + 0.2 * fbm(rng, 8, 2)[..., None]), color)
    return finish(color)


def grate(name: str, metal: Color) -> Image.Image:
    rng = rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    p = 16
    fx = xs % p
    fy = ys % p
    bar = (np.minimum(fx, p - fx) < 3) | (np.minimum(fy, p - fy) < 3)
    border = np.minimum(np.minimum(xs, N - 1 - xs), np.minimum(ys, N - 1 - ys)) < 6
    solid = bar | border
    h = np.where(solid, 1.0, 0.0) - np.minimum(np.minimum(fx, p - fx), np.minimum(fy, p - fy)) * 0.05
    color = np.asarray(metal) * (0.8 + 0.3 * fbm(rng, 6, 3)[..., None])
    color = compose(color, h + scratches(rng, 30) * 0.2, strength=4.0, spec=0.35)
    return finish(color, solid.astype(np.float64))


def hex_tiles(name: str, dark: Color, mid: Color, seam_glow: Color, glow: float = 0.5) -> Image.Image:
    rng = rng_for(name)
    pts = []
    for r in range(4):
        for c in range(4):
            pts.append(((c + 0.5 * (r % 2)) * N / 4, r * N / 4))
    f1, f2, idx, _ = voronoi(rng, 0, points=np.array(pts), aspect=(1.0, 1.15))
    edge = np.clip((f2 - f1) / 3, 0, 1)
    tone = rng.random(len(pts))[idx]
    brushed = fbm(rng, 6, 3)
    color = gradient_map(0.2 + tone * 0.4 + brushed * 0.3, [(0, dark), (1, mid)])
    seam = (1 - edge) ** 3
    color = color * (1 - 0.6 * seam[..., None]) + np.asarray(seam_glow) * seam[..., None] * glow
    height = edge ** 0.5 + brushed * 0.1
    return finish(compose(color, height, strength=6.0, spec=0.3, shininess=30))


def trim(name: str, plate: Color, glow: Color) -> Image.Image:
    rng = rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    h, _ = panel_height(N, 1, bevel=5.0)
    brushed = fbm(rng, 3, 4, stretch=(4.0, 0.25))
    color = np.asarray(plate) * (0.8 + 0.25 * brushed[..., None])
    lines = np.zeros((N, N))
    for y in (N * 0.3, N * 0.7):
        lines = np.maximum(lines, np.exp(-((ys - y) / 3.0) ** 2))
    core = np.exp(-((ys - N * 0.3) / 1.2) ** 2) + np.exp(-((ys - N * 0.7) / 1.2) ** 2)
    color = compose(color, h + brushed * 0.1 - lines * 0.4, strength=5.0, spec=0.3)
    color = color * (1 - lines[..., None] * 0.7) + np.asarray(glow) * lines[..., None] + core[..., None] * 0.5
    return finish(color)


def pad(name: str, base: Color, paint: Color | None = None) -> Image.Image:
    rng = rng_for(name)
    d = fbm(rng, 6, 5)
    tracks = np.clip(fbm(rng_for(name + "t"), 2, 3, stretch=(3.0, 0.3)) - 0.6, 0, 1) * 2
    h, _ = panel_height(N, 2, bevel=2.0)
    color = np.asarray(base) * (0.8 + 0.3 * d[..., None])
    if paint is not None:
        worn = fbm(rng_for(name + "w"), 12, 3) > 0.7
        color = np.where(worn[..., None], color, np.asarray(paint) * (0.85 + 0.2 * d[..., None]))
    color = color * (1 - 0.35 * tracks[..., None])
    return finish(compose(color, h * 0.6 + d * 0.3, strength=4.0))


def magma_core(name: str) -> Image.Image:
    rng = rng_for(name)
    f1, f2, idx, _ = voronoi(rng, 18)
    edge = np.clip((f2 - f1) / 4, 0, 1)
    d = fbm(rng, 6, 4)
    color = gradient_map(d * 0.6 + rng.random(18)[idx] * 0.4, [(0, hexc("120E10")), (1, hexc("3A2A2A"))])
    seam = (1 - edge) ** 2.5
    hot = gradient_map(seam, [(0, hexc("2A0A00")), (0.6, hexc("E0501A")), (1, hexc("FFD27A"))])
    color = color * (1 - seam[..., None]) + hot * seam[..., None]
    return finish(compose(color, edge * 0.8 + d * 0.3, strength=6.0))


def metal_block(name: str, dark: Color, mid: Color, light: Color, emblem: str = "hex") -> Image.Image:
    rng = rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    d = np.minimum(np.minimum(xs, N - 1 - xs), np.minimum(ys, N - 1 - ys))
    frame = np.clip(d / 4, 0, 1)
    inner = np.clip((d - 12) / 3, 0, 1)
    brushed = fbm(rng, 3, 5, stretch=(6.0, 0.2))
    cx = cy = N / 2 - 0.5
    r = np.hypot(xs - cx, ys - cy)
    ang = np.arctan2(ys - cy, xs - cx)
    sector = (ang + math.pi / 6) % (math.pi / 3) - math.pi / 6
    hexr = r * np.cos(sector) / math.cos(math.pi / 6)
    emb = np.clip(1 - np.abs(hexr - 26) / 3, 0, 1) + np.clip(1 - hexr / 10, 0, 1)
    height = frame * 0.6 + inner * 0.4 + emb * 0.5 + brushed * 0.08
    t = 0.3 + brushed * 0.35 + inner * 0.2 + emb * 0.15
    color = gradient_map(t, [(0, dark), (0.5, mid), (1, light)])
    riv = np.zeros((N, N))
    for px in (7, N - 8):
        for py in (7, N - 8):
            riv = np.maximum(riv, np.clip(1 - np.hypot(xs - px, ys - py) / 3, 0, 1))
    return finish(compose(color, height + riv, strength=6.0, spec=0.55, shininess=34, ambient=0.55))


# --- Items (64x64, freigestellt) ----------------------------------------------------------------------------------

SS = 4


def _canvas():
    img = Image.new("RGBA", (ITEM * SS, ITEM * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def _rgba(c: Color, f: float = 1.0, a: int = 255):
    c = np.clip(np.asarray(c) * f, 0, 1)
    return tuple(int(v * 255) for v in c) + (a,)


def _finish_item(img: Image.Image, outline: Color = (0.07, 0.05, 0.06)) -> Image.Image:
    small = img.resize((ITEM, ITEM), Image.LANCZOS)
    arr = np.asarray(small).astype(np.float64)
    solid = arr[..., 3] > 100
    grown = solid.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grown |= np.roll(np.roll(solid, dy, 0), dx, 1)
    edge = grown & ~solid
    arr[..., 3] = np.where(solid, 255, 0)
    arr[edge] = [*(np.asarray(outline) * 255), 255]
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def _shaded_polygon(img: Image.Image, pts, dark: Color, mid: Color, light: Color, direction=(-1, -1)) -> None:
    """Polygon mit Verlauf entlang direction (Licht oben links)."""
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    w, h = img.size
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    xs_p = [p[0] for p in pts]
    ys_p = [p[1] for p in pts]
    proj = (xs * -direction[0] + ys * -direction[1])
    lo = min(x * -direction[0] + y * -direction[1] for x, y in pts)
    hi = max(x * -direction[0] + y * -direction[1] for x, y in pts)
    t = 1 - (proj - lo) / max(hi - lo, 1)
    col = gradient_map(t, [(0, dark), (0.5, mid), (1, light)])
    layer = Image.fromarray(np.dstack([(col * 255).astype(np.uint8), np.asarray(mask)]), "RGBA")
    img.alpha_composite(layer)
    del xs_p, ys_p


def item_ingot(dark: Color, mid: Color, light: Color) -> Image.Image:
    img, draw = _canvas()
    s = SS
    top = [(14 * s, 28 * s), (40 * s, 18 * s), (52 * s, 24 * s), (26 * s, 34 * s)]
    front = [(14 * s, 28 * s), (26 * s, 34 * s), (26 * s, 44 * s), (14 * s, 38 * s)]
    side = [(26 * s, 34 * s), (52 * s, 24 * s), (52 * s, 34 * s), (26 * s, 44 * s)]
    _shaded_polygon(img, side, np.asarray(dark) * 0.8, dark, mid)
    _shaded_polygon(img, front, dark, mid, mid)
    _shaded_polygon(img, top, mid, light, (1, 1, 1))
    draw = ImageDraw.Draw(img)
    draw.line([(18 * s, 29 * s), (40 * s, 21 * s)], fill=_rgba((1, 1, 1), 1, 200), width=s)
    return _finish_item(img)


def item_raw(name: str, dark: Color, mid: Color, light: Color) -> Image.Image:
    rng = rng_for(name)
    img, _ = _canvas()
    s = SS
    for _ in range(7):
        cx, cy = 32 * s + rng.normal(0, 7 * s), 34 * s + rng.normal(0, 6 * s)
        r = (7 + rng.random() * 6) * s
        k = rng.integers(5, 8)
        pts = []
        for i in range(k):
            a = 2 * math.pi * i / k + rng.random() * 0.5
            rr = r * (0.7 + 0.4 * rng.random())
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        _shaded_polygon(img, pts, dark, mid, light)
    return _finish_item(img)


def item_tool(kind: str, dark: Color, mid: Color, light: Color, grip: Color = (0.22, 0.2, 0.24),
              accent: Color = (0.95, 0.75, 0.25)) -> Image.Image:
    img, draw = _canvas()
    s = SS
    # Griff diagonal von unten links nach oben rechts
    if kind != "sword":
        handle = [(10 * s, 56 * s), (14 * s, 58 * s), (40 * s, 26 * s), (36 * s, 23 * s)]
        _shaded_polygon(img, handle, np.asarray(grip) * 0.7, grip, np.asarray(grip) * 1.6)
        for t in (0.25, 0.45):
            x = 12 * s + (38 - 12) * s * t
            y = 57 * s - (57 - 24) * s * t
            ImageDraw.Draw(img).ellipse([x - 2.5 * s, y - 2.5 * s, x + 2.5 * s, y + 2.5 * s], fill=_rgba(accent))
    if kind == "sword":
        blade = [(18 * s, 40 * s), (24 * s, 46 * s), (56 * s, 12 * s), (58 * s, 6 * s), (52 * s, 8 * s)]
        _shaded_polygon(img, blade, dark, mid, light)
        ImageDraw.Draw(img).line([(21 * s, 43 * s), (55 * s, 9 * s)], fill=_rgba(light, 1.15), width=s)
        guard = [(12 * s, 38 * s), (16 * s, 34 * s), (30 * s, 48 * s), (26 * s, 52 * s)]
        _shaded_polygon(img, guard, np.asarray(accent) * 0.6, accent, (1, 0.95, 0.7))
        grip_pts = [(8 * s, 54 * s), (12 * s, 58 * s), (22 * s, 48 * s), (18 * s, 44 * s)]
        _shaded_polygon(img, grip_pts, np.asarray(grip) * 0.7, grip, np.asarray(grip) * 1.6)
        ImageDraw.Draw(img).ellipse([5 * s, 54 * s, 11 * s, 60 * s], fill=_rgba(accent))
    elif kind == "pickaxe":
        head = [(18 * s, 14 * s), (30 * s, 8 * s), (44 * s, 12 * s), (54 * s, 22 * s), (58 * s, 34 * s),
                (52 * s, 28 * s), (42 * s, 20 * s), (32 * s, 16 * s), (22 * s, 18 * s)]
        _shaded_polygon(img, head, dark, mid, light)
        ImageDraw.Draw(img).ellipse([35 * s, 18 * s, 43 * s, 26 * s], fill=_rgba(accent))
    elif kind == "axe":
        # einseitige Klinge mit geschwungener Schneide
        head = [(34 * s, 22 * s), (30 * s, 12 * s), (36 * s, 4 * s), (46 * s, 6 * s), (52 * s, 14 * s), (54 * s, 24 * s),
                (50 * s, 32 * s), (44 * s, 30 * s), (40 * s, 26 * s)]
        _shaded_polygon(img, head, dark, mid, light)
        ImageDraw.Draw(img).arc([34 * s, 2 * s, 58 * s, 34 * s], -80, 40, fill=_rgba(light, 1.25), width=2 * s)
        ImageDraw.Draw(img).ellipse([35 * s, 20 * s, 41 * s, 26 * s], fill=_rgba(accent))
    elif kind == "shovel":
        # Spaten: breites Blatt am Ende des Stiels, Spitze nach oben rechts
        head = [(36 * s, 28 * s), (34 * s, 20 * s), (44 * s, 6 * s), (52 * s, 4 * s), (58 * s, 8 * s), (58 * s, 14 * s),
                (46 * s, 26 * s)]
        _shaded_polygon(img, head, dark, mid, light)
        ImageDraw.Draw(img).line([(38 * s, 24 * s), (54 * s, 8 * s)], fill=_rgba(dark, 0.8), width=s)
    return _finish_item(img)


def item_armor(kind: str, dark: Color, mid: Color, light: Color, accent: Color = (0.95, 0.75, 0.25)) -> Image.Image:
    img, draw = _canvas()
    s = SS
    if kind == "helmet":
        shell = [(14 * s, 40 * s), (14 * s, 24 * s), (22 * s, 12 * s), (42 * s, 12 * s), (50 * s, 24 * s), (50 * s, 40 * s),
                 (42 * s, 40 * s), (40 * s, 30 * s), (24 * s, 30 * s), (22 * s, 40 * s)]
        _shaded_polygon(img, shell, dark, mid, light)
        visor = [(22 * s, 30 * s), (42 * s, 30 * s), (40 * s, 36 * s), (24 * s, 36 * s)]
        _shaded_polygon(img, visor, (0.1, 0.25, 0.35), (0.25, 0.7, 0.9), (0.8, 1, 1))
        ImageDraw.Draw(img).line([(32 * s, 13 * s), (32 * s, 29 * s)], fill=_rgba(accent), width=2 * s)
    elif kind == "chestplate":
        body = [(12 * s, 14 * s), (24 * s, 10 * s), (32 * s, 16 * s), (40 * s, 10 * s), (52 * s, 14 * s), (54 * s, 30 * s),
                (46 * s, 30 * s), (46 * s, 54 * s), (18 * s, 54 * s), (18 * s, 30 * s), (10 * s, 30 * s)]
        _shaded_polygon(img, body, dark, mid, light)
        core = [(28 * s, 26 * s), (36 * s, 26 * s), (38 * s, 32 * s), (32 * s, 38 * s), (26 * s, 32 * s)]
        _shaded_polygon(img, core, np.asarray(accent) * 0.6, accent, (1, 1, 0.85))
        ImageDraw.Draw(img).line([(20 * s, 44 * s), (44 * s, 44 * s)], fill=_rgba(dark, 0.7), width=2 * s)
    elif kind == "leggings":
        legs = [(16 * s, 10 * s), (48 * s, 10 * s), (50 * s, 56 * s), (38 * s, 56 * s), (32 * s, 24 * s), (26 * s, 56 * s),
                (14 * s, 56 * s)]
        _shaded_polygon(img, legs, dark, mid, light)
        ImageDraw.Draw(img).rectangle([16 * s, 10 * s, 48 * s, 16 * s], fill=_rgba(accent, 0.9))
    elif kind == "boots":
        for ox in (0, 22):
            boot = [((10 + ox) * s, 24 * s), ((22 + ox) * s, 24 * s), ((22 + ox) * s, 44 * s), ((30 + ox) * s, 48 * s),
                    ((30 + ox) * s, 54 * s), ((8 + ox) * s, 54 * s), ((8 + ox) * s, 40 * s)]
            _shaded_polygon(img, boot, dark, mid, light)
            ImageDraw.Draw(img).rectangle([(8 + ox) * s, 50 * s, (30 + ox) * s, 54 * s], fill=_rgba(accent, 0.8))
    return _finish_item(img)


def armor_layer(name: str, dark: Color, mid: Color, light: Color, accent: Color, legs: bool) -> Image.Image:
    """Ruestungsschicht 128x64 (doppelte Aufloesung der Standard-UV 64x32): Plattenmuster ueber alle UV-Inseln."""
    rng = rng_for(name)
    w, h = 128, 64
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    big = fbm(rng, 4, 4, size=128)[:h]
    p = 16
    fx = xs % p
    fy = ys % p
    d = np.minimum(np.minimum(fx, p - 1 - fx), np.minimum(fy, p - 1 - fy))
    plate = np.clip(d / 2.5, 0, 1)
    color = gradient_map(0.25 + big * 0.5 + plate * 0.25, [(0, dark), (0.5, mid), (1, light)])
    color = color * (0.6 + 0.4 * plate[..., None])
    glow_line = (np.abs(fy - p / 2) < 1) & (rng.random((h, w)) < 2) & ((xs // p + ys // p) % 3 == 0)
    color = np.where(glow_line[..., None], np.asarray(accent), color)
    del legs
    return finish(color)
