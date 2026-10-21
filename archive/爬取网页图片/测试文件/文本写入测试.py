import time
with open('test.txt', 'a+') as f:
    ti = time.strftime("%Y-%m-%d",time.localtime())
    f.write(str(ti) + u' url链接异常\n')
    f.close()