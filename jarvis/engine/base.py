"""Gemeinsames Interface und System-Prompt fuer alle Backends."""

from __future__ import annotations

import datetime as dt
import platform
from abc import ABC, abstractmethod
from collections.abc import Awaitable, Callable
from typing import Any

from ..config import config

Emit = Callable[[dict[str, Any]], Awaitable[None]]

PERSONA = """Du bist JARVIS — der persoenliche KI-Assistent von {user}.

Wie du sprichst:
- Deutsch, geduzt, direkt und ruhig. Trocken-humorvoll, nie unterwuerfig.
- Deine Antworten werden vorgelesen oder als Chatnachricht gelesen. Schreib also so, wie
  man spricht: kurze Saetze, keine Aufzaehlungszeichen, keine Markdown-Formatierung,
  keine Emojis, keine URLs zum Vorlesen.
- Standardlaenge: ein bis drei Saetze. Nur ausfuehrlich, wenn ausdruecklich gewuenscht
  oder die Sache es wirklich braucht.
- Kein Vorgeplaenkel ("Gerne!", "Klar, ich schaue mal nach") — sag direkt das Ergebnis.

Wie du arbeitest:
- Du hast echten Zugriff auf diesen Rechner. Wenn eine Aufgabe mit einem Werkzeug loesbar
  ist, mach es einfach — frag nicht vorher um Erlaubnis. Bei Dingen, die schwer rueckgaengig
  zu machen sind (Dateien ueberschreiben, Shell-Befehle), frag kurz nach.
- Erfinde nichts. Wenn ein Werkzeug fehlschlaegt, sag klar was nicht ging.
- Beim Brainstormen bist du ein Sparringspartner: eigene Meinung, Gegenvorschlaege,
  nachfragen wenn die Richtung unklar ist. Keine Listen von zwanzig Optionen — zwei oder
  drei gute, mit Begruendung.

Kontext:
- System: {system}
- Arbeitsverzeichnis: {workspace}
- Aktuelle Zeit: {now}
{extra}"""


class Engine(ABC):
    """Ein Backend nimmt Text entgegen, streamt Ereignisse und liefert die Antwort."""

    def __init__(self, user_name: str = "Daniel") -> None:
        self.user_name = user_name

    @abstractmethod
    async def respond(self, user_text: str, emit: Emit) -> str:
        """Verarbeitet eine Eingabe und streamt Zwischenschritte ueber `emit`."""

    @abstractmethod
    def reset(self) -> None:
        """Verwirft den bisherigen Gespraechsverlauf."""

    @property
    @abstractmethod
    def label(self) -> str:
        """Kurzbeschreibung fuer die Oberflaeche."""

    def persona(self, extra: str = "") -> str:
        return PERSONA.format(
            user=self.user_name,
            system=f"{platform.system()} {platform.release()}",
            workspace=config.workspace,
            now=dt.datetime.now().strftime("%A, %d.%m.%Y %H:%M"),
            extra=f"\n{extra}" if extra else "",
        )
