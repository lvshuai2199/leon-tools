import os
import hashlib
import shutil

# 文件去重
def get_file_hash(file_path):
    """计算文件的 MD5 哈希值"""
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def deduplicate_files(directory_path, duplicates_folder):
    """去重指定目录中的文件，并将重复文件移动到指定的文件夹"""
    if not os.path.isdir(directory_path):
        print("指定的路径不是一个目录。")
        return

    # 创建重复文件夹，如果不存在
    if not os.path.exists(duplicates_folder):
        os.makedirs(duplicates_folder)

    file_hashes = set()  # 用于存储文件的哈希值
    for dirpath, dirnames, filenames in os.walk(directory_path):
        for filename in filenames:
            file_path = os.path.join(dirpath, filename)
            try:
                file_hash = get_file_hash(file_path)
                if file_hash in file_hashes:
                    # 如果哈希值已存在，移动文件到重复文件夹
                    print(f"移动重复文件: {file_path} 到 {duplicates_folder}")
                    shutil.move(file_path, os.path.join(duplicates_folder, filename))
                else:
                    # 如果哈希值不存在，添加到集合中
                    file_hashes.add(file_hash)
            except Exception as e:
                print(f"处理文件 {file_path} 时发生错误: {e}")


import os

def remove_empty_folders(directory_path):
    """递归地删除指定目录下的所有空文件夹"""
    if not os.path.isdir(directory_path):
        print("指定的路径不是一个目录。")
        return

    # 使用 os.walk 生成目录树
    for dirpath, dirnames, filenames in os.walk(directory_path, topdown=False):
        for dirname in dirnames:
            folder_path = os.path.join(dirpath, dirname)
            try:
                # 尝试删除空文件夹
                os.rmdir(folder_path)
                print(f"删除空文件夹: {folder_path}")
            except OSError:
                # 如果文件夹不为空，则抛出异常
                print(f"文件夹不为空，无法删除: {folder_path}")

if __name__ == "__main__":
    # 文件去重
    directory_path = "C:\\Users\\13326\\Desktop\\共享文件处理"  # 将此替换为需要处理的文件目录
    duplicates_folder = "C:\\Users\\13326\\Desktop\\去重完成"  # 替换为你希望保存重复文件的目录
    deduplicate_files(directory_path, duplicates_folder)
    # 删除空文件夹
    remove_empty_folders(directory_path)
