# 导入time模块。并进行time初始化
import time
time.asctime()

#顺序栈
class SqStack:
    def __init__(self):         #构造方法
        self.data=[]              #存放栈中元素                                                                                                                                                    #
    def append(self,e):              #元素e进栈
        self.data.append(e)
    def pop(self):        		#元素出栈
        assert not self.empty()     #检测栈为空
        return self.data.pop()
    def empty(self):        	#判断栈是否为空
        if len(self.data)==0:
            return True
        return False
    def gettop(self):      	#取栈顶元素
        assert not self.empty()   #检测栈为空
        return self.data[-1]
    def lenSt(self):
        return len(self.data)

    # 判断是否在栈内
    def inSt(self,e):
        flag = 0
        for i in self.data:
            if e == i.get_plate_number():
                flag = 1
                break
        return flag

    # 指定元素出栈
    def outSt(self,e):
        list1 = []
        for i in range(len(self.data)-1,-1,-1):
            if e != self.data[i].get_plate_number():
                list1.append(self.data[i])
                self.data.pop()
            else:
                end_time = time.time()
                times = end_time - self.data[i].get_starttime()
                print("你的车辆停在停车场停了%f小时,应该付款%d元" % (times / 3600, (times / 3600) * holdPrice))
                self.data.pop()
                break
        # 获取到出停车场的车后，重新将停车场车辆装入栈中
        for j in range(len(list1)-1,-1,-1):
            self.data.append(list1[j])


    def checkSt(self,e):
        for i in self.data:
            if e == i.get_plate_number():
                end_time = time.time()
                times = end_time - i.get_starttime()
                print("你的车辆停在停车场停了%f小时,应该付款%d元" % (times / 3600, (times / 3600) * holdPrice))
            else:
                print("你的车辆停在通道上，无需付款")

#队列中的元素
class LinkNode:                        #链队结点类
  def __init__(self,data=None):       #构造方法
    self.data=data                      #data属性
    self.next=None                     #next属性

class LinkQueue:      		#链队类
    def __init__(self):                #构造方法
        self.front=None                 	#队头指针
        self.rear=None                  	#队尾指针
        self.size = 0

    def empty(self):  # 判断队是否为空
        return self.front == None

    def append(self,e):		#元素e进队
        s=LinkNode(e)             	#新建结点s
        if self.empty():		#原链队为空
            self.front=self.rear=s
        else:				#原链队不空
            self.rear.next=s		#将s结点链接到rear结点后面
            self.rear=s
        self.size += 1
    def pop(self):				#出队操作
        # assert not self.empty()		#检测空链队
        if self.front==self.rear:		#原链队只有一个结点
            e=self.front.data			#取首结点值
            self.front=self.rear=None		#置为空队
        else:					#原链队有多个结点
            e=self.front.data			#取首结点值
            self.front=self.front.next	#front指向下一个结点
        self.size -= 1
        return e
    def gethead(self):   		#取队头元素
        assert not self.empty()	#检测空链队
        e=self.front.data		#取首结点值
        return e
    # 获取队列长度
    def getSize(self):
        return self.size
    def outPoint(self,e):
        assert not self.empty()  # 检测空链队
        # 如果首项相同，并且循环队列中只包含一个元素，则首项位置转移
        if self.front.data == e:
            if self.front == self.rear:  # 原链队只有一个结点
                self.front = self.rear = None  # 置为空队
            else:  # 原链队有多个结点
                self.front = self.front.next
        else:
            # temp = self.front
            #
            # while (temp.next != self.rear):
            #     temp2 = temp.next
            #     if e == temp2.data:
            #         temp.next = temp2.next
            #         break
            #     temp = temp.next
            # self.size -= 1
            # 如果为非首项
            tempL = LinkQueue()
            while(self.front != None):
                if self.front.data != e:
                    tempL.append(self.front.data)
                self.front = self.front.next
            self.front = tempL.front
            self.rear = tempL.rear
            self.size = tempL.size


# 获取车辆的相关信息
class car(object):
    """定义一个车包括 车主人名 车牌 开始停放时间"""
    def __init__(self, plate_number, starttime,caename, ):
        super(car, self).__init__()
        self.plate_number = plate_number
        self.starttime = starttime
        self.carname = carname
    def get_plate_number(self):
        return self.plate_number
    def get_starttime(self):
        return self.starttime
    def get_carname(self):
        return self.carname



# 定义变量
stack = SqStack()
queue = LinkQueue()
# 车位数量
cwNum = 1
# 停车费
holdPrice = 5

if __name__ == '__main__':

    """
     需要实现功能 1.停车 2.出场 3. 查询 .4 退出
     """
    while True:
        choice = input("1,停车 2,出场,3.查询 ,4.退出。请输入你所需要查询的功能：")
        if choice == '1':
            """先判断是否有空车位"""
            # 选择停车，判断是否还有停车位，无则停入过道
            if cwNum - stack.lenSt() == 0:
                plate_number = int(input("请输入你的车牌:"))
                queue.append(plate_number)
                print("汽车停靠在通道"+ str(queue.size))
            else:
                print("恭喜你,还有%d车位" % (cwNum - stack.lenSt()))
                carname = input("停车费为 停车收费" + str(holdPrice) + "元/小时;,请输入你的名字:")
                plate_number = int(input("请输入你的车牌:"))
                starttime = time.time()  # 记录当前时间
                carname = car(plate_number, starttime, carname, )  # 新建对象
                stack.append(carname)  # 将汽车对象存入停车列表
                print("%s的车牌号为%s的车进场了 当前时间%s" % (carname.get_carname(), plate_number, time.ctime()))
        elif choice == '2':
            # 第一步先查询汽车是否存在
            plate_numbers = int(input("请输入你的车牌:"))
            if stack.inSt(plate_numbers):
                # 出停车场
                stack.outSt(plate_numbers)
                # 队列中一辆车放入停车场
                if queue.getSize() > 0:
                    # print(queue.pop())
                    carname = input("停车费为 停车收费" + str(holdPrice) + "元/小时;,请输入你的名字:")
                    plate_number = queue.pop()
                    starttime = time.time()  # 记录当前时间
                    carname = car(plate_number, starttime, carname, )  # 新建对象
                    stack.append(carname)  # 将汽车对象存入停车列表
                else:
                    print("通道内无车！")
            else:
                print("停车场内未找到此车")
                # 通道走车
                queue.outPoint(plate_numbers)
        elif choice == '3':
            plate_numbers = int(input("请输入你的车牌"))
            stack.checkSt(plate_numbers)
        else:
            break
        print("*" * 50)
    print("感谢您的使用,再见")