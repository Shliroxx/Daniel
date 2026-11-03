"""Konfiguration laden und streng pruefen.

Ein Fehler in der Datei soll beim Start auffallen - mit Kombo-Name und
Schrittnummer - und nicht erst mitten im Kampf.

Schritt-Syntax (ein String pro Schritt, Gross/Klein egal):
    taste X            Taste X kurz druecken (Dauer = tastendauer_ms)
    halte X MS         Taste X MS Millisekunden gedrueckt halten
    runter X / hoch X  Taste X druecken bzw. loslassen (fuer Kombis wie shift+klick)
    warte MS           MS Millisekunden warten
    klick T            Maustaste T kurz klicken (links, rechts, mitte, maus4, maus5)
    halteklick T MS    Maustaste T MS Millisekunden halten (Aufladen)
    mrunter T / mhoch T  Maustaste druecken bzw. loslassen
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from . import tasten
from .tasten import Hotkey, TastenFehler

MAX_MS = 60_000
MAX_WIEDERHOLUNGEN = 1000
MODI = ("einmal", "halten", "umschalten")

SCHRITT_ALIASE = {
    "taste": "taste", "tap": "taste", "drueck": "taste", "drück": "taste",
    "halte": "halte", "hold": "halte",
    "runter": "runter", "down": "runter",
    "hoch": "hoch", "up": "hoch",
    "warte": "warte", "wait": "warte", "pause": "warte",
    "klick": "klick", "click": "klick",
    "halteklick": "halteklick", "holdclick": "halteklick",
    "mrunter": "mrunter", "mdown": "mrunter",
    "mhoch": "mhoch", "mup": "mhoch",
}
# Anzahl Argumente je Art (ohne das Schluesselwort)
ARGUMENTE = {"taste": 1, "halte": 2, "runter": 1, "hoch": 1, "warte": 1,
             "klick": 1, "halteklick": 2, "mrunter": 1, "mhoch": 1}


class KonfigFehler(ValueError):
    """Konfiguration ist kaputt oder widerspruechlich."""


@dataclass(frozen=True)
class Schritt:
    art: str
    ziel: str | None = None      # Tasten- oder Maustastenname
    ms: int = 0

    def __str__(self) -> str:
        teile = [self.art]
        if self.ziel:
            teile.append(self.ziel)
        if self.art in ("warte", "halte", "halteklick"):
            teile.append(str(self.ms))
        return " ".join(teile)


@dataclass(frozen=True)
class Kombo:
    name: str
    hotkey: Hotkey
    schritte: tuple[Schritt, ...]
    modus: str = "einmal"
    wiederholen: int = 1
    abstand_ms: int = 0          # Pause zwischen zwei Durchgaengen
    tastendauer_ms: int = 35

    def dauer_ms(self) -> int:
        """Geschaetzte Dauer eines Durchgangs."""
        summe = 0
        for s in self.schritte:
            if s.art in ("taste", "klick"):
                summe += self.tastendauer_ms
            elif s.art in ("warte", "halte", "halteklick"):
                summe += s.ms
        return summe


@dataclass(frozen=True)
class Konfig:
    kombos: tuple[Kombo, ...]
    fenster_titel: tuple[str, ...] = ("nanos",)
    pause_taste: Hotkey = field(default_factory=lambda: tasten.hotkey("f8"))
    stopp_taste: Hotkey = field(default_factory=lambda: tasten.hotkey("f9"))
    beenden_taste: Hotkey = field(default_factory=lambda: tasten.hotkey("f10"))

    def kombo(self, name: str) -> Kombo:
        for k in self.kombos:
            if k.name.lower() == name.lower():
                return k
        namen = ", ".join(k.name for k in self.kombos)
        raise KonfigFehler(f"keine Kombo '{name}' - vorhanden: {namen}")


def _ms(wert: str, wo: str) -> int:
    try:
        ms = int(wert)
    except (TypeError, ValueError):
        raise KonfigFehler(f"{wo}: '{wert}' ist keine ganze Zahl (Millisekunden)") from None
    if not 0 <= ms <= MAX_MS:
        raise KonfigFehler(f"{wo}: {ms} ms liegt nicht zwischen 0 und {MAX_MS}")
    return ms


def schritt(text: str, wo: str = "Schritt") -> Schritt:
    if not isinstance(text, str) or not text.strip():
        raise KonfigFehler(f"{wo}: leerer Schritt")
    teile = text.split()
    art = SCHRITT_ALIASE.get(teile[0].lower())
    if art is None:
        raise KonfigFehler(f"{wo}: unbekannter Befehl '{teile[0]}' "
                           f"(erlaubt: {', '.join(ARGUMENTE)})")
    args = teile[1:]
    if len(args) != ARGUMENTE[art]:
        raise KonfigFehler(f"{wo}: '{art}' braucht {ARGUMENTE[art]} Angabe(n), "
                           f"bekommen: '{text}'")
    try:
        if art == "warte":
            return Schritt(art, None, _ms(args[0], wo))
        if art in ("taste", "runter", "hoch"):
            return Schritt(art, tasten.taste(args[0]))
        if art == "halte":
            return Schritt(art, tasten.taste(args[0]), _ms(args[1], wo))
        if art in ("klick", "mrunter", "mhoch"):
            return Schritt(art, tasten.maustaste(args[0]))
        return Schritt(art, tasten.maustaste(args[0]), _ms(args[1], wo))  # halteklick
    except TastenFehler as e:
        raise KonfigFehler(f"{wo}: {e}") from None


def _pruefe_gleichgewicht(schritte: tuple[Schritt, ...], wo: str) -> None:
    """runter ohne hoch ist erlaubt (wird am Ende geloest) - hoch ohne runter nicht."""
    gedrueckt: set[str] = set()
    for i, s in enumerate(schritte, 1):
        schluessel = ("m:" if s.art.startswith("m") else "t:") + (s.ziel or "")
        if s.art in ("runter", "mrunter"):
            gedrueckt.add(schluessel)
        elif s.art in ("hoch", "mhoch"):
            if schluessel not in gedrueckt:
                raise KonfigFehler(f"{wo}, Schritt {i}: '{s}' ohne vorheriges runter")
            gedrueckt.discard(schluessel)


def kombo_aus_dict(d: dict, standard_dauer: int) -> Kombo:
    if not isinstance(d, dict):
        raise KonfigFehler("jede Kombo muss ein Objekt {...} sein")
    name = d.get("name")
    if not isinstance(name, str) or not name.strip():
        raise KonfigFehler("Kombo ohne 'name'")
    wo = f"Kombo '{name}'"
    try:
        hk = tasten.hotkey(d.get("hotkey", ""))
    except TastenFehler as e:
        raise KonfigFehler(f"{wo}: Hotkey - {e}") from None

    roh = d.get("schritte")
    if not isinstance(roh, list) or not roh:
        raise KonfigFehler(f"{wo}: 'schritte' muss eine nicht-leere Liste sein")
    schritte = tuple(schritt(s, f"{wo}, Schritt {i}") for i, s in enumerate(roh, 1))
    _pruefe_gleichgewicht(schritte, wo)

    modus = str(d.get("modus", "einmal")).lower()
    if modus not in MODI:
        raise KonfigFehler(f"{wo}: modus '{modus}' unbekannt (erlaubt: {', '.join(MODI)})")

    wdh = d.get("wiederholen", 1)
    if not isinstance(wdh, int) or isinstance(wdh, bool) or not 1 <= wdh <= MAX_WIEDERHOLUNGEN:
        raise KonfigFehler(f"{wo}: 'wiederholen' muss zwischen 1 und {MAX_WIEDERHOLUNGEN} liegen")

    k = Kombo(
        name=name.strip(),
        hotkey=hk,
        schritte=schritte,
        modus=modus,
        wiederholen=wdh,
        abstand_ms=_ms(d.get("abstand_ms", 0), f"{wo}: abstand_ms"),
        tastendauer_ms=_ms(d.get("tastendauer_ms", standard_dauer), f"{wo}: tastendauer_ms"),
    )
    if modus == "halten" and hk.mods:
        raise KonfigFehler(f"{wo}: modus halten geht nicht mit {hk} - der Modifikator wuerde "
                           "jede Eingabe veraendern. Hotkey ohne alt/strg/shift waehlen.")
    if modus != "einmal" and k.dauer_ms() + k.abstand_ms < 20:
        raise KonfigFehler(f"{wo}: Schleife ohne Wartezeit - mindestens 20 ms pro Durchgang "
                           "(warte oder abstand_ms), sonst flutet sie das Spiel")
    return k


def aus_dict(daten: dict) -> Konfig:
    if not isinstance(daten, dict):
        raise KonfigFehler("die Datei muss ein JSON-Objekt {...} enthalten")

    def _steuer(schluessel: str, standard: str) -> Hotkey:
        try:
            return tasten.hotkey(daten.get(schluessel, standard))
        except TastenFehler as e:
            raise KonfigFehler(f"{schluessel}: {e}") from None

    pause = _steuer("pause_taste", "f8")
    stopp = _steuer("stopp_taste", "f9")
    beenden = _steuer("beenden_taste", "f10")
    steuer = {"pause_taste": pause, "stopp_taste": stopp, "beenden_taste": beenden}
    if len(set(steuer.values())) != 3:
        raise KonfigFehler("pause_taste, stopp_taste und beenden_taste muessen verschieden sein")

    fenster = daten.get("fenster_titel", ["nanos"])
    if fenster is None:
        fenster = []
    if isinstance(fenster, str):
        fenster = [fenster]
    if not isinstance(fenster, list) or not all(isinstance(f, str) for f in fenster):
        raise KonfigFehler("fenster_titel muss ein Text, eine Liste von Texten oder null sein")

    standard_dauer = _ms(daten.get("tastendauer_ms", 35), "tastendauer_ms")

    roh = daten.get("kombos")
    if not isinstance(roh, list) or not roh:
        raise KonfigFehler("'kombos' muss eine nicht-leere Liste sein")
    kombos = tuple(kombo_aus_dict(d, standard_dauer) for d in roh)

    gesehen: dict[Hotkey, str] = {}
    namen: set[str] = set()
    steuer_haupt = {h.haupt for h in steuer.values()}
    for k in kombos:
        if k.name.lower() in namen:
            raise KonfigFehler(f"Kombo-Name '{k.name}' doppelt")
        namen.add(k.name.lower())
        if k.hotkey in gesehen:
            raise KonfigFehler(f"Hotkey {k.hotkey} doppelt: '{gesehen[k.hotkey]}' und '{k.name}'")
        gesehen[k.hotkey] = k.name
        for name, h in steuer.items():
            if h.haupt == k.hotkey.haupt:
                raise KonfigFehler(f"Kombo '{k.name}': Hotkey {k.hotkey} kollidiert mit {name}")
        for i, s in enumerate(k.schritte, 1):
            if s.ziel is None:
                continue
            if s.ziel == k.hotkey.haupt:
                raise KonfigFehler(f"Kombo '{k.name}', Schritt {i}: drueckt den eigenen Hotkey "
                                   f"'{s.ziel}' - das wuerde sich selbst ausloesen")
            if s.ziel in steuer_haupt:
                raise KonfigFehler(f"Kombo '{k.name}', Schritt {i}: '{s.ziel}' ist eine "
                                   "Steuertaste (Pause/Stopp/Beenden)")

    return Konfig(kombos=kombos,
                  fenster_titel=tuple(f.strip().lower() for f in fenster if f.strip()),
                  pause_taste=pause, stopp_taste=stopp, beenden_taste=beenden)


def laden(pfad: str | Path) -> Konfig:
    p = Path(pfad)
    if not p.is_file():
        raise KonfigFehler(f"Datei nicht gefunden: {p}  (Beispiel anlegen: python -m kombo --beispiel {p})")
    try:
        text = p.read_text(encoding="utf-8-sig")
    except OSError as e:
        raise KonfigFehler(f"{p} nicht lesbar: {e}") from None
    try:
        daten = json.loads(text)
    except json.JSONDecodeError as e:
        raise KonfigFehler(f"{p}: kein gueltiges JSON - Zeile {e.lineno}, Spalte {e.colno}: {e.msg}") from None
    return aus_dict(daten)
