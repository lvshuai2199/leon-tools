TEMPLATE = app
CONFIG += console c++11
CONFIG -= app_bundle
CONFIG -= qt

# 所有依赖库全部下载到这里
DOWNLOAD_DIR = $$PWD/downloads

unix{
    # arcssdk 添加
    {
        libName = aubo_sdk-0.24.1-rc.1-Linux_x86_64+82cc24e
        DEFINES += ARCS_SDK_VERSION=\\\"$$libName\\\"
        DOWNLOAD_URL = https://download.aubo-robotics.cn/sdk/$${libName}.zip

        mybuild_download_$${libName}.target = download_$${libName}
        mybuild_download_$${libName}.commands = echo "ready to download lib [$$libName] from $$DOWNLOAD_URL, save to $$DOWNLOAD_DIR" && \
                                                [ ! -d $$DOWNLOAD_DIR ] && mkdir -p $$DOWNLOAD_DIR || echo "Directory already exists, skipping creation." && \
                                                echo "Downloading file..." && \
                                                [ ! -f $$DOWNLOAD_DIR/$${libName}.zip ] && wget -P $$DOWNLOAD_DIR $$DOWNLOAD_URL || echo "File already exists, skipping download." && \
                                                [ ! -d $$DOWNLOAD_DIR/$${libName} ] && unzip -o $$DOWNLOAD_DIR/$${libName}.zip -d $$DOWNLOAD_DIR  || echo "Directory already exists, skipping extract." && \
                                                [ ! -d $$OUT_PWD/resource/deploy/startup/lib/arcssdk ] && mkdir -p $$OUT_PWD/resource/deploy/startup/lib/arcssdk || echo "Target directory already exists, skipping creation." && \
                                                cp $$DOWNLOAD_DIR/$${libName}/lib/libaubo_sdk.so $$DOWNLOAD_DIR/$${libName}/lib/librobot_proxy.so $$OUT_PWD/resource/deploy/startup/lib/arcssdk
        message("require "$$DOWNLOAD_DIR/$${libName})
        QMAKE_EXTRA_TARGETS += mybuild_download_$${libName}
        PRE_TARGETDEPS += download_$${libName}

        INCLUDEPATH += $$DOWNLOAD_DIR/$${libName}/include
        LIBS += -L$$DOWNLOAD_DIR/$${libName}/lib -laubo_sdk -lrobot_proxy
    }
}


SOURCES += \
        main.cpp \
        robotcontrol.cpp

HEADERS += \
    robotcontrol.h
