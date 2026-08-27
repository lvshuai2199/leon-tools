$ErrorActionPreference = "Stop"

$DeployDir = $PSScriptRoot
$SpringBootDir = Split-Path $DeployDir -Parent
$EnvFile = Join-Path $DeployDir "deploy.env"
$JarPath = Join-Path $DeployDir "dist\app.jar"

if (-not (Test-Path $EnvFile)) {
    Write-Error "缺少 $EnvFile ，请先复制 deploy.env.example 为 deploy.env 并填写服务器信息"
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
    Write-Error "deploy.env 需要填写 DEPLOY_HOST / DEPLOY_USER / DEPLOY_REMOTE_DIR"
}

if (-not $DEPLOY_PORT) { $DEPLOY_PORT = "22" }
if (-not $SKIP_BUILD) { $SKIP_BUILD = "0" }
if (-not $DOCKER_UP) { $DOCKER_UP = "0" }
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
    $env:DEPLOY_PASSWORD = $DEPLOY_PASSWORD
    $env:SSH_ASKPASS = Join-Path $DeployDir "askpass.cmd"
    $env:SSH_ASKPASS_REQUIRE = "force"
    $env:DISPLAY = "127.0.0.1:0"
}

$remote = "${DEPLOY_USER}@${DEPLOY_HOST}"

if ($SKIP_BUILD -ne "1") {
    Write-Host "Maven 打包..."
    Push-Location $SpringBootDir
    try {
        if (Test-Path ".\mvnw.cmd") {
            & .\mvnw.cmd -B clean package -Dmaven.test.skip=true
        } else {
            & mvn -B clean package -Dmaven.test.skip=true
        }
        if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $JarPath)) {
    Write-Error "未找到 $JarPath ，请先成功执行 mvn package"
}

Write-Host "在服务器创建目录 $DEPLOY_REMOTE_DIR ..."
& ssh @sshArgs $remote "mkdir -p '$DEPLOY_REMOTE_DIR'"
if ($LASTEXITCODE -ne 0) { throw "ssh mkdir 失败" }

Write-Host "拷贝 jar 到 ${remote}:$DEPLOY_REMOTE_DIR/app.jar ..."
& scp @scpArgs $JarPath "${remote}:${DEPLOY_REMOTE_DIR}/app.jar"
if ($LASTEXITCODE -ne 0) { throw "scp jar 失败" }

Write-Host "拷贝 Docker 文件..."
& scp @scpArgs `
    (Join-Path $SpringBootDir "Dockerfile") `
    (Join-Path $SpringBootDir "docker-compose.yml") `
    (Join-Path $SpringBootDir ".dockerignore") `
    "${remote}:${DEPLOY_REMOTE_DIR}/"
if ($LASTEXITCODE -ne 0) { throw "scp Docker 文件失败" }

if ($DOCKER_UP -eq "1") {
    Write-Host "在服务器启动 Docker 容器..."
    & ssh @sshArgs $remote "cd '$DEPLOY_REMOTE_DIR' && (docker compose up -d --build || docker-compose up -d --build)"
    if ($LASTEXITCODE -ne 0) { throw "docker compose 失败" }
}

Write-Host "完成：jar 已放到 ${DEPLOY_HOST}:$DEPLOY_REMOTE_DIR"
if ($DOCKER_UP -ne "1") {
    Write-Host "稍后在服务器执行：cd $DEPLOY_REMOTE_DIR && docker compose up -d --build"
}
