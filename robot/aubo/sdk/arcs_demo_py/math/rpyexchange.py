import numpy as np

# 假设当前的 cx, cy, cz 值
cx = 0
cy = -0.785
cz = 1.571

# 计算旋转角度 θ
theta = np.arctan2(cy, np.sqrt(cx**2 + cz**2))

# 构建旋转矩阵 R_z(θ)
R_z = np.array([
    [np.cos(theta), -np.sin(theta), 0],
    [np.sin(theta), np.cos(theta), 0],
    [0, 0, 1]
])

# 假设当前的旋转矩阵 R
R = np.array([
    [1, cx, 0],
    [0, cy, 0],
    [0, cz, 1]
])

# 应用旋转矩阵 R_z(θ)
R_prime = np.dot(R, R_z)

# 提取新的 cx, cy, cz 值
new_cx = R_prime[1, 0]
new_cy = R_prime[1, 1]
new_cz = R_prime[1, 2]

# 输出新的 cx, cy, cz 值
print("New cx:", new_cx)
print("New cy:", new_cy)
print("New cz:", new_cz)
