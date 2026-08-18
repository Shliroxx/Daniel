# SANTIQ Brand Fonts

Beide Marken-Schriften des Santiq Design Systems als fertige, selbst hostbare Dateien.
Familiennamen exakt so, wie `santiq4.css` sie referenziert:

| Familie | Charakter | Herkunft (OFL 1.1) | Ordner |
|---|---|---|---|
| **SANTIQ Display** | Didone-Serif, hoher Strichkontrast, feine Haarstriche, optische Größe 48 pt — für H1–H4, Preise, Editorial-Headlines | Bodoni Moda | `santiq-display/` |
| **SANTIQ Text** | Humanistische Sans, ruhig im Fließtext, 200–900 | Source Sans 3 | `santiq-text/` |

`fonts.css` importiert beide und setzt `--font-display` / `--font-text`.
`specimen.html` zeigt beide im Markenlook (dunkel, Gold, Haarlinien).

## Je Familie enthalten

- `*-Variable.woff2` + `*-ItalicVariable.woff2` — variable Achse `wght`, aufrecht und kursiv
- `*-{Regular,Bold,Italic,BoldItalic}.{woff2,woff,ttf}` — statische Schnitte
- `*.css` — fertige `@font-face`-Regeln
- `OFL.txt` — Lizenztext (muss mitgeliefert werden)

Web-Dateien sind auf Latin + Latin Extended + Satz- und Währungszeichen subsettet.

## Einbinden

```css
@import url("./assets/fonts/fonts.css");

h1, h2, h3, h4 { font-family: var(--font-display); }
body           { font-family: var(--font-text); }
```

Beim Upload in ein Design-Tool die vier **statischen** Dateien je Familie als
Regular / Bold / Italic / Bold Italic zuordnen — Name-Tabellen, `macStyle`,
`fsSelection` und `usWeightClass` sind passend gesetzt, es entsteht also kein
Fake-Bold und kein Fake-Italic.

## Lizenz

Beide Familien stehen unter der SIL Open Font License 1.1 und sind umbenannte,
subsettete Ableitungen. Bodoni Moda trägt keinen Reserved Font Name, Source Sans
den RFN „Source“ — die Umbenennung ist damit lizenzkonform und im Fall von Source
Sans sogar erforderlich. Nutzung, auch kommerziell und öffentlich, ist erlaubt,
solange die jeweilige `OFL.txt` mitgeliefert wird und die Schriften nicht separat
verkauft werden.

Erzeugt mit fontTools: Instanziierung der Variable-Achsen (Display zusätzlich
`opsz=48`), neu geschriebene Name-Tabellen, Subsetting, WOFF2/WOFF-Kompression.
