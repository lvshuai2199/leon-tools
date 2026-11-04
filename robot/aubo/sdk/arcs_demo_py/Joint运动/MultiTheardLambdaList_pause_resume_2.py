import threading
import time
import math
from pyaubo_sdk import RpcClient, RuntimeState

# 机器人IP 地址
LOCAL_IP = "192.168.1.101"


def wait_for_queue_space(motion_function):
    """
    等待运动队列有空位。调用指定的运动函数，直到返回值不为2（表示队列已满）。
    :param motion_function: 要执行的运动函数
    """
    while motion_function() == 2:  # 如果返回值为2，表示队列已满
        time.sleep(0.05)
    motion_function()


def execute_motion_sequence(motions):
    """
    下发运动指令，直到设置终止标志事件。
    :param motions: 运动指令的列表，每个指令是一个函数
    """
    for motion in motions:
        if stop_event.is_set():
            # print("stop")
            return  # 如果设置终止标志事件，停止下发运动指令
        wait_for_queue_space(motion)


def robot_motion_control(cli):
    """
    控制机器人执行运动
    :param cli: RpcClient 实例，用于与机器人通信
    """
    # 路点，用关节角表示，单位: 弧度
    # 指定要读取的txt文件路径
    file_path = "demo.txt"

    # 读取文件并存储在preList中
    preList = []
    with open(file_path, 'r') as file:
        for line in file:
            print(line, end="")
            preList.append(line)
    file.close()

    # 解析字符串并转换为关节角度列表
    joint_angles = []
    for line in preList:
        # 去除换行符并分割字符串
        angles = line.strip().split()
        # 将字符串转换为浮点数
        angles = [float(angle) for angle in angles]
        # 将解析后的角度添加到joint_angles列表中
        joint_angles.append(angles)

    # 接口调用: 获取机器人的名字
    robot_name = rpc_cli.getRobotNames()[0]
    robot_interface = rpc_cli.getRobotInterface(robot_name)

    # 接口调用: 开启规划器
    rpc_cli.getRuntimeMachine().start()

    time.sleep(1)

    # 接口调用： 设置运动速度比例
    robot_interface.getMotionControl().setSpeedFraction(0.05)

    # robot_interface.getMotionControl().moveJoint(0)
    # 运动函数的列表
    lambdalist = lambda motion: [
        robot_interface.getMotionControl().moveLine(
            [joint / 1000 if i < 3 else joint for i, joint in enumerate(joints)],  # 对前三个浮点数除以 1000
            80 * (math.pi / 180),
            60 * (math.pi / 180),
            0.2,
            0
        ) for joints in motion
    ]
    motion = joint_angles

    # 循环执行运动指令，直到停止规划器且设置终止标志事件
    while rpc_cli.getRuntimeMachine().getStatus() != RuntimeState.Stopped and not stop_event.is_set():

        m = lambdalist(motion)
        """
        等待运动队列有空位。调用指定的运动函数，直到返回值不为2（表示队列已满）。
        :param motion_function: 要执行的运动函数
        """
        while lambdalist(motion) == 2:  # 如果返回值为2，表示队列已满
            time.sleep(0.05)
            lambdalist(motion)


def control_operations(cli):
    """
    :param cli: RpcClient 实例，用于与机器人通信
    """
    while not stop_event.is_set():
        input_cmd = input("请输入命令(p/r/s): p表示暂停运动，r表示恢复运动，s表示停止运动\n")

        if input_cmd == "p":
            # 接口调用: 暂停运行时
            cli.getRuntimeMachine().pause()
            print("已暂停运行时")
        elif input_cmd == "r":
            # 接口调用: 恢复运行时
            cli.getRuntimeMachine().resume()
            print("已恢复运行时")
        elif input_cmd == "s":
            # 接口调用: 停止运行时
            cli.getRuntimeMachine().abort()
            robot_name = cli.getRobotNames()[0]
            robot_interface = cli.getRobotInterface(robot_name)
            # 接口调用: 停止运动
            robot_interface.getMotionControl().stopJoint(30)
            # 设置终止标志事件
            stop_event.set()
            print("已停止运行时")
        else:
            print("无效命令，请重新输入")


if __name__ == "__main__":
    rpc_cli = RpcClient()
    # 接口调用: 设置 RPC 超时
    rpc_cli.setRequestTimeout(1000)
    # 接口调用: 连接 RPC 服务
    rpc_cli.connect(LOCAL_IP, 30004)
    # 接口调用: 登录 RPC 服务
    rpc_cli.login("aubo", "123456")

    # 创建终止标志事件
    stop_event = threading.Event()

    # 创建并启动运动控制线程和控制操作线程
    motion_thread = threading.Thread(target=robot_motion_control, args=(rpc_cli,))
    control_thread = threading.Thread(target=control_operations, args=(rpc_cli,))

    motion_thread.start()
    control_thread.start()

    # 等待运动线程结束
    motion_thread.join()
    control_thread.join()

    # 接口调用: 退出 RPC 登录
    rpc_cli.logout()
    # 接口调用: 断开 RPC 连接
    rpc_cli.disconnect()
