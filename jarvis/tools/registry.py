"""Sammelt alle Werkzeuge und fuehrt sie aus."""

from __future__ import annotations

import json
import logging
import traceback
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

log = logging.getLogger("jarvis.tools")

Handler = Callable[[dict[str, Any]], Any]


@dataclass
class Tool:
    name: str
    description: str
    input_schema: dict[str, Any]
    handler: Handler

    def definition(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": self.input_schema,
        }


@dataclass
class ToolRegistry:
    tools: dict[str, Tool] = field(default_factory=dict)
    server_tools: list[dict[str, Any]] = field(default_factory=list)

    def add(self, tool: Tool) -> None:
        self.tools[tool.name] = tool

    def add_server_tool(self, definition: dict[str, Any]) -> None:
        """Serverseitige Anthropic-Tools (z. B. Websuche) — laufen ohne Handler."""
        self.server_tools.append(definition)

    def definitions(self) -> list[dict[str, Any]]:
        return [t.definition() for t in self.tools.values()] + self.server_tools

    def run(self, name: str, payload: dict[str, Any]) -> tuple[str, bool]:
        """Fuehrt ein Werkzeug aus. Gibt (Ergebnistext, is_error) zurueck."""
        tool = self.tools.get(name)
        if tool is None:
            return f"Unbekanntes Werkzeug: {name}", True
        try:
            result = tool.handler(payload or {})
        except Exception as exc:
            log.warning("Werkzeug '%s' fehlgeschlagen:\n%s", name, traceback.format_exc())
            return f"Fehler in '{name}': {exc}", True

        if isinstance(result, str):
            return result, False
        return json.dumps(result, ensure_ascii=False, default=str), False


def build_registry(include_server_tools: bool = True) -> ToolRegistry:
    """Baut das Werkzeug-Register.

    `include_server_tools` steuert die serverseitigen Anthropic-Werkzeuge (Websuche).
    Beim CLI-Backend bleiben sie aus — Claude Code bringt eigene Web-Werkzeuge mit.
    """
    from . import files, memory, system, tasks

    registry = ToolRegistry()
    for module in (system, files, tasks, memory):
        for tool in module.get_tools():
            registry.add(tool)

    if include_server_tools:
        # Laufen serverseitig bei Anthropic — kein eigener Suchmaschinen-Key noetig.
        registry.add_server_tool({"type": "web_search_20260209", "name": "web_search", "max_uses": 8})
        registry.add_server_tool({"type": "web_fetch_20260209", "name": "web_fetch", "max_uses": 5})
    return registry
