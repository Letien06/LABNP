"""Validation shared by the two mailbox repositories."""
import re
from uuid import UUID


def message_id(uid):
    if not isinstance(uid, str) or not re.fullmatch(r"lab3-[1-9][0-9]{0,18}", uid):
        raise ValueError("Mã thư không hợp lệ.")
    value = int(uid[5:])
    if value > 2**63 - 1:
        raise ValueError("Mã thư không hợp lệ.")
    return value


def draft_id(value):
    if isinstance(value, str):
        try:
            canonical = str(UUID(value))
            if value == canonical:
                return canonical
        except ValueError:
            pass
    raise ValueError("Mã bản nháp không hợp lệ.")


def draft_values(draft):
    fields = []
    for key, limit in (("to", 2048), ("subject", 1024), ("body", 65536), ("links", 2048)):
        value = draft.get(key, "")
        if not isinstance(value, str) or len(value) > limit:
            raise ValueError(f"Nội dung bản nháp vượt giới hạn của trường {key}.")
        fields.append(value)
    return draft_id(draft.get("id")), *fields
