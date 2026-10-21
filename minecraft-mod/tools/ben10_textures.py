"""Prozedurale Texturen der Ben-10-Inhalte (Corrodium, Omnitrix-Werkbank, Omnitrix-Teile, Energiekerne, Klempner).

Baut auf planet_textures auf: Bloecke 128x128 nahtlos, Items 64x64 freigestellt mit Umriss. Deterministisch (der
Zufall haengt nur vom Texturnamen ab).
"""
from __future__ import annotations

import math
from typing import Sequence

import numpy as np
from PIL import Image, ImageDraw

import planet_textures as tx

H = tx.hexc
N = tx.N
SS = tx.SS
Color = Sequence[float]

# Corrodium: giftgruen, leuchtend
CORRODIUM = (H("06300E"), H("2EE83A"), H("D2FFB8"))
# Klempner-Technik: helles Grau, schwarze Fugen, gruene Leuchtlinien
PLUMBER_LIGHT = H("C8CCD2")
PLUMBER_DARK = H("2A2E34")
PLUMBER_GLOW = H("39FF14")
OMNITRIX_BLACK = (H("0E1012"), H("2A2E34"), H("5A6068"))
OMNITRIX_GREY = (H("4A4E56"), H("9AA0A8"), H("E2E6EA"))

# Farben der Energiekerne (id -> dunkel, mitte, hell); muessen zu OmnitrixColors passen
CORE_COLORS = {
    "green": (H("0A5A10"), H("39FF14"), H("D8FFC8")),
    "blue": (H("0A3A7A"), H("2FA8FF"), H("D8F0FF")),
    "red": (H("6A0A06"), H("FF3A2A"), H("FFD8D0")),
    "yellow": (H("6A5000"), H("FFD21A"), H("FFF6C8")),
    "purple": (H("3A0A6A"), H("B04AFF"), H("F0DCFF")),
    "white": (H("6A7A88"), H("E8F4FF"), H("FFFFFF")),
    "orange": (H("6A2A00"), H("FF8A1E"), H("FFE0C0")),
    "pink": (H("6A0A44"), H("FF5AC8"), H("FFD8F0")),
    "cyan": (H("005A5A"), H("2FFFE8"), H("D8FFFA")),
}


# --- Bloecke ----------------------------------------------------------------------------------------------------

def end_stone(name: str) -> Image.Image:
    """Endstein-artiges Gestein (blass gelb, poroes) als Grund fuer das Corrodium-Erz."""
    return tx.porous(name, H("8A8656"), H("D8D49A"), H("F2F0C8"), holes=30, hole_size=3.5)


def corrodium_ore(name: str) -> Image.Image:
    return tx.ore(name, end_stone(name + "_stone"), *CORRODIUM, clusters=6, style="crystal", glow=0.45)


def corrodium_block(name: str) -> Image.Image:
    img = tx.crystal(name, *CORRODIUM)
    arr = np.asarray(img).astype(np.float64)
    arr[..., 3] = 255   # fester Block, nicht durchscheinend
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def _hourglass_mask(cx: float, cy: float, r: float) -> np.ndarray:
    """Omnitrix-Sanduhr (zwei Dreiecke mit Spitzen in der Mitte) als 0..1-Maske."""
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    dx = np.abs(xs - cx)
    dy = ys - cy
    upper = (dy < 0) & (dy > -r) & (dx < -dy * 0.75)
    lower = (dy >= 0) & (dy < r) & (dx < dy * 0.75)
    return (upper | lower).astype(np.float64)


def workbench_top(name: str) -> Image.Image:
    """Oberseite: dunkle Platte mit rundem Montagefeld und leuchtender Sanduhr."""
    rng = tx.rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    h, _ = tx.panel_height(N, 1, bevel=5.0)
    brushed = tx.fbm(rng, 3, 4, stretch=(5.0, 0.25))
    color = np.asarray(PLUMBER_DARK) * (0.85 + 0.25 * brushed[..., None])
    r = np.hypot(xs - N / 2 + 0.5, ys - N / 2 + 0.5)
    ring = np.clip(1 - np.abs(r - 44) / 3, 0, 1)
    pad = (r < 41).astype(np.float64)
    glass = _hourglass_mask(N / 2 - 0.5, N / 2 - 0.5, 30)
    color = np.where(pad[..., None] > 0, np.asarray(OMNITRIX_BLACK[1]) * (0.9 + 0.2 * brushed[..., None]), color)
    color = color * (1 - ring[..., None] * 0.5) + np.asarray(PLUMBER_GLOW) * ring[..., None] * 0.9
    color = np.where(glass[..., None] > 0, np.asarray(PLUMBER_GLOW) * 0.9 + 0.1, color)
    height = h * 0.6 + pad * 0.2 + ring * 0.3 + brushed * 0.05
    return tx.finish(tx.compose(color, height, strength=5.0, spec=0.4, shininess=30, ambient=0.65))


def workbench_side(name: str) -> Image.Image:
    """Seite: Klempner-Paneel (hell) mit schwarzem Band und gruener Leuchtlinie."""
    rng = tx.rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    h, _ = tx.panel_height(N, 1, bevel=4.0)
    brushed = tx.fbm(rng, 3, 4, stretch=(0.25, 5.0))
    color = np.asarray(PLUMBER_LIGHT) * (0.85 + 0.2 * brushed[..., None])
    band = ((ys > N * 0.62) & (ys < N * 0.86)).astype(np.float64)
    color = np.where(band[..., None] > 0, np.asarray(PLUMBER_DARK) * (0.9 + 0.2 * brushed[..., None]), color)
    line = np.exp(-((ys - N * 0.74) / 1.6) ** 2) * ((xs > 14) & (xs < N - 15))
    color = color + np.asarray(PLUMBER_GLOW) * line[..., None]
    vent = (((xs.astype(int) // 6) % 2 == 0) & (ys > N * 0.2) & (ys < N * 0.42) & (xs > N * 0.3) & (xs < N * 0.7)).astype(np.float64)
    color = color * (1 - vent[..., None] * 0.55)
    height = h * 0.7 + band * 0.15 - vent * 0.3 + brushed * 0.05
    return tx.finish(tx.compose(color, height, strength=5.0, spec=0.35, shininess=26, ambient=0.62))


def workbench_bottom(name: str) -> Image.Image:
    return tx.hull(name, PLUMBER_DARK, panels=2, wear=0.6)


# --- Items ------------------------------------------------------------------------------------------------------

def _rgba(c: Color, a: int = 255):
    return tx._rgba(c, 1.0, a)


def raw_corrodium(name: str) -> Image.Image:
    return tx.item_raw(name, *CORRODIUM)


def corrodium_crystal(name: str) -> Image.Image:
    """Drei geschliffene Kristallspitzen mit Leuchtkern."""
    img, _ = tx._canvas()
    s = SS
    dark, mid, light = CORRODIUM
    shards = [((32, 58), 9, 46, 0.0), ((20, 58), 6, 30, -0.45), ((44, 58), 6, 34, 0.4)]
    for (bx, by), w, length, tilt in shards:
        tip = (bx + math.sin(tilt) * length, by - math.cos(tilt) * length)
        nx, ny = math.cos(tilt) * w, math.sin(tilt) * w
        left = [(bx * s - nx * s, by * s - ny * s), (bx * s, by * s), (tip[0] * s, tip[1] * s)]
        right = [(bx * s, by * s), (bx * s + nx * s, by * s + ny * s), (tip[0] * s, tip[1] * s)]
        tx._shaded_polygon(img, left, mid, light, (1, 1, 1))
        tx._shaded_polygon(img, right, dark, mid, light)
    draw = ImageDraw.Draw(img)
    draw.ellipse([27 * s, 40 * s, 37 * s, 50 * s], fill=_rgba(light, 140))
    return tx._finish_item(img)


def _face_ring(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, grey: tuple, black: tuple) -> None:
    s = SS
    draw.ellipse([(cx - r) * s, (cy - r) * s, (cx + r) * s, (cy + r) * s], fill=_rgba(grey[1]))
    draw.ellipse([(cx - r + 2) * s, (cy - r + 2) * s, (cx + r - 2) * s, (cy + r - 2) * s], fill=_rgba(grey[2]))
    draw.ellipse([(cx - r + 4) * s, (cy - r + 4) * s, (cx + r - 4) * s, (cy + r - 4) * s], fill=_rgba(black[1]))


def omnitrix_shell(name: str) -> Image.Image:
    """Huelle: Armband mit leerem Zifferblatt-Ring (ohne Kern)."""
    img, draw = tx._canvas()
    s = SS
    black, grey = OMNITRIX_BLACK, OMNITRIX_GREY
    # Armband (oben und unten), mit Gliedern
    for y0, y1 in ((4, 20), (44, 60)):
        band = [(22 * s, y0 * s), (42 * s, y0 * s), (42 * s, y1 * s), (22 * s, y1 * s)]
        tx._shaded_polygon(img, band, black[0], black[1], black[2])
        for y in range(y0 + 3, y1, 4):
            draw.line([(23 * s, y * s), (41 * s, y * s)], fill=_rgba(black[0]), width=s)
    # Gehaeuse mit vier Klauen
    for a in (45, 135, 225, 315):
        rad = math.radians(a)
        cx, cy = 32 + math.cos(rad) * 15, 32 + math.sin(rad) * 15
        draw.ellipse([(cx - 5) * s, (cy - 5) * s, (cx + 5) * s, (cy + 5) * s], fill=_rgba(grey[1]))
    _face_ring(draw, 32, 32, 17, grey, black)
    # leere Fassung: dunkle Mulde mit Kontaktpunkten
    draw.ellipse([22 * s, 22 * s, 42 * s, 42 * s], fill=_rgba((0.04, 0.05, 0.06)))
    for a in range(0, 360, 60):
        rad = math.radians(a)
        cx, cy = 32 + math.cos(rad) * 7, 32 + math.sin(rad) * 7
        draw.ellipse([(cx - 1.4) * s, (cy - 1.4) * s, (cx + 1.4) * s, (cy + 1.4) * s], fill=_rgba(grey[2]))
    return tx._finish_item(img)


def omnitrix_console(name: str) -> Image.Image:
    """Konsole: das Zifferblatt mit Sanduhr und Steuerring, ohne Armband."""
    img, draw = tx._canvas()
    s = SS
    black, grey = OMNITRIX_BLACK, OMNITRIX_GREY
    glow = CORE_COLORS["green"]
    _face_ring(draw, 32, 32, 26, grey, black)
    # Steuerring mit Rillen
    for a in range(0, 360, 20):
        rad = math.radians(a)
        x0, y0 = 32 + math.cos(rad) * 21, 32 + math.sin(rad) * 21
        x1, y1 = 32 + math.cos(rad) * 24, 32 + math.sin(rad) * 24
        draw.line([(x0 * s, y0 * s), (x1 * s, y1 * s)], fill=_rgba(grey[0]), width=s)
    # Sanduhr
    draw.ellipse([14 * s, 14 * s, 50 * s, 50 * s], fill=_rgba(glow[1]))
    draw.polygon([(18 * s, 16 * s), (46 * s, 16 * s), (32 * s, 32 * s)], fill=_rgba(black[0]))
    draw.polygon([(18 * s, 48 * s), (46 * s, 48 * s), (32 * s, 32 * s)], fill=_rgba(black[0]))
    draw.ellipse([20 * s, 22 * s, 26 * s, 28 * s], fill=_rgba(glow[2], 200))
    return tx._finish_item(img)


def energy_core(name: str, color_id: str | None) -> Image.Image:
    """Energiekern: sechseckige Fassung mit leuchtender Kugel; Basis-Kern farblos-grau."""
    img, draw = tx._canvas()
    s = SS
    frame = OMNITRIX_GREY
    orb = CORE_COLORS[color_id] if color_id else (H("2A2E34"), H("7A8088"), H("D0D4DA"))
    hexagon = [(32 * s + math.cos(math.radians(a)) * 26 * s, 32 * s + math.sin(math.radians(a)) * 26 * s)
               for a in range(30, 390, 60)]
    tx._shaded_polygon(img, hexagon, frame[0], frame[1], frame[2])
    inner = [(32 * s + math.cos(math.radians(a)) * 21 * s, 32 * s + math.sin(math.radians(a)) * 21 * s) for a in range(30, 390, 60)]
    draw.polygon(inner, fill=_rgba(OMNITRIX_BLACK[0]))
    # Kugel mit Verlauf und Glanz
    for i, rr in enumerate(range(17, 0, -1)):
        t = i / 16
        c = tx.gradient_map(np.array([[t]]), [(0, orb[0]), (0.6, orb[1]), (1, orb[2])])[0, 0]
        draw.ellipse([(32 - rr) * s, (33 - rr) * s, (32 + rr) * s, (33 + rr) * s], fill=_rgba(c))
    draw.ellipse([24 * s, 23 * s, 31 * s, 30 * s], fill=_rgba((1, 1, 1), 170))
    if color_id is None:
        # Basis-Kern: Sanduhr-Praegung statt Leuchten
        draw.polygon([(26 * s, 25 * s), (38 * s, 25 * s), (32 * s, 33 * s)], fill=_rgba(OMNITRIX_BLACK[1]))
        draw.polygon([(26 * s, 41 * s), (38 * s, 41 * s), (32 * s, 33 * s)], fill=_rgba(OMNITRIX_BLACK[1]))
    return tx._finish_item(img)
