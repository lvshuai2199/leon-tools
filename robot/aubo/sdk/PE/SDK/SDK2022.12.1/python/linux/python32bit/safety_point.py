#coding=utf-8
#!/usr/bin/env python


"""
Pymodbus Synchronous Server Example
--------------------------------------------------------------------------

The synchronous server is implemented in pure python without any third
party libraries (unless you need to use the serial protocols which require
pyserial). This is helpful in constrained or old environments where using
twisted is just not feasible. What follows is an example of its use:
"""
# --------------------------------------------------------------------------- #
# import the various server implementations
# --------------------------------------------------------------------------- #
import json
import math
import threading



# import robotcontrol
from  robotcontrol import *


# --------------------------------------------------------------------------- #
# configure the service logging
# --------------------------------------------------------------------------- #


def run_server():
    time.sleep(3)

    # 连接机械臂
    host = '127.0.0.1'
    port = 8899

    # 系统初始化
    Auboi5Robot.initialize()
    # 创建机械臂控制类
    robot  = Auboi5Robot()
    # 创建上下文
    handle = robot.create_context()
    # 链接robot服务器
    result = robot.connect(host, port)
    print 'Result = '

    PI = 3.1415926
    p1 =  (-89.055162*PI/180,3.188699*PI/180,-161.662832*PI/180,-79.790932*PI/180,90.067676*PI/180,5.841047*PI/180)
    p1_pos = (-0.124917,0.212946,0.24541)
    p2 = (82.039881*PI/180,3.188797*PI/180,-161.662846*PI/180,-79.791130*PI/180,90.067874*PI/180,5.841075*PI/180)
    p2_pos = (0.090449,-0.229717,0.245409)
    # 初始化全局配置文件
    robot.init_profile()

    # 设置关节最大加速度
    robot.set_joint_maxacc((0.2,0.2,0.2,0.2,0.2,0.2))

    # 设置关节最大加速度
    robot.set_joint_maxvelc((0.2,0.2,0.2,0.2,0.2,0.2))

    
    #robot.move_joint(p1)

    cnt = 0
    while 1:
        time.sleep(0.001)
        point = robot.get_current_waypoint()
        cnt = cnt + 1        
        if cnt > 20:
            print cnt , " | " ,point['pos'][0]," | ",point['pos'][1]," | ",point['pos'][2],'\n'
            print 'p1_arrival = ',p1_arrival,'\n'
            print 'p2_arrival = ',p2_arrival,'\n'
            print 'dx = ',point['pos'][0] - p1_pos[0] 
            print 'dy = ',point['pos'][1] - p1_pos[1] 
            print 'dz = ',point['pos'][2] - p1_pos[2] 
            cnt = 0
        
        if((abs(point['pos'][0] - p1_pos[0]) < 0.002) and (abs(point['pos'][1] - p1_pos[1]) < 0.002) and (abs(point['pos'][2] - p1_pos[2]) < 0.002)):
            p1_arrival = True
	    robot.set_board_io_status(RobotIOType.User_DO,"U_DO_02",0)
        else:
            p1_arrival = False
        if((abs(point['pos'][0] - p2_pos[0]) < 0.002) and (abs(point['pos'][1] - p2_pos[1]) < 0.002) and (abs(point['pos'][2] - p2_pos[2]) < 0.002)):
            p2_arrival = True
        else:
            p2_arrival = False
   
        if(p1_arrival == True):
            robot.set_board_io_status(RobotIOType.User_DO,"U_DO_00",1)    
        else:
            robot.set_board_io_status(RobotIOType.User_DO,"U_DO_00",0)

        if(p2_arrival == True):
            robot.set_board_io_status(RobotIOType.User_DO,"U_DO_01",1)
        else:
            robot.set_board_io_status(RobotIOType.User_DO,"U_DO_01",0)
   

        if((robot.get_board_io_status(RobotIOType.User_DI,"U_DI_00") == 1) and (p2_arrival == True)):
            ret = robot.move_joint(p1) 
           
            #if(ret == RobotErrorType.RobotError_SUCC):
                
        if(robot.get_board_io_status(RobotIOType.User_DI,"U_DI_01") == 1):
            ret = robot.move_joint(p2) 
          
            #if(ret == RobotErrorType.RobotError_SUCC):
               # robot.set_board_io_status(RobotIOType.User_DO,"U_DO_01",1)
        if(robot.get_board_io_status(RobotIOType.User_DI,"U_DI_02") == 1):
            robot.set_board_io_status(RobotIOType.User_DO,"U_DO_02",1)
        else:
            robot.set_board_io_status(RobotIOType.User_DO,"U_DO_02",0)

                




if __name__ == "__main__":
    run_server()
