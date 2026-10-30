#!/usr/bin/env python3
"""Erzeugt die Kampfanimationen des Spielers fuer playerAnimator.

Ausgabe: assets/kingdomomnitrix/player_animations/combat.json (Bedrock/GeckoLib-Animationsformat,
von playerAnimator direkt gelesen). Jede Animation heisst wie die Aktion, die der Server meldet
(combo_1, combo_2, combo_finisher, air_1, air_2, air_finisher, heavy, dodge, guard).

Die Bewegungen sind Platzhalter (Status PLACEHOLDER) und lassen sich in Blockbench mit dem
"GeckoLib Animation Utils"-Plugin verfeinern. Winkel in Grad, Knochen in snake_case
(head, body, right_arm, left_arm, right_leg, left_leg, torso).

Aufruf aus dem Ordner minecraft-mod/:
    python tools/generate_player_animations.py            # schreiben
    python tools/generate_player_animations.py --check    # nur pruefen
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

LOG = logging.getLogger("player_animations")
TARGET = (Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
          / "player_animations" / "combat.json")

Vec = tuple[float, float, float]


def track(*keys: tuple[float, Vec], easing: str = "easeInOutSine") -> dict:
    """Keyframes {Zeit: {vector, easing}}."""
    return {f"{time:.2f}": {"vector": list(vec), "easing": easing} for time, vec in keys}


def animation(length: float, bones: dict, hold: bool = False) -> dict:
    data = {"animation_length": length, "bones": bones}
    data["loop"] = "hold_on_last_frame" if hold else False
    return data


def slash(length: float, arm_from: Vec, arm_to: Vec, twist: float, airborne: bool) -> dict:
    windup = round(length * 0.3, 2)
    bones = {
        "right_arm": {"rotation": track((0.0, (0, 0, 0)), (windup, arm_from), (length, arm_to))},
        "body": {"rotation": track((0.0, (0, 0, 0)), (windup, (0, -twist, 0)), (length, (0, twist, 0)))},
        "left_arm": {"rotation": track((0.0, (0, 0, 0)), (windup, (-20, 0, -10)), (length, (10, 0, -15)))},
    }
    if airborne:
        bones["right_leg"] = {"rotation": track((0.0, (-35, 0, 0)), (length, (-25, 0, 0)))}
        bones["left_leg"] = {"rotation": track((0.0, (-15, 0, 0)), (length, (-35, 0, 0)))}
    return animation(length, bones)


def build() -> dict:
    animations = {
        "combo_1": slash(0.30, (-110, 0, -20), (20, 0, 30), 15, False),
        "combo_2": slash(0.30, (-90, 30, 60), (-60, -20, -40), 20, False),
        "combo_finisher": animation(0.50, {
            "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.15, (-170, 0, 0)), (0.35, (10, 0, 0)), (0.5, (0, 0, 0)))},
            "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.15, (-150, 0, 0)), (0.35, (0, 0, 0)))},
            "body": {"rotation": track((0.0, (0, 0, 0)), (0.15, (-10, 0, 0)), (0.35, (20, 0, 0)), (0.5, (0, 0, 0)))},
        }),
        "air_1": slash(0.30, (-110, 0, -20), (20, 0, 30), 15, True),
        "air_2": slash(0.30, (-90, 30, 60), (-60, -20, -40), 20, True),
        "air_finisher": animation(0.45, {
            "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.12, (-180, 0, 0)), (0.3, (30, 0, 0)))},
            "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.12, (-160, 0, 0)), (0.3, (20, 0, 0)))},
            "body": {"rotation": track((0.0, (0, 0, 0)), (0.3, (30, 0, 0)))},
            "right_leg": {"rotation": track((0.0, (-40, 0, 0)), (0.3, (0, 0, 0)))},
            "left_leg": {"rotation": track((0.0, (-40, 0, 0)), (0.3, (0, 0, 0)))},
        }),
        "heavy": animation(0.50, {
            "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.2, (-150, -30, -30)), (0.4, (-20, 30, 50)), (0.5, (0, 0, 0)))},
            "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.2, (-130, 20, 20)), (0.4, (-10, 0, 0)))},
            "body": {"rotation": track((0.0, (0, 0, 0)), (0.2, (0, -35, 0)), (0.4, (0, 30, 0)), (0.5, (0, 0, 0)))},
        }),
        "dodge": animation(0.40, {
            "body": {"rotation": track((0.0, (0, 0, 0)), (0.1, (35, 0, 0)), (0.3, (35, 0, 0)), (0.4, (0, 0, 0)))},
            "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.1, (-40, 0, 20)), (0.4, (0, 0, 0)))},
            "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.1, (-40, 0, -20)), (0.4, (0, 0, 0)))},
            "right_leg": {"rotation": track((0.0, (0, 0, 0)), (0.15, (-50, 0, 0)), (0.4, (0, 0, 0)))},
            "left_leg": {"rotation": track((0.0, (0, 0, 0)), (0.15, (-30, 0, 0)), (0.4, (0, 0, 0)))},
        }),
        "guard": animation(0.15, {
            "right_arm": {"rotation": track((0.0, (0, 0, 0)), (0.15, (-80, -30, 0)))},
            "left_arm": {"rotation": track((0.0, (0, 0, 0)), (0.15, (-60, 30, 0)))},
            "body": {"rotation": track((0.0, (0, 0, 0)), (0.15, (8, 0, 0)))},
        }, hold=True),
    }
    return {"format_version": "1.8.0", "animations": animations}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob die Datei vorhanden und aktuell ist")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    content = json.dumps(build(), indent=2) + "\n"
    if args.check:
        if not TARGET.is_file():
            LOG.error("fehlt: %s", TARGET)
            return 1
        if TARGET.read_text(encoding="utf-8") != content:
            LOG.error("veraltet: %s (Generator erneut ausfuehren)", TARGET)
            return 1
        LOG.info("Spieler-Animationen aktuell")
        return 0
    try:
        TARGET.parent.mkdir(parents=True, exist_ok=True)
        TARGET.write_text(content, encoding="utf-8")
    except OSError as exc:
        LOG.error("konnte %s nicht schreiben: %s", TARGET, exc)
        return 1
    LOG.info("%d Animationen nach %s geschrieben", len(build()["animations"]), TARGET.name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
