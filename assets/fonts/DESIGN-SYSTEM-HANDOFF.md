# Übergabe ans Design-System

## 1. Dateien in den „Upload fonts“-Dialog

Acht Dateien, vier je Familie (WOFF2; falls der Dialog nur TTF nimmt, liegen
dieselben Namen als `.ttf` daneben):

```
santiq-display/SANTIQDisplay-Regular.woff2
santiq-display/SANTIQDisplay-Bold.woff2
santiq-display/SANTIQDisplay-Italic.woff2
santiq-display/SANTIQDisplay-BoldItalic.woff2
santiq-text/SANTIQText-Regular.woff2
santiq-text/SANTIQText-Bold.woff2
santiq-text/SANTIQText-Italic.woff2
santiq-text/SANTIQText-BoldItalic.woff2
```

Optional zusätzlich die vier `*-Variable.woff2` / `*-ItalicVariable.woff2` — die
decken jedes Zwischengewicht ab (Display 400–900, Text 200–900).

## 2. Prompt an den Design-Agenten (kopieren)

> Die Marken-Schriften sind jetzt hochgeladen. Verdrahte in `tokens/fonts.css`
> beide Familien selbst gehostet und entferne alle Platzhalter:
>
> - `SANTIQ Display` — Dateien `SANTIQDisplay-{Regular,Bold,Italic,BoldItalic}.woff2`,
>   Gewichte 400 und 700, `normal` und `italic`. Fallback-Stack:
>   `Didot, Georgia, "Times New Roman", serif`. Nutzung: `--font-display` für
>   H1–H4, Preise, Editorial-Headlines.
> - `SANTIQ Text` — Dateien `SANTIQText-{Regular,Bold,Italic,BoldItalic}.woff2`,
>   Gewichte 400 und 700, `normal` und `italic`. Fallback-Stack:
>   `ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif`.
>   Nutzung: `--font-text` für Fließtext, UI, Formulare.
>
> Alle `@font-face`-Regeln mit `font-display: swap`. Kein CDN-Import mehr, keine
> Inter-/Caudex-Reste. Danach `check_design_system` laufen lassen.

## 3. Wenn der Checker weiter „Missing brand fonts“ meldet

Die dort gelisteten Einträge sind fluide Größentoken (`--text-body-size`,
`--text-lede`, `--text-h1` … mit `vw`-Anteil), keine Schriftdateien. Sie
verschwinden, sobald der Checker echte Fontmetriken hat; bleiben einzelne
stehen, sind sie informativ — an den Token ist nichts kaputt.
