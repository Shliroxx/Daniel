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
PX = 2        # Pixel je Einheit

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
    size: tuple[int, int, int]
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
        base, dark, hi = rgb(mat.base), rgb(mat.dark), rgb(mat.light)
        rng = self.rng
        # Licht von oben auf den Seiten, Rand einen Hauch dunkler (wie die AE-Texturen), feines Rauschen
        t = py / max(1, h - 1) if side not in ("up", "down") else 0.3
        k = light * (1.05 - 0.14 * t) * (0.97 + 0.06 * rng.random())
        if px == 0 or py == 0 or px == w - 1 or py == h - 1:
            k *= 0.93
        c = shade(base, k)
        pat = mat.pattern
        if pat == "fur":
            roll = rng.random()
            if roll < 0.09:
                c = shade(mix(base, dark, 0.3), k)
            elif roll < 0.14:
                c = shade(mix(base, hi, 0.35), k)
        elif pat == "stars":
            roll = rng.random()
            c = shade(base, 0.9 + 0.15 * rng.random())
            if roll < 0.035:
                return STAR
            if roll < 0.06:
                return shade(hi, 1.0)
        elif pat == "slime":
            # glaenzende Laengsstreifen und Blasen
            gloss = math.sin((px + box.origin[0] * PX) * 0.55 + py * 0.12)
            c = shade(mix(base, hi, max(0.0, gloss - 0.55) * 1.6), k)
            roll = rng.random()
            if roll < 0.012:
                c = shade(hi, 1.08)
            elif roll < 0.03:
                c = shade(mix(base, dark, 0.35), k)
        elif pat == "metal":
            c = shade(mix(base, hi, 0.45 if (py % 4 == 1) else 0.0), k)
        elif pat == "brain":
            # Hirnwindungen: geschwungene dunkle Furchen
            fold = math.sin(px * 1.1 + math.sin(py * 0.9) * 2.0) + math.sin(py * 1.7 - px * 0.3)
            c = shade(dark if fold > 1.05 else (hi if fold < -1.3 else base), light * 1.02)
        elif pat == "shell":
            roll = rng.random()
            if roll < 0.06:
                c = shade(mix(base, dark, 0.3), k)
            elif roll < 0.09:
                c = shade(mix(base, hi, 0.35), k)
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
        cx: float = 0.5, cy: float = 0.5) -> None:
    """Pixel-Bild auf eine Flaeche setzen, Mittelpunkt an (cx, cy) der Flaeche."""
    u, v, w, h = rect
    x0 = u + round(w * cx - len(rows[0]) / 2)
    y0 = v + round(h * cy - len(rows) / 2)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in palette:
                color, glow = palette[ch]
                p.put(x0 + i, y0 + j, rgb(color), glow)


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
    art(p, rect, BADGE, BADGE_COLORS, cx, cy)


# --- Gesichter und Abzeichen (Pixel-Bilder, 2 Pixel je Einheit) --------------------------------------

def f_rath_face(p, rect):
    art(p, rect, [".kk........kk.",
                  "..kkk....kkk..",
                  "...kkkkkkkk...",
                  "..yygk..kgyy..",
                  "..yygk..kgyy..",
                  "...kk....kk...",
                  "..............",
                  "..............",
                  "...kkkkkkkk...",
                  "...kwkwwkwk...",
                  "....kkkkkk...."],
        {"k": ("#141414", False), "y": ("#C8F24A", True), "g": ("#4FD12A", True), "w": ("#F4F1E6", False)}, 0.5, 0.52)


def f_rath_muzzle(p, rect):
    art(p, rect, ["..kkkkkk..", "...kkkk..."], {"k": ("#2A1A14", False)}, 0.5, 0.25)


def f_spider_face(p, rect):
    art(p, rect, ["..kkkk..kkkk..",
                  "..kggk..kggk..",
                  "..kkkk..kkkk..",
                  "...kkk..kkk...",
                  "...kgk..kgk...",
                  "...kkk..kkk..."],
        {"k": ("#06080F", False), "g": ("#62F03A", True)}, 0.5, 0.36)


def f_spider_muzzle(p, rect):
    art(p, rect, [".kkkkkk.", "........"], {"k": ("#06080F", False)}, 0.5, 0.7)


def f_waybig_face(p, rect):
    art(p, rect, [".kkk..kkk.",
                  "kggk..kggk",
                  ".kggkkggk.",
                  "..kk..kk.."],
        {"k": ("#111111", False), "g": ("#7CF23A", True)}, 0.5, 0.36)


def f_alienx_face(p, rect):
    art(p, rect, ["............",
                  ".gg......gg.",
                  ".ggg....ggg.",
                  "..ggg..ggg..",
                  "...gg..gg..."],
        {"g": ("#8CFF52", True)}, 0.5, 0.42)


def f_brainstorm_face(p, rect):
    u, v, w, h = rect
    rows = ["....kkkkkkk......kkkkkkk....",
            "...kkkkkkkkkk..kkkkkkkkkk...",
            "........kkkkkkkkkkkk........",
            "....kggggggk....kggggggk....",
            ".....kgggppk....kppgggk.....",
            "......kkkkk......kkkkk......",
            "............................",
            "......kkkkkkkkkkkkkkkk......",
            ".....kwkwkwkwkwkwkwkwkk.....",
            "....kk..............kk......",
            "............................"]
    rows = [r.ljust(28, ".")[:28] for r in rows]
    art(p, rect, rows, {"k": ("#20140A", False), "g": ("#8CF23A", True), "p": ("#0E0E0E", False),
                        "w": ("#F2EEE2", False)}, 0.5, 0.58)


def f_brain_top(p, rect):
    """Mittelnaht der Schaedelplatten (die Platten klappen im Leerlauf auf)."""
    u, v, w, h = rect
    for py in range(h):
        p.put(u + w // 2, v + py, rgb("#3A2210"))


def f_goop_face(p, rect):
    art(p, rect, ["kk....kk",
                  "kgk..kgk",
                  "kggkkggk",
                  ".kk..kk."],
        {"k": ("#0D2A08", False), "g": ("#E8FF7A", True)}, 0.5, 0.45)


def f_chest(p, rect):
    badge(p, rect, 0.5, 0.38)


def f_chest_low(p, rect):
    badge(p, rect, 0.5, 0.5)


def f_projector_lights(p, rect):
    u, v, w, h = rect
    for px in range(0, w, 3):
        for py in range(h):
            p.put(u + px, v + py, rgb("#7CFF4A"), True)


FEATURES: dict[str, Callable] = {
    "rath_face": f_rath_face, "rath_muzzle": f_rath_muzzle, "spider_face": f_spider_face,
    "spider_muzzle": f_spider_muzzle, "waybig_face": f_waybig_face, "alienx_face": f_alienx_face,
    "brainstorm_face": f_brainstorm_face, "brain_top": f_brain_top, "goop_face": f_goop_face,
    "chest": f_chest, "chest_low": f_chest_low, "projector_lights": f_projector_lights,
}


# --- Bauen -----------------------------------------------------------------------------------------


def pack(boxes: list[Box]) -> tuple[dict[int, tuple[int, int]], int]:
    """Box-UV-Felder in Regalen packen (Einheiten); Rueckgabe: Feld je Box (id) und noetige Atlas-Hoehe."""
    order = sorted(boxes, key=lambda b: -(b.size[2] + b.size[1]))
    pos: dict[int, tuple[int, int]] = {}
    x = y = shelf = 0
    for box in order:
        w, h, d = box.size
        bw, bh = 2 * (w + d), d + h
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
            if any(int(s) != s or s < 0 for s in box.size):
                raise ValueError(f"{design.name}/{bone.name}: Boxgroessen muessen ganze Zahlen sein: {box.size}")
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
    rects = {side: (fu * PX, fv * PX, fw * PX, fh * PX) for side, (fu, fv, fw, fh) in faces.items()}
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
    """Rath (UAF): muskuloeser, orange-weisser Tiger ohne Schwanz; weisse Brust/Bauch, Kiefer, Haende und Fuesse,
    schwarze Streifen auf Schultern, Kopf, Oberkoerper und Beinen; je eine schwarze Kralle aus dem Handgelenk;
    gruene Augen unter wuetenden Brauen; Omnitrix mitten auf der Brust."""
    m = {"orange": Material("#E2701F", "#8E3A0C", "#F7A04E", "fur"),
         "white": Material("#E9E6DE", "#9F9A90", "#FFFFFF", "fur"),
         "stripe": Material("#1A1411", "#0A0806", "#3A2E28", "plain"),
         "claw": Material("#1E1E22", "#08080A", "#5A5A66", "metal"),
         "nose": Material("#3A221C", "#1A0E0A", "#5A3A30", "plain")}

    def zone(t: Texel) -> str | None:
        part, y, ax = t.part, t.y, abs(t.x)
        if part == "torso":
            if t.side == "north":
                half = 4.7 if y > 19 else 2.9 + (y - 13) * 0.3
                if ax < half and y < 23.3:
                    return "white"
            if y > 15 and t.side != "north" and tiger(t, 4.0, 0.7, 0.6):
                return "stripe"
            if t.side == "north" and y > 15 and tiger(t, 4.0, 0.7, 0.6) and edge(t) < 0.12:
                return "stripe"
        if part == "hips":
            if t.side == "north" and ax < 2.6:
                return "white"
            return None
        if part == "head":
            if t.side == "north" and t.fy > 0.66:
                return "white"
            if t.side in ("east", "west") and t.fy > 0.66 and t.z < -3.5:
                return "white"
            if t.side == "down":
                return "white"
            if t.side == "up" and abs(t.x) < 0.8 and t.z < -2:
                return "stripe"
            if t.side in ("east", "west") and t.fy < 0.6 and tiger(t, 2.6, 0.6, 0.3):
                return "stripe"
            if t.side == "north" and t.fy < 0.22 and (abs(t.x) > 1.6 and abs(t.x) < 2.6):
                return "stripe"
        if part == "upper_arm" and tiger(t, 3.6, 0.7, 1.1):
            return "stripe"
        if part == "forearm" and y > 13 and tiger(t, 3.6, 0.7, 0.2):
            return "stripe"
        if part == "leg":
            if y < 2.2:
                return "white"
            if y > 5 and tiger(t, 3.4, 0.65, 0.4):
                return "stripe"
        return None

    bones = [
        Bone("body", None, (0, 24, 0), [
            Box((-6, 13, -3.5), (12, 11, 7), "orange", "torso", front="chest"),
            Box((-4.5, 10, -2.5), (9, 3, 5), "orange", "hips"),
            # Nackenmuskel/Trapez hinter dem tief sitzenden Kopf
            Box((-4, 23, -1), (8, 2, 4), "orange", "torso")]),
        Bone("head", None, (0, 23, -2), [
            Box((-3.5, 22.5, -7), (7, 6, 6), "orange", "head", front="rath_face"),
            Box((-2.5, 23, -8), (5, 2, 1), "white", "muzzle", front="rath_muzzle"),
            # weisse Backenbart-Buesche
            Box((-4.5, 22.5, -6), (1, 2, 3), "white", "cheek"),
            Box((3.5, 22.5, -6), (1, 2, 3), "white", "cheek")]),
        Bone("ear_right", "head", (-3, 28.5, -3), [Box((-3.5, 28.5, -3.5), (2, 2, 1), "orange", "ear")], rotation=(0, 0, 15)),
        Bone("ear_left", "head", (3, 28.5, -3), [Box((1.5, 28.5, -3.5), (2, 2, 1), "orange", "ear")], rotation=(0, 0, -15)),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 8.5
        bones += [
            Bone(f"{side}_arm", None, (sx, 23, 0), [Box((sx - 2.5, 16, -2.5), (5, 8, 5), "orange", "upper_arm"),
                                                    Box((sx - 3, 21, -3), (6, 3, 6), "orange", "upper_arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 16, 0), [
                Box((sx - 3, 8, -3), (6, 8, 6), "orange", "forearm"),
                Box((sx - 2.5, 4, -2.5), (5, 4, 5), "white", "fist")]),
            Bone(f"{side}_claw", f"{side}_forearm", (sx + sign * 2, 8, -1), [
                Box((sx + sign * 2.5 - 0.5, 1, -2), (1, 7, 2), "claw", "claw")], rotation=(-12, 0, 0)),
            Bone(f"{side}_leg", None, (sign * 3.2, 11, 0), [
                Box((sign * 3.2 - 2.5, 0, -2.5), (5, 11, 5), "orange", "leg"),
                Box((sign * 3.2 - 2.5, 0, -4.5), (5, 2, 2), "white", "foot")]),
        ]
    return Design("rath", 1.25, "heavy", "#E2701F", m, bones, zone, arm_swing=0.8, leg_swing=0.8, seed=11)


def spidermonkey() -> Design:
    """Spidermonkey (AF/UA): kleiner Affe mit vier Armen, blauem Fell ueber dunkelblauer Mitte (Gesicht, Brust,
    Bauch, Haende, Fuesse), vier gruenen Augen, nach hinten gestrichenem Kopffell und langem Schwanz mit zwei
    dunklen Ringen kurz vor der Spitze; Omnitrix auf der Brust."""
    m = {"blue": Material("#2E5FD6", "#173270", "#5F8FF6", "fur"),
         "navy": Material("#1B2350", "#0B0F24", "#2F3C7A", "plain"),
         "muzzle": Material("#7E8FB8", "#46557E", "#B4C2E2", "plain")}

    def zone(t: Texel) -> str | None:
        part = t.part
        if part == "head" and t.side == "north":
            # rundes dunkles Gesicht im blauen Fell (Ecken bleiben Fell)
            dx, dy = (t.fx - 0.5) / 0.4, (t.fy - 0.6) / 0.52
            if dx * dx + dy * dy < 1.0:
                return "navy"
        if part == "torso" and t.side == "north" and 0.18 < t.fx < 0.82:
            return "navy"
        if part == "torso" and t.side == "down":
            return "navy"
        if part == "tail_tip" and (t.z % 3.0) < 1.0:
            return "navy"
        return None

    bones = [
        Bone("body", None, (0, 21, 0), [Box((-3, 12, -2), (6, 9, 4), "blue", "torso", front="chest")]),
        Bone("head", None, (0, 21, -1), [
            Box((-3.5, 21, -5), (7, 6, 6), "blue", "head", front="spider_face"),
            Box((-2, 21, -6), (4, 2, 1), "muzzle", "muzzle", front="spider_muzzle"),
            # nach hinten gestrichenes Kopffell
            Box((-3, 25, 1), (6, 2, 3), "blue", "hair"),
            Box((-2, 23.5, 3.5), (4, 2, 2), "blue", "hair")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 4
        bones += [
            Bone(f"{side}_arm", None, (sx, 20, 0), [Box((sx - 1, 12, -1), (2, 9, 2), "blue", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 12, 0), [
                Box((sx - 1, 5, -1), (2, 7, 2), "blue", "arm"),
                Box((sx - 1.5, 3, -1.5), (3, 2, 3), "navy", "hand")]),
            # zweites Armpaar: tiefer, weiter aussen und etwas hinten
            Bone(f"{side}_lower_arm", "body", (sign * 4, 16.5, 1), [
                Box((sign * 4.5 - 1, 9, 0), (2, 8, 2), "blue", "arm"),
                Box((sign * 4.5 - 1.5, 7, -0.5), (3, 2, 3), "navy", "hand")], rotation=(8, 0, sign * 14)),
            Bone(f"{side}_leg", None, (sign * 2, 12, 0), [
                Box((sign * 2 - 1.5, 1, -1.5), (3, 11, 3), "blue", "leg"),
                Box((sign * 2 - 1.5, 0, -3.5), (3, 1, 5), "navy", "foot")]),
        ]
    bones += [
        Bone("tail_1", "body", (0, 13, 2.5), [Box((-1, 12, 2.5), (2, 2, 5), "blue", "tail")], rotation=(-35, 0, 0)),
        Bone("tail_2", "tail_1", (0, 13, 7.5), [Box((-1, 12, 7.5), (2, 2, 5), "blue", "tail")], rotation=(-35, 0, 0)),
        Bone("tail_3", "tail_2", (0, 13, 12.5), [Box((-1, 12, 12.5), (2, 2, 4), "blue", "tail")], rotation=(-40, 0, 0)),
        Bone("tail_4", "tail_3", (0, 13, 16.5), [Box((-1, 12, 16.5), (2, 2, 4), "blue", "tail_tip")], rotation=(-55, 0, 0)),
    ]
    loops = {"tail_1": sway("y", 10, 110), "tail_2": sway("y", 12, 110, 30), "tail_3": sway("x", 8, 110, 60),
             "tail_4": sway("x", 12, 110, 90)}
    return Design("spidermonkey", 0.9, "fast", "#2E5FD6", m, bones, zone, loops=loops, seed=12)


def way_big() -> Design:
    """Way Big (AF/UA): schlanker weisser Riese; Kopfkamm vorn rot, unten/hinten schwarz; gruene Augen, dunkle
    untere Gesichtshaelfte und schwarzer Hals; rote Linien von den Schultern zum Omnitrix; weisse Arme mit roten
    Flossen und schwarzen Handgelenken; rote stiefelartige Unterschenkel und Fuesse."""
    m = {"white": Material("#EEEEF0", "#A6A8AE", "#FFFFFF", "plain"),
         "red": Material("#C21A2A", "#6E0C16", "#EE4656", "plain"),
         "black": Material("#1A1A1E", "#060608", "#3A3A42", "plain")}

    def zone(t: Texel) -> str | None:
        part, y, ax = t.part, t.y, abs(t.x)
        if part == "head" and t.side != "up" and t.fy > 0.58:
            return "black"
        if part == "crest" and (t.z > 1.2 or t.side == "down" or t.side == "south"):
            return "black"
        if part == "torso" and t.side == "north" and y > 20:
            # zwei rote Linien von den Schultern schraeg zum Omnitrix (V)
            if abs(ax - (1.4 + (y - 20) * 0.55)) < 0.6:
                return "red"
        if part == "forearm" and y < 9.2:
            return "black"
        if part == "leg":
            peak = 8.0 + (2.0 * (1 - abs(t.fx - 0.5) * 2) if t.side == "north" else 0.0)
            if y < peak:
                return "red"
        return None

    bones = [
        Bone("body", None, (0, 26, 0), [
            Box((-4, 16, -2.5), (8, 10, 5), "white", "torso", front="chest"),
            Box((-3, 13, -2), (6, 3, 4), "white", "waist")]),
        Bone("head", None, (0, 26, 0), [
            Box((-1.5, 26, -1.5), (3, 2, 3), "black", "neck"),
            Box((-2.5, 27.5, -3), (5, 6, 5), "white", "head", front="waybig_face")]),
        # Kopfkamm: drei Lagen, nach vorn oben spitz zulaufend
        Bone("crest", "head", (0, 33, 0), [
            Box((-0.5, 32, -3.5), (1, 3, 7), "red", "crest"),
            Box((-0.5, 35, -3.5), (1, 3, 5), "red", "crest"),
            Box((-0.5, 38, -3.5), (1, 2, 3), "red", "crest")], rotation=(-10, 0, 0)),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 5.5
        bones += [
            Bone(f"{side}_arm", None, (sx, 25, 0), [Box((sx - 1.5, 18, -1.5), (3, 8, 3), "white", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 18, 0), [
                Box((sx - 1.5, 8, -1.5), (3, 10, 3), "white", "forearm"),
                Box((sx - 1.5, 5, -1.5), (3, 3, 3), "white", "hand")]),
            # rote Flosse am Unterarm, aussen, nach hinten oben
            Bone(f"{side}_fin", f"{side}_forearm", (sx + sign * 1.5, 16, 0), [
                Box((sx + sign * 1.5 + (0 if sign > 0 else -1), 9, -1), (1, 9, 3), "red", "fin")], rotation=(0, 0, sign * 12)),
            Bone(f"{side}_leg", None, (sign * 2, 13, 0), [
                Box((sign * 2 - 2, 1, -2), (4, 12, 4), "white", "leg"),
                Box((sign * 2 - 2, 0, -4), (4, 2, 6), "red", "foot")]),
        ]
    return Design("way_big", 3.0, "heavy", "#C21A2A", m, bones, zone, arm_swing=0.7, leg_swing=0.7, seed=13)


def alien_x() -> Design:
    """Alien X: schlanker Humanoid, ganz schwarz mit weissen Sternpunkten (Nachthimmel), weisse Haende, gruene
    pupillenlose Augen, drei hornartige Fortsaetze an der Stirn; Omnitrix auf der Brust."""
    m = {"space": Material("#07080F", "#000000", "#B8D8FF", "stars"),
         "white": Material("#F2F2F4", "#A8AAB2", "#FFFFFF", "plain"),
         "rim": Material("#E8ECF6", "#9CA2B4", "#FFFFFF", "plain")}

    def zone(t: Texel) -> str | None:
        # Hoerner mit weissem Saum (wie die weisse Konturlinie der Vorlage)
        if t.part == "horn" and t.side in ("north", "south") and edge(t) < 0.2:
            return "rim"
        return None

    bones = [
        Bone("body", None, (0, 24, 0), [
            Box((-4.5, 16, -2), (9, 8, 4), "space", "torso", front="chest"),
            Box((-3.5, 12, -1.5), (7, 4, 3), "space", "waist")]),
        Bone("head", None, (0, 24, 0), [
            Box((-1.5, 24, -1.5), (3, 1, 3), "space", "neck"),
            Box((-3, 25, -3), (6, 6, 6), "space", "head", front="alienx_face"),
            Box((-2, 24.5, -3.5), (4, 2, 1), "space", "chin"),
            # mittlerer kleiner Stirnfortsatz
            Box((-0.5, 30, -3.5), (1, 2, 2), "space", "horn")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        hx = sign * 2.5
        bones += [
            # grosse Hoerner: an den Schlaefen nach oben, dann nach hinten gebogen
            Bone(f"horn_{side}", "head", (hx, 30, -1), [Box((hx - 0.5, 30, -2), (1, 3, 2), "space", "horn")],
                 rotation=(15, 0, sign * -10)),
            Bone(f"horn_{side}_2", f"horn_{side}", (hx, 33, -1), [Box((hx - 0.5, 33, -1.5), (1, 3, 2), "space", "horn")],
                 rotation=(30, 0, 0)),
            Bone(f"horn_{side}_3", f"horn_{side}_2", (hx, 36, -1), [Box((hx - 0.5, 36, -1), (1, 2, 1), "space", "horn")],
                 rotation=(35, 0, 0)),
        ]
        sx = sign * 5.5
        bones += [
            Bone(f"{side}_arm", None, (sx, 23, 0), [Box((sx - 1, 17, -1.5), (2, 7, 3), "space", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 17, 0), [
                Box((sx - 1, 11, -1.5), (2, 6, 3), "space", "arm"),
                Box((sx - 1.5, 8, -1.5), (3, 3, 3), "white", "hand")]),
            Bone(f"{side}_leg", None, (sign * 1.8, 12, 0), [Box((sign * 1.8 - 1.5, 0, -1.5), (3, 12, 3), "space", "leg")]),
        ]
    return Design("alien_x", 1.05, "small", "#B8D8FF", m, bones, zone, seed=14)


def brainstorm() -> Design:
    """Brainstorm (AF/UA): Krabbe. Riesiger rostbrauner Kopfpanzer mit wuetendem Gesicht (gruene Augen, schwere
    Brauen, Zahnreihe); die Schaedelplatten klappen auf und zeigen das rosa Gehirn. Silber-schwarze Halsmanschette
    mit Omnitrix. Darunter ein kleiner Leib auf sechs duennen Spinnenbeinen, zwei lange duenne Arme mit Zangen."""
    m = {"shell": Material("#9A4E22", "#56260C", "#C77440", "shell"),
         "plate": Material("#8A4420", "#4A200A", "#B8683A", "shell"),
         "brain": Material("#F28AC4", "#B04A88", "#FFC2E4", "brain", outline=False),
         "silver": Material("#B8BEC6", "#6E747C", "#E6EAF0", "metal"),
         "band": Material("#1C1C20", "#08080A", "#3A3A42", "plain"),
         "leg": Material("#9A4E22", "#56260C", "#C77440", "plain"),
         "tip": Material("#3A2414", "#1A0E06", "#5A3A24", "plain")}

    def zone(t: Texel) -> str | None:
        if t.part == "collar" and abs(t.y - 14.5) < 0.5:
            return "band"
        if t.part == "pincer" and t.y < 1.5:
            return "tip"
        return None

    bones = [
        Bone("head", None, (0, 15, 0), [
            # Halsmanschette (silber mit schwarzem Band, Omnitrix vorn)
            Box((-4, 13, -4), (8, 3, 8), "silver", "collar", front="chest_low"),
            # Kopfpanzer
            Box((-7, 16, -6), (14, 7, 12), "shell", "shell", front="brainstorm_face"),
            Box((-6, 23, -5), (12, 1, 10), "shell", "shell"),
            # Gehirn unter den Platten
            Box((-5, 23.5, -4), (10, 2, 8), "brain", "brain")]),
        # Schaedelplatten links/rechts, klappen an der Aussenkante auf
        Bone("plate_right", "head", (-6, 24, 0), [Box((-6, 24, -5), (6, 2, 10), "plate", "plate", top="brain_top"),
                                                  Box((-4.5, 26, -3.5), (4, 1, 7), "plate", "plate")]),
        Bone("plate_left", "head", (6, 24, 0), [Box((0, 24, -5), (6, 2, 10), "plate", "plate"),
                                                Box((0.5, 26, -3.5), (4, 1, 7), "plate", "plate")]),
        Bone("body", None, (0, 13, 0), [Box((-3, 8, -3), (6, 5, 6), "shell", "thorax")]),
        # gewoelbter Panzerrand rundherum
        Bone("shell_rim", "head", (0, 18, 0), [Box((-8, 17, -5), (1, 5, 10), "shell", "shell"),
                                              Box((7, 17, -5), (1, 5, 10), "shell", "shell"),
                                              Box((-6, 17, 6), (12, 5, 1), "shell", "shell")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 6.5
        bones += [
            # lange duenne Arme unter dem Panzer, Zangen am Ende
            Bone(f"{side}_arm", None, (sx, 16, 0), [Box((sx - 1, 9, -1), (2, 7, 2), "leg", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 9, 0), [
                Box((sx - 1, 3, -1), (2, 6, 2), "leg", "arm"),
                Box((sx - 1.5, 0, -1.5), (3, 3, 3), "shell", "pincer"),
                Box((sx - 0.5, -2, -1.5), (1, 2, 1), "tip", "pincer"),
                Box((sx - 0.5, -2, 0.5), (1, 2, 1), "tip", "pincer")]),
        ]
        # sechs Krabbenbeine: Oberglied schraeg nach aussen oben, Unterglied schraeg nach aussen unten.
        # Mitte = Laufbeine (folgen dem Gang), vorn und hinten gespreizt.
        for name, parent, yaw in ((f"{side}_leg", None, 0), (f"{side}_leg_front", "body", -38), (f"{side}_leg_rear", "body", 38)):
            px = sign * 3
            bones.append(Bone(name, parent, (px, 10, 0), [
                Box((px if sign > 0 else px - 6, 9.5, -0.5), (6, 1, 1), "leg", "leg")],
                rotation=(0, sign * yaw, sign * 28)))
            bones.append(Bone(f"{name}_tip", name, (px + sign * 6, 10, 0), [
                Box((px + sign * 6 - 0.5, -1, -0.5), (1, 11, 1), "leg", "leg"),
                Box((px + sign * 6 - 0.5, -1, -0.5), (1, 2, 1), "tip", "leg")], rotation=(0, 0, sign * -48)))
    loops = {
        # Schaedelplatten oeffnen sich kurz (Gehirn sichtbar), dann zu
        "plate_right": {"rotation": {"vector": [0, 0, "math.max(0,math.sin(query.anim_time*90))*-22"]}},
        "plate_left": {"rotation": {"vector": [0, 0, "math.max(0,math.sin(query.anim_time*90))*22"]}},
        "right_leg_front": sway("x", 6, 180), "left_leg_front": sway("x", 6, 180, 90),
        "right_leg_rear": sway("x", 6, 180, 180), "left_leg_rear": sway("x", 6, 180, 270)}
    return Design("brainstorm", 1.0, "heavy", "#C77440", m, bones, zone, loops=loops, seed=15)


def goop() -> Design:
    """Goop (AF/UA): duenner, gallertartiger gruener Humanoid mit langen Armen, spitz nach oben auslaufendem Kopf,
    kleinen hellen Augen und zerfliessenden Fuessen; der Anti-Gravitations-Projektor schwebt als kleine Untertasse
    ueber dem Kopf. Omnitrix auf der Brust."""
    m = {"slime": Material("#6FD62A", "#2F7A10", "#D8FF8A", "slime"),
         "deep": Material("#4FB01C", "#245E0C", "#A6F060", "slime"),
         "metal": Material("#8A939E", "#4A525C", "#D6DCE4", "metal"),
         "dome": Material("#A8B4C0", "#5E6874", "#E8EEF4", "metal"),
         "light": Material("#7CFF4A", "#3DB018", "#D0FFB8", "plain", glow=True)}

    def zone(t: Texel) -> str | None:
        if t.part in ("leg", "puddle") and t.y < 1.2:
            return "deep"
        return None

    bones = [
        Bone("body", None, (0, 22, 0), [
            Box((-2.5, 17, -1.5), (5, 5, 3), "slime", "torso", front="chest"),
            Box((-1.5, 12, -1), (3, 5, 2), "slime", "torso")]),
        Bone("head", None, (0, 22, 0), [
            Box((-1, 22, -1), (2, 1, 2), "slime", "neck"),
            Box((-2, 23, -2), (4, 4, 4), "slime", "head", front="goop_face"),
            Box((-1.5, 27, -1.5), (3, 2, 3), "slime", "head"),
            Box((-1, 29, -1), (2, 2, 2), "slime", "head"),
            Box((-0.5, 31, -0.5), (1, 1, 1), "slime", "head")]),
        # Projektor: Untertasse mit Lichtring und Kuppel, schwebt ueber dem Kopf
        Bone("projector", "head", (0, 35, 0), [
            Box((-3, 34, -3), (6, 1, 6), "metal", "projector"),
            Box((-2, 33, -2), (4, 1, 4), "light", "projector"),
            Box((-1, 35, -1), (2, 1, 2), "dome", "projector")]),
        Bone("drip", "body", (0, 12, 0), [Box((-2, 10, -1), (1, 2, 1), "slime", "drip"),
                                          Box((1, 9, 0), (1, 3, 1), "slime", "drip")]),
    ]
    for sign in (-1, 1):
        side = side_of(sign)
        sx = sign * 3.5
        bones += [
            Bone(f"{side}_arm", None, (sx, 21, 0), [Box((sx - 1, 13, -1), (2, 9, 2), "slime", "arm")]),
            Bone(f"{side}_forearm", f"{side}_arm", (sx, 13, 0), [
                Box((sx - 1, 6, -1), (2, 7, 2), "slime", "arm"),
                Box((sx - 1.5, 4, -1.5), (3, 2, 3), "slime", "hand")]),
            Bone(f"{side}_leg", None, (sign * 1.2, 12, 0), [
                Box((sign * 1.2 - 1, 2, -1), (2, 10, 2), "slime", "leg"),
                Box((sign * 1.2 - 1.5, 1, -1.5), (3, 1, 3), "slime", "leg"),
                Box((sign * 1.2 - 2.5, 0, -2.5), (5, 1, 5), "slime", "puddle")]),
        ]
    loops = {"projector": {"position": {"vector": [0, "math.sin(query.anim_time*120)*0.6", 0]},
                           "rotation": {"vector": [0, "query.anim_time*90", 0]}},
             "drip": {"scale": {"vector": [1, "1+math.sin(query.anim_time*160)*0.15", 1]}}}
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
