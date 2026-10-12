$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$node = "D:\Softs\nvm4w\nodejs\node.exe"
if (-not (Test-Path -LiteralPath $node)) {
  $cmd = Get-Command node -ErrorAction SilentlyContinue
  if ($cmd) { $node = $cmd.Source }
}
$port = 3780
$cfgPath = Join-Path $root "config.json"
if (Test-Path -LiteralPath $cfgPath) {
  try { $port = [int](Get-Content -LiteralPath $cfgPath -Raw -Encoding UTF8 | ConvertFrom-Json).hubPort } catch {}
}
$url = "http://127.0.0.1:$port/"

$mutex = New-Object System.Threading.Mutex($false, "Local\LeonProDevHubTray")
$hasMutex = $false
try { $hasMutex = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $hasMutex = $true }
if (-not $hasMutex) { exit 0 }

function New-TrayIcon {
  $bmp = New-Object System.Drawing.Bitmap 32, 32
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.Color]::Transparent)
  $bg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(29, 33, 41))
  $g.FillEllipse($bg, 1, 1, 30, 30)
  $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
  $pts = @(
    (New-Object System.Drawing.Point 12, 9),
    (New-Object System.Drawing.Point 12, 23),
    (New-Object System.Drawing.Point 23, 16)
  )
  $g.FillPolygon($brush, $pts)
  $g.Dispose()
  $script:trayBitmap = $bmp
  [System.Drawing.Icon]::FromHandle($bmp.GetHicon())
}

function Open-Page {
  Start-Process $url
}

function Test-Listening {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $client.Connect("127.0.0.1", $port)
    return $true
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Start-Server {
  if ((Test-Listening) -or -not (Test-Path -LiteralPath $node)) { return $null }
  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $node
  $psi.Arguments = "server.js"
  $psi.WorkingDirectory = $root
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  [System.Diagnostics.Process]::Start($psi)
}

function Stop-Server {
  if ($script:proc -and -not $script:proc.HasExited) {
    Stop-Process -Id $script:proc.Id -Force -ErrorAction SilentlyContinue
    return
  }
  Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
  }
}

$script:proc = Start-Server
$icon = New-TrayIcon
$notify = New-Object System.Windows.Forms.NotifyIcon
$notify.Icon = $icon
$notify.Text = "LeonPro 启动台"
$notify.Visible = $true

$menu = New-Object System.Windows.Forms.ContextMenuStrip
$openItem = $menu.Items.Add("打开")
$exitItem = $menu.Items.Add("退出")
$notify.ContextMenuStrip = $menu
$openItem.Add_Click({ Open-Page })
$notify.Add_MouseClick({
  if ($_.Button -eq [System.Windows.Forms.MouseButtons]::Left) { Open-Page }
})

$context = New-Object System.Windows.Forms.ApplicationContext
$exitItem.Add_Click({
  $notify.Visible = $false
  Stop-Server
  $notify.Dispose()
  $context.ExitThread()
})

[System.Windows.Forms.Application]::Run($context)
$mutex.ReleaseMutex() | Out-Null
$mutex.Dispose()
