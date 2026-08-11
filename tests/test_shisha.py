"""End-zu-End-Test des Hookah-Analyzers.

Statt Claude zu fragen antwortet hier ein Ersatz-Analysator mit einer festen
Modellantwort — absichtlich mit Fehlern darin (unsinnige Aktion, Marker ausserhalb
des Bildes, geschoente Note). Der Test prueft, dass der Server das geradezieht.

Ausfuehren:  python tests/test_shisha.py
"""
import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image
from fastapi.testclient import TestClient

from jarvis.config import config
for _f in (config.data_dir / 'shisha').glob('*.json'):
    _f.unlink()
import shisha.server as srv
from shisha.analyse import json_aus_text, verkleinern

ANTWORT = {
  "analysis_status": "ok",
  "bildqualitaet": {"schaerfe": 82, "licht": 70, "perspektive": "schraeg", "kopf_vollstaendig": True, "hinweis": ""},
  "kopf": {"art": "phunnel", "modell": "Oblako M", "geometrie": "mittel, tief",
           "zentrale_oeffnung_sichtbar": True, "quelle": "observed", "confidence": 88},
  "tabak": {"fuellhoehe_mm": 1.5, "fuellhoehe_quelle": "estimated", "dichte": 72,
            "gleichmaessigkeit": 55, "klumpen": True, "luecken": False, "randkontakt": True,
            "ueber_rand": False, "menge_gramm": "ca. 14-17 g", "quelle": "estimated", "confidence": 71},
  "airflow": {"zentrale_oeffnung_frei": True, "blockade_risiko": "medium", "notiz": "leicht verdichtet", "confidence": 65},
  "hmd": {"erkannt": False, "modell": None, "zentriert": None, "abstand_mm": None, "kontakt_tabak": None, "confidence": 20},
  "kohle": {"status": "not_visible", "anzahl": 3, "position": "", "hotspot_risiko": "unknown", "confidence": 10},
  "scores": {"tabak_verteilung": 60, "fuellhoehe": 70, "airflow": 65, "hitzemanagement": 55,
             "kopfgeometrie": 85, "tabak_kompatibilitaet": 75, "zielerreichung": 60},
  "gesamtscore": 99,   # muss vom Server ueberschrieben werden
  "probleme": [
     {"id":"randkontakt","severity":"critical","kategorie":"fuellhoehe","titel":"Tabak am Rand",
      "beschreibung":"Bei 3 Uhr liegt Tabak an der Wand.","confidence":80,"aktion":"remove_tobacco"},
     {"id":"klumpen","severity":"medium","kategorie":"tabak_verteilung","titel":"Klumpen",
      "beschreibung":"Verdichteter Batzen links.","confidence":62,"aktion":"loosen_tobacco"},
     {"id":"low","severity":"low","kategorie":"airflow","titel":"Leicht dicht","beschreibung":"x","confidence":40,"aktion":"loosen_tobacco"}
  ],
  "optimierungen": [
     {"schritt":2,"aktion":"loosen_tobacco","bereich":"links","text":"Lockere den Batzen auf.","wirkung":"gleichmaessige Hitze"},
     {"schritt":1,"aktion":"remove_tobacco","bereich":"3 Uhr","text":"Nimm bei 3 Uhr eine Prise weg.","wirkung":"kein Randanbrand"},
     {"schritt":3,"aktion":"quatsch","bereich":"","text":"Oberflaeche glattziehen.","wirkung":""}
  ],
  "ar_marker": [
     {"typ":"remove","x":0.62,"y":0.30,"w":0.18,"h":0.15,"label":"hier weg","aktion":"remove_tobacco"},
     {"typ":"loosen","x":0.2,"y":0.4,"w":0.2,"h":0.2,"label":"auflockern","aktion":"loosen_tobacco"},
     {"typ":"kaputt","x":1.4,"y":0.4,"w":0.2,"h":0.2,"label":"ausserhalb","aktion":"x"},
     {"typ":"fill_height","x":0.3,"y":0.55,"w":0.4,"h":0.05,"label":"Zielhoehe","aktion":"add_tobacco"}
  ],
  "prognose": {"score_nach_optimierung": 40, "geschmack":"hoch","rauch":"gleich","dauer":"hoch",
               "hitzerisiko":"runter","verbesserung":{"tabak_verteilung":88,"hitzemanagement":80,"airflow":85}},
  "confidence": {"gesamt":0,"kopf_erkennung":88,"tabak_analyse":71,"fuellhoehe":60,"airflow":65,
                 "hitzemanagement":40,"optimierung":70},
  "rueckfrage": None,
  "coach_satz": "Nimm bei drei Uhr etwas Tabak weg."
}

# 1) JSON-Extraktion aus verrauschtem Text
assert json_aus_text('```json\n{"a": 1}\n```')["a"] == 1
assert json_aus_text('Hier bitte: {"a": {"b": 2}} — fertig.')["a"]["b"] == 2

# 2) Bild verkleinern
gross = io.BytesIO(); Image.new("RGB", (3000, 2000), (90, 60, 40)).save(gross, "JPEG")
klein = verkleinern(gross.getvalue(), 768)
assert max(Image.open(io.BytesIO(klein)).size) == 768, Image.open(io.BytesIO(klein)).size

# 3) Server durchspielen
bild = io.BytesIO(); Image.new("RGB", (1024, 768), (120, 80, 50)).save(bild, "JPEG")
roh = bild.getvalue()

class FakeAnalysator:
    label = "Test"
    prompts = []
    async def analysiere(self, daten, prompt):
        FakeAnalysator.prompts.append(prompt)
        return dict(ANTWORT, _dauer=0.1)

srv.analyzer.analysator = FakeAnalysator()
srv.analyzer.startfehler = None
client = TestClient(srv.create_app())

status = client.get("/api/shisha/status").json()
assert status["bereit"] and len(status["phasen"]) == 6, status
assert abs(sum(status["gewichte"].values()) - 1.0) < 1e-9

s = client.post("/api/shisha/sitzung", json={"kontext": {
    "ziel": "geschmack", "kopf_modell": "Oblako Phunnel M", "tabak_marke": "Adalya",
    "tabak_sorte": "Love 66", "hmd": "Kaloud Lotus", "kohlen": "3 x 26er"}}).json()
sid = s["sitzung"]["id"]
assert s["sitzung"]["kontext"]["ziel_name"] == "Maximaler Geschmack"
assert s["sitzung"]["kontext"]["fehlend"] == []

r = client.post(f"/api/shisha/live?id={sid}", files={"bild": ("k.jpg", roh, "image/jpeg")}).json()
a = r["analyse"]

# Score deterministisch nachgerechnet: 60*.2+70*.15+65*.15+55*.2+85*.1+75*.1+60*.1 = 64.75 -> 65
assert a["gesamtscore"] == 65, a["gesamtscore"]
assert a["stufe"] == "acceptable", a["stufe"]
assert len(a["probleme"]) == 2 and a["probleme"][0]["severity"] == "critical"
assert a["probleme"][0]["symbol"] == "🔴"
assert [o["schritt"] for o in a["optimierungen"]] == [1, 2, 3]
assert a["optimierungen"][0]["aktion"] == "remove_tobacco"
assert a["optimierungen"][2]["aktion"] == "redistribute_tobacco"  # ungueltig -> Standard
assert len(a["ar_marker"]) == 3, a["ar_marker"]           # x=1.4 ist geraten -> raus
assert all(0 <= m["x"] <= 1 and m["x"] + m["w"] <= 1.0001 for m in a["ar_marker"])
assert a["prognose"]["score_nach_optimierung"] == 65      # darf nicht unter aktuell fallen
assert a["confidence"]["gesamt"] > 0                       # aus den anderen gemittelt
assert a["tabak"]["menge_gramm"] == "ca. 14-17 g"
assert a["sprechen"] is True
r2 = client.post(f"/api/shisha/live?id={sid}", files={"bild": ("k.jpg", roh, "image/jpeg")}).json()
assert r2["analyse"]["sprechen"] is False                  # kein Wiederholen

# Kontext landet im Prompt
p = FakeAnalysator.prompts[-1]
for teil in ("Oblako Phunnel M", "Adalya", "Kaloud Lotus", "Maximaler Geschmack", "3 x 26er"):
    assert teil in p, teil

full = client.post(f"/api/shisha/analyse?id={sid}", files={"bild": ("k.jpg", roh, "image/jpeg")}).json()
assert len(full["analyse"]["probleme"]) == 3   # Vollanalyse kuerzt nicht auf 2
assert full["analyse"]["kontext"]["kopf_modell"] == "Oblako Phunnel M"

fb = client.post("/api/shisha/feedback", json={"id": sid, "geschmack": 2, "rauch": 4,
                                               "kratzen": 4, "hitze": 5, "dauer_min": 45,
                                               "notiz": "hat nach 40 min gekratzt"}).json()
assert fb["ok"] and fb["treffsicherheit"]["sessions"] == 1, fb
assert fb["treffsicherheit"]["abweichung"] is not None

# Gelerntes muss in den naechsten Prompt
client.post(f"/api/shisha/live?id={sid}", files={"bild": ("k.jpg", roh, "image/jpeg")})
assert "Kratzen: 4" in FakeAnalysator.prompts[-1], FakeAnalysator.prompts[-1][-800:]

# Kein Kopf im Bild -> keine Note
class LeerAnalysator(FakeAnalysator):
    async def analysiere(self, daten, prompt):
        return {"analysis_status": "no_head_detected", "coach_satz": "Kopf ins Bild halten."}
srv.analyzer.analysator = LeerAnalysator()
leer = client.post(f"/api/shisha/live?id={sid}", files={"bild": ("k.jpg", roh, "image/jpeg")}).json()
assert leer["analyse"]["gesamtscore"] is None and leer["analyse"]["stufe"] is None

# Fehlerfall sauber gemeldet
from shisha.analyse import AnalyseFehler
class KaputtAnalysator(FakeAnalysator):
    async def analysiere(self, daten, prompt):
        raise AnalyseFehler("Kontingent erschoepft")
srv.analyzer.analysator = KaputtAnalysator()
kaputt = client.post(f"/api/shisha/live?id={sid}", files={"bild": ("k.jpg", roh, "image/jpeg")})
assert kaputt.status_code == 502 and "Kontingent" in kaputt.json()["fehler"]

leerbild = client.post(f"/api/shisha/live?id={sid}", files={"bild": ("k.jpg", b"", "image/jpeg")})
assert leerbild.status_code == 400

# Oberflaeche wird ausgeliefert
assert client.get("/shisha").status_code == 200
assert client.get("/shisha/manifest.webmanifest").status_code == 200
assert client.get("/shisha/static/ar.js").status_code == 200

# Zertifikat
from shisha.zertifikat import zertifikat_besorgen
cert, key = zertifikat_besorgen(erneuern=True)
assert cert.exists() and key.exists() and cert.stat().st_size > 500

print("ALLE TESTS BESTANDEN")
