$ErrorActionPreference = "Continue"
$folderName = "CursorUsage"
$cfgDir = Join-Path $env:APPDATA "cursor-usage-widget"
$pathFile = Join-Path $cfgDir "install.path"

Remove-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "CursorUsageWidget" -ErrorAction SilentlyContinue
Remove-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" -Name "CursorUsageWidget" -ErrorAction SilentlyContinue
Get-Process -Name CursorUsage -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 800

$dirs = New-Object System.Collections.Generic.List[string]
if (Test-Path -LiteralPath $pathFile) {
  $prev = (Get-Content -LiteralPath $pathFile -Encoding UTF8 | Select-Object -First 1).Trim()
  if ($prev) { $dirs.Add($prev) }
}
$legacy = Join-Path $env:LOCALAPPDATA $folderName
if (-not $dirs.Contains($legacy)) { $dirs.Add($legacy) }
$oldName = Join-Path $env:LOCALAPPDATA "CursorUsageWidget"
if (-not $dirs.Contains($oldName)) { $dirs.Add($oldName) }

foreach ($dir in $dirs) {
  if (-not $dir) { continue }
  $exe = Join-Path $dir "CursorUsage.exe"
  if (Test-Path -LiteralPath $exe) { Remove-Item -LiteralPath $exe -Force -ErrorAction SilentlyContinue }
  $base = [IO.Path]::GetFileName($dir.TrimEnd('\'))
  if ((Test-Path -LiteralPath $dir) -and ($base -eq $folderName -or $base -eq "CursorUsageWidget")) {
    $left = @(Get-ChildItem -LiteralPath $dir -Force -ErrorAction SilentlyContinue)
    if ($left.Count -eq 0) { Remove-Item -LiteralPath $dir -Force -ErrorAction SilentlyContinue }
  }
}

if (Test-Path -LiteralPath $pathFile) { Remove-Item -LiteralPath $pathFile -Force -ErrorAction SilentlyContinue }

Write-Host "已取消开机启动，并删除安装目录里的 CursorUsage.exe"
Write-Host "位置配置仍保留在 $cfgDir\ui.ini"
