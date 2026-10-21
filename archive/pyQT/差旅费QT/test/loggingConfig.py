import logging

# 创建一个logger
logger = logging.getLogger('my_logger')
logger.setLevel(logging.DEBUG)  # 设置日志级别

# 创建一个handler，用于将日志写入文件
log_file = 'my_log.log'
file_handler = logging.FileHandler(log_file, 'a', 'utf-8')  # 使用'utf-8'编码
file_handler.setLevel(logging.DEBUG)

# 创建一个handler，用于将日志输出到控制台
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.DEBUG)

# 定义handler的输出格式
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
file_handler.setFormatter(formatter)
console_handler.setFormatter(formatter)

# 将handlers添加到logger
logger.addHandler(file_handler)
logger.addHandler(console_handler)

# 测试日志输出
logger.debug('这是一条UTF-8编码的日志信息。')