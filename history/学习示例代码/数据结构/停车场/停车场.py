class SqStack:
    def __init__(self):
        self.data=[]
    def push(self,e):               #元素e进展
        self.data.append(e)          #data归属于self
    # def pop(self):
    #     assert len(self.data)!=0       #出栈要判断是否有元素，不等于0出栈
    #     return self.data.pop()
    def popExp(self,e):
        tempList = []
        assert len(self.data)!=0       #出栈要判断是否有元素，不等于0出栈
        for i in range(len(self.data), 0 , -1):
            if self.data[i-1] == e:
                self.data.pop()
                break
            tempList.append(self.data[i-1])
            self.data.pop()
        tempList.reverse()
        for tempE in tempList:
            s.push(tempE)
    def chang(self):
        return  len(self.data)
    # def shuchu(self,e):
    #     postexp.append(e)
    #     return postexp
    def shuchu(self):
        postexp = []
        for i in range(len(self.data)):
            postexp.append(self.data[i])
        return postexp

    def inStack(self,e):
        flag = 0
        for i in range(len(self.data)):
            postexp.append(self.data[i])
            if self.data[i] == e:
                flag = 1
                break
        return flag




#stack = list()                        #用列表实现栈
class LinkNode:                        #链队结点类
  def __init__(self,data=None):       #构造方法
    self.data=data                      #data属性
    self.next=None                     #next属性

class LinkQueue:                     # 链队类
  def __init__(self):                # 构造方法
    self.front = None                # 队头指针
    self.rear = None                 # 队尾指针

  # 队列的基本运算算法
  def empty(self):                  # 判断队是否为空
    return self.front == None

  def push(self, e):  # 元素e进队
    s = LinkNode(e)  # 新建结点s
    if self.empty():  # 原链队为空
      self.front = self.rear = s
    else:  # 原链队不空
      self.rear.next = s  # 将s结点链接到rear结点后面
      self.rear = s

  def outList(self):  # 出队操作
    assert not self.empty()  # 检测空链队
    if self.front == self.rear:  # 原链队只有一个结点
      e = self.front.data  # 取首结点值
      self.front = self.rear = None  # 置为空队
    else:  # 原链队有多个结点
      e = self.front.data  # 取首结点值
      self.front = self.front.next  # front指向下一个结点
    return e

  def outOne(self,e):
      # 由于是队列的原因，采用先进先出原则
      assert not self.empty()  # 检测空链队
      # 如果首项相同，并且循环队列中只包含一个元素，则首项位置转移
      if self.front.data == e :
          if self.front == self.rear:  # 原链队只有一个结点
              self.front = self.rear = None  # 置为空队
          else:  # 原链队有多个结点
              self.front = self.front.next
      else:
          # 如果为非首项
          tempL = LinkQueue()
          # temp = self.front
          # while (self.front.next != None):
          #     # 找到指定的元素
          #     if self.front.next.data == e:
          #         self.front = self.front.next.next
          #         break
          while(self.front != None):
              if self.front.data != e:
                  tempL.push(self.front.data)
              self.front = self.front.next
          self.front = tempL.front
          self.rear = tempL.rear

  def chang1(self):
      return n-s.chang()
  # def shuchu1(self,e):
  #     exp = []
  #     exp.append(e)
  #     return exp

  def shuchu1(self):
      temp = self.front
      exp = []
      while(temp != None):
          exp.append(temp.data)
          temp = temp.next
      return exp

  def inList(self,e):
      flag = 0
      temp = self.front
      while (temp != None):
          if temp.data == e:
              flag =1
              break
          temp = temp.next
      return flag
  # 获取队列长度
  def getListLen(self):
      len = 0
      temp = self.front
      while (temp != None):
          len += 1
          temp = temp.next
      return len




stack = list()
queue = list()
db = dict()
l = LinkQueue()
s = SqStack()
postexp=[]
exp=[]


def hello(n, price):

    print("请输入汽车是到达还是离去（到达/离去）：")
    car_status = input()
    print("请输入车牌号：")
    car_id = input()
    print("请输入到达或离去时刻（整数）：")
    car_time = int(input())


    # 如果是汽车到达
    if car_status == "到达":
        # 判断停车库满没满
        if s.chang() == n:
            # 满了就停在通道
            l.push(car_id)
            db[car_id] = car_time # 记录一下这辆车入库时间
            # 返回值为n-s的长度
            # 错误点
            print("汽车停靠在通道，车位是{}".format(l.chang1()))

        else:
            # 没满直接停到车库
            s.push(car_id)
            db[car_id] = car_time # 记录汽车入库时间
            print("汽车停靠在停车场，车位是{}".format (s.chang()))
    else:
        # 如果是汽车离开，先判断在不在通道中
        if l.inList(car_id):
            print("停靠在通道，无需收取费用")
            l.outOne(car_id) # 从通道中清除车
            del db[car_id] # 从系统中清除车数据
            # 再判断是不是在车库
        elif s.inStack(car_id):
            # 判断是否已在停车位，如果在的话弹出栈并重新压栈
            cost = price * (car_time - db[car_id])  # 计算停车费
            print("停车消费{:.2f}".format(cost))
            s.popExp(car_id) # 从停车场中移除车
            # 车辆入库
            if l.getListLen() > 0:
                temp_car_id = l.outList() # 如果通道还有车
                s.push(temp_car_id) # 将通道中的车移动到车库
                db[temp_car_id] = car_time # 记录汽车入库时间
        else:
            print("输入无效，停车场内暂无车辆")

    print("此时的停车场信息如下:")
    print(s.shuchu())
    print("此时的通道信息如下:")
    print(l.shuchu1())
    # if s.chang()<n:
    #     print(s.shuchu(car_id))
    #     print("此时的通道信息如下:")
    #     print()
    # else:
    #     print(l.shuchu1(car_id))




if __name__ == '__main__':
    print("请输入车库的容量:")
    n = int(input())
    print("请输入停车费单价:")
    price = float(input())
    # n = 2
    # price = 5
    while True:
        hello(n, price)