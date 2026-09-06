# Dump leonpro_db_prod / leonpro_db_dev from old host, import into new mysql8.
# Copy migrate.env.example to migrate.env first.
$ErrorActionPreference = "Stop"

$DeployDir = $PSScriptRoot
$AskPass = Join-Path (Split-Path $DeployDir -Parent) "LeonPro_backend\SpringBoot\deploy\askpass.cmd"
$MigrateFile = Join-Path $DeployDir "migrate.env"
$BootstrapFile = Join-Path $DeployDir "bootstrap.env"
$LocalDump = Join-Path $DeployDir "leonpro-db-dump.sql"

function Read-EnvFile([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -gt 0 -and $line[0] -eq [char]0xFEFF) {
            $line = $line.Substring(1).Trim()
        }
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        Set-Variable -Name $key -Value $value -Scope Script
    }
}

if (-not (Test-Path $MigrateFile)) {
    Write-Error "Missing migrate.env. Copy migrate.env.example first."
}
Read-EnvFile $BootstrapFile
Read-EnvFile $MigrateFile

if (-not $OLD_HOST -or -not $OLD_USER) {
    Write-Error "migrate.env needs OLD_HOST / OLD_USER"
}
if (-not $OLD_PORT) { $OLD_PORT = "22" }
if (-not $OLD_PASSWORD) { $OLD_PASSWORD = "" }
if (-not $OLD_SSH_KEY) { $OLD_SSH_KEY = "" }
if (-not $OLD_MYSQL_PASSWORD) { $OLD_MYSQL_PASSWORD = "Root@123456" }

if (-not $NEW_HOST) { $NEW_HOST = $DEPLOY_HOST }
if (-not $NEW_USER) { $NEW_USER = $DEPLOY_USER }
if (-not $NEW_PORT) { $NEW_PORT = $(if ($DEPLOY_PORT) { $DEPLOY_PORT } else { "22" }) }
if (-not $NEW_PASSWORD) { $NEW_PASSWORD = $DEPLOY_PASSWORD }
if (-not $NEW_SSH_KEY) { $NEW_SSH_KEY = $DEPLOY_SSH_KEY }
if (-not $NEW_MYSQL_PASSWORD) { $NEW_MYSQL_PASSWORD = $(if ($MYSQL_ROOT_PASSWORD) { $MYSQL_ROOT_PASSWORD } else { "Root@123456" }) }

if (-not $NEW_HOST -or -not $NEW_USER) {
    Write-Error "New host missing. Fill NEW_* in migrate.env or bootstrap.env."
}

function New-SshArgs([string]$Port, [string]$Key, [string]$Password) {
    $common = @("-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=15")
    $ssh = @("-p", $Port) + $common
    $scp = @("-P", $Port) + $common
    if ($Key) {
        $ssh += @("-i", $Key)
        $scp += @("-i", $Key)
    } elseif ($Password) {
        if (-not (Test-Path $AskPass)) { Write-Error "Password login needs $AskPass" }
        $ssh += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
        $scp += @("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no")
        $env:DEPLOY_PASSWORD = $Password
        $env:SSH_ASKPASS = $AskPass
        $env:SSH_ASKPASS_REQUIRE = "force"
        $env:DISPLAY = "127.0.0.1:0"
    }
    return @{ Ssh = $ssh; Scp = $scp }
}

$oldRemote = "${OLD_USER}@${OLD_HOST}"
$newRemote = "${NEW_USER}@${NEW_HOST}"
$oldSudo = if ($OLD_USER -eq "root") { "" } else { "sudo " }
$newSudo = if ($NEW_USER -eq "root") { "" } else { "sudo " }

$new = New-SshArgs $NEW_PORT $NEW_SSH_KEY $NEW_PASSWORD
$dumpedViaNew = $false

Write-Host "Dumping databases on $OLD_HOST via SSH ..."
$old = New-SshArgs $OLD_PORT $OLD_SSH_KEY $OLD_PASSWORD
$dumpCmd = "${oldSudo}docker exec mysql8 sh -c 'mysqldump -uroot -p`"$OLD_MYSQL_PASSWORD`" --single-transaction --routines --events --databases leonpro_db_prod leonpro_db_dev' > /tmp/leonpro-db-dump.sql"
& ssh @($old.Ssh) $oldRemote $dumpCmd
if ($LASTEXITCODE -eq 0) {
    Write-Host "Downloading dump ..."
    if (Test-Path $LocalDump) { Remove-Item $LocalDump -Force }
    & scp @($old.Scp) "${oldRemote}:/tmp/leonpro-db-dump.sql" $LocalDump
    if ($LASTEXITCODE -ne 0) { throw "scp dump from old host failed" }
} else {
    Write-Host "Old SSH unreachable. Trying dump from new host to ${OLD_HOST}:3306 ..."
    $new = New-SshArgs $NEW_PORT $NEW_SSH_KEY $NEW_PASSWORD
    $remoteDump = @"
set -e
${newSudo}docker run --rm mysql:8.0 mysqldump -h '$OLD_HOST' -P 3306 -uroot -p'$OLD_MYSQL_PASSWORD' --single-transaction --routines --events --databases leonpro_db_prod leonpro_db_dev > /tmp/leonpro-db-dump.sql
"@
    & ssh @($new.Ssh) $newRemote $remoteDump
    if ($LASTEXITCODE -ne 0) { throw "Could not dump old MySQL via SSH or ${OLD_HOST}:3306" }
    $dumpedViaNew = $true
}

$new = New-SshArgs $NEW_PORT $NEW_SSH_KEY $NEW_PASSWORD
if (-not $dumpedViaNew) {
    if (-not (Test-Path $LocalDump) -or ((Get-Item $LocalDump).Length -lt 100)) {
        Write-Error "Dump file missing or too small: $LocalDump"
    }
    Write-Host "Uploading dump to $NEW_HOST ..."
    & scp @($new.Scp) $LocalDump "${newRemote}:/tmp/leonpro-db-dump.sql"
    if ($LASTEXITCODE -ne 0) { throw "scp dump to new host failed" }
}

Write-Host "Importing into new mysql8 ..."
$importCmd = "${newSudo}docker exec -i mysql8 sh -c 'mysql -uroot -p`"$NEW_MYSQL_PASSWORD`"' < /tmp/leonpro-db-dump.sql && rm -f /tmp/leonpro-db-dump.sql"
& ssh @($new.Ssh) $newRemote $importCmd
if ($LASTEXITCODE -ne 0) { throw "new-host mysql import failed" }

if (-not $dumpedViaNew) {
    & ssh @($old.Ssh) $oldRemote "rm -f /tmp/leonpro-db-dump.sql" | Out-Null
    Remove-Item $LocalDump -Force -ErrorAction SilentlyContinue
}

Write-Host "Done. leonpro_db_prod / leonpro_db_dev copied to $NEW_HOST"
