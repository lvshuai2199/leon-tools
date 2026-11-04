from aubocommon import *
import numpy as np
import math

import time
import multiprocessing

class Board:
    def __init__(self, serverip='192.168.10.187', serverport=8080, clientip='192.168.41.129', clientport=8899,timeout=5):

            # thread1 = threading.Thread(target=self.recv_task, args=())
            # thread2 = threading.Thread(target=self.send_task, args=())
            # thread1.start()
            # thread2.start()

            recv_task = multiprocessing.Process(target=self.recv_task, args=())
            send_task = multiprocessing.Process(target=self.send_task, kwargs={})

            recv_task.start()
            send_task.start()


    def send_task(self):
        clientip = '192.168.41.129'
        clientport = 8899
        Auboi5Robot.initialize()  # 系统初始化
        self.robot = Auboi5Robot()  # 创建机械臂控制类
        self.handle_move = self.robot.create_context()  # 创建上下文
        result = self.robot.connect(clientip, clientport)  # 服务器ip和端口号
        self.m_flag = False
        if result == RobotErrorType.RobotError_SUCC:
            # 上电
            self.robot.robot_startup()
            # 初始化全局的运动属性
            self.robot.init_profile()
            # 设置关节运动的最大加速度
            joint_maxacc = (0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
            self.robot.set_joint_maxacc(joint_maxacc)
            # 设置关节运动的最大速度
            joint_maxvelc = (0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
            self.robot.set_joint_maxvelc(joint_maxvelc)
        while True:
            if self.m_flag == False:
                data = [0.00,0.00,0.00,0.00,0.00,0.00]
                value = self.robot.move_joint(data)
                self.m_flag = True
            else:
                data = [0.1740, 0.5233, 0.5233, 0.5233, 0.5233, 0.5233]
                value = self.robot.move_joint(data)
                self.m_flag = False
            time.sleep(1)

    def recv_task(self):
        clientip = '192.168.41.129'
        clientport = 8899
        Auboi5Robot.initialize()  # 系统初始化
        self.robot = Auboi5Robot()  # 创建机械臂控制类
        self.handle_move = self.robot.create_context()  # 创建上下文
        result = self.robot.connect(clientip, clientport)  # 服务器ip和端口号
        self.m_flag = False
        if result == RobotErrorType.RobotError_SUCC:
            while True:
                # 获取当前机械臂的关节角度、位置、姿态
                current_waypoint = self.robot.get_current_waypoint()
                if current_waypoint is not None:
                    flag1 = True
                    value_joint = np.array(current_waypoint["joint"]) / math.pi * 180
                    value_position = current_waypoint["pos"]
                    value_ori = current_waypoint["ori"]
                    print(value_joint)
                else:
                    print("xxxxxxxxxxxxxxxxxxxxx")

                time.sleep(0.1)

if __name__ == '__main__':
    m_Board = Board()