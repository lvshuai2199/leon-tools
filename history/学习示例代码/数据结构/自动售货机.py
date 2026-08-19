#判断是否还有剩余零钱,若有，则直接继续进行下一步操作
def syCoin():
    global flag
    #检测是否还有剩余零钱
    if numCoin <= 0:
        flag = 0
        #选择充值零钱或直接购买
        syCTyp = int(input('机器内无剩余五角硬币，请选择你要进行的操作：\n1.继续购买\t2.补充硬币\n'))
        if syCTyp == 2 :
            buyCoin()
    else:
        flag = 1
    #返回灯的信号
    return flag
#购买硬币
def buyCoin():
    global numCoin
    numCoin = int(input('输入你想要补充的五角硬币数量:'))
    global flag
    flag = 1
#投入硬币
def tb():
    tbTyp = int(input('选择你要投入的硬币：\n1.一元硬币\t2.五角硬币\n'))
    print('投入硬币选择：'+str(tbTyp))
    print('已投币')
    return tbTyp
#按下按钮
def pressButt():
    preTyp = int(input('选择你想要的饮料：\n1.橙汁\t2.啤酒\n'))
    print('按下按钮选择：' + str(preTyp))
    print('已按按钮')
    return preTyp
#找零系统，显示零钱找完的话投入一元硬币不做任何操作。零钱找完时为自动售货机添加零钱
def giveCoin(li):
    print(li)
    global numCoin
    if li[0] == 0:
        if li[1] == 1:
            print('不送出饮料，一元硬币已退回')
        elif li[1] == 2 :
            if li[2] == 1:
                print('送出橙汁!', end='')
            elif li[2] == 2:
                print('送出啤酒!', end='')
            print('无找零')
            global numCoin
            numCoin += 1
    else:
        if li[1] == 1:
            if li[2] == 1:
                print('送出橙汁!', end='')
            elif li[2] == 2:
                print('送出啤酒!', end='')
            print('找零五角')
            numCoin -= 1

        else:
            if li[2] == 1:
                print('送出橙汁!',end='')
            elif li[2] == 2:
                print('送出啤酒!',end='')
            print('无找零')
            numCoin += 1

if __name__ == '__main__':
    #flag = 0 灯亮；flag = 1 灯灭，程序可正常运行
    flag = 0
    #设置初始零钱数量，即5叫零钱的数量，当零钱归零时红灯亮
    numCoin = 0
    while(1):
        li = ['','','']
        #syCoin()
        li[0] = syCoin()
        print('剩余五角硬币数量：'+str(numCoin))
        #tb()
        li[1] = tb()
        #pressButt()
        li[2] = pressButt()
        #print(li)
        giveCoin(li)
        print('*'*50)

