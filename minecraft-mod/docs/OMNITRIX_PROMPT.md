# Arbeits-Prompt: Omnitrix-Funktionen stärker als Alien Evolution

made by SANTIQ · Stand 2026-10-02. Diesen Text komplett als Auftrag in eine neue Claude-Code-Sitzung (Opus 5.5)
geben — Repository `Shliroxx/Daniel`, Branch `claude/minecraft-mod-crossover-i5vcda`, Mod-Ordner `minecraft-mod/`.
Ergänzt `docs/OPUS_PROMPT.md` (Grundlagen-Pakete A–D) und `docs/OMNITRIX_CORE.md` (Kern-Architektur).
Entscheidungen von SANTIQ sind eingearbeitet (siehe „Festgelegte Entscheidungen“).

---

## Auftrag

Du bist leitender Entwickler von **KINGDOM OMNITRIX** (Fabric 1.21.1, Java 21, GeckoLib 4.9.3). Mache das Omnitrix
**eigenständig stärker als Alien Evolution (AE)**: jede AE-Funktion mindestens gleichwertig, die meisten spürbar
besser, dazu eigene Systeme, die AE nicht hat. Das Aussehen der fünf Aliens bleibt 1:1 AE (importiert, Credits in
`CREDITS.md`); **Mechaniken, Code, UI und Klang sind eigene Arbeit** — keinen AE-Code (KubeJS/Palladium) übernehmen,
nur die Idee als Messlatte.

Leitsatz für jede Funktion: **INPUT → VISUAL → AUDIO → GAMEPLAY RESPONSE**, datengetrieben, in Multiplayer
synchron, performant, getestet.

## Zuerst lesen

1. `docs/OMNITRIX_CORE.md`, `docs/AUDIT_QUALITAET_LEISTUNG.md`, `docs/OPUS_PROMPT.md`, `DEVELOPMENT_STATUS.md`.
2. Code: `omnitrix/*` (Core, State, Profile, Status, AlienStatus, Cue), `alien/TransformationManager`,
   `alien/TransformationState`, `alien/AlienDefinition`, `ability/*` (AbilityRegistry, BuiltinAbilities),
   `progression/AlienMasteryManager`, `client/render/omnitrix/*`, `client/omnitrix/OmnitrixFeedback`,
   `client/screen/OmnitrixScreen`, `client/hud/OmnitrixHud`, `data/.../alien/*.json`, `omnitrix_profile/*.json`,
   `assets/.../omnitrix/feedback.json`.
3. Vorbedingung: Pakete A–C aus `docs/OPUS_PROMPT.md` (Tests, `AlienRenderInfo`, `OmnitrixClientState`) müssen
   erledigt sein. Wenn nicht: zuerst erledigen — neue Funktionen nur auf getestetem Fundament.

## Messlatte: AE gegen uns

| Funktion | AE | Unser Stand | Ziel |
|---|---|---|---|
| Auswahl | 10 Playlists × 10 Slots, Durchschalten | 3D-Rad mit allen Aliens, Feder-Drehung | **Paket 1:** Favoriten-Sets + Radial-Schnellwahl + Smart-Scan-Empfehlung |
| Schnellwechsel | 10 Tasten + Rad, nur Master Control | — | **Paket 1:** Alien→Alien ohne Rückverwandlung (Master Control sofort, sonst mit Hitze-Aufschlag) |
| Zeit/Nachladen | Timeout + Recharge, rotes Abzeichen | Dauer, Nachladen, Hitze, Überhitzung, Sperre, Warnblinken | Hitze an alle Systeme koppeln (Fehlfunktion, Module, Fähigkeiten) |
| Lebenspunkte | Menschen-HP gespeichert, Alien-HP als Prozent | geteilt | **Paket 1:** Alien-Panzer (eigener Pool), DNA-Schock bei 0 |
| Todesschutz | Failsafe (Totem-artig, Abklingzeit) | — | **Paket 1:** Notfall-Verwandlung mit passendem Alien statt Zufall |
| Zufall | 1/500 Chance neues Alien beim Verwandeln | — | **Paket 3:** Fehlfunktionen nach Hitze (falsches Alien, Aussetzer, DNA-Fehler) |
| Master Control | Code auf Code-Tablet | Flag + Profilwerte | **Paket 4:** Holo-Code-Tastatur am Omnitrix + Freischaltung über Fortschritt |
| Selbstzerstörung | Code → SDM | — | **Paket 4:** SDM per Spielregel (Standard aus) |
| Farbe/Upgrades | 16 Farbmodule, Upgrade-Kristalle, Werkbank | Profile prototype/recalibrated | **Paket 6:** Kalibrier-Werkbank mit Modul-Slots inkl. Farbe |
| Systemsprache | — | Cues: Klang, Licht, Kamera | **Paket 5:** Omnitrix-OS Holo-Meldungen |
| Fähigkeiten | 6–7 je Alien, freischaltbar | 1–3 je Alien, AE-Posen | **Paket 7:** 6+ je Alien, über Meisterschaft freischaltbar, Synergien mit Omnitrix |
| Scan | — | Grey Matter „scan“ | **Paket 2:** Smart-Scan als Omnitrix-Funktion |

## Arbeitspakete (Reihenfolge einhalten, jedes vollständig abschließen)

### Paket 1 — Auswahl, Schnellwechsel, Lebenspunkte, Notfall
1. **Favoriten-Sets:** bis zu 4 Sets à 8 Aliens (Attachment, synchronisiert), im Rad per Taste umschalten; leere
   Plätze zeigen „DNA fehlt“. Bearbeiten direkt im Rad (Alien halten → Platz wählen).
2. **Radial-Schnellwahl:** Taste halten öffnet ein Radialmenü des aktiven Sets um das Fadenkreuz (Maus-Richtung wählt,
   Loslassen bestätigt) — ohne Arm-Animation, für Kampf. Rastklang je Segment (Cue `NAVIGATE`).
3. **Schnellwechsel:** verwandelt direkt in ein anderes Alien. Normal: + Hitze (`quick_change_heat` im Profil),
   halbe Restzeit geht verloren. Master Control: sofort, ohne Aufschlag. Eigene kurze Blitzkugel-Variante.
4. **Alien-Panzer:** Beim Verwandeln Menschen-HP einfrieren; das Alien hat einen eigenen Pool
   (`armor_health` in `AlienDefinition`, mit Meisterschaft skaliert). Schaden trifft zuerst den Pool. Bei 0:
   Zwangs-Rückverwandlung, „DNA-Schock“ (2 s Langsamkeit + Bildschirm-Verzerrung), Menschen-HP unverändert.
   HUD: Panzer-Leiste über der Zeitleiste.
5. **Notfall-Verwandlung:** tödlicher Schaden in Menschenform → Omnitrix verwandelt automatisch in das Alien, das
   den Schaden am besten übersteht (Feuer → Heatblast, Sturz → kleinste Größe/Grey Matter, Nahkampf → Vierarm/
   Diamondhead; Regeln als Daten), setzt Hitze auf 90 % und Abklingzeit (`failsafe_cooldown` im Profil). Nur wenn das
   Alien freigeschaltet ist; sonst normaler Tod.
- Abnahme: alles im Spiel vorgeführt (Screenshots), Panzer/Notfall per Test abgedeckt (reine Logik).

### Paket 2 — Smart-Scan
- Omnitrix bewertet alle 20 Ticks (nur während das Rad offen ist bzw. bei Notfall) Umgebung und Ziel:
  Lava/Feuer, Wasser, Höhe/Sturz, enge Räume, Gegnertyp und Rüstung, Entfernung. Regeln datengetrieben
  (`data/.../omnitrix_scan/*.json`: Bedingung → Alien-Gewichte).
- Rad und Radialmenü markieren die Top-Empfehlung (pulsierender Rahmen, Holo-Zeile „Empfohlen: Heatblast — Lava“).
- Taste „Smart-Wahl“: verwandelt direkt in die Empfehlung.
- Abnahme: 5 Situationen im Spiel mit Screenshot (Lava, Wasser, Klippe, gepanzerter Gegner, enger Gang).

### Paket 3 — Fehlfunktionen bei Hitze
- Ab `malfunction_threshold` (Profil, Standard 0,8) beim Verwandeln Würfelwurf mit steigender Chance:
  falsches Alien (aus freigeschalteten), Aussetzer (Verwandlung scheitert, Funken, Hitze +5 %), DNA-Fehler
  (verwandelt, aber Fähigkeit 1 für 10 s gesperrt). Tabelle im Profil, Master Control schaltet ab.
- Klare Rückmeldung: eigener Cue `MALFUNCTION`, Glitch-Effekt am Hologramm, Holo-Meldung.
- Abnahme: Test mit festem Zufalls-Seed; im Spiel jeder Fehlertyp einmal mit Screenshot.

### Paket 4 — Codes, Master Control, Selbstzerstörung
- Holo-Code-Tastatur am Omnitrix (Ziffernfeld im Zifferblatt, Eingabe per Rad/Maus/Ziffern), Codes datengetrieben
  (`omnitrix_code/*.json`: Code → Aktion). Aktionen: Master Control an/aus, Sperre, Profil-Diagnose.
- Master Control zusätzlich über Fortschritt freischaltbar (Heldenstufe + alle Aliens Meisterschaft ≥ 5).
- SDM: Spielregel `omnitrixSelfDestruct` (Standard **false**). Countdown 30 s mit Alarm, abbrechbar per Code,
  Explosion ohne Blockschaden wenn `mobGriefing=false`. Nie ohne Spielregel.
- Abnahme: Codes, MC-Freischaltung, SDM an/aus, Abbruch im Spiel geprüft.

### Paket 5 — Omnitrix-OS (Holo-Meldungen + Töne)
- Kurze Systemzeilen im Hologramm-Stil über der Hotbar bzw. am Zifferblatt: „DNA-Analyse abgeschlossen“, „Kern
  kritisch“, „Kalibrierung übernommen“, „Fehlfunktion erkannt“. Max. 2 Zeilen gleichzeitig, Tipp-Effekt,
  Ausblenden nach 3 s, Spieler-Option zum Abschalten.
- Jede Meldung als Übersetzungsschlüssel (de/en), ausgelöst über `OmnitrixCue` (Feld `message` in `feedback.json`).
- Abnahme: alle Cues mit Meldung im Spiel gesehen; keine Meldung blockiert das Sichtfeld im Kampf.

### Paket 6 — Kalibrier-Werkbank und Module
- Block „Omnitrix-Kalibrator“ (eigenes Modell + emissive Panels) mit Bildschirm: Omnitrix einsetzen, 4 Modul-Slots
  (Kühlung, Kern, Bandbreite, Farbe). Module als Items, Wirkungen als Daten (`omnitrix_module/*.json`: Profilwert →
  Faktor/Summand). Ergebnis = Profil + Module, im `OmnitrixState` gespeichert, synchronisiert.
- Farbmodul ändert Lichtfarbe des Geräts und der Blitzkugel (statt AEs 16 festen Farben: freie Farbe aus Palette).
- Herstellung: Rezepte aus vorhandenen Materialien (Mythril, Orichalcum, Raritanium, Bolts) — keine neuen Erze.
- Abnahme: jedes Modul ändert messbar den Wert (Statusbefehl), Farbe sichtbar am Gerät und in der Blitzkugel.

### Paket 7 — Alien-Fähigkeiten stärker als AE
Ziel je Alien: mindestens die AE-Breite (6–7 Fähigkeiten), freischaltbar über Meisterschaft (Stufe 1/3/5/7/10), die
3 Slot-Tasten bleiben (Auswahl der aktiven 3 im Rad oder auf der Alien-Seite), plus **eine Omnitrix-Synergie** je Alien
und eine **Ultimate** bei Meisterschaft 10. Alle Werte in den Alien-JSONs, Logik in `ability/`, AE-Posen über
`ability_poses` (Import) bzw. eigene Posen.

| Alien | AE hat | Ziel (eigene Umsetzung, besser) |
|---|---|---|
| Heatblast | Feuerball, Feuertornado, Pyro-Sprung, Hitzeabsorption, Feueratem, Feuer-Surfen | + Hitzeabsorption **kühlt das Omnitrix** (Synergie), Feuer-Surfen mit Steuerung und Spur, Ultimate „Supernova“ |
| XLR8 | Rutschtritt, Tornado, Wandlauf, Wasserlauf, Boost, Schlaghagel | + Wand-/Wasserlauf als Bewegungssystem (nicht nur Fähigkeit), Nachbilder, Ultimate „Zeitlupe“ (Welt verlangsamt für Gegner) |
| Vierarm | Felsbrocken, Tornado, Schallklatschen, Sprung, Block, Klettern, Bodenschlag | + Gegner packen und werfen, 4-Arm-Kombo im Nahkampf, Block reflektiert Projektile, Ultimate „Erdbeben“ |
| Diamondhead | Kristallerzeugung, Stachelkreis, Säule, Splitter, Stacheln, Kampfmodus (Schwert/Schild) | + Kristallwände als temporäre Blöcke (verschwinden), Projektil-Reflexion, Kristallrüstung für Gruppenmitglieder, Ultimate „Kristallgefängnis“ |
| Grey Matter | Baupläne (Anzug, Gliedmaßen, Jetpack) | + Schwachstellen-Analyse (kritische Treffer), Omnitrix-Übertakten (Hitze −30 %, Synergie), auf Gegnern reiten, Ultimate „Neuverkabelung“ (Maschinen/Golems kurz übernehmen) |

- Jede Fähigkeit: eigener Cue/Klang, VFX (sparsam, Display-Entities oder eigene Partikel), Pose, Abklingzeit, Energie.
- Balancing-Tabelle in `docs/OMNITRIX_CORE.md` pflegen (Schaden, Kosten, Abklingzeit).
- Abnahme: jede Fähigkeit im Spiel mit Screenshot; Ultimates mit kurzer Bildserie.

## Festgelegte Entscheidungen (SANTIQ, 2026-10-02)

- Extras: Smart-Scan, Alien-Panzer + Notfall, Fehlfunktionen bei Hitze, Module-Werkbank, Fähigkeiten über AE hinaus.
- Codes ja; Selbstzerstörung nur per Spielregel, Standard aus.
- Omnitrix spricht über Holo-Textmeldungen + Töne, keine Sprachaufnahmen.
- Alien-Optik 1:1 AE, Standard-Uniform bleibt classic. Keine AE-Sounds.

## Architektur-Regeln

- **Datengetrieben:** neue Registries nach Muster `OmnitrixCore.PROFILES` (synchronisiert über
  `DynamicRegistries.registerSynced`): `omnitrix_scan`, `omnitrix_code`, `omnitrix_module`. Neue Profilfelder mit
  Standardwerten im Codec (alte JSONs bleiben gültig).
- **Zustand:** Geräte-Zustand in `OmnitrixState` erweitern (Favoriten, Module, Farbe, Panzer) oder eigenes Attachment —
  immer mit Codec, persistent, synchronisiert wo andere es sehen müssen.
- **Rückmeldung:** jede neue Aktion als `OmnitrixCue` + Eintrag in `feedback.json`; Klänge über
  `tools/generate_sounds.py`.
- **Kein Tick-Loop ohne Frühausstieg**, Zeitstempel statt Zählern, Netzwerk nur bei Wechsel, Partikel sparsam.
  Budget: < 1 ms Server-Tick durch die Mod bei 4 Spielern.
- **Server ist Autorität** für alles Spielrelevante (Panzer, Notfall, Fehlfunktion, Codes, Module); der Client zeigt
  an und sagt voraus.
- Code-Stil wie im Projekt: deutsche Kommentare/Javadoc, Übersetzungsschlüssel de/en, keine Stubs/TODOs.

## Prüfen nach jedem Paket

1. `./gradlew build` inkl. Tests, alle `--check`-Generatoren der CI, `python3 tools/check_assets.py`.
2. Im Spiel (`tools/client_smoke.sh`, `DISPLAY=:99`): jede neue Funktion vorführen, Screenshots nach
   `docs/screenshots/`; `/tick query` vor/nach; `tools/client_smoke.sh errors`.
3. Multiplayer-Stichprobe mit `tools/multiplayer_test.sh` (2 Clients): andere sehen Zustände korrekt.
4. Doku: `DEVELOPMENT_STATUS.md`, Fahrplan/Balancing in `docs/OMNITRIX_CORE.md`.
5. Commit + Push, CI beobachten und Fehler sofort beheben (PR `Shliroxx/Daniel#16`).

## Wann fragen

Nur bei Geschmack/Richtung mit großer Wirkung (Tastenbelegung der Schnellwahl, Stärke einer Ultimate, Rezeptkosten).
Technik selbst entscheiden und begründen. Wenn eine Idee fehlt: SANTIQ fragen statt raten.

## Antwortformat an SANTIQ

Erste Zeile `'made by SANTIQ'` · fetter Titel aus 2–4 Wörtern · 1–2 Sätze Erzählung in der dritten Person über Jax ·
genau einmal „Chief“ · Deutsch, direkt · ehrlich sagen, was getestet ist und was nicht · am Ende der nächste Schritt.

## Definition of Done (pro Paket)

AE-Gegenstück erreicht oder übertroffen · Build/Tests/CI grün · im Spiel mit Screenshots geprüft · Multiplayer-
Stichprobe · keine neuen Log-Fehler · Doku aktualisiert · committet und gepusht.
