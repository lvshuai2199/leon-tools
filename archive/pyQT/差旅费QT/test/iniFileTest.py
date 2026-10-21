import configparser


def read_companies_from_config(filename):
    config = configparser.ConfigParser()

    # 从配置文件中读取数据
    config.read(filename, 'utf-8')

    companies = {}
    for section in config.sections():
        # 跳过 'saveDir' 和 'savePath' 部分
        if section == 'saveDir' or section == 'savePath':
            continue

        company_name = config[section].get('CompanyName', '').replace(' ', '')
        tin = config[section].get('TIN', '').replace(' ', '')
        companies[section] = {'CompanyName': company_name, 'TIN': tin}

    return companies


# 使用示例
filename = '../config.ini'
companies = read_companies_from_config(filename)
print(companies)