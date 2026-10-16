cmake_minimum_required(VERSION 3.21)
project(CursorUsageSettings LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC OFF)
set(CMAKE_AUTOUIC OFF)

find_package(Qt6 REQUIRED COMPONENTS Widgets)

add_executable(CursorUsageSettings WIN32
  src/main.cpp
  src/SettingsWindow.cpp
  src/SettingsWindow.h
  src/ConfigStore.cpp
  src/ConfigStore.h
  src/HostBridge.cpp
  src/HostBridge.h
)

target_link_libraries(CursorUsageSettings PRIVATE Qt6::Widgets)

# Windows cross: link shell32 for FindWindow etc already in user32
if(WIN32 OR CMAKE_SYSTEM_NAME STREQUAL "Windows")
  target_link_libraries(CursorUsageSettings PRIVATE user32 shell32)
endif()

install(TARGETS CursorUsageSettings RUNTIME DESTINATION bin)
