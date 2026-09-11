@echo off
setlocal
cd /d "%~dp0"
echo.
echo ========== CursorUsage pack ==========
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack.ps1"
if errorlevel 1 (
  echo.
  echo Pack failed.
  echo If compile failed, close the running usage widget first.
  echo.
  pause
  exit /b 1
)
echo.
echo File is in the dist folder. Filename includes date-time.
echo.
pause
exit /b 0
