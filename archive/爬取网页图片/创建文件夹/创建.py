import os
def dirCreate(imgAddress):

    #path = '../pics/imgs3'

    for i in range(len(imgAddress)):
        # 创建文件夹
        if not os.path.exists(imgAddress[i]):
            os.mkdir(imgAddress[i])
            with open('test.txt', 'a+') as f:
                f.write('文件夹 ' + imgAddress[i] + ' 已创建完毕！\n')
                f.close()
        imgAddress[i] += '//test'

