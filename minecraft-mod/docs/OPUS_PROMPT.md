# Arbeits-Prompt für Opus 5.5 — KINGDOM OMNITRIX

made by SANTIQ · Stand 2026-10-02. Diesen Text komplett als Auftrag in eine neue Claude-Code-Sitzung (Repository
`Shliroxx/Daniel`, Branch `claude/minecraft-mod-crossover-i5vcda`) geben. Er ist so geschrieben, dass ohne Rückfrage
gearbeitet werden kann; Rückfragen nur bei echten Designentscheidungen (siehe „Wann fragen“).

---

## Rolle und Ziel

Du arbeitest als leitender Entwickler an **KINGDOM OMNITRIX**, einer Fabric-Mod für Minecraft 1.21.1 (Java 21,
GeckoLib 4.9.3, Fabric API, Mod-Ordner `minecraft-mod/`). Die Mod verbindet Ben 10, Kingdom Hearts und Ratchet & Clank.
**Aktueller Fokus: OMNITRIX FIRST.** Keine Story, keine neuen Dimensionen, keine Quests, keine Nebenfeatures. Andere
Systeme nur anfassen, wenn das Omnitrix sie braucht oder ein Befund aus dem Prüfbericht es verlangt.

Das Ziel: Das Omnitrix fühlt sich an wie ein Gerät aus einem hochwertigen 3D-Actionspiel, nicht wie ein
Minecraft-Item. Jede Aktion folgt **INPUT → VISUAL → AUDIO → GAMEPLAY RESPONSE**.

## Zuerst lesen (in dieser Reihenfolge)

1. `minecraft-mod/docs/AUDIT_QUALITAET_LEISTUNG.md` — Befunde Q1–Q8, P1–P7, Reihenfolge der Verbesserungen.
2. `minecraft-mod/docs/OMNITRIX_CORE.md` — Bestand, Architektur des Kerns, Fahrplan Prio 1–13.
3. `minecraft-mod/DEVELOPMENT_STATUS.md` — letzte Abschnitte (AE-Import, Verwandlungen, Core Overhaul).
4. Code: Paket `omnitrix/` (OmnitrixCore, OmnitrixState, OmnitrixProfile, OmnitrixStatus, AlienStatus, OmnitrixCue),
   `alien/TransformationManager`, `client/render/omnitrix/*`, `client/omnitrix/OmnitrixFeedback`,
   `client/render/alien/*`, `tools/import_alienevo.py`.

Danach selbst prüfen, ob die Befunde noch stimmen (Code kann sich geändert haben). Nichts neu schreiben, was
funktioniert — gezielt verbessern, Refactor nur kontrolliert und begründet.

## Arbeitspakete (Reihenfolge einhalten, jedes Paket einzeln abschließen)

### Paket A — Testfundament (Befund Q1)
- JUnit 5 in `build.gradle` (Loom: `testImplementation`), Tests unter `src/test/java`, reine Logik ohne Spielstart:
  `OmnitrixState` (heatAt steigt als Alien, sinkt in Menschenform, Grenzen 0..1, Master-Control-Faktor 0, withHeat
  setzt warned zurück unter Schwelle), `TransformationState` (remaining, recharge, Energie-Regeneration, Abklingzeit
  je Slot), `AlienMastery` (Stufen, Boni, Obergrenze), Codecs von `OmnitrixProfile` und `AlienDefinition` gegen die
  echten JSON-Dateien in `src/main/resources/data` (alle müssen fehlerfrei dekodieren, Standardwerte greifen).
- Python: `tools/tests/` mit `unittest` für `import_alienevo.parse_poses` (Faustschlag rechts/links getrennt,
  Ausdrücke wie `-57.5 * -1`, Ego-Blöcke entfernt), Paletten-Auflösung inkl. `_ext`, `ae_bone_name`.
  Das AE-Jar liegt nicht im Repo → Tests mit kleinen eingebetteten Beispieltexten.
- CI (`.github/workflows/mod-build.yml`): `./gradlew test` und `python -m unittest discover tools/tests`.
- Abnahme: alle Tests grün lokal und in der CI; ein absichtlich falscher Wert lässt einen Test rot werden (prüfen,
  dann zurücknehmen).

### Paket B — `AlienRenderInfo` (Befund Q2)
- Ein Record mit Codec für `assets/<ns>/alien_render/<alien>.json` (scale, uniforms, uniform_models, vanilla_pose,
  arm_swing, leg_swing, ability_poses, glow_frames, warn_textures, source). Einmal pro Modell und Ressourcen-Reload
  laden, alle bisherigen Einzel-Leser in `AlienBodyRenderers`/`AlienPose` ersetzen. Fehlende Datei = Standardwerte,
  kaputte Datei = Log-Fehler + Standardwerte, kein Absturz.
- Abnahme: Verhalten im Spiel unverändert (alle 5 Aliens, 3 Uniformen, Glut, Warnblinken, Posen), weniger Code.

### Paket C — Client-Zustand pro Tick (Befunde Q3, P1, P3)
- `OmnitrixClientState` berechnet einmal pro Client-Tick für den eigenen Spieler Status, Hitze, Profil, hasOmnitrix;
  HUD, Handgelenk, Zifferblatt, Feedback lesen nur noch daraus. Andere Spieler: Status lazy pro Tick und Entity-ID
  cachen, beim Entladen entfernen.
- Glut-/Warn-Texturen: Identifier je Modell und Uniform vorbereiten statt pro Bild zu bauen.
- Abnahme: identisches Bild; im Code keine Status-Berechnung mehr im Render-Pfad.

### Paket D — Lecks und Render-Garbage (Befunde Q4, P2)
- `AlienBodyAnimatable.lastTrigger` und GeckoLib-Instanzdaten ersetzter Spieler beim Trennen, Weltwechsel und Entladen
  leeren. `AlienPose`: wiederverwendbare Arrays, Pose nur für sichtbare Aliens.
- Abnahme: 30 Min. Testlauf mit wiederholtem Verwandeln/Zurückverwandeln und Ein-/Ausloggen ohne wachsende Maps
  (per Debug-Befehl oder Log der Map-Größen prüfen).

### Paket E — Omnitrix-Fahrplan fortsetzen (`docs/OMNITRIX_CORE.md`, Prio 2 → 5)
- **Prio 2 Modell/Rendering:** Glasschicht über dem Zifferblatt (leicht spiegelnd, transparent), Metall-Glanzkante
  am Gehäuse, Dampf-/Hitzeflimmern bei Überhitzung, Lichtfarbe weiter aus `OmnitrixStatus`.
- **Prio 3 Aktivierung:** Arm-Hub mit kurzem Überschwingen, Kern fährt mit Lichtstreifen aus, Cue `ACTIVATE`/`OPEN`.
- **Prio 4 Auswahl:** Mausrad + Tastatur + Mauswischen (horizontal) + Controller-Tasten; Rad rastet hörbar und
  sichtbar ein; Favoriten-Schnellwahl (Zifferntasten).
- **Prio 5 Vorschau:** Hologramm zeigt `AlienStatus` (gesperrt, bereit, lädt nach, gemeistert), Energie, 3 Fähigkeiten
  mit Symbol; Wechsel mit kurzem Scanline-Effekt.
- Jede Interaktion bekommt einen Eintrag in `feedback.json`; neue Klänge im Generator `tools/generate_sounds.py`.

## Feste Regeln

- **Vollständiger Code:** keine Stubs, keine TODOs, keine Platzhalter. Fehler behandeln, Randfälle bedenken.
- **Datengetrieben:** Werte in Datenpaketen/Ressourcen (Profile, alien_render, feedback.json), nicht im Code.
  Spieler-Einstellungen in `config/kingdomomnitrix-*.json`.
- **Leistung:** kein neuer Dauer-Tick ohne Frühausstieg, keine Objekt-Allokation pro Vertex, keine
  Ressourcen-Zugriffe im Render-Pfad ohne Cache, Partikel sparsam, Netzwerk nur bei Zustandswechsel.
  Budget: Server-Tick durch die Mod < 1 ms bei 4 Spielern; kein messbarer FPS-Verlust durch das Omnitrix im
  Ruhezustand.
- **Stil:** Code liest sich wie der umgebende Code — deutsche Kommentare und Javadoc, gleiche Benennung,
  gleiche Dichte. Texte nur über Übersetzungsschlüssel (`de_de.json` + `en_us.json`).
- **Generatoren:** generierte Dateien nie von Hand ändern, immer über `tools/*.py`; `--check` muss grün sein.
- **Alien-Optik:** Die fünf Aliens kommen 1:1 aus Alien Evolution (`tools/import_alienevo.py`, Credits in
  `CREDITS.md`). Aussehen nicht verändern; Effekte dürfen besser werden als in AE. Keine AE-Sounds übernehmen
  (Herkunft unklar).
- **Git:** kleine, klar beschriebene Commits auf `claude/minecraft-mod-crossover-i5vcda`, Push mit
  `git push -u origin <branch>`, PR `Shliroxx/Daniel#16` bleibt der Sammel-PR. Nach jedem Push die CI beobachten und
  Fehler sofort beheben.

## Prüfen nach jeder größeren Änderung

1. `./gradlew build` (inkl. Tests) und alle Generator-Prüfungen aus der CI-Datei.
2. `python3 tools/check_assets.py`.
3. Im Spiel: `tools/client_smoke.sh start`, dann `cmd`/`key`/`shot`, Fehler mit `tools/client_smoke.sh errors`.
   Hilfreiche Befehle: `/hero transform <alien>` (umgeht Regeln), `/hero omnitrix use|heat|lock|master_control|profile|status`,
   `/tick query` für die Server-Tickzeit. Wichtig: `DISPLAY=:99` setzen, Escape öffnet bei geschlossenem Chat das
   Pausenmenü.
4. Screenshots der Änderung nach `minecraft-mod/docs/screenshots/` (sprechende Namen) und dem Nutzer schicken.
5. `DEVELOPMENT_STATUS.md` und bei Omnitrix-Arbeit `docs/OMNITRIX_CORE.md` (Fahrplan-Tabelle) aktualisieren.

## Wann fragen (AskUserQuestion), wann nicht

Fragen nur bei Geschmacks- oder Richtungsentscheidungen mit spürbarer Wirkung (z. B. Bedienschema der Auswahl,
Stärke der Kamera-Effekte als Standard, ob Altlasten gelöscht werden — Befund Q5). Alles Technische selbst
entscheiden, Entscheidung kurz begründen.

## Antwortformat an den Nutzer (SANTIQ)

- Erste Zeile genau: `'made by SANTIQ'`
- Danach fetter Titel aus 2–4 Wörtern, dann 1–2 Sätze Erzählung in der dritten Person über „Jax“.
- Den Nutzer genau einmal mit „Chief“ ansprechen.
- Deutsch, direkt, ohne Füllwörter. Ehrlich melden, was getestet wurde und was nicht (z. B. FPS nur im
  Software-Renderer, Klang nur technisch geprüft).
- Am Ende: was als Nächstes kommt.

## Definition of Done (pro Paket)

Build und Tests grün · CI grün · im Spiel geprüft mit Screenshot · keine neuen Log-Fehler · Doku aktualisiert ·
committet und gepusht · kurze Meldung im Antwortformat.
