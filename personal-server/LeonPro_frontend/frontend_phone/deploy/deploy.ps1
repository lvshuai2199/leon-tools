$ErrorActionPreference = "Stop"

$DeployDir = $PSScriptRoot
$FrontendDir = Split-Path $DeployDir -Parent
$EnvFile = Join-Path $DeployDir "deploy.env"
$DistDir = Join-Path $FrontendDir "dist\build\h5"
$NginxConf = Join-Path $FrontendDir "..\vue3_frontend\deploy\nginx.conf"

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
$sudo = if ($DEPLOY_USER -eq "root") { "" } else { "sudo " }

if ($SKIP_BUILD -ne "1") {
    Write-Host "Building uni-app H5..."
    Push-Location $FrontendDir
    try {
        $env:NODE_OPTIONS = "--max-old-space-size=1536"
        npm run build:h5
        if ($LASTEXITCODE -ne 0) { throw "uni-app H5 build failed" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path (Join-Path $DistDir "index.html"))) {
    Write-Error "dist/build/h5/index.html not found. Run npm run build:h5 first."
}

if (-not (Test-Path $NginxConf)) {
    Write-Error "nginx.conf not found: $NginxConf"
}

Write-Host "Preparing $DEPLOY_REMOTE_DIR ..."
$tarPath = Join-Path $DeployDir "dist-upload.tar"
if (Test-Path $tarPath) { Remove-Item $tarPath -Force }
& tar -cf $tarPath -C $DistDir .
if ($LASTEXITCODE -ne 0) { throw "tar dist failed" }

Write-Host "Uploading archive ..."
& scp @scpArgs $tarPath "${remote}:/tmp/leonpro-h5.tar"
if ($LASTEXITCODE -ne 0) { throw "scp archive failed" }
Remove-Item $tarPath -Force

& ssh @sshArgs $remote "${sudo}mkdir -p '$DEPLOY_REMOTE_DIR' && ${sudo}rm -rf '$DEPLOY_REMOTE_DIR'/* && ${sudo}tar -xf /tmp/leonpro-h5.tar -C '$DEPLOY_REMOTE_DIR' && rm -f /tmp/leonpro-h5.tar"
if ($LASTEXITCODE -ne 0) { throw "extract dist failed" }

Write-Host "Writing nginx config $DEPLOY_NGINX_CONF ..."
& scp @scpArgs $NginxConf "${remote}:/tmp/leonpro-nginx.conf"
if ($LASTEXITCODE -ne 0) { throw "scp nginx.conf failed" }

$installNginx = "set -e; if [ ! -f ${DEPLOY_NGINX_CONF}.bak.leonpro ]; then ${sudo}cp '$DEPLOY_NGINX_CONF' '${DEPLOY_NGINX_CONF}.bak.leonpro'; fi; ${sudo}cp /tmp/leonpro-nginx.conf '$DEPLOY_NGINX_CONF'; ${sudo}sed -i 's/\r`$//' '$DEPLOY_NGINX_CONF'; ${sudo}nginx -t"
& ssh @sshArgs $remote $installNginx
if ($LASTEXITCODE -ne 0) { throw "nginx -t failed" }

if ($NGINX_RELOAD -eq "1") {
    Write-Host "Reloading nginx ..."
    & ssh @sshArgs $remote "${sudo}systemctl reload nginx"
    if ($LASTEXITCODE -ne 0) { throw "nginx reload failed" }
}

Write-Host "Done. H5 is at ${DEPLOY_HOST}:$DEPLOY_REMOTE_DIR"
Write-Host "Open http://${DEPLOY_HOST}/h5/"
