import os
import subprocess

def open_folder_and_get_location(folder_path):
    """
    打开指定的本地文件夹，并返回其绝对路径。

    Parameters:
    folder_path (str): 想要打开的文件夹路径。

    Returns:
    str: 该文件夹的绝对路径。
    """
    try:
        # 获取绝对路径
        absolute_path = os.path.abspath(folder_path)

        # 打开文件夹
        if os.name == 'nt':  # 如果是 Windows 系统
            os.startfile(absolute_path)
        elif os.name == 'posix':  # 如果是 macOS 或 Linux 系统
            subprocess.run(['open', absolute_path] if sys.platform == 'darwin' else ['xdg-open', absolute_path])
        else:
            print("不支持的操作系统")

        return absolute_path

    except Exception as e:
        print(f"无法打开文件夹: {e}")
        return None



# 示例用法
if __name__ == "__main__":

    # 打开文件夹
    # 示例用法
    folder_path = r'C:\Users\13326\Desktop\测试文件\notes'
    folder_location = open_folder_and_get_location(folder_path)
    if folder_location:
        print(f"已打开文件夹: {folder_location}")
