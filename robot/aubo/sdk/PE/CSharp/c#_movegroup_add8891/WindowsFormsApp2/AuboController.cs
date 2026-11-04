using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace AuboCon
{
    internal class AuboController
    {
        //遨博SDK相关
        #region aubo_sdk
        public ROBOT_EVENT_CALLBACK m_RobotEventCallback;  //声明委托   2021.08.04

        public enum RobotEventType
        {
            RobotEvent_armCanbusError,           //机械臂CAN总线错误
            RobotEvent_remoteHalt,               //远程关机　　　　TODO
            RobotEvent_remoteEmergencyStop,      //机械臂远程急停
            RobotEvent_jointError,               //关节错误

            RobotEvent_forceControl,             //力控制
            RobotEvent_exitForceControl,         //退出力控制

            RobotEvent_softEmergency,            //软急停
            RobotEvent_exitSoftEmergency,        //退出软急停

            RobotEvent_collision,                //碰撞
            RobotEvent_collisionStatusChanged,   //碰撞状态改变
            RobotEvent_tcpParametersSucc,        //工具动力学参数设置成功
            RobotEvent_powerChanged,             //机械臂电源开关状态改变
            RobotEvent_ArmPowerOff,              //机械臂电源关闭
            RobotEvent_mountingPoseChanged,      //安装位置发生改变
            RobotEvent_encoderError,             //编码器错误

            RobotEvent_encoderLinesError,        //编码器线数不一致
            RobotEvent_singularityOverspeed,     //奇异点超速
            RobotEvent_currentAlarm,             //机械臂电流异常
            RobotEvent_toolioError,             //机械臂工具端错误
            RobotEvent_robotStartupPhase,       //机械臂启动阶段
            RobotEvent_robotStartupDoneResult,  //机械臂启动完成结果
            RobotEvent_robotShutdownDone,       //机械臂关机结果
            RobotEvent_atTrackTargetPos,        //机械臂轨迹运动到位信号通知

            RobotSetPowerOnDone,                //设置电源状态完成
            RobotReleaseBrakeDone,              //机械臂刹车释放完成
            RobotEvent_robotControllerStateChaned,  //机械臂控制状态改变
            RobotEvent_robotControllerError,        //机械臂控制错误----一般是算法规划出现问题时返回
            RobotEvent_socketDisconnected,          //socket断开连接

            RobotEvent_robotControlException,
            RobotEvent_trackPlayInterrupte,

            RobotEvent_staticCollisionStatusChanged,
            RobotEvent_MountingPoseWarning,
            RobotEvent_MacDataInterruptWarning,
            RobotEvent_ToolIoError,
            RobotEvent_InterfacBoardSafeIoEvent,

            RobotEvent_RobotHandShakeSucc,
            RobotEvent_RobotHandShakeFailed,


            RobotEvent_RobotErrorInfoNotify,


            RobotEvent_exceptEvent = 100,

            //unknown event
            robot_event_unknown,

            //user event
            RobotEvent_User = 1000,                            // first user event id
            RobotEvent_MaxUser = 65535                         // last user event id

        }


        int NO_TRACK = 0;

        //for moveJ and moveL
        int TRACKING = 1;

        //cartesian motion for moveP
        int ARC_CIR = 2;
        int CARTESIAN_MOVEP = 3;
        int CARTESIAN_CUBICSPLINE = 4;
        int CARTESIAN_UBSPLINEINTP = 5;
        int CARTESIAN_GNUBSPLINEINTP = 6;
        int CARTESIAN_LOOKAHEAD = 7;

        //joint motion for moveP
        int JIONT_CUBICSPLINE = 8;
        int JOINT_UBSPLINEINTP = 9;
        int JOINT_GNUBSPLINEINTP = 10;
        int JOINT_TOPF = 11;

        int JOINT_MOVE_PROCESS = 12;           // moveP in ARAL
        int SPEEDJ = 13;                       // moveP in ARAL
        int SPEEDL = 14;                      // moveP in ARAL
        int POLYLINE = 15;                     // moveGroup：多段直线
        int JOINT_GNUBSPLINEINTP_ENDSPEED = 16;// moveGroup：关节空间B样条+末端速度限制

        int ARC = 90;
        int CIRCLE = 91;
        int ARC_ORI_ROTATED = 92;
        int CIRCLE_ORI_ROTATED = 93;

        int ORI_POSITION_ROTATE_CIRCUMFERENCE = 101;




        int result = 0xffff;
        UInt16 rshd = 0xffff;
        UInt16 sjhd = 0xffff;

        double[] target0 = { 0, 0, 0, 0, 0, 0 };
        double[] target1 = { 1, 1, 1, 1, 1, 1 };
        double[] max_velc = { 1.5, 1.5, 1.5, 1.5, 1.5, 1.5 };
        double[] max_acc = { 5, 5, 5, 5, 5, 5 };
        JointVelcAccParam temp = new JointVelcAccParam();
        wayPoint_S waypoint = new wayPoint_S();
        wayPoint_S targetpoint = new wayPoint_S();
        double io_status = 0;
        Rpy rpy = new Rpy();
        Ori ori = new Ori();
        Pos pos = new Pos();

        const int RSERR_SUCC = 0;
        //关节个数
        const int ARM_DOF = 6;
        //机械臂IP地址
        string robotIP = "192.168.41.129";
        //机械臂端口号
        const int serverPort = 8899;

        string servojIP = "192.168.1.50";

        const int servojPort = 6000;


        //M_PI
        const double M_PI = 3.14159265358979323846;

        //接口板用户DI地址
        const int ROBOT_IO_F1 = 30;
        const int ROBOT_IO_F2 = 31;
        const int ROBOT_IO_F3 = 32;
        const int ROBOT_IO_F4 = 33;
        const int ROBOT_IO_F5 = 34;
        const int ROBOT_IO_F6 = 35;
        const int ROBOT_IO_U_DI_00 = 36;
        const int ROBOT_IO_U_DI_01 = 37;
        const int ROBOT_IO_U_DI_02 = 38;
        const int ROBOT_IO_U_DI_03 = 39;
        const int ROBOT_IO_U_DI_04 = 40;
        const int ROBOT_IO_U_DI_05 = 41;
        const int ROBOT_IO_U_DI_06 = 42;
        const int ROBOT_IO_U_DI_07 = 43;
        const int ROBOT_IO_U_DI_10 = 44;
        const int ROBOT_IO_U_DI_11 = 45;
        const int ROBOT_IO_U_DI_12 = 46;
        const int ROBOT_IO_U_DI_13 = 47;
        const int ROBOT_IO_U_DI_14 = 48;
        const int ROBOT_IO_U_DI_15 = 49;
        const int ROBOT_IO_U_DI_16 = 50;
        const int ROBOT_IO_U_DI_17 = 51;

        //接口板用户DO地址
        const int ROBOT_IO_U_DO_00 = 32;
        const int ROBOT_IO_U_DO_01 = 33;
        const int ROBOT_IO_U_DO_02 = 34;
        const int ROBOT_IO_U_DO_03 = 35;
        const int ROBOT_IO_U_DO_04 = 36;
        const int ROBOT_IO_U_DO_05 = 37;
        const int ROBOT_IO_U_DO_06 = 38;
        const int ROBOT_IO_U_DO_07 = 39;
        const int ROBOT_IO_U_DO_10 = 40;
        const int ROBOT_IO_U_DO_11 = 41;
        const int ROBOT_IO_U_DO_12 = 42;
        const int ROBOT_IO_U_DO_13 = 43;
        const int ROBOT_IO_U_DO_14 = 44;
        const int ROBOT_IO_U_DO_15 = 45;
        const int ROBOT_IO_U_DO_16 = 46;
        const int ROBOT_IO_U_DO_17 = 47;

        //接口板用户AI地址
        const int ROBOT_IO_VI0 = 0;
        const int ROBOT_IO_VI1 = 1;
        const int ROBOT_IO_VI2 = 2;
        const int ROBOT_IO_VI3 = 3;

        //接口板用户AO地址
        const int ROBOT_IO_VO0 = 0;
        const int ROBOT_IO_VO1 = 1;
        const int ROBOT_IO_CO0 = 2;
        const int ROBOT_IO_CO1 = 3;

        //接口板IO类型
        //
        //接口板用户DI(数字量输入)　可读可写
        const int Robot_User_DI = 4;
        //接口板用户DO(数字量输出)  可读可写
        const int Robot_User_DO = 5;
        //接口板用户AI(模拟量输入)  可读可写
        const int Robot_User_AI = 6;
        //接口板用户AO(模拟量输出)  可读可写
        const int Robot_User_AO = 7;

        //工具端IO类型
        //
        //工具端DI
        const int Robot_Tool_DI = 8;
        //工具端DO
        const int Robot_Tool_DO = 9;
        //工具端AI
        const int Robot_Tool_AI = 10;
        //工具端AO
        const int Robot_Tool_AO = 11;
        //工具端DI
        const int Robot_ToolIoType_DI = Robot_Tool_DI;
        //工具端DO
        const int Robot_ToolIoType_DO = Robot_Tool_DO;

        //工具端IO名称
        const string TOOL_IO_0 = ("T_DI/O_00");
        const string TOOL_IO_1 = ("T_DI/O_01");
        const string TOOL_IO_2 = ("T_DI/O_02");
        const string TOOL_IO_3 = ("T_DI/O_03");

        //工具端数字IO类型
        //
        //输入
        const int TOOL_IO_IN = 0;
        //输出
        const int TOOL_IO_OUT = 0;

        //工具端电源类型
        //
        const int OUT_0V = 0;
        const int OUT_12V = 1;
        const int OUT_24V = 2;

        //IO状态-无效
        const double IO_STATUS_INVALID = 0.0;
        //IO状态-有效
        const double IO_STATUS_VALID = 1.0;

        //坐标系枚举
        const int BaseCoordinate = 0;
        const int EndCoordinate = 1;
        const int WorldCoordinate = 2;

        //坐标系标定方法
        const int Origin_AnyPointOnPositiveXAxis_AnyPointOnPositiveYAxis = 0; // 原点、x轴正半轴、y轴正半轴
        const int Origin_AnyPointOnPositiveYAxis_AnyPointOnPositiveZAxis = 1; // 原点、y轴正半轴、z轴正半轴
        const int Origin_AnyPointOnPositiveZAxis_AnyPointOnPositiveXAxis = 2; // 原点、z轴正半轴、x轴正半轴
        const int Origin_AnyPointOnPositiveXAxis_AnyPointOnFirstQuadrantOfXOYPlane = 3; // 原点、x轴正半轴、x、y轴平面的第一象限上任意一点
        const int Origin_AnyPointOnPositiveXAxis_AnyPointOnFirstQuadrantOfXOZPlane = 4; // 原点、x轴正半轴、x、z轴平面的第一象限上任意一点
        const int Origin_AnyPointOnPositiveYAxis_AnyPointOnFirstQuadrantOfYOZPlane = 5; // 原点、y轴正半轴、y、z轴平面的第一象限上任意一点
        const int Origin_AnyPointOnPositiveYAxis_AnyPointOnFirstQuadrantOfYOXPlane = 6; // 原点、y轴正半轴、y、x轴平面的第一象限上任意一点
        const int Origin_AnyPointOnPositiveZAxis_AnyPointOnFirstQuadrantOfZOXPlane = 7; // 原点、z轴正半轴、z、x轴平面的第一象限上任意一点
        const int Origin_AnyPointOnPositiveZAxis_AnyPointOnFirstQuadrantOfZOYPlane = 8; // 原点、z轴正半轴、z、y轴平面的第一象限上任意一点

        //运动轨迹类型
        //
        //圆
        //const int ARC_CIR = 2;
        //圆弧
        //const int CARTESIAN_MOVEP = 3;

        //机械臂状态
        const int RobotStopped = 0;
        const int RobotRunning = 1;
        const int RobotPaused = 2;
        const int RobotResumed = 3;

        //机械臂工作模式
        const int RobotModeSimulator = 0; //机械臂仿真模式
        const int RobotModeReal = 1; //机械臂真实模式

        //路点位置信息的表示方法
        [StructLayout(LayoutKind.Sequential)]
        public struct Pos
        {
            public double x;
            public double y;
            public double z;
        }

        //路点位置信息的表示方法
        [StructLayout(LayoutKind.Sequential)]
        public struct cartesianPos_U
        {
            // 指定数组尺寸
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 3)]
            public double[] positionVector;
        };

        //姿态的四元素表示方法
        [StructLayout(LayoutKind.Sequential)]
        public struct Ori
        {
            public double w;
            public double x;
            public double y;
            public double z;
        };

        //姿态的欧拉角表示方法
        [StructLayout(LayoutKind.Sequential)]
        public struct Rpy
        {
            public double rx;
            public double ry;
            public double rz;
        };

        //描述机械臂的路点信息
        [StructLayout(LayoutKind.Sequential)]
        public struct wayPoint_S
        {
            //机械臂的位置信息　X,Y,Z
            public Pos cartPos;
            //机械臂姿态信息
            public Ori orientation;
            //机械臂关节角信息
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = ARM_DOF)]
            public double[] jointpos;
        };

        //机械臂关节速度加速度信息
        [StructLayout(LayoutKind.Sequential)]
        public struct JointVelcAccParam
        {
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = ARM_DOF)]
            public double[] jointPara;
        };

        //机械臂关节角度
        [StructLayout(LayoutKind.Sequential)]
        public struct JointRadian
        {
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = ARM_DOF)]
            public double[] jointRadian;
        };

        //机械臂工具端参数
        [StructLayout(LayoutKind.Sequential)]
        public struct ToolInEndDesc
        {
            //工具相对于末端坐标系的位置
            public Pos cartPos;
            //工具相对于末端坐标系的姿态
            public Ori orientation;
        };

        //坐标系结构体
        [StructLayout(LayoutKind.Sequential)]
        public struct CoordCalibrate
        {
            //坐标系类型：当coordType==BaseCoordinate或者coordType==EndCoordinate是，下面3个参数不做处理
            public int coordType;
            //坐标系标定方法
            public int methods;
            //用于标定坐标系的３个点（关节角），对应于机械臂法兰盘中心点基于基座标系
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 3)]
            public JointRadian[] jointPara;
            //标定的时候使用的工具描述
            public ToolInEndDesc toolDesc;
        };

        //转轴定义
        [StructLayout(LayoutKind.Sequential)]
        public struct MoveRotateAxis
        {
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 3)]
            public double[] rotateAxis;

        };

        //描述运动属性中的偏移属性
        [StructLayout(LayoutKind.Sequential)]
        public struct MoveRelative
        {
            //是否使能偏移
            public byte enable;
            //偏移量 x,y,z
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 3)]
            public float[] pos;
            //public Pos pos;
            //相对姿态偏移量
            public Ori orientation;
        };

        //该结构体描述工具惯量
        [StructLayout(LayoutKind.Sequential)]
        public struct ToolInertia
        {
            public double xx;
            public double xy;
            public double xz;
            public double yy;
            public double yz;
            public double zz;
        };

        //动力学参数
        [StructLayout(LayoutKind.Sequential)]
        public struct ToolDynamicsParam
        {
            public double positionX; //工具重心的X坐标
            public double positionY; //工具重心的Y坐标
            public double positionZ; //工具重心的Z坐标
            public double payload; //工具重量
            public ToolInertia toolInertia; //工具惯量
        };



        //机械臂事件
        [StructLayout(LayoutKind.Sequential)]
        public struct RobotEventInfo
        {
            public int eventType; //事件类型号
            public int eventCode; //事件代码
            public IntPtr eventContent; //事件内容(std::string)
        };
        //安装姿态
        public enum RobotControlCommand
        {
            RobotRelease = 0,    // 释放刹车
            RobotBrake = 1,    // 刹车
            OverspeedWarning = 2,    // 拖动示教速度过快报警
            OverspeedRecover = 3,    // 解除拖动过速报警
            DisableForceControl = 4,    // 失能力控
            EnableForceControl = 5,    // 使能力控
            OrpeOpen = 6,    // 打开上位机软件
            OrpeClose = 7,    // 关闭上位机软件
            EnableReadPose = 8,    // 打开读取位姿
            DisableReadPose = 9,    // 关闭读取位姿
            MountingPoseChanged = 10,   // 安装位置已改变
            MountingPoseUnChanged = 11,   // 安装位置未改变
            EnableStaticCollisionDetect = 12, // 打开静止碰撞检测
            DisableStaticCollisionDetect = 13, // 关闭静止碰撞检测
            ClearSingularityOverSpeedAlarm = 14, // 解除机械臂奇异点过速警告
            ClearRobotCurrentAlarm = 15,  // 解除机械臂电流错误警告
            SetCollisionFreeDrive = 0x10,//碰撞之后进入拖动示教模式
            SetCollisionStuck = 0x11,//碰撞之后进入停止模式
            SetCollisionAdamittance = 0x12,//碰撞之后进入导纳控制模式
            EnableNewTemperatureCompensation = 0x13, //打开新型温度补偿开关
            DisableNewTemperatureCompensation = 0x14, //关闭新型温度补偿开关
            EnterDragAndTeachMode = 0x15,  //进入拖动示教模式
            ExitDragAndTeachMode = 0x16,  //退出拖动示教模式
            EnablePayloadDetect = 0x22, //使能负载检测
            DisablePayloadDetect = 0x23, //失能负载检测
            RobotShutdown = 0x24,  //上位机指令控制机械臂一类停机，并上传错误
            ClearRobotShutdown = 0x25,   //清除上位机一类停机标志
            EnableFrictionCollisionDetect = 0x26,//使能摩擦力模型碰撞检测功能
            DisableFrictionCollisionDetect = 0x27, //失能摩擦力模型碰撞检测功能
            EnableCollisionStuckBrake = 0x28,   //使能碰撞stuck模式锁刹车
            DisableCollisionStuckBrake = 0x29,  //失能能碰撞stuck模式锁刹车
            EnableNewForceTeach = 0x2a, //打开新型拖动示教开关
            DisableNewForceTeach = 0x2b, //关闭新型拖动示教开关
        };

        //机械臂诊断信息 qsz
        // [StructLayout(LayoutKind.Sequential)]
        public struct RobotDiagnosis
        {
            public Byte armCanbusStatus;                // CAN通信状态:0x01~0x80：关节CAN通信错误（每个关节占用1bit） 0x00：无错误
            public float armPowerCurrent;                // 机械臂48V电源当前电流
            public float armPowerVoltage;                // 机械臂48V电源当前电压
            public Byte armPowerStatus;                 // 机械臂48V电源状态（开、关）
            public Byte contorllerTemp;                 // 控制箱温度
            public Byte contorllerHumidity;             // 控制箱湿度
            public Byte remoteHalt;                     // 远程关机信号
            public Byte softEmergency;                  // 机械臂软急停
            public Byte remoteEmergency;                // 远程急停信号
            public Byte robotCollision;                 // 碰撞检测位
            public Byte forceControlMode;               // 机械臂进入力控模式标志位
            public Byte brakeStuats;                    // 刹车状态
            public float robotEndSpeed;                  // 末端速度
            public int robotMaxAcc;                    // 最大加速度
            public Byte orpeStatus;                     // 上位机软件状态位
            public Byte enableReadPose;                 // 位姿读取使能位
            public Byte robotMountingPoseChanged;       // 安装位置状态
            public Byte encoderErrorStatus;             // 磁编码器错误状态
            public Byte staticCollisionDetect;          // 静止碰撞检测开关
            public Byte jointCollisionDetect;           // 关节碰撞检测 每个关节占用1bit 0-无碰撞 1-存在碰撞
            public Byte encoderLinesError;              // 光电编码器不一致错误 0-无错误 1-有错误
            public Byte jointErrorStatus;               // joint error status
            public Byte singularityOverSpeedAlarm;      // 机械臂奇异点过速警告
            public Byte robotCurrentAlarm;              // 机械臂电流错误警告
            public Byte toolIoError;                    // tool error
            public Byte robotMountingPoseWarning;       // 机械臂安装位置错位（只在力控模式下起作用）
            public ushort macTargetPosBufferSize;         // mac缓冲器长度          预留
            public ushort macTargetPosDataSize;           // mac缓冲器有效数据长度   预留
            public Byte macDataInterruptWarning;        // mac数据中断           预留
            public Byte controlBoardAbnormalStateFlag;  //主控板(接口板)异常状态标志
        };

        // qisaizhao
        //public struct JointParam
        //{
        //    public double[] jointPos;
        //};




        [StructLayout(LayoutKind.Sequential)]
        public struct JointParam
        {
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = ARM_DOF)]
            public double[] jointPos;
        };



        //机械臂重力分量  qisaizhao
        public struct RobotGravityComponent
        {
            public float x;
            public float y;
            public float z;
        };
        //
        // 示教模式
        public enum teach_mode
        {
            JOINT1 = 1,    //释放刹车
            JOINT2 = 2,     //刹车
            JOINT3 = 3,       //
            JOINT4 = 4,
            JOINT5 = 5,
            JOINT6 = 6,
            MOV_X = 7,
            MOV_Y = 8,
            MOV_Z = 9,
            ROT_X = 10,
            ROT_Y = 11,
            ROT_Z = 12
        };

        public struct JointStatus
        {
            public int jointCurrentI;       /**< Current of driver   关节电流*/
            public int jointSpeedMoto;      /**< Speed of driver 　　关节速度*/
            public float jointPosJ;           /**< Current position in radian 　关节角*/
            public float jointCurVol;         /**< Rated voltage of motor. Unit: mV 　关节电压*/
            public float jointCurTemp;        /**< Current temprature of joint 　　　　当前温度*/
            public int jointTagCurrentI;    /**< Target current of motor 　　　　　 　电机目标电流*/
            public float jointTagSpeedMoto;   /**< Target speed of motor 　　　　　　　 电机目标速度*/
            public float jointTagPosJ;        /**< Target position of joint in radian 目标关节角　*/
            public UInt16 jointErrorNum;       /**< Joint error of joint num　　　　　　关节错误码 */
        };



        //初始化ServoJ
        [DllImport("libservojclient.dll", EntryPoint = "servoj_initialize", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int servoj_initialize();

        [DllImport("libservojclient.dll", EntryPoint = "servoj_create_context", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int servoj_create_context(ref UInt16 sjhd);

        [DllImport("libservojclient.dll", EntryPoint = "servoj_login", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int servoj_login(UInt16 sjhd, [MarshalAs(UnmanagedType.LPStr)] string addr, int port);

        [DllImport("libservojclient.dll", EntryPoint = "servoj_startServoj", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int servoj_startServoj(UInt16 sjhd);

        [DllImport("libservojclient.dll", EntryPoint = "servoj_stopServoj", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int servoj_stopServoj(UInt16 sjhd);

        [DllImport("libservojclient.dll", EntryPoint = "servoj_servoj", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int servoj_servoj(UInt16 sjhd, double[] joint_radia, double duration, double smooth_scale, double delay_sacle, bool is_last_point = false);






        //初始化机械臂控制库
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_initialize", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_initialize();

        //反初始化机械臂控制库
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_uninitialize", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_uninitialize();

        //创建机械臂控制上下文句柄
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_create_context", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_create_context(ref UInt16 rshd);

        //注销机械臂控制上下文句柄
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_destory_context", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_destory_context(UInt16 rshd);

        //链接机械臂服务器
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_login", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_login(UInt16 rshd, [MarshalAs(UnmanagedType.LPStr)] string addr, int port);

        //断开机械臂服务器链接
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_logout", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_logout(UInt16 rshd);

        //初始化全局的运动属性
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_init_global_move_profile", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_init_global_move_profile(UInt16 rshd);

        //设置六个关节轴动的最大速度（最大为180度/秒），注意如果没有特殊需求，6个关节尽量配置成一样！
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_global_joint_maxvelc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_global_joint_maxvelc(UInt16 rshd, double[] max_velc);

        //获取六个关节轴动的最大速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_global_joint_maxvelc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_global_joint_maxvelc(UInt16 rshd, ref JointVelcAccParam max_velc);

        //设置六个关节轴动的最大加速度 （十倍的最大速度），注意如果没有特殊需求，6个关节尽量配置成一样！
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_global_joint_maxacc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_global_joint_maxacc(UInt16 rshd, double[] max_acc);

        //获取六个关节轴动的最大加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_global_joint_maxacc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_global_joint_maxacc(UInt16 rshd, ref JointVelcAccParam max_acc);

        //设置机械臂末端最大线加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_global_end_max_line_acc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_global_end_max_line_acc(UInt16 rshd, double max_acc);

        //设置机械臂末端最大线速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_global_end_max_line_velc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_global_end_max_line_velc(UInt16 rshd, double max_velc);

        //获取机械臂末端最大线加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_global_end_max_line_acc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_global_end_max_line_acc(UInt16 rshd, ref double max_acc);

        //获取机械臂末端最大线速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_global_end_max_line_velc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_global_end_max_line_velc(UInt16 rshd, ref double max_velc);

        //设置机械臂末端最大角加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_global_end_max_angle_acc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_global_end_max_angle_acc(UInt16 rshd, double max_acc);

        //设置机械臂末端最大角速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_global_end_max_angle_velc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_global_end_max_angle_velc(UInt16 rshd, double max_velc);

        //获取机械臂末端最大角加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_global_end_max_angle_acc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_global_end_max_angle_acc(UInt16 rshd, ref double max_acc);

        //获取机械臂末端最大角加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_global_end_max_angle_velc", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_global_end_max_angle_velc(UInt16 rshd, ref double max_velc);


        //设置机械臂末端最大线加加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_jerk_acc_ratio", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_jerk_acc_ratio(UInt16 rshd, double max_jerk);

        //设置机械臂末端最大角加加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_angle_jerk_acc_ratio", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_angle_jerk_acc_ratio(UInt16 rshd, double max_jerk);

        //获取机械臂末端最大线加加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_jerk_acc_ratio", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_jerk_acc_ratio(UInt16 rshd, ref double max_jerk);

        //获取机械臂末端最大角加加速度
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_angle_jerk_acc_ratio", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_angle_jerk_acc_ratio(UInt16 rshd, ref double max_jerk);

        //设置用户坐标系
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_user_coord", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_user_coord(UInt16 rshd, ref CoordCalibrate user_coord);

        //设置基座坐标系
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_base_coord", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_base_coord(UInt16 rshd);

        //机械臂轴动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_joint", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_joint(UInt16 rshd, double[] joint_radia, bool isblock);

        //机械臂直线运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_line", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_line(UInt16 rshd, double[] joint_radia, bool isblock);

        //保持当前位置变换姿态做旋转运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_rotate", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_rotate(UInt16 rshd, ref CoordCalibrate user_coord, ref MoveRotateAxis rotate_axis, double rotate_angle, bool isblock);

        //清除所有已经设置的全局路点
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_remove_all_waypoint", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_remove_all_waypoint(UInt16 rshd);

        //添加全局路点用于轨迹运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_add_waypoint", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_add_waypoint(UInt16 rshd, double[] joint_radia);

        //设置交融半径
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_blend_radius", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_blend_radius(UInt16 rshd, double radius);

        //设置圆运动圈数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_circular_loop_times", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_circular_loop_times(UInt16 rshd, int times);

        //检查用户坐标系参数设置是否合理
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_check_user_coord", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_check_user_coord(UInt16 rshd, ref CoordCalibrate user_coord);

        //设置基于基座标系运动偏移量
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_relative_offset_on_base", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_relative_offset_on_base(UInt16 rshd, ref MoveRelative relative);

        //设置基于用户标系运动偏移量
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_relative_offset_on_user", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_relative_offset_on_user(UInt16 rshd, ref MoveRelative relative, ref CoordCalibrate user_coord);

        //取消提前到位设置
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_no_arrival_ahead", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_no_arrival_ahead(UInt16 rshd);

        //设置距离模式下的提前到位距离
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_arrival_ahead_distance", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_arrival_ahead_distance(UInt16 rshd, double distance);

        //设置时间模式下的提前到位时间
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_arrival_ahead_time", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_arrival_ahead_time(UInt16 rshd, double sec);

        //轨迹运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_track", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_track(UInt16 rshd, int sub_move_mode, bool isblock);

        //保持当前位姿通过直线运动的方式运动到目标位置
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_line_to", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_line_to(UInt16 rshd, ref Pos target, ref ToolInEndDesc tool, bool isblock);

        //保持当前位姿通过关节运动的方式运动到目标位置
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_joint_to", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_joint_to(UInt16 rshd, ref Pos target, ref ToolInEndDesc tool, bool isblock);

        //获取机械臂当前位置信息
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_current_waypoint", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_current_waypoint(UInt16 rshd, ref wayPoint_S waypoint);


        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_joint_status", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_joint_status(UInt16 rshd, [Out] JointStatus[] jointStatus);


        //正解
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_forward_kin", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_forward_kin(UInt16 rshd, double[] joint_radia, ref wayPoint_S waypoint);

        //逆解
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_inverse_kin", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_inverse_kin(UInt16 rshd, double[] joint_radia, ref Pos pos, ref Ori ori, ref wayPoint_S waypoint);

        //四元素转欧拉角
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_rpy_to_quaternion", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_rpy_to_quaternion(UInt16 rshd, ref Rpy rpy, ref Ori ori);

        //欧拉角转四元素
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_quaternion_to_rpy", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_quaternion_to_rpy(UInt16 rshd, ref Ori ori, ref Rpy rpy);

        //基座坐标系转用户坐标系
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_base_to_user", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_base_to_user(UInt16 rshd, ref Pos pos_onbase, ref Ori ori_onbase, ref CoordCalibrate user_coord, ref ToolInEndDesc tool_pos, ref Pos pos_onuser, ref Ori ori_onuser);

        //用户坐标系转基座坐标系
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_user_to_base", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_user_to_base(UInt16 rshd, ref Pos pos_onuser, ref Ori ori_onuser, ref CoordCalibrate user_coord, ref ToolInEndDesc tool_pos, ref Pos pos_onbase, ref Ori ori_onbase);

        //基坐标系转基座标得到工具末端点的位置和姿态
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_base_to_base_additional_tool", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_base_to_base_additional_tool(UInt16 rshd, ref Pos flange_center_pos_onbase, ref Ori flange_center_ori_onbase, ref ToolInEndDesc tool_pos, ref Pos tool_end_pos_onbase, ref Ori tool_end_ori_onbase);

        //设置工具的运动学参数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_tool_end_param", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_tool_end_param(UInt16 rshd, ref ToolInEndDesc tool);

        //设置无工具的动力学参数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_none_tool_dynamics_param", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_none_tool_dynamics_param(UInt16 rshd);

        //根据接口板IO类型和地址设置IO状态
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_board_io_status_by_addr", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_board_io_status_by_addr(UInt16 rshd, int io_type, int addr, double val);

        //根据接口板IO类型和地址获取IO状态
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_board_io_status_by_addr", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_board_io_status_by_addr(UInt16 rshd, int io_type, int addr, ref double val);

        //设置工具端IO状态
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_tool_do_status", CharSet = CharSet.Ansi, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_tool_do_status(UInt16 rshd, string name, int val);

        //获取工具端IO状态
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_tool_io_status", CharSet = CharSet.Ansi, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_tool_io_status(UInt16 rshd, string name, ref double val);

        //设置工具端电源电压类型
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_tool_power_type", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_tool_power_type(UInt16 rshd, int type);

        //获取工具端电源电压类型
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_tool_power_type", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_tool_power_type(UInt16 rshd, ref int type);

        //获取工具端电源电压数值
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_tool_power_voltage", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_tool_power_voltage(UInt16 rshd, ref double voltage);

        //设置工具端数字量IO的类型
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_tool_io_type", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_tool_io_type(UInt16 rshd, int addr, int type);

        //设置工具的动力学参数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_tool_dynamics_param", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_tool_dynamics_param(UInt16 rshd, ref ToolDynamicsParam tool);

        //获取工具的动力学参数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_tool_dynamics_param", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_tool_dynamics_param(UInt16 rshd, ref ToolDynamicsParam tool);

        //设置无工具运动学参数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_none_tool_kinematics_param", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_none_tool_kinematics_param(UInt16 rshd);

        //设置工具的运动学参数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_tool_kinematics_param", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_tool_kinematics_param(UInt16 rshd, ref ToolInEndDesc tool);

        //获取工具的运动学参数
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_tool_kinematics_param", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_tool_kinematics_param(UInt16 rshd, ref ToolInEndDesc tool);

        //启动机械臂
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_robot_startup", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_robot_startup(UInt16 rshd, ref ToolDynamicsParam tool, byte colli_class, bool read_pos, bool static_colli_detect, int board_maxacc, ref int state);

        //关闭机械臂
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_robot_shutdown", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_robot_shutdown(UInt16 rshd);

        //停止机械臂运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_stop", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_stop(UInt16 rshd);

        //停止机械臂运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_fast_stop", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_fast_stop(UInt16 rshd);

        //暂停机械臂运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_pause", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_pause(UInt16 rshd);

        //暂停后回复机械臂运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_move_continue", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_move_continue(UInt16 rshd);

        //机械臂碰撞后恢复
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_collision_recover", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_collision_recover(UInt16 rshd);

        //获取机械臂末端速度

        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_robot_end_speed", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_robot_end_speed(UInt16 rshd, ref float endspeed);


        //获取机械臂当前状态
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_robot_state", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_robot_state(UInt16 rshd, ref int state);

        //设置机械臂服务器工作模式
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_work_mode", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_work_mode(UInt16 rshd, int state);

        //获取机械臂服务器当前工作模式
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_work_mode", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_work_mode(UInt16 rshd, ref int state);

        //设置机械臂碰撞等级
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_collision_class", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_collision_class(UInt16 rshd, int grade);


        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_robot_orpe_stop", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_robot_orpe_stop(UInt16 rshd, UInt16 data);


        //获得机械臂重力分量
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_gravity_component", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_gravity_component(UInt16 rshd, ref RobotGravityComponent gravity);


        //获取机械臂诊断信息
        [DllImport("serviceinterface2.dll", EntryPoint = "rs_get_diagnosis_info", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_diagnosis_info(UInt16 rshd, ref RobotDiagnosis robotDiagnosis);





        //设置机械臂按照位置是否改变
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_robot_control", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_robot_control(UInt16 rshd, RobotControlCommand cmd);

        //开始轴动示教
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_teach_move_start", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_teach_move_start(UInt16 rshd, teach_mode mode, bool dir);

        //结束示教
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_teach_move_stop", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_teach_move_stop(UInt16 rshd);


        //start movegroup运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_start_movegroup", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_start_movegroup(UInt16 rshd);

        //stop movegroup运动
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_endof_movegroup", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_endof_movegroup(UInt16 rshd);

        //wait movegroup move finished
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_wait_movegroup_finished", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_wait_movegroup_finished(UInt16 rshd);


        //获取socket链接状态
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_get_socket_status", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_get_socket_status(UInt16 rshd, ref byte connected);

        //设置是否允许实时路点信息推送
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_enable_push_realtime_roadpoint", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_enable_push_realtime_roadpoint(UInt16 rshd, bool enable);


        //向服务器添加非在线轨迹运动路点 qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_append_offline_track_waypoint", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_append_offline_track_waypoint(UInt16 rshd, ref JointParam[] waypoints, int waypoint_count);


        //向服务器添加非在线轨迹运动路点 qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_append_offline_track_file", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_append_offline_track_file(UInt16 rshd, [MarshalAs(UnmanagedType.LPStr)] string filename);



        //清理服务器上的非在线轨迹运动数据 qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_clear_offline_track", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_clear_offline_track(UInt16 rshd);



        //通知服务器启动非在线轨迹运动 qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_startup_offline_track", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_startup_offline_track(UInt16 rshd);



        //通知服务器停止非在线轨迹运动 qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_stop_offline_track", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_stop_offline_track(UInt16 rshd);


        //进入 tcp 转 can 透传模式 rs_enter_tcp2canbus_mode qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_enter_tcp2canbus_mode", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_enter_tcp2canbus_mode(UInt16 rshd);

        //退出 tcp 转 can 透传模式 rs_leave_tcp2canbus_mode qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_leave_tcp2canbus_mode", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_leave_tcp2canbus_mode(UInt16 rshd);


        //发送坐标数据到关节 can 总线 rs_set_waypoint_to_canbus qi
        [DllImport("libserviceinterface.dll", EntryPoint = "rs_set_waypoint_to_canbus", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_set_waypoint_to_canbus(UInt16 rshd, double[] joint_radia);

        //实时路点回调函数
        [System.Runtime.InteropServices.UnmanagedFunctionPointerAttribute(System.Runtime.InteropServices.CallingConvention.Cdecl)]
        public delegate void REALTIME_ROADPOINT_CALLBACK(ref wayPoint_S waypoint, IntPtr arg);

        [DllImport("libserviceinterface.dll", CallingConvention = CallingConvention.Cdecl)]
        public static extern void rs_setcallback_realtime_roadpoint(UInt16 rshd, [MarshalAs(UnmanagedType.FunctionPtr)] REALTIME_ROADPOINT_CALLBACK CurrentPositionCallback, IntPtr arg);


        //关节状态回调函数
        [System.Runtime.InteropServices.UnmanagedFunctionPointerAttribute(System.Runtime.InteropServices.CallingConvention.Cdecl)]
        public delegate void REALTIME_JOINTSTATUS_CALLBACK(JointStatus[] JointStatus, IntPtr arg);

        [DllImport("libserviceinterface.dll", CallingConvention = CallingConvention.Cdecl)]
        public static extern void rs_setcallback_realtime_joint_status(UInt16 rshd, [MarshalAs(UnmanagedType.FunctionPtr)] REALTIME_JOINTSTATUS_CALLBACK JointStatusCallback, IntPtr arg);


        [DllImport("libserviceinterface.dll", EntryPoint = "rs_script_run_string", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_script_run_string(UInt16 rshd, string script_string, string script_md);


        [DllImport("libserviceinterface.dll", EntryPoint = "rs_thread_script_run_string", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_thread_script_run_string(UInt16 rshd, ref string script_string, ref string script_md);


        //机械臂事件
        [System.Runtime.InteropServices.UnmanagedFunctionPointerAttribute(System.Runtime.InteropServices.CallingConvention.Cdecl)]
        public delegate void ROBOT_EVENT_CALLBACK(ref RobotEventInfo rs_event, IntPtr arg);

        [DllImport("libserviceinterface.dll", CallingConvention = CallingConvention.Cdecl)]
        public static extern void rs_setcallback_robot_event(UInt16 rshd, [MarshalAs(UnmanagedType.FunctionPtr)] ROBOT_EVENT_CALLBACK RobotEventCallback, IntPtr arg);

        //回调函数
        static void CurrentPositionCallback(ref wayPoint_S waypoint, IntPtr arg)
        {
            PrintWaypoint(waypoint);

            Console.WriteLine("当前路点信息为：");

        }

        static void JointStatusCallback([Out] JointStatus[] jointstatus, IntPtr arg)
        {
            //PrintJointStatus(jointstatus[0]);

            Console.Out.WriteLine("jointCurrentI  = {0}", jointstatus[0].jointCurrentI);
            Console.Out.WriteLine("jointSpeedMoto = {0}", jointstatus[0].jointSpeedMoto);
            Console.Out.WriteLine("jointPosJ = {0}", jointstatus[0].jointPosJ);
            Console.Out.WriteLine("jointCurVol = {0}", jointstatus[0].jointCurVol);
            Console.Out.WriteLine("jointCurTemp = {0}", jointstatus[0].jointCurTemp);
        }
        //打印路点信息
        static void PrintWaypoint(wayPoint_S point)
        {
            Console.Out.WriteLine("---------------------------------------------------------------------------------------");
            Console.Out.WriteLine("pos.x={0} y={1} z={2}", point.cartPos.x, point.cartPos.y, point.cartPos.z);
            Console.Out.WriteLine("ori.w={0} x={1} y={2} z={3}", point.orientation.w, point.orientation.x, point.orientation.y, point.orientation.z);
            Console.Out.WriteLine("joint1={0} joint2={1} joint3={2}", point.jointpos[0] * 180 / M_PI, point.jointpos[1] * 180 / M_PI, point.jointpos[2] * 180 / M_PI);
            Console.Out.WriteLine("joint4={0} joint5={1} joint6={2}", point.jointpos[3] * 180 / M_PI, point.jointpos[4] * 180 / M_PI, point.jointpos[5] * 180 / M_PI);
            Console.Out.WriteLine("---------------------------------------------------------------------------------------");
        }

        static void PrintJointStatus(JointStatus jointstatus)
        {
            //Console.Out.WriteLine("---------------------------------------------------------------------------------------");
            //Console.Out.WriteLine("pos.x={0} y={1} z={2}", point.cartPos.x, point.cartPos.y, point.cartPos.z);
            //Console.Out.WriteLine("ori.w={0} x={1} y={2} z={3}", point.orientation.w, point.orientation.x, point.orientation.y, point.orientation.z);
            //Console.Out.WriteLine("joint1={0} joint2={1} joint3={2}", point.jointpos[0] * 180 / M_PI, point.jointpos[1] * 180 / M_PI, point.jointpos[2] * 180 / M_PI);
            //Console.Out.WriteLine("joint4={0} joint5={1} joint6={2}", point.jointpos[3] * 180 / M_PI, point.jointpos[4] * 180 / M_PI, point.jointpos[5] * 180 / M_PI);
            //Console.Out.WriteLine("---------------------------------------------------------------------------------------");

        }

        static void RobotEventCallback(ref RobotEventInfo rs_event, IntPtr arg)
        {
            //Console.Out.WriteLine("---------------------------------------------------------------------------------------");
            //Console.Out.WriteLine("robot event.type={0}", rs_event.eventType);
            //Console.Out.WriteLine("robot event.eventCode={0}", rs_event.eventCode);
            //Console.Out.WriteLine("robot event.eventContent={0}", Marshal.PtrToStringAnsi(rs_event.eventContent));
            //Console.Out.WriteLine("---------------------------------------------------------------------------------------");
        }
        static bool TrackTargetPosState = false;
        private void EventCallBack(ref RobotEventInfo rs_event, IntPtr arg)  // 2021.08.04
        {
            Console.Out.WriteLine("---------------------------------------------------------------------------------------");
            Console.Out.WriteLine("robot event.type={0}", rs_event.eventType);
            Console.Out.WriteLine("robot event.eventCode={0}", rs_event.eventCode);
            Console.Out.WriteLine("robot event.eventContent={0}", Marshal.PtrToStringAnsi(rs_event.eventContent));
            Console.Out.WriteLine("---------------------------------------------------------------------------------------");
            int aa = 0;

            aa = rs_event.eventType;
            aa = rs_event.eventType;

            switch (rs_event.eventType)
            {
                case (int)RobotEventType.RobotEvent_encoderError:
                    break;
                case (int)RobotEventType.RobotEvent_atTrackTargetPos:
                    TrackTargetPosState = true;
                    break;
                case (int)RobotEventType.RobotEvent_collision:
                    break;
                case (int)RobotEventType.RobotEvent_singularityOverspeed:
                    break;


                default:
                    break;
            }

            //RobotEvent_armCanbusError,           //机械臂CAN总线错误
            //RobotEvent_remoteHalt,               //远程关机　　　　TODO
            //RobotEvent_remoteEmergencyStop,      //机械臂远程急停
            //RobotEvent_jointError,               //关节错误

            //RobotEvent_forceControl,             //力控制
            //RobotEvent_exitForceControl,         //退出力控制

            //RobotEvent_softEmergency,            //软急停
            //RobotEvent_exitSoftEmergency,        //退出软急停

            //RobotEvent_collision,                //碰撞
            //RobotEvent_collisionStatusChanged,   //碰撞状态改变
            //RobotEvent_tcpParametersSucc,        //工具动力学参数设置成功
            //RobotEvent_powerChanged,             //机械臂电源开关状态改变
            //RobotEvent_ArmPowerOff,              //机械臂电源关闭
            //RobotEvent_mountingPoseChanged,      //安装位置发生改变
            //RobotEvent_encoderError,             //编码器错误

            //RobotEvent_encoderLinesError,        //编码器线数不一致
            //RobotEvent_singularityOverspeed,     //奇异点超速
            //RobotEvent_currentAlarm,             //机械臂电流异常
            //RobotEvent_toolioError,             //机械臂工具端错误
            //RobotEvent_robotStartupPhase,       //机械臂启动阶段
            //RobotEvent_robotStartupDoneResult,  //机械臂启动完成结果
            //RobotEvent_robotShutdownDone,       //机械臂关机结果
            //RobotEvent_atTrackTargetPos,        //机械臂轨迹运动到位信号通知
            //}
        }


        static void realtimeRoadPointExample(UInt16 rshd)
        {
            int ret = rs_enable_push_realtime_roadpoint(rshd, true);
            if (ret == RSERR_SUCC)
            {
                rs_setcallback_realtime_roadpoint(rshd, CurrentPositionCallback, IntPtr.Zero);
                Thread.Sleep(5 * 1000);
            }
            else
            {
                Console.Error.WriteLine("调用 rs_enable_push_realtime_roadpoint 函数失败！错误码：{ 0} ", ret);
            }
        }

        #endregion

    }


}
