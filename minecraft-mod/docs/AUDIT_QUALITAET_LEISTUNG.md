# KINGDOM OMNITRIX — Prüfbericht Qualität und Leistung

made by SANTIQ · Stand 2026-10-02 · Basis: Commit `ee51f67`, Fabric 1.21.1, GeckoLib 4.9.3

## 1. Kennzahlen

| Messgröße | Wert | Bewertung |
|---|---|---|
| Java-Code | 23 449 Zeilen, 157 Klassen Server/gemeinsam, 70 Client | groß, klar in Pakete getrennt |
| Werkzeuge (Python) | 7 212 Zeilen (Generatoren, Import, Renderer, Prüfungen) | reproduzierbar, CI prüft 15 Generatoren |
| Automatische Tests | **0** | **kritisch**, siehe Q1 |
| Server-Tickzeit (Einzelspieler, Dev) | Ø 3,4 ms Mensch / 2,9 ms als Heatblast (P99 11 ms), Budget 50 ms | gut |
| FPS (Cloud, Software-Renderer) | 41 Mensch / 51 Alien | **nicht aussagekräftig** (llvmpipe), echte GPU-Messung fehlt |
| Tick-Handler | 10 Server, 11 Client | alle steigen früh aus oder laufen in Intervallen |
| Jar | 2,4 MB (Texturen 1,4 MB, Geo 1,2 MB, Sounds 0,8 MB) | in Ordnung |
| Log beim Start | nur bekannte Meldungen (fehlende Data-Fixer für Mod-Entities, Tag-Übersetzungen, GeckoLib-Refmap im Dev) | sauber |

## 2. Qualität — Befunde nach Gewicht

**Q1 (kritisch) Keine automatischen Tests.** Reine Logik wird nur von Hand im Spiel geprüft: Hitze-Formel
(`OmnitrixState.heatAt/withHeat`), `TransformationState` (Dauer, Energie, Abklingzeit), `AlienMastery`-Stufen, Codecs der
Datenpakete (Profile, Aliens), Status-Ableitung (`OmnitrixCore.status` ohne Welt nicht testbar), Python-Werkzeuge
(`parse_poses`, Palettenauflösung, Bone-Mapping). Jede Änderung am Kern kann still brechen.

**Q2 (hoch) Doppelter JSON-Lesecode für `alien_render/<alien>.json`.** `AlienBodyRenderers` liest dieselbe Datei an
6 Stellen in 4 Caches (Uniformen, Uniform-Modelle, Pose, Textur-Animation, Größe). Fehleranfällig, jedes neue Feld
kopiert Code. Ziel: ein `AlienRenderInfo`-Record mit Codec, einmal pro Modell und Reload geladen.

**Q3 (mittel) Status wird mehrfach pro Bild neu berechnet.** `OmnitrixCore.status()` (inkl. Inventar-Scan
`hasOmnitrix`, 11 Aufrufer) läuft im HUD, am Handgelenk, im Zifferblatt und in `OmnitrixFeedback.tick`. Ergebnis ist
pro Tick konstant → einmal pro Client-Tick cachen (`OmnitrixClientState`).

**Q4 (mittel) Kleine Speicherlecks auf dem Client.** `AlienBodyAnimatable.lastTrigger` (Map nach Entity-ID) wird nie
geleert; GeckoLibs Instanz-Cache für ersetzte Spieler wächst mit jeder neuen Entity-ID der Sitzung. Bei langen
Multiplayer-Sitzungen messbar. Lösung: beim Entladen/Verlassen und beim Trennen leeren.

**Q5 (mittel) Altlasten.** Fünf eigene Alien-Baupläne (~1 000 Zeilen in `generate_alien_models.py`) sind seit dem
AE-Import nur noch `--legacy`; `sample_reference.py`-Checks in der CI prüfen Vorlagen, die nicht mehr genutzt werden.
Entweder entfernen oder klar als Archiv markieren und aus der CI nehmen.

**Q6 (niedrig) Uneinheitliche Texte.** Zwei deutsche Literale in `HeroCommand` statt Übersetzungsschlüsseln; fehlende
Übersetzungen für Item-Tags (Fabric-Warnung beim Start).

**Q7 (niedrig) Große Klassen.** `NefariousEntity` (777 Z.), `CommandMenu` (490), `OmnitrixController` (423) — außerhalb
des Omnitrix-Fokus zurückstellen; `OmnitrixController` beim nächsten Omnitrix-Schritt in Eingabe/Animation/Ablauf teilen.

**Q8 (niedrig) Rechtliches.** AE-Inhalte mit Erlaubnis laut SANTIQ (`CREDITS.md`), Nachweis muss SANTIQ aufbewahren;
AE-Sounds bewusst nicht übernommen.

## 3. Leistung — Befunde

| Nr | Stelle | Kosten | Maßnahme |
|---|---|---|---|
| P1 | `OmnitrixCore.status/heat` pro Bild mehrfach | klein je Aufruf, wächst mit Spielern in Sicht | Tick-Cache (Q3) |
| P2 | `AlienPose.apply` je Alien und Bild: Vanilla-Pose berechnen, Arrays anlegen | gering, steigt linear mit Aliens in Sicht | Arrays wiederverwenden, Pose nur bei sichtbarem Alien |
| P3 | `AlienBodyRenderers.animatedTexture` erzeugt pro Bild neue `Identifier` | Garbage pro Bild | Frame-/Warn-Identifier je Modell vorbereiten (Array) |
| P4 | Aura-Partikel alle 5 Ticks je verwandeltem Spieler, an alle in Reichweite | Netzwerk mit vielen Spielern | Intervall 10, oder Client-seitig aus dem synchronisierten Zustand erzeugen |
| P5 | `removeAttributes` iteriert alle Attribute der Registry | nur bei Wechsel | in Ordnung |
| P6 | Server-Tick-Handler | alle mit Frühausstieg/Intervall | in Ordnung |
| P7 | Texturen: Heatblast 8 Frames × 3 Uniformen × 2 (Warn) | 704 KB, AutoGlowing erzeugt je Datei eine Emissiv-Textur | akzeptabel; bei mehr Aliens Atlas/Anim-Mcmeta prüfen |

**Fehlende Messung:** echte FPS auf GPU mit 1/4/8 Aliens in Sicht und Spark-Profil des Servers mit 4 Spielern. Die
Cloud rendert per Software und taugt nur für Funktionsprüfungen.

## 4. Was gut ist (beibehalten)

Datengetriebene Registries (Aliens, Profile, Fähigkeiten, Quests …), synchronisierte Attachments statt eigener
Pakete, Generatoren mit `--check` in der CI, Omnitrix-Kern ohne Tick-Loop (Hitze per Zeitstempel), zentrale
Rückmeldungen (`OmnitrixCue` → `feedback.json`), Spieler-Config für Kamera/Blitz, saubere Fehlerbehandlung beim Laden
(kein Absturz, Standardwerte + Log).

## 5. Verbesserungen in Reihenfolge

1. **Tests einführen** (JUnit 5 über Fabric Loom, ohne Spielstart für reine Logik; Python `unittest` für Werkzeuge) und
   in die CI hängen.
2. **`AlienRenderInfo`** zusammenführen (Q2) — Voraussetzung für jedes weitere Alien-Feld.
3. **`OmnitrixClientState`**: Status, Hitze, Profil einmal pro Tick (Q3/P1).
4. **Lecks schließen** (Q4), **Garbage im Render-Pfad** reduzieren (P2/P3).
5. **Altlasten** entscheiden (Q5), Texte/Übersetzungen (Q6).
6. Danach weiter nach Omnitrix-Fahrplan (`docs/OMNITRIX_CORE.md`): Modell/Glas/Metall, Aktivierung, Auswahl, Vorschau.
