## Aufgabe: Code durchsehen

- modell: opus
- ausgabe: ~/Obsidian/jarvis/ergebnisse/code-review.md

Sieh dir das Projekt unter **[PFAD]** an.

**Worauf du achten sollst**

Echte Fehler zuerst: Dinge, die zu falschem Verhalten, Datenverlust oder Abstürzen
führen. Danach Sicherheitslücken. Danach Stellen, an denen der Code das Falsche
tut, obwohl er läuft.

**Melde alles, auch Unsicheres.** Filtere nicht nach Wichtigkeit — schreib zu
jedem Fund dazu, wie sicher du dir bist und wie schwer es wiegt. Ich sortiere
selbst. Ein Fund, den ich am Ende wegwerfe, ist besser als ein Bug, den du
stillschweigend nicht erwähnt hast, weil er dir zu klein vorkam.

Reine Stilfragen und Namensgeschmack lässt du weg.

**Format pro Fund**

Datei und Zeile. Was ist das Problem — in einem Satz. Wann geht es konkret schief:
welche Eingabe, welcher Zustand, welches Ergebnis. Dann dein Vorschlag.

Wenn du nichts Ernstes findest, schreib das hin. Erfinde keine Funde, um die Liste
zu füllen.
