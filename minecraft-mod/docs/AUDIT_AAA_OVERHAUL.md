# AAA-Overhaul — Technische Audit-Analyse

made by SANTIQ · Stand 2026-10-03 · Grundlage: „KINGDOM OMNITRIX — AAA OVERHAUL MASTER PROMPT“, Abschnitt 30

Ist-Zustand = `docs/FUNKTIONEN.md`. Diese Analyse sagt je Bereich, was existiert, was funktioniert, was nur teilweise
fertig ist und wo der nächste sinnvolle Schritt liegt. Umfang des Codes: 27 Server-Pakete (~21 000 Zeilen),
21 Client-Pakete (~12 000 Zeilen), 33 Netzwerk-Pakete, 11 Testklassen, Asset-Generatoren in `tools/`.

Legende: **fertig** = gebaut und im Spiel geprüft · **teilweise** = funktioniert, Teile fehlen · **fehlt**.

---

## A — Omnitrix

| Teil | Status | Code | Befund |
|---|---|---|---|
| Gerätekern (Hitze, Warnung, Überhitzung, Sperre, Profile) | fertig | `omnitrix/OmnitrixCore`, `OmnitrixState`, `OmnitrixProfile` | datengetrieben (`omnitrix_profile`), ohne eigenen Tick (Hitze = Wert + Zeitstempel) |
| Bedien-Ablauf (Arm heben → öffnen → Rad → bestätigen → Schlag) | fertig | `client/render/omnitrix/OmnitrixController` (Phasen), `OmnitrixPhaseSyncPayload` | 13 Phasen; andere Spieler sehen Arm/Hub über `OmnitrixRemote` |
| 3D-Modell am Arm | fertig | `OmnitrixWrist`, `OmnitrixGeo` (AE-Prototyp, normal/schlank), `OmnitrixPolish`, `OmnitrixSteam` | Kern fährt mit Überschwingen aus, Drehverriegelung, Zifferblatt dreht beim Weiterschalten, Glas-/Metallglanz, Dampf bei Hitze, Farbmodul |
| First / Third Person | fertig | `OmnitrixWrist.renderOnArm` (Feature-Renderer) + Ego-Haltung aus `omnitrix/first_person.json` | gleiche Teile in beiden Ansichten; Haltung datengetrieben |
| Rad, Schnellwahl, Favoriten, Smart Choice, Codes, Master Control, Kalibrierung, Farben, OS-Hologramme, Fehlfunktionen | fertig | `client/screen/OmnitrixScreen`, `omnitrix/*` | siehe `OMNITRIX_CORE.md` §5–24 |
| Alien-Symbole überall | teilweise | `tools/generate_alien_icons.py` | im großen 3D-Rad steht das 3D-Modell statt Symbol (bewusst) |
| DNA-Archiv als eigenes Sammelsystem | teilweise | Alien-Menü, `DnaSampleItem` | Herkunft/Seltenheit/Fortschritt nur als Liste, kein Archiv mit Analyse |

**Gefundener Fehler (behoben in diesem Schritt):** `/hero alien unlock xlr8` speicherte `minecraft:xlr8` (Minecraft liest
Ids ohne Namensraum so) — Freischaltung eines nicht vorhandenen Aliens, Hologramm „alien.minecraft.xlr8“, falsche
Zählung. Jetzt: Ids ohne Namensraum gelten als `kingdomomnitrix:`, unbekannte Aliens werden abgelehnt (alle
`alien`-Argumente: unlock, lock, transform, omnitrix use, dna, mastery).

## B — Aliens

10 spielbare Aliens, alle mit 6 Fähigkeiten (Rollen basic … ultimate, Freischaltung ★1/★3/★5/★8), Eigenschaften
(`traits`), Uniformen classic/evo/ultimate, Symbolen, Ability-Posen (60 Slots), eigener Verwandlungs-Inszenierung
(`TransformStyle`).

| Quelle | Aliens |
|---|---|
| AE-Import (`tools/import_alienevo.py`, Erlaubnis laut SANTIQ) | Heatblast, XLR8, Vierarm, Diamondhead, Grey Matter, Wildmutt, Stinkfly, Ripjaws, Upgrade, Ghostfreak |
| Phase 2 (neu) | **Cannonbolt** — Kugelform als eigenes System: Kugel-Modell aus AE (`BALL`-Teil), Rollwinkel aus der Strecke, Rollen-Manager für Ramm-Schaden, Abprallen, Stufen-Steigen; Verwandlungsstil `ROLL` |
| **im AE-Inhalt verfügbar, noch nicht integriert** | Jetray (Aerophibian), Big Chill (Necrofriggian), Humungousaur (Vaxasaurian), Swampfire (Methanosian), Echo Echo (Sonorosian), Chromastone (Crystalsapien), Atomix, Dragonoid, Astrobot |

Fähigkeiten: `ability/BuiltinAbilities`, `CreatureAbilities`, `MasteryAbilities` (vier Bausteine + Sonderfälle),
alle Zahlen im Datenpaket. **Lücke:** alien-eigene *Systeme* (z. B. Kugelform, Klonen, Einfrieren) gibt es erst als
Fähigkeiten, nicht als dauerhafte Spielmechanik je Alien.

## C — Rendering

| Was | Wie |
|---|---|
| Omnitrix am Arm | Feature-Renderer am Spielermodell; GeckoLib-Geometrie manuell je Knochen gezeichnet (`OmnitrixGeo`), Lichter über Eyes-Layer |
| Alien-Körper | `render/alien/AlienBodyRenderers` (GeckoLib), Mixins ersetzen das Spielermodell; Glowmask, Abzeichen-Umfärbung (`BadgeTint`) |
| Hologramme | `AlienHologram`, `OmnitrixDialDisplay`, OS-Karten im HUD |
| Bosse, Herzlose, NPCs, Schiff | GeckoLib-Renderer, Modelle aus Python-Generatoren |

## D — Aphelion

`space/ShipEntity`, `ShipAi`, `client/space/ShipClient`, `ShipHud`: Einsteigen, Flug (W/S/A/D, Steigen), 2 Plätze,
Bord-KI-Sprüche, Cockpit-Anzeige (Tempo, Höhe, Navigation zu Rissen). **Fehlt:** Zielauswahl-Menü, Start-/
Lande-Sequenz, Innenraum, Upgrades, Fluggefühl (Trägheit/Kamera).

## E — Space

`space/SpaceTravel` (Aufstieg ab Bauhöhe −16, Wiedereintritt, geringe Schwerkraft), Riss-Reise über
`space_route/*.json` (Datenpaket: Ziel-Dimension, Ort im All, Farbe, Radius, Ankunft) → **bereits datengetrieben**.
Himmel (`SpaceSkyRenderer`) mit Nebeln, Sonne, Gasriese; fünf Asteroidenarten.
**Behobener Fehler (vorher):** Wiedereintritt veränderte die Spielerliste während der Schleife (Server-Absturz).

## F — World / Dimension

Zwei eigene Dimensionen per Datenpaket (`dimension/space.json`, `traverse_town.json`, Biome unter
`worldgen/biome/`), Stadt/Dungeon im Code (`world/TraverseTown`, `dungeon/*`). **Lücke:** keine gemeinsame
„Welt-Definition“ (Musik, Atmosphäre, Gegner, Quests, Ressourcen, Bosse je Welt) — jede Welt ist heute Code +
Datenpaket-Teile ohne gemeinsames Schema. Ansatzpunkt: `space_route` um eine Welt-Definition erweitern.

## G — Networking

33 Pakete (`networking/`), Zustände als Fabric-Attachments mit Sync an alle (`OmnitrixState`, Kalibrierung,
Gadget-Gürtel, Transformation) bzw. nur an den Besitzer (Heldendaten). Kampf-Animationen und Schwung-Spur per
`CombatAnimationPayload` an Beobachter; Treffer-Rückmeldung nur an den Angreifer. Kein Befund mit Handlungsbedarf.

## H — Performance

- Tick-Handler: 13 Server- (`END_SERVER_TICK`/`END_WORLD_TICK`), 17 Client-Handler. Alle laufen nur über Spieler oder
  aktive Objekte (Ereignis, Arena, Dungeon), keine Weltscans pro Tick.
- Entity-Abfragen (`getEntitiesByClass`/`getOtherEntities`) nur ereignisgesteuert (Fähigkeit, Zauber, Treffer);
  Ausnahme Client-`SmartScan` (läuft nur bei geöffnetem Rad/Schnellwahl) und `RiftSpawner` (seltene Prüfung).
- Partikel: Server sendet gezielt; Client-Effekte (Schadenszahlen, Spur) mit Obergrenzen (48 / 32 Einträge).
- Kein akuter Engpass gefunden. Beobachten: `AlienBodyRenderers`-Tick (alle Spieler), `CombatManager.tick`.

## I — Tests

11 JUnit-Klassen: Datenpakete (`DataPackTest`), Omnitrix (Zustand, Kalibrierung, OS, Fehlfunktionen, Master
Control, Scan-Regeln), Transformation, Meisterschaft, Kampf-Rückmeldung, Dungeon-Grundriss. Dazu `check_assets.py`
und `--check` aller Generatoren in der CI. **Lücke:** keine automatischen Client-/Mehrspieler-Tests — die laufen über
`tools/client_smoke.sh` und `tools/multiplayer_test.sh` von Hand.

---

## Folgerung für die Phasen

| Phase | Ausgangslage | nächster Schritt |
|---|---|---|
| 1 Omnitrix-Modell/Rendering | weitgehend fertig | Feinschliff nach Sichtprüfung; Fehler im Freischalt-Befehl behoben |
| 2 Alien-Roster | 11 Aliens fertig (Cannonbolt ✔), 9 weitere im AE-Inhalt | Jetray, Big Chill, Humungousaur |
| 3 Alien-Qualität | Fähigkeiten/Posen vorhanden | alien-eigene Dauer-Systeme (Kugelform, Klone, Einfrieren) |
| 4 Transformationen | `TransformStyle` je Alien vorhanden | Kamera/Licht je Stil verfeinern |
| 5 UI/OS/DNA | OS + Symbole fertig | DNA-Archiv (Herkunft, Seltenheit, Analyse, Fortschritt) |
| 6–7 Aphelion/Weltall | Flug + Risse + Himmel | Zielauswahl, Start/Landung, Fluggefühl |
| 8–9 Welt-System/Welten | 2 Dimensionen, Routen datengetrieben | Welt-Definition als Datenpaket, dann neue Welt |
| 10–12 | — | Integration, Leistung, Feinschliff |
