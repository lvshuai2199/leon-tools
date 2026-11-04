
import socket


def loadAndStartProject():
    # 创建一个TCP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    # 定义服务器的IP地址和端口
    server_address = ('192.168.41.129', 1206)

    # 连接到服务器
    sock.connect(server_address)

    #  loadproject
    projectName = "qqqq"
    print(len(projectName))
    wordsLen = 28 + len(projectName)
    message = b'DATA_HEAD000000' + bytes(str(wordsLen), 'utf-8') + b'{"load project":"'+ bytes(str(projectName), 'utf-8') +  b'"}DATA_TAIL'
    print(message)
    tag = sock.sendall(message)
    #  movetoStartWaypoint
    message = b'DATA_HEAD00000049{"program control":"run to ready point"}DATA_TAIL'
    tag = sock.sendall(message)
    # startProject
    message = b'DATA_HEAD00000036{"program control":"start"}DATA_TAIL'
    tag = sock.sendall(message)

    # 关闭连接
    sock.close()

loadAndStartProject()