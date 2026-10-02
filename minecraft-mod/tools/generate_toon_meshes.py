#!/usr/bin/env python3
"""Erzeugt die Cartoon-Netze (Ben-10-Stil) der Aliens: runde Koerper aus Ellipsoiden, Kapseln und Kegeln,
flaechige Serienfarben pro Dreieck, Leuchtflaechen (Glut, Augen, Omnitrix).

Ausgabe: assets/kingdomomnitrix/toon/<alien>.bin — vom Client (`ToonMeshes`) geladen und vom Cartoon-Renderer mit
Cel-Shading und schwarzem Umriss gezeichnet. Skelett und Animationen kommen weiter aus
tools/generate_alien_models.py (gleiche Knochennamen und Drehpunkte; die Netze liegen im selben Koordinatenraum
wie die Wuerfel der .geo.json: Einheit 1/16 Block, Fuesse auf y=0, vorne = -z, rechte Koerperseite = -x).

Binaerformat (big-endian):
    "TOON" | int version=1 | int partCount
    je Teil: UTF boneName | int triCount
        je Dreieck: 9 float (3 Ecken xyz) | 9 byte (3 Normalen, *127) | 3 byte RGB | 1 byte Flags (1 = leuchtet)

Aufruf aus dem Ordner minecraft-mod/:

    python tools/generate_toon_meshes.py              # alle Netze schreiben
    python tools/generate_toon_meshes.py --only heatblast
    python tools/generate_toon_meshes.py --check      # pruefen, ob alle Netze aktuell sind
    python tools/generate_toon_meshes.py --preview DIR  # Vorschau-Render (Vorder-/Seitenansicht) als PNG
"""
from __future__ import annotations

import argparse
import io
import logging
import math
import struct
import sys
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

LOG = logging.getLogger("toon_meshes")
ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
OUT_DIR = ASSETS / "toon"
VERSION = 1

Vec = tuple[float, float, float]
Color = tuple[int, int, int]
# Farbfunktion: (Position im Modell, Normale) -> (Farbe, leuchtet)
Shader = Callable[[Vec, Vec], tuple[Color, bool]]


def rgb(value: str) -> Color:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def add(a: Vec, b: Vec) -> Vec:
    return a[0] + b[0], a[1] + b[1], a[2] + b[2]


def sub(a: Vec, b: Vec) -> Vec:
    return a[0] - b[0], a[1] - b[1], a[2] - b[2]


def mul(a: Vec, s: float) -> Vec:
    return a[0] * s, a[1] * s, a[2] * s


def dot(a: Vec, b: Vec) -> float:
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a: Vec, b: Vec) -> Vec:
    return a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]


def norm(a: Vec) -> Vec:
    length = math.sqrt(dot(a, a)) or 1.0
    return a[0] / length, a[1] / length, a[2] / length


@dataclass
class Tri:
    p: tuple[Vec, Vec, Vec]
    n: tuple[Vec, Vec, Vec]
    color: Color
    glow: bool


@dataclass
class Part:
    bone: str
    tris: list[Tri] = field(default_factory=list)


# --- Grundformen ----------------------------------------------------------------------------------
# Jede Grundform liefert ein Gitter aus (Position, Normale); daraus werden Dreiecke, die Farbe kommt pro Dreieck
# aus dem Shader (Mittelpunkt + gemittelte Normale) — so bleiben Farbgrenzen scharf wie im Zeichentrick.

Grid = list[list[tuple[Vec, Vec]]]


def ellipsoid(center: Vec, radii: Vec, lat: int = 14, lon: int = 20, tilt: Vec = (0.0, 0.0, 0.0)) -> Grid:
    grid: Grid = []
    for i in range(lat + 1):
        theta = math.pi * i / lat                      # 0 = oben
        row = []
        for j in range(lon + 1):
            phi = 2 * math.pi * j / lon
            unit = (math.sin(theta) * math.cos(phi), math.cos(theta), math.sin(theta) * math.sin(phi))
            pos = (unit[0] * radii[0], unit[1] * radii[1], unit[2] * radii[2])
            nrm = norm((unit[0] / radii[0], unit[1] / radii[1], unit[2] / radii[2]))
            pos, nrm = rotate(pos, tilt), rotate(nrm, tilt)
            row.append((add(center, pos), nrm))
        grid.append(row)
    return grid


def rotate(v: Vec, angles: Vec) -> Vec:
    """Drehung in Grad um x, dann y, dann z."""
    x, y, z = v
    ax, ay, az = (math.radians(a) for a in angles)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return x, y, z


def capsule(a: Vec, b: Vec, ra: float, rb: float, rings: int = 10, sides: int = 16, cap: int = 5) -> Grid:
    """Kapsel/Kegelstumpf von a nach b mit runden Enden (Radius ra bei a, rb bei b)."""
    axis = sub(b, a)
    length = math.sqrt(dot(axis, axis)) or 1e-6
    w = mul(axis, 1.0 / length)
    helper = (1.0, 0.0, 0.0) if abs(w[0]) < 0.9 else (0.0, 0.0, 1.0)
    u = norm(cross(w, helper))
    v = cross(w, u)
    slope = (ra - rb) / length
    profile: list[tuple[float, float, float]] = []   # (Abstand entlang der Achse, Radius, Neigung der Normalen)
    for k in range(cap, 0, -1):                      # Kappe bei a
        ang = (math.pi / 2) * k / cap
        profile.append((-ra * math.sin(ang), ra * math.cos(ang), -math.sin(ang)))
    for k in range(rings + 1):
        t = k / rings
        profile.append((t * length, ra + (rb - ra) * t, slope / math.sqrt(1 + slope * slope)))
    for k in range(1, cap + 1):                      # Kappe bei b
        ang = (math.pi / 2) * k / cap
        profile.append((length + rb * math.sin(ang), rb * math.cos(ang), math.sin(ang)))
    grid: Grid = []
    for along, radius, lean in profile:
        row = []
        for j in range(sides + 1):
            phi = 2 * math.pi * j / sides
            radial = add(mul(u, math.cos(phi)), mul(v, math.sin(phi)))
            pos = add(add(a, mul(w, along)), mul(radial, radius))
            nrm = norm(add(mul(radial, math.sqrt(max(0.0, 1 - lean * lean))), mul(w, lean)))
            row.append((pos, nrm))
        grid.append(row)
    return grid


def grid_tris(grid: Grid, shader: Shader) -> list[Tri]:
    tris: list[Tri] = []
    for i in range(len(grid) - 1):
        for j in range(len(grid[i]) - 1):
            a, b, c, d = grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]
            for tri in ((a, b, c), (a, c, d)):
                p = tuple(t[0] for t in tri)
                if area(p) < 1e-6:
                    continue
                n = tuple(t[1] for t in tri)
                centre = mul(add(add(p[0], p[1]), p[2]), 1 / 3)
                color, glow = shader(centre, norm(add(add(n[0], n[1]), n[2])))
                tris.append(Tri(p, n, color, glow))  # type: ignore[arg-type]
    return tris


def area(p: tuple[Vec, ...]) -> float:
    c = cross(sub(p[1], p[0]), sub(p[2], p[0]))
    return math.sqrt(dot(c, c)) / 2


def orient_outward(tris: list[Tri]) -> list[Tri]:
    """Wicklung so drehen, dass die Flaechennormale zur Eckennormale passt (gegen den Uhrzeigersinn von aussen)."""
    result = []
    for t in tris:
        face = cross(sub(t.p[1], t.p[0]), sub(t.p[2], t.p[0]))
        avg = add(add(t.n[0], t.n[1]), t.n[2])
        if dot(face, avg) < 0:
            t = Tri((t.p[0], t.p[2], t.p[1]), (t.n[0], t.n[2], t.n[1]), t.color, t.glow)
        result.append(t)
    return result


# --- Muster ---------------------------------------------------------------------------------------

class Cells:
    """3D-Zellmuster (Voronoi): Abstand zur Zellgrenze — fuer Gesteinsplatten mit Glutfugen."""

    def __init__(self, seed: int, spacing: float, bounds: tuple[Vec, Vec]) -> None:
        self.points: list[Vec] = []
        lo, hi = bounds
        nx = max(1, int((hi[0] - lo[0]) / spacing) + 1)
        ny = max(1, int((hi[1] - lo[1]) / spacing) + 1)
        nz = max(1, int((hi[2] - lo[2]) / spacing) + 1)
        for ix in range(nx + 1):
            for iy in range(ny + 1):
                for iz in range(nz + 1):
                    h = zlib.crc32(f"{seed}:{ix}:{iy}:{iz}".encode())
                    jitter = ((h & 0xFF) / 255.0, ((h >> 8) & 0xFF) / 255.0, ((h >> 16) & 0xFF) / 255.0)
                    self.points.append((lo[0] + (ix + jitter[0] * 0.8) * spacing,
                                        lo[1] + (iy + jitter[1] * 0.8) * spacing,
                                        lo[2] + (iz + jitter[2] * 0.8) * spacing))

    def edge(self, p: Vec) -> tuple[float, int]:
        """(Abstand zur naechsten Zellgrenze, Zellnummer)."""
        best, second, index = 1e9, 1e9, 0
        for i, q in enumerate(self.points):
            d = sub(p, q)
            dist = math.sqrt(dot(d, d))
            if dist < best:
                best, second, index = dist, best, i
            elif dist < second:
                second = dist
        return (second - best) / 2, index


# --- Heatblast ------------------------------------------------------------------------------------

HB_ROCK = (rgb("#6B1A12"), rgb("#4E120C"), rgb("#83261A"))
HB_LAVA = rgb("#FFB21E")
HB_LAVA_HOT = rgb("#FFE45C")
HB_FACE = rgb("#FFC93C")
HB_FACE_DARK = rgb("#6B1410")
HB_EYE = rgb("#FFF8D8")
HB_FLAME = (rgb("#FFE45C"), rgb("#FFB21E"), rgb("#FF6A10"), rgb("#E8380A"))


def heatblast() -> list[Part]:
    cells = Cells(7, 3.4, ((-10, -1, -5), (10, 30, 5)))

    def rock(p: Vec, n: Vec) -> tuple[Color, bool]:
        edge, cell = cells.edge(p)
        if edge < 0.16:
            return (HB_LAVA_HOT if edge < 0.07 else HB_LAVA), True
        return HB_ROCK[cell % 3], False

    def head(p: Vec, n: Vec) -> tuple[Color, bool]:
        return HB_FACE, True

    def solid(color: Color, glow: bool = False) -> Shader:
        return lambda p, n: (color, glow)

    def flame(p: Vec, n: Vec) -> tuple[Color, bool]:
        t = max(0.0, min(1.0, (p[1] - 29.5) / 9.0))
        wave = 0.08 * math.sin(p[0] * 2.3 + p[2] * 1.7)
        t = max(0.0, min(0.999, t + wave))
        return HB_FLAME[int(t * len(HB_FLAME))], True

    def badge(p: Vec, n: Vec) -> tuple[Color, bool]:
        x, y = p[0], p[1] - 20.0
        r = math.hypot(x, y)
        if r > 1.1:
            return rgb("#141414"), False
        if abs(x) < abs(y) * 0.9 + 0.12:
            return rgb("#4DFF3A"), True                               # Sanduhr
        return rgb("#141414"), False

    parts: list[Part] = []

    def add_part(bone: str, *grids: tuple[Grid, Shader]) -> None:
        part = next((p for p in parts if p.bone == bone), None)
        if part is None:
            part = Part(bone)
            parts.append(part)
        for grid, shader in grids:
            part.tris.extend(orient_outward(grid_tris(grid, shader)))

    # Rumpf: Brustkorb, Taille, Becken, Hals
    add_part("body",
             (ellipsoid((0, 19.6, 0), (4.3, 4.4, 2.6), 22, 32), rock),
             (capsule((0, 13.6, 0), (0, 17.2, 0), 2.7, 3.2, 6, 20), rock),
             (ellipsoid((0, 12.4, 0), (3.3, 1.9, 2.3), 10, 20), rock),
             (capsule((0, 23.2, 0), (0, 25.2, 0), 1.35, 1.25, 3, 14), rock),
             (ellipsoid((0, 20.0, -2.45), (1.25, 1.25, 0.35), 10, 20), badge))
    # Kopf: Gesicht + Flammen-Ansatz
    # Gesicht: Augen, Brauen und Mund als eigene glatte Formen auf der Kopfoberflaeche (scharfe Kanten)
    eyes = []
    for s in (-1, 1):
        tilt = (0, -16 * s, -16 * s)
        eyes += [
            (ellipsoid((1.25 * s, 27.95, -2.72), (1.2, 0.8, 0.34), 12, 18, tilt=tilt), solid(HB_FACE_DARK)),
            (ellipsoid((1.3 * s, 27.85, -2.95), (0.78, 0.48, 0.22), 12, 18, tilt=tilt), solid(HB_EYE, True)),
            (capsule((0.25 * s, 28.8, -2.95), (2.35 * s, 29.55, -2.25), 0.36, 0.26, 4, 8), solid(HB_FACE_DARK)),
        ]
    add_part("head",
             (ellipsoid((0, 27.4, -0.2), (3.0, 3.4, 3.0), 22, 28), head),
             *eyes,
             (ellipsoid((0, 25.75, -2.55), (1.45, 0.62, 0.36), 10, 18), solid(HB_FACE_DARK)),
             (ellipsoid((0, 25.6, -2.78), (1.05, 0.3, 0.22), 10, 18), solid(HB_LAVA_HOT, True)))
    add_part("flame_base",
             (capsule((0, 29.6, 0.3), (0, 33.6, 1.0), 3.0, 2.5, 6, 20), flame),
             (capsule((-2.2, 28.4, 0.6), (-3.0, 32.0, 1.6), 1.1, 0.25, 5, 12), flame),
             (capsule((2.2, 28.4, 0.6), (3.0, 32.0, 1.6), 1.1, 0.25, 5, 12), flame))
    add_part("flame_mid", (capsule((0, 33.4, 1.0), (0.2, 37.2, 2.4), 2.5, 1.2, 6, 16), flame))
    add_part("flame_tip", (capsule((0.2, 36.8, 2.3), (0.4, 40.6, 3.6), 1.3, 0.15, 6, 12), flame))
    add_part("flame_spike", (capsule((-1.4, 35.0, 2.0), (-1.9, 38.5, 3.2), 0.8, 0.1, 5, 10), flame),
             (capsule((1.4, 35.0, 2.0), (1.9, 38.5, 3.2), 0.8, 0.1, 5, 10), flame))
    for side, s in (("right", -1), ("left", 1)):
        add_part(f"{side}_arm",
                 (ellipsoid((5.4 * s, 22.2, 0), (2.1, 1.9, 2.1), 10, 16), rock),
                 (capsule((5.6 * s, 21.5, 0), (6.2 * s, 18.0, 0), 1.55, 1.4, 10, 18), rock))
        add_part(f"{side}_forearm",
                 (capsule((6.2 * s, 18.0, 0), (6.7 * s, 13.0, 0), 1.45, 1.35, 10, 18), rock),
                 (ellipsoid((6.8 * s, 11.6, -0.1), (1.6, 1.7, 1.5), 10, 14), rock))
        add_part(f"{side}_leg", (capsule((2.1 * s, 11.6, 0), (2.2 * s, 6.3, 0), 1.95, 1.65, 10, 18), rock))
        add_part(f"{side}_shin",
                 (capsule((2.2 * s, 6.1, 0), (2.25 * s, 1.4, 0), 1.65, 1.3, 10, 18), rock),
                 (ellipsoid((2.25 * s, 0.75, -0.9), (1.5, 0.8, 2.4), 8, 16), rock))
    return parts



# --- gemeinsame Bausteine -------------------------------------------------------------------------

def solid(color: Color, glow: bool = False) -> Shader:
    return lambda p, n: (color, glow)


class Builder:
    def __init__(self) -> None:
        self.parts: list[Part] = []

    def add(self, bone: str, *grids: tuple[Grid, Shader]) -> None:
        part = next((p for p in self.parts if p.bone == bone), None)
        if part is None:
            part = Part(bone)
            self.parts.append(part)
        for grid, shader in grids:
            part.tris.extend(orient_outward(grid_tris(grid, shader)))


def badge_at(cx: float, cy: float, z: float) -> tuple[Grid, Shader]:
    """Omnitrix-Symbol auf der Brust: gruene Sanduhr auf schwarzem Rund."""
    def shader(p: Vec, n: Vec) -> tuple[Color, bool]:
        x, y = p[0] - cx, p[1] - cy
        if math.hypot(x, y) > 1.05:
            return rgb("#141414"), False
        if abs(x) < abs(y) * 0.9 + 0.12:
            return rgb("#4DFF3A"), True
        return rgb("#141414"), False
    return ellipsoid((cx, cy, z), (1.25, 1.25, 0.35), 10, 20), shader


# --- XLR8 -----------------------------------------------------------------------------------------

def xlr8() -> list[Part]:
    black, dark, cyan, white = rgb("#202026"), rgb("#141418"), rgb("#4FC3D9"), rgb("#EEF2F4")
    b = Builder()

    def suit(p: Vec, n: Vec) -> tuple[Color, bool]:
        if p[2] < -1.0 and abs(p[0]) < 1.7 and p[1] > 15.0:
            return white, False                                              # Brustpaneel
        if abs(p[1] - 15.0) < 0.45:
            return rgb("#8F96A3"), False                                     # Guertel
        return black, False

    def tail(p: Vec, n: Vec) -> tuple[Color, bool]:
        return (cyan if int(p[2] / 2.2) % 2 else black), False

    b.add("body",
          (ellipsoid((0, 19.6, 0), (3.6, 4.2, 2.4), 18, 28), suit),
          (capsule((0, 13.6, 0), (0, 17.0, 0), 2.2, 2.6, 8, 18), suit),
          (ellipsoid((0, 12.4, 0), (2.8, 1.7, 2.1), 10, 18), solid(black)),
          (capsule((0, 23.2, -0.2), (0, 25.0, -0.4), 1.2, 1.1, 3, 12), solid(black)),
          badge_at(0, 20.2, -2.2))
    b.add("head",
          (ellipsoid((0, 28.2, -0.3), (3.0, 3.4, 3.4), 18, 24), solid(black)),             # Helm
          (ellipsoid((0, 27.0, -2.4), (2.25, 2.0, 1.35), 14, 20), solid(cyan)),             # Gesichtsplatte
          (ellipsoid((0, 25.9, -2.9), (1.2, 0.35, 0.4), 8, 14), solid(dark)),               # Mund
          (ellipsoid((-1.05, 27.9, -3.25), (0.72, 0.42, 0.2), 10, 14, tilt=(0, 12, 10)), solid(rgb("#9BFF2E"), True)),
          (ellipsoid((1.05, 27.9, -3.25), (0.72, 0.42, 0.2), 10, 14, tilt=(0, -12, -10)), solid(rgb("#9BFF2E"), True)),
          (capsule((0, 31.2, -1.5), (0, 31.0, 2.6), 0.6, 0.3, 4, 8), solid(dark)))           # Helmkamm
    for side, s in (("right", -1), ("left", 1)):
        b.add(f"{side}_arm",
              (ellipsoid((5.0 * s, 22.2, 0), (1.9, 1.7, 1.9), 10, 16), solid(black)),
              (capsule((5.3 * s, 21.4, 0), (5.9 * s, 18.0, 0), 1.25, 1.15, 8, 16), solid(cyan)))
        b.add(f"{side}_forearm",
              (capsule((5.9 * s, 18.0, 0), (6.3 * s, 13.3, 0), 1.2, 1.1, 8, 16), solid(cyan)),
              (capsule((6.6 * s, 17.0, 0.6), (7.2 * s, 14.0, 1.0), 0.35, 0.1, 4, 8), solid(rgb("#2E8FA3"))),
              (ellipsoid((6.4 * s, 12.1, -0.1), (1.35, 1.45, 1.3), 10, 14), solid(black)))
        b.add(f"{side}_leg", (capsule((2.0 * s, 11.6, 0), (2.1 * s, 6.3, 0), 1.75, 1.45, 10, 18), solid(black)))
        b.add(f"{side}_shin",
              (capsule((2.1 * s, 6.1, 0.2), (2.15 * s, 1.5, 0.4), 1.45, 1.15, 10, 18), solid(cyan)),
              (ellipsoid((2.15 * s, 0.75, -1.0), (1.35, 0.75, 2.5), 8, 16), solid(black)),
              (capsule((1.6 * s, 0.6, -2.8), (1.6 * s, 0.4, -4.0), 0.3, 0.05, 3, 6), solid(dark)),
              (capsule((2.7 * s, 0.6, -2.8), (2.7 * s, 0.4, -4.0), 0.3, 0.05, 3, 6), solid(dark)))
    b.add("tail_1", (capsule((0, 13.0, 2.2), (0, 13.0, 7.0), 1.35, 1.05, 8, 14), tail))
    b.add("tail_2", (capsule((0, 13.0, 7.0), (0, 13.0, 12.0), 1.05, 0.65, 8, 12), tail))
    b.add("tail_3", (capsule((0, 13.0, 12.0), (0, 13.4, 17.0), 0.65, 0.08, 8, 10), tail))
    return b.parts


# --- Vierarm --------------------------------------------------------------------------------------

def four_arms() -> list[Part]:
    red, red_dark, black, white = rgb("#D42A24"), rgb("#9C1414"), rgb("#1E1E22"), rgb("#F2F2F2")
    b = Builder()

    def shirt(p: Vec, n: Vec) -> tuple[Color, bool]:
        if p[1] < 13.4:
            return black, False                                             # Hose
        if abs(p[0]) < 0.85:
            return black, False                                             # Mittelstreifen
        return white, False

    b.add("body",
          (ellipsoid((0, 19.6, 0), (5.6, 4.8, 3.3), 20, 30), shirt),
          (capsule((0, 13.4, 0), (0, 16.6, 0), 3.4, 4.0, 8, 22), shirt),
          (ellipsoid((0, 12.3, 0), (3.8, 2.0, 2.8), 10, 20), solid(black)),
          (capsule((0, 23.6, 0), (0, 25.0, 0), 1.7, 1.6, 3, 14), solid(red)))
    b.add("head",
          (ellipsoid((0, 27.6, 0), (2.6, 3.2, 2.6), 18, 24), solid(red)),
          (ellipsoid((0, 25.9, -2.25), (1.3, 0.45, 0.4), 8, 16), solid(rgb("#2A0606"))),
          *[(ellipsoid((0.95 * s, y, -2.42), (0.62, 0.32, 0.2), 8, 14), solid(rgb("#FFC21A"), True))
            for s in (-1, 1) for y in (28.9, 27.7)])
    for side, s in (("right", -1), ("left", 1)):
        b.add(f"{side}_arm",
              (ellipsoid((7.4 * s, 22.0, 0), (2.6, 2.3, 2.6), 12, 18), solid(red)),
              (capsule((7.8 * s, 21.0, 0), (9.0 * s, 17.2, 0), 1.95, 1.75, 8, 18), solid(red)))
        b.add(f"{side}_forearm",
              (capsule((9.0 * s, 17.2, 0), (9.6 * s, 12.2, 0), 1.85, 1.95, 8, 18), solid(red)),
              (ellipsoid((9.7 * s, 10.8, -0.1), (2.0, 1.9, 2.0), 10, 16), solid(red_dark)))
        b.add(f"{side}_lower_arm", (capsule((6.0 * s, 16.0, 1.0), (7.8 * s, 12.2, 1.0), 1.55, 1.45, 8, 16), solid(red)))
        b.add(f"{side}_lower_forearm",
              (capsule((7.8 * s, 12.2, 1.0), (8.4 * s, 8.0, 1.0), 1.45, 1.5, 8, 16), solid(red)),
              (ellipsoid((8.5 * s, 6.8, 0.9), (1.6, 1.5, 1.6), 10, 14), solid(red_dark)))
        b.add(f"{side}_leg", (capsule((2.6 * s, 11.6, 0), (2.7 * s, 6.2, 0), 2.3, 2.0, 10, 18), solid(black)))
        b.add(f"{side}_shin",
              (capsule((2.7 * s, 6.1, 0), (2.75 * s, 1.6, 0), 2.0, 1.75, 10, 18), solid(black)),
              (ellipsoid((2.75 * s, 0.85, -0.8), (1.9, 0.9, 2.7), 8, 16), solid(red)))
    return b.parts


# --- Diamondhead ----------------------------------------------------------------------------------

def diamondhead() -> list[Part]:
    greens = (rgb("#B9F7D0"), rgb("#7FE3A6"), rgb("#4FC27F"), rgb("#2F9A5E"))
    black, white = rgb("#26262B"), rgb("#EDEDED")
    b = Builder()

    def crystal(p: Vec, n: Vec) -> tuple[Color, bool]:
        # Facetten: Farbe nach Facettenrichtung — wenig Polygone ergeben echte Kristallflaechen
        h = zlib.crc32(f"{round(n[0], 1)}:{round(n[1], 1)}:{round(n[2], 1)}".encode()) % 4
        return greens[h], h == 0

    def split(p: Vec, n: Vec) -> tuple[Color, bool]:
        return (black if p[0] < 0 else white), False

    b.add("body",
          (ellipsoid((0, 19.6, 0), (4.0, 4.4, 2.5), 18, 28), split),
          (capsule((0, 13.6, 0), (0, 17.0, 0), 2.6, 3.0, 8, 20), split),
          (ellipsoid((0, 12.4, 0), (3.1, 1.8, 2.2), 10, 20), split),
          badge_at(1.4, 20.4, -2.3))
    b.add("head",
          (ellipsoid((0, 28.0, 0), (2.8, 3.4, 2.6), 4, 6), crystal),
          (capsule((0, 30.8, 0), (0, 35.0, -0.6), 1.6, 0.08, 2, 5, cap=1), crystal),
          (ellipsoid((-1.0, 28.3, -2.15), (0.7, 0.32, 0.2), 6, 10, tilt=(0, 15, 12)), solid(rgb("#FFD21E"), True)),
          (ellipsoid((1.0, 28.3, -2.15), (0.7, 0.32, 0.2), 6, 10, tilt=(0, -15, -12)), solid(rgb("#FFD21E"), True)))
    for side, s in (("right", -1), ("left", 1)):
        b.add(f"{side}_arm",
              (capsule((5.2 * s, 22.6, 0), (8.2 * s, 29.6, 0.6), 1.5, 0.08, 2, 5, cap=1), crystal),   # Schulterkristall
              (ellipsoid((7.0 * s, 19.6, 0), (2.7, 3.6, 2.7), 4, 6), crystal))
        b.add(f"{side}_forearm", (ellipsoid((7.6 * s, 12.4, 0), (3.1, 4.4, 3.1), 4, 6), crystal))
        leg = solid(black if s < 0 else white)
        b.add(f"{side}_leg", (capsule((2.1 * s, 11.6, 0), (2.2 * s, 6.3, 0), 1.9, 1.6, 10, 18), leg))
        b.add(f"{side}_shin",
              (capsule((2.2 * s, 6.1, 0), (2.25 * s, 1.5, 0), 1.6, 1.3, 10, 18), leg),
              (ellipsoid((2.25 * s, 0.8, -0.9), (1.5, 0.8, 2.4), 8, 16), leg))
    return b.parts


# --- Grey Matter ----------------------------------------------------------------------------------

def grey_matter() -> list[Part]:
    grey, white, black = rgb("#A6B0B3"), rgb("#EEF0F1"), rgb("#1E1E22")
    b = Builder()

    def suit(p: Vec, n: Vec) -> tuple[Color, bool]:
        return (black if abs(p[0]) < 0.7 else white), False

    b.add("body",
          (ellipsoid((0, 18.4, 0), (3.1, 3.6, 2.2), 16, 24), suit),
          (ellipsoid((0, 13.4, 0), (2.6, 2.2, 1.9), 12, 20), suit))
    eyes = []
    for s in (-1, 1):
        tilt = (0, 0, 14 * s)
        eyes += [
            (ellipsoid((1.95 * s, 28.4, -3.05), (1.55, 1.1, 0.7), 12, 18, tilt=tilt), solid(black)),
            (ellipsoid((1.95 * s, 28.4, -3.45), (1.3, 0.88, 0.5), 12, 18, tilt=tilt), solid(rgb("#FFF4A3"), True)),
            (ellipsoid((1.95 * s, 28.4, -3.88), (0.95, 0.16, 0.12), 6, 12, tilt=tilt), solid(black)),
        ]
    b.add("head",
          (ellipsoid((0, 27.0, 0), (4.4, 3.7, 3.6), 20, 28), solid(grey)),
          *eyes,
          (ellipsoid((0, 24.9, -3.15), (0.9, 0.22, 0.25), 6, 12), solid(rgb("#5A6366"))))
    for side, s in (("right", -1), ("left", 1)):
        b.add(f"{side}_arm", (capsule((3.6 * s, 20.6, 0), (4.4 * s, 16.4, 0), 0.95, 0.85, 8, 14), solid(white)))
        b.add(f"{side}_forearm",
              (capsule((4.4 * s, 16.4, 0), (4.9 * s, 12.0, 0), 0.85, 0.85, 8, 14), solid(white)),
              (capsule((4.5 * s, 14.6, 0), (4.6 * s, 13.8, 0), 1.05, 1.05, 2, 14), solid(black)),
              (ellipsoid((5.1 * s, 10.2, 0), (2.0, 2.2, 1.9), 12, 16), solid(grey)))
        b.add(f"{side}_leg", (capsule((1.6 * s, 11.6, 0), (1.7 * s, 6.4, 0), 1.1, 0.95, 8, 14), solid(white)))
        b.add(f"{side}_shin",
              (capsule((1.7 * s, 6.2, 0), (1.75 * s, 1.4, 0), 0.95, 0.85, 8, 14), solid(black)),
              (ellipsoid((1.8 * s, 0.7, -0.7), (1.3, 0.7, 2.0), 8, 14), solid(grey)))
    return b.parts


MODELS: dict[str, Callable[[], list[Part]]] = {"heatblast": heatblast, "xlr8": xlr8, "four_arms": four_arms,
                                                "diamondhead": diamondhead, "grey_matter": grey_matter}


# --- Ausgabe --------------------------------------------------------------------------------------

def encode(parts: list[Part]) -> bytes:
    out = io.BytesIO()
    out.write(b"TOON")
    out.write(struct.pack(">ii", VERSION, len(parts)))
    for part in parts:
        name = part.bone.encode("utf-8")
        out.write(struct.pack(">H", len(name)))
        out.write(name)
        out.write(struct.pack(">i", len(part.tris)))
        for t in part.tris:
            out.write(struct.pack(">9f", *(c for p in t.p for c in p)))
            out.write(struct.pack(">9b", *(max(-127, min(127, round(c * 127))) for n in t.n for c in n)))
            out.write(struct.pack(">3B", *t.color))
            out.write(struct.pack(">B", 1 if t.glow else 0))
    return out.getvalue()


def preview(parts: list[Part], path: Path) -> None:
    """Einfacher Software-Render (Front + Seite) mit Cel-Shading — zum schnellen Pruefen ohne Spiel."""
    from PIL import Image, ImageDraw
    size, scale_px = 420, 9.0
    img = Image.new("RGB", (size * 2, size), (40, 42, 52))
    light = norm((0.35, 0.8, -0.5))
    for view in (0, 1):
        draw = ImageDraw.Draw(img)
        tris = []
        for part in parts:
            for t in part.tris:
                pts = []
                for p in t.p:
                    x, y, z = (p[0], p[1], p[2]) if view == 0 else (p[2], p[1], -p[0])
                    pts.append((size * view + size / 2 - x * scale_px, size - 30 - y * scale_px, z))
                n = norm(add(add(t.n[0], t.n[1]), t.n[2]))
                if view == 1:
                    n = (n[2], n[1], -n[0])
                if n[2] > 0.05:
                    continue
                depth = sum(pt[2] for pt in pts) / 3
                d = dot(n, (light[0], light[1], light[2]))
                band = 1.0 if d > 0.3 else 0.8 if d > -0.2 else 0.62
                color = t.color if t.glow else tuple(int(c * band) for c in t.color)
                tris.append((depth, [(pt[0], pt[1]) for pt in pts], color))
        for _, pts, color in sorted(tris, key=lambda e: -e[0]):
            draw.polygon(pts, fill=color)
    img.save(path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Netze vorhanden und aktuell sind")
    parser.add_argument("--only", help="nur dieses Alien")
    parser.add_argument("--preview", type=Path, help="Vorschau-PNG in diesen Ordner schreiben")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    names = [args.only] if args.only else list(MODELS)
    unknown = [n for n in names if n not in MODELS]
    if unknown:
        LOG.error("unbekanntes Alien: %s", ", ".join(unknown))
        return 1
    problems = 0
    for name in names:
        parts = MODELS[name]()
        data = encode(parts)
        path = OUT_DIR / f"{name}.bin"
        count = sum(len(p.tris) for p in parts)
        if args.check:
            if not path.is_file():
                LOG.error("fehlt: %s", path.relative_to(ASSETS))
                problems += 1
            elif path.read_bytes() != data:
                LOG.error("veraltet (Generator neu ausfuehren): %s", path.relative_to(ASSETS))
                problems += 1
            continue
        try:
            OUT_DIR.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        except OSError as exc:
            LOG.error("konnte %s nicht schreiben: %s", path, exc)
            return 1
        LOG.info("%s: %d Teile, %d Dreiecke, %d KB", name, len(parts), count, len(data) // 1024)
        if args.preview:
            args.preview.mkdir(parents=True, exist_ok=True)
            preview(parts, args.preview / f"toon_{name}.png")
    if args.check:
        LOG.info("%d/%d Cartoon-Netze aktuell", len(names) - problems, len(names))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
