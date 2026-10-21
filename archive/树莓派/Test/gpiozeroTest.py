from gpiozero import Button
from signal import pause

# 创建按钮对象，连接到 GPIO 17
button = Button(17)

# 按钮被按下时的回调函数
def on_button_press():
    print("按钮被按下！")

# 按钮未按下时的回调函数
def on_button_release():
    print("按钮未被按下。")

# 连接按钮的事件
button.when_pressed = on_button_press
button.when_released = on_button_release

# 保持程序运行，等待事件
pause()
