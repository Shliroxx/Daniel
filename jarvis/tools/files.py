"""Dateizugriff — streng auf das konfigurierte Arbeitsverzeichnis begrenzt."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from ..config import config
from .registry import Tool

MAX_READ = 200_000  # Zeichen


def _safe_path(raw: str) -> Path:
    """Loest einen Pfad auf und stellt sicher, dass er im Workspace bleibt."""
    candidate = Path(raw.strip() or ".")
    if candidate.is_absolute():
        resolved = candidate.resolve()
    else:
        resolved = (config.workspace / candidate).resolve()
    root = config.workspace.resolve()
    if resolved != root and root not in resolved.parents:
        raise ValueError(
            f"Zugriff verweigert: '{raw}' liegt ausserhalb des Arbeitsverzeichnisses ({root})."
        )
    return resolved


def list_files(payload: dict[str, Any]) -> dict[str, Any]:
    target = _safe_path(str(payload.get("path", ".")))
    if not target.exists():
        return {"fehler": f"'{target}' existiert nicht."}
    if target.is_file():
        return {"datei": str(target), "groesse_bytes": target.stat().st_size}

    entries = []
    for item in sorted(target.iterdir(), key=lambda p: (p.is_file(), p.name.lower())):
        entries.append(
            {
                "name": item.name,
                "typ": "ordner" if item.is_dir() else "datei",
                "groesse_bytes": item.stat().st_size if item.is_file() else None,
            }
        )
    return {"ordner": str(target), "eintraege": entries[:500]}


def read_file(payload: dict[str, Any]) -> str:
    target = _safe_path(str(payload.get("path", "")))
    if not target.is_file():
        return f"'{target}' ist keine Datei."
    text = target.read_text(encoding="utf-8", errors="replace")
    if len(text) > MAX_READ:
        return text[:MAX_READ] + f"\n\n[... gekuerzt, insgesamt {len(text)} Zeichen]"
    return text


def write_file(payload: dict[str, Any]) -> str:
    target = _safe_path(str(payload.get("path", "")))
    content = str(payload.get("content", ""))
    append = bool(payload.get("append", False))

    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("a" if append else "w", encoding="utf-8") as fh:
        fh.write(content)
    verb = "ergaenzt" if append else "geschrieben"
    return f"{len(content)} Zeichen in '{target}' {verb}."


def search_files(payload: dict[str, Any]) -> dict[str, Any]:
    pattern = str(payload.get("pattern", "*")).strip() or "*"
    contains = str(payload.get("contains", "")).strip()
    root = _safe_path(str(payload.get("path", ".")))

    hits: list[dict[str, Any]] = []
    for item in root.rglob(pattern):
        if not item.is_file():
            continue
        entry: dict[str, Any] = {"pfad": str(item.relative_to(config.workspace))}
        if contains:
            try:
                text = item.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            if contains.lower() not in text.lower():
                continue
            index = text.lower().find(contains.lower())
            entry["fundstelle"] = text[max(0, index - 60) : index + 120].replace("\n", " ")
        hits.append(entry)
        if len(hits) >= 100:
            break
    return {"treffer": len(hits), "dateien": hits}


def get_tools() -> list[Tool]:
    root_hint = f"Alle Pfade sind relativ zum Arbeitsverzeichnis ({config.workspace})."
    return [
        Tool(
            name="list_files",
            description=f"Listet Dateien und Ordner. {root_hint}",
            input_schema={
                "type": "object",
                "properties": {"path": {"type": "string", "description": "Ordner, Standard '.'"}},
            },
            handler=list_files,
        ),
        Tool(
            name="read_file",
            description=f"Liest eine Textdatei. {root_hint}",
            input_schema={
                "type": "object",
                "properties": {"path": {"type": "string"}},
                "required": ["path"],
            },
            handler=read_file,
        ),
        Tool(
            name="write_file",
            description=(
                f"Schreibt Text in eine Datei (legt Ordner bei Bedarf an). {root_hint} "
                "Mit append=true wird angehaengt statt ueberschrieben."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "path": {"type": "string"},
                    "content": {"type": "string"},
                    "append": {"type": "boolean"},
                },
                "required": ["path", "content"],
            },
            handler=write_file,
        ),
        Tool(
            name="search_files",
            description=(
                f"Sucht Dateien per Glob-Muster und optional nach Text im Inhalt. {root_hint}"
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "pattern": {"type": "string", "description": "z. B. '*.md' oder '**/*.py'"},
                    "contains": {"type": "string", "description": "Text, der vorkommen muss"},
                    "path": {"type": "string", "description": "Startordner, Standard '.'"},
                },
            },
            handler=search_files,
        ),
    ]
