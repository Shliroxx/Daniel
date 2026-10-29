# Referenz-Analyse 2 — was die großen Mods besser machen

made by SANTIQ · Stand: 2026-10-29 · Kingdom Omnitrix 1.0.0

Diese Analyse ersetzt nicht `VERGLEICH_REFERENZMODS.md` (Stand 0.5.0, Phase 12). Sie folgt auf das Feedback von
SANTIQ zu 1.0.0: „nichts funktioniert richtig, die Omnitrix, die Aliens, die Galaxien könnten besser sein“.

## Grundlage und Grenzen

- **Quellen:** veröffentlichte Beschreibungen, Wikis und Changelogs (Websuche, Liste unten).
- **Nicht gespielt:** Keine der Referenz-Mods wurde installiert oder gespielt, weil CurseForge und Modrinth in der
  Cloud-Umgebung gesperrt sind.
- **Was das für die Aussagen bedeutet:** Aussagen über Spielgefühl und Grafik sind abgeleitet, nicht gemessen.
- **Was am meisten hilft:** Konkrete Fehlerberichte aus dem eigenen Spiel (`latest.log`, „X passiert, erwartet Y“).
  Sie zählen mehr als jeder Vergleich.

## 1. Die Referenzen in einem Satz

| Mod / Pack | Kern, den wir übernehmen sollten |
|---|---|
| **Alien Evolution** (1.20.1, Palladium) | Wenige Aliens (11), jedes mit **eigenem Moveset**. Aliens leveln per XP und kaufen Fähigkeiten im Skill-Baum. Uhr-Funktionen (Quick Change, Decouple, Codes) schaltet man über Meisterung frei. Dazu Galvan-Händler, Relikte → Dungeons → Code-Tafeln (Self Destruct, Master Control). |
| **Fisk's Superheroes** (1.7.10) | 65+ Anzüge, 90+ Kräfte, alles über **Hero Packs** datengetrieben. Freischaltung über einen Knotenbaum in der Suit Database, gebaut im Suit Fabricator. Die Community liefert Verwandlungsanimationen nach. |
| **Heroes Expansion / LucraftCore** (1.12.2) | Kräfte **erzählen ihren Ursprung**: Kryptonit-Meteor → Fossil → Extraktor → Injektion. Mjolnir verlangt Karma, der Black-Panther-Anzug speichert kinetische Energie. |
| **Palladium** (1.20.1) | Framework für datengetriebene Superkräfte. Darauf bauen viele Addons (Arrzee's Multiverse, FSang18, Unlocked Superheroes). Bekanntes Risiko: KubeJS-Addons laggen. |
| **Twilight Forest** | **Boss-Gating**: Jeder Boss (Naga → Lich → Minoshroom → Hydra → Ur-Ghast → Schneekönigin) öffnet das nächste Biom. Jede Arena ist an ihr Biom gebunden. |
| **Thaumcraft 4** | Forschung als **Entdeckung**: mit dem Thaumometer scannen, Aspekte erst kennen, wenn die Bestandteile bekannt sind. Dazu das Forschungstisch-Minispiel und das Thaumonomicon. |
| **Witchery** (1.7.10) | Rituale aus Naturzutaten. Man kann zum **Vampir oder Werwolf werden**, jeweils mit eigenem Level und Quest-Pfad. Konter gibt es als Gegenstände: Silberschwert, Pflock. |
| **Botania** | Eine Ressource (Mana) und ein Buch (Lexica Botania, Strg über ein Item öffnet die Seite). Klare Stufen: Blumen → Apothecary → Pool → Runenaltar → Terrasteel → Alfheim → Gaia-Wächter. |
| **Create** | **Ponder**: Zu jedem Block gibt es eine animierte In-Game-Erklärung (W halten). Man versteht jede Mechanik, ohne ein Wiki zu öffnen. |
| **ATM 9/10/11** | Quest-Buch durch alle Mods und ein einziges Endziel, den **ATM Star**, der Tiefe in vielen Bereichen verlangt. Dazu eine eigene Abbau-Welt, die wöchentlich zurückgesetzt wird. |
| **FTB-Packs** (Infinity Evolved, Sky Adventures …) | FTB Quests: Kapitel → Quests → Aufgaben → Belohnung, linear über Abhängigkeitslinien. Normal- und Expert-Modus. Fortschritt läuft über das Team. |
| **Attack of the B-Team** (1.6.4) | Spaß vor Balance: rund 70 verrückte Mods (u. a. Galacticraft, Advanced Genetics mit Fähigkeiten von Mobs, Witchery, Dragon Mounts). Zusammengehalten wird alles durch Abenteuer und Mehrspieler. |

## 2. Muster, die alle guten Mods teilen

1. **Tiefe vor Breite.**
   - Alien Evolution hat 11 Aliens, Botania im Kern eine einzige Ressource.
   - Kingdom Omnitrix hat 24 Aliens × 20 Kräfte, dazu Kingdom Hearts, Ratchet & Clank, Raumfahrt, 17 Welten und Dungeons.
   - Das ist zu viel Fläche, um jede Stelle sauber zu machen. Daher das Gefühl, dass „nichts richtig funktioniert“.
2. **Das Spiel erklärt sich selbst.**
   - Ponder, Lexica Botania, Thaumonomicon und das ATM-Quest-Buch erfüllen diese Aufgabe.
   - Bei uns stehen die Erklärungen in `FUNKTIONEN.md`, im Spiel stehen nur Tooltips.
3. **Ein roter Faden mit Ziel.**
   - Twilight Forest hat seine Boss-Kette, Botania den Gaia-Wächter, ATM den ATM Star.
   - Bei uns laufen viele Systeme parallel (Bolts, Raritanium, Meisterschaft, DNA, Heldenstufe, Kalibrierung, Upgrades), ohne dass eins klar das nächste öffnet.
4. **Freischaltung ist ein Erlebnis, keine Zahl.**
   - Beispiele: Relikt → Dungeon → Code-Tafel (Alien Evolution), Meteor → Fossil → Injektion (Heroes Expansion), Ritual (Witchery).
   - Bei uns ist es meist „Stufe ★3 erreicht“ oder „1500 Bolts bezahlt“.
5. **Weniger Tasten, mehr Gefühl.**
   - Alien Evolution und Fisk's legen wenige Kräfte gleichzeitig auf Tasten. Jede Kraft hat Animation, Sound und Treffer-Feedback.
   - Bei uns gibt es 6 Tasten + Kombos + Passive je Alien, dazu G, X, N, C, U, I und K. Das wirkt technisch, nicht nach Ben 10.

## 3. Vergleich nach Bereich

| Bereich | Referenz-Bestwert | Kingdom Omnitrix 1.0.0 | Lücke |
|---|---|---|---|
| **Omnitrix-Bedienung** | Alien Evolution: Rad + Power-Screen mit Werten, Quick Change ist freischaltbar | Rad (G), Schnellkreis (X), Smart Choice (N), Codes (C), Evolve (U), Werkbank | Zu viele Einstiege. Es fehlt ein zentraler Omnitrix-Bildschirm, der alles bündelt (Alien, Kräfte, Werte, Upgrades). |
| **Alien-Movesets** | Alien Evolution / Fisk's: wenige Kräfte, jede mit eigener Körperanimation | 20 Kräfte je Alien, viele teilen Partikel und Grundanimation | Bei vielen Kräften fehlen eigene Animation, Wucht und Trefferfeedback. **Qualität je Kraft hinter der Referenz.** |
| **Alien-Fortschritt** | Alien Evolution: XP → Skill-Baum → Fähigkeit kaufen | Meisterschaft ★1–10 durch Benutzen, Freischaltung automatisch | Es fehlt eine **Entscheidung** (Skill-Baum). Fortschritt passiert einfach. |
| **Aliens beschaffen** | Alien Evolution: Relikte, Dungeons, Galvan-Handel | DNA-Scan auf der Heimatwelt, 3 Proben | Gute Grundidee, aber eintönig: hinfliegen, dreimal C halten. Es fehlt ein Höhepunkt je Alien (Prüfung, Boss, Rätsel). |
| **Anzüge / Rüstung** | Fisk's: Anzug-Datenbank mit Knotenbaum + Fabricator; Heroes Expansion: Anzug mit Eigenmechanik (kinetische Ladung) | Klempner-Ausrüstung, R&C-Rüstungen im Terminal | Es gibt keinen Anzug mit eigener Mechanik und keinen Freischaltbaum. Rüstung ist meist nur Schutzwert. |
| **Forschung / Wissen** | Thaumcraft: Scannen schaltet Wissen frei; Botania/Create: In-Game-Buch und Ponder | Grey-Matter-Analyse (+8 % je Stufe), Galvan-Labor, nur Tooltips | **Ein In-Game-Handbuch fehlt komplett.** Das ist die größte Einzellücke. |
| **Welten / Galaxien** | Twilight Forest: Biom → Struktur → Boss → nächstes Biom; ATM: eigene Abbau-Welt | 17 Alien-Heimatwelten + R&C-Welten + Aphelion, meist Landschaft + Spawns | Den Welten fehlen **Strukturen, Bosse und Gründe zu bleiben**. Viele sind ein umgefärbtes Terrain mit einer Mob-Art. |
| **Bosse / Dungeons** | Twilight Forest: 6 Bosse mit Arena, Mechanik und Gating; Botania: Gaia-Wächter als Abschlussprüfung | Dr. Nefarious, Vilgax, Kevin, Geheime Wasserstraße, Arena, Ereignis-Bosse | Zu wenige Bosse für 17 Welten. Kein Boss öffnet etwas Neues. |
| **Quests / roter Faden** | FTB Quests / ATM: Kapitel mit Abhängigkeiten und einem Endziel | Story „Omnitrix bekommen“, verstreute Quests, Heldenstufe | Es fehlt eine durchgehende Questlinie mit Endziel (unser „ATM Star“). |
| **Erweiterbarkeit** | Fisk's Hero Packs, Palladium-Addons | Datenpakete für Codes, Erfindungen, Shop, Weltereignisse | **Gleichauf**, das ist eine echte Stärke. |
| **Leistung** | Palladium warnt selbst vor KubeJS-Lag | Fabric, eigene Systeme ohne Skript-Engine | **Vorne**, solange die Tick-Logik schlank bleibt. |

## 4. Was „funktioniert nicht richtig“ wahrscheinlich bedeutet

- **Ein echter Spielerlauf fehlt.** Getestet wurde in Einzeltests: Befehle, Glas-Arena, Screenshots im Xvfb.
  Ein zusammenhängender Lauf von Spawn bis Endboss wurde nie gespielt.
- **Fehler an den Übergängen.** Genau dort zeigen sich Fehler: Reise → Welt → Scan → Alien → Kampf → Belohnung.
- **Bekannte Schwachstellen:**
  - Positionen hängen nach (Teleports auf langsamen Clients).
  - Meldungen in der Aktionsleiste bleiben ungeloggt.
  - Die Sounds wurden nie per Gehör geprüft.
- **Die Referenz-Mods sind nicht besser, weil sie mehr haben.** Sie sind besser, weil jeder Pfad oft gespielt und
  geschliffen wurde.

## 5. Umbauplan (Vorschlag, Reihenfolge = Priorität)

### P1 — Stabilität vor allem anderen (Grundlage)
1. **Durchspiel-Test als Skript.** Ein automatisierter Lauf auf dem Produktionsserver (Fabric-Launcher, nicht
   Dev-Umgebung) deckt diese Stationen ab:
   - Omnitrix bekommen
   - verwandeln (jedes Alien)
   - jede Kraft einmal auslösen
   - zu jeder Welt reisen
   - scannen
   - jeden Boss beschwören und besiegen

   Jede Station prüft den Log auf Ausnahmen und Warnungen.
2. **Fehlerliste von SANTIQ einarbeiten.** Gebraucht werden konkrete Punkte aus dem eigenen Spiel: welches Alien,
   welche Kraft, welche Welt, was passiert.
3. **Verschlanken.** Kräfte ohne eigene Wirkung oder Animation werden zusammengelegt oder entfernt:
   - je Alien höchstens **8 gute statt 20 mittelmäßige** Kräfte
   - Schwerpunkt auf den 10 bekanntesten Aliens

### P2 — Omnitrix-Erlebnis (Alien Evolution als Maßstab)
1. **Ein Omnitrix-Bildschirm** für alles, die übrigen Tasten bleiben als Kurzwege. Reiter:
   - Aliens (Werte, Kräfte, Skill-Baum)
   - Gerät (Kalibrierung, Upgrades, Boosts, Farbe)
   - Codes
2. **Skill-Baum je Alien statt automatischer ★-Freischaltung:**
   - Alien-XP gibt Punkte.
   - Der Spieler wählt pro Stufe eine von zwei Kräften oder Passiven (Entscheidung, wie in Alien Evolution).
3. **Verwandlung als Szene:**
   - Kamerafahrt, grüner Blitz, DNA-Helix
   - Körper morpht in 0,6 s (GeckoLib-Animation statt Partikelwolke)
4. **Uhr-Funktionen freispielen** über Meisterung und Code-Tafeln aus Dungeons:
   - Quick Change, Decouple, Master Control, Self Destruct

### P3 — Forschung und Handbuch (Thaumcraft/Botania/Create)
1. **Omnitrix-Datenbank (In-Game-Buch).** Jedes gescannte Wesen, jede Welt, jedes Alien und jede Kraft bekommt eine
   Seite, erreichbar per Taste über einem Item oder einer Kraft (wie Strg in Lexica Botania).
2. **Ponder-artige Vorschau.** Im Loadout zeigt eine kleine animierte Vorschau jede Kraft am Dummy.
3. **Scan als Entdeckung.** Eine neue Art scannen schaltet Datenbank-Seiten und Hinweise auf weitere Welten frei
   (Thaumometer-Prinzip).

### P4 — Welten mit Zweck (Twilight Forest)
1. **Jede Heimatwelt bekommt eine Struktur und eine Prüfung:**
   - Struktur: Tempel, Labor, Wrack o. ä.
   - Prüfung: Wächter-Boss oder Rätsel
   - Das Alien gibt es erst nach bestandener Prüfung (ersetzt dreimal C halten).
2. **Galaxie-Gating.** Jeder Weltenboss liefert eine Koordinate oder ein Teil für das Schiff, das die nächste Welle
   von Welten öffnet. Damit wird die Galaxiekarte zu einer Kette statt eines Menüs.
3. **Weniger, aber dichtere Welten:**
   - lieber 8 Welten mit Struktur, Boss und eigenen Ressourcen als 17 umgefärbte
   - die übrigen bleiben als Nebenziele erhalten

### P5 — Roter Faden mit Endziel (ATM/FTB/Botania)
1. **Questbuch** in Kapiteln, wahlweise FTB Quests als optionale Abhängigkeit oder ein eigenes leichtgewichtiges System:
   Erde → Klempner → erste Welten → Vilgax → Galvan Prime → Master Control → Endkampf.
2. **Endziel:**
   - das **Ultimatrix** bzw. vollständige Master Control als Gegenstück zum ATM Star
   - verlangt je eine Trophäe aus Kingdom Hearts, Ratchet & Clank und Ben 10, so verbinden sich die drei Teile
3. **Endboss als Abschlussprüfung:** Vilgax in Ultimate-Form mit Phasen, Arena und Mechaniken (Gaia-Wächter als Maßstab).

### P6 — Anzüge mit Mechanik (Fisk's / Heroes Expansion)
1. **Klempner-Anzug-Baum** in einer Fabricator-Werkbank: Knoten schalten Anzug-Module frei, etwa
   - Schubdüsen
   - Energieschild
   - Nachtsicht
2. **Eine Eigenmechanik je Anzug**, z. B. ein Plumber-Panzer, der Schaden speichert und als Stoß abgibt
   (kinetische Ladung wie beim Black-Panther-Anzug).

## 6. Was bewusst nicht übernommen wird

- **Palladium/KubeJS als Unterbau:** Forge-only (1.20.1), mit Lag-Risiko. Unser Fabric-1.21.1-Unterbau bleibt.
- **Riesenpack-Ansatz (ATM, B-Team):** Ein Mod kann keinen 400-Mod-Pack ersetzen. Übernommen wird nur die Struktur:
  Questbuch und Endziel.
- **Witchery-Verwandlung in Vampir/Werwolf:** passt thematisch nicht. Übernommen wird nur die Idee „Verwandlung mit
  eigenem Level und Konter-Gegenständen“, z. B. Alien-DNA-Instabilität und Gegenmittel.

## 7. Entscheidung durch SANTIQ nötig

1. **Verschlanken:** Ist es in Ordnung, Kräfte und Welten zu kürzen, damit der Rest gut wird?
2. **Reihenfolge:** P1 → P2 → P3 … oder ein anderer Schwerpunkt, z. B. zuerst Welten?
3. **Fehlerliste:** die drei schlimmsten Dinge aus dem eigenen Spiel, mit `latest.log`.

## Quellen

- Alien Evolution: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/alien-evolution), [Modrinth](https://modrinth.com/mod/alienevo)
- Palladium: [Modrinth](https://modrinth.com/mod/threetag-palladium), [ModpackIndex](https://www.modpackindex.com/mod/54915/palladium)
- Palladium-Addons: [Unlocked Superheroes](https://modrinth.com/mod/unlocked-superheroes), [FSang18's Heropack](https://www.curseforge.com/minecraft/mc-mods/fsang18s-heropack), [Palladium-Verse](https://www.curseforge.com/minecraft/modpacks/palladium-verse)
- ATM 10: [SiriusMC Wiki](https://wiki.siriusmc.net/link/103), [Akliz](https://www.akliz.net/blog/posts/exploring-all-the-mods-10)
- Botania: [Project Overpowered Wiki](https://project-overpowered.gitbook.io/project-overpowered-official-wiki/resources-and-guides/botania), [minecraft-guides.com](https://www.minecraft-guides.com/mod/botania/), [craftdownunder](https://craftdownunder.co/guides/mods/botania)
- Create / Ponder: [Create (GitHub-Spiegel)](https://github.com/Attack8/Create), [Dominion Handbook](https://dominionhandbook.notion.site/Create-9b633e718f6b46f9b696b42365dd8fb2)
- Witchery: [FTB Wiki](https://breezewiki.discard.no/ftb/wiki/Witchery/en-gb), [CurseForge Changelog](https://www.curseforge.com/minecraft/mc-mods/witchery/files/2229781)
- FTB: [Infinity Evolved](https://www.modpackindex.com/modpack/18701/ftb-infinity-evolved), [FTB Sky Adventures](https://www.curseforge.com/minecraft/modpacks/ftb-sky-adventures), [FTB Quests](https://www.9minecraft.net/de/ftb-quests-mod/wiki/)
- Fisk's Superheroes, Heroes Expansion, Attack of the B-Team, Twilight Forest und Thaumcraft 4: Quellen siehe
  `VERGLEICH_REFERENZMODS.md` und die Websuche dieser Sitzung.
