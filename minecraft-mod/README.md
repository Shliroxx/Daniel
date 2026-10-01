# Kingdom Omnitrix — Minecraft-Mod

made by SANTIQ · Mod-ID `kingdomomnitrix` · Version 0.2.0-alpha

Fan-Mod für **Minecraft 1.21.1 (Fabric)**, die drei Welten zusammenbringt:
**Kingdom Hearts**, **Ben 10** und **Ratchet & Clank**.

> Nicht-kommerzielles Fanprojekt. Kingdom Hearts gehört Square Enix/Disney, Ben 10 Cartoon Network,
> Ratchet & Clank Insomniac Games/Sony. Alle Texturen sind selbst erzeugt (`tools/generate_textures.py`).

## Inhalt

### Kingdom Hearts
| Ding | Was es tut |
|---|---|
| **Königsschlüssel** (Schlüsselschwert) | Starkes Schwert (Netherit-Stufe). **Rechtsklick** wirkt den gewählten Zauber, **Schleichen + Rechtsklick** wechselt ihn. Reparatur am Amboss mit Herzen. |
| Zauber **Feuer** | Feuerkugel, setzt Ziele in Brand (1 s Abklingzeit) |
| Zauber **Eis** | Drei Eiskugeln im Fächer, verlangsamen und frieren ein (1,5 s) |
| Zauber **Donner** | Blitz auf das anvisierte Ziel bzw. den anvisierten Block, 10 Schaden im Umkreis von 3 Blöcken, ohne Brandschaden an der Welt (4 s) |
| Zauber **Vita** | Heilt 4 Herzen (10 s) |
| **Schatten** (Herzloser) | Feindlicher Mob, erscheint nachts in der Oberwelt, verbrennt nicht im Tageslicht. Lässt zu 50 % ein **Herz** fallen. |
| **Hi-Potion** | Heilt sofort 4 Herzen |
| **Paopu-Frucht** | Nahrung: Regeneration II + Absorption II |

### Ben 10 — das Omnitrix
- **Schleichen + Rechtsklick**: Alien wählen
- **Rechtsklick**: verwandeln (60 Sekunden)
- In Alien-Form **Rechtsklick**: Spezialfähigkeit · **Schleichen + Rechtsklick**: zurückverwandeln
- Danach lädt die Uhr 15 Sekunden nach.

| Alien | Körper | Fähigkeit |
|---|---|---|
| **Heatblast** | Immun gegen Feuer und Lava, +2 Angriff | Feuerstoß |
| **XLR8** | +120 % Tempo, steigt ganze Blöcke hoch, kein Fallschaden | Sprint-Dash in Blickrichtung |
| **Vierarm** | 1,5× so groß, +6 Angriff, +5 Herzen, kaum Rückstoß | Bodenschlag: Schaden + Wegschleudern im Umkreis von 5 Blöcken |
| **Diamondhead** | +12 Rüstung, +6 Härte, immun gegen Geschosse | Kristallsplitter-Salve (5 Stück) |
| **Grey Matter** | 0,3× so groß, schneller, kaum Fallschaden | Analyse: zeigt Leben, Rüstung und Angriff des anvisierten Wesens |

### Ratchet & Clank
| Ding | Was es tut |
|---|---|
| **Bolts** | Währung und Munition. Jedes von einem Spieler besiegte Monster lässt 1–4 Bolts fallen. |
| **Bolt-Kiste** | Zerbricht sofort, gibt 3–8 Bolts |
| **OmniWrench 8000** | Schwert (Diamant-Stufe), **Rechtsklick** wirft den Schraubenschlüssel (8 Schaden) |
| **Combuster** | Plasma-Blaster, 39 Schuss. Leer? Lädt automatisch mit 1 Bolt aus dem Inventar nach (+8 Schuss) oder am Amboss mit Bolts. Zerbricht nie. |
| **Fusionsgranate** | Werfen, explodiert beim Aufprall — verletzt Gegner, zerstört keine Blöcke |
| **Heli-Pack** | In der **Zweithand**: Gleitflug statt Fallen, kein Fallschaden. Schleichen schaltet ab. |

## Rezepte (Werkbank)

```
Königsschlüssel       Omnitrix              OmniWrench 8000
.  .  Herz            Eisen Smaragd Eisen   .     Bolt  Bolt
.  Goldblock .        Smaragd Diamant Smaragd   .     Eisen Bolt
Stock .  .            Eisen Smaragd Eisen   Eisen .     .

Combuster             Fusionsgranate (x4)   Heli-Pack
Bolt Bolt Lohenstaub  .    Bolt .           Eisen Feder    Eisen
Eisen Redstone Eisen  Bolt TNT  Bolt        Bolt  Redstone Bolt
Eisen .    .          .    Bolt .           .     Bolt     .

Bolt-Kiste: 8 Bretter um 1 Eisennugget
Hi-Potion (formlos): Glasflasche + glitzernde Melonenscheibe + Zucker
Paopu-Frucht (formlos): Apfel + Glowstonestaub + Herz
```

Alles ist außerdem im eigenen Kreativ-Tab **Kingdom Omnitrix**.

## Bauen

Voraussetzung: **Java 21** (z. B. [Adoptium](https://adoptium.net)).

- **Windows**: Doppelklick auf `Mod bauen.bat`
- **Linux/macOS**: `./gradlew build`

Die fertige Datei liegt danach in `build/libs/kingdomomnitrix-0.2.0-alpha.jar`.

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

## Installieren

1. [Fabric Loader](https://fabricmc.net/use/installer/) für Minecraft **1.21.1** installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) (Version für 1.21.1) in den `mods`-Ordner legen.
3. `kingdomomnitrix-0.2.0-alpha.jar` ebenfalls in den `mods`-Ordner legen.
4. Minecraft mit dem Fabric-Profil starten.

Zum Testen ohne Installation: `./gradlew runClient` startet ein Minecraft mit der Mod.

## Projektaufbau

```
src/main/java/com/santiq/kingdomomnitrix/      Server + gemeinsamer Code
  KingdomOmnitrix.java      Einstieg: Registrierung, Alien-Resistenzen, Bolt-Drops
  player/                   HeroData (gespeichert + synchronisiert), HeroDataAccess
  command/                  /hero-Befehle
  registry/                 Items, Block, Entities, Statuseffekte, Kreativ-Tab
  alien/                    Omnitrix, Aliens, Alien-Effekt (Prototyp, wird in Phase 3 ersetzt)
  keyblade/  magic/         Schlüsselschwert, Zauber (Prototyp, Phasen 4–6)
  weapon/  gadget/          R&C-Waffen, Geschosse, Heli-Pack (Prototyp, Phasen 8–9)
  enemy/                    Herzlose (Prototyp, Phase 7)
  item/  util/              Hi-Potion, Hilfsklassen
src/client/java/com/santiq/kingdomomnitrix/client/   nur Client (eigenes Source-Set)
  hud/                      Status-Panel
  render/                   Renderer
src/main/resources/
  assets/kingdomomnitrix/   Modelle, Texturen, Übersetzungen (de_de, en_us)
  data/kingdomomnitrix/     Rezepte, Loot-Tabellen
tools/generate_textures.py  erzeugt alle Texturen neu (pip install pillow)
tools/check_assets.py       prüft alle Ressourcen auf Lücken
docs/ANALYSE_UND_ROADMAP.md Bestandsaufnahme, Architektur, Roadmap
```

## Werte anpassen

| Was | Wo |
|---|---|
| Verwandlungsdauer / Nachladezeit | `OmnitrixItem.TRANSFORM_TICKS`, `RECHARGE_TICKS` |
| Alien-Körperwerte | `ModEffects.register()` |
| Alien-Fähigkeiten, Abklingzeiten | `Alien.java` |
| Zauber-Schaden, Abklingzeiten | `Spell.java` |
| Geschoss-Schaden | `HeroProjectileEntity.Kind` |
| Magazin, Bolts pro Nachladen | `CombusterItem.MAGAZINE`, `AMMO_PER_BOLT` |
| Häufigkeit der Herzlosen | `ModEntities.register()` (Gewicht 40, Gruppen 1–3) |
