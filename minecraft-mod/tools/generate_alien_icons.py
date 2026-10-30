#!/usr/bin/env python3
"""Erzeugt die Alien-Symbole des Omnitrix aus den echten Alien-Modellen (keine Handzeichnung, keine Platzhalter).

Je Alien (alle mit ``assets/kingdomomnitrix/alien_render/<alien>.json``) zwei 64x64-Bilder unter
``textures/gui/alien/``:

* ``<alien>.png`` — farbiges Symbol: Modell in Dreiviertel-Ansicht, dunkle Kontur, fuer Menues, HUD, Freischaltung.
* ``<alien>_silhouette.png`` — weisse Silhouette (Vorderansicht) mit Innenkante; der Client faerbt sie je
  Omnitrix-Zustand/Farbe ein (Rad, Schnellwahl-Kreis, Hologramm-Stil).

    python tools/generate_alien_icons.py            # schreiben
    python tools/generate_alien_icons.py --check    # pruefen, ob alle Symbole aktuell sind (CI)

Benoetigt Pillow und numpy; nutzt den Offline-Renderer ``tools/render_geo.py``.
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import sys
import tempfile
from pathlib import Path

try:
    import numpy as np
    from PIL import Image, ImageFilter
except ImportError:  # pragma: no cover - Hinweis fuer den Nutzer
    sys.exit("Pillow und numpy fehlen: pip install pillow numpy")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import render_geo  # noqa: E402

LOG = logging.getLogger("generate_alien_icons")
ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
SIZE = 64
MARGIN = 4
SUPERSAMPLE = 4


def resting_geo(geo: Path, name: str) -> dict:
    """Modell im Grundzustand: Kraft-Bauteile, die die Animation „mode_none“ auf Groesse 0 setzt (Diamondheads
    Schwert, Upgrades Pfuetze, Vierarms Felsbrocken), samt Unterknochen entfernen."""
    model = json.loads(geo.read_text(encoding="utf-8"))
    animation = ASSETS / "animations" / "entity" / "alien" / f"{name}.animation.json"
    if not animation.is_file():
        return model
    rest = json.loads(animation.read_text(encoding="utf-8")).get("animations", {}).get("mode_none", {}).get("bones", {})
    hidden = {bone for bone, tracks in rest.items() if tracks.get("scale", {}).get("vector") == [0, 0, 0]}
    if not hidden:
        return model
    bones = model["minecraft:geometry"][0]["bones"]
    parents = {b["name"]: b.get("parent") for b in bones}

    def under(name: str | None) -> bool:
        while name is not None:
            if name in hidden:
                return True
            name = parents.get(name)
        return False
    model["minecraft:geometry"][0]["bones"] = [b for b in bones if not under(b["name"])]
    return model


def render_view(geo: Path, texture: Path, yaw: float) -> Image.Image:
    """Modell (Grundzustand) in grosser Aufloesung rendern, auf den Inhalt zuschneiden."""
    with tempfile.TemporaryDirectory() as tmp:
        rest = Path(tmp) / geo.name
        rest.write_text(json.dumps(resting_geo(geo, geo.name.removesuffix(".geo.json"))), encoding="utf-8")
        faces, tex = render_geo.load_model(str(rest), str(texture))
    canvas = SIZE * SUPERSAMPLE * 3
    image = render_geo.render(faces, tex, yaw, 8.0, (canvas, canvas), canvas * 0.8)
    box = image.getbbox()
    if box is None:
        raise ValueError(f"{geo.name}: leeres Bild")
    return image.crop(box)


def fit(image: Image.Image) -> Image.Image:
    """Seitenverhaeltnis halten, in SIZE - 2*MARGIN einpassen, mittig/unten ausrichten."""
    inner = SIZE - 2 * MARGIN
    scale = min(inner / image.width, inner / image.height)
    small = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.paste(small, ((SIZE - small.width) // 2, SIZE - MARGIN - small.height), small)
    return out


def outline(image: Image.Image, color: tuple[int, int, int, int]) -> Image.Image:
    """1 px Kontur um den Inhalt (Alpha erweitern, Inhalt darueber)."""
    alpha = image.getchannel("A").point(lambda a: 255 if a > 40 else 0)
    grown = alpha.filter(ImageFilter.MaxFilter(3))
    border = Image.new("RGBA", image.size, color)
    border.putalpha(grown)
    border.alpha_composite(image)
    return border


def icon(geo: Path, texture: Path) -> Image.Image:
    return outline(fit(render_view(geo, texture, render_geo.VIEWS["three_quarter"])), (10, 18, 12, 255))


def silhouette(geo: Path, texture: Path) -> Image.Image:
    """Weisse Flaeche, Innenkante etwas dunkler (wirkt im Spiel wie ein Hologramm-Umriss)."""
    shape = fit(render_view(geo, texture, render_geo.VIEWS["front"]))
    alpha = np.asarray(shape.getchannel("A")) > 40
    edge = alpha & ~np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3))).astype(bool)
    out = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    out[alpha] = (205, 205, 205, 230)
    out[edge] = (255, 255, 255, 255)
    return Image.fromarray(out, "RGBA")


def outputs() -> dict[Path, Image.Image]:
    files: dict[Path, Image.Image] = {}
    for render_file in sorted((ASSETS / "alien_render").glob("*.json")):
        name = render_file.stem
        geo = ASSETS / "geo" / "entity" / "alien" / f"{name}.geo.json"
        texture = ASSETS / "textures" / "entity" / "alien" / f"{name}.png"
        if not geo.is_file() or not texture.is_file():
            LOG.warning("%s: Modell oder Textur fehlt, kein Symbol", name)
            continue
        target = ASSETS / "textures" / "gui" / "alien"
        files[target / f"{name}.png"] = icon(geo, texture)
        files[target / f"{name}_silhouette.png"] = silhouette(geo, texture)
    return files


def same(stored: Image.Image, fresh: Image.Image) -> bool:
    """Gleich bis auf Rundungsrauschen anderer Pillow/numpy-Versionen (hoechstens 1 % der Pixel um > 8 verschieden)."""
    if stored.size != fresh.size:
        return False
    diff = np.abs(np.asarray(stored, dtype=np.int16) - np.asarray(fresh, dtype=np.int16)).max(axis=2)
    return float((diff > 8).mean()) <= 0.01


def png_bytes(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Symbole vorhanden und aktuell sind")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    logging.getLogger("PIL").setLevel(logging.WARNING)
    logging.getLogger("render_geo").setLevel(logging.WARNING)
    try:
        files = outputs()
    except (OSError, ValueError, KeyError) as exc:
        LOG.error("%s", exc)
        return 1
    if args.check:
        stale = [p for p, img in files.items() if not p.is_file() or not same(Image.open(p).convert("RGBA"), img)]
        for p in stale:
            LOG.error("fehlt oder veraltet: %s", p.relative_to(ASSETS))
        LOG.info("%d/%d Alien-Symbole aktuell", len(files) - len(stale), len(files))
        return 1 if stale else 0
    for path, image in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png_bytes(image))
    LOG.info("%d Alien-Symbole geschrieben", len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
