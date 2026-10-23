from __future__ import annotations

from typing import Any, Iterable

import requests

from .models import Message


def format_message(message: Message, limit: int = 3500) -> str:
    parts = [message.title]
    if message.sender:
        parts.append(f"发送者：{message.sender}")
    if message.body:
        parts.append(message.body)
    text = "\n".join(parts)
    return text if len(text) <= limit else text[: limit - 1] + "…"


def resolve_friend_id(friends: Iterable[dict[str, Any]], target_name: str) -> int:
    target = target_name.strip()
    matches = [
        item
        for item in friends
        if target in {
            str(item.get("nickname") or "").strip(),
            str(item.get("remark") or "").strip(),
        }
    ]
    if not matches:
        raise ValueError(f"OneBot 好友列表中找不到“{target}”")
    if len(matches) > 1:
        raise ValueError(f"OneBot 好友列表中有多个“{target}”，请配置明确 QQ 号")
    try:
        return int(matches[0]["user_id"])
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"OneBot 好友“{target}”缺少有效 QQ 号") from exc


def _check_onebot(response: Any, action: str) -> dict[str, Any]:
    response.raise_for_status()
    payload = response.json()
    if payload.get("status") != "ok" or int(payload.get("retcode", -1)) != 0:
        raise RuntimeError(
            f"OneBot {action} 失败: {payload.get('message') or payload.get('wording') or payload}"
        )
    return payload


class NtfyChannel:
    name = "ntfy"

    def __init__(
        self,
        server_url: str,
        topic: str,
        token: str = "",
        *,
        session: Any | None = None,
        timeout: float = 15.0,
    ):
        self.server_url = server_url.rstrip("/")
        self.topic = topic
        self.token = token
        self.session = session or requests.Session()
        self.timeout = timeout

    def send(self, message: Message) -> None:
        headers = {}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        response = self.session.post(
            self.server_url,
            json={
                "topic": self.topic,
                "title": message.title[:200],
                "message": format_message(message, limit=4000),
                "tags": ["books"],
            },
            headers=headers,
            timeout=self.timeout,
        )
        response.raise_for_status()


class OneBotChannel:
    name = "qq"

    def __init__(
        self,
        base_url: str,
        target_name: str = "腾飞辉煌",
        token: str = "",
        target_user_id: int | None = None,
        *,
        session: Any | None = None,
        timeout: float = 15.0,
    ):
        self.base_url = base_url.rstrip("/")
        self.target_name = target_name
        self.token = token
        self.target_user_id = target_user_id
        self.session = session or requests.Session()
        self.timeout = timeout

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def _resolve_target(self) -> int:
        if self.target_user_id is not None:
            return self.target_user_id
        response = self.session.post(
            f"{self.base_url}/get_friend_list",
            json={},
            headers=self._headers(),
            timeout=self.timeout,
        )
        payload = _check_onebot(response, "get_friend_list")
        self.target_user_id = resolve_friend_id(payload.get("data") or [], self.target_name)
        return self.target_user_id

    def send(self, message: Message) -> None:
        response = self.session.post(
            f"{self.base_url}/send_private_msg",
            json={
                "user_id": self._resolve_target(),
                "message": format_message(message),
            },
            headers=self._headers(),
            timeout=self.timeout,
        )
        _check_onebot(response, "send_private_msg")

