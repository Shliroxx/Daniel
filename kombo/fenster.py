"""Titel des aktiven Fensters - damit Kombos nur im Spiel feuern."""

from __future__ import annotations

import logging
import sys

log = logging.getLogger("kombo.fenster")


def aktiver_titel() -> str | None:
    """Titel des Vordergrundfensters; None, wenn es sich nicht ermitteln laesst."""
    if sys.platform != "win32":
        return None
    try:
        import ctypes
        user32 = ctypes.WinDLL("user32", use_last_error=True)
        hwnd = user32.GetForegroundWindow()
        if not hwnd:
            return ""
        laenge = user32.GetWindowTextLengthW(hwnd)
        puffer = ctypes.create_unicode_buffer(laenge + 1)
        user32.GetWindowTextW(hwnd, puffer, laenge + 1)
        return puffer.value
    except OSError as e:
        log.debug("Fenstertitel nicht lesbar: %s", e)
        return None


def passt(filter_: tuple[str, ...], titel: str | None) -> bool:
    """Leerer Filter = ueberall. Unbekannter Titel (kein Windows) = erlaubt."""
    if not filter_ or titel is None:
        return True
    t = titel.lower()
    return any(f in t for f in filter_)
