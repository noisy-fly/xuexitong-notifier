from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from xuexitong_notifier.models import Message
from xuexitong_notifier.service import NotifierService
from xuexitong_notifier.store import MessageStore


def item(remote_id: str, source: str = "notice") -> Message:
    return Message(
        source=source,
        remote_id=remote_id,
        title="新消息",
        body=f"正文 {remote_id}",
        sender="老师",
        occurred_at=1_800_000_000_000,
    )


class SequenceCollector:
    def __init__(self, notices: list[list[Message]], im: list[list[Message]]):
        self.notices = notices
        self.im = im

    def fetch_notices(self) -> list[Message]:
        return self.notices.pop(0)

    def fetch_im_messages(self) -> list[Message]:
        return self.im.pop(0)


class RecordingChannel:
    def __init__(self, name: str):
        self.name = name
        self.messages: list[Message] = []

    def send(self, message: Message) -> None:
        self.messages.append(message)


class FailingCollector(SequenceCollector):
    invalidations = 0

    def fetch_im_messages(self) -> list[Message]:
        raise RuntimeError("IM unavailable")

    def invalidate_session(self) -> None:
        self.invalidations += 1


class NotifierServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.store = MessageStore(Path(self.temp_dir.name) / "messages.db")

    def tearDown(self) -> None:
        self.store.close()
        self.temp_dir.cleanup()

    def test_first_poll_baselines_and_second_poll_sends_to_both_channels(self) -> None:
        collector = SequenceCollector(
            notices=[[item("n1")], [item("n1"), item("n2")]],
            im=[[item("i1", "im")], [item("i1", "im"), item("i2", "im")]],
        )
        ntfy = RecordingChannel("ntfy")
        qq = RecordingChannel("qq")
        service = NotifierService(collector, self.store, [ntfy, qq])

        first = service.poll_once()
        second = service.poll_once()

        self.assertEqual(first.sent, 0)
        self.assertEqual(second.discovered, 2)
        self.assertEqual(second.sent, 4)
        self.assertEqual({m.source_id for m in ntfy.messages}, {"n2", "i2"})
        self.assertEqual({m.source_id for m in qq.messages}, {"n2", "i2"})

    def test_one_source_failure_does_not_block_other_source(self) -> None:
        collector = FailingCollector(notices=[[], [item("n1")]], im=[])
        ntfy = RecordingChannel("ntfy")
        service = NotifierService(collector, self.store, [ntfy])

        first = service.poll_once()
        second = service.poll_once()

        self.assertEqual(first.errors, 1)
        self.assertEqual(second.sent, 1)
        self.assertEqual(ntfy.messages[0].source_id, "n1")
        self.assertEqual(collector.invalidations, 2)


if __name__ == "__main__":
    unittest.main()
