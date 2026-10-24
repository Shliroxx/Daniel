# KINGDOM OMNITRIX — DEVELOPMENT STATUS

made by SANTIQ · Fortsetzungs-Datei für jeden Entwicklungszyklus.
**Ablauf zu Beginn jedes Zyklus:** diese Datei lesen → `git status` → `./gradlew build` → wichtigste offene Aufgabe aus
„NEXT TASK“ fortsetzen. Nach jeder größeren Etappe diese Datei aktualisieren.

Statusbegriffe: IMPLEMENTED · PROTOTYPE · PLACEHOLDER · TODO · POLISH NEEDED · VISUAL REWORK · ANIMATION REWORK ·
TEXTURE REWORK · MODEL REWORK · VFX REWORK · FINAL POLISH. Keine Selbstbewertung als „AAA“ — die Zahlen unten sind
interne Prioritäts-Scores (0–100), ehrlich nach Screenshots vergeben.

---

## CURRENT VERSION
`0.11.0-alpha` (Minecraft 1.21.1, Fabric Loader 0.19.3, Fabric API 0.116.16, GeckoLib 4.9.3, playerAnimator 2.0.4).
Kein Jar-Release, bis alles überarbeitet ist (Entscheidung SANTIQ).

## CURRENT PHASE
**Überarbeitung aller offenen Punkte (Auftrag SANTIQ 2026-10-24, ohne Sounds).** Erledigt: alte Ausrüstung als
GeckoLib-3D, NPCs neu, zweihändige Gewehrhaltung, Alien-/Omnitrix-Prüfung, Texturen, Traverse Town aus eigenen
Blöcken, Leistung gemessen, dieses Dokument. Offen: Inhalte (siehe NEXT TASK).

## CURRENT PRIORITY
1. Omnitrix-Upgrades (Boost-Module + Stufen, freigegeben)
2. Ratchet-Teil: Gadgetron-Shop (R3), Clank-Rucksack mit Heli/Thruster/Gleiter (R4)
3. Alien-Heimatwelten mit wilden Arten fertigstellen, Welten-Ausbau, Ben-10-Schurken
4. Sounds mit echtem Gehör abstimmen (SANTIQ, ausdrücklich zurückgestellt)

## AKTUELLER STAND JE BEREICH (2026-10-24, ehrlich nach Screenshots)
| Bereich | Stand | Offen |
|---|---|---|
| Aliens (24 + Evo/Ultimate) | 1:1-Import aus Alien Evolution, Uniformen, Fähigkeiten, Verwandlung | — |
| Omnitrix | Zustände, Rad mit 3D-Hologramm, OS-Meldungen, Hitze, Warnung (Ring/Zeit rot, Abzeichen blinkt, Piepen) | Upgrades |
| Endgame-Rüstungen (6) | GeckoLib-3D mit Leuchtschicht, Schild/Flug/Boni, Module im Anzug-Konfigurator | — |
| Endgame-Waffen (6) | GeckoLib-3D, animiert, Leuchtschicht, Haltung geprüft (Hand, Ständer, Spieler, 2. Client) | — |
| Ältere Ausrüstung (Keyblades, OmniWrench, Combuster, Swingshot, Granate, Heli-Pack) | GeckoLib-3D, animiert, Inventarbilder aus dem 3D-Modell | — |
| Schusswaffen dritte Person | zweihändig im Anschlag (Armbrust-Haltung) | Rüstungsständer zeigen sie senkrecht |
| NPCs (Yen Sid, Max, Clank) | neu modelliert (Robe/Bart, Hawaiihemd, Roboter mit Leuchtaugen) | — |
| Herzlose, Boss | nach KH gebaut, Boss mit Leuchtmaske | — |
| Welt-Blöcke | 16x16-Pixelstil (bewusst), Technik-Blöcke 128x128 | — |
| Traverse Town | eigene Blöcke (Putz, Dachziegel, Pflaster, Fliesen, Fachwerk), Uhrturm, Arena | alte Welten behalten ihre Stadt |
| Sounds | synthetisch erzeugt | nie mit Gehör geprüft |

---

## VERLAUF (historische Abschnitte; Stand jeweils im Titel, „Offen“-Notizen dort sind überholt)

## STILVORGABE ALIENS (Entscheidung SANTIQ, 2026-10-02, endgültig)
**1:1-Nachbau nach Alien Evolution** (Erlaubnis des Autors laut SANTIQ, Fanprojekt; siehe
`tools/reference/README.md`). Technik: doppelte Texturdichte (PNG 2× so groß wie in der .geo.json angegeben),
Vorderseiten aus der Vorlage abgetastet, Seiten/Rücken mit der Vorlagen-Palette, Farben fürs Spiel-Licht
angehoben, Proportionen der Vorlage (Heatblast Kopf : Rumpf : Beine = 8 : 16 : 14, Darstellungsgröße 0,84 über
`assets/kingdomomnitrix/alien_render/<alien>.json`). Stand: **Heatblast fertig (Rework 1)**, XLR8, Vierarm,
Diamondhead, Grey Matter folgen nach demselben Verfahren.

**Würfelstil auf Alien-Evolution-Niveau.** Der zwischenzeitliche Cartoon-Renderer (runde Netze) wurde verworfen
und entfernt. Ziel: detaillierte Würfelmodelle (viele Teile, schräge Teile, Brocken, Krallen, Zungen) mit
handgemalt wirkender Schattierung (Lichtverlauf, Glanzkante, Kontaktschatten, Pixel-Cluster in drei Tönen).

Ziel: Qualität und Wirkung wie **Alien Evolution** — aber eigene Modelle und Texturen (AlienEvo ist „All Rights
Reserved“, Kopieren nicht erlaubt). Abgeleitete Regeln aus den öffentlichen Vorschaubildern:
1. **Serien-Treue zuerst:** jedes Alien in seinem bekannten Serien-Outfit (Farbblöcke, Anzug, Omnitrix-Symbol
   gut sichtbar auf der Brust bzw. Schulter).
2. **Saubere Flächen statt Rauschen:** flache Farbflächen mit leichter Schattierung, klare dunkle Konturlinien an
   Kanten und Nähten, hoher Kontrast — kein Pixel-Rauschen.
3. **Proportionen nah am Spielerskelett,** aber mit markanter Silhouette pro Alien (Vierarm massiv, Diamondhead
   kantige Kristalle, Grey Matter klein).
4. **Leuchten gezielt:** Augen, Omnitrix, Energie (Feuer, Kristall-Kanten) über Leuchtmaske.
5. **Omnitrix als 3D-Gerät** am Handgelenk in der Ego-Sicht (Ziffernblatt mit Sanduhr und Alien-Silhouette).
Design-Referenz (von SANTIQ vorgegeben): die Original-Serien-Varianten im Alien-Evolution-Wiki
(alienevolution.wiki.gg). Umgesetzt mit eigener Geometrie und eigenen Pixeln (`tools/generate_alien_models.py`,
Serien-Design-Maler: `lava`, `fur`, `crystal`, `split`, `panel`, `shirt`, `clean:#…`, Gesichter + Omnitrix-Logo).
Stand 2026-10-02: alle 5 Aliens im Serien-Design (`docs/screenshots/aliens_serien_design.png`).

## HEATBLAST — 1:1 REFERENCE REWORK (Pass 2, 2026-10-02)

**Ausgangslage (Analyse):** Heatblast hatte bereits ein eigenes GeckoLib-Modell (keine Spieler-Skin), aber mit
geschätzten Proportionen, zu kleinem Omnitrix-Logo-Verhältnis, Flammen als Platzhalter-Stapel, Seitenflächen aus
generiertem Muster und normalen Spielerarmen in der Ego-Sicht.

**Was wurde geändert**
- *Silhouette/Proportionen* direkt aus der Vorlage vermessen: Kopf 9³ (mit Seitenflammen links 4, rechts 3 hoch),
  Rumpf 9×15×5, Kragen 10×3×6, Oberarm 5×10×5, Faust 6×10×6 (Arme 10° abgespreizt), Beine 4×12 mit Lücke,
  große Füße 5×2×6, Kopfflamme 6 Stufen aus der Silhouette der Vorlage gebaut (`flame_from_silhouette`).
- *Textur:* alle Flächen aus Vorlage-Pixeln (Vorderseite 1:1, Rückseite gespiegelt, Seiten aus Randspalten),
  doppelte Texturdichte, Farbhierarchie Dunkelrot → Rot → Orange → Gelb → Hellgelb aus der Vorlagen-Palette,
  für das Spiel-Licht angehoben.
- *Gesicht:* 16×16 aus der Vorlage (V-Brauen, Augen, Nase, Mund mit Zunge).
- *Omnitrix:* eigener Würfel 3×3, Rahmen schwarz, weiße Sanduhr, Position/Größe wie Vorlage (vorher zu groß).
- *Leuchten:* gelbe/orange Flächen über Leuchtmaske; neue Leuchtschicht mit gleichmäßiger Helligkeit von allen
  Seiten (`AlienBodyRenderer.withEvenGlow`) — vorher wurde der Flammenkopf von hinten khakifarben.
- *Ego-Sicht:* Arme verwandelter Spieler zeigen die Alien-Arme (`AlienArms` + Mixin in `renderArm`,
  Textur `heatblast_arms.png` im Skin-Layout, doppelte Dichte).
- *VFX:* kleine Flammen an Kopfflamme und abwechselnd an den Fäusten, sparsam, nicht in der eigenen Ego-Sicht am
  Kopf (`AlienAmbientVfx`).
- *Werkzeug:* `tools/sample_reference.py` (Rechteck- und Umriss-Abtastung, auch schräge Arme), CI prüft die
  Abtastung (`--check`).

**Geänderte/neue Dateien:** `tools/generate_alien_models.py`, `tools/sample_reference.py`,
`tools/reference/heatblast/*.png`, `tools/reference/source/heatblast.png`, `tools/reference/README.md`,
`assets/.../geo|animations|textures/entity/alien/heatblast*`, `heatblast_arms.png`, `alien_render/heatblast.json`,
`client/render/alien/AlienBodyRenderer.java`, `AlienArms.java`, `AlienBodyRenderers.java`,
`client/mixin/PlayerEntityRendererMixin.java`, `client/vfx/AlienAmbientVfx.java`, CI-Workflow.

**Ersetzte Assets:** komplette Heatblast-Geometrie, -Textur, -Leuchtmaske; Platzhalter-Flammenstapel entfernt.

**Behobene Probleme:** khakifarbener Kopf in Seiten-/Rückansicht; Omnitrix zu groß; Ego-Sicht mit Spielerarmen;
leere Leuchtmaske ließ GeckoLib abstürzen; Matrix-Stapel nach Render-Absturz; Kopf-Partikel zu groß.

**Noch nicht perfekt**
- Rücken/Seiten sind aus der Vorderseite abgeleitet (die Vorlage zeigt nur vorn) — echte Rückseite fehlt.
- Die Vorlage ist leicht von oben gerendert; Kopf/Flamme wirken dort durch die Perspektive größer.
- Ego-Sicht nutzt die Armform des Spielermodells (4 breit), nicht die breiten Fäuste des Alien-Modells.
- Angriffs-/Lauf-Animation nur kurz geprüft; Hitzeflimmern nicht umgesetzt (in Vanilla-Shadern nicht sinnvoll).

**Nächster konkreter Visual-Polish-Schritt:** ~~XLR8~~ (erledigt, siehe unten), dann Vierarm, Diamondhead,
Grey Matter nach demselben Verfahren.

## DIAMONDHEAD — 1:1 REFERENCE REWORK (2026-10-02)
- Vorlage `tools/reference/source/diamondhead.png` (PetrosapienOS), ~21 px/Einheit, 13 Teile abgetastet; vom Kinn
  verdeckte Brust und das Logo ausgefüllt.
- Geometrie nach Maß: Kristallkopf 7 mit Kamm, schräge Schulterspitzen 3×7×3, Anzug 8×16×5 (halb schwarz/halb weiß),
  Schulterkristalle 9×10×8, Kristall-Unterarme 7×12×7 (gestuft), Beine rechts schwarz / links weiß, Ego-Arme.
- BEHOBEN (betraf alle Aliens mit Stil „normal“): Der Textur-Maler übersprang gespiegelte Würfel — Überbleibsel der
  alten Bauweise mit geteilter UV. Bei Diamondhead war dadurch die ganze linke Körperseite unsichtbar. Jetzt wird
  nur übersprungen, wenn die UV wirklich schon bemalt ist.
- Offen: Grün im Spiel etwas kräftiger als das Pastell der Vorlage (Farbanhebung ist global); Schulterspitzen
  könnten noch höher stehen.

## VIERARM — 1:1 REFERENCE REWORK (2026-10-02)
- BEHOBEN (Hinweis SANTIQ): Das untere Armpaar zeigte nur Fäuste — Ober-/Unterarm lagen hinter Rumpf und oberen
  Armen. Jetzt hängen die inneren Arme wie in der Vorlage vor den Rumpfkanten.
- Vorlage `tools/reference/source/four_arms.png` (TetramandOS, Kopf oben angeschnitten), ~21,3 px/Einheit,
  14 Teile abgetastet (`tools/sample_reference.py four_arms`); vom inneren Armpaar verdeckte Rumpfkanten mit der
  Hemdfarbe aufgefüllt.
- Geometrie nach Maß: Kopf 6 (vier gelbe, leuchtende Augen), Hemd-Oberkörper 12×13×6 mit schwarzem Mittelstreifen,
  Hose 10×4, Beine 5×12, rote Füße, oberes Armpaar mit weißem Ärmel 6×7 + Fellarm + Faust (10° abgespreizt),
  unteres Armpaar tiefer und weiter innen, Fellzacken an allen Armen (zottelige Silhouette der Vorlage).
- Ego-Arme `four_arms_arms.png` (Ärmel + Fellarm). Darstellungsgröße 0,82 (zusätzlich Alien-Skalierung 1,4).
- Offen: Arme in der Vorlage etwas länger (Fäuste tiefer); Seiten des Hemds grau statt weiß (Schattierung);
  Kopf in der Vorlage angeschnitten — Oberseite frei gestaltet.

## XLR8 — 1:1 REFERENCE REWORK (2026-10-02)
- Vorlage `tools/reference/source/xlr8.png` (KineceleranOS) vermessen (~24,5 px/Einheit), 13 Teile abgetastet
  (`tools/sample_reference.py xlr8`): Helm-Front, Rumpf (Logo und überdeckende Armkante ausgefüllt),
  Schulterpolster, Arm, Krallenhand, Oberschenkel, Schienbeine, Krallenfüße, Ego-Arm.
- Geometrie nach Maß: Kopf 8, Rumpf 10×15×5, Schulterpolster 5×5×6, Oberarm 4×6, Unterarm 4×5 mit heller Flosse,
  Hand 5×4 mit Krallen, Oberschenkel 5×8, Schienbein 4×6, Fuß 6×3×7 mit Ferse, Schwanz (schwarz/türkis).
- Rücken des Rumpfs aus den Seitenspalten statt gespiegelt (`|plainback`) — sonst trug XLR8 das Brustpaneel hinten.
- Transparente Vorlagenpixel werden mit der Durchschnittsfarbe der jeweiligen Vorlage gefüllt (vorher fest
  Heatblast-Bordeaux → rote Kanten auf XLR8s Schulterpolstern).
- Grüne Augen leuchten; Ego-Sicht zeigt XLR8-Arme (`xlr8_arms.png`).
- Offen: Schwanzseiten generisch gestreift (Vorlage zeigt ihn nur teilweise), Ego-Hand liegt teils unter dem
  Omnitrix-HUD (HUD-Position prüfen), Sprint-Animation nach dem Umbau nicht erneut gefilmt.


## ITEMS / TOOLS / HUD — BESTANDSAUFNAHME + AUFTRÄGE (2026-10-02)
Prüfung im Spiel (Inventar, Ego-Sicht, Third-Person): alle 41 Item-/Block-Texturen waren 16×16-Sprites, Waffen ohne
Volumen, Omnitrix nicht am Arm, Omnitrix-HUD überdeckt die Ego-Hand. Eigene Arbeitsaufträge mit Abnahmekriterien:
**`docs/VISUAL_PROMPTS.md`** (Auftrag 1–7, Reihenfolge = Priorität).

**Auftrag 1 — Keyblades 3D: IMPLEMENTED (Pass 1)**
- `tools/generate_item_models.py` (neu, `--check` in CI): Waffen aus Quadern, Textur automatisch gepackt mit
  4 Bildpunkten pro Modell-Pixel (Klinge 6 px breit statt 1–2), pro Material schattiert (Kontur, Glanzkante,
  Rundungsverlauf, Griffwicklung, Federn, Nieten, Leuchtkern, Omnitrix-Zifferblatt).
- Kingdom Key: silberne Klinge, goldener abgerundeter Handschutz, dunkler Wickelgriff, Kronen-Zahnbart (3 Zacken),
  Kette + Anhänger (Kopf mit zwei Ohren). Oathkeeper: weiß, Engelsflügel-Handschutz, Stern-Spitze, Feder-Zahnbart,
  Glücksstern-Anhänger. Omega Key: dunkle Mechanik, violette Leuchtkanten, Omnitrix-Kern, Sanduhr-Zahnbart.
- Ein Modell für alle Ansichten (Hand, Inventar 3D, Boden, Rahmen); Lage aus vanilla „handheld“ hergeleitet und im
  Spiel nachjustiert. Screenshot: `docs/screenshots/keyblades_3d.png`.
- Offen: Zahnbart-Form gegen Original-Artwork feiner; Third-Person am Spieler aus Beobachter-Sicht prüfen.

**Auftrag 3 — Geräte 3D: IMPLEMENTED (Pass 1, ohne Heli-Pack)**
- Gleicher Generator: Omniwrench 8000 (blauer Schaft, gelbe Ringe, Silberkopf mit ungleichen Backen), Combuster
  (orange Gehäuse, Brennstofftank mit Glutfenster, Heizspirale, Mündungsring), Swingshot (Seiltrommel, Greifer mit
  drei Klauen, türkise Paneele), Fusionsgranate (rote Kugel, blauer Leuchtring, Zünder + Hebel), Omnitrix-Item
  (Armband, Gehäuse, grünes Sanduhr-Zifferblatt, Seitentasten).
- Gewehre eigene Lage (Ego-Sicht Lauf nach vorn, Griff unten), kleine Geräte wie vanilla „generated“.
- Offen: Heli-Pack (Rückenmodell), Omnitrix am Handgelenk (Auftrag 2).

**Auftrag 6 — HUD: teilweise**
- Omnitrix- und Waffen-Anzeige von unten rechts nach oben rechts (unter den Effekt-Symbolen), Waffen-Anzeige
  stapelt sich automatisch unter der Omnitrix-Anzeige (Höhe bereit/verwandelt) → Ego-Hand und Waffe frei.
  Screenshot: `docs/screenshots/gadgets_3d.png`.
- Offen: Kommandomenü kompakter, KH-Lebensanzeige.

**Auftrag 2 — Omnitrix 3D am Handgelenk: IMPLEMENTED (Pass 1)**
- Drei Modellteile aus `tools/generate_item_models.py` (`models/omnitrix/wrist_{base,core,glow}.json`, 8 Bildpunkte
  pro Modell-Pixel): Armband mit Randwulsten, Gehäuse mit Seitenwangen und -tasten, graue Fassung, vier grüne
  Eck-Leuchten, Kern mit Sanduhr-Zifferblatt; Leuchtschicht additiv (`RenderLayer.getEyes`) und lichtunabhängig.
- Third-Person: Feature-Renderer am linken Arm aller Spieler, die ein Omnitrix tragen (eigenes: Inventar; fremde:
  wer es je benutzt hat), schmale Arme (Alex) berücksichtigt; nicht sichtbar, solange verwandelt.
- Ego-Sicht (`HeldItemRendererMixin`): Alien-Rad offen → linker Arm hebt sich ins Bild, Zifferblatt zur Kamera,
  Kern fährt heraus und leuchtet heller; Auswahl → Schlag aufs Zifferblatt (Kern runter, Ruck, Blitz). Das Rad
  zeichnet ohne Unschärfe, damit der Arm sichtbar bleibt. Haltung in `assets/.../omnitrix/first_person.json`
  (F3+T lädt neu). Screenshot: `docs/screenshots/omnitrix_3d.png`.
- Offen: Alien-Silhouette als Hologramm über dem Zifferblatt beim Durchblättern, Timeout-Warnung (rot blinkend),
  Kern-Hub für fremde Spieler (nur lokal bekannt).

## OMNIVERSE-OMNITRIX-SYSTEM (Vorgabe SANTIQ 2026-10-02, Pass 1 IMPLEMENTED)
Das Omnitrix ist das Interface: Arm heben → Gerät öffnet sich → Hologramm-Scheibe klappt aus dem Zifferblatt →
drehen → bestätigen → Schlag → Verwandlung. Referenzbild von SANTIQ (Omniverse): grüne, durchscheinende Scheibe in
Segmenten mit Alien-Silhouetten um das Zifferblatt, gewähltes Segment oben hell; Zifferblatt grün mit schwarzem X.
- **Zustandsmaschine** `alien/OmnitrixPhase` + `client/render/omnitrix/OmnitrixController`: IDLE, EQUIPPED,
  ACTIVATING, OPENING, SELECTING, ROTATING, ALIEN_SELECTED, CONFIRMING, IMPACT, TRANSFORMATION, ACTIVE_ALIEN,
  COOLDOWN, REVERT — jeder Zustand mit eigener Arm-/Kern-/Scheiben-/Licht-/Klang-Lage, weich überblendet.
- **Auswahlscheibe** `OmnitrixDisc`: hängt am Gerät (Third- und Ego-Sicht), klappt mit Überschwingen auf, dreht
  physisch (gedämpfte Feder), Segmentzahl = Roster (nichts hartcodiert), Silhouetten aus den **echten Spielmodellen**
  (GeckoLib, flach projiziert), gewähltes Segment hell mit heller Silhouette, gesperrt = dunkle Silhouette + „DNA
  fehlt“; darüber dreht sich das **3D-Hologramm des gewählten Aliens** (`AlienHologram`: dasselbe Modell wie der
  Spielkörper, eingefärbt + additiver Leuchtpass). Bestätigen: Segmente hellen auf, Hologramm pulsiert.
- **Ego-Sicht**: linker Arm wird direkt so gesetzt, dass das Zifferblatt frontal zur Kamera zeigt (Arm waagerecht
  von links); vanilla-Hände und Mod-HUD blenden aus, solange das Omnitrix gehoben ist. Haltung in
  `assets/.../omnitrix/first_person.json` (F3+T).
- **Eingabe** `OmnitrixScreen` (kein Menü-Look, keine Unschärfe): Mausrad/Ziehen/A-D/Pfeile drehen, Klick/Enter/
  Leertaste/G bestätigen, 1–9 direkt, Rechtsklick/Esc schließen. Verwandelt: Bestätigen = zurückverwandeln.
- **Pipeline** Auswahl → `AlienDefinition` (JSON) → `TransformationManager` (Server) → `AlienBodyRenderers`
  (Modell/Animation) → VFX: neue Aliens erscheinen ohne Code-Änderung im Rad.
- **Mehrspieler**: sichtbare Zustände (Arm hoch, offen, Energieaufbau, Schlag) gehen per `OmnitrixPhasePayload` an
  den Server und als `OmnitrixPhaseSyncPayload` an Spieler in Sichtweite; Third-Person-Arm (`PlayerEntityModelMixin`)
  und Scheibe (ohne Roster) für andere sichtbar. Rad-Inhalt bleibt clientseitig.
- Screenshots: `docs/screenshots/omnitrix_omniverse.png`, `omnitrix_omniverse_ablauf.png`, `omnitrix_omniverse_third_person.png`.
- Offen: Kamera-Neigung beim Heben, eigene Klänge pro Zustand (nutzt vorhandene Omnitrix-Sounds), Scheibe in
  Third-Person zur Weltoberseite ausrichten, Gehäuse noch näher an Omniverse (seitliche Flossen), rote Abklingzeit.

## ALIEN-EVOLUTION-VERGLEICH + UNIFORMEN (2026-10-02)
- AlienEvo 1.1.3 (Fabric 1.20.1, Palladium-Addon) lokal entpackt (nicht im Repo); Start in der Cloud scheitert am
  Proxy (Mojang-Downloads). Vergleich stattdessen mit `tools/render_geo.py` (Offline-Renderer fuer GeckoLib-Modelle)
  und nachgebauter Ingame-Faerbung (Palladium-Color-Transformer + Codex-Paletten).
- Erkenntnisse: AE-Aliens mit Tier-Anatomie (XLR8 Raptor, digitigrade Beine, vorgebeugt), viele gedrehte kleine
  Wuerfel, 64x64-Texturen in Ebenen (Haut/Uniform/Glow), 8-stufiges Glut-Leuchten, eigene Ego-Arm-Modelle, drei
  Uniformen je Alien (default/prototype/10k). Unsere bisherigen Aliens entsprechen etwa „prototype“.
- **Uniformen (Entscheidung SANTIQ: alle drei waehlbar):** `classic` (Original-Serie), `evo` (AE-eigener Look),
  `ultimate`. Generator: Farbrollen („role:SKIN“) + Palette je Uniform → `<alien>[_<uniform>].png`, Arme, Leuchtmaske;
  `alien_render/<alien>.json` listet die Uniformen. Spiel: `AlienUniforms` (Attachment, an alle synchronisiert),
  `SetUniformPayload`, Umschalten im Omnitrix mit **U**, Renderer/Ego-Arme nehmen die Uniform-Textur.
- **XLR8 neu (Raptor):** gekippter Rumpf aus gedrehten Teilen, spitzer Helm mit Visier, 4-gliedriger Schwanz mit
  Ringen, angewinkelte Krallenarme, digitigrade Beine; drei Uniformen. Screenshots `docs/screenshots/xlr8_uniformen*.png`.
- **Alle 5 Aliens neu nach AE-Anatomie mit je 3 Uniformen:** Vierarm (breite Brust, Nackenflosse, Armbaender, X-Gurte
  bei evo, 1,3-fach), Diamondhead (Rautenkopf, Rueckenstacheln, Schulterkristalle, Anzug in zwei Haelften, 1,15-fach),
  Heatblast (schlank, Rautenkopf mit Maske, Glutrisse als Linien, prozedurale Flamme, ultimate = blaues Feuer),
  Grey Matter (Froschaugen, grosse Haende, lange Fuesse, 0,45-fach). Neue Materialien `lava:` und `flame:` mit
  Uniform-Farben. Uebersicht `docs/screenshots/aliens_uniformen_uebersicht.png`.
- Naechste: Texturen mit Tonstufen statt Flachfarben (classic-Flaechen wirken noch flach), Glut-Animation,
  Ego-Arm-Modelle, Faehigkeits-VFX (Kristallsaeulen, Bodenschlag), Animationen an die neue Anatomie anpassen.

## ALIEN-EVOLUTION-IMPORT 1:1 (2026-10-02)
- SANTIQ-Feedback auf die Nachbauten: Arme nicht verbunden, Gesicht/Koerper passen nicht, Heatblast nicht erkennbar →
  Entscheidung: AE-Modelle 1:1 uebernehmen (Erlaubnis laut SANTIQ, Credits in `CREDITS.md`).
- **`tools/import_alienevo.py --jar <AlienEvo.jar>`**: Geometrie je Uniform (classic = AE prototype, evo = default,
  ultimate = 10k), Knochen auf unsere Animations-Namen abgebildet (body/head/…_forearm/…_shin/tail_N/…_lower_arm),
  Faehigkeits-Requisiten (Schwert, Schild, Kugel) entfernt, Omnitrix-Abzeichen (prototype) mit gruener Leuchtfarbe
  eingebaut, Texturen aus den AE-Ebenen mit Codex-Standardpaletten eingefaerbt (inkl. `_ext`-Paletten), Glow-Ebene
  zusaetzlich als Leuchtmaske, Ego-Arme aus den Arm-Wuerfeln, Groesse wie AE (Heatblast/XLR8 1,1 · Vierarm 2,0 ·
  Diamondhead 1,35 · Grey Matter 0,25). CI prueft mit `--check` nur die Vollstaendigkeit (Jar nicht im Repo).
- Renderer: `alien_render/<alien>.json` `"uniform_models": true` → `<alien>_<uniform>.geo.json` je Uniform.
- `generate_alien_models.py` schreibt die fuenf importierten Aliens nicht mehr (nur noch mit `--legacy`).
- `render_geo.py`: Drehrichtung y/z an GeckoLib angeglichen (gespiegelte x-Achse) — vorher standen gedrehte Teile
  (z. B. Diamondhead-Schulterkristalle) falsch ab.
- Im Spiel geprueft: alle 5 Aliens classic (`docs/screenshots/ae_import_alle_aliens.png`), Heatblast classic/evo/
  ultimate per Omnitrix + U (`docs/screenshots/ae_import_heatblast_uniformen.png`), keine Fehler im Log.
- **Bewegung wie AE:** AE animiert die Aliens ueber die Spielerpose (Palladium haengt die Ebenen an die Spielerteile)
  plus kleine Zusatzanimationen. Jetzt genauso: `AlienPose` berechnet die Vanilla-Pose (Gliedmaßen-Schwung, Blick,
  Schlag, Schleichen) und setzt sie auf head/body/Arme/Beine (Vierarm: auch das untere Armpaar); Schwung-Daempfung aus
  den AE-Skripten (`alien_render`: `arm_swing`/`leg_swing`, Heatblast 0,8/0,6, Diamondhead + Grey Matter 0,6/0,6).
  Hauptknochen haengen wie in AE einzeln an root. Unsere generierten Lauf-/Idle-/Schlag-Animationen sind fuer die
  importierten Aliens weg; XLR8 spielt AEs Schwanz-Animation, Verwandeln/Zurueckverwandeln skaliert nur root.
  Im Spiel geprueft: Stehen, Laufen, Schleichen, Schlag (`docs/screenshots/ae_pose_heatblast.png`).
- **Verwandlungen verbessert (SANTIQ: beides, classic bleibt Standard, Optik 1:1 / Effekte besser):**
  - `TransformBubble`: AEs „transform bubble“ (rotierende Blitze mit Leuchtsaum) als Welt-Effekt fuer alle Spieler —
    18 Blitze aus der Brust, Leuchthuelle, Bodenring beim Aufprall; gruen beim Verwandeln, weiss-gruen beim
    Zurueckverwandeln, rot bei Zeitablauf (Bildschirmblitz ebenso). Grober Dust-Partikelnebel reduziert.
  - Glut wie AE: alle 8 Frames je Uniform (`<alien>[_<uniform>]_f<i>.png`), Wechsel alle 2 Ticks.
  - Abzeichen-Warnung: AEs Timeout-Abzeichen (rot) blinkt in den letzten 10 s (`_warn`-Texturen).
  - XLR8-Visier: AE `mask_on/mask_off` beim Sprinten (neuer Controller `sprint`).
  - Faehigkeits-Posen direkt aus den AE-Skripten geparst (`parse_poses`, Palladium-Semantik + AE-Easing):
    Heatblast Feuer-Nova/Feuer-Surfen, Vierarm Erdschlag/Faustschlag, Diamondhead Kristallstacheln, XLR8
    Sprungtritt/Gleiten. Im Spiel geprueft: Screenshots `verwandlung_blitzkugel`, `ae_faehigkeitsposen`,
    `abzeichen_warnung`, `heatblast_glut_frames` in `docs/screenshots/`.
  - Nicht im Bild geprueft: rote Kugel beim Zeitablauf (gleicher Code-Pfad wie Verwandeln, Farbe aus Restzeit).

## OMNITRIX CORE OVERHAUL (2026-10-02) — Fokus: OMNITRIX FIRST
- Neue Prioritaet laut SANTIQ: nur noch Omnitrix, keine Story/Dimensionen/Quests. Bestand, Architektur und Fahrplan:
  `docs/OMNITRIX_CORE.md`.
- Schritt 1 (groesste Schwachstelle: kein Geraete-Kern): `OmnitrixCore` mit Profil-Registry (`omnitrix_profile`:
  prototype, recalibrated), synchronisiertem Geraete-Zustand (Hitze, Ueberhitzung, Sperre, Master Control),
  `OmnitrixStatus`/`AlienStatus`, Ueberhitzungs-Ablauf, `/hero omnitrix …`.
- Rueckmeldungen zentral: `OmnitrixCue` → `OmnitrixFeedback` (feedback.json + Spieler-Config), 12 neue Klaenge,
  FOV-Impuls (Mixin), Licht am Geraet und Zifferblatt je Zustand, HUD-Hitzeleiste.

- Phasen 1–13, 15–19 (siehe `docs/ANALYSE_UND_ROADMAP.md`): Omnitrix + 5 Aliens, Keyblade-Kampf, Magie, Herzlose,
  Ratchet-&-Clank-Waffen/Gadgets/Bolts, Raumschiff + Weltraum, Traverse Town, Arena, Dr. Nefarious, Stufe 50 +
  Fähigkeiten, KH-Kommandomenü + HUD-Editor, eigene Partikel, 45 eigene Sounds, Gruppen bis 4 Spieler.
- Werkzeuge: Generatoren für Modelle/Texturen/Icons/Partikel/Sounds, `check_assets.py`, CI-Build,
  `tools/client_smoke.sh`, `tools/multiplayer_test.sh`, **neu** `tools/visual_audit.sh` (Screenshot-Kontaktbogen).

## IN PROGRESS
- Nichts angefangen. Nächster Schritt: siehe NEXT TASK.

## BUGS
- BEHOBEN (2026-10-24): Leere Leuchtmaske (NPC ohne Leuchtteile) ließ den Client abstürzen („Invalid glow layer
  texture“) → Generator schreibt Masken nur mit Pixeln, `SafeGlowLayer` überspringt Modelle ohne Maske.
- BEHOBEN: Waffen schwebten in der dritten Person vor der Faust (Modell-Nullpunkt am Knaufende) → Griff in der Faust.
- BEHOBEN: Absturz im Alien-Renderer hinterließ Matrizen auf dem Stapel → Rückfall räumt den Stapel auf.
- BEHOBEN: Andere Spieler sahen Aliens nur in `idle` → Gliedmaßen-Animator + Positionsänderung.
- Bekannt: Schusswaffen auf Rüstungsständern stehen senkrecht (Ständer kennen die Anschlag-Haltung nicht).
- Bekannt: Gruppen werden bei Server-Neustart nicht gespeichert (bewusst).
- Testumgebung: Der Cloud-Container startet zwischen Sitzungen neu; Xvfb, Server und Client müssen dann neu gestartet
  werden (`tools/client_smoke.sh server`, `join`).

## PERFORMANCE
Gemessen 2026-10-24 mit `/tick query` (Dedicated Server, 1 Client, Traverse Town):
| Lage | Ø ms/Tick (Budget 50) | P95 | P99 |
|---|---|---|---|
| Leerlauf | 5,0 | 8,5 | 11,6 |
| 20 Herzlose im Kampf | 5,0 | 7,5 | 8,7 |
| + verwandelt (Heatblast, Aura, Partikel) | 5,2 | 8,1 | 10,0 |
Ergebnis: rund 10 % des Tick-Budgets, keine Engpässe. Client-FPS lassen sich im Test-Container nicht messen
(Software-Rendering ohne GPU).

## PLACEHOLDERS
- Sounds: synthetisch (`tools/generate_sounds.py`), nie mit Audiogerät angehört (zurückgestellt).
- Partikel-Texturen generiert.
- Traverse Town aus Code gebaut (kein NBT) — bewusst, Aufbau aus dem Weltseed.

---

## NEXT TASK
**Omnitrix-Upgrades:** Boost-Module und Stufen für das Omnitrix (freigegeben), danach Ratchet R3/R4.

## NEXT 5 TASKS
1. Omnitrix-Upgrades (Boost-Module + Stufen)
2. Gadgetron-Shop (R3)
3. Clank-Rucksack mit Heli/Thruster/Gleiter + Jetpack (R4)
4. Alien-Heimatwelten mit wilden Arten fertigstellen
5. Ben-10-Schurken und Bosse ergänzen

## LONG TERM
- Welten-Ausbau auf Nullscape/Terralith-Niveau
- Sounds mit echtem Gehör abstimmen (SANTIQ)

## BUILD STATUS
- `./gradlew build` ✅ (0.11.0-alpha, lokal, 2026-10-24)
- CI (`.github/workflows/mod-build.yml`) ✅ auf den letzten Pushes des Branches `claude/minecraft-mod-crossover-i5vcda`
- `tools/check_assets.py` ✅

## LAST VERIFIED (2026-10-24, im Spiel)
- Endgame-Waffen und ältere Ausrüstung: Ich-Sicht, Rahmen, Ständer, Spieler vorn/hinten, Gewehre per 2. Client seitlich
- NPCs Yen Sid, Max, Clank im Spiel (Leuchtaugen, kein Absturz)
- 8 Aliens verwandelt (Heatblast, XLR8, Vierarm, Diamondhead, Grey Matter, Cannonbolt, Rath, Upgrade)
- Traverse Town neu gebaut mit eigenen Blöcken; Leistung per `/tick query`
