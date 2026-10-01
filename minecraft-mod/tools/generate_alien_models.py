#!/usr/bin/env python3
"""Erzeugt die GeckoLib-Koerper der Aliens: Geometrie (.geo.json), Animationen (.animation.json)
und Texturen (.png) unter assets/kingdomomnitrix/{geo,animations,textures}/entity/alien/.

Die Modelle sind bewusst einfache Platzhalter-Geometrie (Status PLACEHOLDER), die sich in
Blockbench oeffnen und verfeinern laesst. Knochennamen sind fest: root, body, head, right_arm,
left_arm, right_leg, left_leg (+ alien-spezifische Extras). "head" dreht sich mit dem Blick.

Aufruf aus dem Ordner minecraft-mod/:

    python tools/generate_alien_models.py           # alles neu schreiben
    python tools/generate_alien_models.py --check   # nur pruefen, ob alles vorhanden ist

Benoetigt Pillow (pip install pillow).
"""
from __future__ import annotations

import argparse
import json
import logging
import random
import sys
from dataclasses import dataclass, field
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("alien_models")
ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"


def rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


@dataclass
class Cube:
    origin: tuple[float, float, float]
    size: tuple[int, int, int]
    uv: tuple[int, int]
    color: str
    front_detail: str | None = None  # "eyes", "visor", "four_eyes", "big_eyes", "omnitrix", "crack"
    mirror: bool = False

    def to_json(self) -> dict:
        data = {"origin": list(self.origin), "size": list(self.size), "uv": list(self.uv)}
        if self.mirror:
            data["mirror"] = True
        return data


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple[float, float, float]
    cubes: list[Cube] = field(default_factory=list)

    def to_json(self) -> dict:
        data: dict = {"name": self.name, "pivot": list(self.pivot)}
        if self.parent:
            data["parent"] = self.parent
        if self.cubes:
            data["cubes"] = [c.to_json() for c in self.cubes]
        return data


@dataclass
class Alien:
    name: str
    texture_size: tuple[int, int]
    bones: list[Bone]
    accent: str


def humanoid(skin: str, head: str, legs: str, *, body_uv=(16, 16), head_cube: Cube | None = None,
             head_detail: str = "eyes") -> list[Bone]:
    """Grundgeruest im Spieler-Layout (64x64), Fuesse auf y=0, Kopf bis y=32."""
    return [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 24, 0), [Cube((-4, 12, -2), (8, 12, 4), body_uv, skin, "omnitrix")]),
        Bone("head", "body", (0, 24, 0), [head_cube or Cube((-4, 24, -4), (8, 8, 8), (0, 0), head, head_detail)]),
        Bone("right_arm", "body", (-5, 22, 0), [Cube((-8, 12, -2), (4, 12, 4), (40, 16), skin)]),
        Bone("left_arm", "body", (5, 22, 0), [Cube((4, 12, -2), (4, 12, 4), (40, 16), skin, mirror=True)]),
        Bone("right_leg", "root", (-1.9, 12, 0), [Cube((-3.9, 0, -2), (4, 12, 4), (0, 16), legs)]),
        Bone("left_leg", "root", (1.9, 12, 0), [Cube((-0.1, 0, -2), (4, 12, 4), (0, 16), legs, mirror=True)]),
    ]


def bone(bones: list[Bone], name: str) -> Bone:
    return next(b for b in bones if b.name == name)


def build_aliens() -> list[Alien]:
    aliens = []

    # Heatblast: Magma-Koerper, Flammenkrone
    b = humanoid("#5A1E0E", "#FFB000", "#4A170A")
    b.append(Bone("flame_crest", "head", (0, 32, 0), [
        Cube((-3, 32, -3), (6, 4, 6), (32, 0), "#FF7A00"),
        Cube((-1.5, 36, -1.5), (3, 4, 3), (32, 32), "#FFE14D"),
    ]))
    aliens.append(Alien("heatblast", (64, 64), b, "#FF6A00"))

    # XLR8: schlank, Visier, Schwanz
    b = humanoid("#1B3C8C", "#14213D", "#0B1A3A", head_detail="visor")
    b.append(Bone("visor", "head", (0, 28, -4), [Cube((-4, 26, -6), (8, 3, 2), (32, 0), "#7FD4FF")]))
    b.append(Bone("tail", "body", (0, 13, 2), [Cube((-1, 12, 2), (2, 2, 9), (0, 32), "#1B3C8C")]))
    aliens.append(Alien("xlr8", (64, 64), b, "#1E90FF"))

    # Vierarm: breiter Rumpf, vier Arme, vier Augen (128x64)
    b = [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 24, 0), [Cube((-5, 12, -2.5), (10, 12, 5), (64, 0), "#B22A1E", "omnitrix")]),
        Bone("head", "body", (0, 24, 0), [Cube((-4, 24, -4), (8, 8, 8), (0, 0), "#C0392B", "four_eyes")]),
        Bone("right_arm", "body", (-6, 23, 0), [Cube((-10, 13, -2), (4, 11, 4), (40, 16), "#C0392B")]),
        Bone("left_arm", "body", (6, 23, 0), [Cube((6, 13, -2), (4, 11, 4), (40, 16), "#C0392B", mirror=True)]),
        Bone("right_lower_arm", "body", (-5.5, 18, 0), [Cube((-8.5, 9, -1.5), (3, 9, 3), (64, 32), "#A93226")]),
        Bone("left_lower_arm", "body", (5.5, 18, 0), [Cube((5.5, 9, -1.5), (3, 9, 3), (64, 32), "#A93226", mirror=True)]),
        Bone("right_leg", "root", (-2.4, 12, 0), [Cube((-4.9, 0, -2.5), (5, 12, 5), (0, 16), "#1C1C1C")]),
        Bone("left_leg", "root", (2.4, 12, 0), [Cube((-0.1, 0, -2.5), (5, 12, 5), (0, 16), "#1C1C1C", mirror=True)]),
    ]
    aliens.append(Alien("four_arms", (128, 64), b, "#C0392B"))

    # Diamondhead: Kristallkoerper mit Kopf- und Schulterspitzen
    b = humanoid("#2ECC71", "#58F0A0", "#1E9E5E")
    b.append(Bone("crystal_crest", "head", (0, 32, 0), [Cube((-2, 32, -2), (4, 6, 4), (32, 0), "#A8FFD4")]))
    b.append(Bone("right_spike", "right_arm", (-6, 24, 0), [Cube((-7, 24, -1), (2, 4, 2), (0, 32), "#A8FFD4")]))
    b.append(Bone("left_spike", "left_arm", (6, 24, 0), [Cube((5, 24, -1), (2, 4, 2), (0, 32), "#A8FFD4", mirror=True)]))
    aliens.append(Alien("diamondhead", (64, 64), b, "#2ECC71"))

    # Grey Matter: grosser Kopf, grosse Augen (128x64)
    b = humanoid("#95A5A6", "#A9B7B8", "#7F8C8D",
                 head_cube=Cube((-5, 24, -5), (10, 9, 10), (64, 0), "#A9B7B8", "big_eyes"))
    aliens.append(Alien("grey_matter", (128, 64), b, "#95A5A6"))
    return aliens


# --- Textur ---------------------------------------------------------------------------------------

def paint_cube(img: Image.Image, cube: Cube, rng: random.Random) -> None:
    u, v = cube.uv
    w, h, d = cube.size
    base = rgb(cube.color)
    width, height = img.size

    def put(x: int, y: int, color: tuple[int, int, int]) -> None:
        if 0 <= x < width and 0 <= y < height:
            img.putpixel((x, y), color + (255,))

    def shade(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
        jitter = rng.randint(-10, 10)
        return tuple(max(0, min(255, int(c * factor) + jitter)) for c in color)

    faces = [
        (u + d, v, w, d, 1.15),              # oben
        (u + d + w, v, w, d, 0.70),          # unten
        (u, v + d, d, h, 0.85),              # rechts
        (u + d, v + d, w, h, 1.00),          # vorne
        (u + d + w, v + d, d, h, 0.85),      # links
        (u + 2 * d + w, v + d, w, h, 0.80),  # hinten
    ]
    for fx, fy, fw, fh, factor in faces:
        for y in range(fy, fy + fh):
            for x in range(fx, fx + fw):
                put(x, y, shade(base, factor))

    front_x, front_y = u + d, v + d
    detail = cube.front_detail
    if detail == "eyes":
        for ex in (1, w - 3):
            put(front_x + ex, front_y + h // 2 - 1, (255, 255, 255))
            put(front_x + ex + 1, front_y + h // 2 - 1, (30, 30, 30))
    elif detail == "visor":
        for x in range(front_x, front_x + w):
            put(x, front_y + 2, (20, 20, 20))
            put(x, front_y + 3, (90, 200, 255))
    elif detail == "four_eyes":
        for row in (2, 4):
            for ex in (1, w - 3):
                put(front_x + ex, front_y + row, (255, 230, 80))
                put(front_x + ex + 1, front_y + row, (255, 230, 80))
    elif detail == "big_eyes":
        for ex in (1, w - 4):
            for dx in range(3):
                for dy in range(3):
                    put(front_x + ex + dx, front_y + 3 + dy, (40, 255, 40) if (dx, dy) == (1, 1) else (10, 10, 10))
    elif detail == "omnitrix":
        cx, cy = front_x + w // 2 - 1, front_y + 2
        for dx in range(2):
            for dy in range(2):
                put(cx + dx, cy + dy, (57, 255, 20))


def build_texture(alien: Alien, rng: random.Random) -> Image.Image:
    img = Image.new("RGBA", alien.texture_size, (0, 0, 0, 0))
    for b in alien.bones:
        for cube in b.cubes:
            if not cube.mirror:
                paint_cube(img, cube, rng)
    return img


# --- Geometrie und Animation ----------------------------------------------------------------------

def build_geo(alien: Alien) -> dict:
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.kingdomomnitrix.{alien.name}",
                "texture_width": alien.texture_size[0],
                "texture_height": alien.texture_size[1],
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": [b.to_json() for b in alien.bones],
        }],
    }


def swing(amplitude: float, length: float, phase: int = 1) -> dict:
    half = round(length / 2, 3)
    return {"0.0": [amplitude * phase, 0, 0], str(half): [-amplitude * phase, 0, 0], str(length): [amplitude * phase, 0, 0]}


def build_animations(alien: Alien) -> dict:
    names = {b.name for b in alien.bones}
    idle: dict = {
        "body": {"rotation": {"0.0": [0, 0, 0], "1.0": [1.5, 0, 0], "2.0": [0, 0, 0]}},
        "right_arm": {"rotation": {"0.0": [0, 0, 3], "1.0": [0, 0, 6], "2.0": [0, 0, 3]}},
        "left_arm": {"rotation": {"0.0": [0, 0, -3], "1.0": [0, 0, -6], "2.0": [0, 0, -3]}},
    }
    walk: dict = {
        "right_arm": {"rotation": swing(35, 0.8, 1)},
        "left_arm": {"rotation": swing(35, 0.8, -1)},
        "right_leg": {"rotation": swing(35, 0.8, -1)},
        "left_leg": {"rotation": swing(35, 0.8, 1)},
    }
    if "right_lower_arm" in names:
        walk["right_lower_arm"] = {"rotation": swing(25, 0.8, -1)}
        walk["left_lower_arm"] = {"rotation": swing(25, 0.8, 1)}
        idle["right_lower_arm"] = {"rotation": {"0.0": [0, 0, 5], "1.0": [0, 0, 9], "2.0": [0, 0, 5]}}
        idle["left_lower_arm"] = {"rotation": {"0.0": [0, 0, -5], "1.0": [0, 0, -9], "2.0": [0, 0, -5]}}
    if "tail" in names:
        idle["tail"] = {"rotation": {"0.0": [-10, 0, 0], "1.0": [-14, 8, 0], "2.0": [-10, 0, 0]}}
        walk["tail"] = {"rotation": {"0.0": [-25, -10, 0], "0.4": [-25, 10, 0], "0.8": [-25, -10, 0]}}
    if "flame_crest" in names:
        flicker = {"0.0": [1, 1, 1], "0.25": [1.1, 1.25, 1.1], "0.5": [0.95, 0.9, 0.95], "0.75": [1.05, 1.15, 1.05], "1.0": [1, 1, 1]}
        idle["flame_crest"] = {"scale": flicker}
        walk["flame_crest"] = {"scale": flicker}
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": {"loop": True, "animation_length": 2.0, "bones": {k: v for k, v in idle.items() if k in names}},
            "walk": {"loop": True, "animation_length": 0.8, "bones": {k: v for k, v in walk.items() if k in names}},
        },
    }


def outputs(alien: Alien, rng: random.Random) -> dict[Path, object]:
    base = Path("entity") / "alien"
    return {
        ASSETS / "geo" / base / f"{alien.name}.geo.json": build_geo(alien),
        ASSETS / "animations" / base / f"{alien.name}.animation.json": build_animations(alien),
        ASSETS / "textures" / base / f"{alien.name}.png": build_texture(alien, rng),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Dateien vorhanden sind")
    parser.add_argument("--seed", type=int, default=4242, help="Zufalls-Seed fuer das Texturrauschen")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    rng = random.Random(args.seed)
    files: dict[Path, object] = {}
    for alien in build_aliens():
        files.update(outputs(alien, rng))

    if args.check:
        missing = [p for p in files if not p.is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p.relative_to(ASSETS))
        LOG.info("%d/%d Alien-Dateien vorhanden", len(files) - len(missing), len(files))
        return 1 if missing else 0

    for path, content in files.items():
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(content, Image.Image):
                content.save(path)
            else:
                path.write_text(json.dumps(content, indent=2) + "\n", encoding="utf-8")
        except OSError as exc:
            LOG.error("konnte %s nicht schreiben: %s", path, exc)
            return 1
        LOG.debug("geschrieben: %s", path.relative_to(ASSETS))
    LOG.info("%d Alien-Dateien geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
