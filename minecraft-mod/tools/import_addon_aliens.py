#!/usr/bin/env python3
"""Importiert Rath, Spidermonkey, Brainstorm und Goop aus dem Alien-Evolution-Addon „Omni-Evo: Aliens“
(Modrinth: omni-evo-aliens) in unser Format — mit Erlaubnis der Addon-Autoren (siehe CREDITS.md).

Das Addon ist wie Alien Evolution aufgebaut (Palladium-Knochen armorHead … , Texturen je Uniform, Omnitrix-Abzeichen
als eigenes Modell). Daher nutzt dieses Werkzeug die Bausteine aus tools/import_alienevo.py:

* Knochen: AE-Namen → unsere Namen (transform_bones), Abzeichen und Zusatzmodelle (Goops Anti-Gravitations-Projektor)
  werden angehaengt, ihre Texturen unter die Haupttextur gelegt.
* Texturen: Grundtextur(en) uebereinander (Rath: Fell + Uniform), Leuchtebene (Augen), Abzeichen aus dem AE-Jar
  (badge_prototype, wie bei allen AE-Importen).
* Ego-Arme, Animationssatz (Spielerpose), alien_render.

    python tools/import_addon_aliens.py --addon omni_evo_aliens-1.0.1.jar --ae AlienEvo-1.1.3-fabric.jar
    python tools/import_addon_aliens.py --check        # nur pruefen, ob alle Dateien vorhanden sind (CI, ohne Jars)
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import import_alienevo as imp  # noqa: E402

LOG = logging.getLogger("addon_aliens")
ASSETS = imp.ASSETS
DATA = ASSETS.parent.parent / "data" / "kingdomomnitrix" / "kingdomomnitrix" / "alien"
ROOT = "assets/omni_evo_aliens"
SOURCE = "Omni-Evo: Aliens (Alien-Evolution-Addon, mit Erlaubnis)"


@dataclass(frozen=True)
class AddonSpec:
    geo: str                                  # Modell unter assets/omni_evo_aliens/geo/
    textures: tuple[str, ...]                 # Grundtextur(en) unter textures/models/aliens/, uebereinander
    glow: str | None                          # Leuchtebene (Augen) oder None
    badge: str                                # Abzeichen-Modell (prototype)
    size: float                               # sichtbare Groesse (wie AE „size_change“)
    style: str                                # Animations-Charakter
    accent: str
    parts: tuple[tuple[str, str, str], ...] = ()   # (Kuerzel, Modell, Textur) — eigene Textur, wird untergelegt
    full_glow: bool = False                   # ganzes Alien leuchtet (Goop: im Addon komplett als Leuchtebene)
    arm_swing: float = 1.0
    leg_swing: float = 1.0
    drop: str | None = None                   # Requisiten-Knochen (Regex), die nur Faehigkeiten zeigen
    # Fell kraeftiger faerben (Saettigung, Helligkeit als Faktoren) — nur Pixel im Farbton-Bereich (Grad)
    tint: tuple[float, float, float, float] | None = None


SPECS: dict[str, AddonSpec] = {
    "rath": AddonSpec("rath/rath_prototype", ("alien_30/rath_prototype.png", "alien_30/rath_uniform_prototype.png"),
                      "alien_30/rath_prototype_eye.png", "rath/badge_prototype", 1.25, "heavy", "#E2701F",
                      arm_swing=0.8, leg_swing=0.8),
    "spidermonkey": AddonSpec("spidermonkey/spidermonkey", ("alien_28/spidermonkey_skin_defaultpng.png",),
                              "alien_28/spidermonkey_eye_defaultpng.png", "spidermonkey/badge_prototype", 1.7, "fast",
                              "#2C3FB0", tint=(200.0, 250.0, 2.2, 0.78)),
    "brainstorm": AddonSpec("brainstorm/brainstorm", ("alien_27/brainstorm_default.png",),
                            "alien_27/brainstorm_glow_default.png", "brainstorm/badge_prototype", 1.0, "heavy", "#B06A36",
                            leg_swing=0.6),
    "goop": AddonSpec("goop/goop", ("alien_29/goop_prototype.png",), None, "goop/badge_prototype", 1.0, "small",
                      "#6FD62A", parts=(("device", "goop/gravity_device", "alien_29/gravity_pod.png"),), full_glow=True),
}


# Addon-eigene Wurzelnamen → Palladium-Namen (Brainstorm: Krabbenbeine sind die Laufbeine)
ALIASES = {"armorLeftLegCrab": "armorLeftLeg", "armorRightLegCrab": "armorRightLeg"}


def geo_of(addon: imp.Jar, model: str) -> dict:
    geo = addon.json(f"{ROOT}/geo/{model}.geo.json")["minecraft:geometry"][0]
    for bone in geo["bones"]:
        bone["name"] = ALIASES.get(bone["name"], bone["name"])
        if "parent" in bone:
            bone["parent"] = ALIASES.get(bone["parent"], bone["parent"])
    return geo


def texture_of(addon: imp.Jar, path: str) -> Image.Image:
    return addon.image(f"{ROOT}/textures/models/aliens/{path}").convert("RGBA")


def build(addon: imp.Jar, ae: imp.Jar, name: str, spec: AddonSpec) -> dict[Path, object]:
    base = ASSETS / "textures" / "entity" / "alien"
    geo = geo_of(addon, spec.geo)
    desc = geo["description"]
    tw, th = desc.get("texture_width", 64), desc.get("texture_height", 64)
    color = texture_of(addon, spec.textures[0])
    for extra in spec.textures[1:]:
        color = imp._over(color, texture_of(addon, extra).resize(color.size, Image.NEAREST))
    if spec.tint:
        color = tinted(color, spec.tint)
    glow = (texture_of(addon, spec.glow).resize(color.size, Image.NEAREST) if spec.glow
            else Image.new("RGBA", color.size, (0, 0, 0, 0)))
    if spec.full_glow:
        glow = color.copy()
    # gemeinsame Pixeldichte = die hoechste aller Teile (Goops Projektor ist 4× so fein wie der Koerper; beim
    # Verkleinern wuerde er durchsichtig) — Haupttextur notfalls hochskalieren
    dens = [max(color.width / tw, color.height / th)]
    for _tag, model, tex in spec.parts:
        pdesc = geo_of(addon, model)["description"]
        dens.append(texture_of(addon, tex).width / pdesc.get("texture_width", 16))
    target = max(dens)
    size = (round(tw * target), round(th * target))
    if size != color.size:
        color, glow = color.resize(size, Image.NEAREST), glow.resize(size, Image.NEAREST)
    k = (color.width / tw, color.height / th)
    # Zusatzmodelle mit eigener Textur: Textur unter die Haupttextur, UVs um die bisherige Hoehe verschieben
    part_geos: list[tuple[str, dict, float | None]] = []
    for tag, model, tex in spec.parts:
        pgeo = geo_of(addon, model)
        pw, ph = pgeo["description"].get("texture_width", 16), pgeo["description"].get("texture_height", 16)
        pcolor = texture_of(addon, tex)
        pglow = pcolor.copy() if spec.full_glow else Image.new("RGBA", pcolor.size, (0, 0, 0, 0))
        color, glow = imp._stack(color, glow, (pcolor, pglow), (pw, ph), k)
        part_geos.append((tag, pgeo, th))
        tw = max(tw, pw)
        th += ph
    # Omnitrix-Abzeichen: Modell aus dem Addon, Texturen aus Alien Evolution (prototype, wie alle AE-Importe)
    bgeo = geo_of(addon, spec.badge)
    bcolor = ae.image("assets/alienevo/textures/models/badge/badge_prototype.png")
    for piece in ("core_side", "core_top", "dial_inner", "dial_outer"):
        overlay = f"assets/alienevo/textures/models/badge/prototype/{piece}/{piece}_0.png"
        if ae.has(overlay):
            bcolor = imp._over(bcolor, ae.image(overlay))
    bglow = imp.recolor(ae.image("assets/alienevo/textures/models/badge/badge_glow_default_prototype.png"), imp.BADGE_GLOW)
    badge = (bgeo, imp._over(bcolor, bglow), bglow)
    badge_v = th
    bw, bh = bgeo["description"].get("texture_width", 16), bgeo["description"].get("texture_height", 16)
    badge_top = color.height
    color, glow = imp._with_badge(color, glow, badge, k)
    # Abzeichen-Maske: gruene Leuchtpixel im angehaengten Abzeichen-Streifen (BadgeTint erkennt sonst nur
    # AE-Dichte; Brainstorm hat doppelte Dichte)
    marks = Image.new("RGBA", color.size, (0, 0, 0, 0))
    gpx, mpx = glow.load(), marks.load()
    for y in range(badge_top, color.height):
        for x in range(color.width):
            r, g, b, a = gpx[x, y]
            if a and g >= 60 and g > r + 25 and g > b + 25:
                mpx[x, y] = (255, 255, 255, 255)
    th_new, tw_new = th + bh, max(tw, bw)
    bones = imp.transform_bones(geo, None, bgeo, badge_v, (1.0, 1.0), part_geos, spec.drop)
    new_geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.kingdomomnitrix.{name}", "texture_width": tw_new, "texture_height": th_new,
                        "visible_bounds_width": 4, "visible_bounds_height": 4.5, "visible_bounds_offset": [0, 1.75, 0]},
        "bones": bones}]}
    files: dict[Path, object] = {
        ASSETS / "geo" / "entity" / "alien" / f"{name}.geo.json": new_geo,
        base / f"{name}.png": color,
        base / f"{name}_arms.png": imp.arm_skin(bones, color, (tw_new, th_new)),
        base / f"{name}_badgemask.png": marks if marks.getbbox() is not None else None,
    }
    files[base / f"{name}_glowmask.png"] = glow if glow.getbbox() is not None else None
    aspec = imp.Spec("addon", name, "", spec.size, spec.style, spec.accent)
    files[ASSETS / "animations" / "entity" / "alien" / f"{name}.animation.json"] = imp.build_animations(None, name, aspec, bones)
    files[ASSETS / "alien_render" / f"{name}.json"] = {
        "scale": render_scale(name, spec), "uniforms": ["classic"], "uniform_models": False,
        "vanilla_pose": True, "arm_swing": spec.arm_swing, "leg_swing": spec.leg_swing,
        "ability_poses": [], "glow_frames": 1, "warn_textures": False, "source": SOURCE}
    return files


def tinted(img: Image.Image, tint: tuple[float, float, float, float]) -> Image.Image:
    """Fellfarbe kraeftiger: Pixel im Farbtonbereich [lo, hi] bekommen Saettigung × s und Helligkeit × v
    (Spidermonkey: im Addon blasses Stahlblau, in der Serie kraeftiges Blau)."""
    import colorsys
    lo, hi, s_k, v_k = tint
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.15 and lo <= h * 360 <= hi:
                r2, g2, b2 = colorsys.hsv_to_rgb(h, min(1.0, s * s_k), v * v_k)
                px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def render_scale(name: str, spec: AddonSpec) -> float:
    path = DATA / f"{name}.json"
    data_scale = json.loads(path.read_text(encoding="utf-8")).get("scale", 1.0) if path.is_file() else 1.0
    return round(spec.size / data_scale, 3)


def expected_files() -> list[Path]:
    base = ASSETS / "textures" / "entity" / "alien"
    out = []
    for name in SPECS:
        out += [ASSETS / "geo" / "entity" / "alien" / f"{name}.geo.json", base / f"{name}.png", base / f"{name}_arms.png",
                ASSETS / "animations" / "entity" / "alien" / f"{name}.animation.json", ASSETS / "alien_render" / f"{name}.json"]
    return out


def write(files: dict[Path, object]) -> int:
    count = 0
    for path, content in files.items():
        if content is None:
            if path.exists():
                path.unlink()
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, Image.Image):
            content.save(path)
        else:
            if path.parent.name == "alien_render" and path.is_file():
                # vorhandene Faehigkeits-Posen behalten (generate_ability_poses.py)
                content = dict(content, ability_poses=json.loads(path.read_text(encoding="utf-8")).get("ability_poses", []))
            path.write_text(json.dumps(content, indent=2) + "\n")
        count += 1
    return count


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--addon", type=Path, help="omni_evo_aliens-<version>.jar")
    parser.add_argument("--ae", type=Path, help="AlienEvo-<version>-fabric.jar (Omnitrix-Abzeichen)")
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle importierten Dateien vorhanden sind")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if args.check:
        missing = [p for p in expected_files() if not p.is_file()]
        for path in missing:
            LOG.error("fehlt: %s", path)
        LOG.info("%d/%d Addon-Dateien vorhanden", len(expected_files()) - len(missing), len(expected_files()))
        return 1 if missing else 0
    for jar, label in ((args.addon, "--addon"), (args.ae, "--ae")):
        if not jar or not jar.is_file():
            LOG.error("%s fehlt oder ist keine Datei", label)
            return 2
    addon, ae = imp.Jar(args.addon), imp.Jar(args.ae)
    written = 0
    for name, spec in SPECS.items():
        written += write(build(addon, ae, name, spec))
        LOG.info("%s importiert", name)
    LOG.info("%d Dateien geschrieben", written)
    return 0


if __name__ == "__main__":
    sys.exit(main())
