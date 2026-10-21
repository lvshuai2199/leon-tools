import os
import platform
import subprocess
from datetime import datetime

from PyQt5 import QtWidgets

import teach as UIPage

import ExcelEx



# 配置文件的加载
import configparser

# 配置文件存储位置
configName = 'config.ini'

class UIPage(UIPage.Ui_Form):
    def __init__(self):
        super(UIPage, self).__init__()

        self.fileList = []

        self.defaultDir = ''

        self.defaultSaveDir = ''

        self.filePrefix = ''

    def btnConnect(self):
        self.selectBtn.clicked.connect(self.selectFiles)
        self.clearListBtn.clicked.connect(self.clearList)
        # self.fileDucpBtn.clicked.connect(self.fileDucp)
        self.fileMergeBtn.clicked.connect(self.fileMerge)
        self.settingPageBtn.clicked.connect(self.go2SettingPage)
        self.back2MainBtn.clicked.connect(self.back2MainPage)
        self.selectdefaultDirBtn.clicked.connect(self.selectDefaultDir)
        self.selectdefaultSaveDirBtn.clicked.connect(self.selectDefaultSaveDir)
        self.settingSaveBtn.clicked.connect(self.settingSave)
        self.openDirBtn.clicked.connect(self.open_save_dir)

        self.readConfig()


    def selectFiles(self):
        print("选择你需要的文件")
        # 弹出文件选择对话框，允许多选
        file_dialog = QtWidgets.QFileDialog()
        if self.defaultDir != '' or self.defaultDir is not None:
            # // 设置文件对话框初始打开的文件夹路径，这里以 "C:/Users/Public/Documents" 为例
            initialPath = self.defaultDir
            file_dialog.setDirectory(initialPath)

        file_dialog.setFileMode(QtWidgets.QFileDialog.ExistingFiles)  # 设置为选择现有文件
        file_dialog.setNameFilter("All Files (*);;Text Files (*.txt);;Python Files (*.py)")  # 设置文件过滤器
        file_dialog.setViewMode(QtWidgets.QFileDialog.List)  # 设置视图模式为列表

        # 显示对话框并获取所选文件
        if file_dialog.exec_():
            selected_files = file_dialog.selectedFiles()  # 返回选择的文件列表
            print("选择的文件:", selected_files)

            # 定义允许的文件后缀
            allowed_extensions = ['.xlsx', '.xls']  # 根据需要修改

            # 检查文件后缀
            invalid_files = []

            for file_path in selected_files:
                _, extension = os.path.splitext(file_path)  # 获取文件后缀
                if extension in allowed_extensions:
                    self.fileList.append(file_path)
                else:
                    invalid_files.append(file_path)

            self.listWidget.addItems(self.fileList)
            # 输出结果
            if self.fileList:
                print("有效的文件:", self.fileList)
            if invalid_files:
                print("无效的文件:", invalid_files)


    def clearList(self):
        print("清除")
        self.fileList = []
        self.listWidget.clear()

    def fileDucp(self):
        print("去重")

    def fileMerge(self):
        print("合并")
        self.showInfoText.setText("开始合并")
        # print("文件夹存储位置")
        # # 弹出文件选择对话框，允许多选
        # # 弹出文件夹选择对话框
        #
        # folder_path = QtWidgets.QFileDialog.getExistingDirectory(
        #     None,  # 父窗口，这里设为 None
        #     "选择文件夹",  # 对话框标题
        #     "",  # 初始目录，留空表示使用默认目录
        #     QtWidgets.QFileDialog.ShowDirsOnly  # 只显示文件夹
        # )
        #
        #
        # if folder_path:

        if len(self.fileList) > 0 :
            folder_path = self.defaultSaveDir
            print("选择文件存储位置:", folder_path)

            # 获取当前时间
            current_time = datetime.now()
            # 将时间格式化为字符串，作为文件名
            file_name = current_time.strftime("%Y-%m-%d_%H-%M-%S")

            output_file = folder_path + "./" + self.filePrefix + file_name + ".xlsx"
            if ExcelEx.datasheet_copy(self.fileList, output_file):
                self.showInfoText.setText("合成完成，请打开文件夹查看")
            else:
                self.showInfoText.setText("合成失败")
        else:
            self.showInfoText.setText("文件列表为空，请先选择文件！！！")

    def go2SettingPage(self):
        self.stackedWidget.setCurrentIndex(1)
        self.showInfoText.setText("已跳转到设置")

    def back2MainPage(self):
        self.stackedWidget.setCurrentIndex(0)
        self.showInfoText.setText("已跳跳转到主页")

    def selectDefaultDir(self):
        # 弹出文件夹选择对话框
        folder_path = QtWidgets.QFileDialog.getExistingDirectory(
            None,  # 父窗口，这里设为 None
            "选择文件夹",  # 对话框标题
            "",  # 初始目录，留空表示使用默认目录
            QtWidgets.QFileDialog.ShowDirsOnly  # 只显示文件夹
        )

        if folder_path:
            print("选择文件存储位置:", folder_path)
            self.defaultDirText.setText(folder_path)


    def selectDefaultSaveDir(self):
        # 弹出文件夹选择对话框
        folder_path = QtWidgets.QFileDialog.getExistingDirectory(
            None,  # 父窗口，这里设为 None
            "选择文件夹",  # 对话框标题
            "",  # 初始目录，留空表示使用默认目录
            QtWidgets.QFileDialog.ShowDirsOnly  # 只显示文件夹
        )

        if folder_path:
            print("选择文件存储位置:", folder_path)
            self.defaultSaveDirText.setText(folder_path)

    def settingSave(self):
        print("存储配置")

        # 创建 ConfigParser 对象
        config = configparser.ConfigParser()
        # 从配置文件中读取数据
        config.read(configName, encoding='utf-8')

        if config.has_option('savePath', 'defaultDir'):
            print("存在该键值")
            if self.defaultcheckBox.isChecked():
                config['savePath']['defaultDir'] = self.defaultDirText.text()
            else:
                config.remove_option('savePath', 'defaultDir')
            config['savePath']['defaultSaveDir'] = self.defaultSaveDirText.text()
            config['savePath']['filePrefix'] = self.filePrefixText.text()
        else:
            config['savePath'] = {
                'defaultDir': self.defaultDirText.text(),
                'defaultSaveDir': self.defaultSaveDirText.text(),
                'filePrefix': self.filePrefixText.text()
            }

        # 将更新后的配置写回文件
        with open(configName, 'w', encoding='utf-8') as file:
            config.write(file)

        self.readConfig()
        self.showInfoText.setText("配置保存成功！！！")


    def readConfig(self):
        config = configparser.ConfigParser()
        config.read(configName, encoding='utf-8')

        if config.has_section('savePath'):
            if config.has_option('savePath', 'defaultDir'):
                default_dir = config.get('savePath', 'defaultDir')
                self.defaultDirText.setText(default_dir)
                self.defaultcheckBox.setChecked(True)
                self.defaultDir = default_dir
            else:
                self.defaultcheckBox.setChecked(False)

            if config.has_option('savePath', 'defaultSaveDir'):
                default_save_dir = config.get('savePath', 'defaultSaveDir')
                self.defaultSaveDirText.setText(default_save_dir)

                self.defaultSaveDir = default_save_dir

            if config.has_option('savePath', 'filePrefix'):
                file_prefix = config.get('savePath', 'filePrefix')
                self.filePrefixText.setText(file_prefix)

                self.filePrefix = file_prefix

    def open_folder(self, folder_path):
        try:
            system = platform.system()

            if system == 'Windows':
                # 将路径中的左斜杠替换为右斜杠（Windows 适用）
                file_path = folder_path.replace('/', '\\')
                print(f'explorer "{file_path}"')
                subprocess.Popen(f'explorer "{file_path}"')
            elif system == 'Linux':
                subprocess.Popen(f'xdg-open "{folder_path}"', shell=True)
            elif system == 'Darwin':
                subprocess.Popen(f'open "{folder_path}"', shell=True)
        except Exception as e:
            print(f"打开文件夹时出错: {e}")

    def open_default_dir(self):
        default_dir = self.defaultDirText.text()
        if default_dir:
            self.open_folder(default_dir)

    def open_save_dir(self):
        save_dir = self.defaultSaveDirText.text()
        if save_dir:
            self.open_folder(save_dir)
    def open_default_dir(self):
        default_dir = self.defaultDir
        if default_dir:
            self.open_folder(default_dir)

    def open_save_dir(self):
        save_dir = self.defaultSaveDir
        if save_dir:
            self.open_folder(save_dir)
