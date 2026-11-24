@echo off
cd /d "%~dp0"
rem onedir：启动不再把整包解压到临时目录。exe 旁边必须有 _internal。
rem 只用 QtCore/Gui/Widgets。不要 hidden-import elite_script_send，那会把 tkinter 打进包。
python -m PyInstaller --noconfirm --clean --onedir --windowed --noupx --name "Elite脚本下发" --distpath "build\dist" --workpath "build\work" --specpath "build" --hidden-import sender --hidden-import state_listener --hidden-import trajectory --exclude-module elite_script_send --exclude-module tkinter --exclude-module _tkinter --exclude-module PySide6.QtWebEngineCore --exclude-module PySide6.QtWebEngineWidgets --exclude-module PySide6.QtWebEngineQuick --exclude-module PySide6.QtQml --exclude-module PySide6.QtQuick --exclude-module PySide6.QtPdf --exclude-module PySide6.QtPdfWidgets --exclude-module PySide6.Qt3DCore --exclude-module PySide6.QtDesigner elite_script_send_qt.py
if errorlevel 1 (
  echo 打包失败
  pause
  exit /b 1
)
echo 已生成 %~dp0Elite脚本下发.exe
pause