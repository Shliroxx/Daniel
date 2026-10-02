# KINGDOM OMNITRIX — DEVELOPMENT STATUS

made by SANTIQ · Fortsetzungs-Datei für jeden Entwicklungszyklus.
**Ablauf zu Beginn jedes Zyklus:** diese Datei lesen → `git status` → `./gradlew build` → wichtigste offene Aufgabe aus
„NEXT TASK“ fortsetzen. Nach jeder größeren Etappe diese Datei aktualisieren.

Statusbegriffe: IMPLEMENTED · PROTOTYPE · PLACEHOLDER · TODO · POLISH NEEDED · VISUAL REWORK · ANIMATION REWORK ·
TEXTURE REWORK · MODEL REWORK · VFX REWORK · FINAL POLISH. Keine Selbstbewertung als „AAA“ — die Zahlen unten sind
interne Prioritäts-Scores (0–100), ehrlich nach Screenshots vergeben.

---

## CURRENT VERSION
`0.11.0-alpha` (Minecraft 1.21.1, Fabric, GeckoLib 4.9.3, playerAnimator 2.0.4)

## CURRENT PHASE
**Visual Polish Cycle 1** — Phasen 1–19 (Inhalt) abgeschlossen, Begleiter (Phase 14) gestrichen.
Ab jetzt gilt: Qualität vor Menge. Keine neuen Inhalte, bevor Aliens, Keyblades, Herzlose und Boss visuell überarbeitet sind.

## CURRENT PRIORITY
1. Verwandlungs-Sequenz + Alien-Körper (Heatblast zuerst, dann XLR8, Vierarm, Diamondhead, Grey Matter)
2. Herzlose (Shadow zuerst) — Modell, Textur, Animation
3. Keyblades (3D-Handmodell statt flachem Sprite), Omnitrix am Arm
4. Kampf-Feedback (Hit-Stop, Kamera, Schadenszahlen prüfen)
5. Boss, Schiff, Welten

---

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
- Naechste: Vierarm, Diamondhead, Heatblast, Grey Matter mit AE-Anatomie + Uniformen; Texturen mit Tonstufen statt
  Flachfarben; Glut-Animation; Ego-Arm-Modelle.

## COMPLETED
- Phasen 1–13, 15–19 (siehe `docs/ANALYSE_UND_ROADMAP.md`): Omnitrix + 5 Aliens, Keyblade-Kampf, Magie, Herzlose,
  Ratchet-&-Clank-Waffen/Gadgets/Bolts, Raumschiff + Weltraum, Traverse Town, Arena, Dr. Nefarious, Stufe 50 +
  Fähigkeiten, KH-Kommandomenü + HUD-Editor, eigene Partikel, 45 eigene Sounds, Gruppen bis 4 Spieler.
- Werkzeuge: Generatoren für Modelle/Texturen/Icons/Partikel/Sounds, `check_assets.py`, CI-Build,
  `tools/client_smoke.sh`, `tools/multiplayer_test.sh`, **neu** `tools/visual_audit.sh` (Screenshot-Kontaktbogen).

## IN PROGRESS
- **Heatblast** — Rework 1 fertig (siehe unten), FINAL POLISH offen: Hinterkopf-Flamme in der Rückansicht noch
  klotzig, Unterarme/Beine ohne eigene Gesteinsdetails, Lauf-/Sprint-Animation noch nicht im Bild geprüft.

### Visual Polish Cycle 1 — erledigt
- Alien-Generator neu: Materialien (Gestein mit Fasen + glühenden Rissen, Magma, Flamme mit Höhenverlauf und
  gezackter Kante), automatische UV-Packung, Leuchtmaske (`_glowmask.png`), voller Animationssatz für alle Aliens:
  idle, walk, run, jump, fall, attack, ability_0–2, hit, transform, revert.
- Heatblast: 46 Würfel statt 8, eigenes Skelett mit Ellbogen und Knien, Schulterpanzer, Omnitrix-Abzeichen auf der
  Brust, Flammenkopf aus 4 Knochen (flackert, weht beim Laufen nach hinten), Gesicht mit leuchtenden Augen.
- Alien-Renderer: Zustands-Animationen (Lauf/Sprint/Sprung/Fall + überlagerte Schlag-/Fähigkeits-/Treffer-
  Animationen, nur aus synchronisiertem Zustand), Leuchtschicht, Körper wächst bei der Verwandlung mit
  Überschwingen und leuchtet kurz voll, Rückverwandlung lässt den Körper zusammenschrumpfen, Kamera-Stoß beim
  Aufprall (`CameraShake` + `CameraMixin`, nur lokale Ansicht).

---

## VISUAL AUDIT (Zyklus 1, Screenshots `build/visual_audit/before/`)

Befund: Alle Aliens sind derzeit **ein umgefärbter Spielerkörper** (6–9 Würfel, 64×64-Textur aus einfarbigem Rauschen,
Punkt-Augen). Das verletzt die Vorgabe „darf nie wie Vanilla mit Texturen aussehen“. Herzlose sind Blockfiguren mit
Rausch-Textur. Items (2D-Icons) sind solide Pixel-Art. Der Boss ist am weitesten (16 Würfel, 256er Textur, Leuchtmaske,
9 Animationen).

| Asset | MODEL | TEXTURE | ANIMATION | VFX | SOUND INT. | GAMEPLAY FB | OVERALL | Status |
|---|---|---|---|---|---|---|---|---|
| Spieler (Mensch) | 50 (Vanilla) | 50 | 45 (playerAnimator Combo) | 55 | 50 | 50 | 50 | POLISH NEEDED |
| Verwandlungs-Sequenz | – | – | 55 (Wachsen, Kraftpose, Schrumpfen) | 55 (Blitz, Helix, Vollleuchten) | 55 | 55 (Kamera-Stoß) | 55 | POLISH NEEDED |
| Heatblast | 70 (Vorlagen-Silhouette) | 75 (Vorlage 1:1 vorn, doppelte Dichte) | 50 | 55 (Glut, Funken) | 50 | 50 | 68 | FINAL POLISH (echte Rückseite) |
| XLR8 | 70 (Vorlagen-Silhouette) | 75 (Vorlage 1:1 vorn) | 50 | 40 | 45 | 45 | 66 | FINAL POLISH (Schwanz, Sprint) |
| Vierarm | 68 (Vorlagen-Silhouette, Fellzacken) | 72 (Vorlage 1:1 vorn) | 45 (schwerer Gang) | 35 | 45 | 40 | 64 | FINAL POLISH (Armlänge) |
| Diamondhead | 66 (Vorlagen-Silhouette) | 70 (Vorlage 1:1 vorn) | 45 | 35 | 45 | 40 | 62 | FINAL POLISH (Farbton, Spitzen) |
| Grey Matter | 50 (großer Kopf, Augen) | 55 | 45 (flinker Gang) | 30 | 45 | 35 | 48 | POLISH NEEDED |
| Omnitrix (Item/Arm) | 30 (2D-Icon, nicht am Arm) | 60 | – | 50 | 55 | 50 | 40 | MODEL REWORK |
| Keyblades (Kingdom Key, Oathkeeper, Omega) | 35 (flaches Sprite in der Hand) | 60 | 45 | 55 | 50 | 55 | 45 | MODEL REWORK |
| Waffen/Gadgets (Combuster, Omniwrench, Heli-Pack …) | 35 | 55 | 30 | 50 | 50 | 50 | 40 | MODEL REWORK |
| Shadow | 20 | 10 | 35 (5 Animationen) | 35 | 50 | 45 | 25 | VISUAL REWORK |
| Soldier / Air Soldier / Large Body / Darkball | 20 | 10 | 35 | 35 | 50 | 45 | 25 | VISUAL REWORK |
| Dr. Nefarious Mech (Boss) | 45 | 40 | 55 | 45 | 55 | 55 | 45 | POLISH NEEDED |
| Aphelion (Schiff) | 30 | 30 | 30 | 45 | 45 | 50 | 33 | MODEL REWORK |
| NPCs (Yen Sid, Max, Clank) | 25 | 20 | 35 | – | 40 | – | 25 | VISUAL REWORK |
| Traverse Town / Arena / Weltraum | 35 (aus Code gebaut) | 45 (Vanilla-Blöcke) | – | 40 | 45 | – | 35 | VISUAL REWORK |
| UI/HUD (Kommandomenü, Status, Gruppe) | – | 65 | 50 | – | 55 | 60 | 60 | POLISH NEEDED |
| Items (2D-Icons) | – | 60 | – | – | – | – | 60 | POLISH NEEDED |
| Partikel (14 Typen) | – | 55 | 55 | 55 | – | – | 55 | POLISH NEEDED |

---

## VISUAL POLISH NEEDED — VISUAL BACKLOG
Reihenfolge = Abarbeitungsreihenfolge.

1. [x] **Verwandlungs-Sequenz** (Rework 1; offen: Omnitrix-Ring-Aufbau vor dem Blitz) — Aktivierung → Energieaufbau (Omnitrix-Ring) → Blitz → Körper wächst mit Überschwingen
   ein → Aufprall (Bodenring, Kamera-Stoß) → bereit. Rückverwandlung: rotes Pulsieren → Körper schrumpft → Mensch.
2. [x] **Heatblast** (Rework 1; FINAL POLISH offen) — MODEL (Magma-Gesteinsplatten, Flammenkopf aus Knochen, schlanke Silhouette), TEXTURE (128er,
   Risse + Leuchtmaske), ANIMATION (idle, walk, run, jump/fall, attack, ability, hit, transform), VFX (Flammen am Kopf).
3. [x] **XLR8** (Rework 1; offen: Geschwindigkeits-Streifen/Nachbild beim Sprint, Visier-Animation) — MODEL (Visier-Helm, Schwanz, Raptor-Beine, vorgebeugt), TEXTURE, ANIMATION (Sprint-Pose).
4. [ ] **Vierarm** — MODEL (massiv, breite Schultern, 4 Augen), TEXTURE, ANIMATION (schwerer Gang, Bodenschlag).
5. [ ] **Diamondhead** — MODEL (Kristallkanten, Schulter-Kristalle), TEXTURE (Facetten), ANIMATION.
6. [ ] **Grey Matter** — MODEL (klein, großer Kopf, große Augen), TEXTURE, ANIMATION (flink, hüpfend).
7. [ ] **Omnitrix** — 3D-Modell am Handgelenk (Spieler-Layer), Ring-Animation beim Aktivieren.
8. [ ] **Keyblade-Rework** — 3D-Handmodelle (Kingdom Key, Oathkeeper, Omega Key), Schwung-Spur angleichen.
9. [ ] **Heartless-Rework** — Shadow (Antennen, krabbelnd), Soldier, Air Soldier, Large Body, Darkball; Texturen mit
   echter Zeichnung statt Rauschen; Leuchtaugen per Leuchtmaske.
10. [ ] **Boss-Rework** — Nefarious-Mech Textur-Details, Kopf in der Kuppel, Schadenszustände.
11. [ ] **Weapon-Rework** — Combuster, Omniwrench, Heli-Pack am Rücken.
12. [ ] **HUD-Rework** — Feinschliff, Schadenszahlen, Treffer-Feedback.
13. [ ] **World-Rework** — Traverse Town (NBT-Strukturen), Arena, Weltraum.

## BUGS
- BEHOBEN: Leere Leuchtmaske ließ GeckoLib abstürzen → Generator schreibt Masken nur mit Pixeln.
- BEHOBEN: Absturz im Alien-Renderer hinterließ Matrizen auf dem Stapel („Pose stack not empty“, schwarzes Bild)
  → Rückfall räumt den Stapel auf.
- BEHOBEN: Andere Spieler sahen jedes Alien nur in `idle`, auch beim Laufen — GeckoLibs `isMoving()` nutzt die
  Geschwindigkeit, die der Client für fremde Spieler nicht kennt. Jetzt: Gliedmaßen-Animator + Positionsänderung.
- PRÜFEN: Herzlose, NPCs und Boss nutzen ebenfalls `isMoving()` (bei Mobs synchronisiert der Server die
  Geschwindigkeit normalerweise) — beim Herzlosen-Rework im Beobachter-Bild kontrollieren.
- Grey Matter: Größe 0.3 per Attribut — im Third-Person-Bild wirkt er trotzdem gleich groß (Kamera skaliert mit).
  Prüfen, ob der GeckoLib-Körper die Skalierung bekommt.
- Raumschiff-Einstieg spielt noch `BLOCK_PISTON_EXTEND` (Vanilla) — PLACEHOLDER.
- Gruppen werden bei Server-Neustart nicht gespeichert (bewusst, aber undokumentiert für Spieler).

## PERFORMANCE ISSUES
- Keine gemessenen Probleme. 4 Clients + Server auf 4 Kernen laufen; nicht profiliert.
- Partikel „immer voll“ (Entscheidung SANTIQ) — bei 4 Spielern in der Arena noch nicht gemessen.

## PLACEHOLDERS
- Alle Alien-, Herzlosen-, NPC- und Schiff-Modelle/-Texturen (generiert, siehe Audit).
- Sounds: synthetisch (`tools/generate_sounds.py`), nie mit Audiogerät angehört.
- Partikel-Texturen generiert.
- Traverse Town / Arena aus Code gebaut statt NBT.

---

## NEXT TASK
**Detail-Runde 2 je Alien** (AlienEvo-Niveau): Seiten-/Rückansicht prüfen, Muskel-/Panzer-Schattierung
pro Körperteil, Gesichter verfeinern; SANTIQ-Feedback pro Alien abwarten und einarbeitet.

**Danach:**
**Serien-Design Feinschliff:** Seiten- und Rückansicht aller 5 Aliens über den Beobachter-Client prüfen
(Rückseiten der Anzüge, Schwanz XLR8, Kristall-Rücken Diamondhead), danach Omnitrix als 3D-Gerät am Handgelenk.
Danach (aus altem Plan) **Vierarm MODEL/TEXTURE/ANIMATION REWORK** in `tools/generate_alien_models.py` (neue Funktion `four_arms()`,
Stil `heavy`: massiver Oberkörper, breite Schultern, 4 Arme mit Unterarmen, 4 Augen, schwerer Gang mit Gewichts-
verlagerung, Bodenschlag-Animation). Seitenansicht über Beobachter-Client prüfen (siehe Testhinweis unten).

**Testhinweis Seitenansicht:** `tools/multiplayer_test.sh add 2`, Tester2 unsichtbar seitlich platzieren
(`/tp Tester2 8.5 -59.4 0.5 90 0`), Tester läuft entlang z; Bilder aus Client 2 (`import -window`).

## NEXT 5 TASKS
1. Heatblast FINAL POLISH (Rückansicht, Lauf-Check von der Seite)
2. Diamondhead
3. Grey Matter (inkl. Größen-Bug)
4. Shadow + Soldier (Herzlosen-Rework Teil 1)
5. XLR8 FINAL POLISH (Sprint-VFX)

## LONG TERM
- Omnitrix am Arm, 3D-Keyblades, Heli-Pack am Rücken
- Boss-Feinschliff, Schiff-Modell, NPC-Modelle
- Traverse Town als NBT-Struktur, Dungeons
- Sounds mit echtem Gehör abstimmen (SANTIQ)
- Danach erst neue Inhalte (weitere Aliens/Welten)

## BUILD STATUS
- `./gradlew build` ✅ (0.11.0-alpha, lokal)
- CI (`.github/workflows/mod-build.yml`) ✅ auf c9af5dd; afe8199 gepusht
- `tools/check_assets.py` ✅ (40 Items, 5 Aliens, 4 Zauber, 7 Quests, 3 NPCs, 14 Fähigkeiten, 14 Partikel, 45 Sounds)

## LAST VERIFIED
- 0.11.0-alpha: Dedicated Server + 2–4 Clients (Gruppe, Friendly-Fire, Vita auf Mitspieler, Schiff zu zweit, Skalierung)
- XLR8 Rework 1 im Spiel: Front, Seite (Beobachter-Client), Sprint-Bildserie
- Heatblast Rework 1 im Spiel: Front, Nahansicht, Verwandlungs-Bildserie, Schlag + Feuerexplosion (`build/visual_audit/after/`)
- Visual Audit Baseline: alle 5 Aliens (Front/Seite/Ego), 5 Herzlose — `build/visual_audit/before/contact_sheet.png`
