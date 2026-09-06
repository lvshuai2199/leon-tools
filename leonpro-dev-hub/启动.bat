@echo off
cd /d "%~dp0"
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 3780 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
timeout /t 1 /nobreak >nul
start "" "http://127.0.0.1:3780"
node server.js
if errorlevel 1 pause
