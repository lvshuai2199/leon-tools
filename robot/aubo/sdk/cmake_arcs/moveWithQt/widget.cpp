#include "widget.h"
#include "./ui_widget.h"

Widget::Widget(QWidget *parent) : QWidget(parent), ui(new Ui::Widget)
{
    ui->setupUi(this);

    robotCon = new robotControl();

    robotCon->con_init();

    // 创建 robotTool 的实例
    tool = new robotTool();

    pageInit();
    setDegEditData("0, 0, 90, 0, 90, 0");

    // 连接滑块的 valueChanged 信号到槽函数
    connect(ui->speed_Slider, &QSlider::valueChanged, this,
            &Widget::updateSpeedLabel);

    // 后续使用配置获取对应存储的默认参数速度
    ui->speed_Slider->setValue(19);

    robotCon->con_setSpeedFraction((ui->speed_Slider->sliderPosition() + 1) /
                                   100.0);
}

Widget::~Widget()
{
    delete ui;
}

robotControl *Widget::getRobotCon()
{
    return robotCon;
}

void Widget::pageInit()
{
    //    ui->speedSlider->setSliderPosition(10);
}

void Widget::updateSpeedLabel(int value)
{
    // 将滑块的当前值转为 QString，并显示在标签上
    ui->curSpeed_Label->setText(QString::number(value + 1));

    // 输出当前滑块位置到调试日志
    qDebug() << "current slider position:" << value + 1;
}
QString Widget::getRadEditData()
{
    //    ui->input_textEdit->setText("0, 30, 45, 60, 90, 180, 360");
    // 获取 QTextBrowser 的文本
    QString plainText = ui->rad_textEdit->toPlainText();
    qDebug() << plainText;
    return plainText;
}

void Widget::setRadEditData(QString inputData)
{
    ui->rad_textEdit->setText(inputData);
}

QString Widget::getDegEditData()
{
    //    ui->input_textEdit->setText("0, 30, 45, 60, 90, 180, 360");
    // 获取 QTextBrowser 的文本
    QString plainText = ui->deg_textEdit->toPlainText();
    qDebug() << plainText;
    return plainText;
}

void Widget::setDegEditData(QString inputData)
{
    ui->deg_textEdit->setText(inputData);
}

QString Widget::getCartesianEditData()
{
    // 获取 QTextBrowser 的文本
    QString plainText = ui->cartesian_textEdit->toPlainText();
    qDebug() << plainText;
    return plainText;
}

void Widget::setCartesianEditData(QString inputData)
{
    ui->cartesian_textEdit->setText(inputData);
}

void Widget::addText2textBrowser(QString addText)
{
    // 获取当前的文本数据
    QString currentText = ui->textBrowser->toPlainText(); // 获取纯文本
    // 输出到调试窗口
    qDebug() << "Current text in QTextBrowser:" << currentText;
    currentText += addText + "\n";
    ui->textBrowser->setText(currentText);
}

void Widget::on_stackForwordPage_clicked()
{
    int curIndexNum = ui->stackedWidget->currentIndex();
    ui->stackedWidget->setCurrentIndex(curIndexNum - 1);
}

void Widget::on_stackNextPage_clicked()
{
    int curIndexNum = ui->stackedWidget->currentIndex();
    ui->stackedWidget->setCurrentIndex(curIndexNum + 1);
}

void Widget::on_weaveBtn_clicked()
{
    qDebug() << "start weave";

    robotCon->example_startWeave();
}

void Widget::on_rtdeCheckBox_stateChanged(int arg1)
{
    qDebug() << "current rtdeState: " << arg1;
    if (arg1 == 0) {
        qDebug() << "rtde stop";
    } else {
        qDebug() << "rtde start";
        //        robotCon-
    }
}

void Widget::on_settingPage_Btn_clicked()
{
    if (robotCon == nullptr) {
        qDebug() << "robotCon is not initialized!";
        return;
    }
    Setting *popup = new Setting(robotCon);    // 将 robotCon 传递给弹窗
    popup->setAttribute(Qt::WA_DeleteOnClose); // 确保窗口关闭后释放内存
    popup->show();
}

void Widget::on_deg2Rad_Btn_clicked()
{
    QString inputData = getDegEditData();
    // 示例输入角度向量
    std::vector<double> robotDeg = tool->convertQStringToDoubleArray(inputData);

    // 调用 deg2Rad 函数
    std::vector<double> robotRad = tool->deg2Rad(robotDeg);

    // 输出结果（你可以根据需要处理输出）
    for (double rad : robotRad) {
        std::cout << rad << std::endl; // 使用 cout 输出结果
    }

    setRadEditData(tool->vectorToQString(robotRad));
}

void Widget::on_rad2Deg_Btn_clicked()
{
    QString inputData = getRadEditData();
    // 示例输入角度向量
    std::vector<double> robotRad = tool->convertQStringToDoubleArray(inputData);

    // 调用 deg2Rad 函数
    std::vector<double> robotDeg = tool->rad2Deg(robotRad);

    // 输出结果（你可以根据需要处理输出）
    for (double deg : robotDeg) {
        std::cout << deg << std::endl; // 使用 cout 输出结果
    }

    setDegEditData(tool->vectorToQString(robotDeg));
}

void Widget::on_startMove_Btn_clicked()
{
}

void Widget::on_jointMove_Btn_clicked()
{
    // 获取当前的关节弧度数据
    QString inputData = getRadEditData();
    // QString转double数组
    std::vector<double> robotRad = tool->convertQStringToDoubleArray(inputData);
    //调用关节运动指令
    robotCon->con_jointMove(robotRad);
}

void Widget::on_speed_Slider_sliderReleased()
{
    robotCon->con_setSpeedFraction((ui->speed_Slider->sliderPosition() + 1) /
                                   100.0);
}

void Widget::on_inverse_Btn_clicked()
{
    // 获取当前的关节弧度数据
    QString inputData = getCartesianEditData();
    // QString转double数组
    std::vector<double> robotRad = tool->convertQStringToDoubleArray(inputData);

    setRadEditData(
        tool->vectorToQString(robotCon->con_inverseKinematics(robotRad)));
}

void Widget::on_getTcpPose_Btn_clicked()
{
    setCartesianEditData(tool->vectorToQString(robotCon->con_getTcpPose()));
}

void Widget::on_forward_Btn_clicked()
{
    // 获取当前的关节弧度数据
    QString inputData = getRadEditData();
    // QString转double数组
    std::vector<double> robotRad = tool->convertQStringToDoubleArray(inputData);

    setCartesianEditData(
        tool->vectorToQString(robotCon->con_forwardKinematics(robotRad)));
}

void Widget::on_lineMove_Btn_clicked()
{
    // 获取当前输入框数据
    QString inputData = getCartesianEditData();
    // QString转double数组
    std::vector<double> robotPose =
        tool->convertQStringToDoubleArray(inputData);
    //调用关节运动指令
    robotCon->con_lineMove(robotPose);
}

void Widget::on_zeroJoint_Btn_clicked()
{
    // QString转double数组
    std::vector<double> robotRad = { 0, 0, 0, 0, 0, 0 };
    //调用关节运动指令
    robotCon->con_jointMove(robotRad);
}

void Widget::on_oriJoint_Btn_clicked()
{
    // QString转double数组
    std::vector<double> robotRad = {
        0, -0.261799, 1.74533, 0.436332, 1.5708, 0
    };
    //调用关节运动指令
    robotCon->con_jointMove(robotRad);
}

void Widget::on_stop_Btn_clicked()
{
    robotCon->con_robotStop();
}

void Widget::on_createOfft_Btn_clicked()
{
    robotCon->con_createOfft(10);
}
