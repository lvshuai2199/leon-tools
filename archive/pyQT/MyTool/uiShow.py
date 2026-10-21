# 导入程序运行必须模块
import sys
import os
# PyQt5中使用的基本控件都在PyQt5.QtWidgets模块中
from PyQt5 import QtWidgets
# 导入designer工具生成的页面模块
import toolControl as UI

def showMenu():
    app = QtWidgets.QApplication(sys.argv)
    mainmenu = QtWidgets.QWidget()

    myToolControl = UI.UIPage()

    # 设置界面内容
    myToolControl.setupUi(mainmenu)

    # 配置文件及按钮功能初始化
    myToolControl.btnConnect()

    myToolControl.initConfig()

    mainmenu.show()
    sys.exit(app.exec_())


if __name__ == '__main__':
    # 确保当前工作目录是你期望的目录
    print("当前工作目录:", os.getcwd())
    showMenu()