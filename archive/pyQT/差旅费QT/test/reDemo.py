import re

def extract_numbers(text):
    match = re.search(r'\d+\.\d+|\d+', text)
    if match:
        return match.group()  # 返回匹配到的数字及小数点
    else:
        return None

# 测试字符串
text = "'小写)￥910.00'"
number = extract_numbers(text)
print(number)  # 打印匹配到的数字及小数点