using csharp_example;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace robotControl_CSharp
{
    internal class Program
    {
        const int RSERR_SUCC = 0;

        static UInt16 rshd = 0xffff;
        //机械臂IP地址
        const string robot_ip = "192.168.41.134";
        //机械臂端口号
        const int server_port = 30004;


        // 等待机械臂进入目标模式
        static void WaitForRobotMode(IntPtr robot_state, cSharpBinging_TypeDef.RobotModeType target_mode)
        {
            // 接口调用: 获取当前机械臂的模式
            cSharpBinging_TypeDef.RobotModeType current_mode = cSharpBinging_RobotState.getRobotModeType(robot_state);

            while (current_mode != target_mode)
            {
                Console.WriteLine($"机械臂当前模式: {current_mode}");
                Thread.Sleep(1000);
                current_mode = cSharpBinging_RobotState.getRobotModeType(robot_state);
            }
        }

        static int exampleStartup(IntPtr cli)
        {
            IntPtr[] robot_names = new IntPtr[10];
            for (int i = 0; i < 10; i++)
            {
                robot_names[i] = Marshal.AllocHGlobal(100); // 分配100字节内存用于存放字符串（考虑 '\0' 结尾）
            }

            int num = cSharpBinding_RPC.rpc_getRobotNames(cli, robot_names);
            if (num <= 0)
            {
                for (int i = 0; i < 10; i++)
                {
                    Marshal.FreeHGlobal(robot_names[i]); // 释放分配的内存
                }
                return -1;
            }
            string robot_name = Marshal.PtrToStringAnsi(robot_names[0]);
            for (int i = 0; i < 10; i++)
            {
                Marshal.FreeHGlobal(robot_names[i]); // 释放分配的内存
            }
            if (robot_name == "")
            {
                return -1;
            }

            IntPtr robot_interface = cSharpBinding_RPC.rpc_getRobotInterface(cli, robot_name);

            // 接口调用: 设置负载
            double mass = 0.0; // 示例质量值
            double[] cog = { 0.0, 0.0, 0.0 }; // 示例重心坐标
            double[] aom = { 0.0, 0.0, 0.0 }; // 示例附加质量矩
            double[] inertia = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 }; // 示例惯性张量

            IntPtr robot_config = cSharpBinging_RobotInterface.robot_getRobotConfig(robot_interface);

            cSharpBinging_RobotConfig.setPayload(robot_config, mass, cog, aom, inertia);

            IntPtr robot_state = cSharpBinging_RobotInterface.robot_getRobotState(robot_interface);
            // 接口调用: 获取机械臂当前模式
            cSharpBinging_TypeDef.RobotModeType robot_mode = cSharpBinging_RobotState.getRobotModeType(robot_state);

            if (robot_mode == cSharpBinging_TypeDef.RobotModeType.Running)
            {
                Console.WriteLine("机械臂已松刹车，处于运行模式");
                return -1;
            }

            IntPtr robot_manage = cSharpBinging_RobotInterface.robot_getRobotManage(robot_interface);
            // 接口调用: 机械臂发起上电请求
            cSharpBinging_RobotManage.poweron(robot_manage);


            // 等待机械臂进入空闲模式
            WaitForRobotMode(robot_state, cSharpBinging_TypeDef.RobotModeType.Idle);

            robot_mode = cSharpBinging_RobotState.getRobotModeType(robot_state);
            Console.WriteLine($"机械臂上电成功，当前模式:{0}", robot_mode);

            // 接口调用: 机械臂发起松刹车请求
            cSharpBinging_RobotManage.startup(robot_manage);

            // 等待机械臂进入运行模式
            WaitForRobotMode(robot_state, cSharpBinging_TypeDef.RobotModeType.Running);

            robot_mode = cSharpBinging_RobotState.getRobotModeType(robot_state);
            Console.WriteLine($"机械臂松刹车成功，当前模式: {0}", robot_mode);

            return 0;
        }
        static void Main(string[] args)
        {
            cSharpBinding_RPC csharpbingding_rpc = new cSharpBinding_RPC();
            //初始化机械臂控制库
            IntPtr rpc_client = cSharpBinding_RPC.rpc_create_client(0);
            Console.Out.WriteLine("rpc_create_client ret={0}", rpc_client);
            if (rpc_client == IntPtr.Zero)
            {
                Console.Error.WriteLine("rpc_create_client failed!");
            }
            cSharpBinding_RPC.rpc_connect(rpc_client, robot_ip, server_port);
            cSharpBinding_RPC.rpc_setRequestTimeout(rpc_client, 1000);
            cSharpBinding_RPC.rpc_login(rpc_client, "aubo", "123456");


            //用电脑控制机器人的启动
            exampleStartup(rpc_client);
            //加载工程，并启动
            exampleLoadProject(rpc_client);
            //停止 选择程序号和机器人速度

            exampleStopProgram(rpc_client);



        }

        static int exampleStopProgram(IntPtr rpc_client)
        {
            IntPtr rutime_machine = cSharpBinding_RPC.rpc_getRuntimeMachine(rpc_client);
            Console.WriteLine("停止工程");

            int stopRes = cSharpBinging_RuntimeMachine.stop(rutime_machine);

            Console.WriteLine("stop{0}", stopRes);
            return stopRes;
        }

        static int exampleLoadProject(IntPtr cli)
        {
            IntPtr rutime_machine = cSharpBinding_RPC.rpc_getRuntimeMachine(cli);
            Console.WriteLine("加载工程");


            string program = "test";

            int loadProgramRes = cSharpBinging_RuntimeMachine.loadProgram(rutime_machine, program);
            Console.WriteLine("加载工程返回值：{0}", loadProgramRes);

            cSharpBinging_RuntimeMachine.runProgram(rutime_machine);
            return loadProgramRes;
        }
    }
}
