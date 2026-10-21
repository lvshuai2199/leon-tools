import os

dirpath = "./lvshuai"
if not os.path.exists(dirpath):  # 判断存放图片的文件夹是否存在
    os.makedirs(dirpath)  # 若图片文件夹不存在就创建

# 遍历所有的文件
NDirFiles = []
fileDir = "./pdfs"
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
