"""MySQL persistence for the Lab3 SMTP, POP3 and account services."""
from __future__ import annotations

import os
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from mailbox_state import draft_id, draft_values, message_id
from mail_service import (
    DuplicateAccountError, MailStorageError, normalize_account, now, parse_delivery,
    password_hash, password_matches, validate_registration,
)


class MySQLMailStore:
    def __init__(self, *, seed_demo: bool = True):
        try:
            import mysql.connector
        except ImportError:
            raise RuntimeError("Install mysql-connector-python to use MySQL storage.") from None

        self.connector = mysql.connector
        try:
            self.config = {
                "host": os.getenv("LAB3_MYSQL_HOST", "127.0.0.1"),
                "port": int(os.getenv("LAB3_MYSQL_PORT", "3306")),
                "user": os.environ["LAB3_MYSQL_USER"],
                "password": os.environ["LAB3_MYSQL_PASSWORD"],
                "database": os.environ["LAB3_MYSQL_DATABASE"],
                "charset": "utf8mb4",
                "collation": "utf8mb4_bin",
                "connection_timeout": 10,
            }
        except (KeyError, ValueError):
            raise RuntimeError("Set LAB3_MYSQL_USER, LAB3_MYSQL_PASSWORD and LAB3_MYSQL_DATABASE, and use a numeric port.") from None
        self.init_db(seed_demo=seed_demo)

    @contextmanager
    def transaction(self, *, isolation_level=None):
        try:
            connection = self.connector.connect(**self.config)
            try:
                connection.start_transaction(isolation_level=isolation_level)
                cursor = connection.cursor(dictionary=True, buffered=True)
                try:
                    yield cursor
                finally:
                    cursor.close()
                connection.commit()
            except BaseException:
                connection.rollback()
                raise
            finally:
                connection.close()
        except self.connector.Error:
            raise MailStorageError("Không thể truy cập cơ sở dữ liệu MySQL của Ya-mail.") from None

    def init_db(self, *, seed_demo: bool = True) -> None:
        schema = Path(__file__).with_name("mysql_schema.sql").read_text(encoding="utf-8")
        with self.transaction() as db:
            for statement in schema.split(";"):
                if statement.strip():
                    db.execute(statement)
        if seed_demo:
            with self.transaction() as db:
                db.execute(
                    "INSERT INTO users(email,password,created_at,display_name) VALUES(%s,%s,%s,'') "
                    "ON DUPLICATE KEY UPDATE email=email",
                    ("student@localhost", password_hash("student"), now()),
                )

    def add_user(self, address: str, password: str) -> None:
        hashed = password_hash(password)
        with self.transaction() as db:
            db.execute(
                "INSERT INTO users(email,password,created_at,display_name) VALUES(%s,%s,%s,'') "
                "ON DUPLICATE KEY UPDATE password=%s",
                (address.strip().lower(), hashed, now(), hashed),
            )

    def register_user(self, name: str, email: str, password: str) -> dict:
        address, name = validate_registration(name, email, password)
        with self.transaction() as db:
            try:
                db.execute("INSERT INTO users(email,password,created_at,display_name) VALUES(%s,%s,%s,%s)",
                           (address, password_hash(password), now(), name))
            except self.connector.IntegrityError as exc:
                if exc.errno != 1062:
                    raise
                raise DuplicateAccountError("Tài khoản đã tồn tại. Vui lòng đăng nhập hoặc chọn tên khác.") from None
        return {"email": address, "name": name}

    def user_exists(self, address: str) -> bool:
        with self.transaction() as db:
            db.execute("SELECT 1 FROM users WHERE email=%s", (address.strip().lower(),))
            return db.fetchone() is not None

    def profile(self, address: str) -> dict | None:
        with self.transaction() as db:
            db.execute("SELECT email,display_name FROM users WHERE email=%s", (normalize_account(address),))
            row = db.fetchone()
        return {"email": row["email"], "name": row["display_name"] or row["email"].split("@")[0]} if row else None

    def authenticate(self, address: str, password: str) -> bool:
        account = normalize_account(address)
        with self.transaction() as db:
            db.execute("SELECT password FROM users WHERE email=%s", (account,))
            row = db.fetchone()
            if not row or not password_matches(password, row["password"]):
                return False
            if not row["password"].startswith("pbkdf2_sha256$"):
                db.execute("UPDATE users SET password=%s WHERE email=%s AND BINARY password=%s",
                           (password_hash(password), account, row["password"]))
        return True

    def deliver(self, sender, recipients, raw: bytes) -> int:
        subject, body = parse_delivery(raw)
        stamp = now()
        addresses = [recipient.strip().lower() for recipient in recipients if recipient.strip()]
        if not addresses:
            return 0
        with self.transaction() as db:
            for recipient in addresses:
                db.execute(
                    "INSERT INTO messages(sender,recipient,subject,body,raw,created_at) VALUES(%s,%s,%s,%s,%s,%s)",
                    (sender, recipient, subject, body, raw, stamp),
                )
        return len(addresses)

    def messages(self, user: str) -> list[dict]:
        with self.transaction() as db:
            db.execute("SELECT * FROM messages WHERE recipient=%s AND deleted=0 ORDER BY id", (user.strip().lower(),))
            return db.fetchall()

    def mark_deleted(self, message_id: int, user: str) -> None:
        self.mark_deleted_many(user, (message_id,))

    def mark_deleted_many(self, user: str, ids) -> None:
        account = user.strip().lower()
        with self.transaction() as db:
            db.executemany("UPDATE messages SET deleted=1 WHERE id=%s AND recipient=%s",
                           [(message_id, account) for message_id in sorted(ids)])

    def mailbox_listing(self, user: str) -> list[dict]:
        with self.transaction() as db:
            db.execute("SELECT id,OCTET_LENGTH(raw) AS size FROM messages "
                       "WHERE recipient=%s AND deleted=0 ORDER BY id", (user.strip().lower(),))
            return db.fetchall()

    def message_raw(self, user: str, message_id: int) -> bytes | None:
        with self.transaction() as db:
            db.execute("SELECT raw FROM messages WHERE recipient=%s AND id=%s",
                       (user.strip().lower(), message_id))
            row = db.fetchone()
        return bytes(row["raw"]) if row else None

    def list_read(self, user: str) -> list[str]:
        with self.transaction() as db:
            db.execute(
                "SELECT message_id FROM mailbox_read JOIN messages ON messages.id=message_id "
                "WHERE account=%s AND recipient=account AND deleted=0 ORDER BY message_id",
                (normalize_account(user),),
            )
            return [f"lab3-{row['message_id']}" for row in db.fetchall()]

    def set_read(self, user: str, uidl: str, read: bool = True) -> None:
        uid = message_id(uidl)
        account = normalize_account(user)
        with self.transaction() as db:
            if read:
                db.execute(
                    "INSERT INTO mailbox_read(account,message_id) "
                    "SELECT users.email,messages.id FROM messages JOIN users ON users.email=messages.recipient "
                    "WHERE messages.id=%s AND users.email=%s AND messages.deleted=0 "
                    "ON DUPLICATE KEY UPDATE message_id=mailbox_read.message_id",
                    (uid, account),
                )
            else:
                db.execute("DELETE FROM mailbox_read WHERE account=%s AND message_id=%s", (account, uid))

    def list_drafts(self, user: str) -> list[dict]:
        with self.transaction() as db:
            db.execute("SELECT id,recipient AS `to`,subject,body,links,updated_at FROM drafts "
                       "WHERE account=%s ORDER BY updated_at DESC,id", (normalize_account(user),))
            return db.fetchall()

    def save_draft(self, user: str, draft: dict) -> dict:
        identifier, recipient, subject, body, links = draft_values(draft)
        stamp = datetime.now(timezone.utc).isoformat(timespec="microseconds")
        content = (recipient, subject, body, links, stamp)
        with self.transaction() as db:
            db.execute(
                "INSERT INTO drafts(account,id,recipient,subject,body,links,updated_at) VALUES(%s,%s,%s,%s,%s,%s,%s) "
                "ON DUPLICATE KEY UPDATE recipient=%s,subject=%s,body=%s,links=%s,updated_at=%s",
                (normalize_account(user), identifier, *content, *content),
            )
        return {"id": identifier, "to": recipient, "subject": subject, "body": body, "links": links, "updated_at": stamp}

    def delete_draft(self, user: str, identifier: str) -> bool:
        with self.transaction() as db:
            db.execute("DELETE FROM drafts WHERE account=%s AND id=%s", (normalize_account(user), draft_id(identifier)))
            return db.rowcount > 0
