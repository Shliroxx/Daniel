"""Optionaler Rueckweg ueber den eigenen Rechner.

Die App auf dem Handy denkt selbst — sie baut den Prompt, fragt ein Bildmodell und
wertet die Antwort aus. Dieser Server wird nur gebraucht, wenn die Analyse ueber
das Claude-Abo laufen soll statt ueber einen Anbieter im Netz. Dann nimmt er Bild
und Prompt entgegen, reicht beides an Claude weiter und gibt den Text zurueck.

Zwei Betriebsarten:
- eingehaengt in den Jarvis-Server (Route /shisha)
- eigenstaendig ueber `python -m shisha`
"""

from __future__ import annotations

import logging
import os
import secrets
from pathlib import Path
from typing import Any

from fastapi import APIRouter, FastAPI, File, Form, Header, Response, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from jarvis.config import config

from .analyse import AnalyseFehler, Analysator

log = logging.getLogger("shisha.server")

WEB_DIR = Path(__file__).resolve().parent.parent / "web" / "shisha"

MAX_BILD = 8 * 1024 * 1024      # groesstes Bild, das wir annehmen
MAX_PROMPT = 60_000             # laengster Prompt, den wir durchreichen
MAX_BILDER = 5                  # mehr Winkel schickt die App nicht


def _token_besorgen() -> str:
    """Das Losungswort, ohne das der Proxy nichts tut.

    Der Proxy laesst Claude Code auf diesem Rechner arbeiten. Ohne Schranke
    koennte jede Webseite, die im Browser offen ist, ihn ansprechen — fuer ein
    Formular mit Datei braucht der Browser keine Vorabfrage, und die Antwort
    waere wegen der Freigabe unten auch noch lesbar. Das Losungswort steht in
    der .env oder wird beim Start gewuerfelt und in der Startzeile angezeigt;
    in der App wird es einmal neben der Serveradresse eingetragen.
    """
    aus_umgebung = os.getenv("SHISHA_TOKEN", "").strip()
    return aus_umgebung or secrets.token_urlsafe(18)


TOKEN = _token_besorgen()


class Rueckweg:
    """Haelt den Analysator und meldet, ob er einsatzbereit ist."""

    def __init__(self) -> None:
        self.startfehler: str | None = None
        try:
            self.analysator: Analysator | None = Analysator()
        except AnalyseFehler as exc:
            self.analysator = None
            self.startfehler = str(exc)
            log.error("%s", exc)


rueckweg = Rueckweg()
router = APIRouter()


def _freigeben(antwort: Response) -> Response:
    """Erlaubt der App, von einer anderen Adresse aus anzufragen.

    Die App liegt in der Regel auf einer festen Webadresse, dieser Rechner steht
    im Heimnetz — fuer den Browser sind das zwei verschiedene Herkuenfte. Ohne
    diese Kopfzeilen blockt er die Anfrage.

    Die Freigabe ist bewusst weit; die Schranke ist das Losungswort im Kopf
    X-Shisha-Token. Wer es nicht hat, kommt auch von einer erlaubten Herkunft
    nicht durch — und weil es ein eigener Kopf ist, muss der Browser vorher
    fragen, statt die Anfrage einfach abzuschicken.
    """
    antwort.headers["Access-Control-Allow-Origin"] = "*"
    antwort.headers["Access-Control-Allow-Headers"] = "content-type, x-shisha-token"
    antwort.headers["Access-Control-Allow-Methods"] = "POST, OPTIONS"
    antwort.headers["Access-Control-Max-Age"] = "86400"
    return antwort


@router.options("/api/shisha/proxy")
async def proxy_vorabfrage() -> Response:
    return _freigeben(Response(status_code=204))


# --------------------------------------------------------------------------
# Oberflaeche
# --------------------------------------------------------------------------


@router.get("/shisha")
async def oberflaeche() -> FileResponse:
    return FileResponse(WEB_DIR / "index.html")


@router.get("/shisha/manifest.webmanifest")
async def manifest() -> FileResponse:
    return FileResponse(WEB_DIR / "manifest.webmanifest", media_type="application/manifest+json")


# --------------------------------------------------------------------------
# API
# --------------------------------------------------------------------------


@router.get("/api/shisha/status")
async def status() -> dict[str, Any]:
    return {
        "bereit": rueckweg.analysator is not None,
        "fehler": rueckweg.startfehler,
        "denkapparat": rueckweg.analysator.label if rueckweg.analysator else None,
    }


async def _bilddaten(datei: UploadFile) -> bytes | None:
    """Liest ein Bild — und hoert bei der Obergrenze auf zu lesen.

    Erst lesen und dann die Groesse pruefen hiesse: ein absichtlich riesiger
    Upload liegt vorher komplett im Speicher.
    """
    daten = await datei.read(MAX_BILD + 1)
    return daten if len(daten) <= MAX_BILD else None


@router.post("/api/shisha/proxy")
async def proxy(
    bild: list[UploadFile] = File(default=[]),
    prompt: str = Form(""),
    x_shisha_token: str = Header(default=""),
) -> JSONResponse:
    """Bild und Prompt an Claude weiterreichen und den Rohtext zurueckgeben.

    Bewusst dumm: der Server bewertet nichts und kennt die Regeln nicht. Die
    stehen in web/shisha/spec.json und werden von der App angewendet — so gibt es
    nur eine Stelle, an der die Bewertungslogik lebt.
    """
    # Zuerst das Losungswort — vor allem anderen, auch vor dem Lesen der Daten.
    if not secrets.compare_digest(x_shisha_token, TOKEN):
        return _freigeben(JSONResponse(
            {"ok": False, "fehler": "Losungswort fehlt oder stimmt nicht"}, status_code=401,
        ))

    if rueckweg.analysator is None:
        return _freigeben(JSONResponse(
            {"ok": False, "fehler": rueckweg.startfehler or "Analyse nicht bereit"},
            status_code=503,
        ))

    if not bild:
        return _freigeben(JSONResponse({"ok": False, "fehler": "kein Bild mitgeschickt"}, status_code=400))
    if len(bild) > MAX_BILDER:
        return _freigeben(JSONResponse({"ok": False, "fehler": "zu viele Bilder"}, status_code=400))

    daten: list[bytes] = []
    for datei in bild:
        einzeln = await _bilddaten(datei)
        if einzeln is None:
            return _freigeben(JSONResponse({"ok": False, "fehler": "Bild zu gross"}, status_code=400))
        if not einzeln:
            return _freigeben(JSONResponse({"ok": False, "fehler": "leeres Bild"}, status_code=400))
        daten.append(einzeln)

    prompt = (prompt or "").strip()
    if not prompt:
        return _freigeben(JSONResponse({"ok": False, "fehler": "kein Prompt mitgeschickt"}, status_code=400))
    if len(prompt) > MAX_PROMPT:
        return _freigeben(JSONResponse({"ok": False, "fehler": "Prompt zu lang"}, status_code=400))

    try:
        text = await rueckweg.analysator.rohtext(daten, prompt)
    except AnalyseFehler as exc:
        log.warning("Analyse fehlgeschlagen: %s", exc)
        return _freigeben(JSONResponse({"ok": False, "fehler": str(exc)}, status_code=502))
    except Exception as exc:
        log.exception("Analyse abgestuerzt")
        return _freigeben(JSONResponse({"ok": False, "fehler": str(exc)}, status_code=500))

    return _freigeben(JSONResponse({"ok": True, "text": text}))


# --------------------------------------------------------------------------
# Eigenstaendiger Betrieb
# --------------------------------------------------------------------------


def create_app() -> FastAPI:
    """Der Analyzer allein, ohne den Rest von Jarvis."""
    app = FastAPI(title="Hookah-Analyzer")
    app.include_router(router)
    if WEB_DIR.exists():
        # Am Ende einhaengen, damit /shisha weiter die Oberflaeche liefert.
        app.mount("/shisha", StaticFiles(directory=str(WEB_DIR), html=True), name="shisha-web")

    @app.get("/")
    async def wurzel() -> FileResponse:
        return FileResponse(WEB_DIR / "index.html")

    return app


def main() -> None:
    import uvicorn

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)-7s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    kwargs: dict[str, Any] = {}
    schema = "http"
    if config.shisha_tls:
        from .zertifikat import zertifikat_besorgen

        cert, key = zertifikat_besorgen()
        kwargs = {"ssl_certfile": str(cert), "ssl_keyfile": str(key)}
        schema = "https"

    if rueckweg.analysator:
        log.info("Rueckweg ueber: %s", rueckweg.analysator.label)
    else:
        log.error("Rueckweg nicht bereit: %s", rueckweg.startfehler)

    for adresse in _lokale_adressen():
        log.info("Am iPhone oeffnen: %s://%s:%s/shisha", schema, adresse, config.shisha_port)

    log.info("Losungswort fuer die App (Feld neben der Serveradresse): %s", TOKEN)
    if not os.getenv("SHISHA_TOKEN", "").strip():
        log.info("Bleibt es dasselbe: SHISHA_TOKEN in die .env eintragen.")

    uvicorn.run(create_app(), host=config.host, port=config.shisha_port, log_level="warning", **kwargs)


def _lokale_adressen() -> list[str]:
    """IP-Adressen im Heimnetz — damit du weisst, was du am Handy eintippst."""
    import socket

    adressen = {"localhost"}
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            adressen.add(sock.getsockname()[0])
    except OSError:
        pass
    return sorted(adressen)


if __name__ == "__main__":
    main()
