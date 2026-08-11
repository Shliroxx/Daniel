#!/usr/bin/env bash
# Jarvis starten (macOS) — diese Datei laesst sich ins Dock legen.
# Rechtsklick -> Oeffnen beim ersten Mal, danach reicht ein Doppelklick.
cd "$(dirname "$0")/.."

if [ ! -d .venv ]; then
  osascript -e 'display alert "Jarvis ist noch nicht eingerichtet" message "Fuehr einmal ./start.sh im Projektordner aus."' 2>/dev/null
  echo "Jarvis ist noch nicht eingerichtet — bitte einmal ./start.sh ausfuehren."
  exit 1
fi

source .venv/bin/activate
port="$(grep -E '^JARVIS_PORT=' .env 2>/dev/null | cut -d= -f2 | tr -d ' ' | cut -d'#' -f1)"
port="${port:-8765}"

python -m jarvis.server &
sleep 5
open "http://localhost:${port}"
wait
