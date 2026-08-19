import sys
import time

import pdfplumber
import re
import os
import xlwt

# 界面程序
import tkinter as tk
from tkinter import filedialog

# 创建工作簿
wb = xlwt.Workbook()
# 创建表单
sh = wb.add_sheet('sheet 1')
sh.write(0, 0, '日期')
sh.write(0, 1, '发票号')
sh.write(0, 2, '金额')
sh.write(0, 3, '姓名')



def re_text(bt, text):
    m1 = re.search(bt, text)
    if m1 is not None:
        return re_block(m1[0])


def re_block(text):
    return text.replace(' ', '').replace('　', '').replace('）', '').replace(')', '').replace('：', ':')


def get_pdf(dir_path):
    pdf_file = []
    for root, sub_dirs, file_names in os.walk(dir_path):
        for name in file_names:
            if name.endswith('.pdf'):
                filepath = os.path.join(root, name)
                pdf_file.append(filepath)
    return pdf_file

def get_file_path():
    '''打开选择文件夹对话框'''
    root = tk.Tk()
    root.withdraw()
    Filepath = filedialog.askdirectory()  # 获得选择好的文件夹名称
    if Filepath == '':
        sys.exit()
    # print(Filepath[-2:])
    # 设置文件夹名称
    dirName = Filepath[-2:]
    return Filepath

def dateExchange(publish_Time):
    array = time.strptime(publish_Time, u"%Y年%m月%d日")
    try:
        publishTime = time.strftime("%Y-%m-%d", array)
    except:
        print("转换失败")
    return publishTime




def read():
    filePath = get_file_path()
    filePathList = filePath.split('/')
    mingcheng = filePathList[len(filePathList) - 1]
    filenames = get_pdf(filePath)  # 修改为自己的文件目录
    row = 1
    for filename in filenames:
        print(filename)
        with pdfplumber.open(filename) as pdf:
            first_page = pdf.pages[0]
            pdf_text = first_page.extract_text()
            if '发票' not in pdf_text:
                continue
            # print(pdf_text)
            print('--------------------------------------------------------')
            #             print(re_text(re.compile(r'[\u4e00-\u9fa5]+电子普通发票.*?'), pdf_text))
            #             t2 = re_text(re.compile(r'[\u4e00-\u9fa5]+专用发票.*?'), pdf_text)
            #             if t2:
            #                 print(t2)
            # print(re_text(re.compile(r'发票代码(.*\d+)'), pdf_text))
            # print(re_text(re.compile(r'发票号码(.*\d+)'), pdf_text))
            # print(re_text(re.compile(r'开票日期(.*)'), pdf_text))
            # print(re_text(re.compile(r'名\s*称\s*[:：]\s*([\u4e00-\u9fa5]+)'), pdf_text))
            # print(re_text(re.compile(r'名\s*称\s*[:：]\s*([\u4e00-\u9fa5|（|）]+)'), pdf_text))
            # print(re_text(re.compile(r'纳税人识别号\s*[:：]\s*([a-zA-Z0-9]+)'), pdf_text))
            # print(re_text(re.compile(r'税*额.*(.*[0-9.]+)'), pdf_text))
            # fapiaodaima = re_text(re.compile(r'发票代码(.*\d+)'), pdf_text)
            fapiaohaoma = re_text(re.compile(r'发票号码(.*\d+)'), pdf_text)
            kaipiaoriqi = re_text(re.compile(r'开票日期(.*)'), pdf_text)
            # jiaoyan = re_text(re.compile(r'校 验 码\s*[:：]\s*([a-zA-Z0-9 ]+)'), pdf_text)[-6:]
            xiaoxie = re_text(re.compile(r'小写.*(.*[0-9.]+)'), pdf_text)
            # print(f'校验码：{jiaoyan}', xiaoxie, sep="\n")
            # company = re.findall(re.compile(r'名.*称\s*[:：]\s*([\u4e00-\u9fa5]+)'), pdf_text)
            # if company:
            #     print(re_block(company[len(company) - 1]))
            #     gongsi = re_block(company[len(company) - 1])
            # lst = [fapiaodaima[-12:],fapiaohaoma[-8:],kaipiaoriqi[5:],jiaoyan,xiaoxie[3:],gongsi]
            # lst = [fapiaodaima[5:], fapiaohaoma[5:], kaipiaoriqi[5:], jiaoyan, xiaoxie, gongsi]

            # 格式处理
            # 开票日期
            kaipiaoriqi = kaipiaoriqi[5:]
            kaipiaoriqi = dateExchange(kaipiaoriqi)
            lst = [kaipiaoriqi, fapiaohaoma[5:], xiaoxie[3:], mingcheng]
            for i in range(4):
                sh.write(row, i, lst[i])
            row += 1
            print('--------------------------------------------------------')

    return mingcheng


mingcheng = read()

# 保存
wb.save(mingcheng + r'的发票信息.xls')