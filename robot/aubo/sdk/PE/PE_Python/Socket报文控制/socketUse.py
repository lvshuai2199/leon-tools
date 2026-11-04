import socket
import time
import os

GOM_MMT_RETRIES = 10
GOM_MMT_ERROR_CODES = ['MPROJ-0037']


def auto_measure():
    while True:
        try:
            gom.script.atos.insert_scan_measurement(min_measurement_exposure_time=0.014984,
                                                    reference_point_exposure_time=0.01496)
        except RuntimeError as ex:
            if GOM_MMT_ERROR_CODES and not ex.args[0] in GOM_MMT_ERROR_CODES: raise
            GOM_MMT_RETRIES -= 1
            if GOM_MMT_RETRIES <= 0: raise
        else:
            break


def split_path(path):
    newpath = os.path.abspath(path)
    # print(newpath)
    if not os.path.isdir(newpath):
        newpath, newfilename = os.path.split(newpath)
    pathlist = newpath.split('\\')
    return pathlist


# server_ip=input("请输⼊服务器ip:")
# server_sport=int(input("请输⼊服务器127127端口:"))
server_ip = "192.168.80.128"  # 服务器IP
server_sport = 5020  # 服务器端口号

# 连接服务器
while True:
    try:
        tcp_client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        tcp_client_socket.connect((server_ip, server_sport))

        print("connectSuccess\n")
        print(server_ip)
        print(server_sport)
        Connect_result = True

        break
    except ConnectionRefusedError as X:
        print(X)
        print("服务器连接中断！隔5s重新连接……\n")
        time.sleep(5)

while True:
    # 接收数据
    recvData = tcp_client_socket.recv(1024).decode('gbk')
    print('接收到的数据为:', recvData)

    if recvData == '1':
        print("EnterImgCal")
        auto_measure()
        send_data = "2"
        tcp_client_socket.send(send_data.encode("gbk"))
        print('SendData:', send_data)

    # break
    elif recvData == 'f':
        print('server close')
        sys.exit(0)
    recvData = ''

# 关闭客户端
tcp_client_socket.close()