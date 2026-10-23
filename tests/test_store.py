from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from xuexitong_notifier.models import Message
from xuexitong_notifier.store import MessageStore


def message(remote_id: str | None, body: str = "请查看新通知") -> Message:
    return Message(
        source="notice",
        remote_id=remote_id,
        title="课程通知",
        body=body,
        sender="张老师",
        occurred_at=1_800_000_000_000,
    )


class MessageIdentityTests(unittest.TestCase):
    def test_remote_id_is_preferred(self) -> None:
        self.assertEqual(message("server-42").source_id, "server-42")

    def test_fallback_id_is_stable_and_content_sensitive(self) -> None:
        first = message(None)
        same = message(None)
        changed = message(None, body="另一条通知")

        self.assertEqual(first.source_id, same.source_id)
        self.assertNotEqual(first.source_id, changed.source_id)
        self.assertEqual(len(first.source_id), 64)


class MessageStoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.store = MessageStore(Path(self.temp_dir.name) / "messages.db")

    def tearDown(self) -> None:
        self.store.close()
        self.temp_dir.cleanup()

    def test_first_sync_builds_baseline_without_delivery(self) -> None:
        inserted = self.store.ingest([message("1")], ["ntfy", "qq"])

        self.assertEqual(inserted, 1)
        self.assertEqual(self.store.due_deliveries(), [])
        self.assertEqual(self.store.delivery_status("notice", "1", "ntfy"), "skipped")
        self.assertEqual(self.store.delivery_status("notice", "1", "qq"), "skipped")

    def test_later_new_message_is_pending_for_each_channel(self) -> None:
        self.store.ingest([message("1")], ["ntfy", "qq"])
        inserted = self.store.ingest([message("1"), message("2")], ["ntfy", "qq"])

        due = self.store.due_deliveries()
        self.assertEqual(inserted, 1)
        self.assertEqual({(item.message.source_id, item.channel) for item in due}, {
            ("2", "ntfy"),
            ("2", "qq"),
        })

    def test_repeated_unread_message_is_never_inserted_twice(self) -> None:
        self.store.ingest([message("1")], ["ntfy"])
        self.store.ingest([message("2")], ["ntfy"])
        inserted = self.store.ingest([message("2")], ["ntfy"])

        self.assertEqual(inserted, 0)
        self.assertEqual(len(self.store.due_deliveries()), 1)

    def test_new_channel_gets_its_own_baseline(self) -> None:
        self.store.ingest([message("1")], ["ntfy"])
        self.store.ingest([message("1"), message("2")], ["ntfy", "qq"])

        self.assertEqual(self.store.delivery_status("notice", "2", "ntfy"), "pending")
        self.assertEqual(self.store.delivery_status("notice", "2", "qq"), "skipped")

    def test_empty_first_sync_still_establishes_baseline(self) -> None:
        self.store.ingest([], ["ntfy"], known_sources=["notice"])
        self.store.ingest([message("1")], ["ntfy"], known_sources=["notice"])

        self.assertEqual(self.store.delivery_status("notice", "1", "ntfy"), "pending")

    def test_failed_delivery_can_be_retried_then_marked_sent(self) -> None:
        self.store.ingest([message("1")], ["ntfy"], notify_existing=True)
        delivery = self.store.due_deliveries()[0]

        self.store.mark_failed(delivery.id, "temporary failure", retry_at=0)
        retried = self.store.due_deliveries()[0]
        self.assertEqual(retried.attempts, 1)
        self.store.mark_sent(retried.id)

        self.assertEqual(self.store.due_deliveries(), [])
        self.assertEqual(self.store.delivery_status("notice", "1", "ntfy"), "sent")


if __name__ == "__main__":
    unittest.main()
