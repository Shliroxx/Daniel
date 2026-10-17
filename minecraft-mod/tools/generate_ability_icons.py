#!/usr/bin/env python3
"""Eigene, farbige Symbole fuer jede Alien-Faehigkeit (64x64) — jedes zeigt, was die Faehigkeit tut.

Jedes Symbol wird aus Motiven zusammengesetzt (Faust, Flamme, Kristall, Schallwellen, Netz, Tornado …). Jedes Motiv
ist eine Maske, die eingefaerbt (Verlauf hell → Grundfarbe → dunkel), mit dunkler Kontur, Glanzkante und weichem
Leuchten versehen wird. Gezeichnet wird 4-fach ueberabgetastet (256 px) und weich auf 64 px verkleinert.

Ausgabe: assets/kingdomomnitrix/textures/gui/icon/ability_art/<faehigkeit>.png
Aufruf aus minecraft-mod/:
    python tools/generate_ability_icons.py                  # schreiben
    python tools/generate_ability_icons.py --check          # alle vorhanden?
    python tools/generate_ability_icons.py --sheet x.png    # zusaetzlich Uebersicht
"""
from __future__ import annotations

import argparse
import json
import logging
import math
import sys
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("ability_icons")
ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
OUT = ROOT / "assets" / "kingdomomnitrix" / "textures" / "gui" / "icon" / "ability_art"
ALIENS = ROOT / "data" / "kingdomomnitrix" / "kingdomomnitrix" / "alien"
S = 256          # Arbeitsgroesse
SIZE = 64        # Ausgabe


def rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# --- Masken-Motive (Koordinaten 0..256) ---------------------------------------------------------------------------

def _mask():
    m = Image.new("L", (S, S), 0)
    return m, ImageDraw.Draw(m)


def box(a, b):
    """Sortiertes Rechteck aus zwei (ggf. gedrehten) Punkten."""
    return [min(a[0], b[0]), min(a[1], b[1]), max(a[0], b[0]), max(a[1], b[1])]


def poly(points, scale=1.0, dx=0, dy=0, rot=0.0, cx=128, cy=128):
    out = []
    c, s = math.cos(rot), math.sin(rot)
    for x, y in points:
        x, y = (x - cx) * scale, (y - cy) * scale
        out.append((cx + x * c - y * s + dx, cy + x * s + y * c + dy))
    return out


def m_flame(dx=0, dy=0, scale=1.0, rot=0.0):
    m, d = _mask()
    d.polygon(poly([(128, 28), (150, 70), (176, 60), (184, 120), (200, 110), (196, 170), (176, 214), (128, 232), (80, 214),
                    (60, 170), (56, 112), (74, 126), (82, 78), (104, 96)], scale, dx, dy, rot), fill=255)
    return m


def m_flame_core(dx=0, dy=0, scale=1.0):
    m, d = _mask()
    d.polygon(poly([(128, 110), (146, 150), (152, 186), (128, 214), (104, 186), (110, 150)], scale, dx, dy), fill=255)
    return m


def m_circle(cx=128, cy=128, r=60):
    m, d = _mask()
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    return m


def m_ring(cx=128, cy=128, r=80, w=14, start=0, end=360):
    m, d = _mask()
    d.arc([cx - r, cy - r, cx + r, cy + r], start, end, fill=255, width=w)
    return m


def m_burst(cx=128, cy=128, r1=110, r0=40, n=10, rot=0.0):
    m, d = _mask()
    pts = []
    for i in range(n * 2):
        a = rot + i * math.pi / n
        r = r1 if i % 2 == 0 else r0
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    d.polygon(pts, fill=255)
    return m


def m_star(cx=128, cy=128, r=90, rot=-math.pi / 2):
    return m_burst(cx, cy, r, r * 0.42, 5, rot)


def m_crystal(cx=128, cy=128, h=200, w=70, rot=0.0):
    m, d = _mask()
    d.polygon(poly([(cx, cy - h / 2), (cx + w / 2, cy - h / 6), (cx + w / 2.6, cy + h / 2.4), (cx, cy + h / 2),
                    (cx - w / 2.6, cy + h / 2.4), (cx - w / 2, cy - h / 6)], 1.0, 0, 0, rot, cx, cy), fill=255)
    return m


def m_spike(x, base_y, h, w):
    m, d = _mask()
    d.polygon([(x - w / 2, base_y), (x, base_y - h), (x + w / 2, base_y)], fill=255)
    return m


def m_fist(dx=0, dy=0, scale=1.0, rot=0.0):
    """Geballte Faust: Handruecken, vier Knoechel, Daumen."""
    m, d = _mask()
    d.polygon(poly([(66, 110), (190, 104), (198, 180), (172, 214), (92, 216), (60, 176)], scale, dx, dy, rot), fill=255)
    for i in range(4):
        (kx, ky), = poly([(84 + i * 30, 96)], scale, dx, dy, rot)
        r = 19 * scale
        d.ellipse([kx - r, ky - r, kx + r, ky + r], fill=255)
    d.polygon(poly([(60, 150), (40, 128), (54, 108), (110, 150), (104, 176)], scale, dx, dy, rot), fill=255)
    return m


def fist_lines(dx=0, dy=0, scale=1.0, rot=0.0):
    """Fugen zwischen den Fingern und Daumenkante (dunkel ueber die Faust gelegt)."""
    m, d = _mask()
    for i in range(1, 4):
        d.line(poly([(70 + i * 30, 90), (70 + i * 30, 132)], scale, dx, dy, rot), fill=255, width=max(3, int(7 * scale)))
    d.line(poly([(58, 132), (112, 160)], scale, dx, dy, rot), fill=255, width=max(3, int(7 * scale)))
    return m


def fist(color, dx=0, dy=0, scale=1.0, rot=0.0, glow=None, glow_alpha=0.6):
    """Faust als zwei Schichten: Form mit Verlauf, darueber dunkle Fugen."""
    return [layer_color(m_fist(dx, dy, scale, rot), color, glow=glow, glow_alpha=glow_alpha),
            layer_color(fist_lines(dx, dy, scale, rot), mix(color, (0, 0, 0), 0.6), outline=None)]


def m_arrow(x0, y0, x1, y1, w=26, head=50):
    m, d = _mask()
    a = math.atan2(y1 - y0, x1 - x0)
    bx, by = x1 - math.cos(a) * head, y1 - math.sin(a) * head
    nx, ny = -math.sin(a), math.cos(a)
    d.polygon([(x0 + nx * w / 2, y0 + ny * w / 2), (bx + nx * w / 2, by + ny * w / 2), (bx + nx * head * 0.75, by + ny * head * 0.75),
               (x1, y1), (bx - nx * head * 0.75, by - ny * head * 0.75), (bx - nx * w / 2, by - ny * w / 2),
               (x0 - nx * w / 2, y0 - ny * w / 2)], fill=255)
    return m


def m_speedlines(y0=70, y1=190, x0=20, x1=150, n=4, w=12):
    m, d = _mask()
    for i in range(n):
        y = y0 + (y1 - y0) * i / max(1, n - 1)
        off = (i % 2) * 30
        d.rounded_rectangle([x0 + off, y - w / 2, x1 - off * 0.5, y + w / 2], radius=w / 2, fill=255)
    return m


def m_bolt(dx=0, dy=0, scale=1.0, rot=0.0):
    m, d = _mask()
    d.polygon(poly([(150, 20), (70, 140), (124, 140), (96, 236), (190, 104), (134, 104), (170, 20)], scale, dx, dy, rot), fill=255)
    return m


def m_snowflake(cx=128, cy=128, r=100, w=16):
    m, d = _mask()
    for i in range(6):
        a = i * math.pi / 3
        ex, ey = cx + math.cos(a) * r, cy + math.sin(a) * r
        d.line([(cx, cy), (ex, ey)], fill=255, width=w)
        for f in (0.55, 0.8):
            px, py = cx + math.cos(a) * r * f, cy + math.sin(a) * r * f
            for side in (-1, 1):
                b = a + side * 0.7
                d.line([(px, py), (px + math.cos(b) * r * 0.25, py + math.sin(b) * r * 0.25)], fill=255, width=int(w * 0.8))
    d.ellipse([cx - w, cy - w, cx + w, cy + w], fill=255)
    return m


def m_beam(y=128, x0=40, x1=246, w=40):
    m, d = _mask()
    d.rounded_rectangle([x0, y - w / 2, x1, y + w / 2], radius=w / 2, fill=255)
    return m


def m_shield(dx=0, dy=0, scale=1.0):
    m, d = _mask()
    d.polygon(poly([(128, 26), (210, 58), (204, 140), (128, 230), (52, 140), (46, 58)], scale, dx, dy), fill=255)
    return m


def m_heart(dx=0, dy=0, scale=1.0):
    m, d = _mask()
    p = lambda pts: poly(pts, scale, dx, dy)
    a = p([(52, 64), (132, 144)])
    b = p([(124, 64), (204, 144)])
    d.ellipse(box(*a), fill=255)
    d.ellipse(box(*b), fill=255)
    d.polygon(p([(56, 118), (128, 222), (200, 118), (128, 96)]), fill=255)
    return m


def m_plus(cx=190, cy=190, r=40, w=22):
    m, d = _mask()
    d.rectangle([cx - w / 2, cy - r, cx + w / 2, cy + r], fill=255)
    d.rectangle([cx - r, cy - w / 2, cx + r, cy + w / 2], fill=255)
    return m


def m_eye(cx=128, cy=128, w=200, h=110):
    m, d = _mask()
    d.polygon([(cx - w / 2, cy)] + [(cx - w / 2 + w * t / 20, cy - math.sin(math.pi * t / 20) * h / 2) for t in range(1, 20)] + [(cx + w / 2, cy)]
              + [(cx + w / 2 - w * t / 20, cy + math.sin(math.pi * t / 20) * h / 2) for t in range(1, 20)], fill=255)
    return m


def m_claws(rot=0.0, n=3):
    m, d = _mask()
    for i in range(n):
        off = (i - (n - 1) / 2) * 44
        d.polygon(poly([(60 + off, 40), (84 + off, 40), (200 + off, 216), (176 + off, 222)], 1.0, 0, 0, rot), fill=255)
    return m


def m_web(cx=128, cy=128, r=110, w=7):
    m, d = _mask()
    for i in range(8):
        a = i * math.pi / 4
        d.line([(cx, cy), (cx + math.cos(a) * r, cy + math.sin(a) * r)], fill=255, width=w)
    for k in (0.35, 0.65, 0.95):
        pts = [(cx + math.cos(i * math.pi / 4) * r * k, cy + math.sin(i * math.pi / 4) * r * k) for i in range(9)]
        d.line(pts, fill=255, width=w)
    return m


def m_waves(cx=60, cy=128, n=3, start=-50, end=50, w=16, gap=38):
    m, d = _mask()
    for i in range(n):
        r = 40 + i * gap
        d.arc([cx - r, cy - r, cx + r, cy + r], start, end, fill=255, width=w)
    return m


def m_drop(cx=128, cy=140, r=70):
    m, d = _mask()
    d.ellipse([cx - r, cy - r * 0.6, cx + r, cy + r * 1.1], fill=255)
    d.polygon([(cx - r * 0.75, cy - r * 0.1), (cx, cy - r * 1.7), (cx + r * 0.75, cy - r * 0.1)], fill=255)
    return m


def m_tornado(cx=128):
    m, d = _mask()
    for i in range(6):
        y = 40 + i * 34
        w = 180 - i * 26
        d.rounded_rectangle([cx - w / 2 + (i % 2) * 10, y, cx + w / 2 + (i % 2) * 10, y + 22], radius=11, fill=255)
    return m


def m_cloud(cx=128, cy=128, sc=1.0):
    m, d = _mask()
    for x, y, r in [(-50, 10, 42), (0, -18, 56), (50, 6, 46), (-20, 30, 44), (30, 32, 42)]:
        d.ellipse([cx + x * sc - r * sc, cy + y * sc - r * sc, cx + x * sc + r * sc, cy + y * sc + r * sc], fill=255)
    return m


def m_ball(cx=128, cy=128, r=90):
    return m_circle(cx, cy, r)


def ball_plates(cx=128, cy=128, r=90):
    m, d = _mask()
    d.arc([cx - r, cy - r * 0.45, cx + r, cy + r * 0.45], 0, 180, fill=255, width=10)
    d.line([(cx, cy - r), (cx, cy + r)], fill=255, width=10)
    return m


def m_atom(cx=128, cy=128, r=104, w=12):
    m, d = _mask()
    for k in range(3):
        img = Image.new("L", (S, S), 0)
        ImageDraw.Draw(img).ellipse([cx - r, cy - r * 0.38, cx + r, cy + r * 0.38], outline=255, width=w)
        m = ImageChops.lighter(m, img.rotate(k * 60, center=(cx, cy), resample=Image.BICUBIC))
    d = ImageDraw.Draw(m)
    d.ellipse([cx - 26, cy - 26, cx + 26, cy + 26], fill=255)
    return m


def m_clock(cx=128, cy=128, r=96):
    m, d = _mask()
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=255, width=16)
    d.line([(cx, cy), (cx, cy - r * 0.6)], fill=255, width=14)
    d.line([(cx, cy), (cx + r * 0.45, cy + r * 0.2)], fill=255, width=14)
    return m


def m_hourglass():
    m, d = _mask()
    d.polygon([(64, 30), (192, 30), (138, 128), (192, 226), (64, 226), (118, 128)], fill=255)
    return m


def m_boulder(cx=128, cy=128, r=90):
    m, d = _mask()
    pts = [(cx + math.cos(a) * r * f, cy + math.sin(a) * r * f) for a, f in
           zip([i * math.tau / 9 for i in range(9)], [1.0, 0.86, 0.97, 0.8, 1.0, 0.88, 0.95, 0.82, 0.92])]
    d.polygon(pts, fill=255)
    return m


def m_ground(y=196, h=60):
    m, d = _mask()
    d.rectangle([0, y, S, y + h], fill=255)
    return m


def m_cracks(cx=128, y=200):
    m, d = _mask()
    for a in (-2.6, -2.0, -1.2, -0.5):
        x, yy = cx, y
        pts = [(x, yy)]
        for k in range(4):
            x += math.cos(a + (k % 2) * 0.4) * 28
            yy += abs(math.sin(a)) * 6 + 4
            pts.append((x, yy))
        d.line(pts, fill=255, width=8)
        d.line([(cx * 2 - p[0], p[1]) for p in pts], fill=255, width=8)
    return m


def m_debris(cx=128, cy=120, spread=100, n=6, size=22):
    m, d = _mask()
    for i in range(n):
        a = -math.pi / 2 + (i - (n - 1) / 2) * 0.5
        x, y = cx + math.cos(a) * spread * (0.6 + 0.4 * (i % 2)), cy + math.sin(a) * spread * (0.6 + 0.4 * (i % 2))
        d.polygon([(x - size, y), (x - size * 0.2, y - size), (x + size, y - size * 0.3), (x + size * 0.4, y + size)], fill=255)
    return m


def m_tentacle(rot=0.0, w=26):
    m, d = _mask()
    pts = [(40 + t * 9, 200 - t * 6 + math.sin(t * 0.7) * 26) for t in range(20)]
    for i, (x, y) in enumerate(pts):
        r = w * (1 - i / 24)
        d.ellipse([x - r / 2, y - r / 2, x + r / 2, y + r / 2], fill=255)
    return m.rotate(math.degrees(rot), center=(128, 128))


def m_ghost(dx=0, dy=0, scale=1.0):
    m, d = _mask()
    p = lambda pts: poly(pts, scale, dx, dy)
    e = p([(60, 30), (196, 166)])
    d.ellipse(box(*e), fill=255)
    d.polygon(p([(60, 98), (196, 98), (196, 220), (172, 196), (150, 226), (128, 196), (106, 226), (84, 196), (60, 220)]), fill=255)
    return m


def ghost_eyes(dx=0, dy=0, scale=1.0):
    m, d = _mask()
    for x in (100, 156):
        e = poly([(x - 14, 84), (x + 14, 124)], scale, dx, dy)
        d.ellipse(box(*e), fill=255)
    return m


def m_chip():
    m, d = _mask()
    d.rounded_rectangle([70, 70, 186, 186], radius=12, fill=255)
    for i in range(4):
        x = 86 + i * 28
        for y0, y1 in ((40, 70), (186, 216)):
            d.rectangle([x - 6, y0, x + 6, y1], fill=255)
            d.rectangle([y0, x - 6, y1, x + 6], fill=255)
    return m


def chip_core():
    m, d = _mask()
    d.rounded_rectangle([100, 100, 156, 156], radius=6, fill=255)
    return m


def m_gear(cx=128, cy=128, r=90, teeth=8):
    m, d = _mask()
    d.ellipse([cx - r * 0.78, cy - r * 0.78, cx + r * 0.78, cy + r * 0.78], fill=255)
    for i in range(teeth):
        a = i * math.tau / teeth
        img = Image.new("L", (S, S), 0)
        ImageDraw.Draw(img).rectangle([cx - 16, cy - r, cx + 16, cy - r * 0.6], fill=255)
        m = ImageChops.lighter(m, img.rotate(math.degrees(a), center=(cx, cy)))
    d = ImageDraw.Draw(m)
    d.ellipse([cx - r * 0.3, cy - r * 0.3, cx + r * 0.3, cy + r * 0.3], fill=0)
    return m


def m_jaw():
    m, d = _mask()
    d.pieslice([30, 40, 226, 236], 200, 340, fill=255)
    d.pieslice([30, 20, 226, 216], 20, 160, fill=255)
    return m


def jaw_teeth():
    m, d = _mask()
    for i in range(6):
        x = 62 + i * 26
        d.polygon([(x - 11, 84), (x, 116), (x + 11, 84)], fill=255)
        d.polygon([(x - 11, 172), (x, 140), (x + 11, 172)], fill=255)
    return m


def m_wing(left=True):
    m, d = _mask()
    pts = [(128, 128), (30, 50), (16, 110), (40, 150), (24, 190), (90, 170), (128, 150)]
    if not left:
        pts = [(256 - x, y) for x, y in pts]
    d.polygon(pts, fill=255)
    return m


def m_roots():
    m, d = _mask()
    for sx, a in [(60, -1.2), (110, -1.5), (160, -1.8), (200, -1.4)]:
        x, y = sx, 236
        pts = [(x, y)]
        for k in range(6):
            x += math.cos(a + math.sin(k * 1.3) * 0.5) * 30
            y += math.sin(a) * 30
            pts.append((x, y))
        d.line(pts, fill=255, width=16 - 0)
    return m


def m_leaf(cx=128, cy=128, r=90, rot=0.7):
    m, d = _mask()
    d.ellipse([cx - r * 0.5, cy - r, cx + r * 0.5, cy + r], fill=255)
    return m.rotate(math.degrees(rot), center=(cx, cy))


def m_wave(y=150):
    m, d = _mask()
    pts = [(x, y + math.sin(x / 26) * 18) for x in range(0, S + 1, 4)]
    d.polygon(pts + [(S, S), (0, S)], fill=255)
    d.pieslice([90, y - 110, 250, y + 50], 180, 300, fill=255)
    return m


def m_spiral(cx=128, cy=128, turns=2.4, r=104, w=16):
    m, d = _mask()
    pts = [(cx + math.cos(t) * r * t / (turns * math.tau), cy + math.sin(t) * r * t / (turns * math.tau)) for t in
           [i * 0.12 for i in range(int(turns * math.tau / 0.12))]]
    d.line(pts, fill=255, width=w, joint="curve")
    return m


def m_hex(cx=128, cy=128, r=100, w=16, fill=False):
    m, d = _mask()
    pts = [(cx + math.cos(i * math.pi / 3 + math.pi / 6) * r, cy + math.sin(i * math.pi / 3 + math.pi / 6) * r) for i in range(6)]
    if fill:
        d.polygon(pts, fill=255)
    else:
        d.line(pts + [pts[0]], fill=255, width=w, joint="curve")
    return m


def m_prism():
    m, d = _mask()
    d.polygon([(128, 30), (210, 190), (46, 190)], fill=255)
    return m


def m_rays(cx=128, cy=128, n=7, start=-0.6, end=0.6, r0=60, r1=124, w=12):
    m, d = _mask()
    for i in range(n):
        a = start + (end - start) * i / max(1, n - 1)
        d.line([(cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * r1, cy + math.sin(a) * r1)], fill=255, width=w)
    return m


def m_brain():
    m, d = _mask()
    for x, y, r in [(90, 110, 50), (140, 96, 54), (176, 130, 46), (110, 150, 46), (152, 156, 44)]:
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    return m


def brain_folds():
    m, d = _mask()
    d.arc([70, 80, 150, 160], 200, 340, fill=255, width=8)
    d.arc([110, 110, 190, 190], 180, 330, fill=255, width=8)
    d.line([(128, 60), (128, 200)], fill=255, width=8)
    return m


def m_mouth():
    m, d = _mask()
    d.ellipse([56, 70, 200, 200], fill=255)
    return m


def m_figure(dx=0, dy=0, scale=1.0):
    """Einfache Gestalt (Klon, Besessener, Ziel)."""
    m, d = _mask()
    p = lambda pts: poly(pts, scale, dx, dy)
    h = p([(104, 30), (152, 78)])
    d.ellipse(box(*h), fill=255)
    d.polygon(p([(92, 86), (164, 86), (178, 160), (156, 160), (150, 232), (106, 232), (100, 160), (78, 160)]), fill=255)
    return m


def m_reticle(cx=128, cy=128, r=90):
    m, d = _mask()
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=255, width=12)
    for a in range(4):
        ang = a * math.pi / 2
        d.line([(cx + math.cos(ang) * r * 0.4, cy + math.sin(ang) * r * 0.4), (cx + math.cos(ang) * r * 1.2, cy + math.sin(ang) * r * 1.2)],
               fill=255, width=12)
    return m


def m_meteor(cx=170, cy=86, r=40):
    m, d = _mask()
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    d.polygon([(cx - r * 0.7, cy - r * 0.7), (cx - 150, cy + 150), (cx + r * 0.7, cy + r * 0.7)], fill=255)
    return m


def m_footprint():
    m, d = _mask()
    d.ellipse([80, 110, 176, 236], fill=255)
    for x, y in [(70, 72), (108, 46), (148, 46), (186, 72)]:
        d.ellipse([x - 20, y - 22, x + 20, y + 22], fill=255)
    return m


def m_tail():
    m, d = _mask()
    d.arc([30, 30, 226, 226], 100, 330, fill=255, width=34)
    d.polygon([(170, 40), (226, 50), (196, 90)], fill=255)
    return m


def m_cocoon():
    m, d = _mask()
    d.ellipse([70, 30, 186, 226], fill=255)
    return m


def cocoon_lines():
    m, d = _mask()
    for i in range(5):
        y = 60 + i * 34
        d.line([(76, y), (180, y + 18)], fill=255, width=8)
    return m


def m_crown():
    m, d = _mask()
    d.polygon([(40, 190), (52, 80), (96, 140), (128, 60), (160, 140), (204, 80), (216, 190)], fill=255)
    return m


def m_skull():
    m, d = _mask()
    d.ellipse([56, 36, 200, 176], fill=255)
    d.rounded_rectangle([88, 150, 168, 210], radius=10, fill=255)
    return m


def skull_holes():
    m, d = _mask()
    d.ellipse([80, 90, 120, 132], fill=255)
    d.ellipse([136, 90, 176, 132], fill=255)
    d.polygon([(128, 140), (118, 162), (138, 162)], fill=255)
    return m


def m_quills():
    m, d = _mask()
    for i in range(7):
        a = -math.pi + 0.35 + i * (math.pi - 0.7) / 6
        x0, y0 = 128 + math.cos(a) * 40, 200 + math.sin(a) * 40
        x1, y1 = 128 + math.cos(a) * 200, 200 + math.sin(a) * 200
        d.polygon([(x0 + math.sin(a) * 8, y0 - math.cos(a) * 8), (x1, y1), (x0 - math.sin(a) * 8, y0 + math.cos(a) * 8)], fill=255)
    return m


def m_nose():
    m, d = _mask()
    d.ellipse([60, 80, 196, 200], fill=255)
    return m


def nose_holes():
    m, d = _mask()
    d.ellipse([86, 124, 118, 166], fill=255)
    d.ellipse([138, 124, 170, 166], fill=255)
    return m


def m_mace():
    m, d = _mask()
    d.rectangle([118, 120, 138, 236], fill=255)
    m2 = m_burst(128, 90, 80, 50, 8)
    return ImageChops.lighter(m, m2)


def m_cannon():
    m, d = _mask()
    d.rounded_rectangle([40, 96, 196, 160], radius=20, fill=255)
    d.rectangle([180, 104, 236, 152], fill=255)
    d.ellipse([20, 86, 100, 170], fill=255)
    return m


def m_dome(cx=128, cy=200, r=110, w=14):
    m, d = _mask()
    d.arc([cx - r, cy - r, cx + r, cy + r], 180, 360, fill=255, width=w)
    d.line([(cx - r, cy), (cx + r, cy)], fill=255, width=w)
    return m


def m_box(x0=60, y0=60, x1=196, y1=196, w=14):
    m, d = _mask()
    d.rectangle([x0, y0, x1, y1], outline=255, width=w)
    for i in range(1, 3):
        x = x0 + (x1 - x0) * i / 3
        d.line([(x, y0), (x, y1)], fill=255, width=w // 2)
    return m


def m_bubble(cx=128, cy=128, r=90):
    m, d = _mask()
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=255, width=12)
    d.arc([cx - r * 0.6, cy - r * 0.6, cx + r * 0.2, cy + r * 0.2], 200, 260, fill=255, width=12)
    return m


def m_portal():
    return m_spiral(128, 128, 2.2, 108, 18)


def m_shards_ring(cx=128, cy=128, r=92, n=8, size=26):
    m = Image.new("L", (S, S), 0)
    for i in range(n):
        a = i * math.tau / n
        m = ImageChops.lighter(m, m_crystal(cx + math.cos(a) * r, cy + math.sin(a) * r, size * 2.4, size, a + math.pi / 2))
    return m


def m_volley():
    m = Image.new("L", (S, S), 0)
    for i, (x, y) in enumerate([(80, 80), (130, 128), (180, 176)]):
        m = ImageChops.lighter(m, m_crystal(x, y, 110, 34, -math.pi / 4))
    return m


def m_letters_qmark():
    m, d = _mask()
    d.arc([70, 30, 186, 140], 180, 400, fill=255, width=26)
    d.rectangle([116, 130, 140, 176], fill=255)
    d.ellipse([112, 196, 144, 228], fill=255)
    return m


# --- Komposition --------------------------------------------------------------------------------------------------

def layer_color(mask: Image.Image, base, light=None, dark=None, outline=(10, 12, 16), glow=None, glow_alpha=0.6) -> Image.Image:
    """Eingefaerbtes Motiv: senkrechter Verlauf, Glanzkante oben links, dunkle Kontur, optional Leuchten."""
    light = light or mix(base, (255, 255, 255), 0.45)
    dark = dark or mix(base, (0, 0, 0), 0.45)
    grad = Image.new("RGB", (S, S))
    gd = ImageDraw.Draw(grad)
    for y in range(S):
        t = y / (S - 1)
        c = mix(light, base, t * 2) if t < 0.5 else mix(base, dark, (t - 0.5) * 2)
        gd.line([(0, y), (S, y)], fill=c)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if glow:
        g = mask.filter(ImageFilter.GaussianBlur(14)).point(lambda v: int(v * glow_alpha))
        out.paste(Image.new("RGBA", (S, S), glow + (255,)), (0, 0), g)
    if outline:
        ring = mask.filter(ImageFilter.MaxFilter(13))
        out.paste(Image.new("RGBA", (S, S), outline + (255,)), (0, 0), ring)
    out.paste(grad, (0, 0), mask)
    # Glanzkante: Maske minus nach unten rechts verschobene Maske
    shifted = ImageChops.offset(mask, 6, 6)
    rim = ImageChops.subtract(mask, shifted).point(lambda v: int(v * 0.55))
    out.paste(Image.new("RGBA", (S, S), (255, 255, 255, 255)), (0, 0), rim)
    return out


def flat(mask, color, alpha=255):
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (S, S), color + (alpha,)), (0, 0), mask)
    return out


def compose(layers) -> Image.Image:
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for lay in layers:
        img = Image.alpha_composite(img, lay)
    return img.resize((SIZE, SIZE), Image.LANCZOS)


W = (245, 248, 255)
DARK = (18, 20, 26)
FIRE = rgb("FF7A1A")
FIRE_HOT = rgb("FFD45A")
BLUE_FIRE = rgb("3A9CFF")
ICE = rgb("9AD8FF")
EARTH = rgb("8A6A4A")
GOLD = rgb("FFC94A")
ELEC = rgb("8AE8FF")
SLIME = rgb("9EE83A")
ACID = rgb("6AF04A")
WEB = (236, 240, 248)
WATER = rgb("3AA8E8")
PURPLE = rgb("A86AFF")
STONE = rgb("8C8C94")


def L(mask, color, **kw):
    return layer_color(mask, color, **kw)


def icons(c) -> dict[str, list]:
    """Faehigkeit → Schichten. c = Alienfarbe (RGB)."""
    cl = mix(c, W, 0.35)
    ground = lambda: L(m_ground(200, 56), EARTH)
    return {
        # Heatblast
        "fire_blast": lambda: [L(m_flame(-20, 0, 0.75, 1.3), FIRE, glow=FIRE), L(m_flame_core(-20, 10, 0.7), FIRE_HOT, outline=None), L(m_speedlines(80, 176, 10, 80, 3, 10), FIRE_HOT)],
        "fire_burst": lambda: [L(m_burst(128, 128, 120, 52, 12), FIRE, glow=FIRE), L(m_circle(128, 128, 46), FIRE_HOT, outline=None)],
        "flame_boost": lambda: [L(m_flame(0, 30, 0.7, math.pi), FIRE, glow=FIRE), L(m_arrow(128, 150, 128, 20, 34, 64), FIRE_HOT)],
        "inferno_wave": lambda: [L(m_waves(30, 150, 3, -40, 40, 22, 44), FIRE, glow=FIRE), L(m_flame(40, -30, 0.5), FIRE_HOT)],
        "flame_shield": lambda: [L(m_shield(), FIRE, glow=FIRE), L(m_flame(0, 6, 0.55), FIRE_HOT, outline=None)],
        "supernova": lambda: [L(m_burst(128, 128, 126, 70, 16), FIRE_HOT, glow=FIRE, glow_alpha=0.9), L(m_circle(128, 128, 56), W, outline=None),
                      L(m_ring(128, 128, 110, 10), FIRE)],
        # XLR8
        "dash_strike": lambda: [L(m_speedlines(60, 196, 10, 140, 4, 14), cl), *fist(c, 40, 0, 0.6, glow=c)],
        "blur_dodge": lambda: [flat(m_figure(-50, 0, 0.8), c, 90), flat(m_figure(-20, 0, 0.8), c, 150), L(m_figure(20, 0, 0.8), c, glow=c)],
        "rapid_strikes": lambda: [*fist(c, -40, -40, 0.45), *fist(c, 30, -10, 0.45), *fist(c, -10, 50, 0.45, glow=c), L(m_burst(190, 70, 40, 16, 6), W, outline=None)],
        "cyclone_run": lambda: [L(m_tornado(), cl, glow=c)],
        "time_slip": lambda: [L(m_clock(), c, glow=c), L(m_speedlines(200, 230, 30, 220, 2, 10), cl)],
        "lightspeed_barrage": lambda: [L(m_bolt(0, 0, 0.9), cl, glow=c), L(m_speedlines(40, 216, 160, 250, 4, 10), c)],
        # Four Arms
        "ground_slam": lambda: [ground(), L(m_cracks(128, 206), DARK, outline=None), *fist(c, 0, -10, 0.75, math.pi, glow=FIRE_HOT, glow_alpha=0.4)],
        "throw": lambda: [L(m_boulder(150, 96, 70), STONE), L(m_arrow(30, 220, 110, 140, 22, 46), cl), L(m_debris(150, 100, 70, 4, 14), EARTH)],
        "titan_leap": lambda: [ground(), L(m_arrow(70, 200, 180, 40, 30, 60), c, glow=c), L(m_debris(70, 196, 40, 4, 12), EARTH)],
        "thunder_clap": lambda: [L(m_waves(40, 128, 3, -45, 45, 18, 40), W, glow=ELEC), *fist(c, -60, -40, 0.42, -0.6), *fist(c, -60, 40, 0.42, 0.6)],
        "iron_skin": lambda: [L(m_shield(), STONE, glow=W, glow_alpha=0.3), L(m_hex(128, 120, 48, 14), W)],
        "earthquake": lambda: [ground(), L(m_cracks(128, 206), DARK, outline=None), L(m_waves(128, 210, 3, 200, 340, 14, 36), c, glow=c)],
        "four_fist_barrage": lambda: [*fist(c, -56, -50, 0.4), *fist(c, 10, -56, 0.4), *fist(c, -56, 30, 0.4), *fist(c, 14, 26, 0.48, glow=FIRE_HOT, glow_alpha=0.5),
                              L(m_burst(196, 128, 50, 20, 8), W, outline=None)],
        "rising_uppercut": lambda: [ground(), *fist(c, 0, 10, 0.62, -math.pi / 2, glow=c), L(m_arrow(200, 210, 200, 40, 22, 46), cl), L(m_debris(128, 200, 60, 4, 12), EARTH)],
        "slab_heave": lambda: [ground(), L(m_box(40, 60, 216, 120, 0), STONE), L(m_cracks(128, 206), DARK, outline=None), L(m_debris(128, 150, 90, 5, 18), EARTH),
                       L(m_arrow(60, 40, 196, 40, 18, 36), cl)],
        "giant_swing": lambda: [L(m_spiral(128, 128, 1.6, 104, 14), cl, glow=c), L(m_figure(60, -40, 0.55), STONE), *fist(c, 0, 0, 0.45)],
        "war_cry": lambda: [L(m_mouth(), c, glow=FIRE, glow_alpha=0.7), L(m_waves(150, 128, 3, -50, 50, 14, 34), FIRE)],
        "titan_meteor": lambda: [ground(), L(m_meteor(150, 90, 48), FIRE_HOT, glow=FIRE, glow_alpha=0.9), *fist(c, -40, 10, 0.5, math.pi * 0.75),
                         L(m_cracks(128, 206), DARK, outline=None)],
        "shoulder_ram": lambda: [L(m_speedlines(70, 190, 10, 110, 4, 12), cl), L(m_shield(30, 0, 0.75), c, glow=c), L(m_burst(206, 128, 46, 18, 8), W, outline=None)],
        "hammer_fist": lambda: [ground(), *fist(c, -30, -30, 0.48, math.pi), *fist(c, 30, -30, 0.48, math.pi, glow=FIRE_HOT, glow_alpha=0.4),
                        L(m_arrow(128, 100, 128, 190, 20, 40), cl)],
        "grapple_toss": lambda: [L(m_ring(128, 140, 90, 16, 200, 340), cl, glow=c), L(m_figure(-60, 30, 0.55), STONE), L(m_arrow(150, 60, 200, 150, 18, 40), c)],
        "meteor_fist": lambda: [ground(), *fist(c, 0, -20, 0.6, math.pi, glow=FIRE, glow_alpha=0.6), L(m_speedlines(10, 90, 60, 200, 3, 10), FIRE_HOT)],
        "quad_finisher": lambda: [L(m_burst(128, 150, 120, 50, 14), FIRE_HOT, glow=FIRE, glow_alpha=0.7), *fist(c, -40, -40, 0.42, math.pi), *fist(c, 40, -40, 0.42, math.pi),
                          *fist(c, -40, 20, 0.42, math.pi), *fist(c, 40, 20, 0.42, math.pi)],
        "tetramand_rage": lambda: [L(m_flame(0, 10, 0.95), FIRE, glow=FIRE, glow_alpha=0.8), *fist(c, 0, 30, 0.45)],
        "four_fist_combo": lambda: [L(m_ring(128, 128, 104, 12), cl), *fist(c, -44, -44, 0.36), *fist(c, 44, -44, 0.36), *fist(c, -44, 44, 0.36), *fist(c, 44, 44, 0.36, glow=c)],
        "tetramand_hide": lambda: [L(m_shield(), c, glow=c, glow_alpha=0.4), L(m_hex(128, 120, 46, 14), STONE)],
        # Diamondhead
        "crystal_volley": lambda: [L(m_volley(), c, glow=c)],
        "crystal_blade": lambda: [L(m_crystal(128, 128, 230, 52, math.pi / 4), cl, glow=c), L(m_ring(128, 128, 104, 10, 200, 330), W, outline=None)],
        "crystal_vault": lambda: [L(m_crystal(90, 170, 130, 40), c), L(m_crystal(160, 150, 160, 46), c), L(m_arrow(60, 110, 200, 40, 20, 40), W)],
        "spike_eruption": lambda: [ground(), L(m_spike(70, 210, 120, 44), c), L(m_spike(128, 210, 180, 56), cl, glow=c), L(m_spike(186, 210, 110, 42), c)],
        "crystal_armor": lambda: [L(m_shield(), c, glow=c), L(m_crystal(128, 120, 120, 44), cl)],
        "crystal_storm": lambda: [L(m_shards_ring(), c, glow=c), L(m_circle(128, 128, 30), cl)],
        # Big Chill
        "ice_breath": lambda: [L(m_waves(40, 128, 3, -40, 40, 20, 42), ICE, glow=ICE), L(m_snowflake(190, 128, 40, 8), W)],
        "ice_shards": lambda: [L(m_volley(), ICE, glow=ICE)],
        "phase_flight": lambda: [flat(m_wing(True), c, 120), flat(m_wing(False), c, 120), L(m_speedlines(60, 196, 10, 120, 3, 10), ICE)],
        "ice_prison": lambda: [L(m_box(), ICE, glow=ICE), L(m_figure(0, 6, 0.55), mix(c, DARK, 0.3))],
        "cryo_cloak": lambda: [flat(m_wing(True), c, 200), flat(m_wing(False), c, 200), L(m_eye(128, 110, 110, 50), ICE, glow=ICE)],
        "absolute_zero": lambda: [L(m_snowflake(), W, glow=ICE, glow_alpha=0.9), L(m_ring(128, 128, 120, 8), ICE)],
        # Cannonbolt
        "cannonball": lambda: [L(m_ball(150, 128, 76), c, glow=c), L(ball_plates(150, 128, 76), mix(c, DARK, 0.5), outline=None), L(m_speedlines(70, 186, 6, 90, 3, 12), W)],
        "shell_guard": lambda: [L(m_ball(128, 128, 96), c, glow=W, glow_alpha=0.3), L(ball_plates(128, 128, 96), mix(c, DARK, 0.5), outline=None), L(m_shield(0, 0, 0.4), W)],
        "ball_bounce": lambda: [ground(), L(m_ball(128, 90, 56), c), L(m_arrow(60, 200, 110, 140, 16, 30), W), L(m_arrow(150, 140, 200, 200, 16, 30), W)],
        "ricochet": lambda: [L(m_ball(70, 80, 40), c), L(m_ball(186, 128, 40), c), L(m_ball(90, 190, 40), c, glow=c), L(m_arrow(100, 90, 156, 120, 12, 26), W)],
        "rolling_mode": lambda: [L(m_ball(128, 128, 90), c, glow=c), L(m_ring(128, 128, 116, 12, 200, 340), W)],
        "cannonade": lambda: [ground(), L(m_ball(128, 110, 60), c, glow=FIRE, glow_alpha=0.5), L(m_arrow(128, 20, 128, 70, 18, 36), W), L(m_debris(128, 200, 60, 5, 12), EARTH)],
        # Wildmutt
        "pounce": lambda: [L(m_claws(0.0, 3), W, glow=c), L(m_arrow(40, 220, 120, 120, 16, 30), c)],
        "quill_burst": lambda: [L(m_quills(), cl, glow=c)],
        "feral_roar": lambda: [L(m_mouth(), DARK), L(m_waves(150, 128, 3, -50, 50, 16, 30), c, glow=c)],
        "savage_maul": lambda: [L(m_claws(0.2, 4), W, glow=FIRE)],
        "scent_track": lambda: [L(m_nose(), c), L(nose_holes(), DARK, outline=None), L(m_waves(128, 60, 2, 220, 320, 12, 30), cl)],
        "primal_rampage": lambda: [L(m_footprint(), c, glow=FIRE, glow_alpha=0.5), L(m_burst(200, 60, 40, 16, 6), FIRE_HOT)],
        # Stinkfly
        "slime_spit": lambda: [L(m_drop(150, 140, 60), SLIME, glow=SLIME), L(m_speedlines(80, 170, 10, 90, 3, 10), SLIME)],
        "stink_cloud": lambda: [L(m_cloud(), mix(SLIME, DARK, 0.2), glow=SLIME)],
        "wing_dash": lambda: [L(m_wing(True), cl), L(m_wing(False), cl), L(m_arrow(128, 220, 128, 40, 18, 40), c)],
        "slime_bomb": lambda: [L(m_drop(128, 150, 70), SLIME, glow=SLIME), L(m_burst(128, 140, 120, 90, 10), SLIME)],
        "updraft": lambda: [L(m_arrow(128, 236, 128, 24, 40, 70), cl, glow=c), L(m_waves(128, 230, 2, 220, 320, 10, 40), W)],
        "toxic_storm": lambda: [L(m_cloud(128, 100), mix(SLIME, DARK, 0.3), glow=SLIME), L(m_bolt(0, 60, 0.5), SLIME)],
        # Ripjaws
        "jaw_bite": lambda: [L(m_jaw(), c, glow=W, glow_alpha=0.3), L(jaw_teeth(), W)],
        "tidal_dash": lambda: [L(m_wave(150), WATER, glow=WATER), L(m_arrow(30, 90, 220, 90, 20, 40), W)],
        "whirlpool": lambda: [L(m_spiral(), WATER, glow=WATER)],
        "tail_swipe": lambda: [L(m_tail(), c, glow=c)],
        "hydro_heal": lambda: [L(m_drop(128, 130, 74), WATER, glow=WATER), L(m_plus(128, 150, 32, 18), W, outline=None)],
        "tidal_wave": lambda: [L(m_wave(110), WATER, glow=WATER), L(m_wave(170), mix(WATER, W, 0.4))],
        # Upgrade
        "optic_beam": lambda: [L(m_beam(128, 80, 250, 34), c, glow=c), L(m_eye(70, 128, 110, 70), DARK), L(m_circle(70, 128, 20), c, outline=None)],
        "liquid_form": lambda: [L(m_drop(128, 150, 74), DARK, glow=c), L(m_ring(128, 150, 50, 8, 200, 340), c, outline=None)],
        "tech_upgrade": lambda: [L(m_chip(), DARK, glow=c), L(chip_core(), c, outline=None), L(m_arrow(200, 230, 200, 150, 14, 30), c)],
        "mace_fists": lambda: [L(m_mace(), c, glow=c)],
        "system_override": lambda: [L(m_gear(), DARK, glow=c), L(m_bolt(0, 0, 0.5), c)],
        "plasma_cannon": lambda: [L(m_cannon(), DARK, glow=c), L(m_beam(128, 200, 256, 26), c), L(m_circle(60, 128, 22), c, outline=None)],
        # Ghostfreak
        "tentacle_lash": lambda: [L(m_tentacle(0.0), c, glow=PURPLE)],
        "phase_shift": lambda: [flat(m_ghost(), c, 110), L(m_ghost(30, 0, 0.8), c, glow=PURPLE)],
        "haunting_scare": lambda: [L(m_ghost(), c, glow=PURPLE), L(ghost_eyes(), DARK, outline=None), L(m_waves(128, 40, 2, 220, 320, 10, 30), PURPLE)],
        "possession": lambda: [L(m_figure(30, 20, 0.8), mix(c, DARK, 0.4)), flat(m_ghost(-30, -20, 0.6), c, 190), L(m_spiral(80, 80, 1.8, 50, 8), PURPLE)],
        "shadow_step": lambda: [L(m_portal(), PURPLE, glow=PURPLE), L(m_ghost(0, 0, 0.45), c)],
        "nightmare": lambda: [L(m_skull(), c, glow=PURPLE), L(skull_holes(), DARK, outline=None)],
        # Grey Matter
        "scan": lambda: [L(m_reticle(), c, glow=ELEC), L(m_circle(128, 128, 20), ELEC, outline=None)],
        "weak_spot": lambda: [L(m_figure(0, 0, 0.8), mix(c, DARK, 0.3)), L(m_reticle(128, 110, 40), FIRE_HOT, glow=FIRE)],
        "scurry": lambda: [L(m_footprint(), c), L(m_speedlines(40, 216, 170, 250, 3, 10), cl)],
        "tech_snare": lambda: [L(m_web(), ELEC, glow=ELEC), L(m_chip(), DARK)],
        "jury_rig": lambda: [L(m_gear(110, 140, 70), c, glow=c), L(m_bolt(60, -60, 0.4), ELEC)],
        "mastermind": lambda: [L(m_brain(), rgb("E88AB0"), glow=ELEC), L(brain_folds(), mix(rgb("E88AB0"), DARK, 0.5), outline=None)],
        # Humungousaur
        "mega_punch": lambda: [*fist(c, -10, 0, 0.8, glow=FIRE_HOT, glow_alpha=0.5), L(m_burst(206, 128, 44, 18, 7), W)],
        "tail_sweep": lambda: [L(m_tail(), c, glow=c), L(m_debris(128, 190, 80, 4, 12), EARTH)],
        "stampede": lambda: [L(m_footprint(), c, glow=c), L(m_speedlines(30, 226, 180, 256, 3, 12), EARTH)],
        "tectonic_quake": lambda: [ground(), L(m_cracks(128, 206), DARK, outline=None), L(m_boulder(70, 130, 30), STONE), L(m_boulder(186, 110, 36), STONE)],
        "bone_guard": lambda: [L(m_shield(), c, glow=W, glow_alpha=0.3), L(m_spike(128, 120, 70, 30), W), L(m_spike(90, 150, 50, 24), W), L(m_spike(166, 150, 50, 24), W)],
        "titanic_growth": lambda: [L(m_figure(-20, 24, 0.7), c, glow=c), L(m_arrow(200, 230, 200, 30, 26, 50), W)],
        # Jetray
        "neuroshock": lambda: [L(m_eye(), DARK), L(m_beam(128, 120, 256, 26), rgb("FFE84A"), glow=rgb("FFE84A")), L(m_circle(128, 128, 22), rgb("FFE84A"), outline=None)],
        "tail_shock": lambda: [L(m_tail(), c), L(m_bolt(60, -40, 0.5), rgb("FFE84A"), glow=rgb("FFE84A"))],
        "jet_burst": lambda: [L(m_wing(True), c), L(m_wing(False), c), L(m_flame(0, 70, 0.4, math.pi), FIRE, glow=FIRE)],
        "strafing_run": lambda: [L(m_speedlines(40, 216, 10, 240, 5, 10), c, glow=c), L(m_bolt(40, 0, 0.4), rgb("FFE84A"))],
        "slipstream": lambda: [L(m_spiral(128, 128, 1.6, 110, 16), cl, glow=c), L(m_arrow(40, 200, 220, 60, 16, 34), W)],
        "neuroshock_storm": lambda: [L(m_bolt(-50, 0, 0.6), rgb("FFE84A"), glow=rgb("FFE84A")), L(m_bolt(50, 0, 0.6), rgb("FFE84A")), L(m_cloud(128, 50, 0.7), DARK)],
        # Swampfire
        "methane_flame": lambda: [L(m_flame(-10, 0, 0.8, 1.2), FIRE, glow=FIRE), L(m_leaf(70, 190, 40), c)],
        "methane_cloud": lambda: [L(m_cloud(), mix(c, W, 0.2), glow=c), L(m_flame(60, 60, 0.3), FIRE)],
        "methane_jet": lambda: [L(m_flame(0, 30, 0.7, math.pi), FIRE, glow=FIRE), L(m_arrow(128, 140, 128, 20, 30, 50), c)],
        "root_snare": lambda: [L(m_roots(), mix(c, EARTH, 0.4), glow=c), L(m_leaf(180, 60, 40), c)],
        "regrowth": lambda: [L(m_leaf(128, 128, 100), c, glow=c), L(m_plus(190, 190, 34, 18), W)],
        "swamp_inferno": lambda: [L(m_burst(128, 128, 122, 56, 10), FIRE, glow=FIRE), L(m_leaf(128, 128, 60), c)],
        # Echo Echo
        "sonic_scream": lambda: [L(m_waves(30, 128, 4, -45, 45, 16, 36), W, glow=ELEC)],
        "echo_split": lambda: [L(m_figure(-60, 10, 0.6), c), L(m_figure(0, 10, 0.6), c, glow=ELEC), L(m_figure(60, 10, 0.6), c)],
        "sonic_boost": lambda: [L(m_waves(40, 200, 3, 220, 320, 14, 32), W), L(m_arrow(128, 220, 128, 30, 30, 56), c, glow=ELEC)],
        "wall_of_sound": lambda: [L(m_waves(128, 128, 4, 0, 360, 12, 26), W, glow=ELEC)],
        "sound_shield": lambda: [L(m_dome(128, 210, 110, 16), W, glow=ELEC), L(m_figure(0, 40, 0.5), c)],
        "echo_chorus": lambda: [L(m_figure(-60, 30, 0.45), c), L(m_figure(0, 30, 0.45), c), L(m_figure(60, 30, 0.45), c), L(m_waves(128, 70, 3, 200, 340, 10, 24), W, glow=ELEC)],
        # Chromastone
        "prism_beam": lambda: [L(m_prism(), cl, glow=c), L(m_rays(128, 128, 5, -0.5, 0.5, 70, 126, 12), W)],
        "spectral_burst": lambda: [L(m_burst(128, 128, 120, 50, 7), c, glow=c), L(m_prism(), W)],
        "photon_dash": lambda: [L(m_speedlines(50, 206, 10, 140, 4, 12), cl), L(m_crystal(180, 128, 120, 44, math.pi / 2), c, glow=c)],
        "crystal_lattice": lambda: [L(m_hex(128, 128, 104, 14), c, glow=c), L(m_hex(128, 128, 54, 12), cl)],
        "solar_charge": lambda: [L(m_star(128, 128, 104), GOLD, glow=GOLD), L(m_arrow(128, 220, 128, 160, 14, 30), c)],
        "full_spectrum": lambda: [L(m_burst(128, 128, 126, 60, 12), cl, glow=c, glow_alpha=0.9), L(m_prism(), c)],
        # Spidermonkey
        "web_shot": lambda: [L(m_web(160, 128, 80, 6), WEB, glow=W, glow_alpha=0.3), L(m_speedlines(110, 146, 10, 90, 2, 8), WEB)],
        "web_net": lambda: [L(m_web(), WEB, glow=W, glow_alpha=0.3)],
        "web_swing": lambda: [L(m_ring(128, 40, 150, 10, 30, 150), WEB), L(m_figure(-40, 40, 0.5), c, glow=c)],
        "monkey_frenzy": lambda: [*fist(c, -40, -30, 0.45), *fist(c, 40, -30, 0.45), *fist(c, -40, 50, 0.45), *fist(c, 40, 50, 0.45, glow=c)],
        "web_trap": lambda: [ground(), L(m_web(128, 150, 90, 7), WEB, glow=W, glow_alpha=0.3)],
        "web_cocoon": lambda: [L(m_cocoon(), WEB, glow=W, glow_alpha=0.3), L(cocoon_lines(), mix(WEB, DARK, 0.4), outline=None)],
        # Goop
        "acid_spit": lambda: [L(m_drop(160, 140, 56), ACID, glow=ACID), L(m_speedlines(90, 170, 10, 100, 3, 10), ACID)],
        "slime_whip": lambda: [L(m_tentacle(-0.3, 30), c, glow=c)],
        "puddle_form": lambda: [L(m_cloud(128, 190, 1.1), c, glow=c), L(m_arrow(128, 30, 128, 140, 22, 40), W)],
        "engulf": lambda: [L(m_bubble(), c, glow=c), L(m_figure(0, 8, 0.5), mix(c, DARK, 0.4))],
        "acid_pool": lambda: [ground(), L(m_cloud(128, 200, 1.0), ACID, glow=ACID)],
        "acid_flood": lambda: [L(m_wave(120), ACID, glow=ACID), L(m_drop(70, 70, 26), ACID)],
        # Brainstorm
        "lightning_arc": lambda: [L(m_bolt(0, 0, 0.9, 0.5), ELEC, glow=ELEC)],
        "static_grid": lambda: [L(m_hex(128, 128, 104, 12), ELEC, glow=ELEC), L(m_bolt(0, 0, 0.4), W)],
        "electro_levitation": lambda: [L(m_figure(0, -20, 0.6), c), L(m_waves(128, 230, 3, 220, 320, 10, 26), ELEC, glow=ELEC)],
        "force_field": lambda: [L(m_dome(128, 210, 110, 18), ELEC, glow=ELEC), L(m_hex(128, 150, 30, 8), W)],
        "brain_analysis": lambda: [L(m_brain(), rgb("E8A0B8"), glow=ELEC), L(brain_folds(), mix(rgb("E8A0B8"), DARK, 0.5), outline=None), L(m_reticle(196, 196, 40), ELEC)],
        "mind_storm": lambda: [L(m_cloud(128, 90, 0.9), DARK, glow=ELEC), L(m_bolt(-40, 70, 0.5), ELEC), L(m_bolt(40, 70, 0.5), ELEC)],
        # Rath
        "wrist_claw": lambda: [L(m_spike(128, 220, 200, 50), W, glow=c), *fist(c, 0, 40, 0.5)],
        "challenge": lambda: [L(m_reticle(), c, glow=FIRE), L(m_letters_qmark(), W)],
        "rath_charge": lambda: [L(m_arrow(30, 128, 230, 128, 40, 70), c, glow=c), L(m_speedlines(70, 186, 0, 70, 3, 10), W)],
        "suplex": lambda: [L(m_figure(-40, 20, 0.6), c), L(m_ring(128, 128, 100, 16, 180, 360), W, glow=c), L(m_arrow(214, 100, 214, 200, 16, 32), W)],
        "appoplexian_roar": lambda: [L(m_mouth(), DARK), L(m_waves(150, 128, 3, -55, 55, 16, 30), c, glow=FIRE)],
        "rath_finale": lambda: [L(m_crown(), GOLD, glow=c), *fist(c, 0, 50, 0.45, math.pi)],
        # Way Big
        "cosmic_ray": lambda: [L(m_beam(128, 30, 256, 52), rgb("7FE3FF"), glow=rgb("7FE3FF"), glow_alpha=0.9), L(m_beam(128, 30, 256, 20), W, outline=None)],
        "titan_stomp": lambda: [ground(), L(m_footprint(), c, glow=c), L(m_cracks(128, 206), DARK, outline=None)],
        "giant_leap": lambda: [ground(), L(m_figure(-20, -40, 0.6), c, glow=c), L(m_arrow(40, 210, 200, 40, 18, 36), W)],
        "shockwave_clap": lambda: [L(m_waves(128, 128, 3, 0, 360, 14, 34), W, glow=rgb("7FE3FF")), L(m_burst(128, 128, 40, 16, 8), c)],
        "tokustar_guard": lambda: [L(m_shield(), c, glow=rgb("7FE3FF")), L(m_star(128, 120, 50), W)],
        "starfall": lambda: [L(m_meteor(170, 80, 34), rgb("7FE3FF"), glow=rgb("7FE3FF")), L(m_meteor(100, 140, 26), W), L(m_star(70, 70, 26), GOLD)],
        # Atomix
        "atomic_bolt": lambda: [L(m_atom(), c, glow=c), L(m_arrow(150, 200, 236, 120, 14, 30), W)],
        "nuclear_pulse": lambda: [L(m_ring(128, 128, 110, 14), c, glow=c), L(m_ring(128, 128, 70, 12), cl), L(m_circle(128, 128, 30), W, outline=None)],
        "fusion_leap": lambda: [L(m_atom(128, 100, 70, 10), c, glow=c), L(m_arrow(128, 240, 128, 170, 20, 40), W)],
        "containment_field": lambda: [L(m_dome(128, 210, 110, 16), c, glow=c), L(m_atom(128, 160, 40, 6), cl)],
        "half_life": lambda: [L(m_hourglass(), c, glow=c), L(m_atom(128, 128, 40, 6), W)],
        "hero_time": lambda: [L(m_burst(128, 128, 126, 70, 14), GOLD, glow=c, glow_alpha=0.9), L(m_atom(128, 128, 70, 10), c)],
        # Alien X
        "reality_bolt": lambda: [L(m_star(128, 128, 110), W, glow=c, glow_alpha=0.9), L(m_bolt(0, 0, 0.5), c, outline=None)],
        "gravity_warp": lambda: [L(m_spiral(), c, glow=PURPLE), L(m_circle(128, 128, 20), DARK)],
        "blink": lambda: [flat(m_figure(-50, 0, 0.7), c, 90), L(m_figure(50, 0, 0.7), c, glow=W), L(m_star(40, 50, 20), W)],
        "time_stop": lambda: [L(m_clock(), c, glow=c), L(m_box(110, 100, 120, 156, 0), W)],
        "creation": lambda: [L(m_star(128, 128, 104), W, glow=c), L(m_plus(190, 190, 34, 18), c)],
        "big_bang": lambda: [L(m_burst(128, 128, 128, 80, 18), W, glow=c, glow_alpha=1.0), L(m_circle(128, 128, 40), DARK, outline=None),
                     L(m_star(128, 128, 30), W, outline=None)],
        "flamethrower": lambda: [L(m_flame(-40, 0, 0.55, 1.4), FIRE, glow=FIRE), L(m_flame(30, 0, 0.7, 1.45), FIRE_HOT, glow=FIRE), L(m_speedlines(70, 190, 10, 60, 3, 10), FIRE)],
        "blaze_roll": lambda: [L(m_ring(128, 128, 80, 22), FIRE, glow=FIRE), L(m_circle(128, 128, 40), FIRE_HOT, outline=None), L(m_speedlines(60, 196, 0, 50, 4, 10), FIRE)],
        "heat_absorb": lambda: [L(m_spiral(128, 128, 2.0, 104, 12), FIRE, glow=FIRE), L(m_circle(128, 128, 30), FIRE_HOT, outline=None)],
        "fire_tornado": lambda: [L(m_tornado(), FIRE, glow=FIRE), L(m_flame(0, 60, 0.35), FIRE_HOT, outline=None)],
        "magma_eruption": lambda: [ground(), L(m_flame(-50, 20, 0.5), FIRE, glow=FIRE), L(m_flame(0, -10, 0.7), FIRE_HOT, glow=FIRE), L(m_flame(50, 20, 0.5), FIRE, glow=FIRE)],
        "meteor_rain": lambda: [ground(), L(m_meteor(170, 80, 34), FIRE_HOT, glow=FIRE, glow_alpha=0.8), L(m_meteor(90, 120, 26), FIRE, glow=FIRE), L(m_meteor(190, 160, 22), FIRE, glow=FIRE)],
        "blazing_rush": lambda: [L(m_speedlines(70, 190, 10, 110, 4, 12), FIRE), *fist(c, 30, 0, 0.6, glow=FIRE, glow_alpha=0.8)],
        "comet_kick": lambda: [ground(), L(m_meteor(128, 110, 46), FIRE_HOT, glow=FIRE, glow_alpha=0.8), L(m_cracks(128, 206), DARK, outline=None)],
        "ember_grip": lambda: [*fist(c, 0, 10, 0.7, glow=FIRE, glow_alpha=0.9), L(m_flame(0, -60, 0.4), FIRE_HOT, outline=None)],
        "phoenix_dive": lambda: [ground(), flat(m_wing(True), FIRE, 200), flat(m_wing(False), FIRE, 200), L(m_flame(0, 10, 0.6, math.pi), FIRE_HOT, glow=FIRE)],
        "incinerate": lambda: [L(m_burst(128, 128, 120, 60, 14), FIRE_HOT, glow=FIRE, glow_alpha=0.8), L(m_skull(), DARK)],
        "pyronite_core": lambda: [L(m_circle(128, 128, 90), FIRE, glow=FIRE, glow_alpha=0.8), L(m_circle(128, 128, 50), FIRE_HOT, outline=None), L(m_ring(128, 128, 112, 8), BLUE_FIRE)],
        "molten_touch": lambda: [*fist(c, 0, 0, 0.7, glow=FIRE, glow_alpha=0.7), L(m_flame(50, -50, 0.35), FIRE_HOT, outline=None)],
        "heat_haze": lambda: [L(m_waves(128, 200, 3, 200, 340, 12, 30), FIRE_HOT, glow=FIRE), L(m_arrow(40, 90, 150, 90, 14, 30), STONE), L(m_burst(170, 90, 40, 16, 8), FIRE, outline=None)],
        "tail_whip": lambda: [L(m_ring(128, 128, 96, 18, 20, 330), cl, glow=c), L(m_tail(), c), L(m_speedlines(80, 176, 150, 240, 3, 10), W)],
        "rapid_disarm": lambda: [L(m_speedlines(60, 196, 10, 120, 4, 10), cl), L(m_box(130, 70, 200, 190, 0), STONE), L(m_arrow(200, 128, 120, 128, 16, 34), c, glow=c)],
        "vacuum_trail": lambda: [L(m_speedlines(40, 216, 10, 230, 5, 10), cl, glow=c), L(m_spiral(70, 128, 1.4, 50, 10), W)],
        "friction_fire": lambda: [L(m_ring(128, 140, 90, 16), FIRE, glow=FIRE), L(m_flame(-60, 40, 0.3), FIRE_HOT, outline=None), L(m_flame(60, 40, 0.3), FIRE_HOT, outline=None), L(m_speedlines(100, 156, 90, 170, 2, 8), cl)],
        "phantom_strikes": lambda: [flat(m_figure(-60, 0, 0.6), c, 90), flat(m_figure(60, 0, 0.6), c, 90), L(m_figure(0, 0, 0.7), c, glow=c), L(m_burst(128, 60, 30, 12, 6), W, outline=None)],
        "mach_sprint": lambda: [L(m_waves(70, 128, 4, -45, 45, 16, 34), W, glow=c, glow_alpha=0.9), L(m_speedlines(50, 206, 10, 140, 5, 12), c)],
        "supersonic_shove": lambda: [L(m_waves(150, 128, 3, -50, 50, 14, 32), W, glow=c), *fist(c, -30, 0, 0.55)],
        "blitz_kick": lambda: [L(m_spiral(128, 128, 1.2, 90, 14), cl, glow=c), L(m_arrow(128, 200, 128, 40, 20, 40), W)],
        "leg_sweep": lambda: [L(m_ground(200, 56), EARTH), L(m_ring(128, 190, 90, 14, 180, 360), cl, glow=c), L(m_figure(40, 30, 0.45), STONE)],
        "speed_dive": lambda: [L(m_ground(200, 56), EARTH), L(m_arrow(50, 40, 190, 180, 22, 46), c, glow=c), L(m_speedlines(20, 80, 10, 90, 3, 8), cl)],
        "hundred_blows": lambda: [*fist(c, -50, -40, 0.35), *fist(c, 40, -50, 0.35), *fist(c, -40, 40, 0.35), *fist(c, 50, 30, 0.35), L(m_burst(128, 128, 54, 22, 10), W, glow=c)],
        "kineceleran_tempo": lambda: [L(m_clock(128, 128, 90), c, glow=c), L(m_speedlines(200, 230, 20, 230, 2, 8), cl)],
        "blur_runner": lambda: [L(m_wave(170), ICE, glow=ICE), L(m_figure(0, -20, 0.55), c), L(m_speedlines(70, 150, 10, 70, 3, 8), cl)],
        "quick_reflexes": lambda: [flat(m_figure(-40, 0, 0.7), c, 100), L(m_figure(30, 0, 0.7), c, glow=c), L(m_arrow(10, 70, 110, 70, 12, 26), STONE)],
        "crystal_lance": lambda: [ground(), L(m_spike(60, 200, 70, 30), c, glow=c), L(m_spike(110, 200, 100, 34), c, glow=c), L(m_spike(170, 200, 140, 40), cl, glow=c)],
        "crystal_hammer": lambda: [ground(), L(m_mace(), c, glow=c), L(m_cracks(128, 206), DARK, outline=None)],
        "crystal_wall": lambda: [ground(), L(m_hex(80, 130, 40, 0, True), c, glow=c), L(m_hex(176, 130, 40, 0, True), c, glow=c), L(m_hex(128, 100, 40, 0, True), cl, glow=c)],
        "crystal_cage": lambda: [L(m_box(), c, glow=c), L(m_figure(0, 6, 0.55), mix(c, DARK, 0.4))],
        "diamond_drill": lambda: [L(m_spike(128, 230, 210, 70), c, glow=c), L(m_speedlines(40, 216, 10, 90, 4, 10), cl), L(m_debris(190, 128, 50, 4, 12), STONE)],
        "crystal_cathedral": lambda: [ground(), L(m_spike(50, 200, 90, 30), c, glow=c), L(m_spike(206, 200, 90, 30), c, glow=c), L(m_spike(90, 200, 140, 36), cl, glow=c), L(m_spike(166, 200, 140, 36), cl, glow=c), L(m_spike(128, 200, 190, 44), W, glow=c)],
        "crystal_ram": lambda: [L(m_speedlines(70, 190, 10, 110, 4, 12), cl), L(m_shield(30, 0, 0.75), c, glow=c), L(m_shards_ring(200, 128, 30, 6, 12), W)],
        "shard_fall": lambda: [ground(), L(m_spike(128, 200, 150, 50), c, glow=c), L(m_arrow(128, 20, 128, 70, 16, 30), W)],
        "crystal_shackle": lambda: [ground(), L(m_figure(0, -30, 0.6), STONE), L(m_spike(90, 210, 60, 26), c, glow=c), L(m_spike(166, 210, 60, 26), c, glow=c)],
        "diamond_drop": lambda: [ground(), L(m_crystal(128, 110, 120, 50, math.pi), c, glow=c), L(m_spike(50, 200, 60, 24), cl), L(m_spike(206, 200, 60, 24), cl)],
        "crystallize": lambda: [L(m_crystal(128, 128, 170, 80), c, glow=c, glow_alpha=0.8), L(m_shards_ring(128, 128, 100, 8, 22), W)],
        "petrosapien_resonance": lambda: [L(m_crystal(128, 128, 180, 70), c, glow=c, glow_alpha=0.7), L(m_waves(200, 128, 3, 150, 210, 10, 26), W)],
        "crystal_regrowth": lambda: [L(m_crystal(100, 140, 140, 56), c, glow=c), L(m_plus(180, 80, 40, 20), W)],
        "diamond_hide": lambda: [L(m_shield(), c, glow=c), L(m_hex(128, 120, 46, 14), W), L(m_shards_ring(128, 120, 90, 6, 14), cl)],
        "head_hop": lambda: [L(m_figure(0, 40, 0.75), STONE), L(m_brain(), c, glow=c), L(m_arrow(40, 220, 100, 120, 14, 30), cl)],
        "calculated_strike": lambda: [L(m_reticle(), c, glow=c), *fist(c, 0, 0, 0.45)],
        "overload_device": lambda: [L(m_chip(), c, glow=ELEC), L(chip_core(), W, outline=None), L(m_bolt(60, -60, 0.4), ELEC)],
        "mini_turret": lambda: [L(m_gear(128, 150, 70, 8), STONE), L(m_cannon(), c, glow=c)],
        "weakness_grid": lambda: [L(m_hex(128, 128, 104, 10), c, glow=c), L(m_reticle(128, 128, 50), W)],
        "galvan_device": lambda: [L(m_spiral(128, 128, 2.2, 108, 12), c, glow=c), L(m_gear(128, 128, 40, 8), W)],
        "ankle_bite": lambda: [L(m_jaw(), c, glow=c), L(jaw_teeth(), W, outline=None)],
        "skull_hop": lambda: [L(m_skull(), STONE), L(skull_holes(), DARK, outline=None), L(m_arrow(128, 90, 128, 10, 16, 30), c, glow=c)],
        "wire_trip": lambda: [L(m_figure(0, -10, 0.6), STONE), L(m_spiral(128, 200, 1.6, 50, 8), c, glow=ELEC)],
        "precision_drop": lambda: [L(m_reticle(128, 170, 60), c, glow=c), L(m_arrow(128, 20, 128, 120, 18, 34), W)],
        "logical_conclusion": lambda: [L(m_brain(), c, glow=c, glow_alpha=0.8), L(brain_folds(), mix(c, DARK, 0.5), outline=None), L(m_reticle(128, 128, 110), W)],
        "keen_observer": lambda: [L(m_eye(), c, glow=c), L(m_circle(128, 128, 26), DARK, outline=None)],
        "tiny_target": lambda: [L(m_reticle(128, 128, 100), STONE), L(m_figure(0, 20, 0.35), c, glow=c)],
        "wall_crawler": lambda: [L(m_box(150, 20, 236, 236, 0), STONE), L(m_figure(-20, 0, 0.5), c, glow=c), L(m_arrow(70, 200, 70, 60, 14, 30), cl)],
        "crunch_bite": lambda: [L(m_jaw(), c, glow=FIRE, glow_alpha=0.5), L(jaw_teeth(), W, outline=None)],
        "wall_leap": lambda: [L(m_box(196, 30, 236, 236, 0), STONE), L(m_arrow(40, 200, 180, 90, 18, 36), c, glow=c), L(m_arrow(180, 90, 80, 30, 14, 28), cl)],
        "quill_carpet": lambda: [ground(), L(m_quills(), W, glow=c)],
        "hunting_howl": lambda: [L(m_nose(), c, glow=c), L(nose_holes(), DARK, outline=None), L(m_waves(128, 60, 3, 220, 320, 12, 30), W)],
        "drag_down": lambda: [ground(), L(m_figure(0, 40, 0.5), STONE), L(m_claws(0.3, 3), c, glow=c)],
        "wild_hunt": lambda: [L(m_claws(-0.3, 3), c, glow=c, glow_alpha=0.8), L(m_speedlines(60, 196, 150, 240, 4, 10), cl), L(m_jaw(), mix(c, DARK, 0.2))],
        "tackle_pounce": lambda: [L(m_speedlines(70, 190, 10, 100, 4, 12), cl), L(m_claws(0.0, 3), c, glow=c)],
        "quill_dive": lambda: [L(m_quills(), W, glow=c), L(m_arrow(128, 120, 128, 230, 16, 30), c)],
        "throat_bite": lambda: [L(m_jaw(), c), L(jaw_teeth(), W, outline=None), L(m_drop(196, 190, 30), rgb("9A0A0A"))],
        "predator_drop": lambda: [ground(), L(m_figure(0, 50, 0.45), STONE), L(m_claws(math.pi / 2, 3), c, glow=c)],
        "rip_apart": lambda: [L(m_claws(0.4, 3), c, glow=FIRE, glow_alpha=0.7), L(m_claws(-0.4, 3), mix(c, W, 0.3))],
        "vulpimancer_hunt": lambda: [L(m_reticle(), c, glow=c), L(m_claws(0.0, 3), W)],
        "scent_sight": lambda: [L(m_nose(), c, glow=c), L(nose_holes(), DARK, outline=None)],
        "survival_instinct": lambda: [L(m_heart(0, 0, 0.9), rgb("9A0A0A"), glow=FIRE, glow_alpha=0.5), L(m_claws(0.0, 3), c)],
        "acid_drop": lambda: [L(m_drop(80, 90, 34), rgb("C8F040"), glow=c), L(m_drop(140, 130, 34), rgb("C8F040"), glow=c), L(m_drop(196, 170, 34), rgb("C8F040"), glow=c), ground()],
        "stinger_strike": lambda: [L(m_spike(128, 230, 210, 46), c, glow=c), L(m_speedlines(60, 196, 10, 80, 3, 10), cl)],
        "slime_trap": lambda: [L(m_bubble(), rgb("8EEA4E"), glow=c), L(m_figure(0, 10, 0.5), mix(c, DARK, 0.4))],
        "gas_jet": lambda: [L(m_cloud(80, 140, 0.6), mix(c, W, 0.2), glow=c), L(m_arrow(110, 128, 230, 128, 22, 44), c, glow=c)],
        "corrosive_spray": lambda: [L(m_waves(40, 128, 3, -40, 40, 18, 40), rgb("C8F040"), glow=c), L(m_drop(200, 128, 26), rgb("C8F040"))],
        "plague_cloud": lambda: [L(m_cloud(128, 128, 1.3), c, glow=c, glow_alpha=0.8), L(m_skull(), DARK), L(skull_holes(), c, outline=None)],
        "wing_slash": lambda: [flat(m_wing(True), c, 220), L(m_ring(128, 128, 96, 14, 200, 340), cl, glow=c)],
        "dive_sting": lambda: [L(m_spike(128, 40, -160, 44), c, glow=c), L(m_drop(128, 210, 24), rgb("C8F040"))],
        "slime_grab": lambda: [L(m_bubble(128, 140, 80), rgb("8EEA4E"), glow=c), L(m_mouth(), DARK)],
        "toxic_plunge": lambda: [ground(), L(m_cloud(128, 170, 1.0), c, glow=c), L(m_arrow(128, 20, 128, 110, 18, 34), W)],
        "dissolve": lambda: [L(m_burst(128, 128, 120, 60, 12), c, glow=c, glow_alpha=0.8), L(m_drop(128, 140, 50), rgb("C8F040"))],
        "lepidopterran_toxin": lambda: [L(m_drop(128, 140, 70), rgb("C8F040"), glow=c), L(m_skull(), DARK)],
        "compound_eyes": lambda: [L(m_eye(90, 110, 90, 60), c, glow=c), L(m_eye(166, 110, 90, 60), c, glow=c), L(m_eye(110, 170, 70, 46), cl), L(m_eye(146, 170, 70, 46), cl)],
        "stench": lambda: [L(m_waves(128, 200, 3, 220, 320, 14, 30), c, glow=c), L(m_cloud(128, 110, 0.7), mix(c, W, 0.2))],
        "water_jet": lambda: [L(m_beam(128, 40, 236, 40), rgb("3A8CFF"), glow=c), L(m_beam(128, 40, 236, 14), W, outline=None), L(m_jaw(), c)],
        "geyser": lambda: [ground(), L(m_drop(128, 120, 50), rgb("3A8CFF"), glow=c), L(m_arrow(128, 200, 128, 30, 20, 40), W)],
        "feeding_frenzy": lambda: [L(m_jaw(), c, glow=c), L(jaw_teeth(), W, outline=None), L(m_burst(200, 60, 40, 16, 8), rgb("9A0A0A"), outline=None)],
        "bubble_shield": lambda: [L(m_bubble(), rgb("3A8CFF"), glow=c), L(m_figure(0, 10, 0.5), c)],
        "riptide_pull": lambda: [L(m_spiral(128, 128, 1.8, 100, 14), rgb("3A8CFF"), glow=c), L(m_arrow(230, 128, 160, 128, 16, 30), W)],
        "maelstrom": lambda: [L(m_spiral(128, 128, 3.0, 112, 12), rgb("3A8CFF"), glow=c, glow_alpha=0.9), L(m_circle(128, 128, 24), rgb("1A4A9A"), outline=None)],
        "shark_ram": lambda: [L(m_speedlines(70, 190, 10, 100, 4, 12), rgb("3A8CFF")), L(m_jaw(), c, glow=c)],
        "jaw_dive": lambda: [ground(), L(m_jaw(), c, glow=c), L(m_arrow(200, 40, 200, 150, 14, 28), W)],
        "drag_under": lambda: [L(m_wave(110), rgb("3A8CFF"), glow=c), L(m_figure(0, 40, 0.45), STONE), L(m_bubble(190, 60, 20), W)],
        "splash_down": lambda: [L(m_wave(190), rgb("3A8CFF"), glow=c), L(m_drop(128, 100, 46), rgb("3A8CFF")), L(m_arrow(128, 10, 128, 60, 14, 26), W)],
        "death_bite": lambda: [L(m_jaw(), rgb("1A4A9A"), glow=c, glow_alpha=0.9), L(jaw_teeth(), W, outline=None), L(m_skull(), DARK)],
        "tidal_strength": lambda: [L(m_wave(150), rgb("3A8CFF"), glow=c), *fist(c, 0, -30, 0.45)],
        "deep_hunter": lambda: [L(m_eye(128, 110, 160, 80), rgb("1A4A9A"), glow=c), L(m_bubble(60, 200, 20), W), L(m_bubble(200, 180, 16), W)],
        "blood_scent": lambda: [L(m_drop(128, 140, 70), rgb("9A0A0A"), glow=FIRE, glow_alpha=0.4), L(m_jaw(), c)],
        "stretch_grab": lambda: [L(m_beam(128, 30, 190, 20), DARK, glow=rgb("38FF14")), *fist(c, 70, 0, 0.45, glow=rgb("38FF14"))],
        "gear_upgrade": lambda: [L(m_gear(), DARK, glow=rgb("38FF14")), L(m_arrow(128, 200, 128, 60, 20, 40), rgb("38FF14"))],
        "nano_drones": lambda: [L(m_ring(128, 128, 90, 8), rgb("38FF14")), L(m_circle(60, 60, 22), DARK, glow=rgb("38FF14")), L(m_circle(196, 60, 22), DARK, glow=rgb("38FF14")), L(m_circle(60, 196, 22), DARK, glow=rgb("38FF14")), L(m_circle(196, 196, 22), DARK, glow=rgb("38FF14"))],
        "laser_grid": lambda: [L(m_beam(60, 20, 236, 10), rgb("00E6FF"), glow=rgb("00E6FF")), L(m_beam(110, 20, 236, 10), rgb("00E6FF"), glow=rgb("00E6FF")), L(m_beam(160, 20, 236, 10), rgb("00E6FF"), glow=rgb("00E6FF")), L(m_beam(210, 20, 236, 10), rgb("00E6FF"), glow=rgb("00E6FF"))],
        "emp_pulse": lambda: [L(m_ring(128, 128, 104, 10), rgb("00E6FF"), glow=rgb("00E6FF")), L(m_ring(128, 128, 70, 10), rgb("00E6FF")), L(m_bolt(0, 0, 0.6), W)],
        "omega_overdrive": lambda: [L(m_eye(128, 128, 200, 100), DARK, glow=rgb("38FF14"), glow_alpha=0.9), L(m_circle(128, 128, 30), rgb("38FF14"), outline=None), L(m_rays(128, 128, 9, -1.4, 1.4, 70, 124, 10), rgb("38FF14"))],
        "liquid_ram": lambda: [L(m_figure(0, 0, 0.6), STONE), L(m_beam(128, 20, 236, 26), DARK, glow=rgb("38FF14"))],
        "hammer_morph": lambda: [ground(), L(m_mace(), DARK, glow=rgb("38FF14")), L(m_cracks(128, 206), rgb("38FF14"), outline=None)],
        "nanite_inject": lambda: [L(m_spike(128, 210, 160, 30), DARK, glow=rgb("38FF14")), L(m_shards_ring(128, 70, 40, 6, 12), rgb("38FF14"))],
        "liquid_drop": lambda: [ground(), L(m_drop(128, 120, 60), DARK, glow=rgb("38FF14")), L(m_arrow(128, 10, 128, 50, 14, 26), rgb("38FF14"))],
        "system_crash": lambda: [L(m_chip(), DARK, glow=rgb("38FF14")), L(m_burst(128, 128, 60, 26, 10), rgb("38FF14"), outline=None)],
        "mechamorph_integration": lambda: [L(m_chip(), DARK, glow=rgb("38FF14")), L(chip_core(), rgb("38FF14"), outline=None)],
        "self_repair": lambda: [L(m_gear(128, 128, 80, 8), DARK, glow=rgb("38FF14")), L(m_plus(180, 180, 36, 18), rgb("38FF14"))],
        "liquid_body": lambda: [L(m_drop(128, 140, 80), DARK, glow=rgb("38FF14")), *fist(c, -40, -60, 0.35)],
        "soul_drain": lambda: [L(m_beam(128, 30, 220, 24), rgb("5AE0E0"), glow=rgb("5AE0E0")), L(m_ghost(-50, 0, 0.5), c), L(m_heart(70, 0, 0.4), rgb("C02040"))],
        "ghost_flight": lambda: [L(m_box(150, 30, 190, 226, 0), STONE), flat(m_ghost(-40, 0, 0.7), c, 140), L(m_speedlines(60, 196, 10, 60, 3, 10), cl)],
        "spectral_chains": lambda: [L(m_ring(90, 128, 50, 14), c, glow=c), L(m_ring(166, 128, 50, 14), c, glow=c), L(m_ring(128, 128, 50, 14), cl)],
        "vanish": lambda: [flat(m_ghost(0, 0, 0.9), c, 90), L(m_eye(128, 110, 120, 50), W, glow=c)],
        "haunting": lambda: [L(m_figure(0, 20, 0.55), STONE), L(m_ghost(-60, -50, 0.4), c, glow=c), L(m_ghost(60, -50, 0.4), c, glow=c)],
        "phantom_legion": lambda: [L(m_ghost(-60, -40, 0.42), c, glow=c), L(m_ghost(60, -40, 0.42), c, glow=c), L(m_ghost(-60, 50, 0.42), c, glow=c), L(m_ghost(60, 50, 0.42), c, glow=c), L(m_ghost(0, 0, 0.55), W, glow=c)],
        "spectral_rush": lambda: [L(m_figure(0, 0, 0.55), STONE), flat(m_ghost(-30, 0, 0.6), c, 160), L(m_speedlines(60, 196, 10, 70, 3, 10), cl)],
        "shadow_drop": lambda: [ground(), L(m_tentacle(1.6), c, glow=c), L(m_arrow(128, 60, 128, 170, 16, 30), W)],
        "chilling_touch": lambda: [L(m_claws(0.0, 3), mix(c, W, 0.4), glow=ICE), L(m_snowflake(190, 70, 36, 8), ICE)],
        "haunt_crash": lambda: [ground(), L(m_ghost(0, -20, 0.6), c, glow=c), L(m_waves(128, 210, 3, 200, 340, 12, 30), cl)],
        "soul_rend": lambda: [L(m_figure(-40, 10, 0.5), STONE), L(m_ghost(50, -20, 0.5), rgb("5AE0E0"), glow=rgb("5AE0E0")), L(m_arrow(70, 128, 150, 100, 12, 24), W)],
        "ectonurite_dread": lambda: [L(m_ghost(), c, glow=c), L(ghost_eyes(), DARK, outline=None)],
        "intangible": lambda: [flat(m_ghost(0, 0, 0.9), c, 120), L(m_arrow(30, 128, 226, 128, 14, 28), STONE)],
        "night_stalker": lambda: [L(m_circle(170, 80, 40), mix(W, c, 0.2), glow=W, glow_alpha=0.3), L(m_ghost(-30, 30, 0.6), c, glow=c)],
        "bowling_strike": lambda: [ground(), L(m_ball(70, 160, 46), c, glow=c), L(ball_plates(70, 160, 46), mix(c, DARK, 0.3), outline=None), L(m_figure(150, 0, 0.4), STONE), L(m_figure(200, -20, 0.4), STONE)],
        "wrecking_ball": lambda: [ground(), L(m_ball(150, 90, 50), c, glow=c), L(ball_plates(150, 90, 50), mix(c, DARK, 0.3), outline=None), L(m_cracks(128, 206), DARK, outline=None), L(m_ring(110, 180, 90, 10, 200, 300), W)],
        "pinball": lambda: [L(m_ball(128, 128, 50), c, glow=c), L(ball_plates(128, 128, 50), mix(c, DARK, 0.3), outline=None), L(m_arrow(40, 200, 90, 160, 12, 24), W), L(m_arrow(200, 60, 170, 100, 12, 24), W)],
        "spike_shell": lambda: [L(m_burst(128, 128, 116, 70, 12), W, glow=c), L(m_ball(128, 128, 70), c), L(ball_plates(128, 128, 70), mix(c, DARK, 0.3), outline=None)],
        "shell_spin": lambda: [L(m_spiral(128, 128, 1.6, 108, 12), cl, glow=c), L(m_ball(128, 128, 46), c), L(ball_plates(128, 128, 46), mix(c, DARK, 0.3), outline=None)],
        "bounce_barrage": lambda: [ground(), L(m_ball(190, 70, 36), c, glow=FIRE, glow_alpha=0.6), L(m_ring(110, 200, 60, 10, 190, 350), W), L(m_ring(50, 200, 40, 10, 190, 350), W), L(m_burst(170, 200, 40, 16, 8), FIRE_HOT, outline=None)],
        "curl_charge": lambda: [L(m_speedlines(70, 190, 10, 100, 4, 12), cl), L(m_ball(170, 128, 50), c, glow=c), L(ball_plates(170, 128, 50), mix(c, DARK, 0.3), outline=None)],
        "ball_stomp": lambda: [ground(), L(m_ball(128, 110, 50), c, glow=c), L(ball_plates(128, 110, 50), mix(c, DARK, 0.3), outline=None), L(m_waves(128, 210, 2, 200, 340, 12, 30), W)],
        "shell_counter": lambda: [L(m_shield(), c, glow=c), L(m_arrow(120, 128, 30, 128, 14, 28), W)],
        "cannon_drop": lambda: [ground(), L(m_ball(128, 100, 46), c, glow=FIRE, glow_alpha=0.6), L(m_speedlines(20, 60, 100, 160, 3, 8), FIRE_HOT), L(m_cracks(128, 206), DARK, outline=None)],
        "bullseye": lambda: [L(m_reticle(), W), L(m_ball(128, 128, 40), c, glow=FIRE, glow_alpha=0.7)],
        "armored_shell": lambda: [L(m_ball(), c, glow=c), L(ball_plates(), mix(c, DARK, 0.3), outline=None), L(m_arrow(10, 60, 70, 90, 12, 24), STONE)],
        "rolling_momentum": lambda: [L(m_speedlines(70, 190, 10, 90, 4, 12), cl), L(m_ball(170, 128, 56), c, glow=c), L(ball_plates(170, 128, 56), mix(c, DARK, 0.3), outline=None)],
        "bounce_back": lambda: [ground(), L(m_ball(128, 100, 46), c, glow=c), L(m_arrow(200, 190, 200, 60, 14, 28), W)],
        "neuro_volley": lambda: [L(m_eye(70, 128, 110, 60), DARK), L(m_beam(100, 120, 256, 14), rgb("FFE84A"), glow=rgb("FFE84A")), L(m_beam(128, 120, 256, 14), rgb("FFE84A")), L(m_beam(156, 120, 256, 14), rgb("FFE84A"))],
        "dive_bomb": lambda: [ground(), L(m_arrow(60, 30, 150, 170, 26, 50), c, glow=c), L(m_ring(150, 200, 70, 10, 190, 350), rgb("FFE84A"), glow=rgb("FFE84A"))],
        "sonic_barrier": lambda: [L(m_waves(50, 128, 4, -40, 40, 14, 40), W, glow=ICE), L(m_wing(True), c)],
        "barrel_roll": lambda: [L(m_spiral(128, 128, 1.2, 104, 14), cl, glow=c), L(m_wing(True), c), L(m_wing(False), c)],
        "static_field": lambda: [L(m_ring(128, 128, 100, 10), rgb("FFE84A"), glow=rgb("FFE84A")), L(m_bolt(-60, 0, 0.35), rgb("FFE84A")), L(m_bolt(60, 0, 0.35), rgb("FFE84A")), L(m_circle(128, 128, 36), c)],
        "mach_ten": lambda: [L(m_star(128, 128, 110), rgb("FFE84A"), glow=rgb("FFE84A")), L(m_speedlines(40, 216, 10, 200, 5, 10), c), L(m_wing(True), c)],
        "wing_cut": lambda: [L(m_wing(True), c, glow=c), L(m_claws(0.4, 1), W)],
        "sky_snatch": lambda: [L(m_figure(0, 40, 0.45), STONE), L(m_arrow(128, 160, 128, 30, 22, 44), c, glow=c), L(m_wing(True), c)],
        "tail_sting": lambda: [L(m_tail(), c, glow=c), L(m_burst(180, 70, 50, 16, 8), rgb("FFE84A"), outline=None)],
        "power_dive": lambda: [ground(), L(m_arrow(128, 20, 128, 170, 28, 50), c, glow=c), L(m_ring(128, 200, 80, 10, 190, 350), rgb("FFE84A"))],
        "total_discharge": lambda: [L(m_burst(128, 128, 116, 40, 12), rgb("FFE84A"), glow=rgb("FFE84A")), L(m_circle(128, 128, 36), W)],
        "neuro_charge": lambda: [L(m_circle(128, 128, 70), c), L(m_bolt(0, 0, 0.6), rgb("FFE84A"), glow=rgb("FFE84A"))],
        "aerodynamics": lambda: [L(m_wing(True), c, glow=c), L(m_wing(False), c), L(m_speedlines(200, 240, 0, 256, 3, 8), W)],
        "aerophibian": lambda: [L(m_wave(150), WATER, glow=WATER), L(m_wing(True), c), L(m_plus(190, 70, 34, 20), W)],
        "frost_lance": lambda: [L(m_beam(128, 20, 236, 30), ICE, glow=ICE), L(m_crystal(210, 128, 90, 40, 1.57), W)],
        "frost_ground": lambda: [ground(), L(m_ring(128, 200, 100, 12, 190, 350), ICE, glow=ICE), L(m_snowflake(128, 110, 60, 12), W)],
        "wing_wrap": lambda: [L(m_wing(True), c, glow=ICE), L(m_wing(False), c), L(m_shield(0, 10, 0.5), ICE)],
        "ice_spikes": lambda: [ground(), L(m_spike(60, 200, 70, 30), ICE, glow=ICE), L(m_spike(120, 200, 110, 36), ICE), L(m_spike(190, 200, 150, 44), W)],
        "blizzard": lambda: [L(m_cloud(128, 70, 0.8), mix(ICE, W, 0.5), glow=ICE), L(m_snowflake(70, 180, 34, 8), W), L(m_snowflake(140, 200, 30, 8), W), L(m_snowflake(200, 170, 26, 8), W)],
        "ice_comet": lambda: [ground(), L(m_meteor(150, 90, 48), ICE, glow=ICE), L(m_speedlines(10, 70, 20, 110, 3, 10), W), L(m_burst(128, 210, 60, 20, 10), W, outline=None)],
        "cold_rush": lambda: [L(m_figure(-10, 30, 0.45), STONE), L(m_arrow(20, 128, 236, 128, 22, 44), c, glow=ICE)],
        "icicle_drop": lambda: [ground(), L(m_crystal(128, 100, 170, 50, 3.14159), ICE, glow=ICE)],
        "frost_grip": lambda: [L(m_claws(0.0, 3), c, glow=ICE), L(m_snowflake(190, 190, 44, 10), W)],
        "glacier_fall": lambda: [ground(), L(m_arrow(128, 20, 128, 160, 28, 50), c, glow=ICE), L(m_spike(50, 210, 50, 24), ICE), L(m_spike(206, 210, 50, 24), ICE)],
        "shatter_strike": lambda: [L(m_shards_ring(128, 128, 96, 8, 28), ICE, glow=ICE), L(m_crystal(128, 128, 120, 50), W)],
        "hypothermia": lambda: [L(m_snowflake(), ICE, glow=ICE), L(m_circle(128, 128, 30), W)],
        "frost_body": lambda: [L(m_shield(), c, glow=ICE), L(m_snowflake(128, 120, 50, 10), W)],
        "phantom_wings": lambda: [L(m_wing(True), mix(c, W, 0.4), glow=c, glow_alpha=0.6), L(m_wing(False), mix(c, W, 0.4)), L(m_arrow(20, 200, 236, 60, 14, 30), W)],
        "boulder_toss": lambda: [L(m_boulder(150, 100, 60), STONE, glow=EARTH), L(m_speedlines(60, 140, 10, 90, 3, 10), W), L(m_debris(150, 200, 60, 4, 14), EARTH)],
        "primal_roar": lambda: [L(m_mouth(), c, glow=c), L(m_waves(150, 128, 3, -45, 45, 14, 34), W)],
        "belly_flop": lambda: [ground(), L(m_figure(0, 10, 0.6), c, glow=c), L(m_cracks(128, 206), DARK, outline=None), L(m_ring(128, 200, 100, 10, 190, 350), W)],
        "extinction": lambda: [ground(), L(m_meteor(150, 80, 50), c, glow=FIRE, glow_alpha=0.7), L(m_ring(128, 210, 110, 10, 190, 350), W), L(m_ring(128, 210, 70, 10, 190, 350), W)],
        "dino_charge": lambda: [L(m_speedlines(60, 196, 10, 100, 4, 12), cl), L(m_fist(30, 0, 0.8), c, glow=c)],
        "hammer_drop": lambda: [ground(), L(m_fist(0, -20, 0.7, 3.14159), c, glow=c), L(m_cracks(128, 206), DARK, outline=None)],
        "bear_hug": lambda: [L(m_figure(0, 20, 0.45), STONE), L(m_arrow(20, 128, 90, 128, 22, 40), c, glow=c), L(m_arrow(236, 128, 166, 128, 22, 40), c)],
        "quake_landing": lambda: [ground(), L(m_footprint(), c, glow=c), L(m_waves(128, 200, 2, 200, 340, 12, 30), W)],
        "giant_toss": lambda: [L(m_figure(60, -40, 0.4), STONE), L(m_arrow(60, 200, 200, 60, 22, 44), c, glow=c)],
        "thick_hide": lambda: [L(m_shield(), c, glow=c), L(m_arrow(10, 60, 80, 100, 12, 24), STONE)],
        "earthshaker": lambda: [ground(), L(m_fist(0, -10, 0.6, 3.14159), c, glow=c), L(m_waves(128, 200, 3, 200, 340, 12, 30), W)],
        "spike_plates": lambda: [L(m_burst(128, 128, 118, 66, 10), W, glow=c), L(m_shield(0, 0, 0.7), c)],
        "vaxasaurian_growth": lambda: [L(m_figure(-60, 50, 0.35), mix(c, W, 0.4)), L(m_figure(40, 10, 0.75), c, glow=c)],
        "titan_clap": lambda: [L(m_fist(-50, 0, 0.6, 0.6), c, glow=c), L(m_fist(50, 0, 0.6, -0.6), c), L(m_burst(128, 128, 70, 20, 8), W, outline=None)],
    }


def alien_abilities() -> dict[str, tuple[str, tuple[int, int, int]]]:
    out = {}
    for f in sorted(ALIENS.glob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        color = rgb(data.get("color", "#FFFFFF"))
        slots = data.get("abilities", []) + data.get("traits", {}).get("passives", [])
        for slot in slots:
            t = slot.get("type", "")
            if t.startswith("kingdomomnitrix:"):
                out[t.split(":", 1)[1]] = (f.stem, color)
    return out


def build() -> dict[str, Image.Image]:
    result = {}
    for ability, (alien, color) in alien_abilities().items():
        spec = icons(color)
        if ability not in spec:
            raise KeyError(f"kein Symbol fuer {ability} ({alien})")
        result[ability] = compose(spec[ability]())
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--sheet", type=Path)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    names = alien_abilities()
    if args.check:
        missing = [n for n in names if not (OUT / f"{n}.png").is_file()]
        for n in missing:
            LOG.error("fehlt: ability_art/%s.png", n)
        LOG.info("Faehigkeits-Symbole: %d/%d vorhanden", len(names) - len(missing), len(names))
        return 1 if missing else 0
    try:
        images = build()
    except KeyError as exc:
        LOG.error("%s", exc)
        return 1
    OUT.mkdir(parents=True, exist_ok=True)
    for name, img in images.items():
        img.save(OUT / f"{name}.png", optimize=True)
    LOG.info("Faehigkeits-Symbole geschrieben: %d", len(images))
    if args.sheet:
        order = sorted(names.items(), key=lambda kv: kv[1][0])
        cols = 6
        rows = (len(order) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 72, rows * 72), (26, 30, 38, 255))
        for i, (n, _) in enumerate(order):
            sheet.alpha_composite(images[n], ((i % cols) * 72 + 4, (i // cols) * 72 + 4))
        sheet.save(args.sheet)
    return 0


if __name__ == "__main__":
    sys.exit(main())
