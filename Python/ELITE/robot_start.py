import socket
import time

#建立socket连接
def connectETController(ip, port):
    #ip, port：IP和端口号
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.connect((ip, port))
        return (True, sock)
    except Exception as e:
        sock.close()
        return (False)


#29999端口使用，参考dashboard_shell手册
def dashboard_shell(content):
    #content：输入的内容
    robot_ip = "192.168.249.128"
    port = 29999
    ##连接机器人ip和端口
    conSuc, sock = connectETController(robot_ip, port)
    recvData1 = sock.recv(4096)   #清空缓存区
    if (conSuc):
        # 命令转字符串加换行
        # 发送给机器人
        sock.sendall(bytes(str(content + '\n'),"utf-8"))
        # 接受机器人该端口的返回信息
        recvData = sock.recv(4096)
        # decode()方法将一个字节序列转换成字符串
        recvData = recvData.decode()
        # 返回对应指令的字符，成功或者不成功
        return (recvData.replace('\n', '').replace('\r', ''))

#利用29999端口打开电源
def powering_on():
    ##电控柜上电后启动机器人上电
    Data = dashboard_shell("robotControl -on")
    # 返回Powering on上电成功，其他为失败
    print(Data)
    if Data == 'Powering on':
        print('上电成功')
    else:
        print(Data)
    time.sleep(0.5)

#利用29999端口释放抱闸
def brake_releasing():
    while True:
        Data = dashboard_shell("brakeRelease")
        # 返回Brake is released.抱闸释放成功，其他为失败
        if Data == 'Brake is released.':
            print('抱闸释放成功')
            break
        else:
            print(Data)
        time.sleep(0.5)

#利用29999端口运行任务
def play():
    Data = dashboard_shell("play")
    # 返回Starting task任务运行成功，其他为失败
    if Data == 'Starting task':
        print('已启动任务')
    else:
        print('运行失败：'+Data)
    time.sleep(1)

#利用29999端口查询任务状态
def task():
    Data = dashboard_shell("task -r")
    # 返回Task is running.任务正在运行，其他为失败
    if Data == 'Task is running':
        print('任务正在运行')
    else:
        print('当前运行状态为：'+Data)
    time.sleep(1)

#打开电源
powering_on()
#释放抱闸
brake_releasing()
#运行任务
# play()
#查询任务状态
# task()

# # 设置io打开
# def setIO():
#     set_standard_digital_out(0, True)
# end
#
# # 设置输入IO状态
# sec setDigitalIn():
#   set_standard_digital_in(0, False)
# end
# # 设置输出IO状态
# sec setDigitalOut():
#   set_standard_digital_out(0, True)
# end

