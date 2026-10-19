using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

static class Program
{
    static Mutex mutex;

    [DllImport("user32.dll")]
    static extern bool SetProcessDPIAware();


    [STAThread]
    static int Main(string[] args)
    {
        try { SetProcessDPIAware(); } catch { }
        bool silent = false;
        foreach (string arg in args)
        {
            if (string.Equals(arg, "--silent", StringComparison.OrdinalIgnoreCase)) silent = true;
        }

        string dir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        bool created;
        mutex = new Mutex(true, @"Local\GitPicbedTray", out created);
        if (!created)
        {
            if (!silent) OpenPage(dir);
            return 0;
        }

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new TrayApp(dir, silent));
        return 0;
    }

    internal static void OpenPage(string dir)
    {
        Process.Start(new ProcessStartInfo
        {
            FileName = "http://127.0.0.1:" + ReadPort(dir) + "/",
            UseShellExecute = true
        });
    }

    static int ReadPort(string dir)
    {
        int port = 3791;
        string cfg = Path.Combine(dir, "config.json");
        if (!File.Exists(cfg)) return port;
        try
        {
            Match match = Regex.Match(File.ReadAllText(cfg), "\"port\"\\s*:\\s*(\\d+)");
            if (match.Success) port = int.Parse(match.Groups[1].Value);
        }
        catch
        {
        }
        return port;
    }
}

sealed class HiddenForm : Form
{
    public HiddenForm()
    {
        ShowInTaskbar = false;
        FormBorderStyle = FormBorderStyle.None;
        CreateHandle();
    }

    protected override void SetVisibleCore(bool value)
    {
        if (!IsHandleCreated) CreateHandle();
        base.SetVisibleCore(false);
    }
}

sealed class TrayApp : ApplicationContext
{
    readonly string dir;
    readonly Form pump;
    readonly NotifyIcon notify;
    Process child;
    bool quitting;

    public TrayApp(string dir, bool silent)
    {
        this.dir = dir;
        pump = new HiddenForm();
        MainForm = pump;

        string node = @"D:\Softs\nvm4w\nodejs\node.exe";
        if (!File.Exists(node)) node = "node";

        Icon icon = LoadTrayIcon(dir, node);

        var open = new ToolStripMenuItem("\u6253\u5f00\u9875\u9762");
        open.Click += (s, e) => Program.OpenPage(dir);
        var exit = new ToolStripMenuItem("\u9000\u51fa");
        exit.Click += (s, e) => Quit();
        var menu = new ContextMenuStrip();
        menu.Items.Add(open);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add(exit);

        notify = new NotifyIcon();
        notify.Icon = icon;
        notify.Text = "Git \u56fe\u5e8a";
        notify.ContextMenuStrip = menu;
        notify.Visible = true;
        notify.MouseClick += (s, e) =>
        {
            if (e.Button == MouseButtons.Left) Program.OpenPage(dir);
        };

        var psi = new ProcessStartInfo();
        psi.FileName = node;
        psi.Arguments = "\"" + Path.Combine(dir, "server.js") + "\"";
        psi.WorkingDirectory = dir;
        psi.UseShellExecute = false;
        psi.CreateNoWindow = true;
        if (silent) psi.EnvironmentVariables["PICBED_NO_BROWSER"] = "1";
        child = Process.Start(psi);
        child.EnableRaisingEvents = true;
        child.Exited += (s, e) =>
        {
            try { pump.BeginInvoke((Action)HandleChildExit); }
            catch { Environment.Exit(0); }
        };
    }

    static Icon LoadTrayIcon(string dir, string node)
    {
        string ico = Path.Combine(dir, "git-picbed.ico");
        if (File.Exists(ico))
        {
            try
            {
                int px = TrayIconPixels();
                return new Icon(ico, new Size(px, px));
            }
            catch { }
        }
        return LoadNodeIcon(node);
    }

    static int TrayIconPixels()
    {
        try
        {
            using (var g = Graphics.FromHwnd(IntPtr.Zero))
            {
                float scale = g.DpiX / 96f;
                if (scale >= 2f) return 32;
                if (scale >= 1.25f) return 24;
                return 16;
            }
        }
        catch
        {
            return 16;
        }
    }


    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    static extern int SHDefExtractIcon(string pszIconFile, int iIndex, uint uFlags, out IntPtr phiconLarge, out IntPtr phiconSmall, uint nIconSize);

    [DllImport("user32.dll", SetLastError = true)]
    static extern bool DestroyIcon(IntPtr hIcon);

    static Icon LoadNodeIcon(string node)
    {
        IntPtr large;
        IntPtr small;
        uint size = 32 | (16u << 16);
        if (SHDefExtractIcon(node, 0, 0, out large, out small, size) != 0 || large == IntPtr.Zero)
        {
            if (small != IntPtr.Zero) DestroyIcon(small);
            return (Icon)SystemIcons.Application.Clone();
        }
        Icon icon;
        using (Icon tmp = Icon.FromHandle(large)) icon = (Icon)tmp.Clone();
        DestroyIcon(large);
        if (small != IntPtr.Zero) DestroyIcon(small);
        return icon;
    }

    void HandleChildExit()
    {
        if (quitting) return;
        quitting = true;
        notify.Visible = false;
        ExitThread();
    }

    void Quit()
    {
        if (quitting) return;
        quitting = true;
        try
        {
            if (child != null && !child.HasExited) child.Kill();
        }
        catch
        {
        }
        notify.Visible = false;
        notify.Dispose();
        ExitThread();
    }
}
