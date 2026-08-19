# 导入程序运行必须模块
import sys
# PyQt5中使用的基本控件都在PyQt5.QtWidgets模块中
from PyQt5 import QtWidgets
import NoteDeployService as UIPage


def showMenu():
    app = QtWidgets.QApplication(sys.argv)
    mainmenu = QtWidgets.QMainWindow()
    # ui = UIPage.Ui_MainWindow()#界面层次的展示，切换到控制层
    ui = UIPage.NoteDeployService()
    ui.setupUi(mainmenu)

    ui.btn_connect()
    mainmenu.show()
    sys.exit(app.exec_())


if __name__ == '__main__':
    # robotcontrolUIUse.step_test()
    showMenu()
