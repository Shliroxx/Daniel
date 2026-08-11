"""Bildanalyse — schickt ein Kamerabild an Claude und liest strukturiertes JSON zurueck.

Zwei Wege, genau wie beim Rest von Jarvis:
- "cli": ueber `claude -p` und damit ueber dein Claude-Abo. Das Bild wird kurz auf
  die Platte gelegt und von Claude Code gelesen. Keine Token-Kosten, dafuer langsamer.
- "api": ueber die Anthropic-API mit eigenem Key. Schnell, kostet pro Bild.
"""

from __future__ import annotations

import asyncio
import base64
import io
import json
import logging
import shutil
import time
from pathlib import Path
from typing import Any

from jarvis.config import config

log = logging.getLogger("shisha.analyse")

# Nach so vielen Sekunden ohne Antwort brechen wir ein Bild ab — im Livebetrieb
# ist eine spaete Antwort wertlos.
CLI_TIMEOUT = 90
API_TIMEOUT = 60


class AnalyseFehler(RuntimeError):
    """Analyse konnte nicht durchgefuehrt werden (Netz, Kontingent, Konfiguration)."""


# --------------------------------------------------------------------------
# Bildaufbereitung
# --------------------------------------------------------------------------


def verkleinern(bild: bytes, max_kante: int, qualitaet: int = 80) -> bytes:
    """Skaliert das Bild auf `max_kante` herunter. Ohne Pillow bleibt es wie es ist."""
    try:
        from PIL import Image
    except ImportError:
        return bild

    try:
        with Image.open(io.BytesIO(bild)) as img:
            img = img.convert("RGB")
            if max(img.size) <= max_kante:
                # Trotzdem neu kodieren — das Handy liefert gern grosse JPEGs.
                if len(bild) < 400_000:
                    return bild
            else:
                faktor = max_kante / max(img.size)
                neu = (max(1, round(img.width * faktor)), max(1, round(img.height * faktor)))
                img = img.resize(neu, Image.LANCZOS)

            puffer = io.BytesIO()
            img.save(puffer, format="JPEG", quality=qualitaet, optimize=True)
            return puffer.getvalue()
    except Exception as exc:  # kaputtes Bild soll die Sitzung nicht kippen
        log.warning("Bild konnte nicht verkleinert werden: %s", exc)
        return bild


# --------------------------------------------------------------------------
# JSON aus einer Modellantwort schaelen
# --------------------------------------------------------------------------


def json_aus_text(text: str) -> dict[str, Any]:
    """Holt das erste vollstaendige JSON-Objekt aus einer Antwort."""
    text = (text or "").strip()
    if not text:
        raise AnalyseFehler("leere Antwort")

    if text.startswith("```"):
        text = text.split("```")[1] if text.count("```") >= 2 else text.lstrip("`")
        if text.lstrip().startswith("json"):
            text = text.lstrip()[4:]

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Fallback: die Klammern von Hand zaehlen, damit Text drumherum nicht stoert.
    start = text.find("{")
    if start < 0:
        raise AnalyseFehler(f"kein JSON in der Antwort: {text[:160]}")

    tiefe = 0
    in_string = False
    escaped = False
    for index in range(start, len(text)):
        zeichen = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif zeichen == "\\":
                escaped = True
            elif zeichen == '"':
                in_string = False
            continue
        if zeichen == '"':
            in_string = True
        elif zeichen == "{":
            tiefe += 1
        elif zeichen == "}":
            tiefe -= 1
            if tiefe == 0:
                try:
                    return json.loads(text[start : index + 1])
                except json.JSONDecodeError as exc:
                    raise AnalyseFehler(f"JSON unlesbar: {exc}") from exc

    raise AnalyseFehler("JSON unvollstaendig")


# --------------------------------------------------------------------------
# Backends
# --------------------------------------------------------------------------


class Analysator:
    """Nimmt Bild plus Prompt und liefert das geparste Ergebnis."""

    def __init__(self, backend: str | None = None) -> None:
        self.backend = (backend or config.shisha_backend).lower()
        self.bilder_dir = config.data_dir / "shisha"
        self.bilder_dir.mkdir(parents=True, exist_ok=True)
        self._client = None
        self._lock = asyncio.Lock()

        if self.backend == "api" and not config.api_key:
            raise AnalyseFehler(
                "SHISHA_BACKEND=api, aber ANTHROPIC_API_KEY fehlt. "
                "Trag den Key in die .env ein oder setz SHISHA_BACKEND=cli."
            )
        if self.backend == "cli" and not (
            shutil.which(config.claude_bin) or Path(config.claude_bin).exists()
        ):
            raise AnalyseFehler(
                f"Claude Code nicht gefunden ('{config.claude_bin}'). "
                "Installiere es mit: npm install -g @anthropic-ai/claude-code"
            )

    @property
    def label(self) -> str:
        if self.backend == "api":
            return f"Anthropic API ({config.shisha_model})"
        return f"claude -p ({config.shisha_cli_model})"

    async def analysiere(self, bild: bytes, prompt: str) -> dict[str, Any]:
        """Ein Bild, ein Prompt, ein Ergebnis. Wirft AnalyseFehler bei Problemen."""
        klein = await asyncio.to_thread(
            verkleinern, bild, config.shisha_max_kante, config.shisha_qualitaet
        )
        begonnen = time.monotonic()

        # Nur eine Analyse gleichzeitig — sonst ueberholen sich die Hinweise
        # gegenseitig und das Overlay flackert.
        async with self._lock:
            if self.backend == "api":
                rohtext = await self._ueber_api(klein, prompt)
            else:
                rohtext = await self._ueber_cli(klein, prompt)

        ergebnis = json_aus_text(rohtext)
        ergebnis["_dauer"] = round(time.monotonic() - begonnen, 2)
        ergebnis["_bytes"] = len(klein)
        return ergebnis

    # -- API ---------------------------------------------------------------
    async def _ueber_api(self, bild: bytes, prompt: str) -> str:
        if self._client is None:
            from anthropic import AsyncAnthropic

            self._client = AsyncAnthropic(api_key=config.api_key)

        nachricht = {
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "source": {
                        "type": "base64",
                        "media_type": "image/jpeg",
                        "data": base64.b64encode(bild).decode("ascii"),
                    },
                },
                {"type": "text", "text": "Analysiere dieses Bild nach den Vorgaben."},
            ],
        }

        try:
            antwort = await asyncio.wait_for(
                self._client.messages.create(
                    model=config.shisha_model,
                    max_tokens=1400,
                    system=[{"type": "text", "text": prompt, "cache_control": {"type": "ephemeral"}}],
                    messages=[nachricht],
                ),
                timeout=API_TIMEOUT,
            )
        except asyncio.TimeoutError as exc:
            raise AnalyseFehler("Zeitueberschreitung bei der Bildanalyse") from exc
        except Exception as exc:
            raise AnalyseFehler(str(exc)) from exc

        return "".join(block.text for block in antwort.content if block.type == "text")

    # -- CLI ---------------------------------------------------------------
    async def _ueber_cli(self, bild: bytes, prompt: str) -> str:
        """Bild auf die Platte legen und Claude Code darauf schauen lassen."""
        pfad = self.bilder_dir / "aktuell.jpg"
        await asyncio.to_thread(pfad.write_bytes, bild)

        cmd = [
            config.claude_bin,
            "-p",
            f"Lies das Bild {pfad} und analysiere es nach den Vorgaben.",
            "--output-format", "json",
            "--model", config.shisha_cli_model,
            "--max-turns", "3",
            "--allowedTools", "Read",
            "--add-dir", str(self.bilder_dir),
            "--append-system-prompt", prompt,
        ]

        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                cwd=str(self.bilder_dir),
            )
        except FileNotFoundError as exc:
            raise AnalyseFehler(f"Claude Code nicht gefunden ('{config.claude_bin}')") from exc

        try:
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=CLI_TIMEOUT)
        except asyncio.TimeoutError as exc:
            proc.kill()
            await proc.wait()
            raise AnalyseFehler(f"Zeitueberschreitung nach {CLI_TIMEOUT}s") from exc

        text = stdout.decode("utf-8", "replace").strip()
        if not text:
            fehler = stderr.decode("utf-8", "replace").strip()[:300]
            raise AnalyseFehler(f"claude lieferte nichts zurueck: {fehler or 'unbekannt'}")

        # --output-format json liefert einen Umschlag mit dem Text in "result".
        try:
            umschlag = json.loads(text)
        except json.JSONDecodeError:
            return text

        if isinstance(umschlag, dict):
            if umschlag.get("is_error"):
                raise AnalyseFehler(str(umschlag.get("result") or umschlag.get("subtype")))
            return str(umschlag.get("result") or "")
        return text
