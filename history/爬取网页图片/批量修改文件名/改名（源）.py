#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import shutil

from tkinter import *
import tkinter.messagebox as messagebox
from tkinter.filedialog import askdirectory


def modifyPrefixDisplay(filePath, prefix=None, suffix='1'):
    oddAllFileName = os.listdir(filePath)
    if oddAllFileName:
        fileKind = (os.path.splitext(oddAllFileName[0]))[1]
    newAllFileName = [prefix + str(int(suffix) + i) + fileKind for i in range(len(oddAllFileName))]

    displayStr = ''
    for oldName, newName in zip(oddAllFileName, newAllFileName):
        displayStr = displayStr + oldName + "-->" + newName + '\n'
        print(oldName, "-->", newName)
    return displayStr


def modifyPrefix(filePath, prefix=None, suffix='1'):
    oddAllFileName = os.listdir(filePath)
    if oddAllFileName:
        fileKind = (os.path.splitext(oddAllFileName[0]))[1]
    newAllFileName = [prefix + str(int(suffix) + i) + fileKind for i in range(len(oddAllFileName))]

    # print(oddAllFileName)
    # print(oddAllFileNameAbs)
    # print(newAllFileNameAbs)

    isRepeatFile = False
    for oldName, newName in zip(oddAllFileName, newAllFileName):
        if newName in oddAllFileName:
            print("文件%s已经存在!" % newName)
            isRepeatFile = True

    if not isRepeatFile:
        for oldName, newName in zip(oddAllFileName, newAllFileName):
            oldName = os.path.join(filePath, oldName)
            newName = os.path.join(filePath, newName)
            shutil.move(oldName, newName)

        print("重命名完成！")

    return isRepeatFile


class Framework(Frame):
    def __init__(self, master=None):
        Frame.__init__(self, master)

        self.filePath = StringVar()

        self.pack()
        self.canvas = Canvas(master, width=400, height=300, bg="white")
        self.canvas.pack()

        self.createWidgets()

    def createWidgets(self):
        self.modifyFileNameLabel = Label(self)
        self.modifyFileNameLabel.grid()

        self.filePathLabel = Label(self, text="path:")
        self.filePathLabel.grid(row=0, column=0, sticky=E, padx=10, pady=15)
        self.filePathEntry = Entry(self, textvariable=self.filePath)
        self.filePathEntry.grid(row=0, column=1, sticky=E, padx=10, pady=15)

        self.selectPathButton = Button(self, text='select path', command=self.selectPath)
        self.selectPathButton.grid(row=0, column=2, columnspan=1)

        self.prefixLabel = Label(self, text="prefix:")
        self.prefixLabel.grid(row=1, column=0, sticky=E, padx=10, pady=15)
        self.prefixEntry = Entry(self)
        self.prefixEntry.grid(row=1, column=1, sticky=E, padx=10, pady=15)

        self.displayButton = Button(self, text='display', command=self.display)
        # self.displayButton.grid(row=2, columnspan=2)
        self.displayButton.grid(row=2, column=0, columnspan=1)

        self.makeButton = Button(self, text='make', command=self.make)
        # self.makeButton.grid(row=2, columnspan=2)
        self.makeButton.grid(row=2, column=2, columnspan=2)

    def selectPath(self):
        path = askdirectory()
        self.filePath.set(path)

    def display(self):
        prefix = self.prefixEntry.get()
        self.filePath = self.filePathEntry.get()
        self.dirExists()

        # filePath = r"C:\Users\xxxx\Desktop\modifyFileName\test"
        displayStr = modifyPrefixDisplay(self.filePath, prefix)

        print(displayStr)
        print("=======================")
        self.canvas.delete("string")
        self.canvas.create_text(100, 100, text=displayStr, tags="string")

    def make(self):
        prefix = self.prefixEntry.get()
        self.filePath = self.filePathEntry.get()
        self.dirExists()

        # filePath = r"C:\Users\xxxx\Desktop\modifyFileName\test"
        # modifyPrefixDisplay(self.filePath, prefix)

        result = modifyPrefix(self.filePath, prefix)

        if result:
            self.errorFrame("重命名失败！文件名重复！")
        else:
            self.infoFrame("重命名完成！")

        sys.exit()

    def infoFrame(self, message):
        messagebox.showinfo("Message", message)

    def errorFrame(self, message):
        messagebox.showerror("Error", message)

    def dirExists(self):
        result = os.path.exists(self.filePath)
        if not result:
            self.errorFrame("文件夹不存在")
            sys.exit()

tool = Framework()
tool.master.title('Modify File Name Tool')
tool.mainloop()
