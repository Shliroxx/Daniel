"""Eine Kombo abspielen - abbrechbar, mit sauberem Loslassen.

Warten laeuft ueber Event.wait: ein Stopp greift sofort, nicht erst nach dem
laufenden Schritt. Unter Windows wird der System-Timer auf 1 ms gestellt,
sonst rundet Windows jede Wartezeit auf ~15 ms auf.
"""

from __future__ import annotations

import logging
import sys
import threading
import time
from contextlib import contextmanager
from dataclasses import dataclass

from .ausgabe import Ausgabe
from .konfig import Kombo, Schritt

log = logging.getLogger("kombo.ablauf")


@dataclass
class Ergebnis:
    kombo: str
    durchgaenge: int
    abgebrochen: bool
    dauer_ms: float
    fehler: str | None = None
    geloest: tuple[str, ...] = ()


@contextmanager
def feiner_takt():
    """timeBeginPeriod(1) fuer die Dauer der Kombo (nur Windows)."""
    winmm = None
    if sys.platform == "win32":
        try:
            import ctypes
            winmm = ctypes.WinDLL("winmm")
            winmm.timeBeginPeriod(1)
        except OSError as e:
            log.debug("timeBeginPeriod nicht verfuegbar: %s", e)
            winmm = None
    try:
        yield
    finally:
        if winmm is not None:
            winmm.timeEndPeriod(1)


class Abspieler:
    def __init__(self, ausgabe: Ausgabe) -> None:
        self.ausgabe = ausgabe

    def _warte(self, ms: int, abbruch: threading.Event) -> bool:
        """True, wenn waehrend des Wartens abgebrochen wurde."""
        if ms <= 0:
            return abbruch.is_set()
        return abbruch.wait(ms / 1000.0)

    def _schritt(self, s: Schritt, k: Kombo, abbruch: threading.Event) -> bool:
        a = self.ausgabe
        if s.art == "taste":
            a.taste(s.ziel, True)
            r = self._warte(k.tastendauer_ms, abbruch)
            a.taste(s.ziel, False)
            return r
        if s.art == "halte":
            a.taste(s.ziel, True)
            r = self._warte(s.ms, abbruch)
            a.taste(s.ziel, False)
            return r
        if s.art == "runter":
            a.taste(s.ziel, True)
        elif s.art == "hoch":
            a.taste(s.ziel, False)
        elif s.art == "klick":
            a.maus(s.ziel, True)
            r = self._warte(k.tastendauer_ms, abbruch)
            a.maus(s.ziel, False)
            return r
        elif s.art == "halteklick":
            a.maus(s.ziel, True)
            r = self._warte(s.ms, abbruch)
            a.maus(s.ziel, False)
            return r
        elif s.art == "mrunter":
            a.maus(s.ziel, True)
        elif s.art == "mhoch":
            a.maus(s.ziel, False)
        elif s.art == "warte":
            return self._warte(s.ms, abbruch)
        else:
            raise ValueError(f"unbekannte Schrittart {s.art}")
        return abbruch.is_set()

    def abspielen(self, k: Kombo, abbruch: threading.Event,
                  weiter: threading.Event | None = None) -> Ergebnis:
        """Spielt k ab.

        abbruch  - sofort stoppen (Stopp-Taste, Beenden, Fenster verlassen)
        weiter   - fuer modus halten/umschalten: solange gesetzt, wird wiederholt;
                   wird es geloescht, endet die Kombo nach dem laufenden Durchgang.
        """
        start = time.perf_counter()
        durchgaenge = 0
        abgebrochen = False
        fehler = None
        geloest: list[str] = []
        try:
            with feiner_takt():
                while True:
                    for s in k.schritte:
                        if self._schritt(s, k, abbruch):
                            abgebrochen = True
                            break
                    if abgebrochen:
                        break
                    # am Durchgangsende offene runter-Schritte loesen
                    geloest.extend(self.ausgabe.alles_loslassen())
                    durchgaenge += 1
                    if k.modus == "einmal":
                        if durchgaenge >= k.wiederholen:
                            break
                    elif weiter is None or not weiter.is_set():
                        break
                    if self._warte(k.abstand_ms, abbruch):
                        abgebrochen = True
                        break
        except Exception as e:
            fehler = f"{type(e).__name__}: {e}"
            log.error("Kombo '%s' gescheitert: %s", k.name, fehler)
        finally:
            geloest.extend(self.ausgabe.alles_loslassen())
        return Ergebnis(k.name, durchgaenge, abgebrochen,
                        (time.perf_counter() - start) * 1000, fehler, tuple(geloest))
