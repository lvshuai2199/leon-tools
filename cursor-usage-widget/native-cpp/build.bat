@echo off
setlocal
set "VCVARS="
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" (
  for /f "usebackq delims=" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -find VC\Auxiliary\Build\vcvars64.bat`) do set "VCVARS=%%i"
)
if not defined VCVARS (
  for %%e in (Enterprise Professional Community BuildTools) do (
    if exist "C:\Program Files\Microsoft Visual Studio\2022\%%e\VC\Auxiliary\Build\vcvars64.bat" set "VCVARS=C:\Program Files\Microsoft Visual Studio\2022\%%e\VC\Auxiliary\Build\vcvars64.bat"
  )
)
if not defined VCVARS (
  echo Missing vcvars64.bat. Install Visual Studio 2022 with the Desktop C++ workload.
  exit /b 1
)
call "%VCVARS%" >nul
cd /d "%~dp0"
rc /nologo /fo usage-mark.res usage-mark.rc
if errorlevel 1 exit /b 1
cl /nologo /O2 /MT /EHsc /utf-8 /DUNICODE /D_UNICODE /W3 CursorUsage.cpp usage-mark.res /Fe:CursorUsage.exe /link /SUBSYSTEM:WINDOWS user32.lib gdi32.lib gdiplus.lib winhttp.lib shell32.lib ole32.lib oleaut32.lib dwmapi.lib uuid.lib advapi32.lib comctl32.lib shlwapi.lib crypt32.lib bcrypt.lib
if errorlevel 1 exit /b 1
echo Built %~dp0CursorUsage.exe

echo Note: Qt settings is separate - see settings-qt\ and qt-runtime\
if exist "%~dp0qt-runtime\CursorUsageSettings.exe" echo Qt settings runtime: %~dp0qt-runtime\

