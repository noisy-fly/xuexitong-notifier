from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from .channels import NtfyChannel, OneBotChannel

ENV_LINE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)=(.*)$")


@dataclass(frozen=True, slots=True)
class AppConfig:
    phone: str
    password: str
    poll_interval: int
    data_dir: Path
    channels: tuple[NtfyChannel | OneBotChannel, ...]
    notify_existing: bool = False


def write_env_file(path: str | Path, values: Mapping[str, str]) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        f"{key}={json.dumps(str(value), ensure_ascii=False)}"
        for key, value in values.items()
        if value != ""
    ]
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    try:
        target.chmod(0o600)
    except OSError:
        pass


def load_env_file(path: str | Path) -> None:
    target = Path(path)
    if not target.exists():
        return
    for line in target.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = ENV_LINE.match(stripped)
        if not match:
            raise ValueError(f"无效环境变量行: {line[:40]}")
        key, raw = match.groups()
        try:
            value = json.loads(raw) if raw.startswith('"') else raw
        except json.JSONDecodeError as exc:
            raise ValueError(f"环境变量 {key} 的引号格式无效") from exc
        os.environ.setdefault(key, str(value))


def _required_env(env: Mapping[str, str], name: str) -> str:
    value = str(env.get(name) or "").strip()
    if not value:
        raise ValueError(f"缺少环境变量 {name}")
    return value


def _section(data: dict[str, Any], name: str) -> dict[str, Any]:
    value = data.get(name) or {}
    if not isinstance(value, dict):
        raise ValueError(f"配置项 {name} 必须是对象")
    return value


def load_config(path: str | Path, env: Mapping[str, str] | None = None) -> AppConfig:
    config_path = Path(path).resolve()
    if not config_path.exists():
        raise ValueError(f"配置文件不存在: {config_path}")
    try:
        data = json.loads(config_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"配置文件 JSON 无效: {exc}") from exc
    environment = os.environ if env is None else env
    phone = _required_env(environment, "CHAOXING_PHONE")
    password = _required_env(environment, "CHAOXING_PASSWORD")
    interval = max(30, int(data.get("poll_interval", 60)))
    raw_data_dir = Path(str(data.get("data_dir") or "data"))
    data_dir = raw_data_dir if raw_data_dir.is_absolute() else config_path.parent / raw_data_dir

    channels: list[NtfyChannel | OneBotChannel] = []
    ntfy = _section(data, "ntfy")
    if ntfy.get("enabled"):
        topic_env = str(ntfy.get("topic_env") or "NTFY_TOPIC")
        token_env = str(ntfy.get("token_env") or "NTFY_TOKEN")
        channels.append(
            NtfyChannel(
                server_url=str(ntfy.get("server_url") or "https://ntfy.sh"),
                topic=_required_env(environment, topic_env),
                token=str(environment.get(token_env) or ""),
            )
        )

    qq = _section(data, "qq")
    if qq.get("enabled"):
        token_env = str(qq.get("token_env") or "ONEBOT_TOKEN")
        target_user_id = qq.get("target_user_id")
        channels.append(
            OneBotChannel(
                base_url=str(qq.get("base_url") or "http://127.0.0.1:4300"),
                target_name=str(qq.get("target_name") or "腾飞辉煌"),
                target_user_id=int(target_user_id) if target_user_id not in (None, "") else None,
                token=str(environment.get(token_env) or ""),
            )
        )

    if not channels:
        raise ValueError("至少启用一个通知通道（ntfy 或 qq）")
    return AppConfig(
        phone=phone,
        password=password,
        poll_interval=interval,
        data_dir=data_dir,
        channels=tuple(channels),
        notify_existing=bool(data.get("notify_existing", False)),
    )

