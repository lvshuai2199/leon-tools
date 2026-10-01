# LeonPro: upload nginx-leonpro.conf and install it on the server (backup -> nginx -t -> reload, auto rollback).
# Only the site owner runs this. Usage (from personal-server/bootstrap):
#   .\deploy-nginx.ps1 -Check        # upload + nginx -t on the server, then restore the old config (no reload)
#   .\deploy-nginx.ps1               # install for real (asks for confirmation)
#   .\deploy-nginx.ps1 -Force        # continue even if the live config has HTTPS/locations the new one lacks
#   .\deploy-nginx.ps1 -Rollback     # restore the latest backup on the server and reload
# Login is read from bootstrap.env (DEPLOY_HOST/USER/PORT/PASSWORD/SSH_KEY); password/key may also come from
# LeonPro_backend/SpringBoot/deploy/deploy.env.
param(
    [switch]$Check,
    [switch]$Force,
    [switch]$Rollback,
    [string]$BackupFile = "",
    [switch]$Yes
)
$ErrorActionPreference = "Stop"

$Dir = $PSScriptRoot
$ConfFile = Join-Path $Dir "nginx-leonpro.conf"
$InstallScript = Join-Path $Dir "nginx-install.sh"
$RemoteDir = "/tmp/leonpro-nginx"

function Read-EnvFile([string]$path) {
    $map = @{}
    if (-not (Test-Path -LiteralPath $path)) { return $map }
    Get-Content -LiteralPath $path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -gt 0 -and $line[0] -eq [char]0xFEFF) { $line = $line.Substring(1).Trim() }
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $map[$line.Substring(0, $idx).Trim()] = $line.Substring($idx + 1).Trim()
    }
    return $map
}

$envMain = Read-EnvFile (Join-Path $Dir "bootstrap.env")
$envBackend = Read-EnvFile (Join-Path $Dir "..\LeonPro_backend\SpringBoot\deploy\deploy.env")
function Pick([string]$key, [string]$default = "") {
    if ($envMain[$key]) { return $envMain[$key] }
    if ($envBackend[$key]) { return $envBackend[$key] }
    return $default
}
$DEPLOY_HOST = Pick "DEPLOY_HOST"
$DEPLOY_USER = Pick "DEPLOY_USER"
$DEPLOY_PORT = Pick "DEPLOY_PORT" "22"
$DEPLOY_PASSWORD = Pick "DEPLOY_PASSWORD"
$DEPLOY_SSH_KEY = Pick "DEPLOY_SSH_KEY"
if (-not $DEPLOY_HOST -or -not $DEPLOY_USER) {
    Write-Error "Need DEPLOY_HOST / DEPLOY_USER in bootstrap.env (copy env.example) or backend deploy.env"
}
if (-not $Rollback -and -not (Test-Path $ConfFile)) { Write-Error "Missing $ConfFile" }
if (-not (Test-Path $InstallScript)) { Write-Error "Missing $InstallScript" }

$commonOpts = @("-o", "StrictHostKeyChecking=accept-new")
$sshArgs = @("-p", $DEPLOY_PORT) + $commonOpts
$scpArgs = @("-P", $DEPLOY_PORT) + $commonOpts
if ($DEPLOY_SSH_KEY) {
    $sshArgs += @("-i", $DEPLOY_SSH_KEY)
    $scpArgs += @("-i", $DEPLOY_SSH_KEY)
} elseif ($DEPLOY_PASSWORD) {
    $sshArgs += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
    $scpArgs += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
    $AskPass = Join-Path $Dir "..\LeonPro_backend\SpringBoot\deploy\askpass.cmd"
    if (-not (Test-Path $AskPass)) { Write-Error "Password login needs $AskPass" }
    $env:DEPLOY_PASSWORD = $DEPLOY_PASSWORD
    $env:SSH_ASKPASS = (Resolve-Path $AskPass).Path
    $env:SSH_ASKPASS_REQUIRE = "force"
    $env:DISPLAY = "127.0.0.1:0"
}
$remote = "${DEPLOY_USER}@${DEPLOY_HOST}"

if ($Rollback) {
    $action = "ROLL BACK nginx on $remote to " + $(if ($BackupFile) { $BackupFile } else { "the latest backup" })
    $args2 = "--rollback" + $(if ($BackupFile) { " '$BackupFile'" } else { "" })
} elseif ($Check) {
    $action = "CHECK the new nginx config on $remote (nginx -t, then restore; no reload)"
    $args2 = "'$RemoteDir/nginx-leonpro.conf' --check"
} else {
    $action = "INSTALL the new nginx config on $remote and reload nginx"
    $args2 = "'$RemoteDir/nginx-leonpro.conf'"
}
if ($Force) { $args2 += " --force" }

Write-Host $action
if (-not $Yes -and -not $Check) {
    $answer = Read-Host "Type yes to continue"
    if ($answer -ne "yes") { Write-Host "Cancelled."; exit 1 }
}

& ssh @sshArgs $remote "mkdir -p '$RemoteDir'"
if ($LASTEXITCODE -ne 0) { Write-Error "ssh failed" }
& scp @scpArgs $InstallScript "${remote}:$RemoteDir/nginx-install.sh"
if ($LASTEXITCODE -ne 0) { Write-Error "upload nginx-install.sh failed" }
if (-not $Rollback) {
    & scp @scpArgs $ConfFile "${remote}:$RemoteDir/nginx-leonpro.conf"
    if ($LASTEXITCODE -ne 0) { Write-Error "upload nginx-leonpro.conf failed" }
}

$crlf = if ($Rollback) { "'$RemoteDir/nginx-install.sh'" } else { "'$RemoteDir/nginx-install.sh' '$RemoteDir/nginx-leonpro.conf'" }
$run = "sed -i 's/\r`$//' $crlf && if [ `"`$(id -u)`" = 0 ]; then bash '$RemoteDir/nginx-install.sh' $args2; elif sudo -n true 2>/dev/null; then sudo bash '$RemoteDir/nginx-install.sh' $args2; else sudo -S -p '' bash '$RemoteDir/nginx-install.sh' $args2 <<< '$DEPLOY_PASSWORD'; fi"
& ssh @sshArgs $remote $run
$code = $LASTEXITCODE
switch ($code) {
    0 { Write-Host "Done." }
    3 { Write-Host "Stopped before changing anything (see warnings above). Re-run with -Force if intended." -ForegroundColor Yellow }
    4 { Write-Host "nginx -t failed; the old config was restored and nginx was NOT reloaded." -ForegroundColor Red }
    5 { Write-Host "reload failed; the old config was restored." -ForegroundColor Red }
    default { Write-Host "Remote script exited with $code" -ForegroundColor Red }
}
exit $code
