# Design-Vorgabe: AAA-Content-Mod (Abschluss-Überarbeitung)

made by SANTIQ · festgehalten am 2026-10-01

> **Arbeitsauftrag:** Nach Abschluss aller Phasen der Roadmap (`ANALYSE_UND_ROADMAP.md`) wird die gesamte Mod
> anhand dieser Vorgabe noch einmal durchgearbeitet: Repository-Audit (KEEP/REFACTOR/REPLACE/REMOVE),
> technische Roadmap, Architekturplan, Asset-Pipeline, Vertical-Slice-Plan, Asset-Matrix – danach
> OMNITRIX → TRANSFORMATION → 3 ALIENS → ANIMATION → VFX → COMBAT → KEYBLADE auf Endqualität bringen
> und erst dann skalieren. Zusätzlich vorher: Qualitätsvergleich mit AlienEvo, Ad Astra,
> Galaxies and Gallimimus, Kingdom Keys 2 und Thaumcraft.
>
> Der folgende Text ist die Vorgabe von SANTIQ im Wortlaut.

---

KINGDOM OMNITRIX
AAA-STYLE MINECRAFT CONTENT MOD
KINGDOM HEARTS × BEN 10 × RATCHET & CLANK
made by SANTIQ

## DEINE AUFGABE

Du arbeitest ab sofort als vollständiges AAA-Game-Development-Team an dem bereits existierenden Minecraft-Mod-Projekt.

Rollen:

* Creative Director
* Lead Game Designer
* Technical Director
* Senior Java Developer
* Senior Fabric Developer
* Gameplay Programmer
* Combat Designer
* Character/Animation Designer
* Technical Artist
* 3D Asset Designer
* VFX Designer
* UI/UX Designer
* World Designer
* Quest Designer
* AI Programmer
* Performance Engineer
* Multiplayer Engineer
* QA Engineer

Das Projekt existiert bereits.
Du sollst NICHT blind alles neu schreiben.
Zuerst muss das bestehende Repository vollständig analysiert werden.

## DAS ZIEL

KINGDOM OMNITRIX soll eine der umfangreichsten und hochwertigsten Minecraft-Content-Mods werden.
Minecraft bleibt Minecraft.
Die Mod soll nicht versuchen, Minecraft durch ein komplett separates RPG zu ersetzen.
Stattdessen soll Minecraft massiv erweitert werden durch:

KINGDOM HEARTS

* Keyblades
* Magie
* Heartless
* Welten
* Darkness / Light
* Dungeons
* Bosskämpfe
* Action Combat
* Lock-On
* Finisher
* Weltreisen

BEN 10

* Omnitrix
* Alien-Transformationen
* Alien-Movesets
* Alien-Progression
* Alien-Mastery
* Transformation Animationen
* Alien-spezifische Bewegung
* Alien-spezifische Exploration

RATCHET & CLANK

* Bolts
* Sci-Fi-Waffen
* Gadgets
* Raritanium
* Händler
* Upgrades
* verrückte Waffen
* Sci-Fi-Welten
* Mobility-Systeme
* technische Rätsel

Diese drei Systeme dürfen NICHT wie drei getrennte Mods wirken.
Sie müssen miteinander interagieren.

## ABSOLUTE PRIORITÄT

QUALITÄT VOR QUANTITÄT

Baue nicht möglichst viele Features.
Baue zuerst wenige Systeme extrem hochwertig.
Ein perfektes Alien ist wertvoller als zehn schlechte Aliens.
Ein perfekter Boss ist wertvoller als zehn einfache Mobs.
Ein perfekter Dungeon ist wertvoller als fünf leere Dimensionen.
Ein perfektes Keyblade-System ist wertvoller als 50 Schwerter ohne eigene Identität.

## PHASE 0 — BESTEHENDES PROJEKT ANALYSIEREN

Bevor du größeren Code schreibst:
Untersuche das aktuelle Repository.
Prüfe:

* Fabric Setup
* Minecraft Version
* Fabric API
* Java-Version
* bestehende Packages
* bestehende Entities
* Omnitrix
* Aliens
* Transformation
* Keyblades
* Magic
* Heartless
* Waffen
* Bolts
* Gadgets
* Weltgeneration
* Dimensionen
* NPCs
* Quests
* UI
* Networking
* Save System
* Recipes
* Loot Tables
* Texturen
* Modelle
* Animationen
* VFX
* Sounds
* Build System

Bewerte jedes bestehende System:
`KEEP`
`REFACTOR`
`REPLACE`
`REMOVE`
Erstelle daraus eine technische Roadmap.
Nicht funktionierende alte Systeme dürfen ersetzt werden.

## PHASE 1 — VISUAL FOUNDATION

Das wichtigste Ziel ist ein professionelles visuelles Fundament.
Die Mod darf nicht wie:
Minecraft + ein paar farbige Items
aussehen.
Sie soll aussehen wie:
eine große, zusammenhängende Minecraft-Mod mit eigener Art Direction.

### ART DIRECTION

Die Optik muss eine Mischung aus:
Minecraft + Kingdom Hearts + Ben 10 + Ratchet & Clank
sein.
Minecraft muss weiterhin erkennbar bleiben.
Gleichzeitig müssen wichtige Assets deutlich detaillierter und individueller als Vanilla Minecraft sein.

### ASSET-QUALITÄTSREGEL

Ein wichtiges Asset gilt NICHT als fertig, nur weil es technisch geladen wird.
Ein Asset ist erst:
`IMPLEMENTED`
wenn folgende Punkte erfüllt sind:

* Modell
* UV
* Textur
* Material
* Animation
* VFX
* Sound Hook
* Gameplay
* UI Icon
* First Person
* Third Person
* Multiplayer
* Performance

Status immer eindeutig angeben:
`IMPLEMENTED`
`PROTOTYPE`
`PLACEHOLDER`
`TODO`
Nie einen Placeholder als fertig deklarieren.

### TEXTURE SYSTEM

Alle wichtigen Assets benötigen echte individuelle Texturen.
Keine simplen Recolors.
Keine zufälligen Noise-Texturen.
Keine Standard-Minecraft-Textur mit Farbfilter.
Keine unfertigen AI-Texturen als finale Assets.
Texturen müssen:

* sauber UV-gemappt sein
* eine klare Materialdefinition besitzen
* eine starke Silhouette unterstützen
* aus Third Person funktionieren
* aus First Person funktionieren
* zur gesamten Art Direction passen

### VISUELLE SILHOUETTEN

Ein Asset muss idealerweise bereits anhand seiner Silhouette erkennbar sein.
Das gilt besonders für:

* Aliens
* Keyblades
* Waffen
* Heartless
* Bosse
* Gadgets

Wenn die Silhouette austauschbar aussieht, ist das Design noch nicht fertig.

## OMNITRIX — TOP PRIORITÄT

Der Omnitrix ist eines der wichtigsten Assets des gesamten Projekts.
Er benötigt:

* hochwertiges 3D-Modell
* detaillierte Textur
* klare Materialien
* leuchtende Elemente
* Aktivierungsanimation
* Auswahlanimation
* Transformationseffekt
* De-Transformationseffekt
* VFX
* Sound Hooks
* HUD
* Alien Wheel
* Alien Selection

Der Omnitrix darf niemals wie ein normales Minecraft-Armband aussehen.

## ECHTES TRANSFORMATIONSSYSTEM

EXTREM WICHTIG
Eine Transformation darf NICHT einfach bedeuten:
`Player Skin ändern`
oder:
`Player größer machen`
Das ist ausdrücklich nicht ausreichend.
Die Transformation muss das tatsächliche Charaktermodell verändern.
Architektur:

```text
PLAYER
   ↓
TRANSFORMATION MANAGER
   ↓
ALIEN FORM
   ├── MODEL
   ├── TEXTURE
   ├── ANIMATION CONTROLLER
   ├── HITBOX
   ├── MOVEMENT
   ├── COMBAT
   ├── ABILITIES
   ├── VFX
   └── SOUND
```

Jeder Alien darf seine eigene:

* Körperform
* Größe
* Proportion
* Hitbox
* Bewegung
* Animation
* Angriffsmethode
* Fähigkeit
* VFX
* Soundkulisse

besitzen.

### TRANSFORMATION PRESENTATION

Eine Transformation muss sichtbar stattfinden.
Beispiel:

```text
PLAYER
↓
Omnitrix hebt sich
↓
Aktivierung
↓
Omnitrix leuchtet
↓
Energieaufbau
↓
Kamera reagiert
↓
Partikel
↓
Alien-Silhouette
↓
Transformation
↓
Impact
↓
Alien erscheint
↓
HUD aktualisiert sich
```

Nicht einfach:

```text
Button
→
Skin wechseln
```

## ALIEN SYSTEM

Das System muss modular sein.
Mindestens:

HEATBLAST
Eigenschaften:

* eigenes Modell
* eigene Textur
* Feuerkörper
* Feuer-VFX
* Feuerresistenz
* Feuerangriffe
* Feuerball
* Feuerstrahl
* AoE
* Fire Boost
* eigene Animationen

XLR8

* eigenes Modell
* eigene Textur
* extreme Geschwindigkeit
* Dash
* Speed Attack
* schnelle Animationen
* Speed Trails
* Wall Run
* Ausweichmechanik

Geschwindigkeit darf nicht einfach durch absurd hohe Vanilla-Movement-Speed simuliert werden.

FOUR ARMS

* eigenes Modell
* vier Arme
* größere Körperform
* eigene Hitbox
* Heavy Combat
* Ground Slam
* Shockwave
* Throw
* hohe Knockback-Werte

DIAMONDHEAD

* Kristallmodell
* Kristalltextur
* Kristallmaterial
* Crystal Blade
* Crystal Projectile
* Crystal Wall
* Crystal Shield
* Crystal Spikes
* eigene VFX

GREY MATTER

* vollständig eigenes Modell
* sehr kleine Körperform
* eigene Animationen
* kleine Hitbox
* technische Interaktionen
* Hacken
* Rätsel
* versteckte Bereiche
* Analysefähigkeit

Grey Matter darf NICHT nur ein kleiner Spieler sein.

### ALIEN ARCHITEKTUR

Weitere Aliens müssen ohne Umbau des gesamten Systems hinzugefügt werden können.
Beispielsweise:

* Upgrade
* Ghostfreak
* Cannonbolt
* Wildmutt
* Stinkfly
* Ripjaws
* Big Chill
* Swampfire
* Feedback
* Gravattack
* Way Big
* weitere

Das System darf nicht auf fünf hartcodierte Aliens ausgelegt sein.

### ALIEN MASTERY

Jeder Alien kann optional eine eigene Mastery besitzen.
Beispiel:

```text
HEATBLAST MASTERY

Level 1
Fireball

Level 2
Fire Dash

Level 3
Fire Explosion

Level 4
Meteor Attack

Level 5
Ultimate Ability
```

Das soll jedoch nicht zu einem klassischen MMORPG-Grind werden.

### OMNITRIX PROGRESSION

Die Omnitrix bekommt:

* Alien Unlocks
* Transformation Duration
* Cooldown
* Energy
* Ability Unlocks
* Mastery
* neue Formen
* Upgrades
* später Master-Control-artige Endgame-Funktionen

## KEYBLADE SYSTEM

Keyblades sind ein eigenes Gameplay-System.
Jede wichtige Keyblade benötigt:

* eigenes 3D-Modell
* eigene Textur
* eigene Silhouette
* eigene Animation Hooks
* eigene VFX
* eigene Passive
* eigene Stats
* eigene Spezialfähigkeit

Stats können sein:

* Strength
* Magic
* Speed
* Reach
* Critical
* Ability
* Rarity

### KEYBLADE COMBAT

Implementiere:

* Light Attack
* Heavy Attack
* Combo
* Air Combo
* Dash Attack
* Dodge
* Guard
* Counter
* Finisher
* Special Attack
* Magic

## LOCK-ON SYSTEM

Lock-On ist ein Kernsystem.
Der Spieler muss Gegner gezielt anvisieren können.
Features:

* Target Selection
* Target Switching
* Target UI
* Boss Lock-On
* Entfernung
* Kamera-Fokus
* Attack Direction
* Dash-to-Target

## COMBAT SYSTEM

Combat muss deutlich über Vanilla Minecraft hinausgehen.
Benötigt:

* Hit Detection
* Hitboxes
* Hit Stop
* Knockback
* Stagger
* Launch
* I-Frames
* Guard
* Perfect Guard
* Dodge
* Counter
* Air Combat
* Finisher
* Boss Mechanics

Aber:
Minecraft muss weiterhin als Minecraft funktionieren.

## MAGIE

Mindestens:

* Fire
* Blizzard
* Thunder
* Cure

Später:

* Aero
* Gravity
* Reflect
* Stop
* weitere

Jede Magie benötigt:

* Gameplay
* VFX
* Animation Hook
* Sound Hook
* Cooldown/MP
* Upgrade
* Impact

## HEARTLESS

Keine Zombie-Recolors.
Eigene Modelle.
Eigene Animationen.
Eigene AI.
Eigene Angriffe.
Eigene Sounds.
Eigene VFX.
Mindestens:

* Shadow
* Soldier
* Large Body
* Air Soldier
* Darkball
* Defender
* Wyvern
* Elite Heartless

### HEARTLESS AI

Gegner sollen auf die unterschiedlichen Spielweisen reagieren.
Beispielsweise:
XLR8:
→ schnelle Gegner
Four Arms:
→ Heavy Counter
Heatblast:
→ Feuerresistente Gegner
Diamondhead:
→ Gegner mit Nahkampfdruck
Grey Matter:
→ Rätsel/Utility
Dadurch wird Alien-Auswahl strategisch.

## BOSS SYSTEM

Bosse müssen echte Bosskämpfe sein.
Nicht:
`5000 HP + hoher Schaden`
Bosse benötigen:

* Phasen
* einzigartige Angriffe
* Telegraphing
* Schwachstellen
* Stagger
* Arena Mechanics
* Phase Transition
* Enrage
* Bossbar
* Spezialangriffe
* eigene VFX
* eigene Animationen
* eigene Sounds
* Loot

Beispiel:

```text
PHASE 1
↓
PHASE 2
↓
ARENA CHANGE
↓
ENRAGE
↓
FINAL ATTACK
```

## RATCHET & CLANK WEAPON SYSTEM

Bolts sind eine zentrale Ressource.
Quellen:

* Gegner
* Kisten
* Events
* Bosse
* Quests
* Challenges

Verwendung:

* Waffen
* Upgrades
* Munition
* Gadgets
* Händler

### WAFFEN

Mindestens:

* OmniWrench
* Combuster
* Fusion Grenade
* weitere Sci-Fi-Waffen

Zusätzliche Waffen können sein:

* Shock Cannon
* Rocket Launcher
* Plasma Storm
* Gravity Weapon
* Transformation Weapon
* Endgame RYNO-style Weapon

Waffen benötigen:

* eigenes Modell
* eigene Textur
* Projektil
* Muzzle Flash
* Impact
* Sound Hook
* Recoil
* Ammo
* Upgrade

### WAFFEN-UPGRADES

Beispiel:

```text
LEVEL 1
↓
LEVEL 2
↓
LEVEL 3
↓
LEVEL 4
↓
MAX
```

Upgrades:

* Damage
* Fire Rate
* Ammo
* Explosion Radius
* Critical
* Special Effects

## GADGET SYSTEM

Implementiere:

* Heli-Pack
* Swingshot
* Grindboots
* Hydro-Pack
* O2-System
* Trespasser
* weitere

Gadgets müssen Exploration beeinflussen.

## WORLD EXPLORATION

Aliens und Gadgets sollen tatsächlich neue Wege öffnen.
Beispiel:
Grey Matter
→ Wartungsschacht
Four Arms
→ schwere Tür
Heatblast
→ Eis/Hindernis
Diamondhead
→ Kristallplattform
XLR8
→ Zeit-/Speed-Abschnitt
Heli-Pack
→ Luftbereich
Dadurch wird Exploration mit dem Character-System verbunden.

## DUNGEON SYSTEM

Jeder große Dungeon sollte idealerweise enthalten:

```text
ENTRANCE
↓
EXPLORATION
↓
PUZZLE
↓
COMBAT
↓
SECRET
↓
MINIBOSS
↓
NEW AREA
↓
TREASURE
↓
BOSS
```

Dungeons müssen eigene Identität besitzen.

## WORLD SYSTEM

Minecraft Overworld bleibt die Hauptbasis.
Zusätzlich:

* Hubs
* Dimensionen
* Adventure Areas
* Dungeons
* Portale
* Weltreisen

Beispiele:

* Traverse Town
* Destiny-Islands-artige Inselwelt
* Halloween-Town-artige Welt
* Sci-Fi-Planet
* Dark World
* weitere

Die Welten sollen nicht nur dekorativ sein.
Sie benötigen:

* NPCs
* Händler
* Quests
* Gegner
* Dungeons
* Geheimnisse
* Collectibles
* Bosse
* Ressourcen

## DYNAMIC WORLD EVENTS

Die Welt soll Ereignisse erzeugen.
Beispiele:
Heartless Invasion
Alien Crash
Dark Rift
Keyblade Shrine
Raritanium Meteor
Boss Spawn
Temporary Portal
Events sollen selten genug sein, damit sie besonders bleiben.

## ECONOMY

Bolts:

```text
MONSTER
↓
BOLTS
↓
HÄNDLER
↓
WAFFEN
↓
UPGRADES
↓
ENDGAME
```

Händler:

* Weapon Dealer
* Keyblade Merchant
* Omnitrix Technician
* Gadget Engineer
* Rare Material Merchant

## MATERIAL SYSTEM

Materialien müssen unterschiedliche Funktionen besitzen.
Raritanium
Sci-Fi-Waffen und Technologie.
Mythril
Keyblades / Magic.
Orichalcum
Endgame.
Weitere Materialien können später hinzugefügt werden.
Jedes Material braucht einen echten Zweck.

## SYNERGY SYSTEM

Das ist ein Kernsystem.
Die drei Hauptsysteme müssen miteinander interagieren.
Beispiele:

```text
ALIEN
+
KEYBLADE
+
WEAPON
+
GADGET
+
MAGIC
=
BUILD
```

Beispiel:
SPEED BUILD
XLR8 + Speed Keyblade + Dash + Heli-Pack + Thunder
TANK BUILD
Four Arms + Heavy Keyblade + Armor + Ground Slam
MAGIC BUILD
Heatblast + Magic Keyblade + Fire Boost + MP Haste
Builds sollen entstehen, ohne Minecraft in ein klassisches MMO zu verwandeln.

## SKILL TREES

Skill Trees bleiben Bestandteil der Mod.
Bereiche:
COMBAT

* Combo Plus
* Air Combo
* Dodge
* Counter
* Finisher

OMNITRIX

* Duration
* Cooldown
* Energy Efficiency
* Alien Unlocks
* Master Control

KEYBLADE

* Combo
* Magic
* Critical
* Finisher

TECHNOLOGY

* Weapon Upgrade
* Ammo
* Gadgets

EXPLORATION

* Double Jump
* Dash
* Glide
* Wall Run

## PROGRESSION

Der Spieler kann die Mod vollständig durchspielen, muss aber nicht.
Progression:

```text
EXPLORATION
↓
RESOURCES
↓
CRAFTING
↓
ALIENS
↓
KEYBLADES
↓
WEAPONS
↓
DUNGEONS
↓
BOSSES
↓
DIMENSIONS
↓
RARE MATERIALS
↓
ENDGAME
```

Minecraft bleibt jederzeit frei spielbar.

## ENDGAME

Kein New Game+.
Nach dem finalen Boss bleibt die Welt bestehen.
Endgame:

* Secret Bosses
* Boss Rematches
* Legendary Keyblades
* Ultimate Weapons
* Alien Mastery
* Collectibles
* Challenge Dungeons
* seltene Materialien
* Cosmetics
* versteckte Gebiete
* 100%-Completion

## COLLECTIBLES

Implementiere langfristig:

* Gold Bolts
* Alien DNA Samples
* Keyblade Fragments
* Gummi Ship Parts
* Heart Shards
* Raritanium Crystals
* Secret Items

Collectibles sollen tatsächlich etwas freischalten oder dokumentieren.

## HERO CODEX

Erstelle eine Ingame-Datenbank.
Kategorien:

* Aliens
* Keyblades
* Weapons
* Heartless
* Bosses
* Worlds
* Materials
* Gadgets
* Magic

Jeder Eintrag kann enthalten:

* Beschreibung
* Stats
* Fähigkeiten
* Fundort
* Unlock
* Progress
* Mastery

## UI / HUD

HUD soll Informationen übersichtlich darstellen.
Beispielsweise:

* HP
* MP
* XP
* Level
* Alien
* Transformation Timer
* Omnitrix Cooldown
* Keyblade
* Weapon
* Ammo
* Bolts
* Quest
* Lock-On

Nicht alles gleichzeitig auf den Bildschirm klatschen.
Kontextabhängige UI verwenden.

### TRANSFORMATION HUD

Während Alien-Transformation:

```text
ALIEN NAME
ENERGY
TIME
COOLDOWN
ABILITY SLOTS
```

Die Darstellung muss hochwertig und konsistent sein.

## CAMERA

Implementiere situationsabhängige Kamera.
Normal:
Minecraft Third Person.
Combat:
näher.
Lock-On:
Target-Fokus.
Boss:
mehr Abstand.
Transformation:
cinematic.
Finisher:
kurze dynamische Kamera.
Keine übermäßigen Kameraeffekte, die Motion Sickness verursachen.

## ANIMATION SYSTEM

Ein gemeinsames Animationssystem aufbauen.
Benötigt:

* Idle
* Walk
* Run
* Sprint
* Jump
* Fall
* Attack
* Heavy Attack
* Combo
* Dodge
* Guard
* Counter
* Ability
* Transformation
* De-Transformation
* Stagger
* Death

Aliens besitzen eigene Animation Sets.

## VFX SYSTEM

Erstelle ein zentrales wiederverwendbares VFX-System.
Komponenten:

* Fire
* Ice
* Lightning
* Darkness
* Light
* Energy
* Plasma
* Crystal
* Speed
* Explosion
* Magic
* Transformation

VFX müssen Gameplay unterstützen und nicht einfach dauerhaft den Bildschirm überladen.

## AUDIO ARCHITECTURE

Eigene Sound Hooks für:

* Omnitrix
* Transformation
* Aliens
* Keyblades
* Magic
* Heartless
* Weapons
* Gadgets
* Bosses
* Portals
* UI

Keine urheberrechtlich geschützte Originalmusik automatisch einbauen.
Eigene bzw. entsprechend lizenzierte Sounds verwenden.

## MULTIPLAYER

Ziel:
2–4 Spieler.
Synchronisieren:

* Transformationen
* Alien
* Animation State
* Bosses
* Quests
* Loot
* Weapons
* Abilities
* UI-relevante States
* Progression

Server und Client sauber trennen.
Keine Client-only Classes auf Dedicated Servern.

## PERFORMANCE

Die Mod muss große Minecraft-Welten weiterhin spielbar halten.
Vermeiden:

* unnötige Tick-Loops
* massive Entity Scans
* unnötige Pathfinding-Berechnungen
* übermäßige Netzwerkpakete
* Memory Leaks
* unnötige Partikel
* dauerhaft laufende VFX

Testen mit:

* 20 Gegnern
* 50 Gegnern
* Boss
* mehreren Spielern
* mehreren VFX
* Transformationen

## MODPACK KOMPATIBILITÄT

Die Mod muss sich wie eine große Minecraft-Mod verhalten.
Unterstütze:

* JEI
* REI
* EMI
* Tags
* Configs
* Datapacks
* andere Dimensionen
* andere Mods

Keine unnötigen Hardcoded IDs.

## CONFIG

Konfigurierbar:

* Alien Duration
* Cooldowns
* Damage
* Boss Difficulty
* Spawn Rates
* Bolt Drops
* World Generation
* Dimension Generation
* VFX
* Story
* Bosses

## VERTICAL SLICE

Bevor du den kompletten Content baust:
Erstelle eine wirklich hochwertige Vertical Slice.
Sie enthält:

3 ALIENS

* Heatblast
* XLR8
* Four Arms

2 KEYBLADES
3 SCI-FI-WAFFEN
2 GADGETS
5 HEARTLESS
1 BOSS
1 DUNGEON
1 HUB
MAGIC

* Fire
* Blizzard
* Thunder
* Cure

OMNITRIX
vollständig funktionierend.
TRANSFORMATION
vollständig animiert.
COMBAT
vollständig spielbar.
LOCK-ON
vollständig spielbar.
HUD
vollständig funktionierend.
SAVE SYSTEM
funktionierend.
Diese Vertical Slice muss sich bereits wie ein echtes fertiges Teilstück der Mod anfühlen.

## VISUAL QUALITY TEST

Teste die Vertical Slice nicht nur technisch.
Spiele sie tatsächlich in Third Person.
Überprüfe:

* sieht Heatblast wie ein echter Charakter aus?
* sieht XLR8 eindeutig anders aus?
* sieht Four Arms wirklich nach einer anderen Kreatur aus?
* fühlt sich die Transformation spektakulär an?
* sehen Keyblades individuell aus?
* sehen Waffen individuell aus?
* sehen Heartless wie eigene Kreaturen aus?
* sieht der Boss wie ein Boss aus?
* passen VFX und Animationen zusammen?
* fühlt sich Combat flüssig an?

Wenn nein:
nicht weiter skalieren.
Erst Qualität korrigieren.

## ASSET MATRIX

Erstelle eine interne Übersicht:

| Asset | Model | UV | Texture | Animation | VFX | Sound | Gameplay | Status |
|---|---|---|---|---|---|---|---|---|
| Omnitrix | | | | | | | | |
| Heatblast | | | | | | | | |
| XLR8 | | | | | | | | |
| Four Arms | | | | | | | | |
| Keyblade | | | | | | | | |
| Heartless | | | | | | | | |
| Boss | | | | | | | | |

Aktualisiere diese Matrix während der Entwicklung.

## TESTING

Nach jedem großen System:

```text
./gradlew build
```

Wenn Fehler:

1. Fehler analysieren
2. Ursache identifizieren
3. korrigieren
4. erneut bauen

Niemals einen Build als erfolgreich melden, wenn er nicht tatsächlich erfolgreich war.
Wenn externe Server blockiert sind:
klar dokumentieren.

## DEBUG COMMANDS

Nur für OP/Development:

```text
/hero transform
/hero alien
/hero weapon
/hero keyblade
/hero boss
/hero quest
/hero world
/hero debug
```

## ENTWICKLUNGSREIHENFOLGE

Arbeite nicht chaotisch.
Reihenfolge:
PHASE 1 Repository Audit
PHASE 2 Core Architecture
PHASE 3 Transformation Framework
PHASE 4 Omnitrix
PHASE 5 Alien Models
PHASE 6 Alien Animation
PHASE 7 VFX
PHASE 8 Combat
PHASE 9 Lock-On
PHASE 10 Keyblades
PHASE 11 Heartless
PHASE 12 Boss
PHASE 13 Weapons
PHASE 14 Gadgets
PHASE 15 Dungeon
PHASE 16 World
PHASE 17 Quest
PHASE 18 Progression
PHASE 19 UI
PHASE 20 Multiplayer
PHASE 21 Optimization
PHASE 22 Content Expansion
PHASE 23 Final Polish

## ABSOLUTE DEVELOPMENT RULE

Wenn du zwischen:
20 neuen Items
und
einer perfekten Transformation
wählen musst:
Transformation.
Wenn du zwischen:
10 neuen Mobs
und
einem perfekten Boss
wählen musst:
Boss.
Wenn du zwischen:
5 neuen Welten
und
einem perfekten Dungeon
wählen musst:
Dungeon.
Wenn du zwischen:
20 neuen Waffen
und
einem perfekten Combat-System
wählen musst:
Combat-System.

## DAS ENDERGEBNIS

Der Spieler soll die Mod starten und denken:
Nach einigen Minuten:
„Das ist Minecraft.“
Nach der ersten Transformation:
„Okay, das ist etwas Besonderes.“
Beim ersten Keyblade-Kampf:
„Das fühlt sich richtig gut an.“
Beim ersten Sci-Fi-Waffen-Upgrade:
„Ich will mehr davon.“
Beim ersten Dungeon:
„Hier gibt es tatsächlich etwas zu entdecken.“
Beim ersten Boss:
„Das ist ein richtiger Bosskampf.“
Nach vielen Stunden:
„Ich habe immer noch nicht alles gesehen.“

## ENTSCHEIDENDE PHILOSOPHIE

KINGDOM OMNITRIX soll nicht versuchen, Minecraft zu ersetzen.
Es soll Minecraft erweitern.
Minecraft liefert:

* Sandbox
* Survival
* Building
* Exploration
* Crafting
* Multiplayer
* offene Welt

Kingdom Hearts liefert:

* Keyblades
* Magie
* Heartless
* Welten
* Action Combat

Ben 10 liefert:

* Omnitrix
* Transformationen
* Aliens
* unterschiedliche Playstyles

Ratchet & Clank liefert:

* Bolts
* Waffen
* Gadgets
* Technologie
* Sci-Fi
* verrückte Combat-Ideen

Alles muss in einem gemeinsamen System funktionieren.

## START NOW

Analysiere zuerst das vorhandene Repository.
Keine massenhafte Neuprogrammierung bevor die bestehende Architektur verstanden wurde.
Erstelle anschließend:

1. Repository Audit
2. technische Roadmap
3. Architekturplan
4. Asset Pipeline
5. Vertical-Slice-Plan

Danach beginne mit:
OMNITRIX → TRANSFORMATION → 3 ALIENS → ANIMATION → VFX → COMBAT → KEYBLADE
Erst wenn dieser Kern hochwertig funktioniert, skaliere ihn auf den restlichen Content.

## FINALER ANSPRUCH

Ich möchte am Ende keine Mod, die nur auf einer Feature-Liste gut aussieht.
Ich möchte eine Mod, die sich beim Spielen gut anfühlt.
Gameplay.
Visuals.
Animation.
VFX.
Sound.
World Design.
Progression.
Polish.
Alles muss zusammenpassen.

KINGDOM OMNITRIX
Minecraft × Kingdom Hearts × Ben 10 × Ratchet & Clank
made by SANTIQ
