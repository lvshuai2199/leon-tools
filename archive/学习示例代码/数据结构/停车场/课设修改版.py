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
stack= SqStack()
stack=[]
#链队
class LinkNode:                 #链队结点类
  def __init__(self,data=None): #构造方法
    self.data=data                  #data属性
    self.next=None                  #next属性
class LinkQueue:      		#链队类
  def __init__(self):                #构造方法
    self.front=None                 	#队头指针
    self.rear=None                  	#队尾指针
def empty(self):		#判断队是否为空
  return self.front==None
def append(self,e):		#元素e进队
  s=LinkNode(e)             	#新建结点s
  if self.empty():		#原链队为空
    self.front=self.rear=s
  else:				#原链队不空
    self.rear.next=s		#将s结点链接到rear结点后面
    self.rear=s
def pop(self):				#出队操作
  assert not self.empty()		#检测空链队
  if self.front==self.rear:		#原链队只有一个结点
    e=self.front.data			#取首结点值
    self.front=self.rear=None		#置为空队
  else:					#原链队有多个结点
    e=self.front.data			#取首结点值
    self.front=self.front.next	#front指向下一个结点
  return e
def gethead(self):   		#取队头元素
  assert not self.empty()	#检测空链队
  e=self.front.data		#取首结点值
  return e
queue = LinkQueue()
# queue = []
import time
time.asctime()
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


if __name__ == '__main__':
 """
 需要实现功能 1.停车 2.出场 3. 查询 .4 退出
 """
 while True:
  choice = input("1,停车 2,出场,3.查询 ,4.退出。请输入你所需要查询的功能：")
  if choice == '1':
   """先判断是否有空车位"""
   # 选择停车，判断是否还有停车位
   if 1 - len(stack) == 0:
    plate_number = int(input("请输入你的车牌:"))
    queue.append(plate_number)
    print("汽车停靠在通道".format(len(queue)))
   else:
    print("恭喜你,还有%d车位"%(1-len(stack)))
    carname = input("停车费为 停车收费5元/小时;,请输入你的名字:")
    plate_number = int(input("请输入你的车牌:"))
    starttime = time.time() # 记录当前时间
    carname = car(plate_number, starttime, carname, ) # 新建对象
    stack.append(carname) # 将汽车对象存入停车列表
    print("%s的车牌号为%s的车进场了 当前时间%s" % (carname.get_carname(), plate_number,time.ctime()))
  elif choice == '2':
   # 第一步先查询汽车是否存在
   plate_numbers = int(input("请输入你的车牌:"))
   for i in stack:
    if plate_numbers == i.get_plate_number():
     end_time = time.time()
     times = end_time-i.get_starttime()
     print("你的车辆停在停车场停了%f小时,应该付款%d元" % (times/3600, (times / 3600) * 5))
     stack.pop()
     if len(queue) > 0 and 1 - len(stack) > 0:
       temp_plate_number = queue[0]  # 如果通道还有车
       queue.pop(temp_plate_number)
       stack.append(temp_plate_number)  # 将通道中的车移动到车库
       starttime[temp_plate_number] = time.time  # 记录1汽车入库时间
    else:
     queue.pop()
     print("你的车停在通道上，无需付款")
  elif choice == '3':
   plate_numbers = int(input("请输入你的车牌"))
   for i in stack:
    if plate_numbers == i.get_plate_number():
     end_time = time.time()
     times = end_time - i.get_starttime()
     print("你的车辆停在停车场停了%f小时,应该付款%d元" % (times / 3600, (times / 3600) * 5))
    else:
     print("你的车辆停在通道上，无需付款")
  else:
    break
print("感谢您的使用,再见")