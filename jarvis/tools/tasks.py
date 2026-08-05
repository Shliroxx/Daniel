"""Aufgabenliste — damit Jarvis Dinge fuer dich erledigen und nachhalten kann."""

from __future__ import annotations

import datetime as dt
from typing import Any

from ..config import config
from . import store
from .registry import Tool

TASKS_FILE = config.data_dir / "tasks.json"


def _load() -> list[dict[str, Any]]:
    return store.load(TASKS_FILE, [])


def _save(tasks: list[dict[str, Any]]) -> None:
    store.save(TASKS_FILE, tasks)


def add_task(payload: dict[str, Any]) -> str:
    title = str(payload.get("title", "")).strip()
    if not title:
        return "Kein Aufgabentext angegeben."

    tasks = _load()
    task = {
        "id": (max((t["id"] for t in tasks), default=0) + 1),
        "titel": title,
        "notiz": str(payload.get("note", "")).strip() or None,
        "faellig": str(payload.get("due", "")).strip() or None,
        "prioritaet": str(payload.get("priority", "normal")),
        "erledigt": False,
        "erstellt": dt.datetime.now().isoformat(timespec="seconds"),
    }
    tasks.append(task)
    _save(tasks)
    return f"Aufgabe #{task['id']} angelegt: {title}"


def list_tasks(payload: dict[str, Any]) -> dict[str, Any]:
    include_done = bool(payload.get("include_done", False))
    tasks = _load()
    visible = tasks if include_done else [t for t in tasks if not t.get("erledigt")]
    return {"offen": sum(1 for t in tasks if not t.get("erledigt")), "aufgaben": visible}


def complete_task(payload: dict[str, Any]) -> str:
    task_id = payload.get("id")
    tasks = _load()
    for task in tasks:
        if task["id"] == task_id:
            task["erledigt"] = True
            task["erledigt_am"] = dt.datetime.now().isoformat(timespec="seconds")
            _save(tasks)
            return f"Aufgabe #{task_id} als erledigt markiert: {task['titel']}"
    return f"Keine Aufgabe mit der Nummer {task_id} gefunden."


def delete_task(payload: dict[str, Any]) -> str:
    task_id = payload.get("id")
    tasks = _load()
    remaining = [t for t in tasks if t["id"] != task_id]
    if len(remaining) == len(tasks):
        return f"Keine Aufgabe mit der Nummer {task_id} gefunden."
    _save(remaining)
    return f"Aufgabe #{task_id} geloescht."


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
            description="Markiert eine Aufgabe als erledigt.",
            input_schema={
                "type": "object",
                "properties": {"id": {"type": "integer"}},
                "required": ["id"],
            },
            handler=complete_task,
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
