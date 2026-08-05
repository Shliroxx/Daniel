"""Das Gehirn: Claude-Agentenschleife mit Werkzeugen, Gedaechtnis und Streaming."""

from __future__ import annotations

import asyncio
import datetime as dt
import logging
import platform
from collections.abc import Awaitable, Callable
from typing import Any

from anthropic import AsyncAnthropic

from .config import config
from .tools import build_registry
from .tools.memory import load_memory_context

log = logging.getLogger("jarvis.brain")

Emit = Callable[[dict[str, Any]], Awaitable[None]]

MAX_TOOL_ROUNDS = 12
HISTORY_TURNS = 40  # letzte N Nachrichten behalten

SYSTEM_PROMPT = """Du bist JARVIS — der persoenliche KI-Assistent von {user}.

Wie du sprichst:
- Deutsch, geduzt, direkt und ruhig. Trocken-humorvoll, nie unterwuerfig.
- Deine Antworten werden vorgelesen. Schreib also so, wie man spricht: kurze Saetze,
  keine Aufzaehlungszeichen, keine Markdown-Formatierung, keine Emojis, keine URLs
  zum Vorlesen. Zahlen und Einheiten ausschreiben, wenn sie sonst holprig klingen.
- Standardlaenge: ein bis drei Saetze. Nur ausfuehrlich, wenn ausdruecklich gewuenscht
  oder die Sache es wirklich braucht.
- Kein Vorgeplaenkel ("Gerne!", "Klar, ich schaue mal nach") — sag direkt das Ergebnis.

Wie du arbeitest:
- Du hast echten Zugriff auf diesen Rechner: Programme starten, Lautstaerke, Screenshots,
  Systeminfos, Dateien im Arbeitsverzeichnis, To-do-Liste, Websuche.
- Wenn eine Aufgabe mit einem Werkzeug loesbar ist, mach es einfach — frag nicht vorher um
  Erlaubnis. Bei Dingen, die schwer rueckgaengig zu machen sind (Dateien ueberschreiben,
  Shell-Befehle), frag kurz nach.
- Erfinde nichts. Wenn ein Werkzeug fehlschlaegt, sag klar was nicht ging.
- Beim Brainstormen bist du ein Sparringspartner: eigene Meinung, Gegenvorschlaege,
  nachfragen wenn die Richtung unklar ist. Keine Listen von zwanzig Optionen — zwei oder
  drei gute, mit Begruendung.
- Merke dir Wichtiges ueber den Nutzer mit dem Werkzeug 'remember', aber nur wenn es
  spaeter wirklich hilft.

Kontext:
- System: {system}
- Arbeitsverzeichnis: {workspace}
- Aktuelle Zeit beim Start dieser Sitzung: {now}
{memory}"""


class Brain:
    """Haelt den Gespraechsverlauf und fuehrt die Werkzeugschleife aus."""

    def __init__(self, user_name: str = "Daniel") -> None:
        if not config.api_key:
            raise RuntimeError(
                "ANTHROPIC_API_KEY fehlt. Kopiere .env.example nach .env und trage deinen Key ein."
            )
        self.client = AsyncAnthropic(api_key=config.api_key)
        self.registry = build_registry()
        self.user_name = user_name
        self.messages: list[dict[str, Any]] = []
        self._lock = asyncio.Lock()

    # -- System-Prompt -----------------------------------------------------
    def system_prompt(self) -> list[dict[str, Any]]:
        memory = load_memory_context()
        text = SYSTEM_PROMPT.format(
            user=self.user_name,
            system=f"{platform.system()} {platform.release()}",
            workspace=config.workspace,
            now=dt.datetime.now().strftime("%A, %d.%m.%Y %H:%M"),
            memory=f"\n\n{memory}" if memory else "",
        )
        # Der Prompt ist ueber die Sitzung stabil -> caching spart Tokens und Zeit.
        return [{"type": "text", "text": text, "cache_control": {"type": "ephemeral"}}]

    def reset(self) -> None:
        self.messages.clear()

    # -- Hauptschleife -----------------------------------------------------
    async def respond(self, user_text: str, emit: Emit) -> str:
        """Verarbeitet eine Nutzereingabe und streamt die Antwort ueber `emit`."""
        async with self._lock:
            self.messages.append({"role": "user", "content": user_text})
            self._trim()

            final_text = ""
            for round_index in range(MAX_TOOL_ROUNDS):
                try:
                    response = await self._stream_turn(emit)
                except Exception as exc:
                    log.exception("Anfrage an Claude fehlgeschlagen")
                    await emit({"type": "error", "message": f"Fehler beim Denken: {exc}"})
                    return ""

                self.messages.append({"role": "assistant", "content": response.content})

                text = "".join(b.text for b in response.content if b.type == "text").strip()
                if text:
                    final_text = text

                if response.stop_reason == "refusal":
                    message = "Das kann ich nicht beantworten."
                    await emit({"type": "error", "message": message})
                    return message

                if response.stop_reason == "pause_turn":
                    # Serverseitiges Werkzeug (Websuche) laeuft weiter — einfach fortsetzen.
                    continue

                if response.stop_reason == "tool_use":
                    results = await self._run_tools(response, emit)
                    self.messages.append({"role": "user", "content": results})
                    continue

                break
            else:
                await emit({"type": "error", "message": "Zu viele Werkzeugschritte — abgebrochen."})

            self._trim()
            return final_text

    async def _stream_turn(self, emit: Emit):
        params: dict[str, Any] = {
            "model": config.model,
            "max_tokens": config.max_tokens,
            "system": self.system_prompt(),
            "messages": self.messages,
            "tools": self.registry.definitions(),
            "thinking": {"type": "adaptive", "display": "summarized"},
            "output_config": {"effort": config.effort},
        }

        async with self.client.messages.stream(**params) as stream:
            async for event in stream:
                if event.type != "content_block_delta":
                    continue
                if event.delta.type == "text_delta":
                    await emit({"type": "delta", "text": event.delta.text})
                elif event.delta.type == "thinking_delta":
                    await emit({"type": "thinking", "text": event.delta.thinking})
            return await stream.get_final_message()

    async def _run_tools(self, response, emit: Emit) -> list[dict[str, Any]]:
        """Fuehrt alle angeforderten Werkzeuge aus (parallel) und sammelt die Ergebnisse."""
        calls = [b for b in response.content if b.type == "tool_use"]
        for call in calls:
            await emit({"type": "tool_start", "name": call.name, "input": call.input})

        async def run_one(call):
            result, is_error = await asyncio.to_thread(
                self.registry.run, call.name, dict(call.input or {})
            )
            await emit(
                {
                    "type": "tool_end",
                    "name": call.name,
                    "ok": not is_error,
                    "result": result[:2000],
                }
            )
            return {
                "type": "tool_result",
                "tool_use_id": call.id,
                "content": result,
                "is_error": is_error,
            }

        return list(await asyncio.gather(*(run_one(call) for call in calls)))

    def _trim(self) -> None:
        """Haelt den Verlauf kurz — schneidet aber nie mitten in ein Werkzeugpaar."""
        if len(self.messages) <= HISTORY_TURNS:
            return
        cut = len(self.messages) - HISTORY_TURNS
        while cut < len(self.messages) and not self._is_clean_start(self.messages[cut]):
            cut += 1
        self.messages = self.messages[cut:]

    @staticmethod
    def _is_clean_start(message: dict[str, Any]) -> bool:
        """Ein sauberer Schnittpunkt ist eine Nutzernachricht ohne tool_result."""
        if message.get("role") != "user":
            return False
        content = message.get("content")
        if isinstance(content, str):
            return True
        return not any(getattr(b, "type", b.get("type") if isinstance(b, dict) else None) == "tool_result" for b in content)
