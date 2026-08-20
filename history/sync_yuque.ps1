$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$tokenPath = Join-Path $repoRoot 'yuque-token.txt'
$token = (Get-Content -Raw $tokenPath).Trim()

$base = 'https://www.yuque.com/api/v2/repos/snoopy-rfzyo/mp8bfs'
$headers = @{
  'x-auth-token' = $token
}

function Invoke-Yuque {
  param(
    [Parameter(Mandatory=$true)][ValidateSet('GET','POST','PUT')]$Method,
    [Parameter(Mandatory=$true)][string]$Uri,
    [Parameter(Mandatory=$true)]$Body
  )

  $json = $Body | ConvertTo-Json -Depth 20 -Compress
  $delay = 15
  for ($attempt = 1; $attempt -le 5; $attempt++) {
    try {
      return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType 'application/json' -Body $json
    } catch {
      $statusCode = $null
      if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
        $statusCode = [int]$_.Exception.Response.StatusCode
      }
      if (-not $statusCode -and $_.Exception.Message -match '429') {
        $statusCode = 429
      }
      if ($statusCode -eq 429 -and $attempt -lt 5) {
        Write-Host "Yuque rate limited, retrying in $delay seconds..."
        Start-Sleep -Seconds $delay
        $delay = [Math]::Min($delay * 2, 60)
        continue
      }
      throw
    }
  }
}

function Shift-Headings {
  param([Parameter(Mandatory=$true)][string]$Text)

  $lines = $Text -split "`r?`n"
  $shifted = foreach ($line in $lines) {
    if ($line -match '^(#{1,6})(\s+.*)$') {
      '#' + $matches[1] + $matches[2]
    } else {
      $line
    }
  }

  return ($shifted -join "`n").Trim()
}

function Read-Body {
  param([Parameter(Mandatory=$true)][string]$RelativePath)
  return Get-Content -Raw (Join-Path $repoRoot $RelativePath)
}

$luaBody = @"
# 语言基础

Lua 相关内容已按主题归并到一篇里。

$(Shift-Headings (Read-Body 'yuque/语言基础/Lua整理/基础.md'))

$(Shift-Headings (Read-Body 'yuque/语言基础/Lua整理/函数.md'))

$(Shift-Headings (Read-Body 'yuque/语言基础/Lua整理/Table.md'))
"@

$serverBody = @"
# 服务器

Docker 和 Ubuntu 相关内容已按大类归并。

## Docker

$(Shift-Headings (Read-Body 'yuque/服务器/Docker/安装与配置.md'))

$(Shift-Headings (Read-Body 'yuque/服务器/Docker/镜像源与排障.md'))

$(Shift-Headings (Read-Body 'yuque/服务器/Docker/卸载.md'))

$(Shift-Headings (Read-Body 'yuque/服务器/Docker/容器部署.md'))

## Ubuntu

$(Shift-Headings (Read-Body 'yuque/服务器/Ubuntu/Nginx.md'))

$(Shift-Headings (Read-Body 'yuque/服务器/Ubuntu/Redis.md'))
"@

$softwareBody = @"
# 软件安装及应用

常用软件、系统环境和开发环境都已按大类归并在这一页。

## 常用软件

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/常用软件.md'))

## 常用链接

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/常用链接.md'))

## Windows

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/Windows.md'))

## macOS

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/macOS.md'))

## Linux

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/Linux.md'))

## 开发环境

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/开发环境/JDK.md'))

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/开发环境/Maven.md'))

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/开发环境/MySQL.md'))

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/开发环境/Git.md'))

$(Shift-Headings (Read-Body 'yuque/软件安装及应用/开发环境/Node.js.md'))
"@

Start-Sleep -Seconds 20

Write-Host 'Updating Lua page...'
Invoke-Yuque -Method PUT -Uri "$base/docs/bgdlrbw1to1d9g44" -Body @{
  body = $luaBody
  format = 'markdown'
} | Out-Null

Start-Sleep -Seconds 5

Write-Host 'Renaming software page...'
Invoke-Yuque -Method PUT -Uri "$base/docs/gbw2kgoc8fuhibk9" -Body @{
  title = '软件安装及应用'
} | Out-Null

Start-Sleep -Seconds 5

Write-Host 'Updating software page...'
Invoke-Yuque -Method PUT -Uri "$base/docs/gbw2kgoc8fuhibk9" -Body @{
  body = $softwareBody
  format = 'markdown'
} | Out-Null

Start-Sleep -Seconds 5

Write-Host 'Creating server page...'
$serverResp = Invoke-Yuque -Method POST -Uri "$base/docs" -Body @{
  title = '服务器'
  body = $serverBody
  format = 'markdown'
  public = 0
}

Write-Host "Created server page: $($serverResp.data.title) / $($serverResp.data.slug)"
