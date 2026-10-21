
import teach as UIPage

class UIPage(UIPage.Ui_Form):
    def __init__(self):
        super(UIPage, self).__init__()
        # 文件存储名称
        # 按钮点击功能绑定

    # def btnConnect(self):
    #     self.getPasswordBtn.clicked.connect(self.getMyPassword)
    #
    #
    # def getMyPassword(self):
    #     oriWords = self.oriEditText.text()
    #     # 示例用法
    #     shift = -3
    #     encrypted_text = caesar_encrypt(oriWords, shift)
    #     decrypted_text = caesar_decrypt(encrypted_text, shift)
    #
    #     print("Encrypted:", encrypted_text)
    #     print("Decrypted:", decrypted_text)
    #     print("Is Caesar Cipher:", is_caesar_cipher(oriWords, encrypted_text, shift))
    #     self.invEditText.setText(encrypted_text)
