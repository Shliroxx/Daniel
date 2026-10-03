#!/usr/bin/env python3
"""Erzeugt die GeckoLib-Modelle der NPCs (Yen Sid, Max Tennyson, Clank): Geometrie, Animationen
(idle, walk, talk, wave) und Texturen unter assets/kingdomomnitrix/{geo,animations,textures}/entity/npc/.

Platzhalter-Geometrie (Status PLACEHOLDER), in Blockbench verfeinerbar. Nutzt die Bausteine aus
generate_alien_models.py (gleiche Box-UV-Bemalung).

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_npc_models.py           # schreiben
    python tools/generate_npc_models.py --check   # nur pruefen
"""
from __future__ import annotations

import argparse
import json
import logging
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_alien_models import ASSETS, Alien, Bone, Cube, build_geo, build_texture, rgb  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("npc_models")
SKIN = "#E3B98F"


def human(name: str, body: str, arms: str, legs: str, head_detail: str = "eyes", extra: list[Bone] | None = None,
          texture: tuple[int, int] = (128, 64)) -> Alien:
    """Menschliches Grundgeruest im Spieler-Layout; Zusatzteile liegen im UV-Bereich ab u=64."""
    bones = [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 24, 0), [Cube((-4, 12, -2), (8, 12, 4), (16, 16), body, "shirt")]),
        Bone("head", "body", (0, 24, 0), [Cube((-4, 24, -4), (8, 8, 8), (0, 0), SKIN, head_detail)]),
        Bone("right_arm", "body", (-5, 22, 0), [Cube((-8, 12, -2), (4, 12, 4), (40, 16), arms)]),
        Bone("left_arm", "body", (5, 22, 0), [Cube((4, 12, -2), (4, 12, 4), (40, 16), arms, mirror=True)]),
        Bone("right_leg", "root", (-1.9, 12, 0), [Cube((-3.9, 0, -2), (4, 12, 4), (0, 16), legs)]),
        Bone("left_leg", "root", (1.9, 12, 0), [Cube((-0.1, 0, -2), (4, 12, 4), (0, 16), legs, mirror=True)]),
    ]
    return Alien(name, texture, bones + (extra or []), body)


def yen_sid() -> Alien:
    """Blaue Robe, langer grauer Bart, spitzer Zauberhut mit Sternen."""
    robe, hat = "#2A3F8F", "#2B4FC0"
    return human("yen_sid", robe, robe, robe, extra=[
        Bone("beard", "head", (0, 25, -4), [Cube((-3, 19, -4.5), (6, 7, 1), (64, 40), "#DADADA", "beard")]),
        Bone("hat", "head", (0, 32, 0), [
            Cube((-4.5, 32, -4.5), (9, 3, 9), (64, 8), hat, "stars"),
            Cube((-3.5, 35, -3.5), (7, 4, 7), (64, 24), hat, "stars"),
            Cube((-2.5, 39, -2.5), (5, 4, 5), (100, 0), hat),
            Cube((-1.5, 43, -1.5), (3, 3, 3), (100, 12), hat),
        ]),
    ])


def max_tennyson() -> Alien:
    """Rotes Hawaiihemd mit Blumen, Khaki-Hose, graue Haare."""
    return human("max", "#C0392B", SKIN, "#8B7D5B", extra=[
        Bone("hair", "head", (0, 32, 0), [Cube((-4.5, 29.5, -4.5), (9, 3, 9), (64, 0), "#B8B8B8")]),
    ])


def clank() -> Alien:
    """Kleiner Roboter: grosser Kopf, gruene Augen, Antenne mit roter Lampe."""
    metal, dark = "#A8B0B8", "#5E666E"
    return Alien("clank", (64, 64), [
        Bone("root", None, (0, 0, 0)),
        Bone("body", "root", (0, 5, 0), [Cube((-3, 5, -2), (6, 7, 4), (0, 16), metal, "chest")]),
        Bone("head", "body", (0, 12, 0), [Cube((-4, 12, -3.5), (8, 6, 7), (0, 0), metal, "big_eyes")]),
        Bone("antenna", "head", (0, 18, 0), [Cube((-0.5, 18, -0.5), (1, 4, 1), (30, 0), dark),
                                             Cube((-1, 22, -1), (2, 2, 2), (36, 0), "#E53935")]),
        Bone("right_arm", "body", (-3.5, 11, 0), [Cube((-5, 5, -1), (2, 6, 2), (20, 16), dark)]),
        Bone("left_arm", "body", (3.5, 11, 0), [Cube((3, 5, -1), (2, 6, 2), (20, 16), dark, mirror=True)]),
        Bone("right_leg", "root", (-1.5, 5, 0), [Cube((-2.5, 0, -1), (2, 5, 2), (28, 16), dark)]),
        Bone("left_leg", "root", (1.5, 5, 0), [Cube((0.5, 0, -1), (2, 5, 2), (28, 16), dark, mirror=True)]),
    ], metal)


def paint_details(alien: Alien, img: Image.Image) -> None:
    """Muster, die der allgemeine Maler nicht kennt: Blumenhemd, Bart, Sterne, Brustplatte."""
    rng = random.Random(alien.name)
    width, height = img.size
    for bone in alien.bones:
        for cube in bone.cubes:
            if cube.mirror or cube.front_detail is None:
                continue
            u, v = cube.uv
            w, h, d = cube.size
            fx, fy = u + d, v + d
            box_w, box_h = 2 * (d + w), d + h
            if cube.front_detail == "shirt" and alien.name == "max":
                # Blumen ueber die ganze Box verteilt, ausser auf Ober- und Unterseite
                for _ in range(26):
                    x, y = u + rng.randrange(box_w), v + d + rng.randrange(h)
                    if 0 <= x < width and 0 <= y < height:
                        img.putpixel((x, y), (255, 236, 120, 255) if rng.random() < 0.5 else (255, 255, 255, 255))
            elif cube.front_detail == "shirt" and alien.name == "yen_sid":
                for y in range(fy, fy + h):
                    img.putpixel((fx + w // 2, y), (200, 170, 60, 255))  # Gold-Saum der Robe
            elif cube.front_detail == "beard":
                for x in range(u, u + box_w):
                    for y in range(v, v + box_h):
                        if 0 <= x < width and 0 <= y < height and rng.random() < 0.25:
                            img.putpixel((x, y), (190, 190, 190, 255))
            elif cube.front_detail == "stars":
                for _ in range(6):
                    x, y = u + rng.randrange(box_w), v + d + rng.randrange(h)
                    img.putpixel((x, y), (240, 220, 90, 255))
            elif cube.front_detail == "chest":
                for x in range(fx + 1, fx + w - 1):
                    img.putpixel((x, fy + 2), (70, 78, 86, 255))
                img.putpixel((fx + w // 2, fy + 4), (57, 255, 20, 255))


def track(*keys: tuple[float, tuple[float, float, float]]) -> dict:
    return {f"{t:.2f}": list(v) for t, v in keys}


def build_animations(alien: Alien) -> dict:
    names = {b.name for b in alien.bones}
    idle = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.5, (1.5, 0, 0)), (3.0, (0, 0, 0)))},
            "head": {"rotation": track((0.0, (0, 0, 0)), (1.5, (0, 6, 0)), (3.0, (0, 0, 0)))}}
    if "antenna" in names:
        idle["antenna"] = {"rotation": track((0.0, (0, 0, -5)), (1.5, (0, 0, 5)), (3.0, (0, 0, -5)))}
    walk: dict = {}
    for side, sign in (("right", 1), ("left", -1)):
        walk[f"{side}_arm"] = {"rotation": track((0.0, (25 * sign, 0, 0)), (0.5, (-25 * sign, 0, 0)), (1.0, (25 * sign, 0, 0)))}
        walk[f"{side}_leg"] = {"rotation": track((0.0, (-25 * sign, 0, 0)), (0.5, (25 * sign, 0, 0)), (1.0, (-25 * sign, 0, 0)))}
    talk = {
        "head": {"rotation": track((0.0, (0, 0, 0)), (0.3, (8, 0, 0)), (0.6, (-3, 0, 0)), (0.9, (6, 0, 0)), (1.5, (0, 0, 0)))},
        "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-50, 0, 15)), (1.0, (-40, 0, 25)), (1.5, (0, 0, 0)))},
    }
    wave = {"right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.25, (0, 0, 150)), (0.5, (0, 0, 120)),
                                             (0.75, (0, 0, 150)), (1.0, (0, 0, 120)), (1.3, (0, 0, 0)))}}

    def anim(length: float, bones: dict, loop: bool) -> dict:
        return {"loop": loop, "animation_length": length, "bones": {k: v for k, v in bones.items() if k in names}}

    return {"format_version": "1.8.0", "animations": {
        "idle": anim(3.0, idle, True),
        "walk": anim(1.0, walk, True),
        "talk": anim(1.5, talk, False),
        "wave": anim(1.3, wave, False),
    }}


def outputs(alien: Alien, rng: random.Random) -> dict[Path, object]:
    texture = build_texture(alien, rng)
    paint_details(alien, texture)
    geo = build_geo(alien)
    geo["minecraft:geometry"][0]["description"]["identifier"] = f"geometry.kingdomomnitrix.npc_{alien.name}"
    base = Path("entity") / "npc"
    return {
        ASSETS / "geo" / base / f"{alien.name}.geo.json": geo,
        ASSETS / "animations" / base / f"{alien.name}.animation.json": build_animations(alien),
        ASSETS / "textures" / base / f"{alien.name}.png": texture,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alles vorhanden ist")
    parser.add_argument("--seed", type=int, default=4242)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    rng = random.Random(args.seed)
    files: dict[Path, object] = {}
    for builder in (yen_sid, max_tennyson, clank):
        files.update(outputs(builder(), rng))
    if args.check:
        missing = [p for p in files if not p.is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p.relative_to(ASSETS))
        LOG.info("%d/%d NPC-Dateien vorhanden", len(files) - len(missing), len(files))
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
    LOG.info("%d NPC-Dateien geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
