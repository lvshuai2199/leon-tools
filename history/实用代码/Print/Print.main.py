# This is a sample Python script.

# Press Shift+F10 to execute it or replace it with your code.
# Press Double Shift to search everywhere for classes, files, tool windows, actions, and settings.

'''
def print_hi(name):
    # Use a breakpoint in the code line below to debug your script.
    print(f'Hi, {name}')  # Press Ctrl+F8 to toggle the breakpoint.
'''

import commonPrint
import rjgcPr

# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    #print_hi('PyCharm')
    #输入当前打印开头,默认为1
    #i=int(input('输入打印开头：'))
    i=1
    print('打印开头：'+ str(i))
    #输入当前打印结尾
    num=int(input('输入打印结尾：'))
    print('\n打印时按照输出的打印顺序复制填入打印页选择框进行打印，即可完整，且有序地打印出相应地文件')
    commonPrint.print_dy(i, num)
    rjgcPr.rjgcprint_dy(i, num)
    print('\n单页请单独打印，否则将无法实现双页打印的效果')




# See PyCharm help at https://www.jetbrains.com/help/pycharm/
