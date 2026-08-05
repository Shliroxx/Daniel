#!/usr/bin/env bash
# ===== JARVIS starten (macOS / Linux) =====
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d .venv ]; then
  echo "[1/3] Erstelle virtuelle Umgebung ..."
  python3 -m venv .venv
  source .venv/bin/activate
  echo "[2/3] Installiere Abhaengigkeiten (dauert beim ersten Mal ein paar Minuten) ..."
  python -m pip install --upgrade pip
  pip install -r requirements.txt
else
  source .venv/bin/activate
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo
  echo "!! Es gibt noch keine .env — ich habe .env.example kopiert."
  echo "   Trage jetzt deinen ANTHROPIC_API_KEY in .env ein und starte neu."
  exit 1
fi

echo "[3/3] Starte Jarvis ..."
exec python -m jarvis.server
