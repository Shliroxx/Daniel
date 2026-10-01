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

---

## 5. Entscheidungen, die noch offen sind

- [ ] Fehlverwandlung (Omnitrix gibt manchmal das falsche Alien, wie in der Serie): ja/nein?
- [ ] Controller-Unterstützung: über die Mod **Controlify** (empfohlen) oder eigene Lösung?

---

## Bereits in der Cloud-Umgebung vorhanden (erledigt durch Claude)

| Werkzeug | Version | Zweck |
|---|---|---|
| Java (OpenJDK) | 21.0.11 | kompilieren |
| Gradle | 9.7.1 (über den Wrapper heruntergeladen) | Build |
| Python + Pillow | 3 / 12.3 | Textur- und Modell-Generatoren, Asset-Check |
| GitHub Actions „Mod bauen“ | — | echter Build bei jedem Push, liefert die `.jar` |
| Xvfb, Mesa, xdotool, ImageMagick | — | Client auf virtuellem Bildschirm starten, steuern, Screenshots (`tools/client_smoke.sh`) |
