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
                        ti = datetime.datetime.now()
                        f.write(str(ti) + u' 图片保存失败\n')
                        f.close()
                    print('图片保存失败！')
                time.sleep(1.0)
        except:
            with open('test.txt', 'a+') as f:
                ti = datetime.datetime.now()
                f.write(str(ti) + u' url链接异常,正在重新进行链接并存入操作！\n')
                f.close()
            time.sleep(90)


