@echo off
REM ===== Jarvis einrichten =====
REM Einmal ausfuehren. Danach startest du Jarvis ueber das Desktop-Symbol —
REM ohne Konsole, ohne Server von Hand.
chcp 65001 >nul
cd /d "%~dp0"
title Jarvis einrichten

echo.
echo   JARVIS EINRICHTEN
echo   =================
echo.

where python >nul 2>&1
if errorlevel 1 (
  echo   [!] Python wurde nicht gefunden.
  echo       Installiere es von python.org und hake dabei
  echo       "Add Python to PATH" an. Danach diese Datei nochmal starten.
  echo.
  pause
  exit /b 1
)

if not exist .venv (
  echo   [1/4] Virtuelle Umgebung anlegen ...
  python -m venv .venv
) else (
  echo   [1/4] Virtuelle Umgebung ist schon da.
)

echo   [2/4] Abhaengigkeiten installieren ^(beim ersten Mal dauert das^) ...
call .venv\Scripts\activate.bat
python -m pip install --upgrade pip --quiet
pip install -r requirements.txt --quiet
if errorlevel 1 (
  echo   [!] Installation fehlgeschlagen. Meldung steht oben.
  pause
  exit /b 1
)

if not exist .env (
  copy .env.example .env >nul
  echo   [3/4] .env angelegt — schau spaeter mal rein ^(Vault-Pfad^).
) else (
  echo   [3/4] .env ist schon da.
)

echo   [4/4] Desktop-Verknuepfungen anlegen ...
set AUTOSTART=
choice /C JN /N /M "        Soll Jarvis beim Anmelden automatisch starten? [J/N] "
if errorlevel 2 goto :ohne
set AUTOSTART=-Autostart
:ohne
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\verknuepfungen.ps1" %AUTOSTART%

where claude >nul 2>&1
if errorlevel 1 (
  echo.
  echo   [!] Claude Code fehlt noch. Damit Jarvis denken kann:
  echo         npm install -g @anthropic-ai/claude-code
  echo       danach einmal  claude  starten und anmelden.
)

echo.
echo   Fertig. Auf dem Desktop liegt jetzt "Jarvis".
echo   Doppelklick startet ihn und oeffnet das HUD.
echo.
pause
