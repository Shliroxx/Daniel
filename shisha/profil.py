"""Lernspeicher — was aus frueheren Sessions gelernt wurde.

Nach einer Session sagt der Nutzer, wie der Kopf tatsaechlich gelaufen ist. Das
landet hier auf der Platte und geht in die naechsten Analysen ein. Ziel ist nicht
irgendein guter Kopf, sondern der Kopf, der fuer diesen Nutzer funktioniert.
"""

from __future__ import annotations

import json
import logging
import threading
import time
from typing import Any

from jarvis.config import config

from . import wissen

log = logging.getLogger("shisha.profil")

MAX_EINTRAEGE = 60

_sperre = threading.Lock()


def _pfad():
    verzeichnis = config.data_dir / "shisha"
    verzeichnis.mkdir(parents=True, exist_ok=True)
    return verzeichnis / "profil.json"


def laden() -> dict[str, Any]:
    pfad = _pfad()
    if not pfad.exists():
        return {"sessions": []}
    try:
        daten = json.loads(pfad.read_text(encoding="utf-8"))
        if isinstance(daten, dict) and isinstance(daten.get("sessions"), list):
            return daten
    except (OSError, json.JSONDecodeError) as exc:
        log.warning("Profil unlesbar, fange neu an: %s", exc)
    return {"sessions": []}


def _speichern(daten: dict[str, Any]) -> None:
    pfad = _pfad()
    temp = pfad.with_suffix(".tmp")
    temp.write_text(json.dumps(daten, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.replace(pfad)


def merken(eintrag: dict[str, Any]) -> dict[str, Any]:
    """Haengt eine Session-Rueckmeldung an und liefert das aktualisierte Profil."""
    with _sperre:
        daten = laden()
        eintrag = dict(eintrag)
        eintrag["zeit"] = time.time()
        daten["sessions"].append(eintrag)
        daten["sessions"] = daten["sessions"][-MAX_EINTRAEGE:]
        _speichern(daten)
        return daten


def lernkontext() -> str:
    """Der Textblock, der in die Prompts wandert."""
    return wissen.feedback_prompt_block(laden().get("sessions", []))


def treffsicherheit() -> dict[str, Any]:
    """Wie gut die Vorhersagen bisher zur Wirklichkeit passten.

    Verglichen wird der vorhergesagte Score mit einer Note, die aus dem Feedback
    entsteht: Geschmack und Rauch ziehen hoch, Kratzen und falsche Hitze ziehen
    runter. Das ist grob, reicht aber, um einen Trend zu sehen.
    """
    sessions = [s for s in laden().get("sessions", []) if s.get("score") is not None]
    bewertbar = [s for s in sessions if _erlebte_note(s) is not None]
    if not bewertbar:
        return {"sessions": len(sessions), "vergleichbar": 0, "abweichung": None}

    abweichungen = [abs(float(s["score"]) - _erlebte_note(s)) for s in bewertbar]
    mittel = sum(abweichungen) / len(abweichungen)
    return {
        "sessions": len(sessions),
        "vergleichbar": len(bewertbar),
        "abweichung": round(mittel, 1),
        "genauigkeit": round(max(0.0, 100.0 - mittel), 1),
    }


def _erlebte_note(session: dict[str, Any]) -> float | None:
    """Rechnet das Nutzerfeedback in eine Note von 0 bis 100 um."""
    geschmack = session.get("geschmack")
    rauch = session.get("rauch")
    kratzen = session.get("kratzen")
    hitze = session.get("hitze")
    if geschmack is None and rauch is None:
        return None

    punkte = 0.0
    gewicht = 0.0
    if geschmack is not None:
        punkte += (float(geschmack) / 5) * 40
        gewicht += 40
    if rauch is not None:
        punkte += (float(rauch) / 5) * 30
        gewicht += 30
    if kratzen is not None:
        punkte += (1 - (float(kratzen) - 1) / 4) * 20
        gewicht += 20
    if hitze is not None:
        # 3 ist perfekt, 1 und 5 sind gleich weit daneben.
        punkte += (1 - abs(float(hitze) - 3) / 2) * 10
        gewicht += 10

    if gewicht == 0:
        return None
    return round(punkte / gewicht * 100, 1)
