def rjgcprint_dy(i,num):
    #i为起始页，num为结尾页。
    #起始页一定是单印，当结尾页是偶数时，则需要实际双面打印的页数为双页
    #第一页起始页需要单独打印
    print('软件工程打印：')
    #打印逻辑
    pr = str(i) + '，'
    i += 1
    count = 1
    if ( num%2 == 0):
        pr += str(num)
        num -= 1
    #给i+1,num-1
    #odd为奇数，even为偶数
    prodd = ''
    preven = ''
    while( i <= num ):
        preven += str(i) + '、'
        prodd += str(i+1) + '、'
        i +=2
        count += 1
    #由于肯定有单页，所以必定输出0单页打印范围
    print('打印单页：'+ pr)
    print('双页第一次打印：' + prodd)
    print('双页翻页后打印：' + preven)
    #增加的0.3为手工费
    print('收费：'+str(count*0.3+0.3))







