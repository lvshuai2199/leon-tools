# 导入程序运行必须模块
import sys
# PyQt5中使用的基本控件都在PyQt5.QtWidgets模块中
from PyQt5 import QtWidgets
# 导入designer工具生成的页面模块
import Control.chargeControl as UIPage

def showMenu():
    app = QtWidgets.QApplication(sys.argv)
    mainmenu = QtWidgets.QWidget()

    chargeControl = UIPage.ChargeControl()

    # 设置界面内容
    chargeControl.setupUi(mainmenu)

    # 配置文件及按钮功能初始化
    # travelCon.initConfig()

    mainmenu.show()
    sys.exit(app.exec_())


if __name__ == '__main__':
    showMenu()