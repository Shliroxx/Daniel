#!/usr/bin/env python3
"""Faehigkeits-Posen fuer alle Alien-Slots ergaenzen (alien_render/<alien>.json, Feld ability_poses).

Vorhandene Posen (aus den Alien-Evolution-Skripten uebernommen) bleiben unveraendert; leere Slots (null oder fehlend)
bekommen eine Pose nach Art der Faehigkeit (Schuss, Schlag, Sprung, Schutz, Bruellen, Grossangriff …). Die Zuordnung
Faehigkeit → Pose steht in ABILITY_POSE; jede Faehigkeit der Datenpakete muss dort vorkommen.
Operationen wie im Pose-System des Clients (AlienPose): [op, achse, wert], Drehungen in Grad (Vanilla-Spielerteile).

    python3 tools/generate_ability_poses.py           # ergaenzen
    python3 tools/generate_ability_poses.py --check   # nur pruefen (CI)
"""
import argparse
import json
import logging
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ALIENS = ROOT / "src/main/resources/data/kingdomomnitrix/kingdomomnitrix/alien"
RENDER = ROOT / "src/main/resources/assets/kingdomomnitrix/alien_render"
SLOTS = 6
LOG = logging.getLogger("ability_poses")


def rot(axis: str, degrees: float) -> list:
    return ["set_rot", axis, degrees]


POSES = {
    # rechter Arm gerade nach vorn (Schuss, Strahl aus der Hand)
    "shoot": {"ease": "out_cubic", "parts": {"right_arm": [rot("x", -90.0), rot("y", -5.0)], "head": [rot("x", 0.0)]}},
    # beide Arme nach vorn (Strahl, Kanone, Bann)
    "beam": {"ease": "out_cubic", "parts": {"right_arm": [rot("x", -90.0), rot("y", -12.0)],
                                            "left_arm": [rot("x", -90.0), rot("y", 12.0)]}},
    # Wurf ueber Kopf
    "throw": {"ease": "out_quad", "parts": {"right_arm": [rot("x", -160.0), rot("z", 10.0)], "chest": [rot("x", -8.0)]}},
    # Schlag/Hieb: Arm schwingt quer, Oberkoerper dreht mit
    "swipe": {"ease": "out_cubic", "parts": {"right_arm": [rot("x", -100.0), rot("y", 35.0)], "chest": [rot("y", -20.0)],
                                             "left_arm": [rot("x", 20.0)]}},
    # Bodenschlag: vorgebeugt, beide Arme nach unten vorn
    "slam": {"ease": "in_out_cubic", "parts": {"chest": [rot("x", 22.0)], "right_arm": [rot("x", -40.0), rot("z", 10.0)],
                                               "left_arm": [rot("x", -40.0), rot("z", -10.0)], "head": [rot("x", 15.0)]}},
    # Klatschen: Arme vorn zusammen
    "clap": {"ease": "in_cubic", "parts": {"right_arm": [rot("x", -90.0), rot("y", 28.0)],
                                           "left_arm": [rot("x", -90.0), rot("y", -28.0)]}},
    # Sprung/Sprint: Arme nach hinten, Beine im Schritt, Oberkoerper vor
    "leap": {"ease": "out_quad", "parts": {"chest": [rot("x", 18.0)], "right_arm": [rot("x", 50.0)], "left_arm": [rot("x", 50.0)],
                                           "right_leg": [rot("x", -30.0)], "left_leg": [rot("x", 30.0)]}},
    # Ausweichen/Gestaltwechsel: geduckt, Arme vor dem Koerper
    "dodge": {"ease": "out_sine", "parts": {"chest": [rot("x", 12.0), rot("y", 15.0)], "right_arm": [rot("x", -40.0), rot("y", -30.0)],
                                            "left_arm": [rot("x", -40.0), rot("y", 30.0)]}},
    # Schutz/Staerkung: Arme vor der Brust gekreuzt
    "guard": {"ease": "in_out_sine", "parts": {"right_arm": [rot("x", -65.0), rot("y", -45.0)],
                                               "left_arm": [rot("x", -65.0), rot("y", 45.0)], "head": [rot("x", 8.0)]}},
    # Konzentration/Analyse: Hand an den Kopf
    "focus": {"ease": "in_out_sine", "parts": {"right_arm": [rot("x", -130.0), rot("y", -20.0)], "head": [rot("x", -10.0)]}},
    # Bruellen/Erschrecken: Kopf hoch, Arme gespreizt
    "roar": {"ease": "out_cubic", "parts": {"head": [rot("x", -28.0)], "chest": [rot("x", -10.0)],
                                            "right_arm": [rot("z", 60.0), rot("x", -20.0)], "left_arm": [rot("z", -60.0), rot("x", -20.0)]}},
    # Grossangriff: Arme hoch gerissen
    "ultimate": {"ease": "in_out_cubic", "parts": {"head": [rot("x", -20.0)], "chest": [rot("x", -6.0)],
                                                   "right_arm": [rot("z", 150.0)], "left_arm": [rot("z", -150.0)]}},
    # Wirbel/Flaeche: Arme waagerecht ausgebreitet
    "spread": {"ease": "in_out_cubic", "parts": {"right_arm": [rot("z", 85.0)], "left_arm": [rot("z", -85.0)], "chest": [rot("y", 25.0)]}},
}

ABILITY_POSE = {
    # Heatblast
    "fire_blast": "shoot", "fire_burst": "beam", "flame_boost": "leap", "inferno_wave": "spread", "flame_shield": "guard",
    "supernova": "ultimate",
    # XLR8
    "dash_strike": "swipe", "blur_dodge": "dodge", "rapid_strikes": "swipe", "cyclone_run": "spread", "time_slip": "focus",
    "lightspeed_barrage": "ultimate",
    # Vierarm
    "ground_slam": "slam", "throw": "throw", "mighty_leap": "leap", "thunder_clap": "clap", "iron_skin": "guard",
    "earthquake": "slam",
    # Diamondhead
    "crystal_volley": "shoot", "crystal_blade": "swipe", "spike_eruption": "slam", "crystal_armor": "guard",
    "crystal_storm": "ultimate",
    # Grey Matter
    "scan": "focus", "weak_spot": "shoot", "scurry": "dodge", "tech_snare": "beam", "jury_rig": "focus", "mastermind": "ultimate",
    # Wildmutt
    "pounce": "leap", "quill_burst": "spread", "feral_roar": "roar", "savage_maul": "swipe", "scent_track": "focus",
    "primal_rampage": "roar",
    # Stinkfly
    "slime_spit": "shoot", "stink_cloud": "spread", "wing_dash": "leap", "slime_bomb": "throw", "updraft": "dodge",
    "toxic_storm": "ultimate",
    # Ripjaws
    "jaw_bite": "swipe", "tidal_dash": "leap", "whirlpool": "spread", "tail_swipe": "swipe", "hydro_heal": "guard",
    "tidal_wave": "ultimate",
    # Upgrade
    "optic_beam": "beam", "liquid_form": "dodge", "tech_upgrade": "focus", "mace_fists": "swipe", "system_override": "guard",
    "plasma_cannon": "beam",
    # Ghostfreak
    "tentacle_lash": "swipe", "phase_shift": "dodge", "haunting_scare": "roar", "possession": "beam", "shadow_step": "dodge",
    "nightmare": "ultimate",
    # Cannonbolt: in der Kugelform zeichnet der Client die Kugel statt des Koerpers — Posen gelten fuer den Anlauf
    "cannonball": "dodge", "shell_guard": "guard", "ball_bounce": "leap", "ricochet": "dodge", "rolling_mode": "dodge",
    "cannonade": "ultimate",
    # Jetray: Strahlen aus Augen und Schwanz, Flug-Manoever
    "neuroshock": "beam", "tail_shock": "spread", "jet_burst": "leap", "strafing_run": "dodge", "slipstream": "focus",
    "neuroshock_storm": "ultimate",
}


def ability_ids(alien_file: Path) -> list:
    data = json.loads(alien_file.read_text(encoding="utf-8"))
    return [slot["type"].split(":", 1)[-1] for slot in data.get("abilities", [])]


def build(check: bool) -> int:
    problems = 0
    for alien_file in sorted(ALIENS.glob("*.json")):
        name = alien_file.stem
        render_file = RENDER / f"{name}.json"
        if not render_file.is_file():
            LOG.error("%s: alien_render fehlt", name)
            problems += 1
            continue
        render = json.loads(render_file.read_text(encoding="utf-8"))
        if not render.get("vanilla_pose"):
            LOG.debug("%s: eigene Animationen, keine Posen", name)
            continue
        poses = list(render.get("ability_poses", []))
        abilities = ability_ids(alien_file)
        poses += [None] * (len(abilities) - len(poses))
        filled = 0
        for index, ability in enumerate(abilities[:SLOTS]):
            if poses[index] is not None:
                continue
            kind = ABILITY_POSE.get(ability)
            if kind is None:
                LOG.error("%s: keine Pose fuer Faehigkeit %s (ABILITY_POSE ergaenzen)", name, ability)
                problems += 1
                continue
            poses[index] = POSES[kind]
            filled += 1
        render["ability_poses"] = poses
        content = json.dumps(render, indent=2, ensure_ascii=False) + "\n"
        if check:
            if render_file.read_text(encoding="utf-8") != content:
                LOG.error("%s: Posen unvollstaendig — python3 tools/generate_ability_poses.py ausfuehren", render_file.relative_to(ROOT))
                problems += 1
        elif filled:
            render_file.write_text(content, encoding="utf-8")
            LOG.info("%s: %d Posen ergaenzt", name, filled)
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
        LOG.error("%d Probleme", problems)
        return 1
    LOG.info("%s", "Posen vollstaendig" if args.check else "fertig")
    return 0


if __name__ == "__main__":
    sys.exit(main())
