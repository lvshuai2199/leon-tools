@echo off
cd /d "%~dp0"
where python >nul 2>nul
if errorlevel 1 (
  echo 需要先安装 Python 3
  pause
  exit /b 1
)
python md2docx.py
echo.
pause
