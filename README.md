# J.A.R.V.I.S.

Ein persönlicher KI-Assistent mit Wake-Word, echter PC-Steuerung und Iron-Man-HUD.
Läuft als kleiner Server auf deinem Windows-PC — bedienbar im Browser am PC **und**
vom iPhone aus im selben WLAN.

```
"Hey Jarvis"  →  Whisper (lokal)  →  Claude + Werkzeuge  →  Piper (lokal)  →  Lautsprecher
                                          ↕
                              HUD im Browser / auf dem iPhone
```

**Spracherkennung und Sprachausgabe laufen komplett offline auf deinem Rechner.**
Nur das Denken (Claude) geht ins Netz — dafür brauchst du einen API-Key.

---

## Was Jarvis kann

| Bereich | Fähigkeiten |
|---|---|
| **Sprache** | Wake-Word „Hey Jarvis", Dauerbetrieb am PC-Mikro, Push-to-Talk im Browser, deutsche Sprachausgabe |
| **PC steuern** | Programme starten, Webseiten öffnen, Lautstärke, Screenshots, Systeminfos, Prozessliste, Bildschirm sperren |
| **Dateien** | Lesen, schreiben, suchen — streng auf dein Arbeitsverzeichnis begrenzt |
| **Web & Wissen** | Websuche und Seitenabruf (läuft serverseitig bei Anthropic, kein extra Key) |
| **Aufgaben** | To-do-Liste anlegen, abhaken, löschen — bleibt über Neustarts erhalten |
| **Gedächtnis** | Merkt sich dauerhaft Fakten über dich und zieht sie in spätere Gespräche |
| **Brainstorming** | Als Sparringspartner mit eigener Meinung, nicht als Ja-Sager |

---

## Einrichtung (Windows)

**1. Voraussetzungen**

- [Python 3.11 oder neuer](https://www.python.org/downloads/) — beim Installieren
  **„Add Python to PATH"** anhaken
- Einen API-Key von [console.anthropic.com](https://console.anthropic.com)
- Optional, aber empfohlen: [ffmpeg](https://ffmpeg.org/download.html) im PATH
  (für Audio vom iPhone)

**2. Starten**

Doppelklick auf **`start.bat`**. Beim ersten Mal:

- wird eine virtuelle Umgebung angelegt und alles installiert (dauert ein paar Minuten)
- öffnet sich der Editor mit der `.env` → trage dort deinen `ANTHROPIC_API_KEY` ein,
  speichern, schließen
- beim ersten Start lädt Jarvis automatisch das Whisper-Modell, das Wake-Word-Modell
  und die deutsche Piper-Stimme herunter (einmalig, ca. 1 GB)

**3. Benutzen**

Browser öffnen: **http://localhost:8765**

Dann einfach *„Hey Jarvis, wie ist meine CPU-Auslastung?"* sagen.

Auf macOS/Linux stattdessen `./start.sh`.

---

## Vom iPhone aus benutzen

Jarvis lauscht auf allen Netzwerk-Adressen. Du brauchst nur die lokale IP deines PCs:

```
ipconfig        # Windows: "IPv4-Adresse" suchen, z. B. 192.168.1.42
```

Dann am iPhone im Safari aufrufen:

```
http://192.168.1.42:8765
```

Über **Teilen → Zum Home-Bildschirm** wird daraus eine App mit eigenem Icon.

Am iPhone gibt es kein Wake-Word — dort hältst du den runden Mikrofon-Knopf gedrückt
und sprichst (Push-to-Talk). Die Aufnahme wird an deinen PC geschickt, dort
transkribiert und beantwortet. Die Sprachausgabe kommt auch aufs Handy.

> **Hinweis:** iOS erlaubt Mikrofonzugriff im Browser nur über `localhost` oder HTTPS.
> Wenn der Mikrofon-Knopf nicht funktioniert, nutze am iPhone die Texteingabe — oder
> stelle Jarvis hinter ein HTTPS-Proxy (z. B. Tailscale oder Caddy mit lokalem Zertifikat).

---

## Bedienung im HUD

| Element | Funktion |
|---|---|
| Reaktor in der Mitte | Zustand: grau = bereit, cyan = hört zu, grün = nimmt auf, orange = denkt |
| **Mikrofon-Knopf** | Gedrückt halten und sprechen (am PC auch: **Leertaste** halten) |
| **Texteingabe** | Tippen statt sprechen — Antwort wird trotzdem vorgelesen |
| **Verlauf** (links) | Was gesagt und geantwortet wurde |
| **Aktivität** (rechts) | Live-Einblick: welche Werkzeuge Jarvis gerade benutzt, wie er denkt |
| **Stopp** | Bricht die Sprachausgabe ab |
| **Neu starten** | Leert den Gesprächsverlauf (Gedächtnis und Aufgaben bleiben) |

---

## Konfiguration (`.env`)

| Einstellung | Bedeutung |
|---|---|
| `ANTHROPIC_API_KEY` | Dein Key — ohne den geht nichts |
| `JARVIS_EFFORT` | `low` = schnellste Antworten, `medium` = ausgewogen, `high` = klügste Antworten |
| `JARVIS_WHISPER_MODEL` | `tiny`/`base` = schnell auf schwacher Hardware, `small` = guter Standard, `medium`/`large-v3` = beste Erkennung |
| `JARVIS_WHISPER_DEVICE` | `auto`, `cpu` oder `cuda` (mit NVIDIA-Grafikkarte deutlich schneller) |
| `JARVIS_WAKEWORD_THRESHOLD` | Niedriger = löst leichter aus, höher = weniger Fehlauslösungen |
| `JARVIS_MIC_ENABLED` | `false`, wenn Jarvis nicht dauerhaft mithören soll |
| `JARVIS_SPEAK_LOCALLY` | `false`, wenn nur das Handy sprechen soll, nicht der PC |
| `JARVIS_PIPER_VOICE` | Andere Stimme, z. B. `de_DE-kerstin-low` oder `de_DE-eva_k-x_low` |
| `JARVIS_WORKSPACE` | Ordner, in dem Jarvis Dateien lesen/schreiben darf |
| `JARVIS_ALLOW_SHELL` | `true` erlaubt beliebige Shell-Befehle — **nur wenn du weißt, was du tust** |

---

## Sicherheit

Jarvis hat echten Zugriff auf deinen Rechner. Deshalb:

- **Dateizugriff ist eingesperrt.** Nur der Ordner aus `JARVIS_WORKSPACE`, jeder Pfad
  wird vorher aufgelöst und geprüft — kein Ausbrechen über `..` oder absolute Pfade.
- **Shell-Befehle sind standardmäßig komplett gesperrt.**
- **Der Server ist nicht passwortgeschützt.** Betreibe ihn nur in deinem eigenen WLAN
  und gib den Port niemals nach außen ins Internet frei.
- Dein API-Key steht in `.env` — die Datei ist per `.gitignore` vom Repo ausgeschlossen.

---

## Struktur

```
jarvis/
  config.py            Einstellungen aus .env
  brain.py             Claude-Agentenschleife (Streaming, Werkzeuge, Gedächtnis)
  server.py            FastAPI: HUD, WebSocket, /api/stt, Mikrofon-Anbindung
  audio/
    stt.py             faster-whisper (Speech-to-Text)
    tts.py             Piper (Text-to-Speech) inkl. Auto-Download der Stimme
    listener.py        Wake-Word-Erkennung + Aufnahme bis zur Sprechpause
  tools/
    registry.py        Werkzeug-Register und Ausführung
    system.py          PC-Steuerung
    files.py           Dateizugriff (abgesichert)
    tasks.py           To-do-Liste
    memory.py          Langzeitgedächtnis
web/
  index.html hud.css hud.js    Das HUD
```

---

## Probleme

**„Wake-Word reagiert nicht"** — Prüfe im Terminal, ob „Mikrofon aktiv" erscheint.
Falls nicht, ist entweder kein Mikrofon als Standardgerät gesetzt oder `sounddevice`
fehlt. `JARVIS_WAKEWORD_THRESHOLD=0.35` macht es empfindlicher.

**„Jarvis antwortet, aber sagt nichts"** — Die Piper-Stimme konnte nicht geladen werden.
Im Terminal steht warum. Meist hilft ein Neustart (der Download war unterbrochen) oder
eine andere Stimme in `JARVIS_PIPER_VOICE`.

**„Alles ist langsam"** — `JARVIS_WHISPER_MODEL=base` und `JARVIS_EFFORT=low` setzen.
Mit NVIDIA-Karte: `JARVIS_WHISPER_DEVICE=cuda`.

**„Er hört sich selbst zu"** — Passiert bei Lautsprechern ohne Echounterdrückung.
Kopfhörer benutzen, oder `JARVIS_SPEAK_LOCALLY=false` und den Ton übers Handy ausgeben.
