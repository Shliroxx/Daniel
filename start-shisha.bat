@echo off
REM ===== Shisha-Analyzer starten (Windows) =====
REM Startet nur den Kamera-Coach - ohne Mikrofon, ohne WhatsApp.
cd /d "%~dp0"

if not exist .venv (
  echo [1/2] Erstelle virtuelle Umgebung ...
  python -m venv .venv
  call .venv\Scripts\activate.bat
  python -m pip install --upgrade pip
  pip install -r requirements.txt
) else (
  call .venv\Scripts\activate.bat
)

if not exist .env copy .env.example .env >nul

echo [2/2] Starte den Analyzer ...
echo       Am iPhone die gleich angezeigte https-Adresse oeffnen.
python -m shisha
pause
