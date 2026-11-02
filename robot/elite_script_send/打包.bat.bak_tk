@echo off
cd /d "%~dp0"
python -m PyInstaller --noconfirm --clean --onefile --windowed --noupx --name "Elite脚本下发" --distpath "." --workpath "build" --specpath "build" elite_script_send.py
if errorlevel 1 (
  echo 打包失败
  pause
  exit /b 1
)
echo 已生成 %~dp0Elite脚本下发.exe
pause
