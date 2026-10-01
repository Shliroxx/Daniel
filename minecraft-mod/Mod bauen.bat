@echo off
REM Baut Kingdom Omnitrix. Ergebnis: build\libs\kingdomomnitrix-<version>.jar
REM Benoetigt Java 21 (z. B. https://adoptium.net).
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
    echo Java wurde nicht gefunden. Bitte Java 21 installieren: https://adoptium.net
    pause
    exit /b 1
)
call gradlew.bat build
if errorlevel 1 (
    echo.
    echo Build fehlgeschlagen - siehe Meldungen oben.
    pause
    exit /b 1
)
echo.
echo Fertig. Die Mod-Datei liegt in build\libs\ - die Datei OHNE "-sources" in den mods-Ordner kopieren.
pause
