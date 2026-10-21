import requests
import time

#设置爬取的网址
img_url = 'https://api.nmb.show/1985acg.php'
#设置存储的位置
imgAddress = '..//pics//动漫//test'
#设置起始数字
strNum = 1
#设置结束次数
endNum = 500
while strNum < endNum:
    try:
        # 图片地址
        img = requests.get(img_url)
        # print(img.status_code)
        if img.status_code == 200:
            try:
                # 图片定位
                tempAddress = imgAddress + str(strNum) + '.png'
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
            f.write(str(ti) + u' url链接异常\n')
            f.close()
        time.sleep(90)