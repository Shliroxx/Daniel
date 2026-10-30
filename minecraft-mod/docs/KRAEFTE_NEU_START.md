# ALIEN EVOLUTION 2.0 – Kräfte-Neufassung der Start-Aliens

made by SANTIQ

Stand: **Heatblast umgesetzt und im Spiel getestet** · Vierarm und XLR8 folgen im selben Ablauf.

Ziel: AEs Kräfte als Qualitätslatte (echte Bauteile, Animationen, klare Wirkung), aber **eigene** Kräfte mit mehr
Tiefe: Aufladen, Kombos untereinander, Reaktion auf die Umgebung, Stufen. Keine 1:1-Kopie.

Quelle AE: `data/alienevo_aliens/palladium/powers/{pyronite,tetramand,kineceleran}.json` (Alien Evolution 1.1.3).
Pool je Alien bleibt 8 Tasten-Kräfte + Kombos (Sprint-/Luft-/Schleich-Angriff, Sturz, Finisher) + Passive.

Legende: **AE** = was AE macht · **Neu** = unsere Fassung · **Anders/besser** = was über AE hinausgeht.

---

## Heatblast

| # | AE | Neu | Anders/besser |
|---|---|---|---|
| 1 | Feuerball (Rechtsklick, explodiert beim Aufprall) | **Plasmakern** – Tippen: schneller Feuerball; Halten (1 s): großer Kern, der beim Aufprall in 5 brennende Splitter zerspringt | Aufladen, Splitter treffen um Ecken; Treffer laden Kernhitze |
| 2 | Pyro-Strahl (Zweihand-Strahl, zielen) | **Sonnenlanze** – gehaltener Zweihand-Strahl, der mit der Dauer heißer wird (Gelb → Weiß → Blau, mehr Schaden), Stein glüht zu Magma | Hitzestufen sichtbar, Gelände reagiert |
| 3 | Feueratem (Schleichen + halten, Kegel) | **Drachenatem** – breiter Kegel aus dem Mund; trifft er Wasser, entsteht eine blendende Dampfwolke | Umgebungs-Reaktion (Wasser → Dampf) |
| 4 | Hitzeabsorption (halten, lädt Supernova-Anzeige) | **Wärmesog** – zieht Feuer, Lava, Fackeln und brennende Gegner im Umkreis in sich, löscht sie und lädt die Supernova; Lava gibt zusätzlich Glutpanzer | Ressourcen-Spiel mit der Welt |
| 5 | Feuer-Surfen (Brett in der Luft) | **Flammenbrett** – Surfen auf einem Feuerbrett (Modell), die Spur entzündet den Boden; Absprung = Meteortritt mit Einschlag | Brett als Bauteil, Absprung-Kombo |
| 6 | Feuertornado (drehen) | **Feuerwirbel** – wandernder Tornado, hebt Gegner an; Feuerbälle hinein verstärken ihn | Kombo mit Plasmakern |
| 7 | Feuer-Transport (Sprung) | **Kometensatz** – Heatblast wird zur Feuerkugel, fliegt zum anvisierten Punkt und explodiert dort | Bewegung + Angriff in einem |
| 8 | Supernova (Ultimate, nach Anzeige) | **Supernova in Stufen** – halbe Anzeige: Nova; volle Anzeige: Nova + zurückbleibender Sonnenkern | Stufen statt An/Aus |

Passiv: Kernhitze (wächst mit Treffern), Glutberührung, Hitzeflimmern (bleiben).

### Heatblast – Umsetzung (Code: `ability/HeatblastPowers.java`)

| Slot | Kraft | Bedienung | Wechselwirkung |
|---|---|---|---|
| 1 | Plasmakern | Tippen = Feuerball (glühend 3, blau 5) · Halten 1 s = großer Kern mit Rückstoß, Explosion, 5 Splitter (blau 8) | Kern fliegt in den Feuerwirbel → Wirbel wächst |
| 3 | Flammenbrett | 4 s Surfen, Blick lenkt auch hoch/runter · eigenes Brett-Modell (`tools/generate_flame_board.py`) · Schleichen = Meteortritt | Spur zündet den Boden, Berührung schleudert weg |
| 2 | Drachenatem | Halten bis 3 s, Kegel aus dem Mund | Wasser → Dampfwolke (Blindheit, Verlangsamung, Verbrühen) · Atem in den Wirbel → Stufe +1 |
| 4 | Sonnenlanze | Halten bis 4 s · Stufe gelb (erstes Ziel) → weiß (durchbohrt, Sand → Glas) → blau (Stein → Magma) | Schnee/Eis schmilzt immer; Gelände nur bei erlaubter Zerstörung |
| 5 | Wärmesog | Halten bis 3 s, Radius 8 | zieht Feuer, Lava (→ Obsidian/Bruchstein), Magma, Lagerfeuer, Feuergeschosse, Glut brennender Gegner · Lava = Glutpanzer (bis 4 Herzen) |
| 7 | Feuerwirbel | 6 s, folgt dem Blick, hebt an, Feuerspur | wächst bis Stufe 3 (Plasmakern, Drachenatem): größer, länger, mehr Schaden |
| 8 | Kometensatz | Feuerkugel bis 26 Blöcke, unverwundbar | streift Gegner unterwegs, Einschlag mit Krater |
| 6 | Supernova | ab halbem Kern · 1 s Aufsteigen und Sammeln | voller Kern: blaue Nova + Sonnenkern (6 s Sog, dann Implosion) |

Kernhitze: „Überhitzt“ (100) bleibt jetzt bestehen, bis eine Kraft ihn entlädt (vorher kühlte er sofort ab und war
nach 5 Ticks verloren); nur Wasser und Regen kühlen ihn. Die Supernova verbraucht den ganzen Kern — Blaufeuer
für eine Kraft oder volle Supernova ist eine echte Entscheidung.

Getestet im Spiel (Client, Testarena): alle 8 Kräfte mit Bildern; Selbsttest 13/13 (Supernova situativ: kalter
Kern). Nicht getestet: echtes Halten über die Tasten (der Test hält per Befehl bis zur Auto-Auslösung),
Mehrspieler-Sichtbarkeit des Bretts bei anderen Spielern.

## Vierarm

| # | AE | Neu | Anders/besser |
|---|---|---|---|
| 1 | Seismischer Schlag (Linienwelle nach vorn) | **Bruchlinie** – Linienwelle, Blöcke springen nacheinander hoch; Halten fächert sie in 3 Linien auf | Aufladen, Fächer |
| 2 | Felsbrocken (heben, werfen) | **Felsbrocken** (eingebaut) + gehalten blockt er Geschosse; der Wurf zerbricht nach dem ersten Aufprall in Splitter | Schild-Funktion, zweiter Einschlag |
| 3 | Schallklatschen (Druckwelle) | **Donnerklatschen** – Schallkegel, schlägt Geschosse zurück, zerbricht Glas und Blätter | Konter gegen Fernkämpfer |
| 4 | Sprung (aufladen) + Schleichen im Fall = Körperramme | **Titanensprung** – aufladbar; im Fall nochmal drücken = Körperramme mit Krater | Zwei Phasen |
| 5 | Wirbelhaken (drehen) | **Hurrikan-Haken** – dreht sich mit vier Armen, zieht Gegner an, letzte Drehung schleudert sie weg | Sog + Wurf |
| 6 | Blocken (Arme hoch) | **Vierfach-Deckung** – Arme gekreuzt, 70 % weniger Schaden; perfektes Timing (erste 0,3 s) = Gegenschlag | Konter-Fenster |
| 7 | Wandklettern (passiv/Taste) | Wandklettern (eingebaut, passiv) | – |
| 8 | – | **Vier-Faust-Salve** bleibt, Ultimate **Titanen-Meteor** bleibt | eigene Kräfte |

Passiv: Tetramand-Wut, Vier-Schlag-Kombo, Tetramand-Haut, Wandkletterer (bleiben).

## XLR8

| # | AE | Neu | Anders/besser |
|---|---|---|---|
| 1 | Gleittritt (bis 5 Tritte hintereinander) | **Kettenkick** – bis 5 Tritte, jeder springt automatisch zum nächsten Gegner; Kombo-Zähler erhöht den Schaden | Zielsprung + Steigerung |
| 2 | Zyklon (im Kreis rennen) | **Zyklon** – wandert mit XLR8; über Wasser wird er zur Wasserhose, an Feuer/Lava zum Feuerwirbel | Umgebungs-Reaktion |
| 3 | Wandlauf (Taste) | **Wandlauf + Wandsprung** – Wände hochrennen, Absprung zur Seite | Bewegungs-Kette |
| 4 | Wasserlauf (passiv ab Tempo 2) | **Wasserlauf** (passiv) + beim Bremsen eine Wasserwand, die Gegner wegspült | Bremsen wird Angriff |
| 5 | Tempo-Boost (passiv ab Tempo 5) | **Tempostufen 1–5** – Laufen lädt Stufen; Stufe 5 = Schallmauer mit Knall | sichtbare Stufen |
| 6 | Schlaghagel (Rechtsklick halten, Auto-Ziel) | **Schlaghagel** – springt automatisch zwischen nahen Gegnern und trifft jeden mehrfach | mehrere Ziele |
| 7 | – | **Zeitsprung** (eigene Kraft, bleibt) | – |
| 8 | – | Ultimate **Mach-Sprint** (bleibt) | – |

Passiv: Kineceleran-Tempo, Schemenläufer, schnelle Reflexe (bleiben).

---

## Ablauf nach Freigabe

1. Je Alien umsetzen, mit AE-Bauteilen/-Animationen, wo AE sie hat (Feuerbrett, Brocken, Tornado).
2. Selbsttest (`/hero selftest <alien>`) + Bilder im Spiel.
3. Danach die nächsten Aliens im selben Format.
