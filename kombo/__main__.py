"""Kombo-Makro fuer Zenkai / Nanos World.

    python -m kombo                     Makro starten (kombo.json)
    python -m kombo --beispiel          Beispiel-Konfiguration anlegen
    python -m kombo --pruefen           nur pruefen, nichts senden
    python -m kombo --liste             alle Kombos mit Hotkey und Dauer
    python -m kombo --zeige NAME        Schritte einer Kombo anzeigen
    python -m kombo --test NAME         Kombo nach Countdown einmal abspielen
    python -m kombo --trocken           wie normal, aber nur protokollieren
    python -m kombo --fenster-zeigen    Titel des aktiven Fensters anzeigen
"""

from __future__ import annotations

import argparse
import dataclasses
import logging
import shutil
import signal
import sys
import threading
import time
from pathlib import Path

from . import __version__, fenster
from .ablauf import Abspieler
from .ausgabe import AusgabeFehler, erzeugen
from .konfig import Konfig, KonfigFehler, laden
from .steuerung import Steuerung
from .tasten import VK_ZU_NAME, ALIASE, MAUS_HOTKEY, VK

log = logging.getLogger("kombo")
HIER = Path(__file__).resolve().parent
BEISPIEL = HIER / "beispiel.json"
STANDARD = Path("kombo.json")


def logging_einrichten(verbose: bool, debug: bool, datei: str | None) -> None:
    stufe = logging.DEBUG if debug else logging.INFO
    handler: list[logging.Handler] = [logging.StreamHandler(sys.stdout)]
    if datei:
        try:
            handler.append(logging.FileHandler(datei, encoding="utf-8"))
        except OSError as e:
            print(f"Log-Datei '{datei}' nicht beschreibbar: {e}", file=sys.stderr)
    fmt = "%(asctime)s %(levelname)-7s %(name)s: %(message)s" if debug else "%(asctime)s  %(message)s"
    logging.basicConfig(level=stufe, format=fmt, datefmt="%H:%M:%S", handlers=handler, force=True)
    if not verbose and not debug:
        logging.getLogger("kombo.ausgabe").setLevel(logging.WARNING)


def argumente(argv: list[str] | None) -> argparse.Namespace:
    p = argparse.ArgumentParser(prog="python -m kombo",
                                description="Kombo-Makro fuer Zenkai / Nanos World",
                                formatter_class=argparse.RawDescriptionHelpFormatter,
                                epilog=__doc__)
    p.add_argument("-k", "--konfig", default=str(STANDARD), help="Konfigurationsdatei (Standard: kombo.json)")
    p.add_argument("--beispiel", nargs="?", const="", metavar="PFAD",
                   help="Beispiel-Konfiguration anlegen (Standard: --konfig)")
    p.add_argument("--ueberschreiben", action="store_true", help="mit --beispiel: vorhandene Datei ersetzen")
    p.add_argument("--pruefen", action="store_true", help="Konfiguration pruefen und beenden")
    p.add_argument("--liste", action="store_true", help="Kombos auflisten")
    p.add_argument("--zeige", metavar="NAME", help="Schritte einer Kombo anzeigen")
    p.add_argument("--test", metavar="NAME", help="Kombo einmal abspielen (ohne Hotkey)")
    p.add_argument("--countdown", type=float, default=3.0, help="Sekunden vor --test (Standard 3)")
    p.add_argument("--trocken", action="store_true", help="nichts senden, nur protokollieren")
    p.add_argument("--ueberall", action="store_true", help="Fensterfilter abschalten")
    p.add_argument("--fenster-zeigen", action="store_true", help="aktiven Fenstertitel 10 s lang anzeigen")
    p.add_argument("--fremde-eingaben", action="store_true",
                   help="auch eingespeiste Tasten als Hotkey werten (Maus-Software wie G Hub/Synapse)")
    p.add_argument("-v", "--verbose", action="store_true", help="jede Eingabe protokollieren")
    p.add_argument("--debug", action="store_true", help="Debug-Ausgabe")
    p.add_argument("--log-datei", metavar="PFAD", help="zusaetzlich in diese Datei schreiben")
    p.add_argument("--version", action="version", version=f"kombo {__version__}")
    return p.parse_args(argv)


def beispiel_anlegen(ziel: Path, ueberschreiben: bool) -> int:
    if ziel.exists() and not ueberschreiben:
        log.error("%s existiert schon - mit --ueberschreiben ersetzen.", ziel)
        return 1
    try:
        ziel.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(BEISPIEL, ziel)
    except OSError as e:
        log.error("Konnte %s nicht schreiben: %s", ziel, e)
        return 1
    log.info("Beispiel angelegt: %s - Tasten an deine Belegung anpassen.", ziel.resolve())
    return 0


def liste(kf: Konfig) -> None:
    breite = max(len(k.name) for k in kf.kombos)
    print(f"\n  {'Kombo':<{breite}}  {'Hotkey':<12} {'Modus':<11} {'Schritte':>8} {'Dauer':>8}")
    print("  " + "-" * (breite + 44))
    for k in kf.kombos:
        print(f"  {k.name:<{breite}}  {str(k.hotkey):<12} {k.modus:<11} {len(k.schritte):>8} "
              f"{k.dauer_ms():>6} ms")
    fen = ", ".join(kf.fenster_titel) or "ueberall"
    print(f"\n  Pause {kf.pause_taste} | Stopp {kf.stopp_taste} | Beenden {kf.beenden_taste} | "
          f"Fenster: {fen}\n")


def zeige(kf: Konfig, name: str) -> None:
    k = kf.kombo(name)
    print(f"\n  {k.name}  [{k.hotkey}]  modus={k.modus}  wiederholen={k.wiederholen}  "
          f"abstand={k.abstand_ms} ms  tastendauer={k.tastendauer_ms} ms")
    zeit = 0
    for i, s in enumerate(k.schritte, 1):
        print(f"  {i:>3}. t={zeit:>5} ms  {s}")
        if s.art in ("taste", "klick"):
            zeit += k.tastendauer_ms
        elif s.art in ("warte", "halte", "halteklick"):
            zeit += s.ms
    print(f"       Ende ~{zeit} ms pro Durchgang\n")


def fenster_zeigen() -> int:
    if sys.platform != "win32":
        log.error("Fenstertitel lassen sich nur unter Windows lesen.")
        return 1
    log.info("Wechsle jetzt ins Spiel - 10 s lang wird der aktive Titel angezeigt.")
    letzter = None
    ende = time.monotonic() + 10
    while time.monotonic() < ende:
        t = fenster.aktiver_titel()
        if t != letzter:
            log.info("Aktives Fenster: '%s'", t)
            letzter = t
        time.sleep(0.2)
    log.info("Einen Teil davon (klein geschrieben) in fenster_titel eintragen.")
    return 0


# --- pynput-Anbindung --------------------------------------------------------
def _name_aus_taste(key) -> str | None:
    """pynput-Key/KeyCode -> kanonischer Name (oder None)."""
    from pynput import keyboard
    if isinstance(key, keyboard.Key):
        n = key.name
        for basis in ("shift", "ctrl", "alt"):
            if n.startswith(basis):
                return basis
        n = {"page_up": "pageup", "page_down": "pagedown", "caps_lock": "capslock"}.get(n, n)
        return n if n in VK else None
    vk = getattr(key, "vk", None)
    if vk is not None and vk in VK_ZU_NAME:
        return VK_ZU_NAME[vk]
    ch = getattr(key, "char", None)
    if ch:
        ch = ALIASE.get(ch.lower(), ch.lower())
        return ch if ch in VK else None
    return None


def _name_aus_knopf(button) -> str | None:
    n = getattr(button, "name", "")
    return {"x1": "maus4", "button8": "maus4", "x2": "maus5", "button9": "maus5",
            "middle": "maus_mitte"}.get(n)


def listener_starten(st: Steuerung, fremde_eingaben: bool = False):
    try:
        from pynput import keyboard, mouse
    except Exception as e:
        raise AusgabeFehler(f"pynput fehlt ({e}) - pip install -r kombo/requirements.txt") from None

    def eingespeist(args) -> bool:
        # pynput >= 1.8 meldet eingespeiste Ereignisse - darunter unsere eigenen
        if fremde_eingaben:
            return False
        return bool(args and args[-1] is True)

    def bei_druck(key, *args):
        if eingespeist(args):
            return
        n = _name_aus_taste(key)
        if n:
            st.runter(n)
        if st.beendet.is_set():
            return False

    def bei_loslassen(key, *args):
        if eingespeist(args):
            return
        n = _name_aus_taste(key)
        if n:
            st.hoch(n)

    braucht_maus = any(k.hotkey.haupt in MAUS_HOTKEY for k in st.konfig.kombos)

    def bei_klick(x, y, button, gedrueckt, *args):
        if eingespeist(args):
            return
        n = _name_aus_knopf(button)
        if n:
            (st.runter if gedrueckt else st.hoch)(n)

    kb = keyboard.Listener(on_press=bei_druck, on_release=bei_loslassen)
    kb.start()
    ms = None
    if braucht_maus:
        ms = mouse.Listener(on_click=bei_klick)
        ms.start()
    return kb, ms


def starten(kf: Konfig, args: argparse.Namespace) -> int:
    try:
        ausgabe = erzeugen(trocken=args.trocken)
    except AusgabeFehler as e:
        log.error("%s", e)
        return 2
    st = Steuerung(kf, Abspieler(ausgabe))

    if args.test:
        try:
            k = kf.kombo(args.test)
        except KonfigFehler as e:
            log.error("%s", e)
            return 1
        log.info("Test '%s' startet in %.1f s - ins Spiel wechseln. Strg+C bricht ab.", k.name, args.countdown)
        try:
            time.sleep(max(0.0, args.countdown))
        except KeyboardInterrupt:
            log.info("Abgebrochen.")
            return 130
        if k.modus != "einmal":
            log.info("modus=%s - im Test genau ein Durchgang.", k.modus)
            k = dataclasses.replace(k, modus="einmal", wiederholen=1)
        st.starten(k)
        try:
            st.warten()
        except KeyboardInterrupt:
            st.stoppen(warten=True)
        erg = st.ergebnisse[-1] if st.ergebnisse else None
        return 0 if erg and not erg.fehler else 1

    try:
        kb, ms = listener_starten(st, args.fremde_eingaben)
    except AusgabeFehler as e:
        log.error("%s", e)
        return 2

    threading.Thread(target=st.fenster_wache, name="fenster-wache", daemon=True).start()
    signal.signal(signal.SIGINT, lambda *_: st.beenden())

    liste(kf)
    if not kf.fenster_titel:
        log.warning("Fensterfilter aus - Kombos feuern in JEDEM Fenster.")
    elif sys.platform != "win32":
        log.warning("Fensterfilter wirkt nur unter Windows.")
    log.info("Makro AKTIV (Ausgabe: %s). %s = Pause, %s = Stopp, %s oder Strg+C = Beenden.",
             ausgabe.name, kf.pause_taste, kf.stopp_taste, kf.beenden_taste)
    try:
        while not st.beendet.wait(0.5):
            if not kb.is_alive():
                log.error("Tastatur-Listener ist ausgefallen - beende.")
                break
    finally:
        st.stoppen(warten=True)
        kb.stop()
        if ms is not None:
            ms.stop()
        rest = ausgabe.alles_loslassen()
        if rest:
            log.info("Beim Beenden losgelassen: %s", ", ".join(rest))
        log.info("Beendet. %d Kombo(s) abgespielt.", len(st.ergebnisse))
    return 0


def main(argv: list[str] | None = None) -> int:
    args = argumente(argv)
    logging_einrichten(args.verbose or args.trocken, args.debug, args.log_datei)

    if args.beispiel is not None:
        return beispiel_anlegen(Path(args.beispiel or args.konfig), args.ueberschreiben)
    if args.fenster_zeigen:
        return fenster_zeigen()

    try:
        kf = laden(args.konfig)
    except KonfigFehler as e:
        log.error("Konfiguration: %s", e)
        return 1
    if args.ueberall:
        kf = Konfig(kombos=kf.kombos, fenster_titel=(), pause_taste=kf.pause_taste,
                    stopp_taste=kf.stopp_taste, beenden_taste=kf.beenden_taste)

    if args.pruefen:
        log.info("%s ist in Ordnung: %d Kombo(s).", args.konfig, len(kf.kombos))
        liste(kf)
        return 0
    if args.liste:
        liste(kf)
        return 0
    if args.zeige:
        try:
            zeige(kf, args.zeige)
        except KonfigFehler as e:
            log.error("%s", e)
            return 1
        return 0
    return starten(kf, args)


if __name__ == "__main__":
    sys.exit(main())
