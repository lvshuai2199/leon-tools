from 爬取图片 import 多任务单次运行测试项
from 创建文件夹 import 创建
import datetime

if __name__ == '__main__':
    # 每个网站爬取1000张
    # 设置爬取的网址
    img_url = ['http://www.dmoe.cc/random.php',
               'https://api.lyiqk.cn/acg',
               'https://img.catct.cn/',
               'https://img.catct.cn/pixiv.php',
               'https://img.xjh.me/random_img.php?return=302',
               'https://api88.net/api/img/rand/',
               'https://api.uomg.com/api/rand.img3',
               'https://api.lyiqk.cn/miku']
    # 设置存储的位置
    imgAddress = ['pics//imgs2',
                  'pics//imgs8',
                  'pics//imgs12',
                  'pics//imgs13',
                  'pics//imgs14',
                  'pics//高质量',
                  'pics//买家秀',
                  'pics//imgs9',
                  ]
    checkAddress = imgAddress

    #根据列表创建文件夹
    创建.dirCreate(imgAddress)

    #print(imgAddress)
    # 设置起始数字
    #strNum = 1
    strNum = [
            501,
            802,
            501,
            501,
            501,
            501,
            1,
            1
            ]

    # 设置希望获取图片数量
    #picGetNum = 500
    picGetNum = [
            0,
            20,
            500,
            500,
            500,
            500,
            500,
            500
            ]

    for i in range(len(img_url)):
        # print(i)
        # print(img_url[i])
        # print(imgAddress[i])
        ti = datetime.datetime.now()
        staFi = str(ti) + u' url' + str(i) + ':' + str(img_url[i]) + ' 开始爬取！\n'
        print(staFi)
        #开始操作记录写入文件
        with open('test.txt', 'a+') as f:
            f.write('\n\n' + staFi)
            f.close()
        多任务单次运行测试项.picDownload(img_url[i], imgAddress[i], strNum[i], picGetNum[i])

