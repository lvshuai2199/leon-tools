#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Elite 30001 脚本下发 + 29999 Dashboard（上电/下电/松闸/play/查任务）。"""
from __future__ import print_function

import os
import socket
import sys
import time
from pathlib import Path

SCRIPT_SUFFIXES = {".script", ".lua", ".py", ".txt"}
DEFAULT_HOST = os.environ.get("SCRIPT_SEND_HOST", "192.168.249.128")
DEFAULT_PORT = int(os.environ.get("SCRIPT_SEND_PORT", "30001"))
DEFAULT_DASHBOARD_PORT = int(os.environ.get("DASHBOARD_PORT", "29999"))
DEFAULT_TIMEOUT = float(os.environ.get("SCRIPT_SEND_TIMEOUT", "5.0"))


def app_dir():
    """源码运行时用脚本目录；打包后用 exe 所在目录，设置和轨迹写在旁边。"""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


MODULE_DIR = app_dir()
DEFAULT_SCRIPT_DIR = Path(os.environ.get("SCRIPT_OUTPUT_DIR", str(MODULE_DIR / "scripts")))


class SendError(Exception):
    def __init__(self, message, code=1):
        self.message = message
        self.code = code
        Exception.__init__(self, message)


def list_script_files(directory=None):
    """根目录 + 一层子目录内的 .script/.lua/.py/.txt（不更深），按相对路径排序。"""
    d = Path(directory) if directory else DEFAULT_SCRIPT_DIR
    if not d.exists():
        return []
    if not d.is_dir():
        raise OSError("not a directory: %s" % d)
    d = d.resolve()
    files = []

    def _maybe_add(p):
        try:
            if p.is_file() and p.suffix.lower() in SCRIPT_SUFFIXES:
                files.append(p)
        except OSError:
            pass

    try:
        for p in d.iterdir():
            try:
                if p.is_file():
                    _maybe_add(p)
                elif p.is_dir():
                    for child in p.iterdir():
                        _maybe_add(child)
            except OSError:
                continue
    except PermissionError:
        raise
    except OSError:
        raise

    def _key(p):
        try:
            return p.relative_to(d).as_posix().lower()
        except ValueError:
            return p.name.lower()

    files.sort(key=_key)
    return files



def send_text(script_text, host=None, port=None, timeout=None, read_reply=False):
    host = host or DEFAULT_HOST
    port = int(port or DEFAULT_PORT)
    timeout = float(timeout if timeout is not None else DEFAULT_TIMEOUT)
    text = script_text if script_text.endswith("\n") else script_text + "\n"
    content = text.encode("utf-8")
    if not content:
        raise SendError("脚本内容为空", 4005)
    start = time.time()
    reply = ""
    try:
        with socket.create_connection((host, port), timeout=timeout) as sock:
            sock.sendall(content)
            if read_reply:
                sock.settimeout(timeout)
                try:
                    reply = sock.recv(8192).decode("utf-8", "ignore")
                except socket.timeout:
                    reply = ""
    except (ConnectionRefusedError, ConnectionResetError, socket.timeout, TimeoutError, OSError) as exc:
        raise SendError("脚本下发失败（%s:%s）: %s" % (host, port, exc), 5003)
    return {
        "host": host,
        "port": port,
        "bytes_sent": len(content),
        "duration_ms": int((time.time() - start) * 1000),
        "reply": reply,
    }


def dashboard_cmd(command, host=None, port=None, timeout=None):
    host = host or DEFAULT_HOST
    port = int(port or DEFAULT_DASHBOARD_PORT)
    timeout = float(timeout if timeout is not None else DEFAULT_TIMEOUT)
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect((host, port))
        sock.settimeout(min(0.4, timeout))
        try:
            sock.recv(4096)
        except socket.timeout:
            pass
        sock.settimeout(timeout)
        line = command if command.endswith("\n") else command + "\n"
        sock.sendall(line.encode("utf-8"))
        data = sock.recv(4096).decode("utf-8", "ignore")
        return data.replace("\n", "").replace("\r", "")
    except (ConnectionRefusedError, ConnectionResetError, socket.timeout, TimeoutError, OSError) as exc:
        raise SendError("Dashboard 失败（%s:%s）: %s" % (host, port, exc), 5003)
    finally:
        sock.close()


def explain_dashboard(reply):
    """把 Dashboard 英文拒绝翻成示教器上要做的事。"""
    text = (reply or "").strip()
    low = text.lower()
    if "enable remote control setting" in low or "enable setting first" in low:
        return "示教器未启用远程控制。请打开 Elite 图标 → 设置，启用远程控制后再试。"
    if "operation mode" in low and "automatic" in low:
        return "当前是手动模式。请在示教器先切到自动模式，再切到远程控制。"
    if "local control mode" in low:
        return "控制器在本地控制模式，上电/松闸/运行被拒绝。请在示教器把「本地控制」切成「远程控制」。"
    if "enable failed" in low or "disable failed" in low:
        return "切换控制模式失败：" + text
    return text


def ensure_remote_control(host=None, timeout=None):
    """上电、松闸、运行在本地控制模式下会被拒。LOCAL 时先发 remoteControl -on。

    NONE（未启用远程控制，混合模式）和 REMOTE 不改。旧固件不认识该命令时也不拦后续操作。
    """
    mode = dashboard_cmd("remoteControl -s", host=host, timeout=timeout).strip()
    if mode.upper() != "LOCAL":
        return {"ok": True, "mode": mode, "switched": False, "reply": mode}
    switched = dashboard_cmd("remoteControl -on", host=host, timeout=timeout).strip()
    ok = switched == "Remote control mode enabled."
    return {
        "ok": ok,
        "mode": "REMOTE" if ok else mode,
        "switched": ok,
        "reply": switched,
        "message": "已切换到远程控制" if ok else explain_dashboard(switched),
    }


def powering_on(host=None, timeout=None):
    remote = ensure_remote_control(host=host, timeout=timeout)
    if not remote["ok"]:
        return {"ok": False, "reply": remote["reply"], "message": remote["message"]}
    data = dashboard_cmd("robotControl -on", host=host, timeout=timeout)
    time.sleep(0.5)
    ok = data == "Powering on"
    prefix = "已切换到远程控制，" if remote.get("switched") else ""
    message = (prefix + "上电成功") if ok else explain_dashboard(data)
    return {"ok": ok, "reply": data, "message": message}


def powering_off(host=None, timeout=None, stop_event=None, interval=0.5, max_wait=60.0):
    remote = ensure_remote_control(host=host, timeout=timeout)
    if not remote["ok"]:
        return {"ok": False, "reply": remote["reply"], "message": remote["message"]}
    started = time.time()
    last = ""
    while True:
        if stop_event is not None and stop_event.is_set():
            raise SendError("下电已取消")
        if max_wait and (time.time() - started) > max_wait:
            raise SendError("下电超时: %s" % last)
        last = dashboard_cmd("robotControl -off", host=host, timeout=timeout)
        if last == "Powering off":
            prefix = "已切换到远程控制，" if remote.get("switched") else ""
            return {"ok": True, "reply": last, "message": prefix + "下电成功"}
        if "local control mode" in last.lower():
            return {"ok": False, "reply": last, "message": explain_dashboard(last)}
        time.sleep(interval)


def brake_releasing(host=None, timeout=None, stop_event=None, interval=0.5, max_wait=60.0):
    remote = ensure_remote_control(host=host, timeout=timeout)
    if not remote["ok"]:
        return {"ok": False, "reply": remote["reply"], "message": remote["message"]}
    started = time.time()
    last = ""
    while True:
        if stop_event is not None and stop_event.is_set():
            raise SendError("松闸已取消")
        if max_wait and (time.time() - started) > max_wait:
            raise SendError("松闸超时: %s" % last)
        last = dashboard_cmd("brakeRelease", host=host, timeout=timeout)
        if last == "Brake is released.":
            prefix = "已切换到远程控制，" if remote.get("switched") else ""
            return {"ok": True, "reply": last, "message": prefix + "抱闸释放成功"}
        if "local control mode" in last.lower():
            return {"ok": False, "reply": last, "message": explain_dashboard(last)}
        time.sleep(interval)


def play(host=None, timeout=None):
    remote = ensure_remote_control(host=host, timeout=timeout)
    if not remote["ok"]:
        return {"ok": False, "reply": remote["reply"], "message": remote["message"]}
    data = dashboard_cmd("play", host=host, timeout=timeout)
    time.sleep(1)
    ok = data == "Starting task"
    prefix = "已切换到远程控制，" if remote.get("switched") else ""
    message = (prefix + "已启动任务") if ok else ("运行失败：" + explain_dashboard(data))
    return {"ok": ok, "reply": data, "message": message}


def task(host=None, timeout=None):
    data = dashboard_cmd("task -r", host=host, timeout=timeout)
    running = data == "Task is running"
    msg = "任务正在运行" if running else ("当前运行状态为：" + data)
    return {"ok": True, "reply": data, "running": running, "message": msg}


def close_popup(host=None, timeout=None):
    """手册：popup -c，关闭最近由 popup 弹出的消息框，返回 Closing popup。"""
    data = dashboard_cmd("popup -c", host=host, timeout=timeout)
    ok = "closing popup" in data.lower()
    return {"ok": ok, "reply": data, "message": "已清除弹窗" if ok else data}


def stop_task(host=None, timeout=None):
    """手册：stop，停止当前任务/工程。"""
    data = dashboard_cmd("stop", host=host, timeout=timeout)
    low = data.lower()
    fail = ("fail" in low) or ("error" in low) or ("only supported" in low)
    return {
        "ok": not fail,
        "reply": data,
        "message": "工程已停止" if not fail else ("停止失败：" + data),
    }


def emergency_stop(host=None, timeout=None):
    """急停：30001 下发 halt 立刻停运动，并 29999 stop 停任务。"""
    halt_ok = True
    halt_msg = "halt 已发"
    try:
        send_text("halt\n", host=host, timeout=timeout, read_reply=False)
    except SendError as exc:
        halt_ok = False
        halt_msg = "halt 失败: %s" % exc.message
    stop = stop_task(host=host, timeout=timeout)
    ok = halt_ok and stop["ok"]
    return {
        "ok": ok,
        "reply": stop.get("reply") or "",
        "message": "急停  %s；%s" % (halt_msg, stop["message"]),
        "stop": stop,
    }


def boot_and_send(script_text, host=None, timeout=None, stop_event=None, read_reply=False):
    power = powering_on(host=host, timeout=timeout)
    if not power["ok"]:
        raise SendError("上电失败: %s" % power["reply"])
    brake = brake_releasing(host=host, timeout=timeout, stop_event=stop_event)
    send = send_text(script_text, host=host, timeout=timeout, read_reply=read_reply)
    send["message"] = "上电+松闸+下发完成  %s bytes" % send["bytes_sent"]
    send["power"] = power
    send["brake"] = brake
    return send
