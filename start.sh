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
  echo "   Schau sie kurz durch (Vault-Pfad, ggf. WhatsApp) und starte neu."
  exit 1
fi

if ! command -v claude >/dev/null 2>&1; then
  echo
  echo "!! Claude Code wurde nicht gefunden."
  echo "   Installiere es mit:  npm install -g @anthropic-ai/claude-code"
  echo "   und melde dich danach einmal mit  claude  an."
  echo "   Alternativ setze JARVIS_BACKEND=api in der .env."
  echo
fi

echo "[3/3] Starte Jarvis ..."
exec python -m jarvis.server
