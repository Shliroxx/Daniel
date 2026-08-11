' Beendet Jarvis. Steckt hinter der Verknuepfung "Jarvis beenden".

Option Explicit
Dim fs, shell, wurzel, pidDatei, pid

Set fs = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
wurzel = fs.GetParentFolderName(fs.GetParentFolderName(WScript.ScriptFullName))
pidDatei = wurzel & "\data\jarvis.pid"

If Not fs.FileExists(pidDatei) Then
  MsgBox "Jarvis laeuft gerade nicht.", 64, "Jarvis"
  WScript.Quit 0
End If

pid = Trim(fs.OpenTextFile(pidDatei).ReadAll())
If IsNumeric(pid) Then
  ' Nur genau diesen Prozess beenden, nicht alles was Python heisst.
  shell.Run "taskkill /PID " & pid & " /T /F", 0, True
End If
If fs.FileExists(pidDatei) Then fs.DeleteFile(pidDatei)
