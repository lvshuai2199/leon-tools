#ifndef WIDGET_H
#define WIDGET_H

#include <QWidget>
#include <QDebug>

#include "aubo_sdk/rpc.h"

using namespace arcs::common_interface;
using namespace arcs::aubo_sdk;

QT_BEGIN_NAMESPACE

#include "../robotcontrol.h"
#include "../robottool.h"

#include "setting.h"

namespace Ui {
class Widget;
}
QT_END_NAMESPACE

class Widget : public QWidget
{
    Q_OBJECT

public:
    Widget(QWidget *parent = nullptr);
    ~Widget();

    // 提供一个方法获取 robotCon
    robotControl *getRobotCon();
    QString getRadEditData();
    void setRadEditData(QString inputData);
    QString getDegEditData();
    void setDegEditData(QString inputData);
    QString getCartesianEditData();
    void setCartesianEditData(QString inputData);
private slots:

    void on_stackForwordPage_clicked();

    void on_stackNextPage_clicked();

    void on_weaveBtn_clicked();

    void on_rtdeCheckBox_stateChanged(int arg1);

    void on_settingPage_Btn_clicked();

    void on_deg2Rad_Btn_clicked();

    void on_rad2Deg_Btn_clicked();

    void on_startMove_Btn_clicked();

    void on_jointMove_Btn_clicked();

    void on_speed_Slider_sliderReleased();

    void updateSpeedLabel(int value);

    void on_inverse_Btn_clicked();

    void on_getTcpPose_Btn_clicked();

    void on_forward_Btn_clicked();

    void on_lineMove_Btn_clicked();

    void on_zeroJoint_Btn_clicked();

    void on_oriJoint_Btn_clicked();

    void on_stop_Btn_clicked();

    void on_createOfft_Btn_clicked();

private:
    void addText2textBrowser(QString addText);

    void pageInit();

private:
    Ui::Widget *ui;

    robotControl *robotCon;

    robotTool *tool;
};
#endif // WIDGET_H
