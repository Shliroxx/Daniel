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
PX = 2        # Pixel je Einheit (AE-Dichte)
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
    paint: str = "ae"               # "ae" = Alien-Evolution-Malstil, "flat" = flache Farben (Minecraft-DLC-Vorlage)


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
        """AE-Malstil (wie die Alien-Evolution-Texturen): Licht je Flaechenrichtung, heller Kern und dunklere Raender
        (Muskel-/Rundungs-Schattierung), kraeftiges Pixelrauschen mit einzelnen dunklen und hellen Tupfern,
        1 Pixel dunklerer Rand. Leuchtende Flaechen bleiben glatt."""
        base, dark, hi = rgb(mat.base), rgb(mat.dark), rgb(mat.light)
        rng = self.rng
        if mat.glow:
            return shade(base, light * (0.96 + 0.06 * rng.random()))
        if self.design.paint == "flat":
            k = light * (1.04 - 0.1 * ((py + 0.5) / h if side not in ("up", "down") else 0.3)) * (0.98 + 0.04 * rng.random())
            if px == 0 or py == 0 or px == w - 1 or py == h - 1:
                k *= 0.9
            return shade(base, k)
        cx, cy = (px + 0.5) / w, (py + 0.5) / h
        t = cy if side not in ("up", "down") else 0.3
        dist = max(abs(cx - 0.5), abs(cy - 0.5)) * 2.0
        k = light * (1.06 - 0.12 * t) * (1.06 - 0.16 * dist * dist) * (0.9 + 0.17 * rng.random())
        if px == 0 or py == 0 or px == w - 1 or py == h - 1:
            if mat.rim and side not in ("up", "down"):
                return shade(rgb(mat.rim), 0.9 + 0.1 * rng.random())
            k *= 0.86
        c = shade(base, k)
        pat = mat.pattern
        roll = rng.random()
        if pat == "stars":
            c = shade(base, 0.9 + 0.2 * rng.random())
            if roll < 0.045:
                return STAR
            if roll < 0.07:
                return shade(hi, 1.0)
            return c
        if pat == "brain":
            fold = math.sin(px * 1.1 + math.sin(py * 0.9) * 2.0) + math.sin(py * 1.7 - px * 0.3)
            return shade(dark if fold > 1.05 else (hi if fold < -1.3 else base), light * (0.95 + 0.1 * rng.random()))
        if pat == "slime":
            if side in ("north", "west", "east") and 0.12 < cx < 0.3 and 0.1 < cy < 0.8:
                c = shade(mix(base, hi, 0.5), k)
            if roll < 0.03:
                return shade(hi, 1.05)
            return c
        if pat == "metal":
            c = shade(mix(base, hi, 0.4 if py % 3 == 1 else 0.0), k)
        # Tupfer wie bei AE
        if roll < 0.08:
            c = shade(mix(base, dark, 0.45), k)
        elif roll < 0.13:
            c = shade(mix(base, hi, 0.4), k)
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
    art(p, rect, [".kkkkk....kkkkk.",
                  "kkgggk....kgggkk",
                  ".kgggk....kgggk.",
                  "..kkk......kkk.."],
        {"k": ("#141414", False), "g": ("#5EE08A", True)}, 0.5, 0.36)


def f_rath_mouth(p, rect):
    art(p, rect, [".oooooooo.",
                  "..oookoo..",
                  "kkkkkkkkkk",
                  "kttttttttk",
                  "kkkkkkkkkk",
                  ".........."],
        {"k": ("#141414", False), "o": ("#EC6C1E", False), "t": ("#CDB48C", False)}, 0.5, 0.5)



def f_spider_face(p, rect):
    art(p, rect, ["............",
                  "..kkk..kkk..",
                  ".kwwk..kwwk.",
                  ".kwkk..kkwk.",
                  "..kkk..kkk..",
                  "....kk.kk...",
                  "....kk.kk...",
                  "............"],
        {"k": ("#06080F", False), "w": ("#E8EEFF", True)}, 0.5, 0.5)



def f_spider_mouth(p, rect):
    art(p, rect, ["kkkkkk", "wwwwww", "kkkkkk"], {"k": ("#06080F", False), "w": ("#E8ECF2", False)}, 0.5, 0.5)


def f_waybig_eyes(p, rect):
    art(p, rect, ["kk....kk",
                  "kgk..kgk",
                  ".kgkkgk.",
                  "..k..k.."],
        {"k": ("#111111", False), "g": ("#7CF23A", True)}, 0.5, 0.6)


def f_alienx_face(p, rect):
    art(p, rect, ["..gggg....gggg..",
                  "..gggg....gggg.."],
        {"g": ("#7CFF3A", True)}, 0.5, 0.45)



def f_brainstorm_face(p, rect):
    art(p, rect, ["...........kk...........",
                  "...........kk...........",
                  "...........kk...........",
                  "kkkk................kkkk",
                  "kkkkkkk..........kkkkkkk",
                  "..kkkkkkk......kkkkkkk..",
                  ".....kkkkkkkkkkkkkk.....",
                  "........................",
                  ".gggggg..........gggggg.",
                  "..gggggg........gggggg..",
                  "....gggg........gggg....",
                  "........................",
                  "........................",
                  "........wwwwwwww........",
                  ".......wwwwwwwwww.......",
                  ".......wwwwwwwwww......."],
        {"k": ("#121212", False), "g": ("#7CF23A", True), "w": ("#E8E8E8", False)}, 0.5, 0.52)



def f_goop_face(p, rect):
    art(p, rect, ["k.....k",
                  "kk...kk",
                  "kgk.kgk",
                  ".kk.kk."],
        {"k": ("#0D2A08", False), "g": ("#E8FF7A", True)}, 0.5, 0.42)


def f_chest(p, rect):
    badge(p, rect, 0.5, 0.42)


def f_chest_band(p, rect):
    badge(p, rect, 0.5, 0.7)


def f_chest_small(p, rect):
    art(p, rect, [".rrrr.", "rggggr", "rkggkr", "rkggkr", "rggggr", ".rrrr."], BADGE_COLORS, 0.5, 0.5, badge="g")


FEATURES: dict[str, Callable] = {
    "rath_face": f_rath_face, "rath_mouth": f_rath_mouth, "spider_face": f_spider_face,
    "spider_mouth": f_spider_mouth, "waybig_eyes": f_waybig_eyes, "alienx_face": f_alienx_face,
    "brainstorm_face": f_brainstorm_face, "goop_face": f_goop_face, "chest": f_chest, "chest_band": f_chest_band, "chest_small": f_chest_small,
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
    """Rath 1:1 nach dem Minecraft-Ben-10-Modell (DLC-Bild): flache Farben. Kopf als Block direkt auf dem Brustband:
    oben orange mit schwarzen Zacken-Streifen, graue stachelige Fellbueschel an den oberen Ecken, gruene
    Rechteck-Augen mit schwarzem Rand, vorstehende Schnauze (weiss, oranges V mit schwarzer Nase), schwarz gerahmtes
    Maul mit zusammengebissenen beigen Zaehnen, weisses Kinn, schwarze Zacken an den Wangen. Hellgraues Brustband
    mit Omnitrix, schmaler oranger Streifen, weisser Bauchfleck bis in den Schritt. Riesige Schultern und
    Unterarme mit schwarzen Keilstreifen, gebrochen-weisse Haende mit drei dicken Fingern und Daumen. Kraeftige
    Beine, weisse Fuesse mit drei Zehen."""
    m = {"orange": Material("#EC6C1E", "#8E3810", "#FF9A4E", "plain"),
         "band": Material("#C9CCC4", "#8A8D86", "#E6E8E2", "plain"),
         "white": Material("#E2E3DA", "#9C9D94", "#FFFFFF", "plain"),
         "hand": Material("#D6D8CB", "#94968A", "#F0F1EA", "plain"),
         "foot": Material("#F2EEDE", "#A8A496", "#FFFFFF", "plain"),
         "grey": Material("#A9ABA6", "#6A6C68", "#D2D4CE", "plain"),
         "stripe": Material("#1E201C", "#0A0B09", "#3A3C36", "plain")}

    def zone(t: Texel) -> str | None:
        part, y, ax = t.part, t.y, abs(t.x)
        if part == "head":
            if t.side == "north":
                # Zacken-Streifen auf der Stirn (M-Form) und an den Wangenkanten
                if t.fy < 0.32 and (abs(ax - (0.6 + (0.32 - t.fy) * 5)) < 0.35 or abs(ax - (2.4 + (0.32 - t.fy) * 3)) < 0.35):
                    return "stripe"
                if t.fy > 0.5 and ax > 3.3 and int(t.fy * 12) % 3 == 0:
                    return "stripe"
                if t.fy > 0.52:
                    return "white"
            if t.side in ("east", "west"):
                if tiger(t, 2.0, 0.55, 0.3):
                    return "stripe"
                if t.fy > 0.7 and t.z < -3:
                    return "white"
            if t.side == "up" and (abs(ax - 1.0) < 0.35 or abs(ax - 2.6) < 0.35) and t.fy > 0.35:
                return "stripe"
        if part == "chest" and t.side == "south":
            return "orange"
        if part == "belly" and t.side == "north" and ax < 2.3:
            return "white"
        if part == "hips" and t.side in ("north", "down") and ax < 1.8:
            return "white"
        if part == "belly" and t.side in ("east", "west") and tiger(t, 2.6, 0.8, 0.5):
            return "stripe"
        outer = ((t.side == "east" and t.x < 0) or (t.side == "west" and t.x > 0) or t.side == "south"
                 or (t.side == "north" and ax > 10.5) or t.side == "up")
        if part in ("shoulder", "upper_arm", "forearm") and outer and tiger(t, 4.6, 0.9, 0.8):
            return "stripe"
        if part in ("thigh", "shin") and t.side in ("east", "west", "south") and tiger(t, 3.2, 0.7, 0.4):
            return "stripe"
        return None

    bones = [
        Bone("body", None, (0, 24, 0), [
            Box((-5.5, 19, -3), (11, 5, 6), "band", "chest", front="chest_band"),
            Box((-5, 17.5, -2.75), (10, 1.5, 5.5), "orange", "belly"),
            Box((-4.5, 13, -2.5), (9, 4.5, 5), "orange", "belly"),
            Box((-4, 11, -2.25), (8, 2, 4.5), "orange", "hips")]),
        Bone("head", None, (0, 24, -1), [
            Box((-4.5, 24, -6.5), (9, 6.5, 7.5), "orange", "head", front="rath_face"),
            Box((-3, 24, -8), (6, 3.5, 1.5), "white", "muzzle", front="rath_mouth"),
            Box((-3, 23, -7.5), (6, 1, 4.5), "white", "chin")]
            # graue Fellstacheln an den oberen Ecken, schraeg nach aussen oben
            + [Box((sx, 29.5, -6), (2, 1, 1.5), "grey", "tuft") for sx in (-6, 4)]
            + [Box((sx, 30, -6), (1.5, 1, 1), "grey", "tuft") for sx in (-7.5, 6)]
            + [Box((sx, 31, -6), (1, 1, 1), "grey", "tuft") for sx in (-8, 7)]
            + [Box((sx, 29, -7), (2, 0.5, 0.5), "grey", "tuft") for sx in (-4.5, 2.5)]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 9
        inner = sx - sign * 3.5 - (1.5 if sign > 0 else 0)
        bones += [
            Bone(f"{side}_arm", None, (sx, 22, 0), [
                Box((sx - 3.5, 18, -3.5), (7, 6.5, 7), "orange", "shoulder"),
                Box((sx - 3, 13, -3), (6, 5, 6), "orange", "upper_arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 13, 0), [
                Box((sx - 3.5, 7, -3.5), (7, 6, 7), "orange", "forearm"),
                Box((sx - 3.5, 2.5, -3.5), (7, 4.5, 7), "hand", "hand")]
                + [Box((sx - 3.5 + i * 2.5, 0, -3.5), (2, 2.5, 3), "hand", "finger") for i in range(3)]
                + [Box((inner, 2, -3), (1.5, 3, 2.5), "hand", "finger")]),
            Bone(f"{side}_leg", None, (sign * 2.5, 11, 0), [
                Box((sign * 2.5 - 2.25, 5.5, -2.25), (4.5, 5.5, 4.5), "orange", "thigh"),
                Box((sign * 2.5 - 2, 2, -2), (4, 3.5, 4), "orange", "shin"),
                Box((sign * 2.5 - 2.5, 0, -4), (5, 2, 6), "foot", "foot")]
                + [Box((sign * 2.5 - 2.25 + i * 1.75, 0, -5), (1, 1.5, 1), "foot", "toe") for i in range(3)]),
        ]
    return Design("rath", 1.25, "heavy", "#EC6C1E", m, bones, zone, arm_swing=0.8, leg_swing=0.8, seed=11, paint="flat")


def spidermonkey() -> Design:
    """Spidermonkey (nach Minecraft-Vorlage): kraeftiger Vierbeiner, blau mit dunkler Mitte und hellen Akzenten
    (Schultern, Knie, Schwanzspitze). Waagerechter Rumpf mit erhobener Brust, helmartiger Kopf mit dunklem
    Gesicht, zwei grossen und zwei kleinen Augen, heller Schnauze; vier Arme (vorderes Paar = Vorderbeine,
    hinteres Paar gegenphasig), langer aufgerichteter Schwanz mit hellen Ringen."""
    m = {"blue": Material("#2C3FB0", "#141A52", "#5468D6", "plain"),
         "navy": Material("#171C55", "#090C26", "#2A3280", "plain"),
         "pale": Material("#C9D6F2", "#7C8AB0", "#FFFFFF", "plain")}

    def zone(t: Texel) -> str | None:
        part = t.part
        if part == "head" and t.side == "north" and 0.12 < t.fx < 0.88 and t.fy > 0.18:
            return "navy"
        if part == "chest" and t.side == "north" and abs(t.fx - 0.5) < 0.34:
            return "navy"
        if part == "torso" and t.side == "down":
            return "navy"
        if part in ("arm", "thigh") and t.side in ("east", "west", "north") and t.fy < 0.14:
            return "pale"
        if part == "torso" and t.side in ("east", "west") and abs(t.y - 13.5) < 0.35:
            return "pale"
        if part == "tail_tip" and (t.z % 2.0) < 0.7:
            return "pale"
        return None

    bones = [
        Bone("body", None, (0, 12, 0), [
            Box((-3.5, 9, -4), (7, 6, 10), "blue", "torso"),
            Box((-3.5, 10, -6.5), (7, 7, 3), "blue", "chest", front="chest")]),
        Bone("head", None, (0, 17, -6), [
            Box((-3, 15, -11.5), (6, 6, 6), "blue", "head", front="spider_face"),
            Box((-1.5, 15, -12.5), (3, 2, 1), "pale", "muzzle", front="spider_mouth"),
            Box((-2.5, 19, -6), (5, 2.5, 3), "blue", "mane"),
            Box((-2, 17.5, -3.5), (4, 2, 1.5), "blue", "mane")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 3.2
        bones += [
            Bone(f"{side}_arm", None, (sx, 15, -4.5), [Box((sx - 1.5, 6, -6), (3, 9, 3), "blue", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 6, -4.5), [
                Box((sx - 1.5, 1, -6), (3, 5, 3), "blue", "arm"),
                Box((sx - 2, 0, -7), (4, 1, 4), "navy", "hand")]),
            Bone(f"{side}_leg", None, (sign * 2.5, 10, 4), [
                Box((sign * 2.5 - 1.5, 5, 2.5), (3, 5, 4), "blue", "thigh"),
                Box((sign * 2.5 - 1.5, 1, 4), (3, 4, 3), "blue", "shin"),
                Box((sign * 2.5 - 2, 0, 2), (4, 1, 5), "navy", "foot")]),
        ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 4
        # hinteres Armpaar: am Arm der Gegenseite → laeuft gegenphasig (Kreuzgang)
        bones.append(Bone(f"{side}_rear_arm", f"{side_of(-sign)}_arm", (sx, 13, -1), [
            Box((sx - 1.25, 1, -2), (2.5, 12, 2.5), "blue", "arm"),
            Box((sx - 1.75, 0, -3), (3.5, 1, 3.5), "navy", "hand")]))
    tail = [("tail_1", "body", 60, (2.5, 2.5, 4)), ("tail_2", "tail_1", 20, (2, 2, 4)), ("tail_3", "tail_2", 10, (2, 2, 4)),
            ("tail_4", "tail_3", 25, (2, 2, 3.5)), ("tail_5", "tail_4", 35, (1.5, 1.5, 3))]
    z = 6
    for name, parent, angle, size in tail:
        w, h, d = size
        bones.append(Bone(name, parent, (0, 13.5, z), [Box((-w / 2, 13.5 - h / 2, z), size, "blue",
                                                           "tail_tip" if name in ("tail_4", "tail_5") else "tail")],
                          rotation=(angle, 0, 0)))
        z += d
    loops = {"tail_1": sway("y", 8, 110), "tail_2": sway("x", 6, 110, 30), "tail_3": sway("x", 8, 110, 60),
             "tail_4": sway("x", 10, 110, 90)}
    return Design("spidermonkey", 0.9, "fast", "#2C3FB0", m, bones, zone, loops=loops, seed=12)


def way_big() -> Design:
    """Way Big (nach Minecraft-Vorlage, Omniverse-Look): weisser Riese, grosser roter Brust-Diamant mit Spitze
    nach unten, rote gestufte Schulterklingen, rote gezackte Unterarmflossen, je zwei schwarze Armbaender, rote
    Stiefel mit weisser Spitze; Kopf mit roter Kappe und Kamm, gruene Augen, gelbe Wangenaugen, schwarzes
    Untergesicht und schwarzer Hals."""
    m = {"white": Material("#EEEEF0", "#A6A8AE", "#FFFFFF", "plain"),
         "line": Material("#DCDEE4", "#B0B2BA", "#EEEEF2", "plain", outline=False),
         "red": Material("#C21A2A", "#6E0C16", "#EE4656", "plain"),
         "black": Material("#1A1A1E", "#060608", "#3A3A42", "plain"),
         "yellow": Material("#F2E23A", "#A89A14", "#FFF8A0", "plain", glow=True)}

    def zone(t: Texel) -> str | None:
        part, y, ax = t.part, t.y, abs(t.x)
        if part == "crest" and (t.z > 0.3 or t.side in ("down", "south")):
            return "black"
        if part == "head" and (t.side == "up" or (t.side != "down" and t.fy < 0.2)):
            return "red"
        if part == "chest" and t.side == "north" and ax / 3.9 + abs(y - 22.5) / 3.6 < 1.0:
            return "red"
        if part == "abs" and t.side == "north" and ax < (y - 14.5) * 0.45:
            return "red"
        if part == "arm" and abs(y - 19.5) < 0.5:
            return "black"
        if part == "forearm" and (abs(y - 14.5) < 0.5 or abs(y - 12.5) < 0.5):
            return "black"
        if part == "shin":
            if t.side == "north" and abs(t.fx - 0.5) < 0.35 - (5.5 - y) * 0.12 and y < 5.5:
                return "white"
            if y < 7.5 + (1.5 * (1 - abs(t.fx - 0.5) * 2) if t.side == "north" else 0.0):
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
            Box((-2.25, 27.5, -2.25), (4.5, 1, 3.5), "black", "jaw"),
            Box((-2.5, 28.5, -2), (0.5, 0.5, 0.5), "yellow", "cheek"),
            Box((2, 28.5, -2), (0.5, 0.5, 0.5), "yellow", "cheek")]),
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
        blade_x = sx - 0.5
        bones += [
            Bone(f"{side}_arm", None, (sx, 25, 0), [
                Box((sx - 1.5, 22.5, -1.5), (3, 3, 3), "white", "shoulder"),
                Box((sx - 1.25, 17.5, -1.25), (2.5, 5, 2.5), "white", "arm")]),
            # rote Schulterklingen, gestuft nach aussen oben
            Bone(f"{side}_blade", f"{side}_arm", (sx, 25.5, 0), [
                Box((blade_x, 25.5, -1), (1, 2, 2), "red", "blade"),
                Box((blade_x + sign * 0.5, 27.5, -0.5), (1, 2, 1.5), "red", "blade"),
                Box((blade_x + sign * 1, 29.5, 0), (1, 1.5, 1), "red", "blade")], rotation=(0, 0, sign * 15)),
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
    """Alien X (nach Minecraft-Vorlage): Spielerform — Kastenkopf, Koerper und Glieder in Steve-Proportionen,
    ganz schwarz mit Sternen, grosse weisse (leuchtende) Haende, zwei gruene Rechteck-Augen, kleines Omnitrix
    auf der Brust."""
    m = {"space": Material("#05060C", "#000000", "#B8D8FF", "stars"),
         "white": Material("#F6F8FF", "#B8C0D8", "#FFFFFF", "plain", glow=True)}

    bones = [
        Bone("body", None, (0, 24, 0), [Box((-4, 12, -2), (8, 12, 4), "space", "chest", front="chest_small")]),
        Bone("head", None, (0, 24, 0), [Box((-4, 24, -4), (8, 8, 8), "space", "head", front="alienx_face")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 6
        bones += [
            Bone(f"{side}_arm", None, (sx, 22, 0), [Box((sx - 2, 16, -2), (4, 8, 4), "space", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 16, 0), [Box((sx - 2, 12, -2), (4, 4, 4), "white", "hand")]),
            Bone(f"{side}_leg", None, (sign * 2, 12, 0), [Box((sign * 2 - 2, 0, -2), (4, 12, 4), "space", "leg")]),
        ]
    return Design("alien_x", 1.05, "small", "#B8D8FF", m, bones, None, seed=14)


def brainstorm() -> Design:
    """Brainstorm (nach Minecraft-Vorlage): grosser brauner Kastenkopf mit Mittelnaht, welliger schwarzer
    Stirnlinie, kantigen gruenen Augen und weissem Zahnblock; die Deckplatten klappen auf und zeigen das Gehirn.
    Schwarz-silberne Halsmanschette mit Omnitrix, kleiner Leib, vier duenne gerade Beine (Kreuzgang), duenne Arme
    mit schwarzem Handgelenkband und C-foermiger Zange."""
    m = {"shell": Material("#8A4A1E", "#4A2208", "#B06A36", "plain"),
         "plate": Material("#7E4219", "#401C06", "#A45E2E", "plain"),
         "brain": Material("#F28AC4", "#B04A88", "#FFC2E4", "brain", outline=False),
         "silver": Material("#B8BEC6", "#6E747C", "#E6EAF0", "metal"),
         "band": Material("#16161A", "#060608", "#34343C", "plain"),
         "leg": Material("#8A4A1E", "#4A2208", "#B06A36", "plain")}

    def zone(t: Texel) -> str | None:
        if t.part == "collar" and t.side != "up" and t.side != "down" and (t.fy < 0.22 or t.fy > 0.78):
            return "silver"
        if t.part == "arm" and abs(t.y - 3.5) < 0.5:
            return "band"
        return None

    bones = [
        Bone("head", None, (0, 12, 0), [
            Box((-6, 12, -5.5), (12, 10, 11), "shell", "head", front="brainstorm_face"),
            Box((-5.5, 21.5, -5), (11, 1, 10), "brain", "brain")]),
        Bone("plate_right", "head", (-6, 22, 0), [Box((-6, 22, -5.5), (6, 1.5, 11), "plate", "plate")]),
        Bone("plate_left", "head", (6, 22, 0), [Box((0, 22, -5.5), (6, 1.5, 11), "plate", "plate")]),
        Bone("body", None, (0, 9.5, 0), [
            Box((-4.5, 9.5, -4.5), (9, 2.5, 9), "band", "collar", front="chest_small"),
            Box((-4, 5.5, -3.5), (8, 4, 7), "shell", "body")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 6.75
        base = sx - 0.75
        bones += [
            Bone(f"{side}_arm", None, (sx, 13, -1), [Box((base, 5, -1.75), (1.5, 8, 1.5), "leg", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 5, -1), [
                Box((base, 1, -1.75), (1.5, 4, 1.5), "leg", "arm"),
                # C-Zange: senkrechter Ruecken, oben und unten je ein Finger nach vorn
                Box((base, -1, -2.25), (1.5, 2, 1.5), "leg", "claw"),
                Box((base, 0.5, -4.25), (1.5, 1, 2), "leg", "claw"),
                Box((base, -1.5, -4.25), (1.5, 1, 2), "leg", "claw")]),
        ]
    # vier gerade Beine: vorn = Spielerbeine, hinten am Bein der Gegenseite (Kreuzgang)
    for sign in (-1, 1):
        side = side_of(sign)
        x = sign * 3
        bones.append(Bone(f"{side}_leg", None, (x, 6, -2.5), [Box((x - 0.75, 0, -3.25), (1.5, 6, 1.5), "leg", "leg")]))
    for sign in (-1, 1):
        side = side_of(sign)
        x = sign * 3
        bones.append(Bone(f"{side}_leg_rear", f"{side_of(-sign)}_leg", (x, 6, 2.5),
                          [Box((x - 0.75, 0, 1.75), (1.5, 6, 1.5), "leg", "leg")]))
    loops = {
        "plate_right": {"rotation": {"vector": [0, 0, "math.max(0,math.sin(query.anim_time*90))*-24"]}},
        "plate_left": {"rotation": {"vector": [0, 0, "math.max(0,math.sin(query.anim_time*90))*24"]}}}
    return Design("brainstorm", 1.0, "heavy", "#B06A36", m, bones, zone, leg_swing=0.6, loops=loops, seed=15)


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
            Box((-2.5, 17, -1.5), (5, 5, 3), "slime", "chest", front="chest_small"),
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
