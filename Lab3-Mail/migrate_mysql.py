"""Copy a Lab3 SQLite database into an empty MySQL database."""
from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
from contextlib import closing
from pathlib import Path

from mail_service import DEFAULT_DB, MailStorageError
from mysql_store import MySQLMailStore


def sqlite_snapshot(source):
    user_columns = {row[1] for row in source.execute("PRAGMA table_info(users)")}
    tables = {row[0] for row in source.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    display_name = "display_name" if "display_name" in user_columns else "''"
    sequence = (
        source.execute("SELECT seq FROM sqlite_sequence WHERE name='messages'").fetchone()
        if "sqlite_sequence" in tables else None
    )
    queries = [
        ("users", f"SELECT email,password,created_at,{display_name} FROM users ORDER BY email",
         "INSERT INTO users(email,password,created_at,display_name) VALUES(%s,%s,%s,%s)"),
        ("messages", "SELECT id,sender,recipient,subject,body,raw,created_at,deleted FROM messages ORDER BY id",
         "INSERT INTO messages(id,sender,recipient,subject,body,raw,created_at,deleted) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)"),
    ]
    reads = "SELECT owner,message_id FROM message_reads ORDER BY owner,message_id"
    drafts = "SELECT owner,id,recipient,subject,body,links,updated_at FROM drafts ORDER BY owner,id"
    queries.extend([
        ("mailbox_read", reads if "message_reads" in tables else "SELECT 1 WHERE 0",
         "INSERT INTO mailbox_read(account,message_id) VALUES(%s,%s)"),
        ("drafts", drafts if "drafts" in tables else "SELECT 1 WHERE 0",
         "INSERT INTO drafts(account,id,recipient,subject,body,links,updated_at) VALUES(%s,%s,%s,%s,%s,%s,%s)"),
    ])
    return queries, sequence[0] if sequence else 0


def snapshot_digest(source, queries, sequence):
    digest = hashlib.sha256(str(sequence).encode("ascii"))
    counts = {}
    for table, query, _ in queries:
        digest.update(table.encode("ascii") + b"\n")
        count = 0
        for row in source.execute(query):
            # Hash large MIME blobs separately to avoid materializing their hex encoding.
            record = tuple({"sha256": hashlib.sha256(value).hexdigest()} if isinstance(value, bytes) else value
                           for value in row)
            digest.update(json.dumps(record, ensure_ascii=True).encode("ascii") + b"\n")
            count += 1
        counts[table] = count
    return digest.hexdigest(), counts


def migrate(source_path: Path) -> tuple[int, int, bool]:
    with closing(sqlite3.connect(source_path.resolve().as_uri() + "?mode=ro", uri=True)) as source:
        source.execute("BEGIN")
        queries, sequence = sqlite_snapshot(source)
        digest, counts = snapshot_digest(source, queries, sequence)
        store = MySQLMailStore(seed_demo=False)
        with store.transaction(isolation_level="SERIALIZABLE") as db:
            # Hold the lock through the copy so concurrent migration attempts
            # see either an empty target or the committed snapshot receipt.
            db.execute("INSERT INTO sqlite_migration(slot) VALUES(1) ON DUPLICATE KEY UPDATE slot=slot")
            db.execute("SELECT source_digest,user_count,message_count FROM sqlite_migration WHERE slot=1 FOR UPDATE")
            receipt = db.fetchone()
            if receipt["source_digest"] == digest:
                return receipt["user_count"], receipt["message_count"], False
            if receipt["source_digest"] is not None:
                raise ValueError("MySQL đã nhập một bản chụp SQLite khác. Hãy dùng cơ sở dữ liệu MySQL trống.")
            for table in ("users", "messages", "mailbox_read", "drafts"):
                db.execute(f"SELECT 1 FROM {table} LIMIT 1 FOR UPDATE")
                if db.fetchone() is not None:
                    raise ValueError("Cơ sở dữ liệu MySQL đã có dữ liệu. Hãy dùng cơ sở dữ liệu trống.")
            for _, query, insert in queries:
                for row in source.execute(query):
                    db.execute(insert, row)
            if sequence:
                db.execute("SELECT MAX(id) AS largest_id FROM messages")
                largest_id = db.fetchone()["largest_id"] or 0
                if sequence > largest_id:
                    # Advance AUTO_INCREMENT past previously used IDs if a
                    # message was physically removed from the SQLite file.
                    db.execute(
                        "INSERT INTO messages(id,sender,recipient,subject,body,raw,created_at,deleted) "
                        "VALUES(%s,'','','','',X'','',1)", (sequence,),
                    )
                    db.execute("DELETE FROM messages WHERE id=%s", (sequence,))
            db.execute("UPDATE sqlite_migration SET source_digest=%s,user_count=%s,message_count=%s WHERE slot=1",
                       (digest, counts["users"], counts["messages"]))
    return counts["users"], counts["messages"], True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sqlite_path", type=Path, nargs="?", default=DEFAULT_DB)
    args = parser.parse_args()
    try:
        users, messages, imported = migrate(args.sqlite_path)
    except (MailStorageError, RuntimeError, ValueError) as exc:
        parser.exit(1, f"{exc}\n")
    except sqlite3.Error:
        parser.exit(1, "Không thể đọc cơ sở dữ liệu SQLite của Ya-mail.\n")
    status = "Đã nhập" if imported else "Bản chụp đã được nhập trước đó"
    print(f"{status} ({users} tài khoản, {messages} thư).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
