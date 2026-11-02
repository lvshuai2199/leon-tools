@echo off
cd /d "%~dp0"
python -m PyInstaller --noconfirm --clean --onefile --windowed --noupx --name "Elite脚本下发" --distpath "." --workpath "build" --specpath "build" --collect-all PySide6 --hidden-import elite_script_send --hidden-import sender --hidden-import state_listener --hidden-import trajectory elite_script_send_qt.py
if errorlevel 1 (
  echo 打包失败
  pause
  exit /b 1
)
echo 已生成 %~dp0Elite脚本下发.exe
pause