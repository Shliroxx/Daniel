#!/usr/bin/env python3
"""Baut die Aliens, fuer die Alien Evolution kein Modell hat (Rath, Spidermonkey, Way Big, Alien X, Brainstorm, Goop),
im selben Stil und Dateiformat wie die AE-Importe (tools/import_alienevo.py):

* Geometrie aus Bloecken (Blockbench-Stil, Bedrock-Format) nach den Vorlagen aus Alien Force / Ultimate Alien.
  Knochen wie beim Import: head, body, right_arm, left_arm, right_leg, left_leg (+ right_forearm, left_forearm,
  right_lower_arm, left_lower_arm, tail_N, freie Extras).
* Textur in doppelter Aufloesung (2 Pixel je Einheit): jede Box bekommt ihren Platz (Box-UV, automatisch gepackt).
  Jedes Pixel kennt seine Lage im Modell; die Farbzonen eines Aliens (weisse Brust, rote Stiefel, Tigerstreifen,
  dunkles Gesicht …) werden als Funktion dieser Lage beschrieben und laufen so ueber Kanten hinweg weiter.
  Danach wie bei AE: Licht von oben, weiche Kanten, feines Rauschen — plus dunkle Linien an Zonengrenzen
  (Cartoon-Umriss). Gesichter, Augen und das Omnitrix-Symbol sind Pixel-Bilder auf der jeweiligen Flaeche.
* Leuchtmaske (Augen, Symbol, Sterne, Projektor), Ego-Arme, Daueranimationen (Schwanz, Schaedelplatten,
  Projektor, Beine), alien_render.

    python tools/generate_custom_aliens.py            # schreiben
    python tools/generate_custom_aliens.py --check    # pruefen, ob alles aktuell ist (CI)

Eigene Modelle (Kingdom Omnitrix), keine AE-Dateien. Benoetigt Pillow.
"""
from __future__ import annotations

import argparse
import json
import logging
import math
import random
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import import_alienevo as imp  # noqa: E402  (Ego-Arme, Animationssatz)

LOG = logging.getLogger("custom_aliens")
ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
DATA = ASSETS.parent.parent / "data" / "kingdomomnitrix" / "kingdomomnitrix" / "alien"
ATLAS = 128   # Atlasbreite in Modell-Einheiten
PX = 4        # Pixel je Einheit (feine Linien wie bei den Minecraft-Ben-10-Modellen)
ART = PX // 2  # Pixel-Bilder (Gesichter, Symbol) sind fuer 2 Pixel je Einheit gezeichnet und werden hochskaliert

Color = tuple[int, int, int]


def rgb(value: str) -> Color:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def shade(color: Color, k: float) -> Color:
    return tuple(max(0, min(255, round(c * k))) for c in color)  # type: ignore[return-value]


def mix(a: Color, b: Color, t: float) -> Color:
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))  # type: ignore[return-value]


# --- Modell-Beschreibung ---------------------------------------------------------------------------


@dataclass
class Box:
    origin: tuple[float, float, float]
    size: tuple[float, float, float]   # Vielfache von 0,5 (ganze Pixel bei 2 Pixeln je Einheit)
    mat: str
    part: str = ""                  # Name fuer die Farbzonen (leer = Knochenname)
    front: str | None = None        # Bild auf der Vorderseite (north), siehe FEATURES
    back: str | None = None         # Bild auf der Rueckseite (south)
    top: str | None = None          # Bild auf der Oberseite (up)


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple[float, float, float]
    boxes: list[Box] = field(default_factory=list)
    rotation: tuple[float, float, float] | None = None


@dataclass
class Material:
    base: str
    dark: str
    light: str
    pattern: str = "plain"          # plain, fur, stars, slime, metal, brain, shell
    glow: bool = False              # ganze Flaeche leuchtet
    outline: bool = True            # dunkle Linie an Grenzen zu anderen Zonen
    rim: str | None = None          # helle Randlinie um jede Flaeche (Alien X: weisse Kontur der Vorlage)


@dataclass(frozen=True)
class Texel:
    """Ein Texturpixel mit Lage im Modell (Einheiten) und auf seiner Flaeche (0..1)."""
    part: str
    side: str
    x: float
    y: float
    z: float
    fx: float
    fy: float


Zone = Callable[[Texel], "str | None"]


@dataclass
class Design:
    name: str
    ae_size: float                  # sichtbare Groesse (wie AE „size_change“)
    style: str                      # Animations-Charakter (Verwandeln/Zurueck)
    accent: str
    materials: dict[str, Material]
    bones: list[Bone]
    zone: Zone | None = None        # Farbzonen: Pixel → Material (None = Material der Box)
    arm_swing: float = 1.0
    leg_swing: float = 1.0
    loops: dict[str, dict] = field(default_factory=dict)  # Knochen → Bedrock-Spuren der Daueranimation
    seed: int = 1


# --- Maler -----------------------------------------------------------------------------------------

# Flaechenhelligkeit wie bei Minecraft-Mobs (oben hell, unten dunkel, Seiten etwas dunkler)
LIGHT = {"up": 1.1, "down": 0.62, "north": 1.0, "south": 0.84, "east": 0.9, "west": 0.9}


class Painter:
    def __init__(self, design: Design, width: int, height: int) -> None:
        self.design = design
        self.color = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        # Omnitrix-Symbol (gruene Pixel): BadgeTint faerbt genau diese um (Spieler-Farbe), nicht Augen o. Ae.
        self.badge = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.rng = random.Random(design.seed)

    def put(self, x: int, y: int, c: Color, glow: bool = False) -> None:
        if 0 <= x < self.color.width and 0 <= y < self.color.height:
            self.color.putpixel((x, y), (*c, 255))
            self.glow.putpixel((x, y), (*c, 255) if glow else (0, 0, 0, 0))

    def face(self, rect: tuple[int, int, int, int], box: Box, bone: Bone, side: str) -> None:
        """Eine Flaeche bemalen: Zonen bestimmen, schattieren, Zonengrenzen nachziehen."""
        u, v, w, h = rect
        design = self.design
        ox, oy, oz = box.origin
        bw, bh, bd = box.size
        part = box.part or bone.name
        keys: list[list[str]] = []
        for py in range(h):
            row = []
            for px in range(w):
                fx, fy = (px + 0.5) / w, (py + 0.5) / h
                x, y, z = _world(side, ox, oy, oz, bw, bh, bd, fx, fy)
                key = design.zone(Texel(part, side, x, y, z, fx, fy)) if design.zone else None
                row.append(key or box.mat)
            keys.append(row)
        light = LIGHT[side]
        for py in range(h):
            for px in range(w):
                key = keys[py][px]
                mat = design.materials[key]
                c = self.texel(mat, light, px, py, w, h, box, side)
                if mat.outline and ((px + 1 < w and keys[py][px + 1] != key and _darker(design, keys[py][px + 1], key))
                                    or (py + 1 < h and keys[py + 1][px] != key and _darker(design, keys[py + 1][px], key))
                                    or (px > 0 and keys[py][px - 1] != key and _darker(design, keys[py][px - 1], key))
                                    or (py > 0 and keys[py - 1][px] != key and _darker(design, keys[py - 1][px], key))):
                    c = shade(rgb(mat.dark), 0.8)
                self.put(u + px, v + py, c, mat.glow or (mat.pattern == "stars" and c == STAR))

    def texel(self, mat: Material, light: float, px: int, py: int, w: int, h: int, box: Box, side: str) -> Color:
        """Flache Cartoon-Farben wie bei den Minecraft-Ben-10-Modellen: Grundfarbe, Licht je Flaechenrichtung, sanfter
        Verlauf von oben, 1 Pixel dunklerer Rand. Kein Rauschen — Muster nur, wo das Alien eines hat."""
        base, dark, hi = rgb(mat.base), rgb(mat.dark), rgb(mat.light)
        t = py / max(1, h - 1) if side not in ("up", "down") else 0.3
        k = light * (1.04 - 0.1 * t)
        if px == 0 or py == 0 or px == w - 1 or py == h - 1:
            if mat.rim and side not in ("up", "down"):
                return rgb(mat.rim)
            k *= 0.9
        c = shade(base, k)
        pat = mat.pattern
        if pat == "stars":
            roll = self.rng.random()
            c = shade(base, 0.95 + 0.1 * self.rng.random())
            if roll < 0.014:
                return STAR
            if roll < 0.024:
                return shade(hi, 1.0)
        elif pat == "slime":
            # Glanzstreifen nahe der linken Kante und ein heller Lichtpunkt oben — glatter Gel-Look
            fx = (px + 0.5) / w
            if side in ("north", "west", "east") and 0.14 < fx < 0.3 and 0.1 < py / max(1, h) < 0.8:
                c = shade(mix(base, hi, 0.55), k)
            elif side == "up":
                c = shade(mix(base, hi, 0.25), k)
        elif pat == "metal":
            c = shade(mix(base, hi, 0.45 if (py % (2 * ART) == ART) else 0.0), k)
        elif pat == "brain":
            # Hirnwindungen: geschwungene dunkle Furchen
            qx, qy = px / ART, py / ART
            fold = math.sin(qx * 1.1 + math.sin(qy * 0.9) * 2.0) + math.sin(qy * 1.7 - qx * 0.3)
            c = shade(dark if fold > 1.05 else (hi if fold < -1.3 else base), light * 1.02)
        return c


STAR: Color = (255, 255, 255)


def _darker(design: Design, other: str, mine: str) -> bool:
    """Linie nur auf der helleren Seite einer Grenze ziehen (die dunklere Zone ist selbst schon Kontur)."""
    a, b = rgb(design.materials[other].base), rgb(design.materials[mine].base)
    return sum(a) < sum(b) or (sum(a) == sum(b) and other < mine)


def _world(side: str, ox: float, oy: float, oz: float, w: int, h: int, d: int, fx: float, fy: float) -> tuple[float, float, float]:
    """Lage eines Flaechenpixels im Modell. Box-UV-Abwicklung: rechte Seite (−x) | vorn (north, −z) | linke
    Seite (+x) | hinten; die Kanten der Streifen schliessen aneinander an."""
    y = oy + h * (1 - fy)
    if side == "north":
        return ox + w * fx, y, oz
    if side == "south":
        return ox + w * (1 - fx), y, oz + d
    if side == "east":   # erster Streifen: −x-Seite, rechts grenzt die Vorderseite an
        return ox, y, oz + d * (1 - fx)
    if side == "west":
        return ox + w, y, oz + d * fx
    if side == "up":
        return ox + w * fx, oy + h, oz + d * (1 - fy)
    return ox + w * fx, oy, oz + d * fy


def art(p: Painter, rect: tuple[int, int, int, int], rows: list[str], palette: dict[str, tuple[str, bool]],
        cx: float = 0.5, cy: float = 0.5, badge: str = "") -> None:
    """Pixel-Bild auf eine Flaeche setzen, Mittelpunkt an (cx, cy) der Flaeche."""
    u, v, w, h = rect
    x0 = u + round(w * cx - len(rows[0]) * ART / 2)
    y0 = v + round(h * cy - len(rows) * ART / 2)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in palette:
                color, glow = palette[ch]
                for dy in range(ART):
                    for dx in range(ART):
                        p.put(x0 + i * ART + dx, y0 + j * ART + dy, rgb(color), glow)
                        if ch in badge:
                            p.badge.putpixel((x0 + i * ART + dx, y0 + j * ART + dy), (255, 255, 255, 255))


BADGE = ["..rrrrrr..",
         ".rggggggr.",
         "rkggggggkr",
         "rkkggggkkr",
         "rkkkggkkkr",
         "rkkkggkkkr",
         "rkkggggkkr",
         "rkggggggkr",
         ".rggggggr.",
         "..rrrrrr.."]
BADGE_COLORS = {"r": ("#C9CDD2", False), "k": ("#151515", False), "g": ("#5BFF3A", True)}


def badge(p: Painter, rect, cx: float = 0.5, cy: float = 0.4) -> None:
    """Omnitrix-Symbol: silberner Ring, gruene Sanduhr, schwarze Seitenkeile."""
    art(p, rect, BADGE, BADGE_COLORS, cx, cy, badge="g")


# --- Gesichter und Abzeichen (Pixel-Bilder, 2 Pixel je Einheit) --------------------------------------

def f_rath_face(p, rect):
    art(p, rect, [".kkk........kkk.",
                  "..kkkk....kkkk..",
                  "...kkkkkkkkkk...",
                  "..wgggk..kgggw..",
                  "..wggkk..kkggw..",
                  "...wwww..wwww..."],
        {"k": ("#141414", False), "g": ("#8CF23A", True), "w": ("#F4F1E6", False)}, 0.5, 0.36)


def f_rath_mouth(p, rect):
    art(p, rect, ["....kkkk....",
                  ".....kk.....",
                  "kkkkkkkkkkkk",
                  "kwkwkwkwkwkk",
                  "krrrrrrrrrrk",
                  "kkkkkkkkkkkk"],
        {"k": ("#1E1210", False), "w": ("#F4F1E6", False), "r": ("#7A2A24", False)}, 0.5, 0.5)


def f_spider_face(p, rect):
    art(p, rect, ["...kk..kk...",
                  "...gk..kg...",
                  "............",
                  ".kkkk..kkkk.",
                  ".kggk..kggk.",
                  ".kggk..kggk.",
                  ".kkkk..kkkk.",
                  "............",
                  ".....kk....."],
        {"k": ("#06080F", False), "g": ("#62F03A", True)}, 0.5, 0.45)


def f_spider_mouth(p, rect):
    art(p, rect, ["kkkkkk", "wwwwww", "kkkkkk"], {"k": ("#06080F", False), "w": ("#E8ECF2", False)}, 0.5, 0.5)


def f_waybig_eyes(p, rect):
    art(p, rect, ["kk....kk",
                  "kgk..kgk",
                  ".kgkkgk.",
                  "..k..k.."],
        {"k": ("#111111", False), "g": ("#7CF23A", True)}, 0.5, 0.6)


def f_alienx_face(p, rect):
    art(p, rect, [".gg....gg.",
                  ".ggg..ggg.",
                  "..gg..gg.."],
        {"g": ("#8CFF52", True)}, 0.5, 0.5)


def f_brainstorm_face(p, rect):
    art(p, rect, ["........................",
                  "..kkkkk..........kkkkk..",
                  ".kgggggk........kgggggk.",
                  "..kkkkk..........kkkkk..",
                  "........................",
                  "...kkkkkkkkkkkkkkkkkk...",
                  "..kkwkwkwkwkwkwkwkwkkk..",
                  "..k..................k..",
                  "........................"],
        {"k": ("#20140A", False), "g": ("#8CF23A", True), "w": ("#F2EEE2", False)}, 0.5, 0.5)


def f_goop_face(p, rect):
    art(p, rect, ["k.....k",
                  "kk...kk",
                  "kgk.kgk",
                  ".kk.kk."],
        {"k": ("#0D2A08", False), "g": ("#E8FF7A", True)}, 0.5, 0.42)


def f_chest(p, rect):
    badge(p, rect, 0.5, 0.42)


def f_chest_small(p, rect):
    art(p, rect, [".rrrr.", "rggggr", "rkggkr", "rkggkr", "rggggr", ".rrrr."], BADGE_COLORS, 0.5, 0.5, badge="g")


FEATURES: dict[str, Callable] = {
    "rath_face": f_rath_face, "rath_mouth": f_rath_mouth, "spider_face": f_spider_face,
    "spider_mouth": f_spider_mouth, "waybig_eyes": f_waybig_eyes, "alienx_face": f_alienx_face,
    "brainstorm_face": f_brainstorm_face, "goop_face": f_goop_face, "chest": f_chest, "chest_small": f_chest_small,
}


# --- Bauen -----------------------------------------------------------------------------------------


def pack(boxes: list[Box]) -> tuple[dict[int, tuple[int, int]], int]:
    """Box-UV-Felder in Regalen packen (Einheiten); Rueckgabe: Feld je Box (id) und noetige Atlas-Hoehe."""
    order = sorted(boxes, key=lambda b: -(b.size[2] + b.size[1]))
    pos: dict[int, tuple[int, int]] = {}
    x = y = shelf = 0
    for box in order:
        w, h, d = box.size
        bw, bh = math.ceil(2 * (w + d)), math.ceil(d + h)
        if bw > ATLAS:
            raise ValueError(f"Box zu breit fuer den Atlas: {box}")
        if x + bw > ATLAS:
            x, y, shelf = 0, y + shelf, 0
        pos[id(box)] = (x, y)
        x += bw
        shelf = max(shelf, bh)
    height = y + shelf
    return pos, max(32, 1 << (height - 1).bit_length())


def build(design: Design) -> dict[Path, object]:
    for bone in design.bones:
        for box in bone.boxes:
            if any(s * PX != int(s * PX) or s < 0 for s in box.size):
                raise ValueError(f"{design.name}/{bone.name}: Boxgroessen muessen Vielfache von 0,5 sein: {box.size}")
    boxes = [box for bone in design.bones for box in bone.boxes]
    uv, height = pack(boxes)
    painter = Painter(design, ATLAS * PX, height * PX)
    geo_bones = []
    for bone in design.bones:
        entry: dict = {"name": bone.name, "pivot": list(bone.pivot)}
        if bone.parent:
            entry["parent"] = bone.parent
        if bone.rotation:
            entry["rotation"] = list(bone.rotation)
        cubes = []
        for box in bone.boxes:
            u, v = uv[id(box)]
            cubes.append({"origin": list(box.origin), "size": list(box.size), "uv": [u, v]})
            paint_box(painter, design, bone, box, u, v)
        if cubes:
            entry["cubes"] = cubes
        geo_bones.append(entry)
    name = design.name
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.kingdomomnitrix.{name}", "texture_width": ATLAS, "texture_height": height,
                        "visible_bounds_width": 4, "visible_bounds_height": 4.5, "visible_bounds_offset": [0, 1.75, 0]},
        "bones": [{"name": "root", "pivot": [0, 0, 0]}] + [dict(b, parent=b.get("parent", "root")) for b in geo_bones]}]}
    bones_all = geo["minecraft:geometry"][0]["bones"]
    files: dict[Path, object] = {
        ASSETS / "geo" / "entity" / "alien" / f"{name}.geo.json": geo,
        ASSETS / "textures" / "entity" / "alien" / f"{name}.png": painter.color,
        ASSETS / "textures" / "entity" / "alien" / f"{name}_arms.png": imp.arm_skin(bones_all, painter.color, (ATLAS, height)),
    }
    if painter.badge.getbbox() is not None:
        files[ASSETS / "textures" / "entity" / "alien" / f"{name}_badgemask.png"] = painter.badge
    if painter.glow.getbbox() is not None:
        files[ASSETS / "textures" / "entity" / "alien" / f"{name}_glowmask.png"] = painter.glow
    files[ASSETS / "animations" / "entity" / "alien" / f"{name}.animation.json"] = animations(design, bones_all)
    files[ASSETS / "alien_render" / f"{name}.json"] = {
        "scale": render_scale(design), "uniforms": ["classic"], "uniform_models": False,
        "vanilla_pose": True, "arm_swing": design.arm_swing, "leg_swing": design.leg_swing,
        "ability_poses": [], "glow_frames": 1, "warn_textures": False, "source": "Kingdom Omnitrix (eigenes Modell)"}
    return files


def paint_box(p: Painter, design: Design, bone: Bone, box: Box, u: int, v: int) -> None:
    w, h, d = box.size
    # Box-UV (Einheiten): oben (u+d,v) w×d, unten (u+d+w,v) w×d, Reihe v+d: east (u), north (u+d), west (u+d+w), south (u+2d+w)
    faces = {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
             "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}
    rects = {side: (int(fu * PX), int(fv * PX), int(fw * PX), int(fh * PX)) for side, (fu, fv, fw, fh) in faces.items()}
    for side, rect in rects.items():
        if rect[2] and rect[3]:
            p.face(rect, box, bone, side)
    for side, feature in (("north", box.front), ("south", box.back), ("up", box.top)):
        if feature:
            FEATURES[feature](p, rects[side])


def render_scale(design: Design) -> float:
    path = DATA / f"{design.name}.json"
    data_scale = json.loads(path.read_text(encoding="utf-8")).get("scale", 1.0) if path.is_file() else 1.0
    return round(design.ae_size / data_scale, 3)


def animations(design: Design, bones: list[dict]) -> dict:
    spec = imp.Spec("custom", design.name, "", design.ae_size, design.style, design.accent)
    result = imp.build_animations(None, design.name, spec, bones)  # ohne AE-Schleifen: kein Jar noetig
    anims = result["animations"]
    if design.loops:
        length = 2.0
        for key in ("idle", "walk", "run", "jump", "fall"):
            anims[key]["animation_length"] = length
            anims[key]["bones"].update(json.loads(json.dumps(design.loops)))
    return result


# --- Hilfen fuer Entwuerfe -------------------------------------------------------------------------

def sway(axis: str, amount: float, speed: float = 180.0, phase: float = 0.0) -> dict:
    """Bedrock-Pendelspur (Molang): Drehung um eine Achse."""
    expr = f"math.sin(query.anim_time*{speed}+{phase})*{amount}"
    vec = {"x": [expr, 0, 0], "y": [0, expr, 0], "z": [0, 0, expr]}[axis]
    return {"rotation": {"vector": vec}}


def edge(t: Texel) -> float:
    """Abstand zum naechsten Flaechenrand quer zur Hoehe (0 am Rand, 0,5 in der Mitte)."""
    return min(t.fx, 1 - t.fx)


def tiger(t: Texel, period: float, width: float, offset: float = 0.0, slant: float = 0.0) -> bool:
    """Tigerstreifen: Keile, an den Flaechenkanten breit, zur Mitte spitz; laufen ueber Kanten weiter."""
    phase = (t.y + offset + slant * abs(t.x)) % period
    taper = max(0.0, 1.0 - 1.7 * edge(t))
    return abs(phase - period / 2) < width * taper


def side_of(sign: int) -> str:
    return "right" if sign < 0 else "left"


# --- Entwuerfe -------------------------------------------------------------------------------------

def rath() -> Design:
    """Rath (Turnaround/Posen AF-UA): breiter Muskelprotz, kein Schwanz. Weiss: Brust, Bauch, Kiefer, Schnauze,
    Backenbart, Faeuste, Fuesse. Schwarze Tigerstreifen auf Schultern, Armen, Kopf, Flanken, Oberschenkeln.
    Schwarze Klingen-Kralle aus jedem Handgelenk, schwere schwarze Brauen, gruene Augen; Omnitrix mitten auf der Brust."""
    m = {"orange": Material("#E2701F", "#8E3A0C", "#F7A04E", "plain"),
         "white": Material("#ECE9E1", "#A29C90", "#FFFFFF", "plain"),
         "line": Material("#BDB6A8", "#8A8478", "#D8D2C6", "plain", outline=False),
         "stripe": Material("#1A1411", "#0A0806", "#3A2E28", "plain"),
         "claw": Material("#1E1E24", "#08080A", "#6A6A78", "metal"),
         "nose": Material("#3A221C", "#1A0E0A", "#5A3A30", "plain")}

    def zone(t: Texel) -> str | None:
        part, y, ax = t.part, t.y, abs(t.x)
        if part == "torso":
            if t.side == "north" and ax < (4.8 if y > 19 else 3.4 + (y - 16) * 0.47):
                return "white"
            if tiger(t, 4.0, 0.7, 0.6) and (t.side != "north" or edge(t) < 0.1):
                return "stripe"
        if part == "belly":
            if t.side == "north" and ax < 3.4:
                return "white"
            if t.side in ("east", "west") and tiger(t, 4.0, 0.7, 0.6):
                return "stripe"
        if part == "abs" and (abs(t.x) < 0.25 or abs(y - 14.5) < 0.25):
            return "line"
        if part == "head":
            if t.side == "north" and t.fy > 0.6:
                return "white"
            if t.side == "north" and t.fy < 0.2 and 0.6 < ax < 1.4:
                return "stripe"
            if t.side in ("east", "west") and t.fy < 0.65 and tiger(t, 2.4, 0.6, 0.3):
                return "stripe"
            if t.side == "up" and ax < 0.6 and t.fy > 0.4:
                return "stripe"
        if part in ("shoulder", "upper_arm") and t.side != "up" and tiger(t, 4.5, 0.6, 1.1):
            return "stripe"
        if part == "trap" and t.side == "south" and tiger(t, 4.5, 0.6, 1.1):
            return "stripe"
        if part == "forearm" and y > 12 and tiger(t, 4.5, 0.6, 0.2):
            return "stripe"
        if part == "thigh" and tiger(t, 4.0, 0.55, 0.4):
            return "stripe"
        return None

    bones = [
        Bone("body", None, (0, 24, 0), [
            Box((-6, 16, -3.5), (12, 8, 7), "orange", "torso", front="chest"),
            Box((-4.5, 23.5, -1.5), (9, 2, 5), "orange", "trap"),
            Box((-5, 12, -3), (10, 4, 6), "orange", "belly"),
            Box((-3, 12.5, -3.5), (6, 3.5, 0.5), "white", "abs"),
            Box((-4.5, 10, -2.5), (9, 2.5, 5), "orange", "belly")]),
        Bone("head", None, (0, 23.5, -2), [
            Box((-4, 22, -8.5), (8, 6.5, 7), "orange", "head", front="rath_face"),
            Box((-3, 21.5, -9.5), (6, 3.5, 1), "white", "muzzle", front="rath_mouth"),
            Box((-3.5, 21, -8.5), (7, 1, 5), "white", "jaw"),
            Box((-5, 21.5, -7.5), (1, 3.5, 4), "white", "cheek"),
            Box((4, 21.5, -7.5), (1, 3.5, 4), "white", "cheek"),
            Box((-4, 28.5, -5), (2, 1.5, 1), "orange", "ear"),
            Box((2, 28.5, -5), (2, 1.5, 1), "orange", "ear"),
            Box((-3.5, 30, -5), (1, 0.5, 1), "stripe", "ear"),
            Box((2.5, 30, -5), (1, 0.5, 1), "stripe", "ear")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 8.5
        inner = sx - sign * 3 - (1 if sign > 0 else 0)
        outer = sx + sign * 3.75 - 0.25
        bones += [
            Bone(f"{side}_arm", None, (sx, 23, 0), [
                Box((sx - 3, 19.5, -3), (6, 5, 6), "orange", "shoulder"),
                Box((sx - 2.5, 15, -2.5), (5, 5, 5), "orange", "upper_arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 15, 0), [
                Box((sx - 3.5, 7.5, -3.5), (7, 7.5, 7), "orange", "forearm"),
                Box((sx - 3, 1.5, -3.5), (6, 6, 6.5), "white", "fist")]
                + [Box((sx - 3 + i * 1.5, 1.5, -4.5), (1.5, 3.5, 1), "white", "finger") for i in range(4)]
                + [Box((inner - sign * 0.5, 3, -3), (1.5, 2.5, 2.5), "white", "fist")]),
            Bone(f"{side}_claw", f"{side}_forearm", (outer, 8, -1), [
                Box((outer, 0, -1.75), (0.5, 8, 2), "claw", "claw"),
                Box((outer, -1, -1.25), (0.5, 1, 1), "claw", "claw")], rotation=(-15, 0, 0)),
            Bone(f"{side}_leg", None, (sign * 3.2, 11, 0), [
                Box((sign * 3.2 - 2.5, 5.5, -2.5), (5, 5.5, 5), "orange", "thigh"),
                Box((sign * 3.2 - 2, 2, -2), (4, 3.5, 4), "orange", "shin"),
                Box((sign * 3.2 - 3, 0, -5), (6, 2.5, 7.5), "white", "foot")]
                + [Box((sign * 3.2 - 2.75 + i * 2, 0, -6), (1.5, 1.5, 1), "white", "toe") for i in range(3)]),
        ]
    return Design("rath", 1.25, "heavy", "#E2701F", m, bones, zone, arm_swing=0.8, leg_swing=0.8, seed=11)


def spidermonkey() -> Design:
    """Spidermonkey (Turnaround AF-UA): laeuft gebueckt auf allen Vieren — Oberkoerper nach vorn geneigt, alle vier
    Arme reichen zum Boden, Beine hinten, langer Schwanz im S-Bogen mit Haken und zwei dunklen Ringen. Kopf mit nach
    hinten fallender Maehne, dunkles Gesicht mit vier gruenen Augen (zwei gross, zwei klein), Nasenloecher, Zaehne;
    Fellbueschel an Ellbogen und Wangen; Haende und Fuesse dunkel mit drei langen Fingern/Zehen."""
    m = {"blue": Material("#2E5FD6", "#173270", "#5F8FF6", "plain"),
         "navy": Material("#1B2350", "#0B0F24", "#2F3C7A", "plain"),
         "mouth": Material("#7E8FB8", "#46557E", "#B4C2E2", "plain")}

    def zone(t: Texel) -> str | None:
        part = t.part
        if part == "head" and t.side == "north":
            dx, dy = (t.fx - 0.5) / 0.42, (t.fy - 0.6) / 0.5
            if dx * dx + dy * dy < 1.0:
                return "navy"
        if part == "chest" and t.side == "north" and abs(t.fx - 0.5) < 0.32 and t.fy > 0.12 + (0.32 - abs(t.fx - 0.5)) * 0.7:
            return "navy"
        if part == "belly" and t.side in ("north", "down") and abs(t.fx - 0.5) < 0.32:
            return "navy"
        if part == "tail_tip" and (t.z % 2.0) < 0.7:
            return "navy"
        return None

    bones = [
        Bone("body", None, (0, 13, 0), [
            Box((-3, 10, -2.5), (6, 4.5, 5.5), "blue", "belly"),
            Box((-3.5, 9.5, 2.5), (7, 4.5, 4.5), "blue", "hips")]),
        # Oberkoerper nach vorn geneigt (eigener Knochen; body selbst folgt der Spielerpose)
        Bone("chest", "body", (0, 13, -1.5), [
            Box((-3.5, 13, -4), (7, 7, 5), "blue", "chest", front="chest"),
            Box((-3, 15, 1), (6, 4.5, 1), "blue", "chest")], rotation=(40, 0, 0)),
        Bone("head", None, (0, 19, -7), [
            Box((-3, 18, -11), (6, 6, 5), "blue", "head", front="spider_face"),
            Box((-1.5, 18, -12), (3, 2, 1), "navy", "muzzle", front="spider_mouth"),
            Box((-3, 23.5, -10.5), (6, 1, 6), "blue", "mane"),
            Box((-2.5, 21.5, -6), (5, 2.5, 3), "blue", "mane"),
            Box((-2, 19.5, -3.5), (4, 2.5, 1.5), "blue", "mane"),
            Box((-3.5, 18.5, -9.5), (0.5, 3, 2.5), "blue", "tuft"),
            Box((3, 18.5, -9.5), (0.5, 3, 2.5), "blue", "tuft"),
            Box((-1, 17.5, -10.5), (2, 0.5, 1), "blue", "tuft")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 3.8
        bones += [
            # vorderes Armpaar = Vorderbeine (folgt dem Spielerarm)
            Bone(f"{side}_arm", None, (sx, 17, -5), [
                Box((sx - 1, 9.5, -6), (2, 8, 2), "blue", "arm"),
                Box((sx - 1, 9.5, -4), (2, 2.5, 0.5), "blue", "tuft")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 9.5, -5), [
                Box((sx - 1, 2.5, -6), (2, 7, 2), "blue", "arm"),
                Box((sx - 1, 6, -4), (2, 2, 0.5), "blue", "tuft"),
                Box((sx - 1.5, 0.5, -7), (3, 2, 3), "navy", "hand")]
                + [Box((sx - 1.5 + i * 1.25, 0, -9), (0.5, 0.5, 2), "navy", "finger") for i in range(3)]),
            Bone(f"{side}_leg", None, (sign * 2.5, 11, 4), [
                Box((sign * 2.5 - 1.5, 6, 2.5), (3, 5, 4), "blue", "thigh"),
                Box((sign * 2.5 - 1, 1, 5), (2, 5.5, 2), "blue", "shin"),
                Box((sign * 2.5 - 1.5, 0, 1), (3, 1, 5.5), "navy", "foot")]
                + [Box((sign * 2.5 - 1.5 + i * 1.25, 0, -0.5), (0.5, 0.5, 1.5), "navy", "toe") for i in range(3)]),
        ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 4.2
        # hinteres Armpaar: haengt am Arm der Gegenseite → laeuft gegenphasig (Kreuzgang)
        bones.append(Bone(f"{side}_rear_arm", f"{side_of(-sign)}_arm", (sx, 15, -2.5), [
            Box((sx - 1, 8, -3.5), (2, 7, 2), "blue", "arm"),
            Box((sx - 1, 8, -1.5), (2, 2, 0.5), "blue", "tuft"),
            Box((sx - 1, 2, -3.5), (2, 6, 2), "blue", "arm"),
            Box((sx - 1.5, 0.5, -4.5), (3, 1.5, 3), "navy", "hand")]
            + [Box((sx - 1.5 + i * 1.25, 0, -6), (0.5, 0.5, 1.5), "navy", "finger") for i in range(3)]))
    tail = [("tail_1", "body", 55, (2, 2, 4)), ("tail_2", "tail_1", 25, (1.5, 1.5, 4)), ("tail_3", "tail_2", 15, (1.5, 1.5, 3.5)),
            ("tail_4", "tail_3", 45, (1.5, 1.5, 3)), ("tail_5", "tail_4", 50, (1, 1, 2.5))]
    z = 6.5
    for name, parent, angle, size in tail:
        w, h, d = size
        bones.append(Bone(name, parent, (0, 13, z), [Box((-w / 2, 13 - h / 2, z), size, "blue",
                                                         "tail_tip" if name in ("tail_4", "tail_5") else "tail")],
                          rotation=(angle, 0, 0)))
        z += d
    loops = {"tail_1": sway("y", 8, 110), "tail_2": sway("x", 6, 110, 30), "tail_3": sway("x", 8, 110, 60),
             "tail_4": sway("x", 10, 110, 90)}
    return Design("spidermonkey", 0.9, "fast", "#2E5FD6", m, bones, zone, loops=loops, seed=12)


def way_big() -> Design:
    """Way Big (Modell AF-UA): schlanker weisser Riese. Hoher schmaler Kopfkamm, vorn rot, hinten/unten schwarz;
    schmaler Schaedel mit gruenen Augen, schwarzes Untergesicht und schwarzer Hals; rote Linien von den Schultern zum
    Omnitrix; weisse Arme mit langen roten Flossen am Unterarm, schwarze Handgelenke; rote stiefelartige Unterschenkel
    mit Spitze und rote Fuesse."""
    m = {"white": Material("#EEEEF0", "#A6A8AE", "#FFFFFF", "plain"),
         "line": Material("#DCDEE4", "#B0B2BA", "#EEEEF2", "plain", outline=False),
         "red": Material("#C21A2A", "#6E0C16", "#EE4656", "plain"),
         "black": Material("#1A1A1E", "#060608", "#3A3A42", "plain")}

    def zone(t: Texel) -> str | None:
        part, y, ax = t.part, t.y, abs(t.x)
        if part == "crest" and (t.z > 0.3 or t.side in ("down", "south")):
            return "black"
        if part == "chest" and t.side == "north" and ax / 3.4 + abs(y - 22.6) / 3.0 < 1.0:
            return "red"
        if part == "arm" and abs(y - 19.5) < 0.5:
            return "black"
        if part == "forearm" and abs(y - 14) < 0.5:
            return "black"
        if part == "abs" and t.side == "north" and (ax < 0.2 or abs(y - 17.75) < 0.2):
            return "line"
        if part == "shin":
            peak = 5.5 + (2.0 * (1 - abs(t.fx - 0.5) * 2) if t.side == "north" else 0.0)
            if y < peak:
                return "red"
        return None

    bones = [
        Bone("body", None, (0, 26, 0), [
            Box((-4, 20, -2.5), (8, 6, 5), "white", "chest", front="chest"),
            Box((-3, 15.5, -2), (6, 4.5, 4), "white", "abs"),
            Box((-3.5, 13, -2.25), (7, 2.5, 4.5), "white", "hips")]),
        Bone("head", None, (0, 26, 0), [
            Box((-1.25, 25.5, -1.25), (2.5, 2, 2.5), "black", "neck"),
            Box((-2, 28, -2.5), (4, 4, 4.5), "white", "head", front="waybig_eyes"),
            Box((-1.5, 26.5, -2.75), (3, 2, 3.5), "black", "jaw"),
            Box((-2.25, 27.5, -2.25), (4.5, 1, 3.5), "black", "jaw")]),
        Bone("crest", "head", (0, 31.5, 0), [
            Box((-0.5, 31.5, -3), (1, 3, 5.5), "red", "crest"),
            Box((-0.5, 34.5, -2.75), (1, 3, 4), "red", "crest"),
            Box((-0.5, 37.5, -2.75), (1, 3, 2.5), "red", "crest"),
            Box((-0.5, 40.5, -2.5), (1, 1.5, 1.5), "red", "crest")], rotation=(-8, 0, 0)),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 5.5
        fin_x = sx + 1.25 if sign > 0 else sx - 1.75
        bones += [
            Bone(f"{side}_arm", None, (sx, 25, 0), [
                Box((sx - 1.5, 22.5, -1.5), (3, 3, 3), "white", "shoulder"),
                Box((sx - 1.25, 17.5, -1.25), (2.5, 5, 2.5), "white", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 17.5, 0), [
                Box((sx - 1.25, 10.5, -1.25), (2.5, 7, 2.5), "white", "forearm"),
                Box((sx - 1.5, 9, -1.5), (3, 1.5, 3), "black", "wrist"),
                Box((sx - 1.25, 6.5, -1.25), (2.5, 2.5, 2.5), "white", "hand"),
                Box((sx - 1.25, 5, -1.5), (2.5, 1.5, 1), "white", "hand")]),
            Bone(f"{side}_fin", f"{side}_forearm", (fin_x, 11, 0), [
                Box((fin_x, 10, -1), (0.5, 9, 2.5), "red", "fin"),
                Box((fin_x, 19, -0.5), (0.5, 3, 1.5), "red", "fin"),
                Box((fin_x, 12, 1.5), (0.5, 5, 1), "red", "fin"),
                Box((fin_x, 15, 2.5), (0.5, 2.5, 1), "red", "fin"),
                Box((fin_x, 22, 0), (0.5, 1.5, 0.5), "red", "fin")], rotation=(-10, 0, sign * 5)),
            Bone(f"{side}_leg", None, (sign * 2, 13, 0), [
                Box((sign * 2 - 1.5, 7.5, -1.75), (3, 5.5, 3.5), "white", "thigh"),
                Box((sign * 2 - 1.5, 1.5, -1.5), (3, 6, 3), "white", "shin"),
                Box((sign * 2 - 1.5, 0, -4), (3, 1.5, 5.5), "red", "foot"),
                Box((sign * 2 - 1, 0, -5), (2, 1, 1), "red", "foot")]),
        ]
    return Design("way_big", 3.0, "heavy", "#C21A2A", m, bones, zone, arm_swing=0.7, leg_swing=0.7, seed=13)


def alien_x() -> Design:
    """Alien X (Posen AF-UA): schlanker athletischer Humanoid, ganz schwarz mit Sternen (Nachthimmel) und heller
    Konturlinie, weisse Haende, gruene pupillenlose Augen; drei Hoerner an der Stirn — ein mittleres, zwei seitliche
    nach aussen und oben gebogen. Omnitrix auf der Brust."""
    m = {"space": Material("#07080F", "#000000", "#B8D8FF", "stars", rim="#E6EEFF"),
         "white": Material("#F6F8FF", "#B8C0D8", "#FFFFFF", "plain", glow=True)}

    bones = [
        Bone("body", None, (0, 24, 0), [
            Box((-4, 19, -2), (8, 5, 4), "space", "chest", front="chest"),
            Box((-3, 15, -1.75), (6, 4, 3.5), "space", "abs"),
            Box((-3.25, 12, -1.75), (6.5, 3, 3.5), "space", "hips")]),
        Bone("head", None, (0, 24.5, 0), [
            Box((-1.25, 24, -1.25), (2.5, 1.5, 2.5), "space", "neck"),
            Box((-2.5, 25.5, -2.5), (5, 5, 5), "space", "head", front="alienx_face"),
            Box((-1.5, 24.5, -2.75), (3, 1.5, 3), "space", "chin")]),
        Bone("horn_mid", "head", (0, 30.5, -1.5), [
            Box((-0.75, 30.5, -2.25), (1.5, 2.5, 1.5), "space", "horn"),
            Box((-0.5, 33, -2), (1, 2.5, 1), "space", "horn"),
            Box((-0.25, 35.5, -1.75), (0.5, 2, 0.5), "space", "horn")], rotation=(8, 0, 0)),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        hx = sign * 2
        bones += [
            Bone(f"horn_{side}", "head", (hx, 30, -1), [Box((hx - 0.5, 30, -1.5), (1, 3, 1), "space", "horn")],
                 rotation=(10, 0, sign * 25)),
            Bone(f"horn_{side}_2", f"horn_{side}", (hx, 33, -1), [Box((hx - 0.5, 33, -1.5), (1, 2.5, 1), "space", "horn")],
                 rotation=(20, 0, -sign * 15)),
            Bone(f"horn_{side}_3", f"horn_{side}_2", (hx, 35.5, -1), [Box((hx - 0.25, 35.5, -1.25), (0.5, 1, 0.5), "space", "horn")],
                 rotation=(25, 0, -sign * 20)),
        ]
        sx = sign * 5
        bones += [
            Bone(f"{side}_arm", None, (sx, 23, 0), [
                Box((sx - 1.25, 21, -1.5), (2.5, 2.5, 3), "space", "shoulder"),
                Box((sx - 1, 16.5, -1.25), (2, 5, 2.5), "space", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 16.5, 0), [
                Box((sx - 1, 11.5, -1.25), (2, 5, 2.5), "space", "arm"),
                Box((sx - 1.25, 9, -1.25), (2.5, 2.5, 2.5), "white", "hand"),
                Box((sx - 1.25, 7.5, -1.5), (2.5, 1.5, 1), "white", "hand")]),
            Bone(f"{side}_leg", None, (sign * 1.75, 12, 0), [
                Box((sign * 1.75 - 1.5, 6.5, -1.5), (3, 5.5, 3), "space", "thigh"),
                Box((sign * 1.75 - 1.25, 1, -1.25), (2.5, 5.5, 2.5), "space", "shin"),
                Box((sign * 1.75 - 1.25, 0, -3), (2.5, 1, 4), "space", "foot")]),
        ]
    return Design("alien_x", 1.05, "small", "#B8D8FF", m, bones, None, seed=14)


def brainstorm() -> Design:
    """Brainstorm (Turnaround AF-UA) als Krabbe auf vier Beinen: riesiger gewoelbter Panzer mit Mittelnaht und
    Dornen am Rand, der vorn wie eine Stirn ueber das Gesicht faellt (V-Knick); darunter Gesicht mit gruenen
    Mandelaugen und gefletschten Zaehnen. Die Schaedelplatten klappen auf und zeigen das rosa Gehirn. Halsmanschette
    mit Ringbaendern und Omnitrix, runder Hinterleib. Lange Arme mit Gelenkringen und gebogenen Zangen; vier kurze
    gebogene Krabbenbeine mit dunklen Spitzen (vorn und hinten gespreizt, Kreuzgang)."""
    m = {"shell": Material("#9A4E22", "#56260C", "#C77440", "plain"),
         "plate": Material("#8A4420", "#4A200A", "#B8683A", "plain"),
         "brain": Material("#F28AC4", "#B04A88", "#FFC2E4", "brain", outline=False),
         "silver": Material("#B8BEC6", "#6E747C", "#E6EAF0", "metal"),
         "band": Material("#1C1C20", "#08080A", "#3A3A42", "plain"),
         "leg": Material("#9A4E22", "#56260C", "#C77440", "plain"),
         "tip": Material("#3A2414", "#1A0E06", "#5A3A24", "plain"),
         "spike": Material("#D8CDB8", "#8A806E", "#FFFFFF", "plain")}

    def zone(t: Texel) -> str | None:
        if t.part == "collar" and (abs(t.y - 9.25) < 0.3 or abs(t.y - 11.25) < 0.3):
            return "band"
        if t.part in ("arm", "leg") and t.side != "up" and abs(t.fy - 0.82) < 0.08:
            return "band"
        if t.part == "leg_tip" and t.y < 1.5:
            return "tip"
        return None

    bones = [
        Bone("head", None, (0, 13, 0), [
            Box((-8, 15, -7.5), (16, 1.5, 15), "shell", "brim"),
            Box((-1.5, 14.25, -7.75), (3, 1, 1), "shell", "brim"),
            Box((-7.5, 16.5, -7), (15, 2.5, 14), "shell", "plain"),
            Box((-6.5, 19, -6), (13, 0.5, 12), "shell", "plain"),
            Box((-5.5, 19.5, -5), (11, 2.5, 10), "brain", "brain"),
            Box((-6, 10.5, -7), (12, 4.5, 6), "shell", "face", front="brainstorm_face")]
            + [Box((sx, 16.5, sz), (0.5, 1, 0.5), "spike", "spike") for sx, sz in
               ((-8, -4), (-8, 2), (7.5, -4), (7.5, 2), (-4, -7.5), (3.5, -7.5), (-3, 7), (2.5, 7))]),
        Bone("plate_right", "head", (-6.5, 20, 0), [
            Box((-6.5, 19.5, -6), (6.5, 2, 12), "plate", "plate"),
            Box((-5.5, 21.5, -5), (5.5, 1, 10), "plate", "plate"),
            Box((-4, 22.5, -3.5), (4, 0.5, 7), "plate", "plate")]),
        Bone("plate_left", "head", (6.5, 20, 0), [
            Box((0, 19.5, -6), (6.5, 2, 12), "plate", "plate"),
            Box((0, 21.5, -5), (5.5, 1, 10), "plate", "plate"),
            Box((0, 22.5, -3.5), (4, 0.5, 7), "plate", "plate")]),
        Bone("body", None, (0, 11, 0), [
            Box((-4.5, 8.5, -4.5), (9, 3, 9), "silver", "collar", front="chest_small"),
            Box((-3.5, 5, -3.5), (7, 3.5, 7), "shell", "thorax"),
            Box((-3, 4.5, 3), (6, 4, 4), "shell", "abdomen")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 8.5
        bones += [
            Bone(f"{side}_arm", None, (sx, 14, -1), [
                Box((sx - 1, 6, -2), (2, 8, 2), "leg", "arm"),
                Box((sx - 1.25, 5.5, -2.25), (2.5, 0.5, 2.5), "band", "ring")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 6, -1), [
                Box((sx - 1, 2, -2), (2, 4, 2), "leg", "arm"),
                Box((sx - 1.5, 0.5, -3), (3, 2, 3), "shell", "pincer"),
                Box((sx - 0.5, 1.5, -6), (1, 1, 3), "tip", "pincer"),
                Box((sx - 0.5, 0.5, -6.5), (1, 1, 1), "tip", "pincer"),
                Box((sx - 0.5, 0, -5), (1, 0.5, 2), "tip", "pincer")]),
        ]
    # vier Krabbenbeine: vorn = Laufbeine (Spielerbeine), hinten am Bein der Gegenseite (Kreuzgang)
    for sign in (-1, 1):
        side = side_of(sign)
        for name, parent, pivot_z, yaw in ((f"{side}_leg", None, -2.5, -sign * 45), (f"{side}_leg_rear", f"{side_of(-sign)}_leg", 3, sign * 45)):
            px = sign * 3
            bones.append(Bone(name, parent, (px, 6, pivot_z)))
            bones.append(Bone(f"{name}_yaw", name, (px, 6, pivot_z), rotation=(0, yaw, 0)))
            bones.append(Bone(f"{name}_upper", f"{name}_yaw", (px, 6, pivot_z), [
                Box((px if sign > 0 else px - 6, 5.5, pivot_z - 0.5), (6, 1, 1), "leg", "leg")],
                rotation=(0, 0, -sign * 30)))
            tip_x = px + sign * 6
            bones.append(Bone(f"{name}_lower", f"{name}_upper", (tip_x, 6, pivot_z), [
                Box((tip_x - 0.5, -3.5, pivot_z - 0.5), (1, 9.5, 1), "leg", "leg_tip"),
                Box((tip_x - 0.5 + sign * 0.5, -4, pivot_z - 0.5), (1, 1, 1), "tip", "leg_tip")],
                rotation=(0, 0, sign * 50)))
    loops = {
        # Schaedelplatten oeffnen sich kurz (Gehirn sichtbar), dann zu
        "plate_right": {"rotation": {"vector": [0, 0, "math.max(0,math.sin(query.anim_time*90))*-24"]}},
        "plate_left": {"rotation": {"vector": [0, 0, "math.max(0,math.sin(query.anim_time*90))*24"]}}}
    return Design("brainstorm", 1.0, "heavy", "#C77440", m, bones, zone, leg_swing=0.6, loops=loops, seed=15)


def goop() -> Design:
    """Goop (Posen AF-UA): duenner gallertartiger Humanoid, gruen mit Glanzstreifen; spitz nach oben auslaufender
    Kopf mit kleinen Augen, Schleimtropfen an Koerper und Haenden, Beine zerfliessen am Boden zu Lachen. Der
    Anti-Gravitations-Projektor schwebt als Untertasse mit Lichtring ueber dem Kopf. Omnitrix auf der Brust."""
    m = {"slime": Material("#6FD62A", "#2F7A10", "#D8FF8A", "slime"),
         "deep": Material("#4FB01C", "#245E0C", "#A6F060", "slime"),
         "metal": Material("#8A939E", "#4A525C", "#D6DCE4", "metal"),
         "dome": Material("#A8B4C0", "#5E6874", "#E8EEF4", "metal"),
         "light": Material("#7CFF4A", "#3DB018", "#D0FFB8", "plain", glow=True)}

    def zone(t: Texel) -> str | None:
        if t.part == "rim" and t.side in ("north", "south", "east", "west") and int(t.fx * 6) % 2 == 0:
            return "light"
        return None

    bones = [
        Bone("body", None, (0, 22, 0), [
            Box((-2.5, 17, -1.5), (5, 5, 3), "slime", "chest", front="chest"),
            Box((-3, 20, -1), (6, 1.5, 2), "slime", "chest"),
            Box((-1.5, 13, -1), (3, 4, 2), "slime", "waist"),
            Box((-2, 11, -1.25), (4, 2, 2.5), "slime", "waist"),
            Box((1, 15, -1.25), (0.5, 1.5, 0.5), "slime", "drip"),
            Box((-1.5, 12, 0.75), (0.5, 2, 0.5), "slime", "drip")]),
        Bone("head", None, (0, 22, 0), [
            Box((-0.75, 22, -0.75), (1.5, 1, 1.5), "slime", "neck"),
            Box((-1.75, 23, -1.75), (3.5, 3.5, 3.5), "slime", "head", front="goop_face"),
            Box((-1.25, 26.5, -1.5), (2.5, 2, 2.5), "slime", "head"),
            Box((-1, 28.5, -1.25), (2, 1.5, 2), "slime", "head"),
            Box((-0.5, 30, -1), (1, 1.5, 1.5), "slime", "head"),
            Box((-0.25, 31.5, -1), (0.5, 1, 0.5), "slime", "head"),
            Box((-0.5, 24, 1.75), (1, 3, 0.5), "slime", "head")]),
        Bone("projector", "head", (0, 34.5, 0), [
            Box((-2.5, 34, -2.5), (5, 1, 5), "metal", "projector"),
            Box((-3, 34.25, -3), (6, 0.5, 6), "metal", "rim"),
            Box((-1.5, 33.5, -1.5), (3, 0.5, 3), "light", "projector"),
            Box((-1, 35, -1), (2, 1, 2), "dome", "projector")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 3.5
        x = sign * 1.2
        bones += [
            Bone(f"{side}_arm", None, (sx, 21, 0), [
                Box((sx - 1, 14, -1), (2, 7, 2), "slime", "arm"),
                Box((sx - 1.25, 13, -1.25), (2.5, 1.5, 2.5), "slime", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 13, 0), [
                Box((sx - 0.75, 7, -0.75), (1.5, 6, 1.5), "slime", "arm"),
                Box((sx - 1.25, 5, -1.25), (2.5, 2, 2.5), "slime", "hand"),
                Box((sx - 0.5, 3.5, -0.5), (1, 1.5, 1), "slime", "drip"),
                Box((sx - 1.25, 4, -1.25), (0.5, 1, 0.5), "slime", "hand")]),
            Bone(f"{side}_leg", None, (x, 11, 0), [
                Box((x - 1, 6, -1), (2, 5, 2), "slime", "leg"),
                Box((x - 1.25, 1.5, -1.25), (2.5, 4.5, 2.5), "slime", "leg"),
                Box((x - 1.75, 0.5, -1.75), (3.5, 1, 3.5), "slime", "leg"),
                Box((x - 2.5, 0, -3), (5, 0.5, 5.5), "deep", "puddle")]),
        ]
    loops = {"projector": {"position": {"vector": [0, "math.sin(query.anim_time*120)*0.6", 0]},
                           "rotation": {"vector": [0, "query.anim_time*90", 0]}}}
    return Design("goop", 1.0, "small", "#6FD62A", m, bones, zone, loops=loops, seed=16)


DESIGNS = {d.name: d for d in (rath(), spidermonkey(), way_big(), alien_x(), brainstorm(), goop())}


def write(files: dict[Path, object]) -> int:
    for path, content in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, Image.Image):
            content.save(path)
        else:
            path.write_text(json.dumps(content, indent=2) + "\n")
    return len(files)


def same(path: Path, content: object) -> bool:
    if not path.is_file():
        return False
    if isinstance(content, Image.Image):
        have = Image.open(path).convert("RGBA")
        return have.size == content.size and have.tobytes() == content.tobytes()
    current = json.loads(path.read_text(encoding="utf-8"))
    if path.parent.name == "alien_render":
        current = dict(current, ability_poses=[])  # Posen ergaenzt generate_ability_poses.py
    return current == json.loads(json.dumps(content))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Dateien aktuell sind")
    parser.add_argument("--only", help="nur dieses Alien")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    stale = 0
    written = 0
    for name, design in DESIGNS.items():
        if args.only and name != args.only:
            continue
        files = build(design)
        if args.check:
            for path, content in files.items():
                if not same(path, content):
                    LOG.error("veraltet: %s", path)
                    stale += 1
            continue
        render = ASSETS / "alien_render" / f"{name}.json"
        if render.is_file():
            # vorhandene Faehigkeits-Posen behalten (generate_ability_poses.py)
            files[render]["ability_poses"] = json.loads(render.read_text(encoding="utf-8")).get("ability_poses", [])
        written += write(files)
        LOG.info("%s gebaut", name)
    if args.check:
        LOG.info("eigene Aliens %s", "aktuell" if stale == 0 else f"{stale} Dateien veraltet")
        return 1 if stale else 0
    LOG.info("%d Dateien geschrieben", written)
    return 0


if __name__ == "__main__":
    sys.exit(main())
