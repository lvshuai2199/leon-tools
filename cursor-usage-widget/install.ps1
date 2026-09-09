$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $here "native-cpp\CursorUsage.exe"
if (-not (Test-Path -LiteralPath $src)) {
  Write-Host "找不到 $src"
  Write-Host "请先运行 native-cpp\build.bat 编译。"
  exit 1
}

$folderName = "CursorUsage"
$cfgDir = Join-Path $env:APPDATA "cursor-usage-widget"
$defaultParent = $env:LOCALAPPDATA
$pathFile = Join-Path $cfgDir "install.path"
if (Test-Path -LiteralPath $pathFile) {
  $prev = (Get-Content -LiteralPath $pathFile -Encoding UTF8 | Select-Object -First 1).Trim()
  if ($prev) {
    $parent = Split-Path -Parent $prev
    if ($parent -and (Test-Path -LiteralPath $parent)) { $defaultParent = $parent }
  }
}

Add-Type -AssemblyName System.Windows.Forms
$dlg = New-Object System.Windows.Forms.FolderBrowserDialog
$dlg.Description = "选择安装位置。将在该目录下创建 CursorUsage 文件夹。"
$dlg.ShowNewFolderButton = $true
try { $dlg.UseDescriptionForTitle = $true } catch {}
if (Test-Path -LiteralPath $defaultParent) { $dlg.SelectedPath = $defaultParent }

if ($dlg.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
  Write-Host "已取消安装。"
  exit 1
}

$picked = $dlg.SelectedPath.TrimEnd('\')
if ([IO.Path]::GetFileName($picked) -eq $folderName) {
  $dest = $picked
} else {
  $dest = Join-Path $picked $folderName
}

New-Item -ItemType Directory -Force -Path $dest | Out-Null

Get-Process -Name CursorUsage -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 800

$exe = Join-Path $dest "CursorUsage.exe"
try {
  Copy-Item -LiteralPath $src -Destination $exe -Force
} catch {
  Write-Host "复制失败，请先关掉正在运行的用量条后再试。"
  exit 1
}

New-Item -ItemType Directory -Force -Path $cfgDir | Out-Null
Set-Content -LiteralPath $pathFile -Value $dest -Encoding UTF8

$quoted = '"' + $exe + '"'
New-Item -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Force | Out-Null
Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "CursorUsageWidget" -Value $quoted
$approved = [byte[]](2,0,0,0,0,0,0,0,0,0,0,0)
New-Item -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" -Force | Out-Null
Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" -Name "CursorUsageWidget" -Value $approved -Type Binary

Start-Process -FilePath $exe

Write-Host ""
Write-Host "已安装到："
Write-Host "  $exe"
Write-Host ""
Write-Host "开机后会自动启动。拖动后的位置保存在："
Write-Host "  $cfgDir\ui.ini"
Write-Host ""
Write-Host "卸载请运行 uninstall.bat"
Write-Host "也可在用量条上右键勾选或取消开机启动。"
Write-Host ""
