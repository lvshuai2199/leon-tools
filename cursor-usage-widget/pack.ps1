$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe = Join-Path $root "native-cpp\CursorUsage.exe"
$dist = Join-Path $root "dist"
$setupDir = Join-Path $root "setup"
$vcvars = "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"

if (-not (Test-Path -LiteralPath $exe)) {
  Write-Host "Please build native-cpp\build.bat first."
  exit 1
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null
$outExe = Join-Path $dist "CursorUsage-Setup.exe"
$bat = Join-Path $setupDir "_pack.cmd"
@(
  "@echo off"
  "call `"$vcvars`" >nul"
  "cd /d `"$setupDir`""
  "rc /nologo /fo Setup.res Setup.rc"
  "if errorlevel 1 exit /b 1"
  "cl /nologo /O2 /EHsc /utf-8 /DUNICODE /D_UNICODE /W3 Setup.cpp Setup.res /Fe:`"$outExe`" /link /SUBSYSTEM:WINDOWS ole32.lib shell32.lib shlwapi.lib user32.lib advapi32.lib uuid.lib"
  "if errorlevel 1 exit /b 1"
  "del /q Setup.obj Setup.res 2>nul"
) | Set-Content -LiteralPath $bat -Encoding ASCII

try {
  cmd.exe /c "`"$bat`""
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
  Remove-Item -LiteralPath $bat -Force -ErrorAction SilentlyContinue
}

if (-not (Test-Path -LiteralPath $outExe)) {
  Write-Host "CursorUsage-Setup.exe was not built."
  exit 1
}

Write-Host ""
Write-Host "Installer:"
Write-Host $outExe
Write-Host ""
