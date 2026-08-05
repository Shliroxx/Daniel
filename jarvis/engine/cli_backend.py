"""Backend "cli": denkt ueber `claude -p` und damit ueber dein Claude-Abo.

Vorteil gegenueber der API: keine Token-Kosten, und Jarvis bekommt zusaetzlich die
eingebauten Werkzeuge von Claude Code (Dateien, Suche, Web). Seine eigenen Werkzeuge
— PC-Steuerung, Gedaechtnis, Aufgaben — kommen ueber einen MCP-Server dazu.
"""

from __future__ import annotations

import asyncio
import json
import logging
import shutil
import sys
from pathlib import Path
from typing import Any

from ..config import ROOT, config
from ..tools import build_registry
from .base import Emit, Engine

log = logging.getLogger("jarvis.engine.cli")

MCP_SERVER_NAME = "jarvis"

# Eingebaute Werkzeuge von Claude Code, die Jarvis ohne Nachfrage nutzen darf.
BUILTIN_TOOLS = ["Read", "Write", "Edit", "Grep", "Glob", "WebSearch", "WebFetch", "TodoWrite"]


class CliEngine(Engine):
    def __init__(self, user_name: str = "Daniel") -> None:
        super().__init__(user_name)
        self.registry = build_registry(include_server_tools=False)
        self.session_id: str | None = None
        self._lock = asyncio.Lock()
        self._mcp_config = self._write_mcp_config()

        if not shutil.which(config.claude_bin) and not Path(config.claude_bin).exists():
            raise RuntimeError(
                f"Claude Code wurde nicht gefunden ('{config.claude_bin}').\n"
                "Installiere es mit:  npm install -g @anthropic-ai/claude-code\n"
                "und melde dich einmal mit 'claude' an (dafuer brauchst du ein "
                "Claude-Pro- oder Max-Abo).\n"
                "Alternativ kannst du in der .env JARVIS_BACKEND=api setzen und mit "
                "einem API-Key arbeiten."
            )

    @property
    def label(self) -> str:
        return f"claude -p ({config.cli_model})"

    def reset(self) -> None:
        self.session_id = None

    # -- Aufbau ------------------------------------------------------------
    def _write_mcp_config(self) -> Path:
        """Legt die MCP-Konfiguration ab, die Jarvis' eigene Werkzeuge bereitstellt."""
        path = config.data_dir / "mcp-config.json"
        payload = {
            "mcpServers": {
                MCP_SERVER_NAME: {
                    "command": sys.executable,
                    "args": ["-m", "jarvis.mcp_server"],
                    "cwd": str(ROOT),
                    "env": {"PYTHONPATH": str(ROOT)},
                }
            }
        }
        path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        return path

    def _allowed_tools(self) -> str:
        tools = list(BUILTIN_TOOLS)
        if config.allow_shell:
            tools.append("Bash")
        tools += [f"mcp__{MCP_SERVER_NAME}__{name}" for name in sorted(self.registry.tools)]
        return ",".join(tools)

    def _extra_context(self) -> str:
        lines = [
            "- Du laeufst in Claude Code. Neben deinen eigenen Werkzeugen hast du Datei-,",
            "  Such- und Web-Werkzeuge. Nutze sie, statt Dinge zu raten.",
        ]
        if config.uses_vault:
            lines += [
                f"- Dein Obsidian-Vault liegt unter {config.vault}.",
                f"- Deine eigenen Notizen gehoeren nach {config.memory_root}.",
                "- Was du dauerhaft ueber den Nutzer wissen sollst, steht in der Datei",
                f"  {config.memory_root / 'gedaechtnis.md'} — lies sie bei Bedarf.",
            ]
        return "\n".join(lines)

    def _build_command(self, prompt: str) -> list[str]:
        cmd = [
            config.claude_bin,
            "-p", prompt,
            "--output-format", "stream-json",
            "--verbose",
            "--include-partial-messages",
            "--model", config.cli_model,
            "--max-turns", str(config.cli_max_turns),
            "--allowedTools", self._allowed_tools(),
            "--append-system-prompt", self.persona(self._extra_context()),
            "--mcp-config", str(self._mcp_config),
        ]
        for directory in config.write_dirs():
            cmd += ["--add-dir", str(directory)]
        if self.session_id:
            cmd += ["--resume", self.session_id]
        return cmd

    # -- Ausfuehrung -------------------------------------------------------
    async def respond(self, user_text: str, emit: Emit) -> str:
        async with self._lock:
            cmd = self._build_command(user_text)
            log.info("claude -p (%s Zeichen, session=%s)", len(user_text), self.session_id or "neu")

            try:
                proc = await asyncio.create_subprocess_exec(
                    *cmd,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                    # Sitzungen werden pro Arbeitsverzeichnis gefuehrt — deshalb immer
                    # aus demselben Ordner starten, sonst findet --resume nichts.
                    cwd=str(config.workspace),
                )
            except FileNotFoundError:
                message = f"Claude Code nicht gefunden ('{config.claude_bin}')."
                await emit({"type": "error", "message": message})
                return ""

            try:
                reply = await asyncio.wait_for(
                    self._consume(proc, emit), timeout=config.cli_timeout
                )
            except asyncio.TimeoutError:
                proc.kill()
                await proc.wait()
                message = f"Zeitueberschreitung nach {config.cli_timeout}s."
                await emit({"type": "error", "message": message})
                return ""

            stderr = (await proc.stderr.read()).decode("utf-8", "replace").strip()
            await proc.wait()

            if proc.returncode not in (0, None) and not reply:
                message = f"Claude Code beendet mit Code {proc.returncode}: {stderr[:400]}"
                log.error(message)
                await emit({"type": "error", "message": message})
                return ""
            if stderr:
                log.debug("claude stderr: %s", stderr[:600])

            return reply

    async def _consume(self, proc, emit: Emit) -> str:
        """Liest den stream-json-Strom Zeile fuer Zeile und uebersetzt ihn in Ereignisse."""
        final = ""
        pending: dict[str, str] = {}  # tool_use_id -> Werkzeugname

        while True:
            raw = await proc.stdout.readline()
            if not raw:
                break
            line = raw.decode("utf-8", "replace").strip()
            if not line:
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                log.debug("Nicht-JSON-Zeile von claude: %s", line[:200])
                continue

            kind = event.get("type")

            if kind == "system":
                if event.get("subtype") == "init":
                    self.session_id = event.get("session_id") or self.session_id
                    self._report_mcp_problems(event)
                elif event.get("subtype") == "api_retry":
                    await emit(
                        {
                            "type": "tool_start",
                            "name": "warte",
                            "input": {"grund": event.get("error"), "versuch": event.get("attempt")},
                        }
                    )

            elif kind == "stream_event":
                await self._emit_delta(event.get("event") or {}, emit)

            elif kind == "assistant":
                # Nur die Hauptunterhaltung, nicht die Ausgaben von Unteragenten.
                if event.get("parent_tool_use_id"):
                    continue
                for block in (event.get("message") or {}).get("content", []):
                    if block.get("type") == "tool_use":
                        name = self._short_name(block.get("name", "?"))
                        pending[block.get("id", "")] = name
                        await emit({"type": "tool_start", "name": name, "input": block.get("input") or {}})

            elif kind == "user":
                for block in (event.get("message") or {}).get("content", []):
                    if block.get("type") != "tool_result":
                        continue
                    name = pending.pop(block.get("tool_use_id", ""), "werkzeug")
                    await emit(
                        {
                            "type": "tool_end",
                            "name": name,
                            "ok": not block.get("is_error"),
                            "result": self._flatten(block.get("content"))[:2000],
                        }
                    )

            elif kind == "result":
                self.session_id = event.get("session_id") or self.session_id
                text = (event.get("result") or "").strip()
                if event.get("is_error"):
                    await emit({"type": "error", "message": text or event.get("subtype", "Fehler")})
                elif text:
                    final = text

        return final

    async def _emit_delta(self, inner: dict[str, Any], emit: Emit) -> None:
        if inner.get("type") != "content_block_delta":
            return
        delta = inner.get("delta") or {}
        if delta.get("type") == "text_delta" and delta.get("text"):
            await emit({"type": "delta", "text": delta["text"]})
        elif delta.get("type") == "thinking_delta" and delta.get("thinking"):
            await emit({"type": "thinking", "text": delta["thinking"]})

    def _report_mcp_problems(self, event: dict[str, Any]) -> None:
        for problem in event.get("mcp_server_errors") or []:
            log.error(
                "MCP-Server '%s' nicht geladen (%s): %s",
                problem.get("name"),
                problem.get("type"),
                problem.get("message"),
            )

    @staticmethod
    def _short_name(name: str) -> str:
        """'mcp__jarvis__open_app' -> 'open_app'."""
        return name.rsplit("__", 1)[-1] if name.startswith("mcp__") else name

    @staticmethod
    def _flatten(content: Any) -> str:
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            return " ".join(
                part.get("text", "") for part in content if isinstance(part, dict)
            ).strip()
        return "" if content is None else str(content)
