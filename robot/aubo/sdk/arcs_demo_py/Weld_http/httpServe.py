import json

import httpRequest

produceId = None


def perform_get_current_procedure_request():
    """执行 GET 请求并打印结果"""
    global produceId  # 声明使用全局变量
    route_get = "api/weld/procedure/current"  # 示例 API 路径
    response_get = httpRequest.Request_Get(route_get)  # 调用 GET 函数并获取响应
    if response_get:
        # 解析JSON数据
        data = json.loads(response_get)

        # 提取id的值
        id_value = data['data']['id']
        print("GET 请求成功获取响应!流程id"+ str(id_value))
        produceId = id_value  # 更新全局变量
        return produceId
    else:
        print("GET 请求失败!")
        return None


def perform_get_request(weld_id):
    """执行 GET 请求并打印结果"""
    route_get = f"api/weld?id={weld_id}"  # 示例 API 路径
    response_get = httpRequest.Request_Get(route_get)  # 调用 GET 函数并获取响应
    if response_get:
        print("GET 请求成功获取响应!")
    else:
        print("GET 请求失败!")


def perform_post_request(post_data_param):
    """执行 POST 请求并打印结果"""
    dict_data = json.loads(post_data_param)
    route_post = "api/weld/step/add"  # 示例 API 路径
    # print("post_data_param：" + dict_data)
    response_post = httpRequest.Request_Post(route_post, dict_data)  # 调用 POST 函数并获取响应
    if response_post:
        print("POST 请求成功获取响应!焊缝存储成功")
    else:
        print("POST 请求失败!")


def perform_post_weld_start():
    """执行 POST 请求并打印结果"""
    route_post = "api/weld/start"  # 示例 API 路径
    response_post = httpRequest.Request_Post(route_post)  # 调用 POST 函数并获取响应
    if response_post:
        print("POST 请求成功获取响应!")
    else:
        print("POST 请求失败!")


def perform_post_weld_stop():
    """执行 POST 请求并打印结果"""
    route_post = "api/weld/stop"  # 示例 API 路径
    response_post = httpRequest.Request_Post(route_post)  # 调用 POST 函数并获取响应
    if response_post:
        print("POST 请求成功获取响应!")
    else:
        print("POST 请求失败!")


def perform_post_weld_clear():
    """执行 POST 请求并打印结果"""
    route_post = "api/weld/clear"  # 示例 API 路径
    response_post = httpRequest.Request_Post(route_post)  # 调用 POST 函数并获取响应
    if response_post:
        print("POST 请求成功获取响应!")
    else:
        print("POST 请求失败!")


def getRobotPoint():
    print("获取机械臂位置")


if __name__ == "__main__":
    # weld_id = 337  # 可以更改焊接 ID
    # perform_get_request(weld_id)  # 执行 GET 请求
    # perform_post_request()  # 执行 POST 请求

    # 获取焊点位置
    getRobotPoint()
    # 获取对应的流程参数
    perform_get_current_procedure_request()
    print(produceId)  # 打印获取到的生产 ID
