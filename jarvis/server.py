"""FastAPI-Server: HUD, WebSocket, Mikrofon, WhatsApp-Webhook."""

from __future__ import annotations

import asyncio
import base64
import logging
from collections import OrderedDict
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request, Response, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, JSONResponse, PlainTextResponse
from fastapi.staticfiles import StaticFiles

from . import memory
from .audio import stt, tts
from .audio.listener import MicListener
from .channels import whatsapp
from .config import config
from .engine import build_engine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("jarvis.server")

WEB_DIR = Path(__file__).resolve().parent.parent / "web"


class Hub:
    """Verteilt Ereignisse an alle verbundenen Oberflaechen (PC-Browser, Handy, ...)."""

    def __init__(self) -> None:
        self.clients: set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def join(self, ws: WebSocket) -> None:
        await ws.accept()
        async with self._lock:
            self.clients.add(ws)

    async def leave(self, ws: WebSocket) -> None:
        async with self._lock:
            self.clients.discard(ws)

    async def broadcast(self, payload: dict[str, Any]) -> None:
        async with self._lock:
            targets = list(self.clients)
        for ws in targets:
            try:
                await ws.send_json(payload)
            except Exception:
                await self.leave(ws)


class Jarvis:
    """Verbindet Mikrofon, WhatsApp, Denkapparat und Sprachausgabe."""

    def __init__(self) -> None:
        self.hub = Hub()
        self.startup_error: str | None = None
        try:
            self.engine = build_engine()
        except Exception as exc:
            self.engine = None
            self.startup_error = str(exc)
            log.error("%s", exc)

        self.listener: MicListener | None = None
        self.loop: asyncio.AbstractEventLoop | None = None
        self.state = "idle"
        self._turn_lock = asyncio.Lock()
        # WhatsApp stellt Webhooks bei Zeitueberschreitung erneut zu — schon
        # verarbeitete Nachrichten duerfen nicht doppelt beantwortet werden.
        self._seen_messages: OrderedDict[str, None] = OrderedDict()

    # -- Zustand -----------------------------------------------------------
    async def set_state(self, state: str, **extra: Any) -> None:
        self.state = state
        await self.hub.broadcast({"type": "state", "state": state, **extra})

    def seen(self, message_id: str) -> bool:
        """True, wenn diese Nachricht schon verarbeitet wurde."""
        if message_id in self._seen_messages:
            return True
        self._seen_messages[message_id] = None
        while len(self._seen_messages) > 500:
            self._seen_messages.popitem(last=False)
        return False

    # -- Ein Gespraechszug -------------------------------------------------
    async def handle_input(
        self,
        text: str,
        channel: str = "hud",
        reply_to: str | None = None,
        speak: bool = True,
    ) -> str:
        text = (text or "").strip()
        if not text:
            return ""
        if self.engine is None:
            problem = self.startup_error or "Jarvis ist nicht bereit."
            await self.hub.broadcast({"type": "error", "message": problem})
            if channel == "whatsapp" and reply_to:
                await self._safe_whatsapp(reply_to, f"Ich bin gerade nicht einsatzbereit: {problem}")
            return ""

        async with self._turn_lock:
            await self.hub.broadcast({"type": "user", "text": text, "channel": channel})
            await self.set_state("thinking")

            if self.listener:
                self.listener.mute()

            try:
                reply = await self.engine.respond(text, self.hub.broadcast)
            finally:
                if self.listener and not (speak and channel == "hud"):
                    self.listener.unmute()

            await self.hub.broadcast({"type": "assistant", "text": reply, "channel": channel})
            await asyncio.to_thread(memory.log_exchange, text, reply, channel)

            if reply:
                if channel == "whatsapp" and reply_to:
                    await self._reply_whatsapp(reply_to, reply)
                elif speak:
                    await self.set_state("speaking")
                    await self._speak(reply)

            if self.listener:
                self.listener.unmute()
            await self.set_state("listening" if self.listener else "idle")
            return reply

    async def _speak(self, text: str) -> bytes | None:
        result = await asyncio.to_thread(tts.synthesize, text)
        if not result:
            return None
        wav, _rate = result

        # An alle Oberflaechen schicken, damit auch das Handy es hoert
        await self.hub.broadcast(
            {"type": "audio", "format": "wav", "data": base64.b64encode(wav).decode("ascii")}
        )
        if config.speak_locally:
            await asyncio.to_thread(tts.play_wav, wav)
        return wav

    async def _reply_whatsapp(self, to: str, reply: str) -> None:
        await self._safe_whatsapp(to, reply)
        if not config.whatsapp_voice_reply:
            return
        result = await asyncio.to_thread(tts.synthesize, reply)
        if result:
            try:
                await whatsapp.send_voice(to, result[0])
            except Exception as exc:
                log.warning("Sprachnachricht konnte nicht gesendet werden: %s", exc)

    async def _safe_whatsapp(self, to: str, text: str) -> None:
        try:
            await whatsapp.send_text(to, text)
        except Exception as exc:
            log.error("WhatsApp-Antwort fehlgeschlagen: %s", exc)
            await self.hub.broadcast({"type": "error", "message": f"WhatsApp: {exc}"})

    # -- Mikrofon ----------------------------------------------------------
    def _from_thread(self, coro) -> None:
        if self.loop:
            asyncio.run_coroutine_threadsafe(coro, self.loop)

    def start_microphone(self) -> None:
        if not config.mic_enabled:
            log.info("Mikrofon deaktiviert (JARVIS_MIC_ENABLED=false).")
            return

        def on_transcript(text: str) -> None:
            self._from_thread(self.handle_input(text))

        def on_event(kind: str, data: dict[str, Any]) -> None:
            if kind == "level":
                self._from_thread(self.hub.broadcast({"type": "level", "rms": data["rms"]}))
            elif kind == "wake":
                self._from_thread(self.hub.broadcast({"type": "wake"}))
                self._from_thread(self.set_state("recording"))
            elif kind == "listening":
                self._from_thread(self.set_state("listening"))
            elif kind == "thinking":
                self._from_thread(self.set_state("thinking"))
            elif kind == "offline":
                self._from_thread(self.set_state("idle"))

        self.listener = MicListener(on_transcript, on_event)
        self.listener.start()

    def stop_microphone(self) -> None:
        if self.listener:
            self.listener.stop()
            self.listener = None


jarvis = Jarvis()


async def _preload(name: str, loader) -> None:
    """Laedt ein Sprachmodell vor und schluckt Fehler — sie duerfen den Start nicht kippen."""
    try:
        await asyncio.to_thread(loader)
    except Exception as exc:
        log.warning("%s konnte nicht geladen werden: %s", name, exc)


@asynccontextmanager
async def lifespan(app: FastAPI):
    jarvis.loop = asyncio.get_running_loop()

    # Modelle im Hintergrund vorladen, damit die erste Frage nicht haengt.
    # Fehlt ein Sprachpaket, laeuft Jarvis trotzdem — nur eben ohne Stimme.
    asyncio.create_task(_preload("Whisper", stt.get_model))
    asyncio.create_task(_preload("Piper", tts.get_voice))

    jarvis.start_microphone()

    if jarvis.startup_error:
        log.error("Jarvis kann noch nicht denken: %s", jarvis.startup_error)
    else:
        log.info("Denkapparat: %s", jarvis.engine.label)

    if config.whatsapp_enabled:
        fehlend = whatsapp.missing_settings()
        if fehlend:
            log.warning("WhatsApp aktiviert, aber es fehlt: %s", ", ".join(fehlend))
        else:
            log.info("WhatsApp aktiv fuer %d Nummer(n).", len(config.whatsapp_allowed))

    if config.uses_vault:
        log.info("Obsidian-Vault: %s", config.memory_root)
    elif config.vault_enabled:
        log.warning("Vault '%s' nicht gefunden — nutze lokale Dateien.", config.vault)

    log.info("HUD erreichbar unter http://localhost:%s", config.port)
    yield
    jarvis.stop_microphone()


app = FastAPI(title="Jarvis", lifespan=lifespan)

if WEB_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(WEB_DIR)), name="static")


# --------------------------------------------------------------------------
# Oberflaeche
# --------------------------------------------------------------------------


@app.get("/")
async def index() -> FileResponse:
    return FileResponse(WEB_DIR / "index.html")


@app.get("/manifest.webmanifest")
async def manifest() -> FileResponse:
    return FileResponse(WEB_DIR / "manifest.webmanifest", media_type="application/manifest+json")


@app.get("/api/status")
async def status() -> dict[str, Any]:
    return {
        "state": jarvis.state,
        "fehler": jarvis.startup_error,
        "backend": config.backend,
        "denkapparat": jarvis.engine.label if jarvis.engine else None,
        "wakeword": config.wakeword,
        "mikrofon": jarvis.listener is not None,
        "sprache": config.language,
        "stimme": config.piper_voice,
        "werkzeuge": sorted(jarvis.engine.registry.tools) if jarvis.engine else [],
        "arbeitsverzeichnis": str(config.workspace),
        "vault": str(config.memory_root) if config.uses_vault else None,
        "whatsapp": whatsapp.is_configured(),
    }


@app.post("/api/chat")
async def chat(request: Request) -> JSONResponse:
    body = await request.json()
    asyncio.create_task(
        jarvis.handle_input(str(body.get("text", "")), speak=bool(body.get("speak", True)))
    )
    return JSONResponse({"ok": True})


@app.post("/api/stt")
async def speech_to_text(audio: UploadFile) -> JSONResponse:
    """Sprachaufnahme aus dem Browser (Push-to-Talk, z. B. vom Handy)."""
    data = await audio.read()
    if not data:
        return JSONResponse({"ok": False, "error": "leere Aufnahme"}, status_code=400)

    await jarvis.set_state("thinking")
    text = await asyncio.to_thread(stt.transcribe_bytes, data, audio.filename or "audio.webm")
    if not text:
        await jarvis.set_state("listening" if jarvis.listener else "idle")
        return JSONResponse({"ok": True, "text": ""})

    asyncio.create_task(jarvis.handle_input(text))
    return JSONResponse({"ok": True, "text": text})


@app.post("/api/reset")
async def reset() -> dict[str, Any]:
    if jarvis.engine:
        jarvis.engine.reset()
    await jarvis.hub.broadcast({"type": "reset"})
    return {"ok": True}


@app.post("/api/stop")
async def stop_speaking() -> dict[str, Any]:
    await asyncio.to_thread(tts.stop_playback)
    await jarvis.set_state("listening" if jarvis.listener else "idle")
    return {"ok": True}


@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket) -> None:
    await jarvis.hub.join(ws)
    await ws.send_json({"type": "state", "state": jarvis.state})
    try:
        while True:
            message = await ws.receive_json()
            kind = message.get("type")
            if kind == "chat":
                asyncio.create_task(
                    jarvis.handle_input(message.get("text", ""), speak=message.get("speak", True))
                )
            elif kind == "reset":
                if jarvis.engine:
                    jarvis.engine.reset()
                await jarvis.hub.broadcast({"type": "reset"})
            elif kind == "stop":
                await asyncio.to_thread(tts.stop_playback)
            elif kind == "ping":
                await ws.send_json({"type": "pong"})
    except WebSocketDisconnect:
        pass
    finally:
        await jarvis.hub.leave(ws)


# --------------------------------------------------------------------------
# WhatsApp
# --------------------------------------------------------------------------


@app.get("/whatsapp/webhook")
async def whatsapp_verify(request: Request) -> Response:
    """Einmalige Bestaetigung des Webhooks gegenueber Meta."""
    params = request.query_params
    if (
        params.get("hub.mode") == "subscribe"
        and params.get("hub.verify_token") == config.whatsapp_verify_token
        and config.whatsapp_verify_token
    ):
        log.info("WhatsApp-Webhook von Meta bestaetigt.")
        return PlainTextResponse(params.get("hub.challenge", ""))
    log.warning("WhatsApp-Webhook-Bestaetigung abgelehnt (falscher Verify-Token).")
    return PlainTextResponse("verification failed", status_code=403)


@app.post("/whatsapp/webhook")
async def whatsapp_webhook(request: Request) -> Response:
    """Eingehende WhatsApp-Nachrichten.

    Wir antworten Meta sofort mit 200 und arbeiten im Hintergrund weiter — sonst
    stellt Meta die Nachricht wegen Zeitueberschreitung erneut zu.
    """
    raw = await request.body()

    if not whatsapp.verify_signature(raw, request.headers.get("x-hub-signature-256")):
        log.warning("WhatsApp-Webhook mit ungueltiger Signatur abgelehnt.")
        return Response(status_code=403)

    if not whatsapp.is_configured():
        return Response(status_code=200)

    try:
        payload = await request.json()
    except Exception:
        return Response(status_code=200)

    for message in whatsapp.extract_messages(payload):
        sender = message.get("from", "")
        message_id = message.get("id", "")

        if not whatsapp.is_allowed(sender):
            log.warning("WhatsApp-Nachricht von nicht freigeschalteter Nummer %s verworfen.", sender)
            continue
        if message_id and jarvis.seen(message_id):
            continue

        asyncio.create_task(_handle_whatsapp_message(message, sender))

    return Response(status_code=200)


async def _handle_whatsapp_message(message: dict[str, Any], sender: str) -> None:
    kind = message.get("type")
    try:
        if kind == "text":
            text = (message.get("text") or {}).get("body", "")
        elif kind in {"audio", "voice"}:
            media_id = (message.get(kind) or {}).get("id")
            if not media_id:
                return
            await jarvis.set_state("thinking")
            text = await whatsapp.transcribe_voice(media_id)
            if not text:
                await jarvis._safe_whatsapp(sender, "Ich habe die Sprachnachricht nicht verstanden.")
                return
            await jarvis.hub.broadcast({"type": "transcript", "text": text, "channel": "whatsapp"})
        else:
            await jarvis._safe_whatsapp(
                sender, f"Mit '{kind}' kann ich noch nichts anfangen — schreib oder sprich mir."
            )
            return

        await jarvis.handle_input(text, channel="whatsapp", reply_to=sender)
    except Exception as exc:
        log.exception("WhatsApp-Nachricht fehlgeschlagen")
        await jarvis._safe_whatsapp(sender, f"Da ist etwas schiefgegangen: {exc}")


def main() -> None:
    import uvicorn

    uvicorn.run(app, host=config.host, port=config.port, log_level="warning")


if __name__ == "__main__":
    main()
