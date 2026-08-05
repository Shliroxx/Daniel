"""FastAPI-Server: HUD ausliefern, WebSocket-Events, Mikrofon-Schleife, TTS."""

from __future__ import annotations

import asyncio
import base64
import logging
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from .audio import stt, tts
from .audio.listener import MicListener
from .brain import Brain
from .config import config

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("jarvis.server")

WEB_DIR = Path(__file__).resolve().parent.parent / "web"


class Hub:
    """Verteilt Ereignisse an alle verbundenen Oberflaechen (PC-Browser, iPhone, ...)."""

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
    """Verbindet Mikrofon, Gehirn und Sprachausgabe zu einem Gespraechsablauf."""

    def __init__(self) -> None:
        self.hub = Hub()
        self.startup_error: str | None = None
        try:
            self.brain: Brain | None = Brain()
        except Exception as exc:
            self.brain = None
            self.startup_error = str(exc)
            log.error("%s", exc)
        self.listener: MicListener | None = None
        self.loop: asyncio.AbstractEventLoop | None = None
        self.state = "idle"
        self._turn_lock = asyncio.Lock()

    # -- Zustand -----------------------------------------------------------
    async def set_state(self, state: str, **extra: Any) -> None:
        self.state = state
        await self.hub.broadcast({"type": "state", "state": state, **extra})

    # -- Ein Gespraechszug -------------------------------------------------
    async def handle_input(self, text: str, speak: bool = True) -> None:
        text = (text or "").strip()
        if not text:
            return
        if self.brain is None:
            await self.hub.broadcast({"type": "error", "message": self.startup_error or "Jarvis ist nicht bereit."})
            return

        async with self._turn_lock:
            await self.hub.broadcast({"type": "user", "text": text})
            await self.set_state("thinking")

            if self.listener:
                self.listener.mute()

            try:
                reply = await self.brain.respond(text, self.hub.broadcast)
            finally:
                if not speak and self.listener:
                    self.listener.unmute()

            await self.hub.broadcast({"type": "assistant", "text": reply})

            if reply and speak:
                await self.set_state("speaking")
                await self._speak(reply)

            if self.listener:
                self.listener.unmute()
            await self.set_state("listening" if self.listener else "idle")

    async def _speak(self, text: str) -> None:
        result = await asyncio.to_thread(tts.synthesize, text)
        if not result:
            return
        wav, _rate = result

        # An alle Oberflaechen schicken (damit das iPhone es auch hoert)
        await self.hub.broadcast(
            {"type": "audio", "format": "wav", "data": base64.b64encode(wav).decode("ascii")}
        )
        if config.speak_locally:
            await asyncio.to_thread(tts.play_wav, wav)

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


@asynccontextmanager
async def lifespan(app: FastAPI):
    jarvis.loop = asyncio.get_running_loop()

    # Modelle im Hintergrund vorladen, damit die erste Frage nicht haengt.
    asyncio.create_task(asyncio.to_thread(stt.get_model))
    asyncio.create_task(asyncio.to_thread(tts.get_voice))

    jarvis.start_microphone()
    if jarvis.startup_error:
        log.error("Jarvis kann noch nicht denken: %s", jarvis.startup_error)
    log.info("HUD erreichbar unter http://localhost:%s", config.port)
    yield
    jarvis.stop_microphone()


app = FastAPI(title="Jarvis", lifespan=lifespan)

if WEB_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(WEB_DIR)), name="static")


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
        "model": config.model,
        "effort": config.effort,
        "wakeword": config.wakeword,
        "mikrofon": jarvis.listener is not None,
        "sprache": config.language,
        "stimme": config.piper_voice,
        "werkzeuge": sorted(jarvis.brain.registry.tools) if jarvis.brain else [],
        "arbeitsverzeichnis": str(config.workspace),
    }


@app.post("/api/chat")
async def chat(request: Request) -> JSONResponse:
    body = await request.json()
    text = str(body.get("text", ""))
    speak = bool(body.get("speak", True))
    asyncio.create_task(jarvis.handle_input(text, speak=speak))
    return JSONResponse({"ok": True})


@app.post("/api/stt")
async def speech_to_text(audio: UploadFile) -> JSONResponse:
    """Sprachaufnahme aus dem Browser (Push-to-Talk, z. B. vom iPhone)."""
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
    if jarvis.brain:
        jarvis.brain.reset()
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
                if jarvis.brain:
                    jarvis.brain.reset()
                await jarvis.hub.broadcast({"type": "reset"})
            elif kind == "stop":
                await asyncio.to_thread(tts.stop_playback)
            elif kind == "ping":
                await ws.send_json({"type": "pong"})
    except WebSocketDisconnect:
        pass
    finally:
        await jarvis.hub.leave(ws)


def main() -> None:
    import uvicorn

    uvicorn.run(app, host=config.host, port=config.port, log_level="warning")


if __name__ == "__main__":
    main()
