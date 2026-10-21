import sys
from PyQt5.QtWidgets import QApplication, QMainWindow, QMessageBox

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("窗口关闭示例")
        self.setGeometry(100, 100, 400, 300)

    def closeEvent(self, event):
        # 弹出消息框询问用户是否确认关闭窗口
        reply = QMessageBox.question(self, '确认关闭',
            "确定要关闭窗口吗？", QMessageBox.Yes |
            QMessageBox.No, QMessageBox.No)

        if reply == QMessageBox.Yes:
            # 用户确认关闭窗口
            event.accept()
        else:
            # 用户取消关闭窗口
            event.ignore()

def main():
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()
