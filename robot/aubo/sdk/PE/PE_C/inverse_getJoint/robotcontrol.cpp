#include "robotcontrol.h"

robotControl::robotControl() {}

bool robotControl::initRobot()
{
    // this->example_login()

    // robotControl *robot = new robotControl();

    this->example_login(g_rshd, ROBOT_ADDR, ROBOT_PORT);

    return true;
}


void robotControl::getPoseAndPrint()
{
    aubo_robot_namespace::wayPoint_S curWayPoint;
    rs_get_current_waypoint(g_rshd,&curWayPoint);

    printRoadPoint(&curWayPoint);

}


/********************************************************************
    function:	example_login
    purpose :	登陆机械臂
    param   :	rshd 输出上下文句柄
                addr 机械臂服务器地址
                port 机械臂服务器端口
    return  :	true 成功 false 失败
*********************************************************************/
bool robotControl::example_login(RSHD &rshd, const char * addr, int port)
{
    bool result = false;

    rshd = RS_FAILED;

    //初始化接口库
    if (rs_initialize() == RS_SUCC)
    {
        //创建上下文
        if (rs_create_context(&rshd)  == RS_SUCC )
        {
            //登陆机械臂服务器
            if (rs_login(rshd, addr, port) == RS_SUCC)
            {
                result = true;
                //登陆成功
                std::cout<<"login success"<<std::endl;
            }
            else
            {
                //登陆失败
                std::cerr<<"login failed"<<std::endl;
            }
        }
        else
        {
            //创建上下文失败
            std::cerr<<"rs_create_context error"<<std::endl;
        }
    }
    else
    {
        //初始化接口库失败
        std::cerr<<"rs_initialize error"<<std::endl;
    }

    return result;
}

void robotControl::moveToTarget(RSHD &rshd)
{
    aubo_robot_namespace::wayPoint_S wayPoint;

    ik_solutions solutions;

    double jointAngle[aubo_robot_namespace::ARM_DOF] = {-0.000003, -0.127267, -1.321122, 0.376934, -1.570796, -0.000008};

    //正解
    if (RS_SUCC == rs_forward_kin(rshd, jointAngle, &wayPoint))
    {
        std::cout<<"fk succ"<<std::endl;

        printRoadPoint(&wayPoint);
    }

    //逆解
    double startPointJointAngle[aubo_robot_namespace::ARM_DOF] = {0.0/180.0*M_PI,  0.0/180.0*M_PI,  0.0/180.0*M_PI, 0.0/180.0*M_PI, 0.0/180.0*M_PI,0.0/180.0*M_PI};

    aubo_robot_namespace::Pos targetPosition;
    //targetPosition.x =-0.400;
    //targetPosition.y =-0.1215;
    //targetPosition.z = 0.5476;

    targetPosition.x = -0.790764;
    targetPosition.y = -0.293991;
    targetPosition.z = 0.60698;

    aubo_robot_namespace::Rpy rpy;
    aubo_robot_namespace::Ori targetOri;

    rpy.rx = 180.0/180.0*M_PI;
    rpy.ry = 0.0/180.0*M_PI;
    rpy.rz = -90.0/180.0*M_PI;

    rs_rpy_to_quaternion(rshd, &rpy, &targetOri);

    if (RS_SUCC == rs_inverse_kin(rshd, startPointJointAngle, &targetPosition, &targetOri, &wayPoint))
    {
        std::cout<<"ik succ"<<std::endl;
        printRoadPoint(&wayPoint);
    }
    else
    {
        std::cerr<<"ik failed"<<std::endl;
    }

    if (RS_SUCC == rs_inverse_kin_closed_form(rshd, &targetPosition, &targetOri, &solutions))
    {
        std::cout<<"ik succ"<<std::endl;
        for (int i=0;i<solutions.solution_count;i++)
        {
            printRoadPoint(&solutions.waypoint[i]);
        }
    }
    else
    {
        std::cerr<<"ik failed"<<std::endl;
    }

}

void robotControl::printRoadPoint(wayPoint_S *wayPoint)
{
    std::cout << "***************************************************" << std::endl;
    std::cout << "坐标 pos" << std::endl;
    std::cout << wayPoint->cartPos.position.x << std::endl;
    std::cout << wayPoint->cartPos.position.y << std::endl;
    std::cout << wayPoint->cartPos.position.z << std::endl;

    std::cout << "姿态（四元数）ori" << std::endl;
    std::cout << wayPoint->orientation.w << std::endl;
    std::cout << wayPoint->orientation.x << std::endl;
    std::cout << wayPoint->orientation.y << std::endl;
    std::cout << wayPoint->orientation.z << std::endl;

    Rpy oriParam;
    rs_quaternion_to_rpy(g_rshd, &wayPoint->orientation, &oriParam);

    std::cout << "姿态（欧拉角）rpy" << std::endl;
    std::cout << oriParam.rx  * 180 /M_PI<< std::endl;
    std::cout << oriParam.ry  * 180 /M_PI<< std::endl;
    std::cout << oriParam.rz  * 180 /M_PI<< std::endl;

    std::cout << "关节角 joint" << std::endl;
    for(int i = 0 ; i< ARM_DOF; i++){
         std::cout << wayPoint->jointpos[i] * 180 /M_PI<< std::endl;
    }
}


bool robotControl::testInverseKinematics(const aubo_robot_namespace::Pos &targetPos)
{


    // /// ---------- SDK 初始化 ----------
    // if (rs_initialize() != RS_SUCC) {
    //     std::cerr << "[IK Test] Failed to initialize robot SDK" << std::endl;
    //     return false;
    // }

    // /// ---------- 创建上下文 ----------
    // if (rs_create_context(&g_rshd) != RS_SUCC) {
    //     std::cerr << "[IK Test] Failed to create robot context" << std::endl;
    //     rs_uninitialize(); // 清理 SDK
    //     return false;
    // }

    // /// ---------- 登录机器人 ----------
    // // const char* addr = "192.168.41.129"; // 你可以根据需要修改为实际 IP
    // const char* addr = "192.168.1.55"; // 你可以根据需要修改为实际 IP
    // int port = 8899;
    // if (rs_login(g_rshd, addr, port) != RS_SUCC) {
    //     std::cerr << "[IK Test] Failed to connect to robot server: " << addr << ":" << port << std::endl;
    //     rs_destory_context(g_rshd);
    //     rs_uninitialize();
    //     return false;
    // }
    std::cout << "[IK Test] Robot initialized and connected successfully!" << std::endl;

    /// ---------- 启动机器人 ----------
    ToolDynamicsParam tool_dynamics = {0};
    uint8 colli_class = 6;
    bool read_pos = true;
    bool static_colli_detect = true;
    int board_maxacc = 30000;
    ROBOT_SERVICE_STATE state = ROBOT_SERVICE_READY;

    std::cout << "[IK Test] Attempting to start robot..." << std::endl;
    if (rs_robot_startup(g_rshd, &tool_dynamics, colli_class, read_pos, static_colli_detect, board_maxacc, &state) != RS_SUCC) {
        std::cerr << "[IK Test] Robot startup failed." << std::endl;
        return false;
    }

    if (rs_project_startup(g_rshd) != RS_SUCC) {
        std::cerr << "[IK Test] Project startup failed. Safety IO may not be active." << std::endl;
        return false;
    }

    std::cout << "[IK Test] Robot system started successfully. Safety IO is active." << std::endl;

    /// ---------- 获取当前路点 ----------
    aubo_robot_namespace::wayPoint_S currentJoints;
    if (rs_get_current_waypoint(g_rshd, &currentJoints) != RS_SUCC) {
        std::cerr << "[IK Test] Failed to get current waypoint!" << std::endl;
        return false;
    }
    printRoadPoint(&currentJoints);

    /// ---------- 准备逆解 ----------
    double referenceJoints[aubo_robot_namespace::ARM_DOF] = {0};
    for (int i = 0; i < aubo_robot_namespace::ARM_DOF; ++i) {
        referenceJoints[i] = currentJoints.jointpos[i];
    }

    aubo_robot_namespace::wayPoint_S solvedWaypoint = {};
    for (int i = 0; i < ARM_DOF; ++i) {
        solvedWaypoint.jointpos[i] = -999.0;
    }

    /// ---------- 调用逆解 ----------
    int ret = rs_inverse_kin(g_rshd, referenceJoints, &targetPos, &currentJoints.orientation, &solvedWaypoint);
    if (ret != RS_SUCC) {
        std::cerr << "[IK Test] Inverse Kinematics failed!" << std::endl;
        return false;
    }

    /// ---------- 打印逆解结果 ----------
    std::cout << "[IK Test] Inverse Kinematics success!" << std::endl;
    std::cout << "  Solved Position (x, y, z): ("
              << solvedWaypoint.cartPos.position.x << ", "
              << solvedWaypoint.cartPos.position.y << ", "
              << solvedWaypoint.cartPos.position.z << ")" << std::endl;

    aubo_robot_namespace::Ori solvedOri = solvedWaypoint.orientation;
    if (solvedOri.w < 0) {
        solvedOri.w *= -1;
        solvedOri.x *= -1;
        solvedOri.y *= -1;
        solvedOri.z *= -1;
    }

    std::cout << "  Solved Orientation (w, x, y, z): ("
              << solvedOri.w << ", " << solvedOri.x << ", "
              << solvedOri.y << ", " << solvedOri.z << ")" << std::endl;

    std::cout << "  Joint Angles (degrees):" << std::endl;
    for (int i = 0; i < ARM_DOF; ++i) {
        std::cout << "  Joint_" << (i + 1) << ": " << solvedWaypoint.jointpos[i] * 180 / M_PI << std::endl;
    }

    return true;
}

void robotControl::con_robotMove()
{
    std::thread weaveThread(&robotControl::robotMove, this);
    weaveThread.detach();

}

void robotControl::robotMove()
{
    RobotRecongnitionParam param;
    rs_get_robot_recognition_param(g_rshd, 1, &param);

    //该位置为机械臂的初始位置（提供6个关节角的关节信息（单位：弧度））
    double initPos[6]={
                         -0.000172/180*M_PI,
                         -7.291862/180*M_PI,
                         -75.694718/180*M_PI,
                         21.596727/180*M_PI,
                         -89.999982/180*M_PI,
                         -0.00458/180*M_PI};

    //首先运动到初始位置
    if (rs_move_joint(g_rshd, initPos) == RS_SUCC)
    {
        std::cout<<"movej succ"<<std::endl;
    }
    else
    {
        std::cerr<<"movej failed!"<<std::endl;
    }

    double secondPose[6]={
                         -0.000172/180*M_PI,
                         -5.291862/180*M_PI,
                         -60.694718/180*M_PI,
                         10.596727/180*M_PI,
                         -30.999982/180*M_PI,
                         -0.00458/180*M_PI};

    while(true){
        //首先运动到初始位置
        if (rs_move_joint(g_rshd, initPos) == RS_SUCC)
        {
            std::cout<<"movej succ"<<std::endl;
        }
        else
        {
            std::cerr<<"movej failed!"<<std::endl;
            return;
        }
        //首先运动到初始位置
        if (rs_move_joint(g_rshd, secondPose) == RS_SUCC)
        {
            std::cout<<"movej2 succ"<<std::endl;
        }
        else
        {
            std::cerr<<"movej2 failed!"<<std::endl;
            return;
        }

    }

}

void robotControl::con_robotStop()
{
    int res =  rs_move_stop(g_rshd);

    std::cerr<<"con_robotStop res!"<< res << std::endl;
}

void robotControl::robotStop()
{
    rs_move_stop(g_rshd);

}

void robotControl::con_robotFastStop()
{
    int res = rs_move_fast_stop(g_rshd);

    std::cerr<<"con_robotFastStop res!"<< res << std::endl;

    std::cerr<<"con_robotFastStop res!"<< res << std::endl;
}

void robotControl::robotMoveJoint()
{
    RobotRecongnitionParam param;
    rs_get_robot_recognition_param(g_rshd, 1, &param);

    //该位置为机械臂的初始位置（提供6个关节角的关节信息（单位：弧度））
    double initPos[6]={
                         -0.000172/180*M_PI,
                         -7.291862/180*M_PI,
                         -75.694718/180*M_PI,
                         21.596727/180*M_PI,
                         -89.999982/180*M_PI,
                         -0.00458/180*M_PI};

    //首先运动到初始位置
    if (rs_move_joint(g_rshd, initPos) == RS_SUCC)
    {
        std::cout<<"movej succ"<<std::endl;
    }
    else
    {
        std::cerr<<"movej failed!"<<std::endl;
    }

}
