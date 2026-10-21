import socket
import json
import time
import gom


GOM_MMT_RETRIES = 10
GOM_MMT_ERROR_CODES = ['MPROJ-0037']


gom.script.internal.refpoint_measurement_create_new_measurement_series(
    camera_focal_length=24.0,
    measurement_temperature=20.0,
    scale_bars=[])

gom.interactive.sys.tscan_hawk2_capture_reference_points_satellite_mode()

gom.script.photogrammetry.edit_camera(
    camera_definition={'binarisation_offset': 5, 'config_level': 'system', 'device_name': 'Lt-M2450TD',
                       'display_name': '5MP', 'file': 'lumenera-m2450.cam', 'firmware': '00451;00455', 'height': 2056,
                       'hp_x_offset': 0, 'hp_y_offset': 0, 'min_overexposed_gray': 255, 'name': 'Lumenera M2450',
                       'pixel_size': 3.45, 'taps': 0, 'trigger': 1, 'type': 43, 'type_name': 'LUMENERA_M2450',
                       'width': 2464},
    focal_length=8.0,
    measurement_series=[gom.app.project.measurement_series['卫星模式测量 1']])


# v1.2
def connectETController(ip, port=8055):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.connect((ip, port))
        print(f"连接成功：ip： {ip}，端口： {port}")
        return True, sock
    except Exception as e:
        print(f"连接失败: {e}")
        sock.close()
        return False, None


def disconnectETController(sock):
    if (sock):
        sock.close()


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
    # for i in range(5):
    #     print(f"相机处理 {i}")


def loadAndStartProject(sock, projectName=None, fun_num = 1):
    if fun_num == 2:
        # Move to start waypoint
        move_message = b'DATA_HEAD00000035{"program control": "stop"}DATA_TAIL'
        print(f"发送的停止工程消息: {move_message}")
        sock.sendall(move_message)
        return

    if projectName is None:
        print("请先设置需要运行的工程")
        return

    # Load project
    print(f"加载工程: {projectName}")
    wordsLen = 28 + len(projectName)
    message = (b'DATA_HEAD000000' +
               bytes(str(wordsLen), 'utf-8') +
               b'{"load project":"' +
               bytes(str(projectName), 'utf-8') +
               b'"}DATA_TAIL')
    print(f"发送的切换工程消息: {message}")
    sock.sendall(message)

    # 等待切换工程的返回值
    wait_for_response(sock, "Loading project is complete")  # 替换为实际的成功返回字符串

    # Move to start waypoint
    move_message = b'DATA_HEAD00000049{"program control":"run to ready point"}DATA_TAIL'
    print(f"发送的移动到初始位置消息: {move_message}")
    sock.sendall(move_message)

    # 等待移动到初始位置的返回值
    wait_for_response(sock, "Move to ready point completed")  # 替换为实际的成功返回字符串

    # Start project
    start_message = b'DATA_HEAD00000036{"program control":"start"}DATA_TAIL'
    print(f"发送的启动工程消息: {start_message}")
    sock.sendall(start_message)

    # 等待启动工程的返回值
    wait_for_response(sock, "Program start")  # 替换为实际的成功返回字符串

def wait_for_response(sock, expected_response):
    """等待特定的响应返回"""
    while True:
        try:
            ret = sock.recv(1024).decode('utf-8')  # 接收数据并解码
            print(f"接收到的消息: {ret}")
            if expected_response in ret:  # 检查是否包含预期的字符串
                return ret
        except socket.error as e:
            print(f"接收数据失败: {e}")
            return None

def camera_fun(sock):
    while (True):
        ret = sock.recv(1024)
        print('ret: ', ret)
        if (ret == b'1'):
            print('start of measure')
            b_time = time.time()
            auto_measure()
            print('end of measure with ', time.time() - b_time)
            # sock1.sendall(bytes('2', "utf-8"))
            sock.sendall(b'2')
        elif (ret == b'f'):
            print('calc begin')
            # auto_calc()
            print('calc end')
            break

        time.sleep(4)


if __name__ == "__main__":
    robot_ip = "192.168.41.129"
    # robot_ip = "127.0.0.1"
    job_name = "funTest"
    # 连接机械臂控制器，如果没有连接上，则判断，直至完全连接
    conSuc, sock_Con = False, None
    while not conSuc:
        conSuc, sock_Con = connectETController(robot_ip, 1206)
        loadAndStartProject(sock_Con, job_name)
        print(f"{conSuc}")

    if conSuc:
        socSuc, sock1 = connectETController(robot_ip, 5020)
        if socSuc:
            camera_fun(sock1)

    loadAndStartProject(sock_Con, projectName=None, fun_num=2)
    disconnectETController(sock_Con)
    disconnectETController(sock1)