Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
root = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = root
shell.Environment("PROCESS")("PICBED_NO_BROWSER") = "1"
node = "D:\Softs\nvm4w\nodejs\node.exe"
If Not fso.FileExists(node) Then node = "node"
shell.Run """" & node & """ server.js", 0, False
