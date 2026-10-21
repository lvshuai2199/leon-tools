import csv
import os
import re
import shutil
import time

import pdfplumber
import xlwt

from ResourceUI import traveler
from PyQt5 import QtCore, QtGui, QtWidgets

# 配置文件的加载
import configparser

from math import pi

from PyQt5.QtWidgets import QApplication, QMainWindow, QTableWidgetItem, QTableWidget
import sys

from datetime import datetime

import hashlib

# 初始化
QMessageBox = QtWidgets.QMessageBox
# 初始化栏目信息
# 配置文件存储位置
configName = 'config.ini'

# 错误列表
errorFileList = []

# 日志文件的记录
import logging

logFileName = "TravelCal.log"
# 打开文件时以写入模式打开，这会清空文件内容
with open(logFileName, 'w') as f:
    pass  # 什么都不做，只是打开文件以清空内容
# 设置日志的基本配置
logging.basicConfig(filename=logFileName, level=logging.INFO,
                    format='%(asctime)s - %(levelname)s - %(message)s')


class TravelerControl(traveler.Ui_MainWindow):
    def __init__(self):
        super(TravelerControl, self).__init__()
        # 文件存储名称
        self.diff_name = ''
        self.diff_days = ''
        self.diff_allowance = ''
        self.invoice_type = ''
        self.invoice_amount = ''
        self.invoice_num = ''
        self.invoice_date = ''
        self.diff_display = ''
        self.save_file_path = ''
        # 配置尾部名称
        self.tailname = ''
        # 存储的发票号码列表
        self.dataList = []
        # 发票抬头信息字典
        self.inv_header_text = ''
        self.companies = {}
        # 增加加载的文件夹目录位置信息
        self.load_file_path = ''
        # 日志功能是否打开
        self.is_logging_on = ''

    # 配置文件读取，初始化
    def initConfig(self):
        # 初始化，使表格下滚动条不再显示
        self.dataSheet.horizontalHeader().setSectionResizeMode(QtWidgets.QHeaderView.Stretch)

        # # 设置表格大小策略为Expanding
        # self.dataSheet.setSizePolicy(
        #     QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Expanding)

        # 创建 ConfigParser 对象
        config = configparser.ConfigParser()

        # 从配置文件中读取数据
        config.read(configName, 'utf-8')
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
        # 默认存储文件夹获取
        if config.has_option('saveDir', 'dirName'):
            saveDirName = config['saveDir']['dirName']
            self.log_record_show(f"自定目录Value of key in 'saveDir': {saveDirName}")
            self.diffName.setText(saveDirName)
            self.diff_name = saveDirName
        else:
            self.log_record_show("不存在自定目录键值", log_lever=logging.WARNING)
        # 存储位置设置
        if config.has_option('savePath', 'address'):
            savePath = config['savePath']['address']
            self.log_record_show(f"存储位置Value of key in 'section_name': {savePath}")
            self.savePath.setText(savePath)
            self.save_file_path = savePath
        else:
            self.log_record_show("不存在存储位置键值", log_lever=logging.WARNING)
        # 导出文件末尾名称设置
        if config.has_option('savePath', 'tailname'):
            self.tailname = config['savePath']['tailname']
            self.log_record_show("文件导出的尾部名称:" + self.tailname)
        else:
            self.log_record_show("不存在尾部名称键值", log_lever=logging.WARNING)

        # 获取 CMR 部分的数据
        if 'CMR' in config:
            self.log_record_show("开始获取CMR数据")
            cmr_section = config['CMR']

            # 获取津贴信息
            if 'travelAllowance' in cmr_section:
                travel_allowance = cmr_section['travelAllowance'].split(',')

                # 去除空格并输出
                travel_allowance = [allowance.strip() for allowance in travel_allowance]

                # 打印结果
                self.log_record_show("津贴列表:" + list2str(travel_allowance))
                self.diffAllowance.clear()
                self.diffAllowance.addItems(travel_allowance)
            else:
                self.log_record_show("在 [CMR] 部分中未找到 travelAllowance 键。", log_lever=logging.WARNING)
        else:
            self.log_record_show("未找到 [CMR] 部分。", log_lever=logging.WARNING)

        tempcompanies = {}
        self.log_record_show("获取已记录的公司抬头")
        for section in config.sections():
            # 跳过 'saveDir' 和 'savePath' 部分
            if section == 'saveDir' or section == 'savePath' or section == 'CMR' or section == 'logConfig':
                continue

            company_name = config[section].get('CompanyName', '').replace(' ', '')
            tin = config[section].get('TIN', '').replace(' ', '')
            tempcompanies[section] = {'CompanyName': company_name, 'TIN': tin}

        self.companies = tempcompanies

        self.log_record_show("已记录的公司抬头为:" + list2str(self.companies))

        # 使用 addItems 方法添加多个项
        self.invHList.clear()  # 清除现有项
        company_names = [info['CompanyName'] for _, info in self.companies.items()]
        self.invHList.addItems(company_names)

        self.log_record_show("公司抬头注入完成!!!")

        # 设置样式表来调整下拉列表项之间的间距
        self.invHList.setStyleSheet(
            "QComboBox QAbstractItemView::item { background-color: #ADD8E6; padding: 10px; }")
        self.invHList.update()

        self.dataList = []
        self.load_file_path = ''

        self.btnConnect()

        self.log_record_show("Program Init SUCCESS!" + "~_~!!!")
        self.log_record_show("-" * 30 + '初始化已完成' + "-" * 30)

    # 按钮连接
    def btnConnect(self):

        # 按钮点击功能绑定
        self.invoiceAdd.clicked.connect(self.invoice_add)
        self.diffAdd.clicked.connect(self.allowance_add)
        self.clearAll.clicked.connect(self.clear_all)
        self.delRow.clicked.connect(self.del_select_row)
        self.excelCreate.clicked.connect(self.export_to_excel)
        self.diffCal.clicked.connect(self.diff_cal)

        self.log_record_show("欢迎使用！！！", is_show=1)

        self.savePathSelect.clicked.connect(self.save_path_select)

        self.fileLoad.clicked.connect(self.file_load)
        # 文件分类
        self.docSaveByTypeBtn.clicked.connect(self.docSaveByTypeBtnClicked)
        # pdf合并
        self.pdfMergeBtn.clicked.connect(self.pdfMergeBtnClicked)
        # 抬头校验
        self.invHVerBtn.clicked.connect(self.invHVerBtnClicked)
        self.log_record_show("Button Functions Connect" + "~_~!!!")

    # 获取页面窗口数据信息
    def get_param_data(self):
        self.diff_name = self.diffName.text()
        self.invoice_amount = self.invoiceAmount.text()
        self.invoice_num = self.invoiceNum.text()
        self.invoice_date = self.invoiceDate.text()
        self.diff_days = self.diffDays.text()
        self.invoice_type = self.invoiceType.currentText()
        self.diff_allowance = self.diffAllowance.currentText()
        self.inv_header_text = self.invHList.currentText()
        self.log_record_show("get_param_data SUCCESS!" + "~_~!!!")

    '''
        发票信息的增加及对应的处理
    '''

    # 添加发票信息
    def invoice_add(self):
        self.log_record_show("发票添加开始", is_show=1)
        self.get_param_data()
        # logging.info(self.invoice_date)
        # 数据插入
        invoiceList = [self.invoice_date, self.invoice_num, self.invoice_amount, self.invoice_type, '', '']
        self.add_row(invoiceList)

        self.log_record_show("发票添加完成" + "~_~!!!", is_show=1)
        self.log_record_show("-" * 30 + '发票添加完成' + "-" * 30)

    # 添加补贴信息
    def allowance_add(self):
        self.log_record_show("补贴信息添加开始", is_show=1)
        self.get_param_data()
        if self.diff_days == '':
            self.diff_days = 1
        countCost = float(self.diff_days) * float(self.diff_allowance)
        # 数据插入
        invoiceList = ['', '', countCost, '补贴', '', '']
        self.add_row(invoiceList)
        self.log_record_show("补贴信息添加完成" + "~_~!!!", is_show=1)
        self.log_record_show("-" * 30 + '补贴信息添加完成' + "-" * 30)

    #  导出文件存储位置修改
    def save_path_select(self):
        self.log_record_show("开始选择存储位置", is_show=1)
        # 导入存储位置
        file_dialog = QtWidgets.QFileDialog()
        # file_path, _ = file_dialog.getSaveFileName(self, '保存文件', '', 'CSV Files (*.csv);;All Files (*.*)')
        new_save_path = file_dialog.getExistingDirectory()  # 获得选择好的文件夹名称
        if new_save_path == '':
            QtWidgets.QMessageBox.information(self, '提醒', '已取消存储位置修改！！！')
            self.log_record_show("update save_path cancel", is_show=1)
        else:
            self.log_record_show("位置已选择,开始修改")
            self.save_file_path = ''
            # QtWidgets.QMessageBox.information(self, '提醒', '文件存储文件夹已被修改为:！！！')
            self.save_file_path = new_save_path + '/'
            self.savePath.setText(self.save_file_path)
            # 更新已存在的键值对
            # savePath = config['savePath']['address']
            # 创建 ConfigParser 对象
            config = configparser.ConfigParser()
            # 从配置文件中读取数据
            config.read(configName, encoding='utf-8')

            if config.has_option('savePath', 'address'):
                self.log_record_show("存在该键值")
                self.save_file_path = self.save_file_path
                config['savePath']['address'] = self.save_file_path
                with open(configName, 'w', encoding='utf-8') as file:
                    config.write(file)

            else:
                config['savePath'] = {'address': self.save_file_path}
                with open(configName, 'w', encoding='utf-8') as file:
                    config.write(file)
                self.log_record_show("不存在该键值,已重新写入")
            self.log_record_show('已记录并变更本地存储位置!!!' + "~_~!!!", is_show=1)
            self.log_record_show("-" * 30 + "update save_path SUCCESS" + "-" * 30)

    '''
        文件的导出,包含Excel及CSV文件的导出功能 
    '''

    # 导出为excel文件
    def export_to_excel(self):
        try:
            self.log_record_show("正在生成表格", is_show=1)
            data_list = self.sheet_2_list()
            # # 导入存储位置
            # file_dialog = QtWidgets.QFileDialog()
            # file_path, _ = file_dialog.getSaveFileName(self, '保存文件', '', 'CSV Files (*.csv);;All Files (*.*)')
            self.get_param_data()
            if self.save_file_path == '':
                QtWidgets.QMessageBox.warning(self, '提醒', '当前存储位置为空,请确认文件存储地址！！！')
                self.log_record_show("当前存储位置为空,请确认文件存储地址！！！", is_show=1)
                return

            # 获取当前时间
            current_time = datetime.now()
            # 将时间格式化为字符串，作为文件名
            file_name = current_time.strftime("%Y-%m-%d_%H-%M-%S")
            file_path = self.save_file_path + file_name + self.tailname + r'.xls'

            self.log_record_show("导出文件名称构建完成")

            # 改为选择文件存储位置
            # 创建工作簿
            wb = xlwt.Workbook()
            # 创建表单
            sh = wb.add_sheet('sheet 1')
            # sh.write(0, 0, '姓名')
            sh.write(0, 0, '开票日期')
            sh.write(0, 1, '发票号')
            sh.write(0, 2, '金额')
            sh.write(0, 3, '类别')
            sh.write(0, 4, '抬头')
            sh.write(0, 5, '税号')

            # 设置第一列的列宽为 256 * 20 个字符宽度
            for i in range(6):
                if i in [0, 2, 3]:
                    # 设置第二列的列宽为 256 * 30 个字符宽度
                    sh.col(i).width = 256 * 20
                else:
                    sh.col(i).width = 256 * 30

            # 假设要将第三列的数据格式改为数字格式，但不包括第一行
            col_index = 3
            # 创建一个数字格式对象
            style = xlwt.easyxf(num_format_str='0.00')  # 以两位小数的数字格式为例

            row = 1
            for i in data_list:
                self.log_record_show("写入内容:" + list2str(i))
                # 数据写入
                for j in range(len(i)):
                    sh.write(row, j, i[j])
                row += 1

            self.log_record_show('表格已生成！！！')
            # 保存
            wb.save(file_path)
            self.log_record_show("表格导出成功!!!", is_show=1)
            self.log_record_show("-" * 30 + '表格导出成功' + "-" * 30)

        except:
            self.log_record_show("表格生成失败!!!", log_lever=logging.ERROR, is_show=1)
            QtWidgets.QMessageBox.warning(self, '提醒',
                                          '表格生成失败!!!(可能原因如下)\n1.excel表格正在使用\n2.文件存储位置参数未初始化\n3.金额数据存在非数字符号！！！')

    def export_to_csv(self):
        try:
            data_list = self.sheet_2_list()
            # # 导入存储位置
            # file_dialog = QtWidgets.QFileDialog()
            # file_path, _ = file_dialog.getSaveFileName(self, '保存文件', '', 'CSV Files (*.csv);;All Files (*.*)')
            self.get_param_data()
            if self.save_file_path == '' or self.diff_name == '':
                if self.diff_name == '':
                    QtWidgets.QMessageBox.warning(self, '提醒', '请输入姓名！！！')
                else:
                    QtWidgets.QMessageBox.warning(self, '提醒', '请确认文件存储地址！！！')
                return
            file_path = self.save_file_path + self.diff_name + '.csv'

            if not file_path:
                return
            with open(file_path, 'w', newline='') as csvfile:
                writer = csv.writer(csvfile)
                # logging.info(model.rowCount())
                for i in data_list:
                    # writer.writerow(data_list)
                    writer.writerow(i)
            csvfile.close()
            logging.info('表格已生成！！！')
            self.log_record_show("表格导出成功!!!")
            self.log_record_show("-" * 30 + '表格导出成功' + "-" * 30)
        except:
            self.log_record_show("表格生成失败!!!")
            QtWidgets.QMessageBox.warning(self, '提醒', '文件存储位置参数未初始化或数据存在问题！！！')

    '''
        功能区域代码块
    '''

    # 计算出差总体费用
    def diff_cal(self):
        try:
            self.log_record_show("开始计算差旅费用", is_show=1)
            data_list = self.sheet_2_list()
            # logging.info(data_list)
            cost_list = {}
            for row_list in data_list:
                if row_list[3] in cost_list.keys():
                    cost_list[row_list[3]] = cost_list[row_list[3]] + float(row_list[2])
                else:
                    cost_list[row_list[3]] = float(row_list[2])
                self.log_record_show(str(row_list[3]) + ':' + str(cost_list[row_list[3]]))
            self.log_record_show("列表总计汇总完成!!!", is_show=1)

            # 清空所有数据并重新计算对应数值
            self.diff_display = ''
            total = 0.0
            for item, value in cost_list.items():
                self.log_record_show(str(item) + str(value))
                total += value
                # 计算出的数值保留两位小数
                self.diff_display = self.diff_display + str(item) + ':' + str("{:.2f}".format(value)) + '\n'

            # 总计
            self.diff_display = self.diff_display + '总计:' + str("{:.2f}".format(total))
            self.log_record_show(self.diff_display, is_show=1)
            self.log_record_show("-" * 30 + '差旅费用计算完成' + "-" * 30)


        except:
            warnRes = QtWidgets.QMessageBox.warning(self, '提醒', '金额中存在异常数据,请检查！！！')
            self.log_record_show("金额中存在异常数据", log_lever=logging.ERROR, is_show=1)

    # 文件加载
    def file_load(self):
        try:
            errorFileList = []
            self.log_record_show("正在加载文件", is_show=1)
            self.get_param_data()
            # 导入存储位置
            file_dialog = QtWidgets.QFileDialog()
            loadPath = file_dialog.getExistingDirectory()  # 获得选择好的文件夹名称
            if loadPath == '':
                QtWidgets.QMessageBox.information(self, '提醒', '未选取文件夹！！！')
                self.log_record_show('已取消文件夹加载!!!')
                return

            self.load_file_path = loadPath
            self.log_record_show("已记录加载文件地址为:" + self.load_file_path)

            # 获取所有的pdf文件（若文件夹中出现压缩包‘’【，则解压缩）
            pdf_file = fileListCreate(loadPath, ".pdf")
            self.log_record_show("获取所有文件夹列表:" + list2str(pdf_file))

            # 使用正则表达式处理文件并将数据填入表格中
            NoneFileList = []
            NotPassFileList = []
            # 供给错误文件使用
            now_file = ''
            for filename in pdf_file:
                now_file = filename
                self.log_record_show("现在进行解析的文件为:" + filename)
                with pdfplumber.open(filename) as pdf:
                    first_page = pdf.pages[0]
                    pdf_text = first_page.extract_text()

                    # pdf中需要包含的字符
                    pdf_text_calibration = ["名称", "纳税人识别号", "发票", "发票号码", "开票日期"]
                    # if '发票' not in pdf_text or '发票号码' not in pdf_text:
                    #     continue
                    # 优化发票的判定逻辑，只有在pdf中存在对应字符才进行对应的发票处理
                    flag = 0
                    for item in pdf_text_calibration:
                        # if item not in pdf_text:
                        #     self.log_record_show(f"'{item}' 在字符串中没有找到")
                        #     break
                        if item in pdf_text:
                            self.log_record_show(f"'{item}' 在字符串中找到")
                            flag += 1

                    if flag < 4:
                        self.log_record_show("未通过发票判定:" + filename, log_lever=logging.WARNING)
                        NotPassFileList.append(filename)
                        continue
                    self.log_record_show("通过发票判定:" + filename)
                    self.log_record_show("发票数据为:" + '-' * 50)
                    self.log_record_show(pdf_text)
                    # logging.info(pdf_text)
                    self.log_record_show('-' * 100)
                    #             logging.info(re_text(re.compile(r'[\u4e00-\u9fa5]+电子普通发票.*?'), pdf_text))
                    #             t2 = re_text(re.compile(r'[\u4e00-\u9fa5]+专用发票.*?'), pdf_text)
                    #             if t2:
                    #                 logging.info(t2)
                    # logging.info(re_text(re.compile(r'发票代码(.*\d+)'), pdf_text))
                    # logging.info(re_text(re.compile(r'发票号码(.*\d+)'), pdf_text))
                    # logging.info(re_text(re.compile(r'开票日期(.*)'), pdf_text))
                    # logging.info(re_text(re.compile(r'名\s*称\s*[:：]\s*([\u4e00-\u9fa5]+)'), pdf_text))
                    # logging.info(re_text(re.compile(r'名\s*称\s*[:：]\s*([\u4e00-\u9fa5|（|）]+)'), pdf_text))
                    # logging.info(re_text(re.compile(r'纳税人识别号\s*[:：]\s*([a-zA-Z0-9]+)'), pdf_text))
                    # logging.info(re_text(re.compile(r'税*额.*(.*[0-9.]+)'), pdf_text))
                    # fapiaodaima = re_text(re.compile(r'发票代码(.*\d+)'), pdf_text)

                    # jiaoyan = re_text(re.compile(r'校 验 码\s*[:：]\s*([a-zA-Z0-9 ]+)'), pdf_text)[-6:]

                    # logging.info(f'校验码：{jiaoyan}', xiaoxie, sep="\n")
                    # company = re.findall(re.compile(r'名.*称\s*[:：]\s*([\u4e00-\u9fa5]+)'), pdf_text)
                    # if company:
                    #     logging.info(re_block(company[len(company) - 1]))
                    #     gongsi = re_block(company[len(company) - 1])
                    # lst = [fapiaodaima[-12:],fapiaohaoma[-8:],kaipiaoriqi[5:],jiaoyan,xiaoxie[3:],gongsi]
                    # lst = [fapiaodaima[5:], fapiaohaoma[5:], kaipiaoriqi[5:], jiaoyan, xiaoxie, gongsi]

                    # "名称", "纳税人识别号", "发票", "发票号码", "开票日期", "小写"

                    # logging.info(re_text(re.compile(r'名\s*称\s*[:：]\s*([\u4e00-\u9fa5|（||）]+)'), pdf_text))
                    # 出现名称为空的问题,造成空指针异常
                    fapiaomingcheng = re_text(re.compile(r'名\s*称\s*[:：]\s*([\u4e00-\u9fa5()（）]+)'), pdf_text)
                    # logging.info(re_text(re.compile(r'纳税人识别号\s*[:：]\s*([a-zA-Z0-9 ]+)'), pdf_text))
                    nashuirensbh = re_text(re.compile(r'纳税人识别号\s*[:：]\s*([a-zA-Z0-9 ]{19})'), pdf_text)
                    fapiaohaoma = re_text(re.compile(r'发票号码(.*\d+)'), pdf_text)
                    kaipiaoriqi = re_text(re.compile(r'开票日期(.*)'), pdf_text)
                    xiaoxie = extract_amount(re_text(re.compile(r'小写.*(.*[0-9.]+)'), pdf_text))
                    # 格式处理
                    # 开票日期
                    kaipiaoriqi = dateExchange(kaipiaoriqi[5:])
                    pdf.close()
                    self.log_record_show("pdf读取完成,已关闭")
                    # 开始校验
                    self.log_record_show("开始进行信息校验")
                    # 增加判定条件，只有在各项均不为空的情况下才会将对应的数据进行填入，含有空值的文件另外保存下来
                    # 检查是否成功提取到名称
                    if fapiaomingcheng is None or nashuirensbh is None or fapiaohaoma is None or kaipiaoriqi is None or xiaoxie is None:
                        NoneFileList.append(filename)
                        self.log_record_show(
                            "列表中包含空值,已存入空值处理列表中,将进行下一张发票的识别,问题发票:" + filename,
                            log_lever=logging.ERROR)
                        continue
                    # 数据项(姓名,发票日期,发票号码,金额,类别,出差地点)
                    invoiceList = [kaipiaoriqi, fapiaohaoma[5:], xiaoxie]
                    if '客运服务费' in pdf_text:
                        invoiceList.append("市内交通")
                    elif '住宿' in pdf_text:
                        invoiceList.append("住宿")
                    elif '汽油' in pdf_text:
                        invoiceList.append("汽油")
                    elif '通行费' in pdf_text:
                        invoiceList.append("通行费")
                    elif '机票' in pdf_text:
                        invoiceList.append("机票")
                    else:
                        invoiceList.append("")

                    invoiceList.append(fapiaomingcheng[3:])
                    invoiceList.append(nashuirensbh[7:])
                    self.log_record_show("将要填入的发票数据为:" + list2str(invoiceList))

                    self.add_row(invoiceList)

            # 只有在存在空值文件时,才会生成出来
            self.fileSaveByType(NotPassFileList, '文件加载-判定非发票文件')
            if len(NoneFileList) != 0 or len(errorFileList) != 0:
                # 文件存储
                self.fileSaveByType(NoneFileList, '文件加载-空值(错误)文件')
                self.fileSaveByType(errorFileList, '文件加载-异常文件')

                self.log_record_show('存在空值文件或异常文件,需手工判定(已存入对应文件列表中)!!!',
                                     log_lever=logging.WARNING, is_show=1)
            else:
                self.log_record_show("文件加载完成", is_show=1)
                self.log_record_show("-" * 30 + "file load success!!!" + "-" * 30)

        except Exception as e:
            # errorFileList.append(now_file)
            errorFileStr = list2str(errorFileList)
            # 捕获异常并显示出问题的文件名称
            self.log_record_show("加载文件失败，程序异常！" + "错误文件地址：" + errorFileStr, log_lever=logging.ERROR)

    '''
        表格操作：
            列表生成
            表格添加行
            表格清空
            删除选中行
    '''

    # 列表生成
    def sheet_2_list(self):
        model = self.dataSheet
        data_list = []
        for row in range(model.rowCount()):
            row_list = []
            # logging.info(model.columnCount())
            for column in range(model.columnCount()):
                item = model.item(row, column).text()
                row_list.append(item)
            data_list.append(row_list)
            self.log_record_show("数据列表已生成")

        self.log_record_show("表格生成的数据列表为:" + list2str(data_list))
        return data_list

    # 表格添加行
    def add_row(self, data):
        # 检查data中发票号码是否重复，若重复，则不添加data[2]
        if data[1] not in self.dataList:
            if data[1] != '':
                self.dataList.append(data[1])
            row_count = self.dataSheet.rowCount()
            self.dataSheet.insertRow(row_count)

            for i in range(len(data)):
                item = QTableWidgetItem(str(data[i]))
                self.dataSheet.setItem(row_count, i, item)
            self.log_record_show("-" * 30 + "表格已添加行" + "-" * 30)

        else:
            # 跳过该行
            self.log_record_show("表格未添加行", logging.WARNING)
            # warnRes = QtWidgets.QMessageBox.warning(self, '提醒', '发票号码已记录,请勿重复添加！！！')
            self.log_record_show('包含重复发票号码，已自动跳过！！！', is_show=1)

    # 表格清空
    def clear_all(self):
        # self.dataSheet.clearContents()
        self.dataSheet.setRowCount(0)
        self.log_record_show("表格已清空", is_show=1)
        self.dataList = []
        self.log_record_show("-" * 30 + "表格已全部清空" + "-" * 30)

    # 删除选中行
    def del_select_row(self):
        row_select = self.dataSheet.selectedItems()
        if len(row_select) != 0:
            row = row_select[0].row()
            warnRes = QtWidgets.QMessageBox.warning(self, '是否删除', '您要删除的信息序号为：' + str(row) + '！！！',
                                                    QtWidgets.QMessageBox.Yes | QtWidgets.QMessageBox.Cancel)  # "退出"代表的是弹出框的标题,"你确认退出.."表示弹出框的内容
            # 4194304 取消  16384确定
            if warnRes == 16384:
                delInvoiceNum = self.dataSheet.item(row, 1).text()
                self.log_record_show("删除的行号为:" + delInvoiceNum)
                # 清除列表中数据
                if delInvoiceNum != '':
                    # 删除数值为 2 的元素
                    while delInvoiceNum in self.dataList:
                        self.dataList.remove(delInvoiceNum)
                self.dataSheet.removeRow(row)
                self.log_record_show("删除数据成功!!!", is_show=1)
                self.log_record_show("-" * 30 + "删除数据成功" + "-" * 30)
        else:
            warnRes = QtWidgets.QMessageBox.warning(self, '提醒', '您未选中任何信息！！！')
            self.log_record_show("未选中删除信息!!!", is_show=1)

    '''
        文件操作:
            文件分类
            pdf合并
    '''

    # 文件分类
    def docSaveByTypeBtnClicked(self):
        '''打开选择文件夹对话框'''
        try:
            self.log_record_show("开始进行文件分类")
            self.get_param_data()
            # 导入存储位置
            file_dialog = QtWidgets.QFileDialog()
            Filepath = file_dialog.getExistingDirectory()  # 获得选择好的文件夹名称
            if Filepath == '':
                sys.exit()
            pdfFileList = fileListCreate(Filepath, ".pdf")
            # 文件存储位置
            if self.save_file_path == '':
                QtWidgets.QMessageBox.warning(self, '提醒', '文件存储地址未初始化！！！')
                self.log_record_show("文件存储地址未初始化")
                return

            # 创建文件目录分级
            # 判断文件夹是否存在，若存在，则将文件移入
            # 指定文件夹路径
            if self.diff_name == '':
                folder_path = self.save_file_path
            else:
                folder_path = self.save_file_path + self.diff_name
            # 判断文件夹是否存在
            if os.path.exists(folder_path):
                self.log_record_show("文件夹存在")
            else:
                self.log_record_show("文件夹不存在，新建对应文件夹")
                # 创建新文件夹
                os.mkdir(folder_path)

            # 使用正则表达式处理文件并将数据填入表格中
            # 创建存储的二维列表
            jtSaveList = []
            zsSaveList = []
            txSaveList = []
            qtSaveList = []
            # 发票判定条件
            pdf_text_calibration = ["名称", "纳税人识别号", "发票", "发票号码", "开票日期"]
            for pdfName in pdfFileList:
                self.log_record_show("发票位置" + pdfName)

                with pdfplumber.open(pdfName) as pdf:
                    first_page = pdf.pages[0]
                    pdf_text = first_page.extract_text()

                    # 判定是否是发票
                    # pdf中需要包含的字符
                    # if '发票' not in pdf_text or '发票号码' not in pdf_text:
                    #     continue
                    # 优化发票的判定逻辑，只有在pdf中存在对应字符才进行对应的发票处理
                    flag = 0
                    flag_count = 0
                    for item in pdf_text_calibration:
                        # if item not in pdf_text:
                        #     self.log_record_show(f"'{item}' 在字符串中没有找到")
                        #     break
                        if item in pdf_text:
                            self.log_record_show(f"'{item}' 在字符串中找到")
                            flag_count += 1

                    if flag_count < 4:
                        self.log_record_show("未通过发票判定:" + pdfName, log_lever=logging.WARNING)
                        flag = 1

                    if flag == 1:
                        qtSaveList.append(pdfName)
                    elif '客运服务费' in pdf_text or '机票' in pdf_text:
                        jtSaveList.append(pdfName)
                    elif '住宿' in pdf_text:
                        zsSaveList.append(pdfName)
                    elif '汽油' in pdf_text or '通行费' in pdf_text:
                        txSaveList.append(pdfName)
                    else:
                        logging.info("其他")
                        qtSaveList.append(pdfName)

                pdf.close()

            # 存储对应列表中的文件
            self.fileSaveByType(jtSaveList, '文件分类-交通')
            self.fileSaveByType(zsSaveList, '文件分类-住宿')
            self.fileSaveByType(txSaveList, '文件分类-通行')
            self.fileSaveByType(qtSaveList, '文件分类-其他')

            self.log_record_show('文件分类完成!!!', is_show=1)
            self.log_record_show("-" * 30 + "文件分类完成" + "-" * 30)
        except:
            self.log_record_show('文件分类失败!!!', is_show=1)

    def pdfMergeBtnClicked(self):
        '''打开选择文件夹对话框'''
        try:
            self.log_record_show("pdf合并开始")
            self.get_param_data()
            # 导入存储位置
            file_dialog = QtWidgets.QFileDialog()
            Filepath = file_dialog.getExistingDirectory()  # 获得选择好的文件夹名称
            if Filepath == '':
                sys.exit()
            fileList = fileListCreate(Filepath)
            # 文件存储位置
            # 获取当前时间
            current_time = datetime.now()
            # 将时间格式化为字符串，作为文件名
            file_name = current_time.strftime("%Y-%m-%d_%H-%M-%S")
            pdfsMerge2Pdf(fileList, self.save_file_path + "/" + file_name + "合并的resultPdf.pdf")

            self.log_record_show('pdf合并成功!!!', is_show=1)
            self.log_record_show("-" * 30 + "pdf合并成功" + "-" * 30)
        except:
            self.log_record_show('pdf合并失败!!!', is_show=1)

    # 抬头校验
    def invHVerBtnClicked(self):
        self.log_record_show("开始校验抬头及税号", is_show=1)
        if self.load_file_path == '':
            QtWidgets.QMessageBox.warning(self, '提醒', '未加载文件,此功能将在加载文件后开启使用！！！')
            self.log_record_show("停止校验", is_show=1)
            return

        # 获取表格的所有信息
        self.get_param_data()
        data_list = self.sheet_2_list()

        # 要查询的公司名称
        target_company_name = self.inv_header_text

        # 遍历整个公司信息字典，找到匹配的公司信息
        found_company_info = None
        for company, info in self.companies.items():
            if info['CompanyName'] == target_company_name:
                found_company_info = info
                break

        # 打印匹配的公司信息
        if found_company_info:
            self.log_record_show("公司名称:" + found_company_info['CompanyName'])
            self.log_record_show("税号:" + found_company_info['TIN'])
            self.log_record_show("找到对应公司信息")

            specified_title = re_block(found_company_info['CompanyName'])
            specified_tax_id = re_block(found_company_info['TIN'])

            # 创建一个空列表来存储不一致的订单号
            inconsistent_invoices = []

            # 遍历invoice列表
            for invoice in data_list:
                # 提取订单的抬头和税号
                title = invoice[-2]
                tax_id = invoice[-1]

                # 检查抬头和税号是否与指定的抬头和税号一致
                if title != specified_title or tax_id != specified_tax_id:
                    # 如果不一致，则将订单号添加到不一致订单列表中
                    inconsistent_invoices.append(invoice[1])

            if len(inconsistent_invoices) == 0:
                self.log_record_show("抬头校验完成，全部通过", is_show=1)
                return

            # 输出不一致订单列表
            self.log_record_show("不一致发票号：" + list2str(inconsistent_invoices))
            # 根据订单号获取文件列表
            loadPath = self.load_file_path
            # 获取所有的pdf文件（若文件夹中出现压缩包，则解压缩）
            pdf_file_list = fileListCreate(loadPath, ".pdf")
            # 使用正则表达式处理文件并将数据填入表格中
            # 创建存储的二维列表
            inconsistent_invoice_list = []
            # 发票判定条件
            pdf_text_calibration = ["名称", "纳税人识别号", "发票", "发票号码", "开票日期"]
            for pdfName in pdf_file_list:
                # logging.info(pdfName)
                with pdfplumber.open(pdfName) as pdf:
                    first_page = pdf.pages[0]
                    pdf_text = first_page.extract_text()
                    # 判定是否是发票
                    # 判定是否是发票
                    # pdf中需要包含的字符
                    # if '发票' not in pdf_text or '发票号码' not in pdf_text:
                    #     continue
                    # 优化发票的判定逻辑，只有在pdf中存在对应字符才进行对应的发票处理
                    flag_count = 0
                    for item in pdf_text_calibration:
                        # if item not in pdf_text:
                        #     self.log_record_show(f"'{item}' 在字符串中没有找到")
                        #     break
                        if item in pdf_text:
                            self.log_record_show(f"'{item}' 在字符串中找到")
                            flag_count += 1

                    if flag_count < 4:
                        self.log_record_show("未通过发票判定:" + pdfName, log_lever=logging.WARNING)
                        continue
                    # 获取发票号码
                    fapiaohaoma = re_text(re.compile(r'发票号码(.*\d+)'), pdf_text)

                    invoiceNum = fapiaohaoma[5:]
                    for item in inconsistent_invoices:
                        if invoiceNum == item:
                            self.log_record_show("问题发票存储位置如下:" + pdfName)
                            inconsistent_invoice_list.append(pdfName)

                pdf.close()
            self.fileSaveByType(inconsistent_invoice_list, '抬头校验-抬头校验失败')
            self.log_record_show("抬头校验失败文件夹已导出", is_show=1)
        else:
            self.log_record_show("未找到匹配的公司信息", is_show=1)
        self.log_record_show('-' * 30 + 'Invoice heading verification' + '-' * 30)

    # def showBoard(self, msg, is_show=None, log_lever=None):
    def log_record_show(self, msg, log_lever=None, is_show=None):
        if self.is_logging_on == '1':
            if log_lever == logging.DEBUG:
                print("[DEBUG]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.debug(msg)
            elif log_lever == logging.INFO or log_lever == None:
                print("[INFO]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.info(msg)
            elif log_lever == logging.WARNING:
                print("[WARNING]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.warning(msg)
            elif log_lever == logging.ERROR:
                print("[ERROR]:", msg)
                # 在这里写入日志文件或者使用其他日志记录器
                logging.error(msg)
            else:
                raise ValueError("Invalid log level")

        if is_show:
            self.diffCalDisplay.setText(msg)

    # 文件存储
    def fileSaveByType(self, originalfileList, fileType=None):
        try:
            if len(originalfileList) == 0:
                return
            self.log_record_show("开始文件导出", is_show=1)
            # 判断文件夹是否存在，若存在，则将文件移入
            # 指定文件夹路径
            folder_path = self.save_file_path + self.diff_name + '/' + fileType + '/'

            # 目标文件夹路径
            destination_folder = folder_path
            # 如果目标文件夹不存在，则创建它
            if not os.path.exists(destination_folder):
                os.makedirs(destination_folder)
            # 检查目标文件夹是否可写
            if os.access(destination_folder, os.W_OK):
                for originalfile in originalfileList:
                    # 源文件路径
                    source_file = originalfile
                    # 如果目标文件夹可写，则检查是否包含相同文件
                    same_file_res = compare_files(source_file, destination_folder)
                    # 若存在相同文件，则跳过，不存在，则存储
                    if same_file_res:
                        self.log_record_show("存在相同文件，跳过", is_show=1)
                    else:
                        self.log_record_show("不存在相同文件，存储", is_show=1)
                        shutil.copy(source_file, destination_folder)
            else:
                self.log_record_show("目标文件夹不可写，无法复制文件。", is_show=1)
            self.log_record_show("文件导出完成", is_show=1)
        except:
            self.log_record_show("文件导出失败", is_show=1)


# 发票处理
# 金额处理
def extract_amount(text):
    match = re.search(r'\d+\.\d+|\d+', text)
    if match:
        return match.group()  # 返回匹配到的数字及小数点
    else:
        return None


def re_block(text):
    return text.replace(' ', '').replace('　', '').replace('（', '(').replace('）', ')').replace('：', ':')
    # return text.replace(' ', '').replace('　', '').replace('：', ':')


def re_text(bt, text):
    m1 = re.search(bt, text)
    if m1 is not None:
        return re_block(m1[0])


def dateExchange(publish_Time):
    array = time.strptime(publish_Time, u"%Y年%m月%d日")
    try:
        publishTime = time.strftime("%Y-%m-%d", array)
    except:
        logging.info("转换失败")
    return publishTime


# pdf合并功能
from PyPDF2 import PdfReader, PdfMerger


def pdfsMerge2Pdf(pdfList, savePath):
    result_pdf = PdfMerger()
    # 依次读取要合并的文件内容，并进行合并
    for pdf in pdfList:
        pattern = '.+\.(pdf)$'
        m = re.match(pattern, pdf, re.IGNORECASE)
        if m != None:
            logging.info(pdf + '匹配成功！')
            with open(pdf, 'rb') as fp:
                pdf_reader = PdfReader(fp)
                # 判断文件是否加密，如果加密了则跳过
                # if pdf_reader.isEncrypted:
                #     logging.info(f'忽略加密文件：{pdf}')
                #     continue
                result_pdf.append(pdf_reader)
                # result_pdf.append(pdf_reader,import_bookmarks=True)
        else:
            logging.info("匹配失败！")

    # 保存合并的pdf文件
    # result_pdf.write('resultPic.pdf')
    result_pdf.write(savePath)
    result_pdf.close()


# 文件遍历
def fileListCreate(fileDir, endMarker=None):
    # 遍历所有的文件,获取所有符合条件文件
    NDirFiles = []

    for root, sub_dirs, file_names in os.walk(fileDir):
        for name in file_names:
            if not endMarker or name.lower().endswith(endMarker):
                filepath = os.path.join(root, name)
                NDirFiles.append(filepath)

    # 将所有的文件添加到集合中
    logging.info(NDirFiles)
    return NDirFiles


# 文件对比，哈希值比较
def calculate_file_hash(file_path):
    # 使用 SHA-256 计算文件的哈希值
    sha256 = hashlib.sha256()
    with open(file_path, 'rb') as f:
        while True:
            data = f.read(65536)  # 以块的方式读取文件内容
            if not data:
                break
            sha256.update(data)
    return sha256.hexdigest()


def compare_files(source_file, target_folder):
    source_hash = calculate_file_hash(source_file)
    target_files = os.listdir(target_folder)
    for target_file in target_files:
        target_file_path = os.path.join(target_folder, target_file)
        if os.path.isfile(target_file_path):
            target_hash = calculate_file_hash(target_file_path)
            if source_hash == target_hash:
                logging.info(f"{source_file} 和 {target_file} 是相同的文件")
                return True
    logging.info(f"{source_file} 在目标文件夹中没有相同的文件")
    return False


# 列表转字符串
def list2str(listexample, delimiter=","):
    """
    将列表转换为字符串。

    参数：
    listexample: list，要转换的列表。
    delimiter: str，列表元素之间的分隔符，默认为逗号。

    返回值：
    str，转换后的字符串。
    """
    # 使用 join 方法将列表中的元素以指定的分隔符连接成一个字符串
    result = delimiter.join(map(str, listexample))
    return result
