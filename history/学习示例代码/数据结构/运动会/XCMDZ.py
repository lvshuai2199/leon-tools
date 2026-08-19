def 统计各学校总分():
    # 提取所有学校
    学校列表 = []
    for i in list1:
        for j0, j in enumerate(i):
            if j0 >= 1:
                if j not in 学校列表:
                    学校列表.append(j)
    # print(学校列表)

    # 初始化
    dict1 = {}
    for i in 学校列表:
        dict1[i] = 0

    # 统计数量
    for i in list1:
        for j0, j in enumerate(i):
            if j0 == 1:
                dict1[j] += 7
            elif j0 == 2:
                dict1[j] += 5
            elif j0 == 3:
                dict1[j] += 3
            elif j0 == 4:
                dict1[j] += 2
            elif j0 == 5:
                dict1[j] += 1

    # print(dict1)
    for i, j in dict1.items():
        print(f"学校：{i}，分数是：{j}")


def 按学校总分排序输出():
    # 提取所有学校
    学校列表 = []
    for i in list1:
        for j0, j in enumerate(i):
            if j0 >= 1:
                if j not in 学校列表:
                    学校列表.append(j)
    # print(学校列表)

    # 初始化
    dict1 = {}
    for i in 学校列表:
        dict1[i] = 0

    # 统计数量
    for i in list1:
        for j0, j in enumerate(i):
            if j0 == 1:
                dict1[j] += 7
            elif j0 == 2:
                dict1[j] += 5
            elif j0 == 3:
                dict1[j] += 3
            elif j0 == 4:
                dict1[j] += 2
            elif j0 == 5:
                dict1[j] += 1

    # print(dict1)

    # 提取学校和分数存到列表
    学校 = []
    分数 = []
    for i, j in dict1.items():
        学校.append(i)
        分数.append(j)

    # 排序
    for x in range(1, len(分数)):
        for y in range(0, len(分数) - x):
            if 分数[y] < 分数[y + 1]:
                分数[y], 分数[y + 1] = 分数[y + 1], 分数[y]  # 两个数交换值
                学校[y], 学校[y + 1] = 学校[y + 1], 学校[y]  # 两个数交换值
    # print(学校)
    # print(分数)

    # 打印
    for i, j in enumerate(学校):
        print(f"第 {i + 1} 名的学校是： {j}，分数是：{分数[i]}")


def 按男女团体总分排序输出():
    男团体 = []
    女团体 = []

    # 分团体
    for i0, i in enumerate(list2):
        if i == "1":
            男团体.append(list1[i0])
        elif i == "0":
            女团体.append(list1[i0])

    # print(男团体)
    # print(女团体)

    # 提取所有学校
    学校列表 = []
    for i in list1:
        for j0, j in enumerate(i):
            if j0 >= 1:
                if j not in 学校列表:
                    学校列表.append(j)

    # 初始化男团体字典
    dict1 = {}
    for i in 学校列表:
        dict1[i] = 0

    # 初始化女团体字典
    dict0 = dict1.copy()

    # 统计男团体数量
    for i in 男团体:
        for j0, j in enumerate(i):
            if j0 == 1:
                dict1[j] += 7
            elif j0 == 2:
                dict1[j] += 5
            elif j0 == 3:
                dict1[j] += 3
            elif j0 == 4:
                dict1[j] += 2
            elif j0 == 5:
                dict1[j] += 1

    # 统计女团体数量
    for i in 女团体:
        for j0, j in enumerate(i):
            if j0 == 1:
                dict0[j] += 7
            elif j0 == 2:
                dict0[j] += 5
            elif j0 == 3:
                dict0[j] += 3
            elif j0 == 4:
                dict0[j] += 2
            elif j0 == 5:
                dict0[j] += 1

    # 男团体提取学校和分数存到列表
    学校1 = []
    分数1 = []
    for i, j in dict1.items():
        学校1.append(i)
        分数1.append(j)

    # 女团体提取学校和分数存到列表
    学校0 = []
    分数0 = []
    for i, j in dict0.items():
        学校0.append(i)
        分数0.append(j)

    # 男团体排序
    for x in range(1, len(分数1)):
        for y in range(0, len(分数1) - x):
            if 分数1[y] < 分数1[y + 1]:
                分数1[y], 分数1[y + 1] = 分数1[y + 1], 分数1[y]  # 两个数交换值
                学校1[y], 学校1[y + 1] = 学校1[y + 1], 学校1[y]  # 两个数交换值

    # 女团体排序
    for x in range(1, len(分数0)):
        for y in range(0, len(分数0) - x):
            if 分数0[y] < 分数0[y + 1]:
                分数0[y], 分数0[y + 1] = 分数0[y + 1], 分数0[y]  # 两个数交换值
                学校0[y], 学校0[y + 1] = 学校0[y + 1], 学校0[y]  # 两个数交换值

    # 男团体排名
    print("男团体排名：")
    for i, j in enumerate(学校1):
        print(f"第 {i + 1} 名的学校是： {j}，分数是：{分数1[i]}")

    # 女团体排名
    print("女团体排名：")
    for i, j in enumerate(学校0):
        print(f"第 {i + 1} 名的学校是： {j}，分数是：{分数0[i]}")


def 按学校编号查询学校某个项目的情况():
    # 提取所有学校
    学校列表 = []
    for i in list1:
        for j0, j in enumerate(i):
            if j0 >= 1:
                if j not in 学校列表:
                    学校列表.append(j)
    # print(学校列表)

    # 打印
    for i0, i in enumerate(学校列表):
        print(f"{i0}：{i}")

    # 输入
    a = input("请选择序号：")

    # 判断数据有效
    if 0 <= int(a) < len(学校列表):
        学校名称 = 学校列表[int(a)]
        for i in list1:
            print(f"{学校名称} 在 {i[0]} 项目中：", end="")
            for j0, j in enumerate(i):
                if j0 > 0 and j == 学校名称:
                    print(f"获得第 {j0} 名", end=" ")
            print()
    else:
        print("输入不在范围")


def 按项目编号查询取得前五名的学校():
    # 打印
    for i0, i in enumerate(list1):
        print(f"{i0}：{i[0]}")
    # 输入
    a = input("请选择序号：")

    # 判断数据有效
    if 0 <= int(a) < len(list1):
        print(f"{list1[int(a)][0]} 排名：")
        for i0, i in enumerate(list1[int(a)]):
            if i0 >= 1:
                print(f"第 {i0} 名：{i}")
    else:
        print("输入不在范围")


if __name__ == '__main__':
    # 读取文件
    with open(r"data.txt", "r", encoding='utf-8-sig') as file:
        data = file.read()
    print("已经读取文件数据！")

    # 字符串转列表
    list1 = []
    for i in data.split("\n"):
        if i != "":
            list1.append(i.split(","))

    # 输入项目是男子还是女子项目
    list2 = []
    for i in list1:
        a = input(f"请输入序号：项目 {i[0]} 是：\n0.女子项目\n1.男子项目")
        list2.append(a)
    print(list2)
    # list2 = ['1', '1', '0', '0', '0', '1']

    while True:
        print("*" * 50)
        序号 = input("1.统计各学校总分\n2.按学校总分排序输出\n3.按男女团体总分排序输出\n4.按学校编号查询学校某个项目的情况\n5.按项目编号查询取得前五名的学校\n0.退出\n请选择序号：")
        if 序号 == '1':
            统计各学校总分()
        elif 序号 == '2':
            按学校总分排序输出()
        elif 序号 == '3':
            按男女团体总分排序输出()
        elif 序号 == '4':
            按学校编号查询学校某个项目的情况()
        elif 序号 == '5':
            按项目编号查询取得前五名的学校()
        elif 序号 == '0':
            print("已经退出！")
            break
        else:
            print("输入有误，请重新选择。")
