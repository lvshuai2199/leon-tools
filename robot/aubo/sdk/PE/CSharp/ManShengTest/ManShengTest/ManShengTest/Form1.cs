using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Runtime.InteropServices;
using System.Threading;
using Newtonsoft.Json;
using System.IO;



namespace ManShengTest
{
    public partial class Form1 : Form
    {
        public Form1()
        {
            InitializeComponent();

            width = this.Width;
            height = this.Height;

            InitWidgetStatus();
        }
        #region 自定义变量
        private float width;//定义当前窗体的宽度
        private float height;//定义当前窗体的高度

        const string service_interface_dll = "libserviceinterface.dll";
        const string ip = "192.168.144.128";
        const int port = 8899;
        const int RS_SUCC = 0;
        static UInt16 rshd = 0xffff;

        AuboRobot aubo = new AuboRobot();
        AuboRobot.wayPoint_S thisWaypoint = new AuboRobot.wayPoint_S();
        // int result = rs_initialize();
        int result = AuboRobot.rs_initialize();

        
        double io_status;

        //M_PI
        const double M_PI = 3.14159265358979323846;

        //偏移值
        float offsetXData = 0.0f;
        float offsetYData = 0.0f;
        float offsetZData = 0.0f;

        //多线程
        Thread thread1;
        Thread thread2;
        Thread thread3;
        Thread thread4;
        Thread thread5;
        Thread thread6;

        Thread threadAic;
        Thread threadCir;
        Thread threadMoveP;

        Thread threadOffset;

        Thread threadCallBack;
        //矩阵测试线程
        Thread threadMatrix;

        Thread threadInitPosOri;
        Thread threadZeroPosOri;

        Thread threadTcpToCanbus;

        Thread moveTest;
        Thread moveCirTest;

        Thread jointMove;
        Thread lineMove;
        Thread move_track;

        //用户坐标系
        AuboRobot.CoordCalibrate curBaseCoord = new AuboRobot.CoordCalibrate();
        AuboRobot.CoordCalibrate curFlangeCenterCoord = new AuboRobot.CoordCalibrate();
        AuboRobot.CoordCalibrate curUserCoord = new AuboRobot.CoordCalibrate();

        AuboRobot.ToolInEndDesc toolUserCoord=new AuboRobot.ToolInEndDesc();
        AuboRobot.ToolInEndDesc toolInEndDesc=new AuboRobot.ToolInEndDesc();
        AuboRobot.Pos toolEndPosOnUser=new AuboRobot.Pos();
        AuboRobot.Ori toolEndOriOnUser=new AuboRobot.Ori();
        AuboRobot.Rpy toolEndRpyOnUser = new AuboRobot.Rpy();

       

        bool isFlangeCenterTool=true;
        bool isTcpTool = false;

        bool isBaseCoord = true;
        bool isFlangeCenterCoord = true;
        bool isUserCoord = true;

        //当前坐标
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        double rx = 0.0;
        double ry = 0.0;
        double rz = 0.0;

        double ow = 0.0;
        double ox = 0.0;
        double oy = 0.0;
        double oz = 0.0;

        double joint1 = 0.0;
        double joint2 = 0.0;
        double joint3 = 0.0;
        double joint4 = 0.0;
        double joint5 = 0.0;
        double joint6 = 0.0;

        //多路点测试
        List<double> jointTestData=new List<double>();
        

        List<List<double>> jointListData=new List<List<double>>();
        int waypointCount = 0;

        AuboRobot.joint chkJointData = new AuboRobot.joint();
        List<AuboRobot.joint> chkJointDataList = new List<AuboRobot.joint>();
        List<AuboRobot.joint> chkJointDataListBack = new List<AuboRobot.joint>();
        //写入文件的关节数据
        string jointStr = "";
        const string filename = "D:\\visual studio 2015\\Projects\\ManShengTest\\jointFile.txt";
        


        //20230818
        bool isFinish = false;
        bool isIK = false;
        bool isFK = false;


        //txt文件数据
        List<string> txtList=new List<string>();

        #endregion

        #region 自定义函数


        private void setTag(Control cons)
        {
            foreach (Control con in cons.Controls)
            {
                con.Tag = con.Width + ";" + con.Height + ";" + con.Left + ";" + con.Top + ";" + con.Font.Size;
                if (con.Controls.Count > 0)
                {
                    setTag(con);
                }
            }
        }

        private void setControls(float newx, float newy, Control cons)
        {
            //遍历窗体中的控件，重新设置控件的值
            foreach (Control con in cons.Controls)
            {
                //获取控件的Tag属性值，并分割后存储字符串数组
                if (con.Tag != null)
                {
                    string[] mytag = con.Tag.ToString().Split(new char[] { ';' });
                    //根据窗体缩放的比例确定控件的值
                    con.Width = Convert.ToInt32(System.Convert.ToSingle(mytag[0]) * newx);//宽度
                    con.Height = Convert.ToInt32(System.Convert.ToSingle(mytag[1]) * newy);//高度
                    con.Left = Convert.ToInt32(System.Convert.ToSingle(mytag[2]) * newx);//左边距
                    con.Top = Convert.ToInt32(System.Convert.ToSingle(mytag[3]) * newy);//顶边距
                    Single currentSize = System.Convert.ToSingle(mytag[4]) * newy;//字体大小
                    con.Font = new Font(con.Font.Name, currentSize, con.Font.Style, con.Font.Unit);
                    if (con.Controls.Count > 0)
                    {
                        setControls(newx, newy, con);
                    }
                }
            }
        }




            /// <summary>
            /// 初始化运动速度参数
            /// </summary>
            static void Init_MoveParameter()
        {
            //初始化运动属性
          //  AuboRobot.rs_init_global_move_profile(rshd);

            //设置关节运动最大加速度
            double[] jointMaxAcc = new double[6];
            jointMaxAcc[0] = 50 * M_PI / 180;
            jointMaxAcc[1] = 50 * M_PI / 180;
            jointMaxAcc[2] = 50 * M_PI / 180;
            jointMaxAcc[3] = 50 * M_PI / 180;
            jointMaxAcc[4] = 50 * M_PI / 180;
            jointMaxAcc[5] = 50 * M_PI / 180;
            AuboRobot.rs_set_global_joint_maxacc(rshd, jointMaxAcc);

            //设置关节运动最大速度
            double[] jointMaxVelc = new double[6];
            jointMaxVelc[0] = 50 * M_PI / 180;
            jointMaxVelc[1] = 50 * M_PI / 180;
            jointMaxVelc[2] = 50 * M_PI / 180;
            jointMaxVelc[3] = 50 * M_PI / 180;
            jointMaxVelc[4] = 50 * M_PI / 180;
            jointMaxVelc[5] = 50 * M_PI / 180;
            AuboRobot.rs_set_global_joint_maxvelc(rshd, jointMaxVelc);

            //设置末端型运动最大加速度

            AuboRobot.rs_set_global_end_max_line_acc(rshd, 0.2);

            //设置末端型运动最大速度
            AuboRobot.rs_set_global_end_max_line_velc(rshd, 0.2);
        }



        static void Init_MoveParameter_Speed(int value)
        {
            double proportion = value * 0.01;
            //初始化运动属性
            //AuboRobot.rs_init_global_move_profile(rshd);

            //设置关节运动最大加速度
            double[] jointMaxAcc = new double[6];
            jointMaxAcc[0] = proportion * 50 * M_PI / 180;
            jointMaxAcc[1] = proportion * 50 * M_PI / 180;
            jointMaxAcc[2] = proportion * 50 * M_PI / 180;
            jointMaxAcc[3] = proportion * 50 * M_PI / 180;
            jointMaxAcc[4] = proportion * 50 * M_PI / 180;
            jointMaxAcc[5] = proportion * 50 * M_PI / 180;
            AuboRobot.rs_set_global_joint_maxacc(rshd, jointMaxAcc);

            //设置关节运动最大速度
            double[] jointMaxVelc = new double[6];
            jointMaxVelc[0] = proportion * 50 * M_PI / 180;
            jointMaxVelc[1] = proportion * 50 * M_PI / 180;
            jointMaxVelc[2] = proportion * 50 * M_PI / 180;
            jointMaxVelc[3] = proportion * 50 * M_PI / 180;
            jointMaxVelc[4] = proportion * 50 * M_PI / 180;
            jointMaxVelc[5] = proportion * 50 * M_PI / 180;
            AuboRobot.rs_set_global_joint_maxvelc(rshd, jointMaxVelc);

            //设置末端型运动最大加速度

            AuboRobot.rs_set_global_end_max_line_acc(rshd, proportion*1);

            //设置末端型运动最大速度
            AuboRobot.rs_set_global_end_max_line_velc(rshd, proportion*1);
        }
        /// <summary>
        /// 轴动
        /// </summary>
        /// <param name="j1"></param>
        /// <param name="j2"></param>
        /// <param name="j3"></param>
        /// <param name="j4"></param>
        /// <param name="j5"></param>
        /// <param name="j6"></param>
        /// <returns></returns>
        static bool Move_Joint(double j1, double j2, double j3, double j4, double j5, double j6)
        {

            Init_MoveParameter();
            //关节角
            double[] initPos = {
                j1/ 180 * M_PI,
                j2/ 180 * M_PI,
                j3/ 180 * M_PI,
                j4/ 180 * M_PI,
                j5/ 180 * M_PI,
                j6/ 180 * M_PI
            };

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos, false);//非阻塞，阻塞运动
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("关节运动1成功!");

            }
            else
            {

                Console.Error.WriteLine("关节运动1失败!错误码：{0}", ret);
            }
            return true;
        }
        /// <summary>
        /// 直线运动
        /// </summary>
        /// <param name="j1"></param>
        /// <param name="j2"></param>
        /// <param name="j3"></param>
        /// <param name="j4"></param>
        /// <param name="j5"></param>
        /// <param name="j6"></param>
        /// <returns></returns>
        static bool Move_Line(double j1, double j2, double j3, double j4, double j5, double j6)
        {

            return true;
        }
        #endregion

        //初始化机械臂控制库
        [DllImport(service_interface_dll, EntryPoint = "rs_initialize", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_initialize();

        //反初始化机械臂控制库
        [DllImport(service_interface_dll, EntryPoint = "rs_uninitialize", CharSet =
       CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_uninitialize();
        //创建机械臂控制上下文句柄
        [DllImport(service_interface_dll, EntryPoint = "rs_create_context", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_create_context(ref UInt16 rshd);
        //注销机械臂控制上下文句柄
        [DllImport(service_interface_dll, EntryPoint = "rs_destory_context", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_destory_context(UInt16 rshd);
        //链接机械臂服务器
        [DllImport(service_interface_dll, EntryPoint = "rs_login", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_login(UInt16 rshd, [MarshalAs(UnmanagedType.LPStr)] string addr, int port);

        //断开机械臂服务器链接
        [DllImport(service_interface_dll, EntryPoint = "rs_logout", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        public static extern int rs_logout(UInt16 rshd);
        //关节运动—rs_move_joint


        static void initPosOriMove(UInt16 rshd)
        {
            //关节角
            double[] initPos0 = {
                -0.000172/180*M_PI,
                -8.423339/180*M_PI,
                -81.027975/180*M_PI,
                17.378440/180*M_PI,
                -89.999982/180*M_PI,
                -0.000458/180*M_PI
            };

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos0, false);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("运动初始位姿过程中.....!");

            }
            else
            {

                Console.Error.WriteLine("运动点位1运动失败!：{0}", ret);
            }


        }

        static void zeroPosOriMove(UInt16 rshd)
        {
            //关节角
            double[] initPos0 = {
                0.00,
                0.00,
                0.00,
                0.00,
                0.00,
                0.00
            };

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos0, false);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("运动初始位姿过程中。。。!");

            }
            else
            {

                Console.Error.WriteLine("运动点位1运动失败!：{0}", ret);
            }


        }
        static void jointMoveExample(UInt16 rshd)
        {
            Init_MoveParameter();
            //关节角
            double[] initPos0 = {
                -0.000172,
                -8.423339,
                -81.027975,
                17.378440,
                -89.999982,
                -0.000458
            };

            double[] initPos1 = {
               -40.544980,
                1.31057,
                2.32373,
                -0.533722,
                -1.55998,
                0.146084
            };

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos0, false);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("运动点位1运动成功!");

            }
            else
            {

                Console.Error.WriteLine("运动点位1运动失败!：{0}", ret);
            }

            //ret = AuboRobot.rs_move_joint(rshd, initPos1, true);
            //if (ret == RS_SUCC)
            //{
            //    Console.Out.WriteLine("运动点位2运动成功");

            //}
            //else
            //{

            //    Console.Error.WriteLine("运动点位2运动失败：{0}", ret);
            //}

        }

        //圆运动
        static void moveCircleExample(UInt16 rshd)
        {
            Init_MoveParameter();
            //准备点
            double[] jointAngle = new double[6];
            jointAngle[0] = -0.000003;
            jointAngle[1] = -0.127267;
            jointAngle[2] = -1.321122;
            jointAngle[3] = 0.376934;
            jointAngle[4] = -1.570796;
            jointAngle[5] = -0.000008;
            //关节运动到准备点
            int ret = AuboRobot.rs_move_joint(rshd, jointAngle, true);
            if (ret != RS_SUCC)
            {
                Console.Error.WriteLine("关节运动到准备点失败。 错误号: {0}", ret);
            }
            //添加圆轨迹路点
            jointAngle[0] = -0.000003;
            jointAngle[1] = -0.127267;
            jointAngle[2] = -1.321122;
            jointAngle[3] = 0.376934;
            jointAngle[4] = -1.570796;
            jointAngle[5] = -0.000008;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            jointAngle[0] = -0.211675;
            jointAngle[1] = -0.325189;
            jointAngle[2] = -1.466753;
            jointAngle[3] = 0.429232;
            jointAngle[4] = -1.570794;
            jointAngle[5] = -0.211680;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            jointAngle[0] = -0.037186;
            jointAngle[1] = -0.224307;
            jointAngle[2] = -1.398285;
            jointAngle[3] = 0.396819;
            jointAngle[4] = -1.570796;
            jointAngle[5] = -0.037191;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            //设置圆的圈数
            AuboRobot.rs_set_circular_loop_times(rshd, 3);
            //开始轨迹运动
            ret = AuboRobot.rs_move_track(rshd, AuboRobot.ARC_CIR, true);
            if (RS_SUCC != ret)
            {
                Console.Error.WriteLine("圆轨迹运动失败。 错误号:{0}", ret);
            }
            else
            {
                Console.Out.WriteLine("圆轨迹运动成功。");
            }


        }

        //圆弧运动
        static void moveArcExample(UInt16 rshd)
        {
            //Init_MoveParameter();
            ////准备点
            //double[] jointAngle = new double[6];
            //jointAngle[0] = -0.000003;
            //jointAngle[1] = -0.127267;
            //jointAngle[2] = -1.321122;
            //jointAngle[3] = 0.376934;
            //jointAngle[4] = -1.570796;
            //jointAngle[5] = -0.000008;
            ////关节运动到准备点
            //int ret = AuboRobot.rs_move_joint(rshd, jointAngle, true);
            //if (ret != RS_SUCC)
            //{
            //    Console.Error.WriteLine("关节运动到准备点失败。 错误号:{0}", ret);
            //}
            ////添加圆弧轨迹路点
            //jointAngle[0] = -0.000003;
            //jointAngle[1] = -0.127267;
            //jointAngle[2] = -1.321122;
            //jointAngle[3] = 0.376934;
            //jointAngle[4] = -1.570796;
            //jointAngle[5] = -0.000008;
            //AuboRobot.rs_add_waypoint(rshd, jointAngle);
            //jointAngle[0] = 0.200000;
            //jointAngle[1] = -0.127267;
            //jointAngle[2] = -1.321122;
            //jointAngle[3] = 0.376934;
            //jointAngle[4] = -1.570794;
            //jointAngle[5] = -0.000008;
            //AuboRobot.rs_add_waypoint(rshd, jointAngle);
            //jointAngle[0] = 0.600000;
            //jointAngle[1] = -0.127267;
            //jointAngle[2] = -1.321122;
            //jointAngle[3] = 0.376934;
            //jointAngle[4] = -1.570796;
            //jointAngle[5] = -0.000008;
            //AuboRobot.rs_add_waypoint(rshd, jointAngle);
            ////设置圆弧
            //AuboRobot.rs_set_circular_loop_times(rshd, 0);
            ////开始轨迹运动
            //ret = AuboRobot.rs_move_track(rshd, AuboRobot.ARC_CIR, true);
            //if (RS_SUCC != ret)
            //{
            //    Console.Error.WriteLine("圆弧轨迹运动失败。 错误号:{0}", ret);
            //}
            //else
            //{
            //    Console.Out.WriteLine("圆弧轨迹运动成功。");
            //}

            Init_MoveParameter();
            //准备点
            double[] jointAngle = new double[6];
            jointAngle[0] = -0.508439;
            jointAngle[1] = 1.156374;
            jointAngle[2] = -1.343661;
            jointAngle[3] = -0.842751;
            jointAngle[4] = -1.882986;
            jointAngle[5] = 0.680005;
            //关节运动到准备点
            int ret = AuboRobot.rs_move_joint(rshd, jointAngle, true);
            if (ret != RS_SUCC)
            {
                Console.Error.WriteLine("关节运动到准备点失败。 错误号:{0}", ret);
            }
            //添加圆弧轨迹路点
            jointAngle[0] = -0.508439;
            jointAngle[1] = 1.156374;
            jointAngle[2] = -1.343661;
            jointAngle[3] = -0.842751;
            jointAngle[4] = -1.882986;
            jointAngle[5] = 0.680005;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            jointAngle[0] = -0.826750;
            jointAngle[1] = 1.075013;
            jointAngle[2] = -2.144325;
            jointAngle[3] = -2.016737;
            jointAngle[4] = -1.481010;
            jointAngle[5] = 0.428550;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            jointAngle[0] = -1.126905;
            jointAngle[1] = 1.029125;
            jointAngle[2] = -1.733200;
            jointAngle[3] = -1.096205;
            jointAngle[4] = -1.153063;
            jointAngle[5] = 0.166477;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            //设置圆弧
            AuboRobot.rs_set_circular_loop_times(rshd, 0);
            //开始轨迹运动
            ret = AuboRobot.rs_move_track(rshd, 2, true);
            if (RS_SUCC != ret)
            {
                Console.Error.WriteLine("圆弧轨迹运动失败。 错误号:{0}", ret);
            }
            else
            {
                Console.Out.WriteLine("圆弧轨迹运动成功。");
            }

        }

        //MoveP
        static void movePExample(UInt16 rshd)
        {
            Init_MoveParameter();
            //准备点
            double[] jointAngle = new double[6];
            jointAngle[0] = -0.000003;
            jointAngle[1] = -0.127267;
            jointAngle[2] = -1.321122;
            jointAngle[3] = 0.376934;
            jointAngle[4] = -1.570796;
            jointAngle[5] = -0.000008;
            //关节运动到准备点
            int ret = AuboRobot.rs_move_joint(rshd, jointAngle, true);
            if (ret != RS_SUCC)
            {
                Console.Error.WriteLine("关节运动到准备点失败。 错误号:{0}", ret);
            }
            //添加 MOVEP 轨迹路点
            jointAngle[0] = -0.000003;
            jointAngle[1] = -0.127267;
            jointAngle[2] = -1.321122;
            jointAngle[3] = 0.376934;
            jointAngle[4] = -1.570796;
            jointAngle[5] = -0.000008;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            jointAngle[0] = 0.200000;
            jointAngle[1] = -0.127267;
            jointAngle[2] = -1.321122;
            jointAngle[3] = 0.376934;
            jointAngle[4] = -1.570794;
            jointAngle[5] = -0.000008;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            jointAngle[0] = 0.600000;
            jointAngle[1] = -0.127267;
            jointAngle[2] = -1.321122;
            jointAngle[3] = 0.376934;
            jointAngle[4] = -1.570796;
            jointAngle[5] = -0.000008;
            AuboRobot.rs_add_waypoint(rshd, jointAngle);
            //设置交融半径
            AuboRobot.rs_set_blend_radius(rshd, 0.03);
            //开始轨迹运动
            ret = AuboRobot.rs_move_track(rshd, AuboRobot.CARTESIAN_MOVEP, true);
            if (RS_SUCC != ret)
            {
                Console.Error.WriteLine("MOVEP 轨迹运动失败。错误号:{0}", ret);
            }
            else
            {
                Console.Out.WriteLine("MOVEP 轨迹运动成功。");
            }

        }



        void ScrewLockExample(UInt16 rshd)
        {
            Init_MoveParameter();
            //接收外部DI信号触发螺丝锁附
            //获取DI状态
            bool isWait = true;
            while (isWait)
            {
                if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_00, ref io_status)))
                {
                    if (io_status == 1)
                    {
                        btn_U_DI_00.BackColor = Color.Green;
                        isWait = false;

                    }
                    else
                    {
                        btn_U_DI_00.BackColor = Color.White;
                    }
                }
                else
                {
                    //获取IO状态失败
                    Console.WriteLine("获取IO状态失败");
                }
                Thread.Sleep(500);
            }

            //取螺丝
            //姿态同步到取料点
            double[] jointAngle = new double[6];
            jointAngle[0] = -0.000003;
            jointAngle[1] = -0.127267;
            jointAngle[2] = -1.321122;
            jointAngle[3] = 0.376934;
            jointAngle[4] = -1.570796;
            jointAngle[5] = -0.000008;
            //关节运动到准备点
            int ret = AuboRobot.rs_move_joint(rshd, jointAngle, true);
            if (ret != RS_SUCC)
            {
                Console.Error.WriteLine("关节运动到准备点失败。 错误号:{0}", ret);
            }

            //离取螺丝位置差20mm






            //取螺丝位置(直线运动)

            //姿态同步到取螺丝位置（直线运动）

            //姿态同步到锁螺丝位置

            //过渡点1

            //过渡点2

            //离锁螺丝位置差20mm

            //锁螺丝位置(直线运动)

            //姿态同步到锁螺丝位置(直线运动）




        }

        //相对偏移测试
        static void offsetMoveTest(UInt16 rshd)
        {
            //初始化运动参数
            Init_MoveParameter();

            double[] jointAngle = new double[6];
            jointAngle[0] = 0.0 * M_PI / 180;
            jointAngle[1] = 0.0 * M_PI / 180;
            jointAngle[2] = 79.161394 * M_PI / 180;
            jointAngle[3] = -13.118614 * M_PI / 180;
            jointAngle[4] = 87.852939 * M_PI / 180;
            jointAngle[5] = -20.026135 * M_PI / 180;

            // //工具坐标系
            // AuboRobot.CoordCalibrate user_coord = new AuboRobot.CoordCalibrate();

            // IntPtr pt_user_coord = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(AuboRobot.CoordCalibrate)));
            // user_coord = (AuboRobot.CoordCalibrate)Marshal.PtrToStructure(pt_user_coord, typeof(AuboRobot.CoordCalibrate));
            // user_coord.coordType = 1;

            // //坐标系的工具参数
            // AuboRobot.ToolInEndDesc tooluser_coord = new AuboRobot.ToolInEndDesc();
            // //
            // tooluser_coord.cartPos.x = 0.05;
            // tooluser_coord.cartPos.y = 0.05;
            // tooluser_coord.cartPos.z = 0.1;
            // tooluser_coord.orientation.w = 0;
            // tooluser_coord.orientation.x = 0;
            // tooluser_coord.orientation.y = 0;
            // tooluser_coord.orientation.z = 0;
            // user_coord.toolDesc = tooluser_coord;

            // //偏移量
            // AuboRobot.MoveRelative relative = new AuboRobot.MoveRelative();
            // IntPtr pt_relative = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(AuboRobot.
            //MoveRelative)));
            // relative = (AuboRobot.MoveRelative)Marshal.PtrToStructure(pt_relative, typeof
            //(AuboRobot.MoveRelative));
            // relative.enable = 1;
            // relative.orientation.w = 1;
            // relative.orientation.x = 0;
            // relative.orientation.y = 0;
            // relative.orientation.z = 0;
            // relative.pos[0] =0;
            // relative.pos[1] = 0;
            // relative.pos[2] = 0.1f;

            //double[] target0 = new double[6]; //注意这个里面的值是弧度！
            //target0[0] = 0.0 / 180 * M_PI;
            //target0[1] = 0.0 / 180 * M_PI;
            //target0[2] = 79.161394 / 180 * M_PI;
            //target0[3] = -13.118614 / 180 * M_PI;
            //target0[4] = 87.852939 / 180 * M_PI;
            //target0[5] = -20.026135 / 180 * M_PI;

            // ////设置工具参数
            // //AuboRobot.ToolInEndDesc tool = new AuboRobot.ToolInEndDesc();

            // //tool.cartPos.x = 0.2;
            // //tool.cartPos.y = 0.1;
            // //tool.cartPos.z = 0.1;
            // //tool.orientation.w = 1;
            // //tool.orientation.x = 1;
            // //tool.orientation.y = 1;
            // //tool.orientation.z = 1;
            // //AuboRobot.rs_set_tool_kinematics_param(rshd, ref tool);
            // //移动到坐标系原点
            // //AuboRobot.rs_move_joint(rshd, target0, true);

            // //相对坐标系原点沿 z 轴正向运动
            // int ret=AuboRobot.rs_move_joint(rshd, target0, true);
            // if (ret == RS_SUCC)
            // {
            //     Console.Out.WriteLine("初始运动成功!");

            // }
            // else
            // {

            //     Console.Error.WriteLine("初始运动失败!错误码：{0}", ret);
            // }

            // int retOffset=AuboRobot.rs_set_relative_offset_on_user(rshd, ref relative, ref user_coord);
            // Console.Out.WriteLine("retOffse:"+retOffset);
            // ret =AuboRobot.rs_move_joint(rshd, target0, true);
            // if (ret == RS_SUCC)
            // {
            //     Console.Out.WriteLine("相对偏移运动成功!");

            // }
            // else
            // {

            //     Console.Error.WriteLine("相对偏移运动失败!错误码：{0}", ret);
            // }
            // //AuboRobot.rs_move_line(rshd, target0, true);

            //设置偏移
            //AuboRobot.MoveRelative relativeOnEnd;
            //relativeOnEnd.enable = 1;
            //float[] posOffset = new float[3] { 0, 0, 0 };
            //relativeOnEnd.pos = posOffset;
            //relativeOnEnd.pos[0] = 0.0f;
            //relativeOnEnd.pos[1] = 0.0f;
            //relativeOnEnd.pos[2] = 0.1f;

            //AuboRobot.Rpy rpyRelative;
            //AuboRobot.Ori oriRelative;
            //oriRelative.w = 0;
            //oriRelative.x = 0;
            //oriRelative.y = 0;
            //oriRelative.z = 0;


            //rpyRelative.rx = 0;
            //rpyRelative.ry = 0;
            //rpyRelative.rz = 0;
            //AuboRobot.rs_rpy_to_quaternion(rshd, ref rpyRelative, ref oriRelative);
            //relativeOnEnd.orientation = oriRelative;
            ////设置工具
            //AuboRobot.ToolInEndDesc toolEnd = new AuboRobot.ToolInEndDesc();

            //toolEnd.cartPos.x = 0.1;
            //toolEnd.cartPos.y = 0.05;
            //toolEnd.cartPos.z = 0.05;
            //toolEnd.orientation.w = 0;
            //toolEnd.orientation.x = 0;
            //toolEnd.orientation.y = 0;
            //toolEnd.orientation.z = 0;
            //AuboRobot.rs_set_tool_kinematics_param(rshd, ref toolEnd);
            ////设置工具坐标系
            //AuboRobot.CoordCalibrate endCoord=new AuboRobot.CoordCalibrate();
            //EndCoordinate
            //endCoord.coordType = AuboRobot.EndCoordinate;
            ////endCoord.toolDesc = toolEnd;

            ////AuboRobot.JointRadian[] jointPara = new AuboRobot.JointRadian[]{ };
            ////endCoord.jointPara = jointPara;
            ////endCoord.methods = 0;

            //int ret = AuboRobot.rs_move_joint(rshd, target0, true);
            //if (ret == RS_SUCC)
            //{
            //    Console.Out.WriteLine("初始运动成功!");

            //}
            //else
            //{

            //    Console.Error.WriteLine("初始运动失败!错误码：{0}", ret);
            //}
            //偏移：工具在工具坐标系下沿着Z轴下移动0.1m
            //int retOffset=AuboRobot.rs_set_relative_offset_on_user(rshd,ref relativeOnEnd,ref endCoord);
            //Console.WriteLine("retOffset:"+ retOffset);
            //ret = AuboRobot.rs_move_joint(rshd, target0, true);
            //if (ret == RS_SUCC)
            //{
            //    Console.Out.WriteLine("相对偏移运动成功!");


            //}
            //else
            //{

            //    Console.Error.WriteLine("相对偏移运动失败!错误码：{0}", ret);
            //}
            //AuboRobot.rs_move_line(rshd, target0, true);


            //基于base坐标系的相对偏移
            //设置偏移
            //AuboRobot.MoveRelative relativeOnBase =new AuboRobot.MoveRelative();

            //float[] posOffset = new float[3] { 0, 0, 0 };
            ////relativeOnEnd.pos = posOffset;
            ////relativeOnEnd.pos[0] = 0.0f;
            ////relativeOnEnd.pos[1] = 0.0f;
            ////relativeOnEnd.pos[2] = 0.1f;
            //relativeOnBase.enable = 1;
            //relativeOnBase.pos = posOffset;
            //relativeOnBase.pos[0] = 1.1f;
            //relativeOnBase.pos[1] = 0.0f;
            //relativeOnBase.pos[2] = 0.0f;
            //AuboRobot.Rpy rpyRelative;
            //AuboRobot.Ori oriRelative=new AuboRobot.Ori();
            //rpyRelative.rx = 0.0;
            //rpyRelative.ry = 0.0;
            //rpyRelative.rz = 0.0;

            //AuboRobot.rs_rpy_to_quaternion(rshd, ref rpyRelative, ref oriRelative);
            //relativeOnBase.orientation = oriRelative;
            ////偏移：法兰盘中心在基坐标系下沿X轴偏移0.1m
            //AuboRobot.rs_set_relative_offset_on_base(rshd, ref relativeOnBase);
            //int retMoveOffset=AuboRobot.rs_move_joint(rshd, jointAngle, true);
            //Console.WriteLine("retMoveOffset:" + retMoveOffset);

            //基于用户坐标系的相对偏移
            //设置偏移
            //AuboRobot.MoveRelative relativeOnUser=new AuboRobot.MoveRelative();
            //relativeOnUser.enable = 1;
            //relativeOnUser.pos = new float[3] { 0, 0, 0 };
            //relativeOnUser.pos[0] = 0.0f;
            //relativeOnUser.pos[1] = 0.0f;
            //relativeOnUser.pos[2] = 0.1f;
            //AuboRobot.Rpy rpyRelative;
            //AuboRobot.Ori oriRelative=new AuboRobot.Ori();

            //rpyRelative.rx = 0.0 / 180 * M_PI; //要加一位小数点
            //rpyRelative.ry = 0.0 / 180 * M_PI;
            //rpyRelative.rz = 0.0 / 180 * M_PI;
            //AuboRobot.rs_rpy_to_quaternion(rshd, ref rpyRelative, ref oriRelative);
            //relativeOnUser.orientation = oriRelative;

            ////设置用户坐标系
            //AuboRobot.CoordCalibrate userCoord=new AuboRobot.CoordCalibrate();
            //userCoord.coordType = AuboRobot.WorldCoordinate;
            //userCoord.methods = AuboRobot.Origin_AnyPointOnPositiveXAxis_AnyPointOnFirstQuadrantOfXOYPlane;

            //AuboRobot.JointRadian jointPara1 = new AuboRobot.JointRadian();
            //AuboRobot.JointRadian jointPara2 = new AuboRobot.JointRadian();
            //AuboRobot.JointRadian jointPara3 = new AuboRobot.JointRadian();
            //jointPara1.jointRadian = new double[6] {0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
            //jointPara2.jointRadian = new double[6] { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 };
            //jointPara3.jointRadian = new double[6] { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 };

            //jointPara1.jointRadian[0] = -0.000172 * M_PI / 180;
            //jointPara1.jointRadian[1] = -7.291862 * M_PI / 180;
            //jointPara1.jointRadian[2] = -75.694718 * M_PI / 180;
            //jointPara1.jointRadian[3] = 21.596727 * M_PI / 180;
            //jointPara1.jointRadian[4] = -89.999982 * M_PI / 180;
            //jointPara1.jointRadian[5] = -0.000458 * M_PI / 180;

            //jointPara2.jointRadian[0] = -0.000286 * M_PI / 180;
            //jointPara2.jointRadian[1] = -26.232586 * M_PI / 180;
            //jointPara2.jointRadian[2] = -88.129330 * M_PI / 180;
            //jointPara2.jointRadian[3] = 28.103237 * M_PI / 180;
            //jointPara2.jointRadian[4] = -89.999982 * M_PI / 180;
            //jointPara2.jointRadian[5] = -0.000573 * M_PI / 180;

            //jointPara3.jointRadian[0] = -9.334915 * M_PI / 180;
            //jointPara3.jointRadian[1] = -28.274149 * M_PI / 180;
            //jointPara3.jointRadian[2] = -89.014666 * M_PI / 180;
            //jointPara3.jointRadian[3] = 29.259523 * M_PI / 180;
            //jointPara3.jointRadian[4] = -89.999982 * M_PI / 180;
            //jointPara3.jointRadian[5] = -9.335201 * M_PI / 180;

            //userCoord.jointPara = new AuboRobot.JointRadian[3] { jointPara1, jointPara2 , jointPara3 };

            //AuboRobot.ToolInEndDesc toolUserCoord=new AuboRobot.ToolInEndDesc() ;
            //toolUserCoord.cartPos.x = 0.0;
            //toolUserCoord.cartPos.y = 0.0;
            //toolUserCoord.cartPos.z = 0.0;
            //toolUserCoord.orientation.w = 1;
            //toolUserCoord.orientation.x = 0;
            //toolUserCoord.orientation.y = 0;
            //toolUserCoord.orientation.z = 0;
            //userCoord.toolDesc = toolUserCoord;

            //AuboRobot.rs_move_joint(rshd, jointAngle, true);

            //Thread.Sleep(2000);

            ////偏移：法兰盘中心在用户坐标系下沿 X 轴姿态偏移 10 度
            //AuboRobot.rs_set_relative_offset_on_user(rshd, ref relativeOnUser, ref userCoord);
            ////关节运动
            //AuboRobot.rs_move_joint(rshd, jointAngle, true);



            //基于工具坐标系的相对偏移
            //设置偏移
            AuboRobot.MoveRelative relativeOnUser = new AuboRobot.MoveRelative();
            relativeOnUser.enable = 1;
            relativeOnUser.pos = new float[3] { 0, 0, 0 };
            relativeOnUser.pos[0] = 0.0f;
            relativeOnUser.pos[1] = 0.0f;
            relativeOnUser.pos[2] = 0.1f;
            AuboRobot.Rpy rpyRelative;
            AuboRobot.Ori oriRelative = new AuboRobot.Ori();

            rpyRelative.rx = 0.0 / 180 * M_PI; //要加一位小数点
            rpyRelative.ry = 0.0 / 180 * M_PI;
            rpyRelative.rz = 0.0 / 180 * M_PI;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpyRelative, ref oriRelative);
            relativeOnUser.orientation = oriRelative;
            //设置工具
            AuboRobot.ToolInEndDesc toolEnd = new AuboRobot.ToolInEndDesc();



            toolEnd.cartPos.x = 0.05;
            toolEnd.cartPos.y = 0.05;
            toolEnd.cartPos.z = 0.1;
            toolEnd.orientation.w = 0;
            toolEnd.orientation.x = 0;
            toolEnd.orientation.y = 0;
            toolEnd.orientation.z = 0;
            AuboRobot.rs_set_tool_kinematics_param(rshd, ref toolEnd);
            //设置工具坐标系
            AuboRobot.CoordCalibrate endCoord = new AuboRobot.CoordCalibrate();

            endCoord.coordType = AuboRobot.EndCoordinate;


            int ret = AuboRobot.rs_move_joint(rshd, jointAngle, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("初始运动成功!");

            }
            else
            {

                Console.Error.WriteLine("初始运动失败!错误码：{0}", ret);
            }
            Thread.Sleep(2000);
            //偏移：工具在工具坐标系下沿着Z轴下移动0.1m
            int retOffset = AuboRobot.rs_set_relative_offset_on_user(rshd, ref relativeOnUser, ref endCoord);
            Console.WriteLine("retOffset:" + retOffset);
            ret = AuboRobot.rs_move_joint(rshd, jointAngle, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("相对偏移运动成功!");


            }
            else
            {

                Console.Error.WriteLine("相对偏移运动失败!错误码：{0}", ret);
            }

        }

        static void jointMoveExample1(UInt16 rshd)
        {
            Init_MoveParameter();
            //关节角
            double[] initPos = {
                -170.00 / 180 * M_PI,
                0.00/ 180 * M_PI,
                45.00/ 180 * M_PI,
                -45.00/ 180 * M_PI,
                90.00/ 180 * M_PI,
                90.00/ 180 * M_PI
            };

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("关节运动2成功!");

            }
            else
            {

                Console.Error.WriteLine("关节运动2失败!错误码：{0}", ret);
            }

        }

        ////设置基于基座标系运动偏移量
        //[DllImport(service_interface_dll, EntryPoint = "rs_set_relative_offset_on_base", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
        //public static extern int rs_set_relative_offset_on_base(UInt16 rshd, ref MoveRelative relative);
        static void rs_set_relative_offset_on_base_Example(UInt16 rshd)
        {
            Init_MoveParameter();
            //关节角
            double[] initPos = {
                -90.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                45.00/ 180 * M_PI,
                -45.00/ 180 * M_PI,
                90.00/ 180 * M_PI,
                90.00/ 180 * M_PI
            };

            //     //四元素转欧拉角
            //[DllImport("libserviceinterface.dll", EntryPoint = "rs_rpy_to_quaternion", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
            //public static extern int rs_rpy_to_quaternion(UInt16 rshd, ref Rpy rpy, ref Ori ori);

            ////欧拉角转四元素
            //[DllImport("libserviceinterface.dll", EntryPoint = "rs_quaternion_to_rpy", CharSet = CharSet.Auto, CallingConvention = CallingConvention.Cdecl)]
            //public static extern int rs_quaternion_to_rpy(UInt16 rshd, ref Ori ori, ref Rpy rpy);

            AuboRobot.MoveRelative relative;
            float[] arrayPos = new float[3] { 0.1f, 0, 0 };

            double[] arrayOri = new double[4] { 1.0, 0.0, 0.0, 0.0 };

            relative.enable = 1;
            relative.pos = arrayPos;
            relative.orientation.w = arrayOri[0];
            relative.orientation.x = arrayOri[1];
            relative.orientation.y = arrayOri[2];
            relative.orientation.z = arrayOri[3];



            int retOffset = AuboRobot.rs_set_relative_offset_on_base(rshd, ref relative);
            if (retOffset == RS_SUCC)
            {
                Console.Out.WriteLine("设置相对偏移参数成功!");
            }
            else
            {
                Console.Out.WriteLine("设置相对偏移参数失败!");
            }
            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("关节运动1成功!");

            }
            else
            {

                Console.Error.WriteLine("关节运动1失败!错误码：{0}", ret);
            }

        }

       

        //设置和获取机械臂相关参数
        //获取当前路点信息
        static void getCurrentWaypoint(UInt16 rshd)
        {
            AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();

            int ret = AuboRobot.rs_get_current_waypoint(rshd, ref waypoint);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("获取当前路点信息成功！");
                AuboRobot.PrintWaypoint(waypoint);
            }
            else
            {
                Console.Error.WriteLine("获取当前路点信息失败！错误码：{0}", ret);
            }

        }

        /// <summary>
        /// 法兰盘在base坐标系下的坐标
        /// </summary>
        /// <param name="rshd"></param>
        void flange_center_on_base(UInt16 rshd)
        {
            getCurrentWaypoint(rshd);
            thisWaypoint = thisCurrentWaypoint(rshd);
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.rs_quaternion_to_rpy(rshd, ref thisWaypoint.orientation, ref rpy);
            tb_x.Text = thisWaypoint.cartPos.x.ToString();
            tb_y.Text = thisWaypoint.cartPos.y.ToString();
            tb_z.Text = thisWaypoint.cartPos.z.ToString();
            //rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            tb_rx.Text = (rpy.rx * 180 / M_PI).ToString();
            tb_ry.Text = (rpy.ry * 180 / M_PI).ToString();
            tb_rz.Text = (rpy.rz * 180 / M_PI).ToString();
            tb_j1.Text = (thisWaypoint.jointpos[0] * 180 / M_PI).ToString();
            tb_j2.Text = (thisWaypoint.jointpos[1] * 180 / M_PI).ToString();
            tb_j3.Text = (thisWaypoint.jointpos[2] * 180 / M_PI).ToString();
            tb_j4.Text = (thisWaypoint.jointpos[3] * 180 / M_PI).ToString();
            tb_j5.Text = (thisWaypoint.jointpos[4] * 180 / M_PI).ToString();
            tb_j6.Text = (thisWaypoint.jointpos[5] * 180 / M_PI).ToString();
        }

        /// <summary>
        /// 法兰盘在base坐标系下的坐标
        /// </summary>
        /// <param name="rshd"></param>
        void flange_center_on_base_manual(UInt16 rshd)
        {
            getCurrentWaypoint(rshd);
            thisWaypoint = thisCurrentWaypoint(rshd);
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.rs_quaternion_to_rpy(rshd, ref thisWaypoint.orientation, ref rpy);
            posX_tb.Text = thisWaypoint.cartPos.x.ToString("0.000000");
            posY_tb.Text = thisWaypoint.cartPos.y.ToString("0.000000");
            posZ_tb.Text = thisWaypoint.cartPos.z.ToString("0.000000");
            //rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            RX_tb.Text = (rpy.rx * 180 / M_PI).ToString("0.000000");
            RY_tb.Text = (rpy.ry * 180 / M_PI).ToString("0.000000");
            RZ_tb.Text = (rpy.rz * 180 / M_PI).ToString("0.000000");
            J1_tb.Text = (thisWaypoint.jointpos[0] * 180 / M_PI).ToString("0.000000");
            J2_tb.Text = (thisWaypoint.jointpos[1] * 180 / M_PI).ToString("0.000000");
            J3_tb.Text = (thisWaypoint.jointpos[2] * 180 / M_PI).ToString("0.000000");
            J4_tb.Text = (thisWaypoint.jointpos[3] * 180 / M_PI).ToString("0.000000");
            J5_tb.Text = (thisWaypoint.jointpos[4] * 180 / M_PI).ToString("0.000000");
            J6_tb.Text = (thisWaypoint.jointpos[5] * 180 / M_PI).ToString("0.000000");
        }

        /// <summary>
        /// tcp在base坐标系下的坐标
        /// </summary>
        /// <param name="rshd"></param>
        void tool_on_base(UInt16 rshd)
        {
            getCurrentWaypoint(rshd);
            thisWaypoint = thisCurrentWaypoint(rshd);
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.rs_quaternion_to_rpy(rshd, ref thisWaypoint.orientation, ref rpy);
            //法兰盘中心在基坐标系下的位置
            AuboRobot.Pos pos_onbase = new AuboRobot.Pos();
            pos_onbase.x = thisWaypoint.cartPos.x;
            pos_onbase.y = thisWaypoint.cartPos.y;
            pos_onbase.z = thisWaypoint.cartPos.z;
            //法兰盘中心在基坐标系下的姿态（欧拉角）
            AuboRobot.Rpy rpy_onbase = new AuboRobot.Rpy();
            rpy_onbase.rx = rpy.rx;
            rpy_onbase.ry = rpy.ry;
            rpy_onbase.rz = rpy.rz;

            Console.Out.WriteLine("x:" + pos_onbase.x + " y:" + pos_onbase.y + " z:" + pos_onbase.z);

            //法兰盘中心在基坐标系下的姿态（四元数）
            AuboRobot.Ori ori_onbase = new AuboRobot.Ori();
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy_onbase, ref ori_onbase);
            //基坐标系
            AuboRobot.CoordCalibrate user_coord = new AuboRobot.CoordCalibrate();
            IntPtr pt_user_coord = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(AuboRobot.CoordCalibrate)));
            user_coord = (AuboRobot.CoordCalibrate)Marshal.PtrToStructure(pt_user_coord, typeof(AuboRobot.CoordCalibrate));
            user_coord.coordType = 0;
            //工具描述
            AuboRobot.ToolInEndDesc tool_desc = new AuboRobot.ToolInEndDesc();
            tool_desc.cartPos.x = 0;
            tool_desc.cartPos.y = 0;
            tool_desc.cartPos.z = 0.1;
            tool_desc.orientation.w = 0;
            tool_desc.orientation.x = 0.707106;
            tool_desc.orientation.y = 0.707108;
            tool_desc.orientation.z = 0;


            //工具在基坐标系下的位置
           
            AuboRobot.Pos tool_pos_onbase = new AuboRobot.Pos();
            //工具在基坐标系下的姿态
            AuboRobot.Ori tool_ori_onbase = new AuboRobot.Ori();
            //坐标系转换
            AuboRobot.rs_base_to_user(rshd, ref pos_onbase, ref ori_onbase, ref user_coord, ref tool_desc, ref tool_pos_onbase, ref tool_ori_onbase);
           // Console.Out.WriteLine("工具在基坐标系下的位置：（{0}，{1}，{2}）", tool_pos_onbase.x, tool_pos_onbase.y, tool_pos_onbase.z);
           // Console.Out.WriteLine("工具在基坐标系下的姿态（四元数）：({0}, {1}, { 2}, { 3})", tool_ori_onbase.w, tool_ori_onbase.x, tool_ori_onbase.y, tool_ori_onbase.z);
            AuboRobot.Rpy tool_rpy_onbase = new AuboRobot.Rpy();
            AuboRobot.rs_quaternion_to_rpy(rshd, ref tool_ori_onbase, ref tool_rpy_onbase);
            // Console.Out.WriteLine("工具在基坐标系下的姿态（欧拉角）：（{0}，{1}，{ 2}）", tool_rpy_onbase.rx * 180 / M_PI, tool_rpy_onbase.ry * 180 / M_PI, tool_rpy_onbase.rz * 180 / M_PI);

            tb_x.Text = tool_pos_onbase.x.ToString();
            tb_y.Text = tool_pos_onbase.y.ToString();
            tb_z.Text = tool_pos_onbase.z.ToString();
            //rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            tb_rx.Text = (tool_rpy_onbase.rx * 180 / M_PI).ToString();
            tb_ry.Text = (tool_rpy_onbase.ry * 180 / M_PI).ToString();
            tb_rz.Text = (tool_rpy_onbase.rz * 180 / M_PI).ToString();
            tb_j1.Text = (thisWaypoint.jointpos[0] * 180 / M_PI).ToString();
            tb_j2.Text = (thisWaypoint.jointpos[1] * 180 / M_PI).ToString();
            tb_j3.Text = (thisWaypoint.jointpos[2] * 180 / M_PI).ToString();
            tb_j4.Text = (thisWaypoint.jointpos[3] * 180 / M_PI).ToString();
            tb_j5.Text = (thisWaypoint.jointpos[4] * 180 / M_PI).ToString();
            tb_j6.Text = (thisWaypoint.jointpos[5] * 180 / M_PI).ToString();
        }



        /// <summary>
        /// 将法兰盘中心在基坐标系下的位置和姿态转成法兰盘中心在用户坐标系下的位置和姿态
        /// </summary>
        /// <param name="rshd"></param>
        void base_to_userExample(UInt16 rshd)
        {
            getCurrentWaypoint(rshd);
            thisWaypoint = thisCurrentWaypoint(rshd);
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.rs_quaternion_to_rpy(rshd, ref thisWaypoint.orientation, ref rpy);
            //法兰盘中心在基坐标系下的位置
            AuboRobot.Pos pos_onbase = new AuboRobot.Pos();
            pos_onbase.x = thisWaypoint.cartPos.x;
            pos_onbase.y = thisWaypoint.cartPos.y;
            pos_onbase.z = thisWaypoint.cartPos.z;
            //法兰盘中心在基坐标系下的姿态（欧拉角）
            AuboRobot.Rpy rpy_onbase = new AuboRobot.Rpy();
            rpy_onbase.rx = rpy.rx;
            rpy_onbase.ry = rpy.ry;
            rpy_onbase.rz = rpy.rz;

            Console.Out.WriteLine("x:"+ pos_onbase.x+" y:"+ pos_onbase.y+" z:"+ pos_onbase.z);




            //法兰盘中心在基坐标系下的姿态（四元数）
            AuboRobot.Ori ori_onbase = new AuboRobot.Ori();
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy_onbase, ref ori_onbase);

            //用户坐标系
            AuboRobot.CoordCalibrate user_coord = new AuboRobot.CoordCalibrate();
            IntPtr pt_user_coord = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(AuboRobot.CoordCalibrate)));
            user_coord = (AuboRobot.CoordCalibrate)Marshal.PtrToStructure(pt_user_coord, typeof(AuboRobot.CoordCalibrate));
            user_coord.coordType = 2;
            user_coord.methods = 3;
            user_coord.jointPara[0].jointRadian[0] = -4.296152 * M_PI / 180.0;
            user_coord.jointPara[0].jointRadian[1] = -5.549898 * M_PI / 180.0;
            user_coord.jointPara[0].jointRadian[2] = -63.058758 * M_PI / 180.0;
            user_coord.jointPara[0].jointRadian[3] = 29.094796 * M_PI / 180.0;
            user_coord.jointPara[0].jointRadian[4] = -81.181361 * M_PI / 180.0;
            user_coord.jointPara[0].jointRadian[5] = 21.060094 * M_PI / 180.0;

            user_coord.jointPara[1].jointRadian[0] = -6.169495 * M_PI / 180.0;
            user_coord.jointPara[1].jointRadian[1] = -20.281617 * M_PI / 180.0;
            user_coord.jointPara[1].jointRadian[2] = -73.358253 * M_PI / 180.0;
            user_coord.jointPara[1].jointRadian[3] = 33.818947 * M_PI / 180.0;
            user_coord.jointPara[1].jointRadian[4] = -81.075076 * M_PI / 180.0;
            user_coord.jointPara[1].jointRadian[5] = 19.167101 * M_PI / 180.0;

            user_coord.jointPara[2].jointRadian[0] = -23.206853 * M_PI / 180.0;
            user_coord.jointPara[2].jointRadian[1] = -21.767526 * M_PI / 180.0;
            user_coord.jointPara[2].jointRadian[2] = -73.156455 * M_PI / 180.0;
            user_coord.jointPara[2].jointRadian[3] = 38.280171 * M_PI / 180.0;
            user_coord.jointPara[2].jointRadian[4] = -80.560503 * M_PI / 180.0;
            user_coord.jointPara[2].jointRadian[5] = 1.914768 * M_PI / 180.0;


            //坐标系的工具参数
            AuboRobot.ToolInEndDesc tooluser_coord = new AuboRobot.ToolInEndDesc();
            tooluser_coord.cartPos.x = 0;
            tooluser_coord.cartPos.y = 0;
            tooluser_coord.cartPos.z = 0;
            tooluser_coord.orientation.w = 1;
            tooluser_coord.orientation.x = 0;
            tooluser_coord.orientation.y = 0;
            tooluser_coord.orientation.z = 0;
            user_coord.toolDesc = tooluser_coord;

            //工具描述
            AuboRobot.ToolInEndDesc tool_desc = new AuboRobot.ToolInEndDesc();
            tool_desc.cartPos.x = 0;
            tool_desc.cartPos.y = 0;
            tool_desc.cartPos.z = 0;
            tool_desc.orientation.w = 1;
            tool_desc.orientation.x = 0;
            tool_desc.orientation.y = 0;
            tool_desc.orientation.z = 0;

            //法兰盘中心在用户坐标系下的位置
            AuboRobot.Pos pos_onuser = new AuboRobot.Pos();
            //法兰盘中心在用户坐标系下的姿态
            AuboRobot.Ori ori_onuser = new AuboRobot.Ori();
            //坐标系转换
            AuboRobot.rs_base_to_user(rshd, ref pos_onbase, ref ori_onbase, ref user_coord, ref tool_desc, ref pos_onuser, ref ori_onuser);

          //  Console.Out.WriteLine("法兰盘中心在用户坐标系下的位置：（{0}，{1}，{ 2}）", pos_onuser.x, pos_onuser.y, pos_onuser.z);
          //  Console.Out.WriteLine("法兰盘中心在用户坐标系下的姿态（四元数）：({0}, { 1}, { 2}, { 3})", ori_onuser.w, ori_onuser.x, ori_onuser.y, ori_onuser.z);
            AuboRobot.Rpy rpy_onuser = new AuboRobot.Rpy();
            AuboRobot.rs_quaternion_to_rpy(rshd, ref ori_onuser, ref rpy_onuser);
          //  Console.Out.WriteLine("法兰盘中心在用户坐标系下的姿态（欧拉角）：（{ 0}，{ 1}，{ 2}）", rpy_onuser.rx * 180 / M_PI, rpy_onuser.ry * 180 / M_PI, rpy_onuser.rz * 180 / M_PI);


            tb_x.Text = pos_onuser.x.ToString();
            tb_y.Text = pos_onuser.y.ToString();
            tb_z.Text = pos_onuser.z.ToString();
            //rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            tb_rx.Text = (rpy_onuser.rx * 180 / M_PI).ToString();
            tb_ry.Text = (rpy_onuser.ry * 180 / M_PI).ToString();
            tb_rz.Text = (rpy_onuser.rz * 180 / M_PI).ToString();
            tb_j1.Text = (thisWaypoint.jointpos[0] * 180 / M_PI).ToString();
            tb_j2.Text = (thisWaypoint.jointpos[1] * 180 / M_PI).ToString();
            tb_j3.Text = (thisWaypoint.jointpos[2] * 180 / M_PI).ToString();
            tb_j4.Text = (thisWaypoint.jointpos[3] * 180 / M_PI).ToString();
            tb_j5.Text = (thisWaypoint.jointpos[4] * 180 / M_PI).ToString();
            tb_j6.Text = (thisWaypoint.jointpos[5] * 180 / M_PI).ToString();


        }

        //设置和获取机械臂相关参数
        //获取当前路点信息
        AuboRobot.wayPoint_S thisCurrentWaypoint(UInt16 rshd)
        {
            AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();

            int ret = AuboRobot.rs_get_current_waypoint(rshd, ref waypoint);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("获取当前路点信息成功！");
                AuboRobot.PrintWaypoint(waypoint);
            }
            else
            {
                Console.Error.WriteLine("获取当前路点信息失败！错误码：{0}", ret);
            }
            return waypoint;
        }


        //获取关节状态
        static void getJointStatus(UInt16 rshd)
        {
            /* c#向c++封送结构体问题
         * 要先分配一段非托管内存，接口中结构体参数应传入IntPtr
         * 调用接口后，再将非托管内容数据读取到托管结构体数据中
         */

            int size = Marshal.SizeOf(typeof(AuboRobot.JointStatus)) * 6;
            byte[] bytes = new byte[size];
            IntPtr pBuff = Marshal.AllocHGlobal(size);
            AuboRobot.JointStatus[] jointStatus = new AuboRobot.JointStatus[6];
            int ret = AuboRobot.rs_get_joint_status(rshd, pBuff);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("获取关节状态成功！\n");
                for (int i = 0; i < 6; i++)
                {
                    IntPtr pPonitor = new IntPtr(pBuff.ToInt64() + Marshal.SizeOf(typeof(AuboRobot.JointStatus)) * i);
                    jointStatus[i] = (AuboRobot.JointStatus)Marshal.PtrToStructure(pPonitor, typeof(AuboRobot.JointStatus));
                }
                for (int i = 0; i < 6; i++)
                {
                    Console.Out.WriteLine("关节{0}:", i + 1);
                    Console.Out.WriteLine("关节电流: {0} 关节速度: {1} 关节角: {2} 关节电压：{3} 当前温度：{4} ", jointStatus[i].jointCurrentI, jointStatus[i].jointSpeedMoto, jointStatus[i].jointPosJ * 180 / M_PI, jointStatus[i].jointCurVol, jointStatus[i].jointCurTemp);
                    Console.Out.WriteLine("电机目标电流: {0} 电机目标速度: {1} 目标关节角: {2} 关节错误码: {3} ", jointStatus[i].jointTagCurrentI, jointStatus[i].jointTagSpeedMoto, jointStatus[i].jointTagPosJ * 180 / M_PI, jointStatus[i].jointErrorNum);

                }
                Marshal.FreeHGlobal(pBuff);
            }
            else
            {
                Console.Error.WriteLine("获取关节状态失败！错误码：{0}", ret);
            }

        }
        /// <summary>
        /// 机械臂登录
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private void btn_login_Click(object sender, EventArgs e)
        {
            //初始化机械臂动态库
            // int result = rs_initialize();
            if (result == RS_SUCC)
            {
                Console.WriteLine("机械臂初始化成功");
            }
            else
            {
                Console.WriteLine("机械臂初始化失败，错误码:{0}", result);

            }
            //创建机械臂控制上下文
            //result = rs_create_context(ref rshd);
            result = AuboRobot.rs_create_context(ref rshd);
            if (result == RS_SUCC)
            {
                Console.WriteLine("创建上下文句柄成功！");
            }
            else
            {
                Console.WriteLine("创建上下文句柄失败！错误码：{0}", result);

            }

            string serverIp = tb_serverIP.Text;
            //机械臂登录
            //result = rs_login(rshd, ip, port);
            result = AuboRobot.rs_login(rshd, serverIp, port);
            if (result == RS_SUCC)
            {
                Console.Out.WriteLine("登录成功！");
                btn_login.BackColor = Color.Green;
              //  timer1.Enabled = true;//开启定时器
               // btn_login.Enabled = false;
            }
            else
            {
                Console.Error.WriteLine("登录失败！错误码： {0}", result);
                MessageBox.Show("登录失败！错误码：" + result);
            }

            //回调函数获取实时路点信息
            //realtimeRoadPointExample(rshd);
            ////关节运动—rs_move_joint
            //jointMoveExample(rshd);
            //Thread newThread = new Thread(realtimeRoadPointExample);

            //newThread.Start();
            //newThread.IsBackground = true;

        }

        /// <summary>
        /// 机械臂上电
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private void btn_startUp_Click(object sender, EventArgs e)
        {
            //机械臂上电
            //工具参数的设置
            AuboRobot.ToolDynamicsParam tool = new AuboRobot.ToolDynamicsParam();
            tool.positionX = 10.0;
            tool.positionY = 0.1;
            tool.positionZ = 0.05;
            tool.payload = 0.0;  //负载设置
            tool.toolInertia.xx = 0.0;
            tool.toolInertia.xy = 0.0;
            tool.toolInertia.xz = 0.0;
            tool.toolInertia.yy = 0.0;
            tool.toolInertia.yz = 0.0;
            tool.toolInertia.zz = 0.0;

            int state = 0;
            result = AuboRobot.rs_robot_startup(rshd, ref tool, 9, true, true, 1000, ref state);
            if (result == RS_SUCC)
            {
                Console.Out.WriteLine("上电成功！状态：{0}", state);
                btn_startUp.BackColor = Color.Green;
              //  btn_startUp.Enabled = false;
                //timer1.Enabled = true;//开启定时器
                //timer3.Enabled = true;

            }
            else
            {
                Console.Error.WriteLine("上电失败！错误码： {0}", result);
                MessageBox.Show("上电失败！错误码：" + result);
            }

            Init_MoveParameter();

            int ret = AuboRobot.rs_enable_push_realtime_roadpoint(rshd, true);
            if (ret == RS_SUCC)
            {
                AuboRobot.rs_setcallback_realtime_roadpoint(rshd, AuboRobot.CurrentPositionCallback, IntPtr.Zero);
                //Thread.Sleep(5 * 1000);
            }
            else
            {
                Console.Error.WriteLine("调用 rs_enable_push_realtime_roadpoint 函数失败！错误码：{ 0}", ret);
            }




        }

        private void btn_pointA_Click(object sender, EventArgs e)
        {
            //关节运动—rs_move_joint
            //jointMoveExample(rshd);
            btn_pointA.BackColor = Color.Green;
            btn_pointB.BackColor = Color.Gold;
            //线程测试
            thread1 = new Thread(delegate ()
              {

                  jointMoveExample(rshd);
              });
            thread1.Start();
        }

        private void btn_pointB_Click(object sender, EventArgs e)
        {
            //关节运动—rs_move_joint
            //jointMoveExample1(rshd);
            btn_pointB.BackColor = Color.Green;
            btn_pointA.BackColor = Color.Gold;
            // getJointStatus(rshd);
            //线程测试
            thread2 = new Thread(delegate ()
            {

                jointMoveExample1(rshd);
            });
            thread2.Start();

        }

        private void timer1_Tick(object sender, EventArgs e)
        {
            //获取并显示当前位姿信息

            getCurrentWaypoint(rshd);
            thisWaypoint = thisCurrentWaypoint(rshd);
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.rs_quaternion_to_rpy(rshd, ref thisWaypoint.orientation, ref rpy);
            //工具目标切换
            //切换成法兰盘
            if(isFlangeCenterTool==true)
            {
                Console.WriteLine("flangeCenter-----------------------------------------------------");
                toolInEndDesc.cartPos.x = 0.0;
                toolInEndDesc.cartPos.y = 0.0;
                toolInEndDesc.cartPos.z = 0.0;

                toolInEndDesc.orientation.w = 1.0;
                toolInEndDesc.orientation.x = 0.0;
                toolInEndDesc.orientation.y = 0.0;
                toolInEndDesc.orientation.z = 0.0;

            }
            //切换成工具tcp
            if(isTcpTool==true)
            {
                Console.WriteLine("tool-----------------------------------------------------");
                toolInEndDesc.cartPos.x = 0.0;
                toolInEndDesc.cartPos.y = 0.0;
                toolInEndDesc.cartPos.z = 0.1;
                AuboRobot.Rpy rpy1 = new AuboRobot.Rpy();
                AuboRobot.Ori ori1 = new AuboRobot.Ori();
                rpy1.rx = 180.0 / 180 * M_PI;
                rpy1.ry = 0.0;
                rpy1.rz = 90.0 / 180 * M_PI;
                AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy1, ref ori1);
                toolInEndDesc.orientation = ori1;
            }
            //坐标系切换
            //Base坐标系
            if(isBaseCoord==true)
            {
                Console.WriteLine("BaseCoord-----------------------------------------------------");
                curBaseCoord.coordType = AuboRobot.BaseCoordinate;
                int retCurBase=AuboRobot.rs_base_to_user(rshd,ref thisWaypoint.cartPos,ref thisWaypoint.orientation, ref curBaseCoord,ref toolInEndDesc,ref toolEndPosOnUser, ref toolEndOriOnUser);
                Console.WriteLine("retCurBase:"+ retCurBase);
               
            }
            //法兰盘坐标系
            if(isFlangeCenterCoord==true)
            {
                Console.WriteLine("flangeCenterCoord-----------------------------------------------------");
                curFlangeCenterCoord.coordType = AuboRobot.EndCoordinate;

                curFlangeCenterCoord.toolDesc.cartPos.x = 0.0;
                curFlangeCenterCoord.toolDesc.cartPos.y = 0.0;
                curFlangeCenterCoord.toolDesc.cartPos.z = 0.0;
                curFlangeCenterCoord.toolDesc.orientation.w = 1.0;
                curFlangeCenterCoord.toolDesc.orientation.x = 0.0;
                curFlangeCenterCoord.toolDesc.orientation.y = 0.0;
                curFlangeCenterCoord.toolDesc.orientation.z = 0.0;


                int retCurFlangeCenter=AuboRobot.rs_base_to_user(rshd, ref thisWaypoint.cartPos, ref thisWaypoint.orientation, ref curFlangeCenterCoord, ref toolInEndDesc, ref toolEndPosOnUser, ref toolEndOriOnUser);
                Console.WriteLine("retCurFlangeCenter:"+ retCurFlangeCenter);
            }
            //用户坐标系
            if(isUserCoord==true)
            {
                Console.WriteLine("userCoord-----------------------------------------------------");
                curUserCoord.coordType = AuboRobot.WorldCoordinate;
                curUserCoord.methods = AuboRobot.Origin_AnyPointOnPositiveXAxis_AnyPointOnFirstQuadrantOfXOYPlane;

                AuboRobot.JointRadian jointPara1 = new AuboRobot.JointRadian();
                AuboRobot.JointRadian jointPara2 = new AuboRobot.JointRadian();
                AuboRobot.JointRadian jointPara3 = new AuboRobot.JointRadian();

                jointPara1.jointRadian = new double[] { -0.000401 / 180 * M_PI, -18.282682 / 180 * M_PI, -114.410675 / 180 * M_PI, -100.986389 / 180 * M_PI, -89.000392 / 180 * M_PI, 0.000057 / 180 * M_PI };
                jointPara2.jointRadian = new double[] { -0.000401 / 180 * M_PI, -18.569104 / 180 * M_PI, -106.577541 / 180 * M_PI, -92.866892 / 180 * M_PI, -89.999354 / 180 * M_PI, 0.000057 / 180 * M_PI };
                jointPara3.jointRadian = new double[] { -7.421752 / 180 * M_PI, -20.690135 / 180 * M_PI, -107.929604 / 180 * M_PI, -92.138835 / 180 * M_PI, -82.604759 / 180 * M_PI, 0.632144 / 180 * M_PI };
                curUserCoord.jointPara = new AuboRobot.JointRadian[3] { jointPara1, jointPara2, jointPara3 };
                curUserCoord.jointPara[0] = jointPara1;
                curUserCoord.jointPara[1] = jointPara2;
                curUserCoord.jointPara[2] = jointPara3;

                toolUserCoord.cartPos.x = 0.0;
                toolUserCoord.cartPos.y = 0.0;
                toolUserCoord.cartPos.z = 0.1;
                AuboRobot.Rpy rpy2 = new AuboRobot.Rpy();
                AuboRobot.Ori ori2 = new AuboRobot.Ori();
                rpy2.rx = 179.999848 / 180 * M_PI;
                rpy2.ry = 0.0;
                rpy2.rz = 89.999638 / 180 * M_PI;
                AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy2, ref ori2);
                toolUserCoord.orientation = ori2;

                curUserCoord.toolDesc = toolUserCoord;
                int retCurUser=AuboRobot.rs_base_to_user(rshd, ref thisWaypoint.cartPos, ref thisWaypoint.orientation, ref curUserCoord, ref toolInEndDesc, ref toolEndPosOnUser, ref toolEndOriOnUser);
                Console.WriteLine("retCurUser:"+ retCurUser);
            }


            AuboRobot.rs_quaternion_to_rpy(rshd, ref toolEndOriOnUser, ref toolEndRpyOnUser);
            tb_x.Text = toolEndPosOnUser.x.ToString("0.000000");
            tb_y.Text = toolEndPosOnUser.y.ToString("0.000000");
            tb_z.Text = toolEndPosOnUser.z.ToString("0.000000");
            //rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            tb_rx.Text = (toolEndRpyOnUser.rx * 180 / M_PI).ToString();
            tb_ry.Text = (toolEndRpyOnUser.ry * 180 / M_PI).ToString();
            tb_rz.Text = (toolEndRpyOnUser.rz * 180 / M_PI).ToString();

            tb_j1.Text = (thisWaypoint.jointpos[0] * 180 / M_PI).ToString("0.000000");
            tb_j2.Text = (thisWaypoint.jointpos[1] * 180 / M_PI).ToString("0.000000");
            tb_j3.Text = (thisWaypoint.jointpos[2] * 180 / M_PI).ToString("0.000000");
            tb_j4.Text = (thisWaypoint.jointpos[3] * 180 / M_PI).ToString("0.000000");
            tb_j5.Text = (thisWaypoint.jointpos[4] * 180 / M_PI).ToString("0.000000");
            tb_j6.Text = (thisWaypoint.jointpos[5] * 180 / M_PI).ToString("0.000000");




            //tb_x.Text = thisWaypoint.cartPos.x.ToString("0.000000");
            //tb_y.Text = thisWaypoint.cartPos.y.ToString("0.000000");
            //tb_z.Text = thisWaypoint.cartPos.z.ToString("0.000000");
            ////rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            //tb_rx.Text = (rpy.rx * 180 / M_PI).ToString();
            //tb_ry.Text = (rpy.ry * 180 / M_PI).ToString();
            //tb_rz.Text = (rpy.rz * 180 / M_PI).ToString();
            //tb_j1.Text = (thisWaypoint.jointpos[0] * 180 / M_PI).ToString("0.000000");
            //tb_j2.Text = (thisWaypoint.jointpos[1] * 180 / M_PI).ToString("0.000000");
            //tb_j3.Text = (thisWaypoint.jointpos[2] * 180 / M_PI).ToString("0.000000");
            //tb_j4.Text = (thisWaypoint.jointpos[3] * 180 / M_PI).ToString("0.000000");
            //tb_j5.Text = (thisWaypoint.jointpos[4] * 180 / M_PI).ToString("0.000000");
            //tb_j6.Text = (thisWaypoint.jointpos[5] * 180 / M_PI).ToString("0.000000");


            //获取用户IO的状态信息并显示
            //获取DI状态
            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_00, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_00.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_00.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_01, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_01.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_01.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_02, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_02.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_02.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }
            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_03, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_03.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_03.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_04, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_04.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_04.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_05, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_05.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_05.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_06, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_06.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_06.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_07, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_07.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_07.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_10, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_10.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_10.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }
            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_11, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_11.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_11.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_12, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_12.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_12.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_13, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_13.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_13.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_14, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_14.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_14.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_15, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_15.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_15.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_16, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_16.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_16.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }
            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DI, AuboRobot.ROBOT_IO_U_DI_17, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DI_17.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DI_17.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            //获取DO状态
            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_00, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_00.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_00.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_01, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_01.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_01.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }


            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_02, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_02.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_02.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }


            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_03, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_03.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_03.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_04, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_04.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_04.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_05, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_05.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_05.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_06, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_06.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_06.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_07, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_07.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_07.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_10, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_10.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_10.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_11, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_11.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_11.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_12, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_12.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_12.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_13, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_13.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_13.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_14, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_14.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_14.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_15, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_15.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_15.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_16, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_16.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_16.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

            if (0 == (result = AuboRobot.rs_get_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_17, ref io_status)))
            {
                if (io_status == 1)
                {
                    btn_U_DO_17.BackColor = Color.Green;
                }
                else
                {
                    btn_U_DO_17.BackColor = Color.White;
                }
            }
            else
            {
                //获取IO状态失败
                Console.WriteLine("获取IO状态失败");
            }

        }

        private void btn_U_DO_00_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_00.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_01_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_01.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }
        /// <summary>
        /// 发送选中的IO状态
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private void btn_send_Click(object sender, EventArgs e)
        {
            if (lb_IOName.Text == btn_U_DO_00.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_00, io_status)))
                    {
                        Console.WriteLine("设置U_DO_00");
                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_00, io_status)))
                    {
                        Console.WriteLine("设置U_DO_00");
                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_01.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_01, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_01, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_02.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_02, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_02, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_03.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_03, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_03, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_04.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_04, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_04, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_05.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_05, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_05, io_status)))
                    {

                    }
                }
            }


            if (lb_IOName.Text == btn_U_DO_06.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_06, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_06, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_07.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_07, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_07, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_10.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_10, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_10, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_11.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_11, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_11, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_12.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_12, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_12, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_13.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_13, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_13, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_14.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_14, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_14, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_15.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_15, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_15, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_16.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_16, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_16, io_status)))
                    {

                    }
                }
            }

            if (lb_IOName.Text == btn_U_DO_17.Text)
            {
                if (rb_low.Checked == true)
                {
                    io_status = 0.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_17, io_status)))
                    {

                    }
                }
                if (rb_high.Checked == true)
                {
                    io_status = 1.0;
                    if (0 == (result = AuboRobot.rs_set_board_io_status_by_addr(rshd, AuboRobot.Robot_User_DO, AuboRobot.ROBOT_IO_U_DO_17, io_status)))
                    {

                    }
                }
            }


        }

        /// <summary>
        /// 初始化控件状态
        /// </summary>
        void InitWidgetStatus()
        {
            rb_low.Visible = false;
            rb_high.Visible = false;
            //用户输入IO只读状态
            btn_U_DI_00.Enabled = false;
            btn_U_DI_01.Enabled = false;
            btn_U_DI_02.Enabled = false;
            btn_U_DI_03.Enabled = false;
            btn_U_DI_04.Enabled = false;
            btn_U_DI_05.Enabled = false;
            btn_U_DI_06.Enabled = false;
            btn_U_DI_07.Enabled = false;
            btn_U_DI_10.Enabled = false;
            btn_U_DI_11.Enabled = false;
            btn_U_DI_12.Enabled = false;
            btn_U_DI_13.Enabled = false;
            btn_U_DI_14.Enabled = false;
            btn_U_DI_15.Enabled = false;
            btn_U_DI_16.Enabled = false;
            btn_U_DI_17.Enabled = false;

            cb_ori.SelectedIndex = 0;
        }

        private void rb_high_CheckedChanged(object sender, EventArgs e)
        {

        }

        private void btn_JsonTest_Click(object sender, EventArgs e)
        {
            Student mStudent = new Student();
            mStudent.Name = "cheng";
            mStudent.Age = 18;
            mStudent.Sex = "男";
            string jsonString = JsonConvert.SerializeObject(mStudent);
            Console.WriteLine(jsonString);

            Student newStudent = JsonConvert.DeserializeObject<Student>(jsonString);

            Console.WriteLine("newStudent.Name:" + newStudent.Name);
            Console.WriteLine("newStudent.Age:" + newStudent.Age);
            Console.WriteLine("newStudent.Sex:" + newStudent.Sex);

        }
        /// <summary>
        /// 相对偏移测试
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private void btn_offset_Click(object sender, EventArgs e)
        {
            thread3 = new Thread(delegate ()
           {

               rs_set_relative_offset_on_base_Example(rshd);
           });
            thread3.Start();

        }

        private void btn_pause_Click(object sender, EventArgs e)
        {
            thread4 = new Thread(delegate ()
           {

               int retMovePause = AuboRobot.rs_move_pause(rshd);
               if (retMovePause == RS_SUCC)
               {
                   Console.WriteLine("运动暂停成功");
                   btn_pause.BackColor = Color.Green;
                   Thread.Sleep(2000);
                   btn_pause.BackColor = Color.White;
               }
               else
               {
                   Console.WriteLine("运动暂停失败");
                   btn_pause.BackColor = Color.Red;
                   Thread.Sleep(2000);
                   btn_pause.BackColor = Color.White;
               }
           });
            thread4.Start();


        }

        private void btn_continue_Click(object sender, EventArgs e)
        {
            thread5 = new Thread(delegate ()
           {

               int retMoveContinue = AuboRobot.rs_move_continue(rshd);
               if (retMoveContinue == RS_SUCC)
               {
                   Console.WriteLine("运动继续成功");
                   btn_continue.BackColor = Color.Green;
                   Thread.Sleep(2000);
                   btn_continue.BackColor = Color.White;
               }
               else
               {
                   Console.WriteLine("运动继续失败");
                   btn_continue.BackColor = Color.Red;
                   Thread.Sleep(2000);
                   btn_continue.BackColor = Color.White;
               }
           });
            thread5.Start();
        }

        private void btn_stop_Click(object sender, EventArgs e)
        {
            //int retMoveStop = AuboRobot.rs_move_fast_stop(rshd);
            //int retMoveStop = AuboRobot.rs_move_stop(rshd);
            //Console.WriteLine("retMoveStop:" + retMoveStop);
            //if (retMoveStop == RS_SUCC)
            //{
            //    Console.WriteLine("运动停止成功");
            //    btn_stop.BackColor = Color.Green;
            //    Thread.Sleep(2000);
            //    btn_stop.BackColor = Color.White;
            //}
            //else
            //{
            //    Console.WriteLine("运动停止失败");
            //    btn_stop.BackColor = Color.Red;
            //    Thread.Sleep(2000);
            //    btn_stop.BackColor = Color.White;
            //}

            

            thread6 = new Thread(delegate ()
            {

                int retMoveStop = AuboRobot.rs_move_fast_stop(rshd);
                //int retMoveStop = AuboRobot.rs_move_stop(rshd);
                Console.WriteLine("retMoveStop:" + retMoveStop);
                if (retMoveStop == RS_SUCC)
                {
                    Console.WriteLine("运动停止成功");
                    btn_stop.BackColor = Color.Green;
                    Thread.Sleep(2000);
                    btn_stop.BackColor = Color.White;
                }
                else
                {
                    Console.WriteLine("运动停止失败");
                    btn_stop.BackColor = Color.Red;
                    Thread.Sleep(2000);
                    btn_stop.BackColor = Color.White;
                }
            });
            thread6.Start();
        }

        private void Form1_FormClosed(object sender, FormClosedEventArgs e)
        {
            if (thread1 != null)
            {
                thread1.Abort();
            }
            if (thread2 != null)
            {
                thread2.Abort();
            }
            if (thread3 != null)
            {
                thread3.Abort();
            }
            if (thread4 != null)
            {
                thread4.Abort();
            }
            if (thread5 != null)
            {
                thread5.Abort();
            }
            if (thread6 != null)
            {
                thread6.Abort();
            }
            //机械臂退出登录
            //创建机械臂控制上下文
            // result = rs_create_context(ref rshd);
            result = AuboRobot.rs_create_context(ref rshd);
            if (result == RS_SUCC)
            {
                Console.WriteLine("创建上下文句柄成功！");
            }
            else
            {
                Console.WriteLine("创建上下文句柄失败！错误码：{0}", result);

            }

            //机械臂登录
            // result = rs_logout(rshd);
            result = AuboRobot.rs_logout(rshd);
            if (result == RS_SUCC)
            {
                Console.Out.WriteLine("退出登录成功！");

            }
            else
            {
                Console.Error.WriteLine("退出登录失败！错误码： {0}", result);
                MessageBox.Show("登录失败！错误码：" + result);
            }
        }

        private void button6_Click(object sender, EventArgs e)
        {

        }

        private void btn_posXDe_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode x = AuboRobot.teach_mode.MOV_X;
            AuboRobot.rs_teach_move_start(rshd, x, false);
        }

        private void btn_posXDe_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_posXAdd_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode x = AuboRobot.teach_mode.MOV_X;
            AuboRobot.rs_teach_move_start(rshd, x, true);
        }

        private void btn_posXAdd_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_posYDe_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode y = AuboRobot.teach_mode.MOV_Y;
            AuboRobot.rs_teach_move_start(rshd, y, false);
        }

        private void btn_posYDe_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_posYAdd_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode y = AuboRobot.teach_mode.MOV_Y;
            AuboRobot.rs_teach_move_start(rshd, y, true);
        }

        private void btn_posYAdd_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_posZDe_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode z = AuboRobot.teach_mode.MOV_Z;
            AuboRobot.rs_teach_move_start(rshd, z, false);
        }

        private void btn_posZDe_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_posZAdd_MouseDown(object sender, MouseEventArgs e)
        {
            //AuboRobot.CoordCalibrate user_coord1 = new AuboRobot.CoordCalibrate();
            //user_coord1.coordType = AuboRobot.EndCoordinate;
            //user_coord1.toolDesc.cartPos.x = 0;
            //user_coord1.toolDesc.cartPos.y = 0;
            //user_coord1.toolDesc.cartPos.z = 0;
            //user_coord1.toolDesc.orientation.w = 1;
            //user_coord1.toolDesc.orientation.x = 0;
            //user_coord1.toolDesc.orientation.y = 0;
            //user_coord1.toolDesc.orientation.z = 0;

            ////AuboRobot.teach_mode.
            //int retSetTeachCoord = AuboRobot.rs_set_teach_coord(rshd, ref user_coord1);
            //Console.WriteLine("retSetTeachCoord:" + retSetTeachCoord);
            AuboRobot.teach_mode z = AuboRobot.teach_mode.MOV_Z;
            AuboRobot.rs_teach_move_start(rshd, z, true);

        }

        private void btn_posZAdd_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_oriRXDe_MouseDown(object sender, MouseEventArgs e)
        {

            AuboRobot.teach_mode rx = AuboRobot.teach_mode.ROT_X;
           
            AuboRobot.rs_teach_move_start(rshd, rx, false);
        }

        private void btn_oriRXDe_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_oriRXAdd_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode rx = AuboRobot.teach_mode.ROT_X;
            AuboRobot.rs_teach_move_start(rshd, rx, true);
        }

        private void btn_oriRXAdd_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_oriRYDe_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode ry = AuboRobot.teach_mode.ROT_Y;
            AuboRobot.rs_teach_move_start(rshd, ry, false);
        }

        private void btn_oriRYDe_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_oriRYAdd_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode ry = AuboRobot.teach_mode.ROT_Y;
            AuboRobot.rs_teach_move_start(rshd, ry, true);
        }

        private void btn_oriRYAdd_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_oriRZDe_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode rz = AuboRobot.teach_mode.ROT_Z;
            AuboRobot.rs_teach_move_start(rshd, rz, false);
        }

        private void btn_oriRZDe_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_oriRZAdd_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode rz = AuboRobot.teach_mode.ROT_Z;
            AuboRobot.rs_teach_move_start(rshd, rz, true);
        }

        private void btn_oriRZAdd_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ1De_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j1 = AuboRobot.teach_mode.JOINT1;
            AuboRobot.rs_teach_move_start(rshd, j1, false);
        }

        private void btn_jointJ1De_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ1Add_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j1 = AuboRobot.teach_mode.JOINT1;
            AuboRobot.rs_teach_move_start(rshd, j1, true);
        }

        private void btn_jointJ1Add_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ2De_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j2 = AuboRobot.teach_mode.JOINT2;
            AuboRobot.rs_teach_move_start(rshd, j2, false);
        }

        private void btn_jointJ2De_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ2Add_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j2 = AuboRobot.teach_mode.JOINT2;
            AuboRobot.rs_teach_move_start(rshd, j2, true);
        }

        private void btn_jointJ2Add_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ3De_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j3 = AuboRobot.teach_mode.JOINT3;
            AuboRobot.rs_teach_move_start(rshd, j3, false);

        }

        private void btn_jointJ3De_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ3Add_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j3 = AuboRobot.teach_mode.JOINT3;
            AuboRobot.rs_teach_move_start(rshd, j3, true);
        }

        private void btn_jointJ3Add_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ4De_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j4 = AuboRobot.teach_mode.JOINT4;
            AuboRobot.rs_teach_move_start(rshd, j4, false);
        }

        private void btn_jointJ4De_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ4Add_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j4 = AuboRobot.teach_mode.JOINT4;
            AuboRobot.rs_teach_move_start(rshd, j4, true);
        }

        private void btn_jointJ4Add_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ5De_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j5 = AuboRobot.teach_mode.JOINT5;
            AuboRobot.rs_teach_move_start(rshd, j5, false);
        }

        private void btn_jointJ5De_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ5Add_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j5 = AuboRobot.teach_mode.JOINT5;
            AuboRobot.rs_teach_move_start(rshd, j5, true);
        }

        private void btn_jointJ5Add_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ6De_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j6 = AuboRobot.teach_mode.JOINT6;
            AuboRobot.rs_teach_move_start(rshd, j6, false);
        }

        private void btn_jointJ6De_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_jointJ6Add_Click(object sender, EventArgs e)
        {

        }

        private void btn_jointJ6Add_MouseDown(object sender, MouseEventArgs e)
        {
            AuboRobot.teach_mode j6 = AuboRobot.teach_mode.JOINT6;
            AuboRobot.rs_teach_move_start(rshd, j6, true);
        }

        private void btn_jointJ6Add_MouseUp(object sender, MouseEventArgs e)
        {
            AuboRobot.rs_teach_move_stop(rshd);
        }

        private void btn_getSocketState_Click(object sender, EventArgs e)
        {
            timer2.Enabled = true;
        }

        private void timer2_Tick(object sender, EventArgs e)
        {
            bool socketConn=false;
            bool loginStatus=false ;
            int getSocketState = AuboRobot.rs_get_socket_status(rshd, ref socketConn);
            Console.WriteLine("getSocketState:" + getSocketState);
            if (getSocketState == RS_SUCC)
            {
                Console.WriteLine("socketConn:" + socketConn);
            }
            int getLoginState = AuboRobot.rs_get_login_status(rshd, ref loginStatus);
            Console.WriteLine("getLoginState:" + getLoginState);
            if (getLoginState == RS_SUCC)
            {
                Console.WriteLine("loginStatus:" + loginStatus);
            }

        }

        private void btn_moveArc_Click(object sender, EventArgs e)
        {
            //线程测试
            threadAic = new Thread(delegate ()
            {

                moveArcExample(rshd);
            });
            threadAic.Start();
        }

        private void btn_moveCir_Click(object sender, EventArgs e)
        {
            //线程测试
            threadCir = new Thread(delegate ()
            {

                moveCircleExample(rshd);
            });
            threadCir.Start();
        }

        private void btn_MoveP_Click(object sender, EventArgs e)
        {
            //线程测试
            threadMoveP = new Thread(delegate ()
            {

                movePExample(rshd);
            });
            threadMoveP.Start();
        }

        private void btn_U_DI_00_Click(object sender, EventArgs e)
        {

        }

        private void btn_lockTest_Click(object sender, EventArgs e)
        {

        }
        //相对偏移测试
        private void btn_OffsetTest_Click(object sender, EventArgs e)
        {
            //线程测试
            threadOffset = new Thread(delegate ()
            {

                offsetMoveTest(rshd);
            });
            threadOffset.Start();
        }

        private void btn_forward_Click(object sender, EventArgs e)
        {
            //正解
            Console.WriteLine("------------------------------------------------正解开始-----------------------------");
            AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();
            //关节角
            double[] joint =
            {
                0.000 / 180 * M_PI,
                0.000 / 180 * M_PI,
                79.161394 / 180 * M_PI,
                -13.118614 / 180 * M_PI,
                87.852939 / 180 * M_PI,
                 -20.026135/ 180 * M_PI
            };

            //正解
            AuboRobot.rs_forward_kin(rshd, joint, ref waypoint);
            //打印目标路点信息
            AuboRobot.PrintWaypoint(waypoint);
            Console.WriteLine("------------------------------------------------正解结束-----------------------------");

        }

        private void btn_inverse_Click(object sender, EventArgs e)
        {
            Console.WriteLine("------------------------------------------------逆解开始-----------------------------");
            AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();
            AuboRobot.Pos pos = new AuboRobot.Pos();
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.Ori ori = new AuboRobot.Ori();
            //位置 
            pos.x = 0.246138;
            pos.y = 0.046580;
            pos.z = 0.401088;
            //姿态（欧拉角）
            rpy.rx = -179.926758 * M_PI / 180;
            rpy.ry = 0.347005 * M_PI / 180;
            rpy.rz = 135.060440 * M_PI / 180;
            //欧拉角转四元数
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy, ref ori);
            //参考关节角
            double[] joint = {-0.000172/ 180 * M_PI,
                -7.291862/ 180 * M_PI,
                -75.6900/ 180 * M_PI,
                21.5900/ 180 * M_PI,
                -89.900/ 180 * M_PI,
                0.00/ 180 * M_PI };

            //逆解
            AuboRobot.rs_inverse_kin(rshd, joint, ref pos, ref ori, ref waypoint);
            //打印目标路点信息
            AuboRobot.PrintWaypoint(waypoint);
            Init_MoveParameter();
            //关节角
            double[] initPos = {
                -0.000172/ 180 * M_PI,
                -7.291862/ 180 * M_PI,
                -75.6900/ 180 * M_PI,
                21.5900/ 180 * M_PI,
                -89.900/ 180 * M_PI,
                0.00/ 180 * M_PI
            };
            //point.jointpos[0] * 180 / M_PI
            initPos[0] = waypoint.jointpos[0];
            initPos[1] = waypoint.jointpos[1];
            initPos[2] = waypoint.jointpos[2];
            initPos[3] = waypoint.jointpos[3];
            initPos[4] = waypoint.jointpos[4];
            initPos[5] = waypoint.jointpos[5];

            //关节运动
            int ret = AuboRobot.rs_move_line(rshd, initPos, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("关节运动-------------------成功!");

            }
            else
            {

                Console.Error.WriteLine("关节运动--------------------失败!错误码：{0}", ret);
            }

            Console.WriteLine("------------------------------------------------逆解结束-----------------------------");
        }

        private void btn_logout_Click(object sender, EventArgs e)
        {
            //创建机械臂控制上下文
            // result = rs_create_context(ref rshd);
            //result = AuboRobot.rs_create_context(ref rshd);
            //if (result == RS_SUCC)
            //{
            //    Console.WriteLine("创建上下文句柄成功！");
            //}
            //else
            //{
            //    Console.WriteLine("创建上下文句柄失败！错误码：{0}", result);

            //}

            //result = AuboRobot.rs_robot_shutdown(rshd);
            //if (result == RS_SUCC)
            //{
            //    Console.Out.WriteLine("下电成功！");

            //}
            //else
            //{
            //    Console.Error.WriteLine("下电失败！错误码： {0}", result);
            //    MessageBox.Show("下电失败！错误码：" + result);
            //}

            //机械臂退出登录
            // result = rs_logout(rshd);
            result = AuboRobot.rs_logout(rshd);
            if (result == RS_SUCC)
            {
                Console.Out.WriteLine("退出登录成功！");
                btn_login.BackColor = Color.White;
                btn_startUp.BackColor= Color.White;
            }
            else
            {
                Console.Error.WriteLine("退出登录失败！错误码： {0}", result);
                MessageBox.Show("退出登录失败！错误码：" + result);
            }

        }

        /// <summary>
        /// 回调函数，机械臂事件回调
        /// </summary>
        /// <param name="rshd"></param>
        static void realtimeEventInfoExample(UInt16 rshd)
        {
            while(true)
            {
                AuboRobot.rs_setcallback_robot_event(rshd, AuboRobot.RobotEventCallback, IntPtr.Zero);
                Thread.Sleep(100);
            }
            
        }
        /// <summary>
        /// 回调函数，机械臂实时关节信息回调
        /// </summary>
        /// <param name="rshd"></param>
        static void realtimeJointStatusExample(UInt16 rshd)
        {
            while(true)
            {
                AuboRobot.rs_setcallback_realtime_joint_status(rshd, AuboRobot.CurrentJointStatusCallback, IntPtr.Zero);
                Thread.Sleep(100);
            }
           
        }

        static void realtimeRoadPointExample(UInt16 rshd)
        {
            int ret = AuboRobot.rs_enable_push_realtime_roadpoint(rshd, true);
            while (true)
            {
                if (ret == RS_SUCC)
                {
                    AuboRobot.rs_setcallback_realtime_roadpoint(rshd, AuboRobot.CurrentPositionCallback, IntPtr.Zero);

                }
                else
                {
                   // Console.Error.WriteLine("调用 rs_enable_push_realtime_roadpoint 函数失 败！错误码：{ 0}", ret);
                }
                Thread.Sleep(100);

            }
            
        }

        static void getSocketStatus(UInt16 rshd)
        {
            Byte status = 2;
            
            int ret = AuboRobot.rs_get_socket_status(rshd, ref status);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("获取 socket 连接状态成功！socket 状态：{0}", status);
            }
            else
            {
                Console.Error.WriteLine("获取 socket 连接状态失败！错误码：{0}", ret);
            }
        }

        //回调函数测试
        private void btn_callBackTest_Click(object sender, EventArgs e)
        {
            //线程测试
            //threadCallBack = new Thread(delegate ()
            //{

            //    //RealtimeRoadPointExample(rshd);

            //    RealtimeRoadPointExample(rshd);
            //});
            //threadCallBack.Start();



            //线程测试
            threadCallBack = new Thread(delegate ()
            {

                //RealtimeRoadPointExample(rshd);

                // RealtimeRoadPointExample(rshd);
                // realtimeEventInfoExample(rshd);

                // realtimeRoadPointExample(rshd);
               // realtimeJointStatusExample(rshd);
               while(true)
                {
                    //getCurrentWaypoint(rshd);
                    getSocketStatus(rshd);
                    Thread.Sleep(100);
                }


            });
            threadCallBack.Start();



            //Thread.Sleep(50 * 1000);



            //int ret = AuboRobot.rs_enable_push_realtime_roadpoint(rshd, true);
            //if (ret == RS_SUCC)
            //{
            //    AuboRobot.rs_setcallback_realtime_roadpoint(rshd, AuboRobot.CurrentPositionCallback, IntPtr.Zero);
            //    //Thread.Sleep(5 * 1000);
            //}
            //else
            //{
            //    Console.Error.WriteLine("调用 rs_enable_push_realtime_roadpoint 函数失败！错误码：{ 0}", ret);
            //}
        }

        private void btn_loginTest_Click(object sender, EventArgs e)
        {

        }


        static void matrixMoveTest(UInt16 rshd)
        {
            //初始化运动参数
            Init_MoveParameter();
            //关节角
            double[] initPos = {
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                79.161394/ 180 * M_PI,
                -13.118614/ 180 * M_PI,
                87.852939/ 180 * M_PI,
                -20.026135/ 180 * M_PI
            };

            //点位1
            double[] point1 = {
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                79.161394/ 180 * M_PI,
                -13.118614/ 180 * M_PI,
                87.852939/ 180 * M_PI,
                -20.026135/ 180 * M_PI
            };
            //点位2
            double[] point2 = {
                0.00/ 180 * M_PI,
                -14.588193/ 180 * M_PI,
                62.524993/ 180 * M_PI,
                -15.166821/ 180 * M_PI,
                87.852939/ 180 * M_PI,
                -20.026135/ 180 * M_PI
            };
            //点位3
            double[] point3 = {
                10.107588/ 180 * M_PI,
                -12.680144/ 180 * M_PI,
                64.838009/ 180 * M_PI,
                -15.103102/ 180 * M_PI,
                88.286247/ 180 * M_PI,
                -9.922066/ 180 * M_PI
            };
            //点位4
            double[] point4 = {
                12.300588/ 180 * M_PI,
                2.132769/ 180 * M_PI,
                81.138010/ 180 * M_PI,
                -13.679627/ 180 * M_PI,
                88.387771/ 180 * M_PI,
                -7.730495/ 180 * M_PI
            };



            //关节运动到初始点位
            int ret = AuboRobot.rs_move_joint(rshd, initPos, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("初始点位运动成功!");

            }
            else
            {

                Console.Error.WriteLine("初始点位运动失败!：{0}", ret);
            }

            //直线运动到点位1
            ret = AuboRobot.rs_move_joint(rshd, point1, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("点位1运动成功!");

            }
            else
            {

                Console.Error.WriteLine("点位1运动失败!：{0}", ret);
            }
            //直线运动到点位2
            ret = AuboRobot.rs_move_joint(rshd, point2, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("点位2运动成功!");

            }
            else
            {

                Console.Error.WriteLine("点位2运动失败!：{0}", ret);
            }
            //直线运动到点位3
            ret = AuboRobot.rs_move_joint(rshd, point3, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("点位3运动成功!");

            }
            else
            {

                Console.Error.WriteLine("点位3运动失败!：{0}", ret);
            }
            //直线运动到点位4
            ret = AuboRobot.rs_move_joint(rshd, point4, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("点位4运动成功!");

            }
            else
            {

                Console.Error.WriteLine("点位4运动失败!：{0}", ret);
            }

            //直线运动到点位3
            ret = AuboRobot.rs_move_joint(rshd, point3, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("点位3运动成功!");

            }
            else
            {

                Console.Error.WriteLine("点位3运动失败!：{0}", ret);
            }

            //直线运动到点位2
            ret = AuboRobot.rs_move_joint(rshd, point2, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("点位2运动成功!");

            }
            else
            {

                Console.Error.WriteLine("点位2运动失败!：{0}", ret);
            }
            //直线运动到点位1
            ret = AuboRobot.rs_move_joint(rshd, point1, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("点位1运动成功!");

            }
            else
            {

                Console.Error.WriteLine("点位1运动失败!：{0}", ret);
            }
        }

        private void btn_matrix_Click(object sender, EventArgs e)
        {

            Console.WriteLine("矩阵测试2");

            //线程测试
            threadMatrix = new Thread(delegate ()
            {

                matrixMoveTest(rshd);
            });
            threadMatrix.Start();
            Console.WriteLine("矩阵测试2");



        }

        private void btn_targetPosMove_Click(object sender, EventArgs e)
        {
            Console.WriteLine("------------------------------------------------逆解开始-----------------------------");
            AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();
            AuboRobot.Pos pos = new AuboRobot.Pos();
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.Ori ori = new AuboRobot.Ori();


            string x = tb_targetX.Text.Trim();
            string y = tb_targetY.Text.Trim();
            string z = tb_targetZ.Text.Trim();
            string rx = tb_targetRX.Text.Trim();
            string ry = tb_targetRY.Text.Trim();
            string rz = tb_targetRZ.Text.Trim();




            //位置 
            pos.x = double.Parse(x);
            pos.y = double.Parse(y);
            pos.z = double.Parse(z);
            //姿态（欧拉角）
            rpy.rx = double.Parse(rx) * M_PI / 180;
            rpy.ry = double.Parse(ry) * M_PI / 180;
            rpy.rz = double.Parse(rz) * M_PI / 180;

            //欧拉角转四元数
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy, ref ori);
            //参考关节角
            double[] joint = { };
            //逆解
            AuboRobot.rs_inverse_kin(rshd, joint, ref pos, ref ori, ref waypoint);
            //打印目标路点信息
            AuboRobot.PrintWaypoint(waypoint);
            Init_MoveParameter();
            //关节角
            double[] initPos = {
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI
            };
            //point.jointpos[0] * 180 / M_PI
            initPos[0] = waypoint.jointpos[0];
            initPos[1] = waypoint.jointpos[1];
            initPos[2] = waypoint.jointpos[2];
            initPos[3] = waypoint.jointpos[3];
            initPos[4] = waypoint.jointpos[4];
            initPos[5] = waypoint.jointpos[5];

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("关节运动-------------------成功!");

            }
            else
            {

                Console.Error.WriteLine("关节运动--------------------失败!错误码：{0}", ret);
            }

            Console.WriteLine("------------------------------------------------逆解结束-----------------------------");
        }

        private void btn_offsetOnTargetPos_Click(object sender, EventArgs e)
        {
            Console.WriteLine("offsetX:" + offsetXData + " " + offsetYData + " " + offsetZData);

            Console.WriteLine("------------------------------------------------逆解开始-----------------------------");
            AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();
            AuboRobot.Pos pos = new AuboRobot.Pos();
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.Ori ori = new AuboRobot.Ori();


            string x = tb_targetX.Text.Trim();
            string y = tb_targetY.Text.Trim();
            string z = tb_targetZ.Text.Trim();
            string rx = tb_targetRX.Text.Trim();
            string ry = tb_targetRY.Text.Trim();
            string rz = tb_targetRZ.Text.Trim();




            //位置 
            pos.x = double.Parse(x);
            pos.y = double.Parse(y);
            pos.z = double.Parse(z);
            //姿态（欧拉角）
            rpy.rx = double.Parse(rx) * M_PI / 180;
            rpy.ry = double.Parse(ry) * M_PI / 180;
            rpy.rz = double.Parse(rz) * M_PI / 180;

            //欧拉角转四元数
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy, ref ori);
            //参考关节角
            double[] joint = { };
            //逆解
            AuboRobot.rs_inverse_kin(rshd, joint, ref pos, ref ori, ref waypoint);
            //打印目标路点信息
            AuboRobot.PrintWaypoint(waypoint);
            Init_MoveParameter();
            //关节角
            double[] initPos = {
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI,
                0.00/ 180 * M_PI
            };
            //point.jointpos[0] * 180 / M_PI
            initPos[0] = waypoint.jointpos[0];
            initPos[1] = waypoint.jointpos[1];
            initPos[2] = waypoint.jointpos[2];
            initPos[3] = waypoint.jointpos[3];
            initPos[4] = waypoint.jointpos[4];
            initPos[5] = waypoint.jointpos[5];

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos, true);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("关节运动-------------------成功!");

            }
            else
            {

                Console.Error.WriteLine("关节运动--------------------失败!错误码：{0}", ret);
            }

            Console.WriteLine("------------------------------------------------逆解结束-----------------------------");
            for (int i = 0; i < 10; i++)
            {
                //AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();

                //关节角
                double[] curWaypoint = { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 };

                ret = AuboRobot.rs_get_current_waypoint(rshd, ref waypoint);
                if (ret == RS_SUCC)
                {
                    Console.Out.WriteLine("获取当前路点信息成功！");
                    // AuboRobot.PrintWaypoint(waypoint);
                }
                else

                {
                    Console.Error.WriteLine("获取当前路点信息失败！错误码：{0}", ret);
                }
                //获取当前路点的关节角
                curWaypoint = waypoint.jointpos;

                //基于base坐标系的相对偏移
                //设置偏移
                AuboRobot.MoveRelative relativeOnBase = new AuboRobot.MoveRelative();

                float[] posOffset = new float[3] { 0, 0, 0 };
                relativeOnBase.enable = 1;
                relativeOnBase.pos = posOffset;
                relativeOnBase.pos[0] = offsetXData;
                relativeOnBase.pos[1] = offsetYData;
                relativeOnBase.pos[2] = offsetZData;
                AuboRobot.Rpy rpyRelative;
                AuboRobot.Ori oriRelative = new AuboRobot.Ori();
                rpyRelative.rx = 0.0;
                rpyRelative.ry = 0.0;
                rpyRelative.rz = 0.0;

                AuboRobot.rs_rpy_to_quaternion(rshd, ref rpyRelative, ref oriRelative);
                relativeOnBase.orientation = oriRelative;
                //偏移：法兰盘中心在基坐标系下沿X轴偏移0.1m
                AuboRobot.rs_set_relative_offset_on_base(rshd, ref relativeOnBase);
                int retMoveOffset = AuboRobot.rs_move_line(rshd, curWaypoint, true);
                Console.WriteLine("retMoveOffset:" + retMoveOffset);
            }


        }

        private void cb_ori_SelectedIndexChanged(object sender, EventArgs e)
        {
            Console.WriteLine("1");
            switch (cb_ori.SelectedItem.ToString())
            {
                case "X":
                    offsetXData = float.Parse(tb_offset.Text) / 1000;
                    offsetYData = 0;
                    offsetZData = 0;
                    Console.WriteLine("offsetX:" + offsetXData + " " + offsetYData + " " + offsetZData);
                    break;

                case "Y":
                    offsetXData = 0;
                    offsetYData = float.Parse(tb_offset.Text) / 1000;
                    offsetZData = 0;
                    Console.WriteLine("offsetX:" + offsetXData + " " + offsetYData + " " + offsetZData);
                    break;

                case "Z":
                    offsetXData = 0;
                    offsetYData = 0;
                    offsetZData = float.Parse(tb_offset.Text) / 1000;
                    Console.WriteLine("offsetX:" + offsetXData + " " + offsetYData + " " + offsetZData);
                    break;

                    


            }

        }

        private void cb_ori_TextChanged(object sender, EventArgs e)
        {
            Console.WriteLine("2");

            switch (cb_ori.SelectedItem.ToString())
            {
                case "X":
                    offsetXData = float.Parse(tb_offset.Text) / 1000;
                    offsetYData = 0;
                    offsetZData = 0;
                    Console.WriteLine("offsetX:" + offsetXData + " " + offsetYData + " " + offsetZData);
                    break;

                case "Y":
                    offsetXData = 0;
                    offsetYData = float.Parse(tb_offset.Text) / 1000;
                    offsetZData = 0;
                    Console.WriteLine("offsetX:" + offsetXData + " " + offsetYData + " " + offsetZData);
                    break;

                case "Z":
                    offsetXData = 0;
                    offsetYData = 0;
                    offsetZData = float.Parse(tb_offset.Text) / 1000;
                    Console.WriteLine("offsetX:" + offsetXData + " " + offsetYData + " " + offsetZData);
                    break;


            }
        }

        #region 自定义类
        class Student
        {
            public string Name { get; set; }
            public int Age { get; set; }
            public string Sex { get; set; }
        }
        #endregion

        private void label14_Click(object sender, EventArgs e)
        {
            
        }


        private  void mYCurrentPositionCallback(ref AuboRobot.wayPoint_S waypoint, IntPtr arg)
        {
            if (waypoint.Equals(null))
            {
                return;
            }else
            {
                PrintWaypointTest(waypoint);
            }
           


            //Thread.Sleep(5 * 1000);


        }

        //打印路点信息
        private  void PrintWaypointTest(AuboRobot.wayPoint_S point)
        {


            //tb_x.Text = thisWaypoint.cartPos.x.ToString();
            //tb_y.Text = thisWaypoint.cartPos.y.ToString();
            //tb_z.Text = thisWaypoint.cartPos.z.ToString();
            //rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            //tb_rx.Text = (rpy.rx * 180 / M_PI).ToString();
            //tb_ry.Text = (rpy.ry * 180 / M_PI).ToString();
            //tb_rz.Text = (rpy.rz * 180 / M_PI).ToString();

            //Invoke(new Action(() =>
            //{

            //    tb_j1.Text = (point.jointpos[0] * 180 / M_PI).ToString();
            //    tb_j2.Text = (point.jointpos[1] * 180 / M_PI).ToString();
            //    tb_j3.Text = (point.jointpos[2] * 180 / M_PI).ToString();
            //    tb_j4.Text = (point.jointpos[3] * 180 / M_PI).ToString();
            //    tb_j5.Text = (point.jointpos[4] * 180 / M_PI).ToString();
            //    tb_j6.Text = (point.jointpos[5] * 180 / M_PI).ToString();

            //}));
            Console.Out.WriteLine("---------------------------------------------------------------------------------------");
            Console.Out.WriteLine("位置：({0}，{1} ，{2}）", point.cartPos.x, point.cartPos.y, point.cartPos.z);
            Console.Out.WriteLine("姿态（四元数）：（{0}，{1}，{2}，{3}）", point.orientation.w, point.orientation.x, point.orientation.y, point.orientation.z);
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            UInt16 rshd = 0;
            AuboRobot.rs_quaternion_to_rpy(rshd, ref point.orientation, ref rpy);
            Console.Out.WriteLine("姿态（欧拉角）：（{0}，{1}，{2}）", rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI);
            Console.Out.WriteLine("关节1 = {0} 关节2 = {1} 关节3 = {2}", point.jointpos[0] * 180 / M_PI, point.jointpos[1] * 180 / M_PI, point.jointpos[2] * 180 / M_PI);
            Console.Out.WriteLine("关节4 = {0} 关节5 = {1} 关节6 = {2}", point.jointpos[3] * 180 / M_PI, point.jointpos[4] * 180 / M_PI, point.jointpos[5] * 180 / M_PI);
            Console.Out.WriteLine("---------------------------------------------------------------------------------------");
        }
        bool circle = true;
        //定时器用来获取回调函数结果
        private void timer3_Tick(object sender, EventArgs e)
        {
            if (circle == true)
            {
              
                circle = false;
            }
            Console.WriteLine("circle");
            
        }

        private void Form1_Load(object sender, EventArgs e)
        {
            int speed = 50;
            trackBar_value.Text = speed.ToString() + "%";
            comboBox1.SelectedIndex = 0;
            comboBox2.SelectedIndex = 0;

            tb_serverIP.Text = "192.168.2.144";
            tb_serverPort.Text = "8899";
            tb_serverPort.ReadOnly = true;

            jointTestData.Add(0.0);
            jointTestData.Add(0.0);
            jointTestData.Add(0.0);
            jointTestData.Add(0.0);
            jointTestData.Add(0.0);
            jointTestData.Add(0.0);
            jointListData.Add(jointTestData);
            jointListData.Clear();

            rb_moveJ.Checked=true;

            rb_modifyPos.Checked = true;
            //加载文件文件
           // readJointData(filename);


            loadJointData();

            cb_electricity.SelectedIndex= 0;
            cb_t00.SelectedIndex= 0;
            cb_t01.SelectedIndex= 0;
            cb_t02.SelectedIndex= 0;
            cb_t03.SelectedIndex= 0;



        }

        void loadJointData()
        {
            //添加列头到listview 
            listView_joint.Columns.Add("路点名", 100, HorizontalAlignment.Left);
            listView_joint.Columns.Add("关节角度信息", 200, HorizontalAlignment.Left);
            listView_joint.View = View.Details;

            string waypointName = "路点";
            ListViewItem item1 = new ListViewItem();
            //item1.SubItems.Add("子项1");
           // item1.SubItems.Add("子项2");
           // listView.Items.Add(item1);

            for (int i = 0; i < chkJointDataList.ToArray().Length; i++)
            {
                jointStr = "";
                waypointName = "路点";
               
                //将关节数据携带txt文本文件
                for (int j = 0; j < 5; j++)
                {
                    jointStr = jointStr + chkJointData.jointpos[j].ToString() + ",";
                }
                jointStr = jointStr + chkJointData.jointpos[5].ToString();

                waypointName = waypointName + i.ToString();

               
                item1.Text = waypointName;
                item1.SubItems.Add(jointStr);
                //listView_joint.Items.Add(item1);
               // listView_joint.Items.Add(item1);
           


            }

        }

        private void btn_base_to_user_Click(object sender, EventArgs e)
        {
            base_to_userExample(rshd);
        }

        private void comboBox1_SelectedIndexChanged(object sender, EventArgs e)
        {
            //int rs_set_teach_coord(RSHD rshd, const CoordCalibrate* teach_coord);
            AuboRobot.CoordCalibrate baseCoord=new AuboRobot.CoordCalibrate();
            AuboRobot.CoordCalibrate flangeCenterCoord = new AuboRobot.CoordCalibrate();
            AuboRobot.CoordCalibrate userCoord = new AuboRobot.CoordCalibrate();

            AuboRobot.JointRadian jointPara1 = new AuboRobot.JointRadian();
            AuboRobot.JointRadian jointPara2 = new AuboRobot.JointRadian();
            AuboRobot.JointRadian jointPara3 = new AuboRobot.JointRadian();
            



            int retBaseCoord = 0;
            int retflangeCenterCoord = 0;
            int retUserCoord = 0;
            AuboRobot.ToolInEndDesc toolDesc;
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.Ori ori = new AuboRobot.Ori();



            AuboRobot.CoordCalibrate user_coord = new AuboRobot.CoordCalibrate();
            user_coord = new AuboRobot.CoordCalibrate();
            IntPtr pt_user_coord = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(AuboRobot.CoordCalibrate)));
            user_coord = (AuboRobot.CoordCalibrate)Marshal.PtrToStructure(pt_user_coord, typeof(AuboRobot.CoordCalibrate));
            user_coord.coordType = AuboRobot.WorldCoordinate;
            user_coord.methods = AuboRobot.Origin_AnyPointOnPositiveXAxis_AnyPointOnFirstQuadrantOfXOYPlane;


            switch (comboBox1.SelectedItem.ToString()) //获取选择的内容
            {

                case "Base":
                    //以Base坐标系进行示教
                    //flange_center_on_base(rshd);
                    baseCoord.coordType = AuboRobot.BaseCoordinate;
                    retBaseCoord = AuboRobot.rs_set_teach_coord(rshd, ref baseCoord);
                    Console.WriteLine("retBaseCoord: " + retBaseCoord);

                    isBaseCoord = true;
                    isFlangeCenterCoord = false;
                    isUserCoord = false;
                    break;

                case "flange_center":
                    //以法兰盘坐标系进行示教
                    flangeCenterCoord.coordType = AuboRobot.EndCoordinate;
                    toolDesc.cartPos.x = 0.0;
                    toolDesc.cartPos.y = 0.0;
                    toolDesc.cartPos.z = 0.0;
                    toolDesc.orientation.w = 1.0;
                    toolDesc.orientation.x = 0.0;
                    toolDesc.orientation.y = 0.0;
                    toolDesc.orientation.z = 0.0;
                    flangeCenterCoord.toolDesc = toolDesc;
                    retflangeCenterCoord = AuboRobot.rs_set_teach_coord(rshd, ref flangeCenterCoord);
                    Console.WriteLine("retflangeCenterCoord: " + retflangeCenterCoord);

                    // base_to_userExample(rshd); ;
                    isBaseCoord = false;
                    isFlangeCenterCoord = true;
                    isUserCoord = false;
                    break;
                case "user":
                    //以用户坐标系进行示教
                    
                    
                    jointPara1.jointRadian = new double[] { -0.000401 / 180 * M_PI, -18.282682 / 180 * M_PI, -144.410675 / 180 * M_PI, -100.986389 / 180 * M_PI, -89.000392 / 180 * M_PI, 0.000057 / 180 * M_PI };
                    jointPara2.jointRadian = new double[] { -0.000401 / 180 * M_PI, -18.569104 / 180 * M_PI, -106.577541 / 180 * M_PI, -92.866892 / 180 * M_PI, -89.999354 / 180 * M_PI, 0.000057 / 180 * M_PI };
                    jointPara3.jointRadian = new double[] { -7.421752 / 180 * M_PI, -20.690135 / 180 * M_PI, -107.929604 / 180 * M_PI, -92.138835 / 180 * M_PI, -82.604759 / 180 * M_PI, 0.632144 / 180 * M_PI };
                    user_coord.jointPara = new AuboRobot.JointRadian[3] { jointPara1, jointPara2, jointPara3 };
                    user_coord.jointPara[0] = jointPara1;
                    user_coord.jointPara[1] = jointPara2;
                    user_coord.jointPara[2] = jointPara3;

                    rpy.rx = 180.0 / 180 * M_PI;
                    rpy.ry = 0.0;
                    rpy.rz = 90.0 / 180 * M_PI;

                    AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy, ref ori);
                    user_coord.toolDesc.cartPos.x = 0.0;
                    user_coord.toolDesc.cartPos.y = 0.0;
                    user_coord.toolDesc.cartPos.z = 0.1;

                    user_coord.toolDesc.orientation = ori;

                    retUserCoord = AuboRobot.rs_set_teach_coord(rshd, ref user_coord);
                    Console.WriteLine("retCoord: " + retUserCoord);

                    // base_to_userExample(rshd); ;
                    isBaseCoord = false;
                    isFlangeCenterCoord = false;
                    isUserCoord = true;
                    break;
            }
        }

        private void label18_Click(object sender, EventArgs e)
        {

        }

        private void comboBox2_SelectedIndexChanged(object sender, EventArgs e)
        {
            AuboRobot.Rpy rpy = new AuboRobot.Rpy();
            AuboRobot.Ori ori = new AuboRobot.Ori();
            switch (comboBox2.SelectedItem.ToString()) //获取选择的内容
            {

                case "flange_center":
                    AuboRobot.ToolInEndDesc flange_center;
                    flange_center.cartPos.x = 0.0;
                    flange_center.cartPos.y = 0.0;
                    flange_center.cartPos.z = 0.0;
                    flange_center.orientation.w = 1.0;
                    flange_center.orientation.x = 0.0;
                    flange_center.orientation.y = 0.0;
                    flange_center.orientation.z = 0.0;
                    int retFlangeCenter = AuboRobot.rs_set_tool_kinematics_param(rshd, ref flange_center);
                    Console.WriteLine("retFlangeCenter:"+ retFlangeCenter);


                    //int robotServiceSetToolKinematicsParam(const aubo_robot_namespace::ToolKinematicsParam & toolKinematicsParam);

                    //flange_center_on_base(rshd);
                    isFlangeCenterTool = true;
                    isTcpTool = false; 
                    break;

                case "tool":
                    AuboRobot.ToolInEndDesc tool;
                    tool.cartPos.x = 0.0;
                    tool.cartPos.y = 0.0;
                    tool.cartPos.z = 0.1;
                    rpy.rx = 180.0 / 180 * M_PI;
                    rpy.ry = 0.0 / 180 * M_PI;
                    rpy.rz = 90.0 / 180 * M_PI;
                    AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy, ref ori);


                    tool.orientation.w = ori.w;
                    tool.orientation.x = ori.x;
                    tool.orientation.y = ori.y;
                    tool.orientation.z = ori.z;
                    int retTool = AuboRobot.rs_set_tool_kinematics_param(rshd, ref tool);
                    Console.WriteLine("retTool:" + retTool);
                    //tool_on_base(rshd)
                    isFlangeCenterTool = false;
                    isTcpTool = true;
                    break;



            }
        }

        private void trackBar1_Scroll(object sender, EventArgs e)
        {
            trackBar_value.Text = trackBar1.Value.ToString()+"%";
            //修改全局速度
            Init_MoveParameter_Speed(trackBar1.Value);
        }

        private void btn_initPosOri_MouseDown(object sender, MouseEventArgs e)
        {
            //线程测试
            threadInitPosOri = new Thread(delegate ()
            {

                initPosOriMove(rshd);
            });
            threadInitPosOri.Start();
        }

        private void btn_initPosOri_MouseUp(object sender, MouseEventArgs e)
        {
            int retMoveStop = AuboRobot.rs_move_fast_stop(rshd);
            //int retMoveStop = AuboRobot.rs_move_stop(rshd);
            Console.WriteLine("retMoveStop:" + retMoveStop);
        }

        private void btn_zeroPosOri_MouseDown(object sender, MouseEventArgs e)
        {
            threadZeroPosOri = new Thread(delegate ()
            {

                zeroPosOriMove(rshd);
            });
            threadZeroPosOri.Start();
        }

        private void btn_zeroPosOri_MouseUp(object sender, MouseEventArgs e)
        {
            int retMoveStop = AuboRobot.rs_move_fast_stop(rshd);
            //int retMoveStop = AuboRobot.rs_move_stop(rshd);
            Console.WriteLine("retMoveStop:" + retMoveStop);
        }

        private void btn_initPosOri_Click(object sender, EventArgs e)
        {

        }

        private void btn_toolCalib_Click(object sender, EventArgs e)
        {
            //public static extern int rs_tool_calibration(UInt16 rshd, ref ToolCalibrate toolCalibrate, ref ToolInEndDesc toolInEndDesc);
            AuboRobot.ToolCalibrate toolCalibrate;
            AuboRobot.ToolInEndDesc toolInEndDesc;
            // int[][] newArray = { new int[] { 1, 3, 4, 5, 6 }, new int[] { 2, 4, 6, 8, 2 } };
            AuboRobot.wayPoint_S waypoint1;
            AuboRobot.wayPoint_S waypoint2;
            AuboRobot.wayPoint_S waypoint3;
            AuboRobot.wayPoint_S waypoint4;

            AuboRobot.wayPoint_S waypoint5;
            AuboRobot.wayPoint_S waypoint6;
            AuboRobot.wayPoint_S waypoint7;

            


            waypoint1.jointpos = new double[] { -0.000172 / 180 * M_PI, -8.423339 / 180 * M_PI, -81.027975 / 180 * M_PI, 17.378440 / 180 * M_PI, -89.999982 / 180 * M_PI, -0.000458 / 180 * M_PI };
            waypoint2.jointpos = new double[] { 20.091681 / 180 * M_PI, 1.398132 / 180 * M_PI, -77.324210 / 180 * M_PI, 35.702317 / 180 * M_PI, -138.524405 / 180 * M_PI, 31.243102 / 180 * M_PI };
            waypoint3.jointpos = new double[] { -23.095813 / 180 * M_PI, -10.057128 / 180 * M_PI, -87.705626 / 180 * M_PI, 42.435258 / 180 * M_PI, -40.374560 / 180 * M_PI, -37.269873 / 180 * M_PI };
            waypoint4.jointpos = new double[] { -9.093699 / 180 * M_PI, -29.446820 / 180 * M_PI, -115.848232 / 180 * M_PI, -41.590203 / 180 * M_PI, -71.379657 / 180 * M_PI, 32.661630 / 180 * M_PI };

            waypoint5.jointpos = new double[] { -0.000172 / 180 * M_PI, -8.423339 / 180 * M_PI, -81.027975 / 180 * M_PI, 17.378440 / 180 * M_PI, -89.999982 / 180 * M_PI, -0.000458 / 180 * M_PI };
            waypoint6.jointpos = new double[] { -0.000286 / 180 * M_PI, -26.319905 / 180 * M_PI,  -92.757452/ 180 * M_PI, 23.545528 / 180 * M_PI, -89.999982 / 180 * M_PI, -0.000573 / 180 * M_PI };
            waypoint7.jointpos = new double[] { -14.290083 / 180 * M_PI, -29.196668 / 180 * M_PI, -94.038188 / 180 * M_PI, 25.142019 / 180 * M_PI, -89.995802 / 180 * M_PI, -14.290369 / 180 * M_PI };


            waypoint1.cartPos.x = -0.580788;
            waypoint1.cartPos.y = -0.201298;
            waypoint1.cartPos.z = 0.888779;
            AuboRobot.Rpy rpy1;
            AuboRobot.Ori ori1;
            rpy1.rx = 179.983/180*M_PI;
            rpy1.ry = 0.0;
            rpy1.rz = -90 / 180 * M_PI;
            ori1.w = 1.0;
            ori1.x = 0.0;
            ori1.y = 0.0;
            ori1.z = 0.0;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy1, ref ori1);
            waypoint1.orientation = ori1;
            //waypoint1.orientation.w = 1;
            //waypoint1.orientation.x = 0;
            //waypoint1.orientation.y = 0;
            //waypoint1.orientation.z = 0;

            waypoint2.cartPos.x = -0.586406;
            waypoint2.cartPos.y = -0.353850;
            waypoint2.cartPos.z = 0.828375;
            AuboRobot.Rpy rpy2;
            AuboRobot.Ori ori2;
            rpy2.rx = 179.971/ 180 * M_PI;
            rpy2.ry = 52.91 / 180 * M_PI;
            rpy2.rz = -90.0 / 180 * M_PI;
            ori2.w = 1.0;
            ori2.x = 0.0;
            ori2.y = 0.0;
            ori2.z = 0.0;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy2, ref ori2);
            waypoint2.orientation = ori2;
            //waypoint2.orientation.w = 1;
            //waypoint2.orientation.x = 0;
            //waypoint2.orientation.y = 0;
            //waypoint2.orientation.z = 0;

            waypoint3.cartPos.x = -0.596217;
            waypoint3.cartPos.y = 0-0.042435;
            waypoint3.cartPos.z = 0.824642;
            AuboRobot.Rpy rpy3;
            AuboRobot.Ori ori3;
            rpy3.rx = 179.97 / 180 * M_PI;
            rpy3.ry = -55.90 / 180 * M_PI;
            rpy3.rz = -89.975 / 180 * M_PI;
            ori3.w = 1.0;
            ori3.x = 0.0;
            ori3.y = 0.0;
            ori3.z = 0.0;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy3, ref ori3);
            waypoint3.orientation = ori3;
            //waypoint3.orientation.w = 1;
            //waypoint3.orientation.x = 0;
            //waypoint3.orientation.y = 0;
            //waypoint3.orientation.z = 0;

            waypoint4.cartPos.x = -0.447998;
            waypoint4.cartPos.y = -0.162551;
            waypoint4.cartPos.z = 0.774243;
            AuboRobot.Rpy rpy4;
            AuboRobot.Ori ori4;
            rpy4.rx = 144.53 / 180 * M_PI;
            rpy4.ry = -34.91 / 180 * M_PI;
            rpy4.rz = -112.465 / 180 * M_PI;
            ori4.w = 1.0;
            ori4.x = 0.0;
            ori4.y = 0.0;
            ori4.z = 0.0;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy4, ref ori4);
            waypoint4.orientation = ori4;
            //waypoint4.orientation.w = 1;
            //waypoint4.orientation.x = 0;
            //waypoint4.orientation.y = 0;
            //waypoint4.orientation.z = 0;

            waypoint5.cartPos.x = -0.580788;
            waypoint5.cartPos.y = -0.201298;
            waypoint5.cartPos.z = 0.888779;
            AuboRobot.Rpy rpy5;
            AuboRobot.Ori ori5;
            rpy5.rx = 179.983 / 180 * M_PI;
            rpy5.ry = -0.0 / 180 * M_PI;
            rpy5.rz = -90.0 / 180 * M_PI;
            ori5.w = 1.0;
            ori5.x = 0.0;
            ori5.y = 0.0;
            ori5.z = 0.0;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy5, ref ori5);
            waypoint5.orientation = ori5;

            //waypoint5.orientation.w = 1;
            //waypoint5.orientation.x = 0;
            //waypoint5.orientation.y = 0;
            //waypoint5.orientation.z = 0;

            waypoint6.cartPos.x = -0.366093;
            waypoint6.cartPos.y = -0.201298;
            waypoint6.cartPos.z = 0.889206;
            AuboRobot.Rpy rpy6;
            AuboRobot.Ori ori6;
            rpy6.rx = 179.983 / 180 * M_PI;
            rpy6.ry = 0.0;
            rpy6.rz = -90.0/ 180 * M_PI;
            ori6.w = 1.0;
            ori6.x = 0.0;
            ori6.y = 0.0;
            ori6.z = 0.0;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy6, ref ori6);
            waypoint6.orientation = ori6;
            //waypoint6.orientation.w = 1;
            //waypoint6.orientation.x = 0;
            //waypoint6.orientation.y = 0;
            //waypoint6.orientation.z = 0;



            

            waypoint7.cartPos.x = -0.369913;
            waypoint7.cartPos.y = -0.113513;
            waypoint7.cartPos.z = 0.889315;
            AuboRobot.Rpy rpy7;
            AuboRobot.Ori ori7;
            rpy7.rx = 179.983 / 180 * M_PI;
            rpy7.ry = 0.0;
            rpy7.rz = -90.0 / 180 * M_PI;
            ori7.w = 1.0;
            ori7.x = 0.0;
            ori7.y = 0.0;
            ori7.z = 0.0;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy7, ref ori7);
            waypoint7.orientation = ori7;
            ////waypoint7.orientation.w = 1;
            ////waypoint7.orientation.x = 0;
            ////waypoint7.orientation.y = 0;
            ////waypoint7.orientation.z = 0;

            AuboRobot.wayPoint_S[] posCalibData = { waypoint1, waypoint2, waypoint3, waypoint4 };
            AuboRobot.wayPoint_S[] oriCalibData = { waypoint5, waypoint6, waypoint7};

            //wayPoint_S[] oriCalibrateWaypoint;
            toolCalibrate.posCalibrateNum = 4;
            toolCalibrate.posCalibrateWaypoint = posCalibData;

            toolCalibrate.oriCalibrateNum = 3;
            toolCalibrate.oriCalibrateWaypoint = oriCalibData;

            toolCalibrate.CalibMethod = 0;

            toolInEndDesc.cartPos.x = 0;
            toolInEndDesc.cartPos.y = 0;
            toolInEndDesc.cartPos.z = 0;
            toolInEndDesc.orientation.w = 1;
            toolInEndDesc.orientation.x = 0;
            toolInEndDesc.orientation.y = 0;
            toolInEndDesc.orientation.z = 0;

            int retToolCalib=AuboRobot.rs_tool_calibration(rshd, ref toolCalibrate, ref  toolInEndDesc);
            Console.WriteLine("retToolCalib:"+ retToolCalib);
            if(retToolCalib== RS_SUCC)
            {
                btn_toolCalib.BackColor = Color.Green;
            }


        }

        private void btn_coorCalib_Click(object sender, EventArgs e)
        {
           
           // //用户坐标系
           // //AuboRobot.CoordCalibrate user_coord = new AuboRobot.CoordCalibrate();
           // user_coord = new AuboRobot.CoordCalibrate();
           // IntPtr pt_user_coord = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(AuboRobot.CoordCalibrate)));
           // user_coord = (AuboRobot.CoordCalibrate)Marshal.PtrToStructure(pt_user_coord, typeof(AuboRobot.CoordCalibrate));
           // user_coord.coordType = AuboRobot.WorldCoordinate;
           // user_coord.methods = 3;

           // //waypoint5.jointpos = new double[] { -0.000172 / 180 * M_PI, -8.423339 / 180 * M_PI, -81.027975 / 180 * M_PI, 17.378440 / 180 * M_PI, -89.999982 / 180 * M_PI, -0.000458 / 180 * M_PI };
           //// waypoint6.jointpos = new double[] { -0.000286 / 180 * M_PI, -26.319905 / 180 * M_PI, -92.757452 / 180 * M_PI, 23.545528 / 180 * M_PI, -89.999982 / 180 * M_PI, -0.000573 / 180 * M_PI };
           //// waypoint7.jointpos = new double[] { -14.290083 / 180 * M_PI, -29.196668 / 180 * M_PI, -94.038188 / 180 * M_PI, 25.142019 / 180 * M_PI, -89.995802 / 180 * M_PI, -14.290369 / 180 * M_PI };

           // user_coord.jointPara[0].jointRadian[0] = -0.000172 * M_PI / 180.0;
           // user_coord.jointPara[0].jointRadian[1] = -8.423339 * M_PI / 180.0;
           // user_coord.jointPara[0].jointRadian[2] = -81.027975 * M_PI / 180.0;
           // user_coord.jointPara[0].jointRadian[3] = 17.378440 * M_PI / 180.0;
           // user_coord.jointPara[0].jointRadian[4] = -89.999982 * M_PI / 180.0;
           // user_coord.jointPara[0].jointRadian[5] = -0.000458 * M_PI / 180.0;

           // user_coord.jointPara[1].jointRadian[0] = -0.000286 * M_PI / 180.0;
           // user_coord.jointPara[1].jointRadian[1] = -26.319905 * M_PI / 180.0;
           // user_coord.jointPara[1].jointRadian[2] = -92.757452 * M_PI / 180.0;
           // user_coord.jointPara[1].jointRadian[3] = 23.545528 * M_PI / 180.0;
           // user_coord.jointPara[1].jointRadian[4] = -89.999982 * M_PI / 180.0;
           // user_coord.jointPara[1].jointRadian[5] = -0.000573 * M_PI / 180.0;

           // user_coord.jointPara[2].jointRadian[0] = -14.290083 * M_PI / 180.0;
           // user_coord.jointPara[2].jointRadian[1] = -29.196668 * M_PI / 180.0;
           // user_coord.jointPara[2].jointRadian[2] = -94.038188 * M_PI / 180.0;
           // user_coord.jointPara[2].jointRadian[3] = 25.142019 * M_PI / 180.0;
           // user_coord.jointPara[2].jointRadian[4] = -89.995802 * M_PI / 180.0;
           // user_coord.jointPara[2].jointRadian[5] = -14.290369 * M_PI / 180.0;
           // //坐标系的工具参数
           // AuboRobot.ToolInEndDesc tooluser_coord = new AuboRobot.ToolInEndDesc();
           
           // tooluser_coord.cartPos.x = 0.0;
           // tooluser_coord.cartPos.y = 0.0;
           // tooluser_coord.cartPos.z = 0.1;

           // AuboRobot.Rpy rpy;
           // AuboRobot.Ori ori;
           // rpy.rx = -179.999 / 180 * M_PI;
           // rpy.ry = 0.0169 / 180 * M_PI;
           // rpy.rz = -90.0 / 180 * M_PI;
           // ori.w = 1.0;
           // ori.x = 0.0;
           // ori.y = 0.0;
           // ori.z = 0.0;
           // AuboRobot.rs_rpy_to_quaternion(rshd, ref rpy, ref ori);
           // tooluser_coord.orientation = ori;

           // //tooluser_coord.orientation.w = 1;
           // //tooluser_coord.orientation.x = 0;
           // //tooluser_coord.orientation.y = 0;
           // //tooluser_coord.orientation.z = 0;
           // user_coord.toolDesc = tooluser_coord;
           // //int retCheckUserCoord=AuboRobot.rs_check_user_coord(rshd, ref  user_coord);
           // //if(retCheckUserCoord== RS_SUCC)
           // //{
           // //    int retSetUserCoord = AuboRobot.rs_set_user_coord(rshd, ref user_coord);
           // //    if(retSetUserCoord== RS_SUCC)
           // //    {
           // //        btn_coorCalib.BackColor = Color.Green;


           // //    }
           // //}


            AuboRobot.CoordCalibrate user_coord1 = new AuboRobot.CoordCalibrate();
            user_coord1.coordType = AuboRobot.EndCoordinate;
            user_coord1.toolDesc.cartPos.x = 0;
            user_coord1.toolDesc.cartPos.y = 0;
            user_coord1.toolDesc.cartPos.z = 0.1;

            user_coord1.toolDesc.orientation.w = 1;
            user_coord1.toolDesc.orientation.x = 0;
            user_coord1.toolDesc.orientation.y = 0;
            user_coord1.toolDesc.orientation.z = 0;

            //AuboRobot.teach_mode.
            int retSetTeachCoord = AuboRobot.rs_set_teach_coord(rshd, ref user_coord1);
            Console.WriteLine("retSetTeachCoord:" + retSetTeachCoord);
            if (retSetTeachCoord==0)
            {
                btn_coorCalib.BackColor = Color.Green;
            }


            ////工具描述
            //AuboRobot.ToolInEndDesc tool_desc = new AuboRobot.ToolInEndDesc();
            //tool_desc.cartPos.x = 0.2;
            //tool_desc.cartPos.y = 0.1;
            //tool_desc.cartPos.z = 0.1;
            //tool_desc.orientation.w = 1;
            //tool_desc.orientation.x = 0;
            //tool_desc.orientation.y = 0;
            //tool_desc.orientation.z = 0;
        }

        private void btn_oriRZDe_Click(object sender, EventArgs e)
        {

        }

        private void btn_U_DO_02_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_02.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_03_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_03.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_04_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_04.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_05_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_05.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_06_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_06.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_07_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_07.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_10_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_10.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_11_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_11.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_12_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_12.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_13_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_13.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_14_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_14.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_15_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_15.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_16_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_16.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void btn_U_DO_17_Click(object sender, EventArgs e)
        {
            btn_send.Visible = true;
            tb_value.Visible = false;
            lb_IOName.Text = btn_U_DO_17.Text;
            rb_low.Visible = true;
            rb_high.Visible = true;
            rb_low.Select();
        }

        private void Form1_Resize(object sender, EventArgs e)
        {
            float newx = (this.Width) / width;
            float newy = (this.Height) / height;
            setControls(newx, newy, this);

        }
        public ushort macTargetPosBufferSize;         // mac缓冲器长度          预留
        public ushort macTargetPosDataSize;           // mac缓冲器有效数据长度   预留
        AuboRobot.RobotDiagnosis robotDiagnosisInfo = new AuboRobot.RobotDiagnosis();

        double[,] jointData = new double[12, 6] { { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 }, { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 }, { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 }, { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 }, { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 }, { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 },{ 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 },{ 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 },{ 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 },{ 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 } , { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 } , { 0.00, 0.00, 0.00, 0.00, 0.00, 0.00 } };
        /// <summary>
        /// 
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private void btn_tcp2can_Click(object sender, EventArgs e)
        {
            //线程测试
            threadTcpToCanbus = new Thread(delegate ()
            {
                //运动到零点位置

                //关节角
                //确保进入透传之前是处于退出透传状态
                int retLeaveTcpToCanbus = AuboRobot.rs_leave_tcp2canbus_mode(rshd);
                Console.WriteLine("retLeaveTcpToCanbus:" + retLeaveTcpToCanbus);

                double[] zeroPos0 = {
                0.00,
                0.00,
                0.00,
                0.00,
                0.00,
                0.00
            };

                //关节运动
                int ret = AuboRobot.rs_move_joint(rshd, zeroPos0, true);
                if (ret == RS_SUCC)
                {
                    Console.Out.WriteLine("运动零点位姿过程中。。。!");

                }
                else
                {

                    Console.Error.WriteLine("运动零点运动失败!：{0}", ret);
                }


                int retEnterTcpToCanbus = AuboRobot.rs_enter_tcp2canbus_mode(rshd);
                Console.WriteLine("retEnterTcpToCanbus:" + retEnterTcpToCanbus);



                //一次发送单个点位
                //int step = 0;
                //while (step < 1000)
                //{
                //    ret = AuboRobot.rs_get_diagnosis_info(rshd, ref robotDiagnosisInfo);
                //    Console.WriteLine("retGetDiagnosis:" + ret);
                //    macTargetPosDataSize = robotDiagnosisInfo.macTargetPosDataSize;
                //    Console.WriteLine("macTargetPosDataSize:" + macTargetPosDataSize);

                //    if (ret != RS_SUCC)
                //    {
                //        Console.WriteLine("获取诊断信息失败:" + ret);

                //    }
                //    double[] joint = { 0.001745 * step, 0, 0, 0, 0, 0 };
                //    robotDiagnosisInfo.macTargetPosDataSize < 90
                //    if (robotDiagnosisInfo.macTargetPosDataSize < 90)
                //    {
                //        int retSetWaypointToCanbus = AuboRobot.rs_set_waypoint_to_canbus(rshd, joint);
                //        if (retSetWaypointToCanbus == RS_SUCC)
                //        {
                //            step++;
                //        }

                //    }
                //    Thread.Sleep(1);
                //}



                //一次下发多个点位数据
                int step = 1;
                while (step < 1000)
                {
                    string currentDate = DateTime.Now.ToString("HH:mm:ss:fff:ffffff");
                    Console.WriteLine("currentDate:" + currentDate);
                    for (int i = 0; i < 12; i++)
                    {
                        jointData[i, 0] = step * 0.001745;
                    }
                    //获取诊断信息必须要连接真实机械臂
                    ret = AuboRobot.rs_get_diagnosis_info(rshd, ref robotDiagnosisInfo);
                    Console.WriteLine("retGetDiagnosis:" + ret);
                    if (ret != RS_SUCC)
                    {
                        Console.WriteLine("获取诊断信息失败:" + ret);

                    }else if(ret == RS_SUCC)
                    {
                        Console.WriteLine("获取诊断成功:" + ret);
                        macTargetPosDataSize = robotDiagnosisInfo.macTargetPosDataSize;
                        Console.WriteLine("macTargetPosDataSize:" + macTargetPosDataSize);

                        

                        if (robotDiagnosisInfo.macTargetPosDataSize < 200)
                        {
                            int retSetWaypointToCanbus = AuboRobot.rs_set_waypoint_to_canbus(rshd, jointData, 12);
                            if (retSetWaypointToCanbus == RS_SUCC)
                            {
                                step = step + 12;
                            }
                        }
                    }
                    Thread.Sleep(3);
                    string currentDate1 = DateTime.Now.ToString("HH:mm:ss:fff:ffffff");
                    Console.WriteLine("currentDate1:" + currentDate1);
                }
                retLeaveTcpToCanbus = AuboRobot.rs_leave_tcp2canbus_mode(rshd);
                Console.WriteLine("retLeaveTcpToCanbus:" + retLeaveTcpToCanbus);

                ret = AuboRobot.rs_move_joint(rshd, zeroPos0, true);
                if (ret == RS_SUCC)
                {
                    Console.Out.WriteLine("运动零点位姿过程中。。。!");
                    step = 1;

                }
                else
                {

                    Console.Error.WriteLine("运动零点运动失败!：{0}", ret);
                }

            });
            threadTcpToCanbus.Start();

        }
        //从文本文件中读取点位数据
        void readJointData(string file)
        {
            StreamReader sr = new StreamReader(file, Encoding.Default);
            string line = "";
            while ((line = sr.ReadLine()) != null)
            {
                Console.WriteLine("");
                if (line != null && line !="")
                {
                    Console.WriteLine("");
                    string str = "0,0,0,0,0,0";
                    string[] strArr = new string[] { "0", "0", "0", "0", "0", "0" };//根据逗号分隔字符串str
                    double[] joints = new double[] { 0, 0, 0, 0, 0, 0 };//根据逗号分隔字符串str
                    str = line;
                    strArr = str.Split(',');
                    if (strArr != null)
                    {
                        for (int j = 0; j < 6; j++)
                        {
                            joints[j] = double.Parse(strArr[j]);
                        }
                        chkJointData.jointpos = joints;
                        chkJointData.moveStyle = 0;
                        chkJointDataList.Add(chkJointData);
                    }
                   
                }
            }

            Console.WriteLine("init size:"+ chkJointDataList.ToArray().Length);
            waypointCount = chkJointDataList.ToArray().Length;
            tb_waypointCount.Text = waypointCount.ToString();



        }

        private void btn_addWaypoint_Click(object sender, EventArgs e)
        {
            StreamWriter sw = new StreamWriter(filename, true);
            AuboRobot.wayPoint_S waypoint = new AuboRobot.wayPoint_S();
            jointStr = "";
            int ret = AuboRobot.rs_get_current_waypoint(rshd, ref waypoint);

            if(rb_moveJ.Checked==true)
            {
                chkJointData.moveStyle = 0;
            }
            if(rb_moveL.Checked==true)
            {
                chkJointData.moveStyle = 1;
            }

            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("获取当前路点信息成功！");
                chkJointData.jointpos = waypoint.jointpos;
                

                chkJointDataList.Add(chkJointData);
                //jointTestData.Clear();
                //jointTestData.Add(waypoint.jointpos[0]);
                //jointTestData.Add(waypoint.jointpos[1]);
                //jointTestData.Add(waypoint.jointpos[2]);
                //jointTestData.Add(waypoint.jointpos[3]);
                //jointTestData.Add(waypoint.jointpos[4]);
                //jointTestData.Add(waypoint.jointpos[5]);
                //jointListData.Add(jointTestData);

                waypointCount = chkJointDataList.ToArray().Length;
                tb_waypointCount.Text = waypointCount.ToString();

                //将关节数据携带txt文本文件
                for(int i=0;i<5;i++)
                {
                    jointStr = jointStr + chkJointData.jointpos[i].ToString()+",";
                }
                jointStr = jointStr + chkJointData.jointpos[5].ToString();
                sw.WriteLine(jointStr);
                sw.Close();




                //AuboRobot.PrintWaypoint(waypoint);
            }
            else
            {
                Console.Error.WriteLine("获取当前路点信息失败！错误码：{0}", ret);
            }
        }

        private void btn_deleteWaypoint_Click(object sender, EventArgs e)
        {
            File.WriteAllText(filename, string.Empty);
           
           
            jointStr = "";
            if (chkJointDataList.ToArray().Length>0)
            {
                
                chkJointDataList.RemoveAt(chkJointDataList.ToArray().Length - 1);
                Console.WriteLine(""+ chkJointDataList.ToArray().Length);  
                for (int i=0;i< chkJointDataList.ToArray().Length;i++)
                {
                    jointStr = "";
                    StreamWriter sw = new StreamWriter(filename, true);
                    //将关节数据携带txt文本文件
                    for (int j = 0; j < 5; j++)
                    {
                        jointStr = jointStr + chkJointData.jointpos[j].ToString() + ",";
                    }
                    jointStr = jointStr + chkJointData.jointpos[5].ToString();
                   
                    sw.WriteLine(jointStr);
                    sw.Close();
                }
                
                waypointCount = chkJointDataList.ToArray().Length;
                tb_waypointCount.Text = waypointCount.ToString();
            }
            else
            {
                
            }
        }

        private void btn_clearWaypoint_Click(object sender, EventArgs e)
        {
            chkJointDataList.Clear();
            waypointCount = chkJointDataList.ToArray().Length;
            tb_waypointCount.Text = waypointCount.ToString();
            File.WriteAllText(filename, string.Empty);
        }

        private void btn_runOneTime_Click(object sender, EventArgs e)
        {
            
            moveTest = new Thread(delegate ()
            {

                AuboRobot.joint cur =new  AuboRobot.joint();

                for (int i = 0; i < chkJointDataList.ToArray().Length; i++)
                {
                    cur = chkJointDataList[i];
                 
                    //Console.WriteLine("joint1:"+ waypoint[0]+"joint2:"+ waypoint[1]+ "joint3:" + waypoint[2]+ "joint4:" + waypoint[3] + "joint5:" + waypoint[4] + "joint6:" + waypoint[5]);
                    if(cur.moveStyle==0)
                    {
                        int retMove = AuboRobot.rs_move_joint(rshd, cur.jointpos, true);

                    }
                    if(cur.moveStyle == 1)
                    {
                        int retMove = AuboRobot.rs_move_line(rshd, cur.jointpos, true);
                    }
                    
                   // Console.WriteLine("retMove:" + retMove);

                }

            });
            moveTest.Start();

        }

        private void tabPage3_Click(object sender, EventArgs e)
        {

        }

        private void btn_runCircle_Click(object sender, EventArgs e)
        {
            moveCirTest = new Thread(delegate ()
            {
                while(true)
                {
                    AuboRobot.joint cur = new AuboRobot.joint();

                    for (int i = 0; i < chkJointDataList.ToArray().Length; i++)
                    {
                        cur = chkJointDataList[i];

                        //Console.WriteLine("joint1:"+ waypoint[0]+"joint2:"+ waypoint[1]+ "joint3:" + waypoint[2]+ "joint4:" + waypoint[3] + "joint5:" + waypoint[4] + "joint6:" + waypoint[5]);
                        if (cur.moveStyle == 0)
                        {
                            int retMove = AuboRobot.rs_move_joint(rshd, cur.jointpos, true);

                        }
                        if (cur.moveStyle == 1)
                        {
                            int retMove = AuboRobot.rs_move_line(rshd, cur.jointpos, true);
                        }

                        Thread.Sleep(1000);

                      

                    }
                }
               

            });
            moveCirTest.Start();
        }

        private void btn_browse_Click(object sender, EventArgs e)
        {
            //FolderBrowserDialog dlg = new FolderBrowserDialog();
            //if(dlg.ShowDialog()==DialogResult.OK)
            //{
            //    tb_fileRoute.Text = dlg.SelectedPath.ToString();
            //}

            //选择文件路径
            var ofd = new OpenFileDialog();
            string path = string.Empty;
            //筛选文件类型
            ofd.Filter = "*.txt|*.offt";
            if(ofd.ShowDialog()==DialogResult.OK)
            {
                tb_fileRoute.Text = ofd.FileName.ToString();
            }

        }

        private void tabControl1_SelectedIndexChanged(object sender, EventArgs e)
        {
            if (tabControl1.SelectedIndex == 3)//也可以判断tabControl1.SelectedTab.Text的值
            {
                //执行相应的操作
                flange_center_on_base_manual(rshd);
                isFinish = true;
                if(rb_modifyPos.Checked)
                {
                    isIK = true;
                    isFK = false;
                }if(rb_modifyJoint.Checked)
                {
                    isIK = false;
                    isFK = true;
                }
                
               

            }
            else if (tabControl1.SelectedIndex == 1)
            {
                //执行相应的操作
                 //隐藏选项卡tabPageSubject
            }


        }

        void getPosAndOri_IK(UInt16 rshd)
        {
            if (rb_modifyPos.Checked)
            {
                isIK = true;
                isFK = false;
            }
            if (rb_modifyJoint.Checked)
            {
                isIK = false;
                isFK = true;
            }
            //获取数据
            double posX = double.Parse(posX_tb.Text);
            double posY = double.Parse(posY_tb.Text);
            double posZ = double.Parse(posZ_tb.Text);
            double RX = double.Parse(RX_tb.Text);
            double RY = double.Parse(RY_tb.Text);
            double RZ = double.Parse(RZ_tb.Text);

            double j1 = double.Parse(J1_tb.Text);
            double j2 = double.Parse(J2_tb.Text);
            double j3 = double.Parse(J3_tb.Text);
            double j4 = double.Parse(J4_tb.Text);
            double j5 = double.Parse(J5_tb.Text);
            double j6 = double.Parse(J6_tb.Text);

            //逆解
            AuboRobot.wayPoint_S curWayPoint=new AuboRobot.wayPoint_S();
            AuboRobot.Pos curPos;
            AuboRobot.Rpy curRpy;
            AuboRobot.Ori curOri=new AuboRobot.Ori();
            curPos.x = posX;
            curPos.y = posY;
            curPos.z = posZ;
            curRpy.rx = RX * M_PI / 180;
            curRpy.ry= RY * M_PI / 180;
            curRpy.rz= RZ * M_PI / 180;
            AuboRobot.rs_rpy_to_quaternion(rshd, ref curRpy, ref curOri);

            double[] joint = new double[] { j1, j2, j3, j4, j5, j6 };
            int retIk = AuboRobot.rs_inverse_kin(rshd, joint, ref curPos,ref  curOri, ref curWayPoint);
            if(retIk!=RS_SUCC)
            {
                MessageBox.Show("IK false");
            }
            curPos = curWayPoint.cartPos;
            curOri = curWayPoint.orientation;
            AuboRobot.rs_quaternion_to_rpy(rshd, ref curOri, ref curRpy);

            //posX_tb.Text = curPos.x.ToString("0.000000");
            //posY_tb.Text = curPos.y.ToString("0.000000");
            //posZ_tb.Text = curPos.z.ToString("0.000000");
            ////rpy.rx * 180 / M_PI, rpy.ry * 180 / M_PI, rpy.rz * 180 / M_PI
            //RX_tb.Text = (curRpy.rx * 180 / M_PI).ToString("0.000000");
            //RY_tb.Text = (curRpy.ry * 180 / M_PI).ToString("0.000000");
            //RZ_tb.Text = (curRpy.rz * 180 / M_PI).ToString("0.000000");
            J1_tb.Text = (curWayPoint.jointpos[0] * 180 / M_PI).ToString("0.000000");
            J2_tb.Text = (curWayPoint.jointpos[1] * 180 / M_PI).ToString("0.000000");
            J3_tb.Text = (curWayPoint.jointpos[2] * 180 / M_PI).ToString("0.000000");
            J4_tb.Text = (curWayPoint.jointpos[3] * 180 / M_PI).ToString("0.000000");
            J5_tb.Text = (curWayPoint.jointpos[4] * 180 / M_PI).ToString("0.000000");
            J6_tb.Text = (curWayPoint.jointpos[5] * 180 / M_PI).ToString("0.000000");
        }

        void getJointFK(UInt16 rshd)
        {
            if (rb_modifyPos.Checked)
            {
                isIK = true;
                isFK = false;
            }
            if (rb_modifyJoint.Checked)
            {
                isIK = false;
                isFK = true;
            }
            //获取数据
            double j1 = double.Parse(J1_tb.Text);
            double j2 = double.Parse(J2_tb.Text);
            double j3 = double.Parse(J3_tb.Text);
            double j4 = double.Parse(J4_tb.Text);
            double j5 = double.Parse(J5_tb.Text);
            double j6 = double.Parse(J6_tb.Text);
            //逆解
            AuboRobot.wayPoint_S curWayPoint = new AuboRobot.wayPoint_S();
            AuboRobot.Pos curPos;
            AuboRobot.Rpy curRpy=new AuboRobot.Rpy();
            AuboRobot.Ori curOri = new AuboRobot.Ori();

            double[] joint = new double[] { j1, j2, j3, j4, j5, j6 };
            int retFK=AuboRobot.rs_forward_kin(rshd, joint, ref curWayPoint);
            if(retFK!=RS_SUCC)
            {
                MessageBox.Show("FK false");
            }
     
            curPos = curWayPoint.cartPos;
            curOri = curWayPoint.orientation;
            AuboRobot.rs_quaternion_to_rpy(rshd, ref curOri, ref curRpy);

            posX_tb.Text = curPos.x.ToString("0.000000");
            posY_tb.Text = curPos.y.ToString("0.000000");
            posZ_tb.Text = curPos.z.ToString("0.000000");
            RX_tb.Text = (curRpy.rx * 180 / M_PI).ToString("0.000000");
            RY_tb.Text = (curRpy.ry * 180 / M_PI).ToString("0.000000");
            RZ_tb.Text = (curRpy.rz * 180 / M_PI).ToString("0.000000");
            //J1_tb.Text = (curWayPoint.jointpos[0] * 180 / M_PI).ToString("0.000000");
            //J2_tb.Text = (curWayPoint.jointpos[1] * 180 / M_PI).ToString("0.000000");
            //J3_tb.Text = (curWayPoint.jointpos[2] * 180 / M_PI).ToString("0.000000");
            //J4_tb.Text = (curWayPoint.jointpos[3] * 180 / M_PI).ToString("0.000000");
            //J5_tb.Text = (curWayPoint.jointpos[4] * 180 / M_PI).ToString("0.000000");
            //J6_tb.Text = (curWayPoint.jointpos[5] * 180 / M_PI).ToString("0.000000");
        }


        private void posX_tb_TextChanged(object sender, EventArgs e)
        {
            
            
            if(isFinish==true && isIK==true)
            {
                getPosAndOri_IK(rshd);
               
            }
        }

        private void posY_tb_TextChanged(object sender, EventArgs e)
        {

            if (isFinish == true && isIK == true)
            {
                getPosAndOri_IK(rshd);

            }
        }

        private void posZ_tb_TextChanged(object sender, EventArgs e)
        {

            if (isFinish == true && isIK == true)
            {
                getPosAndOri_IK(rshd);

            }
        }

        private void RX_tb_TextChanged(object sender, EventArgs e)
        {

            if (isFinish == true && isIK == true)
            {
                getPosAndOri_IK(rshd);

            }
        }

        private void RY_tb_TextChanged(object sender, EventArgs e)
        {

            if (isFinish == true && isIK == true)
            {
                getPosAndOri_IK(rshd);

            }
        }

        private void RZ_tb_TextChanged(object sender, EventArgs e)
        {

            if (isFinish == true && isIK == true)
            {
                getPosAndOri_IK(rshd);

            }
        }

        private void J1_tb_TextChanged(object sender, EventArgs e)
        {
            
            if(isFinish == true && isFK==true)
            {
                Console.WriteLine("J1 FK");
                getJointFK(rshd);
              
            }
        }

        private void J2_tb_TextChanged(object sender, EventArgs e)
        {
            
            if (isFinish == true && isFK == true)
            {
                Console.WriteLine("J2 FK");
                getJointFK(rshd);

            }
        }

        private void J3_tb_TextChanged(object sender, EventArgs e)
        {
            
            if (isFinish == true && isFK == true)
            {
                Console.WriteLine("J3 FK");
                getJointFK(rshd);

            }
        }

        private void J4_tb_TextChanged(object sender, EventArgs e)
        {
            
            if (isFinish == true && isFK == true)
            {
                Console.WriteLine("J4 FK");
                getJointFK(rshd);

            }
        }

        private void J5_tb_TextChanged(object sender, EventArgs e)
        {
           
            if (isFinish == true && isFK == true)
            {
                Console.WriteLine("J5 FK");
                getJointFK(rshd);

            }
        }

        private void J6_tb_TextChanged(object sender, EventArgs e)
        {
            
            if (isFinish == true && isFK == true)
            {
                Console.WriteLine("J6 FK");
                getJointFK(rshd);

            }
        }

        void jointMoveTest(UInt16 rshd)
        {
            //获取数据
            double j1 = double.Parse(J1_tb.Text);
            double j2 = double.Parse(J2_tb.Text);
            double j3 = double.Parse(J3_tb.Text);
            double j4 = double.Parse(J4_tb.Text);
            double j5 = double.Parse(J5_tb.Text);
            double j6 = double.Parse(J6_tb.Text);
            //关节角
            double[] initPos0 = {
                j1/180*M_PI,
                j2/180*M_PI,
                j3/180*M_PI,
                j4/180*M_PI,
                j5/180*M_PI,
                j6/180*M_PI
            };

            //关节运动
            int ret = AuboRobot.rs_move_joint(rshd, initPos0, false);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("运动给定位姿和关节过程中.....!");

            }
            else
            {

                Console.Error.WriteLine("运动给定位姿失败!：{0}", ret);
            }

        }

        void lineMoveTest(UInt16 rshd)
        {
            //获取数据
            double j1 = double.Parse(J1_tb.Text);
            double j2 = double.Parse(J2_tb.Text);
            double j3 = double.Parse(J3_tb.Text);
            double j4 = double.Parse(J4_tb.Text);
            double j5 = double.Parse(J5_tb.Text);
            double j6 = double.Parse(J6_tb.Text);
            //关节角
            double[] initPos0 = {
                j1/180*M_PI,
                j2/180*M_PI,
                j3/180*M_PI,
                j4/180*M_PI,
                j5/180*M_PI,
                j6/180*M_PI
            };

            //直线运动
            int ret = AuboRobot.rs_move_line(rshd, initPos0, false);
            if (ret == RS_SUCC)
            {
                Console.Out.WriteLine("运动给定位姿和关节过程中.....!");

            }
            else
            {

                Console.Error.WriteLine("运动给定位姿失败!：{0}", ret);
            }

        }


        private void btn_moveJ_to_MouseDown(object sender, MouseEventArgs e)
        {
            //线程测试
            jointMove = new Thread(delegate ()
            {

                jointMoveTest(rshd);
            });
            jointMove.Start();
        }

        private void btn_moveJ_to_MouseUp(object sender, MouseEventArgs e)
        {
            int retMoveStop = AuboRobot.rs_move_fast_stop(rshd);
            //int retMoveStop = AuboRobot.rs_move_stop(rshd);
            Console.WriteLine("retMoveStop:" + retMoveStop);
        }

        private void btn_moveL_to_MouseDown(object sender, MouseEventArgs e)
        {
            //线程测试
            lineMove = new Thread(delegate ()
            {

                lineMoveTest(rshd);
            });
            lineMove.Start();
        }

        private void btn_moveL_to_MouseUp(object sender, MouseEventArgs e)
        {
            int retMoveStop = AuboRobot.rs_move_fast_stop(rshd);
            //int retMoveStop = AuboRobot.rs_move_stop(rshd);
            Console.WriteLine("retMoveStop:" + retMoveStop);
        }

        private void rb_modifyPos_CheckedChanged(object sender, EventArgs e)
        {
            if (rb_modifyPos.Checked)
            {
                isIK = true;
                isFK = false;
                Console.WriteLine("位姿");
            }
            if (rb_modifyJoint.Checked)
            {
                isIK = false;
                isFK = true;
                Console.WriteLine("关节");
            }
            
        }

        private void rb_modifyJoint_CheckedChanged(object sender, EventArgs e)
        {
            if (rb_modifyPos.Checked)
            {
                isIK = true;
                isFK = false;
                Console.WriteLine("位姿");
            }
            if (rb_modifyJoint.Checked)
            {
                isIK = false;
                isFK = true;
                Console.WriteLine("关节");
            }
        }

        private void btn_readTxt_Click(object sender, EventArgs e)
        {
            string path = tb_fileRoute.Text;
            StreamReader sr = new StreamReader(path, Encoding.Default);
            string line="";
            while ((line = sr.ReadLine()) != null)
            {
                if (line != null)
                {
                    txtList.Add(line);
                }
            }

            int size = txtList.ToArray().Length - 1;
            Console.WriteLine("size:"+ size);
           

        }

        private void btn_moveTrack_Click(object sender, EventArgs e)
        {
            //btn_readTxt_Click(sender, e);
            ////string[] strArr = str.Split(',');//根据逗号分隔字符串str
            //txtList.Clear();
            //chkJointDataList.Clear();
            ////线程测试
            //move_track = new Thread(delegate ()
            //{

            //    string str = "0,0,0,0,0,0";
            //    string[] strArr = new string[] { "0", "0", "0", "0", "0", "0" };//根据逗号分隔字符串str
            //    double[] joints = new double[] { 0, 0, 0, 0, 0, 0 };//根据逗号分隔字符串str
            //    for (int i = 0; i < txtList.ToArray().Length - 1; i++)
            //    {
            //        str = txtList[i];
            //        strArr = str.Split(',');
            //        if (strArr != null)
            //        {
            //            for (int j = 0; j < 6; j++)
            //            {
            //                joints[j] = double.Parse(strArr[j]);
            //            }
            //            chkJointData.jointpos = joints;
            //            chkJointData.moveStyle = 0;
            //            chkJointDataList.Add(chkJointData);
            //        }
            //    }
            //    Console.WriteLine("chkJointDataList:" + chkJointDataList.ToArray().Length);
            //    //轴动到第一个点
            //    int retMoveJoint = AuboRobot.rs_move_joint(rshd, chkJointDataList[0].jointpos, true);
            //    Console.WriteLine("retMoveJoint:" + retMoveJoint);
            //    for (int i = 0; i < chkJointDataList.ToArray().Length; i++)
            //    {
            //        AuboRobot.rs_add_waypoint(rshd, chkJointDataList[i].jointpos);
            //    }
            //    //设置交融半径
            //    //AuboRobot.rs_set_blend_radius(rshd, 0.03);
            //    //开始轨迹运动
            //    int retMoveTrack = AuboRobot.rs_move_track(rshd, 3, true);
            //    Console.WriteLine("retMoveTrack:" + retMoveTrack);

            //});
            //move_track.Start();



            move_track = new Thread(delegate ()
            {
                string pathStr = tb_fileRoute.Text;

                //运动到第一个路点
                double[] firstJoint = new double[] { -0.000000, 0.000009, -0.000001, 0.000002, 0.000002, 0.000000 };
                AuboRobot.rs_move_joint(rshd,firstJoint,true);
                //清除离线轨迹路点
                int retClearTrack=AuboRobot.rs_clear_offline_track(rshd);
                Console.WriteLine("retClearTrack:"+ retClearTrack);
                int retAppend=AuboRobot.rs_append_offline_track_file(rshd, pathStr);
                Console.WriteLine("retAppend:" + retAppend);
                int retStartUp=AuboRobot.rs_startup_offline_track(rshd);
                Console.WriteLine("retStartUp:" + retStartUp);
                int retStop=AuboRobot.rs_stop_offline_track(rshd);
                Console.WriteLine("retStop:" + retStop);


            });
            move_track.Start();


        }

        private void cb_electricity_TextChanged(object sender, EventArgs e)
        {
            if(cb_electricity.Text=="DC 0V")
            {
                Console.WriteLine("DC 0V");
                //rs_set_tool_power_type
                AuboRobot.rs_set_tool_power_type(rshd,AuboRobot.OUT_0V);
            }
            else if(cb_electricity.Text == "DC 12V")
            {
                Console.WriteLine("DC 12V");
                AuboRobot.rs_set_tool_power_type(rshd, AuboRobot.OUT_12V);
            }
            else if(cb_electricity.Text == "DC 24V")
            {
                Console.WriteLine("DC 24V");
                AuboRobot.rs_set_tool_power_type(rshd, AuboRobot.OUT_24V);
            }
        }

        private void cb_t00_TextChanged(object sender, EventArgs e)
        {
            //Digital Input
            //Digital Output
            if(cb_t00.Text== "Digital Input")
            {
                Console.WriteLine("t00_Digital Input");
                btn_t00.Enabled = false;
            }
            else if(cb_t00.Text == "Digital Output")
            {
                Console.WriteLine("t00_Digital Output");
                btn_t00.Enabled=true;
            }
        }

        private void cb_t01_TextChanged(object sender, EventArgs e)
        {
            //Digital Input
            //Digital Output
            if (cb_t01.Text == "Digital Input")
            {
                Console.WriteLine("t01_Digital Input");
                btn_t01.Enabled = false;
            }
            else if (cb_t01.Text == "Digital Output")
            {
                Console.WriteLine("t01_Digital Output");
                btn_t01.Enabled=true;
            }
        }

        private void cb_t02_TextChanged(object sender, EventArgs e)
        {
            //Digital Input
            //Digital Output
            if (cb_t02.Text == "Digital Input")
            {
                Console.WriteLine("t02_Digital Input");
                btn_t02.Enabled = false;
            }
            else if (cb_t02.Text == "Digital Output")
            {
                Console.WriteLine("t02_Digital Output");
                btn_t02 .Enabled = true;
            }
        }

        private void cb_t03_TextChanged(object sender, EventArgs e)
        {
            //Digital Input
            //Digital Output
            if (cb_t03.Text == "Digital Input")
            {
                Console.WriteLine("t03_Digital Input");
                btn_t03 .Enabled = false;
            }
            else if (cb_t03.Text == "Digital Output")
            {
                Console.WriteLine("t03_Digital Output");
                btn_t03.Enabled = true;
            }
        }

        private void btn_t00_Click(object sender, EventArgs e)
        {
            if(btn_t00.Text=="打开")
            {
                //rs_set_tool_do_status(UInt16 rshd, string name, int val);
            }
            else if(btn_t00.Text=="关闭")
            {
                
            }
        }

        private void btn_t01_Click(object sender, EventArgs e)
        {
            if (btn_t01.Text == "打开")
            {

            }
            else if (btn_t01.Text == "关闭")
            {

            }
        }

        private void btn_t02_Click(object sender, EventArgs e)
        {
            if (btn_t02.Text == "打开")
            {

            }
            else if (btn_t02.Text == "关闭")
            {

            }
        }

        private void btn_t03_Click(object sender, EventArgs e)
        {
            if (btn_t03.Text == "打开")
            {

            }
            else if (btn_t03.Text == "关闭")
            {

            }
        }

        private void btn_getWaypoint_Click(object sender, EventArgs e)
        {
            int ret = AuboRobot.rs_enable_push_realtime_roadpoint(rshd, true);
            if (ret == RS_SUCC)
            {
                AuboRobot.rs_setcallback_realtime_roadpoint(rshd, AuboRobot.CurrentPositionCallback, IntPtr.Zero);
                Thread.Sleep(5 * 1000);
            }
            else
            {
                Console.Error.WriteLine("调用 rs_enable_push_realtime_roadpoint 函数失败！错误码：{ 0}", ret);
            }

        }

        //private void trackBar1_Scroll(object sender, EventArgs e)
        //{
        //    trackBar_value.Text= trackBar1.Value.ToString();
        //}



    }
}
