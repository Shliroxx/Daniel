# J.A.R.V.I.S.

Ein persönlicher KI-Assistent mit Wake-Word, echter PC-Steuerung und Iron-Man-HUD.
Läuft als kleiner Server auf deinem Rechner — bedienbar per Sprache, im Browser
und über WhatsApp.

```
"Hey Jarvis"  ──▶  Whisper (lokal)  ─┐
WhatsApp      ──▶  Text / Sprache   ─┼─▶  claude -p  ──▶  Piper (lokal)  ──▶  Antwort
HUD-Eingabe   ──▶  Text             ─┘        │
                                              ├─ PC-Steuerung, Dateien (über MCP)
                                              ├─ Websuche, Datei-Werkzeuge
                                              └─ Obsidian: Gedächtnis, Aufgaben, Protokoll
```

**Spracherkennung und Sprachausgabe laufen komplett offline** auf deinem Rechner.
Gedacht wird über deine Claude-Code-Installation — also über dein **Claude-Abo**
statt über bezahlte API-Tokens.

---

## Was Jarvis kann

| Bereich | Fähigkeiten |
|---|---|
| **Sprache** | Wake-Word „Hey Jarvis" im Dauerbetrieb, Push-to-Talk im Browser, deutsche Sprachausgabe |
| **WhatsApp** | Text- und Sprachnachrichten von unterwegs, Antwort als Text oder Sprachnachricht |
| **PC steuern** | Programme starten, Webseiten öffnen, Lautstärke, Screenshots, Systeminfos, Prozesse, Bildschirm sperren |
| **Dateien** | Lesen, schreiben, suchen — auf dein Arbeitsverzeichnis begrenzt |
| **Obsidian** | Gedächtnis, Aufgabenliste und Gesprächsprotokoll als Markdown im Vault; durchsucht deine bestehenden Notizen |
| **Web** | Websuche und Seitenabruf |
| **Nachts** | Arbeitet eine Auftragsliste ab, während du schläfst — mit Wiederholung bei erschöpftem Kontingent |
| **Brainstorming** | Sparringspartner mit eigener Meinung, kein Ja-Sager |
| **Hookah Analyzer** | Eigene Kamera-App fürs iPhone: bewertet den Kopf live, markiert im Bild was weg muss und was fehlt — läuft ohne PC |

---

## Einrichtung

### 1. Voraussetzungen

- [Python 3.11+](https://www.python.org/downloads/) — bei der Installation
  **„Add Python to PATH"** anhaken
- [Node.js 18+](https://nodejs.org) und Claude Code:
  ```
  npm install -g @anthropic-ai/claude-code
  claude          # einmal starten und anmelden
  ```
  Dafür brauchst du ein **Claude-Pro- oder Max-Abo**.
- Optional: [ffmpeg](https://ffmpeg.org/download.html) im PATH — für Sprachnachrichten

> Kein Claude-Abo? Setz in der `.env` `JARVIS_BACKEND=api` und trag einen
> `ANTHROPIC_API_KEY` ein. Dann läuft alles über die API und kostet pro Token.

### 2. Einrichten — einmalig

Doppelklick auf **`Jarvis einrichten.bat`** (Windows).

Das installiert alles, legt die `.env` an und packt zwei Symbole auf den Desktop:
**Jarvis** und **Jarvis beenden**. Auf Wunsch startet er künftig automatisch beim
Anmelden. Schau danach kurz in die `.env` — vor allem `JARVIS_VAULT` sollte auf
deinen Obsidian-Ordner zeigen.

Auf macOS/Linux: einmal `./start.sh`. Danach lässt sich `scripts/jarvis.command`
ins Dock legen.

### 3. Starten

Doppelklick auf das **Jarvis**-Symbol auf dem Desktop. Kein Konsolenfenster, kein
Server von Hand — der Dienst läuft im Hintergrund und das HUD geht im Browser auf.
Läuft er schon, holt das Symbol nur das HUD nach vorn.

Zum Beenden das Symbol **Jarvis beenden**. Wer lieber im Terminal arbeitet,
benutzt weiterhin `start.bat` bzw. `./start.sh`.

Beim ersten Start lädt Jarvis Whisper, das Wake-Word-Modell und die deutsche
Piper-Stimme herunter (einmalig, ca. 1 GB).

### 3. Benutzen

Browser: **http://localhost:8765** — dann einfach *„Hey Jarvis, wie ist meine
CPU-Auslastung?"* sagen.

---

## Vom Handy aus

**Im eigenen WLAN** reicht der Browser. Lokale IP mit `ipconfig` herausfinden, dann
am Handy `http://192.168.1.42:8765` aufrufen. Über *Teilen → Zum Home-Bildschirm*
wird daraus eine App.

> iOS erlaubt Mikrofonzugriff im Browser nur über HTTPS oder localhost. Über die
> lokale IP ist der Mikrofon-Knopf am iPhone deshalb meist blockiert — Texteingabe
> funktioniert. Für Sprache unterwegs nimm WhatsApp.

**Von überall** geht WhatsApp — siehe unten.

---

## WhatsApp einrichten

Das ist der aufwendigste Teil, weil WhatsApp keinen einfachen Bot-Weg wie Telegram
hat. Rechne mit einer halben Stunde.

> **Warum nicht einfacher?** Es gibt Bibliotheken wie whatsapp-web.js, die deine
> normale WhatsApp-Nummer fernsteuern und keine Anmeldung bei Meta brauchen. Die
> verstoßen aber gegen WhatsApps Nutzungsbedingungen und können dazu führen, dass
> **deine Nummer gesperrt wird**. Deshalb ist hier nur der offizielle Weg gebaut.

### Schritt 1 — Meta-App anlegen

1. [developers.facebook.com](https://developers.facebook.com) → **Meine Apps** →
   **App erstellen** → Typ **Business**
2. In der App: **WhatsApp** als Produkt hinzufügen
3. Unter *WhatsApp → API-Einrichtung* findest du:
   - eine kostenlose **Testnummer** und deren **Telefonnummern-ID** → `WHATSAPP_PHONE_NUMBER_ID`
   - ein **temporäres Zugriffstoken** → `WHATSAPP_TOKEN`
4. Bei *Empfängernummer* trägst du **deine eigene Handynummer** ein und bestätigst
   den Code. Nur bestätigte Nummern können mit der Testnummer schreiben.
5. Unter *App-Einstellungen → Allgemein* findest du das **App-Secret** → `WHATSAPP_APP_SECRET`

Das temporäre Token läuft nach 24 Stunden ab. Für den Dauerbetrieb erstellst du
unter *Unternehmenseinstellungen → Systembenutzer* einen Systembenutzer mit einem
permanenten Token.

### Schritt 2 — Deinen PC erreichbar machen

Meta muss deinen Rechner erreichen können. Ein Cloudflare-Tunnel macht das ohne
Konto und ohne Router-Konfiguration:

```
# Windows:  winget install --id Cloudflare.cloudflared
# macOS:    brew install cloudflared

cloudflared tunnel --url http://localhost:8765
```

Das gibt dir eine Adresse wie `https://zufall-worte-hier.trycloudflare.com`.
Lass das Fenster offen — die Adresse ändert sich bei jedem Neustart. Für
Dauerbetrieb richtest du einen benannten Tunnel mit fester Adresse ein.

### Schritt 3 — Webhook verbinden

In der `.env`:

```
JARVIS_WHATSAPP_ENABLED=true
WHATSAPP_TOKEN=EAAG...
WHATSAPP_PHONE_NUMBER_ID=123456789012345
WHATSAPP_VERIFY_TOKEN=denk-dir-was-aus-123
WHATSAPP_APP_SECRET=abc123...
WHATSAPP_ALLOWED_NUMBERS=4915112345678
WHATSAPP_VOICE_REPLY=false
```

Jarvis neu starten. Dann bei Meta unter *WhatsApp → Konfiguration → Webhook*:

- **Callback-URL:** `https://deine-tunnel-adresse.trycloudflare.com/whatsapp/webhook`
- **Verify-Token:** derselbe Wert wie `WHATSAPP_VERIFY_TOKEN`
- Danach bei **Webhook-Felder** das Feld **`messages`** abonnieren

Wenn im Terminal *„WhatsApp-Webhook von Meta bestätigt"* steht, hat es geklappt.
Schreib der Testnummer eine Nachricht.

> **Sicherheit:** `WHATSAPP_ALLOWED_NUMBERS` ist die einzige Zugangskontrolle —
> nur diese Nummern können Jarvis bedienen. `WHATSAPP_APP_SECRET` sorgt dafür, dass
> nur echte Meta-Anfragen angenommen werden. Lass beides nicht leer.
>
> **24-Stunden-Regel:** Meta erlaubt freie Antworten nur innerhalb von 24 Stunden
> nach deiner letzten Nachricht. Für dich als Nutzer ist das kein Problem — du
> schreibst ja zuerst. Jarvis kann dich von sich aus aber nicht anschreiben, wenn
> länger Funkstille war.

---

## Nacht-Aufträge

Du schreibst abends eine Liste, Jarvis arbeitet sie nachts ab.

```
python -m jarvis.orchestrator auftraege.md
python -m jarvis.orchestrator auftraege.md --trocken   # nur anzeigen, nichts tun
```

Eine Auftragsdatei sieht so aus:

```markdown
---
modell: sonnet
ausgabeordner: ~/Obsidian/jarvis/ergebnisse
sperrstunde: "07:00"
versuche: 4
---

- [ ] Fasse alle Notizen aus Projekte/Website zu einer Übersicht zusammen
- [ ] Prüfe meine Aufgabenliste auf Altlasten

## Aufgabe: Tiefenrecherche

- modell: opus
- start: 01:00
- ausgabe: ~/Obsidian/jarvis/ergebnisse/recherche.md

Recherchiere gründlich zu [THEMA] und schreib einen Bericht mit Quellen.
```

Jarvis hakt die Checkboxen ab (`[x]` erledigt, `[!]` fehlgeschlagen) und hängt am
Ende einen Bericht an die Datei. Ist dein Kontingent erschöpft, wartet er eine
Stunde und versucht es erneut — aber nur bis zur **Sperrstunde**, damit tagsüber
wieder genug übrig ist.

Fertige Vorlagen liegen in [`templates/`](templates/): Recherche, Notizen aufräumen,
Wochenrückblick, Code durchsehen.

Damit das automatisch läuft, richte einen Zeitplan ein (Windows: Aufgabenplanung,
macOS/Linux: cron oder launchd) — zum Beispiel jede Nacht um 0:30 Uhr.

---

## Obsidian

Zeigt `JARVIS_VAULT` auf deinen Vault, legt Jarvis dort einen Ordner `jarvis/` an:

| Datei | Inhalt |
|---|---|
| `gedaechtnis.md` | Was Jarvis dauerhaft über dich weiß — du kannst selbst editieren |
| `aufgaben.md` | Deine To-do-Liste als Checkboxen |
| `protokoll/JJJJ-MM-TT.md` | Jedes Gespräch, nach Tagen sortiert |
| `auftraege/`, `ergebnisse/` | Nacht-Aufträge und ihre Ergebnisse |

Alles ist normales Markdown mit Checkboxen und Wikilinks. Die Metadaten stehen in
HTML-Kommentaren, die Obsidian ausblendet. Jarvis durchsucht außerdem deine
bestehenden Notizen, bevor er Fragen aus dem Bauch beantwortet.

Ohne Vault landen dieselben Dateien im `data/`-Ordner — nichts geht verloren.

---

## Bedienung im HUD

| Element | Funktion |
|---|---|
| Reaktor in der Mitte | grau = bereit, cyan = hört zu, grün = nimmt auf, orange = denkt |
| **Mikrofon-Knopf** | Gedrückt halten und sprechen (am PC auch: **Leertaste** halten) |
| **Texteingabe** | Tippen statt sprechen — Antwort wird trotzdem vorgelesen |
| **Verlauf** (links) | Alle Gespräche, auch die über WhatsApp |
| **Aktivität** (rechts) | Live: welche Werkzeuge Jarvis benutzt, wie er denkt |
| **Stopp** | Bricht die Sprachausgabe ab |
| **Neu starten** | Leert den Verlauf (Gedächtnis und Aufgaben bleiben) |

---

## Wichtigste Einstellungen

| Einstellung | Bedeutung |
|---|---|
| `JARVIS_BACKEND` | `cli` = über dein Claude-Abo, `api` = über bezahlte API-Tokens |
| `JARVIS_CLI_MODEL` | `sonnet` (schnell, Standard), `opus` (klüger), `haiku` (am schnellsten) |
| `JARVIS_WHISPER_MODEL` | `base` bei schwacher Hardware, `small` als guter Standard, `large-v3` für beste Erkennung |
| `JARVIS_WHISPER_DEVICE` | `cuda` mit NVIDIA-Grafikkarte — deutlich schneller |
| `JARVIS_WAKEWORD_THRESHOLD` | Niedriger = löst leichter aus, höher = weniger Fehlalarme |
| `JARVIS_MIC_ENABLED` | `false`, wenn Jarvis nicht dauerhaft mithören soll |
| `JARVIS_PIPER_VOICE` | Andere Stimme, z. B. `de_DE-kerstin-low` |
| `JARVIS_ALLOW_SHELL` | `true` erlaubt beliebige Shell-Befehle — **nur wenn du weißt, was du tust** |

---

## Sicherheit

Jarvis hat echten Zugriff auf deinen Rechner. Deshalb:

- **Dateizugriff ist eingesperrt.** Nur `JARVIS_WORKSPACE` und der Jarvis-Ordner im
  Vault. Jeder Pfad wird aufgelöst und geprüft — kein Ausbrechen über `..`.
- **Shell-Befehle sind standardmäßig gesperrt.**
- **WhatsApp:** nur freigeschaltete Nummern, und jede Anfrage wird gegen das
  App-Secret geprüft.
- **Der HUD-Server hat keine Anmeldung.** Betreibe ihn nur im eigenen WLAN und gib
  den Port nicht ins Internet frei. Der Cloudflare-Tunnel für WhatsApp macht nur
  den Webhook-Pfad öffentlich — richte ihn nicht auf den ganzen Server, wenn du
  ihn dauerhaft laufen lässt.
- **Deine Zugangsdaten** stehen in `.env`, die per `.gitignore` ausgeschlossen ist.

---

## Hookah Analyzer — der AR-Kopfbauer fürs iPhone

Ein Kamera-Coach, der dir beim Kopfbauen über die Schulter schaut. Er liegt als
App auf deinem Homescreen und läuft **ohne deinen PC** — die Analyse geht direkt
vom Handy an ein Bildmodell.

```
iPhone-Kamera ─▶ ruhig & scharf? ─▶ Bildmodell (frei wählbar) ─▶ JSON
                       │                                          │
                       └──── AR-Overlay, Sprache, Note ◀──────────┘
```

Das Gehirn steckt in der App selbst: Prompt bauen, Antwort prüfen, Note rechnen —
alles im Browser. Der Rechner wird nur gebraucht, wenn du die Analyse bewusst über
dein Claude-Abo laufen lassen willst.

### Was er macht

- **Live-Coaching** während des Bauens: ein gesprochener Satz plus die nächsten
  Handgriffe, phasenweise von „Kopf prüfen" bis „Kohle auflegen".
- **AR-Markierungen** direkt im Kamerabild: Rot heißt hier Tabak weg, Gelb
  auflockern, Blau ist die empfohlene Füllhöhe, Grün passt schon.
- **Vollanalyse** auf Knopfdruck: Note von 0 bis 100, sieben Einzelkategorien,
  Probleme nach Schweregrad, nummerierter Optimierungsplan und eine Schätzung,
  wie gut der Kopf nach der Korrektur wird.
- **Lernen aus Sessions**: Nach dem Rauchen sagst du, wie es lief (Geschmack,
  Rauch, Kratzen, Hitze, Dauer). Das fließt in die nächsten Empfehlungen ein, und
  die App zeigt, wie treffsicher ihre Vorhersagen bisher waren.
- **Sprachsteuerung**: Beim Bauen sind beide Hände voll — sag einfach „weiter",
  „bewerten" oder „pause".
- **Historie**: Jeder bewertete Kopf bleibt mit Vorschaubild auf dem Gerät, mit
  Notenverlauf über die Zeit.
- **Teilen**: Der Report lässt sich als Bild verschicken.

### Welches Modell — und was es kostet

In der App unter **Modell und Zugang**:

| Anbieter | Kosten | Anmerkung |
|---|---|---|
| **Google Gemini** | kostenloses Freikontingent | Schlüssel auf `aistudio.google.com` → „Get API key". Keine Kreditkarte. Empfehlung. |
| **OpenRouter** | kostenlose Modelle mit `:free` | Schlüssel auf `openrouter.ai`. Auswahl wechselt öfter — Modellname ist frei eintippbar. |
| **eigener Rechner** | dein Claude-Abo | Beste Qualität, aber der PC muss laufen. Adresse **und Losungswort** eintragen — beides zeigt der PC beim Start an. |

Der Schlüssel bleibt auf dem Handy (`localStorage`) und geht nur an den gewählten
Anbieter. Er landet nie in diesem Projekt und nirgendwo sonst.

> **Beschränke den Schlüssel trotzdem beim Anbieter.** Der Speicher gehört zur
> Webadresse, nicht zu dieser einen Seite: alles, was unter derselben Adresse
> liegt, kann ihn lesen. Bei Google geht das unter *API key → Website
> restrictions*, bei OpenRouter über ein Guthabenlimit pro Schlüssel.

**Der Rückweg über den eigenen PC braucht ein Losungswort.** Der Proxy lässt
Claude Code auf deinem Rechner arbeiten — ohne Schranke könnte jede beliebige
Webseite, die du im Browser offen hast, ihn ansprechen, solange er läuft. Beim
Start zeigt er ein Wort an, das du einmal in der App einträgst; für ein festes
Wort `SHISHA_TOKEN` in die `.env` schreiben.

Bei kostenlosen Modellen ist ein **Limit pro Minute und pro Tag** normal. Wenn es
greift, steht in der App „Freikontingent gerade erschöpft" — dann kurz warten.
Bilder gehen auf 896 Pixel Kantenlänge verkleinert raus, das schont das Kontingent.

### Kontingent im Blick

Oben rechts steht, wie viele Anfragen der heutige Tag schon gekostet hat. Wird es
knapp, färbt sich die Zahl. Der **Sparmodus** verhindert außerdem, dass ein
unverändert vor der Kamera liegender Kopf zehnmal dieselbe Bewertung bekommt —
analysiert wird nur, wenn sich im Bild etwas getan hat.

### Wenn es „ewig scannt"

Aus der Hand gehalten ist ein Bild selten ganz ruhig. Die App wartet deshalb kurz
auf eine brauchbare Aufnahme — was gerade fehlt, steht oben als „halt still",
„unscharf" oder „zu dunkel", mit Sekundenzähler. Nach 5 Sekunden misst sie
nachsichtiger, nach 12 Sekunden schickt sie das Bild so, wie es ist. Nur
stockdunkel bringt wirklich nichts.

Live wird Gemini außerdem **ohne Nachdenkzeit** gefragt (`thinkingBudget: 0`).
Sonst verbringt das Modell Sekunden mit einer Gedankenkette, deren Tokens vom
Antwortbudget abgehen — im schlimmsten Fall kommt gar nichts zurück. Für die
Vollanalyse auf Knopfdruck darf es denken, da zählt Gründlichkeit.

### Was die App aus deinen Sessions lernt

Sagst du dreimal, dass es zu heiß war, wird daraus eine benannte Regel:

> **Es wird dir regelmäßig zu heiß** (3 Sessions)
> Empfiehl eine Kohle weniger als üblich und mindestens 1 mm mehr Abstand zum HMD.

Die Regel steht als Vorgabe im Prompt und ist unter *Modell und Zugang*
nachlesbar — kein stiller Automatismus, sondern etwas, das du sehen und
nachvollziehen kannst. Welche Regeln es gibt und ab wie vielen Rückmeldungen sie
greifen, steht in `spec.json`.

### Freihändig per Sprache

Mit dem Mikrofon-Symbol schaltest du das Zuhören ein. Erkannt werden:

| Sagst du | Passiert |
|---|---|
| „weiter" / „nächste Phase" | eine Bauphase vor |
| „zurück" | eine Bauphase zurück |
| „bewerten" / „Analyse" | Vollanalyse starten |
| „neuer Kopf" / „von vorne" | Sitzung zurücksetzen |
| „pause" / „stopp" | Kamera anhalten |
| „weitermachen" / „Kamera an" | aus der Pause zurück |
| „menü" | zurück ins Hauptmenü |
| „wiederhole" | letzten Hinweis nochmal sagen |
| „status" | aktuelle Note vorlesen |
| „ton aus" / „ton an" | Sprachausgabe schalten |

Während die App selbst spricht, hört sie nicht zu — sonst würde sie ihre eigenen
Hinweise als Befehle verstehen. Die Wortlisten stehen in `spec.json` und lassen
sich dort erweitern, ohne Code anzufassen.

Safari braucht dafür einmalig die Mikrofon-Freigabe. Kann der Browser keine
Spracherkennung, erscheint das Symbol gar nicht erst.

### Wenn die Kamera stehenbleibt

iOS gibt die Kamera frei, sobald die App länger im Hintergrund war — das Livebild
friert dann ein. Die App merkt das auf drei Wegen: die Kameraspur meldet ihr Ende,
das Bild bleibt zu lange stehen, oder die App kommt aus dem Hintergrund zurück.

Dann erscheint eine Pausenblende mit zwei Knöpfen: **Kamera fortsetzen** oder
**Zurück ins Hauptmenü**. Der Bauverlauf bleibt erhalten, du machst da weiter, wo
du warst. Kommst du zurück und die Kamera läuft noch, geht es ohne Nachfrage
weiter. Beides geht auch per Zuruf.

Im Hintergrund analysiert die App nicht weiter — das spart Freikontingent und Akku.

### Maßstab: warum der Kopfdurchmesser zählt

Millimeter aus einem Foto zu schätzen ist Raterei, solange nichts im Bild eine
bekannte Größe hat. Deshalb kannst du den **Außendurchmesser des Kopfes** angeben
— quer über den oberen Rand gemessen. Die App gibt ihn dem Modell als Maßstab
mit, das rechnet Entfernungen dann im Verhältnis dazu aus statt frei zu schätzen.

Wichtig ist, dass Maß und Beschriftung zusammenpassen: bis Version 2 stand dort
„Innendurchmesser der Tabakmulde", die hinterlegten Werte waren aber die
Außenmaße der Köpfe. Dadurch kamen alle abgeleiteten Millimeter — Füllhöhe,
HMD-Abstand — systematisch rund ein Fünftel zu klein heraus. Jetzt heißt das
Feld, was drinsteht, und das Modell misst die Mulde selbst im Bild aus.

Gängige Köpfe stehen in `spec.json`; tippst du „Oblako Phunnel M", wird der Wert
vorgeschlagen. Deinen eigenen Kopf einmal ausmessen genügt, danach merkt die App
ihn sich.

### Wenn der Kopf nicht erkannt wird

Aus manchen Winkeln lässt sich ein Kopf nicht zuordnen — leer und von der Seite
erst recht nicht. Dann greift dein **Standardkopf** (voreingestellt Oblako
Phunnel M, änderbar unter *Modell und Zugang*). Die App schreibt in dem Fall
**angenommen** dazu:

```
Oblako Phunnel M · angenommen
```

Das ist bewusst sichtbar: eine Annahme ist keine Erkennung, und die Sicherheit
des Modells steigt dadurch nicht. Was du selbst angegeben hast, schlägt den
Standardkopf; was das Modell wirklich erkennt, schlägt beides.

Ein **leerer Kopf ist ein gültiger Kopf** — in der Phase „Kopf prüfen" ist er
das ja immer. Früher hat das Modell dort gern „kein Kopf im Bild" gemeldet, weil
im Prompt nur von einem Blickwinkel und von sichtbarem Tabak die Rede war. Beides
ist korrigiert: die Erkennung ist jetzt für Aufsicht, Schrägsicht und Seitenansicht
beschrieben, und abgebrochen wird nur, wenn wirklich nichts Kopfartiges im Bild
ist.

### Endurteil aus drei Blickwinkeln

Von schräg oben bleibt die hintere Randkante verdeckt — genau dort entsteht
Randanbrand. Für die Vollanalyse führt dich die App deshalb durch drei Aufnahmen
(„Bild 1 von 3 … jetzt drehen"), die gemeinsam an das Modell gehen. Abschaltbar
auf dem Startbildschirm, dann reicht ein Bild.

### Nachmessen

Nach der Korrektur ein Tipp auf **Nachmessen**: die App bewertet neu und stellt
gegenüber, was sich geändert hat — pro Kategorie, mit Verschlechterungen. Dazu
der Abgleich mit der eigenen Prognose:

```
72 → 84 (+12)
Tabakverteilung   61 → 88 (+27)
Airflow           70 → 63 (−7)
Vorhergesagt waren 86 — gut getroffen.
```

### Gegenprobe mit einem zweiten Modell

Optional wird dasselbe Bild von einem zweiten Anbieter beurteilt. Liegen die
Urteile weit auseinander, ist die Sache nicht so klar, wie eine einzelne Zahl
aussieht — dann setzt die App die Sicherheit herunter und benennt die strittigen
Kategorien. Das ist eine gemessene Konfidenz statt einer behaupteten. Kostet die
doppelte Anfrage, deshalb standardmäßig aus.

### Wie bewertet wird

Die Note ist keine Bauchentscheidung des Modells — die App rechnet sie fest aus
sieben gewichteten Kategorien:

| Kategorie | Gewicht | Was zählt |
|---|---|---|
| Tabakverteilung | 20 % | Gleichmäßigkeit, Dichte, Klumpen, Lücken |
| Hitzemanagement | 20 % | HMD- und Kohleposition, Kontakt, Hotspots |
| Füllhöhe | 15 % | Höhe, Abstand zum HMD, Über-/Unterfüllung |
| Airflow | 15 % | freie Luftwege, zentrale Öffnung, Blockaden |
| Kopfgeometrie | 10 % | passt der Aufbau zur Kopfform |
| Tabakkompatibilität | 10 % | passt die Packweise zur Tabakart |
| Zielerreichung | 10 % | passt der Aufbau zu deinem Ziel |

Das Modell darf die Gesamtnote nicht selbst setzen — damit dasselbe Bild
reproduzierbar dieselbe Note bekommt, egal welcher Anbieter antwortet. Ebenso wird
nachgeprüft: unbekannte Aktionen werden ersetzt, Markierungen außerhalb des Bildes
fliegen raus, eine Prognose darf nie schlechter sein als der Ist-Stand, und ohne
erkennbaren Kopf gibt es gar keine Note statt einer erfundenen.

**Widersprüche werden gekappt.** Modelle neigen dazu, ein kritisches Problem zu
melden und die betroffene Kategorie trotzdem mit 80 zu bewerten. Wer sagt „der
Tabak berührt das HMD", darf das Hitzemanagement nicht gut nennen. Solche Fälle
setzt die App herunter — und schreibt im Report dazu, warum:

```
Fuellhoehe: 70 → 40, weil Tabak beruehrt den Rand
```

Die Obergrenzen stehen in `spec.json` unter `plausibilitaet`.

**Erst sehen, dann urteilen.** Das Modell füllt zuerst ein Feld `befund` aus —
was es tatsächlich im Bild sieht, ohne Wertung — und bewertet erst danach. Der
Befund steht im Report, du kannst also nachlesen, worauf die Note beruht.

**Ein Bild ist kein Urteil.** Ein Einzelbild schwankt: eine Spiegelung, ein
anderer Winkel, und die Zahl liegt fünf Punkte daneben. Angezeigt wird deshalb
der Median der letzten fünf brauchbaren Bilder, mit Trendpfeil. Bilder, die zu
unscharf, zu dunkel oder zu unsicher sind, gelten als **vorläufig** und zählen
für den Konsens nicht mit. Schwankt der Wert noch, wird die Zahl gelb statt
cyan.

Jede Angabe ist als **gesehen**, **geschätzt** oder **unsicher** gekennzeichnet.
Die Tabakmenge in Gramm ist aus einem Foto nicht bestimmbar — sie wird deshalb nie
als Tatsache behauptet.

### Ziele und Angaben

Vor dem Start wählst du ein Ziel: **ausgewogen**, **Geschmack**, **Rauch** oder
**lange Session**. Danach wird bewertet. Wenn du Kopfmodell, Tabak, HMD und
Kohlenanzahl angibst, wird die Analyse deutlich genauer — fehlt etwas Wichtiges,
fragt die App gezielt nach einer einzigen Angabe. Die Angaben merkt sie sich.

### Aufs iPhone bringen

Die App braucht eine feste Adresse mit HTTPS. Der einfachste Weg ist GitHub Pages,
kostenlos und dauerhaft:

1. Auf den Standard-Branch pushen. Der Workflow
   `.github/workflows/analyzer-pages.yml` schaltet Pages ein und veröffentlicht
   den Ordner `web/shisha`. Die Adresse steht danach unter **Settings → Pages**,
   in der Form `https://<name>.github.io/<repo>/`.
2. Adresse am iPhone in Safari öffnen → **Teilen → Zum Home-Bildschirm**.
3. App öffnen → **Modell und Zugang** → Anbieter wählen, Schlüssel einfügen,
   speichern.
4. **Kamera starten** — Zugriff erlauben. Fertig.

Klappt das Einschalten nicht automatisch, einmal von Hand: **Settings → Pages →
Source: GitHub Actions**, dann den Workflow unter *Actions* neu starten.

> GitHub Pages setzt voraus, dass das Repository **öffentlich** ist — für private
> Repos braucht es einen bezahlten Tarif. Soll das Repo privat bleiben, tut es
> **Cloudflare Pages** oder **Netlify** genauso: kostenloses Konto, Repo verbinden,
> als Ausgabeordner `web/shisha` angeben, kein Build-Befehl.
>
> Öffentlich heißt: der Code ist lesbar, nicht deine Zugänge. Die `.env` ist von
> `.gitignore` ausgeschlossen und war nie im Repository. Auch die veröffentlichte
> App enthält keinen Schlüssel — den trägt jeder Nutzer auf seinem eigenen Gerät
> ein, sonst tut sie nichts.

Ab dann läuft die App auch unterwegs — Mobilfunk reicht, der Rechner kann aus sein.
Der Rahmen der App liegt im Cache, sie startet also auch ohne Netz; für die Analyse
selbst braucht sie natürlich Verbindung.

**Ohne Hoster, nur im Heimnetz:** `start-shisha.bat` bzw. `./start-shisha.sh` auf
dem Rechner starten und die angezeigte `https://…`-Adresse am iPhone öffnen. Safari
warnt einmal wegen des selbst ausgestellten Zertifikats — **Details → Diese Website
besuchen**. Der Rechner muss dabei laufen.

### Bedienung

| Element | Bedeutung |
|---|---|
| Leiste oben | Bauphasen — antippen springt direkt dahin |
| Zahl oben rechts | aktuelle Note, sobald ein Kopf erkannt ist |
| Kreis im Bild | Zielbereich; halte den Kopf von oben hinein |
| Kästen im Bild | AR-Markierungen mit Handlungsanweisung |
| 🔊 | Sprachausgabe an/aus |
| Phase weiter | nächste Bauphase erzwingen |
| Vollanalyse | ausführlichen Report mit Optimierungsplan erzeugen |
| ↺ | neuen Kopf anfangen |

Analysiert wird nur, wenn das Bild ruhig, scharf und hell genug ist — sonst steht
oben „halt still", „unscharf" oder „zu dunkel" und es wird nichts verschickt. Das
spart Kontingent und verhindert Fehlurteile aus verwackelten Bildern. Wartet die
App zu lange, wird sie nachsichtiger und schickt am Ende trotzdem (siehe „Wenn es
ewig scannt").

### Regeln ändern

Alles Fachliche steht in **`web/shisha/spec.json`**: Gewichte, Bauphasen,
Zielprofile, das Wissen über Kopftypen und Tabakphysik, die Prompts. Eine
Textdatei, eine einzige Quelle — gelesen wird sie von der App im Browser. Die
Python-Seite kennt die Regeln nicht und braucht sie auch nicht: sie reicht Bild
und Prompt nur weiter. Ändern, pushen, fertig.

### Prüfen, ob alles stimmt

```
node tests/test_engine.mjs     # Bewertungslogik, Maßstab, Lernregeln, Gegenprobe
node tests/test_steuerung.mjs  # Kamera-Lage, Pause, Sprachbefehle, Marker
node tests/test_durchlauf.mjs  # kompletter Durchgang durch die App (~1 Minute)
python tests/test_shisha.py    # Proxy auf dem PC: Losungswort, Grenzen, CLI-Aufruf
```

Die Tests füttern absichtlich fehlerhafte Modellantworten ein und prüfen, dass sie
geradegezogen werden. `steuerung.js` enthält bewusst nur Entscheidungen ohne
Kamera und ohne Oberfläche — genau die Stellen, die in der Praxis Ärger gemacht
haben, sind dadurch überhaupt erst testbar.

**Der Durchgang** ist der interessanteste davon: er lädt die drei Skripte in einen
nachgebauten Browser (`tests/dom-ersatz.mjs`) und bedient die App wie ein Mensch —
Zugang einrichten, Kamera starten, Livebetrieb, Vollanalyse aus drei Winkeln,
Report, Nachmessen, Teilen, Feedback, Pause, Sprachbefehle, Hauptmenü. Danach die
Störfälle: Müll-Antwort, Kontingent erschöpft, kein Kopf im Bild, tote Kamera,
Wechsel in den Hintergrund.

Gefunden hat er damit zwei Fehler, die alle anderen Tests durchgelassen haben:
zwei parallel laufende Analyseschleifen nach einem Hintergrundwechsel (doppelter
Kontingentverbrauch) und eine Vollanalyse, die bei toter Kamera eine halbe Minute
ins Leere lief.

---

## Struktur

```
jarvis/
  config.py            Einstellungen aus .env
  server.py            FastAPI: HUD, WebSocket, WhatsApp-Webhook, Mikrofon
  memory.py            Obsidian-Anbindung: Gedächtnis, Aufgaben, Protokoll, Suche
  mcp_server.py        MCP-Server, der die Werkzeuge für claude -p bereitstellt
  orchestrator.py      Nacht-Aufträge aus Markdown
  engine/
    base.py            Gemeinsames Interface und Persona
    cli_backend.py     claude -p (Standard)
    api_backend.py     Anthropic-API (Alternative)
  audio/
    stt.py             faster-whisper       tts.py       Piper
    listener.py        Wake-Word + Aufnahme bis zur Sprechpause
  channels/
    whatsapp.py        Meta Cloud API
  tools/               PC-Steuerung, Dateien, Aufgaben, Gedächtnis
shisha/                Optionaler Rückweg über den PC (Claude-Abo)
  analyse.py           Bild und Prompt an claude -p oder die API
  server.py            Proxy und Auslieferung der App
  zertifikat.py        Selbst ausgestelltes HTTPS-Zertifikat fürs Heimnetz
web/                   Das HUD
web/shisha/            Die Kamera-App fürs iPhone
  spec.json            Regelwerk: Gewichte, Wissen, Prompts, Köpfe (einzige Quelle)
  engine.js            Prompt bauen, Modell fragen, Antwort prüfen, Note rechnen
  steuerung.js         Entscheidungen ohne Kamera und Oberfläche (testbar)
  ar.js                Kamera, AR-Overlay, Bedienung
  sw.js                hält die App offline startbereit
scripts/               Startskripte und Desktop-Verknüpfungen
tests/                 Tests für Engine und Rückweg
templates/             Vorlagen für Nacht-Aufträge
```

---

## Probleme

**„Claude Code wurde nicht gefunden"** — `npm install -g @anthropic-ai/claude-code`,
dann einmal `claude` starten und anmelden. Liegt es woanders, setz `JARVIS_CLAUDE_BIN`.

**„Wake-Word reagiert nicht"** — Steht im Terminal „Mikrofon aktiv"? Wenn nicht, ist
kein Mikrofon als Standardgerät gesetzt. `JARVIS_WAKEWORD_THRESHOLD=0.35` macht ihn
empfindlicher.

**„Jarvis antwortet, aber sagt nichts"** — Die Piper-Stimme konnte nicht geladen
werden, der Grund steht im Terminal. Meist hilft ein Neustart (Download war
abgebrochen) oder eine andere Stimme.

**„WhatsApp kommt nicht an"** — Läuft der Tunnel noch? Die Adresse ändert sich bei
jedem Neustart und muss dann bei Meta neu eingetragen werden. Ist das Feld
`messages` abonniert? Steht deine Nummer in `WHATSAPP_ALLOWED_NUMBERS` ohne `+`
oder Leerzeichen? Ist das Token noch gültig (temporäre laufen nach 24 h ab)?

**„Alles ist langsam"** — `JARVIS_WHISPER_MODEL=base` und `JARVIS_CLI_MODEL=haiku`.
Mit NVIDIA-Karte: `JARVIS_WHISPER_DEVICE=cuda`.

**„Er hört sich selbst zu"** — Passiert bei Lautsprechern ohne Echounterdrückung.
Kopfhörer benutzen, oder `JARVIS_SPEAK_LOCALLY=false`.

**„Kamera startet nicht am iPhone"** — Die Seite muss über `https://` laufen. Über
`http://` mit IP-Adresse verweigert Safari den Kamerazugriff grundsätzlich.

**„Freikontingent gerade erschöpft"** — Das Limit des kostenlosen Modells greift.
Kurz warten, oder in den Einstellungen einen anderen Anbieter wählen.

**„Modell nicht gefunden"** — Der Modellname stimmt nicht mehr. Kostenlose Modelle
werden bei OpenRouter regelmäßig ausgetauscht; auf der Anbieterseite nachsehen und
den Namen in den Einstellungen korrigieren.

**„Schlüssel wird abgelehnt"** — Schlüssel abgelaufen oder falsch kopiert. Beim
Einfügen auf Leerzeichen am Anfang und Ende achten.

**„Zertifikat wird nicht akzeptiert"** (nur im Heimnetz-Betrieb) — Fehlt das Paket
`cryptography`? Dann `pip install cryptography`. Oder gleich über GitHub Pages
gehen, das bringt ein echtes Zertifikat mit.

**„Er sieht keinen Kopf"** — Von schräg oben in den Kopf halten, sodass die ganze
Tabakfläche im Bild ist, und für Licht sorgen. Steht oben „Bild zu schlecht",
liegt es an Schärfe oder Beleuchtung, nicht am Kopf.
