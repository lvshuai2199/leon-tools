import os
import platform
import subprocess
from datetime import datetime

from PyQt5 import QtWidgets

from UIPage import teach as UIPage

import re

from PyPDF2 import PdfReader, PdfMerger
import os
import sys

# 日志文件的记录
import logging
# 配置文件的加载
import configparser

# 配置文件存储位置
configName = 'config.ini'

logFileName = "MyTool.log"

# 打开文件时以写入模式打开，这会清空文件内容
with open(logFileName, 'w') as f:
    pass  # 什么都不做，只是打开文件以清空内容
# 设置日志的基本配置
logging.basicConfig(filename=logFileName, level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')

from ToolCode import fileScan
from ToolCode import checkImgUse as ImgCheck

from ToolCode import makeMD2HTML as mdHtml


class UIPage(UIPage.Ui_Form):
    def __init__(self):
        super(UIPage, self).__init__()
        self.alian = None
        self.is_logging_on = 1
        # 文件存储名称
        # 按钮点击功能绑定

    def initConfig(self):
        # 创建 ConfigParser 对象
        config = configparser.ConfigParser()

        # 从配置文件中读取数据
        # config.read(configName, 'utf-8')
        # 日志功能是否打开
        # 默认存储文件夹获取
        if config.has_option('logConfig', 'is_logging_on'):
            self.is_logging_on = config['logConfig']['is_logging_on']
            self.log_record_show(f"日志文件开启状态为:': {self.is_logging_on}")
            if self.is_logging_on == '1':
                # 打开文件时以写入模式打开，这会清空文件内容
                with open(logFileName, 'w') as f:
                    pass  # 什么都不做，只是打开文件以清空内容
                # 设置日志的基本配置
                logging.basicConfig(filename=logFileName, level=logging.INFO,
                                    format='%(asctime)s - %(levelname)s - %(message)s')
        else:
            self.log_record_show("不存在日志文件开启配置项", log_lever=logging.WARNING)

    def btnConnect(self):
        self.fileDedupBtn.clicked.connect(self.fileDeduplication)
        self.typorePicBtn.clicked.connect(self.typoraPicEx)
        self.md2htmlBtn.clicked.connect(self.md2Html)
        self.pdfMergeBtn.clicked.connect(self.pdfMerge)
        self.addIniBtn.clicked.connect(self.updateConfigIni)
        self.deleteIniBtn.clicked.connect(self.delConfigIni)
        self.backBtn.clicked.connect(self.back2Page1)
        self.settingBtn.clicked.connect(self.go2Page2)
        self.selectDirBtn.clicked.connect(self.selectCurDir)
        self.deleteIniBtn.clicked.connect(self.delConfigIni)

        self.tempselectBtn.clicked.connect(self.selectTempDir)
        self.showBtn.clicked.connect(self.open_default_dir)

        # 连接列表和下拉框的选择事件
        self.listWidget.itemClicked.connect(self.on_list_item_clicked)
        self.selectBox.currentIndexChanged.connect(self.on_combo_index_changed)

        self.readConfigIni()

        # 初始化数据
        self.fill_input_fields("default")

    def fileDeduplication(self):
        self.log_record_show("文件去重")
        # 文件去重
        directory_path = self.targetFilePathText.text()  # 将此替换为需要处理的文件目录
        duplicates_folder = self.outFilePath.text()  # 替换为你希望保存重复文件的目录
        fileScan.deduplicate_files(directory_path, duplicates_folder)
        # 删除空文件夹
        fileScan.remove_empty_folders(directory_path)

        self.showInfoText.setText("文件去重完成")

    def typoraPicEx(self):
        self.log_record_show("md图片链接处理")
        # 用户修改为自己的文件路径
        md_folder = self.targetFilePathText.text()  # Markdown文件夹路径
        image_folder = md_folder + '/Pictures/imgs'  # 图片文件夹路径

        print(f"Markdown文件夹: {md_folder}")
        print(f"图片文件夹: {image_folder}")

        unreferenced, incorrect, fixed = ImgCheck.check_and_fix_image_references(md_folder, image_folder)

        # 生成报告
        ImgCheck.generate_report(md_folder, image_folder, unreferenced, incorrect, fixed, self.outFilePath.text())

        print("\n检查和修复过程已完成。详细信息请查看生成的报告文件。")

        self.showInfoText.setText("md文件链接处理完成")

    def md2Html(self):
        try:
            md_directory = self.targetFilePathText.text()
            html_directory = self.outFilePath.text() + "/note"
            mdHtml.convert_md_to_html(md_directory, html_directory)
            mdHtml.create_index_html(html_directory)

            mdHtml.moveImg2Place(md_directory + "/Pictures", html_directory)

        except:
            self.showInfoText.setText("失败")
    def pdfMerge(self):
        self.log_record_show("pdf合并")
        '''打开选择文件夹对话框'''
        try:
            Filepath = self.targetFilePathText.text()
            if Filepath == '':
                print("空路径")
            fileList = fileListCreate(Filepath)
            # 文件存储位置
            # 获取当前时间
            current_time = datetime.now()
            # 将时间格式化为字符串，作为文件名
            file_name = current_time.strftime("%Y-%m-%d_%H-%M-%S")
            pdfsMerge2Pdf(fileList, self.outFilePath.text() + "/" + file_name + "合并的resultPdf.pdf")
            self.showBtn.setText("合并成功")
        except:
            print("合并失败")
            self.showBtn.setText("合并失败")

    def delExteraFile(self):
        self.log_record_show("删除目标文件")

    def openFileDir(self):
        self.log_record_show("打开文件夹")

    def log_record_show(self, msg, log_lever=None, is_show=None):
        if self.is_logging_on == '1':
            if log_lever == logging.DEBUG:
                self.log_record_show("[DEBUG]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.debug(msg)
            elif log_lever == logging.INFO or log_lever == None:
                self.log_record_show("[INFO]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.info(msg)
            elif log_lever == logging.WARNING:
                self.log_record_show("[WARNING]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.warning(msg)
            elif log_lever == logging.ERROR:
                self.log_record_show("[ERROR]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.error(msg)
            else:
                raise ValueError("Invalid log level")

        if is_show:
            self.diffCalDisplay.setText(msg)

    def back2Page1(self):
        self.stackedWidget.setCurrentIndex(0)

    def go2Page2(self):
        self.stackedWidget.setCurrentIndex(1)

    def delConfigIni(self):

        self.updateConfigIni(True)

    def updateConfigIni(self, isDel=False):
        self.log_record_show("存储配置")

        # 创建 ConfigParser 对象
        config = configparser.ConfigParser()
        # 从配置文件中读取数据
        config.read(configName, encoding='utf-8')

        # 假设 self.alianText 和 self.selectDirText 是 QLineEdit 等控件
        self.alian = self.alianText.text()

        # 检查 savePath 节是否存在，如果不存在则创建
        if 'savePath' not in config:
            config['savePath'] = {}

        if isDel:
            # 尝试删除指定的键
            if self.alian in config['savePath']:
                config.remove_option('savePath', self.alian)
                self.showInfoText.setText("配置删除成功！！！")
        else:
            # 添加或更新键值对
            config['savePath'][self.alian] = self.selectDirText.text()
            self.showInfoText.setText("配置保存成功！！！")

        # 将更新后的配置写回文件
        with open(configName, 'w', encoding='utf-8') as file:
            config.write(file)

        self.readConfigIni()

    def readConfigIni(self):
        self.log_record_show("readini")

        # 清空列表和下拉框
        self.listWidget.clear()
        self.selectBox.clear()

        # 创建 ConfigParser 对象
        config = configparser.ConfigParser()
        # 从配置文件中读取数据
        config.read(configName, encoding='utf-8')

        # 检查 savePath 节是否存在
        if 'savePath' in config:
            # 获取所有别名
            aliases = config['savePath'].keys()
            for alias in aliases:
                # 添加到列表和下拉框
                self.listWidget.addItem(alias)
                self.selectBox.addItem(alias)
                if alias == "outfiledir":
                    path = config['savePath'][alias]
                    self.outFilePath.setText(path)

    def selectCurDir(self):
        # 弹出文件夹选择对话框
        folder_path = QtWidgets.QFileDialog.getExistingDirectory(
            None,  # 父窗口，这里设为 None
            "选择文件夹",  # 对话框标题
            "",  # 初始目录，留空表示使用默认目录
            QtWidgets.QFileDialog.ShowDirsOnly  # 只显示文件夹
        )

        if folder_path:
            self.log_record_show("选择文件存储位置:", folder_path)
            self.selectDirText.setText(folder_path)

    def selectTempDir(self):
        # 弹出文件夹选择对话框
        folder_path = QtWidgets.QFileDialog.getExistingDirectory(
            None,  # 父窗口，这里设为 None
            "选择文件夹",  # 对话框标题
            "",  # 初始目录，留空表示使用默认目录
            QtWidgets.QFileDialog.ShowDirsOnly  # 只显示文件夹
        )

        if folder_path:
            self.log_record_show("选择文件存储位置:", folder_path)
            self.targetFilePathText.setText(folder_path)

    def on_list_item_clicked(self, item):
        alias = item.text()
        self.fill_input_fields(alias)

    def on_combo_index_changed(self, index):
        alias = self.selectBox.itemText(index)
        self.fill_input_fields(alias)

    def fill_input_fields(self, alias):
        config = configparser.ConfigParser()
        config.read(configName, encoding='utf-8')
        if 'savePath' in config and alias in config['savePath']:
            path = config['savePath'][alias]
            self.alianText.setText(alias)
            self.selectDirText.setText(path)
            self.targetFilePathText.setText(path)
        self.showInfoText.setText("列表刷新完成")

    def open_default_dir(self):
        out_dir = self.outFilePath.text()
        if out_dir:
            open_folder(out_dir)
        self.showInfoText.setText("已打开目录")


# 文件遍历
def fileListCreate(fileDir):
    # 遍历所有的文件
    NDirFiles = []
    for root, dirs, files in os.walk(fileDir):
        print('the path is ...')
        print(root)
        print('the current directories under current directory:')
        print(dirs)
        print('the files in current directory:')
        print(files)
        for file in files:
            NDirFiles.append(os.path.join(root, file))
        print('')
    # 将所有的文件添加到集合中
    print(NDirFiles)
    return NDirFiles


def pdfsMerge2Pdf(pdfList, outFileName):
    result_pdf = PdfMerger()
    # 依次读取要合并的文件内容，并进行合并
    for pdf in pdfList:
        pattern = '.+\\.(pdf)$'
        m = re.match(pattern, pdf)
        if m != None:
            print(pdf + '匹配成功！')
            with open(pdf, 'rb') as fp:
                pdf_reader = PdfReader(fp)
                # 判断文件是否加密，如果加密了则跳过
                # if pdf_reader.isEncrypted:
                #     print(f'忽略加密文件：{pdf}')
                #     continue
                result_pdf.append(pdf_reader)
                # result_pdf.append(pdf_reader,import_bookmarks=True)
        else:
            print("匹配失败！")

    # 保存合并的pdf文件
    result_pdf.write(outFileName)
    result_pdf.close()


def open_folder(folder_path):
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
