"""Eine Bausitzung — Zustand, Verlauf und das Aufbereiten der Modellantworten.

Das Modell sieht immer nur ein Einzelbild. Der Zusammenhang entsteht hier: wo wir
im Ablauf sind, was schon gesagt wurde, was sich ueber mehrere Bilder haelt.

Hier wird auch nachgerechnet: den Gesamtscore bestimmt die App aus den gewichteten
Einzelscores, nicht das Modell. Damit ist die Note reproduzierbar.
"""

from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field
from typing import Any

from jarvis.config import config

from . import wissen
from .wissen import Kontext

# Ein Hinweis wird nicht sofort wiederholt — sonst redet die App im Kreis.
HINWEIS_SPERRE = 22.0  # Sekunden

# So viele uebereinstimmende Bilder braucht es, bevor eine Phase weiterspringt.
FERTIG_SCHWELLE = 2

# Ab dieser Punktzahl gilt die aktuelle Phase als erledigt.
PHASE_FERTIG_SCORE = 78


# --------------------------------------------------------------------------
# Kleine Helfer zum Saeubern der Modellantwort
# --------------------------------------------------------------------------


def _zahl(wert: Any, min_wert: float, max_wert: float, standard: float = 0.0) -> float:
    try:
        zahl = float(wert)
    except (TypeError, ValueError):
        return standard
    if zahl != zahl:  # NaN
        return standard
    return max(min_wert, min(max_wert, zahl))


def _zahl_oder_none(wert: Any, min_wert: float, max_wert: float) -> float | None:
    if wert is None or wert == "":
        return None
    try:
        zahl = float(wert)
    except (TypeError, ValueError):
        return None
    if zahl != zahl:
        return None
    return max(min_wert, min(max_wert, zahl))


def _text(wert: Any, laenge: int = 240) -> str:
    if not isinstance(wert, str):
        return ""
    return wert.strip()[:laenge]


def _text_oder_none(wert: Any, laenge: int = 80) -> str | None:
    text = _text(wert, laenge)
    return text or None


def _wahl(wert: Any, erlaubt: list[str] | tuple[str, ...], standard: str) -> str:
    text = _text(wert, 32).lower().replace(" ", "_")
    return text if text in erlaubt else standard


def _bool_oder_none(wert: Any) -> bool | None:
    if wert is None:
        return None
    if isinstance(wert, bool):
        return wert
    if isinstance(wert, str):
        if wert.strip().lower() in ("true", "ja", "yes", "1"):
            return True
        if wert.strip().lower() in ("false", "nein", "no", "0"):
            return False
    return None


def _dict(wert: Any) -> dict[str, Any]:
    return wert if isinstance(wert, dict) else {}


def _liste(wert: Any) -> list[Any]:
    return wert if isinstance(wert, list) else []


# --------------------------------------------------------------------------
# Normalisierung — aus roher Modellantwort wird ein verlaessliches Ergebnis
# --------------------------------------------------------------------------


def normalisiere(roh: dict[str, Any], live: bool = False) -> dict[str, Any]:
    """Bringt die Modellantwort in genau die Form, auf die sich die App verlaesst."""
    status = _wahl(roh.get("analysis_status"), wissen.ANALYSE_STATUS, "ok")

    roh_scores = _dict(roh.get("scores"))
    scores = {feld: round(_zahl(roh_scores.get(feld), 0, 100)) for feld in wissen.GEWICHTE}

    # Die Note rechnen wir selbst — das Modell darf sie nicht setzen.
    gesamt = wissen.gesamtscore(scores)
    stufe_key, stufe_wort = wissen.stufe(gesamt)

    ergebnis: dict[str, Any] = {
        "analysis_status": status,
        "bildqualitaet": _bildqualitaet(_dict(roh.get("bildqualitaet"))),
        "kopf": _kopf(_dict(roh.get("kopf"))),
        "tabak": _tabak(_dict(roh.get("tabak"))),
        "airflow": _airflow(_dict(roh.get("airflow"))),
        "hmd": _hmd(_dict(roh.get("hmd"))),
        "kohle": _kohle(_dict(roh.get("kohle"))),
        "scores": scores,
        "gesamtscore": gesamt,
        "stufe": stufe_key,
        "stufe_text": stufe_wort,
        "probleme": _probleme(_liste(roh.get("probleme")), live),
        "optimierungen": _optimierungen(_liste(roh.get("optimierungen")), live),
        "ar_marker": _marker(_liste(roh.get("ar_marker")), live),
        "prognose": _prognose(_dict(roh.get("prognose")), gesamt),
        "confidence": _confidence(_dict(roh.get("confidence"))),
        "rueckfrage": _text_oder_none(roh.get("rueckfrage"), 160),
        "coach_satz": _text(roh.get("coach_satz"), 200),
        "dauer": roh.get("_dauer"),
    }

    # Ohne Kopf im Bild ist die Note bedeutungslos — dann lieber gar keine zeigen.
    if status != "ok":
        ergebnis["gesamtscore"] = None
        ergebnis["stufe"] = None
        ergebnis["stufe_text"] = None
    return ergebnis


def _bildqualitaet(roh: dict[str, Any]) -> dict[str, Any]:
    return {
        "schaerfe": round(_zahl(roh.get("schaerfe"), 0, 100)),
        "licht": round(_zahl(roh.get("licht"), 0, 100)),
        "perspektive": _wahl(roh.get("perspektive"), ("oben", "schraeg", "seitlich", "unklar"), "unklar"),
        "kopf_vollstaendig": bool(roh.get("kopf_vollstaendig", True)),
        "hinweis": _text(roh.get("hinweis"), 140),
    }


def _kopf(roh: dict[str, Any]) -> dict[str, Any]:
    arten = ("phunnel", "killer", "vortex", "steinkopf", "turbine", "unbekannt")
    return {
        "art": _wahl(roh.get("art"), arten, "unbekannt"),
        "modell": _text_oder_none(roh.get("modell"), 60),
        "geometrie": _text(roh.get("geometrie"), 140),
        "zentrale_oeffnung_sichtbar": _bool_oder_none(roh.get("zentrale_oeffnung_sichtbar")),
        "quelle": _wahl(roh.get("quelle"), wissen.QUELLEN, "unknown"),
        "confidence": round(_zahl(roh.get("confidence"), 0, 100)),
    }


def _tabak(roh: dict[str, Any]) -> dict[str, Any]:
    return {
        # Negative Werte heissen: Tabak steht ueber dem Rand.
        "fuellhoehe_mm": _zahl_oder_none(roh.get("fuellhoehe_mm"), -15, 30),
        "fuellhoehe_quelle": _wahl(roh.get("fuellhoehe_quelle"), wissen.QUELLEN, "unknown"),
        "dichte": round(_zahl(roh.get("dichte"), 0, 100)),
        "gleichmaessigkeit": round(_zahl(roh.get("gleichmaessigkeit"), 0, 100)),
        "klumpen": bool(roh.get("klumpen", False)),
        "luecken": bool(roh.get("luecken", False)),
        "randkontakt": bool(roh.get("randkontakt", False)),
        "ueber_rand": bool(roh.get("ueber_rand", False)),
        # Gramm sind aus einem Foto nicht bestimmbar — hoechstens eine Spanne als Text.
        "menge_gramm": _text_oder_none(roh.get("menge_gramm"), 40),
        "quelle": _wahl(roh.get("quelle"), wissen.QUELLEN, "unknown"),
        "confidence": round(_zahl(roh.get("confidence"), 0, 100)),
    }


def _airflow(roh: dict[str, Any]) -> dict[str, Any]:
    return {
        "zentrale_oeffnung_frei": _bool_oder_none(roh.get("zentrale_oeffnung_frei")),
        "blockade_risiko": _wahl(roh.get("blockade_risiko"), ("low", "medium", "high"), "low"),
        "notiz": _text(roh.get("notiz"), 140),
        "confidence": round(_zahl(roh.get("confidence"), 0, 100)),
    }


def _hmd(roh: dict[str, Any]) -> dict[str, Any]:
    erkannt = bool(roh.get("erkannt", False))
    return {
        "erkannt": erkannt,
        "modell": _text_oder_none(roh.get("modell"), 60),
        "zentriert": _bool_oder_none(roh.get("zentriert")),
        "abstand_mm": _zahl_oder_none(roh.get("abstand_mm"), 0, 40),
        "kontakt_tabak": _bool_oder_none(roh.get("kontakt_tabak")),
        "confidence": round(_zahl(roh.get("confidence"), 0, 100)),
    }


def _kohle(roh: dict[str, Any]) -> dict[str, Any]:
    sichtbar = _wahl(roh.get("status"), ("visible", "not_visible"), "not_visible")
    return {
        "status": sichtbar,
        "anzahl": None if sichtbar == "not_visible" else _zahl_oder_none(roh.get("anzahl"), 0, 12),
        "position": _text(roh.get("position"), 120),
        "hotspot_risiko": _wahl(
            roh.get("hotspot_risiko"), ("low", "medium", "high", "unknown"), "unknown"
        ),
        "confidence": round(_zahl(roh.get("confidence"), 0, 100)),
    }


def _probleme(roh: list[Any], live: bool) -> list[dict[str, Any]]:
    ergebnis = []
    for eintrag in roh:
        if not isinstance(eintrag, dict):
            continue
        titel = _text(eintrag.get("titel"), 80)
        if not titel:
            continue
        severity = _wahl(eintrag.get("severity"), wissen.SCHWEREGRADE, "medium")
        ergebnis.append(
            {
                "id": _text(eintrag.get("id"), 40) or titel.lower().replace(" ", "_")[:40],
                "severity": severity,
                "symbol": wissen.SCHWERE_SYMBOL[severity],
                "kategorie": _wahl(eintrag.get("kategorie"), list(wissen.GEWICHTE), "tabak_verteilung"),
                "titel": titel,
                "beschreibung": _text(eintrag.get("beschreibung"), 300),
                "confidence": round(_zahl(eintrag.get("confidence"), 0, 100)),
                "aktion": _wahl(eintrag.get("aktion"), wissen.AKTIONEN, "redistribute_tobacco"),
            }
        )

    # Kritisches zuerst — die Oberflaeche zeigt oben nur die ersten Eintraege.
    rang = {grad: index for index, grad in enumerate(wissen.SCHWEREGRADE)}
    ergebnis.sort(key=lambda p: (rang[p["severity"]], -p["confidence"]))
    return ergebnis[: 2 if live else 8]


def _optimierungen(roh: list[Any], live: bool) -> list[dict[str, Any]]:
    ergebnis = []
    for index, eintrag in enumerate(roh, start=1):
        if not isinstance(eintrag, dict):
            continue
        text = _text(eintrag.get("text"), 300)
        if not text:
            continue
        aktion = _wahl(eintrag.get("aktion"), wissen.AKTIONEN, "redistribute_tobacco")
        ergebnis.append(
            {
                "schritt": int(_zahl(eintrag.get("schritt"), 1, 20, index)),
                "aktion": aktion,
                "aktion_text": wissen.AKTION_TEXT[aktion],
                "bereich": _text(eintrag.get("bereich"), 120),
                "text": text,
                "wirkung": _text(eintrag.get("wirkung"), 160),
            }
        )
    ergebnis.sort(key=lambda o: o["schritt"])
    for nummer, eintrag in enumerate(ergebnis, start=1):
        eintrag["schritt"] = nummer
    return ergebnis[: 3 if live else 6]


def _marker(roh: list[Any], live: bool) -> list[dict[str, Any]]:
    ergebnis = []
    for eintrag in roh:
        if not isinstance(eintrag, dict):
            continue
        x = _zahl_oder_none(eintrag.get("x"), -5, 5)
        y = _zahl_oder_none(eintrag.get("y"), -5, 5)
        if x is None or y is None:
            continue
        # Knapp daneben wird zurechtgerueckt, weit daneben ist geraten und fliegt raus.
        if not (-0.2 <= x <= 1.2 and -0.2 <= y <= 1.2):
            continue
        x = min(max(x, 0.0), 1.0)
        y = min(max(y, 0.0), 1.0)
        breite = _zahl(eintrag.get("w"), 0.02, 1, 0.15)
        hoehe = _zahl(eintrag.get("h"), 0.02, 1, 0.15)
        ergebnis.append(
            {
                "typ": _wahl(eintrag.get("typ"), wissen.MARKER_TYPEN, "distribute"),
                # Kasten notfalls in das Bild zurueckschieben.
                "x": round(min(x, 1 - min(breite, 1)), 4),
                "y": round(min(y, 1 - min(hoehe, 1)), 4),
                "w": round(min(breite, 1), 4),
                "h": round(min(hoehe, 1), 4),
                "label": _text(eintrag.get("label"), 40),
                "aktion": _wahl(eintrag.get("aktion"), wissen.AKTIONEN, "redistribute_tobacco"),
            }
        )
    return ergebnis[: 4 if live else 8]


def _prognose(roh: dict[str, Any], gesamt: int) -> dict[str, Any]:
    richtungen = ("hoch", "gleich", "runter")
    nachher = round(_zahl(roh.get("score_nach_optimierung"), 0, 100, gesamt))
    verbesserung = _dict(roh.get("verbesserung"))
    return {
        # Nach der Optimierung soll es nicht schlechter werden.
        "score_nach_optimierung": max(nachher, gesamt),
        "geschmack": _wahl(roh.get("geschmack"), richtungen, "gleich"),
        "rauch": _wahl(roh.get("rauch"), richtungen, "gleich"),
        "dauer": _wahl(roh.get("dauer"), richtungen, "gleich"),
        "hitzerisiko": _wahl(roh.get("hitzerisiko"), richtungen, "gleich"),
        "verbesserung": {
            feld: round(_zahl(verbesserung.get(feld), 0, 100))
            for feld in ("tabak_verteilung", "hitzemanagement", "airflow")
        },
    }


def _confidence(roh: dict[str, Any]) -> dict[str, int]:
    felder = (
        "gesamt",
        "kopf_erkennung",
        "tabak_analyse",
        "fuellhoehe",
        "airflow",
        "hitzemanagement",
        "optimierung",
    )
    werte = {feld: round(_zahl(roh.get(feld), 0, 100)) for feld in felder}
    if not werte["gesamt"]:
        andere = [wert for feld, wert in werte.items() if feld != "gesamt" and wert]
        werte["gesamt"] = round(sum(andere) / len(andere)) if andere else 0
    return werte


# --------------------------------------------------------------------------
# Die Sitzung
# --------------------------------------------------------------------------


@dataclass
class Sitzung:
    """Der Zustand eines Kopfbaus von leer bis bewertet."""

    id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])
    kontext: Kontext = field(default_factory=Kontext)
    phase: str = wissen.PHASEN_KEYS[0]
    gestartet: float = field(default_factory=time.time)
    verlauf: list[dict[str, Any]] = field(default_factory=list)
    letzte: dict[str, Any] | None = None
    analyse: dict[str, Any] | None = None  # die abschliessende Vollanalyse

    _gesagt: dict[str, float] = field(default_factory=dict)
    _fertig_zaehler: int = 0

    # -- Ablauf ------------------------------------------------------------
    @property
    def phase_info(self) -> wissen.Phase:
        return wissen.PHASEN_NACH_KEY[self.phase]

    def phase_setzen(self, key: str) -> None:
        if key in wissen.PHASEN_NACH_KEY and key != self.phase:
            self.phase = key
            self._fertig_zaehler = 0
            self._gesagt.clear()

    def weiter(self) -> str:
        self.phase_setzen(wissen.naechste_phase(self.phase))
        return self.phase

    def zuruecksetzen(self) -> None:
        self.phase = wissen.PHASEN_KEYS[0]
        self.verlauf.clear()
        self.letzte = None
        self.analyse = None
        self._gesagt.clear()
        self._fertig_zaehler = 0
        self.gestartet = time.time()

    # -- Ein analysiertes Bild einsortieren --------------------------------
    def aufnehmen(self, ergebnis: dict[str, Any], auto_weiter: bool = True) -> dict[str, Any]:
        ergebnis = dict(ergebnis)
        ergebnis["zeit"] = time.time()
        ergebnis["sprechen"] = self._darf_sprechen(ergebnis.get("coach_satz", ""))

        gewechselt = False
        if self._phase_erledigt(ergebnis):
            self._fertig_zaehler += 1
            if auto_weiter and self._fertig_zaehler >= FERTIG_SCHWELLE:
                if self.phase != wissen.PHASEN_KEYS[-1]:
                    self.weiter()
                    gewechselt = True
        else:
            self._fertig_zaehler = 0

        ergebnis["phase"] = self.phase
        ergebnis["phase_name"] = self.phase_info.name
        ergebnis["phase_gewechselt"] = gewechselt
        ergebnis["fortschritt"] = self.fortschritt()

        self.letzte = ergebnis
        self.verlauf.append(ergebnis)
        grenze = max(4, config.shisha_verlauf)
        if len(self.verlauf) > grenze:
            del self.verlauf[:-grenze]
        return ergebnis

    def _phase_erledigt(self, ergebnis: dict[str, Any]) -> bool:
        """Die Phase gilt als geschafft, wenn nichts Dringendes mehr offen ist."""
        if ergebnis.get("analysis_status") != "ok":
            return False
        if any(p["severity"] in ("critical", "high") for p in ergebnis.get("probleme", [])):
            return False
        score = ergebnis.get("gesamtscore")
        confidence = (ergebnis.get("confidence") or {}).get("gesamt", 0)
        return bool(score and score >= PHASE_FERTIG_SCORE and confidence >= 50)

    def _darf_sprechen(self, satz: str) -> bool:
        schluessel = satz.strip().lower()
        if not schluessel:
            return False
        jetzt = time.time()
        if jetzt - self._gesagt.get(schluessel, 0.0) < HINWEIS_SPERRE:
            return False
        self._gesagt[schluessel] = jetzt
        for alt, zeit in list(self._gesagt.items()):
            if jetzt - zeit > HINWEIS_SPERRE * 4:
                self._gesagt.pop(alt, None)
        return True

    def fortschritt(self) -> float:
        index = wissen.PHASEN_KEYS.index(self.phase)
        return round((index + 1) / len(wissen.PHASEN_KEYS), 2)

    # -- Kontext fuer den naechsten Prompt ---------------------------------
    def verlaufstext(self, anzahl: int = 4) -> str:
        zeilen = []
        for eintrag in self.verlauf[-anzahl:]:
            if eintrag.get("analysis_status") != "ok":
                continue
            teile = [f"Phase {eintrag.get('phase', '?')}"]
            if eintrag.get("gesamtscore") is not None:
                teile.append(f"Score {eintrag['gesamtscore']}")
            tabak = eintrag.get("tabak") or {}
            if tabak.get("fuellhoehe_mm") is not None:
                teile.append(f"Fuellhoehe ca. {tabak['fuellhoehe_mm']} mm unter Rand")
            if tabak.get("dichte"):
                teile.append(f"Dichte {tabak['dichte']}")
            probleme = [p["titel"] for p in eintrag.get("probleme", [])]
            if probleme:
                teile.append("Probleme: " + ", ".join(probleme))
            if eintrag.get("coach_satz"):
                teile.append(f'gesagt: "{eintrag["coach_satz"]}"')
            zeilen.append("- " + "; ".join(teile))

        kopf = (self.letzte or {}).get("kopf") or {}
        if kopf.get("art") and kopf["art"] != "unbekannt":
            beschreibung = kopf["art"] + (f" ({kopf['modell']})" if kopf.get("modell") else "")
            zeilen.insert(0, f"- Kopf bisher erkannt: {beschreibung}")
        return "\n".join(zeilen)

    # -- Darstellung -------------------------------------------------------
    def zustand(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "phase": self.phase,
            "phase_name": self.phase_info.name,
            "phase_ziel": self.phase_info.ziel,
            "phasen": [
                {"key": phase.key, "name": phase.name, "ziel": phase.ziel}
                for phase in wissen.PHASEN
            ],
            "kontext": self.kontext.als_dict(),
            "fortschritt": self.fortschritt(),
            "bilder": len(self.verlauf),
            "laeuft_seit": round(time.time() - self.gestartet),
            "analyse": self.analyse,
        }
