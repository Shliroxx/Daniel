#!/usr/bin/env python3
"""Erzeugt die 3D-Handmodelle der Waffen von Kingdom Omnitrix (Keyblades) samt hochaufgeloester Textur.

Jede Waffe besteht aus Quadern (Minecraft-Item-Modell mit ``elements``). Die Textur wird automatisch gepackt:
jede sichtbare Flaeche bekommt ihren eigenen Bereich mit ``DENSITY`` Bildpunkten pro Modell-Pixel (Standard 4 —
eine Klinge von 1,5 Modell-Pixeln ist also 6 Bildpunkte breit, statt 1–2 bei einem 16×16-Sprite) und wird pro
Material von Hand schattiert: Kontur, Glanzkante, Rundungsverlauf, Griffwicklung, Omnitrix-Zifferblatt.

Das Modell gilt fuer alle Ansichten (Hand, Inventar, Boden, Rahmen); die Lage pro Ansicht steht in ``display``.
Aufruf aus dem Ordner minecraft-mod/:

    python tools/generate_item_models.py            # schreibt models/item/<waffe>.json + textures/item/3d/<waffe>.png
    python tools/generate_item_models.py --check    # prueft, ob die Dateien aktuell sind (fuer die CI)
    python tools/generate_item_models.py --preview build/item_preview   # Vorschau-Bilder der Texturen (8-fach)

Benoetigt Pillow (pip install pillow).
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import math
import sys
from dataclasses import dataclass, field
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - Hinweis fuer den Nutzer
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("item_models")
MOD_ID = "kingdomomnitrix"
DEFAULT_ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / MOD_ID
DENSITY = 4
TEXTURE_SIZES = (64, 128, 256)
FACES = ("north", "south", "east", "west", "up", "down")

Color = tuple[int, int, int, int]


def hexc(value: str, alpha: int = 255) -> Color:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha


def mix(a: Color, b: Color, t: float) -> Color:
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)  # type: ignore[return-value]


@dataclass(frozen=True)
class Material:
    """Farbstufen eines Materials: Kontur, dunkel, Grundton, hell, Glanz; ``pattern`` waehlt die Zeichnung."""
    outline: str
    dark: str
    base: str
    light: str
    shine: str
    pattern: str = "metal"
    accent: str = "#FFFFFF"


MATERIALS: dict[str, Material] = {
    # Kingdom Key
    "silver": Material("#3E4352", "#7E8596", "#BCC3D0", "#E3E7EE", "#FFFFFF"),
    "gold": Material("#5A3A05", "#A8730F", "#E2AE2A", "#FFD45E", "#FFF4C2"),
    "grip_dark": Material("#0A0C16", "#141A2E", "#232C4A", "#33406A", "#5566A0", "wrap"),
    "chain": Material("#3A3F4C", "#7A8090", "#B2B8C6", "#DADFE8", "#FFFFFF", "chain"),
    # Oathkeeper
    "white": Material("#5C6478", "#A6AEC0", "#DCE1EA", "#F4F6FA", "#FFFFFF"),
    "wing": Material("#5A6276", "#A4ADBF", "#E1E6EE", "#F7F9FC", "#FFFFFF", "feather"),
    "grip_blue": Material("#0E1430", "#1B2552", "#2D3A6E", "#44559A", "#6A7FC8", "wrap"),
    "star": Material("#6A4A00", "#C99A12", "#F6D23A", "#FFE97A", "#FFFBD8"),
    "heart": Material("#4A0C3A", "#9A2A7A", "#D14FA8", "#EE86CC", "#FFD6F0"),
    # Omega Key (Mod-eigen: Ratchet-&-Clank-Mechanik + Omnitrix)
    "dark": Material("#07080C", "#15171F", "#262A36", "#3A3F50", "#5A6178", "plate"),
    "edge": Material("#3A0A66", "#7A22C8", "#B44CFF", "#D79AFF", "#F6E6FF", "glow"),
    "omni_green": Material("#06401A", "#12A040", "#39FF6A", "#9DFFB8", "#E8FFF0", "glow"),
    "omni_face": Material("#050607", "#0C0E12", "#16191F", "#2A2F38", "#39FF6A", "dial"),
    "steel": Material("#2A2E38", "#4E5462", "#7A8292", "#A6AEBE", "#D8DEE8", "plate"),
    # Ratchet-&-Clank-Geraete
    "orange_paint": Material("#4A1A04", "#A8400E", "#E8641C", "#FF9A4A", "#FFD9B0", "plate"),
    "red_paint": Material("#3E0808", "#8A1414", "#C8282A", "#EE5A50", "#FFC8C0", "plate"),
    "yellow_paint": Material("#4E3A02", "#B08A0C", "#F0C21C", "#FFE066", "#FFF6C8"),
    "blue_paint": Material("#0C1A36", "#22406E", "#3A63A8", "#6A92D2", "#C8DCFF", "plate"),
    "teal_paint": Material("#062E2A", "#13695F", "#22A08E", "#5AD0BC", "#C8FFF4", "plate"),
    "fire": Material("#5A1400", "#C83C00", "#FF7A1A", "#FFC060", "#FFF4D0", "glow"),
    "plasma": Material("#08305A", "#1A6EC8", "#3AB0FF", "#9AD8FF", "#E8F8FF", "glow"),
    "strap": Material("#050505", "#0E0F12", "#1C1E24", "#2C2F38", "#4A4F5C", "wrap"),
    # Omnitrix am Handgelenk (Zifferblatt zeigt nach +x = Aussenseite des linken Arms)
    "omni_housing": Material("#08090C", "#181B22", "#2A2E38", "#3E4452", "#6A7286", "plate"),
    "omni_bezel": Material("#30343E", "#6A7180", "#9AA2B2", "#C8CEDA", "#F2F5FA", "metal"),
    "omni_light": Material("#0A5A1E", "#1EC84A", "#39FF6A", "#A8FFC0", "#F0FFF4", "glow"),
    "omni_dial_x": Material("#050607", "#0C0E12", "#121519", "#2A2F38", "#39FF6A", "dial_x"),
    # Leuchtteile weiss: der Renderer faerbt sie je Geraete-Zustand (OmnitrixStatus: gruen, gelb, rot, grau …)
    "omni_glow_x": Material("#000000", "#000000", "#000000", "#000000", "#FFFFFF", "glowmask_x"),
    "omni_glow_full": Material("#C8C8C8", "#C8C8C8", "#FFFFFF", "#FFFFFF", "#FFFFFF", "glowfull"),
}


@dataclass
class Box:
    frm: tuple[float, float, float]
    to: tuple[float, float, float]
    material: str
    rotation: dict | None = None
    faces: dict[str, list[float]] = field(default_factory=dict)
    overlay: bool = False

    def face_size(self, face: str) -> tuple[float, float]:
        dx, dy, dz = (self.to[i] - self.frm[i] for i in range(3))
        if face in ("north", "south"):
            return dx, dy
        if face in ("east", "west"):
            return dz, dy
        return dx, dz


def box(frm, to, material, axis: str | None = None, angle: float = 0.0, origin=None) -> Box:
    rotation = None
    if axis is not None and angle:
        if origin is None:
            origin = [(frm[i] + to[i]) / 2 for i in range(3)]
        rotation = {"origin": [round(v, 4) for v in origin], "axis": axis, "angle": angle}
    return Box(tuple(frm), tuple(to), material, rotation)


def mirror_x(b: Box) -> Box:
    """Spiegelt einen Quader an der Klingenmitte (x = 8); Drehungen um z wechseln das Vorzeichen."""
    frm = (16 - b.to[0], b.frm[1], b.frm[2])
    to = (16 - b.frm[0], b.to[1], b.to[2])
    rotation = None
    if b.rotation is not None:
        origin = list(b.rotation["origin"])
        origin[0] = 16 - origin[0]
        angle = b.rotation["angle"]
        if b.rotation["axis"] in ("z", "y"):
            angle = -angle
        rotation = {"origin": origin, "axis": b.rotation["axis"], "angle": angle}
    return Box(frm, to, b.material, rotation)


# ---------------------------------------------------------------------------------------------------------------
# Waffen-Geometrie. Koordinaten in Modell-Pixeln; Klinge entlang +y, Griffmitte bei y ≈ 7, Klingenmitte x = z = 8.
# ---------------------------------------------------------------------------------------------------------------

def chain(token_top: float) -> list[Box]:
    """Kette vom Knauf (y = 2.5) nach unten bis ``token_top``; Glieder abwechselnd quer und laengs."""
    boxes = []
    y = 2.5
    flat = True
    while y - 1.05 > token_top - 0.2:
        if flat:
            boxes.append(box((7.55, y - 1.1, 7.85), (8.45, y, 8.15), "chain"))
        else:
            boxes.append(box((7.85, y - 1.1, 7.55), (8.15, y, 8.45), "chain"))
        y -= 1.0
        flat = not flat
    return boxes


def kingdom_key() -> list[Box]:
    b: list[Box] = []
    # Griff mit Knauf und Kragen
    b.append(box((7.25, 3.5, 7.25), (8.75, 10.5, 8.75), "grip_dark"))
    b.append(box((6.9, 2.5, 6.9), (9.1, 3.5, 9.1), "gold"))
    b.append(box((6.6, 10.5, 6.6), (9.4, 11.75, 9.4), "gold"))
    # Handschutz: goldener, abgerundeter Rahmen um die Hand (Querschnitt 1 × 1,5)
    left = [
        box((3.5, 4.25, 7.25), (4.75, 9.75, 8.75), "gold"),                       # Seite
        box((4.0, 9.4, 7.25), (5.4, 10.6, 8.75), "gold", "z", -45),              # Ecke oben
        box((4.0, 3.15, 7.25), (5.4, 4.35, 8.75), "gold", "z", 45),              # Ecke unten
    ]
    b += left + [mirror_x(p) for p in left]
    b.append(box((5.0, 10.0, 7.25), (11.0, 11.0, 8.75), "gold"))                 # oberer Bogen
    b.append(box((5.0, 2.75, 7.25), (11.0, 3.75, 8.75), "gold"))                 # unterer Bogen
    # Klinge
    b.append(box((7.2, 11.75, 7.2), (8.8, 27.0, 8.8), "silver"))
    b.append(box((7.45, 27.0, 7.45), (8.55, 27.8, 8.55), "silver"))
    # Zahnbart: Krone, seitlich (+x), drei Zacken
    b.append(box((8.8, 22.0, 7.45), (10.2, 27.0, 8.55), "silver"))               # Steg
    b.append(box((10.2, 25.6, 7.45), (12.6, 27.0, 8.55), "silver"))              # obere Zacke
    b.append(box((10.2, 23.8, 7.45), (11.6, 24.9, 8.55), "silver"))              # mittlere Zacke
    b.append(box((10.2, 22.0, 7.45), (12.6, 23.4, 8.55), "silver"))              # untere Zacke
    b.append(box((11.9, 25.75, 7.45), (13.1, 26.95, 8.55), "silver", "z", 45))   # Spitze oben
    b.append(box((11.9, 22.05, 7.45), (13.1, 23.25, 8.55), "silver", "z", 45))   # Spitze unten
    # Anhaenger: Kette + Kopf mit zwei Ohren
    b += chain(0.4)
    b.append(box((6.4, -3.6, 7.7), (9.6, -0.4, 8.3), "silver"))
    b.append(box((5.0, -1.2, 7.7), (6.9, 0.7, 8.3), "silver"))
    b.append(box((9.1, -1.2, 7.7), (11.0, 0.7, 8.3), "silver"))
    return b


def oathkeeper() -> list[Box]:
    b: list[Box] = []
    b.append(box((7.25, 3.5, 7.25), (8.75, 10.5, 8.75), "grip_blue"))
    b.append(box((6.9, 2.5, 6.9), (9.1, 3.5, 9.1), "white"))
    b.append(box((6.6, 10.5, 6.6), (9.4, 11.75, 9.4), "white"))
    # Handschutz aus zwei Engelsfluegeln (je drei Federn, nach aussen gefaechert)
    left = [
        box((3.4, 4.0, 7.3), (4.8, 10.4, 8.7), "wing"),
        box((2.2, 5.0, 7.4), (3.6, 10.6, 8.6), "wing", "z", -22.5, (3.6, 10.6, 8.0)),
        box((1.4, 6.4, 7.5), (2.8, 10.8, 8.5), "wing", "z", -45, (2.8, 10.8, 8.0)),
        box((4.2, 2.9, 7.3), (5.6, 4.3, 8.7), "wing", "z", 45),
    ]
    b += left + [mirror_x(p) for p in left]
    b.append(box((4.6, 10.0, 7.3), (11.4, 11.0, 8.7), "white"))
    b.append(box((5.0, 2.7, 7.3), (11.0, 3.7, 8.7), "white"))
    # Klinge, mit Herz kurz vor dem Ende
    b.append(box((7.2, 11.75, 7.2), (8.8, 26.0, 8.8), "white"))
    b.append(box((7.0, 18.0, 7.0), (9.0, 19.0, 9.0), "white"))
    # Zahnbart: Stern an der Spitze, zwei Federn seitlich
    b.append(box((6.6, 25.4, 7.4), (9.4, 28.2, 8.6), "white", "z", 45, (8.0, 26.8, 8.0)))
    b.append(box((7.3, 24.3, 7.3), (8.7, 29.3, 8.7), "white"))
    b.append(box((5.7, 26.1, 7.3), (10.3, 27.5, 8.7), "white"))
    b.append(box((8.8, 21.6, 7.45), (12.2, 22.9, 8.55), "wing", "z", 22.5, (8.8, 22.2, 8.0)))
    b.append(box((8.8, 23.4, 7.45), (11.2, 24.6, 8.55), "wing", "z", 22.5, (8.8, 24.0, 8.0)))
    # Anhaenger: Glueckstern (Stern aus zwei gedrehten Quadraten)
    b += chain(0.0)
    b.append(box((6.5, -3.5, 7.7), (9.5, -0.5, 8.3), "star"))
    b.append(box((6.5, -3.5, 7.65), (9.5, -0.5, 8.35), "star", "z", 45, (8.0, -2.0, 8.0)))
    return b


def omega_key() -> list[Box]:
    b: list[Box] = []
    b.append(box((7.2, 3.5, 7.2), (8.8, 10.5, 8.8), "grip_dark"))
    b.append(box((6.7, 2.4, 6.7), (9.3, 3.5, 9.3), "steel"))
    # Handschutz: kantiger Mechanik-Rahmen mit violett leuchtenden Kanten
    left = [
        box((3.0, 3.6, 7.1), (4.6, 10.6, 8.9), "dark"),
        box((2.6, 5.0, 7.4), (3.0, 9.2, 8.6), "edge"),
        box((3.4, 2.6, 7.1), (5.0, 3.8, 8.9), "steel", "z", 45),
    ]
    b += left + [mirror_x(p) for p in left]
    b.append(box((3.4, 10.4, 6.9), (12.6, 12.0, 9.1), "dark"))
    b.append(box((4.8, 2.6, 7.1), (11.2, 3.6, 8.9), "dark"))
    b.append(box((6.8, 10.6, 6.7), (9.2, 11.8, 9.3), "omni_green"))               # Omnitrix-Kern
    # Klinge: dunkel, Leuchtstreifen vorn und hinten
    b.append(box((7.0, 12.0, 7.25), (9.0, 26.5, 8.75), "dark"))
    b.append(box((7.6, 12.6, 7.0), (8.4, 26.0, 9.0), "edge"))
    b.append(box((7.4, 26.5, 7.4), (8.6, 27.6, 8.6), "steel"))
    # Zahnbart: Sanduhr (Omnitrix) seitlich
    b.append(box((9.0, 21.6, 7.4), (13.2, 22.8, 8.6), "dark"))
    b.append(box((9.0, 27.0, 7.4), (13.2, 28.2, 8.6), "dark"))
    b.append(box((12.0, 22.8, 7.4), (13.2, 27.0, 8.6), "edge"))
    b.append(box((10.0, 23.9, 7.3), (11.8, 25.9, 8.7), "omni_green", "z", 45))
    # Anhaenger: kleines Omnitrix-Zifferblatt
    b += chain(0.2)
    b.append(box((6.3, -3.6, 7.6), (9.7, -0.2, 8.4), "omni_face"))
    return b


# Ratchet-&-Clank-Geraete. Gewehre: Lauf entlang +y wie die Klinge, Griff quer (+x) um die Hand bei (8, 7, 8).

def combuster() -> list[Box]:
    b: list[Box] = []
    b.append(box((4.0, 5.6, 7.2), (9.5, 8.4, 8.8), "grip_dark"))                  # Griff
    b.append(box((3.4, 5.2, 7.0), (4.4, 8.8, 9.0), "steel"))                      # Griffkappe
    b.append(box((9.0, 8.4, 7.5), (10.4, 9.6, 8.5), "steel"))                     # Abzug
    b.append(box((9.5, 2.0, 6.0), (14.0, 15.0, 10.0), "orange_paint"))            # Gehaeuse
    b.append(box((9.2, 1.0, 6.4), (14.3, 2.0, 9.6), "dark"))                      # Heckplatte
    b.append(box((14.0, 3.5, 6.5), (16.2, 12.5, 9.5), "steel"))                   # Brennstofftank
    b.append(box((16.2, 5.0, 7.2), (16.5, 11.0, 8.8), "fire"))                    # Tankfenster
    b.append(box((10.5, 9.0, 5.5), (13.2, 13.5, 6.0), "yellow_paint"))            # Seitenstreifen
    b.append(box((10.5, 9.0, 10.0), (13.2, 13.5, 10.5), "yellow_paint"))
    b.append(box((10.2, 15.0, 6.5), (13.4, 21.0, 9.5), "steel"))                  # Lauf
    b.append(box((9.6, 21.0, 5.9), (14.0, 23.0, 10.1), "dark"))                   # Muendungsring
    b.append(box((10.4, 23.0, 6.7), (13.2, 23.4, 9.3), "fire"))                   # Muendungsglut
    b.append(box((10.9, 16.0, 6.1), (12.7, 20.5, 6.5), "fire"))                   # Heizspirale
    b.append(box((10.9, 16.0, 9.5), (12.7, 20.5, 9.9), "fire"))
    return b


def swingshot() -> list[Box]:
    b: list[Box] = []
    b.append(box((4.0, 5.6, 7.2), (9.5, 8.4, 8.8), "grip_dark"))
    b.append(box((9.0, 8.4, 7.5), (10.4, 9.6, 8.5), "steel"))
    b.append(box((9.5, 2.5, 6.3), (13.6, 13.0, 9.7), "steel"))                   # Gehaeuse
    b.append(box((13.6, 4.0, 6.8), (14.2, 11.5, 9.2), "teal_paint"))             # Seitenpaneel
    b.append(box((10.0, 6.0, 5.9), (13.0, 10.5, 6.3), "teal_paint"))
    b.append(box((10.0, 6.0, 9.7), (13.0, 10.5, 10.1), "teal_paint"))
    b.append(box((12.0, 3.5, 5.6), (14.6, 7.5, 10.4), "dark"))                   # Seiltrommel
    b.append(box((10.4, 13.0, 6.8), (12.8, 15.0, 9.2), "dark"))                  # Kopf
    b.append(box((11.0, 15.0, 7.4), (12.2, 19.5, 8.6), "chain"))                 # Greifer Mitte
    b.append(box((9.6, 14.8, 7.4), (10.8, 18.8, 8.6), "chain", "z", 22.5, (10.6, 14.8, 8.0)))
    b.append(box((12.4, 14.8, 7.4), (13.6, 18.8, 8.6), "chain", "z", -22.5, (12.6, 14.8, 8.0)))
    b.append(box((11.1, 19.3, 7.5), (12.1, 20.3, 8.5), "teal_paint", "z", 45))  # Leuchtspitze
    return b


def omniwrench() -> list[Box]:
    b: list[Box] = []
    b.append(box((7.1, -1.0, 7.1), (8.9, 0.6, 8.9), "steel"))                    # Endkappe
    b.append(box((7.25, 0.6, 7.25), (8.75, 10.5, 8.75), "grip_blue"))            # Griff
    b.append(box((7.0, 10.5, 7.0), (9.0, 11.6, 9.0), "yellow_paint"))            # Ring
    b.append(box((7.35, 11.6, 7.35), (8.65, 21.0, 8.65), "blue_paint"))          # Schaft
    b.append(box((7.0, 18.6, 7.0), (9.0, 19.6, 9.0), "yellow_paint"))
    b.append(box((4.6, 21.0, 6.8), (11.4, 24.0, 9.2), "silver"))                 # Kopf
    b.append(box((4.6, 24.0, 6.8), (6.9, 29.0, 9.2), "silver"))                  # Backe links
    b.append(box((9.1, 24.0, 6.8), (11.4, 27.8, 9.2), "silver"))                 # Backe rechts (kuerzer)
    b.append(box((4.9, 28.1, 6.9), (6.6, 29.8, 9.1), "silver", "z", 45))
    b.append(box((9.4, 26.9, 6.9), (11.1, 28.6, 9.1), "silver", "z", 45))
    b.append(box((6.9, 24.0, 7.6), (9.1, 24.6, 8.4), "dark"))                    # Maulgrund
    b.append(box((5.4, 21.6, 6.6), (10.6, 22.4, 9.4), "yellow_paint"))           # Kopfband
    return b


def fusion_grenade() -> list[Box]:
    b: list[Box] = []
    b.append(box((5.0, 5.0, 6.0), (11.0, 11.0, 10.0), "red_paint"))
    b.append(box((6.0, 5.0, 5.0), (10.0, 11.0, 11.0), "red_paint"))
    b.append(box((5.0, 6.0, 5.0), (11.0, 10.0, 11.0), "red_paint"))
    b.append(box((4.7, 7.4, 4.7), (11.3, 8.6, 11.3), "plasma"))                  # Leuchtring
    b.append(box((7.0, 11.0, 7.0), (9.0, 12.2, 9.0), "steel"))                   # Zuender
    b.append(box((8.6, 11.4, 7.4), (11.6, 12.0, 8.6), "steel"))                  # Hebel
    b.append(box((7.4, 3.8, 7.4), (8.6, 5.0, 8.6), "dark"))
    return b


def omnitrix() -> list[Box]:
    b: list[Box] = []
    b.append(box((6.0, -0.5, 7.0), (10.0, 4.6, 9.0), "strap"))                   # Armband
    b.append(box((6.0, 11.4, 7.0), (10.0, 16.5, 9.0), "strap"))
    b.append(box((4.6, 4.6, 6.4), (11.4, 11.4, 9.6), "dark"))                    # Gehaeuse
    b.append(box((5.4, 3.8, 6.6), (10.6, 12.2, 9.4), "dark"))
    b.append(box((3.8, 5.4, 6.6), (12.2, 10.6, 9.4), "dark"))
    b.append(box((3.4, 7.2, 7.4), (3.8, 8.8, 8.6), "omni_green"))               # Seitentasten
    b.append(box((12.2, 7.2, 7.4), (12.6, 8.8, 8.6), "omni_green"))
    b.append(box((5.0, 5.0, 9.6), (11.0, 11.0, 10.2), "omni_face"))              # Zifferblatt vorn
    b.append(box((5.0, 5.0, 5.8), (11.0, 11.0, 6.4), "omni_face"))               # und hinten
    return b


# Omnitrix am Handgelenk: drei Teile, im Code an den linken Arm gehaengt (Arm x/z 6..10, Hand bei y = 0).

def omnitrix_wrist_base() -> list[Box]:
    b: list[Box] = []
    b.append(box((5.55, 1.2, 5.55), (10.45, 4.8, 10.45), "strap"))                # Armband
    b.append(box((5.35, 1.2, 5.35), (10.65, 1.6, 10.65), "omni_housing"))        # Randwulst
    b.append(box((5.35, 4.4, 5.35), (10.65, 4.8, 10.65), "omni_housing"))
    b.append(box((10.45, 0.8, 5.0), (11.5, 5.2, 11.0), "omni_housing"))          # Gehaeuse
    b.append(box((10.45, 1.4, 4.5), (11.3, 4.6, 5.0), "omni_housing"))           # Seitenwangen
    b.append(box((10.45, 1.4, 11.0), (11.3, 4.6, 11.5), "omni_housing"))
    b.append(box((11.5, 1.1, 5.4), (12.1, 4.9, 10.6), "omni_bezel"))             # Fassung
    b.append(box((11.5, 1.6, 4.9), (12.0, 4.4, 5.4), "omni_bezel"))
    b.append(box((11.5, 1.6, 10.6), (12.0, 4.4, 11.1), "omni_bezel"))
    for y in (0.85, 4.55):                                                        # Eck-Leuchten
        for z in (5.05, 10.35):
            b.append(box((11.5, y, z), (11.85, y + 0.6, z + 0.6), "omni_light"))
    b.append(box((10.8, 2.4, 4.1), (11.3, 3.6, 4.5), "omni_bezel"))              # Seitentasten
    b.append(box((10.8, 2.4, 11.5), (11.3, 3.6, 11.9), "omni_bezel"))
    return b


def omnitrix_wrist_core() -> list[Box]:
    return [
        box((11.8, 1.1, 6.1), (12.6, 4.9, 9.9), "omni_housing"),                  # Kern (faehrt aus)
        box((12.6, 1.3, 6.3), (12.95, 4.7, 9.7), "omni_dial_x"),                   # Zifferblatt
    ]


def omnitrix_wrist_glow() -> list[Box]:
    b = [box((12.97, 1.3, 6.3), (13.02, 4.7, 9.7), "omni_glow_x")]               # Sanduhr-Leuchten
    for y in (0.85, 4.55):
        for z in (5.05, 10.35):
            b.append(box((11.86, y, z), (11.91, y + 0.6, z + 0.6), "omni_glow_full"))
    return b


# Teile ohne Item: Modell unter models/omnitrix/, Dichte 8 Bildpunkte pro Modell-Pixel (am Arm sehr nah im Bild)
PARTS = {
    "wrist_base": (omnitrix_wrist_base, "omni_housing"),
    "wrist_core": (omnitrix_wrist_core, "omni_housing"),
    "wrist_glow": (omnitrix_wrist_glow, "omni_glow_x"),
}
PART_DENSITY = 8


WEAPONS = {
    "kingdom_key": (kingdom_key, "silver", "blade"),
    "oathkeeper": (oathkeeper, "white", "blade"),
    "omega_key": (omega_key, "dark", "blade"),
    "omniwrench": (omniwrench, "silver", "blade"),
    "combuster": (combuster, "orange_paint", "gun"),
    "swingshot": (swingshot, "steel", "gun"),
    "fusion_grenade": (fusion_grenade, "red_paint", "small"),
    "omnitrix": (omnitrix, "dark", "small"),
}

# Lage pro Ansicht; hergeleitet aus vanilla „handheld“ (Klinge dort diagonal, hier entlang y → 45° weniger um z)
BLADE_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, -1.4, 1.3], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, -1.4, 1.3], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"rotation": [0, -90, -18], "translation": [0.9, -0.5, -2.0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 90, 18], "translation": [0.9, -0.5, -2.0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, -45], "translation": [-2.1, -2.1, 0], "scale": [0.7, 0.7, 0.7]},
    "ground": {"rotation": [0, 0, -45], "translation": [-0.8, 1.2, 0], "scale": [0.38, 0.38, 0.38]},
    "fixed": {"rotation": [0, 180, -45], "translation": [1.8, -1.8, 0], "scale": [0.62, 0.62, 0.62]},
    "head": {"rotation": [0, 180, -45], "translation": [0, 13, 7], "scale": [0.8, 0.8, 0.8]},
}

# Gewehre: wie die Klinge, nur kuerzer und im Inventar waagerecht
GUN_DISPLAY = dict(BLADE_DISPLAY) | {
    "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, -0.6, 1.3], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, -0.6, 1.3], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"rotation": [0, 90, -72], "translation": [1.4, 5.2, -1.2], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"rotation": [0, -90, 72], "translation": [1.4, 5.2, -1.2], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [0, 0, -45], "translation": [-1.4, -1.6, 0], "scale": [0.78, 0.78, 0.78]},
    "fixed": {"rotation": [0, 180, -45], "translation": [1.4, -1.6, 0], "scale": [0.8, 0.8, 0.8]},
}

# kleine Geraete: wie vanilla „generated“
SMALL_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"rotation": [10, -110, 20], "translation": [1.6, 4.6, -1.2], "scale": [0.36, 0.36, 0.36]},
    "firstperson_lefthand": {"rotation": [10, 110, -20], "translation": [1.6, 4.6, -1.2], "scale": [0.36, 0.36, 0.36]},
    "gui": {"rotation": [18, -28, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}

DISPLAYS = {"blade": BLADE_DISPLAY, "gun": GUN_DISPLAY, "small": SMALL_DISPLAY}


# ---------------------------------------------------------------------------------------------------------------
# Textur: Flaechen packen und schattieren
# ---------------------------------------------------------------------------------------------------------------

@dataclass
class Slot:
    box: Box
    face: str
    w: int
    h: int
    x: int = 0
    y: int = 0


def pack(slots: list[Slot], size: int) -> bool:
    """Regal-Packer: hoechste Flaechen zuerst, Zeile fuer Zeile; False, wenn die Textur zu klein ist."""
    x = y = shelf = 0
    for s in sorted(slots, key=lambda s: (-s.h, -s.w)):
        if s.w > size:
            return False
        if x + s.w > size:
            x, y, shelf = 0, y + shelf, 0
        if y + s.h > size:
            return False
        s.x, s.y = x, y
        x += s.w
        shelf = max(shelf, s.h)
    return True


def paint_face(img: Image.Image, s: Slot) -> None:
    m = MATERIALS[s.box.material]
    outline, dark, base, light, shine = (hexc(c) for c in (m.outline, m.dark, m.base, m.light, m.shine))
    w, h = s.w, s.h
    vertical = h >= w
    for py in range(h):
        for px in range(w):
            # Position quer zur Laengsrichtung (0..1): Rundungsverlauf hell oben/links, dunkel unten/rechts
            across = (px + 0.5) / w if vertical else (py + 0.5) / h
            along = (py + 0.5) / h if vertical else (px + 0.5) / w
            shade = 1.0 - abs(across - 0.32) * 1.6
            if s.face == "down":
                shade -= 0.45
            elif s.face == "up":
                shade += 0.25
            elif s.face in ("east", "west"):
                shade -= 0.12
            color = mix(dark, base, shade + 0.55) if shade < 0.45 else mix(base, light, (shade - 0.45) * 1.6)
            if m.pattern == "wrap" and (px + py * (1 if vertical else -1)) % 6 in (0, 1):
                color = mix(color, outline, 0.55)
            elif m.pattern == "wrap" and (px + py * (1 if vertical else -1)) % 6 == 2:
                color = mix(color, light, 0.35)
            elif m.pattern == "chain" and (py if vertical else px) % 4 == 0:
                color = mix(color, dark, 0.6)
            elif m.pattern == "feather":
                stripe = (py * 2 + px) % 7 if vertical else (px * 2 + py) % 7
                if stripe == 0:
                    color = mix(color, dark, 0.45)
            elif m.pattern == "plate":
                if (py if vertical else px) % 10 == 9:
                    color = mix(color, outline, 0.7)
                elif (py if vertical else px) % 10 == 4 and abs(across - 0.5) < 0.2:
                    color = mix(color, shine, 0.6)  # Niete
            elif m.pattern == "glow":
                core = 1.0 - abs(across - 0.5) * 2.0
                color = mix(base, shine, core * 0.8)
            elif m.pattern == "dial":
                color = dial_color(px, py, w, h, s.face, m)
            elif m.pattern == "dial_x":
                color = dial_color(px, py, w, h, "north" if s.face in ("east", "west") else "up", m)
            elif m.pattern == "glowmask_x":
                lit = s.face in ("east", "west") and dial_color(px, py, w, h, "north", MATERIALS["omni_dial_x"]) == hexc(m.shine)
                color = hexc(m.shine) if lit else (0, 0, 0, 255)
            elif m.pattern == "glowfull":
                color = mix(base, shine, 1.0 - abs((px + 0.5) / w - 0.5) - abs((py + 0.5) / h - 0.5))
            # Glanzlinie entlang der Kante und ein Glanzpunkt pro Flaeche
            if m.pattern in ("metal", "feather", "plate") and s.face not in ("down",):
                if (px if vertical else py) == max(1, round((w if vertical else h) * 0.25)) and 0.12 < along < 0.88:
                    color = mix(color, shine, 0.55)
            img.putpixel((s.x + px, s.y + py), color)
    # Kontur (bei Leuchtmaterial heller statt dunkel)
    if m.pattern in ("glowmask_x", "glowfull"):
        return
    edge = mix(base, light, 0.5) if m.pattern == "glow" else outline
    for px in range(w):
        for py in (0, h - 1):
            img.putpixel((s.x + px, s.y + py), mix(img.getpixel((s.x + px, s.y + py)), edge, 0.75))
    for py in range(1, h - 1):
        for px in (0, w - 1):
            img.putpixel((s.x + px, s.y + py), mix(img.getpixel((s.x + px, s.y + py)), edge, 0.75))


def dial_color(px: int, py: int, w: int, h: int, face: str, m: Material) -> Color:
    """Omnitrix-Zifferblatt: graue Fassung, schwarzer Grund, gruene Sanduhr (nur Vorder-/Rueckseite)."""
    rim, black, green = hexc(m.light), hexc(m.base), hexc(m.shine)
    if face not in ("north", "south"):
        return rim
    cx, cy = (w - 1) / 2, (h - 1) / 2
    dx, dy = (px - cx) / (w / 2), (py - cy) / (h / 2)
    r = math.hypot(dx, dy)
    if r > 0.92:
        return hexc(m.outline)
    if r > 0.72:
        return rim
    if m.pattern == "dial_x":
        # Omniverse: gruenes Feld, zwei schwarze Dreiecke links/rechts bilden das X (Sanduhr bleibt gruen)
        return black if abs(dx) > abs(dy) * 1.05 + 0.07 else green
    if abs(dx) <= abs(dy) * 0.9 and abs(dy) > 0.12:
        return green
    return black


# Omnitrix-Gruen: diese Materialien bekommen eine deckungsgleiche Akzent-Schicht (tintindex 0), die der Renderer im
# Farbmodul einfaerbt (OmnitrixColors; Klassisch = Gruen). Die Grundschicht bleibt als Rueckfall unveraendert.
ACCENT_MATERIALS = {"omni_green", "omni_face", "omni_light", "omni_dial_x"}
# nur Modelle mit Farbgeber (Omnitrix-Item und Armteile); das Omega-Key-Keyblade bleibt fest gruen
TINTED = {"omnitrix", "wrist_base", "wrist_core"}


def is_accent(color: Color) -> bool:
    r, g, b = color[0], color[1], color[2]
    return g >= 60 and g > r + 25 and g > b + 25


def accent_face(img: Image.Image, s: Slot) -> None:
    """Akzent-Schicht: gruene Pixel als Graustufe (Helligkeit = Gruenkanal), alles andere durchsichtig."""
    paint_face(img, s)
    for py in range(s.h):
        for px in range(s.w):
            color = img.getpixel((s.x + px, s.y + py))
            if is_accent(color):
                v = color[1]
                img.putpixel((s.x + px, s.y + py), (v, v, v, 255))
            else:
                img.putpixel((s.x + px, s.y + py), (0, 0, 0, 0))


def build(name: str) -> tuple[dict, Image.Image]:
    if name in PARTS:
        factory, particle_material = PARTS[name]
        display, density, texture = None, PART_DENSITY, f"{MOD_ID}:item/3d/omnitrix_{name}"
    else:
        factory, particle_material, display = WEAPONS[name]
        density, texture = DENSITY, f"{MOD_ID}:item/3d/{name}"
    boxes = factory()
    if name in TINTED:
        boxes += [Box(b.frm, b.to, b.material, b.rotation, overlay=True) for b in boxes if b.material in ACCENT_MATERIALS]
    slots = []
    for b in boxes:
        for face in FACES:
            fw, fh = b.face_size(face)
            slots.append(Slot(b, face, max(2, math.ceil(fw * density - 1e-6)), max(2, math.ceil(fh * density - 1e-6))))
    for size in TEXTURE_SIZES:
        if pack(slots, size):
            break
    else:
        raise ValueError(f"{name}: Flaechen passen nicht in {TEXTURE_SIZES[-1]}px")
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    unit = 16 / size
    particle_uv = None
    for s in slots:
        if s.box.overlay:
            accent_face(img, s)
        else:
            paint_face(img, s)
        u0, v0, u1, v1 = s.x * unit, s.y * unit, (s.x + s.w) * unit, (s.y + s.h) * unit
        # Nord- und Westseite gespiegelt, damit Vorder- und Rueckseite gleich herum gelesen werden
        uv = [u1, v0, u0, v1] if s.face in ("north", "west") else [u0, v0, u1, v1]
        s.box.faces[s.face] = [round(v, 4) for v in uv]
        if particle_uv is None and s.box.material == particle_material and not s.box.overlay:
            particle_uv = s
    elements = []
    for b in boxes:
        element = {"from": [round(v, 4) for v in b.frm], "to": [round(v, 4) for v in b.to]}
        if b.rotation is not None:
            element["rotation"] = b.rotation
        if b.overlay:
            element["faces"] = {f: {"uv": b.faces[f], "texture": "#blade", "tintindex": 0} for f in FACES}
        else:
            element["faces"] = {f: {"uv": b.faces[f], "texture": "#blade"} for f in FACES}
        elements.append(element)
    model = {
        "credit": "made by SANTIQ — tools/generate_item_models.py",
        "gui_light": "front",
        "textures": {"blade": texture, "particle": texture},
        "elements": elements,
    }
    if display is None:
        del model["gui_light"]
    else:
        model["display"] = DISPLAYS[display]
    return model, img


def png_bytes(img: Image.Image) -> bytes:
    buffer = io.BytesIO()
    img.save(buffer, format="PNG", optimize=False)
    return buffer.getvalue()


def outputs(root: Path) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for name in WEAPONS:
        model, img = build(name)
        files[root / "models" / "item" / f"{name}.json"] = (json.dumps(model, indent=2) + "\n").encode()
        files[root / "textures" / "item" / "3d" / f"{name}.png"] = png_bytes(img)
    for name in PARTS:
        model, img = build(name)
        files[root / "models" / "omnitrix" / f"{name}.json"] = (json.dumps(model, indent=2) + "\n").encode()
        files[root / "textures" / "item" / "3d" / f"omnitrix_{name}.png"] = png_bytes(img)
    return files


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="assets/kingdomomnitrix-Ordner (Standard: %(default)s)")
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    parser.add_argument("--preview", type=Path, help="Ordner fuer 8-fach vergroesserte Textur-Vorschauen")
    parser.add_argument("-v", "--verbose", action="store_true", help="jede Datei protokollieren")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    try:
        files = outputs(args.root)
    except ValueError as exc:
        LOG.error("%s", exc)
        return 1

    if args.check:
        stale = []
        for path, data in files.items():
            if not path.is_file():
                stale.append(path)
            elif path.suffix == ".png":
                with Image.open(path) as old, Image.open(io.BytesIO(data)) as new:
                    if old.size != new.size or old.convert("RGBA").tobytes() != new.convert("RGBA").tobytes():
                        stale.append(path)
            elif path.read_bytes() != data:
                stale.append(path)
        for path in stale:
            LOG.error("veraltet oder fehlt: %s", path)
        LOG.info("%d/%d Waffen-Dateien aktuell", len(files) - len(stale), len(files))
        return 1 if stale else 0

    for path, data in files.items():
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        except OSError as exc:
            LOG.error("konnte %s nicht schreiben: %s", path, exc)
            return 1
        LOG.debug("geschrieben: %s", path)
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        for path, data in files.items():
            if path.suffix == ".png":
                with Image.open(io.BytesIO(data)) as img:
                    img.resize((img.width * 8, img.height * 8), Image.NEAREST).save(args.preview / path.name)
    LOG.info("%d Waffen-Dateien nach %s geschrieben", len(files), args.root)
    return 0


if __name__ == "__main__":
    sys.exit(main())
