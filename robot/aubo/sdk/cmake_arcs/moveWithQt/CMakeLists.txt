cmake_minimum_required(VERSION 3.5)

project(robotQt LANGUAGES CXX)

set(CMAKE_INCLUDE_CURRENT_DIR ON)

set(CMAKE_AUTOUIC ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# QtCreator supports the following variables for Android, which are identical to qmake Android variables.
# Check http://doc.qt.io/qt-5/deployment-android.html for more information.
# They need to be set before the find_package(Qt5 ...) call.

#if(ANDROID)
#    set(ANDROID_PACKAGE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/android")
#    if (ANDROID_ABI STREQUAL "armeabi-v7a")
#        set(ANDROID_EXTRA_LIBS
#            ${CMAKE_CURRENT_SOURCE_DIR}/path/to/libcrypto.so
#            ${CMAKE_CURRENT_SOURCE_DIR}/path/to/libssl.so)
#    endif()
#endif()

# 定义变量
set(libName "aubo_sdk-0.24.1-rc.1-Linux_x86_64+82cc24e")
set(DOWNLOAD_DIR "${PROJECT_SOURCE_DIR}/downloads")
set(DOWNLOAD_URL "https://download.aubo-robotics.cn/sdk/${libName}.zip")

# 定义下载文件的函数
function(download_and_extract url save_dir filename)
    set(zip_file "${save_dir}/${filename}.zip")
    set(extract_dir "${save_dir}/${filename}")

    # 创建下载目录
    file(MAKE_DIRECTORY ${save_dir})

    # 下载文件
    if(NOT EXISTS ${zip_file})
        message(STATUS "Downloading file ${url} to ${zip_file}")
        file(DOWNLOAD ${url} ${zip_file} STATUS download_status)
        list(GET download_status 0 download_error)
        if(download_error)
            message(FATAL_ERROR "Failed to download ${url}: ${download_status}")
        endif()
    else()
        message(STATUS "File ${zip_file} already exists, skipping download.")
    endif()

    # 解压文件
    if(NOT EXISTS ${extract_dir})
        message(STATUS "Extracting ${zip_file} to ${extract_dir}")
        execute_process(
            COMMAND unzip -o ${zip_file} -d ${save_dir}
            WORKING_DIRECTORY ${save_dir}
            RESULT_VARIABLE extract_result
        )
        if(extract_result)
            message(FATAL_ERROR "Failed to extract ${zip_file}: ${extract_result}")
        endif()
    else()
        message(STATUS "Directory ${extract_dir} already exists, skipping extract.")
    endif()
endfunction()

# 调用下载和解压函数
download_and_extract(${DOWNLOAD_URL} ${DOWNLOAD_DIR} ${libName})


# 设置msvc编码格式
add_compile_options("$<$<C_COMPILER_ID:MSVC>:/utf-8>")
add_compile_options("$<$<CXX_COMPILER_ID:MSVC>:/utf-8>")

macro(IMPORT_SDK SDK)
    if(DEFINED USING_SDK_SOURCE_CODE)
        message(STATUS "Use aubo_sdk source code.")
        set(SDK_LIB ${ARGV0})
        if("${ARGV1}" STREQUAL "robot_proxy")
            set(PROXY_LIB ${ARGV1})
        endif()
    else()
        set(AUBO_SDK_PATH ${DOWNLOAD_DIR}/${libName})
        message(STATUS "FOUND AUBO_SDK PATH ${AUBO_SDK_PATH}")
        set(CMAKE_PREFIX_PATH ${AUBO_SDK_PATH})
        find_package(aubo_sdk REQUIRED aubo_sdk)
        message(STATUS "Import aubo_sdk::${ARGV0}")
        set(SDK_LIB aubo_sdk::${ARGV0})
        if("${ARGV1}" STREQUAL "robot_proxy")
            message(STATUS "Import aubo_sdk::${ARGV1}")
            set(PROXY_LIB aubo_sdk::${ARGV1})
        endif()
    endif()
endmacro(IMPORT_SDK)

find_package(Qt5 COMPONENTS Widgets)
if(Qt5_FOUND)
    IMPORT_SDK(aubo_sdk robot_proxy)
#    add_subdirectory(robot_proxy EXCLUDE_FROM_ALL)
else()
    IMPORT_SDK(aubo_sdk)
endif()

find_package(Threads REQUIRED)
if (NOT EXECUTABLE_OUTPUT_PATH)
    set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/build/bin)
endif()


find_package(Qt5 COMPONENTS Widgets REQUIRED)

if(ANDROID)
  add_library(robotQt SHARED
    main.cpp
    widget.cpp
    widget.h
    widget.ui
  )
else()
  add_executable(robotQt
    main.cpp
    widget.cpp
    widget.h
    widget.ui
    setting.cpp
    setting.h
    setting.ui
  )
endif()


# 链接 robotcontrol_lib 库
target_link_libraries(robotQt PRIVATE Qt5::Widgets robotcontrol_lib)
