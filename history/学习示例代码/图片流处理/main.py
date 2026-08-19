import os

import cv2
import datetime
import time

# 打开视频文件
cap = cv2.VideoCapture('D:\liuti\FluidFlowFluent.wmv')  # 替换为你的视频文件路径

# 设置初始时间
# current_time = datetime.datetime(2023, 0, 0, 0, 0, 0) # 替换为你的初始时间
current_time = datetime.datetime.now()

# 帧间隔时间（秒）
frame_interval = 0.0002

frameNum = 0
while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    # 在帧上添加时间标签
    font = cv2.FONT_HERSHEY_SIMPLEX
    bottom_left_corner_of_text = (10, 30)
    font_scale = 1
    font_color = (255, 255, 255)
    line_type = 2

    # cv2.putText(frame, current_time.strftime('%Y-%m-%d %H:%M:%S'), bottom_left_corner_of_text, font, font_scale, font_color, line_type)

    # cv2.putText(frame, current_time.strftime('%Y-%m-%d %H:%M:%S.%f')[:-2], (50, 50), cv2.FONT_HERSHEY_SCRIPT_SIMPLEX, 1, (0, 0, 0), 2, cv2.LINE_AA)

    frameNum*frame_interval
    cv2.putText(frame, str(frameNum * int(frame_interval*10000)), (50, 50), cv2.FONT_HERSHEY_SCRIPT_SIMPLEX, 1, (0, 0, 0), 2, cv2.LINE_AA)
    frameNum += 1

    # 显示帧
    cv2.imshow('Video', frame)

    # 控制帧速率
    time.sleep(frame_interval)

    # 更新当前时间
    current_time += datetime.timedelta(seconds=frame_interval)

    # 检测按键，如果按下 'q' 键则退出循环
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break
print(frameNum)
cap.release()
cv2.destroyAllWindows()


