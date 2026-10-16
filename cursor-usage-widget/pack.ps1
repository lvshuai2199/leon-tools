$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$native = Join-Path $root "native-cpp"
$exe = Join-Path $native "CursorUsage.exe"
$qtRuntime = Join-Path $native "qt-runtime"
$settingsExe = Join-Path $qtRuntime "CursorUsageSettings.exe"
$dist = Join-Path $root "dist"
$setupDir = Join-Path $root "setup"
$payloadZip = Join-Path $dist "_payload.zip"
$vcvars = "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"

if (-not (Test-Path -LiteralPath $vcvars)) {
  Write-Host "Missing VS2022 vcvars64.bat"
  Write-Host $vcvars
  exit 1
}

Write-Host "[1/4] Compiling CursorUsage.exe ..."
Get-Process -Name CursorUsage,CursorUsageSettings -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 500
$build = Join-Path $native "build.bat"
cmd.exe /c "`"$build`""
if ($LASTEXITCODE -ne 0) {
  Write-Host "Compile failed. Close CursorUsage.exe if running, then retry."
  exit $LASTEXITCODE
}
if (-not (Test-Path -LiteralPath $exe)) {
  Write-Host "CursorUsage.exe was not built."
  exit 1
}

if (-not (Test-Path -LiteralPath $settingsExe)) {
  Write-Host "Missing qt-runtime\CursorUsageSettings.exe (cross-build and sync first)."
  exit 1
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null

Write-Host "[2/4] Building payload zip (exe + qt-runtime, no user config) ..."
if (Test-Path -LiteralPath $payloadZip) { Remove-Item -LiteralPath $payloadZip -Force }

$stage = Join-Path $dist "_payload_stage"
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item -LiteralPath $exe -Destination (Join-Path $stage "CursorUsage.exe") -Force
$qtDest = Join-Path $stage "qt-runtime"
New-Item -ItemType Directory -Force -Path $qtDest | Out-Null
Copy-Item -Path (Join-Path $qtRuntime "*") -Destination $qtDest -Recurse -Force

$toZip = @(
  (Join-Path $stage "CursorUsage.exe"),
  (Join-Path $stage "qt-runtime")
)
Compress-Archive -Path $toZip -DestinationPath $payloadZip -CompressionLevel Optimal -Force
if (-not (Test-Path -LiteralPath $payloadZip)) { throw "Compress-Archive failed" }
$zipMbCheck = [math]::Round((Get-Item $payloadZip).Length / 1MB, 1)
if ($zipMbCheck -lt 5) { throw "payload too small ($zipMbCheck MB); qt-runtime missing?" }
Remove-Item -LiteralPath $stage -Recurse -Force
if (-not (Test-Path -LiteralPath $payloadZip)) {
  Write-Host "payload zip missing"
  exit 1
}
$zipMb = [math]::Round((Get-Item $payloadZip).Length / 1MB, 1)
Write-Host "  payload: $payloadZip ($zipMb MB)"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$verName = "CursorUsage-Setup-$stamp.exe"
Write-Host "[3/4] Compiling installer $verName ..."

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

$stable = Join-Path $dist "CursorUsage-Setup.exe"
Copy-Item -LiteralPath $outExe -Destination $stable -Force
Set-Content -LiteralPath (Join-Path $dist "latest.txt") -Value $verName -Encoding ASCII
Remove-Item -LiteralPath $payloadZip -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "[4/4] Done."
Write-Host "Installer:"
Write-Host $outExe
Write-Host "Also:"
Write-Host $stable
$sizeMb = [math]::Round((Get-Item $outExe).Length / 1MB, 1)
Write-Host "Size: $sizeMb MB"
Write-Host ""
Write-Host "Share: double-click setup exe, pick folder; unpacks CursorUsage.exe + qt-runtime."

$desktop = [Environment]::GetFolderPath("Desktop")
$deskSetup = Join-Path $desktop $verName
$deskStable = Join-Path $desktop "CursorUsage-Setup.exe"
Copy-Item -LiteralPath $outExe -Destination $deskSetup -Force
Copy-Item -LiteralPath $stable -Destination $deskStable -Force
Write-Host "Copied to Desktop:"
Write-Host $deskSetup
Write-Host $deskStable

