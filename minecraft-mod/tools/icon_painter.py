#!/usr/bin/env python3
"""Kleiner Software-Renderer fuer hochwertige Item-Icons (64x64, 4-fach ueberabgetastet).

Formen werden als Masken gezeichnet und bekommen eine Hoehe (Fase aus Distanzfeld, Kuppel, Rauschen) oder feste
Facetten-Normalen. Daraus berechnet der Renderer Licht von oben links: Metall mit Studio-Spiegelung und Glanzpunkt,
Edelstein mit Fresnel-Rand und Innenleuchten, Glas, Gestein und selbstleuchtende Teile. Danach: Verkleinern,
farbige Umrandung, weicher Leuchtschein und optional ein animierter Glanzstreifen (Frames untereinander, .mcmeta).

Alles ist deterministisch (nur numpy/PIL, Zufall aus festen Seeds), damit ``--check`` der Generatoren stabil bleibt.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
from PIL import Image, ImageDraw

R = 256            # Arbeitsaufloesung
OUT = 64           # Ausgabegroesse
LIGHT = np.array([-0.48, -0.62, 0.62])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
HALF = (LIGHT + np.array([0.0, 0.0, 1.0]))
HALF = HALF / np.linalg.norm(HALF)


def rgb(hexcode: str) -> np.ndarray:
    h = hexcode.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255.0


def lerp(a, b, t):
    return a + (b - a) * t


def smooth(e0: float, e1: float, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


# --- Masken und Felder ---------------------------------------------------------------------------------------------

def poly(points) -> np.ndarray:
    img = Image.new("L", (R, R), 0)
    ImageDraw.Draw(img).polygon([(float(x), float(y)) for x, y in points], fill=255)
    return np.asarray(img, dtype=np.float64) / 255.0


def ellipse(cx, cy, rx, ry) -> np.ndarray:
    img = Image.new("L", (R, R), 0)
    ImageDraw.Draw(img).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=255)
    return np.asarray(img, dtype=np.float64) / 255.0


def line(points, width: float) -> np.ndarray:
    img = Image.new("L", (R, R), 0)
    d = ImageDraw.Draw(img)
    pts = [(float(x), float(y)) for x, y in points]
    d.line(pts, fill=255, width=int(round(width)), joint="curve")
    for x, y in pts:
        d.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=255)
    return np.asarray(img, dtype=np.float64) / 255.0


def ring(cx, cy, rx, ry, width) -> np.ndarray:
    img = Image.new("L", (R, R), 0)
    ImageDraw.Draw(img).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), outline=255, width=int(round(width)))
    return np.asarray(img, dtype=np.float64) / 255.0


def rounded(x0, y0, x1, y1, r) -> np.ndarray:
    img = Image.new("L", (R, R), 0)
    ImageDraw.Draw(img).rounded_rectangle((x0, y0, x1, y1), radius=r, fill=255)
    return np.asarray(img, dtype=np.float64) / 255.0


def regular(cx, cy, r, n, rot=0.0, squash=1.0):
    return [(cx + r * math.cos(rot + i * 2 * math.pi / n), cy + r * squash * math.sin(rot + i * 2 * math.pi / n))
            for i in range(n)]


def inset(points, k):
    cx = sum(p[0] for p in points) / len(points)
    cy = sum(p[1] for p in points) / len(points)
    return [(cx + (x - cx) * k, cy + (y - cy) * k) for x, y in points]


def _shift(a: np.ndarray, dy: int, dx: int) -> np.ndarray:
    out = np.zeros_like(a)
    ys = slice(max(dy, 0), R + min(dy, 0))
    yd = slice(max(-dy, 0), R + min(-dy, 0))
    xs = slice(max(dx, 0), R + min(dx, 0))
    xd = slice(max(-dx, 0), R + min(-dx, 0))
    out[ys, xs] = a[yd, xd]
    return out


def distance(mask: np.ndarray, limit: int) -> np.ndarray:
    """Abstand zum Maskenrand in Pixeln (achteckige Naeherung durch abwechselnde Erosion), gekappt bei limit."""
    m = mask > 0.5
    d = np.zeros(m.shape)
    cross = ((0, 1), (1, 0), (0, -1), (-1, 0))
    square = cross + ((1, 1), (1, -1), (-1, 1), (-1, -1))
    for i in range(limit):
        e = m.copy()
        for dy, dx in (square if i % 2 else cross):
            e &= _shift(m, dy, dx)
        d += m
        m = e
        if not m.any():
            break
    return d


def blur(a: np.ndarray, r: int) -> np.ndarray:
    if r <= 0:
        return a
    out = a
    for _ in range(2):
        p = np.pad(out, r, mode="edge")
        c = np.cumsum(np.cumsum(p, 0), 1)
        c = np.pad(c, ((1, 0), (1, 0)))
        k = 2 * r + 1
        out = (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) / (k * k)
    return out


def noise(seed: int, cells: int, octaves: int = 3) -> np.ndarray:
    rng = np.random.default_rng(seed)
    total = np.zeros((R, R))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        n = cells * (2 ** o)
        grid = (rng.random((n, n)) * 255).astype(np.uint8)
        img = Image.fromarray(grid, "L").resize((R, R), Image.BICUBIC)
        total += amp * np.asarray(img, dtype=np.float64) / 255.0
        norm += amp
        amp *= 0.5
    return total / norm


def bevel(mask: np.ndarray, width: int, profile: str = "round") -> np.ndarray:
    d = distance(mask, width) / width
    if profile == "flat":
        h = np.clip(d, 0, 1)
    elif profile == "dome":
        h = np.sqrt(np.clip(d * (2 - d), 0, 1))
    else:
        h = smooth(0, 1, d)
    return h * (mask > 0.5)


def dome(cx, cy, rx, ry) -> np.ndarray:
    y, x = np.mgrid[0:R, 0:R]
    q = 1 - ((x - cx) / rx) ** 2 - ((y - cy) / ry) ** 2
    return np.sqrt(np.clip(q, 0, 1))


def normals_from(h: np.ndarray, strength: float) -> np.ndarray:
    gy, gx = np.gradient(h)
    n = np.dstack([-gx * strength, -gy * strength, np.ones_like(h)])
    return n / np.linalg.norm(n, axis=2, keepdims=True)


def flat_normal(nx, ny, nz=1.0) -> np.ndarray:
    v = np.array([nx, ny, nz], dtype=np.float64)
    return v / np.linalg.norm(v)


# --- Materialien -------------------------------------------------------------------------------------------------------

@dataclass
class Mat:
    kind: str                  # metal, gem, rough, glass, emit, cosmic
    dark: np.ndarray
    mid: np.ndarray
    light: np.ndarray
    spec: float = 1.0
    shine: float = 1.0         # Anteil am animierten Glanzstreifen


def mat(kind, dark, mid, light, spec=1.0, shine=1.0) -> Mat:
    return Mat(kind, rgb(dark), rgb(mid), rgb(light), spec, shine)


def shade(m: Mat, n: np.ndarray, extra: np.ndarray | None = None) -> np.ndarray:
    """Farbe je Pixel fuer Material m bei Normalen n (H,W,3). extra: zusaetzlicher Helligkeitsverlauf 0..1."""
    nl = np.clip(np.tensordot(n, LIGHT, axes=([2], [0])), 0, 1)
    nh = np.clip(np.tensordot(n, HALF, axes=([2], [0])), 0, 1)
    up = -n[..., 1]
    rim = (1 - np.clip(n[..., 2], 0, 1)) ** 2
    t = extra if extra is not None else 0.5
    if m.kind == "metal":
        env = 0.18 + 0.82 * smooth(-0.25, 0.75, up + 0.15 * n[..., 0] * -1)
        env -= 0.28 * np.exp(-((up + 0.05) / 0.12) ** 2)          # dunkles Horizontband
        base = lerp(m.dark[None, None], m.mid[None, None], np.clip(0.35 + 0.65 * nl, 0, 1)[..., None])
        col = lerp(base, m.light[None, None], np.clip(env * 0.55, 0, 1)[..., None])
        col += (m.spec * (nh ** 60) * 1.1)[..., None]
        col += (0.25 * rim)[..., None] * m.light[None, None]
    elif m.kind == "gem":
        body = lerp(m.dark[None, None], m.mid[None, None], np.clip(0.25 + 0.75 * np.asarray(t), 0, 1)[..., None]
                    if np.ndim(t) else np.clip(0.25 + 0.75 * t, 0, 1))
        col = body * (0.55 + 0.6 * nl)[..., None]
        col += (0.55 * rim)[..., None] * m.light[None, None]
        col += (m.spec * (nh ** 90) * 1.6)[..., None]
        col += (0.25 * smooth(0.6, 1.0, up))[..., None] * m.light[None, None]
    elif m.kind == "rough":
        col = lerp(m.dark[None, None], m.mid[None, None], np.clip(0.15 + 0.95 * nl, 0, 1)[..., None])
        col += (0.12 * m.spec * nh ** 12)[..., None] * m.light[None, None]
    elif m.kind == "glass":
        col = m.mid[None, None] * (0.6 + 0.4 * nl)[..., None]
        col += (0.9 * rim)[..., None] * m.light[None, None]
        col += (m.spec * (nh ** 70) * 1.8)[..., None]
    elif m.kind == "cosmic":
        col = lerp(m.dark[None, None], m.mid[None, None], np.clip(np.asarray(t), 0, 1)[..., None]
                   if np.ndim(t) else t)
        col = col * (0.75 + 0.35 * nl)[..., None]
        col += (0.6 * rim)[..., None] * m.light[None, None]
        col += ((nh ** 90) * 1.4)[..., None]
    else:   # emit
        col = lerp(m.mid[None, None], m.light[None, None], np.clip(np.asarray(t), 0, 1)[..., None]
                   if np.ndim(t) else t)
        col = col * np.ones(n.shape)
    return np.clip(col, 0, 1.6)


# --- Leinwand ----------------------------------------------------------------------------------------------------------

@dataclass
class Icon:
    color: np.ndarray = field(default_factory=lambda: np.zeros((R, R, 3)))
    alpha: np.ndarray = field(default_factory=lambda: np.zeros((R, R)))
    emit: np.ndarray = field(default_factory=lambda: np.zeros((R, R, 3)))     # Quelle fuer Leuchtschein
    shine: np.ndarray = field(default_factory=lambda: np.zeros((R, R)))       # wo der Glanzstreifen wirkt
    pulse: np.ndarray = field(default_factory=lambda: np.zeros((R, R)))       # wo das Pulsieren wirkt
    sparkles: list = field(default_factory=list)                              # (x, y, Groesse, Farbe, Phase)
    outline: np.ndarray = field(default_factory=lambda: rgb("140E1C"))

    def put(self, mask: np.ndarray, col: np.ndarray, opacity: float | np.ndarray = 1.0, m: Mat | None = None,
            emissive: float = 0.0):
        a = np.clip(mask * opacity, 0, 1)
        if col.ndim == 1:
            col = np.broadcast_to(col, (R, R, 3))
        self.color = self.color * (1 - a[..., None]) + col * a[..., None]
        self.alpha = self.alpha + a * (1 - self.alpha)
        self.shine = self.shine * (1 - a) + (m.shine if m else 0.0) * a
        self.emit = self.emit * (1 - a[..., None]) + col * (a * emissive)[..., None]
        self.pulse = self.pulse * (1 - a) + emissive * a

    def surface(self, mask, m: Mat, height=None, strength=1.0, normal=None, extra=None, opacity=1.0, emissive=0.0):
        if normal is not None:
            n = np.broadcast_to(normal, (R, R, 3)) if np.ndim(normal) == 1 else normal
        else:
            n = normals_from(height, strength)
        self.put(mask, shade(m, n, extra), opacity, m, emissive)

    def glow(self, mask, color: str | np.ndarray, core: str | np.ndarray = "FFFFFF", opacity=1.0):
        c1 = rgb(color) if isinstance(color, str) else color
        c2 = rgb(core) if isinstance(core, str) else core
        hot = blur(mask, 3)
        col = lerp(c1[None, None], c2[None, None], np.clip(hot * 1.3 - 0.3, 0, 1)[..., None])
        self.put(mask, col, opacity, None, 1.0)

    def sparkle(self, x, y, size, color="FFFFFF", phase=0.0):
        self.sparkles.append((x, y, size, rgb(color), phase))


# --- Ausgabe -----------------------------------------------------------------------------------------------------------

def _star(size_px: float, x: float, y: float, color: np.ndarray, strength: float, col, alpha):
    yy, xx = np.mgrid[0:R, 0:R]
    dx, dy = np.abs(xx - x), np.abs(yy - y)
    arm = np.exp(-dx / (size_px * 0.09)) * np.exp(-dy / size_px) + np.exp(-dy / (size_px * 0.09)) * np.exp(-dx / size_px)
    core = np.exp(-((dx ** 2 + dy ** 2) / (size_px * 0.18) ** 2))
    v = np.clip((arm * 0.9 + core) * strength, 0, 1)
    return col * (1 - v[..., None]) + color[None, None] * v[..., None], alpha + v * (1 - alpha)


def frames(icon: Icon, count: int = 1, halo: str | None = None, halo_strength: float = 0.75,
           shine_color: str = "FFFFFF") -> Image.Image:
    """Rendert count Frames (untereinander) zu einem 64 x 64*count-Bild."""
    out = Image.new("RGBA", (OUT, OUT * count), (0, 0, 0, 0))
    yy, xx = np.mgrid[0:R, 0:R]
    diag = (xx + yy) / (2 * R)                                  # 0 oben links .. 1 unten rechts
    halo_src = None
    if halo:
        e = icon.emit.max(axis=2) if icon.emit.any() else icon.alpha * 0
        halo_src = blur(e, 16) * 2.2
    shine_c = rgb(shine_color)
    for f in range(count):
        p = f / count
        col = icon.color.copy()
        alpha = icon.alpha.copy()
        if count > 1:
            pos = -0.25 + p * 2.2                               # Streifen laeuft in der ersten Haelfte durch
            band = np.exp(-((diag - pos) / 0.055) ** 2) * 0.75 + np.exp(-((diag - pos + 0.11) / 0.02) ** 2) * 0.5
            col += (band * icon.shine)[..., None] * shine_c[None, None]
            pulse = 0.82 + 0.18 * math.cos(2 * math.pi * p)
            col = col * (1 - icon.pulse[..., None] * (1 - pulse))
        for (x, y, size, c, phase) in icon.sparkles:
            s = 1.0 if count == 1 else max(0.0, math.sin(2 * math.pi * (p + phase))) ** 3
            if s > 0.02:
                col, alpha = _star(size * (0.6 + 0.4 * s), x, y, c, s, col, alpha)
        rgba = np.dstack([np.clip(col, 0, 1) * alpha[..., None], alpha])    # vormultipliziert verkleinern
        img = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA").resize((OUT, OUT), Image.BOX)
        arr = np.asarray(img, dtype=np.float64) / 255.0
        a = arr[..., 3]
        c = np.where(a[..., None] > 0, arr[..., :3] / np.maximum(a[..., None], 1e-6), 0)
        solid = a > 0.45
        # farbige Umrandung: dunkle Variante der Nachbarfarbe
        outline = np.zeros_like(solid)
        for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            outline |= np.roll(np.roll(solid, dy, 0), dx, 1)
        outline &= ~solid
        nb = np.zeros_like(c)
        cnt = np.zeros(a.shape)
        for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            s = np.roll(np.roll(solid, dy, 0), dx, 1)
            nb += np.roll(np.roll(c, dy, 0), dx, 1) * s[..., None]
            cnt += s
        nb = nb / np.maximum(cnt, 1)[..., None]
        edge = icon.outline[None, None] * 0.55 + nb * 0.25
        c = np.where(outline[..., None], edge, c)
        a = np.where(solid | outline, 1.0, 0.0)
        # Kanten-Schaerfung: obere/linke Innenkante leicht aufhellen
        inner_top = solid & ~np.roll(solid, 1, 0)
        c = np.where(inner_top[..., None], np.clip(c * 1.12 + 0.04, 0, 1), c)
        if halo_src is not None:
            h = np.asarray(Image.fromarray((np.clip(halo_src, 0, 1) * 255).astype(np.uint8), "L")
                           .resize((OUT, OUT), Image.BOX), dtype=np.float64) / 255.0
            pulse = 1.0 if count == 1 else 0.75 + 0.25 * math.cos(2 * math.pi * f / count)
            ha = np.clip(h * halo_strength * 1.6 * pulse, 0, 0.7) * (a == 0)
            hc = rgb(halo)
            c = np.where((a == 0)[..., None], hc[None, None], c)
            a = np.where(a == 0, ha, a)
        frame = np.dstack([np.clip(c, 0, 1), a])
        out.paste(Image.fromarray((frame * 255).round().astype(np.uint8), "RGBA"), (0, OUT * f))
    return out


# --- Bausteine fuer Formen ---------------------------------------------------------------------------------------------

def prism(icon: Icon, base, angle_deg, length, width, m: Mat, glow_edge: str | None = None):
    """Sechseckiges Kristallprisma (Seitenansicht) mit Spitze: drei Laengsflaechen, drei Spitzenfacetten."""
    a = math.radians(angle_deg)
    ax, ay = math.sin(a), -math.cos(a)        # Achse (nach oben bei 0 Grad)
    px, py = -ay, ax                          # quer (nach rechts)
    bx, by = base
    tip_len = width * 0.9
    body = length - tip_len
    w = width / 2

    def pt(u, v):                             # u entlang Achse, v quer (-1..1)
        return (bx + ax * u + px * v * w, by + ay * u + py * v * w)
    rows = (-1.0, -0.42, 0.42, 1.0)
    faces = []
    # Laengsflaechen
    normals = (flat_normal(-0.85 * px, -0.85 * py, 0.55), flat_normal(0.15 * ax * 0.0, 0, 1.0),
               flat_normal(0.85 * px, 0.85 * py, 0.55))
    tipn = (flat_normal(-0.7 * px + 0.55 * ax, -0.7 * py + 0.55 * ay, 0.5), flat_normal(0.6 * ax, 0.6 * ay, 0.8),
            flat_normal(0.7 * px + 0.55 * ax, 0.7 * py + 0.55 * ay, 0.5))
    y, x = np.mgrid[0:R, 0:R]
    along = ((x - bx) * ax + (y - by) * ay) / max(length, 1)
    for i in range(3):
        quad = [pt(0, rows[i]), pt(0, rows[i + 1]), pt(body, rows[i + 1]), pt(body, rows[i])]
        faces.append((poly(quad), normals[i]))
        tri = [pt(body, rows[i]), pt(body, rows[i + 1]), pt(length, 0)]
        faces.append((poly(tri), tipn[i]))
    for mask, n in faces:
        icon.surface(mask, m, normal=n, extra=np.clip(along, 0, 1))
    if glow_edge:
        edges = np.zeros((R, R))
        for v in (rows[1], rows[2]):
            edges = np.maximum(edges, line([pt(2, v), pt(body, v), pt(length, 0)], 2.2))
        edges = np.maximum(edges, line([pt(body, -1), pt(body, 1)], 1.6))
        icon.put(edges, rgb(glow_edge), 0.55, m)
    return pt(length, 0)


def bolt(icon: Icon, x, y, r, m: Mat):
    """Sechskant-Schraube mit Fase."""
    mask = poly(regular(x, y, r, 6, math.pi / 6))
    icon.surface(mask, m, height=bevel(mask, max(2, int(r * 0.6))), strength=r * 0.9)
    icon.surface(ellipse(x, y, r * 0.38, r * 0.38), m, height=-dome(x, y, r * 0.38, r * 0.38), strength=r * 0.6)
