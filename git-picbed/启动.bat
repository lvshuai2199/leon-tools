@echo off
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo 需要先安装 Node.js
  pause
  exit /b 1
)
node server.js
if errorlevel 1 pause
