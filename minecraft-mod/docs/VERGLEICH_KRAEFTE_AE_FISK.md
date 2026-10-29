# Kräfte-Vergleich: Kingdom Omnitrix gegen Alien Evolution und Fisk's Superheroes

made by SANTIQ · Stand: 2026-10-29 · Kingdom Omnitrix 1.1.0

## Grundlage

- **Alien Evolution 1.1.3:** das echte Mod-Paket von Modrinth, entpackt und ausgewertet. Grundlage sind die
  Kraft-Definitionen (`data/alienevo_aliens/palladium/powers/*.json`), die Modelle (`geo/aliens/*`), die Animationen und
  die Render-Ebenen. Gemessen wurde, *woraus* jede Kraft besteht.
- **Fisk's Superheroes 2.4.0 (1.7.10):** ebenfalls das echte Paket. Ausgewertet sind die Kräfte
  (`data/powers/*.json`) und die Held-Renderer (`renderers/heroes/*.js`). Von Fisk's werden keine Dateien übernommen.
  Die AE-Dateien dürfen wir laut Absprache mit dem AE-Autor nutzen.
- **Videos:** Ansehen geht von hier aus nicht. Die Pakete zeigen aber genauer als ein Video, wie die Kräfte gebaut sind.

## Das Kernproblem in einem Satz

Bei AE und Fisk's sind Kräfte **Teile des Körpers und der Welt**: Das Modell verwandelt sich, echte Geschosse mit
Modell fliegen, echte Blöcke wachsen, und ein Modus verändert die Werte. Bei uns sind die meisten Kräfte
**Schaden plus Partikel** um ein Modell, das sich dabei nicht verändert.

**Die Ursache ist in unserem Werkzeug belegt.** `tools/import_alienevo.py` entfernt beim Import absichtlich alle
Kraft-Bauteile aus den AE-Modellen:

```
DROP = re.compile(r"sword|shield|boulder|_ball|ball_", re.I)   # „Knochen, die nur fuer Faehigkeiten da sind“
```

Ein Beispiel: Diamondhead hat in AE 66 Knochen, darunter `SWORD`, `SWORD_left`, `shield`, `shield_core` und
`shield_spin`, dazu die Animationen `sword_on/off`, `shield_spin` und `shards_on/off`. Bei uns bleiben davon 53 Knochen
übrig, Schwert und Schild fehlen. Dasselbe gilt für Upgrades Keulenfäuste (`MACE`, `mace_scale`) und seine
Flüssigform (`liquid_base`).

## Woraus die Kräfte bestehen

| Baustein | Alien Evolution | Fisk's Superheroes | Kingdom Omnitrix |
|---|---|---|---|
| **Körper verwandelt sich** (Klinge, Schild, Keule, Visier, Tentakel, Zunge) | GeckoLib-Animationen auf eigenen Knochen, ein- und ausfahrbar (`render_layer_animation`, z. B. Diamondhead `sword_on`, Upgrade `hands_on`, Grey Matter `visor_anim`, Ghostfreak `tentacles`) | `shield`-Effekt am Arm mit `unfold` (Killer Frosts Eisklingen wachsen mit der Ladung), `opening_mask`, `wings`, `cape` | **fehlt**: Import entfernt die Teile, es gibt nur feste Posen |
| **Modus/Haltung** mit anderen Werten | Diamondhead Kampfmodus +14 Angriff und Rückstoß 3; Schildmodus −12 Angriff, immun gegen Geschosse und Explosionen, Stachelaura | Ladungen (Cryo-Charge) erhöhen den Schaden, HUD-Balken | nur Zähler wie Hitze oder Tempo, **keine Modi mit Körperwechsel** |
| **Geschosse mit Modell** | Vierarms Felsbrocken (`rock_throw/boulder`, Größe 1,5, 25 Schaden), Diamondheads Kristallsplitter (`shard_diamond`), Wildmutts Stacheln (`quill_barrage`) | Eiszapfen, Pfeile, Schilde als Modell | Blockstücke für Würfe und Trümmer, sonst **Partikel-Leuchtspuren** |
| **Strahlen als Geometrie** | `energy_beam` (Heatblast-Doppelstrahl 9 Schaden 15 m, Upgrade-Strahl, Blitz) | `bindBeam` (18×: Repulsor, Hitzeblick …) | Strahlen-FX vorhanden (FX-Engine), **gleichauf** |
| **Welt-Konstrukte** | Diamondhead: Kristallsäulen-Lauf, Block-Setzer, Stachel-Brücke, Kristallkuppel; Vierarm: Blockwelle in einer Linie (15 m) | – | Kristallspitzen, Krater, Wurzeln, Netze als echte Blöcke, **gleichauf oder vorne** |
| **Bewegung als Kraft** | Wandklettern (Vierarm, Wildmutt, Ripjaws, Grey Matter), Wandlauf und Wasserlauf (XLR8), Pyro-Surfen, Sprungladung | Speedster mit Speed-Balken und Nachbildern, Netzschwingen mit Seilphysik | Wandlauf (XLR8, Spidermonkey), Kristallsurfen, **neuer Flug** — Wandklettern fehlt bei Vierarm, Wildmutt, Ripjaws und Grey Matter |
| **Körperwerte** | Vierarm: Größe 2,0, Rüstung 80, +40 Leben, rückstoßimmun · Diamondhead: Größe 1,35, +20 Leben, +8 Angriff, rückstoßimmun · Atomix: Größe 3,3, +100 Angriff, +80 Leben | Werte je Anzug | Vierarm: Größe 1,4, +10 Leben, keine Rüstung · Diamondhead: Größe 1,0, keine Lebenspunkte, kein Angriff — **viel zu schwach und zu gleich** |
| **Fortschritt** | Upgrade-Baum je Alien (`gui_position`: Angriff, Rüstung, Leben in Stufen) | Fabricator-Knoten | Meisterschaft ★1–10 (automatisch) |

## Alien für Alien

| Alien | AE: echte Bausteine | Bei uns | Größte Lücke |
|---|---|---|---|
| **Heatblast** | Doppel-Feuerstrahl (Strahl), Feuerball mit Explosion, Feuerwalze, Pyro-Surfen, Supernova (100 Schaden, 15 m), Hitze aufnehmen aus Feuerblöcken | Feuerstoß, Feuerexplosion, Flammenschub, Infernowelle, Flammenschild, Supernova, Flammenwerfer, Meteorhagel, Kernhitze, jetzt echter Flug | Körper glüht nicht stärker mit der Kraft, Feuerball ist eine Leuchtspur statt Kugel |
| **XLR8** | Wasserlauf, Wandlauf, Schweif (`trail`), 6 Sprint-Stöße, Visier `mask_on/off`, Tempo +1,6 | Sturmangriff, Ausweichen, Schlaghagel, Zyklonlauf, Lichtgeschwindigkeits-Hagel, Tempo-System, Wandlauf | **Visier klappt nicht**, Schwanz-Knochen anders benannt |
| **Vierarm** | Felsbrocken-Geschoss mit Modell, Blockwelle, Schallklatschen, Sprungladung, Wandklettern, Explosion bei Landung, Größe 2, Rüstung 80, +40 Leben | Bodenschlag mit Krater, Werfen (Blockbrocken), Titanensprung, Donnerklatschen, Eisenhaut, Erdbeben, Vier-Faust-Salve | **zu klein und zu schwach** (1,4 statt 2,0), kein Wandklettern |
| **Diamondhead** | **Kristallschwert** und **Kristallschild** am Arm (Modell, Modus mit Werten), Splitter-Geschosse mit Modell, Säulen-Lauf, Block-Setzer, Kuppel | Kristallsalve, -klinge, -surfen, Stachelausbruch, Kristallpanzer, Kristallsturm, Kristalllanze, Kathedrale | **Schwert und Schild fehlen im Modell**, Klinge ist nur ein Schwung mit Partikeln |
| **Grey Matter** | **Visier** und **Zunge** als Animation, Bauplan-Erfindungen, Wandklettern, Größe winzig | Analyse, Schwachstelle, Huschen, Technikfalle, Notreparatur, Superhirn, Kopfsprung, Mini-Geschütz, Galvan-Labor | Visier/Zunge fehlen, kein Wandklettern |
| **Wildmutt** | Biss-Animation, Raubtier-Sicht (Shader), Leuchten, Werkzeug-Hände (gräbt 30× schneller), Stachel-Geschosse mit Modell, Wandklettern | Ansprung, Stachelsalve, Gebrüll, Zerfleischen, Witterung, Urwut, Biss, Wilde Hatz, Jagd-System | Stacheln als Modell fehlen, kein Graben, kein Wandklettern |
| **Stinkfly** | Schleim-Geschosse, Sonar, `wet_on/off` (Flügel nass) | 8 Kräfte, Toxin-Schichten, echter Flug | gleichauf; Flügel nass/trocken fehlt |
| **Ripjaws** | Biss-Animation, Wasser-Sprint, 4 Geschosse, Wandklettern, Schwimmtempo 4 | 8 Kräfte, Gezeitenzonen, Austrocknen | Biss als Modell-Animation fehlt |
| **Upgrade** | **Keulenfäuste** (`MACE`, Animation `hands_on`), **Pfützenform** (`liquid_base`, `puddle`), Strahl, Elytra-Flug | Optikstrahl, Flüssige Form, Technik-Upgrade, Keulenfäuste, Systemübernahme, Plasmakanone | Keulen und Pfütze **fehlen im Modell**, Flüssige Form ist nur ein Effekt |
| **Ghostfreak** | **Tentakel** und Stoß-Animation, Unsichtbarkeit, Durchlässigkeit | 8 Kräfte, Spuk-System, Phasen durch Wände, Flug | Tentakel fehlen als Modell-Animation |
| **Cannonbolt** | Kugelform mit Schalen (`ball_in/out`), Rollphysik, Aufprall, Sog, Blockwelle | eigenes Kugel-Modell, Rollen, Abprallen | gleichauf |
| **Big Chill / Humungousaur / Swampfire / Chromastone / Echo Echo** (Alien-Force-Erweiterung von AE) | wenige Kräfte: Strahl, Geschosse, Durchlässigkeit, Wurzeln, Klone, Panzerhaut (`vaxasaurian_armor`) | jeweils 8 Kräfte plus eigenes System | **wir vorne** an Inhalt; Humungousaurs Panzerplatten als Modell fehlen |
| **Atomix** | fast nur Werte (Größe 3,3, +100 Angriff, Rüstung 1024) | 8 Kräfte, Kernspaltung | wir vorne |

Rath, Spidermonkey, Way Big, Alien X, Brainstorm und Goop gibt es in AE nicht. Für sie gelten die passenden
Fisk-Helden als Maßstab:

| Unser Alien | Fisk-Gegenstück | Was Fisk's hat |
|---|---|---|
| XLR8 | The Flash | Speed-Balken, Nachbilder, Blitz-Schweif (`lines`), Phasen durch Wände |
| Spidermonkey | Spider-Man (4 Fassungen) | Netzschwingen mit Seilphysik, Netz-Geschosse, Wandkrabbeln |
| Way Big | Atom Smasher | echte Größenänderung mit Kraft und Reichweite nach Größe |
| Jetray / Stinkfly | Falcon | eigenes Flügel-Modell (`ModelFalconWings`), das ausklappt |
| Big Chill | Killer Frost / Captain Cold | Eisklingen fahren aus dem Arm aus (`shield` + `unfold`), Cryo-Ladung als HUD |
| Upgrade | Iron Man | Rüstungsteile, Repulsor-Strahl aus der Hand, Booster-Flammen, Helm klappt auf (`opening_mask`) |
| Ghostfreak | Martian Manhunter / Spectre | Durchlässigkeit, Formwandel |
| Echo Echo | Black Canary | Schallschrei als Strahl mit Rückstoß |

## Was das für den Umbau heißt — „Echte Bauteile“ (vor P2)

1. **Import repariert.**
   - Die Kraft-Knochen bleiben im Modell und sind normal unsichtbar: Schwert, Schild, Keulen, Pfütze, Visier, Tentakel,
     Zunge.
   - Die AE-Kraft-Animationen werden mitgenommen: `sword_on`, `shield_spin`, `hands_on`, `puddle`, `visor_anim`,
     `tentacles`, `bite`, `mask_on`.
2. **Bauteil-Zustand je Spieler.** Der Server synchronisiert ihn an alle. Der Renderer fährt die Teile mit der
   AE-Animation aus und ein, statt nur Partikel zu zeigen.
3. **Modi mit echten Werten** (Taste halten oder umschalten):
   - Diamondhead **Kristallschwert**: mehr Angriff und Reichweite.
   - Diamondhead **Kristallschild**: blockt Geschosse und Explosionen, Gegner verletzen sich an den Stacheln.
   - Upgrade **Keulenfäuste** und **Pfützenform**: in der Pfützenform flach, unverwundbar und schnell.
   - Grey Matter **Visier**: Analyse.
   - Ghostfreak **Tentakel**.
   - XLR8 **Visier**: Sprintmodus.
4. **Geschosse mit Modell**: Vierarms Felsbrocken, Diamondheads Kristallsplitter und Wildmutts Stacheln, dazu weitere
   Modelle für Heatblasts Feuerball und Big Chills Eiszapfen.
5. **Körperwerte nach Serie und AE**:
   - Vierarm: Größe 2, schwere Rüstung, viele Lebenspunkte, rückstoßimmun.
   - Diamondhead: hart und rückstoßimmun.
   - Grey Matter: winzig und flink.
   - Way Big und Alien X: deutlich darüber.
6. **Wandklettern** für Vierarm, Wildmutt, Ripjaws und Grey Matter, wie bei AE.

**Reihenfolge:** zuerst Diamondhead, dann Upgrade und Vierarm, weil dort die Bauteile schon fertig im AE-Modell liegen.
Danach Grey Matter, Ghostfreak, XLR8, Wildmutt und Ripjaws. Am Ende folgen die Aliens ohne AE-Vorlage mit eigenen
Bauteilen nach Fisk-Vorbild.
