$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$silent = $args -contains "--silent"
$exe = Join-Path $root "GitPicbed.exe"

if (Test-Path -LiteralPath $exe) {
  if ($silent) {
    Start-Process -FilePath $exe -ArgumentList "--silent" -WorkingDirectory $root
  } else {
    Start-Process -FilePath $exe -WorkingDirectory $root
  }
  exit 0
}

$node = "D:\Softs\nvm4w\nodejs\node.exe"
if (-not (Test-Path -LiteralPath $node)) {
  $cmd = Get-Command node -ErrorAction SilentlyContinue
  if (-not $cmd -or -not $cmd.Source) { throw "需要先安装 Node.js" }
  $node = $cmd.Source
}

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $node
$psi.Arguments = '"' + (Join-Path $root "server.js") + '"'
$psi.WorkingDirectory = $root
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
if ($silent) { $psi.EnvironmentVariables["PICBED_NO_BROWSER"] = "1" }
[void][System.Diagnostics.Process]::Start($psi)
