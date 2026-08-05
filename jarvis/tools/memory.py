"""Langzeitgedaechtnis — Fakten, die Jarvis ueber Sitzungen hinweg behalten soll."""

from __future__ import annotations

import datetime as dt
from typing import Any

from ..config import config
from . import store
from .registry import Tool

MEMORY_FILE = config.data_dir / "memory.json"
MAX_FACTS = 300


def _load() -> list[dict[str, Any]]:
    return store.load(MEMORY_FILE, [])


def remember(payload: dict[str, Any]) -> str:
    fact = str(payload.get("fact", "")).strip()
    if not fact:
        return "Nichts zum Merken angegeben."

    facts = _load()
    if any(f["text"].lower() == fact.lower() for f in facts):
        return "Das weiss ich bereits."

    facts.append(
        {
            "text": fact,
            "kategorie": str(payload.get("category", "allgemein")),
            "notiert": dt.datetime.now().isoformat(timespec="seconds"),
        }
    )
    store.save(MEMORY_FILE, facts[-MAX_FACTS:])
    return f"Gemerkt: {fact}"


def recall(payload: dict[str, Any]) -> dict[str, Any]:
    query = str(payload.get("query", "")).strip().lower()
    facts = _load()
    if query:
        facts = [f for f in facts if query in f["text"].lower() or query in f.get("kategorie", "").lower()]
    return {"anzahl": len(facts), "fakten": facts[-50:]}


def forget(payload: dict[str, Any]) -> str:
    query = str(payload.get("query", "")).strip().lower()
    if not query:
        return "Bitte angeben, was vergessen werden soll."
    facts = _load()
    remaining = [f for f in facts if query not in f["text"].lower()]
    removed = len(facts) - len(remaining)
    store.save(MEMORY_FILE, remaining)
    return f"{removed} Eintrag(e) geloescht." if removed else "Nichts Passendes gefunden."


def load_memory_context(limit: int = 60) -> str:
    """Wird beim Start in den System-Prompt gehaengt."""
    facts = _load()[-limit:]
    if not facts:
        return ""
    lines = "\n".join(f"- {f['text']}" for f in facts)
    return f"Was du ueber den Nutzer bereits weisst:\n{lines}"


def get_tools() -> list[Tool]:
    return [
        Tool(
            name="remember",
            description=(
                "Merkt sich dauerhaft eine Information ueber den Nutzer (Vorlieben, Namen, "
                "Gewohnheiten, laufende Projekte). Nur nutzen, wenn es spaeter wirklich nuetzlich ist."
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
