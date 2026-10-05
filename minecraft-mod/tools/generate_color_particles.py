#!/usr/bin/env python3
"""Farbmodul-Varianten der Omnitrix-Partikel (Blitz, DNA-Helix) erzeugen.

Die gruenen Sprites werden je Farbmodul umgefaerbt — Helligkeit bleibt, der gruene Anteil wird zur Modulfarbe, der
weisse Anteil bleibt weiss (dieselbe Regel wie BadgeTint fuer das Alien-Abzeichen). Ergebnis:
textures/particle/<partikel>_<farbe>_<i>.png und particles/<partikel>_<farbe>.json.
Farben muessen zu OmnitrixColors.ALL passen (ohne "green", das sind die Originale).

    python3 tools/generate_color_particles.py           # erzeugen
    python3 tools/generate_color_particles.py --check   # nur pruefen, ob alles vorhanden und aktuell ist
"""
import argparse
import json
import logging
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/kingdomomnitrix"
COLORS = {
    "blue": 0x2FA8FF,
    "red": 0xFF3A2A,
    "yellow": 0xFFD21A,
    "purple": 0xB04AFF,
    "white": 0xE8F4FF,
}
PARTICLES = ("omnitrix_flash", "dna_helix")
LOG = logging.getLogger("color_particles")


def recolor(pixel: tuple, rgb: int) -> tuple:
    r, g, b, a = pixel
    value = g / 255.0
    white = min(r, b) / 255.0
    target = ((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)
    out = tuple(min(255, round(t * value + (255 - t) * white)) for t in target)
    return out + (a,)


def frames(name: str) -> list:
    definition = json.loads((ASSETS / "particles" / f"{name}.json").read_text(encoding="utf-8"))
    return [texture.split(":", 1)[1] for texture in definition["textures"]]


def build(check: bool) -> int:
    problems = 0
    for name in PARTICLES:
        sources = frames(name)
        for color, rgb in COLORS.items():
            textures = []
            for index, source in enumerate(sources):
                target_name = f"{name}_{color}_{index}"
                textures.append(f"kingdomomnitrix:{target_name}")
                image = Image.open(ASSETS / "textures/particle" / f"{source}.png").convert("RGBA")
                tinted = Image.new("RGBA", image.size)
                tinted.putdata([recolor(p, rgb) if p[3] else p for p in image.getdata()])
                path = ASSETS / "textures/particle" / f"{target_name}.png"
                if check:
                    if not path.is_file() or list(Image.open(path).convert("RGBA").getdata()) != list(tinted.getdata()):
                        LOG.error("veraltet oder fehlt: %s", path.relative_to(ROOT))
                        problems += 1
                else:
                    tinted.save(path)
            definition = ASSETS / "particles" / f"{name}_{color}.json"
            content = json.dumps({"textures": textures}, indent=2) + "\n"
            if check:
                if not definition.is_file() or definition.read_text(encoding="utf-8") != content:
                    LOG.error("veraltet oder fehlt: %s", definition.relative_to(ROOT))
                    problems += 1
            else:
                definition.write_text(content, encoding="utf-8")
    LOG.info("%s: %d Partikel x %d Farben", "geprueft" if check else "erzeugt", len(PARTICLES), len(COLORS))
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="nur pruefen, nichts schreiben")
    parser.add_argument("-v", "--verbose", action="store_true", help="ausfuehrliche Ausgabe")
    args = parser.parse_args()
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")
    try:
        problems = build(args.check)
    except (OSError, ValueError, KeyError) as error:
        LOG.error("Fehler: %s", error)
        return 1
    if problems:
        LOG.error("%d Probleme — python3 tools/generate_color_particles.py ausfuehren", problems)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
