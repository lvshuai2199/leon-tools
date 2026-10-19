@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0隐藏启动.ps1"
if errorlevel 1 (
  echo 启动失败
  pause
  exit /b 1
)
exit /b 0
