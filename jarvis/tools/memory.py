"""Werkzeuge fuer Langzeitgedaechtnis und Vault-Zugriff."""

from __future__ import annotations

from typing import Any

from .. import memory
from ..config import config
from .registry import Tool


def remember(payload: dict[str, Any]) -> str:
    fact = str(payload.get("fact", "")).strip()
    if not fact:
        return "Nichts zum Merken angegeben."
    added = memory.add_fact(fact, str(payload.get("category", "allgemein")))
    return f"Gemerkt: {fact}" if added else "Das weiss ich bereits."


def recall(payload: dict[str, Any]) -> dict[str, Any]:
    query = str(payload.get("query", "")).strip().lower()
    facts = memory.read_facts()
    if query:
        facts = [f for f in facts if query in f.text.lower() or query in f.kategorie.lower()]
    return {
        "anzahl": len(facts),
        "fakten": [{"text": f.text, "kategorie": f.kategorie} for f in facts[-50:]],
        "quelle": str(memory.facts_file()),
    }


def forget(payload: dict[str, Any]) -> str:
    removed = memory.remove_facts(str(payload.get("query", "")))
    return f"{removed} Eintrag(e) geloescht." if removed else "Nichts Passendes gefunden."


def search_notes(payload: dict[str, Any]) -> dict[str, Any]:
    if not config.uses_vault:
        return {"fehler": "Kein Obsidian-Vault konfiguriert (JARVIS_VAULT in der .env)."}
    query = str(payload.get("query", ""))
    hits = memory.search_vault(query, limit=int(payload.get("limit", 20)))
    return {"treffer": len(hits), "notizen": hits}


def read_note(payload: dict[str, Any]) -> str:
    return memory.read_note(str(payload.get("path", "")))


def get_tools() -> list[Tool]:
    tools = [
        Tool(
            name="remember",
            description=(
                "Merkt sich dauerhaft eine Information ueber den Nutzer (Vorlieben, Namen, "
                "Gewohnheiten, laufende Projekte). Nur nutzen, wenn es spaeter wirklich "
                "nuetzlich ist. Landet als Zeile in der Gedaechtnis-Notiz."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "fact": {"type": "string"},
                    "category": {"type": "string", "description": "z. B. 'arbeit', 'privat', 'technik'"},
                },
                "required": ["fact"],
            },
            handler=remember,
        ),
        Tool(
            name="recall",
            description="Durchsucht das Langzeitgedaechtnis nach gespeicherten Fakten.",
            input_schema={
                "type": "object",
                "properties": {"query": {"type": "string"}},
            },
            handler=recall,
        ),
        Tool(
            name="forget",
            description="Loescht gespeicherte Fakten, die den Suchbegriff enthalten.",
            input_schema={
                "type": "object",
                "properties": {"query": {"type": "string"}},
                "required": ["query"],
            },
            handler=forget,
        ),
    ]

    if config.vault_enabled:
        tools += [
            Tool(
                name="search_notes",
                description=(
                    "Durchsucht alle Notizen im Obsidian-Vault nach einem Begriff und liefert "
                    "Fundstellen mit Pfad. Nutze das, bevor du eine Frage zum Wissen des "
                    "Nutzers aus dem Bauch beantwortest."
                ),
                input_schema={
                    "type": "object",
                    "properties": {
                        "query": {"type": "string"},
                        "limit": {"type": "integer", "description": "max. Treffer, Standard 20"},
                    },
                    "required": ["query"],
                },
                handler=search_notes,
            ),
            Tool(
                name="read_note",
                description="Liest eine Notiz aus dem Obsidian-Vault (Pfad relativ zum Vault).",
                input_schema={
                    "type": "object",
                    "properties": {"path": {"type": "string"}},
                    "required": ["path"],
                },
                handler=read_note,
            ),
        ]
    return tools
