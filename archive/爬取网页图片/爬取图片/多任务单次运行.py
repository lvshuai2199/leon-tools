import requests
import time
import datetime

def picDownload(img_url,imgAddress,strNum,picGetNum):
    # print(img_url)
    # print(imgAddress)
    endNum = strNum + picGetNum
    #最后爬取到什么位置停止进行爬取
    #print(endNum)
    while strNum < endNum:
        try:
            # 图片地址
            img = requests.get(img_url)
            # print(img.status_code)
            if img.status_code == 200:
                try:
                    # 图片定位
                    tempAddress = imgAddress + str(strNum) + '.jpg'
                    f = open(tempAddress, 'ab')  # 存储图片，多媒体文件需要参数b（二进制文件）
                    f.write(img.content)  # 多媒体存储content
                    time.sleep(1.0)
                    f.close()
                    print('图片' + str(strNum) + '保存于' + imgAddress + '成功！')
                    strNum += 1
                except:
                    with open('test.txt', 'a+') as f:
                        ti = time.strftime("%Y-%m-%d", time.localtime())
                        f.write(str(ti) + u' 图片保存失败\n')
                        f.close()
                    print('图片保存失败！')
                time.sleep(1.0)
        except:
            with open('test.txt', 'a+') as f:
                ti = time.strftime("%Y-%m-%d", time.localtime())
                f.write(str(ti) + u' url链接异常,正在重新进行链接并存入操作！\n')
                f.close()
            time.sleep(90)


if __name__ == '__main__':
    #每个网站爬取1000张
    # 设置爬取的网址
    img_url = ['http://www.dmoe.cc/random.php',
               'https://img.paulzzh.tech/touhou/random',
               'https://api.lyiqk.cn/acg',
               'https://img.catct.cn/',
               'https://img.catct.cn/pixiv.php',
               'https://img.xjh.me/random_img.php?return=302',
               'https://api88.net/api/img/rand/',
                'https://api.nmb.show/1985acg.php',
               'https://api.uomg.com/api/rand.img3',
               'https://api.lyiqk.cn/miku']
    # 设置存储的位置
    imgAddress = ['pics//imgs2//test','pics//imgs6//test','pics//imgs8//test','pics//imgs12//test','pics//imgs13//test','pics//imgs14//test','pics//高质量//test','pics//动漫//test']

    # 设置起始数字
    strNum = 1

    # 设置希望获取图片数量
    picGetNum = 1000

    picDownload(img_url[0], imgAddress[0], 1, picGetNum)
    picDownload(img_url[1], imgAddress[1], 1, picGetNum)
    '''
     for i in range(len(img_url)):
        # print(i)
        # print(img_url[i])
        # print(imgAddress[i])
        ti = time.strftime("%Y-%m-%d", time.localtime())
        staFi = str(ti) + u' url' + str(i) + ':' + str(img_url[i]) + ' 开始爬取！\n'
        print(staFi)
        with open('test.txt', 'a+') as f:
            f.write(staFi)
            f.close()
        picDownload(img_url[i],imgAddress[i],strNum,picGetNum)

    
    '''
