param(
  [switch]$InPlace
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = $null
foreach ($c in @(
  (Join-Path $here "CursorUsage.exe"),
  (Join-Path $here "native-cpp\CursorUsage.exe")
)) {
  if (Test-Path -LiteralPath $c) { $src = $c; break }
}
if (-not $src) {
  Write-Host "找不到 CursorUsage.exe"
  Write-Host "请先运行 native-cpp\build.bat 编译，或使用 dist 里的安装包。"
  exit 1
}

$folderName = "CursorUsage"
$cfgDir = Join-Path $env:APPDATA "cursor-usage-widget"
$defaultParent = $env:LOCALAPPDATA
$pathFile = Join-Path $cfgDir "install.path"
$prevDest = ""
if (Test-Path -LiteralPath $pathFile) {
  $prevDest = (Get-Content -LiteralPath $pathFile -Encoding UTF8 | Select-Object -First 1).Trim()
  if ($prevDest) {
    $parent = Split-Path -Parent $prevDest
    if ($parent -and (Test-Path -LiteralPath $parent)) { $defaultParent = $parent }
  }
}

$dest = $null
if ($InPlace -and $prevDest) {
  $dest = $prevDest.TrimEnd('\')
  Write-Host "按已有安装位置更新：$dest"
} else {
  Add-Type -AssemblyName System.Windows.Forms
  $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
  $dlg.Description = "选择安装位置。将在该目录下创建 CursorUsage 文件夹；若已选中 CursorUsage 则直接装进去。"
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

foreach ($name in @("uninstall.bat", "uninstall.ps1")) {
  $u = Join-Path $here $name
  if (Test-Path -LiteralPath $u) {
    Copy-Item -LiteralPath $u -Destination (Join-Path $dest $name) -Force
  }
}

New-Item -ItemType Directory -Force -Path $cfgDir | Out-Null
Set-Content -LiteralPath $pathFile -Value $dest -Encoding UTF8

$quoted = '"' + $exe + '"'
$autoOk = $false
$autoErr = ""
try {
  Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "CursorUsageWidget" -Value $quoted -Type String -ErrorAction Stop
  $autoOk = $true
} catch {
  $autoErr = $_.Exception.Message
}
if ($autoOk) {
  try {
    $approved = [byte[]](2,0,0,0,0,0,0,0,0,0,0,0)
    $approvedPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run"
    if (Test-Path -LiteralPath $approvedPath) {
      Set-ItemProperty -Path $approvedPath -Name "CursorUsageWidget" -Value $approved -Type Binary -ErrorAction Stop
    }
  } catch {}
}

Start-Process -FilePath $exe

Write-Host ""
Write-Host "已安装到："
Write-Host "  $exe"

if (-not $autoOk) {
  Write-Host "开机自启没有写上，不影响使用。可以在用量条上右键勾选「开机启动」。"
  if ($autoErr) { Write-Host "  $autoErr" }
  Write-Host ""
}
Write-Host ""
Write-Host "开机后会自动启动。位置、贴边、显示大小保存在："
Write-Host "  $cfgDir\ui.ini"
Write-Host "右键「打开程序」勾选的启动项保存在："
Write-Host "  $cfgDir\shortcuts.txt"
Write-Host ""
Write-Host "右键可：刷新、用量页、打开程序（检索本机已装软件并多项勾选；已开则调窗，未开则启动）、显示大小、贴边、开机启动、退出。"
Write-Host "再次更新可运行 install.bat -InPlace，会装到本次同一目录。"
Write-Host "卸载请运行 uninstall.bat"
Write-Host ""
