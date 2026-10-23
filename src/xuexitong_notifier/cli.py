from __future__ import annotations

import argparse
import getpass
import json
import logging
import os
import secrets
import signal
import sys
import threading
from pathlib import Path

from .channels import NtfyChannel, OneBotChannel
from .chaoxing import ChaoxingClient
from .config import AppConfig, load_config, load_env_file, write_env_file
from .service import NotifierService
from .store import MessageStore


def _yes(prompt: str, default: bool = False) -> bool:
    suffix = "[Y/n]" if default else "[y/N]"
    value = input(f"{prompt} {suffix}: ").strip().lower()
    if not value:
        return default
    return value in {"y", "yes", "是"}


def _value(prompt: str, default: str = "") -> str:
    suffix = f" [{default}]" if default else ""
    return input(f"{prompt}{suffix}: ").strip() or default


def init_files(config_path: Path, env_path: Path) -> None:
    if config_path.exists() or env_path.exists():
        raise ValueError("config.json 或 .env 已存在；为避免覆盖，请先移动现有文件")
    phone = _value("学习通账号/手机号")
    password = getpass.getpass("学习通密码: ")
    if not phone or not password:
        raise ValueError("学习通账号和密码不能为空")

    ntfy_enabled = _yes("启用手机 ntfy 通知弹窗", default=True)
    qq_enabled = _yes("启用 QQ 通知", default=False)
    if not ntfy_enabled and not qq_enabled:
        raise ValueError("至少启用一个通知通道")

    env_values = {
        "CHAOXING_PHONE": phone,
        "CHAOXING_PASSWORD": password,
    }
    config: dict[str, object] = {
        "poll_interval": 60,
        "data_dir": "data",
        "notify_existing": False,
        "ntfy": {"enabled": False},
        "qq": {"enabled": False},
    }
    if ntfy_enabled:
        server_url = _value("ntfy 服务地址", "https://ntfy.sh")
        topic = _value("ntfy 主题（留空自动生成）") or (
            "xuexitong-" + secrets.token_urlsafe(18)
        )
        token = getpass.getpass("ntfy 访问令牌（公共主题可留空）: ")
        env_values["NTFY_TOPIC"] = topic
        if token:
            env_values["NTFY_TOKEN"] = token
        config["ntfy"] = {
            "enabled": True,
            "server_url": server_url,
            "topic_env": "NTFY_TOPIC",
            "token_env": "NTFY_TOKEN",
        }
    if qq_enabled:
        base_url = _value("OneBot HTTP 地址", "http://127.0.0.1:4300")
        target_name = _value("QQ 好友昵称或备注", "腾飞辉煌")
        target_id = _value("QQ 号（可留空，由昵称唯一解析）")
        token = getpass.getpass("OneBot 访问令牌（建议设置）: ")
        if token:
            env_values["ONEBOT_TOKEN"] = token
        config["qq"] = {
            "enabled": True,
            "base_url": base_url,
            "target_name": target_name,
            "target_user_id": int(target_id) if target_id else None,
            "token_env": "ONEBOT_TOKEN",
        }

    config_path.parent.mkdir(parents=True, exist_ok=True)
    config_path.write_text(
        json.dumps(config, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    write_env_file(env_path, env_values)
    print(f"已创建 {config_path}")
    print(f"已创建 {env_path}（敏感文件，不要提交 Git）")
    if ntfy_enabled:
        print(f"手机 ntfy 客户端请订阅主题: {env_values['NTFY_TOPIC']}")


def _load(config_path: Path, env_path: Path) -> AppConfig:
    load_env_file(env_path)
    return load_config(config_path)


def _build_service(config: AppConfig) -> tuple[NotifierService, MessageStore]:
    config.data_dir.mkdir(parents=True, exist_ok=True)
    store = MessageStore(config.data_dir / "messages.db")
    collector = ChaoxingClient(config.phone, config.password)
    return (
        NotifierService(
            collector,
            store,
            config.channels,
            notify_existing=config.notify_existing,
        ),
        store,
    )


def check_config(config: AppConfig, live: bool = False) -> None:
    config.data_dir.mkdir(parents=True, exist_ok=True)
    store = MessageStore(config.data_dir / "messages.db")
    store.close()
    print(f"配置有效；轮询间隔 {config.poll_interval}s；数据目录 {config.data_dir}")
    print("已启用通道: " + ", ".join(channel.name for channel in config.channels))
    if not live:
        return

    collector = ChaoxingClient(config.phone, config.password)
    collector.login()
    notices = collector.fetch_notices(max_pages=1)
    print(f"学习通登录成功；通知中心首批 {len(notices)} 条")
    try:
        im_messages = collector.fetch_im_messages(max_pages_per_group=1)
        print(f"学习通 IM 读取成功；首批 {len(im_messages)} 条")
    except Exception as exc:
        print(f"学习通 IM 检查失败: {exc}", file=sys.stderr)
        raise
    for channel in config.channels:
        if isinstance(channel, OneBotChannel):
            user_id = channel._resolve_target()
            print(f"OneBot 连接成功；目标好友 QQ={user_id}")
        elif isinstance(channel, NtfyChannel):
            print(f"ntfy 配置有效；服务 {channel.server_url}（健康检查不发送消息）")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="学习通消息通知服务")
    parser.add_argument("--config", type=Path, default=Path("config.json"))
    parser.add_argument("--env-file", type=Path, default=Path(".env"))
    parser.add_argument("--log-level", default="INFO", choices=("DEBUG", "INFO", "WARNING", "ERROR"))
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("init", help="交互式创建配置")
    check = subparsers.add_parser("check", help="检查配置和运行环境")
    check.add_argument("--live", action="store_true", help="连接学习通和 OneBot 做只读检查")
    run = subparsers.add_parser("run", help="持续运行消息监听")
    run.add_argument("--once", action="store_true", help="只运行一轮")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, args.log_level),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    try:
        if args.command == "init":
            init_files(args.config, args.env_file)
            return 0
        config = _load(args.config, args.env_file)
        if args.command == "check":
            check_config(config, live=args.live)
            return 0
        service, store = _build_service(config)
        try:
            if args.once:
                result = service.poll_once()
                print(
                    f"完成：发现 {result.discovered}，发送 {result.sent}，"
                    f"投递失败 {result.failed}，来源错误 {result.errors}"
                )
                return 0 if result.errors == 0 and result.failed == 0 else 2
            stop = threading.Event()

            def request_stop(_signum, _frame) -> None:
                stop.set()

            signal.signal(signal.SIGINT, request_stop)
            signal.signal(signal.SIGTERM, request_stop)
            service.run_forever(config.poll_interval, stop)
            return 0
        finally:
            store.close()
    except (ValueError, RuntimeError, OSError) as exc:
        logging.getLogger(__name__).error("%s", exc)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

