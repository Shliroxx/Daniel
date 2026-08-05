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
    echo !! Es gibt noch keine .env — ich habe .env.example kopiert.
    echo    Schau sie kurz durch: Vault-Pfad, und falls du die API statt
    echo    deines Claude-Abos nutzen willst, JARVIS_BACKEND=api plus Key.
    echo.
    copy .env.example .env >nul
    notepad .env
)

REM Claude Code pruefen (Standard-Backend)
where claude >nul 2>nul
if errorlevel 1 (
    echo.
    echo !! Claude Code wurde nicht gefunden.
    echo    Installiere es mit:  npm install -g @anthropic-ai/claude-code
    echo    und melde dich danach einmal mit  claude  an.
    echo    Alternativ setze JARVIS_BACKEND=api in der .env.
    echo.
)

echo [3/3] Starte Jarvis...
echo.
python -m jarvis.server
pause
