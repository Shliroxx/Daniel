#!/usr/bin/env bash
# ===== Shisha-Analyzer starten (macOS / Linux) =====
# Startet nur den Kamera-Coach — ohne Mikrofon, ohne WhatsApp.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d .venv ]; then
  echo "[1/2] Erstelle virtuelle Umgebung ..."
  python3 -m venv .venv
  source .venv/bin/activate
  python -m pip install --upgrade pip
  pip install -r requirements.txt
else
  source .venv/bin/activate
fi

[ -f .env ] || cp .env.example .env

echo "[2/2] Starte den Analyzer ..."
echo "      Am iPhone die gleich angezeigte https-Adresse oeffnen."
exec python -m shisha
