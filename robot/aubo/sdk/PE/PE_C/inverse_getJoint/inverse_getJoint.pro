QT = core gui widgets

CONFIG += c++17 cmdline

CODECFORTR = UTF - 8

# You can make your code fail to compile if it uses deprecated APIs.
# In order to do so, uncomment the following line.
#DEFINES += QT_DISABLE_DEPRECATED_BEFORE=0x060000    # disables all the APIs deprecated before Qt 6.0.0

# 包含头文件路径
INCLUDEPATH += $$PWD/dependents/inc

# 链接动态库
# win32: LIBS += -L$$PWD/dependents/auboi5/lib -lserviceinterface
# 添加库文件所在的路径
LIBS += -L$$PWD/dependents/lib
# LIBS += -LD:/GitFiles/aubo_sdk/PE/PE_C/inverse_getJoint/dependents/lib
# 添加具体的库名
LIBS += -llibserviceinterface

# 输出调试信息
message("LIBS: $$LIBS")
message("Library path: $$PWD/dependents/auboi5/lib/")

# 复制 DLL 文件到可执行文件输出目录
win32 {
    # 调试模式下复制 DLL 文件
    debug: {
        DESTDIR = $$OUT_PWD/debug
        QMAKE_POST_LINK += $$quote(xcopy /y /d $$PWD/dependents/auboi5/dll/*.dll $$DESTDIR)
    }
    # 发布模式下复制 DLL 文件
    release: {
        DESTDIR = $$OUT_PWD/release
        QMAKE_POST_LINK += $$quote(xcopy /y /d $$PWD/dependents/auboi5/dll/*.dll $$DESTDIR)
    }
}

SOURCES += \
        form.cpp \
        main.cpp \
        robotcontrol.cpp

HEADERS += \
    form.h \
    robotcontrol.h

# Default rules for deployment.
qnx: target.path = /tmp/$${TARGET}/bin
else: unix:!android: target.path = /opt/$${TARGET}/bin
!isEmpty(target.path): INSTALLS += target

FORMS += \
    form.ui
