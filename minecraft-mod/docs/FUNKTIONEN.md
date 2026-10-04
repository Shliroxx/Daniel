# Kingdom Omnitrix — Funktionsdokumentation

made by SANTIQ · Mod-ID `kingdomomnitrix` · Version 0.10.0-alpha · Stand 2026-10-03

Fan-Mod für **Minecraft 1.21.1 (Fabric)**: Kingdom Hearts, Ben 10 und Ratchet & Clank in einer Welt.
Benötigt Fabric API und GeckoLib (Client und Server). Diese Datei beschreibt jede spielbare Funktion, die Steuerung,
alle Befehle, Spielregeln und Einstellungsdateien. Technische Tiefe steht in den Einzel-Dokumenten am Ende.

---

## 1. Steuerung

| Taste | Funktion |
|---|---|
| **Linksklick** | Kombo mit Keyblade/OmniWrench (letzter Schlag = Finisher); in der Luft Luft-Kombo |
| Linksklick **halten** | schwerer Schlag |
| **Linke Alt** | Ausweichen |
| **Feststelltaste** halten | Blocken (perfekter Block betäubt) |
| **Z** | Ziel erfassen (Kamera folgt) |
| **M** + Mausrad | Zauber wählen |
| **Pfeiltasten** | Kommandomenü: ↑/↓ wählen, → ausführen/öffnen, ← zurück |
| **G** | Omnitrix: Alien-Rad öffnen (auch Rechtsklick mit dem Omnitrix) |
| im Rad: **F** / **Tab** / **C** / **U** | Favorit setzen / Favoriten-Set wechseln / Code-Tastatur / Uniform wechseln |
| **X** halten | Omnitrix-Schnellwahl-Kreis |
| **N** | Smart Choice (empfohlenes Alien, zweiter Druck bestätigt) |
| **R / V / B** | Alien-Fähigkeit 1–3 |
| **Schleichen + R / V / B** | Alien-Fähigkeit 4–6 (ab Meisterschaft) |
| **K** | Heldenmenü (Fähigkeiten, AP) |
| **H** | Gadget-Gürtel |
| **J** | Heli-Pack ↔ Heli-Jet |
| **Y** | Gadget benutzen (Swingshot) |

Alle Tasten sind unter *Optionen → Steuerung → Kingdom Omnitrix* änderbar.

---

## 2. Kingdom Hearts

### Keyblades und Kampf
- **Königsschlüssel**, **Oathkeeper**, **Omega-Schlüssel** (Belohnung für den ersten Sieg über Dr. Nefarious) —
  3D-Modelle, Werte und Passiv-Fähigkeiten aus JSON (`data/<ns>/kingdomomnitrix/keyblade/`).
- **Kombo-System:** Kombo, Finisher, schwerer Schlag, Luft-Kombo, Ausweichen, Blocken, Zielerfassung.
- **Schwung-Spur:** leuchtendes Band folgt der Klinge (Kombo links/rechts, Luft schräg, schwerer Hieb senkrecht,
  Finisher als Kreis). Farbe je Waffe: Königsschlüssel gold, Oathkeeper hellblau, Omega-Schlüssel violett/grün,
  OmniWrench blau/gelb. Für alle Spieler sichtbar.
- **Keyblade-Schmiede:** Rechtsklick mit Keyblade → Upgrade gegen Bolts und Mythril/Orichalcum.

### Magie
Feuer, Eis, Donner, Vita. Wirken per Rechtsklick mit Keyblade oder Kommandomenü → Magie; kostet MP.
Stufen 1–3 über **Magie-Kristalle**. MP laden über Zeit und durch Treffer; leer → MP-Ladezeit.

### Herzlose
Shadow, Soldier, Air Soldier, Large Body, Darkball — nach den Kingdom-Hearts-Vorlagen gebaut (Emblem, Zickzack-Rüstung,
gelb leuchtende Augen). Large Body ist von vorn gepanzert. Sie erscheinen aus **Dunkelheitsrissen** (nachts, 3 Wellen,
Belohnung), bei Welt-Ereignissen, im Dungeon und draußen in Traverse Town.

### Items
**Hi-Potion** (Heilung), **Paopu-Frucht** (Regeneration + Absorption).

---

## 3. Ben 10 — das Omnitrix

Das Omnitrix muss nur im Inventar liegen. Am linken Arm sitzt das 3D-Gerät im Stil der Originalserie.

### Grundlagen
- **DNA-Proben** schalten Aliens frei (Gegner lassen sie fallen; Rechtsklick speichert). Hologramm „DNA ERFASST“.
- **Alien-Rad (G):** 3D-Hologramm-Rad, Mausrad/Ziehen dreht, Klick bestätigt, 1–9 Direktwahl, Mitte = zurück.
- **Schnellwahl-Kreis (X halten)**, **Favoriten-Sets** (F/Tab im Rad), **Schnellwechsel** zwischen Aliens.
- **Smart-Scan + Smart Choice (N):** das Gerät liest die Umgebung (Lava, Wasser, gepanzerte Gegner, Enge, Fernkampf,
  Redstone …) und empfiehlt ein Alien; im Kreis als goldene Silhouette.
- **Verwandlung** mit alien-eigener Sequenz (Blitz, Effekte, Klang), Rückverwandlung mit rotem Pulsieren.
- **Getrennte Lebenspunkte:** fällt das Alien, gibt es einen **DNA-Schock** — zurück in Menschenform, Menschen-Leben bleibt.
- **Notfall-Verwandlung:** in Lebensgefahr springt das Omnitrix selbst ein (danach 10 min nicht verfügbar).

### Die 12 Aliens

| Alien | 1 · 2 · 3 (R/V/B) | 4 SPECIAL ★3 · 5 UTILITY ★5 · 6 ULTIMATE ★8 (Schleichen + R/V/B) | DNA von |
|---|---|---|---|
| **Heatblast** | Feuerstoß · Feuerexplosion · Flammenschub | Infernowelle · Flammenschild · Supernova | Lohe, Magmawürfel |
| **XLR8** | Sturmangriff · Ausweichen · Schlaghagel | Zyklonlauf · Zeitsprung · Lichtgeschwindigkeits-Hagel | Shadow, Ozelot |
| **Vierarm** | Bodenschlag · Werfen · Riesensprung | Donnerklatschen · Eisenhaut · Erdbeben | Eisengolem, Verwüster |
| **Diamondhead** | Kristallsalve · Kristallklinge · Kristallsprung | Stachelausbruch · Kristallpanzer · Kristallsturm | Wächter |
| **Grey Matter** | Analyse · Schwachstelle · Huschen | Technikfalle · Notreparatur · Superhirn | Silberfischchen |
| **Wildmutt** | Ansprung · Stachelsalve · Wildes Gebrüll | Wildes Zerfleischen · Witterung · Urwut | Wolf, Fuchs |
| **Stinkfly** | Schleimspucke · Stinkwolke · Flügelstoß | Schleimbombe · Aufwind · Giftsturm | Biene, Phantom |
| **Ripjaws** | Kieferbiss · Flutstoß · Strudel | Schwanzhieb · Hydroheilung · Flutwelle | Wächter, Ertrunkener, Kabeljau |
| **Upgrade** | Optikstrahl · Flüssige Form · Technik-Upgrade | Keulenfäuste · Systemübernahme · Plasmakanone | Eisengolem, Creeper |
| **Ghostfreak** | Tentakelhieb · Phasenverschiebung · Spuk | Besessenheit · Schattenschritt · Albtraum | Vex, Phantom, Ghast |
| **Cannonbolt** | Kanonenkugel · Panzerkugel · Kugelsprung | Abpraller · Rollmodus · Kanonade | Gürteltier, Schildkröte |
| **Jetray** | Neuroschock · Schwanzblitz · Düsenstoß | Tiefflug · Windschatten · Neuroschock-Sturm | Delfin, Papagei |

Eigenschaften (Auswahl): Heatblast immun gegen Feuer/Lava · XLR8 schnell, kein Fallschaden · Vierarm groß und stark ·
Diamondhead immun gegen Geschosse · Stinkfly fliegt · Ripjaws stark im Wasser, trocknet an Land aus · Ghostfreak halbe
Schwerkraft · Wildmutt sieht Monster durch Wände · Upgrade schneller Abbau · Cannonbolt schwer gepanzert, rollt sich für jede Fähigkeit zur Kugel (eigenes Kugel-Modell, dreht mit der Strecke; Rollmodus steigt Stufen hoch) · Jetray fliegt, atmet unter Wasser, Neuroschock lähmt; in der Luft und im Wasser Flughaltung (Flügel auf, aus der AE-Fluganimation). Uniform „ultimate“ nutzt bei Jetray den evo-Look (AE hat nur zwei). Modelle, Texturen und Uniformen
(classic/evo/ultimate, **U** im Rad) nach Alien Evolution (mit Erlaubnis, siehe `CREDITS.md`).

### Eigene Alien-Systeme (über Alien Evolution hinaus)

AE liefert nur Modelle und Texturen; Fähigkeiten und Systeme sind eigene Entwicklungen.

| Alien | System | Wirkung |
|---|---|---|
| **Heatblast** | Kernhitze 0–100 % (orange Aura) | Jede Fähigkeit und jeder Treffer heizt auf, Lava/Feuer und der Nether heizen nach, Wasser und Regen kühlen schnell (Dampf, unter Wasser geschwächt), sonst 1 %/s Abkühlung. Ab 50 % **glühend**: +30 % Schaden, Feuerstoß mit 3 Kugeln, Hitze-Aura. Bei 100 % **überhitzt**: die nächste Fähigkeit entlädt den Kern als Blaufeuer (doppelter Schaden, größere Reichweite, Feuerstoß 5 explodierende Kugeln). Flammensurfen: gleitet 3 s auf einer Feuersäule, die alles darunter versengt. Inferno-Welle läuft als Flammenfront nach außen. Flammenschild verbrennt Geschosse und speichert Hitze. Supernova wächst mit der Hitze; bei vollem Kern bleibt ein Sonnenkern 4 s brennen. |
| **XLR8** | Tempo 0–100 % (blaue Leucht-Aura) | Rennen baut Tempo auf, Treffer legen nach, Stehen baut ab. Ab 50 % **verschwommen**: blaue Nachbilder, Nahkampf +Schaden nach Tempo. Bei 100 % **Schallmauer**: Überschallknall, beim Sprinten wird alles im Weg umgerannt, die nächste Fähigkeit verbraucht das Tempo für ihre stärkste Form. Schlaghagel: 4–12 Schläge nach Tempo (Schallmauer: Aufwärtshaken). Nachbild: Ausweichen, Gegner verlieren XLR8 und laufen zum Nachbild. Sturmangriff: Weite/Schaden nach Tempo. Zyklonlauf: XLR8 rennt im Kreis, der Wirbel zieht Gegner hinein und schleudert sie am Ende hoch. Zeitlupe: Gegner und Geschosse im Umkreis fast eingefroren. Lichtgeschwindigkeits-Hagel: blitzt Ziel für Ziel an (Anzahl nach Tempo, Schallmauer: jedes doppelt). |
| **Vierarm** | Wut 0–100 % (rote Aura) + Vier-Schlag-Kombo | Wut steigt mit ausgeteiltem und (4× stärker) eingestecktem Schaden, fällt nach 4 s ohne Kampf. Ab 50 % **Zorn**: Schläge mit mehr Wucht. Bei 100 % **Raserei**: nächste Fähigkeit in größter Form. Jeder 4. Nahkampftreffer innerhalb 1,5 s ist ein **Vierfach-Schlag** (Zusatzschaden, weiter Rückstoß, Bodenstoß). Bodenschlag/Donnerklatschen/Erdbeben sind laufende Druckwellen aus Erdbrocken. Werfen: das Ziel wird zum Geschoss und schlägt beim Aufprall ein; zu groß → Ringer-Wurf in den Boden. Titanensprung: Landung nach Fallhöhe. Donnerklatschen zerschlägt Geschosse im Kegel, betäubt. Eisenhaut: unverrückbar, Treffer geben doppelte Wut und 30 % Rückschaden. Erdbeben: 4 (Raserei 6) wachsende Wellen. |
| **Diamondhead** | Resonanz 0–100 % (grüne Aura) + Kristall-Konstrukte | Resonanz steigt, wenn Geschosse abprallen (Diamondhead ist immun), mit ausgeteiltem und eingestecktem Schaden; fällt nach 5 s ohne Kampf. Ab 50 % **facettiert**: jedes abprallende Geschoss fliegt als Kristallsplitter zum Schützen zurück, +30 % Schaden. Bei 100 % **Prisma**: nächste Fähigkeit in größter Form. Spitzenausbruch lässt als Welle echte Amethyst-Spitzen aus dem Boden wachsen (5 s, nur in Luft, zerspringen ohne Drops, beim Abbauen kein Amethyst, beim Server-Stopp sofort weg). Kristallsprung: Kristallsäule katapultiert, Landung mit Spitzenring. Kristallklinge: Halbkreis-Schwung (Prisma: Welle 12 Blöcke). Kristallpanzer: Nahkämpfer stoßen sich an Spitzen, Resonanz bleibt. Kristallsturm: Splitter kreisen 2,5 s um Diamondhead, dann fliegen sie nach außen. |
| **Grey Matter** | Analyse-Datenbank (keine Aura) | Jede Analyse eines neuen Gegners erhöht das Wissen über seine Art (0–5, dauerhaft gespeichert). Pro Stufe +8 % Schaden gegen diese Art — in **jeder** Form, auch als Mensch und als jedes andere Alien. Analysierte Ziele leuchten 10 s und nehmen von der ganzen Gruppe +20 %. Schwachstelle: der nächste Treffer irgendeines Spielers macht +150 % (ab Wissen 3 zusätzlich verlangsamt). Huschen: Gegner verlieren Grey Matter aus den Augen. Technikfalle: bis zu 3 Fallen, festhalten + markieren. Notreparatur: Werkzeug und Rüstung, heilt auch die Gruppe. Masterplan: alles im Umkreis wird analysiert, geschwächt, Grey Matter weicht 8 s lang jedem zweiten Angriff aus. |
| **Grey Matter: Galvan-Labor** | Taste **L** (nur als Grey Matter) | **Erfindungen** (Datenpaket `galvan_invention/*.json`: Ergebnis, Zutaten, Bolts, nötiges Wissen, nötige Hack-Stufe): Galvan-Energiezelle (lädt das Omnitrix sofort voll), Hi-Potion, Galvan-Scanner (Analyse in jeder Form, verschleißt), Kalibrier-Werkbank, Fusionsgranate, Swingshot, Heli-Pack (Hack 1), Orichalcum veredeln (Hack 2). **Omnitrix-Hack**: Zeit als Grey Matter ist Forschungszeit (dauerhaft). Nach 5 / 15 / 30 min wird je ein Hack verfügbar, gestartet im Labor, 10 s stillstehen (Bewegung/Treffer brechen ab): 1 Timer +20 % Verwandlungsdauer, 2 Laden −20 % Nachladezeit, 3 Zugang: Master Control frei (Code 10000) und +10 % Dauer. Admin: `/hero galvan research <min>`. |
| **Wildmutt** | Jagd: Beute + Blutrausch 0–5 (keine Aura) | Eine **Beute** (leuchtet): Witterung, Gebrüll, Zerfleischen oder der erste Treffer markieren sie. Treffer auf die Beute: Blutrausch +1 (je Stufe +10 % Schaden, fällt nach 4 s ohne Treffer), **Blutung** (Schaden je Sekunde). **Kettenjagd**: fällt die Beute, heilt Wildmutt, wird schneller und nimmt sofort die nächste Witterung auf (Duftspur). Stachelsalve: zielt auf die Beute, +1 Stachel je Rausch. Gebrüll: Gegner fliehen, nächster wird Beute, Rausch +2. Ansprung: springt die Beute an, drückt nieder. Zerfleischen: 3 + Rausch Bisse, verbraucht den Rausch für tiefe Wunden. Witterung: 48 Blöcke, stärkstes Ziel wird Beute, Duftspur jede Sekunde. Urraserei: Rausch voll und gesperrt, 25 % Lebensraub, Kills schlagen Gegner in die Flucht. |
| **Stinkfly** | Toxin-Schichten 0–5 am Gegner (keine Aura, grüne Blasen am Ziel) | Jeder Stinkfly-Treffer und jede Sekunde in seinen Giftwolken legt eine Schicht auf; ohne Nachschub fällt alle 3 s eine weg. Bei 5 **Zersetzung**: Verdorren, langsamer, jeder Treffer von irgendwem +30 %. **Ansteckung**: stirbt ein Ziel mit ≥ 3 Schichten, platzt es — Giftwolke, Nachbarn +2 Schichten. Gestankwolke: Giftwolke 6 s. Flügelstoß: hinterlässt eine Giftschleppe. Schleimbombe: Bogenwurf zum Zielpunkt, +2 Schichten, klebriges Schleimfeld. Aufwind: schleudert hoch und vergiftet. Giftsturm: Wolkenring; am Ende entladen sich alle Schichten im Umkreis (3 Schaden je Schicht). |
| **Ripjaws** | Gezeitenzonen (keine Aura, Wasserring am Boden) | Strudel, Flutstoß, Hydroheilung und Flutwelle hinterlassen **Gezeitenzonen**: Für Ripjaws zählen sie als Wasser (Wasserboni, kein Austrocknen). Gegner darin werden gebremst, zur Mitte gezogen und alle 2 s ertränkt. **Biss-Kombo**: jeder Kieferbiss innerhalb von 3 s +25 %, der dritte ist die **Todesrolle** (herumreißen, nach unten ziehen, festhalten). Flutstoß: nass dreimal so weit, rammt. Schwanzhieb: nass mit Wasserklinge, durchnässt (verlangsamt). Flutwelle: laufende Wasserwand, trifft jeden einmal. |
| **Upgrade** | Integration 0–100 (neongrüne Aura, bei Overclock cyan) | Steigt mit Treffern, jedem durchflossenen Gegner, Techno-Fusion und langsam neben Technik-Blöcken (Eisen, Kupfer, Redstone-Bauteile, Schienen); sinkt nach 6 s ohne Nachschub. Treffer legen **Naniten** (bis 5) in Gegner. Ab 50 % **vernetzt**: Optikstrahl durchschlägt alles in der Linie und springt auf 2 weitere Ziele, Morphwaffen-Treffer als Kettenblitz. Bei 100 % **Overclock** 8 s: +40 % Schaden, schneller, Anzeige läuft leer. Flüssigmetall: unverwundbar, jeder durchflossene Gegner wird verletzt und infiziert. Techno-Fusion: Werkzeug (Stromstoß bei jedem Treffer) oder ohne Werkzeug die Rüstung. Morphwaffen: Schockwelle je Treffer, entlädt gespeicherte Naniten. Systemübernahme: Mob kämpft einige Sekunden für Upgrade (+2 s je Nanit, Golems doppelt), Bosse/Spieler bekommen eine Systemstörung. Plasmakanone: 1 s Aufladen, durchschlagend, verbraucht die Integration für Schaden; im Overclock **Omega-Kanone** (breiter, Detonationen entlang der Bahn, ohne Blockschaden). |
| **Ghostfreak** | Spuk 0–100 (violette Aura, bei Seelenernte bleich-weiß) | Gegner sammeln **Angst** (0–3); bei voller Angst **Panik**: sie fliehen, greifen Ghostfreak nicht an und nehmen von ihm +30 % Schaden. Spuk steigt mit gesäter Angst, jeder Panik und Treffern; sinkt nach 6 s ohne Nachschub. Ab 50 % **unheimlich**: jeder Nahkampftreffer sät Angst, Tentakelhieb packt bis zu 3 Ziele. Bei 100 % **Seelenernte bereit**: Monster in 6 Blöcken bekommen von selbst Angst. **Hinterhalt**: nach Phasenform oder Schattenschritt macht der nächste Nahkampftreffer doppelten Schaden. Phasenform: unverwundbar, unsichtbar, Durchquerte erschauern. Spukschrei: Angst +2 im Umkreis (wächst mit Spuk). Besessenheit: Ghostfreak fährt in einen Mob, lässt ihn gegen andere Monster kämpfen, zehrt ihn aus und bricht danach heraus (Schaden, Angst ringsum; Schleichen beendet früher); Bosse/Spieler werden gelähmt und angehoben. Schattenschritt: hinter das anvisierte Ziel, sonst nach vorn. Albtraum: alles im Umkreis in Panik, Dunkelheit, Verdorren; verbraucht den Spuk für Reichweite und Schaden, bei vollem Spuk **Seelenernte** (Panik-Opfer verlieren 2 HP/s, jeder Tod heilt Ghostfreak). |
| **Big Chill** | Unterkühlung 0–100 am Gegner (keine Aura; Frostpartikel, Vanilla-Frostrand) | Eisatem (Kegel, löscht Feuer, friert Wasser zu schmelzendem Frosteis), Eissplitter, Nahkampf, Phasenflug und Umhang unterkühlen; ohne Nachschub taut es nach 3 s. Bei 100 **erstarrt** das Ziel 3 s (keine Bewegung, keine KI; Bosse nur kurz). **Zersplittern**: ein harter Treffer (6+) oder ein Eissplitter auf ein erstarrtes Ziel sprengt es — Bonusschaden, Splitter unterkühlen Nachbarn (Kettenreaktion). Eisgefängnis: echte Eisblöcke um das Ziel (nur in Luft), schmelzen nach 5 s ohne Drop. Kälteumhang: unsichtbar, Geschosse gehen durch, die Nähe kühlt, beim Enthüllen Frostausbruch. Absoluter Nullpunkt: alles im Umkreis erstarrt, nach 2 s zersplittern alle gleichzeitig. Fliegt; am Boden Umhang zu, in der Luft Flügel auf. |
| **Humungousaur** | Wachstum 0–100 in 3 Stufen (keine Aura; man sieht es an der Größe) | Steigt mit ausgeteiltem und stärker mit eingestecktem Schaden; nach 8 s ohne Kampf schrumpft er langsam. Je Stufe (34/67/100) **echt größer** (+12 % inkl. Trefferbox), +2 Angriff, +2 Rüstung, standfester, +0,5 Reichweite, etwas langsamer; jeder Stufenwechsel bebt. Alle Fähigkeiten wachsen mit: Megaschlag (Druckwelle durch alles in der Linie), Schwanzfeger (Rundumschlag, Kleine fliegen hoch), Stampede (Sturmlauf, rammt alles zur Seite, Taumeln), Tektonikbeben (Ringwelle nach außen, schleudert Bodennahe hoch), Panzerblock (kaum Schaden, kein Rückstoß, Nahkampf-Angreifer prallen ab), Riesenwuchs (15 s volle Stufe und noch größer, jeder Schritt bebt; danach Stufe 2). |
| **Swampfire** | Methan & Wildwuchs (keine Aura) | **Methanwolken** (Wolke, Düsenspur, Inferno) machen nur übel und langsam — erst **Feuer** zündet sie: Methanflamme entlang ihrer Bahn, brennende Fäuste am Ziel, brennende Gegner in der Wolke. Jede Explosion zündet überlappende Wolken kurz danach (Kettenreaktion), ohne Blockschaden und ohne gelegtes Feuer. Wurzelfessel: Ranken halten alle Gegner im Zielkreis 3 s fest. Nachwachsen: Heilung, Regeneration, schädliche Effekte weg, Pflanzen ringsum wachsen. Sumpfinferno: Gasring um Swampfire, nach 1,5 s selbst gezündet. **Neubildung** (passiv): tödlicher Schaden → nachgewachsen mit 30 % Leben (alle 90 s). Feuerfest, Regen/Wasser heilt langsam. |
| **Chromastone** | Spektralspeicher 0–100 (keine Aura; Regenbogenfunkeln am Körper wird mit der Ladung dichter) | **Absorption**: Energie-Schaden (Feuer, Blitz, Explosion, Magie, Schallwelle, Drachenatem) wird vollständig aufgesogen (×5 in den Speicher); Sonnenlicht lädt 1/s. Voll + weitere Energie → **Überladung**: Spektral-Nova. Prismenstrahl: Regenbogenstrahl, bricht sich am Ziel in 2 (ab 50 % 3) Teilstrahlen, verbraucht bis 30 Speicher für Bonusschaden. Spektralblitz: blendende Welle (mit 20 Speicher doppelt und größer). Lichtsprung: 10 Blöcke in Lichtgeschwindigkeit, trifft alles auf dem Weg. Kristallgitter: 5 s wird *jeder* Schaden aufgesogen. Spektralladung: Licht aus der Umgebung (hell 40, dunkel 15). Vollspektrum: ganzer Speicher, 7 Farbstrahlen kreisen 3 s. |
| **Echo Echo** | Echo-Chor (keine Aura) | **Klone** sind echte Wesen (AE-Modell, halbe Größe, höchstens 6, werden nie gespeichert und verklingen beim Zurückverwandeln): folgen, locken Monster von Echo Echo weg, schreien selbst. **Chor**: jede Schall-Fähigkeit kommt von Echo Echo *und* jedem Klon zugleich. **Schallresonanz**: Schall aus 2+ Quellen innerhalb 0,5 s auf dasselbe Ziel → +3 Schaden je weiterer Quelle und kurze Lähmung. Schallschrei (Kreuzfeuer auf den Zielpunkt), Echo-Teilung (2 Klone, 30 s), Schallstoß (Rückstoßsprung, schleudert Hintermänner weg), Schallmauer (Klone bilden einen Ring und pulsen 4 s nach außen), Schallschild (Geschosse prallen ab), Echo-Chor (Klone bis 6, dann drei Rundum-Schreie aller Stimmen). Immun gegen Schallwellen. |
| **Atomix** | Kernspaltung (keine Aura) | Gegner werden **bestrahlt** (0–5): 1 Schaden je Stufe und Sekunde, alle 3 s zerfällt eine Stufe; Nahkampf +1. **Spaltung**: stirbt ein Ziel mit 2+ Stufen, springt sein Kern auf die zwei nächsten Gegner (+2 Stufen, 3 Schaden) — Kettenreaktion. Atomstrahl (+1, auf vollem Ziel ×1,6 mit Explosion), Kernpuls (Welle, +2), Kernsprung (Landung bestrahlt +2), Eindämmungsfeld (hält ein Ziel 4 s schwebend, +1/s), Halbwertszeit (alle Strahlung im Umkreis sofort: 3 je Stufe), **Hero Time** (1,5 s aufladen, dann Kernexplosion r=12, volle Strahlung, ohne Blockschaden). Immun gegen Explosionen und Feuer. |
| **Rath** | Ringkampf (keine Aura) | **Herausforderung** macht ein Ziel 15 s zum **Rivalen** (Mobs ringsum greifen Rath an): Treffer auf ihn bilden eine **Griffkette**, jeder dritte ist ein Wurf; vom Rivalen nimmt Rath 30 % weniger Schaden. **Unbeugsam**: je weniger Leben, desto härter (bis +60 % bei 20 % Leben). Handgelenk-Kralle (Blutung, gegen den Rivalen doppelt), Sturmangriff, Suplex (Wurf auch ohne Rivalen), Appoplexian-Gebrüll (Gegner geschwächt, Rath stärker), **Rath-Finale** (springt mit dem Ziel hoch, rammt es in den Boden). |
| **Spidermonkey** | Netzjäger (keine Aura) | **Netze** halten fest; eingesponnene Ziele nehmen +50 % Schaden, sein Nahkampf trifft sie mit allen vier Armen (drei Zusatzschläge). **Wandlauf** passiv. Netzschuss, Netzfächer (Kegel), **Netzschwung** (zieht ihn zum anvisierten Block; ohne Block schlägt er fehl), Affenwirbel (8 Schläge), Netzfalle (Ring, 10 s scharf), **Netzkokon** (alle im Umkreis 6 s eingesponnen und hochgezogen). |
| **Way Big** | Titan (keine Aura) | **Kosmische Ladung** 0–100 (+5/s und je Treffer), der Kosmische Strahl verbraucht sie für Schaden und Breite. **Titanenschritt** passiv: Schritte lassen kleine Gegner taumeln. Titanenstampfer, Riesensprung (Landung bebt), Klatschwelle (Kegel, starker Rückstoß), To'kustar-Panzer (kaum Schaden, kein Rückstoß, Nahkämpfer prallen ab), **Sternensturz** (8 kosmische Einschläge, ohne Blockschaden). Größe ×3. |
| **Alien X** | Rat der Stimmen (keine Aura) | Jede Fähigkeit gehört **Serena** (Raumsprung, Zeitstillstand, Schöpfung) oder **Bellicus** (Realitätsblitz, Schwerkraftbruch, Urknall). Abwechseln baut **Einklang** 0–3 auf → nächste Fähigkeit ist **Allmacht** (×2). Zweimal dieselbe Stimme = **Uneinigkeit**: Fähigkeit verpufft, Alien X erstarrt 1 s. Zeitstillstand hält alles 5 s an, Schöpfung heilt die Gruppe, Urknall ohne Blockschaden. |
| **Brainstorm** | Elektro-Intellekt (keine Aura) | **Analyse** (15 s): Ziele nehmen +30 % Schaden von allen und leiten jeden Blitz auf zwei weitere. **Kraftfeld** saugt 5 s Schaden auf und lädt damit den nächsten Blitz. Blitzbogen, Statikgitter (5 s Blitze in einer Zone), Elektro-Levitation, **Gedankensturm** (4 s Blitze im Umkreis, kein Feuer). Immun gegen Blitz. |
| **Goop** | Polymorph (keine Aura) | Geschosse gehen durch ihn durch, kein Fallschaden. Gegner sammeln **Säure** (0–5, Schaden je Sekunde, Schwäche). **Projektor** als Schwachstelle: ein Treffer ab 8 Schaden lässt Goop 2 s zusammenfallen. Säurespucke, Schleimpeitsche (zieht heran), Pfützenform (unverwundbar), Verschlingen (4 s eingeschlossen und zersetzt), Säurepfütze (8 s), **Säureflut**. |
| *Ultimate (Evolve)* | Taste **U** (verwandelt, Meisterschaft 6+): 60 s Ultimate-Form, danach 5 min Abklingzeit (gilt für alle Aliens); `/hero evolve` für Admins ohne Bedingungen | Eigenes Modell (aus dem AE-Modell umgefärbt + Stacheln/Platten/Scheiben, etwas größer), Zusatz-Attribute je Form, Ultimatrix-Spirale. **Humungosaur**: Panzerplatten aus, Stachelkamm, Raketenfinger (Megaschlag feuert 5 explodierende Geschosse ohne Blockschaden), noch größer. **Big Chill**: Feuereis — jede Unterkühlung setzt in Brand, Atem/Zersplittern ×1,5, violett. **Swampfire**: blaues Feuer, Wolken größer und ×1,5, Flamme ×1,5, Kristalle. **Echo Echo**: Lautsprecherscheiben, bis 8 Klone, Schreie ×1,5 weiter und stärker. **Cannonbolt**: Stachelpanzer, alle Roll-Treffer ×1,5. **Wildmutt**: Stachelrücken feuert alle 2 s selbst auf die Beute, Stachelsalve doppelt. |
| *Anzeige* | nur die Aura (XLR8, Heatblast, Vierarm, Diamondhead, Upgrade, Ghostfreak) — kein HUD-Element | Die Aura am Körper (für alle sichtbar) wird mit dem Wert heller, größer und schneller; ab 50 % zweite Hülle, bei 100 % Farbwechsel (XLR8 weiß-blau, Heatblast blau, Vierarm glühend gelb, Diamondhead weiß-grün, Upgrade cyan, Ghostfreak bleich-weiß) und Pulsieren. In der Ich-Perspektive leuchten die eigenen Arme mit derselben Aura, dazu ein Schein am Bildrand in Aura-Farbe. XLR8 sprüht ab 75 % Funken. |
| **Cannonbolt** | Schwung | Kanonenkugel beschleunigt von 60 % auf 160 %, Rammschaden wächst mit; prallt bis zu 3× von Wänden ab (Druckwelle je Aufprall). Rollmodus schaltet nach je 2 s Vollgas einen Gang hoch (bis +2, Anzeige „Schwung-Gang“), Stillstand setzt zurück. Panzerkugel schickt Geschosse die ganze Dauer zum Schützen zurück und schleudert Nahkämpfer weg. Abpraller-Kette: jeder Treffer +25 %, der letzte mit Druckwelle. Kanonade: Sturzhöhe verstärkt Schaden und Radius (bis doppelt). |
| **Jetray** | Überladung + Flugmodell | Jeder Neuroschock-Treffer lädt das Ziel auf (sichtbare Funkenringe, 5 s). Bei 3 Ladungen: Entladung mit 150 % Schaden, Teilschaden und Lähmung im Umkreis 3,5. Düsenstoß durchbricht die Schallmauer (Knall-Ring) und reißt alles im Flugweg mit. Tiefflug feuert auf 2 Ziele je Salve. Im Flug: Kurvenlage beim Drehen, Vorlage mit dem Tempo, Flügelschlag beim Steigen. |

### Meisterschaft ★1–10 (je Alien)
Steigt durch Benutzen. Je Stufe +5 % Dauer, −3 % Abklingzeit; schaltet Fähigkeit 4 (★3), 5 (★5), 6 (★8) frei;
★10 = gemeistert (keine Fehlfunktionen).

### Gerätezustand: Hitze, Sperre, Fehlfunktionen
- **Hitze** steigt mit jeder Verwandlung und als Alien. Warnschwelle → Meldung, Gerät blinkt bernstein;
  100 % → **Überhitzung**: Zwangs-Rückverwandlung, 12 s Sperre, Abkühlen in Menschenform.
- **Fehlfunktionen** (nur bei hoher Hitze): falsches Alien oder Zeitdrift (−8 s). Abschaltbar per Spielregel;
  nie mit Master Control oder bei ★10.
- **Omnitrix OS:** alle Gerätemeldungen als Hologramm-Karte mit Vorrang (DNA AKTIV, WARNUNG, ÜBERHITZT, GESPERRT …).

### Code-Tastatur (im Rad: C)
| Code | Wirkung |
|---|---|
| 0001 | Diagnose (Hitze, Profil, Notfall, Master-Control-Fortschritt) |
| 4040 | Notkühlung: Hitze 0 %, dafür 30 s gesperrt |
| 7777 | Zufallsmodus (zufälliges freigeschaltetes Alien) |
| 1010 | Kalibrierung Prototyp ↔ Rekalibriert |
| 10000 | Master Control an/aus (nach Freischaltung) |
| 0000 | Selbstzerstörung — nur mit Spielregel `kingdomomnitrixSelfDestruct` (Standard **aus**) |

3 falsche Codes in 30 s → 10 s Sperre. Codes liegen nur auf dem Server (`data/<ns>/kingdomomnitrix/omnitrix_code/`).

### Master Control
Freispielen: **5 Aliens auf Meisterschaft ★5** → Hologramm „Master-Control-Protokoll empfangen“ → Code **10000**.
Wirkung: keine Hitze, 3× Dauer, ¼ Nachladen, Schnellwechsel, **alle sechs Fähigkeiten jedes Aliens frei**.

### Kalibrier-Werkbank
Block mit eigenem Bildschirm. 5 Kalibrierpunkte für drei Module zu je 3 Stufen:

| Modul | je Stufe | Preis |
|---|---|---|
| Kühlung | −12 % Hitze als Alien, +20 % Abkühlung | — |
| Kern | +15 % Verwandlungsdauer | +8 % Hitze je Verwandlung |
| Bandbreite | −12 % Nachladezeit | +25 % Fehlfunktions-Chance |

Kosten: Stufe 1 300 Bolts + 2 Raritanium · Stufe 2 700 Bolts + 4 Raritanium + 3 Mythril · Stufe 3 1500 Bolts +
6 Raritanium + 4 Mythril + 1 Orichalcum. **Farbmodule** (kostenlos): Klassisch Grün, Blau, Rot, Gelb, Violett, Weiß —
färben Gerät, Licht, Hologramm, Oberfläche, Effekte und Alien-Abzeichen; andere Spieler sehen die Farbe mit.
Warnfarben bleiben immer gleich.

---

## 4. Ratchet & Clank

| Ding | Funktion |
|---|---|
| **Bolts** | Währung, landen sofort auf dem **Bolt-Konto** (Status-Panel oben links). Monster 1–4, Bolt-Kisten 3–8 |
| **Waffen-Terminal** | Waffen kaufen, aufrüsten (nur hier), Munition — bezahlt vom Bolt-Konto |
| **OmniWrench 8000** | Nahkampf-Kombo; Rechtsklick wirft ihn als Bumerang (trifft, legt Hebel um, zerschlägt Kisten) |
| **Combuster** | Dauerfeuer (Plasma); Stufe 5 explosive Schüsse ohne Blockschaden |
| **Fusionsgranate** | Wurf, Explosion ohne Blockschaden |
| **Heli-Pack / Heli-Jet** | Rücken-Gadget, **J** wechselt. Heli: Doppelsprung, Gleiten. Jet: Schub nach vorn, Sinkflug. 3D-Modell auf dem Rücken: Rotor über dem Kopf dreht in der Luft, Jet-Düsen brennen |
| **Swingshot** | **Y**: Haken an festen Block (24 Blöcke), zieht dich hin, Hängen bis 10 s |

Gadgets liegen im **Gadget-Gürtel** (H). Combuster, OmniWrench, Swingshot, Granate und Heli-Pack sind 3D-Modelle.

---

## 5. Raumfahrt und Welten

### Aphelion und Weltall
- **Aphelion** (Raumschiff mit Bord-KI): Rechtsklick einsteigen, W/S Blickrichtung, A/D seitlich, Leertaste steigt,
  Schleichen steigt aus; zwei Plätze; Cockpit-Anzeige.
- Hoch genug fliegen → **Weltall**: geringe Schwerkraft, Sternenhimmel mit leuchtenden Nebeln, Sonne mit Lichthof,
  beringtem Gasriesen und der Oberwelt unter dir.
- **Asteroiden in fünf Arten:** Gestein (Erze), Eis (Packeis, Diamant), Kristall (Amethyst-Drusen, Raritanium),
  glutflüssig (Magma-Adern, glühender Kern, Gold), Metall (Eisen, Kupfer, viel Raritanium); große mit Einschlagkratern.
  Neue Arten nur in neu erzeugten Gegenden.
- Ohne Schiff ins Leere gefallen → Wiedereintritt über der Oberwelt (Sanfter Fall).
- **Weltraumrisse:** hineinfliegen = Reise; „Heimatwelt“ führt zurück.

### Traverse Town
Eigene Welt in ewiger Nacht: Stadtrand, Schattenwald, Kristallfelder, dunkles Meer; Erze Mythril, Raritanium, Orichalcum.
Die **Stadt** entsteht beim ersten Besuch:
- Platz mit Brunnen und **Uhrturm** (Gizmo-Laden: echte Uhren auf vier Seiten, Glocke)
- Häuser mit Satteldächern in KH-Farben, Fachwerk, Steinsockel, Blumenkästen, Türlaternen, Balkonen, rauchenden Schornsteinen
- **Waffenladen** (Clank, Terminal, rot-weiße Markise), **Schmiede** (Keyblade-Schmiede, orange-schwarze Markise)
- NPCs Yen Sid und Max auf dem Platz; in der Stadt entstehen keine Monster
- **Arena** im Stil des Kolosseums (Ringboden, Tribünen, Marmorsäulen mit Feuerschalen, Torbogen mit Bannern)

Bestehende Welten: `/hero world traverse_town rebuild` baut die Stadt neu (**überschreibt das Stadtgebiet**).

### Arena
Arena-Terminal → Pokal wählen → Countdown, Runden, Zeitlimit, Bossleiste. Sieg: Bolts, Helden-EP, Material, Bestzeit.
Eigenes Terminal baubar (Kampffläche Radius 12). **Platin-Pokal** (ab Heldenstufe 8) = Dr. Nefarious.

### Dungeon: Geheime Wasserstraße
Unter Traverse Town (Eingang: Kanalhäuschen nördlich der Stadt). Neun Abschnitte: Eingangshalle, Kanal (Bolt-Fässer),
**Raum der Lichter** (Hebel-Rätsel nach Wandbild), **Arena der Schatten** (3 Wellen), **Geheimkammer** (rissige Wand),
**Torwächter** (Miniboss), Schacht der Tiefe, Schatzkammer, **Schattenkoloss** (Boss mit zwei Beschwörungsphasen).
Sieg: 1000 Bolts + 2000 Helden-EP für alle Teilnehmer. Setzt sich nach 20 min (leer), beim Serverstart oder per
Befehl zurück.

---

## 6. Boss: Dr. Nefarious
Nach den Ratchet-&-Clank-Spielen: skelettartiger Roboter, grüne Glaskuppel mit Zahnrädern und drehender Satellitenschüssel,
Schädelgesicht mit rot leuchtenden Augen und silbernem Gebiss, violette Panzerung, Rücken-Tentakel.
Herbeirufen: **Nefarious-Kommunikator**, Platin-Pokal in der Arena oder Welt-Ereignis (ab Stufe 20).
- Angekündigte Angriffe: rote Linie = Laser (danach überhitzt), rote Ringe = Raketen, gelber Ring = Stampfer.
- Schwachstellen: Rücken ×2, Front ×0,5, überhitzt ×2,5.
- **Phase 2** (60 %): Elektrofelder in der Arena · **Wut** (25 %): Überladung — schnell 30 Schaden, sonst Explosion.
- **Schadenszustände:** ab 60 % Risse und Rauch, ab 25 % tiefe Risse, Funken, Qualm.
- Erster Sieg: Omega-Schlüssel, Orichalcum, Raritanium, 800 Bolts; danach wiederholbar. Mehr Leben bei mehreren Spielern.

---

## 7. Welt-Ereignisse
Seltene, angekündigte Ereignisse in der Oberwelt (im Mittel alle 40 min, nie auf Bauwerken/Dörfern). Ankündigung im
Chat mit Koordinaten, Bossleiste in 64 Blöcken, Belohnung für alle Teilnehmer.

| Ereignis | ab Stufe | Ablauf |
|---|---|---|
| Dunkler Riss | 1 | Riss „Schattenschwarm“ schließen |
| Raritanium-Meteor | 1 | Feuerball, gewaltiger Einschlag (Druckwelle bis 16 Schaden, Trümmer, Feuerring), Krater Radius 7 mit 9–14 Erzen |
| Alien-Absturz | 3 | Kapsel stürzt ab (Krater, Druckwelle), Kiste mit Material und DNA, 3–5 Herzlose als Wächter |
| Keyblade-Schrein | 5 | 60 s im Umkreis halten, alle 8 s Herzlose |
| Herzlosen-Invasion | 8 | fünf Wellen |
| Dr. Nefarious landet | 20 | Bosskampf |

Krater und Feuer nur mit `mobGriefing`. Auf „Friedlich“ nur der Meteor.

---

## 8. Heldenstufe und Fähigkeiten
- Helden-EP aus Quests, Herzlosen (Elite ×3), Bossen, Arena, Rissen, Ereignissen, Dungeon, **Entdeckungen** und
  Meisterschafts-Aufstiegen. Höchststufe **50**, jeder Aufstieg heilt.
- Pro Stufe automatisch: +1 Herz je 5 Stufen, +0,04 Angriff, +2 MP, +1 % Omnitrix-Dauer.
- **Heldenmenü (K):** 14 Fähigkeiten in fünf Ästen (Kampf, Keyblade, Omnitrix, Technik, Erkunden), kosten **AP**
  (2 + eine je zwei Stufen).

---

## 9. Menüs, HUD und Rückmeldung
- **Kommandomenü** wie in KH (Angriff · Magie · Items · Omnitrix), Pfeiltasten.
- **Menü-Reiter** im Inventar: Inventar · Held & Fähigkeiten · Aliens · Quests · Weltkarte · HUD anpassen.
- **Weltkarte:** Galaxie mit allen Rissen, unentdeckte „???“.
- **HUD anpassen:** jede Anzeige verschieben, skalieren (50–200 %), ausblenden.
- **Schadenszahlen** über dem Ziel: weiß normal, gold schwer, orange Feuer, violett Magie, grau Block, rot Todesstoß.
- **Treffer-Markierung** am Fadenkreuz: weiß, orange (schwer), rotes X (Todesstoß).
- Rote Vignette bei wenig Leben, Partikel für Verwandlung, Kampf, Magie und Technik; 60 eigene Klänge mit Untertiteln.

---

## 10. Mehrspieler, Quests, NPCs
- **Gruppe:** `/party invite|accept|leave|kick|list`, bis 4 Spieler. Gegner +50 % Leben und +15 % Schaden je weiterem
  Mitglied in 48 Blöcken. Geteilter Quest-Fortschritt und EP, kein Eigenbeschuss, Gruppen-HUD.
- **Quests:** Quest-Buch beim ersten Einloggen; Auftraggeber Yen Sid, Max Tennyson, Clank. Ziele Besiegen,
  Herstellen, Bringen; Story-Quests und wiederholbare Kopfgelder.
- **NPCs:** Yen Sid, Max, Clank mit Dialog (Text läuft ein), „!“ = neuer Auftrag, „?“ = abgeben.

---

## 11. Befehle (OP-Stufe 2)
`[spieler]` ist optional.

| Befehl | Wirkung |
|---|---|
| `/hero debug [spieler]` | alle Heldendaten |
| `/hero bolts add\|set <anzahl> [spieler]` | Bolt-Konto |
| `/hero level set <stufe>` · `/hero xp add <menge>` | Heldenstufe, Erfahrung |
| `/hero ability toggle <id>` · `/hero ability clear` | Heldenfähigkeiten |
| `/hero mastery <alien> <stufe>` | Alien-Meisterschaft 1–10 |
| `/hero alien unlock\|lock <id>` | Alien freischalten/sperren |
| `/hero dna <alien>` | DNA-Probe |
| `/hero transform <alien>` · `/hero revert` | verwandeln ohne Geräte-Regeln · zurück |
| `/hero omnitrix status\|use <alien>\|code <code>\|ability <1-6>\|heat <0..1>\|lock <s>\|master_control <bool>\|profile <id>` | Omnitrix-Gerät testen |
| `/hero spell <zauber> <stufe>` · `/hero mp` | Zauberstufe · MP auffüllen |
| `/hero flag set\|clear <flag>` | Story-Flags (z. B. `master_control_unlocked`) |
| `/hero quest start\|complete\|reset\|list` | Quests |
| `/hero npc spawn <npc>` | NPC aufstellen |
| `/hero rift [riss]` | Dunkelheitsriss öffnen |
| `/hero event start [id]\|stop\|status` | Welt-Ereignisse |
| `/hero dungeon tp\|status\|reset` | Geheime Wasserstraße |
| `/hero world traverse_town [spieler]` | nach Traverse Town |
| `/hero world traverse_town rebuild` | Stadt neu bauen (überschreibt Stadtgebiet) |
| `/hero reset [spieler]` | Heldendaten zurücksetzen |
| `/party …` | Gruppe (für alle Spieler) |

---

## 12. Spielregeln

| Spielregel | Standard | Wirkung |
|---|---|---|
| `kingdomomnitrixDarknessRifts` | true | Dunkelheitsrisse |
| `kingdomomnitrixWorldEvents` | true | Welt-Ereignisse |
| `kingdomomnitrixWorldEventMinutes` | 40 | mittlerer Abstand der Ereignisse (5–600) |
| `kingdomomnitrixOmnitrixMalfunctions` | true | Omnitrix-Fehlfunktionen |
| `kingdomomnitrixSelfDestruct` | **false** | Selbstzerstörungs-Code 0000 erlaubt |

---

## 13. Einstellungsdateien (Client, Ordner `config/`)

| Datei | Inhalt |
|---|---|
| `kingdomomnitrix-hud.json` | Lage, Größe, Sichtbarkeit jeder HUD-Anzeige (im Spiel per „HUD anpassen“) |
| `kingdomomnitrix-omnitrix.json` | Kamera-Stoß, FOV-Effekte, Bildschirmblitz, Lautstärke, `holoMessages` |
| `kingdomomnitrix-combat.json` | `damageNumbers`, `hitMarker`, `numberScale` (0,5–2) |

---

## 14. Datenpakete (erweiterbar)
Unter `data/<namespace>/kingdomomnitrix/`: `alien/`, `omnitrix_profile/`, `omnitrix_scan/` (Smart-Scan-Regeln),
`omnitrix_code/` (nur Server), `keyblade/`, `spell/`, `weapon/`, `hero_ability/`, `quest/`, `npc/`, `rift/`,
`world_event/`, `arena_challenge/`, `space_route/`.

## 15. Weiterführende Dokumente
- `docs/OMNITRIX_CORE.md` — Omnitrix-Architektur und alle Phasen mit Prüfprotokoll
- `docs/WORLD_EVENTS.md` — Welt-Ereignisse im Detail
- `docs/DUNGEONS.md` — Geheime Wasserstraße im Detail
- `DEVELOPMENT_STATUS.md` — Entwicklungsstand und Visual-Backlog
- `README.md` — Installation, Rezepte, Bauen
- `CREDITS.md` — Rechte und Danksagungen
