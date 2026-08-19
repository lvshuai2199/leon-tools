import os
from tkinter import *
from tkinter import messagebox
import tkinter.filedialog

root = Tk()
root.title("筛选重复的视频和照片")
root.geometry("500x500+500+200")


def wbb():
    a = []
    c = {}
    filename = tkinter.filedialog.askopenfilenames()

    for i in filename:
        with open(i, 'rb') as f:
            a.append(f.read())
    for j in range(len(a)):
        c[a[j]] = filename[j]
    filename1 = tkinter.filedialog.askdirectory()

    if filename1 != "":
        p = 1
        lb1.config(text=filename1 + "下的文件为：")
        for h in c:
            k = c[h].split(".")[-1]
            with open(filename1 + "/" + str(p) + "." + k, 'wb') as f:
                f.write(h)
            p = p + 1
        for g in os.listdir(filename1):
            txt.insert(END, g + '\n')

    else:
        messagebox.showinfo("提示", message='请选择路径')


frame1 = Frame(root, relief=RAISED)
frame1.place(relx=0.0)

frame2 = Frame(root, relief=GROOVE)
frame2.place(relx=0.5)

lb1 = Label(frame1, text="等等下面会有变化？", font=('华文新魏', 13))
lb1.pack(fill=X)

txt = Text(frame1, width=30, height=50, font=('华文新魏', 10))
txt.pack(fill=X)

lb = Label(frame2, text="点我选择要进行筛选的文件：", font=('华文新魏', 10))
lb.pack(fill=X)

btn = Button(frame2, text="请选择要进行筛选的文件", fg='black', relief="raised", bd="9", command=wbb)
btn.pack(fill=X)
btn1 = Button(frame2, text="创建文件", fg='black', relief="raised", bd="9", command=wbb)
btn1.pack(fill=X)


# frame3 = Frame(root, relief=GROOVE)
# frame3.place(relx=0.7)

root.mainloop()

