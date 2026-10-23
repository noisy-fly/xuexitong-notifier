from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass
from typing import Protocol, Sequence

from .models import Message
from .store import MessageStore

log = logging.getLogger(__name__)


class Collector(Protocol):
    def fetch_notices(self) -> list[Message]: ...

    def fetch_im_messages(self) -> list[Message]: ...


class Channel(Protocol):
    name: str

    def send(self, message: Message) -> None: ...


@dataclass(frozen=True, slots=True)
class PollResult:
    discovered: int = 0
    sent: int = 0
    failed: int = 0
    errors: int = 0


class NotifierService:
    def __init__(
        self,
        collector: Collector,
        store: MessageStore,
        channels: Sequence[Channel],
        *,
        notify_existing: bool = False,
    ):
        self.collector = collector
        self.store = store
        self.channels = {channel.name: channel for channel in channels}
        self.notify_existing = notify_existing

    def poll_once(self) -> PollResult:
        discovered = 0
        errors = 0
        channel_names = list(self.channels)
        for source, fetch in (
            ("notice", self.collector.fetch_notices),
            ("im", self.collector.fetch_im_messages),
        ):
            try:
                messages = fetch()
                discovered += self.store.ingest(
                    messages,
                    channel_names,
                    notify_existing=self.notify_existing,
                    known_sources=[source],
                )
            except Exception as exc:
                errors += 1
                invalidate = getattr(self.collector, "invalidate_session", None)
                if callable(invalidate):
                    invalidate()
                log.warning("%s collection failed: %s", source, exc)

        sent = 0
        failed = 0
        for delivery in self.store.due_deliveries():
            channel = self.channels.get(delivery.channel)
            if channel is None:
                continue
            try:
                channel.send(delivery.message)
                self.store.mark_sent(delivery.id)
                sent += 1
            except Exception as exc:
                failed += 1
                delay = min(3600, 30 * (2 ** min(delivery.attempts, 7)))
                self.store.mark_failed(
                    delivery.id,
                    str(exc),
                    retry_at=int(time.time()) + delay,
                )
                log.warning(
                    "%s delivery failed for %s/%s: %s",
                    delivery.channel,
                    delivery.message.source,
                    delivery.message.source_id,
                    exc,
                )
        return PollResult(discovered, sent, failed, errors)

    def run_forever(
        self,
        interval_seconds: int,
        stop_event: threading.Event | None = None,
    ) -> None:
        interval = max(30, interval_seconds)
        stop = stop_event or threading.Event()
        while not stop.is_set():
            result = self.poll_once()
            log.info(
                "poll complete: discovered=%d sent=%d failed=%d source_errors=%d",
                result.discovered,
                result.sent,
                result.failed,
                result.errors,
            )
            stop.wait(interval)

