import threading
import time

# 回调：恢复时要执行的代码
def on_resume_callback():
    """程序从暂停恢复瞬间调用这段代码"""
    print("===== 触发恢复回调！执行额外逻辑 =====")
    # 在这里写你要跑的代码：重置参数、打印日志、读取配置、初始化设备等


class PausableTask:
    def __init__(self):
        self._pause_event = threading.Event()
        self._pause_event.set()  # set = 不阻塞，运行状态
        self.last_pause_state = False  # 记录上一轮是否暂停

    def pause(self):
        """设置为暂停"""
        self._pause_event.clear()

    def resume(self):
        """恢复运行，这里触发回调"""
        # 只有原来是暂停状态，才执行回调，防止重复调用
        if not self._pause_event.is_set():
            on_resume_callback()
        self._pause_event.set()

    def is_paused(self):
        return not self._pause_event.is_set()

    def run_business_loop(self):
        """业务工作循环，跑在子线程"""
        count = 0
        while True:
            # 如果暂停，这里阻塞住
            self._pause_event.wait()

            # 业务代码
            count +=1
            print(f"业务正在工作: {count}")
            time.sleep(0.5)


if __name__ == "__main__":
    task = PausableTask()
    t = threading.Thread(target=task.run_business_loop, daemon=True)
    t.start()

    print("指令： p=暂停  r=恢复 q=退出")
    while True:
        cmd = input("> ").strip()
        if cmd == "p":
            task.pause()
            print("已暂停")
        elif cmd == "r":
            task.resume()
            print("已恢复")
        elif cmd == "q":
            print("退出")
            break