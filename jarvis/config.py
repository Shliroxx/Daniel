"""Zentrale Konfiguration — liest .env und stellt Defaults bereit."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / ".env")


def _bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on", "ja"}


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


def _float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


@dataclass
class Config:
    # Modell
    api_key: str = field(default_factory=lambda: os.getenv("ANTHROPIC_API_KEY", ""))
    model: str = field(default_factory=lambda: os.getenv("JARVIS_MODEL", "claude-opus-5"))
    effort: str = field(default_factory=lambda: os.getenv("JARVIS_EFFORT", "medium"))
    max_tokens: int = field(default_factory=lambda: _int("JARVIS_MAX_TOKENS", 8000))

    # Server
    host: str = field(default_factory=lambda: os.getenv("JARVIS_HOST", "0.0.0.0"))
    port: int = field(default_factory=lambda: _int("JARVIS_PORT", 8765))

    # Sprache
    language: str = field(default_factory=lambda: os.getenv("JARVIS_LANGUAGE", "de"))
    whisper_model: str = field(default_factory=lambda: os.getenv("JARVIS_WHISPER_MODEL", "small"))
    whisper_device: str = field(default_factory=lambda: os.getenv("JARVIS_WHISPER_DEVICE", "auto"))
    wakeword: str = field(default_factory=lambda: os.getenv("JARVIS_WAKEWORD", "hey_jarvis"))
    wakeword_threshold: float = field(default_factory=lambda: _float("JARVIS_WAKEWORD_THRESHOLD", 0.5))
    mic_enabled: bool = field(default_factory=lambda: _bool("JARVIS_MIC_ENABLED", True))
    speak_locally: bool = field(default_factory=lambda: _bool("JARVIS_SPEAK_LOCALLY", True))
    piper_voice: str = field(default_factory=lambda: os.getenv("JARVIS_PIPER_VOICE", "de_DE-thorsten-high"))

    # Dateien / Rechte
    workspace: Path = field(
        default_factory=lambda: Path(os.getenv("JARVIS_WORKSPACE", ROOT / "workspace")).expanduser().resolve()
    )
    allow_shell: bool = field(default_factory=lambda: _bool("JARVIS_ALLOW_SHELL", False))

    # Ablage fuer Notizen, Aufgaben, Langzeitgedaechtnis
    data_dir: Path = field(default_factory=lambda: (ROOT / "data").resolve())
    voices_dir: Path = field(default_factory=lambda: (ROOT / "voices").resolve())

    def __post_init__(self) -> None:
        self.workspace.mkdir(parents=True, exist_ok=True)
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.voices_dir.mkdir(parents=True, exist_ok=True)


config = Config()
