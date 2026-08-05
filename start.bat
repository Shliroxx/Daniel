@echo off
REM ===== JARVIS starten (Windows) =====
cd /d "%~dp0"

if not exist ".venv" (
    echo [1/3] Erstelle virtuelle Umgebung...
    python -m venv .venv
    call .venv\Scripts\activate.bat
    echo [2/3] Installiere Abhaengigkeiten ^(dauert beim ersten Mal ein paar Minuten^)...
    python -m pip install --upgrade pip
    pip install -r requirements.txt
) else (
    call .venv\Scripts\activate.bat
)

if not exist ".env" (
    echo.
    echo !! Es gibt noch keine .env — kopiere .env.example nach .env
    echo    und trage deinen ANTHROPIC_API_KEY ein.
    echo.
    copy .env.example .env >nul
    notepad .env
)

echo [3/3] Starte Jarvis...
echo.
python -m jarvis.server
pause
