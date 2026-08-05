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

### 2. Starten

Doppelklick auf **`start.bat`** (Windows) bzw. `./start.sh` (macOS/Linux).

Beim ersten Mal wird alles installiert und eine `.env` angelegt. Schau kurz rein —
vor allem `JARVIS_VAULT` sollte auf deinen Obsidian-Ordner zeigen.

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
web/                   Das HUD
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
