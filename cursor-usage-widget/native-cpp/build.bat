@echo off
setlocal
call "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat" >nul
cd /d "%~dp0"
rc /nologo /fo usage-mark.res usage-mark.rc
if errorlevel 1 exit /b 1
cl /nologo /O2 /EHsc /utf-8 /DUNICODE /D_UNICODE /W3 CursorUsage.cpp usage-mark.res /Fe:CursorUsage.exe /link /SUBSYSTEM:WINDOWS user32.lib gdi32.lib gdiplus.lib winhttp.lib shell32.lib ole32.lib oleaut32.lib dwmapi.lib uuid.lib advapi32.lib
if errorlevel 1 exit /b 1
echo Built %~dp0CursorUsage.exe
