# STM32 舵机

用 STM32F103C8 把舵机转到指定角度。网页下发目标角和速度，控制层走串口，固件用 PA0 输出 50Hz PWM。

假设：

- 板子是 STM32F103C8T6，外部晶振 8MHz。没有晶振时固件退回内部 8MHz，串口仍是 115200。
- 舵机是 0–180° 的 hobby 舵机。默认 500µs = 0°，2500µs = 180°，周期 20ms。
- BOOT0 接地，从 Flash 启动。
- 舵机单独供 5V，和开发板、USB 转串口共地。信号接 PA0，不要用 3.3V 脚给舵机供电。

## 接线

| 功能 | 引脚 |
| --- | --- |
| 舵机信号 | PA0，TIM2_CH1 |
| 串口 TX | PA9，接到 USB 转串口 RX |
| 串口 RX | PA10，接到 USB 转串口 TX |
| 状态灯 | PC13，500ms 闪烁表示固件在跑 |

## 上位机

安装 Python 3。真机再装串口库：

```text
pip install -r host/requirements.txt
```

双击 `启动.bat`，打开 http://127.0.0.1:8766 。没有板子时点「仿真」。速度单位是 °/s，填 0 会立刻到位。

## 固件

工具链是 `arm-none-eabi-gcc`。

```text
cd firmware
make
st-flash write build/servo.bin 0x08000000
```

## 协议

串口 115200，一行一条，以 `\n` 结束。角度写到 0.01°。

```text
PING
PONG

GET
ANG 45.00 90.00

SET 90.00 30
OK 90.00

SET 0.00 0
OK 0.00

STOP
OK 45.00

CFG?
CFG 500 2500

CFG 1000 2000
OK
```

`ANG` 后面先是当前角，再是目标角。`SET` 的第二个数是速度，范围 0–360，省略时按 0。超出范围返回 `ERR RANGE`，格式不对返回 `ERR SYNTAX`。

固件每 20ms 走一步，步长是速度的 2 个百分度，所以 100°/s 正好每秒 100°。
