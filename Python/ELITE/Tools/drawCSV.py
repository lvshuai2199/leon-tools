import re
import os
import numpy as np
import plotly.graph_objects as go
from plotly.subplots import make_subplots


def plot_with_plotly(filename):
    # 1. 读取文件
    try:
        current_dir = os.path.dirname(os.path.abspath(__file__))
    except NameError:
        current_dir = "."

    file_path = os.path.join(current_dir, filename)

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"读取文件失败: {e}")
        return

    # 2. 正则解析数据 [X, Y, Z, Rx, Ry, Rz]
    pattern = r"movep\s*\(full_apply_touch_offset\s*\(\[(.*?)\]\)"
    matches = re.findall(pattern, content)

    if not matches:
        print("未匹配到数据，请检查文件内容格式。")
        return

    trajectory = []
    for match in matches:
        coords = [float(x.strip()) for x in match.split(',')]
        trajectory.append(coords)

    data = np.array(trajectory)
    x, y, z = data[:, 0], data[:, 1], data[:, 2]
    indices = np.arange(len(data))  # 用于记录点位的顺序

    # 3. 创建 Plotly 图表
    # 我们创建一个包含两个子图的画布：左边 2D 投影，右边 3D 轨迹
    fig = make_subplots(
        rows=1, cols=2,
        specs=[[{'type': 'xy'}, {'type': 'scene'}]],
        subplot_titles=('XY 平面投影 (2D)', '空间 3D 轨迹')
    )

    # --- 左图：2D XY 轨迹 ---
    fig.add_trace(
        go.Scatter(
            x=x, y=y,
            mode='lines+markers',
            name='XY Path',
            marker=dict(size=5, color=indices, colorscale='Viridis', showscale=True),
            text=[f"Point: {i}<br>Z: {zi:.4f}" for i, zi in enumerate(z)],  # 悬停显示更多信息
            hovertemplate="Index: %{text}<br>X: %{x:.4f}<br>Y: %{y:.4f}<extra></extra>"
        ),
        row=1, col=1
    )

    # --- 右图：3D 轨迹 ---
    fig.add_trace(
        go.Scatter3d(
            x=x, y=y, z=z,
            mode='lines+markers',
            name='3D Path',
            line=dict(color='blue', width=2),
            marker=dict(size=3, color=indices, colorscale='Viridis'),
            hovertemplate="X: %{x:.4f}<br>Y: %{y:.4f}<br>Z: %{z:.4f}<extra></extra>"
        ),
        row=1, col=2
    )

    # 4. 布局优化
    fig.update_layout(
        title=f"机器人轨迹交互分析 - {filename}",
        showlegend=False,
        template="plotly_white",  # 干净的背景
        height=700
    )

    # 关键：设置左图 (2D) 的比例为 1:1，防止变形
    fig.update_xaxes(title_text="X (m)", scaleanchor="y", scaleratio=1, row=1, col=1)
    fig.update_yaxes(title_text="Y (m)", row=1, col=1)

    # 设置右图 (3D) 的轴标签
    fig.update_scenes(
        xaxis_title='X (m)',
        yaxis_title='Y (m)',
        zaxis_title='Z (m)',
        aspectmode='data'  # 3D 空间也保持比例
    )

    # 5. 打开浏览器展示
    fig.show()


# 调用
if __name__ == "__main__":
    # 确保同级目录下有 trace.txt
    plot_with_plotly('trace.txt')
