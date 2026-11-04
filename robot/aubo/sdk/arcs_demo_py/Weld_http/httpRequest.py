import requests
import utils
# 服务器的 IP 地址和端口
serve_ip = "http://" + utils.get_robot_ip()
serve_port = "8090"


def Request_Get(route_path):
    """
    发送 GET 请求到指定的路由。

    参数:
    route_path (str): API 路径，例如 'api/weld?id=337'

    返回:
    str: 返回响应内容，如果请求失败则返回 None。
    """
    try:
        # 使用 f-string 构建 URL
        url = f"{serve_ip}:{serve_port}/{route_path}"

        # 发送 GET 请求
        response = requests.get(url)
        response.raise_for_status()  # 检查请求是否成功（状态码为 200）

        # 输出响应状态码和内容
        print('GET 请求的响应状态码:', response.status_code)
        print('响应内容:', response.text)

        return response.text  # 返回响应内容
    except requests.exceptions.RequestException as e:
        print('发生错误:', e)
        return None  # 如果请求失败，返回 None


def Request_Post(route_path, data=None):
    """
    发送 POST 请求到指定的路由。

    参数:
    route_path (str): API 路径，例如 'api/weld'
    data (dict): 要发送的 JSON 数据

    返回:
    str: 返回响应内容，如果请求失败则返回 None。
    """
    try:
        print(data)
        # 使用 f-string 构建 URL
        url = f"{serve_ip}:{serve_port}/{route_path}"

        # 发送 POST 请求
        response = requests.post(url, json=data)  # 使用 JSON 格式发送数据
        response.raise_for_status()  # 检查请求是否成功（状态码为 200）

        # 输出响应状态码和内容
        print('POST 请求的响应状态码:', response.status_code)
        print('响应内容:', response.text)

        return response.text  # 返回响应内容
    except requests.exceptions.RequestException as e:
        print('发生错误:', e)
        return None  # 如果请求失败，返回 None
