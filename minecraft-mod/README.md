# Kingdom Omnitrix — Minecraft-Mod

made by SANTIQ · Mod-ID `kingdomomnitrix` · Version 0.6.0-alpha

Fan-Mod für **Minecraft 1.21.1 (Fabric)**, die drei Welten zusammenbringt:
**Kingdom Hearts**, **Ben 10** und **Ratchet & Clank**.

> Nicht-kommerzielles Fanprojekt. Kingdom Hearts gehört Square Enix/Disney, Ben 10 Cartoon Network,
> Ratchet & Clank Insomniac Games/Sony. Alle Texturen sind selbst erzeugt (`tools/generate_textures.py`).

> **Offene Aufgaben für dich:** [`docs/TODO_SANTIQ.md`](docs/TODO_SANTIQ.md) — Netzwerk-Freigaben, Installationen, Testliste.

## Inhalt

### Kingdom Hearts
| Ding | Was es tut |
|---|---|
| **Königsschlüssel**, **Oathkeeper** (Keyblades) | Kampfwaffen mit Combo-System, Werte und Passiv-Fähigkeiten aus JSON (`data/<ns>/kingdomomnitrix/keyblade/`) |
| **Keyblade-Schmiede** | Rechtsklick mit einem Keyblade: Upgrade gegen Bolts vom Konto + Materialien |
| Zauber **Feuer, Eis, Donner, Vita** | Rechtsklick mit Keyblade wirkt den gewählten Zauber (kostet MP); **M + Mausrad** wählt. Stufen 1–3 über **Magie-Kristalle** |
| **Herzlose** | Schatten, Soldat, Großkörper, Luftsoldat, Dunkelball — erscheinen nur aus **Dunkelheitsrissen** (nachts, 3 Wellen, Belohnung). Gamerule `kingdomomnitrixDarknessRifts` schaltet Risse ab |
| **Hi-Potion**, **Paopu-Frucht** | Heilung bzw. Regeneration + Absorption |

**Kampf mit Keyblade/OmniWrench:** Linksklick = Combo (letzter Schlag Finisher), Linksklick halten = schwerer Schlag,
in der Luft = Luft-Combo, **Linke Alt** = Ausweichen, **Feststelltaste** halten = Blocken (perfekter Block betäubt),
**Z** = Ziel erfassen (Kamera folgt). MP laden sich mit der Zeit und durch Treffer auf; ist die Leiste leer, startet die MP-Ladezeit.

### Ben 10 — das Omnitrix

Das Omnitrix muss nur im Inventar liegen.

- **DNA-Proben** schalten Aliens frei. Gegner lassen sie fallen (siehe Tabelle); Rechtsklick auf die Probe speichert das Alien.
- **G** (oder Rechtsklick mit dem Omnitrix) öffnet das **Alien-Rad**: Alien mit der Maus wählen und klicken, die Taste loslassen oder 1–9 drücken. Die Mitte verwandelt zurück.
- **R / V / B** lösen die drei Fähigkeiten des Aliens aus (kosten Energie, haben Abklingzeiten).
- Eine Verwandlung dauert 60 Sekunden. Danach lädt das Omnitrix nach (bei vorzeitiger Rückverwandlung nur halb so lang).
- Das HUD unten rechts zeigt Alien, Restzeit, Energie und die Fähigkeiten.
- Verwandelte Spieler sehen für alle wie das Alien aus (animierter Körper).

| Alien | Körper | R | V | B | DNA von |
|---|---|---|---|---|---|
| **Heatblast** | immun gegen Feuer/Lava, +3 Angriff | Feuerstoß | Feuerexplosion (Umkreis) | Flammenschub (Sprung) | Lohe, Magmawürfel |
| **XLR8** | +60 % Tempo, kein Fallschaden, schnelle Schläge | Sturmangriff (Dash mit Schaden) | Ausweichen (kurz unverwundbar) | Schlaghagel | Schatten, Ozelot |
| **Vierarm** | 1,4× groß, +6 Angriff, +5 Herzen, kaum Rückstoß | Bodenschlag | Werfen | Riesensprung | Eisengolem, Verwüster |
| **Diamondhead** *(Prototyp)* | +12 Rüstung, immun gegen Geschosse | Kristallsalve | – | – | Wächter |
| **Grey Matter** *(Prototyp)* | 0,3× groß, schnell | Analyse | – | – | Silberfischchen |

Neue Aliens: JSON-Datei in `data/<namespace>/kingdomomnitrix/alien/` + Übersetzung + Körper-Dateien.

### Ratchet & Clank
| Ding | Was es tut |
|---|---|
| **Bolts** | Währung. Aufgehobene Bolts landen sofort auf dem **Bolt-Konto** (Status-Panel oben links). Monster lassen 1–4 fallen, Bolt-Kisten 3–8 |
| **Waffen-Terminal** | Rechtsklick: Waffen kaufen, **aufrüsten (nur hier)**, Munition nachfüllen — bezahlt vom Bolt-Konto |
| **OmniWrench 8000** | Nahkampf-Combo; **Rechtsklick** wirft ihn als Bumerang — trifft Gegner, legt Hebel/Knöpfe um, zerschlägt Bolt-Kisten |
| **Combuster** | Rechtsklick halten = Dauerfeuer (Plasma). Stufe 5: explosive Schüsse ohne Blockschaden |
| **Fusionsgranate** | Wurf, Explosion ohne Blockschaden |
| **Heli-Pack / Heli-Jet** | Rücken-Gadget, **J** wechselt den Modus. Heli: Sprungtaste in der Luft = Doppelsprung, halten = Gleiten. Jet: Sprungtaste in der Luft = Schub nach vorn (2×), halten = Sinkflug. Kein Fallschaden beim Gleiten |
| **Swingshot** | Werkzeug-Gadget: **Y** schießt den Haken an jeden festen Block (24 Blöcke) und zieht dich hin. Am Ziel hängst du (bis 10 s): Springen = Absprung, Schleichen oder Y = loslassen |

Waffenstufe und Munition stehen im Waffen-HUD am rechten Rand. Gadgets liegen im **Gadget-Gürtel** (**H** öffnet ihn,
oder Rechtsklick mit dem Gadget). Beim Tod fallen Gadgets wie normale Items (außer mit `keepInventory`).

### Raumfahrt und Welten
- **Aphelion** (Raumschiff mit Bord-KI aus Ratchet & Clank 3): auf den Boden setzen, Rechtsklick zum Einsteigen.
  W/S fliegt in Blickrichtung, A/D seitlich, Leertaste steigt, Schleichen steigt aus. Cockpit-Anzeige mit Tempo und Höhe.
- Hoch genug fliegen (16 Blöcke unter der Bauhöhe) → **Weltall**: Sternenhimmel, Asteroiden mit **Raritanium**, geringe Schwerkraft.
  Ohne Schiff ins Leere gefallen → Wiedereintritt über der Oberwelt (mit Sanftem Fall).
- **Weltraumrisse** (Wirbel mit Name und Entfernung, Navigation im Cockpit): hineinfliegen = Reise. „Heimatwelt“ führt zurück,
  ebenso ein Sinkflug unter Y 0. In einer Welt wieder hoch genug fliegen → zurück ins All neben deren Riss.
- **Traverse Town**: eigene Welt in ewiger Nacht, jedes Mal anders generiert: Stadtrand mit Laternen, Schattenwald,
  Kristallfelder, dunkles Meer. Erze **Mythril**, **Raritanium**, **Orichalcum**. Herzlose streifen draußen umher.
  Die **Stadt** entsteht beim ersten Besuch: Platz mit Brunnen, Straßen, Häuser, Waffenladen (Clank, Terminal), Schmiede,
  Yen Sid und Max auf dem Platz. In der Stadt entstehen keine Monster; bauen und abbauen ist erlaubt.
- **Arena** (Ratchet & Clank): Arena-Terminal → Pokal wählen → Countdown, Runden, Zeitlimit, Bossleiste.
  Sieg: Bolts, Helden-EP, Material, Bestzeit. Ein Arena-Terminal lässt sich auch selbst bauen (Kampffläche: Radius 12 um das Terminal).
- **Materialien**: Raritanium → Waffen-Aufrüstungen (Stufe 4–5), Mythril und Orichalcum → Keyblade-Upgrades.

### Boss: Dr. Nefarious
Der Erzschurke aus Ratchet & Clank in seinem Kampf-Mech. **Nefarious-Kommunikator** benutzen → er landet 8 Blöcke vor dir;
oder im Arena-Terminal den **Platin-Pokal** wählen (ab Heldenstufe 8).
- Jeder Angriff wird angekündigt: **rote Linie** = Laser (danach überhitzt er), **rote Ringe** = Raketeneinschläge,
  **gelber Ring** = Stampfer. Aus den Markierungen gehen!
- **Schwachstellen:** Rücken doppelter Schaden, Panzerfront nur halber; überhitzt (Rauch, Lava-Tropfen) 2,5-facher Schaden.
- **Phase 2** (60 %): die Arena steht unter Strom, Elektrofelder wandern über den Boden.
  **Wut** (25 %): regelmäßige Überladung — schnell 30 Schaden auf ihn, sonst gibt es eine große Explosion.
- Erster Sieg: **Omega-Schlüssel** (neues Keyblade), Orichalcum, Raritanium, 800 Bolts. Danach jederzeit wiederholbar
  für Raritanium und Bolts. Mit mehreren Spielern hat er mehr Leben.

### Quests
Beim ersten Einloggen bekommt jeder Spieler ein **Quest-Buch** (Rechtsklick). Es ist aufgebaut wie ein Gespräch mit einem
Auftraggeber (Yen Sid, Max Tennyson, Clank): Dialog, Ziele, Belohnung und die Knöpfe *Annehmen*, *Abgeben*, *Aufgeben*.

- Reiter **Tracker**: laufende Quests mit Fortschrittsbalken · **Aufträge**: verfügbare und gesperrte · **Erledigt**
- Ziel-Typen: **Besiegen** (Mob oder Tag, z. B. alle Herzlosen), **Herstellen** (Werkbank, Ofen …), **Bringen** (Items im Inventar, werden beim Abgeben abgezogen)
- Belohnungen: Bolts, Helden-EP (mit Stufenaufstieg) und Items, auch mit Komponenten (Magie-Kristall, DNA-Probe)
- Story-Quests schalten weitere frei; Kopfgelder sind wiederholbar
- Neue Quests: JSON-Datei in `data/<namespace>/kingdomomnitrix/quest/` (Beispiele im Mod) + Übersetzungen

### NPCs
**Meister Yen Sid**, **Max Tennyson** und **Clank** stehen als Figuren in der Welt (NPC-Setzer aus dem Kreativ-Tab oder
`/hero npc spawn <npc>`). Über dem Kopf zeigt ein gelbes **!** einen neuen Auftrag, ein goldenes **?** eine Quest zum Abgeben.
Rechtsklick öffnet das Gespräch: Text läuft Buchstabe für Buchstabe ein (Klick/Leertaste = weiter), danach Antworten wie
*Auftrag annehmen*, *Wie läuft es?*, *Abgeben* oder *Tschüss*. NPCs sind unverwundbar und verschwinden nicht;
entfernen mit Schleichen + Schlag im Kreativmodus oder `/kill`. Neue NPCs: JSON in `data/<namespace>/kingdomomnitrix/npc/`
+ Modell unter `entity/npc/`; Quests verweisen über `giver.npc` auf ihren NPC.

## Rezepte (Werkbank)

```
Königsschlüssel       Oathkeeper              Omnitrix
.     .    Herz       .     Amethyst Herz     Eisen   Smaragd Eisen
.  Goldblock .        .     Königs-  Amethyst Smaragd Diamant Smaragd
Stock .    .          Quarz schlüssel .       Eisen   Smaragd Eisen

Keyblade-Schmiede     Waffen-Terminal         OmniWrench 8000
Gold  Herz  Gold      Eisen Glasscheibe Eisen  .     Nugget Nugget
Eisen Amboss Eisen    Redstone Diamant Redstone .    Eisen  Nugget
Eisen Eisen Eisen     Eisen Eisen Eisen        Eisen .      .

Combuster             Fusionsgranate          Heli-Pack               Swingshot
Kupfer Kupfer Lohenstaub  .     Nugget .      Eisen  Feder    Eisen   .     .     Haken
Eisen Redstone Eisen  Nugget TNT  Nugget      Nugget Redstone Nugget  .     Kette .
Eisen .    .          .     Nugget .          .      Nugget   .       Eisen Redstone .

Bolt-Kiste: 8 Bretter um 1 Eisennugget
Quest-Buch (formlos): Buch + Goldnugget + Feder
Aphelion: Glas Enderauge Glas / Eisenblock Diamantblock Eisenblock / Schmelzofen Redstoneblock Schmelzofen
Arena-Terminal: Gold Glocke Gold / Eisen Raritanium Eisen / Eisen Eisen Eisen
Nefarious-Kommunikator: Raritanium Amethyst Raritanium / Kupfer Echo-Splitter Kupfer / Raritanium Amethyst Raritanium
Hi-Potion (formlos): Glasflasche + glitzernde Melonenscheibe + Zucker
Paopu-Frucht (formlos): Apfel + Glowstonestaub + Herz
```

Bolts sind bewusst keine Zutat: sie landen sofort auf dem Konto. (Nugget = Eisennugget, Haken = Haken der Stolperdrahtfalle.)

Alles ist außerdem im eigenen Kreativ-Tab **Kingdom Omnitrix**.

## Bauen

Voraussetzung: **Java 21** (z. B. [Adoptium](https://adoptium.net)).

- **Windows**: Doppelklick auf `Mod bauen.bat`
- **Linux/macOS**: `./gradlew build`

Die fertige Datei liegt danach in `build/libs/kingdomomnitrix-0.6.0-alpha.jar`.

Jeder Push auf GitHub baut die Mod automatisch (Workflow **Mod bauen**). Die fertige `.jar` liegt beim Workflow-Lauf unter *Artifacts*.

Vor dem Bauen lassen sich die Ressourcen schnell prüfen:

```
python tools/check_assets.py          # Übersetzungen, Modelle, Texturen, Rezepte, Loot
python tools/generate_textures.py --check
```

## Heldendaten, HUD und Befehle

Jeder Spieler hat **Heldendaten**: Stufe, Erfahrung, Bolt-Konto, freigeschaltete Aliens und Story-Flags.
Sie werden mit dem Spieler gespeichert, bleiben beim Tod erhalten und werden nur an den eigenen Client gesendet.
Oben links zeigt ein Status-Panel Stufe, Erfahrungsbalken und Bolts (ausgeblendet mit F1 und im F3-Menü).

Befehle (nur OP, Stufe 2). `[spieler]` ist optional, ohne Angabe wirkt der Befehl auf dich selbst:

| Befehl | Wirkung |
|---|---|
| `/hero debug [spieler]` | zeigt alle Heldendaten |
| `/hero bolts add <anzahl> [spieler]` | bucht Bolts (negativ = abbuchen) |
| `/hero bolts set <anzahl> [spieler]` | setzt das Bolt-Konto |
| `/hero level set <stufe> [spieler]` | setzt die Stufe (1–99) |
| `/hero xp add <menge> [spieler]` | gibt Erfahrung, steigt automatisch auf |
| `/hero alien unlock\|lock <id> [spieler]` | schaltet ein Alien frei oder sperrt es |
| `/hero flag set\|clear <flag> [spieler]` | setzt oder löscht ein Story-Flag |
| `/hero reset [spieler]` | setzt alle Heldendaten zurück |
| `/hero transform <alien> [spieler]` | verwandelt sofort (ohne Freischaltung/Nachladen) |
| `/hero revert [spieler]` | verwandelt zurück |
| `/hero dna <alien> [spieler]` | gibt eine DNA-Probe |
| `/hero quest start\|complete <quest> [spieler]` | startet eine Quest bzw. schließt sie sofort mit Belohnung ab |
| `/hero quest reset <quest>\|all [spieler]` | setzt Quest-Fortschritt zurück |
| `/hero quest list [spieler]` | zeigt alle Quests mit Status |
| `/hero npc spawn <npc>` | stellt einen NPC an deiner Position auf |
| `/hero world traverse_town [spieler]` | teleportiert nach Traverse Town (baut die Stadt beim ersten Mal) |

## Installieren

1. [Fabric Loader](https://fabricmc.net/use/installer/) (mindestens 0.17) für Minecraft **1.21.1** installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) und [GeckoLib](https://modrinth.com/mod/geckolib) (jeweils für 1.21.1) in den `mods`-Ordner legen — auch auf dem Server.
3. `kingdomomnitrix-0.6.0-alpha.jar` ebenfalls in den `mods`-Ordner legen.
4. Minecraft mit dem Fabric-Profil starten.

Zum Testen ohne Installation: `./gradlew runClient` startet ein Minecraft mit der Mod.

## Projektaufbau

```
src/main/java/com/santiq/kingdomomnitrix/      Server + gemeinsamer Code
  KingdomOmnitrix.java      Einstieg: Registrierung, Alien-Resistenzen, Bolt-Drops
  player/                   HeroData (gespeichert + synchronisiert), HeroDataAccess
  command/                  /hero-Befehle
  registry/                 Items, Block, Entities, Statuseffekte, Kreativ-Tab
  alien/                    Omnitrix, AlienDefinition, AlienRegistry, TransformationManager, DNA
  ability/                  Fähigkeits-Typen (AbilityRegistry, BuiltinAbilities)
  networking/               Pakete Client ↔ Server
  keyblade/  magic/         Schlüsselschwert, Zauber (Prototyp, Phasen 4–6)
  weapon/                   R&C-Waffen, Bolt-Konto, Waffen-Terminal (Phase 8)
  gadget/                   Heli-Pack (Prototyp, Phase 9)
  enemy/                    Herzlose, Dunkelheitsrisse (Phase 7)
  item/  util/              Hi-Potion, Hilfsklassen
src/client/java/com/santiq/kingdomomnitrix/client/   nur Client (eigenes Source-Set)
  hud/                      Status-Panel, Omnitrix-HUD
  input/                    Tastenbelegung
  screen/                   Alien-Rad
  render/                   Renderer, Alien-Körper (GeckoLib)
  mixin/                    Spielermodell durch Alien-Körper ersetzen
src/main/resources/
  assets/kingdomomnitrix/   Modelle, Texturen, Übersetzungen (de_de, en_us)
  data/kingdomomnitrix/     Rezepte, Loot-Tabellen
tools/generate_textures.py  erzeugt alle Texturen neu (pip install pillow)
tools/generate_alien_models.py  erzeugt Alien-Körper (Geometrie, Animation, Textur)
tools/check_assets.py       prüft alle Ressourcen auf Lücken
docs/TODO_SANTIQ.md         was nur SANTIQ erledigen kann (Downloads, Freigaben, Tests)
docs/ANALYSE_UND_ROADMAP.md Bestandsaufnahme, Architektur, Roadmap
```

## Werte anpassen

| Was | Wo |
|---|---|
| Alien-Werte, Dauer, Nachladen, Energie, Fähigkeiten, DNA-Quellen | `data/kingdomomnitrix/kingdomomnitrix/alien/*.json` |
| Verhalten der Fähigkeits-Typen | `ability/BuiltinAbilities.java` |
| Zauber-Schaden, Abklingzeiten | `Spell.java` |
| Geschoss-Schaden | `HeroProjectileEntity.Kind` |
| Magazin, Bolts pro Nachladen | `CombusterItem.MAGAZINE`, `AMMO_PER_BOLT` |
| Häufigkeit der Herzlosen | `ModEntities.register()` (Gewicht 40, Gruppen 1–3) |
