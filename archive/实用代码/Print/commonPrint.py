def print_dy(i,num):
    # 输入已打印页数
    print('普通双印：')
    if ( num%2 == 1):
        pr=str(num)
        print('单独打印页：\t' + pr)
        num=num-1
    prodd = ''
    preven = ''
    while (i <= num):
        preven += str(i) + '、'
        prodd += str(i + 1) + '、'
        i += 2
    print('双页第一次打印' + prodd)
    print('双页翻页后打印' + preven)



    '''
    jd = i - 1
    dyji = str(i) + "、"
    dyou = str(i + 1) + "、"
    jgj = str(i - jd) + "、"
    jgo = str(i + 1 - jd) + "、"
    while (i < num - 1):
        i += 2
        #奇数
        dyji += str(i) + '、'
        #偶数
        dyou += str(i + 1) + '、'
        jgj += str(i - jd) + "、"
        #有奇数页的情况
        if ( i != num):
            jgo += str(i + 1 - jd) + "、"
    #全部的打印页
    print('奇数页:\t' + dyji)
    print('偶数页:\t' + dyou)
    print('打印选项(奇):\t' + jgj)
    print('打印选项(偶):\t' + jgo)
    '''
