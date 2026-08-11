"""HTTP-Schnittstelle des Hookah-Analyzers.

Laesst sich auf zwei Arten betreiben:
- eingehaengt in den Jarvis-Server (Route /shisha)
- eigenstaendig ueber `python -m shisha` — dann ohne Mikrofon und WhatsApp
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from fastapi import APIRouter, FastAPI, Request, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from jarvis.config import config

from . import profil, wissen
from .analyse import AnalyseFehler, Analysator
from .sitzung import Sitzung, normalisiere
from .wissen import Kontext

log = logging.getLogger("shisha.server")

WEB_DIR = Path(__file__).resolve().parent.parent / "web" / "shisha"

# Groesste Bildmenge, die wir annehmen — schuetzt vor versehentlichen Uploads.
MAX_BILD = 8 * 1024 * 1024


class Analyzer:
    """Haelt den Bildanalysator und die laufenden Sitzungen."""

    def __init__(self) -> None:
        self.sitzungen: dict[str, Sitzung] = {}
        self.startfehler: str | None = None
        try:
            self.analysator: Analysator | None = Analysator()
        except AnalyseFehler as exc:
            self.analysator = None
            self.startfehler = str(exc)
            log.error("%s", exc)

    def sitzung(self, sitzung_id: str | None) -> Sitzung:
        """Holt eine Sitzung oder legt sie an."""
        if sitzung_id and sitzung_id in self.sitzungen:
            return self.sitzungen[sitzung_id]
        neu = Sitzung()
        if sitzung_id:
            neu.id = sitzung_id[:32]
        self.sitzungen[neu.id] = neu

        # Alte Sitzungen aufraeumen — das Ding laeuft eventuell tagelang durch.
        if len(self.sitzungen) > 20:
            for tot in sorted(self.sitzungen.values(), key=lambda s: s.gestartet)[:-20]:
                self.sitzungen.pop(tot.id, None)
        return neu


analyzer = Analyzer()
router = APIRouter()


async def _bild_lesen(bild: UploadFile | None) -> bytes:
    if bild is None:
        raise ValueError("kein Bild mitgeschickt")
    daten = await bild.read()
    if not daten:
        raise ValueError("leeres Bild")
    if len(daten) > MAX_BILD:
        raise ValueError("Bild zu gross")
    return daten


def _kontext_uebernehmen(sitzung: Sitzung, body: dict[str, Any]) -> None:
    """Uebernimmt die Angaben des Nutzers — leere Felder aendern nichts."""
    roh = body.get("kontext") if isinstance(body.get("kontext"), dict) else body
    ziel = str(roh.get("ziel", "")).strip().lower()
    if ziel in wissen.ZIELE:
        sitzung.kontext.ziel = ziel
    for feld in ("kopf_modell", "tabak_marke", "tabak_sorte", "hmd", "kohlen", "notiz"):
        if feld in roh:
            setattr(sitzung.kontext, feld, str(roh.get(feld) or "").strip()[:80])


async def _analysieren(sitzung: Sitzung, daten: bytes, live: bool) -> dict[str, Any]:
    """Prompt bauen, Bild schicken, Ergebnis saeubern."""
    lernkontext = profil.lernkontext()
    if live:
        prompt = wissen.live_prompt(
            sitzung.phase, sitzung.kontext, sitzung.verlaufstext(), lernkontext
        )
    else:
        prompt = wissen.analyse_prompt(sitzung.kontext, sitzung.verlaufstext(8), lernkontext)

    roh = await analyzer.analysator.analysiere(daten, prompt)
    return normalisiere(roh, live=live)


def _nicht_bereit() -> JSONResponse:
    return JSONResponse(
        {"ok": False, "fehler": analyzer.startfehler or "Analyse nicht bereit"},
        status_code=503,
    )


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
        "bereit": analyzer.analysator is not None,
        "fehler": analyzer.startfehler,
        "denkapparat": analyzer.analysator.label if analyzer.analysator else None,
        "phasen": [
            {"key": phase.key, "name": phase.name, "ziel": phase.ziel} for phase in wissen.PHASEN
        ],
        "ziele": {key: wert["name"] for key, wert in wissen.ZIELE.items()},
        "kategorien": wissen.KATEGORIE_NAMEN,
        "gewichte": wissen.GEWICHTE,
        "feedback_felder": wissen.FEEDBACK_FELDER,
        "treffsicherheit": profil.treffsicherheit(),
        "sitzungen": len(analyzer.sitzungen),
    }


@router.post("/api/shisha/sitzung")
async def sitzung_starten(request: Request) -> JSONResponse:
    """Neue Bausitzung anlegen oder eine bestehende zuruecksetzen."""
    try:
        body = await request.json()
    except Exception:
        body = {}

    sitzung = analyzer.sitzung(body.get("id"))
    sitzung.zuruecksetzen()
    _kontext_uebernehmen(sitzung, body)
    return JSONResponse({"ok": True, "sitzung": sitzung.zustand()})


@router.post("/api/shisha/kontext")
async def kontext_setzen(request: Request) -> JSONResponse:
    """Angaben zu Kopf, Tabak, HMD, Kohle und Ziel nachtragen."""
    body = await request.json()
    sitzung = analyzer.sitzung(body.get("id"))
    _kontext_uebernehmen(sitzung, body)
    return JSONResponse({"ok": True, "sitzung": sitzung.zustand()})


@router.get("/api/shisha/sitzung/{sitzung_id}")
async def sitzung_lesen(sitzung_id: str) -> JSONResponse:
    sitzung = analyzer.sitzungen.get(sitzung_id)
    if sitzung is None:
        return JSONResponse({"ok": False, "fehler": "unbekannte Sitzung"}, status_code=404)
    return JSONResponse({"ok": True, "sitzung": sitzung.zustand()})


@router.post("/api/shisha/phase")
async def phase_wechseln(request: Request) -> JSONResponse:
    body = await request.json()
    sitzung = analyzer.sitzung(body.get("id"))
    if body.get("weiter"):
        sitzung.weiter()
    elif body.get("phase"):
        sitzung.phase_setzen(str(body["phase"]))
    return JSONResponse({"ok": True, "sitzung": sitzung.zustand()})


@router.post("/api/shisha/live")
async def live(bild: UploadFile | None = None, id: str = "", phase: str = "") -> JSONResponse:
    """Laufende Analyse waehrend des Bauens — knapp gehalten, laeuft im Dauerbetrieb."""
    if analyzer.analysator is None:
        return _nicht_bereit()
    try:
        daten = await _bild_lesen(bild)
    except ValueError as exc:
        return JSONResponse({"ok": False, "fehler": str(exc)}, status_code=400)

    sitzung = analyzer.sitzung(id or None)
    if phase:
        sitzung.phase_setzen(phase)

    try:
        ergebnis = await _analysieren(sitzung, daten, live=True)
    except AnalyseFehler as exc:
        log.warning("Live-Analyse fehlgeschlagen: %s", exc)
        return JSONResponse({"ok": False, "fehler": str(exc)}, status_code=502)
    except Exception as exc:
        log.exception("Live-Analyse abgestuerzt")
        return JSONResponse({"ok": False, "fehler": str(exc)}, status_code=500)

    return JSONResponse(
        {"ok": True, "sitzung_id": sitzung.id, "analyse": sitzung.aufnehmen(ergebnis)}
    )


@router.post("/api/shisha/analyse")
async def vollanalyse(bild: UploadFile | None = None, id: str = "") -> JSONResponse:
    """Vollstaendige Analyse des fertigen Kopfes samt Optimierungsplan."""
    if analyzer.analysator is None:
        return _nicht_bereit()
    try:
        daten = await _bild_lesen(bild)
    except ValueError as exc:
        return JSONResponse({"ok": False, "fehler": str(exc)}, status_code=400)

    sitzung = analyzer.sitzung(id or None)

    try:
        ergebnis = await _analysieren(sitzung, daten, live=False)
    except AnalyseFehler as exc:
        log.warning("Vollanalyse fehlgeschlagen: %s", exc)
        return JSONResponse({"ok": False, "fehler": str(exc)}, status_code=502)
    except Exception as exc:
        log.exception("Vollanalyse abgestuerzt")
        return JSONResponse({"ok": False, "fehler": str(exc)}, status_code=500)

    ergebnis["phase"] = sitzung.phase
    ergebnis["kontext"] = sitzung.kontext.als_dict()
    sitzung.analyse = ergebnis
    sitzung.letzte = ergebnis
    return JSONResponse({"ok": True, "sitzung_id": sitzung.id, "analyse": ergebnis})


@router.post("/api/shisha/feedback")
async def feedback(request: Request) -> JSONResponse:
    """Rueckmeldung nach der Session — daraus lernt die naechste Empfehlung."""
    body = await request.json()
    sitzung = analyzer.sitzungen.get(str(body.get("id", "")))

    eintrag: dict[str, Any] = {}
    for feld in ("geschmack", "rauch", "kratzen", "hitze"):
        wert = body.get(feld)
        if wert not in (None, ""):
            try:
                eintrag[feld] = max(1, min(5, int(wert)))
            except (TypeError, ValueError):
                pass
    if body.get("dauer_min") not in (None, ""):
        try:
            eintrag["dauer_min"] = max(0, min(600, int(body["dauer_min"])))
        except (TypeError, ValueError):
            pass
    if body.get("notiz"):
        eintrag["notiz"] = str(body["notiz"])[:300]

    if not eintrag:
        return JSONResponse({"ok": False, "fehler": "keine Angaben"}, status_code=400)

    if sitzung is not None:
        eintrag["ziel"] = sitzung.kontext.ziel
        eintrag["kopf_modell"] = sitzung.kontext.kopf_modell
        eintrag["tabak"] = f"{sitzung.kontext.tabak_marke} {sitzung.kontext.tabak_sorte}".strip()
        if sitzung.analyse and sitzung.analyse.get("gesamtscore") is not None:
            eintrag["score"] = sitzung.analyse["gesamtscore"]

    profil.merken(eintrag)
    return JSONResponse({"ok": True, "treffsicherheit": profil.treffsicherheit()})


@router.get("/api/shisha/profil")
async def profil_lesen() -> JSONResponse:
    daten = profil.laden()
    return JSONResponse(
        {
            "ok": True,
            "sessions": daten.get("sessions", [])[-10:],
            "treffsicherheit": profil.treffsicherheit(),
        }
    )


# --------------------------------------------------------------------------
# Eigenstaendiger Betrieb
# --------------------------------------------------------------------------


def create_app() -> FastAPI:
    """Der Analyzer allein, ohne den Rest von Jarvis."""
    app = FastAPI(title="Hookah-Analyzer")
    if WEB_DIR.exists():
        app.mount("/shisha/static", StaticFiles(directory=str(WEB_DIR)), name="shisha-static")
    app.include_router(router)

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

    if analyzer.analysator:
        log.info("Bildanalyse: %s", analyzer.analysator.label)
    else:
        log.error("Bildanalyse nicht bereit: %s", analyzer.startfehler)

    for adresse in _lokale_adressen():
        log.info("Am iPhone oeffnen: %s://%s:%s/shisha", schema, adresse, config.shisha_port)

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
