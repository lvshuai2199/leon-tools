import traceback

try:
    # 在这里执行可能会出现问题的代码，比如读取 PDF 文件
    with open('your_problematic_file.pdf', 'r') as file:
        # 读取文件内容或执行其他操作
        pass
except Exception as e:
    # 捕获异常并显示出问题的文件名称
    print("An error occurred with file:", e.filename)
    # 或者使用 traceback 打印详细的错误信息
    traceback.print_exc()