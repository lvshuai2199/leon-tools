$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$cfgDir = Join-Path $env:APPDATA "cursor-usage-widget"
$pathFile = Join-Path $cfgDir "install.path"
$candidates = @()
if (Test-Path -LiteralPath $pathFile) {
  $prev = (Get-Content -LiteralPath $pathFile -Encoding UTF8 | Select-Object -First 1).Trim()
  if ($prev) { $candidates += (Join-Path $prev "CursorUsage.exe") }
}
$candidates += (Join-Path $env:LOCALAPPDATA "CursorUsage\CursorUsage.exe")
$candidates += (Join-Path $here "native-cpp\CursorUsage.exe")
foreach ($exe in $candidates) {
  if (Test-Path -LiteralPath $exe) {
    Start-Process -FilePath $exe
    exit 0
  }
}
Write-Host "找不到 CursorUsage.exe，请先编译 native-cpp\build.bat 或运行 install.bat"
exit 1
