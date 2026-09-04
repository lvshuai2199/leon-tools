# LeonPro: upload bootstrap dir and run bootstrap-server.sh on Ubuntu (sudo)
# Copy env.example to bootstrap.env, then: .\bootstrap.ps1
$ErrorActionPreference = "Stop"

$DeployDir = $PSScriptRoot
$EnvFile = Join-Path $DeployDir "bootstrap.env"
$RemoteDir = "/tmp/leonpro-bootstrap"

if (-not (Test-Path $EnvFile)) {
    Write-Error "Missing $EnvFile . Copy env.example to bootstrap.env first."
}

Get-Content -LiteralPath $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -gt 0 -and $line[0] -eq [char]0xFEFF) {
        $line = $line.Substring(1).Trim()
    }
    if ($line -ne "" -and -not $line.StartsWith("#")) {
        $idx = $line.IndexOf("=")
        if ($idx -ge 1) {
            $key = $line.Substring(0, $idx).Trim()
            $value = $line.Substring($idx + 1).Trim()
            Set-Variable -Name $key -Value $value -Scope Script
        }
    }
}

if (-not $DEPLOY_HOST -or -not $DEPLOY_USER) {
    Write-Error "bootstrap.env needs DEPLOY_HOST and DEPLOY_USER"
}

if (-not $DEPLOY_PORT) { $DEPLOY_PORT = "22" }
if (-not $DEPLOY_PASSWORD) { $DEPLOY_PASSWORD = "" }
if (-not $DEPLOY_SSH_KEY) { $DEPLOY_SSH_KEY = "" }

$commonOpts = @(
    "-o", "StrictHostKeyChecking=accept-new"
)
$sshArgs = @("-p", $DEPLOY_PORT) + $commonOpts
$scpArgs = @("-P", $DEPLOY_PORT) + $commonOpts
if ($DEPLOY_SSH_KEY) {
    $sshArgs += @("-i", $DEPLOY_SSH_KEY)
    $scpArgs += @("-i", $DEPLOY_SSH_KEY)
} elseif ($DEPLOY_PASSWORD) {
    $sshArgs += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
    $scpArgs += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
    $AskPass = Join-Path (Split-Path $DeployDir -Parent) "LeonPro_backend\SpringBoot\deploy\askpass.cmd"
    if (-not (Test-Path $AskPass)) {
        Write-Error "Password login needs $AskPass"
    }
    $env:DEPLOY_PASSWORD = $DEPLOY_PASSWORD
    $env:SSH_ASKPASS = $AskPass
    $env:SSH_ASKPASS_REQUIRE = "force"
    $env:DISPLAY = "127.0.0.1:0"
}

$remote = "${DEPLOY_USER}@${DEPLOY_HOST}"

Write-Host "Upload bootstrap to ${remote}:${RemoteDir}"
& ssh @sshArgs $remote "mkdir -p '$RemoteDir'"
if ($LASTEXITCODE -ne 0) { Write-Error "SSH failed" }

Get-ChildItem $DeployDir -File | Where-Object { $_.Name -ne "bootstrap.env" } | ForEach-Object {
    & scp @scpArgs $_.FullName "${remote}:${RemoteDir}/$($_.Name)"
    if ($LASTEXITCODE -ne 0) { Write-Error "Upload $($_.Name) failed" }
}

$mysqlPass = if ($MYSQL_ROOT_PASSWORD) { $MYSQL_ROOT_PASSWORD } else { "Root@123456" }
$mysqlPublish = if ($MYSQL_PUBLISH) { $MYSQL_PUBLISH } else { "127.0.0.1:3306:3306" }
$dockerMirror = if ($DOCKER_MIRROR) { $DOCKER_MIRROR } else { "" }
$setupUfw = if ($SETUP_UFW) { $SETUP_UFW } else { "0" }
$remoteEnv = @"
MYSQL_ROOT_PASSWORD=$mysqlPass
MYSQL_PUBLISH=$mysqlPublish
DOCKER_MIRROR=$dockerMirror
SETUP_UFW=$setupUfw
"@
$remoteEnvUnix = $remoteEnv -replace "`r`n", "`n"
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($remoteEnvUnix))
& ssh @sshArgs $remote "printf '%s' '$b64' | base64 -d > '$RemoteDir/bootstrap.env'"

Write-Host "Run bootstrap-server.sh with sudo"
$remoteCmd = "chmod +x '$RemoteDir/bootstrap-server.sh' && if sudo -n true 2>/dev/null; then sudo bash '$RemoteDir/bootstrap-server.sh'; else echo '$DEPLOY_PASSWORD' | sudo -S bash '$RemoteDir/bootstrap-server.sh'; fi"
& ssh @sshArgs $remote $remoteCmd
if ($LASTEXITCODE -ne 0) { Write-Error "Remote bootstrap failed" }
