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
| 4 | Auswahl | Feder-Rad, Rastklang | Maus-Wischen, Controller, Schnellwahl-Favoriten |
| 5 | Vorschau | Silhouette + Hologramm | Status-Chip (AlienStatus), Faehigkeiten-Leiste im Hologramm |
| 6 | Verwandlung | Blitzkugel, Blitz, Kamera | Energieaufbau-Phase sichtbar am Koerper, alien-eigene Sequenz (Datenfeld) |
| 7 | Rueckverwandlung | Blitzkugel, Cue | Energie-Rueckzug-Partikel zum Handgelenk |
| 8 | Energie/Cooldown | **Hitze neu** | Konfig-UI, Ueberhitzung je Alien gewichten |
| 9 | DNA | Registry | Sounds/VFX/Transformation als Felder in `AlienDefinition` |
| 10 | Faehigkeiten | modular, AE-Posen | Liste der Vorgabe (z. B. Heatblast Fireball/Flame Dash) |
| 11 | VFX | Blitzkugel, Glut, Cues | Scanline/Hologramm-Shader, DNA-Partikel |
| 12 | Audio | **16 Omnitrix-Klaenge** | echte Aufnahmen koennen die OGGs ersetzen |
| 13 | Advanced | Fehlfunktionen, Randomizer | erst nach stabilem Kern |
