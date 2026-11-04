#include <QApplication>

#include "form.h"

#include <Windows.h>

int main(int argc, char *argv[])
{
    QApplication a(argc, argv);

    // Set up code that uses the Qt event loop here.
    // Call a.quit() or a.exit() to quit the application.
    // A not very useful example would be including
    // #include <QTimer>
    // near the top of the file and calling
    // QTimer::singleShot(5000, &a, &QCoreApplication::quit);
    // which quits the application after 5 seconds.

    // If you do not need a running Qt event loop, remove the call
    // to a.exec() or use the Non-Qt Plain C++ Application template.

    // robotControl *robot = new robotControl();

    // robot->example_login(g_rshd, ROBOT_ADDR, ROBOT_PORT);

    // robot->getPoseAndPring(g_rshd);

    // 通过系统调用执行 chcp 命令
    std::system("chcp 65001 > nul");

    // // 设置全局编码为 UTF - 8
    // QTextCodec *codec = QTextCodec::codecForName("UTF - 8");
    // QTextCodec::setCodecForLocale(codec);

    // 设置控制台输出编码为 UTF-8
    SetConsoleOutputCP(CP_UTF8);

    Form w;
    w.show();


    return a.exec();
}
