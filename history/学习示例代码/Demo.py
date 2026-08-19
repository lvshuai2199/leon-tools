result = 0
#max = 6
#k = 3
#N = -3
#输入数据
max,N,k = [int(x)for x in input().split()]
if ( abs(N) < k ):
    k = 0
    for k in range(abs(N)+1):
        result += k
if (result < max):
    print(result)
else:
    print("result >= max")
print("运行结束！")
