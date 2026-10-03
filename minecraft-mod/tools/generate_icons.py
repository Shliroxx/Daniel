#!/usr/bin/env python3
"""Erzeugt die GUI-Symbole von Kingdom Omnitrix (16x16 Pixel-Art).

Jedes Symbol ist ein Abzeichen in der Farbe seines Systems (Held/Keyblade gold-blau, Omnitrix gruen,
Technik blau-orange, Erkunden violett) mit einem 12x12-Motiv aus einem ASCII-Raster. Aufruf aus minecraft-mod/:

    python tools/generate_icons.py            # schreibt nach src/main/resources/assets/kingdomomnitrix/textures/gui/icon/
    python tools/generate_icons.py --check    # prueft nur, ob alle Dateien existieren

Benoetigt Pillow (pip install pillow).
"""
from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - Hinweis fuer den Nutzer
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("icons")
DEFAULT_ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"

Color = tuple[int, int, int, int]


def hexc(value: str, alpha: int = 255) -> Color:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha


# Farben der Systeme (gleich wie UiTheme im Client)
THEMES = {
    "hero": ("4AB8FF", "FFC94A"),
    "combat": ("FF6B5A", "FFD0C8"),
    "keyblade": ("FFC94A", "FFF2C0"),
    "omnitrix": ("5CE65C", "D8FFD0"),
    "tech": ("4AB8FF", "FF9A3C"),
    "exploration": ("C48BFF", "F0E0FF"),
}

# 12x12-Motive: x = Hauptfarbe, h = hell, o = dunkle Kontur, a = Akzent
GLYPHS: dict[str, list[str]] = {
    "sword": [
        ".........hho",
        "........hxxo",
        ".......hxxo.",
        "......hxxo..",
        ".....hxxo...",
        "....hxxo....",
        ".a.hxxo.....",
        ".aaxxo......",
        "..aao.......",
        ".aoaa.......",
        "aoo.a.......",
        "oo..........",
    ],
    "sparkle": [
        ".....h......",
        ".....x......",
        "....hxh.....",
        "....xxx.....",
        "..hxxhxxh...",
        "hxxxhhhxxxh.",
        "..hxxhxxh...",
        "....xxx...a.",
        "....hxh..aaa",
        ".....x....a.",
        ".....h......",
        "............",
    ],
    "flame": [
        ".....h......",
        "....hx......",
        "....xx..h...",
        "...hxxh.x...",
        "..hxxxxxx...",
        "..xxaaxxxh..",
        ".hxaaaaxxx..",
        ".xxaahaaxx..",
        ".xxahhhaxx..",
        ".xxaahaaxx..",
        "..xxaaaxx...",
        "...xxxxx....",
    ],
    "snowflake": [
        ".....h......",
        "...h.x.h....",
        "....hxh.....",
        ".h...x...h..",
        "..x..x..x...",
        "hxxxxhxxxxh.",
        "..x..x..x...",
        ".h...x...h..",
        "....hxh.....",
        "...h.x.h....",
        ".....h......",
        "............",
    ],
    "lightning": [
        "......hxxo..",
        ".....hxxo...",
        "....hxxo....",
        "...hxxo.....",
        "..hxxxxxxo..",
        "...oooxxo...",
        ".....hxo....",
        "....hxo.....",
        "...hxo......",
        "..hxo.......",
        "..xo........",
        "..o.........",
    ],
    "heart": [
        "............",
        "..hhh..hhh..",
        ".hxxxhhxxxh.",
        "hxxhxxxxxxxo",
        "hxhxxxxxxxxo",
        "hxxxxxxxxxxo",
        ".xxxxxxxxxo.",
        "..xxxxxxxo..",
        "...xxxxxo...",
        "....xxxo....",
        ".....xo.....",
        "............",
    ],
    "potion": [
        "....oooo....",
        "....ohho....",
        ".....xx.....",
        "....hxxh....",
        "...hxxxxh...",
        "..hxaaaaxo..",
        "..xaahaaao..",
        "..xaaaaaao..",
        "..xaaaaaao..",
        "..oxaaaaxo..",
        "...oooooo...",
        "............",
    ],
    "omnitrix": [
        "...oooooo...",
        "..oxxxxxxo..",
        ".oxhhhhhhxo.",
        "oxxohhhhoxxo",
        "oxxxohhoxxxo",
        "oxxxxooxxxxo",
        "oxxxxooxxxxo",
        "oxxxohhoxxxo",
        "oxxohhhhoxxo",
        ".oxhhhhhhxo.",
        "..oxxxxxxo..",
        "...oooooo...",
    ],
    "clock": [
        "...oooooo...",
        "..ohhhhhho..",
        ".ohhhhxhhho.",
        "ohhhhhxhhhho",
        "ohhhhhxhhhho",
        "ohhhhhxxxxho",
        "ohhhhhhhhhho",
        "ohhhhhhhhhho",
        ".ohhhhhhhho.",
        "..ohhhhhho..",
        "...oooooo...",
        "............",
    ],
    "cycle": [
        "....xxxx....",
        "..xx....xx..",
        ".x........x.",
        ".x.......xxx",
        "x.........x.",
        "x...........",
        "...........x",
        ".x.........x",
        "xxx.......x.",
        ".x........x.",
        "..xx....xx..",
        "....xxxx....",
    ],
    "arrow_up": [
        ".....hh.....",
        "....hxxo....",
        "...hxxxxo...",
        "..hxxxxxxo..",
        ".hxxxxxxxxo.",
        "....xxxo....",
        "....xxxo....",
        "....xxxo....",
        "....xxxo....",
        "....xxxo....",
        "....oooo....",
        "............",
    ],
    "speed": [
        "............",
        "......hxx...",
        ".......hxx..",
        "aaaa....hxx.",
        "........hxx.",
        "..aaaa.hxxx.",
        ".......hxx..",
        "aaaa..hxx...",
        ".....hxx....",
        "...........",
        "............",
        "............",
    ],
    "wing": [
        "............",
        "hh..........",
        "xhhh........",
        "xxxhhh......",
        ".xxxxhhh....",
        ".xxxxxxhhh..",
        "..xxxxxxxxh.",
        "..oxxxxxxxx.",
        "...oxxoxxo..",
        "....oo.oo...",
        "............",
        "............",
    ],
    "swoosh": [
        "............",
        "........hxx.",
        "......hxxo..",
        ".....hxo....",
        "....hxo.....",
        "...hxo......",
        "..hxo..aa...",
        ".hxo..a..a..",
        ".xo...a..a..",
        ".o.....aa...",
        "............",
        "............",
    ],
    "bolt": [
        "....oooo....",
        "..ooxxxxoo..",
        ".oxxhhhhxxo.",
        ".oxhxooxhxo.",
        "oxhxo..oxhxo",
        "oxhxo..oxhxo",
        "oxhxo..oxhxo",
        "oxhxo..oxhxo",
        ".oxhxooxhxo.",
        ".oxxhhhhxxo.",
        "..ooxxxxoo..",
        "....oooo....",
    ],
    "gear": [
        ".....xx.....",
        "..x.xxxx.x..",
        "..xxxxxxxx..",
        "...xxooxx...",
        ".xxxo..oxxx.",
        "xxxo....oxxx",
        "xxxo....oxxx",
        ".xxxo..oxxx.",
        "...xxooxx...",
        "..xxxxxxxx..",
        "..x.xxxx.x..",
        ".....xx.....",
    ],
    "exp": [
        "............",
        "xxxx.xxxx...",
        "x....x..x...",
        "xxx..xxxx...",
        "x....x......",
        "xxxx.x......",
        "............",
        "........a...",
        ".......aaa..",
        "......aaaaa.",
        "........a...",
        "........a...",
    ],
    "fist": [
        "............",
        "..hxhxhxh...",
        ".hxxxxxxxx..",
        ".xxoxxoxxxo.",
        ".xxxxxxxxxo.",
        "hxxxxxxxxxo.",
        "xxxxxxxxxxo.",
        ".xxxxxxxxo..",
        "..xxxxxxxo..",
        "...xxxxxo...",
        "...ooooo....",
        "............",
    ],
    "slam": [
        "....hxxh....",
        "...hxxxxh...",
        "...xxxxxx...",
        "...xxxxxx...",
        "....xxxx....",
        "............",
        "a....aa....a",
        ".a..a..a..a.",
        "..aa....aa..",
        "oooooooooooo",
        "............",
        "............",
    ],
    "throw": [
        "............",
        "......aaaa..",
        "....aa....a.",
        "...a.......a",
        "..a......aaa",
        "..a.......a.",
        ".hxh........",
        "hxxxh.......",
        "xxxxx.......",
        "hxxxh.......",
        ".hxh........",
        "............",
    ],
    "crystal": [
        ".....h......",
        "....hxo.....",
        "...hxxxo..h.",
        "..hxxhxxohxo",
        "..xxhxxxoxxo",
        "...xxxxo.xo.",
        "....xxo..o..",
        ".h...o......",
        "hxo.........",
        "xxo.........",
        ".o..........",
        "............",
    ],
    "eye": [
        "............",
        "............",
        "...oooooo...",
        "..oxxxxxxo..",
        ".oxxhaahxxo.",
        "oxxxaooaxxxo",
        "oxxxaooaxxxo",
        ".oxxhaahxxo.",
        "..oxxxxxxo..",
        "...oooooo...",
        "............",
        "............",
    ],
    "dash": [
        "............",
        "............",
        "..........h.",
        "aaa......hxo",
        "....xxxxxxxx",
        "aa..xxxxxxxx",
        "....xxxxxxxx",
        "aaa......hxo",
        "..........h.",
        "............",
        "............",
        "............",
    ],
    "key": [
        "..xxx.......",
        ".xhhhx......",
        "xh...hx.....",
        "xh...hx.....",
        ".xhhhxx.....",
        "..xxxhxx....",
        "......xhx...",
        ".......xhx..",
        "........xhx.",
        ".......aax.x",
        "........a...",
        ".......aa...",
    ],
    "book": [
        "............",
        ".oooooooooo.",
        ".oxxxxxxxxo.",
        ".oxhhhhhhxo.",
        ".oxxxxxxxxo.",
        ".oxhhhhhxxo.",
        ".oxxxxxxxxo.",
        ".oxhhhhhhxo.",
        ".oxxxxxxxxo.",
        ".oxxxaaxxxo.",
        ".ooooaaoooo.",
        ".....aa.....",
    ],
    "planet": [
        "....oooo....",
        "..ooxxxxoo..",
        ".oxxhhxxxxo.",
        ".oxhxxxxxxo.",
        "aaaaxxxxxxaa",
        "oxxxaaaaaaxo",
        "oxxxxxxxxxxo",
        ".oxxxxxxxxo.",
        ".oxxxxxxxxo.",
        "..ooxxxxoo..",
        "....oooo....",
        "............",
    ],
    "frame": [
        "xxxx....xxxx",
        "x..........x",
        "x..........x",
        "x...hhhh...x",
        "....h..h....",
        "....h..h....",
        "....hhhh....",
        "............",
        "x..aaa.....x",
        "x..a.a.....x",
        "x..aaa.....x",
        "xxxx....xxxx",
    ],
    "plus": [
        "..a..",
        "..a..",
        "aaaaa",
        "..a..",
        "..a..",
    ],
}

# Symbol -> (System, Motiv, Plus-Zeichen?)
ICONS: dict[str, tuple[str, str, bool]] = {
    # Helden-Faehigkeiten (Name = Datei in data/.../hero_ability/)
    "hero_ability/quick_run": ("exploration", "speed", False),
    "hero_ability/combo_plus": ("combat", "sword", True),
    "hero_ability/bolt_bonus": ("tech", "bolt", True),
    "hero_ability/high_jump": ("exploration", "arrow_up", False),
    "hero_ability/mp_haste": ("keyblade", "cycle", False),
    "hero_ability/air_combo_plus": ("combat", "swoosh", True),
    "hero_ability/extended_transformation": ("omnitrix", "clock", False),
    "hero_ability/exp_boost": ("keyblade", "exp", False),
    "hero_ability/second_chance": ("combat", "heart", False),
    "hero_ability/magic_boost": ("keyblade", "sparkle", True),
    "hero_ability/glide": ("exploration", "wing", False),
    "hero_ability/quick_recharge": ("omnitrix", "cycle", False),
    "hero_ability/air_dodge_plus": ("combat", "dash", True),
    "hero_ability/nanotech_regen": ("tech", "gear", False),
    # Zauber
    "spell/fire": ("keyblade", "flame", False),
    "spell/blizzard": ("keyblade", "snowflake", False),
    "spell/thunder": ("keyblade", "lightning", False),
    "spell/cure": ("keyblade", "heart", False),
    # Alien-Faehigkeiten (Name = Faehigkeits-Typ)
    "alien_ability/fire_blast": ("omnitrix", "flame", False),
    "alien_ability/fire_burst": ("omnitrix", "sparkle", False),
    "alien_ability/flame_boost": ("omnitrix", "arrow_up", False),
    "alien_ability/dash_strike": ("omnitrix", "dash", False),
    "alien_ability/blur_dodge": ("omnitrix", "swoosh", False),
    "alien_ability/rapid_strikes": ("omnitrix", "fist", True),
    "alien_ability/ground_slam": ("omnitrix", "slam", False),
    "alien_ability/throw": ("omnitrix", "throw", False),
    "alien_ability/mighty_leap": ("omnitrix", "arrow_up", True),
    "alien_ability/crystal_volley": ("omnitrix", "crystal", False),
    "alien_ability/scan": ("omnitrix", "eye", False),
    "alien_ability/pounce": ("omnitrix", "dash", True),
    "alien_ability/quill_burst": ("omnitrix", "crystal", True),
    "alien_ability/feral_roar": ("omnitrix", "eye", True),
    "alien_ability/slime_spit": ("omnitrix", "potion", False),
    "alien_ability/stink_cloud": ("omnitrix", "cycle", False),
    "alien_ability/wing_dash": ("omnitrix", "wing", False),
    "alien_ability/jaw_bite": ("omnitrix", "fist", False),
    "alien_ability/tidal_dash": ("omnitrix", "swoosh", True),
    "alien_ability/whirlpool": ("omnitrix", "cycle", True),
    "alien_ability/optic_beam": ("omnitrix", "lightning", False),
    "alien_ability/liquid_form": ("omnitrix", "potion", True),
    "alien_ability/tech_upgrade": ("omnitrix", "gear", False),
    "alien_ability/tentacle_lash": ("omnitrix", "throw", True),
    "alien_ability/phase_shift": ("omnitrix", "swoosh", False),
    "alien_ability/haunting_scare": ("omnitrix", "sparkle", True),
    "alien_ability/inferno_wave": ("omnitrix", "flame", True),
    "alien_ability/flame_shield": ("omnitrix", "heart", False),
    "alien_ability/supernova": ("omnitrix", "sparkle", True),
    "alien_ability/cyclone_run": ("omnitrix", "cycle", True),
    "alien_ability/time_slip": ("omnitrix", "clock", False),
    "alien_ability/lightspeed_barrage": ("omnitrix", "lightning", True),
    "alien_ability/thunder_clap": ("omnitrix", "slam", True),
    "alien_ability/iron_skin": ("omnitrix", "heart", False),
    "alien_ability/earthquake": ("omnitrix", "slam", False),
    "alien_ability/crystal_blade": ("omnitrix", "sword", False),
    "alien_ability/spike_eruption": ("omnitrix", "crystal", True),
    "alien_ability/crystal_armor": ("omnitrix", "heart", True),
    "alien_ability/crystal_storm": ("omnitrix", "snowflake", True),
    "alien_ability/weak_spot": ("omnitrix", "eye", True),
    "alien_ability/scurry": ("omnitrix", "speed", False),
    "alien_ability/tech_snare": ("omnitrix", "gear", True),
    "alien_ability/jury_rig": ("omnitrix", "plus", False),
    "alien_ability/mastermind": ("omnitrix", "book", True),
    "alien_ability/savage_maul": ("omnitrix", "fist", True),
    "alien_ability/scent_track": ("omnitrix", "eye", False),
    "alien_ability/primal_rampage": ("omnitrix", "fist", False),
    "alien_ability/slime_bomb": ("omnitrix", "potion", True),
    "alien_ability/updraft": ("omnitrix", "wing", True),
    "alien_ability/toxic_storm": ("omnitrix", "cycle", False),
    "alien_ability/tail_swipe": ("omnitrix", "swoosh", False),
    "alien_ability/hydro_heal": ("omnitrix", "heart", True),
    "alien_ability/tidal_wave": ("omnitrix", "swoosh", True),
    "alien_ability/mace_fists": ("omnitrix", "fist", False),
    "alien_ability/system_override": ("omnitrix", "gear", True),
    "alien_ability/plasma_cannon": ("omnitrix", "lightning", False),
    "alien_ability/possession": ("omnitrix", "eye", False),
    "alien_ability/shadow_step": ("omnitrix", "dash", False),
    "alien_ability/nightmare": ("omnitrix", "sparkle", False),
    "alien_ability/cannonball": ("omnitrix", "dash", True),
    "alien_ability/shell_guard": ("omnitrix", "heart", True),
    "alien_ability/ball_bounce": ("omnitrix", "arrow_up", False),
    "alien_ability/ricochet": ("omnitrix", "cycle", False),
    "alien_ability/rolling_mode": ("omnitrix", "speed", True),
    "alien_ability/cannonade": ("omnitrix", "slam", True),
    "alien_ability/titan_leap": ("omnitrix", "slam", False),
    "alien_ability/neuroshock": ("omnitrix", "lightning", False),
    "alien_ability/tail_shock": ("omnitrix", "cycle", False),
    "alien_ability/jet_burst": ("omnitrix", "wing", False),
    "alien_ability/strafing_run": ("omnitrix", "dash", True),
    "alien_ability/slipstream": ("omnitrix", "speed", True),
    "alien_ability/neuroshock_storm": ("omnitrix", "lightning", True),
    # Kommandomenue
    "command/attack": ("hero", "sword", False),
    "command/magic": ("keyblade", "sparkle", False),
    "command/items": ("hero", "potion", False),
    "command/omnitrix": ("omnitrix", "omnitrix", False),
    # Menue-Reiter
    "menu/hero": ("hero", "key", False),
    "menu/aliens": ("omnitrix", "omnitrix", False),
    "menu/quests": ("keyblade", "book", False),
    "menu/map": ("exploration", "planet", False),
    "menu/hud": ("tech", "frame", False),
}

# Motiv-Farben, die vom System abweichen (Zauber in ihrer Elementfarbe, Rahmen bleibt Keyblade-gold)
ICON_COLORS = {
    "spell/fire": ("FF7A2A", "FFE14A"),
    "spell/blizzard": ("8FD8FF", "FFFFFF"),
    "spell/thunder": ("FFE14A", "FFFFFF"),
    "spell/cure": ("5CE65C", "D8FFD0"),
    "hero_ability/second_chance": ("FF6B5A", "FFD0C8"),
}

# Motive mit eigener Akzentfarbe
GLYPH_ACCENT = {
    "flame": "FFE14A",
    "potion": "5CE65C",
    "speed": "FFFFFF",
    "swoosh": "FFFFFF",
    "dash": "FFFFFF",
    "exp": "FFC94A",
    "slam": "C8A07A",
    "throw": "FFFFFF",
    "eye": "1A1F2B",
    "key": "FFC94A",
    "book": "FF6B5A",
    "planet": "FFC94A",
    "frame": "FF9A3C",
    "sword": "FFC94A",
    "sparkle": "FFFFFF",
}


def glyph(name: str, main: str, light: str) -> Image.Image:
    rows = GLYPHS[name]
    accent = GLYPH_ACCENT.get(name, light)
    palette = {"x": hexc(main), "h": hexc(light), "o": hexc("10131C"), "a": hexc(accent)}
    size = max(len(rows), max(len(r) for r in rows))
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in palette:
                raise KeyError(f"Zeichen {ch!r} fehlt in der Palette ({name}, Zeile {y})")
            img.putpixel((x, y), palette[ch])
    return img


def badge(theme: str) -> Image.Image:
    border, _ = THEMES[theme]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            corner = (x in (0, 15)) and (y in (0, 15))
            if corner:
                continue
            edge = x in (0, 15) or y in (0, 15)
            img.putpixel((x, y), hexc(border) if edge else hexc("1A1F2B", 235))
    # innere Lichtkante oben
    for x in range(2, 14):
        img.putpixel((x, 1), hexc("2C3446", 235))
    return img


def build_icon(name: str, theme: str, glyph_name: str, plus: bool) -> Image.Image:
    main, light = ICON_COLORS.get(name, THEMES[theme])
    img = badge(theme)
    img.alpha_composite(glyph(glyph_name, main, light), (2, 2))
    if plus:
        sign = glyph("plus", "FFE14A", "FFE14A")
        img.alpha_composite(sign, (10, 10))
    return img


def build_all() -> dict[str, Image.Image]:
    return {f"textures/gui/icon/{name}.png": build_icon(name, *spec) for name, spec in ICONS.items()}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="assets/kingdomomnitrix-Ordner (Standard: %(default)s)")
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Symbole vorhanden sind")
    parser.add_argument("--sheet", type=Path, help="zusaetzlich eine vergroesserte Uebersicht aller Symbole speichern")
    parser.add_argument("-v", "--verbose", action="store_true", help="jede Datei protokollieren")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    icons = build_all()
    if args.check:
        missing = [p for p in icons if not (args.root / p).is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p)
        LOG.info("%d/%d Symbole vorhanden", len(icons) - len(missing), len(icons))
        return 1 if missing else 0

    for rel, img in icons.items():
        target = args.root / rel
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            img.save(target)
        except OSError as exc:
            LOG.error("konnte %s nicht schreiben: %s", target, exc)
            return 1
        LOG.debug("geschrieben: %s", target)
    LOG.info("%d Symbole nach %s geschrieben", len(icons), args.root)

    if args.sheet:
        columns = 10
        scale = 6
        rows = (len(icons) + columns - 1) // columns
        sheet = Image.new("RGBA", (columns * 18 * scale, rows * 18 * scale), hexc("2A2F3A"))
        for i, img in enumerate(icons.values()):
            tile = img.resize((16 * scale, 16 * scale), Image.NEAREST)
            sheet.alpha_composite(tile, ((i % columns) * 18 * scale + scale, (i // columns) * 18 * scale + scale))
        sheet.save(args.sheet)
        LOG.info("Uebersicht: %s", args.sheet)
    return 0


if __name__ == "__main__":
    sys.exit(main())
