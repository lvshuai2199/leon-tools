#! /usr/bin/env python
# coding=utf-8

"""
机械臂上电与断电

步骤:
第一步: 连接到 RPC 服务
第二步: 机械臂登录
第三步: 机械臂上电
第四步: 机械臂断电
"""
import json

import pyaubo_sdk
import time
from .stepDataJson import WeldingData
import httpServe
import utils


robot_ip = utils.get_robot_ip()  # 服务器 IP 地址
robot_port = 30004  # 端口号
M_PI = 3.14159265358979323846
robot_rpc_client = pyaubo_sdk.RpcClient()

welding_data = WeldingData()


# 机械臂上电
def exampleStartup():
    robot_name = robot_rpc_client.getRobotNames()[0]  # 接口调用: 获取机器人的名字
    if 0 == robot_rpc_client.getRobotInterface(
            robot_name).getRobotManage().poweron():  # 接口调用: 发起机器人上电请求
        print("The robot is requesting power-on!")
        if 0 == robot_rpc_client.getRobotInterface(
                robot_name).getRobotManage().startup():  # 接口调用: 发起机器人启动请求
            print("The robot is requesting startup!")
            # 循环直至机械臂松刹车成功
            while 1:
                robot_mode = robot_rpc_client.getRobotInterface(robot_name) \
                    .getRobotState().getRobotModeType()  # 接口调用: 获取机器人的模式类型
                print("Robot current mode: %s" % (robot_mode.name))
                if robot_mode == pyaubo_sdk.RobotModeType.Running:
                    break
                time.sleep(1)


# 机械臂断电
def examplePoweroff():
    robot_name = robot_rpc_client.getRobotNames()[0]  # 接口调用: 获取机器人的名字
    if 0 == robot_rpc_client.getRobotInterface(
            robot_name).getRobotManage().poweroff():  # 接口调用: 机械臂断电
        print("The robot is requesting power-off!")


def getCurrentPose():
    robot_name = robot_rpc_client.getRobotNames()[0]  # 接口调用: 获取机器人的名字
    # 接口调用: 获取TCP的位姿
    tcp_pose = robot_rpc_client.getRobotInterface(robot_name).getRobotState().getTcpPose()
    print("TCP的位姿:", tcp_pose)
    # 接口调用: 获取机械臂关节角度
    joint_positions = robot_rpc_client.getRobotInterface(robot_name).getRobotState().getJointPositions()
    print("机械臂关节角度:", joint_positions)
    return joint_positions, tcp_pose  # 返回获取到的数据


def getIOStatus():
    robot_name = robot_rpc_client.getRobotNames()[0]  # 接口调用: 获取机器人的名字
    # 接口调用: 获取TCP的位姿
    inputIoStatus = robot_rpc_client.getRobotInterface(robot_name).getIoControl().getStandardDigitalInput(0)

    print("Current io status is:" + str(inputIoStatus))


def weld_data_set(fun_type):
    # 获取对应的笛卡尔坐标系位置及关节角度
    # 设置后部和前部数据
    joint_positions, tcp_pose = getCurrentPose()  # 获取当前的位姿和关节角度
    print()
    if fun_type == 1:
        welding_data.set_front(
            joint=joint_positions,
            pose=tcp_pose
        )
        getIOStatus()
    elif fun_type == 2:
        welding_data.set_back(
            joint=joint_positions,
            pose=tcp_pose
        )
    elif fun_type == 3:
        welding_data.add_point(
            idx=1,
            joint=joint_positions,
            pose=tcp_pose
        )
    elif fun_type == 4:
        # 获取并设置procedureId，如果返回值为空，则清空一次再重新存储
        procedure_id = httpServe.perform_get_current_procedure_request()
        if procedure_id:
            welding_data.set_procedure_id(procedure_id)
            print("设置流程id完成")

        # 打印 JSON 数据
        print(welding_data.to_json())
        httpServe.perform_post_request(welding_data.to_json())
    elif fun_type == 5:
        # 打印 JSON 数据
        httpServe.perform_post_weld_start()
    elif fun_type == 6:
        # 打印 JSON 数据
        httpServe.perform_post_weld_stop()
    elif fun_type == 7:
        # 打印 JSON 数据
        httpServe.perform_post_weld_clear()
    elif fun_type == 8:
        # 打印 JSON 数据
        welding_data.set_mount_type()
    else:
        print("错误")



def weldServiceStart():
    robot_rpc_client.connect(robot_ip, robot_port)  # 接口调用: 连接到 RPC 服务
    if robot_rpc_client.hasConnected():
        print("Robot rcp_client connected successfully!")
        robot_rpc_client.login("aubo", "123456")  # 接口调用: 机械臂登录
        if robot_rpc_client.hasLogined():
            print("Robot rcp_client logined successfully!")
            exampleStartup()  # 机械臂上电

            while 1:
                inCon = input(
                    "输入：\n 1. 设置前移动点\n 2. 设置后移动点\n 3. 设置焊接点位\n 4. 获取焊缝id并存储\n 5. 焊接启动\n 6. 焊接停止\n 7. 焊缝清除\n 8. 切换焊接类型\n")
                if inCon == 'q':
                    break
                weld_data_set(int(inCon))

            # weldingDataSetExample()

            # examplePoweroff()  # 机械臂断电


if __name__ == '__main__':
    robot_rpc_client.connect(robot_ip, robot_port)  # 接口调用: 连接到 RPC 服务
    if robot_rpc_client.hasConnected():
        print("Robot rcp_client connected successfully!")
        robot_rpc_client.login("aubo", "123456")  # 接口调用: 机械臂登录
        if robot_rpc_client.hasLogined():
            print("Robot rcp_client logined successfully!")
            exampleStartup()  # 机械臂上电

            while 1:
                inCon = input("输入：\n 1. 设置前移动点\n 2. 设置后移动点\n 3. 设置焊接点位\n 4. 获取焊缝id并存储\n 5. 焊接启动\n 6. 焊接停止\n 7. 焊缝清除\n "
                              "8. 切换焊接类型\n")
                if inCon == 'q':
                    break
                weld_data_set(int(inCon))

            examplePoweroff()  # 机械臂断电
