# 路点信息分割与组合
# 分割示例：   moveLine({0.5494349803383992,-0.30106376141155083,-0.0742610991578412,0.010878055662214699,0.5034946554037781,0.3259853698958129}, 1.2, 0.015243359209832982, 0, 0)


# s = "moveLine({0.5494349803383992,-0.30106376141155083,-0.0742610991578412,0.010878055662214699,0.5034946554037781,0.3259853698958129}, 1.2, 0.015243359209832982, 0, 0)"
# print('待分割的字符串为：', s)
# lst = s.split(",")
# print('分割后为：', lst)  # ['apple', 'banana', 'pear']
#
# # 字符串拼接
# # 拼接数据
#
# # lst = ['Hello', 'World', '!']
# lst[3] = "0"
# lst[4] = "0"
# lst[5] = "0}"
#
# result = ','.join(lst)
# print(result)  # Output: HelloWorld!


flag1 = "0.010878055662214699"
flag2 = "0.5034946554037781"
flag3 = "0.3259853698958129}"


# 指定要读取的txt文件路径
file_path = "demo.txt"

preList = []
# 打开txt文件
with open(file_path, 'r') as file:
    # 遍历每一行并输出
    for line in file:
        print(line,end="")
        preList.append(line)

file.close()

print(preList)

creList = []
# 使用for循环并指定step为2
for i in range(0, len(preList), 2):
    print(preList[i])
    # preList[i]
    s = preList[i]
    print('待分割的字符串为：', s)
    lst = s.split(",")
    print('分割后为：', lst)  # ['apple', 'banana', 'pear']

    # 字符串拼接
    # 拼接数据

    # lst = ['Hello', 'World', '!']
    lst[3] = flag1
    lst[4] = flag2
    lst[5] = flag3

    result = ','.join(lst)
    creList.append(result)
    creList.append("sleep(0.06)\n")

print(creList)
print("end")

my_list = creList
# 打开或新建名为output.txt的TXT文件（如果已存在则覆盖）
with open('output.txt', 'w') as file:
    # 使用for循环遍历列表中的每个元素
    for item in my_list:
        # 将当前元素作为字符串写入TXT文件，并添加换行符'\n'
        file.write(item)
file.close()
print("完成！")