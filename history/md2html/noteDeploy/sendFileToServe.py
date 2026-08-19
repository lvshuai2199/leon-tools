import os
import paramiko
from scp import SCPClient
import zipfile

def create_zip(local_folder):
    zip_filename = local_folder + '.zip'
    with zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, dirs, files in os.walk(local_folder):
            for file in files:
                file_path = os.path.join(root, file)
                arcname = os.path.relpath(file_path, start=local_folder)
                zipf.write(file_path, arcname)
    return zip_filename

def create_ssh_client(server, port, user, password):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(server, port=port, username=user, password=password)
    return client

def upload_file_via_scp(ssh_client, local_file, remote_file):
    with SCPClient(ssh_client.get_transport()) as scp:
        scp.put(local_file, remote_path=remote_file)


if __name__ == '__main__':

    server_ip = '124.70.134.114'
    port = 8822
    username = 'Administrator'
    password = 'Lvshuai2199'

    local_folder = r'C:\Users\13326\Desktop\测试文件\notes'
    remote_zip_file = r'C:/Users/Administrator/Desktop/notes.zip'
    remote_extract_folder = r'C:/Users/Administrator/Desktop/notes'

    try:
        # 压缩文件夹
        zip_file = create_zip(local_folder)

        # 建立SSH连接
        ssh = create_ssh_client(server_ip, port, username, password)

        # 上传zip文件
        upload_file_via_scp(ssh, zip_file, remote_zip_file)

        # 在服务器上解压缩（假设使用Linux命令）
        ssh.exec_command(f'unzip -o {remote_zip_file} -d {os.path.dirname(remote_zip_file)}')

        # 在Windows服务器上创建目标文件夹（如果不存在）
        ssh.exec_command(f'if not exist "{remote_zip_file}" mkdir "{remote_extract_folder}"')

        # 在Windows服务器上解压缩
        unzip_command = f'powershell -command "Expand-Archive -Path \'{remote_zip_file}\' -DestinationPath \'{remote_extract_folder}\' -Force"'
        stdin, stdout, stderr = ssh.exec_command(unzip_command)

        # 打印命令输出（可选）
        print(stdout.read().decode())
        print(stderr.read().decode())

        print("文件夹上传并解压成功！")
    finally:
        ssh.close()
        # 删除临时zip文件
        os.remove(zip_file)
