# TODO für SANTIQ — was nur du erledigen kannst

made by SANTIQ · Stand: 2026-10-01 · wird bei jeder Phase aktualisiert

Diese Liste enthält alles, was Claude in der Cloud-Umgebung **nicht selbst herunterladen oder einrichten kann**.
Abhaken mit `[x]`, sobald erledigt.

---

## 1. Netzwerk der Cloud-Umgebung — ✅ erledigt

Seit 2026-10-01 13:00 sind Fabric, Mojang und playerAnimator erreichbar. Claude baut jetzt lokal,
startet einen Dedicated Server und spielt den Client auf einem virtuellen Bildschirm an (Screenshots).

- [x] `maven.fabricmc.net`, `meta.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`,
      `libraries.minecraft.net`, `resources.download.minecraft.net`, `maven.kosmx.dev`
- [ ] Optional: `api.minecraftservices.com`, `sessionserver.mojang.com` — nur für Chat-Signaturen/Skins im Test-Client, nicht nötig

---

## 2. Auf deinem PC installieren — zum Spielen und Testen

- [ ] **Java 21** — https://adoptium.net (Temurin 21, „JDK“). Nur nötig, wenn du selbst baust (`Mod bauen.bat`).
- [ ] **Minecraft Java Edition 1.21.1**
- [ ] **Fabric Loader ≥ 0.17 (empfohlen 0.19.3)** für 1.21.1 — https://fabricmc.net/use/installer/
- [ ] **Fabric API** für 1.21.1 (≥ 0.116) — https://modrinth.com/mod/fabric-api → in den `mods`-Ordner
- [ ] **GeckoLib** für 1.21.1 (≥ 4.9) — https://modrinth.com/mod/geckolib → in den `mods`-Ordner
- playerAnimator ist in Kingdom Omnitrix eingebettet — nichts extra zu installieren
- [ ] **Kingdom Omnitrix** — die `.jar` aus dem letzten grünen GitHub-Lauf: Repository → *Actions* → Workflow **Mod bauen** → neuester grüner Lauf → *Artifacts* → `kingdomomnitrix` (ZIP entpacken, die Datei **ohne** `-sources` in `mods`)
- [ ] Für einen Server: dieselben vier Mods (Fabric API, GeckoLib, Kingdom Omnitrix) auch in den `mods`-Ordner des Servers

## 3. Auf deinem PC installieren — für Grafik und Sound (später)

- [ ] **Blockbench** — https://www.blockbench.net — echte Alien-, Heartless- und Boss-Modelle statt der Platzhalter
  - [ ] in Blockbench: *File → Plugins →* **GeckoLib Animation Utils** installieren
  - Die Platzhalter liegen unter `src/main/resources/assets/kingdomomnitrix/geo/entity/alien/` und lassen sich direkt öffnen
- [ ] **Audacity** (oder ein anderes Audio-Programm) — eigene Sounds als `.ogg` (ab Phase 18). Keine Originalmusik/-sounds aus den Spielen verwenden.
- [ ] Optional: **IntelliJ IDEA Community** + Plugin *Minecraft Development* — falls du selbst am Code arbeiten willst

---

## 4. Im Spiel testen (nach jeder Phase)

### Phase 2 + 3 — Heldendaten und Omnitrix
- [ ] `/hero bolts add 500` und `/hero xp add 250` → Panel oben links aktualisiert sich
- [ ] Sterben und neu einloggen → Werte bleiben erhalten
- [ ] Omnitrix ins Inventar, `/hero dna kingdomomnitrix:heatblast`, Probe rechtsklicken → „Neue DNA im Omnitrix“
- [ ] `G` drücken → Alien-Rad, Heatblast wählen → Verwandlung mit Partikeln, Heatblast-Körper sichtbar (F5)
- [ ] `R` / `V` / `B` → Feuerstoß, Feuerexplosion, Flammenschub; HUD unten rechts zeigt Abklingzeit und Energie
- [ ] In Lava stehen als Heatblast → kein Schaden
- [ ] 60 Sekunden warten → „Omnitrix-Zeit abgelaufen“, danach Nachladezeit im HUD
- [ ] Vierarm in einem 2 Blöcke hohen Gang → „Zu wenig Platz“, kein Ersticken
- [ ] Mit einem zweiten Spieler: Der sieht dein Alien und die Partikel
- [ ] Rückmeldung an Claude: was fehlt, was sich falsch anfühlt, welche Werte zu stark/zu schwach sind

### Phase 4–7 — Kampf, Keyblades, Magie, Herzlose
- [ ] Keyblade: Combo (Linksklick), schwerer Schlag (halten), Luft-Combo, Ausweichen (**Linke Alt**), Blocken (**Feststelltaste**, früher X), Lock-On (**Z**)
- [ ] Zauber mit Rechtsklick, Auswahl mit **M + Mausrad**, MP-Ladezeit wenn die Leiste leer ist
- [ ] `/hero rift` → 3 Wellen Herzlose, Belohnung am Ende

### Phase 8 + 9 — Ratchet & Clank: Waffen und Gadgets
- [ ] Bolts aufheben → landen auf dem Konto; Waffen-Terminal: Combuster kaufen, aufrüsten, Munition nachfüllen
- [ ] OmniWrench werfen (Rechtsklick) auf einen Hebel; Fusionsgranate werfen
- [ ] Heli-Pack + Swingshot herstellen, mit Rechtsklick ausrüsten (oder **H** → Gadget-Gürtel)
- [ ] Heli-Pack: in der Luft Sprungtaste = Doppelsprung, halten = Gleiten. **J** → Heli-Jet: Sprungtaste in der Luft = Schub
- [ ] Swingshot: **Y** auf einen Block → hinziehen, hängen, Springen = Absprung
- [ ] Optionen → Steuerung → Kategorie „Kingdom Omnitrix“: keine rot markierten (doppelt belegten) Tasten. Die Mod meldet doppelte Belegungen beim Einloggen im Chat.
- [ ] Mit einem zweiten Spieler: Der sieht das Swingshot-Seil

### Phase 10 — Quests
- [ ] Neue Welt: Quest-Buch liegt im Inventar, Rechtsklick öffnet es
- [ ] „Das Erwachen“ annehmen, nachts 5 Herzlose aus einem Riss besiegen (oder `/hero rift`), im Buch abgeben
- [ ] „Eine Uhr aus dem All“: Omnitrix herstellen → Quest bereit → DNA-Probe als Belohnung
- [ ] Kopfgeld „Untote“ zweimal hintereinander erledigen (wiederholbar)
- [ ] Texte und Belohnungen: zu viel/zu wenig? Eigene Quest-Ideen an Claude

### Phase 11 — NPCs
- [ ] Kreativ-Tab: „NPC: Meister Yen Sid“ auf den Boden setzen → „!“ über dem Kopf
- [ ] Rechtsklick (mit leerer Hand, nah dran) → Gespräch, Leertaste blättert, Auftrag annehmen
- [ ] Quest erfüllen → „?“ erscheint → beim NPC abgeben
- [ ] Gefallen dir Aussehen und Sprüche der drei? Wünsche für weitere NPCs

### Phase 12 — Raumfahrt, Traverse Town, Arena
- [ ] Aphelion herstellen, aufstellen, einsteigen, nach oben fliegen → Weltall
- [ ] Im All dem Cockpit-Pfeil zu „Traverse Town“ folgen und in den Wirbel fliegen → Ankunft über der Stadt
- [ ] Stadt ansehen: gefallen dir Häuser, Platz, Laternen? (Jede Welt baut ihre Stadt anders)
- [ ] Arena-Terminal am Südende: Bronze-Pokal spielen
- [ ] Außerhalb der Stadt Mythril/Raritanium/Orichalcum suchen, Keyblade und Combuster auf die höchste Stufe bringen
- [ ] Rückweg: im All nach unten fliegen (unter Y 0) oder in den Riss „Heimatwelt“

### Phase 13 — Boss Dr. Nefarious
- [ ] Nefarious-Kommunikator herstellen (Raritanium aus dem All/Traverse Town, Echo-Splitter aus der Antiken Stadt) und benutzen
- [ ] Angriffe lesen: rote Linie, rote Ringe, gelber Ring — fair angekündigt oder zu schnell?
- [ ] Von hinten angreifen und nach dem Laser (überhitzt) zuschlagen — fühlt sich die Schwachstelle gut an?
- [ ] Phase 2 und Wut erleben, Überladung unterbrechen; Omega-Schlüssel ausprobieren
- [ ] Platin-Pokal in der Arena spielen; wenn möglich zu zweit (mehr Leben, geteilte Belohnung)

### Phase 15 — Progression
- [ ] Heldenmenü mit **K** öffnen: Fähigkeiten an-/ablegen, gefällt dir die Liste?
- [ ] Ein paar Stufen aufsteigen (Herzlose, Quests): sind die EP-Mengen fair, oder geht es zu schnell/langsam?
- [ ] Gleiten (ab Stufe 18), Zweite Chance (ab 14) und Hochsprung ausprobieren
- [ ] Ein Alien oft benutzen und die Meisterschaft (★ im Omnitrix-HUD) steigen sehen
- [ ] Ins Weltall/Traverse Town/Nether reisen: „Neue Welt entdeckt“ mit EP

---

### Vergleich mit Referenz-Mods (docs/VERGLEICH_REFERENZMODS.md)
- [ ] AlienEvo und Kingdom Keys selbst kurz anspielen und mit Kingdom Omnitrix vergleichen (die Mod-Seiten sind in der Cloud gesperrt, Claude konnte sie nicht testen)

## 5. Entscheidungen, die noch offen sind

- [ ] Fehlverwandlung (Omnitrix gibt manchmal das falsche Alien, wie in der Serie): ja/nein?
- [ ] Controller-Unterstützung: über die Mod **Controlify** (empfohlen) oder eigene Lösung?
- [ ] Sauerstoff/Überleben im All wie Ad Astra (Pflicht) oder nur als Gadget (O2-System, Vorgabe)?
- [ ] Alien-Farbanpassung am Omnitrix wie bei AlienEvo: gewünscht?

---

## Bereits in der Cloud-Umgebung vorhanden (erledigt durch Claude)

| Werkzeug | Version | Zweck |
|---|---|---|
| Java (OpenJDK) | 21.0.11 | kompilieren |
| Gradle | 9.7.1 (über den Wrapper heruntergeladen) | Build |
| Python + Pillow | 3 / 12.3 | Textur- und Modell-Generatoren, Asset-Check |
| GitHub Actions „Mod bauen“ | — | echter Build bei jedem Push, liefert die `.jar` |
| Xvfb, Mesa, xdotool, ImageMagick | — | Client auf virtuellem Bildschirm starten, steuern, Screenshots (`tools/client_smoke.sh`) |
