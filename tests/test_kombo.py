"""Test des Kombo-Makros - ohne echte Tastatur.

Geprueft werden: Tasten- und Hotkey-Namen, strenge Konfigurationspruefung,
genaue Reihenfolge und Zeiten beim Abspielen, sofortiger Abbruch ohne
haengende Tasten, die drei Modi und der Fensterfilter.

Ausfuehren:  python tests/test_kombo.py
"""

import json
import sys
import tempfile
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from kombo import tasten
from kombo.__main__ import main
from kombo.ablauf import Abspieler
from kombo.ausgabe import TrockenAusgabe
from kombo.konfig import KonfigFehler, aus_dict, laden, schritt
from kombo.steuerung import Steuerung


def fehler(daten, enthaelt):
    try:
        aus_dict(daten)
    except KonfigFehler as e:
        assert enthaelt in str(e), (enthaelt, str(e))
        return
    raise AssertionError(f"kein Fehler, erwartet: {enthaelt}")


def basis(**kombo):
    k = {"name": "A", "hotkey": "1", "schritte": ["taste q"]}
    k.update(kombo)
    return {"kombos": [k]}


# --- Tasten & Hotkeys --------------------------------------------------------
assert tasten.taste("Strg") == "ctrl"
assert tasten.taste("LEERTASTE") == "space"
assert tasten.taste("f12") == "f12"
assert tasten.maustaste("left") == "links"
hk = tasten.hotkey("Strg+Umschalt+F")
assert hk.haupt == "f" and hk.mods == {"ctrl", "shift"}, hk
assert tasten.hotkey("x1").haupt == "maus4" and tasten.hotkey("maus4").ist_maus
for kaputt in ("", "alt+", "shift", "q+1", "links", "gibtsnicht"):
    try:
        tasten.hotkey(kaputt)
    except tasten.TastenFehler:
        pass
    else:
        raise AssertionError(f"Hotkey '{kaputt}' haette scheitern muessen")
# Windows-Codes: Buchstaben, Ziffern, F-Tasten, Numpad
assert tasten.VK["a"] == 0x41 and tasten.VK["0"] == 0x30
assert tasten.VK["f1"] == 0x70 and tasten.VK["f24"] == 0x87 and tasten.VK["num5"] == 0x65
assert tasten.SCAN_ERSATZ["q"] == 0x10 and tasten.SCAN_ERSATZ["f10"] == 0x44

# --- Schritte ----------------------------------------------------------------
assert str(schritt("HALTE w 200")) == "halte w 200"
assert schritt("click right").ziel == "rechts"
assert schritt("warte 0").ms == 0
for kaputt, teil in [("hüpf", "unbekannter Befehl"), ("warte", "braucht 1"),
                     ("warte -5", "zwischen 0"), ("warte 1.5", "ganze Zahl"),
                     ("taste ö", "unbekannte Taste"), ("klick oben", "Maustaste")]:
    try:
        schritt(kaputt)
    except KonfigFehler as e:
        assert teil in str(e), (kaputt, str(e))
    else:
        raise AssertionError(kaputt)

# --- Konfiguration -----------------------------------------------------------
kf = aus_dict(basis())
assert kf.fenster_titel == ("nanos",) and str(kf.stopp_taste) == "f9"
assert aus_dict({**basis(), "fenster_titel": None}).fenster_titel == ()
fehler({}, "kombos")
fehler(basis(schritte=[]), "nicht-leere Liste")
fehler(basis(schritte=["taste 1"]), "eigenen Hotkey")
fehler(basis(schritte=["taste f9"]), "Steuertaste")
fehler(basis(hotkey="f8"), "kollidiert")
fehler(basis(modus="turbo"), "modus")
fehler(basis(modus="halten", tastendauer_ms=0), "Schleife ohne Wartezeit")
fehler(basis(wiederholen=0), "wiederholen")
fehler(basis(schritte=["hoch shift"]), "ohne vorheriges runter")
fehler({"kombos": [{"name": "A", "hotkey": "1", "schritte": ["taste q"]},
                   {"name": "B", "hotkey": "1", "schritte": ["taste e"]}]}, "doppelt")
fehler({**basis(), "pause_taste": "f9"}, "verschieden")

# Beispiel aus dem Paket ist gueltig
bsp = laden(Path(__file__).resolve().parent.parent / "kombo" / "beispiel.json")
assert len(bsp.kombos) == 5 and bsp.kombo("ki-salve").modus == "halten"

# kaputtes JSON nennt die Zeile
with tempfile.TemporaryDirectory() as tmp:
    p = Path(tmp) / "k.json"
    p.write_text('{"kombos": [\n  {,}\n]}', encoding="utf-8")
    try:
        laden(p)
    except KonfigFehler as e:
        assert "Zeile 2" in str(e), str(e)
    else:
        raise AssertionError("kaputtes JSON")

# --- Abspielen: Reihenfolge und Zeiten --------------------------------------
k = aus_dict(basis(schritte=["runter shift", "klick links", "warte 50", "halte w 60",
                             "halteklick rechts 40", "taste e"],
                   tastendauer_ms=10)).kombos[0]
assert k.dauer_ms() == 10 + 50 + 60 + 40 + 10
aus = TrockenAusgabe()
t0 = time.perf_counter()
erg = Abspieler(aus).abspielen(k, threading.Event())
dauer = (time.perf_counter() - t0) * 1000
assert not erg.abgebrochen and erg.fehler is None and erg.durchgaenge == 1, erg
folge = [(a, n, r) for _, a, n, r in aus.protokoll]
assert folge == [
    ("t", "shift", True), ("m", "links", True), ("m", "links", False),
    ("t", "w", True), ("t", "w", False), ("m", "rechts", True), ("m", "rechts", False),
    ("t", "e", True), ("t", "e", False), ("t", "shift", False),
], folge
assert "shift" in erg.geloest and not aus.gedrueckt()
assert 160 <= dauer < 600, dauer
# Haltezeit stimmt
t_w = [t for t, _, n, _ in aus.protokoll if n == "w"]
assert 55 <= (t_w[1] - t_w[0]) * 1000 < 200, t_w

# Wiederholen
k3 = aus_dict(basis(wiederholen=3, abstand_ms=5, tastendauer_ms=1)).kombos[0]
aus = TrockenAusgabe()
erg = Abspieler(aus).abspielen(k3, threading.Event())
assert erg.durchgaenge == 3 and len(aus.protokoll) == 6

# --- Abbruch: sofort, nichts bleibt gedrueckt -------------------------------
lang = aus_dict(basis(schritte=["runter shift", "halte w 5000"])).kombos[0]
aus = TrockenAusgabe()
abbruch = threading.Event()
threading.Timer(0.1, abbruch.set).start()
t0 = time.perf_counter()
erg = Abspieler(aus).abspielen(lang, abbruch)
assert erg.abgebrochen and (time.perf_counter() - t0) < 1.0
assert not aus.gedrueckt(), aus.gedrueckt()


# --- Steuerung: Hotkeys, Modi, Pause, Fenster -------------------------------
class FehlerAusgabe(TrockenAusgabe):
    def _taste(self, name, runter):
        if name == "e" and runter:
            raise RuntimeError("blockiert")
        super()._taste(name, runter)


def steuerung(daten, titel="Nanos World - Zenkai", ausgabe=None):
    aus = ausgabe or TrockenAusgabe()
    zustand = {"titel": titel}
    st = Steuerung(aus_dict(daten), Abspieler(aus), titel_quelle=lambda: zustand["titel"])
    return st, aus, zustand


daten = {"kombos": [
    {"name": "Kette", "hotkey": "1", "tastendauer_ms": 5, "schritte": ["klick links", "warte 5"]},
    {"name": "AltKette", "hotkey": "alt+1", "tastendauer_ms": 5, "schritte": ["taste q"]},
    {"name": "Halten", "hotkey": "2", "modus": "halten", "abstand_ms": 20, "schritte": ["taste e"]},
    {"name": "Schalter", "hotkey": "maus4", "modus": "umschalten", "abstand_ms": 20,
     "schritte": ["klick links"]},
    {"name": "Lang", "hotkey": "3", "schritte": ["halte w 5000"]},
]}

# einfache Kombo; Modifikator waehlt die spezifischere
st, aus, _ = steuerung(daten)
st.runter("1"); st.warten(2)
assert st.ergebnisse[-1].kombo == "Kette"
st.runter("alt"); st.runter("1"); st.warten(2); st.hoch("1"); st.hoch("alt")
assert st.ergebnisse[-1].kombo == "AltKette"

# halten: laeuft, solange gedrueckt
st.runter("2"); time.sleep(0.25); st.hoch("2"); st.warten(2)
assert st.ergebnisse[-1].durchgaenge >= 3, st.ergebnisse[-1]

# umschalten: zweiter Druck beendet
st.runter("maus4"); st.hoch("maus4"); time.sleep(0.2)
assert st.aktiv is not None and st.aktiv.name == "Schalter"
st.runter("1")                      # andere Hotkeys werden ignoriert, waehrend etwas laeuft
st.runter("maus4"); st.warten(2)
assert st.aktiv is None and st.ergebnisse[-1].kombo == "Schalter" and st.ergebnisse[-1].durchgaenge >= 2

# Stopp-Taste bricht sofort ab
st.runter("3"); time.sleep(0.05)
t0 = time.perf_counter(); st.runter("f9"); st.warten(2)
assert st.ergebnisse[-1].abgebrochen and time.perf_counter() - t0 < 1.0
assert not aus.gedrueckt()

# Pause: nichts feuert, bis wieder aktiviert
n = len(st.ergebnisse)
st.runter("f8"); st.runter("1"); time.sleep(0.05)
assert st.pausiert and len(st.ergebnisse) == n and st.aktiv is None
st.runter("f8"); st.runter("1"); st.warten(2)
assert len(st.ergebnisse) == n + 1

# falsches Fenster: kein Start; Fenster verlassen: Abbruch
st, aus, zustand = steuerung(daten, titel="Discord")
st.runter("1"); time.sleep(0.05)
assert not st.ergebnisse and st.aktiv is None
zustand["titel"] = "nanos world"
wache = threading.Thread(target=st.fenster_wache, args=(0.02,), daemon=True)
wache.start()
st.runter("3"); time.sleep(0.05)
zustand["titel"] = "Discord"; st.warten(2)
assert st.ergebnisse[-1].abgebrochen and not aus.gedrueckt()

# Beenden
st.runter("f10")
assert st.beendet.is_set()

# Fehler beim Senden: wird gemeldet, nichts bleibt haengen
st, aus, _ = steuerung({"kombos": [{"name": "X", "hotkey": "1",
                                    "schritte": ["runter shift", "taste e"]}]},
                       ausgabe=FehlerAusgabe())
st.runter("1"); st.warten(2)
assert "blockiert" in st.ergebnisse[-1].fehler and not aus.gedrueckt()

# --- Kommandozeile -----------------------------------------------------------
with tempfile.TemporaryDirectory() as tmp:
    ziel = Path(tmp) / "kombo.json"
    assert main(["--beispiel", str(ziel)]) == 0 and ziel.is_file()
    assert main(["--beispiel", str(ziel)]) == 1              # nicht still ueberschreiben
    assert main(["-k", str(ziel), "--pruefen"]) == 0
    assert main(["-k", str(ziel), "--zeige", "Nahkampf-Kette"]) == 0
    assert main(["-k", str(ziel), "--zeige", "gibtsnicht"]) == 1
    assert main(["-k", str(ziel), "--test", "Dash-Angriff", "--trocken", "--countdown", "0"]) == 0
    assert main(["-k", str(ziel), "--test", "Ki-Salve", "--trocken", "--countdown", "0"]) == 0
    json.loads(ziel.read_text(encoding="utf-8"))
    assert main(["-k", str(Path(tmp) / "fehlt.json"), "--pruefen"]) == 1

print("Kombo-Makro: alle Pruefungen bestanden.")
