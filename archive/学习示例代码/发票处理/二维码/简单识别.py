import pyzbar.pyzbar as pyzbar
# 实际为安装了pillor库进行访问
from PIL import Image
'''
示例数据：
01,10,031002200511,71683020,3.30,20230721,03677848839994463406,7CBE,
对应位为：
*，*，发票代码，发票号码，金额，日期，校验码，*

还需增加，扫描图片不是发票情况，拟采用异常捕获实现，不会中断程序
'''
# 图片路径
image = "imgs/images_0.png"
# image = "DisHello.png"
# image = "hello.png"

img = Image.open(image)

# 解码 image 中的 datamatrix 条形码
barcodes = pyzbar.decode(img)

barcodeData = ""
for barcode in barcodes:
    barcodeData += barcode.data.decode("utf-8")

print(barcodeData)

