#!/usr/bin/env python3
"""Prueft die Ressourcen von Kingdom Omnitrix auf Luecken, bevor Minecraft sie laedt.

Geprueft wird:
  * jede JSON-Datei unter src/main/resources ist gueltiges JSON
  * de_de und en_us haben exakt dieselben Schluessel
  * jeder im Java-Code fest verwendete Uebersetzungsschluessel existiert
  * jedes registrierte Item hat ein Modell und (falls verwendet) seine Textur
  * jedes Item hat einen Namen in beiden Sprachen
  * Rezepte verweisen nur auf existierende Mod-Items, Muster und Schluessel passen zusammen
  * Loot-Tabellen verweisen nur auf existierende Mod-Items
  * Alien-Definitionen: Pflichtfelder, bekannte Faehigkeits-Typen, Uebersetzungen, Koerper-Dateien

Aufruf aus dem Ordner minecraft-mod/:  python tools/check_assets.py [-v]
Rueckgabe: 0 = alles in Ordnung, 1 = Fehler gefunden (Details im Log).
"""
from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from pathlib import Path

MOD_ID = "kingdomomnitrix"
LOG = logging.getLogger("check_assets")
ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / MOD_ID
DATA = RESOURCES / "data" / MOD_ID
JAVA_DIRS = [ROOT / "src" / "main" / "java", ROOT / "src" / "client" / "java"]
LANGS = ("de_de", "en_us")

# Praefixe, an die der Code zur Laufzeit eine ID anhaengt ("spell.kingdomomnitrix." + id).
DYNAMIC_PREFIXES = tuple(f"{kind}.{MOD_ID}." for kind in ("party", "spell", "alien", "ability", "passive", "quest", "gadget", "npc", "ship", "route", "arena", "boss"))
# zusammengesetzte Schluessel des Kommandomenues (Zeilen attack/magic/items/omnitrix, leere Untermenues)
DYNAMIC_KEYS = (f"hud.{MOD_ID}.command.", f"hud.{MOD_ID}.command.empty.")
KEY_PATTERN = re.compile(r'"((?:party|message|tooltip|spell|alien|ability|hud|commands|itemGroup|effect|key|category|screen|item|passive|quest|gadget|container|npc|dialog|ship|route|block|entity|arena|boss)\.' + MOD_ID + r'[\w.]*)"')
ITEM_PATTERN = re.compile(r'register\("([a-z0-9_]+)",')


class Report:
    def __init__(self) -> None:
        self.errors = 0

    def error(self, message: str, *args: object) -> None:
        self.errors += 1
        LOG.error(message, *args)


def load_json(path: Path, report: Report) -> object | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        report.error("ungueltiges JSON %s: %s", path.relative_to(ROOT), exc)
        return None


def java_sources() -> str:
    return "\n".join(p.read_text(encoding="utf-8") for d in JAVA_DIRS if d.is_dir() for p in d.rglob("*.java"))


def registered_items() -> list[str]:
    path = ROOT / "src" / "main" / "java" / "com" / "santiq" / MOD_ID / "registry" / "ModItems.java"
    return ITEM_PATTERN.findall(path.read_text(encoding="utf-8"))


def check_json_syntax(report: Report) -> None:
    for path in RESOURCES.rglob("*.json"):
        load_json(path, report)


def check_lang(report: Report, source: str) -> dict[str, str]:
    langs = {}
    for code in LANGS:
        data = load_json(ASSETS / "lang" / f"{code}.json", report)
        langs[code] = data if isinstance(data, dict) else {}
    a, b = (set(langs[c]) for c in LANGS)
    for key in sorted(a - b):
        report.error("Schluessel nur in %s: %s", LANGS[0], key)
    for key in sorted(b - a):
        report.error("Schluessel nur in %s: %s", LANGS[1], key)
    for key in sorted(set(KEY_PATTERN.findall(source))):
        if (key.startswith(DYNAMIC_PREFIXES) and key.endswith(".")) or key in DYNAMIC_KEYS:
            continue
        if key not in langs[LANGS[0]]:
            report.error("Code verwendet unbekannten Schluessel: %s", key)
    return langs[LANGS[0]]


def check_items(report: Report, items: list[str], lang: dict[str, str]) -> None:
    for item in items:
        model_path = ASSETS / "models" / "item" / f"{item}.json"
        if not model_path.is_file():
            report.error("Item ohne Modell: %s", item)
            continue
        model = load_json(model_path, report)
        textures = model.get("textures", {}) if isinstance(model, dict) else {}
        for texture in textures.values():
            namespace, _, tex_path = texture.partition(":")
            if namespace == MOD_ID and not (ASSETS / "textures" / f"{tex_path}.png").is_file():
                report.error("Modell %s verweist auf fehlende Textur %s", item, texture)
        is_block_item = isinstance(model, dict) and str(model.get("parent", "")).startswith(f"{MOD_ID}:block/")
        name_key = f"block.{MOD_ID}.{item}" if is_block_item else f"item.{MOD_ID}.{item}"
        if name_key not in lang:
            report.error("Item ohne Namen: %s (%s)", item, name_key)


def ingredient_items(entry: object) -> list[str]:
    if isinstance(entry, dict) and "item" in entry:
        return [entry["item"]]
    if isinstance(entry, list):
        return [i for e in entry for i in ingredient_items(e)]
    return []


def check_recipes(report: Report, items: set[str]) -> None:
    for path in sorted((DATA / "recipe").glob("*.json")):
        recipe = load_json(path, report)
        if not isinstance(recipe, dict):
            continue
        name = path.stem
        ingredients = list(recipe.get("key", {}).values()) if "key" in recipe else recipe.get("ingredients", [])
        if f"{MOD_ID}:bolt" in ingredient_items(ingredients):
            report.error("Rezept %s nutzt Bolts als Zutat - Bolts landen sofort auf dem Konto und sind nie im Inventar", name)
        for item in ingredient_items(ingredients) + [recipe.get("result", {}).get("id", "")]:
            namespace, _, item_path = item.partition(":")
            if namespace == MOD_ID and item_path not in items:
                report.error("Rezept %s verweist auf unbekanntes Item %s", name, item)
        if "pattern" in recipe:
            used = set("".join(recipe["pattern"])) - {" "}
            keys = set(recipe.get("key", {}))
            if used != keys:
                report.error("Rezept %s: Muster-Zeichen %s passen nicht zu Schluesseln %s", name, sorted(used), sorted(keys))


def check_loot(report: Report, items: set[str]) -> None:
    for path in sorted((DATA / "loot_table").rglob("*.json")):
        table = load_json(path, report)
        if not isinstance(table, dict):
            continue
        for pool in table.get("pools", []):
            for entry in pool.get("entries", []):
                namespace, _, item_path = str(entry.get("name", "")).partition(":")
                if namespace == MOD_ID and item_path not in items:
                    report.error("Loot-Tabelle %s verweist auf unbekanntes Item %s", path.relative_to(DATA), entry["name"])


# Registrierung direkt oder ueber die Kurzform reg("name", …) in MasteryAbilities
ABILITY_PATTERN = re.compile(r'(?:AbilityRegistry\.register\(KingdomOmnitrix\.id\(|\breg\()"([a-z0-9_]+)"')
MAX_ABILITIES_PATTERN = re.compile(r'MAX_ABILITIES\s*=\s*(\d+)')


def check_aliens(report: Report, source: str, lang: dict[str, str]) -> int:
    abilities = set(ABILITY_PATTERN.findall(source))
    found_max = MAX_ABILITIES_PATTERN.search(source)
    max_abilities = int(found_max.group(1)) if found_max else 3
    alien_dir = DATA / MOD_ID / "alien"
    files = sorted(alien_dir.glob("*.json"))
    if not files:
        report.error("keine Alien-Definitionen in %s", alien_dir.relative_to(ROOT))
    for path in files:
        alien = load_json(path, report)
        if not isinstance(alien, dict):
            continue
        name = path.stem
        for field in ("color", "duration", "recharge", "abilities", "model"):
            if field not in alien:
                report.error("Alien %s: Pflichtfeld %s fehlt", name, field)
        if f"alien.{MOD_ID}.{name}" not in lang:
            report.error("Alien %s hat keinen Namen in der Uebersetzung", name)
        model_ns, _, model_path = str(alien.get("model", "")).partition(":")
        if model_ns == MOD_ID:
            for rel in (f"geo/entity/alien/{model_path}.geo.json", f"animations/entity/alien/{model_path}.animation.json",
                        f"textures/entity/alien/{model_path}.png"):
                if not (ASSETS / rel).is_file():
                    report.error("Alien %s: Koerper-Datei fehlt: %s", name, rel)
        slots = alien.get("abilities", [])
        if not 1 <= len(slots) <= max_abilities:
            report.error("Alien %s: 1 bis %d Faehigkeiten erlaubt, gefunden %d", name, max_abilities, len(slots))
        for slot in slots:
            namespace, _, ability = str(slot.get("type", "")).partition(":")
            if namespace == MOD_ID and ability not in abilities:
                report.error("Alien %s: unbekannter Faehigkeits-Typ %s", name, slot.get("type"))
            if f"ability.{namespace}.{ability}" not in lang:
                report.error("Faehigkeit %s hat keinen Namen in der Uebersetzung", slot.get("type"))
    return len(files)


def check_spells(report: Report, lang: dict[str, str]) -> int:
    files = sorted((DATA / MOD_ID / "spell").glob("*.json"))
    for path in files:
        spell = load_json(path, report)
        if not isinstance(spell, dict):
            continue
        levels = spell.get("levels", [])
        if not levels:
            report.error("Zauber %s hat keine Stufen", path.stem)
        for level in range(1, len(levels) + 1):
            key = f"spell.{MOD_ID}.{path.stem}.{level}"
            if key not in lang:
                report.error("Zauberstufe ohne Namen: %s", key)
    return len(files)


def translate_keys(node: object) -> list[str]:
    """Alle {"translate": ...}-Schluessel in einem JSON-Baum."""
    if isinstance(node, dict):
        found = [node["translate"]] if isinstance(node.get("translate"), str) else []
        return found + [k for v in node.values() for k in translate_keys(v)]
    if isinstance(node, list):
        return [k for v in node for k in translate_keys(v)]
    return []


def check_feature_order(report: Report) -> None:
    """Minecraft bricht das Laden ab, wenn zwei Biome einer Dimension Features in widerspruechlicher Reihenfolge haben."""
    biome_dir = DATA / "worldgen" / "biome"
    groups: dict[str, list[Path]] = {}
    for path in biome_dir.rglob("*.json"):
        groups.setdefault(path.parent.name, []).append(path)
    for group, paths in groups.items():
        before: dict[tuple[int, str, str], str] = {}
        for path in paths:
            biome = load_json(path, report)
            if not isinstance(biome, dict):
                continue
            for step, features in enumerate(biome.get("features", [])):
                for i, first in enumerate(features):
                    for second in features[i + 1:]:
                        if (step, second, first) in before:
                            report.error("Feature-Reihenfolge widerspruechlich (%s): %s vor %s in %s, umgekehrt in %s",
                                         group, first, second, path.stem, before[(step, second, first)])
                        before.setdefault((step, first, second), path.stem)


def check_arena(report: Report, lang: dict[str, str]) -> int:
    files = sorted((DATA / MOD_ID / "arena_challenge").glob("*.json"))
    for path in files:
        if f"arena.{MOD_ID}.{path.stem}" not in lang:
            report.error("Arena-Herausforderung ohne Namen: arena.%s.%s", MOD_ID, path.stem)
    return len(files)


WORLD_EVENT_TYPES = {"heartless_invasion", "dark_rift", "alien_crash", "raritanium_meteor", "boss_spawn", "keyblade_shrine"}


def check_world_events(report: Report, lang: dict[str, str]) -> int:
    """Welt-Ereignisse: bekannter Typ, Riss vorhanden (Invasion/Riss), Name und Hinweis uebersetzt."""
    files = sorted((DATA / MOD_ID / "world_event").glob("*.json"))
    rifts = {path.stem for path in (DATA / MOD_ID / "rift").glob("*.json")}
    for path in files:
        event = load_json(path, report)
        if not isinstance(event, dict):
            continue
        kind = event.get("type")
        if kind not in WORLD_EVENT_TYPES:
            report.error("Weltereignis %s: unbekannter Typ %s", path.stem, kind)
        if kind in ("heartless_invasion", "dark_rift"):
            rift = str(event.get("rift", "")).split(":")[-1]
            if rift not in rifts:
                report.error("Weltereignis %s: Riss %s fehlt", path.stem, event.get("rift"))
        for key in (f"world_event.{MOD_ID}.{path.stem}", f"world_event.{MOD_ID}.{path.stem}.hint"):
            if key not in lang:
                report.error("Weltereignis ohne Text: %s", key)
    return len(files)


HERO_ABILITY_CATEGORIES = {"combat", "keyblade", "omnitrix", "tech", "exploration"}
HERO_MAX_LEVEL = 50


def hero_ability_effects() -> set[str]:
    """Effekt-Namen aus dem Java-Enum, damit JSON und Code nicht auseinanderlaufen."""
    source = (ROOT / "src/main/java/com/santiq/kingdomomnitrix/progression/HeroAbilityEffect.java").read_text(encoding="utf-8")
    body = source.split("implements StringIdentifiable {", 1)[1]
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.DOTALL).split(";", 1)[0]
    return {name.lower() for name in re.findall(r"^\s*([A-Z_]+)\s*[,]?\s*$", body, re.MULTILINE)}


def check_hero_abilities(report: Report, lang: dict[str, str]) -> int:
    effects = hero_ability_effects()
    files = sorted((DATA / MOD_ID / "hero_ability").glob("*.json"))
    for path in files:
        ability = load_json(path, report)
        if not isinstance(ability, dict):
            continue
        key = f"hero_ability.{MOD_ID}.{path.stem}"
        for needed in (key, key + ".desc"):
            if needed not in lang:
                report.error("Helden-Faehigkeit ohne Text: %s", needed)
        if ability.get("category") not in HERO_ABILITY_CATEGORIES:
            report.error("Helden-Faehigkeit %s: unbekannte Kategorie %s", path.stem, ability.get("category"))
        if ability.get("effect") not in effects:
            report.error("Helden-Faehigkeit %s: unbekannter Effekt %s (bekannt: %s)", path.stem, ability.get("effect"), ", ".join(sorted(effects)))
        if not 1 <= int(ability.get("unlock_level", 1)) <= HERO_MAX_LEVEL:
            report.error("Helden-Faehigkeit %s: unlock_level muss 1–%d sein", path.stem, HERO_MAX_LEVEL)
        if not 0 <= int(ability.get("ap_cost", -1)) <= 20:
            report.error("Helden-Faehigkeit %s: ap_cost muss 0–20 sein", path.stem)
    for category in HERO_ABILITY_CATEGORIES:
        if f"hero_ability.{MOD_ID}.category.{category}" not in lang:
            report.error("Kategorie ohne Text: hero_ability.%s.category.%s", MOD_ID, category)
    return len(files)


def check_icons(report: Report) -> None:
    """Jede Faehigkeit, jeder Zauber und jeder Alien-Faehigkeits-Typ braucht ein Symbol (tools/generate_icons.py)."""
    icons = ASSETS / "textures" / "gui" / "icon"
    wanted = [f"hero_ability/{p.stem}" for p in (DATA / MOD_ID / "hero_ability").glob("*.json")]
    wanted += [f"spell/{p.stem}" for p in (DATA / MOD_ID / "spell").glob("*.json")]
    for path in (DATA / MOD_ID / "alien").glob("*.json"):
        alien = load_json(path, report)
        if isinstance(alien, dict):
            for ability in alien.get("abilities", []):
                kind = str(ability.get("type", ""))
                if kind.startswith(MOD_ID + ":"):
                    wanted.append("alien_ability/" + kind.split(":", 1)[1])
    wanted += [f"command/{name}" for name in ("attack", "magic", "items", "omnitrix")]
    wanted += [f"menu/{name}" for name in ("hero", "aliens", "quests", "map", "hud")]
    for name in sorted(set(wanted)):
        if not (icons / f"{name}.png").is_file():
            report.error("Symbol fehlt: textures/gui/icon/%s.png (tools/generate_icons.py ergaenzen)", name)


def check_particles(report: Report) -> int:
    """Jeder registrierte Partikel braucht particles/<name>.json mit vorhandenen Texturen (tools/generate_particles.py)."""
    source = (ROOT / "src/main/java/com/santiq/kingdomomnitrix/registry/ModParticles.java").read_text(encoding="utf-8")
    names = re.findall(r'register\("([a-z0-9_]+)"\)', source)
    for name in names:
        definition = load_json(ASSETS / "particles" / f"{name}.json", report)
        if not isinstance(definition, dict) or not definition.get("textures"):
            report.error("Partikel ohne Definition: particles/%s.json", name)
            continue
        for texture in definition["textures"]:
            path = texture.split(":", 1)[1]
            if not (ASSETS / "textures" / "particle" / f"{path}.png").is_file():
                report.error("Partikel %s: Textur fehlt: textures/particle/%s.png", name, path)
    return len(names)


def check_sounds(report: Report, lang: dict[str, str]) -> int:
    """Jedes registrierte Sound-Ereignis braucht einen Eintrag in sounds.json, Dateien und einen Untertitel."""
    source = (ROOT / "src/main/java/com/santiq/kingdomomnitrix/registry/ModSounds.java").read_text(encoding="utf-8")
    events = re.findall(r'register\("([a-z0-9_.]+)"\)', source)
    sounds = load_json(ASSETS / "sounds.json", report)
    if not isinstance(sounds, dict):
        report.error("sounds.json fehlt")
        return 0
    for event in events:
        entry = sounds.get(event)
        if not isinstance(entry, dict):
            report.error("Sound ohne Eintrag in sounds.json: %s", event)
            continue
        if entry.get("subtitle") not in lang:
            report.error("Sound %s: Untertitel fehlt in der Sprachdatei (%s)", event, entry.get("subtitle"))
        for sound in entry.get("sounds", []):
            name = sound if isinstance(sound, str) else sound.get("name", "")
            path = name.split(":", 1)[1]
            if not (ASSETS / "sounds" / f"{path}.ogg").is_file():
                report.error("Sound %s: Datei fehlt: sounds/%s.ogg", event, path)
    for event in sorted(set(sounds) - set(events)):
        report.error("sounds.json enthaelt nicht registriertes Ereignis: %s", event)
    return len(events)


def check_routes(report: Report, lang: dict[str, str]) -> int:
    files = sorted((DATA / MOD_ID / "space_route").glob("*.json"))
    for path in files:
        route = load_json(path, report)
        if not isinstance(route, dict):
            continue
        if f"route.{MOD_ID}.{path.stem}" not in lang:
            report.error("Weltraumriss ohne Namen: route.%s.%s", MOD_ID, path.stem)
        if len(route.get("position", [])) != 3:
            report.error("Weltraumriss %s: position braucht 3 Zahlen", path.stem)
    return len(files)


def check_npcs(report: Report, lang: dict[str, str]) -> set[str]:
    npcs = set()
    for path in sorted((DATA / MOD_ID / "npc").glob("*.json")):
        npc = load_json(path, report)
        if not isinstance(npc, dict):
            continue
        npcs.add(f"{MOD_ID}:{path.stem}")
        if f"npc.{MOD_ID}.{path.stem}" not in lang:
            report.error("NPC ohne Namen: npc.%s.%s", MOD_ID, path.stem)
        for key in translate_keys(npc):
            if key not in lang:
                report.error("NPC %s: Uebersetzung fehlt: %s", path.stem, key)
        model = npc.get("model", path.stem)
        for rel in (f"geo/entity/npc/{model}.geo.json", f"animations/entity/npc/{model}.animation.json", f"textures/entity/npc/{model}.png"):
            if not (ASSETS / rel).is_file():
                report.error("NPC %s: Datei fehlt: %s", path.stem, rel)
    return npcs


def check_quests(report: Report, lang: dict[str, str], items: set[str], npcs: set[str]) -> int:
    files = sorted((DATA / MOD_ID / "quest").glob("*.json"))
    known = {f"{MOD_ID}:{p.stem}" for p in files}
    entity_tags = {f"{MOD_ID}:{p.stem}" for p in (DATA / "tags" / "entity_type").glob("*.json")}
    for path in files:
        quest = load_json(path, report)
        if not isinstance(quest, dict):
            continue
        for key in translate_keys(quest):
            if key not in lang:
                report.error("Quest %s: Uebersetzung fehlt: %s", path.stem, key)
        for required in quest.get("requires", []):
            if required not in known:
                report.error("Quest %s setzt unbekannte Quest %s voraus", path.stem, required)
        objectives = quest.get("objectives", [])
        if not 1 <= len(objectives) <= 6:
            report.error("Quest %s braucht 1 bis 6 Ziele", path.stem)
        for objective in objectives:
            kind, target = objective.get("type"), objective.get("target", "")
            if kind not in ("kill", "craft", "collect"):
                report.error("Quest %s: unbekannter Zieltyp %s", path.stem, kind)
            namespace, _, name = target.lstrip("#").partition(":")
            if namespace != MOD_ID:
                continue
            if target.startswith("#"):
                if kind == "kill" and target[1:] not in entity_tags:
                    report.error("Quest %s: unbekannter Entity-Tag %s", path.stem, target)
            elif kind != "kill" and name not in items:
                report.error("Quest %s: unbekanntes Item %s", path.stem, target)
        npc = quest.get("giver", {}).get("npc")
        if npc is not None and npc not in npcs:
            report.error("Quest %s: unbekannter NPC %s", path.stem, npc)
        reward_items = quest.get("rewards", {}).get("items", [])
        icon = quest.get("giver", {}).get("icon", "")
        for item in [r.get("id", "") for r in reward_items] + [icon]:
            namespace, _, name = item.partition(":")
            if namespace == MOD_ID and name not in items:
                report.error("Quest %s: unbekanntes Item %s", path.stem, item)
    return len(files)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-v", "--verbose", action="store_true", help="ausfuehrliche Ausgabe")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    report = Report()
    check_json_syntax(report)
    lang = check_lang(report, java_sources())
    items = registered_items()
    LOG.debug("registrierte Items: %s", ", ".join(items))
    check_items(report, items, lang)
    check_recipes(report, set(items))
    check_loot(report, set(items))
    alien_count = check_aliens(report, java_sources(), lang)
    spell_count = check_spells(report, lang)
    npcs = check_npcs(report, lang)
    check_routes(report, lang)
    check_arena(report, lang)
    check_world_events(report, lang)
    check_feature_order(report)
    ability_count = check_hero_abilities(report, lang)
    check_icons(report)
    particle_count = check_particles(report)
    sound_count = check_sounds(report, lang)
    for row in ("attack", "magic", "items", "omnitrix"):
        if f"hud.{MOD_ID}.command.{row}" not in lang:
            report.error("Kommandomenue-Zeile ohne Text: hud.%s.command.%s", MOD_ID, row)
    for row in ("magic", "items", "omnitrix"):
        if f"hud.{MOD_ID}.command.empty.{row}" not in lang:
            report.error("Kommandomenue ohne Leer-Text: hud.%s.command.empty.%s", MOD_ID, row)
    quest_count = check_quests(report, lang, set(items), npcs)

    if report.errors:
        LOG.error("%d Fehler gefunden", report.errors)
        return 1
    LOG.info("Ressourcen in Ordnung: %d Items, %d Aliens, %d Zauber, %d Quests, %d NPCs, %d Faehigkeiten, %d Partikel, %d Sounds, %d Uebersetzungen",
             len(items), alien_count, spell_count, quest_count, len(npcs), ability_count, particle_count, sound_count, len(lang))
    return 0


if __name__ == "__main__":
    sys.exit(main())
