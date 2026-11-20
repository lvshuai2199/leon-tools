@echo off
cd /d "%~dp0"
rem 只用到 QtCore/Gui/Widgets。collect-all 会打进 WebEngine/QML/全套翻译，onefile 冷启动要先解压几百 MB。
rem 不要 hidden-import elite_script_send，那会把 tkinter 打进包。
python -m PyInstaller --noconfirm --clean --onefile --windowed --noupx --name "Elite脚本下发" --distpath "." --workpath "build" --specpath "build" --hidden-import sender --hidden-import state_listener --hidden-import trajectory --exclude-module elite_script_send --exclude-module tkinter --exclude-module _tkinter --exclude-module PySide6.QtWebEngineCore --exclude-module PySide6.QtWebEngineWidgets --exclude-module PySide6.QtWebEngineQuick --exclude-module PySide6.QtQml --exclude-module PySide6.QtQuick --exclude-module PySide6.QtPdf --exclude-module PySide6.QtPdfWidgets --exclude-module PySide6.Qt3DCore --exclude-module PySide6.QtDesigner elite_script_send_qt.py
if errorlevel 1 (
  echo 打包失败
  pause
  exit /b 1
)
echo 已生成 %~dp0Elite脚本下发.exe
pause