"""MCP-Server, der Jarvis' eigene Werkzeuge fuer `claude -p` bereitstellt.

Claude Code bringt Datei-, Such- und Web-Werkzeuge schon mit. Was ihm fehlt, ist der
Zugriff auf den Rechner selbst und auf Jarvis' Gedaechtnis — genau das liefert dieser
Server. Er wird nicht von Hand gestartet, sondern von Claude Code ueber die Datei
`data/mcp-config.json` als Unterprozess hochgezogen.

Das Protokoll ist hier direkt implementiert (JSON-RPC 2.0 ueber stdin/stdout, eine
Nachricht pro Zeile) statt ueber das offizielle SDK. Grund: dessen Python-API hat
sich zwischen den Hauptversionen mehrfach grundlegend geaendert, waehrend das
Protokoll stabil ist. Der benoetigte Teil ist klein genug, dass sich das lohnt.

Zum Testen von Hand:
    echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | python -m jarvis.mcp_server
"""

from __future__ import annotations

import json
import logging
import sys
import threading
from concurrent.futures import ThreadPoolExecutor
from typing import Any

from . import __version__
from .config import config
from .tools import build_registry

# Logs muessen in eine Datei — stdout gehoert dem Protokoll.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s %(message)s",
    datefmt="%H:%M:%S",
    filename=str(config.log_dir / "mcp-server.log"),
)
log = logging.getLogger("jarvis.mcp")

PROTOCOL_VERSION = "2025-06-18"

# JSON-RPC-Fehlercodes
METHOD_NOT_FOUND = -32601
INVALID_PARAMS = -32602
INTERNAL_ERROR = -32603


class McpServer:
    def __init__(self) -> None:
        self.registry = build_registry(include_server_tools=False)
        self._stdout_lock = threading.Lock()
        self._pool = ThreadPoolExecutor(max_workers=4, thread_name_prefix="mcp-tool")

    # -- Ausgabe -----------------------------------------------------------
    def _send(self, payload: dict[str, Any]) -> None:
        line = json.dumps(payload, ensure_ascii=False)
        with self._stdout_lock:
            sys.stdout.write(line + "\n")
            sys.stdout.flush()

    def _reply(self, request_id: Any, result: dict[str, Any]) -> None:
        self._send({"jsonrpc": "2.0", "id": request_id, "result": result})

    def _fail(self, request_id: Any, code: int, message: str) -> None:
        self._send({"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}})

    # -- Methoden ----------------------------------------------------------
    def _initialize(self, params: dict[str, Any]) -> dict[str, Any]:
        client = params.get("clientInfo") or {}
        log.info("Verbunden mit %s %s", client.get("name", "?"), client.get("version", ""))
        return {
            # Die Version des Clients spiegeln, solange wir sie kennen.
            "protocolVersion": params.get("protocolVersion") or PROTOCOL_VERSION,
            "capabilities": {"tools": {"listChanged": False}},
            "serverInfo": {"name": "jarvis", "version": __version__},
        }

    def _list_tools(self) -> dict[str, Any]:
        return {
            "tools": [
                {
                    "name": tool.name,
                    "description": tool.description,
                    "inputSchema": tool.input_schema,
                }
                for tool in self.registry.tools.values()
            ]
        }

    def _call_tool(self, params: dict[str, Any]) -> dict[str, Any]:
        name = params.get("name", "")
        arguments = params.get("arguments") or {}
        log.info("Werkzeug '%s' aufgerufen", name)

        result, is_error = self.registry.run(name, arguments)
        if is_error:
            # Als Ergebnis mit isError melden, nicht als Protokollfehler — dann kann
            # Claude damit weiterarbeiten, statt den Durchlauf abzubrechen.
            log.warning("Werkzeug '%s' meldet Fehler: %s", name, result[:200])
        return {"content": [{"type": "text", "text": result}], "isError": is_error}

    # -- Verteilung --------------------------------------------------------
    def _handle(self, message: dict[str, Any]) -> None:
        method = message.get("method")
        request_id = message.get("id")
        params = message.get("params") or {}

        # Benachrichtigungen haben keine id und bekommen keine Antwort.
        if request_id is None:
            log.debug("Benachrichtigung '%s'", method)
            return

        try:
            if method == "initialize":
                self._reply(request_id, self._initialize(params))
            elif method == "tools/list":
                self._reply(request_id, self._list_tools())
            elif method == "tools/call":
                if not params.get("name"):
                    self._fail(request_id, INVALID_PARAMS, "Kein Werkzeugname angegeben.")
                else:
                    self._reply(request_id, self._call_tool(params))
            elif method == "ping":
                self._reply(request_id, {})
            elif method in ("resources/list", "prompts/list"):
                # Wir bieten nur Werkzeuge an — leere Listen sind die korrekte Antwort.
                self._reply(request_id, {method.split("/")[0]: []})
            else:
                self._fail(request_id, METHOD_NOT_FOUND, f"Unbekannte Methode: {method}")
        except Exception as exc:
            log.exception("Fehler bei Methode '%s'", method)
            self._fail(request_id, INTERNAL_ERROR, str(exc))

    def serve(self) -> None:
        log.info("MCP-Server startet mit %d Werkzeugen", len(self.registry.tools))
        for raw in sys.stdin:
            line = raw.strip()
            if not line:
                continue
            try:
                message = json.loads(line)
            except json.JSONDecodeError:
                log.warning("Ungueltiges JSON verworfen: %s", line[:200])
                continue

            # Werkzeuge koennen dauern (Screenshot, Websuche). In Threads ausfuehren,
            # damit parallele Aufrufe sich nicht gegenseitig blockieren.
            self._pool.submit(self._handle, message)

        self._pool.shutdown(wait=True)
        log.info("MCP-Server beendet.")


def main() -> None:
    try:
        McpServer().serve()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
