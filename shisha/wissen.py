"""Fachwissen, Bewertungsregeln und Prompts der Analyse-Engine.

Alles, was das Modell ueber Kopfbau wissen muss, steht hier — getrennt vom Code,
damit du es nachschaerfen kannst, ohne zu programmieren.

Leitsatz der Engine: Funktion vor Optik. Ein huebscher Kopf kann technisch
schlecht sein, ein unregelmaessiger technisch gut.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

# --------------------------------------------------------------------------
# Bewertung — die Gewichte sind bindend, der Server rechnet damit selbst nach
# --------------------------------------------------------------------------

GEWICHTE: dict[str, float] = {
    "tabak_verteilung": 0.20,
    "fuellhoehe": 0.15,
    "airflow": 0.15,
    "hitzemanagement": 0.20,
    "kopfgeometrie": 0.10,
    "tabak_kompatibilitaet": 0.10,
    "zielerreichung": 0.10,
}

KATEGORIE_NAMEN = {
    "tabak_verteilung": "Tabakverteilung",
    "fuellhoehe": "Fuellhoehe",
    "airflow": "Airflow",
    "hitzemanagement": "Hitzemanagement",
    "kopfgeometrie": "Kopfgeometrie",
    "tabak_kompatibilitaet": "Tabakkompatibilitaet",
    "zielerreichung": "Zielerreichung",
}

STUFEN = (
    (90, "excellent", "hervorragend"),
    (80, "very_good", "sehr gut"),
    (70, "good", "gut"),
    (60, "acceptable", "brauchbar"),
    (40, "needs_improvement", "verbesserungswuerdig"),
    (0, "poor", "schlecht"),
)


def stufe(score: float) -> tuple[str, str]:
    """(Schluessel, deutsches Wort) zur Punktzahl."""
    for grenze, key, wort in STUFEN:
        if score >= grenze:
            return key, wort
    return "poor", "schlecht"


def gesamtscore(scores: dict[str, float]) -> int:
    """Gewichtete Summe — deterministisch, nicht vom Modell abhaengig."""
    summe = sum(float(scores.get(kategorie, 0)) * gewicht for kategorie, gewicht in GEWICHTE.items())
    return int(round(max(0.0, min(100.0, summe))))


# --------------------------------------------------------------------------
# Zielprofile
# --------------------------------------------------------------------------

ZIELE: dict[str, dict[str, str]] = {
    "geschmack": {
        "name": "Maximaler Geschmack",
        "prioritaet": (
            "1. Aromaintensitaet 2. gleichmaessige Hitze 3. kontrollierte Temperatur "
            "4. lange Aromastabilitaet 5. Ueberhitzung vermeiden"
        ),
        "bau": (
            "Eher locker und etwas niedriger bauen, grosszuegiger Abstand zum HMD "
            "(3 bis 5 mm), Hitze zurueckhaltend dosieren."
        ),
    },
    "rauch": {
        "name": "Maximaler Rauch",
        "prioritaet": (
            "1. ausreichende Hitze 2. gleichmaessige Verdampfung 3. genug Tabakflaeche "
            "4. stabile Temperatur"
        ),
        "bau": (
            "Flaeche ausnutzen, gleichmaessig bis knapp unter den Rand (1 bis 3 mm), "
            "locker genug fuer Durchzug, eher eine Kohle mehr."
        ),
    },
    "lange_session": {
        "name": "Lange Session",
        "prioritaet": (
            "1. Temperaturkontrolle 2. gleichmaessige Hitze 3. langsame Tabakausnutzung "
            "4. Hotspots vermeiden"
        ),
        "bau": (
            "Etwas dichter und tiefer, grosser Abstand zum HMD, Kohle am Rand, "
            "lieber weniger Hitze und dafuer laenger."
        ),
    },
    "balanced": {
        "name": "Ausgewogen",
        "prioritaet": "Geschmack, Rauch, Sessiondauer und Temperatur gleich gewichtet.",
        "bau": "Locker eingestreut, eben, 2 bis 3 mm unter dem Rand, Kohle am Rand verteilt.",
    },
}


# --------------------------------------------------------------------------
# Phasen des Kopfbaus (fuer den Live-Coach)
# --------------------------------------------------------------------------


@dataclass(frozen=True)
class Phase:
    key: str
    name: str
    ziel: str
    achte_auf: str


PHASEN: tuple[Phase, ...] = (
    Phase(
        key="kopf",
        name="Kopf pruefen",
        ziel="Leerer, sauberer, trockener Kopf — von oben im Bild.",
        achte_auf=(
            "Kopfart und wenn moeglich Modell, Geometrie, Randhoehe, zentrale Oeffnung, "
            "Reste vom letzten Mal, Feuchtigkeit, verstopfte Loecher, Risse in der Glasur."
        ),
    ),
    Phase(
        key="tabak",
        name="Tabak vorbereiten",
        ziel="Aufgelockert, entrippt, ueberschuessige Molasse abgetropft.",
        achte_auf=(
            "Klumpen, gepresste Batzen, grobe Rippen, stehende Molasse, "
            "zu nasser oder ausgetrockneter Schnitt."
        ),
    ),
    Phase(
        key="einstreuen",
        name="Einstreuen",
        ziel="Locker und gleichmaessig einstreuen statt stopfen.",
        achte_auf=(
            "Verteilung ueber die ganze Flaeche, Fuellhoehe, ungewollte Verdichtung, "
            "Tabak der in Kamin oder Zugloecher faellt, Hohlraeume."
        ),
    ),
    Phase(
        key="glattziehen",
        name="Glattziehen und Rand",
        ziel="Ebene Oberflaeche, freier Rand, freie zentrale Oeffnung.",
        achte_auf=(
            "Hoehenunterschiede, Tabak am Rand, Randabstand in Millimetern, "
            "freier Kamin, Airflow-Blockaden."
        ),
    ),
    Phase(
        key="aufsatz",
        name="Folie oder HMD",
        ziel="Folie straff und gelocht oder HMD sauber und zentriert aufgesetzt.",
        achte_auf=(
            "Folie straff, Lochmuster gleichmaessig; beim HMD Sitz, Zentrierung, "
            "Abstand zum Tabak und ob der Boden den Tabak beruehrt."
        ),
    ),
    Phase(
        key="kohle",
        name="Kohle auflegen",
        ziel="Durchgegluehte Kohle richtig verteilt.",
        achte_auf=(
            "Kohle komplett durchgegluht, Anzahl, Verteilung, Abstand zueinander und "
            "zum Rand, keine Kohle mittig ueber dem Kamin."
        ),
    ),
)

PHASEN_NACH_KEY = {phase.key: phase for phase in PHASEN}
PHASEN_KEYS = [phase.key for phase in PHASEN]


def naechste_phase(key: str) -> str:
    if key not in PHASEN_NACH_KEY:
        return PHASEN_KEYS[0]
    return PHASEN_KEYS[min(PHASEN_KEYS.index(key) + 1, len(PHASEN_KEYS) - 1)]


# --------------------------------------------------------------------------
# Aufzaehlungen, an die sich das Modell halten muss
# --------------------------------------------------------------------------

QUELLEN = ["observed", "estimated", "unknown"]
SCHWEREGRADE = ["critical", "high", "medium", "low"]
SCHWERE_SYMBOL = {"critical": "🔴", "high": "🟠", "medium": "🟡", "low": "🟢"}

AKTIONEN = [
    "remove_tobacco",
    "add_tobacco",
    "loosen_tobacco",
    "redistribute_tobacco",
    "flatten_surface",
    "increase_hmd_distance",
    "decrease_hmd_distance",
    "reposition_hmd",
    "reposition_coals",
    "reduce_heat",
    "increase_heat",
]

AKTION_TEXT = {
    "remove_tobacco": "Tabak entfernen",
    "add_tobacco": "Tabak nachlegen",
    "loosen_tobacco": "auflockern",
    "redistribute_tobacco": "neu verteilen",
    "flatten_surface": "glattziehen",
    "increase_hmd_distance": "HMD-Abstand vergroessern",
    "decrease_hmd_distance": "HMD-Abstand verringern",
    "reposition_hmd": "HMD neu setzen",
    "reposition_coals": "Kohle umlegen",
    "reduce_heat": "Hitze reduzieren",
    "increase_heat": "Hitze erhoehen",
}

# Markertypen fuers AR-Overlay. Die Farben stehen in der Oberflaeche.
MARKER_TYPEN = ["remove", "loosen", "distribute", "ok", "fill_height", "hmd", "coal"]

ANALYSE_STATUS = ["ok", "insufficient_image", "no_head_detected"]


# --------------------------------------------------------------------------
# Wissensbloecke
# --------------------------------------------------------------------------

KOPFTYPEN = """\
- Phunnel: ein erhoehter Kamin in der Mitte, kein Loch im Boden. Molasse bleibt
  in der Schale. Tabak bis knapp unter die Kaminoberkante, Kamin bleibt frei.
- Killer- / Trichterkopf: mehrere Loecher im Trichterboden, braucht etwas weniger
  Tabak, Loecher duerfen nicht zugestopft werden, Molasse laeuft durch.
- Vortex: erhoehter Kamin mit seitlichen Schlitzen — Schlitze muessen frei bleiben.
- Steinkopf: saugt Feuchtigkeit, vertraegt mehr Hitze und etwas mehr Tabak.
- Turbinen- / Rauchsaeulenkopf: flache Schale, sehr locker bauen.
Grosse Koepfe brauchen mehr Hitze und vertragen mehr Kohle, kleine Koepfe
ueberhitzen schnell."""

TABAKPHYSIK = """\
Tabake verhalten sich unterschiedlich — es gibt keine pauschale Packregel:
- Dunkler, feuchter Blattschnitt (z. B. Dark-Blend-Typen): hitzefest, vertraegt
  dichter gepackt und mehr Kohle, kratzt sonst nicht sofort.
- Heller, trockener oder feiner Schnitt: hitzeempfindlich, locker bauen, weniger
  Kohle, groesserer HMD-Abstand.
- Sehr feuchte, stark saftende Tabake: leicht abtropfen lassen, sonst kocht die
  Molasse und der Kopf blubbert und schmeckt verbrannt.
- Grosse Blaetter zupfen, Rippen entfernen — Rippen brennen zuerst an.
Kennst du die Eigenschaften einer genannten Marke nicht sicher, sag das
ausdruecklich und bewerte nur, was du siehst."""

MENGEN = """\
Richtwerte, locker eingestreut: kleiner Kopf 8 bis 12 g, mittlerer Phunnel
12 bis 18 g, grosser Kopf 18 bis 25 g. Die exakte Gramm-Menge ist aus einem Foto
NICHT bestimmbar — hoechstens die relative Fuellhoehe.
Fuellhoehe wird in Millimetern unter dem Rand angegeben: 0 mm = randbuendig,
2 bis 3 mm ist der uebliche Zielbereich, negative Werte heissen: Tabak steht ueber
den Rand. Ueber Rand ist nur mit HMD und hitzefestem Tabak sinnvoll.
Kohle: drei bis vier 26er Wuerfel bei Folie, zwei bis drei bei HMD, am Rand
verteilt, nie mittig ueber dem Kamin, Abstand zueinander halten."""

TYPISCHE_FEHLER = """\
- Tabak beruehrt HMD oder Folie -> brennt an, wird bitter (kritisch).
- Zentrale Oeffnung verdeckt oder Zugloecher zugestopft -> kein Zug (kritisch).
- Zu dicht gepackt -> Airflow bricht ein, Kopf kratzt.
- Zu locker und zu niedrig -> duenner Rauch, Aroma bricht schnell weg.
- Ungleichmaessige Hoehe -> eine Seite verbrennt, die andere bleibt kalt.
- Hohlraeume unter der Oberflaeche -> Hitzenester, plotzliches Kratzen.
- Randueberfuellung -> Randanbrand, bitterer Beigeschmack.
- Molassepfuetze im Kopf -> blubbert, kocht, verbrannter Geschmack.
- Kohle nicht durchgegluht -> Kohlegeschmack und Kohlenmonoxid.
- Kohle mittig ueber dem Kamin -> Kern verbrennt zuerst."""


# --------------------------------------------------------------------------
# Nutzerkontext
# --------------------------------------------------------------------------


@dataclass
class Kontext:
    """Was der Nutzer ueber seinen Aufbau angegeben hat."""

    ziel: str = "balanced"
    kopf_modell: str = ""
    tabak_marke: str = ""
    tabak_sorte: str = ""
    hmd: str = ""
    kohlen: str = ""
    notiz: str = ""

    def als_text(self) -> str:
        zeilen = []
        profil = ZIELE.get(self.ziel, ZIELE["balanced"])
        zeilen.append(f"- Ziel des Nutzers: {profil['name']} — {profil['prioritaet']}")
        zeilen.append(f"- Bauempfehlung fuer dieses Ziel: {profil['bau']}")
        for beschriftung, wert in (
            ("Kopfmodell", self.kopf_modell),
            ("Tabakmarke", self.tabak_marke),
            ("Tabaksorte", self.tabak_sorte),
            ("HMD", self.hmd),
            ("Kohlen", self.kohlen),
            ("Anmerkung", self.notiz),
        ):
            if wert:
                zeilen.append(f"- {beschriftung}: {wert}")
        offen = self.fehlende_angaben()
        if offen:
            zeilen.append("- Nicht angegeben: " + ", ".join(offen))
        return "\n".join(zeilen)

    def fehlende_angaben(self) -> list[str]:
        """Nach Prioritaet: Kopfmodell, Tabak, HMD, Kohlen."""
        offen = []
        if not self.kopf_modell:
            offen.append("Kopfmodell")
        if not (self.tabak_marke or self.tabak_sorte):
            offen.append("Tabak")
        if not self.hmd:
            offen.append("HMD")
        if not self.kohlen:
            offen.append("Kohlenanzahl")
        return offen

    def als_dict(self) -> dict[str, Any]:
        return {
            "ziel": self.ziel,
            "ziel_name": ZIELE.get(self.ziel, ZIELE["balanced"])["name"],
            "kopf_modell": self.kopf_modell,
            "tabak_marke": self.tabak_marke,
            "tabak_sorte": self.tabak_sorte,
            "hmd": self.hmd,
            "kohlen": self.kohlen,
            "notiz": self.notiz,
            "fehlend": self.fehlende_angaben(),
        }


# --------------------------------------------------------------------------
# Promptbausteine
# --------------------------------------------------------------------------

ROLLE = """\
Du bist die Vision- und Analyse-Engine eines professionellen Hookah-Head-Analyzers:
Experte fuer Kopfbau, Tabakphysik, Hitzemanagement und Airflow.

Du bist kein Chatbot. Du schreibst keine Erklaerungen ausserhalb des JSON, kein
Markdown, keine Kommentare. Du gibst ausschliesslich ein gueltiges JSON-Objekt aus.

Leitsatz: Funktion vor Optik. Ein optisch schoener Kopf kann technisch schlecht
sein, ein unregelmaessiger technisch funktionieren. Bewerte, ob der Aufbau
funktional optimal ist — nicht, ob er huebsch aussieht.

Jede Analyse muss zu einer konkreten Handlung fuehren. Der Nutzer muss danach
genau wissen: Was muss ich wo und wie veraendern?"""

WAHRHEIT = """\
Visuelle Wahrheit — niemals Daten erfinden:
- "observed"  = direkt im Bild erkennbar
- "estimated" = aus dem Bild sinnvoll geschaetzt
- "unknown"   = nicht zuverlaessig bestimmbar
Die exakte Tabakmenge in Gramm ist aus einem Foto grundsaetzlich "unknown".
Die relative Fuellhoehe darf "estimated" sein.
Erfinde nie eine hohe Sicherheit fuer etwas, das nicht zuverlaessig erkennbar ist.
Ist der Kopf nicht sicher identifizierbar, setz das Modell auf null statt zu raten.

Bildqualitaet zuerst pruefen: Schaerfe, Licht, Perspektive, ist der ganze Kopf
sichtbar, ist der Tabak sichtbar, verdecken HMD oder Kohle die Flaeche, Kamerawinkel.
Erlaubt die Bildqualitaet keine zuverlaessige Analyse, setz analysis_status auf
"insufficient_image" und die Sicherheiten entsprechend niedrig. Ist gar kein Kopf
im Bild, setz "no_head_detected". Lieber ehrlich unvollstaendig als erfunden."""

SICHT = """\
So liest du das Bild:
- In der Regel schaust du von schraeg oben in den Kopf.
- Dichte auf einer Skala: 0 extrem locker, 25 locker, 50 mittel, 75 dicht,
  100 extrem dicht.
- Fuellhoehe in Millimetern unter der Oberkante des Kopfes. Negativ = ueber Rand.
- Beurteile nur, was du wirklich siehst. Verdecktes bleibt unbekannt."""

MARKER_REGELN = """\
AR-Markierungen: nur dort, wo sich die Position wirklich aus dem Bild ableiten
laesst. Koordinaten sind normalisiert auf die gesamte Bildflaeche:
x und y sind die linke obere Ecke, w und h Breite und Hoehe, alle 0.0 bis 1.0.
Typen: "remove" (Tabak weg), "loosen" (auflockern), "distribute" (verteilen),
"ok" (bereits optimal), "fill_height" (empfohlene Fuellhoehe), "hmd" (HMD-Position),
"coal" (Kohleposition). Label maximal drei Woerter."""

BEWERTUNGSREGELN = """\
Bewertung: sieben Kategorien, jede 0 bis 100.
- tabak_verteilung: Gleichmaessigkeit, Dichte, Klumpen, Luecken
- fuellhoehe: Hoehe, Abstand zum HMD, Ueber- oder Unterfuellung
- airflow: freie Luftwege, zentrale Oeffnung, Blockaden, Verdichtung
- hitzemanagement: HMD- und Kohleposition, Kontakt, Hotspots, Hitzeverteilung
- kopfgeometrie: passt der Aufbau zur Kopfform
- tabak_kompatibilitaet: passt die Packweise zur Tabakart
- zielerreichung: passt der Aufbau zum Ziel des Nutzers
Den Gesamtscore berechnet die App selbst aus diesen Werten — gib ihn nicht an.
Bewerte reproduzierbar: gleiches Bild und gleiche Angaben ergeben gleiche Zahlen.
Keine Gefaelligkeitsnoten."""

SCHEMA_VOLL = """\
Gib genau dieses JSON zurueck, ohne Text davor oder danach, ohne Code-Zaun:

{
  "analysis_status": "ok|insufficient_image|no_head_detected",
  "bildqualitaet": {"schaerfe": 0, "licht": 0, "perspektive": "oben|schraeg|seitlich|unklar",
                    "kopf_vollstaendig": true, "hinweis": "kurz, nur wenn etwas stoert"},
  "kopf": {"art": "phunnel|killer|vortex|steinkopf|turbine|unbekannt", "modell": null,
           "geometrie": "kurz", "zentrale_oeffnung_sichtbar": true,
           "quelle": "observed|estimated|unknown", "confidence": 0},
  "tabak": {"fuellhoehe_mm": 0.0, "fuellhoehe_quelle": "observed|estimated|unknown",
            "dichte": 0, "gleichmaessigkeit": 0, "klumpen": false, "luecken": false,
            "randkontakt": false, "ueber_rand": false, "menge_gramm": null,
            "quelle": "observed|estimated|unknown", "confidence": 0},
  "airflow": {"zentrale_oeffnung_frei": true, "blockade_risiko": "low|medium|high",
              "notiz": "kurz", "confidence": 0},
  "hmd": {"erkannt": false, "modell": null, "zentriert": null, "abstand_mm": null,
          "kontakt_tabak": null, "confidence": 0},
  "kohle": {"status": "visible|not_visible", "anzahl": null, "position": "kurz",
            "hotspot_risiko": "low|medium|high|unknown", "confidence": 0},
  "scores": {"tabak_verteilung": 0, "fuellhoehe": 0, "airflow": 0, "hitzemanagement": 0,
             "kopfgeometrie": 0, "tabak_kompatibilitaet": 0, "zielerreichung": 0},
  "probleme": [
    {"id": "kurzer_schluessel", "severity": "critical|high|medium|low",
     "kategorie": "tabak_verteilung|fuellhoehe|airflow|hitzemanagement|kopfgeometrie|tabak_kompatibilitaet|zielerreichung",
     "titel": "kurz", "beschreibung": "ein bis zwei Saetze", "confidence": 0,
     "aktion": "eine der erlaubten Aktionen"}
  ],
  "optimierungen": [
    {"schritt": 1, "aktion": "remove_tobacco", "bereich": "wo genau, aus Sicht des Nutzers",
     "text": "konkrete Handlung mit Menge oder Millimeterangabe",
     "wirkung": "was das bringt"}
  ],
  "ar_marker": [
    {"typ": "remove|loosen|distribute|ok|fill_height|hmd|coal",
     "x": 0.0, "y": 0.0, "w": 0.0, "h": 0.0, "label": "max drei Woerter",
     "aktion": "eine der erlaubten Aktionen"}
  ],
  "prognose": {"score_nach_optimierung": 0, "geschmack": "hoch|gleich|runter",
               "rauch": "hoch|gleich|runter", "dauer": "hoch|gleich|runter",
               "hitzerisiko": "hoch|gleich|runter",
               "verbesserung": {"tabak_verteilung": 0, "hitzemanagement": 0, "airflow": 0}},
  "confidence": {"gesamt": 0, "kopf_erkennung": 0, "tabak_analyse": 0, "fuellhoehe": 0,
                 "airflow": 0, "hitzemanagement": 0, "optimierung": 0},
  "rueckfrage": null,
  "coach_satz": "Ein gesprochener Satz fuer den Nutzer, maximal 14 Woerter."
}

Regeln:
- Erlaubte Aktionen: remove_tobacco, add_tobacco, loosen_tobacco, redistribute_tobacco,
  flatten_surface, increase_hmd_distance, decrease_hmd_distance, reposition_hmd,
  reposition_coals, reduce_heat, increase_heat.
- "optimierungen": nummerierte Schritt-fuer-Schritt-Anleitung, hoechstens sechs Schritte.
  Keine schwammigen Saetze wie "besser verteilen". Stattdessen: "Nimm bei 3 Uhr eine
  Prise Tabak weg und ziehe die Oberflaeche auf etwa 2 mm unter dem Rand glatt."
- "prognose.score_nach_optimierung": realistische Schaetzung, nie ueber 100 und nie
  unter dem aktuellen Wert.
- "confidence": Prozentwerte 0 bis 100.
- "rueckfrage": null, wenn die Angaben reichen. Sonst genau EINE Frage nach der
  wichtigsten fehlenden Angabe (Reihenfolge: Kopfmodell, Tabak, HMD, Kohlen, Ziel).
- "coach_satz": gesprochenes Deutsch, geduzt, ohne Aufzaehlung und ohne Emojis."""

SCHEMA_LIVE = """\
Gib genau dasselbe JSON-Schema zurueck wie bei der Vollanalyse — aber kurz halten,
weil es waehrend des Bauens im Sekundentakt laeuft:
- hoechstens zwei Eintraege in "probleme" (nur die dringendsten)
- hoechstens drei Eintraege in "optimierungen" (nur der naechste Handgriff)
- hoechstens vier "ar_marker"
- "prognose" darf mit Nullwerten gefuellt bleiben
- "coach_satz" ist das Wichtigste: der eine Satz, der jetzt hilft"""


def _wissensblock() -> str:
    return (
        f"Kopftypen:\n{KOPFTYPEN}\n\n"
        f"Tabakphysik:\n{TABAKPHYSIK}\n\n"
        f"Mengen und Hoehen:\n{MENGEN}\n\n"
        f"Typische Fehler:\n{TYPISCHE_FEHLER}"
    )


def _basis(kontext: Kontext) -> list[str]:
    return [
        ROLLE,
        _wissensblock(),
        WAHRHEIT,
        SICHT,
        f"Angaben des Nutzers:\n{kontext.als_text()}",
        BEWERTUNGSREGELN,
        MARKER_REGELN,
    ]


def live_prompt(
    phase_key: str,
    kontext: Kontext,
    verlauf: str = "",
    profil: str = "",
) -> str:
    """Prompt fuer die laufende Analyse waehrend des Bauens."""
    phase = PHASEN_NACH_KEY.get(phase_key, PHASEN[0])
    teile = _basis(kontext)
    teile.append(
        f"Der Nutzer baut gerade. Phase laut App: {phase.name}\n"
        f"Ziel dieser Phase: {phase.ziel}\n"
        f"Achte besonders auf: {phase.achte_auf}\n"
        "Bewerte trotzdem alle Kategorien — was in dieser Phase noch nicht beurteilbar\n"
        "ist, bekommt eine niedrige Sicherheit statt einer erfundenen Zahl."
    )
    if profil:
        teile.append(f"Was du aus frueheren Sessions dieses Nutzers weisst:\n{profil}")
    if verlauf:
        teile.append(
            "Deine letzten Beobachtungen zu diesem Kopf (nicht wiederholen, weiterfuehren):\n"
            f"{verlauf}"
        )
    teile.append(SCHEMA_VOLL)
    teile.append(SCHEMA_LIVE)
    return "\n\n".join(teile)


def analyse_prompt(kontext: Kontext, verlauf: str = "", profil: str = "") -> str:
    """Prompt fuer die abschliessende Vollanalyse des fertigen Kopfes."""
    teile = _basis(kontext)
    teile.append(
        "Der Kopf ist fertig gebaut. Fuehre die vollstaendige technische Analyse durch: "
        "sehen, verstehen, bewerten, erklaeren, verbessern. Nenne jede Verbesserung als "
        "konkreten Handgriff mit Ort und Menge."
    )
    if profil:
        teile.append(f"Was du aus frueheren Sessions dieses Nutzers weisst:\n{profil}")
    if verlauf:
        teile.append(f"Beobachtungen aus dem Bauverlauf:\n{verlauf}")
    teile.append(SCHEMA_VOLL)
    return "\n\n".join(teile)


# --------------------------------------------------------------------------
# Lernen aus Session-Rueckmeldungen
# --------------------------------------------------------------------------

FEEDBACK_FELDER = {
    "geschmack": "Geschmack (1 schwach bis 5 stark)",
    "rauch": "Rauchmenge (1 wenig bis 5 viel)",
    "kratzen": "Kratzen (1 gar nicht bis 5 stark)",
    "dauer_min": "Sessiondauer in Minuten",
    "hitze": "Hitze (1 zu kalt, 3 passend, 5 zu heiss)",
    "notiz": "freie Anmerkung",
}


def feedback_prompt_block(eintraege: list[dict[str, Any]]) -> str:
    """Fasst frueheres Feedback als Lernkontext zusammen."""
    if not eintraege:
        return ""

    zeilen = []
    for eintrag in eintraege[-6:]:
        teile = []
        if eintrag.get("ziel"):
            teile.append(f"Ziel {ZIELE.get(eintrag['ziel'], {}).get('name', eintrag['ziel'])}")
        if eintrag.get("score") is not None:
            teile.append(f"vorhergesagt {eintrag['score']}/100")
        for feld, beschriftung in FEEDBACK_FELDER.items():
            wert = eintrag.get(feld)
            if wert not in (None, ""):
                teile.append(f"{beschriftung.split(' (')[0]}: {wert}")
        if teile:
            zeilen.append("- " + ", ".join(str(t) for t in teile))

    if not zeilen:
        return ""
    return (
        "\n".join(zeilen)
        + "\nZieh daraus Konsequenzen fuer die Empfehlung: wiederholt zu heiss heisst "
        "weniger Kohle oder mehr Abstand, wiederholt zu wenig Geschmack heisst lockerer "
        "und niedriger bauen, fruehes Kratzen heisst zu dicht oder zu nah am HMD."
    )
