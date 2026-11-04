#ifndef SETTING_H
#define SETTING_H

#include <QWidget>

#include <QDebug>

#include "widget.h"

namespace Ui {
class Setting;
}

class Setting : public QWidget
{
    Q_OBJECT

public:
    explicit Setting(QWidget *parent = nullptr);

    // 构造函数中传递 robotCon
    explicit Setting(robotControl *robot, QWidget *parent = nullptr);
    ~Setting();

private slots:
    void on_robotInit_Btn_clicked();

    void on_startUp_Btn_clicked();

    void on_powerOff_Btn_clicked();

private:
    robotControl *robotCon;

private:
    Ui::Setting *ui;
};

#endif // SETTING_H
