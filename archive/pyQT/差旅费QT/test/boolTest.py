import os
import hashlib
import shutil
def compare_files(source_file, target_folder):
    source_hash = calculate_file_hash(source_file)
    target_files = os.listdir(target_folder)
    for target_file in target_files:
        target_file_path = os.path.join(target_folder, target_file)
        if os.path.isfile(target_file_path):
            target_hash = calculate_file_hash(target_file_path)
            if source_hash == target_hash:
                print(f"{source_file} 和 {target_file} 是相同的文件")
                return True
    print(f"{source_file} 在目标文件夹中没有相同的文件")
    return False


def calculate_file_hash(file_path):
    # 使用 SHA-256 计算文件的哈希值
    sha256 = hashlib.sha256()
    with open(file_path, 'rb') as f:
        while True:
            data = f.read(65536)  # 以块的方式读取文件内容
            if not data:
                break
            sha256.update(data)
    return sha256.hexdigest()

source_file = 'C:/Users/13326/Desktop/报销\\3.27苏州\\通行费电子发票\\91320000714089457T_3a70e88851a14483a03c5dd8c28c9621.pdf'
destination_folder = 'C:/Users/13326/Desktop/吕帅/空值文件/'

# 如果目标文件夹可写，则检查是否包含相同文件
sameFileRes = compare_files(source_file, destination_folder)
# 若存在相同文件，则跳过，不存在，则存储
if sameFileRes:
    print("存在相同文件")
else:
    print("不存在相同文件，存储")
    shutil.copy(source_file, destination_folder)

