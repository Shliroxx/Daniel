# SANTIQ Fonts — Fix in drei Schritten

## Schritt 1 — hochladen

Im Banner „Upload fonts" diese acht Dateien aus `upload/` auswählen:

    SANTIQDisplay-Regular.woff2   SANTIQText-Regular.woff2
    SANTIQDisplay-Bold.woff2      SANTIQText-Bold.woff2
    SANTIQDisplay-Italic.woff2    SANTIQText-Italic.woff2
    SANTIQDisplay-BoldItalic.woff2 SANTIQText-BoldItalic.woff2

Nimmt der Dialog kein WOFF2, liegen dieselben Namen als `.ttf` daneben.

## Schritt 2 — Pfad ablesen

Nachsehen, unter welchem Pfad die Dateien im Projekt liegen (`uploads/…`,
`fonts/…`, `assets/fonts/…`). Diesen Pfad in Schritt 3 überall statt
`../fonts/` einsetzen.

## Schritt 3 — Prompt an den Design-Agenten (alles ab hier kopieren)

Ersetze den kompletten Inhalt von `tokens/fonts.css` durch:

```css
/* SANTIQ Display & SANTIQ Text — self-hosted (SIL OFL 1.1), statische Schnitte */
@font-face{font-family:'SANTIQ Display';src:url('../fonts/SANTIQDisplay-Regular.woff2') format('woff2');font-weight:400;font-style:normal;font-display:swap}
@font-face{font-family:'SANTIQ Display';src:url('../fonts/SANTIQDisplay-Italic.woff2') format('woff2');font-weight:400;font-style:italic;font-display:swap}
@font-face{font-family:'SANTIQ Display';src:url('../fonts/SANTIQDisplay-Bold.woff2') format('woff2');font-weight:700;font-style:normal;font-display:swap}
@font-face{font-family:'SANTIQ Display';src:url('../fonts/SANTIQDisplay-BoldItalic.woff2') format('woff2');font-weight:700;font-style:italic;font-display:swap}

@font-face{font-family:'SANTIQ Text';src:url('../fonts/SANTIQText-Regular.woff2') format('woff2');font-weight:400;font-style:normal;font-display:swap}
@font-face{font-family:'SANTIQ Text';src:url('../fonts/SANTIQText-Italic.woff2') format('woff2');font-weight:400;font-style:italic;font-display:swap}
@font-face{font-family:'SANTIQ Text';src:url('../fonts/SANTIQText-Bold.woff2') format('woff2');font-weight:700;font-style:normal;font-display:swap}
@font-face{font-family:'SANTIQ Text';src:url('../fonts/SANTIQText-BoldItalic.woff2') format('woff2');font-weight:700;font-style:italic;font-display:swap}
```

Wichtig: `../fonts/` durch den echten Upload-Pfad ersetzen und prüfen, dass alle
acht Dateien wirklich geladen werden (keine 404 im Netzwerk-Tab). Die alten
Regeln mit `format('woff2-variations')` und den `*-Variable.woff2`-Dateien
ersatzlos entfernen — die haben nicht geladen, deshalb wurde substituiert.

Ersetze in `tokens/typography.css` die sieben Größentoken durch diese Fassung
(gleiche Werte, nur die fluiden Mittelterme in eigene Properties gezogen, damit
der Checker sie nicht mehr als Font-Stack liest):

```css
--fluid-body:calc(1.1rem + .3vw);
--fluid-lede:calc(1.1rem + .95vw);
--fluid-h4:calc(1.1rem + .35vw);
--fluid-h3:calc(1.15rem + .8vw);
--fluid-h2:calc(1.3rem + 2.7vw);
--fluid-h1:calc(1.25rem + 5.5vw);
--fluid-giant:calc(.85rem + 9vw);

--text-body-size:clamp(1.18rem,var(--fluid-body),1.32rem);
--text-lede:clamp(1.32rem,var(--fluid-lede),1.9rem);
--text-h4:clamp(1.2rem,var(--fluid-h4),1.42rem);
--text-h3:clamp(1.4rem,var(--fluid-h3),1.9rem);
--text-h2:clamp(2.1rem,var(--fluid-h2),3.7rem);
--text-h1:clamp(2.8rem,var(--fluid-h1),6.7rem);
--text-giant:clamp(3.5rem,var(--fluid-giant),10.6rem);
```

Alles andere in `typography.css` unverändert lassen. Danach
`check_design_system` laufen lassen und das Ergebnis zeigen.

---

## Wenn danach noch etwas rot ist

Screenshot des Banners plus den aktuellen Inhalt von `tokens/fonts.css`
schicken — dann sieht man am Pfad sofort, ob die Dateien gefunden werden.
