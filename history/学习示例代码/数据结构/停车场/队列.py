class myQueue():

    def __init__(self, size):#初始化一个空队列
        self.size = size
        self.front =0
        self.rear = 0
        self.queue = []

    def enqueue(self, x):  # 入队操作
        if self.isfull():
            print("queue is full")
            return False
        else:
            self.queue.append(x)
            self.rear = self.rear + 1
            return True

    def dequeue(self):  # 出队操作
        if self.isempty():
            print("queue is empty")
            return False
        else:
            duiwei = self.queue[self.front]
            self.front = self.front + 1
            self.queue.pop(self.front)
            return duiwei

    def isfull(self):
        return self.rear - self.front + 1 == self.size
    def isempty(self):
        return self.front == self.rear

    def gethead(self):
        if self.isempty():
            return False
        else:
            return self.queue[self.front]

    def show(self):
        print(self.queue)

qq= myQueue
print("初始化队列为空",qq.isempty())

for i in range(10):
    qq.enqueue(i)
qq.show()
print("插入十个元素后，队满",qq.isfull())
print(qq.dequeue())


