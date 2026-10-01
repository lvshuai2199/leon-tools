$ErrorActionPreference = "Stop"

$DeployDir = $PSScriptRoot
$FrontendDir = Split-Path $DeployDir -Parent
$EnvFile = Join-Path $DeployDir "deploy.env"
$DistDir = Join-Path $FrontendDir "dist"

if (-not (Test-Path $EnvFile)) {
    Write-Error "Missing deploy.env. Copy deploy.env.example first."
}

Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    Set-Variable -Name $key -Value $value -Scope Script
}

# Admin deploys only to its own folder; the old DEPLOY_REMOTE_DIR (site root) now belongs to the user-side app
$DEPLOY_REMOTE_DIR = if ($DEPLOY_ADMIN_REMOTE_DIR) { $DEPLOY_ADMIN_REMOTE_DIR } else { "/var/www/leonpro-admin" }
if ($DEPLOY_REMOTE_DIR -notmatch "^/var/www/[^/]+") { Write-Error "DEPLOY_ADMIN_REMOTE_DIR must be under /var/www/: $DEPLOY_REMOTE_DIR" }

if (-not $DEPLOY_HOST -or -not $DEPLOY_USER) {
    Write-Error "deploy.env needs DEPLOY_HOST / DEPLOY_USER"
}

if (-not $DEPLOY_PASSWORD -and -not $DEPLOY_SSH_KEY) {
    $fallbackEnvs = @(
        (Join-Path $FrontendDir "..\..\bootstrap\bootstrap.env"),
        (Join-Path $FrontendDir "..\..\LeonPro_backend\SpringBoot\deploy\deploy.env")
    )
    foreach ($fallbackEnv in $fallbackEnvs) {
        if (-not (Test-Path $fallbackEnv)) { continue }
        Get-Content $fallbackEnv | ForEach-Object {
            $line = $_.Trim()
            if ($line -eq "" -or $line.StartsWith("#")) { return }
            $idx = $line.IndexOf("=")
            if ($idx -lt 1) { return }
            $key = $line.Substring(0, $idx).Trim()
            $value = $line.Substring($idx + 1).Trim()
            if ($key -eq "DEPLOY_PASSWORD" -and $value -and -not $script:DEPLOY_PASSWORD) { $script:DEPLOY_PASSWORD = $value }
            if ($key -eq "DEPLOY_SSH_KEY" -and $value -and -not $script:DEPLOY_SSH_KEY) { $script:DEPLOY_SSH_KEY = $value }
        }
        if ($DEPLOY_PASSWORD -or $DEPLOY_SSH_KEY) {
            Write-Host "Using login from $fallbackEnv"
            break
        }
    }
}

if (-not $DEPLOY_PORT) { $DEPLOY_PORT = "22" }
if (-not $SKIP_BUILD) { $SKIP_BUILD = "0" }
if ($env:HUB_SKIP_BUILD -eq "0" -or $env:HUB_SKIP_BUILD -eq "1") { $SKIP_BUILD = $env:HUB_SKIP_BUILD }
if (-not $DEPLOY_PASSWORD) { $DEPLOY_PASSWORD = "" }
if (-not $DEPLOY_SSH_KEY) { $DEPLOY_SSH_KEY = "" }

$commonOpts = @(
    "-o", "StrictHostKeyChecking=accept-new"
)
$sshArgs = @("-p", $DEPLOY_PORT) + $commonOpts
$scpArgs = @("-P", $DEPLOY_PORT) + $commonOpts
if ($DEPLOY_PASSWORD) {
    $sshArgs += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
    $scpArgs += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
    $env:DEPLOY_PASSWORD = $DEPLOY_PASSWORD
    $env:SSH_ASKPASS = Join-Path $DeployDir "askpass.cmd"
    $env:SSH_ASKPASS_REQUIRE = "force"
    $env:DISPLAY = "127.0.0.1:0"
} elseif ($DEPLOY_SSH_KEY) {
    $sshArgs += @("-i", $DEPLOY_SSH_KEY)
    $scpArgs += @("-i", $DEPLOY_SSH_KEY)
} else {
    Write-Error "未找到 SSH 密码或密钥。请填写 bootstrap.env 的 DEPLOY_PASSWORD，或在 deploy.env 填写 DEPLOY_SSH_KEY"
}

$remote = "${DEPLOY_USER}@${DEPLOY_HOST}"
$sudo = if ($DEPLOY_USER -eq "root") { "" } else { "sudo " }

if ($SKIP_BUILD -ne "1") {
    Write-Host "Building frontend..."
    Push-Location $FrontendDir
    try {
        $env:NODE_OPTIONS = "--max-old-space-size=1536"
        $env:UV_THREADPOOL_SIZE = "1"
        pnpm run build-only
        if ($LASTEXITCODE -ne 0) { throw "frontend build failed" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path (Join-Path $DistDir "index.html"))) {
    Write-Error "dist/index.html not found. Run pnpm run build-only first."
}

Write-Host "Preparing $DEPLOY_REMOTE_DIR ..."
$tarPath = Join-Path $DeployDir "dist-upload.tar"
if (Test-Path $tarPath) { Remove-Item $tarPath -Force }
& tar -cf $tarPath -C $DistDir .
if ($LASTEXITCODE -ne 0) { throw "tar dist failed" }

Write-Host "Uploading archive ..."
& scp @scpArgs $tarPath "${remote}:/tmp/leonpro-dist.tar"
if ($LASTEXITCODE -ne 0) { throw "scp archive failed" }
Remove-Item $tarPath -Force

& ssh @sshArgs $remote "${sudo}mkdir -p '$DEPLOY_REMOTE_DIR' && ${sudo}chown -R '${DEPLOY_USER}:${DEPLOY_USER}' '$DEPLOY_REMOTE_DIR' && ${sudo}rm -rf '$DEPLOY_REMOTE_DIR'/* && ${sudo}tar -xf /tmp/leonpro-dist.tar -C '$DEPLOY_REMOTE_DIR' && rm -f /tmp/leonpro-dist.tar"
if ($LASTEXITCODE -ne 0) { throw "extract dist failed" }

# nginx config is deployed by the backend side; this script no longer touches nginx

Write-Host "Done. Admin frontend is at ${DEPLOY_HOST}:$DEPLOY_REMOTE_DIR"
Write-Host "Open http://${DEPLOY_HOST}/admin/"
