#!/usr/bin/env python3
"""Importiert die Alien-Modelle von Alien Evolution 1:1 in Kingdom Omnitrix.

Modelle, Texturen und Farben stammen aus Alien Evolution (Habb and Stephen, https://www.curseforge.com/minecraft/mc-mods/alienevo),
verwendet mit Erlaubnis des Autors laut SANTIQ (Fanprojekt) — siehe CREDITS.md. Das AE-Jar liegt NICHT im Repo;
dieses Werkzeug erzeugt daraus die Spieldateien:

* Geometrie je Uniform (classic = AE „prototype“, evo = AE „default“, ultimate = AE „10k“), Knochen auf unsere
  Animations-Knochen abgebildet (body/head/right_arm/right_forearm/right_leg/right_shin/tail_N/…_lower_arm), Faehigkeits-
  Requisiten (Schwert, Schild, Kugel) entfernt, Omnitrix-Abzeichen (prototype) eingebaut.
* Textur je Uniform: AE-Ebenen in der Reihenfolge des Render-Layers, Platzhalterfarben mit den Codex-Standardpaletten
  ersetzt (wie Palladiums Color-Transformer), Glow-Ebene zusaetzlich als Leuchtmaske.
* Ego-Arme (Spieler-Skin-Layout), Darstellungsgroesse aus den AE-Groessen (Pehkui), Animationen mit unserem Generator.

    python tools/import_alienevo.py --jar AlienEvo-1.1.3-fabric.jar      # schreibt nach src/main/resources/...
    python tools/import_alienevo.py --check                               # prueft nur, ob alle Dateien da sind

Benoetigt Pillow.
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import re
import sys
import zipfile
from copy import deepcopy
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - Hinweis fuer den Nutzer
    sys.exit("Pillow fehlt: pip install pillow")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_alien_models as gen  # noqa: E402  (Animationen)

LOG = logging.getLogger("import_alienevo")
ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
# unsere Uniform → AE-Uniform
UNIFORMS = {"classic": "prototype", "evo": "default", "ultimate": "10k"}
TOKEN = {"prototype": "PROTO", "default": "DEFAULT", "10k": "10K"}
# Knochen, die nur fuer Faehigkeiten da sind (in AE per Animation ein-/ausgeblendet)
DROP = re.compile(r"sword|shield|boulder|_ball|ball_", re.I)
RESERVED = {"root", "body", "head", "chest", "right_arm", "left_arm", "right_forearm", "left_forearm", "right_leg",
            "left_leg", "right_shin", "left_shin", "right_lower_arm", "left_lower_arm", "right_lower_forearm",
            "left_lower_forearm", "tail_1", "tail_2", "tail_3", "tail_4", "flame_base"}
BADGE_GLOW = {"ffffff": "B3FF40", "eaeaea": "A7F72E", "cfcfdd": "8ED721"}


@dataclass(frozen=True)
class Spec:
    number: str          # AE-Nummer (alien_N)
    species: str         # AE-Dateiname
    layer: str           # Render-Layer-Datei
    scale: float         # Groesse im Spiel (AE-Pehkui-Groesse)
    style: str           # Animations-Charakter
    accent: str
    extra: str | None = None   # zweites Modell (Vierarm: unteres Armpaar)


ALIENS = {
    "heatblast": Spec("1", "pyronite", "pyronite.json", 1.1, "heat", "#FF6A00"),
    "xlr8": Spec("4", "kineceleran", "kineceleran.json", 1.1, "fast", "#1E90FF"),
    "four_arms": Spec("6", "tetramand", "tetramand.json", 2.0, "heavy", "#C0392B", extra="tetramand_arms"),
    "diamondhead": Spec("3", "petrosapien", "petrosapien.json", 1.35, "heavy", "#2ECC71"),
    "grey_matter": Spec("5", "galvan", "galvan.json", 0.25, "small", "#95A5A6"),
}


class Jar:
    def __init__(self, path: Path) -> None:
        self.zip = zipfile.ZipFile(path)
        self.names = set(self.zip.namelist())

    def has(self, name: str) -> bool:
        return name in self.names

    def json(self, name: str) -> dict:
        return json.loads(self.zip.read(name))

    def image(self, name: str) -> Image.Image:
        return Image.open(io.BytesIO(self.zip.read(name))).convert("RGBA")

    def asset(self, resource: str) -> str:
        namespace, path = resource.split(":", 1)
        return f"assets/{namespace}/{path}"


def codex_palettes(jar: Jar) -> dict[str, list[str]]:
    text = jar.zip.read("addon/alienevo/kubejs_scripts/alien_codex.js").decode()
    return {m.group(1): re.findall(r'"([0-9a-fA-F]{6})"', m.group(2))
            for m in re.finditer(r'global\.(alienevo_\w+)\s*=\s*\[([^\]]*)\]', text)}


def palette_color(palettes: dict[str, list[str]], number: str, prop: str) -> str | None:
    # kineceleran_default_skincolor_palette_1_color_2 → alienevo_4_default_skincolor_palette_1[1]
    m = re.match(r'[a-z_]+?_(default|prototype|10k)_(skincolor_palette|glowcolor|uniformcolor_palette|uniformcolor)_(\d+(?:_ext)?)_color_(\d+)', prop)
    if not m:
        return None
    values = palettes.get(f"alienevo_{number}_{m.group(1)}_{m.group(2)}_{m.group(3)}")
    index = int(m.group(4)) - 1
    return values[index] if values and index < len(values) else None


def recolor(img: Image.Image, mapping: dict[str, str]) -> Image.Image:
    if not mapping:
        return img
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a:
                new = mapping.get(f"{r:02x}{g:02x}{b:02x}")
                if new:
                    px[x, y] = (int(new[0:2], 16), int(new[2:4], 16), int(new[4:6], 16), a)
    return out


def layers_of(jar: Jar, spec: Spec) -> list[dict]:
    data = jar.json(f"assets/alienevo/palladium/render_layers/aliens/alien_{spec.number}/{spec.layer}")
    return data["layers"] if data.get("type") == "palladium:compound" else [data]


def build_texture(jar: Jar, spec: Spec, ae_uniform: str, palettes: dict) -> tuple[Image.Image, Image.Image]:
    """Farbtextur (alle Ebenen inkl. Glow) und Leuchtmaske (nur Glow-Ebenen) einer Uniform."""
    color = glow = None
    # Leuchtebenen zuletzt (im Spiel emissiv ueber der Haut)
    for layer in sorted(layers_of(jar, spec), key=lambda l: l.get("render_type") == "glow"):
        tex = layer.get("texture")
        model = layer.get("model", {})
        model_base = model.get("base", "") if isinstance(model, dict) else str(model)
        if not isinstance(tex, dict) or "/aliens/" not in tex["base"] or "badge" in model_base or spec.species not in model_base:
            continue
        name = jar.asset(tex["base"].replace("#UNIFORM", ae_uniform).replace("#I", "0"))
        if not jar.has(name):
            LOG.debug("Ebene fehlt: %s", name)
            continue
        variables = tex.get("variables", {})
        mapping = {}
        for transformer in tex.get("transformers", []):
            if transformer.get("type") != "palladium:color":
                continue
            var = transformer["color"].strip("#")
            if TOKEN[ae_uniform] not in var:
                continue
            value = palette_color(palettes, spec.number, variables.get(var, {}).get("property", ""))
            if value:
                mapping[transformer["filter"].lstrip("#").lower()] = value.lower()
        img = recolor(jar.image(name), mapping)
        is_glow = "glow" in tex["base"] or layer.get("render_type") == "glow"
        color = img if color is None else _over(color, img)
        if is_glow:
            glow = img if glow is None else _over(glow, img)
    if color is None:
        raise ValueError(f"{spec.species}/{ae_uniform}: keine Textur-Ebene gefunden")
    return color, glow if glow is not None else Image.new("RGBA", color.size, (0, 0, 0, 0))


def _over(base: Image.Image, top: Image.Image) -> Image.Image:
    result = base.copy()
    result.alpha_composite(top.resize(base.size, Image.NEAREST))
    return result


def badge_parts(jar: Jar, spec: Spec) -> tuple[dict, Image.Image, Image.Image] | None:
    """Omnitrix-Abzeichen (prototype) mit Standardfarben: Geometrie, Farbtextur, Leuchttextur."""
    folder = f"assets/alienevo/geo/aliens/alien_{spec.number}"
    geo_name = f"{folder}/badge_prototype.geo.json"
    if not jar.has(geo_name):
        geo_name = f"{folder}/badge.geo.json"
    if not jar.has(geo_name):
        return None
    color = jar.image("assets/alienevo/textures/models/badge/badge_prototype.png")
    for part in ("core_side", "core_top", "dial_inner", "dial_outer"):
        overlay = f"assets/alienevo/textures/models/badge/prototype/{part}/{part}_0.png"
        if jar.has(overlay):
            color = _over(color, jar.image(overlay))
    glow = recolor(jar.image("assets/alienevo/textures/models/badge/badge_glow_default_prototype.png"), BADGE_GLOW)
    return jar.json(geo_name)["minecraft:geometry"][0], _over(color, glow), glow


def transform_bones(geo: dict, extra: dict | None, badge: dict | None, badge_v: float, scale_uv: tuple[float, float]) -> list[dict]:
    """AE-Knochen auf unsere Namen abbilden, Requisiten entfernen, Abzeichen anhaengen."""
    bones = [b for b in geo["bones"]]
    by_name = {b["name"]: b for b in bones}

    def dropped(bone: dict) -> bool:
        while bone is not None:
            if DROP.search(bone["name"]):
                return True
            bone = by_name.get(bone.get("parent", ""))
        return False

    bones = [deepcopy(b) for b in bones if not dropped(b)]
    rename: dict[str, str] = {}
    roots = {"armorBody": "body", "armorHead": "head", "armorRightArm": "right_arm", "armorLeftArm": "left_arm",
             "armorRightLeg": "right_leg", "armorLeftLeg": "left_leg"}
    rename.update({k: v for k, v in roots.items() if any(b["name"] == k for b in bones)})

    def children(name: str, pool: list[dict]) -> list[dict]:
        return [b for b in pool if b.get("parent") == name]

    def find(root: str, pattern: str, pool: list[dict]) -> str | None:
        queue = list(children(root, pool))
        while queue:
            bone = queue.pop(0)
            if re.search(pattern, bone["name"], re.I) and not re.search(r"hand|thumb|shard", bone["name"], re.I):
                return bone["name"]
            queue += children(bone["name"], pool)
        return None

    for side, ae_arm, ae_leg in (("right", "armorRightArm", "armorRightLeg"), ("left", "armorLeftArm", "armorLeftLeg")):
        forearm = find(ae_arm, r"forearm|_lower|armrotate[45]", bones)
        if forearm:
            rename[forearm] = f"{side}_forearm"
        shin = find(ae_leg, r"lower|calf", bones)
        if shin:
            rename[shin] = f"{side}_shin"
    for i, name in enumerate(("TAIL_BASE", "TAIL_LOWMID", "TAIL_HIGHMID", "TAIL_HIGH"), start=1):
        if any(b["name"] == name for b in bones):
            rename[name] = f"tail_{i}"
    # alle anderen AE-Namen, die mit unseren kollidieren, umbenennen
    for b in bones:
        if b["name"] not in rename and b["name"] in RESERVED:
            rename[b["name"]] = "ae_" + b["name"]
    for b in bones:
        b["name"] = rename.get(b["name"], b["name"])
        if "parent" in b:
            b["parent"] = rename.get(b["parent"], b["parent"])
    # Hierarchie: root ← body ← (head, Arme); root ← Beine
    for b in bones:
        if b["name"] in ("head", "right_arm", "left_arm"):
            b["parent"] = "body"
        elif b["name"] in ("body", "right_leg", "left_leg"):
            b["parent"] = "root"
    result = [{"name": "root", "pivot": [0, 0, 0]}] + bones

    if extra:
        extra_bones = [deepcopy(b) for b in extra["bones"] if not dropped(b)]
        names = {b["name"]: b for b in extra_bones}
        keep: list[dict] = []
        for side, ae_arm in (("right", "armorRightArm"), ("left", "armorLeftArm")):
            if ae_arm not in names:
                continue
            sub = [b for b in extra_bones if _under(b, ae_arm, names)]
            forearm = find(ae_arm, r"forearm|_lower", extra_bones)
            mapping = {ae_arm: f"{side}_lower_arm"}
            if forearm:
                mapping[forearm] = f"{side}_lower_forearm"
            for b in sub:
                b["name"] = mapping.get(b["name"], f"lo_{side}_{b['name']}")
                if "parent" in b:
                    b["parent"] = mapping.get(b["parent"], f"lo_{side}_{b['parent']}")
                if b["name"] == f"{side}_lower_arm":
                    b["parent"] = "body"
            keep += sub
        result += keep

    if badge:
        for b in badge["bones"]:
            nb = deepcopy(b)
            nb["name"] = "badge_" + b["name"]
            if b.get("parent"):
                nb["parent"] = "badge_" + b["parent"]
            else:  # Wurzel-Knochen (armorX) haengen am passenden Koerperteil, damit das Abzeichen mitlaeuft
                nb["parent"] = roots.get(b["name"], "body")
            for cube in nb.get("cubes", []):
                _shift_uv(cube, badge_v, scale_uv)
            result.append(nb)
    return result


def _under(bone: dict, root: str, names: dict[str, dict]) -> bool:
    while bone is not None:
        if bone["name"] == root:
            return True
        bone = names.get(bone.get("parent", ""))
    return False


def _shift_uv(cube: dict, dv: float, scale: tuple[float, float]) -> None:
    """Abzeichen-UVs in den angehaengten Bereich unter der Alien-Textur verschieben (Einheiten der Alien-Textur)."""
    sx, sy = scale
    uv = cube.get("uv")
    if isinstance(uv, list):
        cube["uv"] = [uv[0] * sx, uv[1] * sy + dv]
        if sx != 1 or sy != 1:
            # Box-UV skaliert nicht mit — als Pro-Flaechen-UV ausschreiben
            cube["uv"] = _box_to_faces(cube, uv, sx, sy, dv)
    elif isinstance(uv, dict):
        for face in uv.values():
            face["uv"] = [face["uv"][0] * sx, face["uv"][1] * sy + dv]
            if "uv_size" in face:
                face["uv_size"] = [face["uv_size"][0] * sx, face["uv_size"][1] * sy]


def _box_to_faces(cube: dict, uv: list[float], sx: float, sy: float, dv: float) -> dict:
    w, h, d = (abs(v) for v in cube["size"])
    u, v = uv
    boxes = {"east": (u, v + d, d, h), "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h),
             "south": (u + 2 * d + w, v + d, w, h), "up": (u + d, v, w, d), "down": (u + d + w, v + d, w, -d)}
    return {f: {"uv": [x * sx, y * sy + dv], "uv_size": [fw * sx, fh * sy]} for f, (x, y, fw, fh) in boxes.items()}


def arm_skin(geo_bones: list[dict], texture: Image.Image, tex_size: tuple[float, float]) -> Image.Image:
    """Ego-Arme im Spieler-Skin-Layout (128x128): Flaechen des groessten Unterarm-/Arm-Wuerfels hineingestreckt."""
    k = (texture.width / tex_size[0], texture.height / tex_size[1])
    canvas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    for side, (au, av) in (("right", (40, 16)), ("left", (32, 48))):
        cube = _largest_cube(geo_bones, side)
        if cube is None:
            continue
        faces = _face_images(cube, texture, k)
        # Spieler-Arm 4x12x4 bei doppelter Dichte
        layout = {"east": (0, 4, 4, 12), "north": (4, 4, 4, 12), "west": (8, 4, 4, 12), "south": (12, 4, 4, 12),
                  "up": (4, 0, 4, 4), "down": (8, 0, 4, 4)}
        for face, (x, y, w, h) in layout.items():
            img = faces.get(face)
            if img is not None and img.width and img.height:
                canvas.alpha_composite(img.resize((w * 2, h * 2), Image.NEAREST), ((au + x) * 2, (av + y) * 2))
    return canvas


def _largest_cube(bones: list[dict], side: str) -> dict | None:
    names = {b["name"]: b for b in bones}
    best, volume = None, 0.0
    for b in bones:
        if not (_under(b, f"{side}_forearm", names) or _under(b, f"{side}_arm", names)):
            continue
        for cube in b.get("cubes", []):
            w, h, d = (abs(v) for v in cube["size"])
            if w * h * d > volume:
                best, volume = cube, w * h * d
    return best


def _face_images(cube: dict, tex: Image.Image, k: tuple[float, float]) -> dict[str, Image.Image]:
    w, h, d = (abs(v) for v in cube["size"])
    uv = cube.get("uv", [0, 0])
    if isinstance(uv, list):
        u, v = uv
        boxes = {"east": (u, v + d, d, h), "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h),
                 "south": (u + 2 * d + w, v + d, w, h), "up": (u + d, v, w, d), "down": (u + d + w, v, w, d)}
    else:
        boxes = {f: (s["uv"][0], s["uv"][1], *s.get("uv_size", [0, 0])) for f, s in uv.items()}
    result = {}
    for face, (x, y, fw, fh) in boxes.items():
        x0, x1 = sorted((x * k[0], (x + fw) * k[0]))
        y0, y1 = sorted((y * k[1], (y + fh) * k[1]))
        if x1 - x0 >= 1 and y1 - y0 >= 1:
            result[face] = tex.crop((int(x0), int(y0), int(round(x1)), int(round(y1))))
    return result


def build(jar: Jar, name: str, spec: Spec, palettes: dict) -> dict[Path, object]:
    files: dict[Path, object] = {}
    base = ASSETS / "textures" / "entity" / "alien"
    geo_dir = ASSETS / "geo" / "entity" / "alien"
    badge = badge_parts(jar, spec)
    classic_bones: list[dict] = []
    for index, (uniform, ae) in enumerate(UNIFORMS.items()):
        suffix = "" if index == 0 else f"_{uniform}"
        geo_name = f"assets/alienevo/geo/aliens/alien_{spec.number}/{spec.species}_{ae}.geo.json"
        geo = jar.json(geo_name)["minecraft:geometry"][0]
        extra = None
        if spec.extra:
            extra_name = f"assets/alienevo/geo/aliens/alien_{spec.number}/{spec.extra}_{ae}.geo.json"
            extra = jar.json(extra_name)["minecraft:geometry"][0] if jar.has(extra_name) else None
        color, glow = build_texture(jar, spec, ae, palettes)
        desc = geo["description"]
        tw, th = desc.get("texture_width", 64), desc.get("texture_height", 64)
        kx, ky = color.width / tw, color.height / th
        if extra:
            # zweites Modell teilt sich die Textur (gleiche Groesse in AE)
            ew, eh = extra["description"].get("texture_width", tw), extra["description"].get("texture_height", th)
            if (ew, eh) != (tw, th):
                LOG.warning("%s: Armmodell-Textur %sx%s weicht ab (%sx%s)", name, ew, eh, tw, th)
        badge_dv = th
        scale_uv = (1.0, 1.0)
        if badge:
            bgeo, bcolor, bglow = badge
            bw, bh = bgeo["description"].get("texture_width", 16), bgeo["description"].get("texture_height", 16)
            scale_uv = (bcolor.width / bw / kx, bcolor.height / bh / ky)
            scale_uv = (1 / scale_uv[0], 1 / scale_uv[1]) if scale_uv != (1.0, 1.0) else scale_uv
            # Abzeichen-Pixel in Alien-Texturdichte unter die Alien-Textur haengen
            target = (max(1, round(bw * kx)), max(1, round(bh * ky)))
            sheet = Image.new("RGBA", (max(color.width, target[0]), color.height + target[1]), (0, 0, 0, 0))
            sheet.paste(color, (0, 0))
            sheet.alpha_composite(bcolor.resize(target, Image.NEAREST), (0, color.height))
            gsheet = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
            gsheet.paste(glow, (0, 0))
            gsheet.alpha_composite(bglow.resize(target, Image.NEAREST), (0, color.height))
            color, glow = sheet, gsheet
            scale_uv = (1.0, 1.0)
            th_new = th + bh
            tw_new = max(tw, bw)
        else:
            th_new, tw_new = th, tw
        bones = transform_bones(geo, extra, badge[0] if badge else None, badge_dv, scale_uv)
        new_geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.kingdomomnitrix.{name}{suffix}", "texture_width": tw_new,
                            "texture_height": th_new, "visible_bounds_width": 4, "visible_bounds_height": 4.5,
                            "visible_bounds_offset": [0, 1.75, 0]},
            "bones": bones}]}
        files[geo_dir / f"{name}{suffix}.geo.json"] = new_geo
        files[base / f"{name}{suffix}.png"] = color
        # immer schreiben (auch leer): die Leuchtebene sucht je Uniform ihre Maske
        files[base / f"{name}{suffix}_glowmask.png"] = glow
        files[base / f"{name}{suffix}_arms.png"] = arm_skin(bones, color, (tw_new, th_new))
        if index == 0:
            classic_bones = bones
    stub = gen.Alien(name, (64, 64), [gen.Bone(b["name"], b.get("parent"), tuple(b.get("pivot", (0, 0, 0))))
                                       for b in classic_bones], spec.accent, style=spec.style)
    files[ASSETS / "animations" / "entity" / "alien" / f"{name}.animation.json"] = gen.build_animations(stub)
    files[ASSETS / "alien_render" / f"{name}.json"] = {"scale": spec.scale, "uniforms": list(UNIFORMS),
                                                       "uniform_models": True, "source": "Alien Evolution (Habb and Stephen)"}
    return files


def expected_files() -> list[Path]:
    paths = []
    for name in ALIENS:
        for index, uniform in enumerate(UNIFORMS):
            suffix = "" if index == 0 else f"_{uniform}"
            paths += [ASSETS / "geo" / "entity" / "alien" / f"{name}{suffix}.geo.json",
                      ASSETS / "textures" / "entity" / "alien" / f"{name}{suffix}.png"]
        paths += [ASSETS / "animations" / "entity" / "alien" / f"{name}.animation.json",
                  ASSETS / "alien_render" / f"{name}.json"]
    return paths


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", type=Path, help="AlienEvo-Jar (Fabric oder Forge, 1.1.x)")
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle importierten Dateien vorhanden sind")
    parser.add_argument("--only", help="nur dieses Alien (z. B. heatblast)")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    logging.getLogger("PIL").setLevel(logging.WARNING)

    if args.check:
        missing = [p for p in expected_files() if not p.is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p)
        LOG.info("%d/%d importierte Dateien vorhanden", len(expected_files()) - len(missing), len(expected_files()))
        return 1 if missing else 0
    if not args.jar or not args.jar.is_file():
        LOG.error("--jar fehlt oder ist keine Datei")
        return 2
    jar = Jar(args.jar)
    palettes = codex_palettes(jar)
    written = 0
    for name, spec in ALIENS.items():
        if args.only and name != args.only:
            continue
        try:
            files = build(jar, name, spec, palettes)
        except (KeyError, ValueError) as exc:
            LOG.error("%s: %s", name, exc)
            return 1
        for path, content in files.items():
            if content is None:
                if path.exists():
                    path.unlink()
                continue
            path.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(content, Image.Image):
                content.save(path)
            else:
                path.write_text(json.dumps(content, indent=2) + "\n")
            written += 1
        LOG.info("%s importiert", name)
    LOG.info("%d Dateien geschrieben", written)
    return 0


if __name__ == "__main__":
    sys.exit(main())
