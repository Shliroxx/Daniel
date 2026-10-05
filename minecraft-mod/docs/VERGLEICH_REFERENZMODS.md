# Qualitätsvergleich mit Referenz-Mods

made by SANTIQ · Stand: 2026-10-01 (nach Phase 12) · Kingdom Omnitrix 0.5.0-alpha

## Grundlage und Einschränkung

Verglichen wurde mit den von SANTIQ genannten Projekten:

| Referenz | Art | Version / Loader | Quelle der Angaben |
|---|---|---|---|
| Alien Evolution (AlienEvo) | Ben-10-Mod (Palladium-Addon) | 1.20.1, Forge (Palladium) | Websuche: Modrinth-/CurseForge-Beschreibung |
| Ad Astra | Weltraum-Mod | Fabric/NeoForge | Websuche: Mod-Wikis, Guides |
| Galaxies and Gallimimus | Modpack (alle Galacticraft-Addons) | 1.12.2, Forge | Websuche: CurseForge-Eintrag |
| Kingdom Keys 2 (Modpack um die Mod Kingdom Keys) | Kingdom-Hearts-Mod | bis 1.21.1, Forge/NeoForge | Websuche: Modrinth, Wiki |
| Thaumcraft | Magie-/Forschungs-Mod | 1.7.10–1.12.2, Forge | Websuche: Wiki |

**Wichtig:** Die CurseForge- und Modrinth-Seiten sind in dieser Cloud-Umgebung gesperrt (Netzwerk-Proxy).
Die Referenz-Mods konnten daher **nicht installiert und nicht selbst gespielt** werden. Der Vergleich stützt sich auf
die veröffentlichten Feature-Beschreibungen. Aussagen über deren Grafikqualität sind deshalb nur grob; ein
echter Seite-an-Seite-Test (gleiche Szene, gleiche Kamera) steht noch aus → `TODO_SANTIQ.md`.

Bewertung: **vorne** · **gleichauf** · **dahinter** · **fehlt** (aus Sicht von Kingdom Omnitrix).

---

## 1. Ben 10 — gegen Alien Evolution

| Bereich | Alien Evolution | Kingdom Omnitrix | Bewertung |
|---|---|---|---|
| Anzahl Aliens | viele („a variety“), mit Addons (Into The Omniverse) noch mehr | 5 (3 voll, 2 Prototyp) | **dahinter** |
| Modell-/Animationsqualität | „high quality aliens“, über Palladium | generierte Platzhalter-Modelle (GeckoLib), einfache Animationen | **dahinter** |
| Eigene Movesets je Alien | ja | ja, 3 Fähigkeiten je Alien, JSON-gesteuert | gleichauf (Umfang dahinter) |
| Farb-Anpassung (Alien, Omnitrix-Werkbank) | ja, jede Farbe einzeln | fehlt | **fehlt** |
| Abhängigkeiten | braucht Palladium (Superhelden-Framework) | eigenständig (nur Fabric API, GeckoLib) | **vorne** |
| Datengetriebene Aliens | über Palladium-Packs | eigene JSON-Registry, Datapacks | gleichauf |
| Einbindung ins Spiel (DNA, Quests, Gegner-Synergien) | Fokus auf Verwandlung | DNA-Drops, Quests, Arena, Kombination mit Keyblade/Waffen | **vorne** |
| Transformations-Präsentation | – (nicht prüfbar) | Partikel + Ton, keine Kamerafahrt, keine Zwischenanimation | vermutlich **dahinter** |

**Fazit:** Bei Optik und Anzahl der Aliens klar hinten. Die Verwandlung ist der wichtigste Moment der Mod (AAA-Vorgabe)
und muss hier zuerst aufholen: echte Modelle, Verwandlungssequenz, Omnitrix-Modell.

## 2. Kingdom Hearts — gegen Kingdom Keys

| Bereich | Kingdom Keys | Kingdom Omnitrix | Bewertung |
|---|---|---|---|
| Keyblades | 120+, fast 100 mit 3D-Modell | 2, flache Item-Texturen, Upgrades per JSON | **weit dahinter** |
| Drive Forms / Formen | ja, inkl. Beidhändigkeit | fehlt (Alien-Verwandlung übernimmt die Rolle teilweise) | **fehlt** |
| Kommandomenü / KH-Menü | KH2-Stil, Hauptmenü | eigene HUDs (Status, Magie, Lock-On), Quest-Buch, kein Kommandomenü | dahinter |
| Magie | ja, mehrere | Feuer, Eis, Donner, Vita in 3 Stufen, KH2-MP-Ladezeit | gleichauf (Anzahl dahinter) |
| Kampfsystem | Kombos, Limits, Shotlocks | Combo + Finisher, Luftcombo, schwerer Schlag, Ausweichen, Perfekter Block, Lock-On mit Kamera | gleichauf |
| Gegner | 15+ Herzlose und Niemande | 5 Herzlose mit eigenem Verhalten, Elite-Variante, Skalierung | dahinter |
| Bosse | einige | **keiner** | **fehlt** |
| Level-/Fähigkeitssystem | ja, Ability-Punkte | Heldenstufe + EP, keine Fähigkeitspunkte | dahinter |
| Welten | „Station of Awakening“ | Traverse Town (generiert, Stadt, Arena) + Weltall | **vorne** |
| Organisation XIII, Limits | ja | fehlt | fehlt (bewusst nicht geplant) |

**Fazit:** Kingdom Keys ist bei Inhalt (Keyblades, Formen, Gegner) um Größenordnungen weiter. Kingdom Omnitrix punktet
mit der Verbindung zu den anderen beiden Welten und mit erkundbaren Welten. Laut AAA-Vorgabe soll nicht die Masse
aufgeholt werden, sondern die Qualität: wenige Keyblades mit echtem 3D-Modell, Silhouette, Spezialfähigkeit.

## 3. Weltraum — gegen Ad Astra und Galaxies and Gallimimus (Galacticraft-Addons)

| Bereich | Ad Astra / Galacticraft-Pack | Kingdom Omnitrix | Bewertung |
|---|---|---|---|
| Reiseziele | 5 Planeten/Monde in 2 Sonnensystemen (Ad Astra); Galacticraft-Addons: sehr viele | Weltall + Traverse Town (+ Rückweg zur Oberwelt) | **dahinter** |
| Raumschiff | Raketen in 4 Stufen, Rover | 1 fliegbares Schiff mit KI-Meldungen, frei steuerbar im All | anders (Fliegen im All: **vorne**, Stufen: fehlt) |
| Weltall als Ort | meist Raumstation/Orbit-Menü | eigene Dimension mit Asteroiden, Sternenhimmel, Navigation zu Rissen | **vorne** |
| Überleben (Sauerstoff, Temperatur, Treibstoff) | Kernmechanik | fehlt (bewusst „Minecraft bleibt Minecraft“) | fehlt (Entscheidung offen) |
| Maschinen, Fluide, Energie | umfangreich | fehlt | fehlt (nicht Ziel der Mod) |
| Planeten-Materialien als Fortschritt | ja (Erz → nächste Raketenstufe) | Raritanium/Mythril/Orichalcum → Waffen- und Keyblade-Upgrades | gleichauf (kleiner) |
| Eigene Generierung je Welt | ja | ja (Traverse Town: 4 Biome, eigene Features, Erze) | gleichauf (Anzahl dahinter) |
| Bewohnte Welten (NPCs, Stadt, Quests, Arena) | kaum | ja | **vorne** |

**Fazit:** Ad Astra und Galacticraft sind Technik-/Survival-Mods mit vielen Planeten. Kingdom Omnitrix ist
Abenteuer-orientiert: weniger Ziele, dafür belebt. Fehlend und sinnvoll: mehr Welten (Vorgabe: Insel, Halloween-artig,
Sci-Fi-Planet, Dark World) und Gefahren pro Welt (z. B. O2-System als Gadget laut Vorgabe statt globaler Sauerstoffpflicht).

## 4. Fortschritt und Wissen — gegen Thaumcraft

| Bereich | Thaumcraft | Kingdom Omnitrix | Bewertung |
|---|---|---|---|
| Forschungssystem / Entdeckung | Scannen, Aspekte, Forschungstisch, Thaumonomicon | Quest-Buch mit Story/Neben/Kopfgeld; kein Lexikon | **dahinter** |
| Ingame-Lexikon | Thaumonomicon füllt sich durch Entdeckung | fehlt (AAA-Vorgabe: „Hero Codex“) | **fehlt** |
| Eigene Handwerkssysteme | Infusionsaltar, Arkanwerkbank | Keyblade-Schmiede, Waffen-Terminal, Gadget-Gürtel | gleichauf (einfacher) |
| Welt-Konsequenzen | Taint (Verderbnis breitet sich aus) | Dunkelheitsrisse (zeitlich begrenzt) | dahinter |
| Tempo/Pacing des Fortschritts | sehr stark geführt | Heldenstufe, Pokale, Quests, Materialien aus Welten | gleichauf |

**Fazit:** Thaumcraft ist das Vorbild für **Entdecken statt Ablesen**. Der „Hero Codex“ aus der AAA-Vorgabe sollte sich
wie das Thaumonomicon durch Entdecken füllen (Herzlose besiegen → Eintrag, Alien freischalten → Eintrag).

---

## Gesamtbild

**Wo Kingdom Omnitrix heute vorne liegt**
- Drei Welten in **einem** Spiel: Bolts kaufen Waffen, Erze aus fremden Welten verbessern Keyblades, Aliens kämpfen in der Arena.
- Bewohnte, erkundbare Welten statt reiner Rohstoff-Planeten; freies Fliegen im Weltall.
- Eigenständig (keine Framework-Abhängigkeit), datengetrieben (Aliens, Keyblades, Zauber, Waffen, Quests, NPCs, Risse, Arena als JSON).
- Mehrspielerfest gebaut (Server prüft alle Absichten, Dedicated-Server-Test, kein Anti-Fly-Kick).

**Wo sie deutlich hinten liegt (nach Gewicht)**
1. **Optik:** Alle Modelle (Aliens, Herzlose, NPCs, Schiff) und Texturen sind generierte Platzhalter. Das ist der größte Abstand
   zu AlienEvo und Kingdom Keys und widerspricht der AAA-Vorgabe („Asset gilt erst als fertig mit Modell, UV, Textur, Animation, VFX, Sound“).
2. **Keine Bosse:** Kingdom Keys hat welche; die Vorgabe verlangt echte Bosskämpfe. → Phase 13 ist der nächste Schritt.
3. **Verwandlung ohne Inszenierung:** keine Sequenz, keine Kamera, kein Omnitrix-Modell.
4. **Keine eigenen Sounds:** überall Vanilla-Klänge.
5. **Umfang:** 5 Aliens, 2 Keyblades, 3 Waffen, 1 Welt – bewusst klein (Qualität vor Quantität), aber die Qualität der
   vorhandenen Assets ist noch nicht auf Referenzniveau.
6. **Kein Lexikon / Codex, keine Fähigkeitspunkte, keine Drive-Formen.**
7. **Keine JEI/REI/EMI-Anbindung, keine Config-Datei.**

## Maßnahmen (eingeordnet in die Roadmap)

| Maßnahme | wann | Bezug |
|---|---|---|
| Boss mit Phasen, Telegraphing, Arena-Mechanik, Schwachstelle | Phase 13 (nächste) | Kingdom Keys, AAA-Vorgabe |
| Echte Modelle + Texturen für Omnitrix, Heatblast, XLR8, Vierarm, 2 Keyblades, 5 Herzlose, Boss (Blockbench, nicht generiert) | Phase 18 + Abschluss-Überarbeitung | AlienEvo, Kingdom Keys |
| Verwandlungssequenz (Omnitrix hebt sich → Licht → Silhouette → Impact), Kamera | Abschluss-Überarbeitung (Kern 1) | AAA-Vorgabe |
| Eigene Sounds (selbst erstellt/lizenzfrei) | Phase 18 | alle |
| Hero Codex, der sich durch Entdecken füllt | Phase 19 (UI) | Thaumcraft |
| Fähigkeitspunkte / Skill-Bäume (optional, kein Zwang) | Phase 15 (Progression) | Kingdom Keys, DESIGN_PROGRESSION |
| Weitere Welten mit Gefahren (O2-Gadget statt globaler Sauerstoffpflicht) | Phase 22 | Ad Astra, Galacticraft |
| JEI/REI/EMI-Rezepte, Config | Phase 21 | Modpack-Tauglichkeit |
| Alien-Farbanpassung am Omnitrix | später (Wunsch prüfen) | AlienEvo |

## Quellen

- [Alien Evolution – Modrinth](https://modrinth.com/mod/alienevo), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/alienevo), [Into The Omniverse](https://modrinth.com/mod/into-the-omniverse)
- [Kingdom Keys 2 – Modrinth](https://modrinth.com/mod/kingdom-keys-2), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/kingdom-keys-re-coded), [Kingdom Keys Wiki](https://www.kingdomkeys.online/)
- [Ad Astra – minecraft-guides.com](https://www.minecraft-guides.com/mod/ad-astra/), [craftdownunder.co](https://craftdownunder.co/guides/mods/ad-astra)
- [Galaxies and Gallimimus – CurseForge](https://www.curseforge.com/minecraft/modpacks/galaxies-and-gallimimus)
- [Thaumcraft 4 Wiki – Research](https://thaumcraft-4.fandom.com/wiki/Research), [Aspects](https://thaumcraft-4.fandom.com/wiki/Aspects)
