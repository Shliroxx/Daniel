"""Nacht-Auftraege: eine Markdown-Datei voller Aufgaben ueber Nacht abarbeiten.

Du schreibst abends eine Liste, Jarvis arbeitet sie nacheinander mit `claude -p` ab und
legt die Ergebnisse ab. Laeuft dein Kontingent unterwegs leer, wartet er und versucht es
spaeter erneut — aber nur bis zur eingestellten Sperrstunde, damit tagsueber wieder
genug uebrig ist.

Aufruf:
    python -m jarvis.orchestrator auftraege.md
    python -m jarvis.orchestrator auftraege.md --trocken     # nur anzeigen, nichts tun

Dateiformat siehe templates/00-auftragsdatei.md
"""

from __future__ import annotations

import argparse
import datetime as dt
import logging
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .config import config

log = logging.getLogger("jarvis.orchestrator")

# "## Aufgabe: Recherche zu X"
HEADING = re.compile(r"^##\s+(?:Aufgabe|Task)\s*:\s*(?P<title>.+?)\s*$", re.IGNORECASE)
# "- modell: opus"
OPTION = re.compile(r"^[-*]\s*(?P<key>[a-zA-ZäöüÄÖÜ_]+)\s*:\s*(?P<value>.+?)\s*$")
# "- [ ] Kurzaufgabe"
CHECKBOX = re.compile(r"^(?P<indent>\s*)-\s*\[(?P<state>[ xX!])\]\s*(?P<text>.+?)\s*$")
FRONTMATTER = re.compile(r"\A---\s*\n(?P<body>.*?)\n---\s*\n", re.DOTALL)

# Deutsche wie englische Schluesselwoerter akzeptieren
ALIASES = {
    "modell": "model", "model": "model",
    "start": "schedule", "zeitpunkt": "schedule", "schedule": "schedule",
    "ausgabe": "output", "output": "output",
    "versuche": "retry", "retry": "retry",
}

STATUS_MARK = {"offen": " ", "fertig": "x", "fehler": "!"}


@dataclass
class Job:
    title: str
    prompt: str
    model: str = "sonnet"
    schedule: str = ""          # "01:30" — vorher wird gewartet
    output: str = ""
    retry: int = 4
    line: int = -1              # Zeile der Checkbox, falls vorhanden
    status: str = "offen"
    detail: str = ""


@dataclass
class Plan:
    jobs: list[Job] = field(default_factory=list)
    defaults: dict[str, Any] = field(default_factory=dict)
    curfew: str = "07:00"
    source: Path | None = None


# --------------------------------------------------------------------------
# Einlesen
# --------------------------------------------------------------------------


def _parse_frontmatter(text: str) -> tuple[dict[str, Any], str]:
    """Winziger YAML-Parser fuer flache key: value-Bloecke — keine Abhaengigkeit noetig."""
    match = FRONTMATTER.match(text)
    if not match:
        return {}, text

    data: dict[str, Any] = {}
    for raw in match.group("body").splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip() or line.strip().startswith("-"):
            continue
        key, _, value = line.partition(":")
        value = value.strip().strip("\"'")
        if value:
            data[key.strip()] = value
    return data, text[match.end():]


def parse_plan(path: Path) -> Plan:
    text = path.read_text(encoding="utf-8")
    defaults, body = _parse_frontmatter(text)
    lines = body.splitlines()
    offset = len(text.splitlines()) - len(lines)  # Zeilen, die das Frontmatter belegt

    plan = Plan(
        defaults=defaults,
        curfew=str(defaults.get("sperrstunde") or defaults.get("curfew") or "07:00"),
        source=path,
    )
    default_model = str(defaults.get("modell") or defaults.get("default_model") or "sonnet")
    default_retry = int(defaults.get("versuche") or defaults.get("retry") or 4)

    current: Job | None = None
    prompt_lines: list[str] = []

    def close_current() -> None:
        nonlocal current, prompt_lines
        if current is not None:
            current.prompt = "\n".join(prompt_lines).strip()
            if current.prompt:
                plan.jobs.append(current)
            else:
                log.warning("Aufgabe '%s' hat keinen Prompt — uebersprungen.", current.title)
        current, prompt_lines = None, []

    for index, line in enumerate(lines):
        heading = HEADING.match(line)
        if heading:
            close_current()
            current = Job(
                title=heading.group("title"),
                prompt="",
                model=default_model,
                retry=default_retry,
            )
            continue

        if current is not None:
            # Leerzeilen vor dem Prompt gehoeren noch zum Optionsblock.
            if not line.strip() and not any(l.strip() for l in prompt_lines):
                continue

            option = OPTION.match(line)
            if option and not any(l.strip() for l in prompt_lines):
                key = ALIASES.get(option.group("key").lower())
                value = option.group("value").strip()
                if key == "model":
                    current.model = value
                elif key == "schedule":
                    current.schedule = value
                elif key == "output":
                    current.output = value
                elif key == "retry":
                    current.retry = int(value)
                if key:
                    continue
            prompt_lines.append(line)
            continue

        # Ausserhalb eines Abschnitts: einfache Checkboxen als Kurzaufgaben
        checkbox = CHECKBOX.match(line)
        if checkbox and checkbox.group("state") == " ":
            plan.jobs.append(
                Job(
                    title=checkbox.group("text"),
                    prompt=checkbox.group("text"),
                    model=default_model,
                    retry=default_retry,
                    line=offset + index,
                )
            )

    close_current()
    return plan


# --------------------------------------------------------------------------
# Ausfuehren
# --------------------------------------------------------------------------


def _parse_time(value: str) -> dt.time | None:
    for fmt in ("%H:%M", "%H.%M", "%H"):
        try:
            return dt.datetime.strptime(value.strip(), fmt).time()
        except ValueError:
            continue
    return None


def _past_curfew(curfew: str) -> bool:
    """True, wenn die Sperrstunde erreicht ist (typisch morgens um 7)."""
    limit = _parse_time(curfew)
    if limit is None:
        return False
    now = dt.datetime.now().time()
    # Sperrstunden liegen morgens; nachts (nach 20 Uhr) gilt sie noch nicht.
    return limit <= now < dt.time(20, 0)


def _wait_until(target: dt.time) -> None:
    now = dt.datetime.now()
    when = dt.datetime.combine(now.date(), target)
    if when <= now:
        when += dt.timedelta(days=1)
    seconds = (when - now).total_seconds()
    log.info("Warte bis %s (%.0f Minuten).", target.strftime("%H:%M"), seconds / 60)
    time.sleep(seconds)


def _output_path(job: Job, plan: Plan) -> Path:
    if job.output:
        return Path(job.output).expanduser()
    base = plan.defaults.get("ausgabeordner") or plan.defaults.get("default_output_dir")
    folder = Path(base).expanduser() if base else config.outbox
    safe = re.sub(r"[^\w\s-]", "", job.title).strip().replace(" ", "-").lower()[:60] or "auftrag"
    return folder / f"{dt.date.today().isoformat()}-{safe}.md"


def _build_prompt(job: Job, target: Path) -> str:
    return (
        f"{job.prompt}\n\n"
        f"Schreib das Ergebnis nach {target}. Lege fehlende Ordner an. "
        f"Antworte am Ende mit einer Zeile, was du getan hast."
    )


def run_job(job: Job, plan: Plan, dry_run: bool = False) -> Job:
    target = _output_path(job, plan)

    if job.schedule:
        when = _parse_time(job.schedule)
        if when and not dry_run:
            _wait_until(when)

    allowed = ["Read", "Write", "Edit", "Grep", "Glob", "WebSearch", "WebFetch", "Bash"]
    cmd = [
        config.claude_bin,
        "-p", _build_prompt(job, target),
        "--model", job.model,
        "--output-format", "text",
        "--permission-mode", "acceptEdits",
        "--allowedTools", ",".join(allowed),
    ]
    for directory in {target.parent, *config.write_dirs()}:
        cmd += ["--add-dir", str(directory)]

    if dry_run:
        log.info("[trocken] %s -> %s (Modell %s)", job.title, target, job.model)
        job.status = "offen"
        job.detail = f"Trockenlauf, Ziel waere {target}"
        return job

    target.parent.mkdir(parents=True, exist_ok=True)

    for attempt in range(1, job.retry + 1):
        log.info("Aufgabe '%s' — Versuch %d/%d (Modell %s)", job.title, attempt, job.retry, job.model)
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=3 * 60 * 60)
        except FileNotFoundError:
            job.status, job.detail = "fehler", f"'{config.claude_bin}' nicht gefunden"
            return job
        except subprocess.TimeoutExpired:
            job.status, job.detail = "fehler", "Zeitueberschreitung nach 3 Stunden"
            return job

        output = (result.stdout or "").strip()
        errors = (result.stderr or "").strip()

        if result.returncode == 0:
            job.status = "fertig"
            job.detail = output.splitlines()[-1][:200] if output else f"Ergebnis in {target}"
            log.info("Aufgabe '%s' fertig.", job.title)
            return job

        combined = f"{output}\n{errors}".lower()
        limit_reached = any(
            word in combined for word in ("rate limit", "usage limit", "quota", "kontingent", "429")
        )
        if not limit_reached:
            job.status = "fehler"
            job.detail = (errors or output or f"Exit {result.returncode}")[:300]
            log.error("Aufgabe '%s' fehlgeschlagen: %s", job.title, job.detail)
            return job

        if _past_curfew(plan.curfew):
            job.status = "fehler"
            job.detail = f"Kontingent erschoepft, Sperrstunde {plan.curfew} erreicht"
            log.warning("Sperrstunde %s erreicht — breche ab.", plan.curfew)
            return job

        if attempt < job.retry:
            log.warning("Kontingent erschoepft — warte eine Stunde und versuche es erneut.")
            time.sleep(3600)

    job.status = "fehler"
    job.detail = "Kontingent nach allen Versuchen weiterhin erschoepft"
    return job


# --------------------------------------------------------------------------
# Bericht
# --------------------------------------------------------------------------


def update_checkboxes(plan: Plan) -> None:
    """Setzt die Haken in der Auftragsdatei: [x] fertig, [!] fehlgeschlagen."""
    if plan.source is None:
        return
    marked = {job.line: job for job in plan.jobs if job.line >= 0}
    if not marked:
        return

    lines = plan.source.read_text(encoding="utf-8").splitlines()
    for index, job in marked.items():
        if not 0 <= index < len(lines):
            continue
        match = CHECKBOX.match(lines[index])
        if match:
            mark = STATUS_MARK.get(job.status, " ")
            lines[index] = f"{match.group('indent')}- [{mark}] {match.group('text')}"
    plan.source.write_text("\n".join(lines) + "\n", encoding="utf-8")


def append_report(plan: Plan) -> None:
    if plan.source is None:
        return
    fertig = sum(1 for j in plan.jobs if j.status == "fertig")
    stamp = dt.datetime.now().strftime("%d.%m.%Y %H:%M")

    lines = [f"\n\n---\n\n## Durchlauf {stamp}\n"]
    lines.append(f"{fertig} von {len(plan.jobs)} Aufgaben erledigt.\n")
    for job in plan.jobs:
        symbol = {"fertig": "✓", "fehler": "✕"}.get(job.status, "·")
        lines.append(f"- {symbol} **{job.title}** — {job.detail or job.status}")
    with plan.source.open("a", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def run_plan(plan: Plan, dry_run: bool = False) -> int:
    if not plan.jobs:
        log.warning("Keine Aufgaben in der Datei gefunden.")
        return 0

    log.info("%d Aufgabe(n) gefunden. Sperrstunde: %s", len(plan.jobs), plan.curfew)
    for job in plan.jobs:
        if _past_curfew(plan.curfew) and not dry_run:
            job.status, job.detail = "fehler", f"Sperrstunde {plan.curfew} erreicht"
            log.warning("Sperrstunde erreicht — restliche Aufgaben bleiben offen.")
            break
        run_job(job, plan, dry_run)

    if dry_run:
        return 0

    update_checkboxes(plan)
    append_report(plan)

    fertig = sum(1 for j in plan.jobs if j.status == "fertig")
    log.info("Fertig: %d von %d Aufgaben.", fertig, len(plan.jobs))
    return 0 if fertig == len(plan.jobs) else 1


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)-7s %(message)s",
        datefmt="%H:%M:%S",
        handlers=[
            logging.StreamHandler(),
            logging.FileHandler(config.log_dir / "orchestrator.log", encoding="utf-8"),
        ],
    )

    parser = argparse.ArgumentParser(description="Arbeitet eine Auftragsdatei ueber Nacht ab.")
    parser.add_argument("datei", type=Path, help="Markdown-Datei mit den Aufgaben")
    parser.add_argument("--trocken", action="store_true", help="nur anzeigen, nichts ausfuehren")
    args = parser.parse_args(argv)

    path = args.datei.expanduser()
    if not path.is_file():
        parser.error(f"Datei nicht gefunden: {path}")

    return run_plan(parse_plan(path), dry_run=args.trocken)


if __name__ == "__main__":
    sys.exit(main())
