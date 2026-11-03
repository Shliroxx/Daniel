"""Hotkeys auswerten und Kombos starten/stoppen.

Unabhaengig von pynput: der Listener in __main__ meldet nur
`runter(name)` / `hoch(name)` mit kanonischen Namen. So laesst sich die ganze
Logik ohne echte Tastatur testen.

Regeln:
- Waehrend eine Kombo laeuft, werden andere Hotkeys ignoriert (die Kombo
  selbst drueckt Tasten - die duerfen nichts ausloesen).
- Stopp, Pause und Beenden wirken immer.
- modus "halten":     wiederholt, solange der Hotkey gedrueckt ist.
- modus "umschalten": erster Druck startet die Schleife, zweiter beendet sie.
- Verlaesst man das Spielfenster, bricht eine laufende Kombo ab.
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Callable

from . import fenster
from .ablauf import Abspieler, Ergebnis
from .konfig import Kombo, Konfig
from .tasten import MODIFIKATOREN, Hotkey

log = logging.getLogger("kombo.steuerung")


class Steuerung:
    MOD_WARTEZEIT = 0.6   # Sekunden, die auf das Loslassen von Alt/Strg/Shift gewartet wird

    def __init__(self, konfig: Konfig, abspieler: Abspieler,
                 titel_quelle: Callable[[], str | None] = fenster.aktiver_titel,
                 bei_ergebnis: Callable[[Ergebnis], None] | None = None) -> None:
        self.konfig = konfig
        self.abspieler = abspieler
        self.titel_quelle = titel_quelle
        self.bei_ergebnis = bei_ergebnis
        self.pausiert = False
        self.beendet = threading.Event()
        self._mods: set[str] = set()
        self._sperre = threading.Lock()
        self._laeuft: Kombo | None = None
        self._faden: threading.Thread | None = None
        self._abbruch = threading.Event()
        self._weiter = threading.Event()
        self.ergebnisse: list[Ergebnis] = []

    # --- Zustand ---
    @property
    def aktiv(self) -> Kombo | None:
        return self._laeuft

    def _trifft(self, hk: Hotkey, name: str) -> bool:
        return hk.haupt == name and hk.mods <= self._mods

    def _finde(self, name: str) -> Kombo | None:
        # die Kombo mit den meisten passenden Modifikatoren gewinnt (alt+1 vor 1)
        passend = [k for k in self.konfig.kombos if self._trifft(k.hotkey, name)]
        return max(passend, key=lambda k: len(k.hotkey.mods), default=None)

    # --- Ereignisse vom Listener ---
    def runter(self, name: str) -> None:
        if name in MODIFIKATOREN:
            self._mods.add(name)
            return
        kf = self.konfig
        if self._trifft(kf.beenden_taste, name):
            log.info("Beenden-Taste - schliesse.")
            self.beenden()
            return
        if self._trifft(kf.stopp_taste, name):
            if self.stoppen():
                log.info("Stopp - Kombo abgebrochen.")
            return
        if self._trifft(kf.pause_taste, name):
            self.pausiert = not self.pausiert
            if self.pausiert:
                self.stoppen()
            log.info("Makro %s.", "PAUSIERT" if self.pausiert else "AKTIV")
            return

        with self._sperre:
            laufend = self._laeuft
        if laufend is not None:
            if laufend.modus == "umschalten" and self._trifft(laufend.hotkey, name):
                self._weiter.clear()
                log.info("'%s' endet nach diesem Durchgang.", laufend.name)
            return
        if self.pausiert:
            return
        k = self._finde(name)
        if k is None:
            return
        titel = self.titel_quelle()
        if not fenster.passt(kf.fenster_titel, titel):
            log.debug("'%s' ignoriert - aktives Fenster '%s' ist nicht das Spiel.", k.name, titel)
            return
        self.starten(k)

    def hoch(self, name: str) -> None:
        if name in MODIFIKATOREN:
            self._mods.discard(name)
            return
        with self._sperre:
            laufend = self._laeuft
        if laufend is not None and laufend.modus == "halten" and laufend.hotkey.haupt == name:
            self._weiter.clear()

    # --- Ablauf ---
    def starten(self, k: Kombo) -> bool:
        with self._sperre:
            if self._laeuft is not None:
                return False
            self._laeuft = k
            self._abbruch.clear()
            self._weiter.set()
            self._faden = threading.Thread(target=self._lauf, args=(k,),
                                           name=f"kombo-{k.name}", daemon=True)
            self._faden.start()
        log.info("> %s", k.name)
        return True

    def _mods_frei(self, k: Kombo) -> bool:
        """Wartet, bis die Modifikatoren des Hotkeys losgelassen sind.

        Sonst saehe das Spiel bei Hotkey alt+1 und Schritt "taste 2" ein Alt+2.
        """
        ende = time.monotonic() + self.MOD_WARTEZEIT
        while k.hotkey.mods & self._mods:
            if time.monotonic() >= ende or self._abbruch.wait(0.01):
                return False
        return True

    def _lauf(self, k: Kombo) -> None:
        try:
            if not self._mods_frei(k):
                gehalten = "+".join(sorted(k.hotkey.mods & self._mods))
                log.warning("'%s' nicht gestartet - %s noch gedrueckt. Hotkey loslassen, "
                            "dann laeuft die Kombo.", k.name, gehalten)
                return
            erg = self.abspieler.abspielen(k, self._abbruch, self._weiter)
            self.ergebnisse.append(erg)
            if erg.fehler:
                log.error("'%s' Fehler: %s", k.name, erg.fehler)
            else:
                log.info("  %s fertig: %dx, %.0f ms%s", k.name,
                         erg.durchgaenge, erg.dauer_ms, " (abgebrochen)" if erg.abgebrochen else "")
            if erg.geloest:
                log.debug("  losgelassen: %s", ", ".join(erg.geloest))
            if self.bei_ergebnis:
                self.bei_ergebnis(erg)
        finally:
            with self._sperre:
                self._laeuft = None

    def stoppen(self, warten: bool = False) -> bool:
        """Bricht eine laufende Kombo ab. True, wenn eine lief.

        Aus dem Listener heraus nie warten: der Kombo-Faden schickt beim Loslassen
        noch Eingaben, und die laufen unter Windows durch genau diesen Hook.
        """
        with self._sperre:
            faden = self._faden if self._laeuft is not None else None
            self._abbruch.set()
            self._weiter.clear()
        if faden is not None:
            if warten:
                faden.join(timeout=2.0)
            return True
        return False

    def warten(self, timeout: float | None = None) -> None:
        """Wartet, bis die laufende Kombo fertig ist (fuer Tests und --test)."""
        faden = self._faden
        if faden is not None:
            faden.join(timeout)

    def beenden(self) -> None:
        self.stoppen()
        self.beendet.set()

    def fenster_wache(self, intervall: float = 0.25) -> None:
        """Bricht ab, sobald das Spielfenster den Fokus verliert (eigener Faden)."""
        while not self.beendet.wait(intervall):
            if self._laeuft is None or not self.konfig.fenster_titel:
                continue
            titel = self.titel_quelle()
            if not fenster.passt(self.konfig.fenster_titel, titel):
                log.info("Spielfenster verlassen ('%s') - Kombo abgebrochen.", titel)
                self.stoppen()
