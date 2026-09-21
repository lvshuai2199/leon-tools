#!/bin/bash

# 切换到脚本所在目录
cd "$(dirname "$0")"

# 1. 找到最新的 .zip 文件（按修改时间排序，取第一个）
LATEST_ZIP=$(ls -t *.zip 2>/dev/null | head -n 1)

if [ -z "$LATEST_ZIP" ]; then
    zenity --error --text="没找到任何 .zip 文件！" --title="错误"
    exit 1
fi

echo "最新压缩包: $LATEST_ZIP"

# 2. 推导解压目录名（去掉 .zip 后缀）
TARGET_DIR="${LATEST_ZIP%.zip}"

# 3. 如果目录已存在，先删除（避免旧文件残留）—— 谨慎，可按需注释掉
if [ -d "$TARGET_DIR" ]; then
    echo "目录 $TARGET_DIR 已存在，先删除旧目录"
    rm -rf "$TARGET_DIR"
fi

# 4. 解压
echo "正在解压到 $TARGET_DIR ..."
unzip -q "$LATEST_ZIP" -d "$TARGET_DIR"

if [ $? -ne 0 ]; then
    zenity --error --text="解压失败！" --title="错误"
    exit 1
fi

# 5. 找到 run.sh（可能在子目录里）
RUN_SH=$(find "$TARGET_DIR" -name "run.sh" -type f | head -n 1)

if [ -z "$RUN_SH" ]; then
    zenity --error --text="解压后没找到 run.sh！" --title="错误"
    exit 1
fi

echo "找到 run.sh: $RUN_SH"

# 6. 赋可执行权限并运行
chmod +x "$RUN_SH"
cd "$(dirname "$RUN_SH")"
./run.sh
