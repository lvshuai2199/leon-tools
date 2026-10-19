# 取消开机启动，停掉图床进程，删除 D:\Softs\git-picbed。源码目录不动。
$ErrorActionPreference = "Continue"

$dest = "D:\Softs\git-picbed"
$runName = "GitPicbed"

Remove-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name $runName -ErrorAction SilentlyContinue
Remove-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" -Name $runName -ErrorAction SilentlyContinue

$launcher = [IO.Path]::GetFullPath((Join-Path $dest "GitPicbed.exe"))
$launchers = @(Get-CimInstance Win32_Process -Filter "Name = 'GitPicbed.exe'" -ErrorAction SilentlyContinue | Where-Object {
  ($_.ExecutablePath -and ($_.ExecutablePath -ieq $launcher)) -or ($_.CommandLine -and ($_.CommandLine -like "*$launcher*"))
})
foreach ($p in $launchers) {
  Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
if ($launchers.Count -gt 0) { Start-Sleep -Milliseconds 300 }

$needle = [IO.Path]::GetFullPath((Join-Path $dest "server.js"))
$port = 3791
$cfg = Join-Path $dest "config.json"
if (Test-Path -LiteralPath $cfg) {
  try {
    $j = Get-Content -LiteralPath $cfg -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($j.port) { $port = [int]$j.port }
  } catch {}
}
$ids = New-Object "System.Collections.Generic.HashSet[int]"
Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
  if ($_.CommandLine -and ($_.CommandLine -like "*$needle*")) {
    [void]$ids.Add([int]$_.ProcessId)
  }
}
Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
  $procId = [int]$_.OwningProcess
  $p = Get-CimInstance Win32_Process -Filter "ProcessId = $procId" -ErrorAction SilentlyContinue
  if ($p -and $p.Name -eq "node.exe" -and $p.CommandLine -match "server\.js") {
    [void]$ids.Add($procId)
  }
}
foreach ($procId in $ids) {
  Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
}
if ($ids.Count -gt 0) { Start-Sleep -Milliseconds 400 }

if (Test-Path -LiteralPath $dest) {
  Remove-Item -LiteralPath $dest -Recurse -Force -ErrorAction SilentlyContinue
}

if (Test-Path -LiteralPath $dest) {
  Write-Host "开机启动已取消，但目录没删干净：$dest"
  Write-Host "请关掉占用该目录的程序后再运行一次卸载。"
  exit 1
}

Write-Host "已取消开机启动，并删除 $dest"
Write-Host "源码仍在仓库 git-picbed，需要时再双击 安装.bat"
