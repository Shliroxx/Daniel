---
# Diese Werte gelten für alle Aufgaben, sofern eine Aufgabe nichts anderes sagt.
modell: sonnet
ausgabeordner: ~/Obsidian/jarvis/ergebnisse
sperrstunde: "07:00"     # ab dieser Uhrzeit werden keine neuen Versuche mehr gestartet
versuche: 4              # wie oft bei erschöpftem Kontingent erneut probiert wird
---

# Aufträge für heute Nacht

Starten mit:

```
python -m jarvis.orchestrator auftraege.md
python -m jarvis.orchestrator auftraege.md --trocken    # nur anzeigen, nichts tun
```

## Kurzaufgaben

Einzeilige Sachen schreibst du einfach als Checkbox. Jarvis hakt sie ab
(`[x]` erledigt, `[!]` fehlgeschlagen).

- [ ] Fasse alle Notizen aus dem Ordner Projekte/Website zu einer Übersicht zusammen
- [ ] Prüfe meine Aufgabenliste und markiere, was länger als zwei Wochen offen ist

## Aufgabe: Recherche zu einem Thema

- modell: opus
- start: 01:00
- ausgabe: ~/Obsidian/jarvis/ergebnisse/recherche.md

Recherchiere gründlich zu [THEMA]. Nutze Websuche und arbeite mindestens acht
brauchbare Quellen durch.

Der Bericht soll enthalten: Was ist der aktuelle Stand, wo sind die Streitpunkte,
welche Zahlen sind belastbar und welche nicht, und was folgt daraus praktisch.
Jede Behauptung mit Quelle. Wenn sich Quellen widersprechen, schreib das hin,
statt dich für eine Seite zu entscheiden.

Keine Zusammenfassung am Anfang und keine am Ende — der Text selbst ist das Ergebnis.
