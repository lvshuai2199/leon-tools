@echo off
cd /d "%~dp0host"
where python >nul 2>nul
if errorlevel 1 (
  echo 需要先安装 Python 3
  pause
  exit /b 1
)
echo 舵机控制：http://127.0.0.1:8766
python server.py
if errorlevel 1 pause
