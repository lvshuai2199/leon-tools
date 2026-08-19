import RPi.GPIO as GPIO
import time

# 设置 GPIO 模式为 BCM
GPIO.setmode(GPIO.BCM)

# 定义按钮和 LED 的引脚
button_pins = [17, 18]  # 按钮连接到 GPIO 17 和 GPIO 18
led_pins = [26, 22]     # LED 连接到 GPIO 26 和 GPIO 22

# 设置按钮引脚为输入，并启用下拉电阻
for pin in button_pins:
    GPIO.setup(pin, GPIO.IN, pull_up_down=GPIO.PUD_DOWN)

# 设置 LED 引脚为输出
for pin in led_pins:
    GPIO.setup(pin, GPIO.OUT)
    GPIO.output(pin, GPIO.LOW)  # 初始状态为关闭

# 定义按钮按下的回调函数
def button_callback(channel):
    print(f"按钮 {channel} 被按下")
    # 根据按钮按下控制对应的 LED
    if channel == 17:
        GPIO.output(26, not GPIO.input(26))  # 切换 GPIO 26 的状态
    elif channel == 18:
        GPIO.output(22, not GPIO.input(22))  # 切换 GPIO 22 的状态

# 为每个按钮添加事件检测
for pin in button_pins:
    GPIO.add_event_detect(pin, GPIO.RISING, callback=button_callback, bouncetime=200)

# 主循环
try:
    print("按下按钮来控制 LED...")
    while True:
        time.sleep(1)  # 保持程序运行
except KeyboardInterrupt:
    print("程序已退出")
finally:
    GPIO.cleanup()  # 清理 GPIO 设置
