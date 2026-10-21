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

    def push(self,e):		#元素e进队
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
    # 获取学校分数
    def getSchoolScore(self,e):
        temp = self.front
        scSc = {}
        while(temp):
            scName = temp.data.getScName()
            scScore = temp.data.getScScore()
            flag = 0
            for key in scSc:
                if scName == key:
                    scSc[key] = scSc[key] + scScore
                    flag = 1
            if flag == 0:
                scSc[scName] = scScore
            temp = temp.next
        # 0为直接输出，1为排序输出
        if e == 0:
            print(scSc)
        else:
            scName = []
            scScore = []
            for key in scSc:
                scName.append(key)
                scScore.append(scSc[key])

            for i in range(1, len(scScore)):
                for j in range(0, len(scScore) - i):
                    if scScore[j] < scScore[j + 1]:
                        scScore[j], scScore[j + 1] = scScore[j + 1], scScore[j]
                        scName[j], scName[j + 1] = scName[j + 1], scName[j]

            for i in range(len(scName)):
                print(scName[i] + ':' + str(scScore[i]),end=";")
            print("")
    # 男女
    def getBGSc(self):
        temp = self.front
        boySc = {}
        girlSc = {}
        while (temp):
            scName = temp.data.getScName()
            scScore = temp.data.getScScore()
            eveName = temp.data.getEveName()
            eveType = temp.data.getEveType()
            flag = 0
            if eveType == "1":
                for key in boySc:
                    if eveName == key:
                        boySc[key] = boySc[key] + scScore
                        flag = 1
                if flag == 0:
                    boySc[eveName] = scScore
            else:
                for key in girlSc:
                    if eveName == key:
                        girlSc[key] = girlSc[key] + scScore
                        flag = 1
                if flag == 0:
                    girlSc[eveName] = scScore
            temp = temp.next
        print("男生项目得分：")
        print(boySc)
        print("女生项目得分：")
        print(girlSc)

    # 活动
    def getScEve(self,e):
        temp = self.front
        scSc = {}
        while (temp):
            scName = temp.data.getScName()
            scScore = temp.data.getScScore()
            eveName = temp.data.getEveName()
            flag = 0
            if e == scName:
                for key in scSc:
                    if eveName == key:
                        scSc[key] = scSc[key] + scScore
                        flag = 1
                if flag == 0:
                    scSc[eveName] = scScore
            temp = temp.next
        print(scSc)
    # 获取项目前五学校
    def getFirstFive(self,e):
        temp = self.front
        scSc = {}
        while (temp):
            scName = temp.data.getScName()
            scScore = temp.data.getScScore()
            eveName = temp.data.getEveName()
            flag = 0
            if e == eveName:
                for key in scSc:
                    if scName == key:
                        scSc[key] = scSc[key] + scScore
                        flag = 1
                if flag == 0:
                    scSc[scName] = scScore
            temp = temp.next

        scName = []
        scScore = []
        for key in scSc:
            scName.append(key)
            scScore.append(scSc[key])
        for i in range(1, len(scScore)):
            for j in range(0, len(scScore) - i):
                if scScore[j] < scScore[j + 1]:
                    scScore[j], scScore[j + 1] = scScore[j + 1], scScore[j]
                    scName[j], scName[j + 1] = scName[j + 1], scName[j]
        if len(scName) > 5:
            for i in range(5):
                print(scName[i] + ':' + str(scScore[i]), end=";")
        else:
            for i in range(len(scName)):
                print(scName[i] + ':' + str(scScore[i]), end=";")
        print("")

# 获取得分的相关信息
class schoolScore(object):
    """
    定义一个得分项
    项目名称
    项目类型
    学校
    得分
    """
    def __init__(self, eveName, eveType, sc, scScore):
        super(schoolScore, self).__init__()
        self.eveName = eveName
        self.eveType = eveType
        self.sc = sc
        self.scScore = scScore
    def getEveName(self):
        return self.eveName
    def getEveType(self):
        return self.eveType
    def getScName(self):
        return self.sc
    def getScScore(self):
        return self.scScore
# 建立一个全局变量，以存储相关数据
queue = LinkQueue()
if __name__ == '__main__':
    # 读取文件
    # 项目名称
    # 项目类型
    # 学校
    # 得分
    with open(r"testData.txt", "r", encoding='utf-8-sig') as file:
        data = file.read()
    print("已经读取文件数据！")
    # 使用旗帜来定义文件相关信息来对读取的信息进行拆
    flag = 1
    scList = []
    for i in data.split("\n"):
        # 数据拆分
        if flag%3 == 0:
            scList = i.split(",")
            # 数据信息写入列表
            scFlag = 1
            for sc in scList:
                # print(j + eveName + eveType)
                if scFlag%5 == 0:
                    scCl = schoolScore(eveName, eveType, sc, 1)
                elif scFlag%4 == 0:
                    scCl = schoolScore(eveName, eveType, sc, 2)
                elif scFlag%3 == 0:
                    scCl = schoolScore(eveName, eveType, sc, 3)
                elif scFlag%2 == 0:
                    scCl = schoolScore(eveName, eveType, sc, 5)
                else:
                    scCl = schoolScore(eveName, eveType, sc, 7)
                if scFlag == 5:
                    scFlag = 1
                else:
                    scFlag += 1
                queue.push(scCl)
        elif flag%2 == 0:
            eveType = i
        else:
            eveName = i
        if flag == 3:
            flag = 1
        else:
            flag += 1
    # 至此所有数据处理完成
    print("数据处理完成")
    while True:
        print("*" * 50)
        met = input("1.统计各学校总分\n2.按学校总分排序输出\n3.按男女团体总分排序输出\n4.按学校编号查询学校某个项目的情况\n5.按项目编号查询取得前五名的学校\n0.退出\n请选择选项：")
        if met == '1':
            queue.getSchoolScore(0)
        elif met == '2':
            queue.getSchoolScore(1)
        elif met == '3':
            queue.getBGSc()
        elif met == '4':
            scName = input("输入学校名称：")
            queue.getScEve(scName)
        elif met == '5':
            eveName = input("输入项目名称：")
            queue.getFirstFive(eveName)
        elif met == '0':
            print("已经退出！")
            break
        else:
            print("输入有误，请重新选择。")