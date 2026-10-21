def caesar_encrypt(text, shift):
    result = []
    for c in text:
        if c.isalpha():
            base = 'a' if c.islower() else 'A'
            result.append(chr(((ord(c) - ord(base) + shift) % 26) + ord(base)))
        else:
            result.append(c)  # 保持非字母字符不变
    return ''.join(result)

def caesar_decrypt(text, shift):
    return caesar_encrypt(text, -shift)  # 解密是加密的逆过程

def is_caesar_cipher(original, encrypted, shift):
    return caesar_encrypt(original, shift) == encrypted


import hashlib


def md5_encrypt(text):
    """
    MD5加密函数
    :param text: 要加密的字符串
    :return: MD5加密后的字符串（32位小写）
    """
    # 创建md5对象
    md5 = hashlib.md5()

    # 更新要加密的内容（需要转换为bytes）
    md5.update(text.encode('utf-8'))

    # 获取加密后的16进制字符串
    return md5.hexdigest()




import chargeTools as UIPage

class UIPage(UIPage.Ui_Form):
    def __init__(self):
        super(UIPage, self).__init__()
        # 文件存储名称
        # 按钮点击功能绑定

    def btnConnect(self):
        self.getPasswordBtn.clicked.connect(self.getMyPassword)


    def getMyPassword(self):
        if(self.lockType_comboBox.currentIndex() == 1):
            oriWords = self.oriEditText.text()
            # 示例用法
            shift = -3
            encrypted_text = caesar_encrypt(oriWords, shift)
            decrypted_text = caesar_decrypt(encrypted_text, shift)
            print("Encrypted:", encrypted_text)
            print("Decrypted:", decrypted_text)
            print("Is Caesar Cipher:", is_caesar_cipher(oriWords, encrypted_text, shift))
            self.invEditText.setText(encrypted_text)

        else:
            oriWords = self.oriEditText.text() + "youbo_leon"
            encrypted = md5_encrypt(oriWords)
            print(f"原文: {oriWords}")
            print(f"MD5: {encrypted}")

            # 取前12位
            encrypted_12 = encrypted[:12]
            print(f"前12位: {encrypted_12}")

            self.invEditText.setText(encrypted_12)

if __name__ == "__main__":
    oriWords = "VenOBXbF"
    shift = -3
    encrypted_text = caesar_encrypt(oriWords, shift)
    decrypted_text = caesar_decrypt(encrypted_text, shift)

    print("Encrypted:", encrypted_text)
    print("Decrypted:", decrypted_text)
    print("Is Caesar Cipher:", is_caesar_cipher(oriWords, encrypted_text, shift))

    # 使用示例
    text = "123456youbo_leon"
    encrypted = md5_encrypt(text)
    print(f"原文: {text}")
    print(f"MD5: {encrypted}")
