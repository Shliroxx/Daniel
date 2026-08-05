"""Aufgabenliste — als Checkboxen im Obsidian-Vault."""

from __future__ import annotations

from typing import Any

from .. import memory
from .registry import Tool


def add_task(payload: dict[str, Any]) -> str:
    title = str(payload.get("title", "")).strip()
    if not title:
        return "Kein Aufgabentext angegeben."
    task = memory.add_task(
        title,
        notiz=str(payload.get("note", "")),
        faellig=str(payload.get("due", "")),
        prioritaet=str(payload.get("priority", "normal")),
    )
    return f"Aufgabe #{task.id} angelegt: {task.titel}"


def list_tasks(payload: dict[str, Any]) -> dict[str, Any]:
    include_done = bool(payload.get("include_done", False))
    tasks = memory.read_tasks()
    visible = tasks if include_done else [t for t in tasks if not t.erledigt]
    return {
        "offen": sum(1 for t in tasks if not t.erledigt),
        "aufgaben": [t.to_dict() for t in visible],
        "quelle": str(memory.tasks_file()),
    }


def complete_task(payload: dict[str, Any]) -> str:
    task = memory.set_task_done(int(payload.get("id", 0)), True)
    if task is None:
        return f"Keine Aufgabe mit der Nummer {payload.get('id')} gefunden."
    return f"Aufgabe #{task.id} abgehakt: {task.titel}"


def reopen_task(payload: dict[str, Any]) -> str:
    task = memory.set_task_done(int(payload.get("id", 0)), False)
    if task is None:
        return f"Keine Aufgabe mit der Nummer {payload.get('id')} gefunden."
    return f"Aufgabe #{task.id} wieder geoeffnet: {task.titel}"


def delete_task(payload: dict[str, Any]) -> str:
    task_id = int(payload.get("id", 0))
    return (
        f"Aufgabe #{task_id} geloescht."
        if memory.delete_task(task_id)
        else f"Keine Aufgabe mit der Nummer {task_id} gefunden."
    )


def get_tools() -> list[Tool]:
    return [
        Tool(
            name="add_task",
            description="Legt eine Aufgabe in der persoenlichen To-do-Liste an.",
            input_schema={
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "note": {"type": "string"},
                    "due": {"type": "string", "description": "Faelligkeit als Text, z. B. 'morgen 14 Uhr'"},
                    "priority": {"type": "string", "enum": ["niedrig", "normal", "hoch"]},
                },
                "required": ["title"],
            },
            handler=add_task,
        ),
        Tool(
            name="list_tasks",
            description="Zeigt die offenen Aufgaben (optional inklusive erledigter).",
            input_schema={
                "type": "object",
                "properties": {"include_done": {"type": "boolean"}},
            },
            handler=list_tasks,
        ),
        Tool(
            name="complete_task",
            description="Hakt eine Aufgabe ab.",
            input_schema={
                "type": "object",
                "properties": {"id": {"type": "integer"}},
                "required": ["id"],
            },
            handler=complete_task,
        ),
        Tool(
            name="reopen_task",
            description="Macht das Abhaken einer Aufgabe rueckgaengig.",
            input_schema={
                "type": "object",
                "properties": {"id": {"type": "integer"}},
                "required": ["id"],
            },
            handler=reopen_task,
        ),
        Tool(
            name="delete_task",
            description="Loescht eine Aufgabe endgueltig.",
            input_schema={
                "type": "object",
                "properties": {"id": {"type": "integer"}},
                "required": ["id"],
            },
            handler=delete_task,
        ),
    ]
