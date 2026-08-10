import time
import _thread

# 全局变量（顶层定义，不要写global）
msg = "初始内容"


def thread_reader():
    """持续读取全局变量的线程，模拟HTTP服务不断读取message"""
    global msg
    while True:
        print(f"【读线程】当前msg = {msg}")
        time.sleep(1.5)


def thread_writer():
    """每隔3秒修改全局变量，模拟RPC修改message"""
    global msg
    count = 1
    while True:
        msg = f"被更新的内容 {count}"
        print(f"【写线程】执行修改 → {msg}")
        count += 1
        time.sleep(3)


if __name__ == "__main__":
    # 启动读写两个线程
    _thread.start_new_thread(thread_reader, ())
    _thread.start_new_thread(thread_writer, ())

    # 主线程持续等待
    while True:
        time.sleep(1)