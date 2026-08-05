"""Zentrale Konfiguration — liest .env und stellt Defaults bereit."""

from __future__ import annotations

import os
import shutil
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


def _path(name: str, default: str | Path) -> Path:
    return Path(os.getenv(name, str(default))).expanduser().resolve()


def _list(name: str) -> list[str]:
    raw = os.getenv(name, "")
    return [item.strip() for item in raw.split(",") if item.strip()]


@dataclass
class Config:
    # --- Denkapparat ------------------------------------------------------
    # "cli"  = ueber claude -p und damit ueber dein Claude-Abo (keine Token-Kosten)
    # "api"  = ueber die Anthropic-API mit eigenem Key (kostet pro Token)
    backend: str = field(default_factory=lambda: os.getenv("JARVIS_BACKEND", "cli").lower())

    # Backend "cli"
    claude_bin: str = field(
        default_factory=lambda: os.getenv("JARVIS_CLAUDE_BIN") or shutil.which("claude") or "claude"
    )
    cli_model: str = field(default_factory=lambda: os.getenv("JARVIS_CLI_MODEL", "sonnet"))
    cli_timeout: int = field(default_factory=lambda: _int("JARVIS_CLI_TIMEOUT", 300))
    cli_max_turns: int = field(default_factory=lambda: _int("JARVIS_CLI_MAX_TURNS", 24))

    # Backend "api"
    api_key: str = field(default_factory=lambda: os.getenv("ANTHROPIC_API_KEY", ""))
    model: str = field(default_factory=lambda: os.getenv("JARVIS_MODEL", "claude-opus-5"))
    effort: str = field(default_factory=lambda: os.getenv("JARVIS_EFFORT", "medium"))
    max_tokens: int = field(default_factory=lambda: _int("JARVIS_MAX_TOKENS", 8000))

    # --- Server -----------------------------------------------------------
    host: str = field(default_factory=lambda: os.getenv("JARVIS_HOST", "0.0.0.0"))
    port: int = field(default_factory=lambda: _int("JARVIS_PORT", 8765))

    # --- Sprache ----------------------------------------------------------
    language: str = field(default_factory=lambda: os.getenv("JARVIS_LANGUAGE", "de"))
    whisper_model: str = field(default_factory=lambda: os.getenv("JARVIS_WHISPER_MODEL", "small"))
    whisper_device: str = field(default_factory=lambda: os.getenv("JARVIS_WHISPER_DEVICE", "auto"))
    wakeword: str = field(default_factory=lambda: os.getenv("JARVIS_WAKEWORD", "hey_jarvis"))
    wakeword_threshold: float = field(default_factory=lambda: _float("JARVIS_WAKEWORD_THRESHOLD", 0.5))
    mic_enabled: bool = field(default_factory=lambda: _bool("JARVIS_MIC_ENABLED", True))
    speak_locally: bool = field(default_factory=lambda: _bool("JARVIS_SPEAK_LOCALLY", True))
    piper_voice: str = field(default_factory=lambda: os.getenv("JARVIS_PIPER_VOICE", "de_DE-thorsten-high"))

    # --- Obsidian ---------------------------------------------------------
    vault: Path = field(default_factory=lambda: _path("JARVIS_VAULT", Path.home() / "Obsidian"))
    vault_enabled: bool = field(default_factory=lambda: _bool("JARVIS_VAULT_ENABLED", True))
    memory_dir_name: str = field(default_factory=lambda: os.getenv("JARVIS_MEMORY_DIR", "jarvis"))

    # --- WhatsApp (Meta Cloud API) ---------------------------------------
    whatsapp_enabled: bool = field(default_factory=lambda: _bool("JARVIS_WHATSAPP_ENABLED", False))
    whatsapp_token: str = field(default_factory=lambda: os.getenv("WHATSAPP_TOKEN", ""))
    whatsapp_phone_id: str = field(default_factory=lambda: os.getenv("WHATSAPP_PHONE_NUMBER_ID", ""))
    whatsapp_verify_token: str = field(default_factory=lambda: os.getenv("WHATSAPP_VERIFY_TOKEN", ""))
    whatsapp_app_secret: str = field(default_factory=lambda: os.getenv("WHATSAPP_APP_SECRET", ""))
    whatsapp_allowed: list[str] = field(default_factory=lambda: _list("WHATSAPP_ALLOWED_NUMBERS"))
    whatsapp_voice_reply: bool = field(default_factory=lambda: _bool("WHATSAPP_VOICE_REPLY", False))
    whatsapp_api_version: str = field(default_factory=lambda: os.getenv("WHATSAPP_API_VERSION", "v21.0"))

    # --- Dateien / Rechte -------------------------------------------------
    workspace: Path = field(default_factory=lambda: _path("JARVIS_WORKSPACE", ROOT / "workspace"))
    allow_shell: bool = field(default_factory=lambda: _bool("JARVIS_ALLOW_SHELL", False))

    data_dir: Path = field(default_factory=lambda: (ROOT / "data").resolve())
    voices_dir: Path = field(default_factory=lambda: (ROOT / "voices").resolve())
    log_dir: Path = field(default_factory=lambda: (ROOT / "logs").resolve())

    def __post_init__(self) -> None:
        for directory in (self.workspace, self.data_dir, self.voices_dir, self.log_dir):
            directory.mkdir(parents=True, exist_ok=True)

    # -- Abgeleitete Pfade -------------------------------------------------
    @property
    def memory_root(self) -> Path:
        """Ordner im Vault, in dem Jarvis seine Notizen ablegt."""
        return self.vault / self.memory_dir_name

    @property
    def inbox(self) -> Path:
        """Hier legst du Nacht-Auftraege ab."""
        return self.memory_root / "auftraege"

    @property
    def outbox(self) -> Path:
        """Hier landen die Ergebnisse der Nacht-Auftraege."""
        return self.memory_root / "ergebnisse"

    @property
    def uses_vault(self) -> bool:
        """Vault nur nutzen, wenn er aktiviert ist und wirklich existiert."""
        return self.vault_enabled and self.vault.is_dir()

    def write_dirs(self) -> list[Path]:
        """Verzeichnisse, in die Jarvis schreiben darf."""
        dirs = [self.workspace]
        if self.uses_vault:
            dirs.append(self.memory_root)
        return dirs


config = Config()
