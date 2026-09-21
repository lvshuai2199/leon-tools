from __future__ import annotations
import argparse, signal, sys, time
from can_tester.bus import DEFAULT_IFACE, VIRTUAL_IFACE, CanSession, list_can_interfaces
from can_tester.parse import parse_can_data, parse_can_id_auto
from can_tester.config import (
    frame_log_key,
    is_new_or_changed_payload,
    load_settings,
    parse_rx_id_whitelist,
)


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Linux SocketCAN 通用监听 / 发送（可选 Megmeet 预设）")
    p.add_argument("--gui", action="store_true", help="启动图形界面（默认）")
    p.add_argument("--cli", action="store_true", help="命令行模式")
    p.add_argument("--iface", default=DEFAULT_IFACE, help=f"接口名（默认 {DEFAULT_IFACE}；也可用 {VIRTUAL_IFACE}）")
    p.add_argument("--list", action="store_true", help="列出可用接口后退出")
    p.add_argument("--id", default="0x123", help="发送 CAN ID")
    p.add_argument("--data", default="01 02 03 04", help="发送数据十六进制")
    p.add_argument("--ext", action="store_true", help="强制扩展帧（否则按 ID 自动）")
    p.add_argument("--period-ms", type=float, default=0, help="周期发送间隔毫秒；0=单次")
    p.add_argument("--listen", action="store_true", help="仅监听")
    p.add_argument("--count", type=int, default=0, help="最多收发条数；0=无限")
    p.add_argument(
        "--init-module",
        default="",
        metavar="PATH",
        help="连接后、监听前运行初始化模块 JSON（path/to.json）",
    )
    p.add_argument(
        "--init-dry-run",
        action="store_true",
        help="仅解析/演练初始化模块，不访问总线（可与 --init-module 联用，无需 --cli）",
    )
    p.add_argument(
        "--init-checked-only",
        action="store_true",
        help="只执行模块中 enabled=true 的行（与 --init-module / --init-dry-run 联用）",
    )
    p.add_argument(
        "--list-modules",
        action="store_true",
        help="列出可用初始化模块后退出",
    )
    p.add_argument(
        "--print-client",
        action="store_true",
        help="连续打印客户端 RX（覆盖配置默认关闭；总线 RX 始终打印）",
    )
    p.add_argument(
        "--no-print-client",
        action="store_true",
        help="关闭客户端 RX 选项；总线 RX 仍全部打印，发送后按配置回帧条数提示超时",
    )
    p.add_argument(
        "--rx-filter",
        default=None,
        metavar="IDS",
        help="RX ID 白名单（逗号/空格分隔十六进制，例 1FD07063,1FD08063；空=全部）",
    )
    p.add_argument(
        "--rx-changes-only",
        action="store_true",
        help="仅数据变化时打印（同方向+同 ID 数据未变不打印；TX 同样抑制）",
    )
    p.add_argument(
        "--responder",
        action="store_true",
        help="启用应答服务（匹配应答档自动回帧；与 --listen / 发送联用）",
    )
    p.add_argument(
        "--responder-module",
        default="",
        metavar="NAME",
        help="应答档名或 stem（如 megmeet_welder）；空则用配置或首个随包档",
    )
    p.add_argument(
        "--list-responders",
        action="store_true",
        help="列出可用应答档后退出",
    )
    return p


def _run_init(session: CanSession | None, path: str, dry_run: bool, only_enabled: bool) -> int:
    from can_tester.init_module import load_module, run_module
    from can_tester.config import load_settings as _ls

    try:
        mod = load_module(path)
    except Exception as exc:
        print(f"加载初始化模块失败: {exc}", file=sys.stderr)
        return 1
    enabled_n = sum(1 for r in mod.rows if r.enabled)
    print(
        f"初始化模块: {mod.name}（{len(mod.rows)} 行"
        + (f"，启用 {enabled_n}" if only_enabled else "")
        + f"，默认间隔 {mod.default_interval_ms} ms）"
    )

    def on_progress(i, total, row, msg):
        print(f"  [{i + 1}/{total}] {row.display}: {msg}")

    settings = _ls()
    result = run_module(
        mod,
        session,
        dry_run=dry_run,
        on_progress=on_progress,
        only_enabled=only_enabled,
        default_interval_ms=settings.default_interval_ms,
    )
    if result.ok:
        print(result.message)
        return 0
    print(result.message, file=sys.stderr)
    return 1


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.list:
        for name in list_can_interfaces():
            print(name)
        return 0
    if args.list_modules:
        from can_tester.init_module import list_modules

        mods = list_modules()
        if not mods:
            print("(无模块)")
            return 0
        for m in mods:
            loc = str(m.path) if m.path else "?"
            on = sum(1 for r in m.rows if r.enabled)
            print(
                f"{m.name}\trows={len(m.rows)}\tenabled={on}\t"
                f"interval={m.default_interval_ms}ms\t{loc}"
            )
        return 0
    if args.list_responders:
        from can_tester.responder_module import list_responder_modules

        mods = list_responder_modules()
        if not mods:
            print("(无应答档)")
            return 0
        for m in mods:
            loc = str(m.path) if m.path else "?"
            on = sum(1 for r in m.rows if r.enabled)
            eng = m.engine or "static"
            print(
                f"{m.name}\tstem={m.path.stem if m.path else '?'}\t"
                f"rows={len(m.rows)}\tenabled={on}\tengine={eng}\t{loc}"
            )
        return 0
    if args.init_dry_run and args.init_module:
        return _run_init(
            None, args.init_module, dry_run=True, only_enabled=args.init_checked_only
        )
    if not args.cli:
        from can_tester.gui import run_gui
        run_gui(initial_iface=args.iface)
        return 0

    settings = load_settings()
    if args.print_client:
        settings.print_client_data = True
    if args.no_print_client:
        settings.print_client_data = False
    if args.rx_filter is not None:
        settings.rx_id_filter = str(args.rx_filter)
    if args.rx_changes_only:
        settings.rx_changes_only = True
    if args.responder:
        settings.enable_responder = True
    if args.responder_module:
        settings.responder_module = str(args.responder_module).strip()
    settings = settings.clamp()

    session = CanSession()
    stop = False

    def _sig(*_):
        nonlocal stop
        stop = True

    signal.signal(signal.SIGINT, _sig)
    signal.signal(signal.SIGTERM, _sig)
    try:
        session.connect(
            args.iface,
            listen_only_shared=bool(settings.listen_only_shared),
        )
    except Exception as exc:
        print(exc, file=sys.stderr)
        return 1
    print(f"已连接 {session.channel} ({session.backend})")
    resp_svc = None
    if settings.enable_responder:
        from can_tester.responder_module import (
            find_responder_by_name_or_stem,
            list_responder_modules,
        )
        from can_tester.responder_service import ResponderService

        key = (settings.responder_module or "").strip()
        mod = find_responder_by_name_or_stem(key) if key else None
        if mod is None:
            mods = list_responder_modules()
            mod = mods[0] if mods else None
        if mod is None:
            print("应答服务: 无可用应答档", file=sys.stderr)
            session.disconnect()
            return 1
        def _on_tx(fr):
            kind = "EXT" if fr.is_extended else "STD"
            print(f"TX  {fr.id_text}  {kind}  [{fr.dlc}]  {fr.data_hex}  (应答)")

        resp_svc = ResponderService(session, mod, on_tx=_on_tx)
        print(
            f"应答服务: 开 · {mod.name}"
            f"（engine={mod.engine or 'static'}）"
        )
        if session.backend == "virtual":
            print(
                "提示: virtual 仅本进程内共享；双终端请用 vcan0，"
                "或同进程库测（tests/test_dual_virtual_responder.py）",
                file=sys.stderr,
            )
    wl = parse_rx_id_whitelist(settings.rx_id_filter)
    if not settings.print_client_data:
        print(
            f"打印客户端数据: 关（发送后按 {settings.reply_count} 条回帧计数，"
            f"超时 {settings.reply_timeout_ms:g} ms）"
        )
    else:
        print("打印客户端数据: 开")
    if wl:
        print(
            "RX 白名单: "
            + ",".join(f"0x{i:X}" for i in sorted(wl))
            + ("；仅数据变化时打印" if settings.rx_changes_only else "")
        )
    elif settings.rx_changes_only:
        print("RX 过滤: 全收 + 仅数据变化时打印")
    else:
        print("RX 过滤: 全收")

    if args.init_module:
        rc = _run_init(
            session,
            args.init_module,
            dry_run=False,
            only_enabled=args.init_checked_only,
        )
        if rc != 0:
            session.disconnect()
            return rc

    sent = recv = 0
    next_tx = time.time()
    await_replies = 0
    await_deadline = 0.0
    await_got = 0
    last_shown: dict = {}
    try:
        while not stop:
            whitelist = parse_rx_id_whitelist(settings.rx_id_filter)
            for fr in session.drain_rx():
                if resp_svc is not None:
                    try:
                        resp_svc.handle_frame(fr)
                    except Exception as exc:
                        print(f"应答失败: {exc}", file=sys.stderr)
                if whitelist and fr.can_id not in whitelist:
                    continue
                if (not settings.print_client_data) and await_replies > 0:
                    await_got += 1
                    await_replies -= 1
                key = frame_log_key("RX", fr.can_id, bool(fr.is_extended))
                changed = is_new_or_changed_payload(last_shown, key, fr.data)
                if settings.rx_changes_only and not changed:
                    continue
                kind = "EXT" if fr.is_extended else "STD"
                print(f"RX  {fr.id_text}  {kind}  [{fr.dlc}]  {fr.data_hex}")
                recv += 1
                if args.count and (sent + recv) >= args.count:
                    stop = True
                    break
            if (
                not settings.print_client_data
                and await_replies > 0
                and time.time() >= await_deadline
            ):
                print(
                    f"回帧超时：期望 {settings.reply_count} 条，已收到 {await_got} 条，"
                    f"仍缺 {await_replies} 条（超时 {settings.reply_timeout_ms:g} ms）",
                    file=sys.stderr,
                )
                await_replies = 0
            while True:
                try:
                    print(session.error_queue.get_nowait(), file=sys.stderr)
                except Exception:
                    break
            if stop:
                break
            if not args.listen and time.time() >= next_tx:
                try:
                    can_id, ext = parse_can_id_auto(args.id, True if args.ext else None)
                    data = parse_can_data(args.data)
                    fr = session.send(can_id, data, ext)
                    tx_key = frame_log_key("TX", fr.can_id, bool(fr.is_extended))
                    tx_changed = is_new_or_changed_payload(last_shown, tx_key, fr.data)
                    if not settings.rx_changes_only or tx_changed:
                        kind = "EXT" if fr.is_extended else "STD"
                        print(f"TX  {fr.id_text}  {kind}  [{fr.dlc}]  {fr.data_hex}")
                    sent += 1
                    if not settings.print_client_data:
                        await_replies = int(settings.reply_count)
                        await_got = 0
                        await_deadline = (
                            time.time() + settings.reply_timeout_ms / 1000.0
                        )
                except Exception as exc:
                    print(f"发送失败: {exc}", file=sys.stderr)
                    return 1
                if args.period_ms <= 0:
                    next_tx = float("inf")
                    if args.count == 0 and settings.print_client_data:
                        args.listen = True
                else:
                    next_tx = time.time() + args.period_ms / 1000.0
                if args.count and (sent + recv) >= args.count:
                    break
            if (
                not args.listen
                and args.period_ms <= 0
                and next_tx == float("inf")
                and await_replies == 0
                and not settings.print_client_data
                and sent > 0
            ):
                break
            time.sleep(0.02)
    finally:
        if resp_svc is not None:
            try:
                resp_svc.stop_background()
            except Exception:
                pass
        session.disconnect()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
