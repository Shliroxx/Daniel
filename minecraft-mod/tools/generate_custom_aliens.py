#!/usr/bin/env python3
"""Baut die Aliens, fuer die Alien Evolution kein Modell hat (Rath, Spidermonkey, Way Big, Alien X, Brainstorm, Goop),
im selben Stil und Dateiformat wie die AE-Importe (tools/import_alienevo.py):

* Geometrie aus Bloecken (Blockbench-Stil, Bedrock-Format), Knochen wie beim Import: head, body, right_arm, left_arm,
  right_leg, left_leg (+ right_forearm, left_forearm, right_lower_arm, left_lower_arm, tail_N, freie Extras).
* Textur: jede Box bekommt ihren Platz (Box-UV, automatisch gepackt) und wird nach ihrem Material bemalt —
  Grundfarbe, Licht von oben, Kantenschatten, Rauschen und Muster (Tigerstreifen, Fell, Sternenhimmel, Schleim,
  Panzerplatten, Metall). Gesichter, Augen und das Omnitrix-Symbol sind Merkmale auf der Vorderseite.
* Leuchtmaske (Augen, Gehirn, Sterne, Symbol), Ego-Arme, Daueranimationen (Schwanz, Projektor), alien_render.

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

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import import_alienevo as imp  # noqa: E402  (Ego-Arme, Animationssatz)

LOG = logging.getLogger("custom_aliens")
ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
DATA = ASSETS.parent.parent / "data" / "kingdomomnitrix" / "kingdomomnitrix" / "alien"
ATLAS = 128

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
    front: str | None = None        # Merkmal auf der Vorderseite (north), siehe FEATURES
    back: str | None = None         # Merkmal auf der Rueckseite (south)


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
    pattern: str = "plain"          # plain, stripes, fur, stars, slime, shell, metal
    accent: str | None = None       # Musterfarbe (Streifen, Sterne, Fugen)
    glow: bool = False              # ganze Flaeche leuchtet


@dataclass
class Design:
    name: str
    ae_size: float                  # sichtbare Groesse (wie AE „size_change“)
    style: str                      # Animations-Charakter (Verwandeln/Zurueck)
    accent: str
    materials: dict[str, Material]
    bones: list[Bone]
    arm_swing: float = 1.0
    leg_swing: float = 1.0
    loops: dict[str, dict] = field(default_factory=dict)  # Knochen → Bedrock-Spuren der Daueranimation
    seed: int = 1


# --- Maler -----------------------------------------------------------------------------------------


class Painter:
    def __init__(self, design: Design, width: int, height: int) -> None:
        self.design = design
        self.color = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.rng = random.Random(design.seed)

    def put(self, x: int, y: int, c: Color, glow: bool = False) -> None:
        if 0 <= x < self.color.width and 0 <= y < self.color.height:
            self.color.putpixel((x, y), (*c, 255))
            if glow:
                self.glow.putpixel((x, y), (*c, 255))

    def face(self, u: int, v: int, w: int, h: int, mat: Material, light: float, world: tuple[float, float, float, str]) -> None:
        """Eine Flaeche bemalen. {@code world}: Weltlage der Flaechenmitte + Flaechenname fuer Muster."""
        base, dark, hi = rgb(mat.base), rgb(mat.dark), rgb(mat.light)
        accent = rgb(mat.accent) if mat.accent else dark
        wx, wy, wz, side = world
        for py in range(h):
            for px in range(w):
                # Licht von oben, dunklere Kanten, feines Rauschen
                t = py / max(1, h - 1)
                k = light * (1.06 - 0.16 * t)
                edge = px == 0 or py == 0 or px == w - 1 or py == h - 1
                c = shade(base, k * (0.9 if edge else 1.0) * (0.96 + 0.08 * self.rng.random()))
                pat = mat.pattern
                gy = wy + (h / 2 - py)  # Welt-Hoehe dieses Pixels (ungefaehr)
                if pat == "stripes":
                    # Tigerstreifen: schraege Baender je nach Hoehe, unregelmaessig
                    phase = (gy * 0.8 + math.sin(px * 0.7 + wx) * 1.2 + (wx + wz) * 0.15) % 6.0
                    if phase < 0.75 and self.rng.random() > 0.25:
                        c = shade(accent, light * 0.95)
                elif pat == "fur":
                    if self.rng.random() < 0.18:
                        c = shade(mix(base, dark, 0.6), k)
                    elif self.rng.random() < 0.08:
                        c = shade(hi, k)
                elif pat == "stars":
                    c = shade(base, 0.9 + 0.1 * self.rng.random())
                    roll = self.rng.random()
                    if roll < 0.05:
                        self.put(u + px, v + py, rgb("#FFFFFF"), glow=True)
                        continue
                    if roll < 0.09:
                        self.put(u + px, v + py, accent, glow=True)
                        continue
                elif pat == "slime":
                    blob = math.sin(px * 0.9 + gy * 0.7) + math.sin(py * 1.3 - wx * 0.5)
                    c = shade(mix(base, hi, max(0.0, blob) * 0.25), k)
                    if self.rng.random() < 0.05:
                        c = shade(hi, 1.1)
                    if py == 0 and self.rng.random() < 0.4:
                        c = shade(hi, 1.15)
                elif pat == "shell":
                    if (px % 4 == 0 and py % 3 != 0) or py % 5 == 0:
                        c = shade(accent, k * 0.9)
                    elif self.rng.random() < 0.1:
                        c = shade(hi, k)
                elif pat == "metal":
                    c = shade(mix(base, hi, 0.25 if px % 3 == 0 else 0.0), k)
                self.put(u + px, v + py, c, glow=mat.glow)

    def feature(self, u: int, v: int, w: int, h: int, name: str) -> None:
        FEATURES[name](self, u, v, w, h)


def _block(p: Painter, x: int, y: int, rows: list[str], palette: dict[str, tuple[str, bool]]) -> None:
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in palette:
                color, glow = palette[ch]
                p.put(x + i, y + j, rgb(color), glow)


def badge(p: Painter, u: int, v: int, w: int, h: int, cx: float = 0.5, cy: float = 0.42) -> None:
    """Omnitrix-Symbol (Sanduhr) auf der Brust."""
    x, y = u + round(w * cx) - 3, v + round(h * cy) - 3
    _block(p, x, y, ["kkkkkk", "kgggg k", " kggk ", " kggk ", "kgggg k", "kkkkkk"],
           {"k": ("#1A1A1A", False), "g": ("#4DFF3A", True)})


def f_rath_chest(p, u, v, w, h):
    for py in range(round(h * 0.25), h):
        for px in range(round(w * 0.2), round(w * 0.8)):
            p.put(u + px, v + py, shade(rgb("#F1ECE0"), 1.0 - 0.15 * py / h))
    badge(p, u, v, w, h, 0.5, 0.42)


def f_rath_face(p, u, v, w, h):
    x0, y0 = u + w // 2 - 3, v + 1
    _block(p, x0, y0, ["kk  kk", "gk  kg", "      ", " wwww ", "wkwwkw", " wwww "],
           {"k": ("#151515", False), "g": ("#7CFF4A", True), "w": ("#F1ECE0", False)})


def f_spider_chest(p, u, v, w, h):
    for py in range(h):
        for px in range(w // 2 - 1, w // 2 + 1):
            p.put(u + px, v + py, rgb("#14213D"))
    badge(p, u, v, w, h, 0.5, 0.35)


def f_spider_face(p, u, v, w, h):
    x0, y0 = u + w // 2 - 3, v + 1
    _block(p, x0, y0, ["gg  gg", "gg  gg", " g  g ", "      ", " llll ", " lkkl "],
           {"g": ("#7CFF4A", True), "l": ("#9CC9FF", False), "k": ("#1A1A1A", False)})


def f_waybig_chest(p, u, v, w, h):
    for py in range(h):
        for px in (0, 1, w - 2, w - 1):
            p.put(u + px, v + py, rgb("#C8202E"))
    for py in range(round(h * 0.15), round(h * 0.8)):
        p.put(u + w // 2, v + py, rgb("#262626"))
    badge(p, u, v, w, h, 0.5, 0.3)


def f_waybig_face(p, u, v, w, h):
    x0, y0 = u + w // 2 - 2, v + 2
    _block(p, x0, y0, ["bb bb", "      ", " kkk "], {"b": ("#7FE3FF", True), "k": ("#262626", False)})


def f_alienx_chest(p, u, v, w, h):
    badge(p, u, v, w, h, 0.5, 0.35)


def f_alienx_face(p, u, v, w, h):
    x0, y0 = u + w // 2 - 3, v + 2
    _block(p, x0, y0, ["gg  gg", "gg  gg"], {"g": ("#9CFF7A", True)})


def f_brain(p, u, v, w, h):
    for py in range(h):
        for px in range(w):
            c = rgb("#F07AC8") if (px + py * 2) % 5 else rgb("#B9409A")
            p.put(u + px, v + py, c, glow=True)


def f_brainstorm_face(p, u, v, w, h):
    x0, y0 = u + w // 2 - 3, v + h - 4
    _block(p, x0, y0, ["g    g", "      ", "kkkkkk"], {"g": ("#7CFF4A", True), "k": ("#3A2410", False)})


def f_brainstorm_chest(p, u, v, w, h):
    badge(p, u, v, w, h, 0.5, 0.5)


def f_goop_face(p, u, v, w, h):
    x0, y0 = u + w // 2 - 2, v + 2
    _block(p, x0, y0, ["k  k", "k  k"], {"k": ("#0E2A0B", False)})


def f_goop_chest(p, u, v, w, h):
    badge(p, u, v, w, h, 0.5, 0.35)


def f_projector_light(p, u, v, w, h):
    for py in range(h):
        for px in range(w):
            p.put(u + px, v + py, rgb("#7CFF4A"), glow=True)


FEATURES = {
    "rath_chest": f_rath_chest, "rath_face": f_rath_face, "spider_chest": f_spider_chest, "spider_face": f_spider_face,
    "waybig_chest": f_waybig_chest, "waybig_face": f_waybig_face, "alienx_chest": f_alienx_chest, "alienx_face": f_alienx_face,
    "brain": f_brain, "brainstorm_face": f_brainstorm_face, "brainstorm_chest": f_brainstorm_chest,
    "goop_face": f_goop_face, "goop_chest": f_goop_chest, "projector_light": f_projector_light,
}
LIGHT = {"up": 1.12, "down": 0.6, "north": 1.0, "south": 0.82, "east": 0.88, "west": 0.88}


# --- Bauen -----------------------------------------------------------------------------------------


def pack(boxes: list[Box]) -> tuple[dict[int, tuple[int, int]], int]:
    """Box-UV-Felder in Regalen packen; Rueckgabe: Feld je Box (id) und noetige Atlas-Hoehe."""
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
    boxes = [box for bone in design.bones for box in bone.boxes]
    uv, height = pack(boxes)
    painter = Painter(design, ATLAS, height)
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
            paint_box(painter, design, box, u, v)
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


def paint_box(p: Painter, design: Design, box: Box, u: int, v: int) -> None:
    mat = design.materials[box.mat]
    w, h, d = box.size
    ox, oy, oz = box.origin
    cx, cy, cz = ox + w / 2, oy + h / 2, oz + d / 2
    # Box-UV: oben (u+d,v) w×d, unten (u+d+w,v) w×d, Seiten in der Reihe v+d: east (u), north (u+d), west (u+d+w), south (u+2d+w)
    faces = {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
             "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}
    for side, (fu, fv, fw, fh) in faces.items():
        if fw and fh:
            p.face(fu, fv, fw, fh, mat, LIGHT[side], (cx, cy, cz, side))
    if box.front:
        p.feature(u + d, v + d, w, h, box.front)
    if box.back:
        p.feature(u + 2 * d + w, v + d, w, h, box.back)


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


# --- Entwuerfe -------------------------------------------------------------------------------------

def sway(axis: str, amount: float, speed: float = 180.0, phase: float = 0.0) -> dict:
    """Bedrock-Pendelspur (Molang): Drehung um eine Achse."""
    expr = f"math.sin(query.anim_time*{speed}+{phase})*{amount}"
    vec = {"x": [expr, 0, 0], "y": [0, expr, 0], "z": [0, 0, expr]}[axis]
    return {"rotation": {"vector": vec}}


def humanoid_limbs(mat_arm: str, mat_leg: str, arm_w: int, arm_len: int, fore_len: int, leg_w: int, leg_len: int,
                   shoulder_x: float, shoulder_y: float, hip_x: float, arm_d: int = 4, leg_d: int = 4,
                   fore_mat: str | None = None) -> list[Bone]:
    bones = []
    for side, sign in (("right", -1), ("left", 1)):
        sx = sign * shoulder_x
        x0 = sx - arm_w / 2
        top = shoulder_y
        bones.append(Bone(f"{side}_arm", None, (sx, top - 1, 0),
                          [Box((x0, top - arm_len, -arm_d / 2), (arm_w, arm_len, arm_d), mat_arm)]))
        bones.append(Bone(f"{side}_forearm", f"{side}_arm", (sx, top - arm_len, 0),
                          [Box((x0 - 0.5, top - arm_len - fore_len, -arm_d / 2 - 0.5), (arm_w + 1, fore_len, arm_d + 1), fore_mat or mat_arm)]))
        hx = sign * hip_x
        bones.append(Bone(f"{side}_leg", None, (hx, leg_len, 0),
                          [Box((hx - leg_w / 2, 0, -leg_d / 2), (leg_w, leg_len, leg_d), mat_leg)]))
    return bones


def rath() -> Design:
    m = {"fur": Material("#E8862A", "#9A4A12", "#FFB15A", "stripes", "#1C1C1C"),
         "white": Material("#F1ECE0", "#B9B0A0", "#FFFFFF", "fur"),
         "claw": Material("#E9E2D2", "#9E968A", "#FFFFFF", "metal")}
    bones = [
        Bone("body", None, (0, 24, 0), [Box((-6, 12, -4), (12, 13, 8), "fur", front="rath_chest")]),
        Bone("head", None, (0, 25, -1), [Box((-3.5, 25, -5), (7, 6, 7), "fur", front="rath_face"),
                                           Box((-3, 24, -6), (6, 2, 2), "white")]),
        Bone("ear_right", "head", (-3, 31, -2), [Box((-3.5, 31, -2.5), (2, 2, 1), "fur")]),
        Bone("ear_left", "head", (3, 31, -2), [Box((1.5, 31, -2.5), (2, 2, 1), "fur")]),
    ] + humanoid_limbs("fur", "fur", 5, 9, 7, 5, 12, 8.5, 24, 3, arm_d=5, leg_d=6, fore_mat="fur")
    for side, sign in (("right", -1), ("left", 1)):
        bones.append(Bone(f"{side}_claw", f"{side}_forearm", (sign * 8.5, 9, 0),
                          [Box((sign * 8.5 - 0.5, 9, -7), (1, 1, 4), "claw")]))
    bones += [Bone("tail_1", "body", (0, 14, 4), [Box((-1, 13, 4), (2, 2, 6), "fur")]),
              Bone("tail_2", "tail_1", (0, 14, 10), [Box((-1, 13, 10), (2, 2, 5), "fur")])]
    loops = {"tail_1": sway("y", 12, 140), "tail_2": sway("y", 18, 140, 40)}
    return Design("rath", 1.25, "heavy", "#E8862A", m, bones, arm_swing=0.8, leg_swing=0.8, loops=loops, seed=11)


def spidermonkey() -> Design:
    m = {"fur": Material("#2D63C8", "#1B2E5C", "#5B8FF0", "fur"),
         "suit": Material("#1B2440", "#0D1222", "#34436E", "plain"),
         "face": Material("#2D63C8", "#1B2E5C", "#5B8FF0", "plain"),
         "muzzle": Material("#9CC9FF", "#5C86B8", "#D4E8FF", "plain")}
    bones = [
        Bone("body", None, (0, 22, 0), [Box((-3.5, 12, -2), (7, 10, 4), "suit", front="spider_chest")]),
        Bone("head", None, (0, 22, 0), [Box((-4, 22, -4), (8, 7, 7), "face", front="spider_face"),
                                        Box((-2, 22, -5), (4, 3, 1), "muzzle")]),
        Bone("ear_right", "head", (-4, 27, 0), [Box((-5.5, 26, -1), (2, 2, 1), "face")]),
        Bone("ear_left", "head", (4, 27, 0), [Box((3.5, 26, -1), (2, 2, 1), "face")]),
    ] + humanoid_limbs("fur", "fur", 3, 7, 6, 3, 12, 5, 21, 1.8, arm_d=3, leg_d=3)
    for side, sign in (("right", -1), ("left", 1)):
        # zweites Armpaar: weiter aussen und etwas hinten, damit es neben dem ersten zu sehen ist
        bones.append(Bone(f"{side}_lower_arm", None, (sign * 6.5, 18, 1.5),
                          [Box((sign * 7 - 1, 11, 0.5), (2, 7, 2), "fur"), Box((sign * 7 - 1.5, 7, 0.25), (3, 4, 3), "fur")],
                          rotation=(0, 0, sign * 12)))
    bones += [Bone("tail_1", "body", (0, 13, 2), [Box((-1, 12, 2), (2, 2, 5), "fur")]),
              Bone("tail_2", "tail_1", (0, 13, 7), [Box((-1, 12, 7), (2, 2, 5), "fur")], rotation=(-25, 0, 0)),
              Bone("tail_3", "tail_2", (0, 13, 12), [Box((-1, 12, 12), (2, 2, 4), "fur")], rotation=(-30, 0, 0))]
    loops = {"tail_1": sway("x", 8, 120), "tail_2": sway("y", 15, 120, 30), "tail_3": sway("y", 20, 120, 60)}
    return Design("spidermonkey", 0.9, "fast", "#2D63C8", m, bones, loops=loops, seed=12)


def way_big() -> Design:
    m = {"white": Material("#ECECEC", "#A8A8A8", "#FFFFFF", "plain"),
         "red": Material("#C8202E", "#7E101A", "#F04A55", "plain"),
         "dark": Material("#262626", "#111111", "#444444", "plain")}
    bones = [
        Bone("body", None, (0, 27, 0), [Box((-5, 14, -3), (10, 13, 6), "white", front="waybig_chest")]),
        Bone("head", None, (0, 27, 0), [Box((-3, 27, -3), (6, 7, 6), "white", front="waybig_face")]),
        Bone("fin", "head", (0, 30, 0), [Box((-0.5, 30, -4), (1, 9, 9), "red")]),
        Bone("shoulder_right", "body", (-5, 26, 0), [Box((-8, 25, -2), (3, 3, 4), "red")]),
        Bone("shoulder_left", "body", (5, 26, 0), [Box((5, 25, -2), (3, 3, 4), "red")]),
    ] + humanoid_limbs("white", "red", 3, 7, 6, 4, 14, 6.5, 25, 2.5, arm_d=4, leg_d=5, fore_mat="red")
    return Design("way_big", 3.0, "heavy", "#C8202E", m, bones, arm_swing=0.7, leg_swing=0.7, seed=13)


def alien_x() -> Design:
    m = {"stars": Material("#05060E", "#000000", "#1B1E33", "stars", "#9FD8FF"),
         "horn": Material("#F2F2F2", "#B0B0B0", "#FFFFFF", "metal")}
    bones = [
        Bone("body", None, (0, 24, 0), [Box((-4, 12, -2), (8, 12, 4), "stars", front="alienx_chest")]),
        Bone("head", None, (0, 24, 0), [Box((-3.5, 24, -3.5), (7, 7, 7), "stars", front="alienx_face")]),
        # Hoerner: drei Glieder je Seite, nach aussen und oben gebogen
        Bone("horn_right", "head", (-3.5, 29, 0), [Box((-5.5, 28, -1), (2, 2, 2), "horn")], rotation=(0, 0, 20)),
        Bone("horn_right_2", "horn_right", (-5.5, 30, 0), [Box((-7, 30, -1), (2, 3, 2), "horn")], rotation=(0, 0, -25)),
        Bone("horn_right_3", "horn_right_2", (-6, 33, 0), [Box((-6.5, 33, -0.5), (1, 2, 1), "horn")], rotation=(0, 0, -25)),
        Bone("horn_left", "head", (3.5, 29, 0), [Box((3.5, 28, -1), (2, 2, 2), "horn")], rotation=(0, 0, -20)),
        Bone("horn_left_2", "horn_left", (5.5, 30, 0), [Box((5, 30, -1), (2, 3, 2), "horn")], rotation=(0, 0, 25)),
        Bone("horn_left_3", "horn_left_2", (6, 33, 0), [Box((5.5, 33, -0.5), (1, 2, 1), "horn")], rotation=(0, 0, 25)),
    ] + humanoid_limbs("stars", "stars", 3, 7, 6, 3, 12, 5.5, 24, 2, arm_d=3, leg_d=4)
    return Design("alien_x", 1.05, "small", "#9FD8FF", m, bones, seed=14)


def brainstorm() -> Design:
    m = {"shell": Material("#9C6A33", "#5A3A18", "#C99A5E", "shell", "#4A2E12"),
         "soft": Material("#C7955A", "#7A5530", "#E9C08A", "plain"),
         "brain": Material("#F07AC8", "#B9409A", "#FFB0E6", "plain", glow=True)}
    bones = [
        Bone("body", None, (0, 18, 0), [Box((-5, 9, -4), (10, 9, 8), "shell", front="brainstorm_chest")]),
        Bone("head", None, (0, 18, 0), [Box((-5.5, 17, -5), (11, 5, 10), "shell", front="brainstorm_face"),
                                          Box((-4, 22, -3.5), (8, 3, 7), "brain", front="brain")]),
        Bone("shell_top", "head", (0, 23, 0), [Box((-5, 24, -4.5), (10, 1, 9), "shell")]),
    ] + humanoid_limbs("soft", "soft", 3, 6, 5, 3, 9, 6.5, 17, 2.5, arm_d=3, leg_d=4, fore_mat="shell")
    for side, sign in (("right", -1), ("left", 1)):
        bones.append(Bone(f"{side}_pincer", f"{side}_forearm", (sign * 6.5, 6, 0),
                          [Box((sign * 6.5 - 1.5, 3, -5), (3, 2, 4), "shell"), Box((sign * 6.5 - 1.5, 6, -5), (3, 1, 3), "shell")]))
        bones.append(Bone(f"{side}_leg_rear", "body", (sign * 4, 9, 3),
                          [Box((sign * 4 - 1, 0, 2), (2, 9, 2), "soft")], rotation=(-15, 0, 0)))
    loops = {"shell_top": {"position": {"vector": [0, "math.max(0,math.sin(query.anim_time*90))*0.4", 0]}}}
    return Design("brainstorm", 1.0, "heavy", "#C99A5E", m, bones, loops=loops, seed=15)


def goop() -> Design:
    m = {"slime": Material("#5FD13A", "#2E7A18", "#B6FF8A", "slime"),
         "metal": Material("#8E9AA6", "#4E5864", "#D2DAE2", "metal"),
         "light": Material("#7CFF4A", "#4DCC2A", "#C8FFB0", "plain", glow=True)}
    bones = [
        Bone("body", None, (0, 22, 0), [Box((-3.5, 11, -2.5), (7, 11, 5), "slime", front="goop_chest")]),
        Bone("head", None, (0, 22, 0), [Box((-3, 22, -3), (6, 6, 6), "slime", front="goop_face")]),
        Bone("drip", "body", (0, 11, 0), [Box((-2, 9, -1.5), (1, 2, 1), "slime"), Box((1.5, 8, 0.5), (1, 3, 1), "slime")]),
        Bone("projector", "head", (0, 31, 0), [Box((-3, 31, -3), (6, 1, 6), "metal"), Box((-2, 30, -2), (4, 1, 4), "light")]),
    ] + humanoid_limbs("slime", "slime", 3, 7, 6, 3, 11, 5, 21, 1.5, arm_d=3, leg_d=4)
    loops = {"projector": {"position": {"vector": [0, "math.sin(query.anim_time*120)*0.6", 0]},
                           "rotation": {"vector": [0, "query.anim_time*90", 0]}},
             "drip": {"scale": {"vector": [1, "1+math.sin(query.anim_time*160)*0.15", 1]}}}
    return Design("goop", 1.0, "small", "#5FD13A", m, bones, loops=loops, seed=16)


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
