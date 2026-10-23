from __future__ import annotations

import sqlite3
import time
from collections import defaultdict
from pathlib import Path
from typing import Iterable, Sequence

from .models import Delivery, Message


class MessageStore:
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.path)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA foreign_keys = ON")
        self._create_schema()

    def _create_schema(self) -> None:
        self.connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY,
                source TEXT NOT NULL,
                source_id TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                sender TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                discovered_at INTEGER NOT NULL,
                UNIQUE(source, source_id)
            );

            CREATE TABLE IF NOT EXISTS deliveries (
                id INTEGER PRIMARY KEY,
                message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                channel TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('pending', 'sent', 'failed', 'skipped')),
                attempts INTEGER NOT NULL DEFAULT 0,
                next_attempt_at INTEGER NOT NULL DEFAULT 0,
                last_error TEXT NOT NULL DEFAULT '',
                sent_at INTEGER,
                UNIQUE(message_id, channel)
            );

            CREATE TABLE IF NOT EXISTS channel_baselines (
                source TEXT NOT NULL,
                channel TEXT NOT NULL,
                completed_at INTEGER NOT NULL,
                PRIMARY KEY(source, channel)
            );
            """
        )
        self.connection.commit()

    def close(self) -> None:
        self.connection.close()

    def ingest(
        self,
        messages: Iterable[Message],
        channels: Sequence[str],
        *,
        notify_existing: bool = False,
        known_sources: Sequence[str] = (),
    ) -> int:
        grouped: dict[str, list[Message]] = defaultdict(list)
        for item in messages:
            grouped[item.source].append(item)
        for source in known_sources:
            grouped.setdefault(source, [])

        inserted_count = 0
        now = int(time.time())
        with self.connection:
            for source, source_messages in grouped.items():
                baseline = {
                    channel: self._has_baseline(source, channel)
                    for channel in channels
                }
                for item in source_messages:
                    cursor = self.connection.execute(
                        """
                        INSERT OR IGNORE INTO messages
                            (source, source_id, title, body, sender, occurred_at, discovered_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            item.source,
                            item.source_id,
                            item.title,
                            item.body,
                            item.sender,
                            item.occurred_at,
                            now,
                        ),
                    )
                    is_new = cursor.rowcount == 1
                    inserted_count += int(is_new)
                    message_id = self.connection.execute(
                        "SELECT id FROM messages WHERE source = ? AND source_id = ?",
                        (item.source, item.source_id),
                    ).fetchone()["id"]
                    for channel in channels:
                        if baseline[channel]:
                            if not is_new:
                                continue
                            status = "pending"
                        else:
                            status = "pending" if notify_existing else "skipped"
                        self.connection.execute(
                            """
                            INSERT OR IGNORE INTO deliveries (message_id, channel, status)
                            VALUES (?, ?, ?)
                            """,
                            (message_id, channel, status),
                        )
                for channel, already_complete in baseline.items():
                    if not already_complete:
                        self.connection.execute(
                            """
                            INSERT OR IGNORE INTO channel_baselines (source, channel, completed_at)
                            VALUES (?, ?, ?)
                            """,
                            (source, channel, now),
                        )
        return inserted_count

    def _has_baseline(self, source: str, channel: str) -> bool:
        return self.connection.execute(
            "SELECT 1 FROM channel_baselines WHERE source = ? AND channel = ?",
            (source, channel),
        ).fetchone() is not None

    def due_deliveries(self, now: int | None = None, limit: int = 100) -> list[Delivery]:
        current = int(time.time()) if now is None else now
        rows = self.connection.execute(
            """
            SELECT d.id, d.channel, d.status, d.attempts,
                   m.source, m.source_id, m.title, m.body, m.sender, m.occurred_at
            FROM deliveries AS d
            JOIN messages AS m ON m.id = d.message_id
            WHERE d.status IN ('pending', 'failed') AND d.next_attempt_at <= ?
            ORDER BY m.occurred_at, d.id
            LIMIT ?
            """,
            (current, limit),
        ).fetchall()
        return [
            Delivery(
                id=row["id"],
                message=Message(
                    source=row["source"],
                    remote_id=row["source_id"],
                    title=row["title"],
                    body=row["body"],
                    sender=row["sender"],
                    occurred_at=row["occurred_at"],
                ),
                channel=row["channel"],
                status=row["status"],
                attempts=row["attempts"],
            )
            for row in rows
        ]

    def mark_sent(self, delivery_id: int) -> None:
        with self.connection:
            self.connection.execute(
                """
                UPDATE deliveries
                SET status = 'sent', sent_at = ?, last_error = ''
                WHERE id = ?
                """,
                (int(time.time()), delivery_id),
            )

    def mark_failed(self, delivery_id: int, error: str, retry_at: int) -> None:
        with self.connection:
            self.connection.execute(
                """
                UPDATE deliveries
                SET status = 'failed', attempts = attempts + 1,
                    next_attempt_at = ?, last_error = ?
                WHERE id = ?
                """,
                (retry_at, error[:500], delivery_id),
            )

    def delivery_status(self, source: str, source_id: str, channel: str) -> str | None:
        row = self.connection.execute(
            """
            SELECT d.status
            FROM deliveries AS d
            JOIN messages AS m ON m.id = d.message_id
            WHERE m.source = ? AND m.source_id = ? AND d.channel = ?
            """,
            (source, source_id, channel),
        ).fetchone()
        return row["status"] if row else None

