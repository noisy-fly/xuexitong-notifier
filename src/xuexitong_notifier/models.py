from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Message:
    source: str
    title: str
    body: str
    sender: str
    occurred_at: int
    remote_id: str | None = None

    @property
    def source_id(self) -> str:
        if self.remote_id:
            return str(self.remote_id)
        canonical = json.dumps(
            {
                "body": self.body.strip(),
                "occurred_at": self.occurred_at,
                "sender": self.sender.strip(),
                "source": self.source,
                "title": self.title.strip(),
            },
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
        )
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


@dataclass(frozen=True, slots=True)
class Delivery:
    id: int
    message: Message
    channel: str
    status: str
    attempts: int

