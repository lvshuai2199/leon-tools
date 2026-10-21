import os
import hashlib

# filedir = 'C:/Users/13326/Desktop/整合'
filedir = 'D:/phonePhoto/相机胶卷'



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
    dir = os.walk(filedir)
    for i in dir:
        for tlie in i[2]:

            if md5sum(tlie) in all_md5.values():
                os.remove(filedir + '/' + tlie)
                print(tlie)
            else:
                all_md5[tlie] = md5sum(tlie)


if __name__ == '__main__':

    oldf = filecount(filedir)
    print('去重前有', oldf, '个文件\n请稍等正在删除重复文件...')
    delfile()
    newf = filecount(filedir)
    print('\n\n去重后剩', newf , '个文件')
    print('\n\n一共删除了', oldf - newf, '个文件\n\n')
