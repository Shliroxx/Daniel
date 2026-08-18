# SANTIQ Text

Fertige, selbst hostbare Textschrift für das Design-System — Familienname exakt **`SANTIQ Text`**.

## Dateien

| Datei | Zweck |
|---|---|
| `SANTIQText-Variable.woff2` | Variable, aufrecht, Gewicht 200–900 (empfohlen fürs Web) |
| `SANTIQText-ItalicVariable.woff2` | Variable, kursiv, Gewicht 200–900 |
| `SANTIQText-Regular/Bold/Italic/BoldItalic.woff2` | statische Schnitte (WOFF2) |
| `SANTIQText-*.woff` | dieselben Schnitte als WOFF (alte Browser) |
| `SANTIQText-*.ttf` | Desktop-Installation / Upload in Design-Tools, die TTF verlangen |
| `santiq-text.css` | fertige `@font-face`-Regeln + `--font-text`-Token |
| `specimen.html` | Schriftmuster zum Gegenprüfen |
| `OFL.txt` | Lizenztext (muss mitgeliefert werden) |

Web-Dateien sind auf Latin + Latin Extended + Währungs-/Satzzeichen subsettet
(Regular ≈ 52 KB WOFF2); die TTFs enthalten den vollen Zeichensatz.

## Einbinden

```css
@import url("./assets/fonts/santiq-text/santiq-text.css");

body { font-family: var(--font-text); }
```

Beim Upload in ein Design-Tool die vier statischen Dateien als Regular / Bold /
Italic / Bold Italic zuordnen — die Namenstabellen sind bereits passend gesetzt
(`SANTIQ Text` + Regular/Bold/Italic/Bold Italic), Fake-Bold entsteht also nicht.

## Herkunft & Lizenz

SANTIQ Text ist eine umbenannte, subsettete Ableitung von **Source Sans 3**
(Adobe, SIL Open Font License 1.1). Source Sans trägt den Reserved Font Name
„Source“; die Umbenennung auf `SANTIQ Text` ist daher lizenzkonform und
erforderlich. Verwendung — auch kommerziell und öffentlich — ist erlaubt,
solange `OFL.txt` mitgeliefert wird und die Schrift nicht separat verkauft wird.

Erzeugt mit fontTools (Instanziierung der Variable-Achse, Namenstabellen,
Subsetting, WOFF2/WOFF-Kompression).
