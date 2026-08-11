' Startet Jarvis unsichtbar im Hintergrund und oeffnet danach das HUD.
' Diese Datei steckt hinter der Desktop-Verknuepfung — kein Konsolenfenster,
' kein Server von Hand starten.

Option Explicit
Dim fs, shell, wurzel, pythonw, port, zeile, datei

Set fs = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")

wurzel = fs.GetParentFolderName(fs.GetParentFolderName(WScript.ScriptFullName))
pythonw = wurzel & "\.venv\Scripts\pythonw.exe"

If Not fs.FileExists(pythonw) Then
  MsgBox "Jarvis ist noch nicht eingerichtet." & vbCrLf & vbCrLf & _
         "Fuehr einmal 'Jarvis einrichten.bat' im Projektordner aus.", 48, "Jarvis"
  WScript.Quit 1
End If

' Laeuft er schon? Dann nur das HUD holen.
If fs.FileExists(wurzel & "\data\jarvis.pid") Then
  If ProzessLaeuft(Trim(fs.OpenTextFile(wurzel & "\data\jarvis.pid").ReadAll())) Then
    shell.Run "http://localhost:" & PortLesen(wurzel), 1, False
    WScript.Quit 0
  End If
End If

port = PortLesen(wurzel)
shell.CurrentDirectory = wurzel
shell.Run """" & pythonw & """ -m jarvis.server", 0, False

' Kurz warten, bis der Server steht, dann das HUD oeffnen.
WScript.Sleep 5000
shell.Run "http://localhost:" & port, 1, False

' --- Hilfsfunktionen --------------------------------------------------------

Function PortLesen(ordner)
  PortLesen = "8765"
  If Not fs.FileExists(ordner & "\.env") Then Exit Function
  Set datei = fs.OpenTextFile(ordner & "\.env", 1)
  Do Until datei.AtEndOfStream
    zeile = Trim(datei.ReadLine())
    If Left(zeile, 12) = "JARVIS_PORT=" Then
      PortLesen = Trim(Split(Mid(zeile, 13) & "#", "#")(0))
    End If
  Loop
  datei.Close
End Function

Function ProzessLaeuft(pid)
  Dim wmi, treffer
  ProzessLaeuft = False
  If Not IsNumeric(pid) Then Exit Function
  Set wmi = GetObject("winmgmts:\\.\root\cimv2")
  Set treffer = wmi.ExecQuery("SELECT ProcessId FROM Win32_Process WHERE ProcessId = " & pid)
  ProzessLaeuft = (treffer.Count > 0)
End Function
