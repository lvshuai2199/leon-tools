#include "form.h"
#include "ui_form.h"

Form::Form(QWidget *parent)
    : QWidget(parent)
    , ui(new Ui::Form)
{
    ui->setupUi(this);

    robot = new robotControl();
}

Form::~Form()
{
    delete ui;
}

void Form::on_initBtn_clicked()
{
    robot->initRobot();
}


void Form::on_getPoseBtn_clicked()
{
    robot->getPoseAndPrint();
}


void Form::on_inverseBtn_clicked()
{
    QMessageBox::information(nullptr, "提示", "开始测试");
    QApplication::processEvents();
    aubo_robot_namespace::Pos targetPos = {-0.790764, -0.293991, 0.606980};
        // 调用测试函数
    if (robot->testInverseKinematics(targetPos)) {
        QMessageBox::information(this, "成功", "逆解成功！请查看控制台输出。");
    } else {
        QMessageBox::critical(this, "失败", "逆解失败！");
    }
}


void Form::on_jointMoveBtn_clicked()
{
    robot->con_robotMove();
}


void Form::on_moveStop_btn_clicked()
{
    robot->con_robotStop();
}


void Form::on_moveFastStop_btn_clicked()
{
    robot->con_robotFastStop();
}


void Form::on_jointMoveBtn_2_pressed()
{
    robot->robotMoveJoint();

}


void Form::on_jointMoveBtn_2_released()
{
    robot->con_robotFastStop();
}

