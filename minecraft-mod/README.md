# Heroverse Crossover — Minecraft-Mod

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

Alles ist außerdem im eigenen Kreativ-Tab **Heroverse**.

## Bauen

Voraussetzung: **Java 21** (z. B. [Adoptium](https://adoptium.net)).

- **Windows**: Doppelklick auf `Mod bauen.bat`
- **Linux/macOS**: `./gradlew build`

Die fertige Datei liegt danach in `build/libs/heroverse-1.0.0.jar`.

## Installieren

1. [Fabric Loader](https://fabricmc.net/use/installer/) für Minecraft **1.21.1** installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) (Version für 1.21.1) in den `mods`-Ordner legen.
3. `heroverse-1.0.0.jar` ebenfalls in den `mods`-Ordner legen.
4. Minecraft mit dem Fabric-Profil starten.

Zum Testen ohne Installation: `./gradlew runClient` startet ein Minecraft mit der Mod.

## Projektaufbau

```
src/main/java/com/daniel/heroverse/
  Heroverse.java            Einstieg: Registrierung, Alien-Resistenzen, Bolt-Drops
  registry/                 Items, Block, Entities, Statuseffekte, Kreativ-Tab
  item/                     Schlüsselschwert + Zauber, Omnitrix + Aliens, R&C-Waffen
  entity/                   Schatten, Geschosse, Fusionsgranate
  effect/AlienEffect.java   Alien-Form als Statuseffekt (Attribute + Partikel)
  client/                   Renderer
src/main/resources/
  assets/heroverse/         Modelle, Texturen, Übersetzungen (de_de, en_us)
  data/heroverse/           Rezepte, Loot-Tabellen
tools/generate_textures.py  erzeugt alle Texturen neu (pip install pillow)
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
