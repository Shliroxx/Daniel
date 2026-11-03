@echo off
REM ===== Kombo-Makro fuer Zenkai / Nanos World starten (Windows) =====
REM Laeuft das Spiel als Administrator, diese Datei ebenfalls als Administrator starten.
cd /d "%~dp0"

if not exist .venv (
  echo [1/3] Erstelle virtuelle Umgebung ...
  python -m venv .venv || goto :fehler
)
call .venv\Scripts\activate.bat

python -c "import pynput" 2>nul || (
  echo [2/3] Installiere pynput ...
  pip install -r kombo\requirements.txt || goto :fehler
)

if not exist kombo.json (
  python -m kombo --beispiel
  echo.
  echo kombo.json wurde angelegt - Tasten an deine Belegung anpassen und neu starten.
  notepad kombo.json
  pause
  exit /b 0
)

echo [3/3] Starte Makro ...  F8 = Pause, F9 = Stopp, F10 = Beenden
python -m kombo %*
pause
exit /b 0

:fehler
echo Einrichtung fehlgeschlagen - ist Python 3.10+ installiert und im PATH?
pause
exit /b 1
