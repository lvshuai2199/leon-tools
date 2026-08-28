# 本机启动后端。PATH 默认若是 Java 8，会改用 %USERPROFILE%\.jdks\ 下的 JDK 21。
# Maven 使用 .mvn/settings.xml（阿里云镜像），避免公司 Nexus 拉不到 Spring Boot 4。
$ErrorActionPreference = "Stop"
$SpringBootDir = $PSScriptRoot

function Use-Jdk21IfNeeded {
    $javaHome = $env:JAVA_HOME
    $needSwitch = $true
    if ($javaHome -and (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $ver = cmd /c "`"$javaHome\bin\java.exe`" -version 2>&1"
        } finally {
            $ErrorActionPreference = $prev
        }
        if ("$ver" -match 'version "2[1-9]') { $needSwitch = $false }
    }
    if ($needSwitch) {
        $ms21 = Get-ChildItem (Join-Path $env:USERPROFILE ".jdks") -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "21" } |
            Select-Object -First 1
        if (-not $ms21) {
            Write-Error "需要 JDK 21。请安装或把 JAVA_HOME 指到 21，例如 $env:USERPROFILE\.jdks\ms-21.0.12.1"
        }
        $env:JAVA_HOME = $ms21.FullName
        $env:PATH = "$($ms21.FullName)\bin;" + $env:PATH
        Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
    }
}

Use-Jdk21IfNeeded
Set-Location $SpringBootDir
if (Test-Path ".\mvnw.cmd") {
    & .\mvnw.cmd spring-boot:run
} else {
    & mvn spring-boot:run
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
