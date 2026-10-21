'''
word操作。
利用python实现word软件的自动化操作
pywin32已安装
'''

# 利用python打开记事本
# 方式一：利用os模块
import os
import time

def runApp():
    os.system(u"C:\\Windows\\System32\\notepad.exe")
# 方式二：使用win32api中的ShellExecute函数
def runApp2():
    import win32api
    #最后一个参数表示是窗口属性，0表示不显示，1表示正常显示，2表示最小化，3表示最大化
    res = win32api.ShellExecute(0, 'open', 'C:\\Windows\\System32\\notepad.exe', 'C:\\Users\\13326\\Desktop\\刷课.txt', '', 2)
    #res = win32api.ShellExecute(0,'open','C:\\Windows\\System32\\notepad.exe','','',3)
# 还有其他方式，详情参考博文：Python调用（运行）外部程序。

# ***************  通过句柄操作软件  ***************

# 打开软件后要实际操作软件，需要获取软件的句柄。 ** 句柄的获取对后续操作至关重要
# 打开 mysql命令.txt 记事本程序，要获取它的句柄
def findAppHandle():
    #等待0.1秒，以防句柄无法完全显示出来
    time.sleep(0.1)
    import win32gui
    appName = u"刷课.txt - 记事本"
    hwnd = win32gui.FindWindow(None,appName)
    print(appName+'的句柄为'+str(hwnd))
    return hwnd

#通过句柄操作软件
def operataHandle():
    hwnd = findAppHandle()
    import win32gui
    import win32con
    #关闭软件
    win32gui.PostMessage(hwnd,win32con.WM_CLOSE,0,0)
    #软件最大化
    #win32gui.PostMessage(hwnd,win32con.WM_SYSCOMMAND,win32con.SC_MAXIMIZE,0)
    #将软件窗口置于最前
    #win32gui.SetForegroundWindow(hwnd)


'''
利用按键精灵来实现点击操作
自动化软件在大部分情况下还有更方便的工具，那就是按键精灵
很多时候，项目所要自动化的软件中的很多的窗口句柄和操作很难通过程序来直接操作。
按键精灵是一款模拟鼠标键盘动作的软件。软件通过
注：结合按键精灵完成程序的自动化操作
要完成文件的自动打印，可以使用文件打开 -> 窗口最大化 -> 按键精灵实现打印操作 -> 窗口关闭 -> 继续浏览文件直至文件夹中文件全部浏览完毕
'''

if __name__ == '__main__':
    #主函数已测试完毕。正常使用
    #分别调用封装函数
    print('123456')
    # runApp()
    runApp2()
    findAppHandle()
    operataHandle()