"""Test des Rueckwegs ueber den eigenen Rechner.

Der Server bewertet nichts mehr — er reicht Bild und Prompt an Claude weiter und
gibt den Antworttext zurueck. Getestet wird genau das: Weiterreichen, Grenzen
einhalten, Fehler verstaendlich melden und die Freigabe fuer den Browser setzen.

Die Bewertungslogik selbst steckt in web/shisha/engine.js und wird von
tests/test_engine.mjs geprueft.

Ausfuehren:  python tests/test_shisha.py
"""

import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PIL import Image
from fastapi.testclient import TestClient

import shisha.server as srv
from shisha.analyse import AnalyseFehler, verkleinern

# --- Bilder werden vor dem Verschicken geschrumpft --------------------------
gross = io.BytesIO()
Image.new("RGB", (3000, 2000), (90, 60, 40)).save(gross, "JPEG")
klein = verkleinern(gross.getvalue(), 768)
assert max(Image.open(io.BytesIO(klein)).size) == 768, Image.open(io.BytesIO(klein)).size
assert len(klein) < len(gross.getvalue())

# kaputte Daten duerfen nicht zum Absturz fuehren
assert verkleinern(b"kein bild", 768) == b"kein bild"

# --- Server mit einem Analysator-Ersatz -------------------------------------
bild = io.BytesIO()
Image.new("RGB", (1024, 768), (120, 80, 50)).save(bild, "JPEG")
roh = bild.getvalue()


class FakeAnalysator:
    label = "Test"
    prompts: list[str] = []

    async def rohtext(self, daten, prompt):
        FakeAnalysator.prompts.append(prompt)
        return '{"analysis_status": "ok", "coach_satz": "Passt."}'


srv.rueckweg.analysator = FakeAnalysator()
srv.rueckweg.startfehler = None
client = TestClient(srv.create_app())

status = client.get("/api/shisha/status").json()
assert status["bereit"] and status["denkapparat"] == "Test", status

# --- Weiterreichen ----------------------------------------------------------
antwort = client.post(
    "/api/shisha/proxy",
    files={"bild": ("k.jpg", roh, "image/jpeg")},
    data={"prompt": "Mein Prompt aus der App"},
)
assert antwort.status_code == 200, antwort.text
assert antwort.json()["text"].startswith('{"analysis_status"')
assert FakeAnalysator.prompts[-1] == "Mein Prompt aus der App", "Prompt kommt aus der App"
assert antwort.headers["access-control-allow-origin"] == "*", "Browser braucht die Freigabe"

# Vorabfrage des Browsers
vorab = client.options("/api/shisha/proxy")
assert vorab.status_code == 204
assert vorab.headers["access-control-allow-origin"] == "*"

# --- Grenzen ----------------------------------------------------------------
ohne_prompt = client.post("/api/shisha/proxy", files={"bild": ("k.jpg", roh, "image/jpeg")})
assert ohne_prompt.status_code == 400 and "Prompt" in ohne_prompt.json()["fehler"]

leer = client.post(
    "/api/shisha/proxy", files={"bild": ("k.jpg", b"", "image/jpeg")}, data={"prompt": "x"}
)
assert leer.status_code == 400 and "leeres Bild" in leer.json()["fehler"]

zu_lang = client.post(
    "/api/shisha/proxy",
    files={"bild": ("k.jpg", roh, "image/jpeg")},
    data={"prompt": "x" * (srv.MAX_PROMPT + 1)},
)
assert zu_lang.status_code == 400 and "zu lang" in zu_lang.json()["fehler"]

zu_gross = client.post(
    "/api/shisha/proxy",
    files={"bild": ("k.jpg", b"\xff" * (srv.MAX_BILD + 1), "image/jpeg")},
    data={"prompt": "x"},
)
assert zu_gross.status_code == 400 and "zu gross" in zu_gross.json()["fehler"]

# --- Fehler verstaendlich melden --------------------------------------------
class KaputtAnalysator(FakeAnalysator):
    async def rohtext(self, daten, prompt):
        raise AnalyseFehler("Kontingent erschoepft")


srv.rueckweg.analysator = KaputtAnalysator()
kaputt = client.post(
    "/api/shisha/proxy", files={"bild": ("k.jpg", roh, "image/jpeg")}, data={"prompt": "x"}
)
assert kaputt.status_code == 502 and "Kontingent" in kaputt.json()["fehler"]
assert kaputt.headers["access-control-allow-origin"] == "*"

srv.rueckweg.analysator = None
srv.rueckweg.startfehler = "Claude Code nicht gefunden"
nicht_bereit = client.post(
    "/api/shisha/proxy", files={"bild": ("k.jpg", roh, "image/jpeg")}, data={"prompt": "x"}
)
assert nicht_bereit.status_code == 503 and "Claude Code" in nicht_bereit.json()["fehler"]

# --- Oberflaeche wird ausgeliefert ------------------------------------------
for pfad in ("/shisha", "/shisha/manifest.webmanifest", "/shisha/ar.js",
             "/shisha/engine.js", "/shisha/spec.json", "/shisha/sw.js"):
    assert client.get(pfad).status_code == 200, pfad

# --- Zertifikat fuers Handy --------------------------------------------------
from shisha.zertifikat import zertifikat_besorgen

cert, key = zertifikat_besorgen(erneuern=True)
assert cert.exists() and key.exists() and cert.stat().st_size > 500

print("ALLE TESTS BESTANDEN")
