#ifndef ROBOTCONTROL_H
#define ROBOTCONTROL_H


#include "rsdef.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif


#define ROBOT_ADDR "192.168.216.128"
#define ROBOT_PORT 8899

#define MAX_LINE 4200


#include <QThread>


class robotControl
{
public:
    robotControl();

    bool initRobot();
public:
    bool example_login(RSHD &rshd, const char * addr, int port);

    void moveToTarget(RSHD &rshd);

    void printRoadPoint(aubo_robot_namespace::wayPoint_S *wayPoint);


    //机械臂控制上下文句柄
    RSHD g_rshd = -1;


    void getPoseAndPrint();
    bool testInverseKinematics(const aubo_robot_namespace::Pos &targetPos);

    void con_robotMove();
    void robotMove();
    void con_robotStop();
    void robotStop();
    void con_robotFastStop();
    void robotFastStop();

    void robotMoveJoint();
};

#endif // ROBOTCONTROL_H
