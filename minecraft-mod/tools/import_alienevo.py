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
import math
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
MAIN_BONES = ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")
ROOT_NAMES = {"armorBody": "body", "armorHead": "head", "armorRightArm": "right_arm", "armorLeftArm": "left_arm",
              "armorRightLeg": "right_leg", "armorLeftLeg": "left_leg"}
TAIL_NAMES = {"TAIL_BASE": "tail_1", "TAIL_LOWMID": "tail_2", "TAIL_HIGHMID": "tail_3", "TAIL_HIGH": "tail_4"}
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
    arm_swing: float = 1.0     # Armschwung relativ zum Spieler (AE-Skript: rotateX(xRot * -k) → 1 - k)
    leg_swing: float = 1.0
    loops: tuple[str, ...] = ()  # AE-Daueranimationen (laufen immer, z. B. XLR8-Schwanz)
    script: str | None = None    # AE-Animationsskript (kubejs_scripts/<script>.js) mit den Faehigkeits-Posen
    poses: tuple[str | None, ...] = ()  # je Faehigkeits-Slot der AE-Posen-Name (registerForPower) oder None
    sprint: tuple[str, str] | None = None  # AE-Animationen beim Sprinten an/aus (XLR8-Visier)
    parts: tuple[str, ...] = ()  # weitere AE-Modelle, die fest dazugehoeren (Stinkfly: Beinpaar, Fluegel)
    variables: tuple[tuple[str, str], ...] = ()  # Textur-Variablen (#TIMER, #X …) → fester Wert (Grundzustand)
    drop: str | None = None  # zusaetzliche Requisiten-Knochen (Regex), die nur Faehigkeiten zeigen
    form: tuple[str, str, str] | None = None  # Zweitform: (AE-Modell, Wurzelknochen, unser Kuerzel), z. B. Cannonbolts Kugel
    # AE-Aliens ohne Uniform-Modelle (ein Modell, Texturebenen je „watch_type“-Bedingung): unsere AE-Uniform → watch_type
    watch: tuple[tuple[str, str], ...] = ()
    # Flug-/Schwimmhaltung: AE-Animation (Datei:Name), deren Endpose der Renderer in der Luft/im Wasser einblendet
    flight_pose: str | None = None


ALIENS = {
    # Faehigkeiten: fire_blast, fire_burst (→ AE Feuer-Nova), flame_boost (→ AE Feuer-Surfen)
    "heatblast": Spec("1", "pyronite", "pyronite.json", 1.1, "heat", "#FF6A00", arm_swing=0.8, leg_swing=0.6,
                      script="pyronite", poses=(None, "alienevo/nova", "alienevo/surf")),
    # dash_strike (→ Sprungtritt), blur_dodge (→ Gleiten), rapid_strikes (Spielerschlag)
    "xlr8": Spec("4", "kineceleran", "kineceleran.json", 1.1, "fast", "#1E90FF",
                 loops=("xlr8.json:animation.xlr8.tail",), script="kineceleran",
                 poses=("alienevo_aliens/kick_dash", "skate", None),
                 sprint=("xlr8.json:animation.xlr8.mask_on", "xlr8.json:animation.xlr8.mask_off")),
    # ground_slam (→ Erdschlag), throw (→ Faustschlag), mighty_leap
    "four_arms": Spec("6", "tetramand", "tetramand.json", 2.0, "heavy", "#C0392B", extra="tetramand_arms",
                      script="tetramand", poses=("earth/smash", "tetramand/punch", None)),
    # crystal_volley (→ Kristallstacheln)
    "diamondhead": Spec("3", "petrosapien", "petrosapien.json", 1.35, "heavy", "#2ECC71", arm_swing=0.6, leg_swing=0.6,
                        script="petrosapien", poses=("diamond/spikes",)),
    "grey_matter": Spec("5", "galvan", "galvan.json", 0.25, "small", "#95A5A6", arm_swing=0.6, leg_swing=0.6),
    # Groessen aus den AE-Pehkui-Befehlen (Augenhoehe/Hitbox): Wildmutt geduckt 0,8, Upgrade 1,2, Ripjaws/Ghostfreak 1,1
    "wildmutt": Spec("2", "vulpimancer", "vulpimancer.json", 0.85, "fast", "#E8892B", script="vulpimancer"),
    # Augen/Schwanz-Leerlauf und Fluegelschlag laufen dauernd (Stinkfly schwebt)
    "stinkfly": Spec("7", "lepidopterran", "lepidopterran.json", 1.0, "small", "#8DB33A", script="lepidopterran",
                     parts=("lepidopterran_legs", "lepidopterran_wings"),
                     loops=("stinkfly.animation.json:animation.stinkfly.idle", "stinkfly.animation.json:animation.stinkfly.flight")),
    # Landform (Beine): #TIMER 0 — die Schwanzform im Wasser blendet AE per Maske um
    "ripjaws": Spec("8", "piscciss_volann", "piscciss_volann.json", 1.1, "heavy", "#7FA9B5", script="piscciss_volann",
                    variables=(("TIMER", "0"),), loops=("ripjaws.animation.json:animation.ripjaws.swim_slow",)),
    # Fluessig-Pfuetze und Kampf-Keulen zeigt AE nur bei Faehigkeiten
    "upgrade": Spec("9", "galvanic_mechamorph", "galvanic_mechamorph.json", 1.2, "heavy", "#39FF14",
                    script="galvanic_mechamorph", drop=r"liquid|mace|spikes_"),
    # ein Modell; Ebenen „prototype“ (classic) und „recal“ (evo/ultimate, Alien-Force-Look); Fluegel/Umhang aus flight_on
    "jetray": Spec("34", "aerophibian", "aerophibian.json", 1.0, "fast", "#E72D2D",
                   watch=(("prototype", "prototype"), ("default", "recal"), ("10k", "recal")),
                   loops=("jetray.animation.json:animation.jetray.idle",),
                   flight_pose="jetray.animation.json:animation.jetray.flight_on"),
    # #X 0 = sichtbar (1 = AE-Unsichtbarkeit)
    # Kugelform als eigenes Modell (cannonbolt_ball), die eingeklappte Kugel im Koerper faellt weg; Groesse 1,33 wie AE
    "cannonbolt": Spec("11", "arburian_pelarota", "arburian_pelarota.json", 1.33, "heavy", "#F2C230",
                       script="arburian_pelarota", drop=r"^BALL$", form=("arburian_pelarota_ball", "BALL", "ball")),
    "ghostfreak": Spec("10", "ectonurite", "ectonurite.json", 1.1, "small", "#C9C3D6", script="ectonurite",
                       variables=(("X", "0"),),
                       loops=("ectonurite.animation.json:animation.ghostfreak.idle", "ectonurite.animation.json:animation.ghostfreak.eye")),
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


def _model_of(layer: dict) -> str:
    model = layer.get("model", {})
    return model.get("base", "") if isinstance(model, dict) else str(model)


def _layer_belongs(layer: dict, model: str) -> bool:
    """Ebene gehoert genau zu diesem AE-Modell (nicht zu Fluegeln/Beinen/Ego-Armen mit gleichem Artnamen)."""
    base = _model_of(layer)
    return base.endswith(f"/{model}_#UNIFORM.geo.json") or base.endswith(f"/{model}.geo.json")


def _watch_matches(layer: dict, spec: Spec, ae_uniform: str) -> bool:
    """Ebene mit „alienevo:watch_type“-Bedingung nur fuer die zugeordnete Uniform (Spec.watch)."""
    wanted = dict(spec.watch).get(ae_uniform, ae_uniform)
    for cond in layer.get("conditions", []):
        if cond.get("type") == "alienevo:watch_type" and cond.get("value") != wanted:
            return False
    return True


def _resolve(path: str, spec: Spec, ae_uniform: str, frame: int) -> str:
    path = path.replace("#UNIFORM", ae_uniform).replace("#I", str(frame))
    for name, value in spec.variables:
        path = path.replace(f"#{name}", value)
    return path


def glow_frames(jar: Jar, spec: Spec) -> int:
    """Anzahl Glut-Frames (AE: Ebene mit #I, alle 2 Ticks naechster Frame, 8 Frames) — nur Ebenen des Hauptmodells."""
    for layer in layers_of(jar, spec):
        tex = layer.get("texture")
        if (isinstance(tex, dict) and "/aliens/" in tex["base"] and "#I" in tex["base"]
                and _layer_belongs(layer, spec.species)):
            frames = 0
            while jar.has(jar.asset(_resolve(tex["base"], spec, "default", frames))):
                frames += 1
            return max(1, frames)
    return 1


def _alpha_mask(img: Image.Image, mask: Image.Image) -> Image.Image:
    """Palladium alpha_mask: weisse Maskenpixel blenden aus, schwarze lassen stehen."""
    mask = mask.resize(img.size, Image.NEAREST)
    out = img.copy()
    px, mpx = out.load(), mask.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            mr, mg, mb, ma = mpx[x, y]
            if a and ma and (mr + mg + mb) > 382:
                px[x, y] = (r, g, b, 0)
    return out


def build_texture(jar: Jar, spec: Spec, ae_uniform: str, palettes: dict, frame: int = 0,
                  model: str | None = None) -> tuple[Image.Image, Image.Image]:
    """Farbtextur (alle Ebenen inkl. Glow) und Leuchtmaske (nur Glow-Ebenen) einer Uniform und eines AE-Modells."""
    model = model or spec.species
    color = glow = None
    # Leuchtebenen zuletzt (im Spiel emissiv ueber der Haut)
    for layer in sorted(layers_of(jar, spec), key=lambda l: l.get("render_type") == "glow"):
        tex = layer.get("texture")
        if isinstance(tex, str):  # Ebene ohne Transformer (Jetray)
            tex = {"base": tex}
        if ("/aliens/" not in tex.get("base", "") or not _layer_belongs(layer, model)
                or not _watch_matches(layer, spec, ae_uniform)):
            continue
        if model != spec.species and "#I" in tex["base"]:
            continue  # Bewegungsunschaerfe-Bilder der Zusatzmodelle (Stinkfly-Fluegelschlag) — die Animation schlaegt

        name = jar.asset(_resolve(tex["base"], spec, ae_uniform, frame))
        if not jar.has(name):
            LOG.debug("Ebene fehlt: %s", name)
            continue
        variables = tex.get("variables", {})
        mapping = {}
        mask = None
        for transformer in tex.get("transformers", []):
            if transformer.get("type") == "palladium:alpha_mask":
                mask_name = jar.asset(_resolve(transformer["mask"], spec, ae_uniform, frame))
                mask = jar.image(mask_name) if jar.has(mask_name) else None
                continue
            if transformer.get("type") != "palladium:color":
                continue
            var = transformer["color"].strip("#")
            if TOKEN[ae_uniform] not in var:
                continue
            value = palette_color(palettes, spec.number, variables.get(var, {}).get("property", ""))
            if value:
                mapping[transformer["filter"].lstrip("#").lower()] = value.lower()
        img = recolor(jar.image(name), mapping)
        if mask is not None:
            img = _alpha_mask(img, mask)
        is_glow = "glow" in tex["base"] or layer.get("render_type") == "glow"
        color = img if color is None else _over(color, img)
        if is_glow:
            glow = img if glow is None else _over(glow, img)
    if color is None:
        raise ValueError(f"{model}/{ae_uniform}: keine Textur-Ebene gefunden")
    return color, glow if glow is not None else Image.new("RGBA", color.size, (0, 0, 0, 0))


def _stack(color: Image.Image, glow: Image.Image, part: tuple[Image.Image, Image.Image],
           units: tuple[float, float], k: tuple[float, float]) -> tuple[Image.Image, Image.Image]:
    """Textur eines Zusatzmodells (in dessen Einheiten) unter die bisherige Textur haengen, gleiche Pixeldichte."""
    pcolor, pglow = part
    target = (max(1, round(units[0] * k[0])), max(1, round(units[1] * k[1])))
    sheet = Image.new("RGBA", (max(color.width, target[0]), color.height + target[1]), (0, 0, 0, 0))
    sheet.paste(color, (0, 0))
    sheet.alpha_composite(pcolor.resize(target, Image.NEAREST), (0, color.height))
    gsheet = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
    gsheet.paste(glow, (0, 0))
    gsheet.alpha_composite(pglow.resize(target, Image.NEAREST), (0, color.height))
    return sheet, gsheet


def _texture_bases(jar: Jar, spec: Spec, model: str) -> set[str]:
    return {layer["texture"]["base"] for layer in layers_of(jar, spec)
            if isinstance(layer.get("texture"), dict) and _layer_belongs(layer, model)}


def _over(base: Image.Image, top: Image.Image) -> Image.Image:
    result = base.copy()
    result.alpha_composite(top.resize(base.size, Image.NEAREST))
    return result


def _with_badge(color: Image.Image, glow: Image.Image, badge: tuple[dict, Image.Image, Image.Image],
                k: tuple[float, float]) -> tuple[Image.Image, Image.Image]:
    """Abzeichen-Pixel in Alien-Texturdichte unter die Alien-Textur haengen (UVs: transform_bones/_shift_uv)."""
    bgeo, bcolor, bglow = badge
    bw, bh = bgeo["description"].get("texture_width", 16), bgeo["description"].get("texture_height", 16)
    target = (max(1, round(bw * k[0])), max(1, round(bh * k[1])))
    sheet = Image.new("RGBA", (max(color.width, target[0]), color.height + target[1]), (0, 0, 0, 0))
    sheet.paste(color, (0, 0))
    sheet.alpha_composite(bcolor.resize(target, Image.NEAREST), (0, color.height))
    gsheet = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
    gsheet.paste(glow, (0, 0))
    gsheet.alpha_composite(bglow.resize(target, Image.NEAREST), (0, color.height))
    return sheet, gsheet


def badge_parts(jar: Jar, spec: Spec, state: str = "default") -> tuple[dict, Image.Image, Image.Image] | None:
    """Omnitrix-Abzeichen (prototype): Geometrie, Farbtextur, Leuchttextur. state „default“ (gruen) oder
    „timeout“ (AE: rot, bei uns Warnblinken kurz vor Ablauf)."""
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
    glow = jar.image(f"assets/alienevo/textures/models/badge/badge_glow_{state}_prototype.png")
    if state == "default":
        glow = recolor(glow, BADGE_GLOW)
    return jar.json(geo_name)["minecraft:geometry"][0], _over(color, glow), glow


def transform_bones(geo: dict, extra: dict | None, badge: dict | None, badge_v: float, scale_uv: tuple[float, float],
                    parts: list[tuple[str, dict, float | None]] | None = None, drop: str | None = None) -> list[dict]:
    """AE-Knochen auf unsere Namen abbilden, Requisiten entfernen, Zusatzmodelle und Abzeichen anhaengen.

    parts: (Kuerzel, Geometrie, V-Versatz) — V-Versatz None = teilt sich die Haupttextur, sonst liegt seine Textur
    ab dieser Hoehe (Einheiten der Haupttextur) im zusammengesetzten Bild."""
    bones = [b for b in geo["bones"]]
    by_name = {b["name"]: b for b in bones}
    extra_drop = re.compile(drop, re.I) if drop else None

    def dropped(bone: dict, pool: dict[str, dict] | None = None) -> bool:
        pool = by_name if pool is None else pool
        while bone is not None:
            if DROP.search(bone["name"]) or (extra_drop and extra_drop.search(bone["name"])):
                return True
            bone = pool.get(bone.get("parent", ""))
        return False

    bones = [deepcopy(b) for b in bones if not dropped(b)]
    rename: dict[str, str] = {}
    roots = ROOT_NAMES
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
        forearm = find(ae_arm, r"forearm|_lower|armrotate[45]|armlow", bones)
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
    # Hierarchie wie in AE: Kopf, Rumpf, Arme und Beine haengen einzeln an root und folgen im Spiel je dem
    # passenden Teil des Spielermodells (Vanilla-Pose, wie Palladium/GeckoLib-Ruestung)
    for b in bones:
        if b["name"] in MAIN_BONES:
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
                    b["parent"] = "root"
            keep += sub
        result += keep

    for tag, part, dv in parts or ():
        pool = {b["name"]: b for b in part["bones"]}
        for b in part["bones"]:
            if dropped(b, pool) or (b["name"] in ROOT_NAMES and not b.get("cubes")):
                continue  # leere Wurzeln (armorX) — Kinder haengen direkt am passenden Koerperteil
            nb = deepcopy(b)
            nb["name"] = f"{tag}_{b['name']}"
            parent = b.get("parent")
            nb["parent"] = (ROOT_NAMES.get(b["name"], "body") if not parent
                            else ROOT_NAMES[parent] if parent in ROOT_NAMES and not pool[parent].get("cubes")
                            else f"{tag}_{parent}")
            if dv is not None:
                for cube in nb.get("cubes", []):
                    _shift_uv(cube, dv, (1.0, 1.0))
            result.append(nb)

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
        if not jar.has(geo_name):  # ein Modell fuer alle Uniformen (Jetray)
            geo_name = f"assets/alienevo/geo/aliens/alien_{spec.number}/{spec.species}.geo.json"
        geo = jar.json(geo_name)["minecraft:geometry"][0]
        extra = None
        if spec.extra:
            extra_name = f"assets/alienevo/geo/aliens/alien_{spec.number}/{spec.extra}_{ae}.geo.json"
            extra = jar.json(extra_name)["minecraft:geometry"][0] if jar.has(extra_name) else None
        frames = glow_frames(jar, spec)
        color, glow = build_texture(jar, spec, ae, palettes)
        desc = geo["description"]
        tw, th = desc.get("texture_width", 64), desc.get("texture_height", 64)
        kx, ky = color.width / tw, color.height / th
        # Zusatzmodelle (Stinkfly-Beine/-Fluegel): eigene Textur unter die Haupttextur haengen, sonst mitbenutzen
        part_geos: list[tuple[str, dict, float | None]] = []
        stacked: list[tuple[tuple[Image.Image, Image.Image], tuple[float, float], float]] = []
        main_textures = _texture_bases(jar, spec, spec.species)
        th_parts = th
        for part_name in spec.parts:
            part_file = next((f for f in (f"assets/alienevo/geo/aliens/alien_{spec.number}/{part_name}_{ae}.geo.json",
                                          f"assets/alienevo/geo/aliens/alien_{spec.number}/{part_name}.geo.json") if jar.has(f)), None)
            if part_file is None:
                raise ValueError(f"{name}: Zusatzmodell {part_name} fehlt fuer {ae}")
            pgeo = jar.json(part_file)["minecraft:geometry"][0]
            tag = part_name.replace(spec.species + "_", "")
            if _texture_bases(jar, spec, part_name) <= main_textures:
                part_geos.append((tag, pgeo, None))
                continue
            pw = pgeo["description"].get("texture_width", 64)
            ph = pgeo["description"].get("texture_height", 64)
            stacked.append((build_texture(jar, spec, ae, palettes, model=part_name), (pw, ph), th_parts))
            part_geos.append((tag, pgeo, th_parts))
            tw = max(tw, pw)
            th_parts += ph
        for part_tex, units, _ in stacked:
            color, glow = _stack(color, glow, part_tex, units, (kx, ky))
        th = th_parts
        if extra:
            # zweites Modell teilt sich die Textur (gleiche Groesse in AE)
            ew, eh = extra["description"].get("texture_width", tw), extra["description"].get("texture_height", th)
            if (ew, eh) != (tw, th):
                LOG.warning("%s: Armmodell-Textur %sx%s weicht ab (%sx%s)", name, ew, eh, tw, th)
        badge_dv = th
        scale_uv = (1.0, 1.0)
        if badge:
            bgeo = badge[0]
            bw, bh = bgeo["description"].get("texture_width", 16), bgeo["description"].get("texture_height", 16)
            th_new, tw_new = th + bh, max(tw, bw)
        else:
            th_new, tw_new = th, tw
        # je Glut-Frame und Abzeichen-Zustand eine Textur: <name>[_<uniform>][_f<i>][_warn].png (+ _glowmask)
        for frame in range(frames):
            fcolor, fglow = (color, glow) if frame == 0 else build_texture(jar, spec, ae, palettes, frame)
            if frame:
                for part_tex, units, _ in stacked:
                    fcolor, fglow = _stack(fcolor, fglow, part_tex, units, (kx, ky))
            for state, tag in (("default", ""), ("timeout", "_warn")):
                parts = badge_parts(jar, spec, state) if badge else None
                c, g = (_with_badge(fcolor, fglow, parts, (kx, ky)) if parts else (fcolor, fglow))
                stem = f"{name}{suffix}{f'_f{frame}' if frame else ''}{tag}"
                files[base / f"{stem}.png"] = c
                # immer schreiben (auch leer): die Leuchtebene sucht je Textur ihre Maske
                files[base / f"{stem}_glowmask.png"] = g
                if frame == 0 and not tag:
                    sheet_color = c
                if not badge:
                    break
        bones = transform_bones(geo, extra, badge[0] if badge else None, badge_dv, scale_uv, part_geos, spec.drop)
        new_geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.kingdomomnitrix.{name}{suffix}", "texture_width": tw_new,
                            "texture_height": th_new, "visible_bounds_width": 4, "visible_bounds_height": 4.5,
                            "visible_bounds_offset": [0, 1.75, 0]},
            "bones": bones}]}
        files[geo_dir / f"{name}{suffix}.geo.json"] = new_geo
        files[base / f"{name}{suffix}_arms.png"] = arm_skin(bones, sheet_color, (tw_new, th_new))
        if spec.form:
            files[geo_dir / f"{name}{suffix}_{spec.form[2]}.geo.json"] = form_geo(jar, spec, name, suffix, (tw_new, th_new))
        if index == 0:
            classic_bones = bones
    files[ASSETS / "animations" / "entity" / "alien" / f"{name}.animation.json"] = build_animations(jar, name, spec, classic_bones)
    files[ASSETS / "alien_render" / f"{name}.json"] = {
        "scale": spec.scale, "uniforms": list(UNIFORMS), "uniform_models": True,
        # Hauptknochen folgen der Spielerpose (wie AE); Schwung-Faktoren aus den AE-Animationsskripten
        "vanilla_pose": True, "arm_swing": spec.arm_swing, "leg_swing": spec.leg_swing,
        # Faehigkeits-Posen je Slot (aus dem AE-Skript, siehe parse_poses); null = nur Spielerpose
        "ability_poses": [parse_poses(jar, spec.script).get(p) if spec.script and p else None for p in spec.poses],
        # Glut-Frames (alle 2 Ticks, wie AE) und Warn-Texturen (Abzeichen rot) — Dateinamen siehe build()
        "glow_frames": glow_frames(jar, spec), "warn_textures": badge is not None,
        # Endpose der AE-Fluganimation (Knochen → Drehung/Lage/Groesse), im Renderer in der Luft/im Wasser eingeblendet
        **({"flight_pose": flight_pose(jar, spec, classic_bones)} if spec.flight_pose else {}),
        "source": "Alien Evolution (Habb and Stephen)"}
    return files


def form_geo(jar: Jar, spec: Spec, name: str, suffix: str, texture: tuple[float, float]) -> dict:
    """Zweitform (z. B. Kugel): nur der Teilbaum unter dem Wurzelknochen, Wurzel an root und um die eigene Mitte
    drehbar; gleiche Textur wie der Koerper (AE nutzt dieselbe Bildaufteilung)."""
    model, root, tag = spec.form
    geo = jar.json(f"assets/alienevo/geo/aliens/alien_{spec.number}/{model}.geo.json")["minecraft:geometry"][0]
    by_name = {b["name"]: b for b in geo["bones"]}

    def under(bone: dict) -> bool:
        while bone is not None:
            if bone["name"] == root:
                return True
            bone = by_name.get(bone.get("parent", ""))
        return False

    bones = [deepcopy(b) for b in geo["bones"] if under(b)]
    for b in bones:
        if b["name"] == root:
            b["parent"] = "root"
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.kingdomomnitrix.{name}{suffix}_{tag}", "texture_width": texture[0],
                        "texture_height": texture[1], "visible_bounds_width": 4, "visible_bounds_height": 4,
                        "visible_bounds_offset": [0, 1.0, 0]},
        "bones": [{"name": "root", "pivot": [0, 0, 0]}] + bones}]}


POSE_OPS = re.compile(r"^(set|move|rotate)([XYZ])(Rot)?(Degrees)?$")
PALLADIUM_PARTS = {"head", "chest", "body", "right_arm", "left_arm", "right_leg", "left_leg"}


def _number(expr: str) -> float | None:
    """Konstanter Ausdruck aus dem AE-Skript („-57.5 * -1“, „-2 - 2.5“); alles mit Spielwerten → None."""
    expr = expr.strip()
    if not expr or not re.fullmatch(r"[0-9.+\-*/ ()]+", expr):
        return None
    try:
        return float(eval(expr, {"__builtins__": {}}, {}))  # nur Zahlen und Rechenzeichen (Regex oben)
    except (SyntaxError, ZeroDivisionError, TypeError):
        return None


def _calls(chain: str) -> list[tuple[str, str]]:
    """.name(args) .name(args) … mit verschachtelten Klammern in den Argumenten."""
    calls, i = [], 0
    while True:
        m = re.compile(r"\s*\.(\w+)\(").match(chain, i)
        if not m:
            return calls
        depth, j = 1, m.end()
        while j < len(chain) and depth:
            depth += {"(": 1, ")": -1}.get(chain[j], 0)
            j += 1
        calls.append((m.group(1), chain[m.end():j - 1]))
        i = j


def _strip_first_person(body: str) -> str:
    """Bloecke „if (… builder.isFirstPerson()) { … }“ (nicht „!builder…“) entfernen — nur Third-Person-Posen."""
    out, i = [], 0
    pattern = re.compile(r"if\s*\([^{]*?(?<!!)builder\.isFirstPerson\(\)\)\s*\{")
    while True:
        m = pattern.search(body, i)
        if not m:
            out.append(body[i:])
            return "".join(out)
        out.append(body[i:m.start()])
        depth, j = 1, m.end()
        while j < len(body) and depth:
            depth += {"{": 1, "}": -1}.get(body[j], 0)
            j += 1
        i = j


def parse_poses(jar: Jar, script: str) -> dict[str, dict]:
    """AE-Posen aus einem Palladium-Animationsskript: {name: {"ease": …, "parts": {teil: [[op, achse, wert], …]}}}.

    Palladium-Semantik: set* setzt Lage (Pixel, Modellraum des Spielers) bzw. Drehung absolut, move*/rotate*
    addieren; animate(ease, t) blendet von der normalen Spielerpose zur Zielpose. Operationen mit Spielwerten
    (builder.getModel()…, Math.sin) werden ausgelassen."""
    return parse_poses_text(jar.zip.read(f"assets/alienevo/kubejs_scripts/{script}.js").decode())


def parse_poses_text(text: str) -> dict[str, dict]:
    """Wie {@code parse_poses}, aber direkt aus dem Skripttext (testbar ohne Jar)."""
    poses: dict[str, dict] = {}
    for m in re.finditer(r"registerForPower\('([^']+)'", text):
        start = text.index("{", m.end())
        depth, j = 1, start + 1
        while j < len(text) and depth:
            depth += {"{": 1, "}": -1}.get(text[j], 0)
            j += 1
        body = text[start:j]
        # mehrere Zeitgeber in einem Block (z. B. Faustschlag rechts/links): nur der erste gehoert zur Pose
        timers = [t.start() for t in re.finditer(r"getAnimationTimerAbilityValue|abilityUtil\.isEnabled", body)]
        if len(timers) > 1:
            body = body[:timers[1]]
        body = _strip_first_person(body)
        parts: dict[str, list] = {}
        ease = "in_out_cubic"
        for g in re.finditer(r"builder\.get\('(\w+)'\)", body):
            part = g.group(1)
            if part not in PALLADIUM_PARTS:
                continue
            for name, args in _calls(body[g.end():]):
                if name == "animate":
                    e = args.split(",")[0].strip().strip("'\"")
                    ease = re.sub(r"(?<!^)([A-Z])", r"_\1", e.removeprefix("ease")).lower()
                    continue
                op = POSE_OPS.match(name)
                value = _number(args)
                if not op or value is None:
                    continue
                kind = {"set": "set", "move": "move", "rotate": "rotate"}[op.group(1)]
                if op.group(3) is None and kind == "rotate":
                    kind = "rotate"  # rotateX(rad)
                rot = op.group(3) is not None or kind == "rotate"
                if rot and op.group(4) is None:
                    value = math.degrees(value)
                if not rot:
                    parts.setdefault(part, []).append([kind + "_pos", op.group(2).lower(), round(value, 4)])
                else:
                    parts.setdefault(part, []).append([("set" if kind == "set" else "add") + "_rot", op.group(2).lower(), round(value, 4)])
        if parts:
            poses[m.group(1)] = {"ease": ease, "parts": parts}
    return poses


def ae_bone_name(name: str) -> str:
    """AE-Knochenname in einer AE-Animation → unser Name (wie transform_bones fuer Wurzeln, Schwanz, Kollisionen)."""
    if name in ROOT_NAMES:
        return ROOT_NAMES[name]
    if name in TAIL_NAMES:
        return TAIL_NAMES[name]
    return "ae_" + name if name in RESERVED else name


def _keyframe_end(animation: dict) -> float:
    """Laenge einer AE-Animation ohne animation_length: letzter Schluesselbild-Zeitpunkt (mindestens 1 s)."""
    end = 1.0
    for tracks in animation.get("bones", {}).values():
        for channel in tracks.values():
            if isinstance(channel, dict):
                for key in channel:
                    try:
                        end = max(end, float(key))
                    except ValueError:
                        pass
    return end


def _repeat_tracks(tracks: dict, own: float, repeats: int, total: float) -> dict:
    """Schluesselbilder einer Schleife der Laenge own repeats-mal hintereinander legen (bis total)."""
    if repeats <= 1:
        return tracks
    result = {}
    for channel, frames in tracks.items():
        if not isinstance(frames, dict):
            result[channel] = frames  # fester Wert oder Molang: gilt ohnehin immer
            continue
        timed = []
        for key, value in frames.items():
            try:
                timed.append((float(key), value))
            except ValueError:
                timed = []
                break
        if not timed:
            result[channel] = frames
            continue
        merged = {}
        for k in range(repeats):
            for t, value in timed:
                at = round(t + k * own, 4)
                if at <= total + 1.0e-6:
                    merged[f"{at:g}"] = deepcopy(value)
        result[channel] = dict(sorted(merged.items(), key=lambda item: float(item[0])))
    return result


def build_animations(jar: Jar, name: str, spec: Spec, bones: list[dict]) -> dict:
    """Animationssatz wie AE: Hauptknochen bleiben frei (Spielerpose im Renderer), Daueranimationen aus AE laufen in
    allen Bewegungszustaenden; Verwandeln/Zurueckverwandeln skalieren nur root (Wachsen aus dem Blitz)."""
    present = {b["name"] for b in bones}
    loop_bones: dict[str, dict] = {}
    length = 1.0
    tags = [part.replace(spec.species + "_", "") for part in spec.parts]
    sources = []
    for ref in spec.loops:
        file, anim = ref.split(":", 1)
        source = jar.json(f"assets/alienevo/animations/{file}")["animations"][anim]
        sources.append((source, float(source.get("animation_length") or _keyframe_end(source))))
    length = max([length] + [own for _, own in sources])
    for source, own in sources:
        # kuerzere Schleifen bis zur gemeinsamen Laenge wiederholen (Ghostfreak: 16-s-Augenblick + kurzes Schweben)
        repeats = max(1, math.ceil(length / own - 1.0e-6))
        for bone, tracks in source.get("bones", {}).items():
            # Knochen der Zusatzmodelle tragen ihr Kuerzel (wings_wing_left)
            target = next((c for c in [ae_bone_name(bone)] + [f"{t}_{bone}" for t in tags] if c in present), None)
            if target is None or target in MAIN_BONES:
                continue
            loop_bones.setdefault(target, {}).update(_repeat_tracks(deepcopy(tracks), own, repeats, length))
    loop = {"loop": True, "animation_length": length, "bones": loop_bones}
    stub = gen.Alien(name, (64, 64), [gen.Bone(b["name"], b.get("parent"), tuple(b.get("pivot", (0, 0, 0))))
                                      for b in bones], spec.accent, style=spec.style)
    generated = gen.build_animations(stub)["animations"]
    animations = {key: deepcopy(loop) for key in ("idle", "walk", "run", "jump", "fall")}
    for key in ("transform", "revert"):
        anim = deepcopy(generated[key])
        anim["bones"] = {"root": {k: v for k, v in anim["bones"]["root"].items() if k == "scale"}}
        animations[key] = anim
    # Sprinten: AE-Animation an/aus (XLR8: Visier schliesst sich), sonst leer — der Controller braucht beide Namen
    for key, index in (("sprint_on", 0), ("sprint_off", 1)):
        anim = {"loop": "hold_on_last_frame", "animation_length": 0.05, "bones": {}}
        if spec.sprint:
            file, name_ = spec.sprint[index].split(":", 1)
            source = deepcopy(jar.json(f"assets/alienevo/animations/{file}")["animations"][name_])
            source["bones"] = {ae_bone_name(b): t for b, t in source.get("bones", {}).items()
                               if ae_bone_name(b) in present and ae_bone_name(b) not in MAIN_BONES}
            source["loop"] = "hold_on_last_frame"
            anim = source
        animations[key] = anim
    # Schlag, Treffer und Faehigkeiten zeigt die Spielerpose (Armschwung); die Controller brauchen die Namen trotzdem
    for key in ("attack", "hit", "ability_0", "ability_1", "ability_2"):
        animations[key] = {"loop": "hold_on_last_frame", "animation_length": generated[key]["animation_length"], "bones": {}}
    return {"format_version": "1.8.0", "animations": {k: animations[k] for k in sorted(animations)}}


def _last_vector(track: dict) -> list[float] | None:
    """Letzter Schluesselwert einer AE-Spur ({"vector": …} oder {Zeit: {"vector": …}}), nur Zahlen."""
    if "vector" in track:
        values = track["vector"]
    else:
        frames = sorted(((float(k), v) for k, v in track.items()), key=lambda kv: kv[0])
        if not frames:
            return None
        last = frames[-1][1]
        values = last.get("vector", last.get("post", last)) if isinstance(last, dict) else last
    if not isinstance(values, list) or not all(isinstance(v, (int, float)) for v in values):
        return None
    return [float(v) for v in values]


def flight_pose(jar: Jar, spec: Spec, bones: list[dict]) -> dict:
    """Endpose einer AE-Animation als {knochen: {"rotation"|"position"|"scale": [x, y, z]}} (unsere Knochennamen)."""
    file, anim = spec.flight_pose.split(":", 1)
    source = jar.json(f"assets/alienevo/animations/{file}")["animations"][anim]
    present = {b["name"] for b in bones}
    # umbenannte Knochen (Unterarm/Unterschenkel) ueber gleiche Lage und Wuerfel wiederfinden
    ae_geo = jar.json(f"assets/alienevo/geo/aliens/alien_{spec.number}/{spec.species}.geo.json")["minecraft:geometry"][0]

    def shape(bone: dict) -> tuple:
        return (tuple(bone.get("pivot", ())), tuple((tuple(c.get("origin", ())), tuple(c.get("size", ())))
                                                    for c in bone.get("cubes", [])))
    ours = {shape(b): b["name"] for b in bones}
    renamed = {b["name"]: ours.get(shape(b)) for b in ae_geo["bones"]}
    # Knochen der Daueranimationen setzt GeckoLib jedes Bild neu → Pose dort aufaddieren (Schwanz wedelt weiter)
    looped = set()
    for ref in spec.loops:
        lfile, lanim = ref.split(":", 1)
        looped |= {ae_bone_name(n) for n in jar.json(f"assets/alienevo/animations/{lfile}")["animations"][lanim].get("bones", {})}
    pose: dict[str, dict] = {}
    for bone, tracks in source.get("bones", {}).items():
        target = ae_bone_name(bone)
        if target not in present:
            target = renamed.get(bone)
        if target not in present:
            LOG.warning("%s: Flugpose-Knochen %s nicht gefunden", spec.species, bone)
            continue
        for kind in ("rotation", "position", "scale"):
            if kind in tracks:
                value = _last_vector(tracks[kind])
                if value is not None:
                    pose.setdefault(target, {})[kind] = value
        if target in pose and target in looped:
            pose[target]["add"] = True
    return dict(sorted(pose.items()))


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
