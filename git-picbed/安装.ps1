# 安装到 D:\Softs\git-picbed，并写入当前用户开机启动。
$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dest = "D:\Softs\git-picbed"
$runName = "GitPicbed"

function Find-Node {
  $fixed = "D:\Softs\nvm4w\nodejs\node.exe"
  if (Test-Path -LiteralPath $fixed) { return $fixed }
  $cmd = Get-Command node -ErrorAction SilentlyContinue
  if ($cmd -and $cmd.Source) { return $cmd.Source }
  throw "需要先安装 Node.js"
}

function Get-PicbedPort([string]$root) {
  $port = 3791
  $cfg = Join-Path $root "config.json"
  if (Test-Path -LiteralPath $cfg) {
    try {
      $j = Get-Content -LiteralPath $cfg -Raw -Encoding UTF8 | ConvertFrom-Json
      if ($j.port) { $port = [int]$j.port }
    } catch {}
  }
  return $port
}

function Stop-Launcher([string]$root) {
  $exe = [IO.Path]::GetFullPath((Join-Path $root "GitPicbed.exe"))
  $procs = @(Get-CimInstance Win32_Process -Filter "Name = 'GitPicbed.exe'" -ErrorAction SilentlyContinue | Where-Object {
    ($_.ExecutablePath -and ($_.ExecutablePath -ieq $exe)) -or ($_.CommandLine -and ($_.CommandLine -like "*$exe*"))
  })
  foreach ($p in $procs) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
  }
  if ($procs.Count -gt 0) { Start-Sleep -Milliseconds 300 }
}

function Stop-Picbed([string]$root) {
  Stop-Launcher $root
  $needle = [IO.Path]::GetFullPath((Join-Path $root "server.js"))
  $port = Get-PicbedPort $root
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
}

function Build-Launcher([string]$dir) {
  $csc = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
  if (-not (Test-Path -LiteralPath $csc)) {
    $csc = Join-Path $env:WINDIR "Microsoft.NET\Framework\v4.0.30319\csc.exe"
  }
  if (-not (Test-Path -LiteralPath $csc)) { throw "找不到 csc.exe，无法编译隐藏启动器" }
  $cs = Join-Path $dir "launch.cs"
  $exe = Join-Path $dir "GitPicbed.exe"
  $out = & $csc /nologo /target:winexe /r:System.Windows.Forms.dll /r:System.Drawing.dll "/out:$exe" $cs 2>&1
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $exe)) {
    throw ("编译隐藏启动器失败 " + ($out -join " "))
  }
}

function Copy-Tree([string]$name) {
  $src = Join-Path $here $name
  if (-not (Test-Path -LiteralPath $src)) { return }
  $target = Join-Path $dest $name
  New-Item -ItemType Directory -Force -Path $target | Out-Null
  Get-ChildItem -LiteralPath $src -Force | Copy-Item -Destination $target -Recurse -Force
}

if (-not (Test-Path -LiteralPath (Join-Path $here "server.js"))) {
  throw "找不到 server.js，请在 git-picbed 目录里运行安装。"
}

$node = Find-Node
New-Item -ItemType Directory -Force -Path "D:\Softs" | Out-Null
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$hereFull = [IO.Path]::GetFullPath($here).TrimEnd('\')
$destFull = [IO.Path]::GetFullPath($dest).TrimEnd('\')
$same = $hereFull.Equals($destFull, [StringComparison]::OrdinalIgnoreCase)

Stop-Picbed $here
if (-not $same) { Stop-Picbed $dest }

if (-not $same) {
  foreach ($name in @(
    "server.js", "package.json", "启动.bat", "开机启动.vbs", "隐藏启动.ps1", "launch.cs", "git-picbed.ico",
    "config.example.json", "README.md", "安装.bat", "安装.ps1", "卸载.bat", "卸载.ps1"
  )) {
    $src = Join-Path $here $name
    if (Test-Path -LiteralPath $src) {
      Copy-Item -LiteralPath $src -Destination (Join-Path $dest $name) -Force
    }
  }
  Copy-Tree "lib"
  Copy-Tree "public"

  $cfg = Join-Path $here "config.json"
  $cfgDest = Join-Path $dest "config.json"
  if ((Test-Path -LiteralPath $cfg) -and -not (Test-Path -LiteralPath $cfgDest)) {
    Copy-Item -LiteralPath $cfg -Destination $cfgDest -Force
  }
  $data = Join-Path $here "data"
  $dataDest = Join-Path $dest "data"
  if ((Test-Path -LiteralPath $data) -and -not (Test-Path -LiteralPath $dataDest)) {
    Copy-Tree "data"
  }
}

Build-Launcher $dest
$launcher = Join-Path $dest "GitPicbed.exe"
$runValue = "`"$launcher`" --silent"
Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name $runName -Value $runValue -Type String
try {
  $approved = [byte[]](2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  $approvedPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run"
  if (-not (Test-Path -LiteralPath $approvedPath)) {
    New-Item -Path $approvedPath -Force | Out-Null
  }
  Set-ItemProperty -Path $approvedPath -Name $runName -Value $approved -Type Binary
} catch {}

$page = "http://127.0.0.1:$(Get-PicbedPort $dest)/"
Start-Process -FilePath $launcher -ArgumentList "--silent" -WorkingDirectory $dest

$ok = $false
for ($i = 0; $i -lt 25; $i++) {
  try {
    $resp = Invoke-WebRequest -Uri $page -UseBasicParsing -TimeoutSec 2
    if ($resp.StatusCode -eq 200) { $ok = $true; break }
  } catch {}
  Start-Sleep -Milliseconds 200
}

Write-Host ""
Write-Host "已安装到："
Write-Host "  $dest"
Write-Host "开机启动：登录后在右下角显示图标，不弹黑窗口，也不打开浏览器"
Write-Host "左键图标打开页面，右键可以退出"
Write-Host "页面：$page"
if (-not $ok) {
  Write-Host "服务还没有响应。可在安装目录执行 node server.js 查看报错。"
  exit 1
}
Write-Host "服务已启动。"
Write-Host "卸载请运行安装目录里的 卸载.bat"
Write-Host ""
