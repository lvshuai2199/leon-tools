from PyQt5.QtGui import QTextCursor
from PyQt5.QtWidgets import QFileDialog, QWidget
from PyQt5.QtCore import QSettings, QThread, pyqtSignal
from UIResources import noteDeploy
import os
import makeMD2HTML as mdHtml
import sendFileToServe as sshSend
import checkImgUse as ImgCheck

class Worker(QThread):
    progress = pyqtSignal(str)
    finished = pyqtSignal()
    error = pyqtSignal(str)

    def __init__(self, parent=None):
        super(Worker, self).__init__()
        self.parent = parent

    def run(self):
        try:
            self.progress.emit("开始转换md文件》》》》》》》》》")
            md_directory = self.parent.note_path_text.text()
            html_directory = self.parent.note_export_path_text.text()
            mdHtml.convert_md_to_html(md_directory, html_directory)
            mdHtml.create_index_html(html_directory)

            self.progress.emit("md文件转换完成！！！》》》》》》》》》")
            self.progress.emit("移动图片文件夹到指定位置》》》》》》》》》")
            mdHtml.moveImg2Place(self.parent.img_path, self.parent.img_export_path)
            self.progress.emit("图片移动完成！！！》》》》》》》》》")

            server_ip = self.parent.ssh_ip_text.text()
            port = self.parent.ssh_port_text.text()
            username = self.parent.ssh_username_text.text()
            password = self.parent.ssh_password_text.text()

            local_folder = html_directory
            remote_extract_folder = self.parent.server_file_path_text.text()
            remote_zip_file = remote_extract_folder + '.zip'
            self.progress.emit("文件处理完成，开始上传》》》》》》》》》")

            # 压缩文件夹
            zip_file = sshSend.create_zip(local_folder)

            # 建立SSH连接
            ssh = sshSend.create_ssh_client(server_ip, port, username, password)

            # 上传zip文件
            sshSend.upload_file_via_scp(ssh, zip_file, remote_zip_file)

            # 在Windows服务器上解压缩
            unzip_command = f'powershell -command "Expand-Archive -Path \'{remote_zip_file}\' -DestinationPath \'{remote_extract_folder}\' -Force"'
            stdin, stdout, stderr = ssh.exec_command(unzip_command)

            # 打印命令输出（可选）
            print(stdout.read().decode())
            print(stderr.read().decode())

            self.progress.emit("上传成功》》》》》》》》》")
        except Exception as e:
            self.error.emit(str(e))
        finally:
            if 'ssh' in locals():
                ssh.close()
            if 'zip_file' in locals():
                os.remove(zip_file)
            self.finished.emit()

class NoteDeployService(noteDeploy.Ui_MainWindow):
    def __init__(self):
        super(NoteDeployService, self).__init__()
        self.note_export_path_text = None
        self.note_path_text = None
        self.worker = None
        self.img_path = None
        self.img_export_path = None


    def btn_connect(self):
        print("按钮连接")

        # 文件夹选择按钮绑定，连接机械臂
        self.select_note_dir_btn.clicked.connect(self.select_note_dir)
        self.select_note_export_dir_btn.clicked.connect(self.select_note_export_dir)
        # 配置保存
        self.save_config_btn.clicked.connect(self.save_config)

        self.export_and_upload_btn.clicked.connect(self.export_and_upload)

        self.img_relative_btn.clicked.connect(self.img_relative_handle)

        self.load_config()

    def select_note_dir(self):
        print("选择文件位置")
        window = QWidget()
        note_dir = QFileDialog.getExistingDirectory(window, "选择文件夹")
        if note_dir:
            self.note_path_text.setText(note_dir)

    def select_note_export_dir(self):
        print("选择导出文件位置")
        window = QWidget()
        note_export_dir = QFileDialog.getExistingDirectory(window, "选择文件夹")
        if note_export_dir:
            self.note_export_path_text.setText(note_export_dir)

    def export_and_upload(self):
        print("转换并上传")
        self.export_and_upload_btn.setEnabled(False)
        self.worker = Worker(self)  # 不传递 parent 参数
        self.worker.progress.connect(self.update_progress)
        self.worker.finished.connect(self.on_finished)
        self.worker.error.connect(self.on_error)
        self.worker.start()

    # def update_progress(self, message):
    #     self.info_show_text.setText(message)

    def update_progress(self, message):
        self.info_show_text.append(message)  # 使用 append() 方法追加消息
        self.info_show_text.moveCursor(QTextCursor.End)  # 自动滚动到最新行


    def on_finished(self):
        self.export_and_upload_btn.setEnabled(True)
        self.info_show_text.setText("操作完成！")

    def on_error(self, error_message):
        self.export_and_upload_btn.setEnabled(True)
        self.info_show_text.setText(f"错误：{error_message}")

    def save_config(self):
        settings = QSettings("config.ini", QSettings.IniFormat)

        settings.beginGroup("FilePath")
        settings.setValue("NotePath", self.note_path_text.text())
        settings.setValue("NoteExportPath", self.note_export_path_text.text())
        settings.setValue("TargetDirPath", self.server_file_path_text.text())
        settings.endGroup()

        settings.beginGroup("SSHConfig")
        settings.setValue("host", self.ssh_ip_text.text())
        settings.setValue("port", self.ssh_port_text.text())
        settings.setValue("username", self.ssh_username_text.text())
        settings.setValue("password", self.ssh_password_text.text())
        settings.endGroup()

        settings.sync()
        self.info_show_text.setText("配置保存成功！！！")

    def load_config(self):
        settings = QSettings("config.ini", QSettings.IniFormat)

        settings.beginGroup("FilePath")
        self.note_path_text.setText(settings.value("NotePath", ""))
        # 增加图片地址，固定，不做可自定义配置
        self.img_path = settings.value("ImgDirPath", "")
        self.img_export_path = settings.value("ImgExportPath", "")
        self.note_export_path_text.setText(settings.value("NoteExportPath", ""))
        self.server_file_path_text.setText(settings.value("TargetDirPath", ""))
        settings.endGroup()

        settings.beginGroup("SSHConfig")
        self.ssh_ip_text.setText(settings.value("host", ""))
        self.ssh_port_text.setText(settings.value("port", ""))
        self.ssh_username_text.setText(settings.value("username", ""))
        self.ssh_password_text.setText(settings.value("password", ""))
        settings.endGroup()

        self.info_show_text.setText("配置加载成功！！！")

    def img_relative_handle(self):
        # 用户修改为自己的文件路径
        md_folder = 'D:/GitFiles/typoraFiles'  # Markdown文件夹路径
        image_folder = 'D:/GitFiles/typoraFiles/Pictures/imgs'  # 图片文件夹路径

        md_folder = self.note_path_text.text()  # Markdown文件夹路径
        image_folder = self.img_path  # 图片文件夹路径

        print(f"Markdown文件夹: {md_folder}")
        print(f"图片文件夹: {image_folder}")

        unreferenced, incorrect, fixed = ImgCheck.check_and_fix_image_references(md_folder, image_folder)

        # 生成报告
        ImgCheck.generate_report(md_folder, image_folder, unreferenced, incorrect, fixed)

        print("\n检查和修复过程已完成。详细信息请查看生成的报告文件。")

        self.info_show_text.setText("图片处理完成！！！")

