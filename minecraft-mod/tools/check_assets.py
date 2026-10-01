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
DYNAMIC_PREFIXES = tuple(f"{kind}.{MOD_ID}." for kind in ("spell", "alien", "ability"))
KEY_PATTERN = re.compile(r'"((?:message|tooltip|spell|alien|ability|hud|commands|itemGroup|effect|key|category|screen|item)\.' + MOD_ID + r'[\w.]*)"')
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
        if key.startswith(DYNAMIC_PREFIXES) and key.endswith("."):
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


ABILITY_PATTERN = re.compile(r'AbilityRegistry\.register\(KingdomOmnitrix\.id\("([a-z0-9_]+)"\)')


def check_aliens(report: Report, source: str, lang: dict[str, str]) -> int:
    abilities = set(ABILITY_PATTERN.findall(source))
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
        if not 1 <= len(slots) <= 3:
            report.error("Alien %s: 1 bis 3 Faehigkeiten erlaubt, gefunden %d", name, len(slots))
        for slot in slots:
            namespace, _, ability = str(slot.get("type", "")).partition(":")
            if namespace == MOD_ID and ability not in abilities:
                report.error("Alien %s: unbekannter Faehigkeits-Typ %s", name, slot.get("type"))
            if f"ability.{namespace}.{ability}" not in lang:
                report.error("Faehigkeit %s hat keinen Namen in der Uebersetzung", slot.get("type"))
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

    if report.errors:
        LOG.error("%d Fehler gefunden", report.errors)
        return 1
    LOG.info("Ressourcen in Ordnung: %d Items, %d Aliens, %d Uebersetzungen", len(items), alien_count, len(lang))
    return 0


if __name__ == "__main__":
    sys.exit(main())
