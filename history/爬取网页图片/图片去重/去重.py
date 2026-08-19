import os
import hashlib

#filedir = 'D:/programmer/workFiles/pythonFiles/爬取网页图片/pics/imgs2'

def filecount(DIR):
    filecount = len([name for name in os.listdir(DIR) if os.path.isfile(os.path.join(DIR, name))])
    return (filecount)


def md5sum(filename):
    f = open(filedir+'/'+filename, 'rb')
    md5 = hashlib.md5()
    while True:
        fb = f.read(8096)
        if not fb:
            break
        md5.update(fb)
    f.close()
    return (md5.hexdigest())


def delfile():
    all_md5 = {}
    dir =os.walk(filedir)
    for i in dir:
        for tlie in i[2]:

            if md5sum(tlie) in all_md5.values():
                os.remove(filedir+'/'+tlie)
                print(tlie)
            else:
                all_md5[tlie] = md5sum(tlie)


if __name__ == '__main__':
    # 设置存储的位置
    imgAddress = [
        '../pics/imgs2',
        '../pics/imgs6',
        '../pics/imgs8',
        '../pics/imgs9',
        '../pics/imgs12',
        '../pics/imgs13',
        '../pics/imgs14',
        '../pics/高质量',
        '../pics/桌面壁纸'
        ]
    sum = 0
    for i in range(len(imgAddress)):
        filedir = imgAddress[i]
        oldf = filecount(filedir)
        print('去重前有', oldf, '个文件\n请稍等正在删除重复文件...')
        delfile()
        newf = filecount(filedir)
        print('\n\n去重后剩', newf , '个文件')
        print('\n\n一共删除了', oldf - newf, '个文件\n\n')
        prData = '去重前有' + str(oldf) + '个文件\n' + '\n\n去重后剩' + str(newf) + '个文件' + '\n\n一共删除了' + str(oldf - newf) + '个文件\n\n' + '*'*50 +'\n\n'
        with open('去重结果.txt', 'a+') as f:
            f.write(prData)
            f.close()
        sum += newf

    print(sum)

