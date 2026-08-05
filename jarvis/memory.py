"""Gedaechtnis und Aufgaben — als Markdown im Obsidian-Vault.

Alles wird so geschrieben, dass es in Obsidian lesbar bleibt und du selbst darin
editieren kannst. Die Metadaten stehen in HTML-Kommentaren, die Obsidian ausblendet.
Ohne Vault faellt Jarvis automatisch auf lokale Dateien im data-Ordner zurueck.
"""

from __future__ import annotations

import datetime as dt
import logging
import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .config import config

log = logging.getLogger("jarvis.memory")

_write_lock = threading.Lock()

# "- [x] Titel <!-- id:3 | prio:hoch -->"
TASK_LINE = re.compile(r"^\s*-\s*\[(?P<done>[ xX])\]\s*(?P<title>.*?)\s*(?:<!--\s*(?P<meta>.*?)\s*-->)?\s*$")
# "- Fakt <!-- kategorie | 2026-08-05 -->"
FACT_LINE = re.compile(r"^\s*-\s+(?P<text>.*?)\s*(?:<!--\s*(?P<meta>.*?)\s*-->)?\s*$")

# Ordner, die bei der Vault-Suche uebersprungen werden
SKIP_DIRS = {".obsidian", ".trash", ".git", "node_modules", ".smart-env"}

FACTS_HEADER = """# Gedaechtnis

Was Jarvis dauerhaft ueber dich weiss. Du kannst hier selbst Zeilen ergaenzen oder
loeschen — er liest die Datei bei jedem Gespraech.

"""

TASKS_HEADER = """# Aufgaben

Von Jarvis gefuehrt. Haken kannst du auch selbst setzen.

"""


def _today() -> str:
    return dt.date.today().isoformat()


# --------------------------------------------------------------------------
# Ablageorte
# --------------------------------------------------------------------------


def facts_file() -> Path:
    root = config.memory_root if config.uses_vault else config.data_dir
    return root / "gedaechtnis.md"


def tasks_file() -> Path:
    root = config.memory_root if config.uses_vault else config.data_dir
    return root / "aufgaben.md"


def journal_file(day: dt.date | None = None) -> Path:
    root = config.memory_root if config.uses_vault else config.data_dir
    return root / "protokoll" / f"{(day or dt.date.today()).isoformat()}.md"


def _read(path: Path, header: str) -> str:
    if not path.exists():
        return header
    return path.read_text(encoding="utf-8")


def _write(path: Path, content: str) -> None:
    with _write_lock:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(content, encoding="utf-8")
        tmp.replace(path)


def _parse_meta(raw: str | None) -> dict[str, str]:
    """'id:3 | prio:hoch' -> {'id': '3', 'prio': 'hoch'}"""
    meta: dict[str, str] = {}
    for part in (raw or "").split("|"):
        part = part.strip()
        if not part:
            continue
        key, _, value = part.partition(":")
        meta[key.strip()] = value.strip() if value else ""
    return meta


def _render_meta(meta: dict[str, str]) -> str:
    pairs = " | ".join(f"{k}:{v}" for k, v in meta.items() if v)
    return f" <!-- {pairs} -->" if pairs else ""


# --------------------------------------------------------------------------
# Fakten
# --------------------------------------------------------------------------


@dataclass
class Fact:
    text: str
    kategorie: str = "allgemein"
    notiert: str = ""


def read_facts() -> list[Fact]:
    facts: list[Fact] = []
    for line in _read(facts_file(), FACTS_HEADER).splitlines():
        if not line.lstrip().startswith("- "):
            continue
        match = FACT_LINE.match(line)
        if not match or not match.group("text").strip():
            continue
        meta = _parse_meta(match.group("meta"))
        facts.append(
            Fact(
                text=match.group("text").strip(),
                kategorie=meta.get("kategorie", "allgemein"),
                notiert=meta.get("notiert", ""),
            )
        )
    return facts


def add_fact(text: str, kategorie: str = "allgemein") -> bool:
    """Ergaenzt einen Fakt. False, wenn er schon bekannt war."""
    text = text.strip()
    if not text:
        return False
    if any(f.text.lower() == text.lower() for f in read_facts()):
        return False

    content = _read(facts_file(), FACTS_HEADER).rstrip("\n")
    lines = content.splitlines()
    # An eine bestehende Liste direkt anhaengen, nach Fliesstext eine Leerzeile lassen.
    continues_list = bool(lines) and lines[-1].lstrip().startswith("- ")
    separator = "\n" if continues_list else "\n\n"

    meta = _render_meta({"kategorie": kategorie, "notiert": _today()})
    _write(facts_file(), f"{content}{separator}- {text}{meta}\n")
    return True


def remove_facts(query: str) -> int:
    """Loescht alle Fakten, die den Suchbegriff enthalten. Gibt die Anzahl zurueck."""
    query = query.strip().lower()
    if not query:
        return 0

    kept: list[str] = []
    removed = 0
    for line in _read(facts_file(), FACTS_HEADER).splitlines():
        match = FACT_LINE.match(line) if line.lstrip().startswith("- ") else None
        if match and query in match.group("text").lower():
            removed += 1
            continue
        kept.append(line)

    if removed:
        _write(facts_file(), "\n".join(kept).rstrip("\n") + "\n")
    return removed


def load_memory_context(limit: int = 60) -> str:
    """Kurzfassung fuers System-Prompt."""
    facts = read_facts()[-limit:]
    if not facts:
        return ""
    lines = "\n".join(f"- {f.text}" for f in facts)
    return f"Was du ueber den Nutzer bereits weisst:\n{lines}"


# --------------------------------------------------------------------------
# Aufgaben
# --------------------------------------------------------------------------


@dataclass
class Task:
    id: int
    titel: str
    erledigt: bool
    prioritaet: str = "normal"
    faellig: str = ""
    notiz: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "titel": self.titel,
            "erledigt": self.erledigt,
            "prioritaet": self.prioritaet,
            "faellig": self.faellig or None,
            "notiz": self.notiz or None,
        }

    def render(self) -> str:
        box = "x" if self.erledigt else " "
        meta = _render_meta(
            {
                "id": str(self.id),
                "prio": self.prioritaet,
                "faellig": self.faellig,
                "notiz": self.notiz,
            }
        )
        return f"- [{box}] {self.titel}{meta}"


def read_tasks() -> list[Task]:
    tasks: list[Task] = []
    for line in _read(tasks_file(), TASKS_HEADER).splitlines():
        match = TASK_LINE.match(line)
        if not match or not match.group("title").strip():
            continue
        meta = _parse_meta(match.group("meta"))
        try:
            task_id = int(meta.get("id", "0"))
        except ValueError:
            task_id = 0
        tasks.append(
            Task(
                id=task_id,
                titel=match.group("title").strip(),
                erledigt=match.group("done").lower() == "x",
                prioritaet=meta.get("prio", "normal"),
                faellig=meta.get("faellig", ""),
                notiz=meta.get("notiz", ""),
            )
        )
    return tasks


def _write_tasks(tasks: list[Task]) -> None:
    body = "\n".join(task.render() for task in tasks)
    _write(tasks_file(), TASKS_HEADER + body + "\n")


def add_task(titel: str, notiz: str = "", faellig: str = "", prioritaet: str = "normal") -> Task:
    tasks = read_tasks()
    task = Task(
        id=max((t.id for t in tasks), default=0) + 1,
        titel=titel.strip(),
        erledigt=False,
        prioritaet=prioritaet,
        faellig=faellig.strip(),
        notiz=notiz.strip(),
    )
    tasks.append(task)
    _write_tasks(tasks)
    return task


def set_task_done(task_id: int, done: bool = True) -> Task | None:
    tasks = read_tasks()
    for task in tasks:
        if task.id == task_id:
            task.erledigt = done
            _write_tasks(tasks)
            return task
    return None


def delete_task(task_id: int) -> bool:
    tasks = read_tasks()
    remaining = [t for t in tasks if t.id != task_id]
    if len(remaining) == len(tasks):
        return False
    _write_tasks(remaining)
    return True


# --------------------------------------------------------------------------
# Gespraechsprotokoll
# --------------------------------------------------------------------------


def log_exchange(user_text: str, reply: str, channel: str = "hud") -> None:
    """Haengt einen Gespraechszug ans Tagesprotokoll an."""
    if not (user_text or reply):
        return
    path = journal_file()
    stamp = dt.datetime.now().strftime("%H:%M")
    header = f"# Protokoll {_today()}\n\n" if not path.exists() else ""
    entry = f"## {stamp} · {channel}\n\n**Du:** {user_text.strip()}\n\n**Jarvis:** {reply.strip()}\n\n"
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with _write_lock, path.open("a", encoding="utf-8") as fh:
            fh.write(header + entry)
    except OSError as exc:
        log.warning("Protokoll konnte nicht geschrieben werden: %s", exc)


# --------------------------------------------------------------------------
# Vault-Suche
# --------------------------------------------------------------------------


def search_vault(query: str, limit: int = 20) -> list[dict[str, Any]]:
    """Durchsucht alle Markdown-Notizen im Vault nach Text."""
    if not config.uses_vault:
        return []
    needle = query.strip().lower()
    if not needle:
        return []

    hits: list[dict[str, Any]] = []
    for path in config.vault.rglob("*.md"):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        index = text.lower().find(needle)
        if index < 0:
            continue
        hits.append(
            {
                "notiz": str(path.relative_to(config.vault)),
                "fundstelle": text[max(0, index - 80) : index + 160].replace("\n", " ").strip(),
            }
        )
        if len(hits) >= limit:
            break
    return hits


def read_note(relative: str) -> str:
    """Liest eine Notiz aus dem Vault. Pfad wird gegen Ausbrechen geprueft."""
    if not config.uses_vault:
        return "Kein Obsidian-Vault konfiguriert."
    target = (config.vault / relative.strip()).resolve()
    if config.vault.resolve() not in target.parents:
        return f"Zugriff verweigert: '{relative}' liegt ausserhalb des Vaults."
    if not target.is_file():
        return f"Notiz '{relative}' existiert nicht."
    return target.read_text(encoding="utf-8", errors="replace")[:200_000]
