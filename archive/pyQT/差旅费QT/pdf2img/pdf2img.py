import fitz  # PyMuPDF
import re

# PDF发票的路径
pdf_path = 'problemInvoice.pdf'

# 打开PDF文件
doc = fitz.open(pdf_path)

# 初始化提取的文本
extracted_text = ''

# 遍历PDF中的每一页
for page_num in range(len(doc)):
    page = doc[page_num]

    # 提取当前页面的文本
    text = page.get_text()

    # 将当前页面的文本添加到提取的文本中
    extracted_text += text

# 关闭PDF文件
doc.close()

# 定义抬头和税号的正则表达式模式
# 这些模式需要根据实际的发票格式进行调整
head_pattern = r'抬头:\s+(.*)'
tax_number_pattern = r'税号:\s+(.*)'

# 使用正则表达式查找抬头和税号
head_match = re.search(head_pattern, extracted_text, re.IGNORECASE)
tax_number_match = re.search(tax_number_pattern, extracted_text, re.IGNORECASE)

# 如果找到匹配项，则打印抬头和税号
if head_match:
    company_head = head_match.group(1).strip()
    print(f"抬头: {company_head}")

if tax_number_match:
    tax_number = tax_number_match.group(1).strip()
    print(f"税号: {tax_number}")

# 如果没有找到匹配项，则打印相应的消息
if not head_match:
    print("未找到抬头信息")

if not tax_number_match:
    print("未找到税号信息")