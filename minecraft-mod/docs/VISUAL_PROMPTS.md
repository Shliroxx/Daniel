# KINGDOM OMNITRIX — Visual-Arbeitsaufträge (eigene Prompts)

made by SANTIQ · Stand 2026-10-02 · Vorgabe SANTIQ: „keine 0815-Mod, Items und Tools stark und grafisch gut, nicht
kleine Pixel, alles originalgetreu“. Jeder Auftrag unten ist so geschrieben, dass er ohne Rückfrage abgearbeitet und
im Spiel abgenommen werden kann. Reihenfolge = Priorität.

## Bestandsaufnahme (im Spiel geprüft)

| Bereich | Ist-Zustand | Score | Problem |
|---|---|---|---|
| Aliens (Heatblast, XLR8, Vierarm, Diamondhead) | 1:1 nach Vorlage, doppelte Dichte, Ego-Arme | 62–68 | Rückseiten abgeleitet, Feinschliff |
| Grey Matter | altes Würfelmodell | 30 | noch nicht nach Vorlage |
| Keyblades (Kingdom Key, Oathkeeper, Omega) | 16×16-Sprite, in der Hand flach extrudiert | 30 | wirkt wie Standard-Schwert, Pixel riesig |
| Waffen/Gadgets (Combuster, Omniwrench, Swingshot, Granate, Heli-Pack) | 16×16-Sprites | 30 | kein Modell, kein Volumen |
| Omnitrix | 16×16-Icon, nicht am Arm sichtbar | 25 | zentrales Objekt ohne 3D-Darstellung |
| Materialien/Verbrauchsitems (Bolts, Erze, Tränke, Paopu …) | 16×16, sauber | 45 | zu schlicht, wenig Glanz/Tiefe |
| Blöcke (Erze, Terminals, Schmiede, Bolt-Kiste) | 16×16 | 40 | Terminals wirken wie Vanilla-Blöcke |
| HUD (Status, Kommandomenü, Omnitrix, Gruppe) | eigenes Design | 55 | Menü groß, Omnitrix-Box verdeckt Ego-Hand |

---

## AUFTRAG 1 — KEYBLADES ALS ECHTE 3D-WAFFEN

**Ziel:** Kingdom Key, Oathkeeper und Omega Key sehen in der Hand aus wie in Kingdom Hearts — nicht wie ein
Minecraft-Schwert.

**Referenz (Original-Design, aus dem Gedächtnis der Spiele, nicht kopiert):**
- *Kingdom Key:* silberne runde Klinge, goldener abgerundeter Handschutz (großer Bogen um den Griff), schwarzer bzw.
  blauer Griff, Zahnbart als stilisierte Krone am Klingenende, Anhänger: Mickey-Silhouette (drei Kreise) an einer
  kurzen Kette am Knauf.
- *Oathkeeper:* weiße/silberne Klinge mit Sternen- und Herzmotiv, Handschutz aus geschwungenen Flügeln, Zahnbart als
  Stern, Anhänger: Stern (Paopu-Form).
- *Omega Key (Mod-eigen):* dunkle Klinge mit violett leuchtenden Kanten, Handschutz eckig/mechanisch (Ratchet-&-
  Clank-Einfluss), Zahnbart als Omnitrix-Sanduhr, Anhänger: kleines Omnitrix-Symbol.

**Technik:**
1. 3D-Handmodell als JSON-Item-Modell mit Quadern (Griff, Handschutz-Bogen aus mehreren gedrehten Quadern, Klinge,
   Zahnbart, Kette, Anhänger) — Länge ca. 1,5 Blöcke in der Hand, Textur 64×64 (hohe Pixeldichte).
2. Im Inventar weiter ein 2D-Icon, aber neu in **32×32** gezeichnet. Umschalten wie Dreizack/Fernrohr:
   Zusatzmodell `<name>_in_hand` laden (`ModelLoadingPlugin`), im `ItemRenderer` für Hand-/Kopf-Ansichten das
   3D-Modell nehmen, für GUI/Boden/Rahmen das Icon.
3. Display-Transformationen für Ego-Sicht rechts/links, Third-Person, Kopf, Boden so einstellen, dass die Klinge
   schräg nach oben/vorn zeigt wie in KH.
4. Generator `tools/generate_keyblades.py` (reproduzierbar, `--check` in der CI).

**Abnahme:** Screenshots Ego-Sicht, Third-Person (Beobachter), Inventar, Boden. Erkennbar „Kingdom Key“ auf den
ersten Blick; keine sichtbare Sprite-Extrusion; Pixel nicht größer als 1/4 Klingenbreite.

## AUFTRAG 2 — OMNITRIX ALS 3D-GERÄT

**Ziel:** Das Omnitrix sitzt sichtbar am linken Handgelenk (Ego- und Third-Person), wie in der Serie und bei Alien
Evolution: schwarzes Armband, graue Fassung, grünes Ziffernblatt mit Sanduhr.
**Technik:** Feature-Renderer am Spielermodell (Arm-Knochen), eigenes Würfelmodell 64×64; Ego-Sicht über den
Arm-Render. Item-Icon 32×32 neu. Beim Aktivieren (Rad offen) leuchtet das Ziffernblatt, beim Abklingen rot.
**Abnahme:** Ego-Sicht mit leerer Hand, Third-Person von vorn/seitlich, Inventar.

## AUFTRAG 3 — WAFFEN UND GADGETS ALS 3D-MODELLE (Ratchet & Clank)

**Ziel:** Combuster (Blaster mit Tank und Mündung), Omniwrench (großer Schraubenschlüssel, gelb/grau), Swingshot
(Handgerät mit Greifkopf), Fusionsgranate (Kugel mit Leuchtring), Heli-Pack (Rucksack mit Rotoren, auf dem Rücken
sichtbar).
**Technik:** wie Auftrag 1 (3D in der Hand, 32×32-Icon im Inventar), Heli-Pack als Rückenmodell (Feature-Renderer).
**Abnahme:** Ego-Sicht, Third-Person, Inventar je Gerät.

## AUFTRAG 4 — ITEMS IN 32×32, ORIGINALGETREU

**Ziel:** Alle Material- und Verbrauchsitems in doppelter Auflösung mit Glanzkanten, 3–5 Tonstufen und dunkler
Kontur: Bolts (sechseckige Mutter, gold), Mythril/Orichalcum/Raritanium (Kristalle in KH-/R&C-Farben),
Hi-Potion (grüne KH-Flasche), Paopu-Frucht (gelber Stern), Herz (KH-Herz), DNA-Probe, Zauberkristall,
Questbuch, Nefarious-Kommunikator.
**Technik:** `tools/generate_textures.py` auf 32×32 umstellen (Item-Modelle bleiben `item/generated`).
**Abnahme:** Inventar-Screenshot, Vergleich alt/neu.

## AUFTRAG 5 — BLÖCKE IN 32×32

**Ziel:** Erze mit eingebetteten Kristallen statt Flecken, Keyblade-Schmiede (Amboss + Herzsymbol), Waffen-Terminal
und Arena-Terminal mit Bildschirm (Leuchtmaske), Bolt-Kiste im R&C-Stil.
**Abnahme:** gesetzte Blöcke im Spiel bei Tag und Nacht.

## AUFTRAG 6 — HUD-FEINSCHLIFF

**Ziel:** KH-Kommandomenü kompakter (skaliert 0,85, halbtransparent), Omnitrix-Anzeige nicht über der Ego-Hand
(oben rechts oder über der Hotbar), Lebens-/MP-Anzeige im KH-Stil (Bogen), Gruppenanzeige schlanker.
**Abnahme:** Screenshot Spielbildschirm verwandelt und unverwandelt, 854×480 und 1280×720.

## AUFTRAG 7 — GREY MATTER NACH VORLAGE

Wie Heatblast/XLR8/Vierarm/Diamondhead (Vorlage `GalvanOS.png`, Galvan ist dort sehr nah gerendert — Maßstab über
Augen/Anzugbreite bestimmen).
