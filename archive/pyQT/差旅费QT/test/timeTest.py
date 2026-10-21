from datetime import datetime

# 获取当前时间
current_time = datetime.now()

# 将时间格式化为字符串，作为文件名
file_name = current_time.strftime("%Y-%m-%d_%H-%M-%S.txt")

# 创建文件并写入内容
with open(file_name, 'w') as file:
    file.write("这是由当前时间生成的文件。")

print("文件已生成:", file_name)
