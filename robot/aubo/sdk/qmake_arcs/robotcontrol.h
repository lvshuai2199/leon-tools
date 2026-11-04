#ifndef ROBOTCONTROL_H
#define ROBOTCONTROL_H

#include "aubo_sdk/rpc.h"

using namespace arcs::common_interface;
using namespace arcs::aubo_sdk;

class robotControl
{
public:
    robotControl();

public:
    // 机器人上电
    void robotStartUp();

    // 机械臂上电
    void exampleStartup(RpcClientPtr cli);

    void waitForRobotMode(RobotInterfacePtr robot_interface,
                          RobotModeType target_mode);

    void examplePoweroff(RpcClientPtr cli);
};

#endif // ROBOTCONTROL_H
