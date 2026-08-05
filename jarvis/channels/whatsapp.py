"""WhatsApp-Kanal ueber die offizielle Meta Cloud API.

Ablauf:
  1. Meta schickt eingehende Nachrichten an unseren Webhook (POST /whatsapp/webhook).
  2. Wir pruefen die Signatur und ob die Absendernummer freigeschaltet ist.
  3. Text geht direkt an Jarvis. Sprachnachrichten laden wir herunter und lassen sie
     lokal von Whisper transkribieren.
  4. Die Antwort geht per Graph-API zurueck, optional zusaetzlich als Sprachnachricht.

Damit Meta unseren Rechner erreicht, braucht der Webhook eine oeffentliche
HTTPS-Adresse — siehe README (Cloudflare-Tunnel).

Bewusst nicht gebaut: Bibliotheken wie whatsapp-web.js oder Baileys, die WhatsApp Web
fernsteuern. Die verstossen gegen WhatsApps Nutzungsbedingungen und koennen zur
Sperrung der Telefonnummer fuehren.
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import logging
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Any

import httpx

from ..config import config

log = logging.getLogger("jarvis.whatsapp")

GRAPH = "https://graph.facebook.com"
MAX_MESSAGE = 4000  # WhatsApp erlaubt 4096 Zeichen, wir lassen Puffer


class WhatsAppError(RuntimeError):
    pass


def _base_url() -> str:
    return f"{GRAPH}/{config.whatsapp_api_version}"


def is_configured() -> bool:
    return bool(
        config.whatsapp_enabled
        and config.whatsapp_token
        and config.whatsapp_phone_id
        and config.whatsapp_verify_token
    )


def missing_settings() -> list[str]:
    fehlend = []
    if not config.whatsapp_token:
        fehlend.append("WHATSAPP_TOKEN")
    if not config.whatsapp_phone_id:
        fehlend.append("WHATSAPP_PHONE_NUMBER_ID")
    if not config.whatsapp_verify_token:
        fehlend.append("WHATSAPP_VERIFY_TOKEN")
    if not config.whatsapp_allowed:
        fehlend.append("WHATSAPP_ALLOWED_NUMBERS")
    return fehlend


# --------------------------------------------------------------------------
# Eingehend
# --------------------------------------------------------------------------


def verify_signature(raw_body: bytes, header: str | None) -> bool:
    """Prueft die X-Hub-Signature-256 gegen das App-Secret.

    Ohne gesetztes App-Secret wird nicht geprueft — dann sollte der Webhook nur
    ueber einen nicht erratbaren Pfad erreichbar sein. Mit Secret ist die Pruefung
    Pflicht: eine fehlende oder falsche Signatur fuehrt zur Ablehnung.
    """
    if not config.whatsapp_app_secret:
        log.warning("WHATSAPP_APP_SECRET nicht gesetzt — Signatur wird nicht geprueft.")
        return True
    if not header or not header.startswith("sha256="):
        return False
    expected = hmac.new(
        config.whatsapp_app_secret.encode("utf-8"), raw_body, hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, header.removeprefix("sha256="))


def is_allowed(number: str) -> bool:
    """Nur freigeschaltete Nummern duerfen Jarvis bedienen."""
    clean = number.lstrip("+")
    return any(clean == allowed.lstrip("+") for allowed in config.whatsapp_allowed)


def extract_messages(payload: dict[str, Any]) -> list[dict[str, Any]]:
    """Zieht die eigentlichen Nachrichten aus dem verschachtelten Webhook-Payload."""
    messages: list[dict[str, Any]] = []
    for entry in payload.get("entry") or []:
        for change in entry.get("changes") or []:
            value = change.get("value") or {}
            # Zustellbestaetigungen ("statuses") ignorieren wir.
            for message in value.get("messages") or []:
                messages.append(message)
    return messages


async def download_media(media_id: str) -> tuple[bytes, str]:
    """Laedt eine Mediendatei (z. B. Sprachnachricht) herunter."""
    headers = {"Authorization": f"Bearer {config.whatsapp_token}"}
    async with httpx.AsyncClient(timeout=60.0) as client:
        meta = await client.get(f"{_base_url()}/{media_id}", headers=headers)
        meta.raise_for_status()
        info = meta.json()
        url = info.get("url")
        if not url:
            raise WhatsAppError(f"Keine Download-URL fuer Medium {media_id}.")

        media = await client.get(url, headers=headers)
        media.raise_for_status()
        mime = info.get("mime_type", "audio/ogg")
        return media.content, mime


async def transcribe_voice(media_id: str) -> str:
    """Laedt eine Sprachnachricht und transkribiert sie lokal mit Whisper."""
    from ..audio import stt

    data, mime = await download_media(media_id)
    suffix = ".ogg" if "ogg" in mime else ".m4a" if "mp4" in mime or "m4a" in mime else ".bin"
    return await asyncio.to_thread(stt.transcribe_bytes, data, f"sprachnachricht{suffix}")


# --------------------------------------------------------------------------
# Ausgehend
# --------------------------------------------------------------------------


def _chunks(text: str, size: int = MAX_MESSAGE) -> list[str]:
    """Teilt lange Antworten an Zeilengrenzen, damit nichts mitten im Wort reisst."""
    if len(text) <= size:
        return [text]
    parts: list[str] = []
    current = ""
    for line in text.splitlines(keepends=True):
        while len(line) > size:  # eine einzelne, sehr lange Zeile
            if current:
                parts.append(current)
                current = ""
            parts.append(line[:size])
            line = line[size:]
        if len(current) + len(line) > size:
            parts.append(current)
            current = line
        else:
            current += line
    if current:
        parts.append(current)
    return parts


async def send_text(to: str, text: str) -> None:
    if not is_configured():
        raise WhatsAppError("WhatsApp ist nicht vollstaendig konfiguriert.")
    text = (text or "").strip()
    if not text:
        return

    headers = {
        "Authorization": f"Bearer {config.whatsapp_token}",
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=30.0) as client:
        for part in _chunks(text):
            response = await client.post(
                f"{_base_url()}/{config.whatsapp_phone_id}/messages",
                headers=headers,
                json={
                    "messaging_product": "whatsapp",
                    "recipient_type": "individual",
                    "to": to,
                    "type": "text",
                    "text": {"preview_url": False, "body": part},
                },
            )
            if response.status_code >= 400:
                raise WhatsAppError(f"Senden fehlgeschlagen ({response.status_code}): {response.text[:300]}")


async def send_voice(to: str, wav_bytes: bytes) -> bool:
    """Schickt die Antwort zusaetzlich als Sprachnachricht. Braucht ffmpeg."""
    ogg = await asyncio.to_thread(_wav_to_ogg, wav_bytes)
    if ogg is None:
        return False

    headers = {"Authorization": f"Bearer {config.whatsapp_token}"}
    async with httpx.AsyncClient(timeout=60.0) as client:
        upload = await client.post(
            f"{_base_url()}/{config.whatsapp_phone_id}/media",
            headers=headers,
            files={"file": ("antwort.ogg", ogg, "audio/ogg")},
            data={"messaging_product": "whatsapp", "type": "audio/ogg"},
        )
        if upload.status_code >= 400:
            log.warning("Medien-Upload fehlgeschlagen: %s", upload.text[:300])
            return False

        media_id = upload.json().get("id")
        response = await client.post(
            f"{_base_url()}/{config.whatsapp_phone_id}/messages",
            headers={**headers, "Content-Type": "application/json"},
            json={
                "messaging_product": "whatsapp",
                "to": to,
                "type": "audio",
                "audio": {"id": media_id},
            },
        )
        if response.status_code >= 400:
            log.warning("Sprachnachricht senden fehlgeschlagen: %s", response.text[:300])
            return False
    return True


def _wav_to_ogg(wav_bytes: bytes) -> bytes | None:
    """WhatsApp will Sprachnachrichten als Opus in einem Ogg-Container."""
    if not shutil.which("ffmpeg"):
        log.info("ffmpeg fehlt — Sprachantwort per WhatsApp wird uebersprungen.")
        return None

    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp) / "in.wav"
        target = Path(tmp) / "out.ogg"
        source.write_bytes(wav_bytes)
        try:
            subprocess.run(
                [
                    "ffmpeg", "-nostdin", "-loglevel", "error", "-y",
                    "-i", str(source),
                    "-c:a", "libopus", "-b:a", "32k", "-ar", "48000", "-ac", "1",
                    str(target),
                ],
                check=True,
                timeout=120,
            )
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
            log.warning("Umwandlung nach Opus fehlgeschlagen: %s", exc)
            return None
        return target.read_bytes()
