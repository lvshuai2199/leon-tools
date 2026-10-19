@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0卸载.ps1"
set ERR=%ERRORLEVEL%
echo.
pause
exit /b %ERR%
