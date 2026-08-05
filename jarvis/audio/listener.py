"""Dauerlauf am PC-Mikrofon: wartet auf "Hey Jarvis", nimmt auf, transkribiert."""

from __future__ import annotations

import logging
import queue
import threading
import time
from collections.abc import Callable

import numpy as np

from ..config import config
from . import stt

log = logging.getLogger("jarvis.listener")

SAMPLE_RATE = 16_000
FRAME = 1280           # openWakeWord erwartet 80 ms Bloecke
SILENCE_RMS = 0.012    # Schwelle, ab der es als "still" gilt
SILENCE_HOLD = 1.2     # Sekunden Stille, die den Satz beenden
MAX_UTTERANCE = 20.0   # Notbremse gegen endloses Aufnehmen
PREROLL = 0.35         # Sekunden vor dem Wake-Word mitschneiden


class MicListener:
    """Hoert dauerhaft zu und meldet erkannte Sprache per Callback."""

    def __init__(
        self,
        on_transcript: Callable[[str], None],
        on_event: Callable[[str, dict], None] | None = None,
    ) -> None:
        self.on_transcript = on_transcript
        self.on_event = on_event or (lambda kind, data: None)
        self._audio: queue.Queue[np.ndarray] = queue.Queue()
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()
        self._muted = threading.Event()
        self._oww = None

    # -- Steuerung ---------------------------------------------------------
    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="jarvis-mic", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=3)

    def mute(self) -> None:
        """Waehrend Jarvis spricht — damit er sich nicht selbst zuhoert."""
        self._muted.set()

    def unmute(self) -> None:
        self._muted.clear()

    # -- Interna -----------------------------------------------------------
    def _load_wakeword(self):
        import openwakeword
        from openwakeword.model import Model

        try:
            openwakeword.utils.download_models()
        except Exception as exc:  # Modelle evtl. schon vorhanden / offline
            log.debug("download_models(): %s", exc)
        return Model(wakeword_models=[config.wakeword], inference_framework="onnx")

    def _callback(self, indata, _frames, _time_info, status) -> None:
        if status:
            log.debug("Mic-Status: %s", status)
        self._audio.put(indata[:, 0].copy())

    def _run(self) -> None:
        try:
            import sounddevice as sd
        except Exception as exc:
            log.error("sounddevice nicht verfuegbar (%s) — Mikrofon deaktiviert.", exc)
            return

        try:
            self._oww = self._load_wakeword()
        except Exception as exc:
            log.error("Wake-Word-Modell konnte nicht geladen werden: %s", exc)
            return

        log.info("Mikrofon aktiv — sag '%s'.", config.wakeword.replace("_", " "))
        self.on_event("listening", {"wakeword": config.wakeword})

        preroll_len = int(PREROLL * SAMPLE_RATE)
        preroll = np.zeros(0, dtype=np.float32)

        try:
            with sd.InputStream(
                samplerate=SAMPLE_RATE,
                channels=1,
                dtype="float32",
                blocksize=FRAME,
                callback=self._callback,
            ):
                while not self._stop.is_set():
                    try:
                        block = self._audio.get(timeout=0.5)
                    except queue.Empty:
                        continue

                    if self._muted.is_set():
                        preroll = np.zeros(0, dtype=np.float32)
                        continue

                    self.on_event("level", {"rms": float(np.sqrt(np.mean(block**2)))})

                    preroll = np.concatenate([preroll, block])[-preroll_len:]

                    scores = self._oww.predict((block * 32767).astype(np.int16))
                    score = max(scores.values()) if scores else 0.0
                    if score < config.wakeword_threshold:
                        continue

                    self._oww.reset()
                    log.info("Wake-Word erkannt (%.2f).", score)
                    self.on_event("wake", {"score": score})

                    utterance = self._record_utterance(preroll)
                    preroll = np.zeros(0, dtype=np.float32)

                    if utterance.size < SAMPLE_RATE // 2:
                        self.on_event("listening", {})
                        continue

                    self.on_event("thinking", {"stage": "transcribe"})
                    text = stt.transcribe_pcm(utterance)
                    if text:
                        self.on_transcript(text)
                    else:
                        self.on_event("listening", {})
        except Exception as exc:
            log.error("Mikrofon-Schleife beendet: %s", exc)
        finally:
            self.on_event("offline", {})

    def _record_utterance(self, preroll: np.ndarray) -> np.ndarray:
        """Nimmt auf, bis es SILENCE_HOLD Sekunden still ist."""
        chunks: list[np.ndarray] = [preroll] if preroll.size else []
        started = time.monotonic()
        last_voice = started
        heard_voice = False

        while not self._stop.is_set():
            try:
                block = self._audio.get(timeout=0.5)
            except queue.Empty:
                continue

            chunks.append(block)
            rms = float(np.sqrt(np.mean(block**2)))
            self.on_event("level", {"rms": rms})

            now = time.monotonic()
            if rms > SILENCE_RMS:
                last_voice = now
                heard_voice = True
            elif heard_voice and now - last_voice > SILENCE_HOLD:
                break
            elif not heard_voice and now - started > 4.0:
                break  # nach dem Wake-Word kam nichts

            if now - started > MAX_UTTERANCE:
                break

        return np.concatenate(chunks) if chunks else np.zeros(0, dtype=np.float32)
