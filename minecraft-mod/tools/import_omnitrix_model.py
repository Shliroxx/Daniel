#!/usr/bin/env python3
"""Importiert das Omnitrix-Modell (Prototyp, Originalserie) aus Alien Evolution fuer den Arm-Renderer.

Quelle: Alien Evolution (Habb and Stephen), verwendet mit Erlaubnis des Autors laut SANTIQ (Fanprojekt) — CREDITS.md.
Das AE-Jar liegt NICHT im Repo; das Werkzeug erzeugt daraus:

* geo/omnitrix/prototype_omnitrix.geo.json und …_slim.geo.json (Knochen os_omnitrix, core, dial, lights, cylinder,
  arrows/a_*, os_body …) — unveraendert, GeckoLib backt sie automatisch.
* textures/omnitrix/prototype_omnitrix.png: Grundtextur wie AEs Render-Layer im Auslieferungszustand
  (prototype.png + Teil-Ebenen mit Wert -1, Kern-Maske 0) — schwarz/grau/weiss wie in der Originalserie.
* textures/omnitrix/prototype_omnitrix_glow.png: Leuchtschicht (prototype_glow_default + Kern-Maske) als Graustufe
  (Helligkeit = Gruenkanal); unser Renderer faerbt sie im Geraete-Zustand und im Farbmodul — wie AEs drei
  Farbfilter (#b3ff40, #a7f72e, #8ed721), nur stufenlos.

    python3 tools/import_omnitrix_model.py --jar AlienEvo-1.1.3-fabric.jar   # schreiben
    python3 tools/import_omnitrix_model.py --check                           # nur pruefen, ob alles da ist (CI)
"""
from __future__ import annotations

import argparse
import io
import json
import logging
import sys
import zipfile
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow fehlt: pip install pillow")

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/kingdomomnitrix"
AE = "assets/alienevo/"
PROTO = AE + "textures/models/prototype_omnitrix/"
PARTS = ("base_primary", "base_secondary", "base_tertiary", "buttons", "core_side", "core_top", "dial_inner", "dial_outer",
         "tubes")
OUTPUTS = (
    ASSETS / "geo/omnitrix/prototype_omnitrix.geo.json",
    ASSETS / "geo/omnitrix/prototype_omnitrix_slim.geo.json",
    ASSETS / "textures/omnitrix/prototype_omnitrix.png",
    ASSETS / "textures/omnitrix/prototype_omnitrix_glow.png",
)
LOG = logging.getLogger("import_omnitrix_model")


def read_image(jar: zipfile.ZipFile, name: str) -> Image.Image:
    return Image.open(io.BytesIO(jar.read(name))).convert("RGBA")


def alpha_mask(image: Image.Image, mask: Image.Image) -> Image.Image:
    """Wie Palladiums alpha_mask: nur wo die Maske deckt, bleibt das Bild."""
    out = image.copy()
    pixels = out.load()
    m = mask.resize(image.size).load()
    for y in range(image.height):
        for x in range(image.width):
            if m[x, y][3] == 0:
                pixels[x, y] = (0, 0, 0, 0)
    return out


def grayscale_glow(image: Image.Image) -> Image.Image:
    out = image.copy()
    pixels = out.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a:
                pixels[x, y] = (g, g, g, a)
    return out


def png(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def build(jar_path: Path) -> dict[Path, bytes]:
    with zipfile.ZipFile(jar_path) as jar:
        files: dict[Path, bytes] = {}
        for variant in ("prototype_omnitrix", "prototype_omnitrix_slim"):
            geo = json.loads(jar.read(f"{AE}geo/{variant}.geo.json"))
            files[ASSETS / f"geo/omnitrix/{variant}.geo.json"] = (json.dumps(geo, indent=1) + "\n").encode()
        mask = read_image(jar, AE + "textures/models/alpha_masks/prototype/prototype_core_0.png")
        base = read_image(jar, PROTO + "prototype.png")
        for part in PARTS:
            base.alpha_composite(read_image(jar, f"{PROTO}{part}/{part}_-1.png").resize(base.size))
        files[ASSETS / "textures/omnitrix/prototype_omnitrix.png"] = png(alpha_mask(base, mask))
        glow = alpha_mask(read_image(jar, PROTO + "prototype_glow_default.png"), mask)
        files[ASSETS / "textures/omnitrix/prototype_omnitrix_glow.png"] = png(grayscale_glow(glow))
        return files


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--jar", type=Path, help="AlienEvo-Jar (1.1.x)")
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Dateien vorhanden sind")
    parser.add_argument("-v", "--verbose", action="store_true", help="ausfuehrliche Ausgabe")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    if args.check:
        missing = [path for path in OUTPUTS if not path.is_file()]
        for path in missing:
            LOG.error("fehlt: %s (mit --jar importieren)", path.relative_to(ROOT))
        if not missing:
            LOG.info("Omnitrix-Modell vollstaendig (%d Dateien)", len(OUTPUTS))
        return 1 if missing else 0
    if args.jar is None or not args.jar.is_file():
        LOG.error("--jar fehlt oder ist keine Datei")
        return 1
    try:
        files = build(args.jar)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as error:
        LOG.error("Import fehlgeschlagen: %s", error)
        return 1
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        LOG.info("geschrieben: %s", path.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
