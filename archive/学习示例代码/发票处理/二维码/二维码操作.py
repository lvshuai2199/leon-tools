'''
该项目主要内容：
1. 读取文件夹内所有的发票信息
2. 发票pdf转图片
3. 图片遍历扫描生成list
4. 遍历完成导出excel表格
'''
import os
# 文件操作库
import datetime
import sys

import fitz
# 二维码识别库
import pyzbar.pyzbar as pyzbar
# 实际为安装了pillor库进行访问
from PIL import Image
# excel操作
from openpyxl import Workbook
# 配置文件
import configparser

# 界面程序
import tkinter as tk
from tkinter import filedialog


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


# pdf转图片
def pdf2png(pdfPath, imagePath, imgName):
    startTime_pdf2img = datetime.datetime.now()  # 开始时间

    print("imagePath=" + imagePath)
    pdfDoc = fitz.open(pdfPath)
    for pg in range(pdfDoc.pageCount):
        page = pdfDoc[pg]
        rotate = int(0)
        # 每个尺寸的缩放系数为1.3，这将为我们生成分辨率提高2.6的图像。
        # 此处若是不做设置，默认图片大小为：792X612, dpi=96
        # zoom_x = 1.33333333  # (1.33333333-->1056x816)   (2-->1584x1224)
        zoom_x = 2.0  # (1.33333333-->1056x816)   (2-->1584x1224)
        # zoom_y = 1.33333333
        zoom_y = 2.0
        mat = fitz.Matrix(zoom_x, zoom_y).preRotate(rotate)
        pix = page.getPixmap(matrix=mat, alpha=False)

        # 一个pdf中存在多张图片，命名更改
        if not os.path.exists(imagePath + '/' + imgName + '%s.png' % pg):
            pix.writePNG(imagePath + '/' + imgName + '_%s.png' % pg)  # 将图片写入指定的文件夹内

    endTime_pdf2img = datetime.datetime.now()  # 结束时间
    print('pdf2img时间=', (endTime_pdf2img - startTime_pdf2img).seconds)


def QRCodeCreate(image):
    '''
    示例数据：
    01,10,031002200511,71683020,3.30,20230721,03677848839994463406,7CBE,
    对应位为：
    *，*，发票代码，发票号码，金额，日期，校验码，*

    还需增加，扫描图片不是发票情况，拟采用异常捕获实现，不会中断程序
    '''
    # image = './电子_生成文件/imgs/pdf0_0.png'
    img = Image.open(image)

    # 解码 image 中的 datamatrix 条形码
    barcodes = pyzbar.decode(img)

    barcodeData = ""
    for barcode in barcodes:
        barcodeData += barcode.data.decode("utf-8")

    print(barcodeData.split(','))

    return barcodeData.split(',')


def caiwuList(QRCode,dirName):

    del QRCode[0:3]
    del QRCode[3:6]
    # print(QRCode)
    # 数据值重构，生成数据信息
    QRCodeRe = []

    # 日期格式转换
    dateRe = ''
    dateRe += QRCode[2][0:4] + '-'
    dateRe += QRCode[2][4:6] + '-'
    dateRe += QRCode[2][-2:]

    QRCodeRe.append(dateRe)

    QRCodeRe.append(int(QRCode[0]))
    QRCodeRe.append(float(QRCode[1]))
    QRCodeRe.append(dirName)
    print(QRCodeRe)
    return QRCodeRe


if __name__ == '__main__':

    # 读取配置文件，若配置文件存在，读取对应项，不存在，则取默认值
    if os.path.exists('conf.ini'):
        cfp = configparser.ConfigParser()
        cfp.read("conf.ini")
        value = cfp.get("QRScan", "chooseDir")
    else:
        value = '0'
    # 根据配置文件中的值判断是采用默认方式还是弹窗选择
    if value == '1':
        '''打开选择文件夹对话框'''
        root = tk.Tk()
        root.withdraw()
        Filepath = filedialog.askdirectory()  # 获得选择好的文件夹名称
        if Filepath == '':
            sys.exit()
        # print(Filepath[-2:])
        # 设置文件夹名称
        dirName = Filepath[-2:]
    else:
        Filepath = "./pdfs"
        # 设置文件夹名称
        dirName = '吕帅'
    # 图片存储路径
    imgDirName = './' + dirName + '_生成文件/imgs'
    fpDirName = './' + dirName + '_生成文件/'
    # 文件遍历
    fileList = fileListCreate(Filepath)
    # 判断存放图片的文件夹是否存在
    if not os.path.exists(imgDirName):
        os.makedirs(imgDirName)

    # 文件名称也会进行修改, 添加一个单元是为了确保pdf的唯一性，目录结构更加清晰
    for index,pdfItem in enumerate(fileList):
        pdf2png(pdfItem, imgDirName,'pdf'+str(index))
    imgList = fileListCreate(imgDirName)
    # print(imgList)
    # 传入图片地址
    # 生成date数据项
    # QRCodeList
    QRCodeList = []
    # 直接存储
    outwb = Workbook()
    outws = outwb.worksheets[0]
    # * ， * ，发票代码，发票号码，金额，日期，校验码， *
    outws.append(['日期', '发票号', '金额', '姓名'])  # 先添加一行表头
    for index,imgItem in enumerate(imgList):
        tempList = caiwuList(QRCodeCreate(imgItem),dirName)
        outws.append(tempList)

    # 在上面操作中存在创建文件夹操作
    outwb.save(fpDirName + dirName+ r'发票数据.xlsx')
    print('数据存入excel成功')







