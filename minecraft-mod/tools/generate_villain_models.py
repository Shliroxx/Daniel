#!/usr/bin/env python3
"""Erzeugt die GeckoLib-Modelle der Ben-10-Schurken (Vilgax, Kevin 11, Forever Knight, Vilgax-Drohne): Geometrie,
Textur, Leuchtmaske und Animationen (idle, walk, attack, shoot, slam, roar) unter
assets/kingdomomnitrix/{geo,animations,textures}/entity/villain/.

Baut auf dem Figuren-Baukasten der NPCs auf (tools/generate_npc_models.py: Knochen, Haut, Stoff, Gesichter) und dem
Ruestungs-Maler (Platten, Zierleisten, Leuchtlinien).

    python tools/generate_villain_models.py           # schreiben
    python tools/generate_villain_models.py --check   # pruefen, ob die Dateien aktuell sind
    python tools/generate_villain_models.py --preview out.png
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_armor_models import C, Palette  # noqa: E402
from generate_npc_models import Bone, Npc, box, build, diamond, humanoid, mirror_bone, octo, track  # noqa: E402

LOG = logging.getLogger("villain_models")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
BASE = Path("entity") / "villain"


def vilgax() -> Npc:
    """Vilgax (Ben 10): gruene Haut, kantiger Kopf mit roten Augen und sechs Gesichtstentakeln, schwarzer Panzeranzug
    mit roten Zierleisten, silberner Brustpanzer mit leuchtendem Emblem, wuchtige Schulterplatten, schwere Handschuhe."""
    skin, armor, red, steel = "5A9A44", "1C1C22", "B01818", "8A909A"
    pal = Palette(armor, "0A0A0E", "3A3A44", armor, red, "FF2A1A", "FFD0C0", "FF2A1A", "FF2A1A")
    npc = Npc("vilgax", pal, humanoid(6.6) + [Bone("tentacles", "head", (0, 25, -4))], "vilgax", skin=skin)
    b = npc.bone("body").cubes
    b += [box(0, 18.0, 0, 11.0, 13.0, 6.4, "plate"), box(0, 20.0, -3.4, 9.0, 7.0, 0.8, "plate", tint=steel),
          diamond(0, 20.2, -3.9, 2.6, 0.4, "glow"), box(0, 20.2, -3.95, 1.0, 1.0, 0.2, "gem"),
          box(0, 12.4, 0, 11.4, 1.6, 6.8, "trim"), box(0, 12.4, -3.45, 2.4, 2.0, 0.2, "glow", seams=False)]
    for side in (-1, 1):
        b += [box(side * 3.8, 18.0, -3.45, 0.6, 11.0, 0.2, "trim", seams=False), box(side * 5.2, 16.0, 0, 0.6, 6.0, 6.6, "trim")]
    b += [box(0, 23.0, 3.4, 8.0, 5.0, 1.0, "plate", tint=steel)]  # Rueckenplatte
    for side, name in ((-1, "right_arm"), (1, "left_arm")):
        if side > 0:
            continue
        a = npc.bone(name).cubes
        a += [box(-8.6, 21.4, 0, 7.0, 3.6, 7.4, "plate", tint=steel), box(-8.6, 23.4, 0, 5.4, 0.8, 5.8, "trim"),
              box(-8.6, 21.4, -3.8, 5.0, 2.0, 0.2, "trim", seams=False)]
        a += [box(-8.4, 16.4, 0, 4.6, 7.0, 4.6, "skin"), box(-8.4, 10.6, 0, 5.4, 5.0, 5.4, "plate"),
              box(-8.4, 11.6, -2.8, 4.0, 0.8, 0.2, "glow", seams=False), box(-8.4, 7.4, 0, 4.4, 2.0, 4.4, "plate", tint="101014")]
    mirror_bone(npc, "right_arm", "left_arm")
    for name, x in (("right_leg", -2.6), ("left_leg", 2.6)):
        npc.bone(name).cubes = [box(x, 7.0, 0, 5.0, 10.0, 5.2, "plate"), box(x, 8.0, -2.75, 3.6, 3.0, 0.4, "trim"),
                                box(x, 1.6, -0.6, 5.4, 3.2, 6.4, "plate", tint="101014"), box(x, 3.3, -0.6, 5.6, 0.4, 6.6, "trim")]
        npc.bone(name).pivot = (x, 12, 0)
    h = npc.bone("head").cubes
    h += [box(0, 29.0, 0, 9.0, 9.0, 9.0, "head"), box(0, 33.8, 0.6, 7.0, 1.0, 7.0, "skin"), box(0, 34.4, 1.2, 4.0, 0.8, 5.0, "skin")]
    for side in (-1, 1):
        h += [box(side * 4.6, 30.0, 1.2, 0.8, 3.0, 3.0, "skin"), box(side * 3.0, 33.2, -2.8, 2.4, 1.0, 2.0, "skin")]
    t = npc.bone("tentacles").cubes
    for i, x in enumerate((-3.0, -1.8, -0.6, 0.6, 1.8, 3.0)):
        length = 4.6 + (0.8 if i in (2, 3) else 0.0)
        tilt = (x * 4.0, 0, -x * 3.0)
        t += [C((x - 0.5, 25.6 - length, -4.9), (1.0, length, 1.0), "skin", rot=tilt, pivot=(x, 25.6, -4.4), seams=False),
              C((x - 0.35, 25.6 - length - 1.4, -4.8), (0.7, 1.6, 0.7), "skin", tint="4A8A38", rot=tilt, pivot=(x, 25.6, -4.4), seams=False)]
    return npc


def kevin() -> Npc:
    """Kevin 11 (Ben 10): schwarzes Langarmshirt mit grauem Streifen, olivgruene Hose, schwarze Haare; der rechte Arm
    hat Stahl absorbiert und ist zu einer stacheligen Keule geworden, Adern aus Energie an Arm und Schulter."""
    shirt, pants, metal = "16161A", "4A5236", "8A929E"
    pal = Palette(metal, "3A4048", "C8D0DA", shirt, "5A626A", "B04AFF", "F0E0FF", "B04AFF", "B04AFF")
    npc = Npc("kevin", pal, humanoid(5.0), "kevin", skin="D8A886")
    b = npc.bone("body").cubes
    b += [box(0, 18.0, 0, 8.4, 12.0, 4.6, "cloth", tint=shirt), box(0, 18.0, -2.35, 8.6, 1.4, 0.2, "cloth", tint="6A6A70"),
          box(0, 12.4, 0, 8.6, 0.8, 4.8, "leather", tint="2A2420")]
    npc.bone("left_arm").cubes = [box(6.2, 18.0, 0, 3.8, 12.0, 3.8, "cloth", tint=shirt), box(6.2, 11.4, 0, 3.4, 1.8, 3.4, "skin")]
    r = npc.bone("right_arm").cubes
    r += [box(-6.4, 20.0, 0, 4.6, 6.0, 4.6, "plate"), box(-6.6, 14.0, 0, 5.4, 6.0, 5.4, "plate"),
          box(-6.8, 9.2, 0, 6.4, 4.6, 6.4, "plate", tint="6A727E"), box(-6.4, 17.0, -2.4, 0.5, 8.0, 0.2, "glow", seams=False)]
    for dx, dz in ((-1, 0), (0, -1), (0, 1), (-1, -1), (-1, 1)):
        r.append(diamond(-6.8 + dx * 3.3, 9.6, dz * 3.3, 1.6, 1.4, "trim"))
    r.append(C((-7.4, 6.2, -0.7), (1.4, 1.6, 1.4), "trim", rot=(0, 45, 0), pivot=(-6.8, 7.0, 0)))
    for name, x in (("right_leg", -1.9), ("left_leg", 1.9)):
        npc.bone(name).cubes = [box(x, 7.0, 0, 4.0, 10.0, 4.0, "cloth", tint=pants), box(x, 6.0, -2.05, 2.0, 2.0, 0.2, "cloth", tint="3A4228"),
                                box(x, 1.0, -0.4, 4.2, 2.0, 4.8, "leather", tint="1A1A1A")]
    h = npc.bone("head").cubes
    h += [box(0, 28.0, 0, 8.0, 8.0, 8.0, "head"), box(0, 32.4, 0.4, 8.4, 1.2, 8.0, "hair", tint="141414"),
          box(0, 29.6, 4.1, 8.4, 6.4, 0.8, "hair", tint="141414"), box(-4.2, 29.8, 1.0, 0.6, 4.6, 6.0, "hair", tint="141414"),
          box(4.2, 29.8, 1.0, 0.6, 4.6, 6.0, "hair", tint="141414"), box(-2.0, 31.6, -4.1, 4.0, 1.8, 0.4, "hair", tint="141414"),
          box(2.4, 31.9, -4.1, 3.0, 1.2, 0.4, "hair", tint="141414")]
    return npc


def forever_knight() -> Npc:
    """Forever Knight (Ben 10): silberne Ritterruestung mit rotem Waffenrock und goldenem Drachen, Topfhelm mit
    leuchtend rotem Sehschlitz, Laser-Lanze in der rechten Hand, Rundschild am linken Arm."""
    steel, red, gold = "B8BEC8", "9A1414", "D0A030"
    pal = Palette(steel, "4A505A", "E8ECF2", "2A2E34", gold, "FF3A2A", "FFE0D0", "FF3A2A", "FF3A2A")
    npc = Npc("forever_knight", pal, humanoid(5.4), "robot")
    b = npc.bone("body").cubes
    b += [box(0, 18.0, 0, 8.8, 12.0, 5.0, "plate"), box(0, 16.0, -2.6, 7.0, 12.0, 0.4, "cloth", tint=red),
          box(0, 16.0, 2.6, 7.0, 12.0, 0.4, "cloth", tint=red), diamond(0, 18.6, -2.85, 2.6, 0.2, "trim"),
          box(0, 18.6, -2.9, 0.6, 3.4, 0.1, "trim"), box(0, 12.4, 0, 9.2, 1.0, 5.4, "leather", tint="3A2A1A"),
          box(0, 12.4, -2.75, 1.4, 1.2, 0.2, "trim")]
    for side in (-1, 1):
        b.append(box(side * 3.4, 11.0, -2.65, 2.4, 4.0, 0.3, "cloth", tint=red))
    r = npc.bone("right_arm").cubes
    r += [box(-6.6, 21.6, 0, 5.4, 3.0, 5.6, "plate"), box(-6.4, 16.0, 0, 3.8, 8.0, 3.8, "plate", tint="9AA0AA"),
          box(-6.4, 11.0, 0, 4.4, 3.0, 4.4, "plate")]
    # Laser-Lanze: Griff in der Faust, lange Spitze nach vorn, rot gluehende Klinge
    r += octo(-6.4, 10.4, -3.0, 1.0, 10.0, "z", "suit") + octo(-6.4, 10.4, -9.0, 2.6, 2.0, "z", "trim") + \
        octo(-6.4, 10.4, -13.0, 1.6, 6.0, "z", "plate") + octo(-6.4, 10.4, -16.6, 0.8, 1.6, "z", "glow")
    r.append(box(-6.4, 10.4, -13.0, 0.3, 0.3, 6.0, "glow", seams=False))
    l = npc.bone("left_arm").cubes
    l += [box(6.6, 21.6, 0, 5.4, 3.0, 5.6, "plate"), box(6.4, 16.0, 0, 3.8, 8.0, 3.8, "plate", tint="9AA0AA"),
          box(6.4, 11.0, 0, 4.4, 3.0, 4.4, "plate")]
    l += [box(8.9, 15.0, 0, 0.8, 9.0, 9.0, "cloth", tint=red), box(9.4, 15.0, 0, 0.4, 3.0, 3.0, "trim"),
          box(8.9, 19.6, 0, 1.0, 0.6, 9.2, "trim"), box(8.9, 10.4, 0, 1.0, 0.6, 9.2, "trim")]
    for name, x in (("right_leg", -2.0), ("left_leg", 2.0)):
        npc.bone(name).cubes = [box(x, 7.0, 0, 4.2, 10.0, 4.2, "plate", tint="9AA0AA"), box(x, 7.6, -2.3, 3.0, 2.4, 0.4, "plate"),
                                box(x, 1.2, -0.5, 4.6, 2.4, 5.4, "plate")]
    h = npc.bone("head").cubes
    h += [box(0, 28.4, 0, 8.6, 8.8, 8.6, "plate"), box(0, 28.6, -4.35, 6.4, 0.9, 0.3, "suit"),
          box(0, 28.6, -4.45, 5.6, 0.5, 0.1, "glow"), box(0, 26.2, -4.35, 0.8, 3.6, 0.3, "suit"),
          box(0, 33.0, 0, 1.2, 1.0, 7.0, "trim")]
    for z in (-2.0, 0.0, 2.0):
        h.append(box(0, 33.8 + (0.6 if z == 0 else 0.0), z, 0.6, 1.4, 1.2, "cloth", tint=red))
    return npc


def vilgax_drone() -> Npc:
    """Vilgax-Bioid-Drohne: schwebender dunkler Kugelkoerper mit rotem Auge, Panzerkranz und vier Tentakeln."""
    pal = Palette("2A2E36", "0E1014", "5A606C", "1A1C22", "6A1A1A", "FF2A1A", "FFD0C0", "FF2A1A", "FF2A1A")
    bones = [Bone("root", None, (0, 0, 0)), Bone("body", "root", (0, 8, 0))] + \
        [Bone(f"tentacle_{i}", "body", (x, 5.6, z)) for i, (x, z) in enumerate(((-2.2, -2.2), (2.2, -2.2), (-2.2, 2.2), (2.2, 2.2)))]
    npc = Npc("vilgax_drone", pal, bones, "robot")
    b = npc.bone("body").cubes
    b += octo(0, 9.0, 0, 7.0, 6.0, "y", "plate") + octo(0, 9.0, 0, 8.0, 1.2, "y", "trim")
    b += octo(0, 9.4, -3.6, 2.6, 0.6, "z", "suit") + octo(0, 9.4, -3.95, 1.6, 0.3, "z", "glow")
    b += [box(0, 12.6, 0, 3.0, 1.2, 3.0, "plate"), box(0, 13.6, 1.2, 0.5, 2.0, 0.5, "trim"), box(0, 14.7, 1.2, 0.8, 0.8, 0.8, "glow")]
    for side in (-1, 1):
        b.append(C((side * 4.4 - 0.4, 8.6, -1.6), (0.8, 1.6, 3.2), "trim", rot=(0, 0, side * -20), pivot=(side * 4.0, 9.4, 0)))
    for i, (x, z) in enumerate(((-2.2, -2.2), (2.2, -2.2), (-2.2, 2.2), (2.2, 2.2))):
        npc.bone(f"tentacle_{i}").cubes = [box(x, 4.0, z, 1.0, 3.2, 1.0, "suit"), box(x * 1.1, 1.6, z * 1.1, 0.8, 2.2, 0.8, "suit"),
                                           box(x * 1.15, 0.2, z * 1.15, 0.6, 0.8, 0.6, "glow")]
    return npc


VILLAINS = {"vilgax": vilgax, "kevin": kevin, "forever_knight": forever_knight, "vilgax_drone": vilgax_drone}


def animations(npc: Npc) -> dict:
    """idle, walk sowie Angriffe: attack (Schlag), shoot (Strahl/Schuss), slam (Bodenstampfer), roar (Phase/Brueller)."""
    names = {b.name for b in npc.bones}

    def anim(length: float, bones: dict, loop: bool) -> dict:
        return {"loop": loop, "animation_length": length, "bones": {k: v for k, v in bones.items() if k in names}}

    idle = {"body": {"rotation": track((0.0, (0, 0, 0)), (1.5, (2, 0, 0)), (3.0, (0, 0, 0)))},
            "head": {"rotation": track((0.0, (0, 0, 0)), (1.5, (0, 8, 0)), (3.0, (0, 0, 0)))},
            "right_arm": {"rotation": track((0.0, (0, 0, 3)), (1.5, (0, 0, 6)), (3.0, (0, 0, 3)))},
            "left_arm": {"rotation": track((0.0, (0, 0, -3)), (1.5, (0, 0, -6)), (3.0, (0, 0, -3)))},
            "tentacles": {"rotation": track((0.0, (-4, 0, 0)), (1.5, (6, 0, 0)), (3.0, (-4, 0, 0)))}}
    for i in range(4):
        phase = i * 0.35
        idle[f"tentacle_{i}"] = {"rotation": track((0.0, (10, 0, 0)), (0.75 + phase / 3, (-12, 0, 6)), (1.5, (10, 0, 0)))}
    if "tentacle_0" in names:
        idle["body"] = {"position": track((0.0, (0, 0, 0)), (0.75, (0, 0.8, 0)), (1.5, (0, 0, 0)))}
    walk: dict = {"body": {"rotation": track((0.0, (0, 3, 0)), (0.5, (0, -3, 0)), (1.0, (0, 3, 0)))}}
    for side, sign in (("right", 1), ("left", -1)):
        walk[f"{side}_arm"] = {"rotation": track((0.0, (22 * sign, 0, 0)), (0.5, (-22 * sign, 0, 0)), (1.0, (22 * sign, 0, 0)))}
        walk[f"{side}_leg"] = {"rotation": track((0.0, (-28 * sign, 0, 0)), (0.5, (28 * sign, 0, 0)), (1.0, (-28 * sign, 0, 0)))}
    attack = {"right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.2, (-150, 10, 0)), (0.45, (-20, -10, 0)), (0.8, (0, 0, 0)))},
              "body": {"rotation": track((0.0, (0, 0, 0)), (0.2, (0, -15, 0)), (0.45, (8, 15, 0)), (0.8, (0, 0, 0)))}}
    shoot = {"right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.25, (-90, 0, 0)), (0.9, (-90, 0, 0)), (1.2, (0, 0, 0)))},
             "head": {"rotation": track((0.0, (0, 0, 0)), (0.25, (-6, 0, 0)), (1.2, (0, 0, 0)))}}
    slam = {"right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-170, 0, -10)), (0.6, (-40, 0, 0)), (1.0, (0, 0, 0)))},
            "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-170, 0, 10)), (0.6, (-40, 0, 0)), (1.0, (0, 0, 0)))},
            "body": {"rotation": track((0.0, (0, 0, 0)), (0.4, (-12, 0, 0)), (0.6, (18, 0, 0)), (1.0, (0, 0, 0)))}}
    roar = {"head": {"rotation": track((0.0, (0, 0, 0)), (0.3, (-25, 0, 0)), (1.2, (-25, 0, 0)), (1.6, (0, 0, 0)))},
            "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.3, (-20, 0, 50)), (1.2, (-20, 0, 50)), (1.6, (0, 0, 0)))},
            "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.3, (-20, 0, -50)), (1.2, (-20, 0, -50)), (1.6, (0, 0, 0)))},
            "tentacles": {"rotation": track((0.0, (0, 0, 0)), (0.3, (-35, 0, 0)), (1.2, (-30, 0, 0)), (1.6, (0, 0, 0)))}}
    if "tentacle_0" in names:
        shoot = {"body": {"rotation": track((0.0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.5, (0, 0, 0)))}}
    return {"format_version": "1.8.0", "animations": {
        "idle": anim(3.0 if "tentacle_0" not in names else 1.5, idle, True),
        "walk": anim(1.0, walk, True),
        "attack": anim(0.8, attack, False),
        "shoot": anim(1.2 if "tentacle_0" not in names else 0.5, shoot, False),
        "slam": anim(1.0, slam, False),
        "roar": anim(1.6, roar, False),
    }}


def png(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def outputs() -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for name, make in VILLAINS.items():
        npc = make()
        geo, color, mask = build(npc)
        geo["minecraft:geometry"][0]["description"]["identifier"] = f"geometry.kingdomomnitrix.villain_{name}"
        files[ASSETS / "geo" / BASE / f"{name}.geo.json"] = (json.dumps(geo, indent=1) + "\n").encode()
        files[ASSETS / "animations" / BASE / f"{name}.animation.json"] = (json.dumps(animations(npc), indent=2) + "\n").encode()
        files[ASSETS / "textures" / BASE / f"{name}.png"] = png(color)
        # nur mit Leuchtpixeln: GeckoLib bricht bei einer leeren Leuchtmaske ab (SafeGlowLayer ueberspringt fehlende)
        if np.asarray(mask)[..., 3].any():
            files[ASSETS / "textures" / BASE / f"{name}_glowmask.png"] = png(mask)
    return files


def preview(out: Path) -> None:
    base = ASSETS / "{}" / BASE
    specs = [f"{str(base).format('geo')}/{n}.geo.json:{str(base).format('textures')}/{n}.png" for n in VILLAINS]
    labels = sum((["--label", n] for n in VILLAINS), [])
    subprocess.run([sys.executable, str(ROOT / "tools" / "render_geo.py"), str(out), *specs, "--views", "front,three_quarter,side",
                    "--scale", "9", "--background", "#2A3038", *labels], check=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Dateien aktuell sind")
    parser.add_argument("--preview", type=Path, help="Vorschaubild schreiben")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    files = outputs()
    if args.check:
        stale = [p for p, data in files.items() if not p.is_file() or p.read_bytes() != data]
        for path in stale:
            LOG.error("veraltet oder fehlt: %s", path.relative_to(ASSETS))
        LOG.info("%d/%d Schurken-Dateien aktuell", len(files) - len(stale), len(files))
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    LOG.info("%d Schurken-Dateien geschrieben", len(files))
    if args.preview:
        preview(args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
