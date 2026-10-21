from openpyxl import Workbook

outwb = Workbook()
outws = outwb.worksheets[0]

mongoDB_data = [{'name': '周', 'age': 18, 'sex': '男'},
                {'name': '王', 'age': 19, 'sex': '男'},
                {'name': '李', 'age': 16, 'sex': '女'}]

outws.append(['姓名', '年龄', '性别'])  # 先添加一行表头
# 遍历外层列表
for new_dict in mongoDB_data:
    a_list = []
    # 遍历内层每一个字典dict，把dict每一个值存入list
    for item in new_dict.values():
        a_list.append(item)
    # sheet直接append list即可
    outws.append(a_list)

outwb.save(r'test.xlsx')
print('数据存入excel成功')