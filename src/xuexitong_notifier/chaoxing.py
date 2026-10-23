from __future__ import annotations

import base64
import html
import json
import re
from dataclasses import dataclass
from typing import Any, Iterable, Iterator

import requests
from Crypto.Cipher import AES, DES
from Crypto.Util.Padding import pad, unpad

from .models import Message

AES_KEY = b"u2oh6Vu^HWe4_AES"
IM_DES_KEY = b"SL2(M/eD"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
)
EASEMOB_USER_AGENT = "Easemob-SDK(Android) 4.9.0.1"

LOGIN_URL = "https://passport2.chaoxing.com/fanyalogin"
USER_INFO_URL = "https://sso.chaoxing.com/apis/login/userLogin4Uname.do"
NOTICES_URL = "https://notice.chaoxing.com/pc/notice/getNoticeList"
IM_TOKEN_URL = "https://a1-vip6.easemob.com/cx-dev/cxstudy/token"
IM_GROUPS_URL = (
    "https://a1-vip6.easemob.com/cx-dev/cxstudy/users/"
    "{username}/joined_chatgroups?detail=true&version=v3&pagenum=1&pagesize=200"
)
IM_ROAMING_URL = (
    "https://a1-vip6.easecdn.com/cx-dev/cxstudy/users/{username}/messageroaming"
)


@dataclass(frozen=True, slots=True)
class UserInfo:
    uid: str
    puid: str
    name: str
    encrypted_im_password: str


@dataclass(frozen=True, slots=True)
class IMAuth:
    token: str
    username: str


@dataclass(frozen=True, slots=True)
class IMGroup:
    id: str
    name: str


@dataclass(frozen=True, slots=True)
class MessagePage:
    messages: list[Message]
    next_cursor: str
    is_last: bool


def aes_encrypt(value: str) -> str:
    cipher = AES.new(AES_KEY, AES.MODE_CBC, AES_KEY)
    encrypted = cipher.encrypt(pad(value.encode("utf-8"), AES.block_size))
    return base64.b64encode(encrypted).decode("ascii")


def decrypt_im_password(encrypted_hex: str) -> str:
    try:
        encrypted = bytes.fromhex(encrypted_hex)
        cipher = DES.new(IM_DES_KEY, DES.MODE_ECB)
        return unpad(cipher.decrypt(encrypted), DES.block_size).decode("utf-8")
    except (ValueError, UnicodeDecodeError):
        if len(encrypted_hex) < 16:
            return encrypted_hex
        raise ValueError("学习通 IM 密码格式无效") from None


def plain_text(value: Any) -> str:
    text = str(value or "")
    text = re.sub(r"<script[\s\S]*?</script>|<style[\s\S]*?</style>", " ", text)
    text = re.sub(r"<[^>]+>", " ", text)
    return " ".join(html.unescape(text).replace("\xa0", " ").split())


def parse_user_info(payload: dict[str, Any]) -> UserInfo:
    try:
        data = payload["msg"]
        return UserInfo(
            uid=str(data["uid"]),
            puid=str(data["puid"]),
            name=str(data["name"]),
            encrypted_im_password=str(data["accountInfo"]["imAccount"]["password"]),
        )
    except (KeyError, TypeError) as exc:
        raise ValueError("账号信息缺少 IM 凭据") from exc


def _first_value(item: dict[str, Any], names: Iterable[str]) -> str | None:
    for name in names:
        value = item.get(name)
        if value not in (None, ""):
            return str(value)
    return None


def parse_notice_page(payload: dict[str, Any]) -> MessagePage:
    if payload.get("status") is not True:
        raise ValueError(f"通知接口返回失败: {payload.get('msg') or '未知错误'}")
    notices = payload.get("notices") or {}
    messages: list[Message] = []
    for item in notices.get("list") or []:
        remote_id = _first_value(item, ("id", "idCode", "uuid", "noticeId"))
        timestamp = item.get("completeTime") or item.get("insertTime") or 0
        try:
            timestamp = int(timestamp)
        except (TypeError, ValueError):
            timestamp = 0
        messages.append(
            Message(
                source="notice",
                remote_id=remote_id,
                title=plain_text(item.get("title")) or "学习通通知",
                body=plain_text(item.get("content")),
                sender=plain_text(item.get("createrName") or item.get("senderName")),
                occurred_at=timestamp,
            )
        )
    return MessagePage(
        messages=messages,
        next_cursor=str(notices.get("lastGetId") or ""),
        is_last=bool(notices.get("lastPage")) or not messages,
    )


def parse_im_groups(payload: dict[str, Any]) -> list[IMGroup]:
    groups: list[IMGroup] = []
    for item in payload.get("data") or []:
        group_id = str(item.get("id") or "").strip()
        if not group_id:
            continue
        name = str(item.get("name") or "").strip()
        if not name:
            try:
                description = json.loads(item.get("description") or "{}")
                name = str((description.get("courseInfo") or {}).get("coursename") or "").strip()
            except (TypeError, ValueError):
                pass
        groups.append(IMGroup(group_id, name or f"学习通群组 {group_id}"))
    return groups


def _read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while offset < len(data) and shift < 70:
        current = data[offset]
        offset += 1
        value |= (current & 0x7F) << shift
        if current < 0x80:
            return value, offset
        shift += 7
    raise ValueError("无效的 Protobuf varint")


def _protobuf_fields(data: bytes) -> Iterator[tuple[int, int, int | bytes]]:
    offset = 0
    while offset < len(data):
        tag, offset = _read_varint(data, offset)
        number, wire_type = tag >> 3, tag & 7
        if number == 0:
            raise ValueError("无效的 Protobuf 字段编号")
        if wire_type == 0:
            value, offset = _read_varint(data, offset)
        elif wire_type == 1:
            end = offset + 8
            if end > len(data):
                raise ValueError("截断的 Protobuf fixed64")
            value, offset = data[offset:end], end
        elif wire_type == 2:
            length, offset = _read_varint(data, offset)
            end = offset + length
            if end > len(data):
                raise ValueError("截断的 Protobuf bytes")
            value, offset = data[offset:end], end
        elif wire_type == 5:
            end = offset + 4
            if end > len(data):
                raise ValueError("截断的 Protobuf fixed32")
            value, offset = data[offset:end], end
        else:
            raise ValueError(f"不支持的 Protobuf wire type: {wire_type}")
        yield number, wire_type, value


def _decode_text(value: int | bytes) -> str:
    if not isinstance(value, bytes):
        return ""
    return value.decode("utf-8", errors="replace")


def _parse_jid(data: bytes) -> str:
    for number, _, value in _protobuf_fields(data):
        if number == 1:
            return _decode_text(value)
    return ""


def _parse_content(data: bytes) -> str:
    values: list[str] = []
    for number, _, value in _protobuf_fields(data):
        if number in (2, 5, 8, 16, 19):
            text = plain_text(_decode_text(value))
            if text and text not in values:
                values.append(text)
    return " | ".join(values)


def _attachment_text(raw: str) -> str:
    try:
        item = json.loads(raw)
    except (TypeError, ValueError):
        return plain_text(raw)
    values: list[str] = []

    def add(value: Any) -> None:
        text = plain_text(value)
        if text and text not in values:
            values.append(text)

    for key in ("title", "content", "description", "name"):
        add(item.get(key))
    chat = item.get("att_chat_course") or {}
    course = chat.get("courseInfo") or {}
    for value in (
        chat.get("title"),
        chat.get("subTitle"),
        chat.get("atypeName"),
        course.get("coursename"),
    ):
        add(value)
    return " | ".join(values)


def _parse_extension(data: bytes) -> str:
    key = ""
    value = ""
    for number, _, raw in _protobuf_fields(data):
        if number == 1:
            key = _decode_text(raw)
        elif number == 6:
            value = _decode_text(raw)
    return _attachment_text(value) if key == "attachment" else ""


def _parse_message_body(data: bytes) -> tuple[str, str]:
    sender = ""
    text_parts: list[str] = []
    for number, _, value in _protobuf_fields(data):
        if not isinstance(value, bytes):
            continue
        if number == 2:
            sender = _parse_jid(value)
        elif number == 4:
            text = _parse_content(value)
            if text:
                text_parts.append(text)
        elif number == 5:
            text = _parse_extension(value)
            if text:
                text_parts.append(text)
    return sender, "\n".join(dict.fromkeys(text_parts)) or "[非文本消息]"


def _parse_meta(data: bytes, group_name: str) -> Message:
    message_id = 0
    timestamp = 0
    body = b""
    for number, _, value in _protobuf_fields(data):
        if number == 1 and isinstance(value, int):
            message_id = value
        elif number == 4 and isinstance(value, int):
            timestamp = value
        elif number == 6 and isinstance(value, bytes):
            body = value
    sender, text = _parse_message_body(body)
    return Message(
        source="im",
        remote_id=str(message_id) if message_id else None,
        title=group_name,
        body=text,
        sender=sender,
        occurred_at=timestamp,
    )


def parse_roaming_page(payload: dict[str, Any], group_name: str) -> MessagePage:
    data = payload.get("data") or {}
    messages: list[Message] = []
    for item in data.get("msgs") or []:
        encoded = item.get("msg")
        if not encoded:
            continue
        try:
            messages.append(_parse_meta(base64.b64decode(encoded, validate=True), group_name))
        except (ValueError, TypeError) as exc:
            raise ValueError("IM 漫游消息 Protobuf 解析失败") from exc
    return MessagePage(
        messages=messages,
        next_cursor=str(data.get("next_key") or ""),
        is_last=bool(data.get("is_last")) or not messages,
    )


class ChaoxingClient:
    def __init__(self, phone: str, password: str, timeout: float = 20.0):
        self.phone = phone
        self.password = password
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": USER_AGENT})
        self._logged_in = False
        self._user_info: UserInfo | None = None

    def login(self) -> None:
        response = self.session.post(
            LOGIN_URL,
            data={
                "fid": "-1",
                "uname": aes_encrypt(self.phone),
                "password": aes_encrypt(self.password),
                "refer": "https%3A%2F%2Fi.chaoxing.com",
                "t": "true",
                "forbidotherlogin": "0",
                "validate": "",
                "doubleFactorLogin": "0",
                "independentId": "0",
                "independentNameId": "0",
            },
            timeout=self.timeout,
        )
        response.raise_for_status()
        payload = response.json()
        if payload.get("status") is not True:
            raise RuntimeError(f"学习通登录失败: {payload.get('msg2') or '未知错误'}")
        self._logged_in = True

    def ensure_login(self) -> None:
        if not self._logged_in:
            self.login()

    def invalidate_session(self) -> None:
        self._logged_in = False
        self._user_info = None

    def user_info(self) -> UserInfo:
        if self._user_info is None:
            self.ensure_login()
            response = self.session.get(USER_INFO_URL, timeout=self.timeout)
            response.raise_for_status()
            self._user_info = parse_user_info(response.json())
        return self._user_info

    def fetch_notices(self, max_pages: int = 5) -> list[Message]:
        self.ensure_login()
        cursor = ""
        messages: list[Message] = []
        for _ in range(max_pages):
            response = self.session.post(
                NOTICES_URL,
                data={
                    "type": 0,
                    "notice_type": 0,
                    "lastValue": cursor,
                    "sort": 0,
                    "folderUUID": "",
                    "kw": "",
                    "startTime": "",
                    "endTime": "",
                    "gKw": "",
                    "gName": "",
                    "year": "",
                    "tag": "",
                    "fidsCode": "",
                    "filterSenderPuids": "",
                    "filterTags": "",
                },
                timeout=self.timeout,
            )
            response.raise_for_status()
            page = parse_notice_page(response.json())
            messages.extend(page.messages)
            if page.is_last or not page.next_cursor or page.next_cursor == cursor:
                break
            cursor = page.next_cursor
        return messages

    def _im_auth(self) -> IMAuth:
        info = self.user_info()
        response = self.session.post(
            IM_TOKEN_URL,
            data=json.dumps({
                "grant_type": "password",
                "password": decrypt_im_password(info.encrypted_im_password),
                "username": info.uid,
            }),
            headers={
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": EASEMOB_USER_AGENT,
            },
            timeout=self.timeout,
        )
        response.raise_for_status()
        payload = response.json()
        try:
            return IMAuth(str(payload["access_token"]), str(payload["user"]["username"]))
        except (KeyError, TypeError) as exc:
            raise RuntimeError("学习通 IM token 响应缺少必要字段") from exc

    def _im_groups(self, auth: IMAuth) -> list[IMGroup]:
        response = self.session.get(
            IM_GROUPS_URL.format(username=auth.username),
            headers={
                "Authorization": f"Bearer {auth.token}",
                "User-Agent": EASEMOB_USER_AGENT,
            },
            timeout=self.timeout,
        )
        response.raise_for_status()
        return parse_im_groups(response.json())

    def _group_messages(
        self,
        auth: IMAuth,
        group: IMGroup,
        max_pages: int,
    ) -> list[Message]:
        cursor = "-1"
        messages: list[Message] = []
        for _ in range(max_pages):
            response = self.session.post(
                IM_ROAMING_URL.format(username=auth.username),
                data=json.dumps({
                    "queue": f"{group.id}@conference.easemob.com",
                    "start": cursor,
                    "end": "-1",
                }),
                headers={
                    "Authorization": f"Bearer {auth.token}",
                    "Content-Type": "text/plain;charset=UTF-8",
                    "User-Agent": EASEMOB_USER_AGENT,
                },
                timeout=self.timeout,
            )
            response.raise_for_status()
            page = parse_roaming_page(response.json(), group.name)
            messages.extend(page.messages)
            if page.is_last or not page.next_cursor or page.next_cursor == cursor:
                break
            cursor = page.next_cursor
        return messages

    def fetch_im_messages(self, max_pages_per_group: int = 3) -> list[Message]:
        self.ensure_login()
        auth = self._im_auth()
        messages: list[Message] = []
        errors: list[str] = []
        for group in self._im_groups(auth):
            try:
                messages.extend(self._group_messages(auth, group, max_pages_per_group))
            except (requests.RequestException, ValueError) as exc:
                errors.append(f"{group.name}: {exc}")
        if errors and not messages:
            raise RuntimeError("所有 IM 群组拉取失败: " + "; ".join(errors[:3]))
        return messages

