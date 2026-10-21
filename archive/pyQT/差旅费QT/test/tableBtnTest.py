from PyQt5.QtWidgets import *
from PyQt5.QtGui import *
from PyQt5.QtCore import *
class MainWindow(QWidget):
    def __init__(self):
        super().__init__()

        # 创建 QStandardItemModel 并填充数据
        self.model = QStandardItemModel()
        self.model.setHorizontalHeaderLabels(['Name', 'Age', 'Button'])
        for row in range(3):
            for column in range(3):
                item = QStandardItem(str(row * 3 + column + 1))
                self.model.setItem(row, column, item)

        # 创建表格视图
        self.tableView = QTableView()
        self.tableView.setModel(self.model)
        # 在最后一列插入按钮
        for row in range(3):
            btn = QPushButton('Button')
            btn.setProperty('row', row)
            btn.setProperty('column', 2)
            btn.clicked.connect(self.on_button_clicked)
            index = self.model.index(row, 2, QModelIndex())
            self.tableView.setIndexWidget(index, btn)
        # 将表格视图添加到窗口中
        layout = QHBoxLayout(self)
        layout.addWidget(self.tableView)
        self.setLayout(layout)

    def on_button_clicked(self):
        sender = self.sender()
        row = sender.property('row')
        column = sender.property('column')
        index = self.model.index(row, column, QModelIndex())
        print(f'Button clicked at row {row}, column {column}, value is {self.model.data(index, Qt.DisplayRole)}')


if __name__ == '__main__':
    app = QApplication([])
    window = MainWindow()
    window.show()
    app.exec_()

