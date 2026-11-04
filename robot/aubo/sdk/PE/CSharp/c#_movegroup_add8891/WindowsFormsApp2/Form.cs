using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;


using System.Net;
using System.Net.Sockets;
using System.Text;

using System.Threading;



using System.IO;
using System.Globalization;
using System.Runtime.InteropServices;

using AuboRobot;




namespace WindowsFormsApp2
{
    public partial class Form : System.Windows.Forms.Form
    {



        int result = 0xffff;
        UInt16 rshd = 0xffff;
        UInt16 sjhd = 0xffff;

        double[] target0 = { 0, 0, 0, 0, 0, 0 };
        double[] target1 = { 1, 1, 1, 1, 1, 1 };
        double[] max_velc = { 1.5, 1.5, 1.5, 1.5, 1.5, 1.5 };
        double[] max_acc = { 5, 5, 5, 5, 5, 5 };
        AuboController.JointVelcAccParam temp = new AuboController.JointVelcAccParam();
        AuboController.wayPoint_S waypoint = new AuboController.wayPoint_S();
        AuboController.wayPoint_S targetpoint = new AuboController.wayPoint_S();
        double io_status = 0;
        AuboController.Rpy rpy = new AuboController.Rpy();
        AuboController.Ori ori = new AuboController.Ori();
        AuboController.Pos pos = new AuboController.Pos();

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

        public Form()
        {
            InitializeComponent();

            // 订阅事件
            AuboController.WaypointReceived += OnWaypointReceived;
        }

        private void button1_Click(object sender, EventArgs e)
        {
            result = AuboController.rs_initialize();

            if (RSERR_SUCC == result)
            {

                //创建机械臂控制上下文句柄
                if (AuboController.rs_create_context(ref rshd) == RSERR_SUCC)
                {
                    //链接机械臂服务器
                    if (AuboController.rs_login(rshd, robotIP, serverPort) == RSERR_SUCC)
                    {

                        Console.Out.WriteLine("-----------------------------------------------------------------------连接机械臂成功");

                    }

                    else
                    {

                        Console.Out.WriteLine("-----------------------------------------------------------------------连接机械臂失败");

                    }

                }
            }
        }

        private void button2_Click(object sender, EventArgs e)
        {
            //工具参数
            AuboController.ToolDynamicsParam tool = new AuboController.ToolDynamicsParam();
            int state = 0;
            int ret = -1;
            tool.payload = 0;
            tool.positionX = 0; tool.positionX = 0;
            tool.positionZ = 0;
            tool.toolInertia.xx = 0;
            tool.toolInertia.xy = 0;
            tool.toolInertia.xz = 0;
            tool.toolInertia.yy = 0;
            tool.toolInertia.yz = 0;
            tool.toolInertia.zz = 0;

            ret = AuboController.rs_robot_startup(rshd, ref tool, 6, true, true, 1000, ref state);
            if (ret == 0)
            {
                Console.Out.WriteLine("-----------------------------------------------------------------------已启动");               
            }
        }

        private void button3_Click(object sender, EventArgs e)
        {
            int ret;

            double[] Angles_2 = new double[] {
                    0 * M_PI / 180.0 ,
                    0 * M_PI / 180.0 ,
                    0 * M_PI / 180.0 ,
                    0 * M_PI / 180.0 ,
                    0 * M_PI / 180.0 ,
                    0 * M_PI / 180.0
                    };

            ret = AuboController.rs_move_joint(rshd, Angles_2, true);

            if (ret == 0)
            {
                Console.Out.WriteLine("-----------------------------------------------------------关节运动目标点2成功");
            }
        }

        private void button4_Click(object sender, EventArgs e)
        {
            int ret;
            double[] Angles_1 = new double[] {
                    1.96 * M_PI / 180.0 ,
                    -7.68 * M_PI / 180.0 ,
                    -114.2 * M_PI / 180.0 ,
                    -15.9 * M_PI / 180.0 ,
                    -89 * M_PI / 180.0 ,
                    1.55 * M_PI / 180.0
                    };

            ret = AuboController.rs_move_joint(rshd, Angles_1, true);
            if (ret == 0)
            {
                Console.Out.WriteLine("-----------------------------------------------------------关节运动目标点1成功");
            }
        }

        private void button5_Click(object sender, EventArgs e)
        {

            int ret;
            AuboController.JointStatus[] joint_status = new AuboController.JointStatus[6];
            ret = AuboController.rs_get_joint_status(rshd, joint_status);

            Console.Out.WriteLine("--1关节关节电流----- {0}", joint_status[0].jointCurrentI);
            Console.Out.WriteLine("--1关节关节速度------ {0}", joint_status[0].jointSpeedMoto);
            Console.Out.WriteLine("--1关节关节角------ {0}", joint_status[0].jointPosJ);
            Console.Out.WriteLine("--1关节关节电压------ {0}", joint_status[0].jointCurVol);
            Console.Out.WriteLine("--1关节关节温度------ {0}", joint_status[0].jointCurTemp);

        }

        private void button6_Click(object sender, EventArgs e)
        {
            //REALTIME_ROADPOINT_CALLBACK RobotRoadPointCallbackPtr = new REALTIME_ROADPOINT_CALLBACK(CurrentPositionCallback);
            //AuboController.rs_setcallback_realtime_roadpoint(rshd, RobotRoadPointCallbackPtr, IntPtr.Zero);


            //ROBOT_EVENT_CALLBACK RobotEventCallbackPtr = new ROBOT_EVENT_CALLBACK(RobotEventCallback);
            //AuboController.rs_setcallback_robot_event(rshd, RobotEventCallbackPtr, IntPtr.Zero);




            AuboController.REALTIME_JOINTSTATUS_CALLBACK RobotJointStatusCallbackPtr = new AuboController.REALTIME_JOINTSTATUS_CALLBACK(AuboController.JointStatusCallback);
            AuboController.rs_setcallback_realtime_joint_status(rshd, RobotJointStatusCallbackPtr, IntPtr.Zero);


        }

        private void button7_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.MOV_X, true);
        }

        private void button8_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.MOV_X, false);
        }

        private void button9_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.MOV_Y, true);
        }

        private void button10_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.MOV_Y, false);
        }

        private void button12_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.MOV_Z, true);
        }

        private void button13_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.MOV_Z, false);
        }

        private void button11_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_stop(rshd);
        }

        private void button16_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.ROT_X, true);
        }

        private void button17_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.ROT_X, false);
        }

        private void button18_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.ROT_Y, true);
        }

        private void button19_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.ROT_Y, false);
        }

        private void button14_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.ROT_Z, true);
        }

        private void button15_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_start(rshd, AuboController.teach_mode.ROT_Z, false);
        }

        private void button20_Click(object sender, EventArgs e)
        {
            AuboController.rs_teach_move_stop(rshd);
        }

        private void button22_Click(object sender, EventArgs e)
        {
            int ret = -1;

            double joint1 = Convert.ToDouble(textBox1.Text);
            double joint2 = Convert.ToDouble(textBox2.Text);
            double joint3 = Convert.ToDouble(textBox3.Text);
            double joint4 = Convert.ToDouble(textBox4.Text);
            double joint5 = Convert.ToDouble(textBox5.Text);
            double joint6 = Convert.ToDouble(textBox6.Text);

            double[] waypoint_1 = new double[] {
                    joint1 * M_PI / 180.0,
                    joint2 * M_PI / 180.0,
                    joint3 * M_PI / 180.0,
                    joint4 * M_PI / 180.0,
                    joint5 * M_PI / 180.0,
                    joint6 * M_PI / 180.0
                    };

            ret = AuboController.rs_move_joint(rshd, waypoint_1, true);
            if (ret == 0)
            {
                Console.Out.WriteLine("-----------------------------------------------------------关节运动目标点1成功");
            }
            else {
                Console.Out.WriteLine("------运动结果-{0}----------------------------------------------------",ret);
            }


        }

        private void button23_Click(object sender, EventArgs e)
        {
            int ret = -1;
            double[] refJointRadia = new double[6] { 0.0 / 57.3, -7.3 / 57.3, -75.7 / 57.3, 21.6 / 57.3, -90 / 57.3, 0.0 / 57.3 };

            AuboController.wayPoint_S ResJoint1 = new AuboController.wayPoint_S();
            AuboController.Pos Point1Pos = new AuboController.Pos();
            AuboController.Ori Point1Ori = new AuboController.Ori();

            Point1Pos.x = -0.45;
            Point1Pos.y = -0.4;
            Point1Pos.z = 0.5;
            Point1Ori.w = 0;
            Point1Ori.x = 0.707;
            Point1Ori.y = -0.707;
            Point1Ori.z = 0;

            
            for (int count = 0; count < 100; count ++)
            {
                Point1Pos.z = Point1Pos.z + count / 5000.0;
                ret = AuboController.rs_inverse_kin(rshd, refJointRadia, ref Point1Pos, ref Point1Ori, ref ResJoint1);
                Console.Out.WriteLine("--逆解结果----- {0}—————第—{1}次", ret, count);
                
                Console.Out.WriteLine("--joint1----- {0}————", ResJoint1.jointpos[0]);
                Console.Out.WriteLine("--joint2----- {0}————", ResJoint1.jointpos[1]);
                Console.Out.WriteLine("--joint3----- {0}————", ResJoint1.jointpos[2]);
                Console.Out.WriteLine("--joint4----- {0}————", ResJoint1.jointpos[3]);
                Console.Out.WriteLine("--joint5----- {0}————", ResJoint1.jointpos[4]);
                Console.Out.WriteLine("--joint6----- {0}————", ResJoint1.jointpos[5]);
            }

            //rs_move_joint(rshd, ResJoint1.jointpos, true);

        }

        private void button24_Click(object sender, EventArgs e)
        {
            int ret;
            double[] JointAcc = new double[6] {1,1,1,1,1,1};
            ret = AuboController.rs_set_global_joint_maxacc(rshd, JointAcc);
            Console.Out.WriteLine("--设置结果----- {0}————", ret);

        }

        private void button25_Click(object sender, EventArgs e)
        {
            AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.RobotBrake);
        }

        private void button26_Click(object sender, EventArgs e)
        {
            AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.RobotRelease);
        }

        private void button27_Click(object sender, EventArgs e)
        {
            AuboController.rs_collision_recover(rshd);
        }

        private void button28_Click(object sender, EventArgs e)
        {
            AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.EnableCollisionStuckBrake);
        }

        private void button29_Click(object sender, EventArgs e)
        {
            AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.DisableCollisionStuckBrake);

        }

        private void button30_Click(object sender, EventArgs e)
        {
            for (int count = 0; count < 100; count++)
            {

                if (AuboController.rs_create_context(ref rshd) == RSERR_SUCC)
                {
                    //链接机械臂服务器
                    if (AuboController.rs_login(rshd, robotIP, serverPort) == RSERR_SUCC)
                    {

                        Console.Out.WriteLine("-----------------------------------------------------------------------连接机械臂成功");

                    }

                    else
                    {

                        Console.Out.WriteLine("-----------------------------------------------------------------------连接机械臂失败");

                    }

                }

                AuboController.ToolDynamicsParam tool = new AuboController.ToolDynamicsParam();
                int state = 0;
                int ret = -1;
                tool.payload = 0;
                tool.positionX = 0; tool.positionX = 0;
                tool.positionZ = 0;
                tool.toolInertia.xx = 0;
                tool.toolInertia.xy = 0;
                tool.toolInertia.xz = 0;
                tool.toolInertia.yy = 0;
                tool.toolInertia.yz = 0;
                tool.toolInertia.zz = 0;

                ret = AuboController.rs_robot_startup(rshd, ref tool, 6, true, true, 1000, ref state);
                if (ret == 0)
                {
                    Console.Out.WriteLine("-----------------------------------------------------------------------已启动");
                }

                

                AuboController.rs_robot_shutdown(rshd);

                //rs_logout(rshd);

                //AuboController.rs_destory_context(rshd);

            }
        }

        private void textBox11_TextChanged(object sender, EventArgs e)
        {

        }

        private void button4_Click_1(object sender, EventArgs e)
        {

            
            int ret = -1;

            double waypoint_1_joint1 = Convert.ToDouble(textBox1.Text);
            double waypoint_1_joint2 = Convert.ToDouble(textBox2.Text);
            double waypoint_1_joint3 = Convert.ToDouble(textBox3.Text);
            double waypoint_1_joint4 = Convert.ToDouble(textBox4.Text);
            double waypoint_1_joint5 = Convert.ToDouble(textBox5.Text);
            double waypoint_1_joint6 = Convert.ToDouble(textBox6.Text);

            double[] waypoint_1 = new double[] {
                    waypoint_1_joint1 * M_PI / 180.0,
                    waypoint_1_joint2 * M_PI / 180.0,
                    waypoint_1_joint3 * M_PI / 180.0,
                    waypoint_1_joint4 * M_PI / 180.0,
                    waypoint_1_joint5 * M_PI / 180.0,
                    waypoint_1_joint6 * M_PI / 180.0
                    };

            double waypoint_2_joint1 = Convert.ToDouble(textBox7.Text);
            double waypoint_2_joint2 = Convert.ToDouble(textBox8.Text);
            double waypoint_2_joint3 = Convert.ToDouble(textBox9.Text);
            double waypoint_2_joint4 = Convert.ToDouble(textBox10.Text);
            double waypoint_2_joint5 = Convert.ToDouble(textBox11.Text);
            double waypoint_2_joint6 = Convert.ToDouble(textBox12.Text);

            double[] waypoint_2 = new double[] {
                    waypoint_2_joint1 * M_PI / 180.0,
                    waypoint_2_joint2 * M_PI / 180.0,
                    waypoint_2_joint3 * M_PI / 180.0,
                    waypoint_2_joint4 * M_PI / 180.0,
                    waypoint_2_joint5 * M_PI / 180.0,
                    waypoint_2_joint6 * M_PI / 180.0
                    };

            while (true)
            {

                ret = AuboController.rs_move_joint(rshd, waypoint_1, true);
                if (ret == 0)
                {
                    Console.Out.WriteLine("-----------------------------------------------------------关节运动目标点1成功");
                }
                else
                {
                    Console.Out.WriteLine("------运动结果-{0}----------------------------------------------------", ret);
                }


                AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.RobotBrake);

                Thread.Sleep(10000);

                AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.RobotRelease);

                Thread.Sleep(1000);



                ret = AuboController.rs_move_joint(rshd, waypoint_2, true);
                if (ret == 0)
                {
                    Console.Out.WriteLine("-----------------------------------------------------------关节运动目标点1成功");
                }
                else
                {
                    Console.Out.WriteLine("------运动结果-{0}----------------------------------------------------", ret);
                }

                AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.RobotBrake);

                Thread.Sleep(10000);

                AuboController.rs_robot_control(rshd, AuboController.RobotControlCommand.RobotRelease);

                Thread.Sleep(1000);
            }
        }

        private void button3_Click_1(object sender, EventArgs e)
        {
            int ret = -1;

            double joint1 = Convert.ToDouble(textBox7.Text);
            double joint2 = Convert.ToDouble(textBox8.Text);
            double joint3 = Convert.ToDouble(textBox9.Text);
            double joint4 = Convert.ToDouble(textBox10.Text);
            double joint5 = Convert.ToDouble(textBox11.Text);
            double joint6 = Convert.ToDouble(textBox12.Text);

            double[] waypoint_2 = new double[] {
                    joint1 * M_PI / 180.0,
                    joint2 * M_PI / 180.0,
                    joint3 * M_PI / 180.0,
                    joint4 * M_PI / 180.0,
                    joint5 * M_PI / 180.0,
                    joint6 * M_PI / 180.0
                    };

            ret = AuboController.rs_move_joint(rshd, waypoint_2, true);
            if (ret == 0)
            {
                Console.Out.WriteLine("-----------------------------------------------------------关节运动目标点1成功");
            }
            else
            {
                Console.Out.WriteLine("------运动结果-{0}----------------------------------------------------", ret);
            }
        }

        private void button5_Click_1(object sender, EventArgs e)
        {
            int ret = -1;

            //double[] track_first_point = new double[] {
            //        1.000,1.0,1.0,1.0,1.0,1.0
            //        };

            //double[] track_first_point = new double[] {
            //        0.000,0.0,0.0,0.0,0.0,0.0
            //        };

            double[] track_first_point = new double[] {
                    0.000,0.0,0.0,0.0,0.0,0.0
                    };


            ret = AuboController.rs_clear_offline_track(rshd);
            string path = @"E:/aubo_code/20240930/aubo_sharp_test/test/WindowsFormsApp2/weld_1.offt";
            //string path = @"E:\aubo_code\20240930\aubo_sharp_test\test\WindowsFormsApp2\offlineTrack_1.txt";
            StreamReader sr = new StreamReader(path, Encoding.Default);
            String line;
            int count = 0;

            AuboController.JointParam[] joint_temp = new AuboController.JointParam[10];

            joint_temp[0].jointPos = new double[6] { 0.000, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[1].jointPos = new double[6] { 0.001, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[2].jointPos = new double[6] { 0.002, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[3].jointPos = new double[6] { 0.003, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[4].jointPos = new double[6] { 0.004, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[5].jointPos = new double[6] { 0.005, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[6].jointPos = new double[6] { 0.006, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[7].jointPos = new double[6] { 0.007, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[8].jointPos = new double[6] { 0.008, 0.0, 0.0, 0.0, 0.0, 0.0 };
            joint_temp[9].jointPos = new double[6] { 0.009, 0.0, 0.0, 0.0, 0.0, 0.0 };



            /* while ((line = sr.ReadLine()) != null)
             {
                 //textBox1.Text += line + "\r\n";

                 //Console.Out.WriteLine("第{0}行内容：-- {1}",count ,line);
                 //List<string> line_list = new List<string>(line.Split(','));
                 //Console.Out.WriteLine("第{0}行内容：-- {1}", count, line_list);

                 var array = line.Split(new string[] { "," }, StringSplitOptions.RemoveEmptyEntries);
                 Array.Sort(array);
                 //Console.Out.WriteLine("第{0}行内容：{1}",count,string.Join(", ", array));

                 double[] doubleArray = new double[6];

                 doubleArray = Array.ConvertAll<string, double>(array, s => double.Parse(s));

                 //for (int i = 0; i < 6; i++)
                 //{
                 //    Console.Out.WriteLine("第{0}行内容：{1},{2},{3},{4},{5},{6}",count ,doubleArray[0],
                 //        doubleArray[1],doubleArray[2],doubleArray[3],doubleArray[4],doubleArray[5]);
                 //}


                 joint_temp[count].jointPos = doubleArray;

                 //joint_temp[count].jointPos[0] = doubleArray[0];
                 //joint_temp[count].jointPos[1] = doubleArray[1];
                 //joint_temp[count].jointPos[2] = doubleArray[2];
                 //joint_temp[count].jointPos[3] = doubleArray[3];
                 //joint_temp[count].jointPos[4] = doubleArray[4];
                 //joint_temp[count].jointPos[5] = doubleArray[5];





                 count = count + 1;
             }


             //for (int i = 0; i < joint_temp.Count(); i++)
             //{
             //    Console.Out.WriteLine("第{0}行内容：{1},{2},{3},{4},{5},{6}", i,
             //        joint_temp[i].jointPos[0],
             //        joint_temp[i].jointPos[1],
             //        joint_temp[i].jointPos[2],
             //        joint_temp[i].jointPos[3],
             //        joint_temp[i].jointPos[4],
             //        joint_temp[i].jointPos[5]);

             //}


             //ret = rs_append_offline_track_file(rshd, "E:\aubo_code\20240930\aubo_sharp_test\test\WindowsFormsApp2\offlineTrack.txt");
            */

            //double[] joint_1 = new double[] { 0.000, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_2 = new double[] { 0.001, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_3 = new double[] { 0.002, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_4 = new double[] { 0.003, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_5 = new double[] { 0.004, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_6 = new double[] { 0.005, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_7 = new double[] { 0.006, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_8 = new double[] { 0.007, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_9 = new double[] { 0.008, 0.0, 0.0, 0.0, 0.0, 0.0 };
            //double[] joint_10 = new double[] { 0.009, 0.0, 0.0, 0.0, 0.0, 0.0 };

            //joint_temp[0].jointPos = joint_1;
            //joint_temp[1].jointPos = joint_2;
            //joint_temp[2].jointPos = joint_3; 
            //joint_temp[3].jointPos = joint_4;
            //joint_temp[4].jointPos = joint_5;
            //joint_temp[5].jointPos = joint_6;
            //joint_temp[6].jointPos = joint_7;
            //joint_temp[7].jointPos = joint_8;
            //joint_temp[8].jointPos = joint_9;
            //joint_temp[9].jointPos = joint_10;

            //joint_temp[0].jointPos[0] = joint_1[0];
            //joint_temp[0].jointPos[1] = joint_1[1];
            //joint_temp[0].jointPos[2] = joint_1[2];
            //joint_temp[0].jointPos[3] = joint_1[3];
            //joint_temp[0].jointPos[4] = joint_1[4];
            //joint_temp[0].jointPos[5] = joint_1[5];

            //joint_temp[1].jointPos[0] = joint_2[0];
            //joint_temp[1].jointPos[1] = joint_2[1];
            //joint_temp[1].jointPos[2] = joint_2[2];
            //joint_temp[1].jointPos[3] = joint_2[3];
            //joint_temp[1].jointPos[4] = joint_2[4];
            //joint_temp[1].jointPos[5] = joint_2[5];

            //joint_temp[2].jointPos[0] = joint_3[0];
            //joint_temp[2].jointPos[1] = joint_3[1];
            //joint_temp[2].jointPos[2] = joint_3[2];
            //joint_temp[2].jointPos[3] = joint_3[3];
            //joint_temp[2].jointPos[4] = joint_3[4];
            //joint_temp[2].jointPos[5] = joint_3[5];

            //joint_temp[3].jointPos[0] = joint_4[0];
            //joint_temp[3].jointPos[1] = joint_4[1];
            //joint_temp[3].jointPos[2] = joint_4[2];
            //joint_temp[3].jointPos[3] = joint_4[3];
            //joint_temp[3].jointPos[4] = joint_4[4];
            //joint_temp[3].jointPos[5] = joint_4[5];

            //joint_temp[4].jointPos[0] = joint_5[0];
            //joint_temp[4].jointPos[1] = joint_5[1];
            //joint_temp[4].jointPos[2] = joint_5[2];
            //joint_temp[4].jointPos[3] = joint_5[3];
            //joint_temp[4].jointPos[4] = joint_5[4];
            //joint_temp[4].jointPos[5] = joint_5[5];

            //joint_temp[5].jointPos[0] = joint_6[0];
            //joint_temp[5].jointPos[1] = joint_6[1];
            //joint_temp[5].jointPos[2] = joint_6[2];
            //joint_temp[5].jointPos[3] = joint_6[3];
            //joint_temp[5].jointPos[4] = joint_6[4];
            //joint_temp[5].jointPos[5] = joint_6[5];

            //joint_temp[6].jointPos[0] = joint_7[0];
            //joint_temp[6].jointPos[1] = joint_7[1];
            //joint_temp[6].jointPos[2] = joint_7[2];
            //joint_temp[6].jointPos[3] = joint_7[3];
            //joint_temp[6].jointPos[4] = joint_7[4];
            //joint_temp[6].jointPos[5] = joint_7[5];


            //joint_temp[7].jointPos[0] = joint_8[0];
            //joint_temp[7].jointPos[1] = joint_8[1];
            //joint_temp[7].jointPos[2] = joint_8[2];
            //joint_temp[7].jointPos[3] = joint_8[3];
            //joint_temp[7].jointPos[4] = joint_8[4];
            //joint_temp[7].jointPos[5] = joint_8[5];


            //joint_temp[8].jointPos[0] = joint_9[0];
            //joint_temp[8].jointPos[1] = joint_9[1];
            //joint_temp[8].jointPos[2] = joint_9[2];
            //joint_temp[8].jointPos[3] = joint_9[3];
            //joint_temp[8].jointPos[4] = joint_9[4];
            //joint_temp[8].jointPos[5] = joint_9[5];



            //joint_temp[9].jointPos[0] = joint_10[0];
            //joint_temp[9].jointPos[1] = joint_10[0];
            //joint_temp[9].jointPos[2] = joint_10[0];
            //joint_temp[9].jointPos[3] = joint_10[0];
            //joint_temp[9].jointPos[4] = joint_10[0];
            //joint_temp[9].jointPos[0] = joint_10[0];

            for (int i = 0; i < joint_temp.Count(); i++)
            {
                Console.Out.WriteLine("第{0}行内容：{1},{2},{3},{4},{5},{6}", i,
                    joint_temp[i].jointPos[0],
                    joint_temp[i].jointPos[1],
                    joint_temp[i].jointPos[2],
                    joint_temp[i].jointPos[3],
                    joint_temp[i].jointPos[4],
                    joint_temp[i].jointPos[5]);

            }

            //for (int testcount = 0; testcount < 10; testcount++)
            //{
            ret = AuboController.rs_move_joint(rshd, track_first_point, true);
                if (ret == 0)
                {
                    Console.Out.WriteLine("-----------------------------------------------------------关节运动第一个点成功");
                }
                else
                {
                    Console.Out.WriteLine("------关节运动第一个点失败 运动结果-{0}----------------------------------------------------", ret);
                }

            //ret = rs_append_offline_track_waypoint(rshd, ref joint_temp, 10);

            //string file_name = @"E:/aubo_code/20240930/aubo_sharp_test/test/WindowsFormsApp2/weld_1.offt";

            string file_name = "E:/aubo_code/20240930/aubo_sharp_test/test/WindowsFormsApp2/weld_1.offt";

            ret = AuboController.rs_append_offline_track_file(rshd, file_name);

            // Thread.Sleep(1000);

            if (ret == 0)
                {
                    Console.Out.WriteLine("----添加离线轨迹点成功");
                }
                else
                {
                    Console.Out.WriteLine("---添加离线轨迹点失败 运动结果-{0}----------------------------------------------------", ret);
                }



                ret = AuboController.rs_startup_offline_track(rshd);

                if (ret == 0)
                {
                    Console.Out.WriteLine("----运行离线轨迹点成功");
                }
                else
                {
                    Console.Out.WriteLine("---运行离线轨迹点失败 运动结果-{0}----------------------------------------------------", ret);
                }

            //}
        }

        private void button6_Click_1(object sender, EventArgs e)
        {
            int ret;
            AuboController.wayPoint_S current_waypoint = new AuboController.wayPoint_S();
            ret = AuboController.rs_get_current_waypoint(rshd, ref current_waypoint);
            Console.Out.WriteLine("  current X : {0}", waypoint.cartPos.x);
            Console.Out.WriteLine("  current Y : {0}", waypoint.cartPos.y);
            Console.Out.WriteLine("  current Z : {0}", waypoint.cartPos.z);

            AuboController.Ori current_ori = new AuboController.Ori();
            AuboController.Rpy current_rpy = new AuboController.Rpy();

            current_ori.w = current_waypoint.orientation.w;
            current_ori.x = current_waypoint.orientation.x;
            current_ori.y = current_waypoint.orientation.y;
            current_ori.z = current_waypoint.orientation.z;

            ret = AuboController.rs_quaternion_to_rpy(rshd, ref current_ori, ref current_rpy);

            Console.Out.WriteLine("  current X : {0}", waypoint.cartPos.x);
            Console.Out.WriteLine("  current Y : {0}", waypoint.cartPos.y);
            Console.Out.WriteLine("  current Z : {0}", waypoint.cartPos.z);

            Console.Out.WriteLine("  current RX : {0}", current_rpy.rx / M_PI * 180.0);
            Console.Out.WriteLine("  current RY : {0}", current_rpy.ry / M_PI * 180.0);
            Console.Out.WriteLine("  current RZ : {0}", current_rpy.rz / M_PI * 180.0);

        }

        private void button7_Click_1(object sender, EventArgs e)
        {
            int ret;

            double[] joint_1 = new double[] { 0.000, 0.0, 0.0, 0.0, 0.0, 0.0 };
            double[] joint_2 = new double[] { 0.020, 0.0, 0.0, 0.0, 0.0, 0.0 };

            AuboController.rs_set_robot_orpe_stop(rshd,0x02);
            ret = AuboController.rs_move_joint(rshd, joint_1, true);
            
            if (ret == 0)
            {
                Console.Out.WriteLine("-----------------------------------------------------------关节运动第一个点成功");
            }
            else
            {
                Console.Out.WriteLine("------关节运动第一个点失败 运动结果-{0}----------------------------------------------------", ret);
            }
            AuboController.rs_set_robot_orpe_stop(rshd, 0x01);

            AuboController.rs_set_robot_orpe_stop(rshd, 0x02);
            ret = AuboController.rs_move_joint(rshd, joint_2, true);
            if (ret == 0)
            {
                Console.Out.WriteLine("-----------------------------------------------------------关节运动第一个点成功");
            }
            else
            {
                Console.Out.WriteLine("------关节运动第二个点失败 运动结果-{0}----------------------------------------------------", ret);
            }
            AuboController.rs_set_robot_orpe_stop(rshd, 0x01);
        }

        private void button8_Click_1(object sender, EventArgs e)
        {



            //////double[] joint_1 = new double[] {
            //////        1.96 * M_PI / 180.0 ,
            //////        -7.68 * M_PI / 180.0 ,
            //////        -114.2 * M_PI / 180.0 ,
            //////        -15.9 * M_PI / 180.0 ,
            //////        -89 * M_PI / 180.0 ,
            //////        1.55 * M_PI / 180.0
            //////        };


            //////double[] joint_2 = new double[] {
            //////        80 * M_PI / 180.0 ,
            //////        -7.68 * M_PI / 180.0 ,
            //////        -114.2 * M_PI / 180.0 ,
            //////        -15.9 * M_PI / 180.0 ,
            //////        -89 * M_PI / 180.0 ,
            //////        1.55 * M_PI / 180.0
            //////        };


            ////double[] joint_1 = new double[] {2.03,-0.15,-1.31,0.3,-1.616,0.05 };
            ////double[] joint_2 = new double[] {2.38, 0.08,-1.07,0.3,-1.65,0.4 };
            ////double[] joint_3 = new double[] {2.38,0.139,-0.9,0.43,-1.65,0.4 };
            ////double[] joint_4 = new double[] {1.41,-0.17,-1.21,0.4,-1.545,-0.56 };
            ////double[] joint_5 = new double[] {1.41,-0.29,-1.94,-0.19,-1.545,-0.566 };
            ////double[] joint_6 = new double[] {1.41,-0.23,-1.45,0.24,-1.54,-0.566 };


            ////rs_start_move_group(rshd);

            ////rs_init_global_move_profile(rshd);

            ////rs_set_global_end_max_line_acc(rshd, 5.0);//（最大线加速度5000mm/s²）

            ////rs_set_global_end_max_line_velc(rshd, 2.4);//（最大线速度2400mm/s）

            ////rs_set_global_end_max_angle_acc(rshd, 1500.0 * M_PI / 180.0);//（最大角加速度1500.0/s²）

            ////rs_set_global_end_max_angle_velc(rshd, 180.0 * M_PI / 180.0);//（最大角速度180.0/s）

            ////rs_set_max_jerk_acc_percent(rshd,1.0);//(默认0.25，范围0.01-1.0)

            ////rs_set_max_jerk_angle_percent(rshd,1.0);//(默认0.25，范围0.01-1.0)

            ////rs_move_line(rshd, joint_1, false);

            ////rs_move_line(rshd, joint_2, false);

            ////rs_move_line(rshd, joint_3, false);

            ////rs_move_line(rshd, joint_4, false);

            ////rs_move_line(rshd, joint_5, false);

            ////rs_move_line(rshd, joint_6, false);

            ////rs_end_move_group(rshd);

            ////rs_wait_move_group_finsihed(rshd);

            int ret;

            //double[] point1_joint = new double[]{ 0.000501, -0.126556, -1.319983, 0.379898, -1.573658, 0.000501 };
            //double[] point2_joint = new double[] { -0.341983, -0.172833, -1.357454, 0.387596, -1.574341, -0.341984 };
            //double[] point3_joint = new double[] { -0.341983, -0.172833, -1.357454, 0.387596, -1.574341, -0.341984 };
            //double[] point4_joint = new double[] { -0.502177, -0.082374, -1.093226, 0.560781, -1.574522, -0.502179 };
            //double[] point5_joint = new double[] { -0.340724, 0.020273, -0.783671, 0.768277, -1.574339, -0.340724 };
            //double[] point6_joint = new double[] { -0.340724, 0.020273, -0.783671, 0.768277, -1.574339, -0.340724 };
            //double[] point7_joint = new double[] { 0.001463, 0.070957, -0.730550, 0.771820, -1.573656, 0.001464 };
            //double[] point8_joint = new double[] { 0.001463, 0.070957, -0.730550, 0.771820, -1.573656, 0.001464 };
            //double[] point9_joint = new double[] { 0.163451, 0.008600, -1.008267, 0.556889, -1.573210, 0.163451 };
            //double[] point10_joint = new double[] { 0.000501, -0.126556, -1.319983, 0.379898, -1.573658, 0.000501 };



            double[] point1_joint = new double[] {3.021788,-0.085760,-1.940884,-0.283262,-1.567304,-2.943446};
            double[] point2_joint = new double[] { 3.032280,0.010803,-1.848368,-0.287353,-1.567293,-2.932951 };
            double[] point3_joint = new double[] { 3.032280, 0.010803, -1.848368, -0.287353, -1.567293, -2.932951 };
            double[] point4_joint = new double[] { 2.995145,0.050797,-1.806959,-0.285822,-1.567333,-2.970081 };
            double[] point5_joint = new double[] { 2.945177, -0.002157, -1.861345, -0.287067, -1.567396, -3.020054 };
            double[] point6_joint = new double[] { 2.945177, -0.002157, -1.861345, -0.287067, -1.567396, -3.020054 };
            double[] point7_joint = new double[] { 2.926152,-0.100521,-1.954045,-0.281329,-1.567420,-3.039073 };
            double[] point8_joint = new double[] { 2.926152, -0.100521, -1.954045, -0.281329, -1.567420, -3.039073 };
            double[] point9_joint = new double[] { 2.965430,-0.144696,-1.992178,-0.275446,-1.567367,-2.999798 };
            double[] point10_joint = new double[] { 3.021788, -0.085760, -1.940884, -0.283262, -1.567304, -2.943446 };

            ret = AuboController.rs_move_joint(rshd, point1_joint, true);
            if (ret == 0)
            {
                Console.Out.WriteLine("------关节运动第一个点成功 运动结果-{0}----------------------------------------------------", ret);
            }
            else
            {
                Console.Out.WriteLine("------关节运动第一个点失败 运动结果-{0}----------------------------------------------------", ret);
            }

            AuboController.rs_start_movegroup(rshd);

            for (int count = 0; count < 5; count++)
            {
                AuboController.rs_init_global_move_profile(rshd);
                AuboController.rs_set_global_end_max_line_velc(rshd, 0.04);
                AuboController.rs_set_global_end_max_line_acc(rshd, 1.570796);
                AuboController.rs_set_global_end_max_angle_velc(rshd, 2);
                AuboController.rs_set_global_end_max_angle_acc(rshd, 13.089969);
                AuboController.rs_set_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_angle_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_arrival_ahead_distance(rshd, 0.01);
                AuboController.rs_add_waypoint(rshd, point1_joint);
                AuboController.rs_add_waypoint(rshd, point2_joint);

                AuboController.rs_move_track(rshd, 15, false);



                AuboController.rs_init_global_move_profile(rshd);
                AuboController.rs_set_global_end_max_line_velc(rshd, 0.04);
                AuboController.rs_set_global_end_max_line_acc(rshd, 1.570796);
                AuboController.rs_set_global_end_max_angle_velc(rshd, 2);
                AuboController.rs_set_global_end_max_angle_acc(rshd, 13.089969);
                AuboController.rs_set_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_angle_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_arrival_ahead_distance(rshd, 0.01);
                AuboController.rs_add_waypoint(rshd, point3_joint);
                AuboController.rs_add_waypoint(rshd, point4_joint);
                AuboController.rs_add_waypoint(rshd, point5_joint);
                AuboController.rs_move_track(rshd, 90, false);


                AuboController.rs_init_global_move_profile(rshd);
                AuboController.rs_set_global_end_max_line_velc(rshd, 0.04);
                AuboController.rs_set_global_end_max_line_acc(rshd, 1.570796);
                AuboController.rs_set_global_end_max_angle_velc(rshd, 2);
                AuboController.rs_set_global_end_max_angle_acc(rshd, 13.089969);
                AuboController.rs_set_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_angle_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_arrival_ahead_distance(rshd, 0.01);
                AuboController.rs_add_waypoint(rshd, point6_joint);
                AuboController.rs_add_waypoint(rshd, point7_joint);
                AuboController.rs_move_track(rshd, 15, false);


                AuboController.rs_init_global_move_profile(rshd);
                AuboController.rs_set_global_end_max_line_velc(rshd, 0.04);
                AuboController.rs_set_global_end_max_line_acc(rshd, 1.570796);
                AuboController.rs_set_global_end_max_angle_velc(rshd, 2);
                AuboController.rs_set_global_end_max_angle_acc(rshd, 13.089969);
                AuboController.rs_set_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_angle_jerk_acc_ratio(rshd, 0.25);
                AuboController.rs_set_arrival_ahead_distance(rshd, 0.01);
                AuboController.rs_add_waypoint(rshd, point8_joint);
                AuboController.rs_add_waypoint(rshd, point9_joint);
                AuboController.rs_add_waypoint(rshd, point10_joint);
                AuboController.rs_move_track(rshd, 90, false);
            }



            AuboController.rs_endof_movegroup(rshd);
            AuboController.rs_wait_movegroup_finished(rshd);


        }

        private void button9_Click_1(object sender, EventArgs e)
        {
            double[] joint_1 = new double[] { 2.03, -0.15, -1.31, 0.3, -1.616, 0.05 };
            double[] joint_2 = new double[] { 2.38, 0.08, -1.07, 0.3, -1.65, 0.4 };
            double[] joint_3 = new double[] { 2.38, 0.139, -0.9, 0.43, -1.65, 0.4 };
            double[] joint_4 = new double[] { 1.41, -0.17, -1.21, 0.4, -1.545, -0.56 };
            double[] joint_5 = new double[] { 1.41, -0.29, -1.94, -0.19, -1.545, -0.566 };

            double[] maxVelc = new double[] {223.0 * M_PI / 180.0 * 0.8, 223.0 * M_PI / 180.0 * 0.8 , 223.0 * M_PI / 180.0 * 0.8,
                                             237.0 * M_PI / 180.0 * 0.8, 223.0 * M_PI / 180.0 * 0.8,  223.0 * M_PI / 180.0  * 0.8 };

            double[] maxAcc = new double[] {2975 * M_PI / 180.0 * 0.8, 2975 * M_PI / 180.0 * 0.8, 2975 * M_PI / 180.0 * 0.8,
                                            3564 * M_PI / 180.0 * 0.8, 3564 * M_PI / 180.0 * 0.8,3564 * M_PI / 180.0 * 0.8};
            
            AuboController.rs_set_global_end_max_line_acc(rshd, 5.0);//（最大线加速度5000mm/s²）

            AuboController.rs_set_global_end_max_line_velc(rshd, 2.4);//（最大线速度2400mm/s）

            AuboController.rs_set_global_joint_maxvelc(rshd, maxVelc);

            AuboController.rs_set_global_joint_maxacc(rshd, maxAcc);

            //AuboController.rs_move_line(rshd, joint_1, true);
            //AuboController.rs_move_line(rshd, joint_2, true);
            //AuboController.rs_move_line(rshd, joint_3, true);
            //AuboController.rs_move_line(rshd, joint_4, true);
            //AuboController.rs_move_line(rshd, joint_5, true);
            AuboController.rs_move_joint(rshd, joint_1, true);

            AuboController.rs_add_waypoint(rshd, joint_1);
            AuboController.rs_add_waypoint(rshd, joint_2);
            AuboController.rs_add_waypoint(rshd, joint_3);
            AuboController.rs_add_waypoint(rshd, joint_4);
            AuboController.rs_add_waypoint(rshd, joint_5);


            //AuboController.rs_move_track(rshd, AuboController.JOINT_GNUBSPLINEINTP, true);
            AuboController.rs_move_track(rshd, 10, true);

        }

        private void button10_Click_1(object sender, EventArgs e)
        {

            //string script_path = "E:/aubo_code/20240930/moveTest.aubo";
            //string script_string = File.ReadAllText(script_path);
            
            string script_string = "move_group";

            Console.Out.WriteLine("{0}", script_string);

            //string md_string = "848cc4469070c4f5d6ceee8c72283212";

            //AuboController.rs_script_run_string(rshd, script_string, md_string);
        }

        private void button11_Click_1(object sender, EventArgs e)
        {
            AuboController.CoordCalibrate coord = new AuboController.CoordCalibrate();
            coord.coordType = 1;
            coord.toolDesc.cartPos.x = 0.0;
            coord.toolDesc.cartPos.y = 0.0;
            coord.toolDesc.cartPos.z = 0.3;
            coord.toolDesc.orientation.w = 1.0;
            coord.toolDesc.orientation.x = 0.0;
            coord.toolDesc.orientation.y = 0.0;
            coord.toolDesc.orientation.z = 0.0;

            AuboController.MoveRotateAxis axis = new AuboController.MoveRotateAxis();
            axis.rotateAxis = new double[3];

            axis.rotateAxis[0] = 0.0;
            axis.rotateAxis[1] = 1.0;
            axis.rotateAxis[2] = 0.0;

            double rotate_angle = 30.0 * M_PI / 180.0;

            AuboController.rs_move_rotate(rshd, ref coord, ref axis, rotate_angle, true);
        }

        private void button12_Click_1(object sender, EventArgs e)
        {

            //创建机械臂控制上下文句柄
            if (AuboController.rs_create_context(ref rshd) == RSERR_SUCC)
            {
                Console.WriteLine("创建上下文句柄成功!");
            }

            //断开机械臂服务器链接
            if (AuboController.rs_logout(rshd) == RSERR_SUCC)
            {
                Console.WriteLine("logout!");
                //注销机械臂控制上下文句柄

                MessageBox.Show("注销成功");

            }
            else
            {
                Console.WriteLine("logout failure!");
                MessageBox.Show("注销失败");
            }
        }

        // 静态变量来存储最新的路点数据
        private static AuboController.wayPoint_S latestWaypoint;

        private void button13_Click_1(object sender, EventArgs e)
        {
            //realtimeRoadPointExample(rshd);
            AuboController.realtimeRoadPointExample(rshd);
        }

        // 事件处理函数
        private void OnWaypointReceived(object sender, AuboController.WaypointEventArgs e)
        {
            // 获取最新的路点信息
            latestWaypoint = e.Waypoint;
            Console.WriteLine($"接收到路点信息: X={waypoint.cartPos.x}, Y={waypoint.cartPos.y}, Z={waypoint.cartPos.z}");
        }

        public static void realtimeRoadPointExample(UInt16 rshd)
        {
            int ret = AuboController.rs_enable_push_realtime_roadpoint(rshd, true);
            if (ret == RSERR_SUCC)
            {
                AuboController.rs_setcallback_realtime_roadpoint(rshd, CurrentPositionCallback, IntPtr.Zero);
                Thread.Sleep(5 * 1000);
            }
            else
            {
                Console.Error.WriteLine("调用 rs_enable_push_realtime_roadpoint 函数失败！错误码：{ 0} ", ret);
            }
        }


        // 回调函数
        private static void CurrentPositionCallback(ref AuboController.wayPoint_S waypoint, IntPtr arg)
        {
            AuboController.PrintWaypoint(waypoint);
            // 更新最新的路点数据
            latestWaypoint = waypoint;
        }


        private void button14_Click_1(object sender, EventArgs e)
        {
            // 这里可以添加更多处理逻辑，例如打印数据
            MessageBox.Show("路点信息：" + latestWaypoint.cartPos.x + "," + latestWaypoint.cartPos.y + "," + latestWaypoint.cartPos.z);
        }

        private void button15_Click_1(object sender, EventArgs e)
        {
            // 创建线程并指定要执行的方法
            Thread thread = new Thread(start8891);
            // 启动线程
            thread.Start();

        }


        static void start8891()
        {
            //for (int i = 0; i < 5; i++)
            //{
            //    Console.WriteLine($"子线程: {i}");
            //    Thread.Sleep(300); // 睡眠 300 毫秒
            //}
            // 定义服务端地址和端口
            string serverIP = "192.168.41.129";
            int serverPort = 8891;

            // 创建 TCP 客户端
            TcpClient client = new TcpClient();
            try
            {
                client.Connect(serverIP, serverPort);
                Console.WriteLine("服务器连接成功！");

                // 获取网络流
                NetworkStream stream = client.GetStream();
                byte[] recvBuffer = new byte[1550];
                int count = 1000;

                while (count-- > 0)
                {
                    // 接收数据
                    int bytesRead = stream.Read(recvBuffer, 0, recvBuffer.Length);
                    if (bytesRead > 0)
                    {
                        // 将字节数组转换为字符串
                        string receivedData = Encoding.UTF8.GetString(recvBuffer, 0, bytesRead);
                        Console.WriteLine($"接收到数据: {receivedData}");

                        // 查找特定字符串
                        string joint_get1 = "jointPos";
                        string joint_get2 = "jointTagCurrentI";

                        int startIndex = receivedData.IndexOf(joint_get1);
                        int endIndex = receivedData.IndexOf(joint_get2);

                        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex)
                        {
                            // 提取所需的数据
                            string essid = receivedData.Substring(startIndex + 11, endIndex - startIndex - 13);
                            Console.WriteLine($"提取数据: {essid}");

                            // 分割字符串并存储到 double 数组中
                            double[] values = new double[6];
                            string[] stringValues = essid.Split(',');

                            // 确保分割后的数组长度不超过 6
                            for (int i = 0; i < stringValues.Length && i < 6; i++)
                            {
                                if (double.TryParse(stringValues[i], out double parsedValue))
                                {
                                    values[i] = parsedValue; // 将解析后的值存储到 double 数组中
                                }
                                else
                                {
                                    Console.WriteLine($"无法解析 '{stringValues[i]}' 为 double 类型.");
                                }
                            }
                        }
                    }
                    else
                    {
                        Console.WriteLine("接收失败！");
                        break;
                    }
                }
                Console.WriteLine("运行完成！");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"发生错误: {ex.Message}");
            }
            finally
            {
                // 关闭客户端
                client.Close();
            }
        }
    }
}
