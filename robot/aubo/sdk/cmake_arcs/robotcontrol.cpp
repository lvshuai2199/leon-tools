#include "robotcontrol.h"
#include "robotcontrol.h"

#define LOCAL_IP "127.0.0.1"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// offt生成使用
#include <fstream>

robotControl::robotControl()
{
    robot_init();
}

int robotControl::con_jointMove(std::vector<double> jointRad)
{
    targetJointRad = jointRad;
    std::thread weaveThread(&robotControl::robot_jointMove, this, rpc_cli);
    weaveThread.detach();
    return 0;
}

int robotControl::con_lineMove(std::vector<double> jointPose)
{
    targetPose = jointPose;
    std::thread weaveThread(&robotControl::robot_lineMove, this, rpc_cli);
    weaveThread.detach();
    return 0;
}

int robotControl::con_setSpeedFraction(double robSpeed)
{
    globalSpeed = robSpeed;
    this->robot_setSpeedFraction(rpc_cli);
}

int robotControl::robot_init()
{
    std::cout << "Initializing robot..." << std::endl;

    try {
        rpc_cli = std::make_shared<RpcClient>();

        // 设置 RPC 超时
        rpc_cli->setRequestTimeout(1000);
        std::cout << "RPC timeout set." << std::endl;

        // 连接到 RPC 服务
        rpc_cli->connect(LOCAL_IP, 30004);
        std::cout << "Connected to RPC service." << std::endl;

        // 登录
        rpc_cli->login("aubo", "123456");
        std::cout << "Robot login successful." << std::endl;

        std::cout << "Robot initialized successfully." << std::endl;
        return 0;                      // 成功
    } catch (const ExceptionCode &e) { // 捕获具体异常
        std::cerr << "robot_init error: " << e << std::endl; // 输出异常信息
        return -1;                                           // 失败
    } catch (const std::exception &e) {
        std::cerr << "Unexpected error: " << e.what()
                  << std::endl; // 捕获其他异常
        return -1;              // 失败
    }
}

int robotControl::robot_powerOn(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);

    // 接口调用: 设置负载
    double mass = 0.0;
    std::vector<double> cog(3, 0.0);
    std::vector<double> aom(3, 0.0);
    std::vector<double> inertia(6, 0.0);
    robot_interface->getRobotConfig()->setPayload(mass, cog, aom, inertia);

    // 接口调用: 获取机械臂当前模式
    auto robot_mode = robot_interface->getRobotState()->getRobotModeType();

    if (robot_mode == RobotModeType::Running) {
        std::cout << "机械臂已松刹车，处于运行模式" << std::endl;

    } else {
        // 接口调用: 机械臂发起上电请求
        robot_interface->getRobotManage()->poweron();

        // 等待机械臂进入空闲模式
        waitForRobotMode(robot_interface, RobotModeType::Idle);

        std::cout << "机械臂上电成功，当前模式:"
                  << robot_interface->getRobotState()->getRobotModeType()
                  << std::endl;

        // 接口调用: 机械臂发起松刹车请求
        cli->getRobotInterface(robot_name)->getRobotManage()->startup();

        // 等待机械臂进入运行模式
        waitForRobotMode(robot_interface, RobotModeType::Running);

        std::cout << "机械臂松刹车成功，当前模式:"
                  << robot_interface->getRobotState()->getRobotModeType()
                  << std::endl;
    }
    return 1;
}

int robotControl::robot_powerOff(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);

    // 接口调用: 机械臂断电
    int robotPowerOff = robot_interface->getRobotManage()->poweroff();

    std::cout << "robotPowerOff" << robotPowerOff << std::endl;
    while (robotPowerOff != 0) {
        robotPowerOff = robot_interface->getRobotManage()->poweroff();
        std::cout << "robotPowerOff" << robotPowerOff << std::endl;
    }

    // 等待机械臂进入断电模式
    waitForRobotMode(robot_interface, RobotModeType::PowerOff);

    std::cout << "机械臂断电成功，当前模式:"
              << robot_interface->getRobotState()->getRobotModeType()
              << std::endl;
    return 1;
}

void robotControl::example_powerOnAndOff()
{
    auto rpc_cli = std::make_shared<RpcClient>();
    // 接口调用: 设置 RPC 超时
    rpc_cli->setRequestTimeout(1000);
    // 接口调用: 连接到 RPC 服务
    rpc_cli->connect(LOCAL_IP, 30004);
    // 接口调用: 登录
    rpc_cli->login("aubo", "123456");

    // 机械臂上电
    robot_powerOn(rpc_cli);

    // 机械臂关节运动
    //    exampleJointMove(rpc_cli);

    //    exampleLineMove(rpc_cli);

    //    exampleInverseKinematics(rpc_cli);

    exampleMoveCir(rpc_cli);

    // 机械臂断电
    //    examplePoweroff(rpc_cli);

    // 接口调用: 退出登录
    rpc_cli->logout();
    // 接口调用: 断开连接
    rpc_cli->disconnect();
}

void robotControl::example_moveCir()
{
    //    std::thread weaveThread(&robotControl::exampleMoveCir, this, rpc_cli);

    //    weaveThread.detach();

    actionThreads.emplace_back(&robotControl::exampleMoveCir, this, rpc_cli);
}

void robotControl::example_startWeave()
{
    std::thread weaveThread(&robotControl::exampleStartWeave, this, rpc_cli);
    weaveThread.detach();
}

void robotControl::con_robotStop()
{
    std::thread stopThread(&robotControl::robot_stop, this, rpc_cli);
    stopThread.detach();
}

void robotControl::con_init()
{
    this->robot_init();
}

void robotControl::con_powerOn()
{
    this->robot_powerOn(rpc_cli);
}

void robotControl::con_powerOff()
{
    this->robot_powerOff(rpc_cli);
}

std::vector<double> robotControl::con_forwardKinematics(
    std::vector<double> jointRad)
{
    this->targetJointRad = jointRad;
    this->targetPose = this->robot_forwardKinematics(rpc_cli);
    return this->targetPose;
}

std::vector<double> robotControl::con_inverseKinematics(
    std::vector<double> jointPose)
{
    this->targetPose = jointPose;
    this->targetJointRad = this->robot_inverseKinematics(rpc_cli);
    return this->targetJointRad;
}

std::vector<double> robotControl::con_getTcpPose()
{
    return this->robot_getTcpPose(rpc_cli);
}

void robotControl::createOfft(int offtTime)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = rpc_cli->getRobotNames().front();

    auto robot_interface = rpc_cli->getRobotInterface(robot_name);

    std::string filename = "/root/Documents/offtDemo.offt";
    std::ofstream ofs(filename);

    // 检查文件是否成功打开
    if (!ofs.is_open()) {
        std::cerr << "无法打开文件: " << filename << std::endl;
        return;
    }

    // 设置采样频率（例如每 100 毫秒获取一次数据）
    int sample_interval_ms = 10;                             // 100 毫秒
    int iterations = (offtTime * 1000) / sample_interval_ms; // 计算总的迭代次数

    for (int i = 0; i < iterations; ++i) {
        // 获取关节位置
        auto joint_positions =
            robot_interface->getRobotState()->getJointPositions();

        // 存储关节位置到文件
        //        for (const auto &pos : joint_positions) {
        //            ofs << pos << ","; // 假设关节位置是 double 类型
        //        }
        // 存储关节位置到文件
        for (size_t j = 0; j < joint_positions.size(); ++j) {
            ofs << joint_positions[j]; // 写入关节位置
            if (j < joint_positions.size() - 1) {
                ofs << ","; // 在后续位置之间添加逗号
            }
        }
        ofs << std::endl; // 每次写入后换行

        // 等待指定的延迟
        std::this_thread::sleep_for(
            std::chrono::milliseconds(sample_interval_ms));
    }

    ofs.close(); // 关闭文件
    std::cout << "关节位置已成功存储到 " << filename << std::endl;
}

void robotControl::con_createOfft(int offtTime)
{
    std::thread weaveThread(&robotControl::createOfft, this, offtTime);
    weaveThread.detach();
}

std::vector<double> robotControl::robot_getTcpPose(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);

    return robot_interface->getRobotState()->getTcpPose();
}

int robotControl::robot_setSpeedFraction(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);

    // 接口调用: 设置机械臂的速度比率
    return robot_interface->getMotionControl()->setSpeedFraction(globalSpeed);
}

// 等待机械臂进入目标模式
void robotControl::waitForRobotMode(RobotInterfacePtr robot_interface,
                                    RobotModeType target_mode)
{
    // 接口调用: 获取当前机械臂的模式
    auto current_mode = robot_interface->getRobotState()->getRobotModeType();

    while (current_mode != target_mode) {
        std::cout << "机械臂当前模式:" << current_mode << std::endl;
        std::this_thread::sleep_for(std::chrono::seconds(1));
        current_mode = robot_interface->getRobotState()->getRobotModeType();
    }
}

std::vector<double> robotControl::robot_inverseKinematics(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);

    //    // 接口调用: 设置工具中心点（TCP相对于法兰盘中心的偏移）
    //    std::vector<double> tcp_offset(6, 0.0);

    //    tcp_offset[0] = 0;
    //    tcp_offset[1] = 0.05;
    //    tcp_offset[2] = 0.05;
    //    tcp_offset[3] = 1;
    //    tcp_offset[4] = 0;
    //    tcp_offset[5] = 0;

    //    robot_interface->getRobotConfig()->setTcpOffset(tcp_offset);

    // 获取当前关节位置（单位：弧度）
    std::vector<double> curjoint =
        robot_interface->getRobotState()->getJointPositions();

    // 输出第一个关节角（单位：度）
    if (!curjoint.empty()) {
        std::cout << curjoint[0] * 180 / M_PI << std::endl;
    }

    // 调用逆解算法
    ResultWithErrno targetPoseJoint =
        robot_interface->getRobotAlgorithm()->inverseKinematics(curjoint,
                                                                targetPose);

    // 检查逆解结果
    if (std::get<1>(targetPoseJoint) == 0) {
        std::cout << "逆解完成 " << std::endl;
        return std::get<0>(targetPoseJoint);

    } else {
        std::cout << "逆解失败，错误码: " << std::get<1>(targetPoseJoint)
                  << std::endl;
    }
    return {};
}

std::vector<double> robotControl::robot_forwardKinematics(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);
    // 调用正解算法
    ResultWithErrno targetPoseJoint =
        robot_interface->getRobotAlgorithm()->forwardKinematics(targetJointRad);

    // 检查正解结果
    if (std::get<1>(targetPoseJoint) == 0) {
        std::cout << "正解完成 " << std::endl;

        return std::get<0>(targetPoseJoint);

    } else {
        std::cout << "正解失败，错误码: " << std::get<1>(targetPoseJoint)
                  << std::endl;
    }
    return {};
}

// 实现阻塞功能: 当机械臂运动到目标路点时，程序再往下执行
int robotControl::waitArrival(RobotInterfacePtr impl)
{
    int curWaitType = 1;
    if (curWaitType == 1) {
        //最大轨迹点检测次数
        const int max_retry_count = 5;
        int cnt = 0;
        //机械臂当前状态
        bool robotSteady = true;
        exec_id = -1;

        std::cout << "wait for robot start" << std::endl;
        // 等待机械臂开始运动
        while (exec_id == -1 or robotSteady == true) {
            if (cnt++ > max_retry_count) {
                return 0;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            robotSteady = impl->getRobotState()->isSteady();
            exec_id = impl->getMotionControl()->getExecId();

            //            qDebug() << "robotSteady:" << robotSteady;
            //            qDebug() << "exec_id:" << exec_id;
        }

        //        robotSteady = false;
        std::cout << "wait for robot arrival" << std::endl;
        while (robotSteady == false or exec_id != -1) {
            robotSteady = impl->getRobotState()->isSteady();
            exec_id = impl->getMotionControl()->getExecId();

            std::cout << "robotSteady:" << robotSteady << std::endl;
            std::cout << "exec_id:" << exec_id << std::endl;
            // 可选择的延时，以确保系统有足够时间处理命令
            std::this_thread::sleep_for(
                std::chrono::milliseconds(100)); // 例如，延时100毫秒
        }
        std::cout << "robot arrival at target pose" << std::endl;
    } else {
        const int max_retry_count = 5;
        int cnt = 0;

        // 接口调用: 获取当前的运动指令 ID
        int exec_id = impl->getMotionControl()->getExecId();

        // 等待机械臂开始运动
        while (exec_id == -1) {
            if (cnt++ > max_retry_count) {
                return -1;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            exec_id = impl->getMotionControl()->getExecId();
        }

        // 等待机械臂动作完成
        while (impl->getMotionControl()->getExecId() != -1) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
    }

    return 0;
}

void robotControl::robot_jointMove(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);

    // 接口调用: 关节运动
    int moveJRes = robot_interface->getMotionControl()->moveJoint(
        targetJointRad, 80 * (M_PI / 180), 60 * (M_PI / 180), 0, 0);
    // 阻塞
    std::cout << "moveJRes = " << moveJRes << std::endl;
    //    int ret = waitArrival(robot_interface);
    //    if (ret == 0) {
    //        std::cout << "关节运动到路点1成功" << std::endl;
    //    } else {
    //        std::cout << "关节运动到路点1失败" << std::endl;
    //    }
}

void robotControl::robot_lineMove(RpcClientPtr cli)
{
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);

    // 接口调用: 关节运动
    int moveLRes = robot_interface->getMotionControl()->moveLine(
        targetPose, 80 * (M_PI / 180), 60 * (M_PI / 180), 0, 0);
    // 阻塞
    std::cout << "moveJRes = " << moveLRes << std::endl;
    //    int ret = waitArrival(robot_interface);
    //    if (ret == 0) {
    //        std::cout << "关节运动到路点1成功" << std::endl;
    //    } else {
    //        std::cout << "关节运动到路点1失败" << std::endl;
    //    }
}

void robotControl::exampleMoveCir(RpcClientPtr cli)
{
    //    // 接口调用: 获取机器人的名字
    //    auto robot_name = cli->getRobotNames().front();

    //    auto robot_interface = cli->getRobotInterface(robot_name);

    //    // 接口调用: 设置机械臂运动的速度比率
    //    robot_interface->getMotionControl()->setSpeedFraction(1);

    //    // 接口调用: 设置工具中心点
    //    std::vector<double> tcp_offset(6, 0.0);
    //    //    robot_interface->getRobotConfig()->setTcpOffset(tcp_offset);

    //    cli->getRuntimeMachine()->start();
    //    //    移动到圆弧的初始位置
    //    // 获取当前关节位置（单位：弧度）
    //    std::vector<double> curjoint =
    //        robot_interface->getRobotState()->getJointPositions();

    //    // 输出第一个关节角（单位：度）
    //    if (!curjoint.empty()) {
    //        std::cout << curjoint[0] * 180 / M_PI << std::endl;
    //    }

    //    // 定义目标点
    //    std::vector<double> targetPoint = cirParam.ci rPose1;
    //    std::cout << "目标点更新为: " << targetPoint[2] << std::endl;
    //    // 调用逆解算法
    //    ResultWithErrno targetPoseJoint =
    //        robot_interface->getRobotAlgorithm()->inverseKinematics(curjoint,
    //                                                                targetPoint);

    //    // 检查逆解结果
    //    if (std::get<1>(targetPoseJoint) == 0) {
    //        std::cout << "逆解完成 " << std::endl;
    //        int moveRes = robot_interface->getMotionControl()->moveJoint(
    //            std::get<0>(targetPoseJoint), 1.39626, 1.0472, 0, 0);
    //        if (moveRes == 0) {
    //            //                waitArrival(robot_interface);
    //        } else {
    //            std::cout << "运动命令下发错误" << moveRes << std::endl;
    //        }

    //    } else {
    //        std::cout << "逆解失败，错误码: " << std::get<1>(targetPoseJoint)
    //                  << std::endl;
    //    }

    //    // 阻塞
    //    int ret = waitArrival(robot_interface);
    //    if (ret == 0) {
    //        std::cout << "关节运动到初始位置成功" << std::endl;
    //    } else {
    //        std::cout << "关节运动到初始位置失败" << std::endl;
    //    }

    //    // 位姿
    //    std::vector<double> pose1 = cirParam.cirPose1;

    //    std::vector<double> pose2 = cirParam.cirPose2;

    //    std::vector<double> pose3 = cirParam.cirPose3;

    //    double speed = cirParam.speed;
    //    double acc = cirParam.acc;

    //    // 接口调用: 直线运动到圆弧的起始点——pose1
    //    robot_interface->getMotionControl()->moveLine(pose1, 1.2, 0.25, 0.025,
    //    0);

    //    // 阻塞
    //    ret = waitArrival(robot_interface);
    //    if (ret == 0) {
    //        std::cout << "直线运动到圆弧的起始点成功" << std::endl;
    //    } else {
    //        std::cout << "直线运动到圆弧的起始点失败" << std::endl;
    //    }

    //    std::cout << "圆弧运动速度" << speed << std::endl;
    //    std::cout << "圆弧运动加速度" << acc << std::endl;
    //    // 接口调用: 圆弧运动
    //    robot_interface->getMotionControl()->moveCircle(pose2, pose3, acc,
    //    0.1, 0,
    //                                                    0);

    //    // 阻塞
    //    ret = waitArrival(robot_interface);
    //    if (ret == 0) {
    //        std::cout << "圆弧运动成功" << std::endl;
    //    } else {
    //        std::cout << "圆弧运动失败" << std::endl;
    //    }
    //    cli->getRuntimeMachine()->stop();
}

void robotControl::exampleStartWeave(RpcClientPtr cli)
{
    //    // 接口调用: 获取机器人的名字
    //    auto robot_name = cli->getRobotNames().front();

    //    auto robot_interface = cli->getRobotInterface(robot_name);

    //    // 接口调用: 设置机械臂运动的速度比率
    //    robot_interface->getMotionControl()->setSpeedFraction(1);

    //    // 接口调用: 设置工具中心点
    //    std::vector<double> tcp_offset(6, 0.0);
    //    //    robot_interface->getRobotConfig()->setTcpOffset(tcp_offset);

    //    //    移动到圆弧的初始位置
    //    // 获取当前关节位置（单位：弧度）
    //    std::vector<double> curjoint =
    //        robot_interface->getRobotState()->getJointPositions();

    //    // 输出第一个关节角（单位：度）
    //    if (!curjoint.empty()) {
    //        std::cout << curjoint[0] * 180 / M_PI << std::endl;
    //    }

    //    // 定义目标点
    //    std::vector<double> targetPoint = cirParam.cirPose1;
    //    std::cout << "目标点更新为: " << targetPoint[2] << std::endl;
    //    // 调用逆解算法
    //    ResultWithErrno targetPoseJoint =
    //        robot_interface->getRobotAlgorithm()->inverseKinematics(curjoint,
    //                                                                targetPoint);

    //    // 检查逆解结果
    //    if (std::get<1>(targetPoseJoint) == 0) {
    //        std::cout << "逆解完成 " << std::endl;
    //        int moveRes = robot_interface->getMotionControl()->moveJoint(
    //            std::get<0>(targetPoseJoint), 1.39626, 1.0472, 0, 0);
    //        if (moveRes == 0) {
    //            //                waitArrival(robot_interface);
    //        } else {
    //            std::cout << "运动命令下发错误" << moveRes << std::endl;
    //        }

    //    } else {
    //        std::cout << "逆解失败，错误码: " << std::get<1>(targetPoseJoint)
    //                  << std::endl;
    //    }

    //    // 阻塞
    //    int ret = waitArrival(robot_interface);
    //    if (ret == 0) {
    //        std::cout << "关节运动到初始位置成功" << std::endl;
    //    } else {
    //        std::cout << "关节运动到初始位置失败" << std::endl;
    //    }

    //    // 位姿
    //    std::vector<double> pose1 = cirParam.cirPose1;

    //    std::vector<double> pose2 = cirParam.cirPose2;

    //    std::vector<double> pose3 = cirParam.cirPose3;

    //    double speed = lineParam.speed;
    //    double acc = lineParam.acc;

    //    cli->getRuntimeMachine()->start();
    //    robot_interface->getMotionControl()->setSpeedFraction(0.1);
    //    //    int weaveRes = robot_interface->getMotionControl()->weaveStart(
    //    // "{\"type\":\"SINE\",\"step\":0.00833333,\"amplitude\":[0.01,0.01],"
    //    //
    //    "\"hold_distance\":[0,0],\"hold_time\":[0,0],\"angle\":0,\"direction\":"
    //    //        "0}");

    //    //    int weaveRes = robot_interface->getMotionControl()->weaveStart(
    //    //
    //    "{\"type\":\"TRIANGLE\",\"step\":0.003111,\"amplitude\":[0.001600,0."
    //    //        "001600],"
    //    //
    //    "\"hold_distance\":[0,0],\"hold_time\":[0.350000,0.350000],\"angle\":0,"
    //    //        "\"direction\":"
    //    //        "0}");

    //    int weaveRes = robot_interface->getMotionControl()->weaveStart(
    //        "{\"type\":\"SINE\",\"step\":0.003111,\"amplitude\":[0.001600,0."
    //        "001600],"
    //        "\"hold_distance\":[0,0],\"hold_time\":[0.2,0.2],\"angle\":0,"
    //        "\"direction\":"
    //        "0}");

    //    if (weaveRes == 0) {
    //        std::cout << "weave start success" << std::endl;
    //    } else {
    //        std::cout << "weave start failure" << std::endl;
    //    }
    //    //    // 接口调用: 直线运动到直线的起始点——pose1
    //    // robot_interface->getMotionControl()->moveLine(lineParam.linePose1,
    //    //    acc,
    //    //                                                  speed, 0, 0);
    //    // robot_interface->getMotionControl()->moveLine(lineParam.linePose2,
    //    //    acc,
    //    //                                                  speed, 0, 0);

    //    //    // 阻塞
    //    //    ret = waitArrival(robot_interface);
    //    //    if (ret == 0) {
    //    //        std::cout << "直线运动到圆弧的起始点成功" << std::endl;
    //    //    } else {
    //    //        std::cout << "直线运动到圆弧的起始点失败" << std::endl;
    //    //    }

    //    std::cout << "圆弧运动速度" << speed << std::endl;
    //    std::cout << "圆弧运动加速度" << acc << std::endl;
    //    // 接口调用: 圆弧运动
    //    robot_interface->getMotionControl()->moveCircle(pose2, pose3, acc,
    //    0.1, 0,
    //                                                    0);

    //    robot_interface->getMotionControl()->moveLine(pose1, acc, 0.1, 0, 0);

    //    robot_interface->getMotionControl()->weaveEnd();

    //    // 阻塞
    //    ret = waitArrival(robot_interface);
    //    if (ret == 0) {
    //        std::cout << "weaveEnd" << std::endl;
    //        std::cout << "摆动运动成功" << std::endl;
    //    } else {
    //        std::cout << "摆动运动失败" << std::endl;
    //    }
    //    cli->getRuntimeMachine()->stop();
}

void robotControl::robot_stop(RpcClientPtr cli)
{
    std::cout << "stop robot move" << std::endl;
    cli->getRuntimeMachine()->abort();
    // 接口调用: 停止关节运动
    // 接口调用: 获取机器人的名字
    auto robot_name = cli->getRobotNames().front();

    auto robot_interface = cli->getRobotInterface(robot_name);
    robot_interface->getMotionControl()->stopJoint(30);
}
