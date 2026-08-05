"""PC-Steuerung: Programme oeffnen, Lautstaerke, Screenshots, Systeminfos."""

from __future__ import annotations

import datetime as dt
import logging
import os
import platform
import shlex
import shutil
import subprocess
import sys
import webbrowser
from pathlib import Path
from typing import Any

from ..config import config
from .registry import Tool

log = logging.getLogger("jarvis.tools.system")

IS_WINDOWS = sys.platform.startswith("win")
IS_MAC = sys.platform == "darwin"

# Kurznamen -> was tatsaechlich gestartet wird
APP_ALIASES: dict[str, dict[str, str]] = {
    "browser": {"win32": "msedge", "darwin": "Safari", "linux": "xdg-open https://start.duckduckgo.com"},
    "chrome": {"win32": "chrome", "darwin": "Google Chrome", "linux": "google-chrome"},
    "firefox": {"win32": "firefox", "darwin": "Firefox", "linux": "firefox"},
    "explorer": {"win32": "explorer", "darwin": "Finder", "linux": "xdg-open ."},
    "dateien": {"win32": "explorer", "darwin": "Finder", "linux": "xdg-open ."},
    "notepad": {"win32": "notepad", "darwin": "TextEdit", "linux": "gedit"},
    "editor": {"win32": "notepad", "darwin": "TextEdit", "linux": "gedit"},
    "terminal": {"win32": "wt", "darwin": "Terminal", "linux": "x-terminal-emulator"},
    "rechner": {"win32": "calc", "darwin": "Calculator", "linux": "gnome-calculator"},
    "calculator": {"win32": "calc", "darwin": "Calculator", "linux": "gnome-calculator"},
    "spotify": {"win32": "spotify", "darwin": "Spotify", "linux": "spotify"},
    "vscode": {"win32": "code", "darwin": "Visual Studio Code", "linux": "code"},
    "code": {"win32": "code", "darwin": "Visual Studio Code", "linux": "code"},
    "discord": {"win32": "discord", "darwin": "Discord", "linux": "discord"},
    "einstellungen": {"win32": "ms-settings:", "darwin": "System Settings", "linux": "gnome-control-center"},
}


def _platform_key() -> str:
    if IS_WINDOWS:
        return "win32"
    if IS_MAC:
        return "darwin"
    return "linux"


# --- Handler ---------------------------------------------------------------


def open_app(payload: dict[str, Any]) -> str:
    name = str(payload.get("name", "")).strip()
    if not name:
        return "Kein Programmname angegeben."

    key = _platform_key()
    target = APP_ALIASES.get(name.lower(), {}).get(key, name)

    try:
        if IS_WINDOWS:
            os.startfile(target)  # type: ignore[attr-defined]
        elif IS_MAC:
            subprocess.Popen(["open", "-a", target])
        else:
            subprocess.Popen(shlex.split(target))
    except Exception as exc:
        return f"Konnte '{name}' nicht starten: {exc}"
    return f"'{name}' wurde gestartet."


def open_url(payload: dict[str, Any]) -> str:
    url = str(payload.get("url", "")).strip()
    if not url:
        return "Keine URL angegeben."
    if not url.startswith(("http://", "https://")):
        url = "https://" + url
    webbrowser.open(url)
    return f"Geoeffnet: {url}"


def take_screenshot(payload: dict[str, Any]) -> dict[str, Any]:
    import mss
    import mss.tools

    monitor_index = int(payload.get("monitor", 0))
    shots_dir = config.workspace / "screenshots"
    shots_dir.mkdir(parents=True, exist_ok=True)
    path = shots_dir / f"screenshot-{dt.datetime.now():%Y%m%d-%H%M%S}.png"

    with mss.mss() as sct:
        monitors = sct.monitors
        monitor = monitors[monitor_index] if 0 <= monitor_index < len(monitors) else monitors[0]
        img = sct.grab(monitor)
        mss.tools.to_png(img.rgb, img.size, output=str(path))

    return {"gespeichert": str(path), "breite": img.size[0], "hoehe": img.size[1]}


def set_volume(payload: dict[str, Any]) -> str:
    percent = payload.get("percent")
    mute = payload.get("mute")

    if IS_WINDOWS:
        try:
            from comtypes import CLSCTX_ALL
            from ctypes import POINTER, cast
            from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume

            devices = AudioUtilities.GetSpeakers()
            interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
            volume = cast(interface, POINTER(IAudioEndpointVolume))
            if mute is not None:
                volume.SetMute(bool(mute), None)
                return "Ton stumm." if mute else "Ton wieder an."
            if percent is not None:
                level = max(0, min(100, int(percent)))
                volume.SetMasterVolumeLevelScalar(level / 100.0, None)
                return f"Lautstaerke auf {level}% gesetzt."
            current = round(volume.GetMasterVolumeLevelScalar() * 100)
            return f"Aktuelle Lautstaerke: {current}%."
        except Exception as exc:
            return f"Lautstaerke nicht steuerbar: {exc}"

    if IS_MAC:
        if mute is not None:
            subprocess.run(["osascript", "-e", f"set volume output muted {str(bool(mute)).lower()}"])
            return "Ton stumm." if mute else "Ton wieder an."
        if percent is not None:
            level = max(0, min(100, int(percent)))
            subprocess.run(["osascript", "-e", f"set volume output volume {level}"])
            return f"Lautstaerke auf {level}% gesetzt."
        return "Lautstaerke unveraendert."

    if shutil.which("pactl"):
        if mute is not None:
            subprocess.run(["pactl", "set-sink-mute", "@DEFAULT_SINK@", "1" if mute else "0"])
            return "Ton stumm." if mute else "Ton wieder an."
        if percent is not None:
            level = max(0, min(100, int(percent)))
            subprocess.run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{level}%"])
            return f"Lautstaerke auf {level}% gesetzt."
    return "Lautstaerkeregelung auf diesem System nicht verfuegbar."


def system_info(_payload: dict[str, Any]) -> dict[str, Any]:
    import psutil

    mem = psutil.virtual_memory()
    disk = psutil.disk_usage(str(Path.home()))
    info: dict[str, Any] = {
        "zeit": dt.datetime.now().strftime("%A, %d.%m.%Y %H:%M"),
        "system": f"{platform.system()} {platform.release()}",
        "cpu_prozent": psutil.cpu_percent(interval=0.3),
        "cpu_kerne": psutil.cpu_count(logical=True),
        "ram_genutzt_prozent": mem.percent,
        "ram_frei_gb": round(mem.available / 1024**3, 1),
        "festplatte_frei_gb": round(disk.free / 1024**3, 1),
    }
    try:
        battery = psutil.sensors_battery()
        if battery:
            info["akku_prozent"] = battery.percent
            info["am_strom"] = battery.power_plugged
    except Exception:
        pass
    return info


def list_processes(payload: dict[str, Any]) -> list[dict[str, Any]]:
    import psutil

    limit = int(payload.get("limit", 10))
    procs = []
    for proc in psutil.process_iter(["name", "pid", "memory_percent"]):
        try:
            procs.append(proc.info)
        except Exception:
            continue
    procs.sort(key=lambda p: p.get("memory_percent") or 0, reverse=True)
    return [
        {"name": p["name"], "pid": p["pid"], "ram_prozent": round(p.get("memory_percent") or 0, 1)}
        for p in procs[:limit]
    ]


def lock_screen(_payload: dict[str, Any]) -> str:
    try:
        if IS_WINDOWS:
            subprocess.run(["rundll32.exe", "user32.dll,LockWorkStation"], check=True)
        elif IS_MAC:
            subprocess.run(
                ["osascript", "-e", 'tell application "System Events" to keystroke "q" using {control down, command down}'],
                check=True,
            )
        else:
            subprocess.run(["loginctl", "lock-session"], check=True)
    except Exception as exc:
        return f"Sperren fehlgeschlagen: {exc}"
    return "Bildschirm gesperrt."


def current_time(_payload: dict[str, Any]) -> str:
    now = dt.datetime.now().astimezone()
    return now.strftime("%A, %d. %B %Y, %H:%M:%S %Z")


def run_shell(payload: dict[str, Any]) -> str:
    if not config.allow_shell:
        return (
            "Shell-Befehle sind deaktiviert. Setze JARVIS_ALLOW_SHELL=true in der .env, "
            "wenn du das freischalten willst."
        )
    command = str(payload.get("command", "")).strip()
    if not command:
        return "Kein Befehl angegeben."
    proc = subprocess.run(
        command,
        shell=True,
        capture_output=True,
        text=True,
        timeout=int(payload.get("timeout", 60)),
        cwd=str(config.workspace),
    )
    out = (proc.stdout or "") + (("\n[stderr]\n" + proc.stderr) if proc.stderr else "")
    return (out.strip() or "(keine Ausgabe)")[:8000] + f"\n[exit={proc.returncode}]"


# --- Definitionen ----------------------------------------------------------


def get_tools() -> list[Tool]:
    return [
        Tool(
            name="open_app",
            description=(
                "Startet ein Programm auf dem PC. Kennt Kurznamen wie 'browser', 'spotify', "
                "'rechner', 'editor', 'vscode', 'discord'. Sonst wird der Name direkt ausgefuehrt."
            ),
            input_schema={
                "type": "object",
                "properties": {"name": {"type": "string", "description": "Programmname oder Kurzname"}},
                "required": ["name"],
            },
            handler=open_app,
        ),
        Tool(
            name="open_url",
            description="Oeffnet eine Webseite im Standardbrowser.",
            input_schema={
                "type": "object",
                "properties": {"url": {"type": "string"}},
                "required": ["url"],
            },
            handler=open_url,
        ),
        Tool(
            name="take_screenshot",
            description="Macht einen Screenshot und legt ihn im Arbeitsverzeichnis ab.",
            input_schema={
                "type": "object",
                "properties": {
                    "monitor": {"type": "integer", "description": "0 = alle Bildschirme, 1 = Hauptbildschirm"}
                },
            },
            handler=take_screenshot,
        ),
        Tool(
            name="set_volume",
            description=(
                "Liest oder aendert die Systemlautstaerke. Ohne Argumente wird der aktuelle Wert gemeldet."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "percent": {"type": "integer", "description": "Ziel-Lautstaerke 0-100"},
                    "mute": {"type": "boolean", "description": "true = stumm, false = Ton an"},
                },
            },
            handler=set_volume,
        ),
        Tool(
            name="system_info",
            description="Liefert Uhrzeit, CPU-Last, RAM, freien Speicher und Akkustand.",
            input_schema={"type": "object", "properties": {}},
            handler=system_info,
        ),
        Tool(
            name="list_processes",
            description="Listet die Programme mit dem hoechsten Speicherverbrauch.",
            input_schema={
                "type": "object",
                "properties": {"limit": {"type": "integer", "description": "Anzahl Eintraege, Standard 10"}},
            },
            handler=list_processes,
        ),
        Tool(
            name="lock_screen",
            description="Sperrt den Bildschirm.",
            input_schema={"type": "object", "properties": {}},
            handler=lock_screen,
        ),
        Tool(
            name="current_time",
            description="Aktuelles Datum und Uhrzeit des Rechners.",
            input_schema={"type": "object", "properties": {}},
            handler=current_time,
        ),
        Tool(
            name="run_shell",
            description=(
                "Fuehrt einen Shell-Befehl im Arbeitsverzeichnis aus. Standardmaessig gesperrt "
                "und nur aktiv, wenn JARVIS_ALLOW_SHELL=true gesetzt ist. Nur nutzen, wenn der "
                "Nutzer es ausdruecklich verlangt."
            ),
            input_schema={
                "type": "object",
                "properties": {
                    "command": {"type": "string"},
                    "timeout": {"type": "integer", "description": "Sekunden, Standard 60"},
                },
                "required": ["command"],
            },
            handler=run_shell,
        ),
    ]
