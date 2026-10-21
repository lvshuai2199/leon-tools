import os
import re

import TransWordToPDF

print(os.sep) #显示当前平台下的文件分割符
#文件目录
#fileDir = "E:" + os.sep + "FileCopy"
#将分隔符放进路径
#设置要改变的文件夹目录
fileDir = os.sep.join(["D:","shuaishuaiNiubi"])

def fun():
    NDirFiles = []
    for root, dirs, files in os.walk(fileDir):
        print('the path is ...')
        print(root)
        print('the current directories under current directory:')
        print(dirs)
        print('the files in current directory:')
        print(files)
        for file in files:
            NDirFiles.append(os.path.join(root,file))
        print('')
    #将所有的文件添加到集合中
    print(NDirFiles)

    for file in NDirFiles:
        #print(file)  **  以正则表达式匹配相匹配的对应项，符合条件的，进行转换操作
        # 匹配出 .doc 文件 或 .docx 文件
        pattern = '.+\.(doc|docx)$'
        m = re.match(pattern,file)
        if m != None:
            print(file + '匹配成功！')
            TransWordToPDF.funGeneratePDF(file)



def fun1():
    fileList = os.listdir(fileDir)
    for file in fileList:
        print(file)
