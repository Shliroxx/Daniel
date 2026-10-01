# KINGDOM OMNITRIX — Bestandsaufnahme und technische Roadmap

made by SANTIQ · Minecraft 1.21.1 · Fabric · Stand: 2026-10-01

Status-Begriffe in diesem Dokument:

| Begriff | Bedeutung |
|---|---|
| **IMPLEMENTED** | Läuft vollständig: Logik, Speicherung, Multiplayer, UI, Feedback, Fehlerbehandlung |
| **PROTOTYPE** | Läuft, ist aber noch nicht endgültig |
| **PLACEHOLDER** | Vorläufiger Inhalt, wird ersetzt |
| **TODO** | Noch nicht gebaut |
| **UNVERIFIED** | Code ist geschrieben, aber nie kompiliert oder im Spiel getestet |

---

## 0. Harter Blocker: Der Build ist nicht geprüft

Für die bestehende Version (`minecraft-mod/`, 1 420 Zeilen Java) lief **nie** ein `./gradlew build`.
Das Netzwerk dieser Entwicklungsumgebung sperrt alle Server, die Fabric braucht:

| Host | Wofür | Status (geprüft 2026-10-01) |
|---|---|---|
| `maven.fabricmc.net` | Loom, Loader, Fabric API, Yarn | gesperrt (403 am Proxy) |
| `piston-meta.mojang.com` | Minecraft-Versionsliste | gesperrt |
| `libraries.minecraft.net` | Minecraft-Bibliotheken | gesperrt |
| `maven.terraformersmc.com`, `dl.cloudsmith.io` | mögliche Abhängigkeiten (GeckoLib, Mod Menu …) | gesperrt |

Was trotzdem geprüft wurde:
- Jeder Aufruf an Minecraft und die Fabric API wurde einzeln gegen die Yarn-Mappings `1.21.1` und den Quellcode der Fabric API (Branch `1.21.1`) abgeglichen.
- Ein `javac`-Lauf ohne Bibliotheken zeigt keine Syntaxfehler.
- Ein Skript prüft, dass jeder Übersetzungsschlüssel, jedes Modell, jede Textur und jede Rezeptzutat vorhanden ist.

**Folge für die Roadmap:** Laut Qualitätsregel ist eine Phase erst fertig, wenn `./gradlew build` grün ist.
Solange die Hosts gesperrt sind, kann hier keine Phase diesen Status erreichen. Zwei Auswege:

1. **Empfohlen:** Die Hosts oben in den Umgebungseinstellungen freigeben (Network access → Allowed domains).
2. Nach jeder Phase lokal bauen (`Mod bauen.bat`) und die Fehlerausgabe zurückgeben.

Zusätzlich empfohlen: ein GitHub-Actions-Workflow, der die Mod bei jedem Push baut. GitHub-Runner erreichen Maven.
Dann hat jeder PR einen echten Build-Nachweis, egal wie diese Umgebung eingestellt ist. → Phase 2, Schritt 1.

---

## Entscheidungen (2026-10-01)

| Frage | Entscheidung |
|---|---|
| Build-Prüfung | Hosts werden freigegeben; zusätzlich baut GitHub Actions bei jedem Push |
| Mod-ID / Paket | `kingdomomnitrix` / `com.santiq.kingdomomnitrix` |
| Bibliotheken | GeckoLib + playerAnimator — eingebunden in der Phase, die sie zuerst nutzt (3 bzw. 4) |
| Ablauf | Phase für Phase, nach jeder Phase Prüfung durch SANTIQ |
| Alien-Aussehen | echter, animierter Alien-Körper (GeckoLib), für alle sichtbar |
| Alien-Freischaltung | DNA-Proben von Gegnern (Quellen pro Alien im JSON) |
| Diamondhead, Grey Matter | ins neue System übernommen, Status PROTOTYPE |

---

## 1. Bestandsaufnahme: jedes System bewertet

### Übersicht

| System | Ist-Zustand | Status | Urteil |
|---|---|---|---|
| Fabric-Setup | Loom 1.10, Loader 0.16.14, API 0.116.16+1.21.1, Yarn build.3, Gradle 8.14.3 | UNVERIFIED | **KEEP** + Paket umbenennen |
| Aliens | `enum Alien` mit `switch` je Fähigkeit | PROTOTYPE | **REPLACE** |
| Transformation | Statuseffekt pro Alien trägt den Zustand | PROTOTYPE | **REPLACE** |
| Omnitrix | Item, Rechtsklick/Schleichen, Nachladen im Item-NBT | PROTOTYPE | **REFACTOR** |
| Keyblade | `SwordItem` + Zauber über Rechtsklick | PROTOTYPE | **REPLACE** (Kampf), **KEEP** (Item-Hülle) |
| Magie | `enum Spell` mit `switch`, nur Abklingzeit, kein MP | PROTOTYPE | **REPLACE** |
| Heartless | 1 Mob (Shadow), Zombie-Unterklasse | PROTOTYPE | **REFACTOR** |
| Projektile | 1 Entity, Art wird aus dem Item-Stack abgelesen | PROTOTYPE | **REFACTOR** |
| Bolts | Item, Drop-Event, Kiste | PROTOTYPE | **REFACTOR** (Bolt-Konto) |
| Combuster | Haltbarkeit = Munition | PROTOTYPE | **REPLACE** (Waffen-Komponente) |
| Fusionsgranate | Wurf-Entity, Explosion ohne Blockschaden | PROTOTYPE | **KEEP** |
| OmniWrench | Schwert, Wurf ohne Rückkehr | PROTOTYPE | **REFACTOR** |
| Heli-Pack | Item in der Zweithand | PROTOTYPE | **REFACTOR** (Gadget-Slot) |
| Rezepte | 9 JSON-Rezepte | IMPLEMENTED (UNVERIFIED) | **KEEP**, später zum Teil durch Shop ersetzt |
| Loot | 2 Loot-Tabellen + Code-Drop für Bolts | IMPLEMENTED (UNVERIFIED) | **KEEP** |
| Texturen | 23 prozedurale Pixel-PNGs | PLACEHOLDER | **REPLACE** (Generator bleibt für Platzhalter) |
| Übersetzung | de_de + en_us, 53 Schlüssel, vollständig | IMPLEMENTED | **KEEP** |
| Kreativ-Tab | 1 Tab | PROTOTYPE | **REFACTOR** (mehrere Tabs) |
| Networking | nicht vorhanden | TODO | **NEU** |
| Speichersystem | nur Item-NBT (Zauberwahl, Alienwahl, Nachladezeit) | TODO | **NEU** |
| HUD / UI | nur Actionbar-Texte und Tooltips | TODO | **NEU** |
| Lock-On, Combos, Dodge, Guard | nicht vorhanden | TODO | **NEU** |
| Sounds | nur Vanilla-Sounds, keine `sounds.json` | PLACEHOLDER | **NEU** (Sound-Architektur) |
| Advancements, Befehle, Quests, Dialoge, NPCs, Welten, Bosse, Party, Progression | nicht vorhanden | TODO | **NEU** |

### Begründungen im Einzelnen

**Fabric-Setup — KEEP.** Die Versionen passen zu 1.21.1. Ändern:
- Paket `com.daniel.heroverse` → `com.santiq.kingdomomnitrix`.
- Mod-ID: **entschieden → `kingdomomnitrix`** (umgesetzt in Phase 2).
- `loom { splitEnvironmentSourceSets() }` einführen. Dann kann Client-Code nicht mehr versehentlich auf einem Dedicated Server landen. Der Compiler erzwingt die Trennung.

**Aliens — REPLACE.** `item/Alien.java:22` ist ein `enum`, Fähigkeiten hängen an einem `switch` (`Alien.java:82`).
Ein neues Alien bedeutet heute Änderungen an vier Stellen (Enum, Switch, `ModEffects`, Partikel-Switch in `AlienEffect`).
Das widerspricht direkt der Anforderung „modular, beliebig erweiterbar“. Ersatz:
- `AlienDefinition` aus JSON: Werte, Attribute, Hitbox-Skalierung, Zuordnung der Fähigkeiten zu Slots, Dauer, Energie, Freischalt-Bedingung.
- Fähigkeiten als Java-Klassen in einer `AbilityType`-Registry. Das JSON verweist per ID darauf und übergibt Parameter.

**Transformation — REPLACE.** Der Zustand steckt heute in einem Statuseffekt. Das bringt drei echte Fehler:
1. **Milch** beendet die Verwandlung, und die Nachladezeit läuft trotzdem.
2. Die Nachladezeit liegt im Item-NBT (`OmnitrixItem.java:85`). Mit zwei Omnitrixen umgeht man sie.
3. Vierarm mit Skalierung ×1,5 (`ModEffects.java:33`) passt nicht durch 2 Blöcke hohe Gänge. Ohne Platzprüfung erstickt der Spieler.

Ersatz: `TransformationManager` auf dem Server. Der Zustand liegt in einer Spieler-Attachment (Fabric Data Attachment API). Die Attribute setzt der Manager direkt. Vor jeder Größenänderung prüft er den Platz.

**Omnitrix — REFACTOR.** Das Item bleibt als Auslöser und Darstellung. Die Bedienung zieht um:
- Taste (Standard `G`) öffnet das Alien-Rad, Taste `R` löst die Fähigkeiten-Slots aus.
- Der Client schickt nur die Absicht als Paket, der Server prüft und führt aus.
- Energie, Dauer, Nachladezeit und Freischaltungen liegen beim Spieler, nicht im Item.

**Keyblade — REPLACE (Kampf), KEEP (Item).** Ein `SwordItem` mit Rechtsklick-Zaubern kann keine Leicht-/Schwer-Angriffe, Combos, Ausweichen, Blocken oder Lock-On.
Neu wird ein eigenes Kampfsystem gebaut. Die Werte des Keyblades (Schaden, Magiekraft, Tempo, Reichweite, Passiv, Seltenheit, Stufe) kommen aus JSON und als Item-Komponente.

**Magie — REPLACE.** `Spell.java:62` ist ebenfalls ein `switch` ohne MP. Ersatz:
- `SpellDefinition` aus JSON: MP-Kosten, Abklingzeit, Stufen, VFX- und Sound-IDs.
- Die Wirkung als `SpellEffect`-Klasse in einer Registry.
- MP liegt in der Spieler-Attachment und wird an den Client synchronisiert.

**Heartless — REFACTOR.** Spawn-Prüfung (Dunkelheit + fester Boden) und Loot sind brauchbar und bleiben. Neu:
- Gemeinsame Basisklasse `HeartlessEntity` mit eigener KI (Ziele, Angriffsmuster, Telegraphing).
- Eigene Modelle. Die Zombie-Unterklasse erbt Zombie-Eigenheiten (Verstärkungen rufen, Baby-Varianten, Hühnerreiter).
- Spawn-Regeln aus JSON (Dimension, Biom, Tageszeit, Story-Fortschritt, Spielerlevel). Heute ist es ein fester Eintrag (`ModEntities.java:42`).

**Projektile — REFACTOR.** `HeroProjectileEntity.getKind()` (`HeroProjectileEntity.java:63`) liest die Art aus dem angezeigten Item. Das ist ein Hack.
Ersatz: Die Projektil-Daten (Schaden, Effekte, Explosion, Schwerkraft, Lebensdauer, VFX) werden per `DataTracker` mitgegeben. Der Renderer bleibt.

**Bolts — REFACTOR.** Das Item bleibt als sichtbarer Drop. Beim Aufsammeln wandert der Wert auf ein Bolt-Konto (Attachment, im HUD sichtbar). Shops und Upgrades buchen davon ab. Das Drop-Event bleibt, nur die Zielfunktion ändert sich.

**Combuster — REPLACE.** Haltbarkeit als Munition (`CombusterItem.java:54`) schließt Upgrades, Feuerrate und Magazin pro Stufe aus. Ersatz:
- Allgemeine `WeaponDefinition` (JSON) und eine `WeaponState`-Komponente am Stack (Stufe, Munition, Erfahrung).
- Gemeinsame Schuss-Pipeline mit Rückstoß-, VFX- und Sound-Haken.

**Fusionsgranate — KEEP.** `ExplosionSourceType.NONE` lässt Blöcke stehen. Das erfüllt die Vorgabe. Sie wird nur an die Waffen-Pipeline angeschlossen (Stufen, Radius).

**OmniWrench — REFACTOR.** Neu:
- Der geworfene Schlüssel kehrt zurück.
- Er löst Mechanismen aus: Bolt-Schrauben, Hebel, Knöpfe, eigene Mechanismus-Blöcke.

**Heli-Pack — REFACTOR.** Die Zweithand ist eine Notlösung (`HeliPackItem.java:32`). Neu:
- Gadget-Slots am Spieler.
- Steuerung über Taste und Sprung-Eingabe am Client (Gleiten, Doppelsprung, Luftschub). Der Server prüft nur Grenzen.

**Texturen — REPLACE.** Die 16×16-Pixelbilder sind Platzhalter. `tools/generate_textures.py` bleibt, damit neue Inhalte sofort sichtbar sind.
Echte Assets entstehen in Blockbench: 3D-Itemmodelle für Keyblades und Waffen, GeckoLib-Modelle für Heartless, Bosse und Alien-Körper.

---

## 2. Zielarchitektur

```
com.santiq.kingdomomnitrix
├── KingdomOmnitrix                 Haupteinstieg, nur Aufrufe der Bootstrap-Klassen
├── core/                           Registries, IDs, Logging, Konfiguration
├── data/                           JSON-Loader (ResourceReloadListener) + Codecs + Sync an Clients
├── player/                         HeroData (Attachment): Level, Werte, MP, Bolts, Freischaltungen,
│                                   Story-Flags, Quests, Gadget-Slots, Transformations-Zustand
├── networking/                     Payloads (C2S-Absichten, S2C-Zustand), Ratenbegrenzung
├── alien/                          AlienDefinition, AlienRegistry, TransformationManager, Omnitrix
├── ability/                        AbilityType-Registry, AbilityContext, Abklingzeiten
│   └── impl/                       Feuerstrahl, Dash, Wandlauf, Bodenschlag, Kristallwand …
├── combat/                         Angriffs-Pipeline, Combo-Graph, Ausweichen, Blocken, Treffer-Stopp,
│                                   Lock-On (Server prüft), Finisher
├── keyblade/                       KeybladeDefinition, Komponente, Stufen
├── magic/                          SpellDefinition, SpellEffect-Registry, MP
├── weapon/                         WeaponDefinition, WeaponState-Komponente, Schuss-Pipeline, Upgrades
├── gadget/                         Heli-Pack, Swingshot, Grindboots, Hydro-Pack …
├── enemy/                          HeartlessEntity-Basis, KI-Bausteine, Spawn-Regeln (JSON)
├── boss/                           BossEntity, Phasen-Automat, Telegraphing, Arena, Bossbar
├── quest/                          QuestDefinition (JSON), Ziel-Typen, Tracker, Belohnungen
├── dialogue/                       Dialog-Baum (JSON), Bedingungen, Aktionen
├── cutscene/                       Cutscene-Skript (JSON): Kamera, Text, Fade, Teleport, Sound
├── npc/                            NPC-Entity, Händler, Shop-Definitionen
├── world/                          Dimensionen, Portale, Weltkarte, Hub-Strukturen
├── party/                          Begleiter-Entity, KI, Befehle
├── progression/                    Level, Erfahrung, Werte, Fähigkeiten-Punkte
├── command/                        /hero … (nur OP)
└── client/                         nur im Client-Source-Set
    ├── hud/                        HUD-Bausteine (HP, MP, Bolts, Alien, Munition, Quest, Lock-On)
    ├── screen/                     Alien-Rad, Weltkarte, Shop, Dialog, Quest-Log, Inventar-Tabs
    ├── input/                      Tastenbelegungen
    ├── render/                     Entity-Renderer, Alien-Körper statt Spielermodell, Spuren
    ├── vfx/                        Partikel-Presets
    └── cutscene/                   Kamera-Steuerung
```

### Grundregeln

1. **Server entscheidet.** Der Client schickt nur Absichten („Fähigkeit 2 auslösen, Ziel #123“). Der Server prüft Abklingzeit, Energie, Reichweite und Sichtlinie.
   Ausnahme ist die Spielerbewegung (Dash, Doppelsprung, Gleiten). Sie läuft im Client, weil Minecraft die Bewegung dort berechnet. Der Server prüft dabei Grenzen gegen Missbrauch.
2. **Ein Zustand, eine Stelle.** Alles Spielerbezogene liegt in `HeroData` (Fabric Data Attachment API, `persistent(codec)`).
   In 1.21.1 synchronisiert die Attachment-API noch nicht von selbst. Deshalb gibt es eigene S2C-Pakete, nur bei Änderungen und nur für Felder, die der Client sehen muss.
3. **Daten in JSON, Verhalten in Java.** Aliens, Keyblades, Zauber, Waffen, Gadgets, Quests, Dialoge, Bosse, Shops und Spawn-Regeln sind JSON unter `data/<modid>/…`.
   Das Verhalten (Fähigkeiten, Zaubereffekte, Boss-Angriffe, Quest-Ziele) sind registrierte Java-Typen, die das JSON per ID und Parameter auswählt.
   Neues Alien = eine JSON-Datei + Assets. Neue Fähigkeit = eine Java-Klasse.
4. **Keine Tick-Schleifen über alle Entities.** Spieler-Ticks laufen nur für Spieler mit aktivem Zustand. Gesucht wird nur in kleinen Boxen. Bosse ticken ihre Phasen-Logik selbst.
5. **Client-Code nur im Client-Source-Set** (`splitEnvironmentSourceSets`).

### Abhängigkeiten (Entscheidung nötig)

| Bibliothek | Wofür | Empfehlung |
|---|---|---|
| **GeckoLib 4** (1.21.1) | animierte Modelle für Heartless, Bosse, Alien-Körper, Clank | **ja** — ohne sie kein ernstzunehmendes Animationssystem |
| **playerAnimator** (KosmX) | Spieler-Animationen für Keyblade-Combos, Dodge, Wurf | **ja** — Vanilla kann Spieler-Arme nicht frei animieren |
| Mod Menu + Cloth Config | Einstellungsmenü | optional, später |

Beide Kern-Bibliotheken liegen auf Maven-Servern, die hier gesperrt sind (siehe Abschnitt 0).

---

## 3. Vertical Slice — Umfang und Abnahme

Die Vertical Slice ist der erste Meilenstein. Alles danach baut auf ihr auf.

| Bereich | Inhalt | Abnahme (alle 8 Qualitätsregeln) |
|---|---|---|
| Hub | 1 Traverse-Town-artige Dimension: Platz, 3 Gebäude, Händler, Portal zurück | per Portal erreichbar, Position wird gespeichert, NPCs vorhanden |
| Aliens | Heatblast, XLR8, Vierarm — je 3 Fähigkeiten-Slots | Rad-Menü, Verwandlung mit Effekt, HUD, Dauer/Nachladen/Energie, Multiplayer sichtbar, übersteht Neuanmeldung |
| Keyblades | Kingdom Key, Oathkeeper-artiges Zweitschwert | Leicht/Schwer/3er-Combo/Luft-Combo, Dodge, Guard, Finisher, Lock-On mit UI |
| Magie | Feuer, Eis, Donner, Vita | MP-Leiste, Kosten, Abklingzeit, VFX, Sound, 3 Stufen |
| Waffen | OmniWrench (Wurf + Rückkehr), Combuster, Fusionsgranate | Munition, 5 Stufen, Kauf beim Händler, HUD-Anzeige |
| Gadgets | Heli-Pack, Swingshot | Gadget-Slot, Taste, sichtbare Wirkung |
| Heartless | Shadow, Soldier, Large Body, Air Soldier, Darkball | eigene KI-Muster, Spawn-Regeln aus JSON |
| Boss | 1 Boss mit 2 Phasen (Guard-Armor-artig, zerlegbare Teile = Schwachstellen) | Telegraphing, Phasenwechsel, Enrage, Bossbar, Arena-Sperre, Loot |
| Quests | 5 (1 Story, 1 Neben, 1 Kopfgeld, 1 Boss, 1 Erkundung) | Quest-Log, HUD-Tracker, Belohnung, gespeichert |
| HUD | HP, MP, Level, Bolts, Alien, Munition, Quest, Lock-On | skalierbar, abschaltbar |
| Speichern | gesamter `HeroData`-Zustand | Neustart- und Rejoin-Test |

**Bewusst nicht in der Vertical Slice:** Party, Welten außer Hub, Cutscene-Kamera (nur Text + Fade), Diamondhead/Grey Matter, Endgame-Waffen.

---

## 4. Roadmap in Phasen

Jede Phase endet mit `./gradlew build` grün (siehe Abschnitt 0), einem Testprotokoll und einer Status-Tabelle (IMPLEMENTED/PROTOTYPE/PLACEHOLDER/TODO).

| # | Phase | Ergebnis | Hängt ab von |
|---|---|---|---|
| 1 | Analyse | dieses Dokument | — |
| 2 | Architektur | Paket-Umbau, Split Source Sets, CI-Build-Workflow, `HeroData`-Attachment mit Codec und Sync, Status-HUD, `/hero`-Befehle | Build-Zugang |
| 3 | Omnitrix | `AlienDefinition` + `AbilityType`-Registry, `TransformationManager`, Rad-Screen, Tasten, HUD-Baustein, Heatblast/XLR8/Vierarm mit je 3 Fähigkeiten, Platzprüfung bei Größe, `/hero transform` | 2 |
| 4 | Kampf | Angriffs-Pipeline, Combo-Graph, Dodge mit Unverwundbarkeits-Fenster, Guard, Lock-On (Auswahl, Wechsel, UI, Entfernung), Treffer-Feedback | 2 |
| 5 | Keyblades | `KeybladeDefinition`, 2 Keyblades, Stufen, Finisher | 4 |
| 6 | Magie | MP, `SpellDefinition`, 4 Zauber mit 3 Stufen | 2, 4 |
| 7 | Heartless | Basisklasse, 5 Typen, KI-Bausteine, Spawn-Regeln aus JSON, GeckoLib-Modelle (Platzhalter-Geometrie) | 2 |
| 8 | Waffen | `WeaponDefinition`, Komponente, Schuss-Pipeline, 3 Waffen, 5 Stufen, Bolt-Konto | 2 |
| 9 | Gadgets | Slots, Heli-Pack, Swingshot | 2 |
| 10 | Quests | JSON-Definitionen, Ziel-Typen (töten, sammeln, reden, erreichen, Boss), Log, Tracker | 2 |
| 11 | NPCs | NPC-Entity, Dialog-JSON mit Antworten, Händler-Screen | 10 |
| 12 | Welten | Hub-Dimension, Portal-Block, Hub-Struktur | 11 |
| 13 | Bosse | Phasen-Automat, 1 Boss, Arena | 4, 7 |
| — | **Vertical Slice abgenommen** | Abnahme nach Abschnitt 3 mit 2 Spielern auf Dedicated Server | 3–13 |
| 14 | Party | Begleiter-Entity, Sora-/Ratchet-/Clank-artig | VS |
| 15 | Progression | Level, Werte, Fähigkeiten-Punkte, Fähigkeiten-Liste | VS |
| 16 | UI | Inventar-Tabs, Weltkarte, Feinschliff aller Screens | VS |
| 17 | VFX | Partikel-Presets, Spuren, Aufprall, Verwandlungssequenz | VS |
| 18 | Audio | `sounds.json`, eigene Sound-Events für alle Haken, Platzhalter-OGGs | VS |
| 19 | Multiplayer | 2–4-Spieler-Tests, Boss-Sync, geteilte Quests | VS |
| 20 | Optimierung | Profiling (Spark) mit 20/50 Gegnern, Boss, 4 Spielern | 19 |
| 21 | Tests | GameTests (Fabric) für Kernregeln, QA-Checkliste | 20 |
| 22 | Release | Versionierung, Modrinth-Paket, Changelog | 21 |

Danach folgen Inhalts-Wellen: weitere Aliens, Welten (Destiny Islands, Halloween Town, Sci-Fi-Planet, Dark World), Waffen bis RYNO-artig, Advancements, Cutscene-Kamera.

---

## Phasenstatus

### Phase 2 — Architektur (abgeschlossen bis auf den Build-Nachweis)

| Baustein | Status | Anmerkung |
|---|---|---|
| Paket `com.santiq.kingdomomnitrix`, Mod-ID `kingdomomnitrix` | IMPLEMENTED | alle Klassen, Assets, Daten, Übersetzungen umgezogen |
| Client-Source-Set (`splitEnvironmentSourceSets`) | IMPLEMENTED | Client-Code in `src/client/java` |
| Paketstruktur nach Zielarchitektur | IMPLEMENTED | Prototyp-Systeme in `alien/ keyblade/ magic/ weapon/ gadget/ enemy/` |
| `HeroData` (Stufe, EP, Bolts, Aliens, Story-Flags) | IMPLEMENTED | Attachment: persistent, beim Tod kopiert, an eigenen Client synchronisiert, robuster Codec |
| Status-HUD (Stufe, EP-Balken, Bolts) | PROTOTYPE | funktionsfähig; Gestaltung folgt in Phase 16 |
| `/hero debug · reset · bolts · level · xp · alien · flag` | IMPLEMENTED | OP-Stufe 2, optional mit Zielspieler |
| CI-Build `.github/workflows/mod-build.yml` | IMPLEMENTED | Ressourcen-Check, Textur-Check, `./gradlew build`, Jar als Artefakt |
| `tools/check_assets.py` | IMPLEMENTED | JSON, Übersetzungen, Modelle, Texturen, Rezepte, Loot |
| Netzwerk-Payloads (C2S-Absichten) | TODO → Phase 3 | erster Bedarf: Alien-Rad und Fähigkeiten-Tasten |
| JSON-Definitionen (Aliens usw.) | TODO → Phase 3 | über Fabric `DynamicRegistries.registerSynced`: wird mit der Welt geladen und automatisch an Clients gesendet; kein eigener Loader nötig |
| Bolt-Konto statt Bolt-Items | TODO → Phase 8 | Datenfeld existiert bereits |
| `./gradlew build` | **UNVERIFIED** | lokal gesperrt; Nachweis über den CI-Lauf auf GitHub |

### Phase 3 — Omnitrix

| Baustein | Status | Anmerkung |
|---|---|---|
| Alien-Definitionen als JSON (`data/<ns>/kingdomomnitrix/alien/*.json`) | IMPLEMENTED | Fabric-`DynamicRegistries`, automatisch an Clients synchronisiert; neues Alien = neue Datei |
| Fähigkeits-Typen (`AbilityRegistry`) | IMPLEMENTED | 11 Typen, Parameter aus JSON, Fehler einer Fähigkeit stürzen den Server nicht ab |
| `TransformationManager` | IMPLEMENTED | Dauer, Nachladen (Timeout voll, manuell halb), Energie, Abklingzeiten, Attribute, Größe mit Platzprüfung, Immunitäten, Unverwundbarkeit beim Ausweichen, Tod beendet die Verwandlung |
| Zustand speichern + Multiplayer | IMPLEMENTED | Attachment, persistent, an alle Clients; Energie/Zeit ohne Tick-Pakete |
| Heatblast, XLR8, Vierarm (je 3 Fähigkeiten) | IMPLEMENTED | Werte im JSON, Balancing offen |
| Diamondhead, Grey Matter | PROTOTYPE | je 1 Fähigkeit; Kristallwand/-schild, Hacken, Rätsel folgen nach der Vertical Slice |
| Alien-Rad (G) | IMPLEMENTED | Maus/Klick, Taste loslassen, Ziffern 1–9; gesperrte Aliens als „?“ |
| Fähigkeiten-Tasten R / V / B | IMPLEMENTED | in den Steuerungsoptionen änderbar |
| Omnitrix-HUD | IMPLEMENTED | Alien, Restzeit, Energie, 3 Slots mit Taste und Abklingzeit, Nachladeanzeige |
| DNA-Proben + Drops | IMPLEMENTED | Quellen und Chancen pro Alien im JSON; Freischalten per Rechtsklick |
| Alien-Körper (GeckoLib) | PROTOTYPE | Ersatz des Spielermodells, Idle/Walk, Kopf folgt Blick; Modelle/Texturen sind PLACEHOLDER |
| Verwandlungs-Effekt | PROTOTYPE | Partikel (Omnitrix-Grün + Alienfarbe), Blitz, Sound; eigene Sounds folgen in Phase 18 |
| `/hero transform · revert · dna` | IMPLEMENTED | mit Alien-Vorschlägen |
| Controller-Unterstützung | TODO | Tastatur/Maus fertig; Controller über Controlify-Kompatibilität nach der Vertical Slice |
| Fehlverwandlung (falsches Alien) | TODO | bewusst offen; braucht Designentscheidung (Spaß vs. Frust) |
| Erste-Person-Arme des Aliens | TODO | in der Ego-Ansicht sieht man noch die Spielerarme |
| Alte Statuseffekt-Lösung | entfernt | behebt: Milch beendet Verwandlung, zweites Omnitrix umgeht Nachladen, Vierarm erstickt |

**Test-Rezept:** `/hero dna kingdomomnitrix:heatblast` → Probe rechtsklicken (Omnitrix im Inventar) → `G` → Heatblast → `R`/`V`/`B`.

### Phase 4 — Kampf

| Baustein | Status | Anmerkung |
|---|---|---|
| Leichte Combo (3 Schläge, Finisher als Flächenschlag) | IMPLEMENTED | im Client getestet: Treffer, Kill, Bolt-Drop, Advancement |
| Luftcombo (Schweben, Finisher nach unten) | IMPLEMENTED | Logik fertig, Spielgefühl noch zu testen |
| Schwerer Angriff (Linksklick halten) | IMPLEMENTED | |
| Ausweichen (Alt) mit Unverwundbarkeit, 1× in der Luft | IMPLEMENTED | |
| Blocken (X) mit perfektem Block + Betäubung | IMPLEMENTED | |
| Lock-On (Z): Auswahl, Wechsel, Lösen (Schleichen+Z), Kamera folgt, Umrandung, HUD | IMPLEMENTED | im Client getestet |
| Kampfanimationen (playerAnimator, Jar-in-Jar) | PROTOTYPE | 9 Animationen, Platzhalter-Bewegungen |
| `ComboWeapon`-Schnittstelle | IMPLEMENTED | Keyblade nutzt sie; Werte pro Waffe in Phase 5 |
| Schnelle Klicks unter 1 Tick | behoben | wurden anfangs verschluckt (im Client-Test gefunden) |
| Skilltree-Anbindung (Combo Plus, Air Combo …) | TODO → Phase 15 | laut `DESIGN_PROGRESSION.md` |

### Phase 5 — Keyblades

| Baustein | Status | Anmerkung |
|---|---|---|
| Keyblade-Werte als JSON (`data/<ns>/kingdomomnitrix/keyblade/*.json`) | IMPLEMENTED | Schaden, Magie, Tempo, Reichweite, Combo-Länge, Passive, Seltenheit, Upgrade-Kosten; synchronisiert |
| Kingdom Key + Treueschwur (Oathkeeper) | IMPLEMENTED | je 5 Stufen; Erhalt per Crafting (Entscheidung SANTIQ) |
| Passive: Combo Plus, Kritisch, Finisher Plus | IMPLEMENTED | wirken im Kampfsystem |
| Passive: Magie-Boost, MP-Eile | TODO → Phase 6 | Werte vorhanden, Wirkung kommt mit dem MP-System |
| Keyblade-Schmiede (Block) | IMPLEMENTED | im Client getestet: Stufe 1 → 2, Bolts und Herzen abgezogen, zeigt nächste Kosten |
| Tooltip mit Werten | IMPLEMENTED | im Client getestet |
| Unzerstörbar, verzauberbar (Schärfe, Verbrennung …) | IMPLEMENTED | über Item-Tags |
| Kampfwerte über `ComboProfile` | IMPLEMENTED | Kampfsystem kennt keine Waffenarten mehr |
| Bolts als Kosten | PROTOTYPE | heute Bolt-Items im Inventar; ab Phase 8 Bolt-Konto |
| 3D-Modelle für Keyblades | TODO | aktuell 2D-Pixel-Texturen |

### Phase 6 — Magie

| Baustein | Status | Anmerkung |
|---|---|---|
| Zauber als JSON (`data/<ns>/kingdomomnitrix/spell/*.json`), 3 Stufen je Zauber | IMPLEMENTED | Feuer/Feura/Feuga, Eis/Eisra/Eisga, Blitz/Blitzra/Blitzga, Vita/Vitra/Vitga |
| Zaubereffekte als Registry (`SpellEffects`) | IMPLEMENTED | neue Zauber = JSON, neue Wirkung = eine Java-Methode |
| Nur mit Keyblade; Magiekraft + Magie-Boost verstärken | IMPLEMENTED | Entscheidung SANTIQ |
| MP im KH-Stil: Regeneration, +MP durch Nahkampftreffer, letzter Zauber leert, MP-Ladezeit | IMPLEMENTED | im Client getestet |
| MP-Eile (Keyblade-Passiv) | IMPLEMENTED | schnellere Regeneration und kürzere Ladezeit |
| Zauberwahl: M halten + Mausrad, M tippen = nächster | IMPLEMENTED | im Client getestet |
| Rechtsklick wirkt den aktiven Zauber | IMPLEMENTED | im Client getestet |
| Zauberleiste (MP, Stufen, Kosten, Abklingzeit) + MP im Status-Panel | IMPLEMENTED | Position im Test korrigiert (lag auf dem Chat) |
| Magie-Kristalle (Zauberstufe +1), Drop vom Schatten (4 %) | IMPLEMENTED | weitere Quellen: Dungeons/Bosse (Phasen 12–13) |
| Stufe 2/3: mehrere Geschosse, Mehrfachblitz, Gruppenheilung | IMPLEMENTED | |
| `/hero spell <zauber> <stufe>`, `/hero mp` | IMPLEMENTED | |
| Aero, Gravity, Reflect, Stop | TODO | nach der Vertical Slice |
| Eigene VFX/Sounds für Zauber | PROTOTYPE | Vanilla-Partikel und -Sounds; Phase 17/18 |

### Testumgebung (seit Phase 4)

- `./gradlew build` lokal ✅ · Dedicated Server startet/stoppt sauber, 5 Aliens geladen ✅
- Client auf Xvfb: Status-HUD, Omnitrix-HUD, DNA-Freischaltung, Alien-Rad, Heatblast-Körper (GeckoLib), Feuerexplosion, Lock-On, Combo ✅ (Screenshots)
- `tools/client_smoke.sh` für wiederholbare Client-Tests

---

## 5. Risiken

| Risiko | Wirkung | Gegenmaßnahme |
|---|---|---|
| Kein Build-Zugang | keine Phase nachweisbar fertig | Hosts freigeben, CI-Workflow |
| Alien-Körper statt Spielermodell rendern | braucht Eingriff in den Spieler-Renderer (Mixin) + GeckoLib | Prototyp in Phase 3 isoliert; Rückfallebene: Skalierung + Partikel-Aura |
| Bewegung (Wandlauf, Dash) läuft im Client | Desync, Cheat-Anfälligkeit | serverseitige Grenzprüfung, Abklingzeit beim Server |
| Hub-Strukturen müssen gebaut werden | können hier nicht im Spiel gebaut werden | Phase 12: Struktur zuerst im Code erzeugt, später als NBT aus dem Spiel ersetzt |
| Umfang | Gesamtplan entspricht vielen Monaten Teamarbeit | strikt Vertical Slice zuerst, nichts aus Phase 14+ vorziehen |
| Urheberrecht | Namen und Marken von Square Enix/Disney, Cartoon Network, Sony | nicht-kommerziell, nur eigene Assets, keine Originalmusik oder -sounds |

---

## 6. Sofort behebbare Fehler in der aktuellen Version

Diese Fehler verschwinden mit den REPLACE-Schritten oben. Behoben werden sie deshalb nicht einzeln, sondern dort.

| Fehler | Ort | Wird behoben in |
|---|---|---|
| Milch beendet die Verwandlung | Statuseffekt als Zustand | Phase 3 |
| Zweites Omnitrix umgeht die Nachladezeit | `OmnitrixItem.java:85` | Phase 3 |
| Vierarm kann in 2 Blöcke hohen Gängen ersticken | `ModEffects.java:33` | Phase 3 (Platzprüfung) |
| Die Art eines Projektils hängt am angezeigten Item | `HeroProjectileEntity.java:63` | Phase 8 |
| Shadows erben Zombie-Verstärkungen und Babys | `ShadowEntity` | Phase 7 |
