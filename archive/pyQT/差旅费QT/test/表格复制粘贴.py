import sys
from PyQt5.QtWidgets import QApplication, QMainWindow, QTableWidget, QTableWidgetItem, QShortcut, QMenu, QAction, QHeaderView, QMessageBox
from PyQt5.QtGui import QKeySequence

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        self.initUI()

    def initUI(self):
        self.table_widget = QTableWidget()
        self.table_widget.setRowCount(15)
        self.table_widget.setColumnCount(5)

        # 填充表格
        for row in range(15):
            for col in range(5):
                item = QTableWidgetItem(f"Row {row}, Col {col}")
                self.table_widget.setItem(row, col, item)

        # 设置表头
        self.table_widget.setHorizontalHeaderLabels([f"Col {i}" for i in range(5)])
        self.table_widget.setVerticalHeaderLabels([f"Row {i}" for i in range(15)])
        self.table_widget.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)

        self.setCentralWidget(self.table_widget)

        # 创建快捷键
        copy_shortcut = QShortcut(QKeySequence.Copy, self)
        copy_shortcut.activated.connect(self.copy_cells)

        paste_shortcut = QShortcut(QKeySequence.Paste, self)
        paste_shortcut.activated.connect(self.paste_cells)

        self.setWindowTitle("Copy and Paste Example")
        self.setGeometry(100, 100, 600, 400)

    def copy_cells(self):
        selected_indexes = self.table_widget.selectedIndexes()
        if not selected_indexes:
            QMessageBox.warning(self, "Warning", "No cells selected to copy.")
            return

        selected_text = ""
        rows = set()
        cols = set()
        for index in selected_indexes:
            rows.add(index.row())
            cols.add(index.column())
            selected_text += f"{index.data()}\t"
        selected_text = selected_text.strip()

        QApplication.clipboard().setText(selected_text)

    def paste_cells(self):
        clipboard_text = QApplication.clipboard().text()
        rows = self.table_widget.currentRow()
        cols = self.table_widget.currentColumn()

        # Split clipboard text into rows
        clipboard_rows = clipboard_text.split('\n')

        for i, row in enumerate(clipboard_rows):
            # Split each row into cells
            cells = row.split('\t')
            for j, cell in enumerate(cells):
                if rows + i < self.table_widget.rowCount() and cols + j < self.table_widget.columnCount():
                    self.table_widget.setItem(rows + i, cols + j, QTableWidgetItem(cell))

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())
