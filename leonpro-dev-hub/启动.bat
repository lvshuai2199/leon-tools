@echo off
cd /d "%~dp0"
start "" powershell.exe -NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0tray.ps1"
timeout /t 2 /nobreak >nul
start "" "http://127.0.0.1:3780"
