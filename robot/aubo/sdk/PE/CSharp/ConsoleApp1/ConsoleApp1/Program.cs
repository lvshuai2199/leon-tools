using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Runtime.InteropServices;
using ConsoleApp1;
using System.Windows.Forms;

namespace SdkTest
{
    class Program
    {

        static void Main(string[] args)
        {
            Form1 myClass = new Form1();

            Application.Run(myClass); // 使用 Application.Run 来保持窗体运行

        }
    }
}
