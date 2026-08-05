"""Speech-to-Text mit faster-whisper — laeuft komplett lokal, keine Cloud."""

from __future__ import annotations

import io
import logging
import subprocess
import tempfile
import threading
from pathlib import Path

import numpy as np

from ..config import config

log = logging.getLogger("jarvis.stt")

SAMPLE_RATE = 16_000

_model = None
_model_lock = threading.Lock()


def _resolve_device() -> tuple[str, str]:
    """Gibt (device, compute_type) zurueck. CUDA nur wenn wirklich vorhanden."""
    wanted = config.whisper_device.lower()
    if wanted == "cpu":
        return "cpu", "int8"
    if wanted == "cuda":
        return "cuda", "float16"
    try:
        import ctranslate2

        if ctranslate2.get_cuda_device_count() > 0:
            return "cuda", "float16"
    except Exception:  # pragma: no cover - haengt von der Installation ab
        pass
    return "cpu", "int8"


def get_model():
    """Laedt das Whisper-Modell einmalig (thread-safe, beim ersten Zugriff)."""
    global _model
    if _model is None:
        with _model_lock:
            if _model is None:
                from faster_whisper import WhisperModel

                device, compute_type = _resolve_device()
                log.info(
                    "Lade Whisper-Modell '%s' auf %s (%s) ...",
                    config.whisper_model,
                    device,
                    compute_type,
                )
                _model = WhisperModel(
                    config.whisper_model, device=device, compute_type=compute_type
                )
                log.info("Whisper bereit.")
    return _model


def transcribe_pcm(audio: np.ndarray, sample_rate: int = SAMPLE_RATE) -> str:
    """Transkribiert float32-PCM im Bereich [-1, 1]."""
    if audio.size == 0:
        return ""
    if audio.dtype != np.float32:
        audio = audio.astype(np.float32)
    if sample_rate != SAMPLE_RATE:
        audio = _resample(audio, sample_rate, SAMPLE_RATE)

    segments, _info = get_model().transcribe(
        audio,
        language=config.language,
        beam_size=5,
        vad_filter=True,
        vad_parameters={"min_silence_duration_ms": 400},
        condition_on_previous_text=False,
    )
    return " ".join(seg.text.strip() for seg in segments).strip()


def transcribe_bytes(data: bytes, filename: str = "audio.webm") -> str:
    """Transkribiert eine hochgeladene Audiodatei (z. B. WebM/Opus vom Browser)."""
    suffix = Path(filename).suffix or ".webm"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(data)
        tmp_path = Path(tmp.name)

    try:
        pcm = _decode_to_pcm(tmp_path)
        if pcm is not None:
            return transcribe_pcm(pcm)
        # Fallback: faster-whisper kann viele Formate direkt ueber ffmpeg lesen
        segments, _info = get_model().transcribe(
            str(tmp_path), language=config.language, beam_size=5, vad_filter=True
        )
        return " ".join(seg.text.strip() for seg in segments).strip()
    finally:
        tmp_path.unlink(missing_ok=True)


def _decode_to_pcm(path: Path) -> np.ndarray | None:
    """Dekodiert eine Datei nach 16 kHz Mono float32. Erst soundfile, dann ffmpeg."""
    try:
        import soundfile as sf

        data, sr = sf.read(str(path), dtype="float32", always_2d=True)
        mono = data.mean(axis=1)
        return _resample(mono, sr, SAMPLE_RATE) if sr != SAMPLE_RATE else mono
    except Exception:
        pass

    try:
        proc = subprocess.run(
            [
                "ffmpeg", "-nostdin", "-loglevel", "error",
                "-i", str(path),
                "-f", "f32le", "-ac", "1", "-ar", str(SAMPLE_RATE), "-",
            ],
            capture_output=True,
            check=True,
            timeout=120,
        )
        return np.frombuffer(proc.stdout, dtype=np.float32).copy()
    except Exception as exc:
        log.warning("Konnte Audio nicht dekodieren (%s) — nutze Whisper-Fallback.", exc)
        return None


def _resample(audio: np.ndarray, src: int, dst: int) -> np.ndarray:
    if src == dst:
        return audio
    duration = audio.shape[0] / src
    target_len = int(round(duration * dst))
    if target_len <= 0:
        return np.zeros(0, dtype=np.float32)
    src_idx = np.linspace(0, audio.shape[0] - 1, num=target_len, dtype=np.float64)
    return np.interp(src_idx, np.arange(audio.shape[0]), audio).astype(np.float32)


def pcm_to_wav_bytes(audio: np.ndarray, sample_rate: int = SAMPLE_RATE) -> bytes:
    """Hilfsfunktion: float32-PCM -> WAV-Bytes (fuer Debug/Playback)."""
    import soundfile as sf

    buf = io.BytesIO()
    sf.write(buf, audio, sample_rate, format="WAV", subtype="PCM_16")
    return buf.getvalue()
