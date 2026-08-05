"""Text-to-Speech mit Piper — laeuft lokal, deutsche Stimme, keine Cloud."""

from __future__ import annotations

import io
import logging
import threading
import wave
from pathlib import Path

import numpy as np

from ..config import config

log = logging.getLogger("jarvis.tts")

_HF_BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/main"

_voice = None
_voice_lock = threading.Lock()
_unavailable = False


def _voice_url_path(name: str) -> str:
    """'de_DE-thorsten-high' -> 'de/de_DE/thorsten/high/de_DE-thorsten-high'."""
    locale, speaker, quality = name.split("-", 2)
    lang = locale.split("_")[0]
    return f"{lang}/{locale}/{speaker}/{quality}/{name}"


def _ensure_voice_files() -> tuple[Path, Path]:
    """Laedt Modell + Config beim ersten Start herunter (danach lokal gecached)."""
    import httpx

    name = config.piper_voice
    model = config.voices_dir / f"{name}.onnx"
    cfg = config.voices_dir / f"{name}.onnx.json"
    if model.exists() and cfg.exists():
        return model, cfg

    rel = _voice_url_path(name)
    for target, url in ((model, f"{_HF_BASE}/{rel}.onnx"), (cfg, f"{_HF_BASE}/{rel}.onnx.json")):
        if target.exists():
            continue
        log.info("Lade Stimme herunter: %s", url)
        tmp = target.with_suffix(target.suffix + ".part")
        with httpx.stream("GET", url, follow_redirects=True, timeout=120.0) as resp:
            resp.raise_for_status()
            with tmp.open("wb") as fh:
                for chunk in resp.iter_bytes(chunk_size=1 << 16):
                    fh.write(chunk)
        tmp.rename(target)
    log.info("Stimme '%s' bereit.", name)
    return model, cfg


def get_voice():
    """Laedt die Piper-Stimme einmalig. Gibt None zurueck, wenn Piper fehlt."""
    global _voice, _unavailable
    if _unavailable:
        return None
    if _voice is None:
        with _voice_lock:
            if _voice is None and not _unavailable:
                try:
                    from piper import PiperVoice

                    model, cfg = _ensure_voice_files()
                    _voice = PiperVoice.load(str(model), config_path=str(cfg))
                except Exception as exc:
                    log.warning("Piper nicht verfuegbar (%s) — Jarvis bleibt stumm.", exc)
                    _unavailable = True
                    return None
    return _voice


def synthesize(text: str) -> tuple[bytes, int] | None:
    """Erzeugt WAV-Bytes aus Text. Gibt (wav_bytes, sample_rate) oder None zurueck."""
    text = (text or "").strip()
    if not text:
        return None
    voice = get_voice()
    if voice is None:
        return None

    try:
        pcm, rate = _synthesize_pcm(voice, text)
    except Exception as exc:
        log.warning("TTS fehlgeschlagen: %s", exc)
        return None
    if pcm.size == 0:
        return None

    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(rate)
        wf.writeframes(pcm.tobytes())
    return buf.getvalue(), rate


def _synthesize_pcm(voice, text: str) -> tuple[np.ndarray, int]:
    """Deckt die verschiedenen Piper-API-Generationen ab."""
    rate = int(getattr(getattr(voice, "config", None), "sample_rate", 22_050))

    # Neuere API: synthesize() liefert AudioChunk-Objekte
    if hasattr(voice, "synthesize"):
        chunks: list[np.ndarray] = []
        for chunk in voice.synthesize(text):
            if hasattr(chunk, "audio_int16_bytes"):
                chunks.append(np.frombuffer(chunk.audio_int16_bytes, dtype=np.int16))
                rate = int(getattr(chunk, "sample_rate", rate))
            elif isinstance(chunk, (bytes, bytearray)):
                chunks.append(np.frombuffer(chunk, dtype=np.int16))
        if chunks:
            return np.concatenate(chunks), rate

    # Aeltere API: synthesize_stream_raw() liefert rohe int16-Bytes
    if hasattr(voice, "synthesize_stream_raw"):
        raw = b"".join(voice.synthesize_stream_raw(text))
        return np.frombuffer(raw, dtype=np.int16), rate

    raise RuntimeError("Unbekannte Piper-API — bitte 'piper-tts' aktualisieren.")


def play_wav(wav_bytes: bytes) -> None:
    """Spielt WAV ueber die Lautsprecher des Rechners ab, auf dem Jarvis laeuft."""
    try:
        import sounddevice as sd
        import soundfile as sf

        data, rate = sf.read(io.BytesIO(wav_bytes), dtype="float32", always_2d=False)
        sd.play(data, rate)
        sd.wait()
    except Exception as exc:
        log.warning("Konnte Audio nicht abspielen: %s", exc)


def stop_playback() -> None:
    try:
        import sounddevice as sd

        sd.stop()
    except Exception:
        pass
