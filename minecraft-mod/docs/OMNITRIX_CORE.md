# OMNITRIX CORE — Bestand, Architektur, Fahrplan

made by SANTIQ · Stand 2026-10-02 · Vorgabe: „OMNITRIX FIRST. EVERYTHING ELSE SECOND.“

## 1. Bestandsaufnahme vor dem Umbau

| Bereich | Stand | Bewertung |
|---|---|---|
| Alien-Definitionen (DNA) | `AlienDefinition` als synchronisierte Datenpaket-Registry (`data/.../alien/*.json`): Dauer, Nachladen, Energie, Groesse, Attribute, Immunitaeten, Faehigkeiten, DNA-Quellen, Modell | **funktioniert**, datengetrieben |
| Faehigkeiten | `AbilityRegistry` + Slots je Alien, Energie/Abklingzeit pro Slot | **funktioniert**, modular |
| Verwandlung (Server) | `TransformationManager` + `TransformationState` (synchronisiert): verwandeln, zurueck, Ablauf, Attribute, Warnpiepen | funktioniert, aber **Geraet hatte keinen eigenen Zustand** |
| Bedien-Ablauf (Client) | `OmnitrixController` mit `OmnitrixPhase` (13 Phasen: heben, oeffnen, Rad, bestaetigen, Schlag …), Feder-Rad, Sync an andere | funktioniert, Zeiten **hart codiert** |
| Modell/Rendering | 3D-Geraet am Arm (Ego + Third-Person), Kern faehrt aus, Zifferblatt-Silhouette, Hologramm | funktioniert; Licht **immer gruen** (nur Abklingzeit gedimmt) |
| Freischaltung | DNA-Proben → `HeroData.unlockedAliens` | funktioniert, ohne Rueckmeldung am Geraet |
| Meisterschaft | `AlienMasteryManager` (Stufe 1–10, Dauer-/Abklingbonus) | funktioniert |
| Klang | 4 Omnitrix-Klaenge; Fehler nutzte den Magie-Sound | **Platzhalter** |
| Hitze/Ueberhitzung, Sperre, Master Control, Modi, Upgrades | — | **fehlte** |
| Konfiguration | alle Werte im Code | **fehlte** |

**Groesste Schwachstelle:** Das Omnitrix existierte nur als Bedien-Oberflaeche. Es gab keinen Geraete-Kern, der
Zustand, Belastung und Regeln besitzt — damit fehlte die Grundlage fuer Overheat, Lock, Master Control, Upgrades und
fuer zustandsabhaengiges Feedback. Genau dort wurde begonnen.

## 2. Architektur (neu: Paket `com.santiq.kingdomomnitrix.omnitrix`)

```
OmnitrixCore (Server-Regeln, Lesen auch auf dem Client)
 ├── OmnitrixProfile   Datenpaket-Registry „omnitrix_profile“ (synchronisiert): Hitze, Warnschwelle, Sperrzeit,
 │                     Dauer-/Abklingfaktoren, Bestaetigungszeit, Master-Control-Werte → prototype.json, recalibrated.json
 ├── OmnitrixState     Attachment je Spieler (persistent, an alle synchronisiert): Profil, Hitze + Zeitstempel,
 │                     Warnung, ueberhitzt-bis, gesperrt-bis, Master Control
 ├── OmnitrixStatus    IDLE · READY · ACTIVE · SELECTING · TRANSFORMING · TRANSFORMED · WARNING · OVERHEATED ·
 │                     COOLDOWN · LOCKED · MASTER_CONTROL — je Zustand Lichtfarbe, Helligkeit, Puls
 ├── AlienStatus       LOCKED · UNLOCKED · SELECTED · TRANSFORMING · ACTIVE · COOLDOWN · MASTERED
 └── OmnitrixCue       ACTIVATE · OPEN · NAVIGATE · SELECT · CONFIRM · CANCEL · TRANSFORM · DETRANSFORM · ERROR ·
                       WARNING · COOLDOWN · READY · OVERHEAT · UNLOCK · LOCK · MASTER_CONTROL
TransformationManager  fragt OmnitrixCore.checkTransform, meldet onTransform/onRevert, prueft Hitze im Ablauf
OmnitrixFeedback (Client) Cue → Klang, Licht-Puls, Bildschirmblitz, Kamera-Stoss, FOV-Impuls
                       Wirkung: assets/.../omnitrix/feedback.json · Staerke je Spieler: config/kingdomomnitrix-omnitrix.json
OmnitrixController (Client) Bedien-Phasen; displayStatus() legt die Phase ueber den Server-Zustand
```

**Leistung:** kein zusaetzlicher Tick-Loop. Hitze = Wert + Zeitstempel, berechnet bei Bedarf; Pruefung nur fuer
verwandelte Spieler im bestehenden Ablauf; Client-Rueckmeldungen nur bei Zustandswechsel; Cues als 1-Byte-Pakete.

**Ablauf Ueberhitzung:** Verwandlung (+Hitze je Profil) → Hitze steigt als Alien → Warnschwelle: Meldung, Warnklang,
gelber Blitz, Geraet blinkt bernstein → 100 %: Zwangs-Rueckverwandlung, Alarm, roter Blitz, Kamera-Stoss, Sperre
(Profil: 12 s) → Geraet rot → kuehlt in Menschenform ab → „bereit“-Klang.

**Master Control (vorbereitet):** Flag je Spieler; Profil legt fest: keine Hitze, Nachladen ×0,25, Dauer ×3,
Bestaetigung 0,08 s, `quick_change`. Freischaltung ueber Progression folgt.

**Befehle (Admin/Test):** `/hero omnitrix status | use <alien> | heat <0..1> | lock <s> | master_control <bool> |
profile <id>` — `use` verwandelt mit allen Geraete-Regeln (anders als `/hero transform`, das sie umgeht).

## 3. Im Spiel geprueft (2026-10-02)

- Status-Befehl, Hitze 70 % → Verwandlung (+22 %) → Warnung (88 %) → 100 % → Zwangs-Rueckverwandlung, „ueberhitzt“,
  Abkuehlen (91 % nach 5 s), erneute Verwandlung verweigert.
- Sperre/Entsperren, Master Control an/aus, Profilwechsel-Befehl.
- Ego-Sicht mit gehobenem Omnitrix: Zifferblatt und Eck-Lichter je Zustand (bereit, gesperrt grau, Warnung bernstein,
  Bestaetigen hell) — `docs/screenshots/omnitrix_core_zustaende.png`; HUD-Hitzeleiste — `omnitrix_core_hud_hitze.png`.
- Keine Fehler im Log.

## 4. Fahrplan (Prioritaeten der Vorgabe)

| Prio | Bereich | Stand | Naechster Schritt |
|---|---|---|---|
| 1 | Core | **neu** | Upgrades als Profilwechsel (Item/Quest), Modi-Feld |
| 2 | Modell/Rendering | Licht je Zustand **neu** | Glas-Schicht ueber dem Zifferblatt, Metall-Glanz, Ueberhitzungs-Dampf |
| 3 | Aktivierung | Phasen + Cues | Arm-Hub mit leichtem Ueberschwingen, Kern-Ausfahr-Licht |
| 4 | Auswahl | Feder-Rad, Rastklang, **Favoriten-Sets, Schnellwahl-Kreis, Schnellwechsel** | **Smart-Scan mit Empfehlung + Smart-Wahl (N)** | Controller |
| 5 | Vorschau | Silhouette + Hologramm | Status-Chip (AlienStatus), Faehigkeiten-Leiste im Hologramm |
| 6 | Verwandlung | Blitzkugel, Blitz, Kamera | Energieaufbau-Phase sichtbar am Koerper, alien-eigene Sequenz (Datenfeld) |
| 7 | Rueckverwandlung | Blitzkugel, Cue | Energie-Rueckzug-Partikel zum Handgelenk |
| 8 | Energie/Cooldown | Hitze, **getrennte Lebenspunkte, DNA-Schock, Notfall-Verwandlung** | Konfig-UI, Ueberhitzung je Alien gewichten |
| 9 | DNA | Registry | Sounds/VFX/Transformation als Felder in `AlienDefinition` |
| 10 | Faehigkeiten | modular, AE-Posen | Liste der Vorgabe (z. B. Heatblast Fireball/Flame Dash) |
| 11 | VFX | Blitzkugel, Glut, Cues | Scanline/Hologramm-Shader, DNA-Partikel |
| 12 | Audio | **16 Omnitrix-Klaenge** | echte Aufnahmen koennen die OGGs ersetzen |
| 13 | Advanced | Fehlfunktionen, Randomizer | erst nach stabilem Kern |

## 5. Phase 2 — Phase A: Einstufung des Bestands (2026-10-02)

Legende: **VORHANDEN** fertig und im Spiel geprueft · **TEILWEISE** funktioniert, Teile fehlen · **PLATZHALTER** sichtbar,
aber nicht echt · **FEHLT** nicht gebaut · **SCHWACH** gebaut, Qualitaet unter Vorgabe · **FEHLER** falsches Verhalten.

| Bereich | Einstufung | Befund |
|---|---|---|
| Geraete-Kern (Hitze, Warnung, Ueberhitzung, Sperre, Profile) | VORHANDEN | im Spiel geprueft, Daten in `omnitrix_profile` |
| Master Control | TEILWEISE | nur Flag + Profilwerte per Befehl; keine Freischaltung, kein eigener Modus (→ Phase K) |
| Favoriten-Sets, Schnellwahl-Kreis, Schnellwechsel | VORHANDEN | geprueft (XLR8 → Heatblast) |
| Getrennte Lebenspunkte, DNA-Schock, Notfall-Verwandlung | VORHANDEN | geprueft; Darstellung schlicht (→ Phase I) |
| Smart-Scan (11 Regeln) + Smart-Wahl (N) | VORHANDEN | geprueft: Lava, Wasser, Panzer, Enge, Fernkampf; Fall-Fall **nicht** geprueft |
| Alien-Symbole (Icon, Silhouette) | **FEHLTE → jetzt VORHANDEN** | aus den echten Modellen gerendert (`tools/generate_alien_icons.py`), in Kreis, HUD, Alien-Menue, Freischalt-Meldung |
| Freischalt-Rueckmeldung | SCHWACH → verbessert | vorher nur Klang; jetzt Hologramm-Karte mit Scan-Effekt |
| Holo-Textmeldungen (Omnitrix OS) | FEHLT | → Phase I |
| Code-Eingabe | FEHLT | → Phase J |
| Kalibrier-Werkbank, Farbmodule | FEHLT | → Phasen L, M |
| Fehlfunktionen | FEHLT | → Phase H |
| Aliens | TEILWEISE | 5 von 10 der Vorgabe (Wildmutt, Stinkfly, Ripjaws, Upgrade, Ghostfreak fehlen; Modelle/Texturen = **REQUIRES ASSET**) |
| Faehigkeiten je Alien | SCHWACH | 3–4 statt 6 Rollen (BASIC … ULTIMATE) |
| Meisterschaft | TEILWEISE | Stufen 1–10 geben nur Dauer/Abklingzeit, keine Freischaltungen |
| Geraete-Modell (Glas, Glanz, Dampf, Ueberschwingen) | SCHWACH | Licht je Zustand vorhanden, Material-Feinschliff fehlt (→ Phase N) |
| Klaenge | PLATZHALTER | synthetisch erzeugt (`generate_sounds.py`), funktional, nicht final abgemischt |
| Mehrspieler-Sync | siehe Abschnitt 6 | |

**Groesste Qualitaetsluecke zu Beginn:** Das Omnitrix zeigte Aliens nur als Text. Rad, Kreis, HUD und Menue hatten kein
einziges Alien-Symbol — genau das, was ein Omnitrix ausmacht. Deshalb kam Phase B (Symbole) direkt nach der Analyse.

## 6. Phase A — Mehrspieler-Stichprobe und behobene Fehler (2026-10-02)

Dedizierter Server + 2 Clients (Tester, Tester2):

- Verwandlung, Rueckverwandlung, Alien-Modell (XLR8, Four Arms) fuer den zweiten Spieler sichtbar — Sync ueber
  Attachments funktioniert, keine Client-only-Zustaende gefunden.
- **FEHLER behoben:** Als Alien hatten Spieler fuer andere **zwei Namensschilder**. Ursache: GeckoLib 4.9.3 ruft
  `EntityRenderer.render` in `postRender` *und* `renderFinal` auf. `AlienBodyRenderer.postRender` ist jetzt leer.
- **FEHLER behoben:** In der Ego-Sicht fuellten Verwandlungs-Staub und Alien-Aura das Bild (blaue Pixelwolke vor der
  Kamera). Jetzt: andere sehen die Wolke wie bisher, der Spieler selbst einen Ring auf Fusshoehe und keine Aura.
- **FEHLER behoben (durch Phase B entstanden, vor dem Commit gefunden):** Symbol + langer Name lief im HUD in die
  Restzeit — Name wird bei Platzmangel schmaler gezeichnet.
- Nicht geprueft: Notfall-Verwandlung und Schnellwechsel im Mehrspieler (nur Einzelspieler).

## 7. Phase B — Alien-Symbole (2026-10-02)

- `tools/generate_alien_icons.py` rendert je Alien aus dem echten Modell + Textur: farbiges Symbol (Dreiviertel-Ansicht,
  Kontur) und weisse Silhouette (einfaerbbar). 64×64, `--check` in CI (toleriert Rundungsrauschen anderer Pillow-Versionen).
  Neue Aliens bekommen ihre Symbole automatisch, sobald `alien_render/<alien>.json` existiert.
- Schnellwahl-Kreis: Silhouette je Segment in Alien-Farbe (aktiv weiss, Nachladen grau), Mitte: farbiges Symbol +
  Name + Zustand; ohne Auswahl die Smart-Scan-Empfehlung als pulsierende goldene Silhouette.
- HUD: Symbol vor dem Alien-Namen (verwandelt), Silhouette in Geraete-Zustandsfarbe (Mensch).
- Alien-Menue: Symbol je Zeile, gesperrt nur dunkle Silhouette + „???“; grosses Symbol im Detailbereich.
- Freischaltung: Hologramm-Karte „DNA ERFASST“ — gruene Silhouette, Scanlinie, blendet ins farbige Symbol; ausgeloest
  vom synchronisierten Heldenzustand.
- Fremde Datenpaket-Aliens ohne Symbol: alle Stellen fallen auf den Namen zurueck (kein fehlendes-Textur-Muster).
- Offen: Symbol im 3D-Hologramm des grossen Rads (`OmnitrixScreen`) — dort steht weiterhin das 3D-Modell.

Bilder: `docs/screenshots/alien_symbole.png`, `docs/screenshots/omnitrix_phase2_symbole.png`.

## 8. Phase C/D — Smart-Scan erweitert, Smart Choice (2026-10-02)

- Neue Bedingungen: `submerged`, `drop_near` (Abgrund neben dem Spieler), `dark` (Lichtstufe), `target_is`,
  `entity_near` (Kreaturen-ID oder `#tag`), `block_near` (Block-ID oder `#tag`, Anzahl). Unbekannte Typen und fehlende
  Ziele lehnt das Datenpaket beim Laden ab (Test `scanRuleRejectsTyposAndMissingTarget`).
- Ziel-Erfassung: eigener Strahl bis 16 Bloecke (Waende verdecken) — vorher nur 3 Bloecke Spiel-Reichweite, ein Golem
  in 4 Bloecken wurde nicht erkannt (im Spiel gefunden und behoben).
- 17 Regeln. Neu: Golem/Verwuester/Waechter → Four Arms (`#kingdomomnitrix:heavy_hitters`), unter Wasser → Ripjaws,
  Abgrund/Fall/Flieger → Stinkfly, Redstone-Technik → Upgrade (`#kingdomomnitrix:technology`), Dunkelheit/Untote →
  Ghostfreak/Wildmutt, Enge → Ghostfreak. Gewichte fuer noch fehlende Aliens wirken erst, wenn sie freigeschaltet sind.
- **Smart Choice (N):** erster Druck → Hologramm „SMART CHOICE · Alien · EMPFOHLEN · Grund · [N] bestaetigen“ mit
  Symbol und Klang, zweiter Druck innerhalb 4 s verwandelt genau in das gezeigte Alien. Nie automatisch. Im
  Schnellwahl-Kreis bestaetigt N direkt (Empfehlung ist dort schon sichtbar), im grossen Rad dreht N nur hin.
- **Omnitrix-Hologramm-Meldung** (`OmnitrixHolo`, Grundlage fuer Phase I): klappt aus der Mitte auf, Text wird mit
  Scanlinie freigeschrieben, blendet aus; immer nur eine Meldung.
- Im Spiel geprueft: Golem in 5 Bloecken → Four Arms, Bestaetigung verwandelt (`docs/screenshots/smart_choice_golem.png`).
  Noch nicht geprueft: Redstone, unter Wasser, Abgrund, Dunkelheit (warten auf die Aliens aus Phase E).
