#!/usr/bin/env python3
"""Erzeugt die Aphelion als glattes 3D-Netz mit gebackener Textur (kein Wuerfelmodell).

Vorlage: Ratchets oranges Gadgetron-Schiff — gegabelte Nase aus zwei spitzen Klingen, grosse bernsteinfarbene
Glaskuppel, gepanzerte Schwenkfluegel an Hydraulikarmen mit Stacheln und Gadgetron-Sechseck, Doppelkanonen an den
Fluegelspitzen, zwei Haupttriebwerke und ein Ruecken-Triebwerk.

Jedes Teil ist eine parametrische Flaeche S(u, v). Daraus entstehen
  * das Netz (Gitter in u/v, Normalen aus den Ableitungen, Dreiecke gegen den Uhrzeigersinn von aussen gesehen) und
  * die Textur: jedes Teil bekommt ein Rechteck im Atlas, jeder Texel wird an S(u, v) ausgewertet — Lack, Paneelfugen,
    Nieten, Schriftzuege, Abnutzung, Kontaktschatten und Glanz werden so direkt auf die Oberflaeche gebacken.

Koordinaten in Bloecken, Ursprung = Mitte der Hitbox am Boden, +z = Nase, +y = oben, +x = linke Schiffsseite.

Ausgabe:
  assets/kingdomomnitrix/ship/aphelion.bin           Netz (Format SHIP v1, siehe write_mesh)
  assets/kingdomomnitrix/textures/entity/ship/aphelion.png
  assets/kingdomomnitrix/textures/item/aphelion.png        Item-Symbol (aus dem Netz gerendert)

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_ship_mesh.py              # schreiben
    python tools/generate_ship_mesh.py --check      # Dateien vorhanden und lesbar?
    python tools/generate_ship_mesh.py --preview out.png   # zusaetzlich Vorschaubild rendern
"""
from __future__ import annotations

import argparse
import logging
import math
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

try:
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    sys.exit("numpy und Pillow fehlen: pip install numpy pillow")

LOG = logging.getLogger("ship_mesh")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
MESH_PATH = ASSETS / "ship" / "aphelion.bin"
TEXTURE_PATH = ASSETS / "textures" / "entity" / "ship" / "aphelion.png"
ICON_PATH = ASSETS / "textures" / "item" / "aphelion.png"

ATLAS = 1024
PAD = 3
MAGIC = b"SHIP"
VERSION = 1

# Ebenen: 0 = Lack/Metall (undurchsichtig), 1 = Glas (durchscheinend), 2 = Leuchten (additiv, volle Helligkeit)
SOLID, GLASS, GLOW = 0, 1, 2

# Farben (linear 0..1)
ORANGE = np.array([0.80, 0.37, 0.11])
ORANGE_DARK = np.array([0.55, 0.22, 0.07])
SEAM = np.array([0.20, 0.08, 0.03])
GUNMETAL = np.array([0.24, 0.23, 0.22])
BLACK = np.array([0.09, 0.085, 0.08])
YELLOW = np.array([0.93, 0.70, 0.20])
CREAM = np.array([0.90, 0.82, 0.64])
INTERIOR = np.array([0.16, 0.15, 0.15])


# --- Flaechen ---------------------------------------------------------------------------------------------------

@dataclass
class Part:
    """Parametrische Flaeche. surface(u, v) -> (pos[...,3], local[...,3]) fuer Arrays u, v in [0, 1]."""
    name: str
    surface: Callable[[np.ndarray, np.ndarray], tuple[np.ndarray, np.ndarray]]
    res: tuple[int, int]
    layer: int
    paint: Callable[["Sample"], np.ndarray]
    closed_u: bool = True
    alpha: Callable[["Sample"], np.ndarray] | None = None
    outward: tuple[float, float, float] | None = None   # feste Aussenrichtung (flache Deckel)
    rect: tuple[int, int, int, int] = (0, 0, 0, 0)   # x, y, w, h im Atlas
    size: tuple[float, float] = (1.0, 1.0)            # mittlere Laenge in u und v (Bloecke)


@dataclass
class Sample:
    u: np.ndarray
    v: np.ndarray
    pos: np.ndarray      # [..., 3]
    nrm: np.ndarray      # [..., 3]
    local: np.ndarray    # [..., 3] teilbezogene Koordinaten (z. B. Laenge, Quer, Hoch)
    extra: dict = field(default_factory=dict)


def smooth_curve(keys: list[float], values: list[float]) -> Callable[[np.ndarray], np.ndarray]:
    """Kubische Hermite-Interpolation (Catmull-Rom, ungleichmaessig) durch Stuetzwerte; ohne Ueberschwingen an den Enden."""
    k = np.asarray(keys, dtype=np.float64)
    y = np.asarray(values, dtype=np.float64)
    m = np.zeros_like(y)
    for i in range(len(k)):
        if 0 < i < len(k) - 1:
            m[i] = (y[i + 1] - y[i - 1]) / (k[i + 1] - k[i - 1])
        elif i == 0:
            m[i] = (y[1] - y[0]) / (k[1] - k[0])
        else:
            m[i] = (y[-1] - y[-2]) / (k[-1] - k[-2])

    def f(t: np.ndarray) -> np.ndarray:
        t = np.clip(np.asarray(t, dtype=np.float64), k[0], k[-1])
        i = np.clip(np.searchsorted(k, t, side="right") - 1, 0, len(k) - 2)
        h = k[i + 1] - k[i]
        s = (t - k[i]) / h
        h00 = 2 * s ** 3 - 3 * s ** 2 + 1
        h10 = s ** 3 - 2 * s ** 2 + s
        h01 = -2 * s ** 3 + 3 * s ** 2
        h11 = s ** 3 - s ** 2
        return h00 * y[i] + h10 * h * m[i] + h01 * y[i + 1] + h11 * h * m[i + 1]
    return f


def superellipse(theta: np.ndarray, n: float | np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    c = np.cos(theta)
    s = np.sin(theta)
    e = 2.0 / n
    return np.sign(c) * np.abs(c) ** e, np.sign(s) * np.abs(s) ** e


def loft_z(z_keys, half_w, half_h, center_x, center_y, exponent, z_range=None):
    """Rumpfartige Flaeche entlang z: Querschnitt Superellipse. u = rundum (ab rechts unten), v = hinten -> vorn."""
    fw = smooth_curve(z_keys, half_w)
    fh = smooth_curve(z_keys, half_h)
    fx = smooth_curve(z_keys, center_x)
    fy = smooth_curve(z_keys, center_y)
    fn = smooth_curve(z_keys, exponent)
    z0, z1 = z_range if z_range else (z_keys[0], z_keys[-1])

    def surface(u, v):
        z = z0 + (z1 - z0) * v
        theta = 2 * math.pi * u
        a, b = superellipse(theta, fn(z))
        w, h = fw(z), fh(z)
        x = fx(z) + w * a
        y = fy(z) + h * b
        pos = np.stack([x, y, np.broadcast_to(z, x.shape)], axis=-1)
        local = np.stack([z, a, b], axis=-1)
        return pos, local
    return surface


def cap(rim: Callable[[np.ndarray], np.ndarray], center: np.ndarray, depth: np.ndarray | None = None):
    """Deckel: Scheibe vom Mittelpunkt (v = 0) zum Rand rim(u) (v = 1)."""
    def surface(u, v):
        r = rim(u)
        c = np.broadcast_to(center, r.shape)
        pos = c + (r - c) * v[..., None]
        if depth is not None:
            pos = pos + depth * (1 - v[..., None] ** 2)
        local = np.stack([u, v, np.zeros_like(u)], axis=-1)
        return pos, local
    return surface


def ellipsoid(center, radii, v_range=(0.0, 1.0), axis_tilt=0.0):
    """Ellipsoid; v = 0 oben (Pol), v = 1 unten. Optional um x gekippt."""
    cx, cy, cz = center
    rx, ry, rz = radii
    ct, st = math.cos(axis_tilt), math.sin(axis_tilt)

    def surface(u, v):
        phi = math.pi * (v_range[0] + (v_range[1] - v_range[0]) * v)
        theta = 2 * math.pi * u
        x = rx * np.sin(phi) * np.cos(theta)
        y = ry * np.cos(phi)
        z = rz * np.sin(phi) * np.sin(theta)
        y2 = y * ct - z * st
        z2 = y * st + z * ct
        pos = np.stack([cx + x, cy + y2, cz + z2], axis=-1)
        local = np.stack([x / rx, y / ry, z / rz], axis=-1)
        return pos, local
    return surface


def frame_for(tangent: np.ndarray, up_hint=np.array([0.0, 1.0, 0.0])):
    t = tangent / np.linalg.norm(tangent, axis=-1, keepdims=True)
    hint = np.broadcast_to(up_hint, t.shape).copy()
    parallel = np.abs(np.sum(t * hint, axis=-1)) > 0.95
    hint[parallel] = np.array([1.0, 0.0, 0.0])
    n = np.cross(hint, t)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    b = np.cross(t, n)
    return t, n, b


def tube(points: list[tuple[float, float, float]], radii: list[float], keys: list[float] | None = None, smooth=False,
         up_hint=(0.0, 1.0, 0.0)):
    """Rohr entlang eines Pfads (lineare oder glatte Interpolation); Radius 0 an den Enden = geschlossene Kappe."""
    p = np.asarray(points, dtype=np.float64)
    r = np.asarray(radii, dtype=np.float64)
    if keys is None:
        seg = np.linalg.norm(np.diff(p, axis=0), axis=1)
        keys = np.concatenate([[0], np.cumsum(seg)])
        keys = keys / max(keys[-1], 1e-9)
        # doppelte Punkte (flache Kappen) bekommen minimal versetzte Schluessel
        for i in range(1, len(keys)):
            if keys[i] <= keys[i - 1]:
                keys[i] = keys[i - 1] + 1e-4
        keys = keys / keys[-1]
    keys = np.asarray(keys)
    up = np.asarray(up_hint, dtype=np.float64)

    if smooth:
        fx = smooth_curve(list(keys), list(p[:, 0]))
        fy = smooth_curve(list(keys), list(p[:, 1]))
        fz = smooth_curve(list(keys), list(p[:, 2]))
        path = lambda t: np.stack([fx(t), fy(t), fz(t)], axis=-1)  # noqa: E731
    else:
        path = lambda t: np.stack([np.interp(t, keys, p[:, i]) for i in range(3)], axis=-1)  # noqa: E731
    radius = lambda t: np.interp(t, keys, r)  # noqa: E731
    # Tangente ueber den ganzen Pfad (Richtung der Achse) — fuer gerade Rohre stabil, fuer Kappen ohne Knick
    direction = p[-1] - p[0]

    def surface(u, v):
        c = path(v)
        if smooth:
            eps = 1e-3
            tan = path(np.clip(v + eps, 0, 1)) - path(np.clip(v - eps, 0, 1))
            tan = np.where(np.linalg.norm(tan, axis=-1, keepdims=True) < 1e-9, direction, tan)
        else:
            tan = np.broadcast_to(direction, c.shape)
        _, n, b = frame_for(tan, up)
        theta = 2 * math.pi * u
        rr = radius(v)[..., None]
        pos = c + rr * (np.cos(theta)[..., None] * n + np.sin(theta)[..., None] * b)
        local = np.stack([v, np.cos(theta), np.sin(theta)], axis=-1)
        return pos, local
    return surface


def wing_surface(side: float):
    """Fluegelplatte: Querschnitt (Sehne x Dicke) als flache Superellipse, entlang der Spannweite gekippt und gepfeilt."""
    root = np.array([1.42 * side, 1.18, -1.72])
    span = np.array([1.0 * side, -0.42, -0.30])
    span_len = 1.62
    span /= np.linalg.norm(span)
    chord = np.array([0.0, 0.0, 1.0])
    chord -= span * np.dot(chord, span)
    chord /= np.linalg.norm(chord)
    thick = np.cross(span, chord) * side   # zeigt nach oben-aussen
    if thick[1] < 0:
        thick = -thick
    keys = [0.0, 0.25, 0.6, 0.85, 1.0]
    half_chord = smooth_curve(keys, [0.82, 0.86, 0.78, 0.62, 0.42])
    half_thick = smooth_curve(keys, [0.11, 0.115, 0.10, 0.085, 0.07])
    sweep = smooth_curve(keys, [0.0, -0.06, -0.22, -0.36, -0.46])
    lift = smooth_curve(keys, [0.0, 0.03, 0.05, 0.05, 0.04])

    def surface(u, v):
        s = v
        theta = 2 * math.pi * u
        a, b = superellipse(theta, 2.6)
        a = a * side   # Wicklung fuer beide Seiten gleich halten
        c = half_chord(s)[..., None]
        t = half_thick(s)[..., None]
        base = root + span * (span_len * s)[..., None] + chord * sweep(s)[..., None] + thick * lift(s)[..., None]
        pos = base + chord * (a[..., None] * side) * c + thick * b[..., None] * t
        local = np.stack([s, a * side, b], axis=-1)
        return pos, local
    surface.frame = (root, span, chord, thick, span_len, half_chord, sweep, lift, half_thick)  # type: ignore[attr-defined]
    return surface


# --- Malen --------------------------------------------------------------------------------------------------------

def hash3(ix, iy, iz):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def value_noise(p: np.ndarray, scale: float) -> np.ndarray:
    q = p * scale
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)
    out = np.zeros(q.shape[:-1])
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * (f[..., 2] if dz else 1 - f[..., 2])
                out += w * hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz)
    return out


def fbm(p: np.ndarray, scale: float, octaves=3) -> np.ndarray:
    total = np.zeros(p.shape[:-1])
    amp = 0.5
    norm = 0.0
    for o in range(octaves):
        total += amp * value_noise(p + o * 17.3, scale * (2 ** o))
        norm += amp
        amp *= 0.5
    return total / norm


def line_mask(d: np.ndarray, width: float) -> np.ndarray:
    """1 auf der Linie (|d| < width), weich auslaufend."""
    return np.clip(1.0 - np.abs(d) / width, 0.0, 1.0) ** 0.7


def nearest_line(x: np.ndarray, lines: list[float]) -> np.ndarray:
    arr = np.asarray(lines)
    return (x[..., None] - arr).__abs__().min(axis=-1) * np.sign(1)


def shade(base: np.ndarray, s: Sample, gloss=0.25, grime=0.18, ao=True) -> np.ndarray:
    """Gemeinsame Lackbehandlung: Rauschen, Schmutz, Kontaktschatten unten, Glanz oben."""
    n = s.nrm
    col = np.broadcast_to(base, s.pos.shape).copy()
    dirt = fbm(s.pos, 3.2)
    col *= (1.0 - grime * 0.6 + grime * dirt[..., None])
    streak = fbm(s.pos * np.array([1.0, 6.0, 1.0]), 1.4)          # Schlieren (senkrecht)
    col *= (0.96 + 0.08 * streak[..., None])
    if ao:
        under = np.clip(-n[..., 1], 0, 1)
        col *= (1.0 - 0.22 * under[..., None])
    top = np.clip(n[..., 1], 0, 1) ** 3
    col = col + gloss * top[..., None] * (1.0 - col) * 0.55
    return col


def apply_seams(col: np.ndarray, d: np.ndarray, width=0.012, highlight=True) -> np.ndarray:
    """Paneelfuge: dunkle Rille, daneben heller Kantenglanz."""
    groove = line_mask(d, width)
    col = col * (1 - 0.75 * groove[..., None]) + SEAM * 0.75 * groove[..., None]
    if highlight:
        edge = line_mask(np.abs(d) - width * 2.2, width * 0.9)
        col = col + 0.16 * edge[..., None] * (1 - col)
    return col


def rivets(col: np.ndarray, along: np.ndarray, across: np.ndarray, spacing=0.09, offset=0.035, radius=0.011) -> np.ndarray:
    """Nietenreihe parallel zur Fuge (along = Koordinate entlang, across = Abstand zur Fuge)."""
    a = (along / spacing) % 1.0 - 0.5
    da = a * spacing
    for sgn in (-1, 1):
        dc = across - sgn * offset
        r = np.sqrt(da ** 2 + dc ** 2)
        dot = np.clip(1 - r / radius, 0, 1)
        lit = np.clip(1 - np.sqrt((da + radius * 0.35) ** 2 + (dc - radius * 0.35) ** 2) / (radius * 0.6), 0, 1)
        col = col * (1 - 0.45 * dot[..., None]) + ORANGE_DARK * 0.45 * dot[..., None]
        col = col + 0.35 * lit[..., None] * (1 - col)
    return col


# Schriftzug "GADGETRON" als Maske
_TEXT_CACHE: dict[str, np.ndarray] = {}


def text_mask(text: str) -> np.ndarray:
    if text not in _TEXT_CACHE:
        try:
            font = ImageFont.load_default(size=64)
        except TypeError:  # aeltere Pillow-Versionen
            font = ImageFont.load_default()
        probe = Image.new("L", (1, 1))
        box = ImageDraw.Draw(probe).textbbox((0, 0), text, font=font)
        img = Image.new("L", (box[2] - box[0] + 8, box[3] - box[1] + 8), 0)
        ImageDraw.Draw(img).text((4 - box[0], 4 - box[1]), text, fill=255, font=font)
        _TEXT_CACHE[text] = np.asarray(img, dtype=np.float64) / 255.0
    return _TEXT_CACHE[text]


def stamp(mask: np.ndarray, x: np.ndarray, y: np.ndarray) -> np.ndarray:
    """Maske an normierten Koordinaten (0..1, ausserhalb = 0) abtasten (bilinear)."""
    h, w = mask.shape
    inside = (x >= 0) & (x <= 1) & (y >= 0) & (y <= 1)
    fx = np.clip(x, 0, 1) * (w - 1)
    fy = np.clip(y, 0, 1) * (h - 1)
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    x1 = np.minimum(x0 + 1, w - 1)
    y1 = np.minimum(y0 + 1, h - 1)
    tx = fx - x0
    ty = fy - y0
    val = (mask[y0, x0] * (1 - tx) * (1 - ty) + mask[y0, x1] * tx * (1 - ty) + mask[y1, x0] * (1 - tx) * ty
           + mask[y1, x1] * tx * ty)
    return np.where(inside, val, 0.0)


def hex_logo(px: np.ndarray, py: np.ndarray, radius: float) -> tuple[np.ndarray, np.ndarray]:
    """Gadgetron-Sechseck: (Ring-Maske, Innen-Maske) fuer Koordinaten relativ zur Logomitte."""
    ang = np.arctan2(py, px)
    r = np.hypot(px, py)
    sector = (ang + math.pi / 6) % (math.pi / 3) - math.pi / 6
    hexr = r * np.cos(sector) / math.cos(math.pi / 6)   # 1 auf dem Sechseckrand (bei radius)
    ring = np.clip(1 - np.abs(hexr - radius * 0.86) / (radius * 0.12), 0, 1)
    fill = (hexr < radius * 0.74).astype(float)
    # Zahnrad-Symbol innen: Kreis mit Loch und Steg
    gear = ((r < radius * 0.48) & (r > radius * 0.24)).astype(float)
    teeth = ((r < radius * 0.58) & (np.cos(ang * 8) > 0.45) & (r > radius * 0.4)).astype(float)
    bar = ((np.abs(py) < radius * 0.07) & (px > 0) & (px < radius * 0.5)).astype(float)
    inner = np.clip(gear + teeth + bar, 0, 1)
    return ring, fill * (1 - inner)


# --- Teile malen ------------------------------------------------------------------------------------------------

CANOPY_C = np.array([0.0, 1.02, -0.42])
CANOPY_R = np.array([0.62, 0.92, 1.38])


def under_canopy(pos: np.ndarray) -> np.ndarray:
    d = ((pos[..., 0] / CANOPY_R[0]) ** 2 + ((pos[..., 2] - CANOPY_C[2]) / CANOPY_R[2]) ** 2)
    return (d < 0.86) & (pos[..., 1] > 0.85)


def paint_fuselage(s: Sample) -> np.ndarray:
    x, y, z = s.pos[..., 0], s.pos[..., 1], s.pos[..., 2]
    col = np.broadcast_to(ORANGE, s.pos.shape).copy()
    # Paneele: Ringfugen quer, Laengsfugen oben/unten
    rings = [-2.25, -1.62, -0.95, -0.15, 0.55, 1.12]
    d_ring = np.min(np.abs(z[..., None] - np.asarray(rings)), axis=-1)
    b = s.local[..., 2]
    d_long = np.minimum(np.abs(b - 0.42), np.abs(b + 0.30))
    panel = np.floor((z + 3) / 0.6) + 7 * np.round(b * 1.5)
    tint = 0.92 + 0.12 * hash3(panel.astype(np.int64), 3, 5)
    col *= tint[..., None]
    col = shade(col, s)
    col = apply_seams(col, d_ring)
    col = apply_seams(col, d_long * 0.55)
    col = rivets(col, x + y, d_ring)
    # dunkler Streifen an der Seite (Gadgetron-Zierlinie)
    stripe = line_mask(b + 0.05, 0.05) * (z < 0.9)
    col = col * (1 - 0.5 * stripe[..., None]) + ORANGE_DARK * 0.5 * stripe[..., None]
    # Lufteinlass zwischen den Klingen und Cockpit-Innenraum
    intake = np.clip((z - 1.0) / 0.25, 0, 1)
    col = col * (1 - intake[..., None]) + shade(GUNMETAL, s, gloss=0.1)[...] * intake[..., None]
    inner = under_canopy(s.pos)
    if inner.any():
        cockpit = shade(INTERIOR, s, gloss=0.05, grime=0.05)
        # Sitze und Armaturenbrett angedeutet
        seat = ((np.abs(z + 0.9) < 0.22) | (np.abs(z - 0.1) < 0.22)) & (np.abs(x) < 0.3)
        dash = (np.abs(z - 0.62) < 0.08) & (np.abs(x) < 0.45)
        cockpit = np.where(seat[..., None], cockpit * 0.6 + np.array([0.35, 0.12, 0.05]) * 0.4, cockpit)
        lights = dash & (np.sin(x * 60) > 0.6)
        cockpit = np.where(dash[..., None], np.array([0.08, 0.08, 0.09]), cockpit)
        cockpit = np.where(lights[..., None], np.array([0.4, 0.9, 1.0]), cockpit)
        col = np.where(inner[..., None], cockpit, col)
    return col


def paint_spine(s: Sample) -> np.ndarray:
    z = s.pos[..., 2]
    col = shade(ORANGE * 0.97, s)
    col = apply_seams(col, np.min(np.abs(z[..., None] - np.array([-2.3, -1.85, -1.4])), axis=-1))
    # Lueftungsschlitze oben
    b = s.local[..., 2]
    vent = (b > 0.55) & (z > -2.2) & (z < -1.5)
    slats = (np.sin(z * 90) > 0.2) & vent
    col = np.where(vent[..., None], col * 0.55 + GUNMETAL * 0.45, col)
    col = np.where(slats[..., None], BLACK, col)
    return col


def paint_prong(s: Sample) -> np.ndarray:
    z = s.pos[..., 2]
    a = s.local[..., 1]   # quer (-1 innen ... +1 aussen, je Seite gespiegelt)
    b = s.local[..., 2]   # hoch
    side = s.extra["side"]
    outward = a * side
    col = shade(ORANGE, s, gloss=0.3)
    col = apply_seams(col, np.min(np.abs(z[..., None] - np.array([0.75, 1.45, 2.2, 2.85])), axis=-1))
    col = apply_seams(col, (b - 0.62) * 0.35)
    col = rivets(col, z, np.min(np.abs(z[..., None] - np.array([0.75, 1.45, 2.2])), axis=-1), spacing=0.06, offset=0.03)
    # Kuehlrippen oben hinten
    vent = (b > 0.7) & (z > 0.35) & (z < 1.25) & (np.abs(outward) < 0.6)
    col = np.where(vent[..., None], col * 0.4 + GUNMETAL * 0.6, col)
    col = np.where((vent & (np.sin(z * 110) > 0.25))[..., None], BLACK, col)
    # Schriftzug aussen
    mask = text_mask("GADGETRON")
    tx = (z - 1.55) / 1.05
    ty = 1.0 - (b + 0.25) / 0.42
    if side > 0:
        # linke Seite: von aussen gesehen zeigt die Leserichtung zum Heck
        tx = 1.0 - tx
    letters = stamp(mask, tx, ty) * (outward > 0.55)
    col = col * (1 - letters[..., None]) + YELLOW * letters[..., None]
    # Spitze: blanker, abgenutzter Stahl
    tip = np.clip((z - 3.0) / 0.4, 0, 1)
    col = col * (1 - tip[..., None]) + shade(np.array([0.55, 0.5, 0.45]), s, gloss=0.4) * tip[..., None]
    return col


def paint_wing(s: Sample) -> np.ndarray:
    span = s.local[..., 0]
    a = s.local[..., 1]
    b = s.local[..., 2]
    col = shade(ORANGE, s, gloss=0.3)
    # Rahmen: dunkler Rand, Panzerplatte innen
    border = np.maximum(np.abs(a) > 0.86, span > 0.93)
    col = np.where(border[..., None], col * 0.72, col)
    col = apply_seams(col, np.minimum(np.abs(np.abs(a) - 0.86), np.abs(span - 0.93) * 2) * 0.6)
    col = apply_seams(col, np.abs(span - 0.42) * 1.0)
    col = rivets(col, a * 1.4, np.abs(span - 0.42) * 1.6, spacing=0.07)
    # Gadgetron-Sechseck auf der Oberseite
    top = b > 0.3
    px = (span - 0.6) * 1.55
    py = a * 0.8
    ring, fill = hex_logo(px, py, 0.32)
    col = np.where((top & (fill > 0.5))[..., None], col * 0.35 + ORANGE_DARK * 0.65, col)
    col = np.where((top & (ring > 0.3))[..., None], CREAM * (0.8 + 0.2 * ring[..., None]), col)
    inner_mark = top & (np.hypot(px, py) < 0.32 * 0.6) & (fill < 0.5) & (np.hypot(px, py) > 0.32 * 0.2)
    col = np.where(inner_mark[..., None], CREAM * 0.92, col)
    # Unterseite dunkler, Hitzespuren
    col = np.where((b < -0.3)[..., None], col * 0.8, col)
    return col


def paint_metal(base: np.ndarray, gloss=0.35, bands: float = 0.0):
    def paint(s: Sample) -> np.ndarray:
        col = shade(base, s, gloss=gloss, grime=0.12)
        if bands:
            v = s.local[..., 0]
            ring = (np.sin(v * bands * 2 * math.pi) > 0.85)
            col = np.where(ring[..., None], col * 0.6, col)
        return col
    return paint


def paint_spike(s: Sample) -> np.ndarray:
    v = s.local[..., 0]
    col = shade(ORANGE_DARK, s, gloss=0.3)
    t = np.clip((v - 0.45) / 0.35, 0, 1)[..., None]
    return col * (1 - t) + shade(YELLOW, s, gloss=0.5) * t


def paint_nozzle(s: Sample) -> np.ndarray:
    v = s.local[..., 0]
    col = shade(GUNMETAL, s, gloss=0.3)
    ring = (np.abs(v - 0.3) < 0.04) | (np.abs(v - 0.6) < 0.04)
    col = np.where(ring[..., None], col * 0.55, col)
    burn = np.clip((0.25 - v) / 0.25, 0, 1)[..., None]   # Hitzeverfaerbung am Austritt (hinten = v klein)
    return col * (1 - 0.5 * burn) + np.array([0.25, 0.18, 0.30]) * 0.5 * burn


def paint_glow(core: np.ndarray, edge: np.ndarray):
    def paint(s: Sample) -> np.ndarray:
        r = np.clip(s.local[..., 1], 0, 1)   # 0 Mitte -> 1 Rand (Deckel-v)
        t = r[..., None]
        col = core * (1 - t) + edge * t
        flicker = 0.9 + 0.1 * fbm(s.pos * 3, 4.0)[..., None]
        return np.clip(col * flicker, 0, 1)
    return paint


def paint_light(color: np.ndarray):
    def paint(s: Sample) -> np.ndarray:
        up = np.clip(s.local[..., 1], -1, 1)
        return np.clip(color * (0.75 + 0.25 * up[..., None]), 0, 1)
    return paint


def paint_glass(s: Sample) -> np.ndarray:
    lx, ly, lz = s.local[..., 0], s.local[..., 1], s.local[..., 2]
    col = np.broadcast_to(np.array([0.98, 0.68, 0.16]), s.pos.shape).copy()
    # Himmelsspiegelung oben heller, unten satter Bernstein
    col = col * (0.75 + 0.35 * np.clip(ly, 0, 1)[..., None])
    streak = line_mask(lx * 0.7 + lz * 0.7 - 0.25, 0.08) + 0.6 * line_mask(lx * 0.7 + lz * 0.7 - 0.05, 0.03)
    col = col + 0.55 * np.clip(streak, 0, 1)[..., None] * (1 - col)
    return np.clip(col, 0, 1)


def alpha_glass(s: Sample) -> np.ndarray:
    lx, ly, lz = s.local[..., 0], s.local[..., 1], s.local[..., 2]
    streak = line_mask(lx * 0.7 + lz * 0.7 - 0.25, 0.08)
    fresnel = 1 - np.clip(ly, 0, 1)   # flache Blickwinkel spiegeln staerker
    return np.clip(0.42 + 0.25 * fresnel + 0.3 * streak, 0, 0.92)


# --- Schiff aufbauen --------------------------------------------------------------------------------------------

def build_parts() -> list[Part]:
    parts: list[Part] = []

    # Rumpf
    zk = [-2.55, -2.3, -1.9, -1.4, -0.8, -0.2, 0.4, 1.0, 1.45, 1.85]
    fus_w = [0.50, 0.60, 0.66, 0.68, 0.66, 0.62, 0.52, 0.36, 0.20, 0.05]
    fus_h = [0.36, 0.42, 0.46, 0.46, 0.42, 0.38, 0.33, 0.26, 0.17, 0.04]
    fus_x = [0.0] * len(zk)
    fus_y = [0.80, 0.81, 0.80, 0.77, 0.70, 0.64, 0.59, 0.53, 0.48, 0.46]
    fus_n = [3.2, 3.2, 3.0, 3.0, 2.8, 2.7, 2.6, 2.4, 2.2, 2.0]
    fus = loft_z(zk, fus_w, fus_h, fus_x, fus_y, fus_n)
    parts.append(Part("fuselage", fus, (72, 64), SOLID, paint_fuselage))
    rear_rim = lambda u: fus(u, np.zeros_like(u))[0]  # noqa: E731
    parts.append(Part("fuselage_back", cap(rear_rim, np.array([0.0, 0.80, -2.62])), (72, 6), SOLID,
                      paint_metal(GUNMETAL * 0.9, gloss=0.2), outward=(0.0, 0.0, -1.0)))

    # Ruecken-Verkleidung hinter der Kuppel (steigt nach hinten an)
    sk = [-2.75, -2.45, -2.0, -1.5, -1.0, -0.55]
    spine = loft_z(sk, [0.30, 0.36, 0.38, 0.36, 0.28, 0.10], [0.22, 0.30, 0.34, 0.30, 0.22, 0.06], [0.0] * 6,
                   [1.18, 1.24, 1.22, 1.14, 1.04, 0.98], [3.0, 3.0, 2.8, 2.6, 2.4, 2.2])
    parts.append(Part("spine", spine, (48, 40), SOLID, paint_spine))
    parts.append(Part("spine_back", cap(lambda u: spine(u, np.zeros_like(u))[0], np.array([0.0, 1.18, -2.8])), (48, 5),
                      SOLID, paint_metal(GUNMETAL * 0.9, gloss=0.2), outward=(0.0, 0.0, -1.0)))

    # Gabel-Nase: zwei Klingen
    for side, name in ((1.0, "prong_left"), (-1.0, "prong_right")):
        pk = [-0.1, 0.5, 1.15, 1.8, 2.45, 3.05, 3.55]
        surface = loft_z(pk, [0.30, 0.30, 0.27, 0.23, 0.17, 0.09, 0.008], [0.30, 0.28, 0.24, 0.20, 0.15, 0.08, 0.008],
                         [side * v for v in (0.44, 0.47, 0.47, 0.44, 0.39, 0.33, 0.28)],
                         [0.50, 0.48, 0.45, 0.42, 0.38, 0.33, 0.29], [2.6, 2.6, 2.5, 2.4, 2.3, 2.2, 2.0])
        part = Part(name, surface, (40, 64), SOLID, paint_prong)
        part.extra = {"side": side}  # type: ignore[attr-defined]
        parts.append(part)

    # Glaskuppel, Rahmenring und Mittelsteg
    canopy = ellipsoid(tuple(CANOPY_C), tuple(CANOPY_R), v_range=(0.0, 0.56))
    parts.append(Part("canopy", canopy, (72, 28), GLASS, paint_glass, alpha=alpha_glass))
    ring_pts = []
    for i in range(65):
        th = 2 * math.pi * i / 64
        phi = math.pi * 0.53
        ring_pts.append((CANOPY_C[0] + CANOPY_R[0] * math.sin(phi) * math.cos(th) * 1.01,
                         CANOPY_C[1] + CANOPY_R[1] * math.cos(phi),
                         CANOPY_C[2] + CANOPY_R[2] * math.sin(phi) * math.sin(th) * 1.01))
    parts.append(Part("canopy_ring", tube(ring_pts, [0.055] * 65, smooth=True), (12, 96), SOLID,
                      paint_metal(GUNMETAL, gloss=0.4)))
    # Triebwerke: zwei Haupt-Duesen und das Ruecken-Triebwerk
    for side, name in ((1.0, "left"), (-1.0, "right")):
        x = 0.36 * side
        y = 0.70
        parts.append(Part(f"nozzle_{name}", tube([(x, y, -2.98), (x, y, -2.98), (x, y, -2.55), (x, y, -2.2)],
                                                  [0.18, 0.25, 0.27, 0.27]), (32, 12), SOLID, paint_nozzle))
        parts.append(Part(f"engine_glow_{name}", cap(lambda u, x=x, y=y: np.stack(
            [x + 0.18 * np.cos(2 * math.pi * u), y + 0.18 * np.sin(2 * math.pi * u), np.full_like(u, -2.97)], axis=-1),
            np.array([x, y, -2.90])), (32, 6), GLOW, paint_glow(np.array([0.85, 0.97, 1.0]), np.array([0.15, 0.45, 1.0])),
            outward=(0.0, 0.0, -1.0)))
    parts.append(Part("nozzle_top", tube([(0, 1.24, -3.0), (0, 1.24, -3.0), (0, 1.24, -2.6), (0, 1.24, -2.3)],
                                         [0.15, 0.2, 0.22, 0.22]), (28, 10), SOLID, paint_nozzle))
    parts.append(Part("engine_glow_top", cap(lambda u: np.stack(
        [0.15 * np.cos(2 * math.pi * u), 1.24 + 0.15 * np.sin(2 * math.pi * u), np.full_like(u, -2.99)], axis=-1),
        np.array([0.0, 1.24, -2.93])), (28, 6), GLOW, paint_glow(np.array([0.85, 0.97, 1.0]), np.array([0.15, 0.45, 1.0])),
        outward=(0.0, 0.0, -1.0)))

    # Hydraulikarme, Gelenke, Fluegel, Stacheln, Kanonen
    for side, name in ((1.0, "left"), (-1.0, "right")):
        sx = side
        body_a = (0.58 * sx, 0.98, -1.72)
        body_b = (0.60 * sx, 0.70, -1.25)
        root_a = (1.44 * sx, 1.20, -1.95)
        root_b = (1.46 * sx, 1.08, -1.45)
        parts.append(Part(f"arm_{name}", tube([body_a, body_a, root_a, root_a], [0.0, 0.11, 0.11, 0.0]), (20, 12), SOLID,
                          paint_metal(GUNMETAL, gloss=0.45, bands=3)))
        parts.append(Part(f"piston_{name}", tube([body_b, body_b, root_b, root_b], [0.0, 0.07, 0.07, 0.0]), (16, 10), SOLID,
                          paint_metal(np.array([0.62, 0.6, 0.58]), gloss=0.6)))
        for i, (c, r) in enumerate(((body_a, 0.17), (root_a, 0.19), (root_b, 0.12))):
            parts.append(Part(f"joint_{name}_{i}", ellipsoid(c, (r, r, r)), (20, 12), SOLID, paint_metal(BLACK * 1.4, gloss=0.5)))

        wing = wing_surface(side)
        part = Part(f"wing_{name}", wing, (72, 36), SOLID, paint_wing)
        parts.append(part)
        root, span, chord, thick, span_len, half_chord, sweep, lift, half_thick = wing.frame  # type: ignore[attr-defined]

        def on_wing(s_, a_, up):
            s_arr = np.array(s_)
            base = root + span * span_len * s_ + chord * float(sweep(s_arr)) + thick * float(lift(s_arr))
            return base + chord * a_ * float(half_chord(s_arr)) + thick * up * float(half_thick(s_arr))

        # Stacheln an der Hinterkante, nach oben-hinten
        for k, s_ in enumerate((0.3, 0.5, 0.7, 0.88)):
            base = on_wing(s_, -0.62, 0.9)
            direction = thick * 0.75 - chord * 0.65 + span * 0.15
            direction /= np.linalg.norm(direction)
            tip = base + direction * (0.42 - 0.05 * k)
            parts.append(Part(f"spike_{name}_{k}", tube([tuple(base - direction * 0.05), tuple(base - direction * 0.05),
                                                         tuple(tip)], [0.0, 0.085, 0.0], up_hint=tuple(chord)),
                              (14, 8), SOLID, paint_spike))
        # Doppelkanone an der Vorderkante aussen
        mount = on_wing(0.78, 0.55, 0.0)
        fwd = np.array([0.0, 0.0, 1.0])
        housing = [tuple(mount - fwd * 0.25), tuple(mount - fwd * 0.25), tuple(mount + fwd * 0.25), tuple(mount + fwd * 0.25)]
        parts.append(Part(f"gun_housing_{name}", tube(housing, [0.0, 0.14, 0.14, 0.0]), (20, 8), SOLID,
                          paint_metal(GUNMETAL, gloss=0.35, bands=2)))
        for j, off in enumerate((0.075, -0.075)):
            o = thick * off
            start = mount + o + fwd * 0.2
            end = mount + o + fwd * 0.95
            parts.append(Part(f"gun_barrel_{name}_{j}", tube([tuple(start), tuple(start), tuple(end - fwd * 0.08), tuple(end),
                                                              tuple(end)], [0.0, 0.05, 0.05, 0.062, 0.0]),
                              (14, 8), SOLID, paint_metal(BLACK * 1.6, gloss=0.55, bands=4)))
        # Positionslichter: links rot, rechts gruen (wie Flugzeuge), Fluegelspitze
        light = on_wing(0.99, 0.0, 0.0)
        parts.append(Part(f"nav_light_{name}", ellipsoid(tuple(light + span * 0.03), (0.07, 0.07, 0.07)), (12, 8), GLOW,
                          paint_light(np.array([1.0, 0.25, 0.15]) if side > 0 else np.array([0.3, 1.0, 0.4]))))

    # Kufen unter dem Rumpf (Landegestell)
    for side, name in ((1.0, "left"), (-1.0, "right")):
        x = 0.42 * side
        parts.append(Part(f"skid_{name}", tube([(x, 0.18, -1.9), (x, 0.18, -1.9), (x, 0.15, -1.2), (x, 0.15, 0.2), (x, 0.22, 0.55),
                                                 (x, 0.22, 0.55)], [0.0, 0.06, 0.06, 0.06, 0.05, 0.0], smooth=False), (12, 24),
                          SOLID, paint_metal(GUNMETAL, gloss=0.4)))
        for z in (-1.5, 0.0):
            parts.append(Part(f"skid_strut_{name}_{int(z * 10)}", tube([(x, 0.15, z), (x, 0.15, z), (x * 0.8, 0.5, z + 0.1),
                                                                         (x * 0.8, 0.5, z + 0.1)], [0.0, 0.045, 0.045, 0.0]),
                              (10, 6), SOLID, paint_metal(GUNMETAL, gloss=0.4)))

    # Teile mit Seitenangabe fuer die Malfunktion
    for p in parts:
        if not hasattr(p, "extra"):
            p.extra = {}  # type: ignore[attr-defined]
    return parts


# --- Netz, Atlas, Backen --------------------------------------------------------------------------------------------

def evaluate(part: Part, u: np.ndarray, v: np.ndarray):
    pos, local = part.surface(u, v)
    eps = 1e-3
    pu1, _ = part.surface(np.clip(u + eps, 0, 1) if not part.closed_u else (u + eps) % 1.0, v)
    pu0, _ = part.surface(np.clip(u - eps, 0, 1) if not part.closed_u else (u - eps) % 1.0, v)
    pv1, _ = part.surface(u, np.clip(v + eps, 0, 1))
    pv0, _ = part.surface(u, np.clip(v - eps, 0, 1))
    du = pu1 - pu0
    dv = pv1 - pv0
    n = np.cross(du, dv)
    length = np.linalg.norm(n, axis=-1, keepdims=True)
    # entartete Stellen (Pole, Spitzen): Richtung vom Teil-Mittelpunkt
    bad = length[..., 0] < 1e-10
    n = np.where(bad[..., None], 0.0, n / np.maximum(length, 1e-12))
    return pos, local, n


def orient(part: Part) -> float:
    """+1, wenn cross(du, dv) nach aussen zeigt, sonst -1 (geprueft am Gitter gegen den Schwerpunkt)."""
    gu, gv = np.meshgrid(np.linspace(0.02, 0.98, 24), np.linspace(0.05, 0.95, 24))
    pos, _, n = evaluate(part, gu, gv)
    if part.outward is not None:
        return 1.0 if np.sum(n @ np.asarray(part.outward)) >= 0 else -1.0
    centroid = pos.reshape(-1, 3).mean(axis=0)
    score = np.sum((pos - centroid) * n)
    return 1.0 if score >= 0 else -1.0


def mean_lengths(part: Part) -> tuple[float, float]:
    gu, gv = np.meshgrid(np.linspace(0, 1, 33), np.linspace(0, 1, 33))
    pos, _ = part.surface(gu, gv)
    lu = np.linalg.norm(np.diff(pos, axis=1), axis=-1).sum(axis=1).mean()
    lv = np.linalg.norm(np.diff(pos, axis=0), axis=-1).sum(axis=0).mean()
    return max(lu, 0.02), max(lv, 0.02)


def pack(parts: list[Part]) -> float:
    """Rechtecke im Atlas verteilen (Regal-Packer); Rueckgabe: Pixel je Block."""
    for p in parts:
        p.size = mean_lengths(p)
    area = sum(p.size[0] * p.size[1] for p in parts)
    density = math.sqrt(0.70 * ATLAS * ATLAS / area)
    while density > 4:
        x = y = shelf = 0
        ok = True
        placed = []
        for p in sorted(parts, key=lambda q: -q.size[1]):
            w = max(4, int(math.ceil(p.size[0] * density)))
            h = max(4, int(math.ceil(p.size[1] * density)))
            if x + w + PAD > ATLAS:
                x = 0
                y += shelf + PAD
                shelf = 0
            if w + PAD > ATLAS or y + h + PAD > ATLAS:
                ok = False
                break
            placed.append((p, (x + PAD, y + PAD, w, h)))
            x += w + PAD * 2
            shelf = max(shelf, h + PAD)
        if ok:
            for p, rect in placed:
                p.rect = rect
            return density
        density *= 0.95
    raise RuntimeError("Atlas zu klein")


def bake(parts: list[Part]) -> Image.Image:
    rgba = np.zeros((ATLAS, ATLAS, 4), dtype=np.float64)
    for p in parts:
        x0, y0, w, h = p.rect
        # Texelmitten, plus Rand (PAD) mit geklemmten Koordinaten gegen Saeume
        ii = np.arange(-PAD, w + PAD)
        jj = np.arange(-PAD, h + PAD)
        uu = np.clip((ii + 0.5) / w, 0, 1)
        vv = np.clip((jj + 0.5) / h, 0, 1)
        gu, gv = np.meshgrid(uu, vv)
        pos, local, n = evaluate(p, gu, gv)
        n = n * orient(p)
        sample = Sample(gu, gv, pos, n, local, dict(p.extra))  # type: ignore[attr-defined]
        col = np.clip(p.paint(sample), 0, 1)
        alpha = np.ones(gu.shape) if p.alpha is None else np.clip(p.alpha(sample), 0, 1)
        ys = slice(max(0, y0 - PAD), min(ATLAS, y0 + h + PAD))
        xs = slice(max(0, x0 - PAD), min(ATLAS, x0 + w + PAD))
        oy = ys.start - (y0 - PAD)
        ox = xs.start - (x0 - PAD)
        hh = ys.stop - ys.start
        ww = xs.stop - xs.start
        rgba[ys, xs, :3] = col[oy:oy + hh, ox:ox + ww]
        rgba[ys, xs, 3] = alpha[oy:oy + hh, ox:ox + ww]
    img = (np.clip(rgba, 0, 1) ** (1 / 1.0) * 255 + 0.5).astype(np.uint8)
    return Image.fromarray(img, "RGBA")


@dataclass
class MeshPart:
    name: str
    layer: int
    positions: np.ndarray   # [n, 3]
    normals: np.ndarray     # [n, 3]
    uvs: np.ndarray         # [n, 2]
    triangles: np.ndarray   # [m, 3]


def tessellate(p: Part) -> MeshPart:
    nu, nv = p.res
    us = np.linspace(0, 1, nu + 1)
    vs = np.linspace(0, 1, nv + 1)
    gu, gv = np.meshgrid(us, vs)            # [nv+1, nu+1]
    pos, _, n = evaluate(p, gu, gv)
    sign = orient(p)
    n = n * sign
    # Normalen an entarteten Punkten: Mittel der Nachbarn bzw. vom Mittelpunkt weg
    bad = np.linalg.norm(n, axis=-1) < 0.5
    if bad.any():
        centroid = pos.reshape(-1, 3).mean(axis=0)
        fallback = pos - centroid
        fallback /= np.maximum(np.linalg.norm(fallback, axis=-1, keepdims=True), 1e-9)
        n = np.where(bad[..., None], fallback, n)
    x0, y0, w, h = p.rect
    uv = np.stack([(x0 + gu * w) / ATLAS, (y0 + gv * h) / ATLAS], axis=-1)
    idx = np.arange((nu + 1) * (nv + 1)).reshape(nv + 1, nu + 1)
    a = idx[:-1, :-1].ravel()
    b = idx[:-1, 1:].ravel()
    c = idx[1:, 1:].ravel()
    d = idx[1:, :-1].ravel()
    if sign > 0:
        tris = np.concatenate([np.stack([a, b, c], 1), np.stack([a, c, d], 1)])
    else:
        tris = np.concatenate([np.stack([a, c, b], 1), np.stack([a, d, c], 1)])
    # entartete Dreiecke weglassen
    flat = pos.reshape(-1, 3)
    area = np.linalg.norm(np.cross(flat[tris[:, 1]] - flat[tris[:, 0]], flat[tris[:, 2]] - flat[tris[:, 0]]), axis=1)
    tris = tris[area > 1e-9]
    return MeshPart(p.name, p.layer, flat, n.reshape(-1, 3), uv.reshape(-1, 2), tris)


def write_mesh(parts: list[MeshPart]) -> bytes:
    """SHIP v1 (big endian): Kennung, Version, Teilanzahl; je Teil: Name (UTF), Ebene (Byte), Eckenzahl, Ecken
    (3 float Position, 3 byte Normale * 127, 2 float UV), Dreieckszahl, Dreiecke (3 int)."""
    out = bytearray(MAGIC)
    out += struct.pack(">ii", VERSION, len(parts))
    for p in parts:
        name = p.name.encode("utf-8")
        out += struct.pack(">H", len(name)) + name
        out += struct.pack(">bi", p.layer, len(p.positions))
        nrm = np.clip(np.round(p.normals * 127), -127, 127).astype(np.int8)
        for i in range(len(p.positions)):
            out += struct.pack(">fff", *p.positions[i].astype(np.float32))
            out += struct.pack(">bbb", *nrm[i])
            out += struct.pack(">ff", *p.uvs[i].astype(np.float32))
        out += struct.pack(">i", len(p.triangles))
        out += p.triangles.astype(">i4").tobytes()
    return bytes(out)


def read_mesh_summary(data: bytes) -> tuple[int, int]:
    if data[:4] != MAGIC:
        raise ValueError("Kennung fehlt")
    version, count = struct.unpack_from(">ii", data, 4)
    if version != VERSION:
        raise ValueError(f"Version {version}")
    off = 12
    tris = 0
    for _ in range(count):
        (nlen,) = struct.unpack_from(">H", data, off)
        off += 2 + nlen
        _, verts = struct.unpack_from(">bi", data, off)
        off += 5 + verts * (12 + 3 + 8)
        (t,) = struct.unpack_from(">i", data, off)
        off += 4 + t * 12
        tris += t
    if off != len(data):
        raise ValueError("Laenge stimmt nicht")
    return count, tris


# --- Vorschau (Software-Rasterizer) -------------------------------------------------------------------------------

def preview(mesh: list[MeshPart], texture: Image.Image, path: Path | None, yaw=35.0, pitch=22.0, size=900,
            transparent=False, zoom=7.5) -> Image.Image:
    tex = np.asarray(texture, dtype=np.float64) / 255.0
    img = np.zeros((size, size, 3)) + np.array([0.55, 0.68, 0.85])
    zbuf = np.full((size, size), np.inf)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    glass = []

    def project(p):
        x = p[:, 0] * cy + p[:, 2] * sy
        z = -p[:, 0] * sy + p[:, 2] * cy
        y = p[:, 1] - 0.8
        y2 = y * cp - z * sp
        z2 = y * sp + z * cp
        scale = size / zoom
        return np.stack([size / 2 - x * scale, size / 2 - y2 * scale, z2], axis=1)

    def raster(part, blend):
        scr = project(part.positions)
        for t in part.triangles:
            p = scr[t]
            # Rueckseiten weg (Bildschirm: x nach links gespiegelt -> Wicklung beachten)
            area = (p[1, 0] - p[0, 0]) * (p[2, 1] - p[0, 1]) - (p[2, 0] - p[0, 0]) * (p[1, 1] - p[0, 1])
            if area >= 0:
                continue
            xmin, xmax = int(max(0, p[:, 0].min())), int(min(size - 1, p[:, 0].max() + 1))
            ymin, ymax = int(max(0, p[:, 1].min())), int(min(size - 1, p[:, 1].max() + 1))
            if xmin > xmax or ymin > ymax:
                continue
            xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
            d = area
            w0 = ((p[1, 0] - xs) * (p[2, 1] - ys) - (p[2, 0] - xs) * (p[1, 1] - ys)) / d
            w1 = ((p[2, 0] - xs) * (p[0, 1] - ys) - (p[0, 0] - xs) * (p[2, 1] - ys)) / d
            w2 = 1 - w0 - w1
            inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
            if not inside.any():
                continue
            depth = w0 * p[0, 2] + w1 * p[1, 2] + w2 * p[2, 2]
            uv = w0[..., None] * part.uvs[t[0]] + w1[..., None] * part.uvs[t[1]] + w2[..., None] * part.uvs[t[2]]
            nrm = w0[..., None] * part.normals[t[0]] + w1[..., None] * part.normals[t[1]] + w2[..., None] * part.normals[t[2]]
            tx = np.clip((uv[..., 0] * ATLAS).astype(int), 0, ATLAS - 1)
            ty = np.clip((uv[..., 1] * ATLAS).astype(int), 0, ATLAS - 1)
            texel = tex[ty, tx]
            lit = 0.45 + 0.6 * np.clip(nrm @ light, 0, 1)
            sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
            front = inside & (depth < sub)
            if part.layer == GLOW:
                color = texel[..., :3]
            else:
                color = texel[..., :3] * lit[..., None]
            target = img[ymin:ymax + 1, xmin:xmax + 1]
            if blend:
                a = texel[..., 3:4]
                target[front] = (target * (1 - a) + color * a)[front]
            else:
                target[front] = color[front]
                sub[front] = depth[front]

    for part in mesh:
        if part.layer == GLASS:
            glass.append(part)
        else:
            raster(part, False)
    for part in glass:
        raster(part, True)
    rgb = (np.clip(img, 0, 1) * 255).astype(np.uint8)
    if transparent:
        alpha = np.where(np.isfinite(zbuf), 255, 0).astype(np.uint8)
        out = Image.fromarray(np.dstack([rgb, alpha]), "RGBA")
    else:
        out = Image.fromarray(rgb)
    if path is not None:
        out.save(path)
    return out


def icon(mesh: list[MeshPart], texture: Image.Image) -> Image.Image:
    """Item-Symbol 64x64: Schiff schraeg von oben, freigestellt, mit dunklem Rand fuer Lesbarkeit im Inventar."""
    big = preview(mesh, texture, None, yaw=-40.0, pitch=-38.0, size=512, transparent=True, zoom=8.4)
    small = big.resize((64, 64), Image.LANCZOS)
    arr = np.asarray(small).astype(np.float64)
    a = arr[..., 3] / 255.0
    solid = a > 0.35
    grown = solid.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grown |= np.roll(np.roll(solid, dy, 0), dx, 1)
    outline = grown & ~solid
    out = arr.copy()
    out[..., 3] = np.where(solid, 255, 0)
    out[outline] = [40, 18, 8, 255]
    return Image.fromarray(out.astype(np.uint8), "RGBA")


# --- Hauptprogramm ----------------------------------------------------------------------------------------------

def generate() -> tuple[list[MeshPart], Image.Image, float]:
    parts = build_parts()
    density = pack(parts)
    texture = bake(parts)
    mesh = [tessellate(p) for p in parts]
    return mesh, texture, density


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob Netz und Textur vorhanden und lesbar sind")
    parser.add_argument("--preview", type=Path, help="zusaetzlich ein Vorschaubild schreiben")
    parser.add_argument("--yaw", type=float, default=35.0)
    parser.add_argument("--pitch", type=float, default=22.0)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    if args.check:
        try:
            count, tris = read_mesh_summary(MESH_PATH.read_bytes())
            with Image.open(TEXTURE_PATH) as img:
                if img.size != (ATLAS, ATLAS):
                    raise ValueError(f"Textur {img.size}, erwartet {ATLAS}x{ATLAS}")
        except (OSError, ValueError, struct.error) as exc:
            LOG.error("Schiffsnetz fehlerhaft: %s", exc)
            return 1
        LOG.info("Schiffsnetz in Ordnung: %d Teile, %d Dreiecke", count, tris)
        return 0

    mesh, texture, density = generate()
    try:
        MESH_PATH.parent.mkdir(parents=True, exist_ok=True)
        TEXTURE_PATH.parent.mkdir(parents=True, exist_ok=True)
        MESH_PATH.write_bytes(write_mesh(mesh))
        texture.save(TEXTURE_PATH, optimize=True)
        icon(mesh, texture).save(ICON_PATH, optimize=True)
    except OSError as exc:
        LOG.error("konnte nicht schreiben: %s", exc)
        return 1
    LOG.info("Aphelion: %d Teile, %d Dreiecke, Textur %dx%d (%.0f px/Block)", len(mesh),
             sum(len(p.triangles) for p in mesh), ATLAS, ATLAS, density)
    if args.preview:
        preview(mesh, texture, args.preview, args.yaw, args.pitch)
        LOG.info("Vorschau: %s", args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
