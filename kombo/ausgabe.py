"""Eingaben ans Spiel schicken.

WindowsAusgabe  - SendInput mit Scancodes. Unreal-Spiele wie Nanos World lesen
                  Raw Input; reine VK-Ereignisse werden dort oft verschluckt.
PynputAusgabe   - Ersatz fuer Linux/macOS (Testen ausserhalb von Windows).
TrockenAusgabe  - schickt nichts, protokolliert nur. Fuer --trocken und Tests.

Jede Ausgabe merkt sich, was gerade gedrueckt ist, damit ein Abbruch nie eine
haengende Taste hinterlaesst (alles_loslassen).
"""

from __future__ import annotations

import logging
import sys
import threading
import time

from . import tasten

log = logging.getLogger("kombo.ausgabe")


class AusgabeFehler(RuntimeError):
    """Eingabe konnte nicht verschickt werden."""


class Ausgabe:
    name = "basis"

    def __init__(self) -> None:
        self._gedrueckt: set[tuple[str, str]] = set()
        self._sperre = threading.Lock()

    # --- von Unterklassen umzusetzen ---
    def _taste(self, name: str, runter: bool) -> None:
        raise NotImplementedError

    def _maus(self, knopf: str, runter: bool) -> None:
        raise NotImplementedError

    # --- oeffentlich ---
    def taste(self, name: str, runter: bool) -> None:
        with self._sperre:
            self._taste(name, runter)
            (self._gedrueckt.add if runter else self._gedrueckt.discard)(("t", name))

    def maus(self, knopf: str, runter: bool) -> None:
        with self._sperre:
            self._maus(knopf, runter)
            (self._gedrueckt.add if runter else self._gedrueckt.discard)(("m", knopf))

    def gedrueckt(self) -> set[tuple[str, str]]:
        with self._sperre:
            return set(self._gedrueckt)

    def alles_loslassen(self) -> list[str]:
        """Laesst alles los, was diese Ausgabe gedrueckt hat. Gibt die Namen zurueck."""
        geloest = []
        for art, name in sorted(self.gedrueckt()):
            try:
                (self.taste if art == "t" else self.maus)(name, False)
                geloest.append(name)
            except Exception as e:  # weiter loslassen, auch wenn eine Taste scheitert
                log.error("Konnte '%s' nicht loslassen: %s", name, e)
        return geloest


class TrockenAusgabe(Ausgabe):
    name = "trocken"

    def __init__(self) -> None:
        super().__init__()
        self.protokoll: list[tuple[float, str, str, bool]] = []

    def _taste(self, name: str, runter: bool) -> None:
        self.protokoll.append((time.perf_counter(), "t", name, runter))
        log.info("[trocken] Taste %-10s %s", name, "runter" if runter else "hoch")

    def _maus(self, knopf: str, runter: bool) -> None:
        self.protokoll.append((time.perf_counter(), "m", knopf, runter))
        log.info("[trocken] Maus  %-10s %s", knopf, "runter" if runter else "hoch")


class WindowsAusgabe(Ausgabe):
    name = "windows-sendinput"

    def __init__(self) -> None:
        super().__init__()
        if sys.platform != "win32":
            raise AusgabeFehler("WindowsAusgabe laeuft nur unter Windows")
        import ctypes
        from ctypes import wintypes

        self._ct = ctypes
        ULONG_PTR = ctypes.c_size_t

        class KEYBDINPUT(ctypes.Structure):
            _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD),
                        ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD),
                        ("dwExtraInfo", ULONG_PTR)]

        class MOUSEINPUT(ctypes.Structure):
            _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG),
                        ("mouseData", wintypes.DWORD), ("dwFlags", wintypes.DWORD),
                        ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]

        class HARDWAREINPUT(ctypes.Structure):
            _fields_ = [("uMsg", wintypes.DWORD), ("wParamL", wintypes.WORD),
                        ("wParamH", wintypes.WORD)]

        class _U(ctypes.Union):
            _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT), ("hi", HARDWAREINPUT)]

        class INPUT(ctypes.Structure):
            _anonymous_ = ("u",)
            _fields_ = [("type", wintypes.DWORD), ("u", _U)]

        self._INPUT, self._KEYBDINPUT, self._MOUSEINPUT = INPUT, KEYBDINPUT, MOUSEINPUT
        self._user32 = ctypes.WinDLL("user32", use_last_error=True)
        self._user32.SendInput.argtypes = (wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int)
        self._user32.SendInput.restype = wintypes.UINT
        self._user32.MapVirtualKeyExW.argtypes = (wintypes.UINT, wintypes.UINT, wintypes.HKL)
        self._user32.MapVirtualKeyExW.restype = wintypes.UINT
        self._user32.GetKeyboardLayout.argtypes = (wintypes.DWORD,)
        self._user32.GetKeyboardLayout.restype = wintypes.HKL
        self._layout = self._user32.GetKeyboardLayout(0)
        self._scan_cache: dict[str, int] = {}

    def scancode(self, name: str) -> int:
        if name not in self._scan_cache:
            sc = self._user32.MapVirtualKeyExW(tasten.VK[name], 0, self._layout)  # MAPVK_VK_TO_VSC
            if not sc:
                sc = tasten.SCAN_ERSATZ.get(name, 0)
            if not sc:
                raise AusgabeFehler(f"kein Scancode fuer '{name}'")
            self._scan_cache[name] = sc
        return self._scan_cache[name]

    def _senden(self, eingabe) -> None:
        n = self._user32.SendInput(1, self._ct.byref(eingabe), self._ct.sizeof(eingabe))
        if n != 1:
            err = self._ct.get_last_error()
            raise AusgabeFehler(
                f"SendInput blockiert (Fehler {err}). Laeuft das Spiel als Administrator? "
                "Dann das Makro ebenfalls als Administrator starten.")

    def _taste(self, name: str, runter: bool) -> None:
        KEYEVENTF_EXTENDEDKEY, KEYEVENTF_KEYUP, KEYEVENTF_SCANCODE = 0x1, 0x2, 0x8
        flags = KEYEVENTF_SCANCODE
        if name in tasten.ERWEITERT:
            flags |= KEYEVENTF_EXTENDEDKEY
        if not runter:
            flags |= KEYEVENTF_KEYUP
        e = self._INPUT(type=1)  # INPUT_KEYBOARD
        e.ki = self._KEYBDINPUT(0, self.scancode(name), flags, 0, 0)
        self._senden(e)

    _MAUS = {  # knopf -> (runter-flag, hoch-flag, mouseData)
        "links": (0x0002, 0x0004, 0), "rechts": (0x0008, 0x0010, 0),
        "mitte": (0x0020, 0x0040, 0), "maus4": (0x0080, 0x0100, 1),
        "maus5": (0x0080, 0x0100, 2),
    }

    def _maus(self, knopf: str, runter: bool) -> None:
        f_runter, f_hoch, daten = self._MAUS[knopf]
        e = self._INPUT(type=0)  # INPUT_MOUSE
        e.mi = self._MOUSEINPUT(0, 0, daten, f_runter if runter else f_hoch, 0, 0)
        self._senden(e)


class PynputAusgabe(Ausgabe):
    name = "pynput"

    def __init__(self) -> None:
        super().__init__()
        try:
            from pynput import keyboard, mouse
        except Exception as e:
            raise AusgabeFehler(f"pynput nicht verfuegbar: {e} - pip install -r kombo/requirements.txt") from None
        self._kb = keyboard.Controller()
        self._ms = mouse.Controller()
        K, B = keyboard.Key, mouse.Button
        self._sonder = {
            "backspace": K.backspace, "tab": K.tab, "enter": K.enter, "shift": K.shift,
            "ctrl": K.ctrl, "alt": K.alt, "capslock": K.caps_lock, "esc": K.esc,
            "space": K.space, "pageup": K.page_up, "pagedown": K.page_down, "end": K.end,
            "home": K.home, "left": K.left, "up": K.up, "right": K.right, "down": K.down,
            "insert": getattr(K, "insert", None), "delete": K.delete,
        }
        for i in range(1, 21):
            self._sonder[f"f{i}"] = getattr(K, f"f{i}", None)
        self._knopf = {"links": B.left, "rechts": B.right, "mitte": B.middle,
                       "maus4": getattr(B, "x1", None) or getattr(B, "button8", None),
                       "maus5": getattr(B, "x2", None) or getattr(B, "button9", None)}
        self._KeyCode = keyboard.KeyCode

    def _taste(self, name: str, runter: bool) -> None:
        if name in self._sonder:
            k = self._sonder[name]
        elif len(name) == 1:
            k = name
        elif name.startswith("num") and name[3:].isdigit():
            k = self._KeyCode.from_vk(tasten.VK[name])
        else:
            k = None
        if k is None:
            raise AusgabeFehler(f"Taste '{name}' wird von pynput auf diesem System nicht unterstuetzt")
        (self._kb.press if runter else self._kb.release)(k)

    def _maus(self, knopf: str, runter: bool) -> None:
        b = self._knopf.get(knopf)
        if b is None:
            raise AusgabeFehler(f"Maustaste '{knopf}' wird auf diesem System nicht unterstuetzt")
        (self._ms.press if runter else self._ms.release)(b)


def erzeugen(trocken: bool = False) -> Ausgabe:
    if trocken:
        return TrockenAusgabe()
    if sys.platform == "win32":
        return WindowsAusgabe()
    log.warning("Kein Windows - nutze pynput. Fuer das Spiel bitte unter Windows starten.")
    return PynputAusgabe()
