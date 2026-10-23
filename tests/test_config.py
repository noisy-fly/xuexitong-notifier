from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from xuexitong_notifier.config import load_config, load_env_file, write_env_file


class EnvironmentFileTests(unittest.TestCase):
    def test_round_trip_preserves_special_characters(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            values = {
                "CHAOXING_PHONE": "13800138000",
                "CHAOXING_PASSWORD": "p&a ss#word[]",
            }

            write_env_file(path, values)
            with patch.dict(os.environ, {}, clear=True):
                load_env_file(path)
                self.assertEqual(os.environ["CHAOXING_PASSWORD"], "p&a ss#word[]")

    def test_existing_process_environment_wins(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            write_env_file(path, {"CHAOXING_PHONE": "from-file"})
            with patch.dict(os.environ, {"CHAOXING_PHONE": "from-process"}, clear=True):
                load_env_file(path)
                self.assertEqual(os.environ["CHAOXING_PHONE"], "from-process")


class AppConfigTests(unittest.TestCase):
    def test_loads_enabled_channels_and_resolves_relative_data_dir(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config_path = root / "config.json"
            config_path.write_text(json.dumps({
                "poll_interval": 75,
                "data_dir": "state",
                "ntfy": {
                    "enabled": True,
                    "server_url": "https://ntfy.sh",
                    "topic_env": "NTFY_TOPIC",
                    "token_env": "NTFY_TOKEN",
                },
                "qq": {
                    "enabled": True,
                    "base_url": "http://127.0.0.1:4300",
                    "target_name": "腾飞辉煌",
                    "token_env": "ONEBOT_TOKEN",
                },
            }), encoding="utf-8")
            env = {
                "CHAOXING_PHONE": "13800138000",
                "CHAOXING_PASSWORD": "secret",
                "NTFY_TOPIC": "random-topic",
                "ONEBOT_TOKEN": "bot-token",
            }

            config = load_config(config_path, env)

            self.assertEqual(config.poll_interval, 75)
            self.assertEqual(config.data_dir, root / "state")
            self.assertEqual({channel.name for channel in config.channels}, {"ntfy", "qq"})

    def test_rejects_no_notification_channel(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.json"
            path.write_text('{"ntfy":{"enabled":false},"qq":{"enabled":false}}')
            with self.assertRaisesRegex(ValueError, "至少启用"):
                load_config(path, {
                    "CHAOXING_PHONE": "u",
                    "CHAOXING_PASSWORD": "p",
                })


if __name__ == "__main__":
    unittest.main()
