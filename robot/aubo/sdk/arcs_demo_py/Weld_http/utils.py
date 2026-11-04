import subprocess
import platform


def ping(host):
    """
    使用ping命令检查IP地址是否联通
    :param host: IP地址或主机名
    :return: True 如果联通，False 如果不联通
    """
    # 根据操作系统选择合适的ping命令参数
    param = '-n' if platform.system().lower() == 'windows' else '-c'

    # 构建ping命令
    command = ['ping', param, '1', host]

    # 执行ping命令
    response = subprocess.call(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    # 返回结果
    return response == 0


def get_robot_ip():
    return "192.168.31.182"


# # 示例调用
# ip_address = "192.168.2.123"  # 示例IP地址
# if ping(ip_address):
#     print(f"{ip_address} 是联通的")
# else:
#     print(f"{ip_address} 是不联通的")
