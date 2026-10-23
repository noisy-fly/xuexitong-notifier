from __future__ import annotations

import base64
import json
import unittest

from xuexitong_notifier.chaoxing import (
    aes_encrypt,
    decrypt_im_password,
    parse_im_groups,
    parse_notice_page,
    parse_roaming_page,
    parse_user_info,
)


def varint(value: int) -> bytes:
    output = bytearray()
    while value > 0x7F:
        output.append((value & 0x7F) | 0x80)
        value >>= 7
    output.append(value)
    return bytes(output)


def int_field(number: int, value: int) -> bytes:
    return varint(number << 3) + varint(value)


def bytes_field(number: int, value: bytes) -> bytes:
    return varint((number << 3) | 2) + varint(len(value)) + value


def text_field(number: int, value: str) -> bytes:
    return bytes_field(number, value.encode("utf-8"))


def roaming_fixture() -> dict:
    sender = text_field(1, "teacher-7")
    content = int_field(1, 0) + text_field(2, "明早第一节课调到 9 点")
    attachment = json.dumps(
        {"title": "调课附件", "content": "请相互转告"},
        ensure_ascii=False,
    )
    extension = text_field(1, "attachment") + text_field(6, attachment)
    body = (
        bytes_field(2, sender)
        + bytes_field(4, content)
        + bytes_field(5, extension)
    )
    meta = int_field(1, 987654321) + int_field(4, 1_800_000_000_000) + bytes_field(6, body)
    return {
        "data": {
            "msgs": [{"msg": base64.b64encode(meta).decode("ascii")}],
            "is_last": True,
            "next_key": "",
        }
    }


class LoginParsingTests(unittest.TestCase):
    def test_aes_login_vector(self) -> None:
        self.assertEqual(aes_encrypt("13800138000"), "yYcVatS+4J+JGnm88UM56A==")

    def test_im_password_des_vector(self) -> None:
        self.assertEqual(
            decrypt_im_password("215aff8ff522a70583ac3cd53827a633"),
            "secret42",
        )

    def test_user_info_extracts_im_credentials(self) -> None:
        payload = {
            "msg": {
                "name": "测试用户",
                "puid": 123,
                "uid": 456,
                "accountInfo": {"imAccount": {"password": "encrypted"}},
            }
        }

        info = parse_user_info(payload)

        self.assertEqual(info.uid, "456")
        self.assertEqual(info.puid, "123")
        self.assertEqual(info.name, "测试用户")
        self.assertEqual(info.encrypted_im_password, "encrypted")


class NoticeParsingTests(unittest.TestCase):
    def test_notice_page_maps_fields_and_plain_text(self) -> None:
        payload = {
            "status": True,
            "notices": {
                "list": [{
                    "id": 81,
                    "title": "实验通知",
                    "content": "<p>周五提交&nbsp;<b>报告</b></p>",
                    "createrName": "李老师",
                    "completeTime": 1_800_000_000_000,
                    "isread": 0,
                }],
                "lastGetId": "cursor-2",
                "lastPage": False,
            },
        }

        page = parse_notice_page(payload)

        self.assertEqual(page.next_cursor, "cursor-2")
        self.assertFalse(page.is_last)
        self.assertEqual(page.messages[0].source_id, "81")
        self.assertEqual(page.messages[0].body, "周五提交 报告")

    def test_failed_notice_response_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "通知接口"):
            parse_notice_page({"status": False, "msg": "login required"})


class IMParsingTests(unittest.TestCase):
    def test_group_name_falls_back_to_course_description(self) -> None:
        groups = parse_im_groups({"data": [{
            "id": "group-1",
            "name": "",
            "description": json.dumps({
                "courseInfo": {"coursename": "软件工程"},
            }, ensure_ascii=False),
        }]})

        self.assertEqual(groups[0].id, "group-1")
        self.assertEqual(groups[0].name, "软件工程")

    def test_roaming_protobuf_maps_text_message(self) -> None:
        page = parse_roaming_page(roaming_fixture(), "软件工程")

        self.assertTrue(page.is_last)
        self.assertEqual(page.messages[0].source_id, "987654321")
        self.assertEqual(page.messages[0].title, "软件工程")
        self.assertEqual(page.messages[0].sender, "teacher-7")
        self.assertIn("明早第一节课调到 9 点", page.messages[0].body)
        self.assertIn("调课附件", page.messages[0].body)
        self.assertIn("请相互转告", page.messages[0].body)


if __name__ == "__main__":
    unittest.main()
