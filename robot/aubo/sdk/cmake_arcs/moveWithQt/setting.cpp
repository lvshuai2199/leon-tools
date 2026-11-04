#include "setting.h"
#include "ui_setting.h"

Setting::Setting(QWidget *parent) : QWidget(parent), ui(new Ui::Setting)
{
    ui->setupUi(this);
    qDebug() << "setting page open";
}
// 构造函数中传递 robotCon
Setting::Setting(robotControl *robot, QWidget *parent)
    : QWidget(parent), robotCon(robot), ui(new Ui::Setting)
{
    ui->setupUi(this);
    qDebug() << "Setting page open";
}

Setting::~Setting()
{
    delete ui;
}

void Setting::on_robotInit_Btn_clicked()
{
    robotCon->con_init();
}

void Setting::on_startUp_Btn_clicked()
{
    robotCon->con_powerOn();
}

void Setting::on_powerOff_Btn_clicked()
{
    robotCon->con_powerOff();
}
