@echo off
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo 需要先安装 Node.js
  pause
  exit /b 1
)
where git >nul 2>nul
if errorlevel 1 (
  echo 需要先安装 Git for Windows
  pause
  exit /b 1
)
echo 本机 Git 工具：http://127.0.0.1:3792
node server.js
if errorlevel 1 pause
