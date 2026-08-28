$ErrorActionPreference = "Stop"

$DeployDir = $PSScriptRoot
$FrontendDir = Split-Path $DeployDir -Parent
$EnvFile = Join-Path $DeployDir "deploy.env"
$DistDir = Join-Path $FrontendDir "dist"
$NginxConf = Join-Path $DeployDir "nginx.conf"

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

if (-not $DEPLOY_HOST -or -not $DEPLOY_USER -or -not $DEPLOY_REMOTE_DIR) {
    Write-Error "deploy.env needs DEPLOY_HOST / DEPLOY_USER / DEPLOY_REMOTE_DIR"
}

if (-not $DEPLOY_PASSWORD -and -not $DEPLOY_SSH_KEY) {
    $backendEnv = Join-Path $FrontendDir "..\..\LeonPro_backend\SpringBoot\deploy\deploy.env"
    if (Test-Path $backendEnv) {
        Get-Content $backendEnv | ForEach-Object {
            $line = $_.Trim()
            if ($line -eq "" -or $line.StartsWith("#")) { return }
            $idx = $line.IndexOf("=")
            if ($idx -lt 1) { return }
            $key = $line.Substring(0, $idx).Trim()
            $value = $line.Substring($idx + 1).Trim()
            if ($key -eq "DEPLOY_PASSWORD" -and $value) { $script:DEPLOY_PASSWORD = $value }
            if ($key -eq "DEPLOY_SSH_KEY" -and $value) { $script:DEPLOY_SSH_KEY = $value }
        }
        Write-Host "Using login from backend deploy.env"
    }
}

if (-not $DEPLOY_PORT) { $DEPLOY_PORT = "22" }
if (-not $SKIP_BUILD) { $SKIP_BUILD = "0" }
if (-not $NGINX_RELOAD) { $NGINX_RELOAD = "1" }
if (-not $DEPLOY_PASSWORD) { $DEPLOY_PASSWORD = "" }
if (-not $DEPLOY_SSH_KEY) { $DEPLOY_SSH_KEY = "" }
if (-not $DEPLOY_NGINX_CONF) { $DEPLOY_NGINX_CONF = "/etc/nginx/sites-available/default" }

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
    $env:DEPLOY_PASSWORD = $DEPLOY_PASSWORD
    $env:SSH_ASKPASS = Join-Path $DeployDir "askpass.cmd"
    $env:SSH_ASKPASS_REQUIRE = "force"
    $env:DISPLAY = "127.0.0.1:0"
}

$remote = "${DEPLOY_USER}@${DEPLOY_HOST}"

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

& ssh @sshArgs $remote "mkdir -p '$DEPLOY_REMOTE_DIR' && rm -rf '$DEPLOY_REMOTE_DIR'/* && tar -xf /tmp/leonpro-dist.tar -C '$DEPLOY_REMOTE_DIR' && rm -f /tmp/leonpro-dist.tar"
if ($LASTEXITCODE -ne 0) { throw "extract dist failed" }

Write-Host "Writing nginx config $DEPLOY_NGINX_CONF ..."
& scp @scpArgs $NginxConf "${remote}:/tmp/leonpro-nginx.conf"
if ($LASTEXITCODE -ne 0) { throw "scp nginx.conf failed" }

$installNginx = "set -e; if [ ! -f ${DEPLOY_NGINX_CONF}.bak.leonpro ]; then cp '$DEPLOY_NGINX_CONF' '${DEPLOY_NGINX_CONF}.bak.leonpro'; fi; cp /tmp/leonpro-nginx.conf '$DEPLOY_NGINX_CONF'; nginx -t"
& ssh @sshArgs $remote $installNginx
if ($LASTEXITCODE -ne 0) { throw "nginx -t failed" }

if ($NGINX_RELOAD -eq "1") {
    Write-Host "Reloading nginx ..."
    & ssh @sshArgs $remote "systemctl reload nginx"
    if ($LASTEXITCODE -ne 0) { throw "nginx reload failed" }
}

Write-Host "Done. Frontend is at ${DEPLOY_HOST}:$DEPLOY_REMOTE_DIR"
Write-Host "Open http://${DEPLOY_HOST}/"
