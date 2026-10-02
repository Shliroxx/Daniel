#!/usr/bin/env python3
"""Rendert GeckoLib-/Bedrock-Modelle (.geo.json + Textur) offline als Bild — fuer Vergleiche ohne laufendes Spiel.

Orthografische Ansichten in Grundpose (vorn, seitlich, hinten, schraeg), Z-Puffer, einfache Flaechenhelligkeit wie
im Spiel (oben hell, unten dunkel, Seiten mittel). Box-UV und Pro-Flaechen-UV, Bone- und Cube-Rotation, Inflate,
Mirror. Mehrere Modelle nebeneinander im selben Massstab, damit Groessen direkt vergleichbar sind.

    python tools/render_geo.py out.png modell.geo.json:textur.png [modell2.geo.json:textur2.png ...]
        [--views front,side,back,three_quarter] [--scale 8] [--label Name ...] [--glow maske.png]

Benoetigt Pillow und numpy.
"""
from __future__ import annotations

import argparse
import json
import logging
import math
import sys
from dataclasses import dataclass
from pathlib import Path

try:
    import numpy as np
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover - Hinweis fuer den Nutzer
    sys.exit("Pillow und numpy fehlen: pip install pillow numpy")

LOG = logging.getLogger("render_geo")
# Blickrichtungen: Drehung des Modells um y (Grad), damit die gewuenschte Seite zur Kamera zeigt
VIEWS = {"front": 180.0, "back": 0.0, "side": 90.0, "three_quarter": 145.0}
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


@dataclass
class Face:
    corners: np.ndarray  # 4×3, Reihenfolge oben-links, oben-rechts, unten-rechts, unten-links (von aussen gesehen)
    uv: tuple[float, float, float, float]  # u0, v0, u1, v1 in Pixeln (u1 < u0 = gespiegelt)
    shade: float


def rot_matrix(rx: float, ry: float, rz: float) -> np.ndarray:
    """Bedrock/Blockbench-Reihenfolge: erst x, dann y, dann z (Matrix = Rz·Ry·Rx), Grad."""
    x, y, z = (math.radians(a) for a in (rx, ry, rz))
    mx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    my = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    mz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return mz @ my @ mx


def bone_transforms(bones: list[dict]) -> dict[str, tuple[np.ndarray, np.ndarray]]:
    """Weltmatrix (3×3) und Verschiebung je Knochen aus Pivot/Rotation inklusive Eltern."""
    by_name = {b["name"]: b for b in bones}
    cache: dict[str, tuple[np.ndarray, np.ndarray]] = {}

    def resolve(name: str) -> tuple[np.ndarray, np.ndarray]:
        if name in cache:
            return cache[name]
        bone = by_name[name]
        pivot = np.array(bone.get("pivot", [0, 0, 0]), dtype=float)
        # Bedrock speichert x/y-Rotation mit umgekehrtem Vorzeichen gegenueber der Darstellung
        rx, ry, rz = bone.get("rotation", [0, 0, 0])
        local = rot_matrix(-rx, -ry, rz)
        # p' = R (p - pivot) + pivot
        offset = pivot - local @ pivot
        parent = bone.get("parent")
        if parent and parent in by_name:
            pm, po = resolve(parent)
            result = (pm @ local, pm @ offset + po)
        else:
            result = (local, offset)
        cache[name] = result
        return result

    for b in bones:
        resolve(b["name"])
    return cache


def cube_faces(cube: dict, tex_w: int, tex_h: int) -> list[Face]:
    ox, oy, oz = cube["origin"]
    w, h, d = cube["size"]
    inf = cube.get("inflate", 0.0)
    x0, y0, z0 = ox - inf, oy - inf, oz - inf
    x1, y1, z1 = ox + w + inf, oy + h + inf, oz + d + inf
    # Ecken je Flaeche (von aussen gesehen: oben-links, oben-rechts, unten-rechts, unten-links); Bedrock-Front = north (-z)
    corners = {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "west": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
        "down": [(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)],
    }
    uvs: dict[str, tuple[float, float, float, float]] = {}
    uv = cube.get("uv", [0, 0])
    mirror = cube.get("mirror", False)
    if isinstance(uv, list):
        u, v = uv
        fw, fh, fd = math.floor(w), math.floor(h), math.floor(d)
        boxes = {
            "east": (u, v + fd, fd, fh), "north": (u + fd, v + fd, fw, fh), "west": (u + fd + fw, v + fd, fd, fh),
            "south": (u + 2 * fd + fw, v + fd, fw, fh), "up": (u + fd, v, fw, fd), "down": (u + fd + fw, v, fw, fd),
        }
        if mirror:
            boxes["east"], boxes["west"] = boxes["west"], boxes["east"]
        for face, (fu, fv, sw, sh) in boxes.items():
            if face == "down":
                uvs[face] = (fu, fv + sh, fu + sw, fv)
            else:
                uvs[face] = (fu, fv, fu + sw, fv + sh)
            if mirror:
                a, b, c, e = uvs[face]
                uvs[face] = (c, b, a, e)
    else:
        for face, spec in uv.items():
            fu, fv = spec["uv"]
            sw, sh = spec.get("uv_size", [0, 0])
            uvs[face] = (fu, fv, fu + sw, fv + sh)
    faces = []
    for face, (u0, v0, u1, v1) in uvs.items():
        if abs(u1 - u0) < 1e-6 and abs(v1 - v0) < 1e-6:
            continue
        faces.append(Face(np.array(corners[face], dtype=float), (u0, v0, u1, v1), SHADE[face]))
    # Cube-eigene Rotation um den Cube-Pivot
    if "rotation" in cube:
        rx, ry, rz = cube["rotation"]
        m = rot_matrix(-rx, -ry, rz)
        p = np.array(cube.get("pivot", [0, 0, 0]), dtype=float)
        for f in faces:
            f.corners = (f.corners - p) @ m.T + p
    return faces


def load_texture(spec: str) -> np.ndarray:
    """Eine Textur oder mehrere Ebenen uebereinander („haut.png+uniform.png+glow.png“)."""
    layers = [Image.open(p).convert("RGBA") for p in spec.split("+")]
    base = layers[0].copy()
    for layer in layers[1:]:
        base.alpha_composite(layer.resize(base.size, Image.NEAREST))
    return np.asarray(base, dtype=float)


def load_model(geo_spec: str, tex_spec: str) -> tuple[list[Face], np.ndarray]:
    """Ein oder mehrere Modelle mit gemeinsamer Textur („koerper.geo.json+arme.geo.json“)."""
    tex = load_texture(tex_spec)
    faces: list[Face] = []
    for geo_path in geo_spec.split("+"):
        faces += load_geo(Path(geo_path), tex)
    return faces, tex


def load_geo(geo_path: Path, tex: np.ndarray) -> list[Face]:
    data = json.loads(geo_path.read_text())
    geo = data["minecraft:geometry"][0]
    desc = geo["description"]
    tw, th = desc.get("texture_width", tex.shape[1]), desc.get("texture_height", tex.shape[0])
    sx, sy = tex.shape[1] / tw, tex.shape[0] / th
    bones = geo.get("bones", [])
    transforms = bone_transforms(bones)
    faces = []
    for bone in bones:
        m, o = transforms[bone["name"]]
        for cube in bone.get("cubes", []):
            for f in cube_faces(cube, tw, th):
                f.corners = f.corners @ m.T + o
                u0, v0, u1, v1 = f.uv
                f.uv = (u0 * sx, v0 * sy, u1 * sx, v1 * sy)
                faces.append(f)
    return faces


def render(faces: list[Face], tex: np.ndarray, yaw: float, scale: float, size: tuple[int, int], ground: float) -> Image.Image:
    """Orthografisch: Modell um y drehen, x nach rechts, y nach oben, Tiefe +z zur Kamera."""
    width, height = size
    color = np.zeros((height, width, 4), dtype=float)
    depth = np.full((height, width), -1e9)
    r = rot_matrix(0, yaw, 0)
    th, tw = tex.shape[:2]
    for f in faces:
        c = f.corners @ r.T
        sxy = np.stack([width / 2 + c[:, 0] * scale, ground - c[:, 1] * scale], axis=1)
        u0, v0, u1, v1 = f.uv
        uv = np.array([(u0, v0), (u1, v0), (u1, v1), (u0, v1)], dtype=float)
        for tri in ((0, 1, 2), (0, 2, 3)):
            p = sxy[list(tri)]
            z = c[list(tri), 2]
            t = uv[list(tri)]
            minx, maxx = int(max(0, math.floor(p[:, 0].min()))), int(min(width - 1, math.ceil(p[:, 0].max())))
            miny, maxy = int(max(0, math.floor(p[:, 1].min()))), int(min(height - 1, math.ceil(p[:, 1].max())))
            if minx > maxx or miny > maxy:
                continue
            den = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            a = ((p[1, 1] - p[2, 1]) * (xs - p[2, 0]) + (p[2, 0] - p[1, 0]) * (ys - p[2, 1])) / den
            b = ((p[2, 1] - p[0, 1]) * (xs - p[2, 0]) + (p[0, 0] - p[2, 0]) * (ys - p[2, 1])) / den
            g = 1 - a - b
            inside = (a >= -1e-6) & (b >= -1e-6) & (g >= -1e-6)
            if not inside.any():
                continue
            zz = a * z[0] + b * z[1] + g * z[2]
            uu = a * t[0, 0] + b * t[1, 0] + g * t[2, 0]
            vv = a * t[0, 1] + b * t[1, 1] + g * t[2, 1]
            ui = np.clip(np.floor(uu).astype(int), 0, tw - 1)
            vi = np.clip(np.floor(vv).astype(int), 0, th - 1)
            texel = tex[vi, ui]
            ys_i = (ys - 0.5).astype(int)
            xs_i = (xs - 0.5).astype(int)
            mask = inside & (texel[..., 3] > 8) & (zz > depth[ys_i, xs_i])
            if not mask.any():
                continue
            depth[ys_i[mask], xs_i[mask]] = zz[mask]
            shaded = texel[mask].copy()
            shaded[:, :3] *= f.shade
            color[ys_i[mask], xs_i[mask]] = shaded
    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGBA")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("out", type=Path)
    parser.add_argument("models", nargs="+", help="geo.json[+geo2.json]:textur.png[+ebene.png][:massstab]")
    parser.add_argument("--views", default="front,side,back,three_quarter")
    parser.add_argument("--scale", type=float, default=8.0, help="Bildpunkte pro Modell-Pixel")
    parser.add_argument("--label", action="append", default=[], help="Beschriftung je Modell")
    parser.add_argument("--background", default="#B8C4CC")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    views = [v for v in args.views.split(",") if v]
    for v in views:
        if v not in VIEWS:
            LOG.error("unbekannte Ansicht %s (moeglich: %s)", v, ", ".join(VIEWS))
            return 2
    loaded = []
    for spec in args.models:
        parts = spec.split(":")
        try:
            faces, tex = load_model(parts[0], parts[1])
        except (OSError, KeyError, ValueError, IndexError) as exc:
            LOG.error("%s nicht lesbar: %s", spec, exc)
            return 1
        model_scale = float(parts[2]) if len(parts) > 2 else 1.0
        for f in faces:
            f.corners = f.corners * model_scale
        loaded.append((faces, tex))
        LOG.debug("%s: %d Flaechen", parts[0], len(faces))

    top = max(max(f.corners[:, 1].max() for f in faces) for faces, _ in loaded)
    cell_w = int(48 * args.scale)
    cell_h = int((top + 6) * args.scale)
    ground = cell_h - 3 * args.scale
    sheet = Image.new("RGBA", (cell_w * len(views), (cell_h + 18) * len(loaded)), args.background)
    draw = ImageDraw.Draw(sheet)
    for row, (faces, tex) in enumerate(loaded):
        spec = args.models[row].split(":")
        for col, view in enumerate(views):
            img = render(faces, tex, VIEWS[view], args.scale, (cell_w, cell_h), ground)
            sheet.alpha_composite(img, (col * cell_w, row * (cell_h + 18) + 18))
            # Menschen-Massstab: 32 Pixel (2 Bloecke) als Linie
            x = col * cell_w + 6
            y0 = row * (cell_h + 18) + 18 + int(ground)
            draw.line([(x, y0), (x, y0 - int(32 * args.scale))], fill="#30343A", width=3)
        label = args.label[row] if row < len(args.label) else Path(spec[0]).stem
        draw.text((6, row * (cell_h + 18) + 3), f"{label}   (Linie = Spielergroesse 2 Bloecke)", fill="#101418")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(args.out)
    LOG.info("geschrieben: %s", args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
