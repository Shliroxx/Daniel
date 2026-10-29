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
| **Leertaste in der Luft** (als Flug-Alien) | abheben; **W** fliegt in Blickrichtung, **Sprinten** = Schub, Leertaste/Schleichen steigen/sinken, Landen beendet den Flug |

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

Das Omnitrix muss nur im Inventar liegen. Am linken Arm sitzt das 3D-Gerät im Stil der Originalserie; als Item
(Inventar, Hand, Werkbank) zeigt es dasselbe 3D-Modell, mit der Leuchtfarbe seines Energiekerns.

### Story: Wie man das Omnitrix bekommt
Es gibt kein Werkbank-Rezept. Das Omnitrix entsteht in vier Schritten:

1. **Absturz der PROCTOR.** Einmal pro Welt stürzt nach `kingdomomnitrixPlumberCrashMinutes` Minuten Spielzeit
   (Standard 10) ein Klempner-Kreuzer 110–170 Blöcke neben einem Spieler ab: Warnung „⚠ NOTSIGNAL“, Feuerschweif am
   Himmel, Einschlag mit Explosion. Der Ort wird ohne Chunk-Generierung aus dem Geländerauschen gewählt (trocken, nicht
   zu steil); die Chunks unter dem Wrack lädt der Server während des Anflugs vor. Jeder Spieler bekommt einen **Klempner-Peilsender** (Kompass auf das Wrack), auch wer
   später beitritt.
2. **Das Wrack (212 Blöcke).** Bugteil schräg im Boden mit Krater: Cockpit (Logbuch des Kapitäns), Quartiere,
   Waffenkammer und eine runde, düstere **Containment-Kammer** (Grube aus Schwarzstein und Kohle mit Glutnestern,
   grüner Leuchtschacht, eingestürzter Steg, umgestürzte Säulen, Ketten) mit dem **kaputten Omnitrix (Hülle)**. Heckteil versetzt dahinter: Frachtraum, Maschinenraum (Notizen des
   Chefingenieurs) mit einer zweiten Kammer und der **Konsole**, Triebwerke und Finne. Schleifspur, Trümmer, Feuer und Rauchsäulen ringsum.
   Die beiden **Containment-Kapseln** (Galvan-Pod, 3D) öffnen sich per Rechtsklick: Beben, glühende Nähte, Knall,
   die Kuppel sprengt in vier Segmenten auf, das Teil steigt im grünen Strahl auf und fliegt dem Spieler an den Arm.
   Sie sind unzerstörbar und geben jedem Spieler sein Teil genau einmal; wer es schon hat, bekommt nur einen Hinweis,
   wer während einer fremden Öffnung klickt, wartet kurz. Ist der Spieler bei der Übergabe weg (z. B. Serverneustart
   mitten im Ablauf), bleibt sein Teil in der Kapsel. Truhen je Bereich
   (Quartiere, Waffenkammer, Labor, Fracht, Maschinenraum) mit eigener Beute, darunter Klempner-Ausrüstung.
3. **Corrodium aus dem End.** Giftgrünes Erz im Endstein (Höhe 10–90). Abbau ohne Behutsamkeit vergiftet kurz (der
   Klempner-Helm filtert den Staub). Roh-Corrodium → Ofen → Corrodium-Kristall.
4. **Omnitrix-Werkbank als Reaktor.** Werkbank (3 Kristalle, Werkbank, Eisen) plus **vier Omnitrix-Stabilisatoren** —
   je einer nördlich, östlich, südlich, westlich, 3 Blöcke höher, 3–7 Blöcke entfernt. Hülle + Konsole + Energiekern
   einlegen: über der Werkbank lädt sich ein Wabenfeld auf, die Stabilisatoren feuern Strahlen hinein, die echten
   Bauteile der Uhr setzen sich als Explosionsansicht zusammen, der Kern taucht ins Zifferblatt, Blitz und Schockwelle —
   fertig (12 s). Das Menü zeigt fehlende Stabilisatoren an.

**Farbe:** Basis-Energiekern (4 Kristalle + Enderauge) = Klassisch Grün; Kern + Farbstoff = farbiger Kern (Grün, Blau,
Rot, Gelb, Lila, Weiß, Orange, Pink, Türkis). Fertiges Omnitrix + farbiger Kern in der Werkbank = umgefärbt (5 s).

### Klempner-Ausrüstung
| Gegenstand | Wirkung | Rezept |
|---|---|---|
| Klempner-Panzerplatte | Baustoff, Reparatur | 2 Eisen + 1 Corrodium-Kristall → 2 |
| Klempner-Blaster | Rechtsklick: Energiestrahl, 7 Schaden, 32 Blöcke, 320 Schuss | Panzerplatten, Kristall, Redstone-Block |
| Klempner-Abzeichen | Rechtsklick: Feinde in 32 Blöcken leuchten 10 s; zeigt Entfernung/Richtung zum Wrack | 4 Gold + Kristall |
| Klempner-Rüstung | knapp unter Diamant; Helm: kein Corrodium-Gift; volles Set: giftimmun | Panzerplatten |

### Grundlagen
- **DNA-Scan:** Mit dem Omnitrix sind **Heatblast, Vierarm und XLR8** sofort gespeichert. Alle anderen Aliens muss man
  scannen: **C halten** und ein Lebewesen anvisieren (bis 16 Blöcke, freie Sicht, nicht verwandelt) — grüner Scanstrahl,
  Abtast-Ring am Ziel, Fortschrittsring am Fadenkreuz. Nach 2 s ist eine Probe genommen; jedes Einzelwesen zählt nur
  einmal, meist braucht ein Alien **3 Proben**. Die meisten Arten liefern stabile DNA **nur auf ihrer Heimatwelt** — wer
  z. B. auf der Erde ein Kamel scannt, bekommt „Humungousaur-DNA instabil – stabile Proben auf Veldin“. Die Alien-Seite
  im Heldenmenü (K) zeigt für jedes fehlende Alien Lebewesen, Welt und Proben. **DNA-Proben** aus Ereignissen, Rissen
  und Quests zählen als je eine Probe; die letzte muss man selbst auf der Herkunftswelt scannen. Hologramm „DNA ERFASST“.
- **Alien-Rad (G):** 3D-Hologramm-Rad, Mausrad/Ziehen dreht, Klick bestätigt, 1–9 Direktwahl, Mitte = zurück.
- **Schnellwahl-Kreis (X halten)**, **Favoriten-Sets** (F/Tab im Rad), **Schnellwechsel** zwischen Aliens.
- **Smart-Scan + Smart Choice (N):** das Gerät liest die Umgebung (Lava, Wasser, gepanzerte Gegner, Enge, Fernkampf,
  Redstone …) und empfiehlt ein Alien; im Kreis als goldene Silhouette.
- **Verwandlung** mit alien-eigener Sequenz (Blitz, Effekte, Klang), Rückverwandlung mit rotem Pulsieren.
- **Getrennte Lebenspunkte:** fällt das Alien, gibt es einen **DNA-Schock** — zurück in Menschenform, Menschen-Leben bleibt.
- **Notfall-Verwandlung:** in Lebensgefahr springt das Omnitrix selbst ein (danach 10 min nicht verfügbar).

### Die 12 Aliens

| Alien | 1 · 2 · 3 (R/V/B) | 4 SPECIAL ★3 · 5 UTILITY ★5 · 6 ULTIMATE ★8 (Schleichen + R/V/B) | DNA |
|---|---|---|---|
| **Heatblast** | Feuerstoß · Feuerexplosion · Flammenschub | Infernowelle · Flammenschild · Supernova | Start-Alien |
| **XLR8** | Sturmangriff · Ausweichen · Schlaghagel | Zyklonlauf · Zeitsprung · Lichtgeschwindigkeits-Hagel | Start-Alien |
| **Vierarm** | Bodenschlag · Werfen · Riesensprung | Donnerklatschen · Eisenhaut · Erdbeben | Start-Alien |
| **Diamondhead** | Kristallsalve · Kristallklinge · Kristallsprung | Stachelausbruch · Kristallpanzer · Kristallsturm | Petrosapien (Petropia) |
| **Grey Matter** | Analyse · Schwachstelle · Huschen | Technikfalle · Notreparatur · Superhirn | Galvan (Galvan Prime) |
| **Wildmutt** | Ansprung · Stachelsalve · Wildes Gebrüll | Wildes Zerfleischen · Witterung · Urwut | Vulpimancer (Vulpin) |
| **Stinkfly** | Schleimspucke · Stinkwolke · Flügelstoß | Schleimbombe · Aufwind · Giftsturm | Biene (Erde) |
| **Ripjaws** | Kieferbiss · Flutstoß · Strudel | Schwanzhieb · Hydroheilung · Flutwelle | Piscciss Volann (Piscciss) |
| **Upgrade** | Optikstrahl · Flüssige Form · Technik-Upgrade | Keulenfäuste · Systemübernahme · Plasmakanone | Mechamorph (Galvan B) |
| **Ghostfreak** | Tentakelhieb · Phasenverschiebung · Spuk | Besessenheit · Schattenschritt · Albtraum | Ghast (Nether) |
| **Cannonbolt** | Kanonenkugel · Panzerkugel · Kugelsprung | Abpraller · Rollmodus · Kanonade | Gürteltier (Veldin) |
| **Jetray** | Neuroschock · Schwanzblitz · Düsenstoß | Tiefflug · Windschatten · Neuroschock-Sturm | Delfin (Rilgar) |

Eigenschaften (Auswahl): Heatblast immun gegen Feuer/Lava · XLR8 schnell, kein Fallschaden · Vierarm groß und stark ·
Diamondhead immun gegen Geschosse · Stinkfly, Jetray, Big Chill, Ghostfreak, Heatblast, Chromastone, Atomix und Alien X fliegen (siehe Alien-Flug) · Ripjaws stark im Wasser, trocknet an Land aus · Ghostfreak halbe
Schwerkraft · Wildmutt sieht Monster durch Wände · Upgrade schneller Abbau · Cannonbolt schwer gepanzert, rollt sich für jede Fähigkeit zur Kugel (eigenes Kugel-Modell, dreht mit der Strecke; Rollmodus steigt Stufen hoch) · Jetray fliegt, atmet unter Wasser, Neuroschock lähmt; in der Luft und im Wasser Flughaltung (Flügel auf, aus der AE-Fluganimation). Uniform „ultimate“ nutzt bei Jetray den evo-Look (AE hat nur zwei). Modelle, Texturen und Uniformen
(classic/evo/ultimate, **U** im Rad) nach Alien Evolution (mit Erlaubnis, siehe `CREDITS.md`).

**Alien-Heimatwelten:** Für diese Aliens zählt nur die echte Art auf ihrer Heimatwelt — sie leben dort als
wilde Aliens (gleiches Modell wie die Omnitrix-Form, zufällige Uniform je Exemplar, Name der Art, Leuchtpartikel ihrer
Kraft) und erscheinen in Gruppen außerhalb der Sicht des Spielers (eigener Spawner, unabhängig von Licht und Boden).
Verhalten je Art: scheu (flieht), neutral (wehrt sich, die Gruppe hilft), feindlich, Rudel. Treffer tragen die Kraft der
Art (Feuer, Frost, Gift, Schall, Kristall, Schock). Reise über die Galaxiekarte, Reiter **Alien-Heimatwelten**.

| Welt | Art → Alien | Verhalten | Landschaft |
|---|---|---|---|
| Sonorosia | Sonorosianer → Echo Echo | scheu, Schall | violette Klangwüste, Resonanzgestein, Resonit-Kristalle, Klangrohr |
| Petropia | Petrosapien → Diamondhead, Crystalsapien → Chromastone | neutral, Kristall/Schock | Kristallwelt, Splittersand, Petrosapien-Kristalle |
| Galvan Prime | Galvan → Grey Matter | scheu | Sumpf mit Galvan-Moos, Schlick, Schiefer, Seen |
| Galvan B | Galvanischer Mechamorph → Upgrade | neutral, Schock | schwarzer Technik-Mond, Mechamorph-Platten mit grünen Leiterbahnen |
| Kylmyys | Necrofriggian → Big Chill | neutral, Frost, kein Fallschaden | Eiswelt, Ewiges Eis, Permafrost, Schneesturm |
| Vulpin | Vulpimancer → Wildmutt | Rudel | verseuchte Aschewüste, Giftschlamm, Giftdorn |
| Piscciss | Piscciss Volann → Ripjaws | feindlich, im Wasser | Ozeanwelt mit Riffinseln |

**Welle 2:**

| Welt | Art → Alien | Verhalten | Landschaft |
|---|---|---|---|
| Arburia | Arburian Pelarota → Cannonbolt | neutral, Rückstoß | ockerfarbene Canyons, Sandstein, Pelarota-Bernstein |
| Lepidopterra | Lepidopterraner → Stinkfly | feindlich, fliegt, Gift | schwüler Sumpf, Wabenstein, Stinkschleim |
| Anur Phaetos | Ectonurit → Ghostfreak | feindlich, fliegt, Entzug | finstere Totenwelt, Grabstaub, Seelensteine |
| Appoplexia | Appoplexianer → Rath | feindlich, Rückstoß | orange Steppe, Rotlehm, Kampffels |
| Aranhaschimmia | Arachnichimp → Spidermonkey | scheu | Dschungel mit Seidenballen, Moosschiefer |
| Terradino | Vaxasaurier → Humungousaur | neutral, Rückstoß | Urzeitebene, Lavafelder, Urbasalt, Bernstein |
| Aeropela | Aerophibianer → Jetray | neutral, fliegt, Schock | Ozeanwelt mit Korallenfels und Leuchtkorallen |
| Methanos | Methanosianer → Swampfire | neutral, Feuer | Methansumpf mit glühenden Gasschloten |
| Viscosia | Polymorph → Goop | neutral, Gift | giftgrüne Gelwelt, Polymorph-Schleim |
| Encephalonus IV | Cerebrocrustacean → Brainstorm | scheu, Schock | Küste mit Panzergestein und Synapsenkristallen |

Ohne Heimatwelt (Sonderfälle): Atomix Magmawürfel/Witherskelett (Nether) · Alien X Enderdrache (Ende, 1 Probe) ·
Way Big Verwüster (Torren IV, 2 Proben) — To'kustars leben im All, Celestialsapiens außerhalb der Wirklichkeit.

### Modi mit echten Bauteilen (wie Alien Evolution)

Manche Kräfte verwandeln den Körper: Das Bauteil sitzt im Modell und fährt mit der Original-Animation aus Alien
Evolution aus und wieder ein, andere Spieler sehen es mit. Im Modus gelten andere Werte.

| Alien | Kraft | Bauteil | Wirkung im Modus |
|---|---|---|---|
| Diamondhead | Kristallklinge (R/V/B-Taste, nochmal drücken = einziehen) | rechter Unterarm wird zum Kristallschwert | +5 Angriff, +1 Reichweite, mehr Rückstoß, jeder Schlag trifft auch Gegner daneben (60 %); beim Ausfahren ein weiter Hieb |
| Diamondhead | Kristallpanzer (12 s) | Kristallschild am linken Unterarm | immun gegen Geschosse und Explosionen, Stacheln verletzen Nahkämpfer (4 je ½ s), Widerstand und Stachelkranz; langsamer, −30 % Schlagkraft |

Diamondhead ist außerdem nach AE-Vorbild rückstoßfest und hat +10 Leben und +3 Angriff. Datenfeld:
`traits.modes` im Alien-JSON, Modell-Animationen `mode_<id>_on/_off` (Import: `Spec.keep`/`Spec.modes`).

### Alien-Flug (wie Alien Evolution und die Superhelden-Mods)

Acht Aliens fliegen richtig, nicht im Kreativflug: **Abheben** mit einem Sprung in der Luft (oder Doppelsprung),
**Fahrt in Blickrichtung**, auch steil hoch und im Sturzflug runter, A/D seitwärts, Leertaste/Schleichen steigen und
sinken. **Sprinten = Schub** bis zum Höchsttempo der Flugart, mit weitem Sichtfeld. Ohne Eingabe **schwebt** das Alien,
die Fahrt läuft aus. Landen beendet den Flug, kein Fallschaden im Flug.

Die Haltung folgt der Flugrichtung: langsam aufrecht, im Schub waagerecht wie ein Superheld (Kopf schaut nach vorn),
senkrecht hoch aufrecht, im Sturzflug kopfüber; die Beine laufen in der Luft nicht mehr. Ab Reisetempo zieht jede
Flugart einen eigenen Schweif, schnelle Flugarten durchbrechen beim Erreichen des Schubs die Schallmauer (Druckring,
Knall, Kamerastoß in der Nähe). Alles für andere Spieler sichtbar.

| Flugart | Aliens | Reise · Schub (Blöcke/s) | Eigenheit |
|---|---|---|---|
| Insektenflügel (`wings`) | Stinkfly | 15 · 34 | wendig, steht in der Luft, Sporen-Schweif |
| Düsenflug (`jet`) | Jetray | 19 · 60 | viel Schwung, Überschallknall, Funken-Schweif; Windschatten erhöht das Tempo |
| Frostschwingen (`ice`) | Big Chill | 16 · 42 | ruhig, Schneeschleier |
| Schweben (`ghost`) | Ghostfreak | 11 · 26 | gleitet lange aus, Seelen-Schweif |
| Feuerstrahl (`flame`) | Heatblast | 17 · 48 | Flammenschweif, Überschallknall |
| Energiefeld (`energy`) | Chromastone, Atomix | 16 · 44 | Leuchtspur, Überschallknall |
| Kosmisch (`cosmic`) | Alien X | 22 · 68 | fast ohne Trägheit, am schnellsten |

Datenfeld: `traits.flight_style` im Alien-JSON (setzt `flight` mit). Der Server gibt dem Schubflug dieselbe
Bewegungs-Toleranz wie dem Elytra-Flug.

### Eigene Alien-Systeme (über Alien Evolution hinaus)

AE liefert nur Modelle und Texturen; Fähigkeiten und Systeme sind eigene Entwicklungen.

| Alien | System | Wirkung |
|---|---|---|
| **Heatblast** | Kernhitze 0–100 % (orange Aura) | Jede Fähigkeit und jeder Treffer heizt auf, Lava/Feuer und der Nether heizen nach, Wasser und Regen kühlen schnell (Dampf, unter Wasser geschwächt), sonst 1 %/s Abkühlung. Ab 50 % **glühend**: +30 % Schaden, Feuerstoß mit 3 Kugeln, Hitze-Aura. Bei 100 % **überhitzt**: die nächste Fähigkeit entlädt den Kern als Blaufeuer (doppelter Schaden, größere Reichweite, Feuerstoß 5 explodierende Kugeln). Flammensurfen: gleitet 3 s auf einer Feuersäule, die alles darunter versengt. Inferno-Welle läuft als Flammenfront nach außen. Flammenschild verbrennt Geschosse und speichert Hitze. Supernova wächst mit der Hitze; bei vollem Kern bleibt ein Sonnenkern 4 s brennen. |
| **XLR8** | Tempo 0–100 % (blaue Leucht-Aura) | Rennen baut Tempo auf, Treffer legen nach, Stehen baut ab. Ab 50 % **verschwommen**: blaue Nachbilder, Nahkampf +Schaden nach Tempo. Bei 100 % **Schallmauer**: Überschallknall, beim Sprinten wird alles im Weg umgerannt, die nächste Fähigkeit verbraucht das Tempo für ihre stärkste Form. Schlaghagel: 4–12 Schläge nach Tempo (Schallmauer: Aufwärtshaken). Nachbild: Ausweichen, Gegner verlieren XLR8 und laufen zum Nachbild. Sturmangriff: Weite/Schaden nach Tempo. Zyklonlauf: XLR8 rennt im Kreis, der Wirbel zieht Gegner hinein und schleudert sie am Ende hoch. Zeitlupe: Gegner und Geschosse im Umkreis fast eingefroren. Lichtgeschwindigkeits-Hagel: blitzt Ziel für Ziel an (Anzahl nach Tempo, Schallmauer: jedes doppelt). |
| **Vierarm** | Wut 0–100 % (rote Aura) + Vier-Schlag-Kombo | Wut steigt mit ausgeteiltem und (4× stärker) eingestecktem Schaden, fällt nach 4 s ohne Kampf. Ab 50 % **Zorn**: Schläge mit mehr Wucht. Bei 100 % **Raserei**: nächste Fähigkeit in größter Form. Jeder 4. Nahkampftreffer innerhalb 1,5 s ist ein **Vierfach-Schlag** (Zusatzschaden, weiter Rückstoß, Bodenstoß). **Bodenschlag (Taste halten = aufladen, bis 1,5 s)** reißt einen echten Krater (wächst mit der Ladung, volle Ladung: Erdexplosionen am Rand). **Werfen ohne Ziel**: Vierarm reißt einen Brocken aus dem Boden und wirft ihn — Einschlag mit Krater und Flächenschaden. Titanensprung-Landung und Vierfach-Schlag hinterlassen Krater, Erdbeben reißt sternförmige Erdspalten auf, Donnerklatschen zerspringt Glas und Laub. Faustschlag zerschlägt Blöcke bis Steinhärte sofort. Bodenschlag/Donnerklatschen/Erdbeben/Landungen sind laufende Druckwellen: die echten Bodenblöcke springen ringförmig (Donnerklatschen: als Kegel) hoch und fallen zurück (nur Anzeige, die Welt bleibt unverändert), dazu Staubring, Druckwelle und Kamerawackeln. Werfen: das Ziel wird zum Geschoss und schlägt beim Aufprall ein; zu groß → Ringer-Wurf in den Boden. Titanensprung: Landung nach Fallhöhe. Donnerklatschen zerschlägt Geschosse im Kegel, betäubt. Eisenhaut: unverrückbar, Treffer geben doppelte Wut und 30 % Rückschaden. Erdbeben: 4 (Raserei 6) wachsende Wellen. |
| **Diamondhead** | Resonanz 0–100 % (grüne Aura) + Kristall-Konstrukte | Resonanz steigt, wenn Geschosse abprallen (Diamondhead ist immun), mit ausgeteiltem und eingestecktem Schaden; fällt nach 5 s ohne Kampf. Ab 50 % **facettiert**: jedes abprallende Geschoss fliegt als Kristallsplitter zum Schützen zurück, +30 % Schaden. Bei 100 % **Prisma**: nächste Fähigkeit in größter Form. Spitzenausbruch lässt als Welle echte grüne Kristallspitzen aus dem Boden wachsen (nur in Luft, zerspringen ohne Drops, beim Server-Stopp sofort weg; der Erschaffer läuft gefahrlos darüber). **Riesenkristalle**: Spitzenausbruch, Kristallpanzer und das Kielwasser beim Surfen lassen hohe, schräg nach außen kippende Geoden-Kristalle wachsen (wachsen in 4 Ticks, zerspringen am Ende in Splitter). **Kristallsurfen**: Diamondhead surft auf einer wachsenden Kristallbrücke (Blickrichtung, steigt und fällt mit dem Blick), seitlich schießen Stacheln hoch, Gegner werden weggeschleudert; Schleichen beendet. Kristallklinge: Halbkreis-Schwung (Prisma: Welle 12 Blöcke). Kristallpanzer: Nahkämpfer stoßen sich an Spitzen, Resonanz bleibt. Kristallsturm: Splitter kreisen 2,5 s um Diamondhead, dann fliegen sie nach außen. |
| **Grey Matter** | Analyse-Datenbank (keine Aura) | Jede Analyse eines neuen Gegners erhöht das Wissen über seine Art (0–5, dauerhaft gespeichert). Pro Stufe +8 % Schaden gegen diese Art — in **jeder** Form, auch als Mensch und als jedes andere Alien. Analysierte Ziele leuchten 10 s und nehmen von der ganzen Gruppe +20 %. Schwachstelle: der nächste Treffer irgendeines Spielers macht +150 % (ab Wissen 3 zusätzlich verlangsamt). Huschen: Gegner verlieren Grey Matter aus den Augen. Technikfalle: bis zu 3 Fallen, festhalten + markieren. Notreparatur: Werkzeug und Rüstung, heilt auch die Gruppe. Masterplan: alles im Umkreis wird analysiert, geschwächt, Grey Matter weicht 8 s lang jedem zweiten Angriff aus. |
| **Grey Matter: Galvan-Labor** | Taste **I** (nur als Grey Matter) | **Erfindungen** (Datenpaket `galvan_invention/*.json`: Ergebnis, Zutaten, Bolts, nötiges Wissen, nötige Hack-Stufe): Galvan-Energiezelle (lädt das Omnitrix sofort voll), Hi-Potion, Galvan-Scanner (Analyse in jeder Form, verschleißt), Kalibrier-Werkbank, Fusionsgranate, Swingshot, Heli-Pack (Hack 1), Orichalcum veredeln (Hack 2). **Omnitrix-Hack**: Zeit als Grey Matter ist Forschungszeit (dauerhaft). Nach 5 / 15 / 30 min wird je ein Hack verfügbar, gestartet im Labor, 10 s stillstehen (Bewegung/Treffer brechen ab): 1 Timer +20 % Verwandlungsdauer, 2 Laden −20 % Nachladezeit, 3 Zugang: Master Control frei (Code 10000) und +10 % Dauer. Admin: `/hero galvan research <min>`. |
| **Wildmutt** | Jagd: Beute + Blutrausch 0–5 (keine Aura) | Eine **Beute** (leuchtet): Witterung, Gebrüll, Zerfleischen oder der erste Treffer markieren sie. Treffer auf die Beute: Blutrausch +1 (je Stufe +10 % Schaden, fällt nach 4 s ohne Treffer), **Blutung** (Schaden je Sekunde). **Kettenjagd**: fällt die Beute, heilt Wildmutt, wird schneller und nimmt sofort die nächste Witterung auf (Duftspur). Stachelsalve: zielt auf die Beute, +1 Stachel je Rausch. Gebrüll: Gegner fliehen, nächster wird Beute, Rausch +2. Ansprung: springt die Beute an, drückt nieder. Zerfleischen: 3 + Rausch Bisse, verbraucht den Rausch für tiefe Wunden. Witterung: 48 Blöcke, stärkstes Ziel wird Beute, Duftspur jede Sekunde. Urraserei: Rausch voll und gesperrt, 25 % Lebensraub, Kills schlagen Gegner in die Flucht. |
| **Stinkfly** | Toxin-Schichten 0–5 am Gegner (keine Aura, grüne Blasen am Ziel) | Jeder Stinkfly-Treffer und jede Sekunde in seinen Giftwolken legt eine Schicht auf; ohne Nachschub fällt alle 3 s eine weg. Bei 5 **Zersetzung**: Verdorren, langsamer, jeder Treffer von irgendwem +30 %. **Ansteckung**: stirbt ein Ziel mit ≥ 3 Schichten, platzt es — Giftwolke, Nachbarn +2 Schichten. Gestankwolke: Giftwolke 6 s. Flügelstoß: hinterlässt eine Giftschleppe. Schleimbombe: Bogenwurf zum Zielpunkt, +2 Schichten, klebriges Schleimfeld. Aufwind: schleudert hoch und vergiftet. Giftsturm: Wolkenring; am Ende entladen sich alle Schichten im Umkreis (3 Schaden je Schicht). |
| **Ripjaws** | Gezeitenzonen (keine Aura, Wasserring am Boden) | Strudel, Flutstoß, Hydroheilung und Flutwelle hinterlassen **Gezeitenzonen**: Für Ripjaws zählen sie als Wasser (Wasserboni, kein Austrocknen). Gegner darin werden gebremst, zur Mitte gezogen und alle 2 s ertränkt. **Biss-Kombo**: jeder Kieferbiss innerhalb von 3 s +25 %, der dritte ist die **Todesrolle** (herumreißen, nach unten ziehen, festhalten). Flutstoß: nass dreimal so weit, rammt. Schwanzhieb: nass mit Wasserklinge, durchnässt (verlangsamt). Flutwelle: laufende Wasserwand, trifft jeden einmal. |
| **Upgrade** | Integration 0–100 (neongrüne Aura, bei Overclock cyan) | Steigt mit Treffern, jedem durchflossenen Gegner, Techno-Fusion und langsam neben Technik-Blöcken (Eisen, Kupfer, Redstone-Bauteile, Schienen); sinkt nach 6 s ohne Nachschub. Treffer legen **Naniten** (bis 5) in Gegner. Ab 50 % **vernetzt**: Optikstrahl durchschlägt alles in der Linie und springt auf 2 weitere Ziele, Morphwaffen-Treffer als Kettenblitz. Bei 100 % **Overclock** 8 s: +40 % Schaden, schneller, Anzeige läuft leer. Flüssigmetall: unverwundbar, jeder durchflossene Gegner wird verletzt und infiziert. Techno-Fusion: Werkzeug (Stromstoß bei jedem Treffer) oder ohne Werkzeug die Rüstung. Morphwaffen: Schockwelle je Treffer, entlädt gespeicherte Naniten. Systemübernahme: Mob kämpft einige Sekunden für Upgrade (+2 s je Nanit, Golems doppelt), Bosse/Spieler bekommen eine Systemstörung. Plasmakanone: 1 s Aufladen, durchschlagend, verbraucht die Integration für Schaden; im Overclock **Omega-Kanone** (breiter, Detonationen entlang der Bahn, ohne Blockschaden). |
| **Ghostfreak** | Spuk 0–100 (violette Aura, bei Seelenernte bleich-weiß) | Gegner sammeln **Angst** (0–3); bei voller Angst **Panik**: sie fliehen, greifen Ghostfreak nicht an und nehmen von ihm +30 % Schaden. Spuk steigt mit gesäter Angst, jeder Panik und Treffern; sinkt nach 6 s ohne Nachschub. Ab 50 % **unheimlich**: jeder Nahkampftreffer sät Angst, Tentakelhieb packt bis zu 3 Ziele. Bei 100 % **Seelenernte bereit**: Monster in 6 Blöcken bekommen von selbst Angst. **Hinterhalt**: nach Phasenform oder Schattenschritt macht der nächste Nahkampftreffer doppelten Schaden. Phasenform: unverwundbar, unsichtbar, Durchquerte erschauern. Spukschrei: Angst +2 im Umkreis (wächst mit Spuk). Besessenheit: Ghostfreak fährt in einen Mob, lässt ihn gegen andere Monster kämpfen, zehrt ihn aus und bricht danach heraus (Schaden, Angst ringsum; Schleichen beendet früher); Bosse/Spieler werden gelähmt und angehoben. Schattenschritt: hinter das anvisierte Ziel, sonst nach vorn. Albtraum: alles im Umkreis in Panik, Dunkelheit, Verdorren; verbraucht den Spuk für Reichweite und Schaden, bei vollem Spuk **Seelenernte** (Panik-Opfer verlieren 2 HP/s, jeder Tod heilt Ghostfreak). |
| **Big Chill** | Unterkühlung 0–100 am Gegner (keine Aura; Frostpartikel, Vanilla-Frostrand) | Eisatem (Frostkegel, löscht Feuer, friert Wasser zu schmelzendem Frosteis, am Boden wachsen Eisspitzen), Eissplitter, Nahkampf, Phasenflug und Umhang unterkühlen; ohne Nachschub taut es nach 3 s. Bei 100 **erstarrt** das Ziel 3 s (keine Bewegung, keine KI; Bosse nur kurz). **Zersplittern**: ein harter Treffer (6+) oder ein Eissplitter auf ein erstarrtes Ziel sprengt es — Bonusschaden, Splitter unterkühlen Nachbarn (Kettenreaktion). Erstarren lässt einen Ring aus Eisspitzen um das Ziel wachsen. Eisgefängnis: echte Necrofriggian-Eisblöcke um das Ziel (nur in Luft), zerspringen nach 5 s ohne Drop. Phasenflug hinterlässt eine Eisspur. Kälteumhang: unsichtbar, Geschosse gehen durch, die Nähe kühlt, beim Enthüllen Frostausbruch. Absoluter Nullpunkt: alles im Umkreis erstarrt, ein Feld aus Eisspitzen und Eisblöcken wächst Ring für Ring nach außen (mit hohen, schrägen Eiskristallen), nach 2 s zersplittern alle gleichzeitig. Fliegt; am Boden Umhang zu, in der Luft Flügel auf. |
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

### Kräfte 2.0: Pool, Loadout, Kombos, Passive
Jedes Alien hat mehr Kräfte als Tasten: **alle 24 Aliens** haben 8 Tasten-Fähigkeiten, 5 Kombos und 3 Passive (16 Kräfte) — bewusst weniger, dafür jede geprüft (`/hero selftest`). Die Beschreibung jeder Kraft steht im Loadout-Bildschirm als Tooltip.

| Art | Wie | Regeln |
|---|---|---|
| **Tasten-Fähigkeiten (Pool)** | bis zu 8 je Alien, 6 davon ausgerüstet auf R V B, Shift + R V B | Menü „Aliens“ → *Loadout & Kräfte*: Fähigkeit anklicken, dann Taste (oder umgekehrt); liegt sie schon auf einer anderen Taste, tauschen beide. Rechtsklick auf eine Taste = Standard. Höchstens **eine Ultimate** ausgerüstet. Gesperrte Fähigkeiten dürfen ausgerüstet werden und bleiben bis zur Meisterschaftsstufe grau. |
| **Kombos** | entstehen beim Schlag aus der Bewegung, kein Platz nötig | Angriffsbalken mindestens 85 %. Reihenfolge: *Finisher* (Ziel ≤ 30 % Leben) → *Luft* → *Sprint* → *Schleichen*; *Sturz* = hoch in der Luft (ab 1,5 Blöcken Fall, nicht im Flug) Schleichen drücken. Eigene Abklingzeit und Energie; ist eine nicht bereit, gilt die nächstpassende, sonst bleibt es ein normaler Schlag. HUD: Kombo-Leiste unter den Tasten (» Sprint, ↑ Luft, ▾ Schleichen, ⇓ Sturz, ✖ Finisher). |
| **Passive** | immer aktiv (ab Freischaltstufe) | wirken auf ausgeteilten/genommenen Schaden, Treffer, Siege oder jeden Tick. |

**Heatblast** — Pool: Feuerstoß, Feuerexplosion, Flammenschub, Infernowelle ★3, Flammenschild ★5, Supernova (Ultimate) ★8, Flammenwerfer ★2, Meteorhagel (Ultimate) ★10. Kombos: Glutansturm (Sprint), Feuerkomet ★2 (Luft), Glutgriff ★3 (Schleichen), Phoenix-Sturz ★4 (Sturz), Einäschern ★6 (Finisher). Passive: Pyronit-Kern, Glutkörper, Hitzeschleier ★4.

**XLR8** — Pool: Sturmangriff, Ausweichen, Schlaghagel, Zyklonlauf ★3, Zeitsprung ★5, Lichtgeschwindigkeits-Hagel (Ultimate) ★8, Sogspur ★4, Mach-Sprint (Ultimate) ★10. Kombos: Überschallstoß (Sprint), Sprungtritt ★2 (Luft), Fußfeger ★3 (Schleichen), Sturzflug ★4 (Sturz), Hundert Schläge ★6 (Finisher). Passive: Kineceleran-Tempo, Tempo-Lauf ★3, Reflexe ★5.

**Vierarm** — Pool: Bodenschlag, Werfen, Titanensprung, Donnerklatschen ★3, Eisenhaut ★5, Erdbeben (Ultimate) ★8, Vier-Faust-Salve ★2, Titanen-Meteor (Ultimate) ★10. Kombos: Rammstoß (Sprint), Hammerfaust ★2 (Luft), Schulterwurf ★3 (Schleichen), Meteorfaust ★4 (Sturz), Vierfach-Finale ★6 (Finisher). Passive: Tetramand-Wut, Vier-Faust-Kombo, Tetramand-Haut ★4.

**Diamondhead** — Pool: Kristallsalve, Kristallklinge, Kristallsurfen, Stachelausbruch ★3, Kristallpanzer ★5, Kristallsturm (Ultimate) ★8, Kristalllanze ★2, Kristallkathedrale (Ultimate) ★10. Kombos: Kristallramme (Sprint), Splitterfall ★2 (Luft), Kristallfessel ★3 (Schleichen), Diamantsturz ★4 (Sturz), Kristallisieren ★6 (Finisher). Passive: Petrosapien-Resonanz, Nachwachsen ★3, Diamanthaut ★5.

**Grey Matter** — Pool: Analyse, Schwachstelle, Huschen, Technikfalle ★3, Notreparatur ★5, Superhirn (Ultimate) ★8, Kopfsprung ★2, Mini-Geschütz ★5. Kombos: Wadenbiss (Sprint), Kopfnuss ★2 (Luft), Kabelfalle ★3 (Schleichen), Präzisionssturz ★4 (Sturz), Logisches Ende ★6 (Finisher). Passive: Beobachtung, Kleines Ziel, Kletterkünstler ★3.

**Wildmutt** — Pool: Ansprung, Stachelsalve, Wildes Gebrüll, Wildes Zerfleischen ★3, Witterung ★5, Urwut (Ultimate) ★8, Knochenbrecher-Biss ★2, Wilde Hatz (Ultimate) ★10. Kombos: Rammsprung (Sprint), Stachelsturz ★2 (Luft), Kehlenbiss ★3 (Schleichen), Raubtiersprung ★4 (Sturz), Zerfleischen ★6 (Finisher). Passive: Vulpimancer-Jagd, Witterung, Überlebensinstinkt ★4.

**Stinkfly** — Pool: Schleimspucke, Stinkwolke, Flügelstoß, Schleimbombe ★3, Aufwind ★5, Giftsturm (Ultimate) ★8, Gasdüse ★5, Seuchenwolke (Ultimate) ★10. Kombos: Flügelhieb (Sprint), Sturzstich ★2 (Luft), Schleimgriff ★3 (Schleichen), Giftbombe ★4 (Sturz), Zersetzen ★6 (Finisher). Passive: Lepidopterran-Gift, Facettenaugen, Gestank ★4.

**Ripjaws** — Pool: Kieferbiss, Flutstoß, Strudel, Schwanzhieb ★3, Hydroheilung ★5, Flutwelle (Ultimate) ★8, Fressrausch ★4, Mahlstrom (Ultimate) ★10. Kombos: Hairamme (Sprint), Kiefersturz ★2 (Luft), Unterwasserzug ★3 (Schleichen), Wasserbombe ★4 (Sturz), Todesbiss ★6 (Finisher). Passive: Gezeitenkraft, Tiefseejäger, Blutwitterung ★4.

**Upgrade** — Pool: Optikstrahl, Flüssige Form, Technik-Upgrade, Keulenfäuste ★3, Systemübernahme ★5, Plasmakanone (Ultimate) ★8, Dehngriff ★2, Omega-Übertaktung (Ultimate) ★10. Kombos: Flüssigramme (Sprint), Hammerarm ★2 (Luft), Naniten-Injektion ★3 (Schleichen), Flüssigsturz ★4 (Sturz), Systemabsturz ★6 (Finisher). Passive: Mechamorph-Integration, Selbstreparatur ★3, Flüssiger Körper ★5.

**Ghostfreak** — Pool: Tentakelhieb, Phasenverschiebung, Spuk, Besessenheit ★3, Schattenschritt ★5, Albtraum (Ultimate) ★8, Seelenraub ★2, Geisterlegion (Ultimate) ★10. Kombos: Geisterstoß (Sprint), Schattenfall ★2 (Luft), Kalter Griff ★3 (Schleichen), Spukeinschlag ★4 (Sturz), Seelenriss ★6 (Finisher). Passive: Ectonurit-Spuk, Körperlos, Nachtgestalt ★4.

**Cannonbolt** — Pool: Kanonenkugel, Panzerkugel, Kugelsprung, Abpraller ★3, Rollmodus ★5, Kanonade (Ultimate) ★8, Abrissbirne ★3, Kanonenhagel (Ultimate) ★10. Kombos: Kugelrammbock (Sprint), Kugelstampfer ★2 (Luft), Panzerkonter ★3 (Schleichen), Kanonenschlag ★4 (Sturz), Volltreffer ★6 (Finisher). Passive: Panzerschale, Rollschwung, Abprallen ★4.

**Jetray** — Pool: Neuroschock, Schwanzblitz, Düsenstoß, Tiefflug ★3, Windschatten ★5, Neuroschock-Sturm (Ultimate) ★8, Neuro-Salve ★2, Mach Zehn (Ultimate) ★10. Kombos: Flügelschnitt (Sprint), Himmelsgriff ★2 (Luft), Schwanzstachel ★3 (Schleichen), Sturzflug ★4 (Sturz), Totalentladung ★6 (Finisher). Passive: Neuro-Ladung, Aerodynamik, Aerophibie ★4.

**Big Chill** — Pool: Eisatem, Eissplitter, Phasenflug, Eisgefängnis ★3, Kälteumhang ★5, Absoluter Nullpunkt (Ultimate) ★8, Frostlanze ★2, Eiskomet (Ultimate) ★10. Kombos: Kältesturm (Sprint), Eiszapfen ★2 (Luft), Frostgriff ★3 (Schleichen), Gletschersturz ★4 (Sturz), Zersplitterschlag ★6 (Finisher). Passive: Unterkühlung, Frostkörper, Phantomflügel ★4.

**Humungousaur** — Pool: Megaschlag, Schwanzfeger, Stampede, Tektonikbeben ★3, Panzerblock ★5, Riesenwuchs (Ultimate) ★8, Bauchklatscher ★4, Aussterben (Ultimate) ★10. Kombos: Dino-Ansturm (Sprint), Hammerschlag ★2 (Luft), Bärengriff ★3 (Schleichen), Bebenlandung ★4 (Sturz), Riesenwurf ★6 (Finisher). Passive: Vaxasaurier-Wachstum, Dicke Haut, Erderschütterer ★4.

**Swampfire** — Pool: Methanflamme, Methanwolke, Methandüse, Wurzelfessel ★3, Nachwachsen ★5, Sumpfinferno (Ultimate) ★8, Feuerwand ★4, Dschungelzorn (Ultimate) ★10. Kombos: Flammensturm (Sprint), Schlammschlag ★2 (Luft), Wurzelgriff ★3 (Schleichen), Sumpfsturz ★4 (Sturz), Selbstentzündung ★6 (Finisher). Passive: Methanfäuste, Methankörper, Photosynthese ★4.

**Chromastone** — Pool: Prismenstrahl, Spektralblitz, Lichtsprung, Kristallgitter ★3, Spektralladung ★5, Vollspektrum (Ultimate) ★8, Lichtlanze ★2, Spektraler Kataklysmus (Ultimate) ★10. Kombos: Prismenansturm (Sprint), Sternenfall ★2 (Luft), Absorptionsgriff ★3 (Schleichen), Prismenmeteor ★4 (Sturz), Spektralbruch ★6 (Finisher). Passive: Solarzellen, Kristallresonanz, Brechung ★4.

**Echo Echo** — Pool: Schallschrei, Echo-Teilung, Schallstoß, Schallmauer ★3, Schallschild ★5, Echo-Chor (Ultimate) ★8, Rückkopplung ★4, Schall-Symphonie (Ultimate) ★10. Kombos: Echo-Rempler (Sprint), Tonfall ★2 (Luft), Volle Breitseite ★3 (Schleichen), Knalllandung ★4 (Sturz), Resonanzbruch ★6 (Finisher). Passive: Sonorosianische Resonanz, Echoortung, Schalldämpfung ★4.

**Atomix** — Pool: Atomstrahl, Kernpuls, Kernsprung, Eindämmungsfeld ★3, Halbwertszeit ★5, Hero Time (Ultimate) ★8, Protonensalve ★2, Fusionssonne (Ultimate) ★10. Kombos: Atomschlag (Sprint), Fallout ★2 (Luft), Isotopengriff ★3 (Schleichen), Kernsturz ★4 (Sturz), Kritische Masse ★6 (Finisher). Passive: Radioaktive Berührung, Reaktorkern, Strahlenschutz ★4.

**Rath** — Pool: Handgelenk-Kralle, Herausforderung, Sturmangriff, Suplex ★3, Appoplexian-Gebrüll ★5, Rath-Finale (Ultimate) ★8, Ellbogensturz ★5, Königliche Keilerei (Ultimate) ★10. Kombos: Speer-Tackle (Sprint), Fliegender Ellbogen ★2 (Luft), Schwitzkasten ★3 (Schleichen), Bauchplatscher ★4 (Sturz), Rath-Bombe ★6 (Finisher). Passive: Unbeugsam, Appoplexisches Temperament, Ehrenrunde ★4.

**Spidermonkey** — Pool: Netzschuss, Netzfächer, Netzschwung, Affenwirbel ★3, Netzfalle ★5, Netzkokon (Ultimate) ★8, Netzbola ★2, Spinnenhinterhalt (Ultimate) ★10. Kombos: Schwungtritt (Sprint), Spinnenfall ★2 (Luft), Vier-Arm-Wirbel ★3 (Schleichen), Netzsturz ★4 (Sturz), Kokonschlag ★6 (Finisher). Passive: Netzjäger, Kletteraffe, Spinnensinn ★4.

**Way Big** — Pool: Kosmischer Strahl, Titanenstampfer, Riesensprung, Klatschwelle ★3, To'kustar-Panzer ★5, Sternensturz (Ultimate) ★8, Bodenwischer ★3, Kosmische Kanone (Ultimate) ★10. Kombos: Titanentritt (Sprint), Himmelshammer ★2 (Luft), Riesenschnipser ★3 (Schleichen), Kolosssturz ★4 (Sturz), Kosmischer Gnadenstoß ★6 (Finisher). Passive: Kosmische Ladung, Titanenschritt, To'kustar-Haut ★4.

**Alien X** — Pool: Realitätsblitz, Schwerkraftbruch, Raumsprung, Zeitstillstand ★3, Schöpfung ★5, Urknall (Ultimate) ★8, Sternbild ★6, Genesis (Ultimate) ★10. Kombos: Kometenschlag (Sprint), Schwerkraftsturz ★2 (Luft), Stasisberührung ★3 (Schleichen), Sternensturz ★4 (Sturz), Auslöschen ★6 (Finisher). Passive: Himmlischer Einklang, Celestialsapien-Körper, Serenas Gnade ★4.

**Brainstorm** — Pool: Blitzbogen, Statikgitter, Elektro-Levitation, Kraftfeld ★3, Analyse ★5, Gedankensturm (Ultimate) ★8, Elektropeitsche ★2, Donnerkuppel (Ultimate) ★10. Kombos: Panzerramme (Sprint), Schädelknacker ★2 (Luft), Scherengriff ★3 (Schleichen), Donnersturz ★4 (Sturz), Neuronale Überlastung ★6 (Finisher). Passive: Überlegener Intellekt, Krabbenpanzer, Statische Entladung ★4.

**Goop** — Pool: Säurespucke, Schleimpeitsche, Pfützenform, Verschlingen ★3, Säurepfütze ★5, Säureflut (Ultimate) ★8, Säuresalve ★2, Säure-Tsunami (Ultimate) ★10. Kombos: Schleimrutsche (Sprint), Schleimkappe ★2 (Luft), Säuregriff ★3 (Schleichen), Platsch ★4 (Sturz), Totale Zersetzung ★6 (Finisher). Passive: Ätzende Berührung, Schleimkörper, Antigrav-Projektor ★4.

### Meisterschaft ★1–10 (je Alien)
Steigt durch Benutzen. Je Stufe +5 % Dauer, −3 % Abklingzeit; schaltet Kräfte, Kombos und Passive auf ihrer Stufe frei (★ in der Liste oben);
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
6 Raritanium + 4 Mythril + 1 Orichalcum. Die **Farbanzeige** zeigt die aktive Gerätefarbe; geändert wird sie über
einen farbigen Energiekern in der Omnitrix-Werkbank. Die Farbe färbt Gerät, Licht, Hologramm, Oberfläche, Effekte und
Alien-Abzeichen; andere Spieler sehen sie mit. Warnfarben bleiben immer gleich.

### Omnitrix-Upgrades
Rechts in der Kalibrier-Werkbank: **Omnitrix-Stufe 1–5**. Jede Stufe über 1 bringt einen Kalibrierpunkt mehr
(bis 9), −4 % Nachladezeit, +5 % Verwandlungsdauer und einen **Boost-Steckplatz** mehr (Stufe = Zahl der Plätze).
Kosten: Stufe 2 1500 Bolts + 4 Raritanium + 2 Mythril · Stufe 3 3000 + 6 + 4 + 1 Orichalcum · Stufe 4 6000 + 8 + 6 + 2 ·
Stufe 5 12000 + 10 + 8 + 4.

**Boost-Module** (Rechtsklick setzt ein, × in der Werkbank baut aus und gibt das Modul zurück; gleiche Module stapeln):

| Modul | Wirkung je Modul | Rezept (Mitte; oben/unten Mythril, links/rechts Raritanium) |
|---|---|---|
| Kraft | verwandelt +15 % Angriffsschaden | Lohenstaub |
| Panzer | verwandelt +3 Rüstung | Eisenbarren |
| Tempo | verwandelt +8 % Laufgeschwindigkeit | Zucker |
| Ausdauer | +20 % Verwandlungsdauer | Uhr |
| Kühlung | −15 % Hitze | Packeis |
| Laden | −15 % Nachladezeit | Redstone-Block |

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
- **Aphelion** (Raumschiff mit Bord-KI; rundes 3D-Modell nach dem Gadgetron-Schiff mit Glaskuppel, Stachelflügeln, Doppelkanonen und leuchtenden Triebwerken): Rechtsklick einsteigen, W/S Blickrichtung, A/D seitlich, Leertaste steigt,
  Schleichen steigt aus; zwei Plätze; Cockpit-Anzeige.
- Hoch genug fliegen → **Weltall**: geringe Schwerkraft, Sternenhimmel mit leuchtenden Nebeln, Sonne mit Lichthof,
  beringtem Gasriesen und der Oberwelt unter dir.
- **Asteroiden in fünf Arten:** Gestein (Erze), Eis (Packeis, Diamant), Kristall (Amethyst-Drusen, Raritanium),
  glutflüssig (Magma-Adern, glühender Kern, Gold), Metall (Eisen, Kupfer, viel Raritanium); große mit Einschlagkratern.
  Neue Arten nur in neu erzeugten Gegenden.
- Ohne Schiff ins Leere gefallen → Wiedereintritt über der Oberwelt (Sanfter Fall).
- **Weltraumrisse:** hineinfliegen = Reise; „Heimatwelt“ führt zurück.
- **Galaxiekarte (O):** Sternsysteme mit ihren Planeten (Heimat, Unbekannte Welten, Solana, Polaris, Tiefraum),
  „Du bist hier“, Kurslinie, Infotafel. Als Pilot **Kurs setzen** → Warp (6 s, mit Warp-Antrieb kürzer), das Schiff
  erscheint über dem **Landeplatz** der Zielwelt (runde Plattform mit gelbem Ring und Lichtern, beim ersten Anflug
  gebaut). Freischaltung je Planet: Heldenstufe, vorher besuchter Planet, Warp-Stufe für fernere Systeme.
  Planeten sind Daten (`data/<ns>/kingdomomnitrix/planet/*.json`).
- **Schiffsausbau** (Galaxiekarte, je 3 Stufen für 1500/4000/9000 Bolts + 2/5/10 Raritanium): **Triebwerk** (+15 %
  Tempo je Stufe), **Warp-Antrieb** (Reichweite, kürzerer Warp), **Bordkanone** (Angriffstaste im Cockpit: zwei
  Laser, Stufe 3 explodiert ohne Blockschaden). Ausbau und besuchte Planeten gelten für jedes Schiff, das du fliegst.

### Ratchet-&-Clank-Welten
Sechs eigene Welten, erreichbar über die Galaxiekarte. Beim ersten Anflug entsteht um den Landeplatz eine Siedlung
(Gelände eingeebnet, Fundamente am Hang, je ein **Waffen-Terminal** als Laden):

| Welt | Gelände | Siedlung |
|---|---|---|
| **Veldin** (Solana) | Wüste, Tafelberge, Felsnadeln | Ratchets Garage mit Werkstatt und Satellitenschüssel, Windrad, Blarg-Schrotthaufen |
| **Kerwan** (Solana, nach Veldin) | Ebenen und Wiesen | Metropole: Glastürme, Straßenkreuz mit Laternen, Gadgetron-Laden |
| **Novalis** (Solana, nach Veldin, Stufe 3) | Blumenwiesen, Wälder | Bauernhäuser, Felder, Windmühle, abgestürztes Blarg-Schiff |
| **Rilgar** (Solana, nach Kerwan, Stufe 6) | Meer, Strand, Mangroven | Wasserstadt Blackwater: Stege auf Pfählen, Pfahlhäuser, Startbogen der Hoverboard-Strecke |
| **Torren IV** (Polaris, Stufe 12, Warp 2) | Savanne, rote Wüste | Raumhafen: drei weitere Landeplätze, Kontrollturm, Hangars, Tanks, Frachtkisten |
| **Nefarious-Station** (Tiefraum, Stufe 20, Warp 3, nach Torren IV) | Leere, ewige Nacht | Schwebende Plattformen mit Brücken, dunkler Zentralturm mit grüner Kuppel |

Die Welten bestehen **nur aus eigenen Blöcken** (einzige Ausnahme: das Meer auf Rilgar ist Wasser). Jeder Planet hat
eigenes Gestein, eigene Böden und Pflanzen, zwei eigene Biome mit eigenem Himmel und Nebel, und ganz unten den
unzerstörbaren **Planetenkern** statt Grundgestein. Die Landwelten haben kein Meer (tiefe Becken statt Ozean); der
Landeplatz sucht sich die nächste ebene Hochfläche. Gegner: Herzlose (Schatten, Soldaten, Luftsoldaten, Dunkelkugeln,
Großkörper) je nach Planet; eigene R&C-Gegner folgen.

**Eigene Blöcke** (alle Texturen 128×128, prozedural mit Licht und Schatten erzeugt, `tools/generate_planet_content.py`):

| Planet | Natur | Erz |
|---|---|---|
| Veldin | Dünensand, Schichtsandstein, Veldin-Fels, Dürrstrauch | Sonnenkupfer |
| Kerwan | Kerwan-Rasen, Kerwan-Erde, Schiefer, Spiralbaum (Stamm, Bretter, Laub), Lichtschilf (leuchtet) | Azurit |
| Novalis | Novalis-Moos, Lehm, Kalkstein, Glockenbaum (Stamm, Bretter, Laub), Sternblüte | Viridium |
| Rilgar | Riffsand, Korallenfels, Mangrove (Stamm, Pfahlholz, Laub), Riffgras | Aquarin |
| Torren IV | Rotstaub, Brandkruste, Säulenbasalt (glühende Fugen), Glutdorn (leuchtet) | Pyronit |

Technik-Blöcke (Siedlungen, Landeplätze, Station): Rumpfplatte (hell, dunkel, Gadgetron-Orange), Warnplatte,
Panzerglas, Lichtpaneel, Gitterrost, Stationsplatte, Stationsleiste (leuchtet), Landefeld, Landefeld-Markierung.

**Materialien und Ausrüstung** (eigener Kreativ-Reiter „Kingdom Omnitrix – Welten“):

| Material | Stufe | Werkzeuge (Schwert, Spitzhacke, Axt, Schaufel) | Rüstung (Helm/Brust/Beine/Stiefel, Härte) |
|---|---|---|---|
| Sonnenkupfer | Eisen | 320 Haltbarkeit, Tempo 6,5 | 2/6/5/2, 0,5 |
| Azurit | Eisen | 540, Tempo 7 | 3/6/5/2, 1 |
| Viridium | Diamant | 1050, Tempo 8 | 3/7/6/3, 2 |
| Aquarin | Diamant | 1500, Tempo 8,5 | 3/8/6/3, 2,5 |
| Pyronit | Netherit | 2100, Tempo 9,5 | 4/9/7/4, 3,5 |

Ablauf: Erz abbauen → Roh-Material (Glück wirkt) → im Ofen/Schmelzofen zum Barren → Werkzeuge, Schwert, Rüstung und
Speicherblock wie gewohnt an der Werkbank. Die Erze verlangen aufsteigend Stein-, Eisen- und Diamant-Werkzeug, so führt
der Weg von Veldin bis Torren IV. Weitere Rezepte: Bretter aus den Stämmen; Panzerglas aus Planetensand im Ofen;
Rumpfplatten aus Planetenstein + Sonnenkupfer, daraus dunkle und orange Platten, Warnplatten und Landefeld;
Lichtpaneel aus Panzerglas + Azurit; Gitterrost aus Sonnenkupfer; Stationsplatten aus dunklen Platten + Aquarin.
Planetenstein zählt als Bruchstein (Ofen, Steinwerkzeuge), die Bretter als Holz (Werkbank, Stöcke).

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
| `/hero power <nummer> [spieler]` | Kraft Nummer *n* des aktiven Aliens auslösen (Tasten und Kombos, Ziel = Gegner im Blick) — zum Testen |
| `/hero bolts add\|set <anzahl> [spieler]` | Bolt-Konto |
| `/hero level set <stufe>` · `/hero xp add <menge>` | Heldenstufe, Erfahrung |
| `/hero ability toggle <id>` · `/hero ability clear` | Heldenfähigkeiten |
| `/hero selftest worlds` | Selbsttest der Welten und Wesen: reist in jede Mod-Dimension (Boden, Lebewesen, Log-Fehler) und beschwört jedes Mod-Wesen einmal; Bericht `kingdomomnitrix-selftest-welten.md` |
| `/hero selftest [alien]` | Selbsttest: löst jede Tasten-Kraft und Kombo jedes Aliens an drei Testzielen aus, misst Schaden, Treffer, Effekte, Bewegung, Wesen und Blöcke; Bericht `kingdomomnitrix-selftest.md` im Serverordner (Ergebnis je Kraft: OK, SITUATIV, WIRKUNGSLOS, ABGELEHNT, FEHLER, ABSTURZ) |
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
| `/hero plumber crash\|here [spieler]` | Klempner-Kreuzer stürzt ab (Ortssuche über mehrere Ticks, nennt bei Misserfolg die Gründe · 40 Blöcke vor dem Spieler), nur Oberwelt |
| `/hero plumber tp\|status\|reset` | zum Wrack · Zustand · Ereignis zurücksetzen (Wrack bleibt) |
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
| `kingdomomnitrixPlumberCrash` | true | Story-Absturz der PROCTOR (einmal pro Welt) |
| `kingdomomnitrixPlumberCrashMinutes` | 10 | Spielzeit bis zum Absturz (0–1440) |

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

## Zerstörung durch Alien-Kräfte

Starke Aliens zerstören Blöcke dauerhaft (wie eine Explosion; ein Teil der Blöcke lässt etwas fallen, Trümmer fliegen als echte Blockstücke). Jedes Alien hat eine **Stärke** als Blockhärte: Faustschläge zerschlagen bis 60 %, Fähigkeiten bis 100 % (aufgeladen mehr). Richtwerte: Vierarm 6 (Stein, Erze, Eisenblock), Humungousaur 8, Rath 5, Cannonbolt 5,5, Diamondhead/Chromastone 4, Heatblast 3, Way Big/Atomix/Alien X 55 (auch Obsidian).

Nie zerstört: unzerstörbare Blöcke, Blöcke mit Inventar/Block-Entity (Truhen, Öfen …), Tag `#kingdomomnitrix:alien_proof` (Grundgestein, Portale, Spawner …). Schutz-Mods (Claims) und Spawnschutz werden wie beim Abbauen gefragt. Große Krater wachsen von innen nach außen und werden auf mehrere Ticks verteilt.

Abschalten: Gamerule `kingdomomnitrixAlienGriefing false`, Vanilla-Gamerule `mobGriefing false` oder Datei `config/kingdomomnitrix-power.json`:

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `blockDamage` | `true` | Hauptschalter |
| `dropBlocks` / `dropChance` | `true` / `0.35` | Drops und ihr Anteil |
| `respectMobGriefing` | `true` | Vanilla-Gamerule beachten |
| `strengthScale` | `1.0` | Faktor auf die Stärke aller Aliens |
| `blocksPerTick` | `500` | Obergrenze je Tick |
| `debrisChance` / `maxDebris` | `0.18` / `260` | fliegende Trümmer |

**Zerstörung je Alien** (Stand des Umbaus): Vierarm (siehe oben) · Humungousaur: Megaschlag aufladbar, schlägt einen Tunnel durch Fels; Schwanzfeger räumt Wände auf Hüfthöhe weg; Stampede rammt durch Wände; Tektonikbeben mit Krater und Erdspalten; Riesenwuchs-Schritte drücken weichen Boden ein · Way Big: Kosmischer Strahl aufladbar, brennt eine Schneise ins Gelände und explodiert am Ende; Stampfer, Riesensprung und Sternensturz mit Kratern; Klatschwelle knickt Bäume · Rath: Sturmangriff durch Wände, Suplex und Finale schlagen Krater, Gebrüll zerspringt Glas · Cannonbolt: bricht mit Schwung durch Wände statt abzuprallen, Sprung/Kanonade mit Einschlagkrater · XLR8: ab „verschwommen“ Tempo-Streifen, beim Sprinten über Wasser laufen und Wände hinauf; Überschallknall zerspringt Glas/Laub ringsum; mit Schallmauer rast XLR8 (Sprint und Ansturm) durch Wände; Zyklonlauf als Wirbelsturm mit Windringen, reißt losen Boden mit, Brocken kreisen nach oben · Upgrade: Plasmakanone brennt eine Schneise und reißt einen Krater (Omega: groß) · Atomix: Atombolzen schlägt am Zielpunkt ein, Kernpuls und Hero Time mit großem Krater und Brand · Chromastone: Prismastrahl schneidet durch Fels, Vollspektrum-Krater · Goop: Säure zersetzt den Boden (Spucke am Zielpunkt, Säurepfütze, Säureflut) · Spidermonkey: Netze sind echte Spinnweben um das Ziel (verschwinden wieder) · Echo Echo: Schrei schneidet eine Schneise durch Glas/Laub, Schallwand und Chor lassen ringsum Zerbrechliches zerspringen · Brainstorm: Blitzbogen schlägt am Zielpunkt ein · Swampfire: Methanflamme setzt den Zielpunkt in Brand, Wurzelfalle aus echten Mangrovenwurzeln, Sumpf-Inferno mit Krater · Stinkfly: Schleimbombe fesselt mit klebrigen Netzen · Jetray: Neuroschock schlägt am Zielpunkt ein · Wildmutt: Zerfleischen reißt weichen Boden/Holz weg, Gebrüll zerspringt Glas · Alien X: Realitätsblitz und Urknall mit Kratern · Ghostfreak: in der Phasenform durch Wände gleiten (bis 4 Blöcke dick), schleichend durch den Boden sinken · Ripjaws: ohne Ziel beißen die Kiefer durch Blöcke, auch Metall; Gezeitenansturm rammt durch weiche Blöcke · Heatblast: Feuerstoß und Supernova reißen Krater und setzen die Umgebung in Brand, Feuerbälle schlagen kleine Krater.

**Aufladen**: Fähigkeiten mit „Taste halten“ laden auf, solange die Taste gedrückt ist (Ton steigt, Blitz bei voller Ladung), und lösen beim Loslassen aus; volle Ladung löst nach 2 s von selbst aus. Antippen = schwache Form.
