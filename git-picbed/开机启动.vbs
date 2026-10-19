Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
root = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = root
exe = root & "\GitPicbed.exe"
If fso.FileExists(exe) Then
  shell.Run """" & exe & """ --silent", 0, False
Else
  ps1 = root & "\隐藏启动.ps1"
  shell.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & ps1 & """ --silent", 0, False
End If
