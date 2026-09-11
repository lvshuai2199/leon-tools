$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$native = Join-Path $root "native-cpp"
$exe = Join-Path $native "CursorUsage.exe"
$dist = Join-Path $root "dist"
$setupDir = Join-Path $root "setup"
$vcvars = "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"

if (-not (Test-Path -LiteralPath $vcvars)) {
  Write-Host "找不到 Visual Studio 2022 vcvars64.bat"
  Write-Host $vcvars
  exit 1
}

Write-Host "[1/2] Compiling CursorUsage.exe ..."
$build = Join-Path $native "build.bat"
cmd.exe /c "`"$build`""
if ($LASTEXITCODE -ne 0) {
  Write-Host "Compile failed. Close CursorUsage.exe if it is running, then try again."
  exit $LASTEXITCODE
}
if (-not (Test-Path -LiteralPath $exe)) {
  Write-Host "CursorUsage.exe was not built."
  exit 1
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$verName = "CursorUsage-Setup-$stamp.exe"
Write-Host "[2/2] Packing $verName ..."

New-Item -ItemType Directory -Force -Path $dist | Out-Null
$outExe = Join-Path $dist $verName
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
  Write-Host "Installer was not built."
  exit 1
}

Set-Content -LiteralPath (Join-Path $dist "latest.txt") -Value $verName -Encoding ASCII

Write-Host ""
Write-Host "OK"
Write-Host $outExe
Write-Host ""


$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$verName = "CursorUsage-Setup-$stamp.exe"

New-Item -ItemType Directory -Force -Path $dist | Out-Null
$outExe = Join-Path $dist $verName
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
  Write-Host "Installer was not built."
  exit 1
}

Set-Content -LiteralPath (Join-Path $dist "latest.txt") -Value $verName -Encoding ASCII

Write-Host ""
Write-Host "Installer:"
Write-Host $outExe
Write-Host ""
