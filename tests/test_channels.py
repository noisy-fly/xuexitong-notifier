from __future__ import annotations

import unittest

from xuexitong_notifier.channels import NtfyChannel, OneBotChannel, resolve_friend_id
from xuexitong_notifier.models import Message


class FakeResponse:
    def __init__(self, payload: dict | None = None):
        self.payload = payload or {"status": "ok", "retcode": 0}

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return self.payload


class FakeSession:
    def __init__(self, responses: list[FakeResponse] | None = None):
        self.responses = list(responses or [])
        self.calls: list[tuple[str, str, dict]] = []

    def post(self, url: str, **kwargs) -> FakeResponse:
        self.calls.append(("POST", url, kwargs))
        return self.responses.pop(0) if self.responses else FakeResponse()


def sample_message() -> Message:
    return Message(
        source="notice",
        remote_id="n-1",
        title="实验通知",
        body="周五提交报告",
        sender="李老师",
        occurred_at=1_800_000_000_000,
    )


class NtfyChannelTests(unittest.TestCase):
    def test_send_uses_json_api_and_bearer_token(self) -> None:
        session = FakeSession()
        channel = NtfyChannel(
            server_url="https://notify.example.test",
            topic="private-topic",
            token="secret-token",
            session=session,
        )

        channel.send(sample_message())

        _, url, kwargs = session.calls[0]
        self.assertEqual(url, "https://notify.example.test")
        self.assertEqual(kwargs["headers"]["Authorization"], "Bearer secret-token")
        self.assertEqual(kwargs["json"]["topic"], "private-topic")
        self.assertIn("周五提交报告", kwargs["json"]["message"])


class OneBotChannelTests(unittest.TestCase):
    def test_friend_resolves_by_remark_or_nickname(self) -> None:
        friends = [
            {"user_id": 10001, "nickname": "张三", "remark": ""},
            {"user_id": 10002, "nickname": "王五", "remark": "腾飞辉煌"},
        ]
        self.assertEqual(resolve_friend_id(friends, "腾飞辉煌"), 10002)

    def test_ambiguous_friend_is_rejected(self) -> None:
        friends = [
            {"user_id": 1, "nickname": "腾飞辉煌", "remark": ""},
            {"user_id": 2, "nickname": "腾飞辉煌", "remark": ""},
        ]
        with self.assertRaisesRegex(ValueError, "多个"):
            resolve_friend_id(friends, "腾飞辉煌")

    def test_send_resolves_friend_once_and_posts_private_message(self) -> None:
        session = FakeSession([
            FakeResponse({
                "status": "ok",
                "retcode": 0,
                "data": [{"user_id": 10002, "nickname": "腾飞辉煌", "remark": ""}],
            }),
            FakeResponse(),
        ])
        channel = OneBotChannel(
            base_url="http://127.0.0.1:4300",
            target_name="腾飞辉煌",
            token="onebot-token",
            session=session,
        )

        channel.send(sample_message())

        self.assertEqual(session.calls[0][1], "http://127.0.0.1:4300/get_friend_list")
        self.assertEqual(session.calls[1][1], "http://127.0.0.1:4300/send_private_msg")
        self.assertEqual(session.calls[1][2]["json"]["user_id"], 10002)
        self.assertEqual(
            session.calls[1][2]["headers"]["Authorization"],
            "Bearer onebot-token",
        )


if __name__ == "__main__":
    unittest.main()

