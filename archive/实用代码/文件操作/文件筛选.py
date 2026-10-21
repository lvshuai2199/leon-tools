import os
import re

fileDir = 'C:\\Users\\13326\\Desktop\\华为手机文件'
#将文件拷贝到哪一个文件夹
DefileDir = 'C:\\Users\\13326\\Desktop\\需要导入ios'
#输入文件的关键词
patt = '.+(video|wx|VID|2020).+$'

#显示当前平台下的文件分割符
print(os.sep)
#文件目录
#fileDir = "E:" + os.sep + "FileCopy"
#将分隔符放进路径
#设置要改变的文件夹目录



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

    num = 0

    for file in NDirFiles:
        #print(file)  **  以正则表达式匹配相匹配的对应项，符合条件的，进行转换操作
        # 匹配出 .doc 文件 或 .docx 文件
        # pattern = '.+\.(doc|docx)$'
        # m = re.match(pattern,file)
        # if m != None:
        #     print(file + '匹配成功！')

        m = re.match(patt,file)
        if m != None:
            os.remove(file)
            print('已删除：' + file)
        num += 1
    print(num)

if __name__ == '__main__':
    fun()

