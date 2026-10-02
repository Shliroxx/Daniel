#!/usr/bin/env python3
"""Erzeugt alle Soundeffekte von Kingdom Omnitrix per Synthese (rechtefrei) und die sounds.json.

Jeder Sound entsteht aus Oszillatoren, Rauschen, Filtern, Huellkurven und einem einfachen Hall.
Die WAV-Daten werden mit ffmpeg (libvorbis) in OGG umgewandelt, wie Minecraft es verlangt (mono, damit
der Klang eine Position im Raum hat). Aufruf aus minecraft-mod/:

    python tools/generate_sounds.py            # erzeugt OGG-Dateien und sounds.json (braucht numpy + ffmpeg)
    python tools/generate_sounds.py --check    # prueft nur, ob alles vorhanden und aktuell ist (ohne numpy/ffmpeg)
    python tools/generate_sounds.py --only omnitrix_transform   # einzelne Sounds neu erzeugen

Ersetzen durch eigene Aufnahmen: gleichnamige OGG-Datei in assets/kingdomomnitrix/sounds/ ablegen und
den Namen in KEEP eintragen, dann ueberschreibt der Generator sie nicht mehr.
"""
from __future__ import annotations

import argparse
import json
import logging
import shutil
import subprocess
import sys
import tempfile
import wave
from pathlib import Path
from typing import Callable

LOG = logging.getLogger("sounds")
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"
SR = 32000
TARGET_RMS = 10 ** (-16 / 20)
PEAK_LIMIT = 0.89

# Sounds, die von Hand ersetzt wurden und nicht neu erzeugt werden sollen (Dateiname ohne .ogg)
KEEP: set[str] = set()

# Ereignis -> (Datei-Basisname, Anzahl Varianten, Untertitel-Schluessel)
# Die Untertitel stehen in den Sprachdateien als subtitles.kingdomomnitrix.<ereignis>.
EVENTS: dict[str, tuple[str, int]] = {
    # Omnitrix & Aliens
    "omnitrix.transform": ("omnitrix_transform", 1),
    "omnitrix.revert": ("omnitrix_revert", 1),
    "omnitrix.beep": ("omnitrix_beep", 1),
    "omnitrix.select": ("omnitrix_select", 1),
    "omnitrix.activate": ("omnitrix_activate", 1),
    "omnitrix.open": ("omnitrix_open", 1),
    "omnitrix.navigate": ("omnitrix_navigate", 2),
    "omnitrix.confirm": ("omnitrix_confirm", 1),
    "omnitrix.cancel": ("omnitrix_cancel", 1),
    "omnitrix.error": ("omnitrix_error", 1),
    "omnitrix.warning": ("omnitrix_warning", 1),
    "omnitrix.overheat": ("omnitrix_overheat", 1),
    "omnitrix.ready": ("omnitrix_ready", 1),
    "omnitrix.unlock": ("omnitrix_unlock", 1),
    "omnitrix.lock": ("omnitrix_lock", 1),
    "omnitrix.master_control": ("omnitrix_master_control", 1),
    "omnitrix.emergency": ("omnitrix_emergency", 1),
    "omnitrix.dna_shock": ("omnitrix_dna_shock", 1),
    "omnitrix.malfunction": ("omnitrix_malfunction", 1),
    "alien.fire": ("alien_fire", 2),
    "alien.slam": ("alien_slam", 1),
    "alien.dash": ("alien_dash", 2),
    "alien.crystal": ("alien_crystal", 2),
    # Kampf & Magie
    "combat.swing": ("combat_swing", 3),
    "combat.hit": ("combat_hit", 2),
    "combat.finisher": ("combat_finisher", 1),
    "combat.guard": ("combat_guard", 1),
    "combat.dodge": ("combat_dodge", 1),
    "magic.fire": ("magic_fire", 1),
    "magic.blizzard": ("magic_blizzard", 1),
    "magic.thunder": ("magic_thunder", 1),
    "magic.cure": ("magic_cure", 1),
    "magic.mp_empty": ("magic_mp_empty", 1),
    "hero.discovery": ("hero_discovery", 1),
    "hero.level_up": ("hero_level_up", 1),
    # Technik & Welten
    "weapon.combuster": ("weapon_combuster", 2),
    "weapon.empty": ("weapon_empty", 1),
    "weapon.throw": ("weapon_throw", 1),
    "weapon.bolt": ("weapon_bolt", 2),
    "weapon.buy": ("weapon_buy", 1),
    "gadget.heli": ("gadget_heli", 1),
    "gadget.jet": ("gadget_jet", 1),
    "gadget.swingshot": ("gadget_swingshot", 1),
    "gadget.attach": ("gadget_attach", 1),
    "ship.launch": ("ship_launch", 1),
    "ship.ai": ("ship_ai", 1),
    "world.rift": ("world_rift", 1),
    "arena.round": ("arena_round", 1),
    "arena.victory": ("arena_victory", 1),
    # Gegner & Boss
    "heartless.ambient": ("heartless_ambient", 3),
    "heartless.hurt": ("heartless_hurt", 2),
    "heartless.death": ("heartless_death", 1),
    "heartless.spawn": ("heartless_spawn", 1),
    "boss.laser_charge": ("boss_laser_charge", 1),
    "boss.laser_fire": ("boss_laser_fire", 1),
    "boss.rocket": ("boss_rocket", 1),
    "boss.stomp": ("boss_stomp", 1),
    "boss.overload": ("boss_overload", 1),
    "boss.hurt": ("boss_hurt", 2),
    "boss.death": ("boss_death", 1),
}


def file_names() -> list[str]:
    names = []
    for base, variants in EVENTS.values():
        names += [base] if variants == 1 else [f"{base}_{i + 1}" for i in range(variants)]
    return names


def sounds_json() -> dict:
    data = {}
    for event, (base, variants) in EVENTS.items():
        files = [base] if variants == 1 else [f"{base}_{i + 1}" for i in range(variants)]
        data[event] = {
            "subtitle": f"subtitles.kingdomomnitrix.{event}",
            "sounds": [f"kingdomomnitrix:{name}" for name in files],
        }
    return data


# --- Synthese ------------------------------------------------------------------------------------
# numpy wird erst hier geladen, damit --check ohne numpy laeuft (CI).

def synth_all(only: set[str] | None) -> dict[str, "object"]:
    import numpy as np

    import zlib

    rng = np.random.default_rng(0)

    def n(seconds: float) -> int:
        return int(SR * seconds)

    def time(seconds: float):
        return np.arange(n(seconds)) / SR

    def sweep(f0: float, f1: float, seconds: float, curve: float = 1.0):
        x = np.linspace(0.0, 1.0, n(seconds)) ** curve
        return f0 * (f1 / f0) ** x if f0 > 0 and f1 > 0 else f0 + (f1 - f0) * x

    def osc(freq, seconds: float, kind: str = "sine"):
        f = np.broadcast_to(np.asarray(freq, dtype=float), (n(seconds),))
        phase = np.cumsum(2 * np.pi * f / SR)
        if kind == "sine":
            return np.sin(phase)
        if kind == "square":
            return np.sign(np.sin(phase)) * 0.6
        if kind == "saw":
            return ((phase / np.pi) % 2.0 - 1.0) * 0.7
        if kind == "tri":
            return (2 * np.abs((phase / np.pi) % 2.0 - 1.0) - 1.0)
        raise ValueError(kind)

    def noise(seconds: float):
        return rng.uniform(-1.0, 1.0, n(seconds))

    def lowpass(x, cutoff):
        c = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
        alpha = 1.0 - np.exp(-2 * np.pi * np.clip(c, 20, SR / 2.2) / SR)
        y = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):
            acc += alpha[i] * (x[i] - acc)
            y[i] = acc
        return y

    def highpass(x, cutoff):
        return x - lowpass(x, cutoff)

    def bandpass(x, low, high):
        return highpass(lowpass(x, high), low)

    def decay(seconds: float, rate: float):
        return np.exp(-time(seconds) * rate)

    def adsr(seconds: float, attack: float, release: float, sustain: float = 1.0):
        total = n(seconds)
        a = max(1, n(attack))
        r = max(1, n(release))
        env = np.full(total, sustain)
        env[:a] = np.linspace(0.0, sustain, a)
        env[-r:] *= np.linspace(1.0, 0.0, r)
        return env

    def pad(x, seconds: float):
        out = np.zeros(n(seconds))
        out[:min(len(x), len(out))] = x[:len(out)]
        return out

    def mix(seconds: float, *parts):
        """Addiert beliebig lange Teile auf die Laenge {@code seconds} (kuerzere werden aufgefuellt)."""
        out = np.zeros(n(seconds))
        for part in parts:
            out += pad(np.asarray(part, dtype=float), seconds)
        return out

    def at(x, start: float, seconds: float):
        out = np.zeros(n(seconds))
        s = n(start)
        end = min(len(out), s + len(x))
        out[s:end] = x[:end - s]
        return out

    def reverb(x, amount: float = 0.3, size: float = 1.0):
        y = x.copy()
        for delay_ms, gain in ((29.7, 0.7), (37.1, 0.65), (41.1, 0.6), (43.7, 0.55)):
            d = int(SR * delay_ms * size / 1000)
            buf = np.zeros(len(x) + d)
            buf[:len(x)] = x
            for i in range(d, len(buf)):
                buf[i] += buf[i - d] * gain * 0.6
            y += buf[:len(x)] * amount / 4
        return y

    def bell(freq: float, seconds: float, rate: float = 6.0, partials=((1, 1.0), (2.76, 0.4), (5.4, 0.2))):
        out = np.zeros(n(seconds))
        for mult, gain in partials:
            out += osc(freq * mult, seconds) * gain * decay(seconds, rate * (1 + mult * 0.3))
        return out

    def crackle(seconds: float, density: float):
        x = np.zeros(n(seconds))
        hits = rng.random(len(x)) < density / SR
        x[hits] = rng.uniform(-1, 1, hits.sum())
        return lowpass(x, 5000) * 6

    def whoosh(seconds: float, f_from: float, f_to: float, attack: float = 0.3):
        cutoff = sweep(f_from, f_to, seconds)
        body = bandpass(noise(seconds), cutoff * 0.4, cutoff)
        return body * adsr(seconds, seconds * attack, seconds * 0.6)

    def thump(seconds: float, f_from: float, f_to: float, rate: float):
        return osc(sweep(f_from, f_to, seconds, 0.4), seconds) * decay(seconds, rate)

    def metal(seconds: float, base: float, rate: float = 9.0):
        out = np.zeros(n(seconds))
        for mult, gain in ((1.0, 1.0), (2.41, 0.6), (3.87, 0.45), (5.93, 0.3), (8.1, 0.2)):
            out += osc(base * mult, seconds) * gain * decay(seconds, rate * (0.8 + mult * 0.15))
        return out + highpass(noise(seconds), 3000) * decay(seconds, 60) * 0.5

    def chord(freqs, seconds: float, kind: str = "tri"):
        return sum(osc(f, seconds, kind) for f in freqs) / len(freqs)

    notes = {"C5": 523.25, "E5": 659.25, "G5": 783.99, "C6": 1046.5, "E6": 1318.5, "G6": 1568.0, "A5": 880.0, "D6": 1174.7}

    def arpeggio(names, step: float, seconds: float, rate: float = 5.0):
        out = np.zeros(n(seconds))
        for i, name in enumerate(names):
            out += at(bell(notes[name], seconds - i * step, rate), i * step, seconds)
        return out

    # --- die einzelnen Sounds ---
    def omnitrix_transform():
        s = 1.2
        rise = lowpass(osc(sweep(180, 1500, 0.7, 1.4), 0.7, "saw"), sweep(600, 6000, 0.7)) * adsr(0.7, 0.05, 0.15)
        shimmer = osc(sweep(800, 2400, 0.7) + 40 * osc(37, 0.7), 0.7) * 0.25 * adsr(0.7, 0.3, 0.2)
        chime = bell(1760, 0.65, 4.0) + bell(2637, 0.65, 5.0) * 0.6
        whsh = whoosh(0.6, 400, 5000, 0.8) * 0.6
        return reverb(pad(rise + shimmer, s) + at(chime, 0.55, s) + at(whsh, 0.15, s), 0.35)

    def omnitrix_revert():
        s = 0.9
        fall = lowpass(osc(sweep(1300, 140, 0.7, 0.7), 0.7, "square"), sweep(5000, 500, 0.7)) * adsr(0.7, 0.01, 0.3)
        return reverb(mix(s, fall, whoosh(0.5, 3000, 300) * 0.4), 0.25)

    def omnitrix_beep():
        return osc(1850, 0.13, "square") * adsr(0.13, 0.005, 0.03) * 0.8

    def omnitrix_select():
        return osc(sweep(1100, 1500, 0.08), 0.08) * adsr(0.08, 0.004, 0.05)

    def omnitrix_activate():
        # Geraet erwacht: tiefer Puls, Servo-Surren nach oben, heller Ping
        s = 0.55
        pulse = thump(0.25, 140, 60, 12) * 0.7
        servo = lowpass(osc(sweep(220, 880, 0.3, 0.8), 0.3, "saw"), 1800) * adsr(0.3, 0.02, 0.1) * 0.35
        ping = bell(1975, 0.3, 9.0) * 0.5
        return reverb(mix(s, pulse, at(servo, 0.05, s), at(ping, 0.28, s)), 0.2)

    def omnitrix_open():
        # Gehaeuse oeffnet sich: mechanisches Klacken, Druckluft, Kern faehrt aus
        s = 0.5
        clack = metal(0.12, 900, 30) * 0.5
        hiss = bandpass(noise(0.3), 2500, 7000) * adsr(0.3, 0.01, 0.25) * 0.3
        rise = osc(sweep(400, 1200, 0.25), 0.25, "tri") * adsr(0.25, 0.02, 0.1) * 0.3
        return mix(s, clack, at(hiss, 0.04, s), at(rise, 0.12, s))

    def omnitrix_navigate(v):
        # Rastung des Rads: kurzes Klicken mit Tonhoehe je Variante
        base = 1350 if v == 0 else 1550
        click = metal(0.05, base * 0.7, 80) * 0.35
        tick = osc(sweep(base, base * 1.15, 0.04), 0.04) * adsr(0.04, 0.002, 0.03) * 0.5
        return mix(0.07, click, tick)

    def omnitrix_confirm():
        # Auswahl bestaetigt: Energieaufbau (steigender Ton) mit Doppelpiep
        s = 0.5
        charge = lowpass(osc(sweep(300, 2200, 0.42, 1.6), 0.42, "saw"), sweep(800, 7000, 0.42)) * adsr(0.42, 0.02, 0.05) * 0.4
        beeps = at(omnitrix_beep() * 0.6, 0.0, s) + at(omnitrix_beep() * 0.6, 0.16, s)
        return mix(s, charge, beeps)

    def omnitrix_cancel():
        s = 0.3
        down = osc(sweep(1200, 500, 0.18), 0.18, "tri") * adsr(0.18, 0.005, 0.1) * 0.5
        clack = metal(0.1, 700, 35) * 0.35
        return mix(s, down, at(clack, 0.12, s))

    def omnitrix_error():
        # tiefer Doppel-Buzz (verweigert)
        s = 0.42
        buzz = lowpass(osc(165, 0.14, "square"), 1400) * adsr(0.14, 0.005, 0.03)
        return mix(s, buzz * 0.7, at(buzz * 0.7, 0.2, s))

    def omnitrix_warning():
        # Warnung: drei steigende, harte Pieptoene
        s = 0.6
        out = np.zeros(n(s))
        for i, f in enumerate((1500, 1750, 2050)):
            out += at(osc(f, 0.1, "square") * adsr(0.1, 0.004, 0.03) * 0.6, i * 0.16, s)
        return out

    def omnitrix_overheat():
        # Ueberhitzung: Alarm-Sirene, Funkenknistern, Abschalt-Abfall
        s = 1.4
        siren = osc(1200 + 350 * osc(5.5, 0.9), 0.9, "square") * adsr(0.9, 0.01, 0.2) * 0.35
        sparks = crackle(1.0, 90) * 0.25
        down = lowpass(osc(sweep(900, 60, 0.6, 0.6), 0.6, "saw"), sweep(3000, 200, 0.6)) * adsr(0.6, 0.01, 0.3) * 0.5
        return reverb(mix(s, siren, sparks, at(down, 0.8, s)), 0.25)

    def omnitrix_ready():
        # wieder bereit: zwei aufsteigende Glockentoene
        s = 0.6
        return reverb(mix(s, bell(notes["E6"], 0.5, 6.0) * 0.5, at(bell(notes["G6"], 0.45, 6.0) * 0.5, 0.12, s)), 0.2)

    def omnitrix_unlock():
        # neue DNA: Scan-Sweep und Akkord
        s = 1.1
        scan = osc(sweep(600, 2400, 0.5) + 30 * osc(23, 0.5), 0.5, "tri") * adsr(0.5, 0.05, 0.1) * 0.3
        return reverb(mix(s, scan, at(arpeggio(["C6", "E6", "G6"], 0.09, 0.6, 4.0) * 0.5, 0.45, s)), 0.35)

    def omnitrix_lock():
        s = 0.5
        bolt = metal(0.18, 380, 22) * 0.6
        low = thump(0.3, 120, 50, 10) * 0.6
        return mix(s, bolt, at(low, 0.04, s))

    def omnitrix_master_control():
        # Master Control: tiefer Akkord, Schimmer, langer Hall
        s = 2.0
        pad_ = chord([130.8, 196.0, 261.6, 392.0], 1.6, "saw")
        body = lowpass(pad_, sweep(400, 5000, 1.6)) * adsr(1.6, 0.4, 0.8) * 0.35
        shimmer = osc(sweep(1600, 3200, 1.4), 1.4) * adsr(1.4, 0.6, 0.6) * 0.12
        return reverb(mix(s, body, at(shimmer, 0.3, s), at(bell(notes["C6"], 1.2, 2.5) * 0.4, 0.5, s)), 0.5, 1.4)

    def omnitrix_emergency():
        # Notfall: schneller Doppel-Alarm, dann harter Verwandlungs-Anstieg
        s = 1.1
        alarm = np.zeros(n(s))
        for i in range(4):
            alarm += at(osc(2200 if i % 2 == 0 else 1650, 0.09, "square") * adsr(0.09, 0.003, 0.02) * 0.5, i * 0.11, s)
        surge = lowpass(osc(sweep(150, 2500, 0.5, 1.8), 0.5, "saw"), sweep(500, 8000, 0.5)) * adsr(0.5, 0.02, 0.1) * 0.5
        return reverb(mix(s, alarm, at(surge, 0.45, s), at(thump(0.3, 120, 40, 9) * 0.7, 0.95, s)), 0.3)

    def omnitrix_dna_shock():
        # DNA-Schock: verzerrtes Abwaerts-Glitch, Knistern, dumpfer Aufprall
        s = 1.0
        glitch = osc(sweep(1800, 90, 0.7, 0.5) * (1 + 0.3 * np.sign(osc(23, 0.7, "square"))), 0.7, "square") * adsr(0.7, 0.005, 0.3) * 0.35
        return reverb(mix(s, glitch, crackle(0.6, 160) * 0.3, at(thump(0.35, 90, 35, 8) * 0.8, 0.5, s)), 0.25)

    def omnitrix_malfunction():
        # Fehlfunktion: stotterndes Signal (Tonhoehe springt), Knistern, eiernder Abwaerts-Ton
        s = 1.0
        stutter = np.zeros(n(s))
        for i, f in enumerate((1400, 900, 1700, 620, 1250, 480)):
            stutter += at(osc(f, 0.05, "square") * adsr(0.05, 0.002, 0.015) * 0.4, i * 0.075, s)
        wobble = osc(sweep(900, 260, 0.5) + 70 * osc(31, 0.5), 0.5, "saw") * adsr(0.5, 0.01, 0.25) * 0.25
        return reverb(mix(s, stutter, crackle(0.8, 260) * 0.22, at(lowpass(wobble, sweep(3000, 600, 0.5)), 0.42, s)), 0.25)

    def alien_fire(v):
        s = 0.6
        roar = lowpass(noise(s), sweep(800, 3500 + v * 600, s)) * adsr(s, 0.04, 0.35)
        return reverb(roar * 1.4 + crackle(s, 220) * adsr(s, 0.05, 0.3) + thump(s, 140, 60, 10) * 0.5, 0.2)

    def alien_slam():
        s = 0.8
        boom = thump(s, 95, 38, 5.5) * 1.2 + lowpass(noise(s), 300) * decay(s, 7) * 1.5
        debris = bandpass(noise(s), 800, 3500) * decay(s, 14) * 0.4
        return reverb(boom + debris, 0.3, 1.4)

    def alien_dash(v):
        return reverb(whoosh(0.4, 6000 - v * 1200, 700, 0.15) * 1.6, 0.15)

    def alien_crystal(v):
        s = 0.6
        out = np.zeros(n(s))
        for i in range(5):
            f = rng.uniform(2200, 5200) * (1 + v * 0.07)
            out += at(bell(f, 0.4, 12, ((1, 1.0), (2.1, 0.3))), i * 0.04, s) * 0.5
        return reverb(out, 0.4)

    def combat_swing(v):
        center = (2600, 3300, 2100)[v]
        return whoosh(0.28, center * 1.8, center * 0.5, 0.35) * 1.8

    def combat_hit(v):
        s = 0.22
        body = thump(s, 160 + v * 30, 70, 22) + bandpass(noise(s), 500, 4000) * decay(s, 35) * 0.9
        click = highpass(noise(0.01), 4000)
        return pad(at(click, 0, s) * 0.6 + body, s)

    def combat_finisher():
        s = 1.0
        impact = thump(0.5, 140, 45, 7) * 1.2 + lowpass(noise(0.5), 1200) * decay(0.5, 10)
        sparkle = arpeggio(["C6", "E6", "G6"], 0.05, 0.7, 7) * 0.4
        return reverb(pad(impact, s) + at(sparkle, 0.05, s), 0.35)

    def combat_guard():
        return reverb(metal(0.45, 620, 8) * 0.8, 0.25)

    def combat_dodge():
        return whoosh(0.3, 2500, 900, 0.2) * 1.2

    def magic_fire():
        s = 0.8
        blast = lowpass(noise(s), sweep(500, 4000, s, 0.5)) * adsr(s, 0.02, 0.5) * 1.2
        return reverb(blast + crackle(s, 300) * decay(s, 3) + thump(s, 110, 50, 6) * 0.5, 0.3)

    def magic_blizzard():
        s = 0.9
        wind = highpass(noise(s), sweep(3000, 6000, s)) * adsr(s, 0.1, 0.4) * 0.6
        tinkles = np.zeros(n(s))
        for i in range(8):
            tinkles += at(bell(rng.uniform(3000, 6500), 0.3, 18, ((1, 1.0),)), rng.uniform(0, 0.55), s) * 0.35
        return reverb(wind + tinkles, 0.45)

    def magic_thunder():
        s = 1.4
        crack = highpass(noise(0.12), 1500) * decay(0.12, 25) * 1.4
        rumble = lowpass(noise(s), 180) * adsr(s, 0.05, 1.0) * 3.0
        return reverb(pad(crack, s) + rumble, 0.35, 1.6)

    def magic_cure():
        s = 1.2
        return reverb(arpeggio(["C5", "E5", "G5", "C6", "E6"], 0.08, s, 3.5) * 0.7
                      + highpass(noise(s), 7000) * adsr(s, 0.3, 0.6) * 0.08, 0.5)

    def magic_mp_empty():
        s = 0.45
        return osc(sweep(900, 200, s), s, "tri") * adsr(s, 0.01, 0.2) * 0.6 + bandpass(noise(s), 1000, 5000) * decay(s, 9) * 0.3

    def hero_discovery():
        s = 0.6
        return reverb(at(bell(1318.5, 0.4, 7), 0, s) + at(bell(1975.5, 0.45, 6), 0.12, s), 0.3)

    def hero_level_up():
        s = 1.6
        melody = arpeggio(["C5", "E5", "G5", "C6"], 0.11, s, 2.5)
        harmony = at(chord([notes["C6"], notes["E6"], notes["G6"]], 0.9, "tri") * adsr(0.9, 0.02, 0.6) * 0.35, 0.44, s)
        return reverb(melody * 0.7 + harmony, 0.45)

    def weapon_combuster(v):
        s = 0.28
        zap = osc(sweep(2400 - v * 300, 280, s, 0.6), s, "square") * adsr(s, 0.003, 0.18) * 0.7
        return zap + bandpass(noise(s), 1500, 6000) * decay(s, 25) * 0.5 + thump(s, 180, 90, 25) * 0.4

    def weapon_empty():
        s = 0.1
        return pad(highpass(noise(0.015), 2000) * 0.8, s) + osc(2600, s) * decay(s, 60) * 0.3

    def weapon_throw():
        return whoosh(0.35, 1800, 600, 0.3) * 1.5

    def weapon_bolt(v):
        return metal(0.18, 2100 + v * 350, 25) * 0.6

    def weapon_buy():
        s = 0.6
        return reverb(at(metal(0.2, 2300, 20) * 0.5, 0, s) + at(metal(0.2, 2700, 20) * 0.5, 0.08, s)
                      + at(bell(1568, 0.4, 5), 0.18, s) * 0.6, 0.3)

    def gadget_heli():
        s = 0.55
        chop = (0.5 + 0.5 * np.sign(osc(18, s))) * lowpass(noise(s), 900)
        return chop * adsr(s, 0.05, 0.25) * 1.6 + thump(s, 90, 70, 4) * 0.2

    def gadget_jet():
        s = 0.7
        return lowpass(noise(s), sweep(1500, 4500, s)) * adsr(s, 0.02, 0.4) * 1.3 + osc(sweep(80, 140, s), s, "saw") * adsr(s, 0.02, 0.3) * 0.3

    def gadget_swingshot():
        s = 0.35
        return osc(sweep(500, 3000, s, 0.6), s, "saw") * adsr(s, 0.005, 0.15) * 0.5 + whoosh(s, 4000, 1500, 0.1) * 0.6

    def gadget_attach():
        return metal(0.25, 900, 14) * 0.8

    def ship_launch():
        s = 1.6
        rumble = lowpass(noise(s), sweep(200, 1400, s)) * adsr(s, 0.3, 0.5) * 2.0
        engine = osc(sweep(60, 160, s), s, "saw") * adsr(s, 0.2, 0.5) * 0.4
        return reverb(rumble + engine, 0.25, 1.4)

    def ship_ai():
        s = 0.4
        return at(osc(1320, 0.12, "tri") * adsr(0.12, 0.005, 0.05), 0, s) + at(osc(1760, 0.18, "tri") * adsr(0.18, 0.005, 0.08), 0.13, s)

    def world_rift():
        s = 1.5
        warp = osc(sweep(120, 1600, s, 1.6) * (1 + 0.03 * osc(9, s)), s, "saw")
        return reverb(lowpass(warp, sweep(500, 7000, s)) * adsr(s, 0.2, 0.3) * 0.7 + whoosh(s, 300, 6000, 0.7) * 0.6, 0.55, 1.5)

    def arena_round():
        s = 1.0
        horn = lowpass(chord([220, 277.2, 329.6], s, "saw"), 1400) * adsr(s, 0.06, 0.4) * 1.4
        return reverb(horn, 0.4, 1.3)

    def arena_victory():
        s = 1.8
        fanfare = arpeggio(["G5", "C6", "E6", "G6"], 0.12, s, 2.2) * 0.6
        brass = at(lowpass(chord([523.25, 659.25, 783.99], 1.0, "saw"), 2500) * adsr(1.0, 0.04, 0.6) * 0.7, 0.5, s)
        return reverb(fanfare + brass, 0.45)

    def heartless_ambient(v):
        s = 0.7
        base = (75, 95, 62)[v]
        voice = osc(base * (1 + 0.08 * osc(5 + v, s)), s, "saw")
        breath = bandpass(noise(s), 300, 1400) * 0.6
        return lowpass(voice * 0.6 + breath, 900) * adsr(s, 0.15, 0.35) * 1.4

    def heartless_hurt(v):
        s = 0.25
        chirp = osc(sweep(700 + v * 120, 260, s), s, "square") * adsr(s, 0.005, 0.15)
        return lowpass(chirp, 2500) * 0.9 + bandpass(noise(s), 400, 2000) * decay(s, 20) * 0.4

    def heartless_death():
        s = 1.3
        dissolve = bandpass(noise(0.8), 600, sweep(3000, 600, 0.8)) * adsr(0.8, 0.02, 0.6) * 1.2
        heart = at(bell(1046.5, 0.8, 4) + bell(1568, 0.8, 5) * 0.5, 0.35, s)
        return reverb(pad(dissolve, s) + heart * 0.6, 0.45)

    def heartless_spawn():
        s = 1.0
        swell = lowpass(noise(s), sweep(150, 1200, s, 2.0)) * np.linspace(0, 1, n(s)) ** 2 * 2.0
        return reverb(swell + osc(sweep(50, 90, s), s, "saw") * adsr(s, 0.6, 0.1) * 0.4, 0.3, 1.3)

    def boss_laser_charge():
        s = 1.5
        whine = osc(sweep(300, 2600, s, 1.3), s, "saw") * (0.6 + 0.4 * osc(sweep(6, 30, s), s)) * adsr(s, 0.1, 0.05)
        return lowpass(whine, 5000) * 0.8

    def boss_laser_fire():
        s = 1.1
        beam = chord([110, 220, 331], s, "saw") * adsr(s, 0.01, 0.3) + bandpass(noise(s), 1500, 7000) * adsr(s, 0.01, 0.3) * 0.5
        return reverb(lowpass(beam, 4000) * 1.2, 0.2)

    def boss_rocket():
        s = 0.7
        return lowpass(noise(s), sweep(5000, 1200, s)) * adsr(s, 0.01, 0.5) * 1.3 + thump(s, 200, 80, 12) * 0.5

    def boss_stomp():
        s = 0.9
        return reverb(mix(s, thump(s, 80, 32, 5) * 1.5, lowpass(noise(s), 250) * decay(s, 6) * 1.6,
                          metal(0.4, 300, 12) * 0.3), 0.3, 1.5)

    def boss_overload():
        s = 1.2
        siren = osc(np.where((time(s) * 4).astype(int) % 2 == 0, 880, 660), s, "square")
        return lowpass(siren, 3000) * adsr(s, 0.01, 0.1) * 0.7

    def boss_hurt(v):
        return metal(0.35, 410 + v * 70, 11) * 0.8 + crackle(0.35, 400) * decay(0.35, 8) * 0.6

    def boss_death():
        s = 2.4
        blast = lowpass(noise(s), 900) * decay(s, 2.0) * 2.0 + thump(s, 70, 25, 2.5) * 1.3
        power_down = at(osc(sweep(900, 60, 1.4, 0.6), 1.4, "square") * adsr(1.4, 0.01, 0.6) * 0.4, 0.3, s)
        return reverb(blast + power_down, 0.4, 1.8)

    designs: dict[str, Callable[[], object]] = {
        "omnitrix_transform": omnitrix_transform, "omnitrix_revert": omnitrix_revert,
        "omnitrix_beep": omnitrix_beep, "omnitrix_select": omnitrix_select,
        "omnitrix_activate": omnitrix_activate, "omnitrix_open": omnitrix_open,
        "omnitrix_confirm": omnitrix_confirm, "omnitrix_cancel": omnitrix_cancel, "omnitrix_error": omnitrix_error,
        "omnitrix_warning": omnitrix_warning, "omnitrix_overheat": omnitrix_overheat, "omnitrix_ready": omnitrix_ready,
        "omnitrix_unlock": omnitrix_unlock, "omnitrix_lock": omnitrix_lock,
        "omnitrix_master_control": omnitrix_master_control, "omnitrix_emergency": omnitrix_emergency,
        "omnitrix_dna_shock": omnitrix_dna_shock, "omnitrix_malfunction": omnitrix_malfunction,
        "alien_slam": alien_slam, "combat_finisher": combat_finisher, "combat_guard": combat_guard,
        "combat_dodge": combat_dodge, "magic_fire": magic_fire, "magic_blizzard": magic_blizzard,
        "magic_thunder": magic_thunder, "magic_cure": magic_cure, "magic_mp_empty": magic_mp_empty,
        "hero_discovery": hero_discovery, "hero_level_up": hero_level_up, "weapon_empty": weapon_empty,
        "weapon_throw": weapon_throw, "weapon_buy": weapon_buy, "gadget_heli": gadget_heli, "gadget_jet": gadget_jet,
        "gadget_swingshot": gadget_swingshot, "gadget_attach": gadget_attach, "ship_launch": ship_launch,
        "ship_ai": ship_ai, "world_rift": world_rift, "arena_round": arena_round, "arena_victory": arena_victory,
        "heartless_death": heartless_death, "heartless_spawn": heartless_spawn,
        "boss_laser_charge": boss_laser_charge, "boss_laser_fire": boss_laser_fire, "boss_rocket": boss_rocket,
        "boss_stomp": boss_stomp, "boss_overload": boss_overload, "boss_death": boss_death,
    }
    variant_designs = {
        "omnitrix_navigate": omnitrix_navigate, "alien_fire": alien_fire, "alien_dash": alien_dash, "alien_crystal": alien_crystal, "combat_swing": combat_swing,
        "combat_hit": combat_hit, "weapon_combuster": weapon_combuster, "weapon_bolt": weapon_bolt,
        "heartless_ambient": heartless_ambient, "heartless_hurt": heartless_hurt, "boss_hurt": boss_hurt,
    }

    result = {}
    for base, variants in EVENTS.values():
        if variants == 1:
            jobs = [(base, lambda b=base: designs[b]())]
        else:
            jobs = [(f"{base}_{i + 1}", lambda b=base, i=i: variant_designs[b](i)) for i in range(variants)]
        for name, job in jobs:
            if (only and name not in only) or name in KEEP:
                continue
            # eigener Zufalls-Seed je Datei: gleiches Ergebnis, egal ob einzeln (--only) oder alle erzeugt werden
            rng = np.random.default_rng(zlib.crc32(name.encode("utf-8")))
            x = np.asarray(job(), dtype=float)
            x = x - np.mean(x)
            # kurze Ein-/Ausblendung gegen Knacken, dann auf -1 dBFS normalisieren
            fade = min(len(x) // 4, int(SR * 0.004))
            if fade > 0:
                x[:fade] *= np.linspace(0, 1, fade)
                x[-fade:] *= np.linspace(1, 0, fade)
            # sehr spitze Signale (Knistern, Klicks) weich begrenzen, damit sie nicht leiser wirken als der Rest
            peak = np.max(np.abs(x))
            rms = np.sqrt(np.mean(x ** 2))
            if rms > 0 and peak / rms > 5.0:
                x = np.tanh(x / (rms * 3.0)) * rms * 3.0
            # Lautheit angleichen: Ziel -16 dBFS RMS, Spitze hoechstens -1 dBFS
            peak = np.max(np.abs(x))
            rms = np.sqrt(np.mean(x ** 2))
            if peak > 0 and rms > 0:
                x = x * min(TARGET_RMS / rms, PEAK_LIMIT / peak)
            result[name] = (x * 32767).astype(np.int16)
    return result


def encode(name: str, samples, target: Path, ffmpeg: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / f"{name}.wav"
        with wave.open(str(wav), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(SR)
            out.writeframes(samples.tobytes())
        command = [ffmpeg, "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5",
                   "-map_metadata", "-1", "-fflags", "+bitexact", str(target)]
        subprocess.run(command, check=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alles vorhanden und aktuell ist")
    parser.add_argument("--only", nargs="*", help="nur diese Dateien (ohne .ogg) neu erzeugen")
    parser.add_argument("--wav", type=Path, help="zusaetzlich WAV-Dateien in diesen Ordner schreiben (zum Anhoeren)")
    parser.add_argument("-v", "--verbose", action="store_true", help="jede Datei protokollieren")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    sounds_dir = ASSETS / "sounds"
    json_path = ASSETS / "sounds.json"
    if args.check:
        problems = [f"sounds/{name}.ogg" for name in file_names() if not (sounds_dir / f"{name}.ogg").is_file()]
        if not json_path.is_file() or json.loads(json_path.read_text(encoding="utf-8")) != sounds_json():
            problems.append("sounds.json")
        for p in problems:
            LOG.error("fehlt oder veraltet: %s", p)
        LOG.info("%d Sound-Ereignisse, %d Dateien, %d Probleme", len(EVENTS), len(file_names()), len(problems))
        return 1 if problems else 0

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        LOG.error("ffmpeg fehlt (mit libvorbis), z. B. apt install ffmpeg")
        return 1
    try:
        samples = synth_all(set(args.only) if args.only else None)
    except ImportError:
        LOG.error("numpy fehlt: pip install numpy")
        return 1
    sounds_dir.mkdir(parents=True, exist_ok=True)
    for name, data in samples.items():
        try:
            encode(name, data, sounds_dir / f"{name}.ogg", ffmpeg)
        except (OSError, subprocess.CalledProcessError) as exc:
            LOG.error("konnte %s nicht kodieren: %s", name, exc)
            return 1
        if args.wav:
            args.wav.mkdir(parents=True, exist_ok=True)
            with wave.open(str(args.wav / f"{name}.wav"), "wb") as out:
                out.setnchannels(1)
                out.setsampwidth(2)
                out.setframerate(SR)
                out.writeframes(data.tobytes())
        LOG.debug("erzeugt: %s (%.2f s)", name, len(data) / SR)
    json_path.write_text(json.dumps(sounds_json(), indent=2) + "\n", encoding="utf-8")
    LOG.info("%d Sounds und sounds.json nach %s geschrieben", len(samples), ASSETS)
    return 0


if __name__ == "__main__":
    sys.exit(main())
