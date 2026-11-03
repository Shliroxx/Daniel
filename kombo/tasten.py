"""Tastennamen, Hotkeys und virtuelle Tastencodes.

Alle Namen in der Konfiguration laufen hier durch: Kleinschreibung, deutsche
Aliase (strg, umschalt, leertaste ...) und die Pruefung, ob es die Taste gibt.
Die Windows-Codes (VK) werden zur Laufzeit ueber das aktive Tastaturlayout in
Scancodes umgerechnet - so trifft "z" auf QWERTZ auch wirklich die Z-Taste.
"""

from __future__ import annotations

from dataclasses import dataclass

# --- Aliase -> kanonischer Name ---------------------------------------------
ALIASE: dict[str, str] = {
    "strg": "ctrl", "control": "ctrl", "steuerung": "ctrl",
    "umschalt": "shift", "hochstell": "shift",
    "leertaste": "space", "leer": "space",
    "eingabe": "enter", "return": "enter",
    "rueck": "backspace", "rücktaste": "backspace", "ruecktaste": "backspace",
    "entf": "delete", "del": "delete",
    "einfg": "insert", "ins": "insert",
    "pos1": "home",
    "ende": "end",
    "bildauf": "pageup", "bild_auf": "pageup",
    "bildab": "pagedown", "bild_ab": "pagedown",
    "escape": "esc",
    "feststell": "capslock",
    "pfeiloben": "up", "pfeilunten": "down", "pfeillinks": "left", "pfeilrechts": "right",
    "mouse4": "maus4", "x1": "maus4", "seitentaste1": "maus4",
    "mouse5": "maus5", "x2": "maus5", "seitentaste2": "maus5",
    "mausmitte": "maus_mitte", "mittelklick": "maus_mitte",
}

# --- Windows Virtual-Key-Codes ----------------------------------------------
VK: dict[str, int] = {
    "backspace": 0x08, "tab": 0x09, "enter": 0x0D, "shift": 0x10, "ctrl": 0x11,
    "alt": 0x12, "capslock": 0x14, "esc": 0x1B, "space": 0x20,
    "pageup": 0x21, "pagedown": 0x22, "end": 0x23, "home": 0x24,
    "left": 0x25, "up": 0x26, "right": 0x27, "down": 0x28,
    "insert": 0x2D, "delete": 0x2E,
    "num*": 0x6A, "num+": 0x6B, "num-": 0x6D, "num.": 0x6E, "num/": 0x6F,
}
for _c in "abcdefghijklmnopqrstuvwxyz0123456789":
    VK[_c] = ord(_c.upper())
for _i in range(1, 25):
    VK[f"f{_i}"] = 0x6F + _i
for _i in range(10):
    VK[f"num{_i}"] = 0x60 + _i

# Tasten, die im Scancode das E0-Praefix brauchen
ERWEITERT = {"up", "down", "left", "right", "insert", "delete", "home", "end",
             "pageup", "pagedown", "num/"}

# Ersatz-Scancodes (US-Set-1), falls MapVirtualKey nicht verfuegbar ist
SCAN_ERSATZ: dict[str, int] = {
    "esc": 0x01, "backspace": 0x0E, "tab": 0x0F, "enter": 0x1C, "ctrl": 0x1D,
    "shift": 0x2A, "alt": 0x38, "space": 0x39, "capslock": 0x3A,
    "up": 0x48, "down": 0x50, "left": 0x4B, "right": 0x4D, "insert": 0x52,
    "delete": 0x53, "home": 0x47, "end": 0x4F, "pageup": 0x49, "pagedown": 0x51,
    "num*": 0x37, "num-": 0x4A, "num+": 0x4E, "num.": 0x53, "num/": 0x35,
    "num7": 0x47, "num8": 0x48, "num9": 0x49, "num4": 0x4B, "num5": 0x4C,
    "num6": 0x4D, "num1": 0x4F, "num2": 0x50, "num3": 0x51, "num0": 0x52,
    "f11": 0x57, "f12": 0x58,
}
for _i, _c in enumerate("1234567890"):
    SCAN_ERSATZ[_c] = 0x02 + _i
for _i, _c in enumerate("qwertyuiop"):
    SCAN_ERSATZ[_c] = 0x10 + _i
for _i, _c in enumerate("asdfghjkl"):
    SCAN_ERSATZ[_c] = 0x1E + _i
for _i, _c in enumerate("zxcvbnm"):
    SCAN_ERSATZ[_c] = 0x2C + _i
for _i in range(1, 11):
    SCAN_ERSATZ[f"f{_i}"] = 0x3A + _i

MODIFIKATOREN = {"ctrl", "shift", "alt"}

# Maustasten fuer Schritte (klick ...) und fuer Hotkeys
MAUS_SCHRITT = {"links": "links", "left": "links", "l": "links",
                "rechts": "rechts", "right": "rechts", "r": "rechts",
                "mitte": "mitte", "middle": "mitte", "m": "mitte",
                "maus4": "maus4", "x1": "maus4", "maus5": "maus5", "x2": "maus5"}
MAUS_HOTKEY = {"maus4", "maus5", "maus_mitte"}

# Rueckweg VK -> Name fuer den Listener (inkl. linker/rechter Modifikatoren)
VK_ZU_NAME: dict[int, str] = {v: k for k, v in VK.items()}
VK_ZU_NAME.update({0xA0: "shift", 0xA1: "shift", 0xA2: "ctrl", 0xA3: "ctrl",
                   0xA4: "alt", 0xA5: "alt"})


class TastenFehler(ValueError):
    """Unbekannter Tastenname oder kaputter Hotkey."""


def taste(name: str) -> str:
    """Normalisiert einen Tastennamen der Tastatur; wirft TastenFehler."""
    if not isinstance(name, str) or not name.strip():
        raise TastenFehler("leerer Tastenname")
    n = name.strip().lower()
    n = ALIASE.get(n, n)
    if n not in VK:
        raise TastenFehler(f"unbekannte Taste '{name}'")
    return n


def maustaste(name: str) -> str:
    n = name.strip().lower()
    if n not in MAUS_SCHRITT:
        raise TastenFehler(f"unbekannte Maustaste '{name}' (links, rechts, mitte, maus4, maus5)")
    return MAUS_SCHRITT[n]


@dataclass(frozen=True)
class Hotkey:
    haupt: str                       # z.B. "1", "f5", "maus4"
    mods: frozenset[str] = frozenset()

    @property
    def ist_maus(self) -> bool:
        return self.haupt in MAUS_HOTKEY

    def __str__(self) -> str:
        return "+".join(sorted(self.mods) + [self.haupt])


def hotkey(text: str) -> Hotkey:
    """'alt+1', 'strg+umschalt+f', 'maus4' -> Hotkey."""
    if not isinstance(text, str) or not text.strip():
        raise TastenFehler("leerer Hotkey")
    teile = [t.strip().lower() for t in text.split("+")]
    if any(not t for t in teile):
        raise TastenFehler(f"kaputter Hotkey '{text}'")
    *mods_roh, haupt_roh = teile
    mods = set()
    for m in mods_roh:
        m = taste(m)
        if m not in MODIFIKATOREN:
            raise TastenFehler(f"'{m}' ist kein Modifikator (ctrl, shift, alt) in '{text}'")
        mods.add(m)
    haupt = ALIASE.get(haupt_roh, haupt_roh)
    if haupt in ("links", "rechts", "left", "right_click", "maus1", "maus2"):
        raise TastenFehler(f"linke/rechte Maustaste als Hotkey ist nicht erlaubt ('{text}')")
    if haupt not in MAUS_HOTKEY:
        haupt = taste(haupt)
        if haupt in MODIFIKATOREN:
            raise TastenFehler(f"Modifikator allein ist kein Hotkey ('{text}')")
    return Hotkey(haupt, frozenset(mods))
