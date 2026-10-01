# Design-Vorgabe: Progression & RPG-Design

made by SANTIQ · festgehalten am 2026-10-01

> **Arbeitsauftrag:** Wenn alle Phasen abgeschlossen sind, wird die gesamte Mod noch einmal
> Punkt für Punkt gegen dieses Dokument geprüft. Bis dahin gilt es als verbindliche Leitlinie
> für Phase 15 (Progression), Phase 10 (Quests), Phase 12 (Welten) und Phase 13 (Bosse).

---

## Grundsatz

Die Mod darf Skilltrees und umfangreiche Progression besitzen, ist aber **kein klassisches lineares RPG**.
Ziel: eine Minecraft-Content-Mod mit **optionaler** RPG-/Adventure-Tiefe.

Der Spieler kann:

- einfach normales Minecraft Survival spielen
- einzelne Features der Mod nutzen
- nur bestimmte Aliens verwenden
- nur Waffen und Gadgets verwenden
- die Dimensionen erkunden
- Dungeons suchen
- Bosse bekämpfen
- Skilltrees ausbauen
- seltene Items sammeln
- oder die komplette Progression der Mod verfolgen

## Skilltrees

Ausdrücklich erwünscht, aber **nie nötig**, um Minecraft normal zu spielen.

| Baum | Beispiele |
|---|---|
| **Combat** | Combo Plus, Heavy Attack, Dodge, Perfect Guard, Counter, Air Combo, Finisher |
| **Omnitrix** | längere Transformation, kürzere Cooldowns, neue Alien-Fähigkeiten, bessere Energieeffizienz, neue Transformationen, Master Control |
| **Keyblade** | neue Combos, Magic Boost, Critical, Air Combat, Finisher, Keyblade Techniques |
| **Technology** | Waffen-Upgrades, Gadget-Upgrades, bessere Energieeffizienz, neue Munition, neue Maschinen |
| **Exploration** | Double Jump, Glide, bessere Dash-Fähigkeiten, neue Gadget-Funktionen, verborgene Bereiche entdecken |

## Progressionskette

Exploration → Ressourcen → Crafting → neue Waffen / Gadgets / Aliens → Skilltree-Unlocks → Dungeons → Bosse → neue Dimensionen → seltene Materialien → Endgame-Items

## Optionale Hauptprogression

| Abschnitt | Inhalte |
|---|---|
| **Early Game** | Omnitrix entdecken → erste Aliens → erste Heartless → erste Keyblade → erste Gadgets |
| **Mid Game** | weitere Aliens → bessere Keyblades → Waffen-Upgrades → erste Dimensionen → Dungeons → stärkere Heartless → erste große Bosse |
| **Late Game** | seltene Aliens → Master-Control-ähnliche Omnitrix-Funktionen → legendäre Keyblades → High-End-Waffen → schwierige Dungeons → starke Bosse → weitere Dimensionen |
| **Endgame** | Ultimate Weapons → seltene Keyblades → Ultimate-/Master-Control-Inhalte → Endgame-Dungeons → Boss Challenges → Geheimbosse → seltene Collectibles |

## Kein New Game+

Es gibt **ausdrücklich kein New Game+**. Nach dem Endgame bleibt die Welt normal bestehen. Der Spieler kann weiter bauen, erkunden, Items sammeln, Bosse erneut bekämpfen, andere Mods/Modpack-Inhalte spielen, geheime Items suchen und Skilltrees optimieren.
Die Mod ist nach ihrem „Ende“ nicht beendet.

## Theoretisch durchspielbar

Es gibt eine klare Linie, sodass ein Spieler sagen kann: **„Ich habe die Mod durchgespielt.“**

- **Final Boss**: großer Endgame-Boss
- Nach dem Sieg: Endgame-Dimension freigeschaltet, besondere Items, geheime Inhalte, Boss-Rematches, zusätzliche Challenges
- Der Spieler wird **nicht** aus der Welt entfernt und **nicht** in ein New Game+ gezwungen

## Minecraft bleibt Minecraft

Die Mod setzt **nie** voraus, dass der Spieler ausschließlich ihre Progression verfolgt.

- Völlig in Ordnung: 100 Stunden Minecraft mit einem Alien, einer Keyblade, ein paar Waffen, einem Dungeon — und nie dem finalen Boss.
- Ebenso vorgesehen: alle Aliens, Keyblades, Waffen, Skilltrees, Dimensionen, Bosse, Dungeons und Collectibles.

## Drei Spielweisen — keine blockiert die andere

1. **Casual Minecraft** — neue Items, Mobs, Waffen, Strukturen und Dimensionen
2. **Adventure** — gezieltes Erkunden von Inhalten, Dungeons, Dimensionen
3. **Completionist** — komplette Mod-Progression abschließen

## Leitsatz

Skilltrees und Progression machen die Mod **tiefer**, ersetzen Minecraft aber nicht.
Gefühl: **„Ich spiele Minecraft mit einer riesigen neuen Mod“** — nicht **„Ich spiele ein komplett anderes RPG“**.

---

## Folgerungen für die bisherige Umsetzung (zur Abschlussprüfung)

| Bestehendes System | Prüfpunkt |
|---|---|
| `HeroData` (Stufe, EP) | Stufe darf nie Vanilla-Inhalte sperren; nur Mod-Inhalte vertiefen |
| Kampfsystem (Combo, Dodge, Guard, Lock-On) | heute für alle verfügbar → in Phase 15 als Skilltree-Knoten (Combat) mit sinnvoller Grundausstattung |
| Omnitrix-Werte (Dauer, Nachladen, Energie) | als Omnitrix-Skilltree-Boni modifizierbar machen |
| DNA-Freischaltung | passt zur Exploration-first-Kette (Gegner/Orte → DNA) |
| Linksklick-Combo ersetzt Vanilla-Schlag nur mit Combo-Waffe | erfüllt „Minecraft bleibt Minecraft“ |
