#将word转换为pdf
import re

from win32com.client import Dispatch


# 设置pdf存放地址，pdf文件生成后，村梵高此文件夹位置，代码中存放位置也需要更改

#生成pdf文件
def funGeneratePDF(fileName):
    word = Dispatch('Word.Application')
    #word.Visible = 0 #后台运行，不显示
    word.Visible = 1  # 0:后台运行 1:前台运行（可见）
    word.DisplayAlerts = 0  # 不显示，不警告

    print('待转换文件：'+fileName)

    #print(os.path.join(currentPath,'1801班javaWeb讲义转换的word文档.doc'))
    doc = word.Documents.Open(fileName) #打开一个已有的word文档
    pattern = '.+\.doc$'
    m = re.match(pattern, fileName)
    if m != None:
        fileName = fileName.replace('.doc','.pdf')
    else:
        fileName = fileName.replace('.docx','.pdf')
    doc.SaveAs(fileName,17)  # txt = 4 ,html = 10,docx = 16, pdf = 17
    doc.Close()
    word.Quit()
    print(fileName + '  ** 文件已成功转换为pdf！')


#导入包
from docx import Document
#win32com将  doc  转为  docx
from win32com import client as wc
def TransDocToDocx(oldDocName,newDocxName):
    #新建文件，注：利用线程的方式将文件转换变为线程程序存放于计算机中，若有文件存在，则将其转换为 .docx 格式
    document = Document()
    document.save('旧doc格式文档.doc')

    print("正在将文件"+oldDocName+"转换为"+newDocxName)
    #打开word
    word = wc.Dispatch('Word.Application')

    #打开旧word文件
    doc = word.Documents.Open(oldDocName)

    #保存为新word文件，其中参数12表示的是docx文件
    doc.SaveAs(newDocxName,12)

    #关闭word文档
    doc.Close()
    word.Quit()

    print("文件转换完毕！")