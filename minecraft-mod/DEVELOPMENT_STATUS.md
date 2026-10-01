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

## COMPLETED
- Phasen 1–13, 15–19 (siehe `docs/ANALYSE_UND_ROADMAP.md`): Omnitrix + 5 Aliens, Keyblade-Kampf, Magie, Herzlose,
  Ratchet-&-Clank-Waffen/Gadgets/Bolts, Raumschiff + Weltraum, Traverse Town, Arena, Dr. Nefarious, Stufe 50 +
  Fähigkeiten, KH-Kommandomenü + HUD-Editor, eigene Partikel, 45 eigene Sounds, Gruppen bis 4 Spieler.
- Werkzeuge: Generatoren für Modelle/Texturen/Icons/Partikel/Sounds, `check_assets.py`, CI-Build,
  `tools/client_smoke.sh`, `tools/multiplayer_test.sh`, **neu** `tools/visual_audit.sh` (Screenshot-Kontaktbogen).

## IN PROGRESS
- **Heatblast VISUAL REWORK** (Modell, Textur + Leuchtmaske, Animationen) und
  **Verwandlungs-Sequenz** im Alien-Renderer (Aufbau → Blitz → Einblenden des Körpers → Bereit).

---

## VISUAL AUDIT (Zyklus 1, Screenshots `build/visual_audit/before/`)

Befund: Alle Aliens sind derzeit **ein umgefärbter Spielerkörper** (6–9 Würfel, 64×64-Textur aus einfarbigem Rauschen,
Punkt-Augen). Das verletzt die Vorgabe „darf nie wie Vanilla mit Texturen aussehen“. Herzlose sind Blockfiguren mit
Rausch-Textur. Items (2D-Icons) sind solide Pixel-Art. Der Boss ist am weitesten (16 Würfel, 256er Textur, Leuchtmaske,
9 Animationen).

| Asset | MODEL | TEXTURE | ANIMATION | VFX | SOUND INT. | GAMEPLAY FB | OVERALL | Status |
|---|---|---|---|---|---|---|---|---|
| Spieler (Mensch) | 50 (Vanilla) | 50 | 45 (playerAnimator Combo) | 55 | 50 | 50 | 50 | POLISH NEEDED |
| Verwandlungs-Sequenz | – | – | 10 (Körper erscheint sofort) | 50 (Blitz, Helix) | 55 | 40 | 30 | VISUAL REWORK |
| Heatblast | 15 | 10 | 15 (idle, walk) | 45 | 50 | 45 | 20 | MODEL/TEXTURE/ANIMATION REWORK |
| XLR8 | 15 | 10 | 15 | 35 | 45 | 40 | 18 | MODEL/TEXTURE/ANIMATION REWORK |
| Vierarm | 20 (4 Arme) | 10 | 15 | 35 | 45 | 40 | 20 | MODEL/TEXTURE/ANIMATION REWORK |
| Diamondhead | 20 (Kristallkopf) | 15 | 15 | 35 | 45 | 40 | 22 | MODEL/TEXTURE/ANIMATION REWORK |
| Grey Matter | 15 | 10 | 15 | 30 | 45 | 35 | 18 | MODEL/TEXTURE/ANIMATION REWORK |
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

1. [ ] **Verwandlungs-Sequenz** — Aktivierung → Energieaufbau (Omnitrix-Ring) → Blitz → Körper wächst mit Überschwingen
   ein → Aufprall (Bodenring, Kamera-Stoß) → bereit. Rückverwandlung: rotes Pulsieren → Körper schrumpft → Mensch.
2. [ ] **Heatblast** — MODEL (Magma-Gesteinsplatten, Flammenkopf aus Knochen, schlanke Silhouette), TEXTURE (128er,
   Risse + Leuchtmaske), ANIMATION (idle, walk, run, jump/fall, attack, ability, hit, transform), VFX (Flammen am Kopf).
3. [ ] **XLR8** — MODEL (Visier-Helm, Schwanz, Raptor-Beine, vorgebeugt), TEXTURE, ANIMATION (Sprint-Pose).
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
Heatblast-Rework abschließen (Generator `tools/generate_alien_models.py` → neues detailliertes Modell, 128er Textur +
`_glowmask`, Animationen idle/walk/run/jump/attack/ability/transform), Alien-Renderer mit Zustands-Animationen,
Leuchtschicht und Einwachs-Verwandlung. Test: `tools/visual_audit.sh build/visual_audit/after aliens`.

## NEXT 5 TASKS
1. XLR8 nach demselben Muster (Modell, Textur, Animationen, Sprint-Pose)
2. Vierarm
3. Diamondhead
4. Grey Matter (inkl. Größen-Bug)
5. Shadow + Soldier (Herzlosen-Rework Teil 1)

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
- Visual Audit Baseline: alle 5 Aliens (Front/Seite/Ego), 5 Herzlose — `build/visual_audit/before/contact_sheet.png`
