import configparser

cfp = configparser.ConfigParser()
cfp.read("conf.ini")

'''获取所有的selections'''
selections = cfp.sections()
print(selections) #  ['Title1', 'Title2']

'''获取指定selections下的所有options'''
options = cfp.options(selections[0])
print(options)  # ['key1', 'key2']

value= cfp.get("QRScan", "chooseDir")
print(value)  # 1111111111