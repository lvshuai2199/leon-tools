# from PyPDF2 import PdfFileReader,PdfFileMerger
import re

from PyPDF2 import PdfReader, PdfMerger
import os
import sys

# 界面程序
import tkinter as tk
from tkinter import filedialog


def pdfMerge(pdfList):
    result_pdf = PdfMerger()
    # 依次读取要合并的文件内容，并进行合并
    for pdf in pdfList:
        pattern = '.+\.(pdf)$'
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
    result_pdf.write('resultPic.pdf')
    result_pdf.close()


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


'''打开选择文件夹对话框'''
root = tk.Tk()
root.withdraw()
Filepath = filedialog.askdirectory()  # 获得选择好的文件夹名称
if Filepath == '':
    sys.exit()
fileList = fileListCreate(Filepath)
pdfMerge(fileList)
