#!/usr/bin/env python3
"""Icons der Ruestungs-Materialkette, gerendert mit ``icon_painter`` (64x64, teils animiert).

Jede Funktion baut ein Icon aus Formen und Materialien; ``render(item)`` liefert (Bild, Frames). Animierte Icons
(Frames > 1) bekommen von ``generate_suit_materials`` eine .mcmeta-Datei.

    python tools/suit_icons.py --sheet ausgabe.png   # Kontaktbogen zur Kontrolle (wie im Inventar)
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import icon_painter as ip  # noqa: E402
from icon_painter import R, Icon, bevel, blur, dome, ellipse, line, mat, noise, poly, regular, rgb  # noqa: E402

FRAMES = 24

# Material -> (dunkel, mittel, hell, Leuchtfarbe)
PAL = {
    "taydenite": ("0A3F2A", "2FD08A", "C8FFE6", "5BFFB4"),
    "biotech": ("061A0C", "1F8A34", "B8FFC8", "4BFF6A"),
    "celestium": ("2A1F5C", "B6A8F0", "FFFFFF", "D8CCFF"),
    "raritanium": ("2C1452", "9A5CF0", "EEDCFF", "C08AFF"),
    "infernite": ("3A0604", "D2470E", "FFE7A0", "FF8A1A"),
}


def gem(name, kind="gem", spec=1.0):
    d, m, l, _ = PAL[name]
    return mat(kind, d, m, l, spec)


STEEL = mat("metal", "1C1F26", "6C7482", "E6ECF5")
GUNMETAL = mat("metal", "0B0C10", "2E323C", "8C96A8", 0.9)
GOLD = mat("metal", "4A2A06", "C08A2A", "FFF0B0")
ROCK = mat("rough", "15141A", "4A4652", "8E8898", 0.6)


# --- Rohstoffe -------------------------------------------------------------------------------------------------------

def rock_chunk(icon: Icon, seed: int, cx=128, cy=168, rx=92, ry=62, m=ROCK):
    rng = np.random.default_rng(seed)
    pts = []
    for i in range(10):
        a = math.pi * 2 * i / 10 + rng.uniform(-0.15, 0.15)
        r = rng.uniform(0.78, 1.0)
        pts.append((cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r))
    mask = poly(pts)
    h = bevel(mask, 26, "dome") * 1.0 + (ip.noise(seed, 6) - 0.5) * 0.35 * mask
    icon.surface(mask, m, height=blur(h, 2), strength=34)
    # Risse
    for _ in range(3):
        x0, y0 = cx + rng.uniform(-50, 50), cy + rng.uniform(-20, 30)
        crack = line([(x0, y0), (x0 + rng.uniform(-25, 25), y0 + rng.uniform(8, 22)),
                      (x0 + rng.uniform(-30, 30), y0 + rng.uniform(20, 36))], 2.5) * mask
        icon.put(crack, rgb("0A090E"), 0.8)
    return mask


def raw(name: str, seed: int) -> tuple[Image.Image, int]:
    icon = Icon()
    m = gem(name)
    glow = PAL[name][3]
    rock_chunk(icon, seed)
    for base, ang, ln, w in (((92, 160), -28, 92, 34), ((150, 158), 22, 118, 42), ((122, 172), -4, 70, 28)):
        tip = ip.prism(icon, base, ang, ln, w, m, glow_edge=PAL[name][2])
    icon.sparkle(tip[0], tip[1] + 8, 26, PAL[name][2], 0.1)
    icon.sparkle(78, 98, 16, PAL[name][2], 0.55)
    halo = glow if name != "celestium" else "C8BCFF"
    return ip.frames(icon, FRAMES, halo=halo, halo_strength=0.45), FRAMES


def raw_biotech() -> tuple[Image.Image, int]:
    """Mechamorph-Masse: schwarz-gruener Metallklumpen mit leuchtenden Schaltkreisbahnen."""
    icon = Icon()
    goo = mat("metal", "020604", "16301E", "9CFFB8", 1.2)
    mask = np.zeros((R, R))
    for cx, cy, r in ((128, 150, 72), (82, 170, 46), (176, 172, 44), (120, 98, 40), (160, 120, 34), (98, 206, 28)):
        mask = np.maximum(mask, ellipse(cx, cy, r, r * 0.92))
    mask = (blur(mask, 6) > 0.5).astype(float)
    h = ip.bevel(mask, 40, "dome")
    icon.surface(mask, goo, height=blur(h, 3), strength=60)
    circuit = np.zeros((R, R))
    for path in (((70, 168), (100, 168), (112, 150), (150, 150)), ((150, 150), (170, 130), (170, 112)),
                 ((112, 150), (112, 196), (138, 210)), ((150, 150), (190, 170)), ((100, 120), (126, 120), (138, 104))):
        circuit = np.maximum(circuit, line(path, 5))
    for x, y in ((70, 168), (170, 112), (138, 210), (190, 170), (138, 104), (100, 120)):
        circuit = np.maximum(circuit, ellipse(x, y, 8, 8))
    icon.glow(circuit * mask, "3CFF5A", "E8FFE8")
    icon.surface(ellipse(128, 150, 20, 20), goo, height=dome(128, 150, 20, 20), strength=30)
    icon.glow(ellipse(128, 150, 11, 11), "6BFF7A", "FFFFFF")
    icon.sparkle(96, 112, 18, "C8FFD0", 0.3)
    return ip.frames(icon, FRAMES, halo="3CFF5A", halo_strength=0.55), FRAMES


# --- Staub -----------------------------------------------------------------------------------------------------------

def dust(name: str, seed: int) -> tuple[Image.Image, int]:
    icon = Icon()
    d, m, l, g = PAL[name]
    pile_mat = mat("rough", d, m, l, 1.4)
    # Schale aus dunklem Metall
    dish = ellipse(128, 196, 104, 34)
    icon.surface(dish, GUNMETAL, height=bevel(dish, 18, "round"), strength=24)
    icon.surface(ellipse(128, 192, 92, 26), GUNMETAL, height=-dome(128, 192, 92, 26), strength=16)
    # Haufen: Kegel mit unruhiger Oberkante, darauf Klumpen und Koernung
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:R, 0:R]
    base, peak, rx = 198, 74, 92
    jag = blur((np.random.default_rng(seed + 1).random(R) - 0.5)[None, :].repeat(1, 0), 0)[0]
    jag = np.convolve(jag, np.ones(15) / 15, mode="same") * 60
    dx = np.abs(x - 128) / rx
    top = base - (base - peak) * np.clip(1 - dx, 0, 1) ** 1.35 + jag[None, :] * np.clip(1 - dx, 0, 1)
    mask = ((y >= top) & (y <= base + 8 - (dx ** 2) * 8) & (dx < 1)).astype(float)
    h = bevel(mask, 46, "dome")
    lumps = np.zeros((R, R))
    for _ in range(70):
        lx, ly, lr = rng.uniform(44, 212), rng.uniform(80, 200), rng.uniform(6, 15)
        lumps = np.maximum(lumps, dome(lx, ly, lr, lr * 0.8) * 0.25)
    grain = ip.noise(seed, 48, 2)
    h = h + lumps * mask + (grain - 0.5) * 0.08
    shade_t = np.clip(grain * 1.1 + lumps * 1.5 - 0.15, 0, 1)
    icon.surface(mask, pile_mat, height=blur(h, 1), strength=80, extra=shade_t)
    mound = mask
    # glitzernde Koerner
    glints = np.zeros((R, R))
    for _ in range(46):
        gx, gy = rng.uniform(50, 206), rng.uniform(86, 196)
        if mound[int(gy), int(gx)] > 0.5:
            glints = np.maximum(glints, ellipse(gx, gy, 2.6, 2.6))
    icon.glow(glints, l, "FFFFFF")
    for _ in range(14):
        gx, gy = rng.uniform(26, 230), rng.uniform(196, 222)
        icon.surface(ellipse(gx, gy, 5, 4), pile_mat, height=dome(gx, gy, 5, 4), strength=8)
    icon.sparkle(104, 112, 24, l, 0.0)
    icon.sparkle(160, 150, 16, l, 0.45)
    icon.sparkle(132, 86, 14, l, 0.75)
    return ip.frames(icon, FRAMES, halo=g, halo_strength=0.3), FRAMES


# --- veredelte Materialien -----------------------------------------------------------------------------------------

def cut_gem(name: str) -> tuple[Image.Image, int]:
    """Brillant-Schliff in Schraegansicht: Tafel, Krone, Pavillon."""
    icon = Icon()
    m = gem(name, spec=1.4)
    d, mid, l, g = PAL[name]
    cx, top, gird, bot = 128, 56, 112, 222
    half = 104
    tab = 50
    crown = [(cx - half, gird), (cx - tab - 18, top), (cx + tab + 18, top), (cx + half, gird)]
    # Pavillon: Dreiecke zur Spitze
    xs = np.linspace(cx - half, cx + half, 7)
    for i in range(6):
        nx = (xs[i] + xs[i + 1]) / 2 - cx
        n = ip.flat_normal(nx / half * 0.9, 0.55, 0.7)
        icon.surface(poly([(xs[i], gird), (xs[i + 1], gird), (cx, bot)]), m, normal=n, extra=0.25 + 0.12 * (i % 2))
    # Krone: Facetten zwischen Tafel und Rundiste
    tl, tr = (cx - tab, top + 4), (cx + tab, top + 4)
    pts_top = [(cx - tab - 18, top), tl, (cx, top - 2), tr, (cx + tab + 18, top)]
    pts_gird = [(cx - half, gird), (cx - half * 0.5, gird), (cx, gird), (cx + half * 0.5, gird), (cx + half, gird)]
    for i in range(4):
        quad = [pts_top[i], pts_top[i + 1], pts_gird[i + 1], pts_gird[i]]
        nx = ((pts_top[i][0] + pts_top[i + 1][0]) / 2 - cx) / half
        icon.surface(poly(quad), m, normal=ip.flat_normal(nx * 0.8, -0.55, 0.75), extra=0.6 + 0.15 * (i % 2))
    # Tafel
    table = poly([(cx - tab - 18, top), (cx + tab + 18, top), (cx + tab, top + 26), (cx - tab, top + 26)])
    icon.surface(table, m, normal=ip.flat_normal(0, -0.2, 1), extra=0.9)
    inner = poly([(cx - tab + 6, top + 6), (cx + tab - 6, top + 6), (cx + tab - 18, top + 20), (cx - tab + 18, top + 20)])
    icon.put(inner, rgb(l), 0.25, m)
    # Facettenkanten
    edges = np.zeros((R, R))
    for i in range(5):
        edges = np.maximum(edges, line([pts_top[i], pts_gird[i]], 1.6))
    edges = np.maximum(edges, line([(cx - half, gird), (cx + half, gird)], 2.2))
    for x in xs:
        edges = np.maximum(edges, line([(x, gird), (cx, bot)], 1.4))
    icon.put(edges * np.clip(crown_mask(crown, xs, gird, bot, cx), 0, 1), rgb(l), 0.5, m)
    icon.sparkle(cx - 40, top + 10, 34, "FFFFFF", 0.0)
    icon.sparkle(cx + 58, gird + 18, 18, l, 0.5)
    return ip.frames(icon, FRAMES, halo=g, halo_strength=0.4), FRAMES


def crown_mask(crown, xs, gird, bot, cx):
    return np.maximum(poly(crown), poly([(xs[0], gird), (xs[-1], gird), (cx, bot)]))


def ingot(name: str, rune: str) -> tuple[Image.Image, int]:
    icon = Icon()
    d, m, l, g = PAL[name]
    metal = mat("metal", d, m, l, 1.3)
    # Barren in Schraegansicht: Deckflaeche, Vorderflaeche, rechte Seite
    top = [(52, 104), (164, 70), (214, 100), (104, 138)]
    front = [(52, 104), (104, 138), (104, 186), (40, 148)]
    side = [(104, 138), (214, 100), (226, 144), (104, 186)]
    icon.surface(poly(side), metal, normal=ip.flat_normal(0.35, 0.35, 0.85))
    icon.surface(poly(front), metal, normal=ip.flat_normal(-0.55, 0.15, 0.82))
    tmask = poly(top)
    icon.surface(tmask, metal, height=bevel(tmask, 14, "round") * 0.6, strength=30)
    # Fasenkanten
    edges = line([(52, 104), (104, 138), (214, 100)], 3) + line([(104, 138), (104, 186)], 3)
    icon.put(np.clip(edges, 0, 1), rgb(l), 0.65, metal)
    # Gravur auf der Deckflaeche
    cx, cy = 132, 104
    if rune == "star":
        pts = []
        for i in range(8):
            r = 24 if i % 2 == 0 else 9
            a = -math.pi / 2 + i * math.pi / 4
            pts.append((cx + math.cos(a) * r * 1.3, cy + math.sin(a) * r * 0.7))
        glyph = np.clip(line(pts + [pts[0]], 5), 0, 1)
    else:
        glyph = np.clip(line([(cx - 26, cy + 4), (cx, cy - 12), (cx + 26, cy + 4), (cx, cy + 18), (cx - 26, cy + 4)], 6)
                        + line([(cx - 8, cy + 3), (cx + 8, cy + 3)], 5), 0, 1)
    groove = np.clip(blur(glyph, 3) * 2.2, 0, 1)
    icon.put(groove, rgb(d), 0.95)
    icon.glow(glyph, g, "FFFFFF")
    icon.sparkle(164, 74, 26, "FFFFFF", 0.2)
    icon.sparkle(54, 106, 14, l, 0.7)
    return ip.frames(icon, FRAMES, halo=g, halo_strength=0.22), FRAMES


def gel_vial() -> tuple[Image.Image, int]:
    icon = Icon()
    glass = mat("glass", "0C2A18", "6ACF8A", "F0FFF4", 1.4)
    liquid = mat("emit", "0A4A1A", "1EE84A", "C8FFC8")
    cx = 128
    body = ip.rounded(cx - 46, 70, cx + 46, 222, 40)
    # Fluessigkeit (unten 70 %)
    y, x = np.mgrid[0:R, 0:R]
    fill = body * (y > 112)
    t = np.clip((y - 112) / 110, 0, 1)
    icon.put(body, rgb("08140C"), 0.85)
    swirl = ip.noise(77, 5, 3)
    icon.put(fill, ip.shade(liquid, np.dstack([np.zeros((R, R)), np.zeros((R, R)), np.ones((R, R))]),
                            np.clip(0.9 - t * 0.7 + (swirl - 0.5) * 0.5, 0, 1)), 1.0, None, 1.0)
    icon.put(ellipse(cx, 112, 44, 9) * body, rgb("B8FFC0"), 0.85, None, 1.0)
    rng = np.random.default_rng(11)
    for _ in range(9):
        bx, by, br = rng.uniform(cx - 30, cx + 30), rng.uniform(126, 206), rng.uniform(3, 8)
        icon.put(ring(bx, by, br, br, 2), rgb("E8FFE8"), 0.85, None, 1.0)
        icon.put(ellipse(bx - br * 0.3, by - br * 0.3, br * 0.3, br * 0.3), rgb("FFFFFF"), 0.9, None, 1.0)
    # Glas darueber
    icon.surface(body, glass, height=bevel(body, 30, "dome"), strength=46, opacity=0.38)
    streak = ip.rounded(cx - 34, 84, cx - 24, 200, 5)
    icon.put(streak, rgb("FFFFFF"), 0.55)
    icon.put(ip.rounded(cx + 26, 130, cx + 31, 190, 3), rgb("FFFFFF"), 0.3)
    # Kappe und Hals
    neck = ip.rounded(cx - 30, 46, cx + 30, 76, 6)
    icon.surface(neck, STEEL, height=bevel(neck, 8), strength=20)
    cap = ip.rounded(cx - 40, 26, cx + 40, 54, 10)
    icon.surface(cap, GUNMETAL, height=bevel(cap, 10), strength=22)
    icon.glow(ip.rounded(cx - 40, 38, cx + 40, 43, 2), "3CFF5A", "E8FFE8")
    icon.sparkle(cx - 28, 92, 22, "FFFFFF", 0.15)
    return ip.frames(icon, FRAMES, halo="3CFF5A", halo_strength=0.6), FRAMES


def ring(cx, cy, rx, ry, w):
    return ip.ring(cx, cy, rx, ry, w)


def weave() -> tuple[Image.Image, int]:
    """Biotech-Gewebe: gefaltete Stoffbahn mit Sechseck-Fasernetz."""
    icon = Icon()
    cloth = mat("rough", "071810", "2A6A40", "9AF0B4", 1.3)
    sheet = [(28, 118), (132, 52), (230, 112), (126, 190)]
    mask = poly(sheet)
    y, x = np.mgrid[0:R, 0:R]
    wave = np.sin((x + y * 0.4) / 22.0) * 0.18
    icon.surface(mask, cloth, height=bevel(mask, 10) * 0.5 + wave, strength=40)
    # zweite Lage (umgeschlagen) unten
    lower = poly([(28, 118), (126, 190), (126, 216), (28, 146)])
    icon.surface(lower, cloth, normal=ip.flat_normal(-0.45, 0.3, 0.85))
    lower2 = poly([(126, 190), (230, 112), (230, 138), (126, 216)])
    icon.surface(lower2, cloth, normal=ip.flat_normal(0.35, 0.4, 0.85))
    # Sechseck-Netz in Projektion
    net = np.zeros((R, R))
    for row in range(-3, 4):
        for col in range(-4, 5):
            u, v = col * 1.5, row * math.sqrt(3) + (col % 2) * math.sqrt(3) / 2
            hexpts = []
            for k in range(7):
                a = k * math.pi / 3
                hu, hv = u + math.cos(a), v + math.sin(a)
                px = 129 + hu * 14 + hv * -14 * 0.95
                py = 121 + hu * 8.2 + hv * 8.0
                hexpts.append((px, py))
            net = np.maximum(net, line(hexpts, 2.6))
    icon.glow(net * mask, "3CFF5A", "D8FFD8")
    seam = line([(28, 118), (126, 190), (230, 112)], 3)
    icon.glow(seam, "6BFF7A", "FFFFFF")
    icon.sparkle(132, 58, 22, "D8FFD8", 0.3)
    return ip.frames(icon, FRAMES, halo="3CFF5A", halo_strength=0.4), FRAMES


def infernite_core() -> tuple[Image.Image, int]:
    icon = Icon()
    cx, cy, r = 128, 132, 70
    sphere = ellipse(cx, cy, r, r)
    n = ip.normals_from(dome(cx, cy, r, r), 60)
    lava = ip.noise(31, 5, 4)
    veins = np.clip(1 - np.abs(lava - 0.5) * 9, 0, 1)
    crust = mat("rough", "1A0402", "4A1206", "FF8A3A", 0.5)
    icon.put(sphere, ip.shade(crust, n), 1.0)
    hot = np.clip(veins + dome(cx, cy, r, r) ** 3 * 0.35, 0, 1)
    icon.put(sphere * hot, ip.lerp(rgb("FF5A0A")[None, None], rgb("FFF2B0")[None, None], hot[..., None]), 1.0, None, 1.0)
    # Kaefig: zwei Ringe, Klammern
    for rx, ry, w in ((96, 30, 12), (30, 96, 12)):
        rmask = ring(cx, cy, rx, ry, w)
        y, x = np.mgrid[0:R, 0:R]
        front = (y > cy) if rx > ry else (x < cx)
        icon.surface(rmask * front, GOLD, height=bevel(rmask, 5), strength=16)
    for a in range(4):
        ang = math.pi / 4 + a * math.pi / 2
        bx, by = cx + math.cos(ang) * 66, cy + math.sin(ang) * 66
        clamp = poly(regular(bx, by, 16, 4, ang))
        icon.surface(clamp, GUNMETAL, height=bevel(clamp, 6), strength=18)
        icon.glow(ellipse(bx, by, 4, 4), "FF8A1A", "FFF2B0")
    icon.sparkle(cx - 26, cy - 30, 28, "FFF2B0", 0.0)
    return ip.frames(icon, FRAMES, halo="FF6A10", halo_strength=0.85), FRAMES


def star_matrix() -> tuple[Image.Image, int]:
    icon = Icon()
    cx, cy = 128, 128
    cosmic = mat("cosmic", "06030F", "3A1C7A", "D8CCFF")
    edge = rgb("D8CCFF")
    # hinterer Ringteil
    y, x = np.mgrid[0:R, 0:R]
    orbit = ring(cx, cy + 6, 116, 34, 7)
    icon.glow(orbit * (y < cy + 6), "8A6AFF", "FFFFFF", 0.8)
    # Oktaeder: vier Facetten oben, vier unten (Schraegansicht)
    top, bottom = (cx, 22), (cx, 236)
    left, right, front = (cx - 80, cy), (cx + 80, cy), (cx - 14, cy + 22)
    neb = ip.noise(5, 3, 4)
    for tri, nrm in (((top, left, front), (-0.6, -0.5, 0.7)), ((top, front, right), (0.45, -0.55, 0.75)),
                     ((bottom, front, left), (-0.55, 0.45, 0.75)), ((bottom, right, front), (0.5, 0.5, 0.7))):
        icon.surface(poly(tri), cosmic, normal=ip.flat_normal(*nrm), extra=np.clip(neb * 1.3 - 0.2, 0, 1))
    body = np.maximum(poly((top, left, bottom, right)), 0)
    rng = np.random.default_rng(3)
    stars = np.zeros((R, R))
    for _ in range(60):
        sx, sy = rng.uniform(50, 206), rng.uniform(30, 226)
        s = rng.uniform(1.2, 3.0)
        stars = np.maximum(stars, ellipse(sx, sy, s, s))
    icon.glow(stars * body, "C8BCFF", "FFFFFF")
    edges = np.zeros((R, R))
    for a, b in ((top, left), (top, right), (bottom, left), (bottom, right), (top, front), (bottom, front),
                 (left, front), (front, right)):
        edges = np.maximum(edges, line([a, b], 2.4))
    icon.put(edges, edge, 0.85, None, 0.6)
    # vorderer Ringteil
    icon.glow(orbit * (y >= cy + 6), "8A6AFF", "FFFFFF")
    icon.sparkle(cx + 112, cy + 12, 22, "FFFFFF", 0.0)
    icon.sparkle(cx - 30, 70, 20, "FFFFFF", 0.4)
    icon.sparkle(cx + 24, 170, 14, "D8CCFF", 0.7)
    return ip.frames(icon, FRAMES, halo="7A5AFF", halo_strength=0.7), FRAMES


# --- Panzerplatten -------------------------------------------------------------------------------------------------

def taydenite_plate() -> tuple[Image.Image, int]:
    icon = Icon()
    frame_pts = regular(128, 128, 112, 6, -math.pi / 2, 0.98)
    fmask = poly(frame_pts)
    icon.surface(fmask, STEEL, height=bevel(fmask, 18), strength=30)
    inner = inset(frame_pts, 0.78)
    imask = poly(inner)
    icon.surface(imask, GUNMETAL, height=-bevel(imask, 6), strength=20)
    m = gem("taydenite", spec=1.5)
    crystal = inset(frame_pts, 0.66)
    c = (128, 128)
    for i in range(6):
        a, b = crystal[i], crystal[(i + 1) % 6]
        mx, my = (a[0] + b[0]) / 2 - 128, (a[1] + b[1]) / 2 - 128
        ln = math.hypot(mx, my)
        icon.surface(poly([a, b, c]), m, normal=ip.flat_normal(mx / ln * 0.7, my / ln * 0.7, 0.75),
                     extra=0.5 + 0.2 * (i % 2))
    edges = np.zeros((R, R))
    for p in crystal:
        edges = np.maximum(edges, line([p, c], 2.2))
    edges = np.maximum(edges, line(crystal + [crystal[0]], 2.6))
    icon.glow(edges, "5BFFB4", "E8FFF4")
    for p in inset(frame_pts, 0.89):
        ip.bolt(icon, p[0], p[1], 9, STEEL)
    icon.sparkle(112, 92, 30, "FFFFFF", 0.1)
    return ip.frames(icon, FRAMES, halo="3AE89A", halo_strength=0.3), FRAMES


def inset(points, k):
    return ip.inset(points, k)


def magnaplate() -> tuple[Image.Image, int]:
    icon = Icon()
    metal = mat("metal", "1E1238", "8A6AC0", "F2E8FF", 1.3)
    pts = [(46, 30), (210, 30), (232, 52), (232, 170), (128, 232), (24, 170), (24, 52)]
    mask = poly(pts)
    icon.surface(mask, metal, height=bevel(mask, 20), strength=34)
    # zwei Rillen und Mittelpaneel
    for yy in (86, 150):
        groove = poly([(36, yy), (220, yy), (220, yy + 7), (36, yy + 7)]) * mask
        icon.surface(groove, GUNMETAL, normal=ip.flat_normal(0, -0.6, 0.8))
    panel = poly([(80, 100), (176, 100), (176, 140), (128, 168), (80, 140)])
    icon.surface(panel, metal, height=bevel(panel, 10) * 1.0, strength=30)
    # Raritanium-Raute
    m = gem("raritanium", spec=1.5)
    diamond = [(128, 104), (152, 132), (128, 160), (104, 132)]
    for i in range(4):
        a, b = diamond[i], diamond[(i + 1) % 4]
        mx, my = (a[0] + b[0]) / 2 - 128, (a[1] + b[1]) / 2 - 132
        icon.surface(poly([a, b, (128, 132)]), m, normal=ip.flat_normal(mx / 24, my / 24, 0.9), extra=0.6)
    icon.glow(line(diamond + [diamond[0]], 2.4), "C08AFF", "FFFFFF")
    for x, y in ((48, 52), (208, 52), (48, 168), (208, 168), (128, 54)):
        ip.bolt(icon, x, y, 11, STEEL)
    icon.glow(line([(40, 196), (128, 214)], 3) + line([(128, 214), (216, 196)], 3), "C08AFF", "FFFFFF", 0.9)
    icon.sparkle(70, 40, 28, "FFFFFF", 0.0)
    return ip.frames(icon, FRAMES, halo="A86AFF", halo_strength=0.25), FRAMES


def carbonox() -> tuple[Image.Image, int]:
    icon = Icon()
    carbon = mat("metal", "050507", "22252C", "8A92A6", 1.1)
    pts = [(40, 26), (216, 26), (236, 70), (200, 186), (128, 236), (56, 186), (20, 70)]
    mask = poly(pts)
    y, x = np.mgrid[0:R, 0:R]
    weave_h = (np.sign(np.sin((x + y) / 7.0)) * np.sign(np.sin((x - y) / 7.0)) * 0.04)
    icon.surface(mask, carbon, height=bevel(mask, 18) + weave_h, strength=36)
    # Chevron-Paneele
    for off in (0, 44):
        chev = poly([(56, 70 + off), (128, 112 + off), (200, 70 + off), (200, 92 + off), (128, 134 + off), (56, 92 + off)])
        icon.surface(chev * mask, GUNMETAL, height=bevel(chev, 7), strength=24)
        icon.glow(line([(60, 80 + off), (128, 120 + off), (196, 80 + off)], 3.2) * mask, "FF2A2A", "FFD0C0")
    # Kante rot
    edge = np.clip(bevel(mask, 4) < 0.6, 0, 1) * mask
    icon.glow(blur(edge, 1) * (y > 60), "FF2A2A", "FF9A8A", 0.7)
    # Kern
    icon.surface(ellipse(128, 196, 16, 16), GUNMETAL, height=dome(128, 196, 16, 16), strength=16)
    icon.glow(ellipse(128, 196, 9, 9), "FF2A2A", "FFFFFF")
    for x0, y0 in ((52, 44), (204, 44)):
        ip.bolt(icon, x0, y0, 10, STEEL)
    icon.sparkle(96, 40, 24, "FFFFFF", 0.3)
    icon.outline = rgb("1A0406")
    return ip.frames(icon, FRAMES, halo="FF2020", halo_strength=0.35, shine_color="FFD8D8"), FRAMES


def hellfire() -> tuple[Image.Image, int]:
    icon = Icon()
    char = mat("metal", "0A0302", "3A1A10", "C88A5A", 1.0)
    pts = [(18, 10), (64, 52), (128, 36), (192, 52), (238, 10), (224, 92), (196, 182), (128, 240), (60, 182), (32, 92)]
    mask = poly(pts)
    icon.surface(mask, char, height=bevel(mask, 20) + (noise(41, 8) - 0.5) * 0.2 * mask, strength=38)
    # Lavarisse
    rng = np.random.default_rng(9)
    cracks = np.zeros((R, R))
    for _ in range(7):
        x0, y0 = rng.uniform(60, 196), rng.uniform(70, 200)
        path = [(x0, y0)]
        for _ in range(4):
            x0 += rng.uniform(-26, 26)
            y0 += rng.uniform(-20, 20)
            path.append((x0, y0))
        cracks = np.maximum(cracks, line(path, rng.uniform(3, 5.5)))
    cracks *= blur(mask, 1) * (bevel(mask, 20) > 0.5)
    icon.glow(cracks, "FF5A0A", "FFF2A0")
    # Mittelstein
    m = gem("infernite", spec=1.6)
    gem_pts = [(128, 92), (162, 128), (128, 172), (94, 128)]
    for i in range(4):
        a, b = gem_pts[i], gem_pts[(i + 1) % 4]
        mx, my = (a[0] + b[0]) / 2 - 128, (a[1] + b[1]) / 2 - 130
        icon.surface(poly([a, b, (128, 130)]), m, normal=ip.flat_normal(mx / 30, my / 30, 0.9), extra=0.7)
    icon.glow(line(gem_pts + [gem_pts[0]], 3), "FF8A1A", "FFF2A0")
    frame = poly([(128, 80), (176, 128), (128, 184), (80, 128)]) - poly(gem_pts)
    icon.surface(np.clip(frame, 0, 1), GOLD, height=bevel(np.clip(frame, 0, 1), 6), strength=18)
    icon.sparkle(128, 98, 30, "FFF2A0", 0.0)
    icon.sparkle(30, 18, 16, "FFB060", 0.5)
    icon.sparkle(226, 18, 16, "FFB060", 0.75)
    icon.outline = rgb("200402")
    return ip.frames(icon, FRAMES, halo="FF5A0A", halo_strength=0.7, shine_color="FFE0B0"), FRAMES


# --- Schluesselgegenstaende des Fusionsaltars ----------------------------------------------------------------------

def resonance_shard() -> tuple[Image.Image, int]:
    """Resonanz-Splitter: schwebende Kristallsplitter mit Resonanzwellen."""
    icon = Icon()
    m = mat("gem", "06402E", "38E8B0", "D8FFF0", 1.6)
    y, x = np.mgrid[0:R, 0:R]
    # Resonanzwellen: hintere Ringhaelfte vor, vordere nach den Kristallen
    for r in (96, 74):
        icon.glow(ip.ring(128, 150, r, r * 0.36, 4) * (y < 150), "38E8B0", "E8FFF8", 0.7)
    ip.prism(icon, (128, 212), 0, 180, 54, m, glow_edge="D8FFF0")
    ip.prism(icon, (82, 190), -24, 104, 34, m, glow_edge="D8FFF0")
    ip.prism(icon, (176, 196), 26, 92, 30, m, glow_edge="D8FFF0")
    for r in (96, 74):
        icon.glow(ip.ring(128, 150, r, r * 0.36, 4) * (y >= 150), "38E8B0", "E8FFF8", 0.9)
    icon.sparkle(128, 40, 34, "FFFFFF", 0.0)
    icon.sparkle(70, 96, 18, "D8FFF0", 0.5)
    return ip.frames(icon, FRAMES, halo="38E8B0", halo_strength=0.7), FRAMES


def nefarious_chip() -> tuple[Image.Image, int]:
    """Nefarious-Chip: Prozessor mit Pins, roten Leiterbahnen und Totenkopf-Kern."""
    icon = Icon()
    board = mat("metal", "07060A", "2A2232", "8A7A9A", 1.0)
    pins = mat("metal", "4A3A10", "C8A040", "FFF0B0", 1.2)
    for i in range(6):
        c = 52 + i * 30.4
        for (x0, y0, x1, y1) in ((c - 6, 18, c + 6, 48), (c - 6, 208, c + 6, 238), (18, c - 6, 48, c + 6), (208, c - 6, 238, c + 6)):
            pm = ip.rounded(x0, y0, x1, y1, 3)
            icon.surface(pm, pins, height=bevel(pm, 4), strength=8)
    body = ip.rounded(40, 40, 216, 216, 18)
    icon.surface(body, board, height=bevel(body, 16), strength=30)
    traces = np.zeros((R, R))
    for path in (((60, 70), (100, 70), (112, 96)), ((196, 70), (156, 70), (144, 96)), ((60, 186), (100, 186), (112, 160)),
                 ((196, 186), (156, 186), (144, 160)), ((60, 128), (92, 128)), ((196, 128), (164, 128))):
        traces = np.maximum(traces, line(path, 4))
    for x, y in ((60, 70), (196, 70), (60, 186), (196, 186), (60, 128), (196, 128)):
        traces = np.maximum(traces, ellipse(x, y, 6, 6))
    icon.glow(traces, "FF2A4A", "FFD0D8")
    die = ip.rounded(92, 92, 164, 164, 10)
    icon.surface(die, GUNMETAL, height=bevel(die, 8), strength=20)
    skull = np.clip(ellipse(128, 122, 22, 20) + ip.rounded(114, 128, 142, 148, 4), 0, 1)
    icon.glow(skull, "B04AFF", "F0D8FF")
    icon.put(ellipse(119, 121, 6, 6) + ellipse(137, 121, 6, 6), rgb("12081A"), 1.0)
    icon.sparkle(70, 52, 22, "FFFFFF", 0.2)
    return ip.frames(icon, FRAMES, halo="B04AFF", halo_strength=0.4), FRAMES


def chaos_heart() -> tuple[Image.Image, int]:
    """Chaos-Herz: schwarzer Herzkristall mit pulsierenden Rissen."""
    icon = Icon()
    m = mat("gem", "050208", "3A0A2A", "FF7AB0", 1.6)
    heart = np.clip(ellipse(94, 98, 54, 52) + ellipse(162, 98, 54, 52)
                    + poly([(44, 116), (212, 116), (128, 226)]), 0, 1)
    icon.surface(heart, m, height=bevel(heart, 40, "dome"), strength=70, extra=0.3)
    cracks = np.zeros((R, R))
    rng = np.random.default_rng(13)
    for _ in range(8):
        x0, y0 = 128 + rng.uniform(-20, 20), 130 + rng.uniform(-20, 20)
        path = [(x0, y0)]
        for _ in range(4):
            x0 += rng.uniform(-28, 28)
            y0 += rng.uniform(-24, 24)
            path.append((x0, y0))
        cracks = np.maximum(cracks, line(path, rng.uniform(3, 5)))
    icon.glow(cracks * (bevel(heart, 20) > 0.4), "FF2A6A", "FFE0F0")
    icon.glow(ellipse(128, 132, 16, 16), "FF4A8A", "FFFFFF")
    icon.sparkle(96, 76, 26, "FFD0E0", 0.0)
    icon.outline = rgb("1A0410")
    return ip.frames(icon, FRAMES, halo="FF1A5A", halo_strength=0.9, shine_color="FFC0D8"), FRAMES


def cryo_cell() -> tuple[Image.Image, int]:
    """Kryo-Zelle: Druckkapsel mit eisblauem Kuehlmittel und Frost."""
    icon = Icon()
    glass = mat("glass", "0A2030", "8AD8FF", "F0FCFF", 1.5)
    liquid = mat("emit", "0A3A5A", "3AC8FF", "E8FCFF")
    body = ip.rounded(78, 58, 178, 206, 26)
    icon.put(body, rgb("06121A"), 0.9)
    y, x = np.mgrid[0:R, 0:R]
    swirl = ip.noise(91, 4, 3)
    flat = np.dstack([np.zeros((R, R)), np.zeros((R, R)), np.ones((R, R))])
    icon.put(body * (y > 84), ip.shade(liquid, flat, np.clip(0.9 - (y - 84) / 160 + (swirl - 0.5) * 0.6, 0, 1)), 1.0, None, 1.0)
    rng = np.random.default_rng(5)
    for _ in range(14):
        fx, fy, fr = rng.uniform(86, 170), rng.uniform(96, 196), rng.uniform(3, 7)
        icon.put(ip.poly(regular(fx, fy, fr, 6, rng.uniform(0, 1))), rgb("F0FCFF"), 0.7, None, 1.0)
    icon.surface(body, glass, height=bevel(body, 26, "dome"), strength=40, opacity=0.35)
    icon.put(ip.rounded(90, 74, 100, 190, 5), rgb("FFFFFF"), 0.55)
    for y0, y1 in ((30, 62), (202, 232)):
        cap = ip.rounded(70, y0, 186, y1, 8)
        icon.surface(cap, STEEL, height=bevel(cap, 8), strength=20)
        icon.glow(ip.rounded(70, (y0 + y1) / 2 - 3, 186, (y0 + y1) / 2 + 3, 2), "3AC8FF", "E8FCFF")
    icon.sparkle(100, 82, 22, "FFFFFF", 0.3)
    return ip.frames(icon, FRAMES, halo="3AC8FF", halo_strength=0.55), FRAMES


def fusion_codex() -> tuple[Image.Image, int]:
    """Fusions-Kodex: dunkles Buch mit Metallecken und leuchtendem Sanduhr-Emblem."""
    icon = Icon()
    cover = mat("rough", "0A0E14", "1E2A3A", "6A8AAA", 1.1)
    pages = mat("rough", "8A8470", "E8E0C8", "FFFFFF", 0.4)
    icon.surface(poly([(60, 40), (214, 52), (214, 226), (60, 214)]), pages, normal=ip.flat_normal(0.4, 0.1, 0.9))
    for i in range(5):
        icon.put(line([(206, 60 + i * 34), (206, 70 + i * 34)], 1.5), rgb("8A8470"), 0.6)
    book = poly([(38, 30), (196, 40), (196, 220), (38, 228)])
    icon.surface(book, cover, height=bevel(book, 14) + (ip.noise(3, 20, 2) - 0.5) * 0.05, strength=30)
    spine = poly([(38, 30), (58, 31), (58, 227), (38, 228)])
    icon.surface(spine, cover, normal=ip.flat_normal(-0.5, 0, 0.85))
    for cx, cy, a in ((48, 40, 0), (186, 50, 1), (186, 210, 2), (48, 218, 3)):
        corner = ip.poly(regular(cx, cy, 16, 4, math.pi / 4))
        icon.surface(corner, GOLD, height=bevel(corner, 5), strength=14)
    ring_m = ip.ring(120, 130, 46, 46, 9)
    icon.surface(ring_m, STEEL, height=bevel(ring_m, 4), strength=14)
    glass_hour = np.clip(poly([(96, 98), (144, 98), (120, 130)]) + poly([(96, 162), (144, 162), (120, 130)]), 0, 1)
    icon.glow(glass_hour, "3AE8FF", "FFFFFF")
    icon.sparkle(150, 96, 22, "E8FFFF", 0.4)
    return ip.frames(icon, FRAMES, halo="3AE8FF", halo_strength=0.35), FRAMES


# --- Verzeichnis ---------------------------------------------------------------------------------------------------

BUILDERS = {
    "raw_taydenite": lambda: raw("taydenite", 3),
    "taydenite_dust": lambda: dust("taydenite", 4),
    "taydenite_crystal": lambda: cut_gem("taydenite"),
    "taydenite_plate": taydenite_plate,
    "raw_biotech": raw_biotech,
    "biotech_dust": lambda: dust("biotech", 5),
    "biotech_gel": gel_vial,
    "biotech_weave": weave,
    "raw_celestium": lambda: raw("celestium", 6),
    "celestium_dust": lambda: dust("celestium", 7),
    "celestium_ingot": lambda: ingot("celestium", "star"),
    "star_matrix": star_matrix,
    "raritanium_dust": lambda: dust("raritanium", 8),
    "raritanium_ingot": lambda: ingot("raritanium", "chevron"),
    "magnaplate_plating": magnaplate,
    "carbonox_plate": carbonox,
    "raw_infernite": lambda: raw("infernite", 9),
    "infernite_dust": lambda: dust("infernite", 10),
    "infernite_core": infernite_core,
    "hellfire_plate": hellfire,
    "resonance_shard": resonance_shard,
    "nefarious_chip": nefarious_chip,
    "chaos_heart": chaos_heart,
    "cryo_cell": cryo_cell,
    "fusion_codex": fusion_codex,
}


def render(item: str) -> tuple[Image.Image, int]:
    return BUILDERS[item]()


def sheet(path: Path, items: list[str], frame: int = 0) -> None:
    """Kontaktbogen: Inventarfelder in 2-facher (32 px) und 4-facher (64 px) GUI-Groesse."""
    cols = 5
    rows = math.ceil(len(items) / cols)
    cell = 76
    img = Image.new("RGB", (cols * cell * 2 + 20, rows * cell + 10), (198, 198, 198))
    d = ImageDraw.Draw(img)
    for i, item in enumerate(items):
        icon, n = render(item)
        f = icon.crop((0, 64 * (frame % n), 64, 64 * (frame % n) + 64))
        r, c = divmod(i, cols)
        for k, (size, ox) in enumerate(((32, 10), (64, cols * cell + 10))):
            x, y = ox + c * cell, 6 + r * cell
            s = size + 4
            d.rectangle((x, y, x + s, y + s), fill=(139, 139, 139))
            d.line((x, y, x + s, y), fill=(55, 55, 55))
            d.line((x, y, x, y + s), fill=(55, 55, 55))
            d.line((x, y + s, x + s, y + s), fill=(255, 255, 255))
            d.line((x + s, y, x + s, y + s), fill=(255, 255, 255))
            scaled = f.resize((size, size), Image.NEAREST)
            img.paste(scaled, (x + 2, y + 2), scaled)
    img.save(path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sheet", type=Path, required=True)
    parser.add_argument("--frame", type=int, default=0)
    parser.add_argument("--only", nargs="*")
    args = parser.parse_args(argv)
    sheet(args.sheet, args.only or list(BUILDERS), args.frame)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
