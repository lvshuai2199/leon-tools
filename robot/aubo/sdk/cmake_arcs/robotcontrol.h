#ifndef ROBOTCONTROL_H
#define ROBOTCONTROL_H

#include <QThread>

#include "aubo_sdk/rpc.h"

using namespace arcs::common_interface;
using namespace arcs::aubo_sdk;

class robotControl
{
public:
    robotControl();

public:
    int con_jointMove(std::vector<double> jointRad);
    int con_lineMove(std::vector<double> jointPose);

    int con_setSpeedFraction(double robSpeed);

    void con_robotStop();

    void con_init();

    void con_powerOn();

    void con_powerOff();

    std::vector<double> con_forwardKinematics(std::vector<double> jointRad);
    std::vector<double> con_inverseKinematics(std::vector<double> jointPose);

    std::vector<double> con_getTcpPose();

    void createOfft(int offtTime);

    void con_createOfft(int offtTime);

public:
    void example_powerOnAndOff();
    void example_moveCir();

    void example_startWeave();

private:
    int robot_init();

    // 机械臂上电
    int robot_powerOn(RpcClientPtr cli);

    // 机械臂下电
    int robot_powerOff(RpcClientPtr cli);

    std::vector<double> robot_getTcpPose(RpcClientPtr cli);

    int robot_setSpeedFraction(RpcClientPtr cli);

    // 关节运动
    //        void exampleJointMove(RpcClientPtr cli);
    void robot_jointMove(RpcClientPtr cli);
    void robot_lineMove(RpcClientPtr cli);

    // 直线运动
    //    void exampleLineMove(RpcClientPtr cli);

    // 逆解
    std::vector<double> robot_inverseKinematics(RpcClientPtr cli);

    // 正解
    std::vector<double> robot_forwardKinematics(RpcClientPtr cli);

    // IO控制和状态读取

    // 获取机械臂当前运动状态

    // 圆弧运动
    void exampleMoveCir(RpcClientPtr cli);

    void exampleStartWeave(RpcClientPtr cli);

    void robot_stop(RpcClientPtr cli);

private:
    // 阻塞，等到机械臂运动完成
    int waitArrival(RobotInterfacePtr impl);

    // 等待机械臂进入目标模式
    void waitForRobotMode(RobotInterfacePtr robot_interface,
                          RobotModeType target_mode);

    std::shared_ptr<RpcClient> rpc_cli; // 成员变量

    int exec_id;

    // 机械臂运行状态
    int robotState;

    //机械全局速度
    double globalSpeed;

    std::vector<std::thread> actionThreads; // 存储线程

    std::vector<double> targetJointRad;
    std::vector<double> targetPose;
};

#endif // ROBOTCONTROL_H
