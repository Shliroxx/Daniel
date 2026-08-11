# Legt die Desktop-Verknuepfungen fuer Jarvis an.
# Wird von "Jarvis einrichten.bat" aufgerufen, laesst sich aber auch einzeln starten.

param([switch]$Autostart)

$wurzel  = Split-Path -Parent $PSScriptRoot
$desktop = [Environment]::GetFolderPath('Desktop')
$icon    = Join-Path $wurzel 'web\jarvis.ico'
$shell   = New-Object -ComObject WScript.Shell

function Neu($ziel, $skript, $beschreibung) {
    $link = $shell.CreateShortcut($ziel)
    $link.TargetPath       = "$env:SystemRoot\System32\wscript.exe"
    $link.Arguments        = '"' + (Join-Path $wurzel "scripts\$skript") + '"'
    $link.WorkingDirectory = $wurzel
    $link.Description      = $beschreibung
    if (Test-Path $icon) { $link.IconLocation = $icon }
    $link.Save()
    Write-Host "  angelegt: $ziel"
}

Neu (Join-Path $desktop 'Jarvis.lnk')          'jarvis-start.vbs' 'Jarvis starten und HUD oeffnen'
Neu (Join-Path $desktop 'Jarvis beenden.lnk')  'jarvis-stop.vbs'  'Jarvis beenden'

if ($Autostart) {
    $autostartOrdner = [Environment]::GetFolderPath('Startup')
    Neu (Join-Path $autostartOrdner 'Jarvis.lnk') 'jarvis-start.vbs' 'Jarvis beim Anmelden starten'
    Write-Host "  Jarvis startet ab jetzt automatisch beim Anmelden."
}
