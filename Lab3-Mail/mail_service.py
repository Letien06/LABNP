"""SMTP + POP3 lab service with SQLite or MySQL persistence.

The implementation is intentionally easy to inspect for a networking lab.  It
speaks the line-oriented portions of SMTP and POP3 needed by a mail client and
stores accepted messages through the configured repository. The module also contains
helpers for the Gmail SMTP/POP3-over-TLS path; credentials are read only from
environment variables.
"""
from __future__ import annotations

import argparse
import email
import email.policy
import base64
import hashlib
import hmac
import json
import os
import poplib
import re
import smtplib
import socketserver
import sqlite3
import ssl
import sys
import threading
from contextlib import closing, contextmanager
from datetime import datetime, timezone
from email.message import EmailMessage
from email.parser import BytesParser
from pathlib import Path
from typing import Iterable

from mailbox_state import draft_id, draft_values, message_id
from smtp_relay import GmailRelay, RelayError


DEFAULT_DB = Path(__file__).with_name("mail.db")
PASSWORD_ROUNDS = 260_000


class DuplicateAccountError(ValueError):
    duplicate_account = True


class MailStorageError(Exception):
    """Storage is temporarily unavailable."""


def password_hash(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PASSWORD_ROUNDS)
    return f"pbkdf2_sha256${PASSWORD_ROUNDS}${salt.hex()}${digest.hex()}"


def password_matches(password: str, saved: str) -> bool:
    if not saved.startswith("pbkdf2_sha256$"):
        return hmac.compare_digest(saved.encode("utf-8"), password.encode("utf-8"))
    try:
        _, rounds, salt, digest = saved.split("$")
        actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), bytes.fromhex(salt), int(rounds))
        return hmac.compare_digest(actual, bytes.fromhex(digest))
    except (ValueError, OverflowError):
        return False


def normalize_account(address: str) -> str:
    address = address.strip().lower()
    return address if "@" in address else address + "@yamail.local"


def validate_registration(name, email, password):
    if not isinstance(name, str) or not name.strip() or len(name.strip()) > 100 or any(ord(c) < 32 for c in name):
        raise ValueError("Tên hiển thị cần từ 1 đến 100 ký tự hợp lệ.")
    if not isinstance(email, str):
        raise ValueError("Vui lòng nhập tên tài khoản Ya-mail.")
    address = normalize_account(email)
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,63}@(?:yamail\.local|localhost)", address):
        raise ValueError("Tên tài khoản cần 3–64 ký tự (chữ, số, dấu chấm, gạch dưới hoặc gạch ngang), thuộc @yamail.local hoặc @localhost.")
    if (not isinstance(password, str) or not 6 <= len(password) <= 1024
            or any(ord(character) < 32 or ord(character) == 127 for character in password)):
        raise ValueError("Mật khẩu cần từ 6 đến 1024 ký tự và không chứa ký tự điều khiển.")
    return address, name.strip()


def parse_delivery(raw):
    msg = BytesParser(policy=email.policy.default).parsebytes(raw)
    subject = str(msg.get("Subject", ""))
    body = msg.get_body(preferencelist=("plain", "html"))
    text = body.get_content() if body is not None else (msg.get_content() if not msg.is_multipart() else "")
    if not isinstance(text, str):
        text = text.decode("utf-8", "replace")
    return subject, text


class MailStore:
    """SQLite-backed users and messages repository.

    A connection is opened per operation so SMTP and POP3 worker threads can
    safely share one database file without sharing sqlite connection objects.
    """

    def __init__(self, path: str | os.PathLike[str] = DEFAULT_DB):
        self.path = str(path)
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self.init_db()

    def connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.path, timeout=15)
        db.row_factory = sqlite3.Row
        return db

    @contextmanager
    def transaction(self):
        with closing(self.connect()) as db, db:
            yield db

    def init_db(self) -> None:
        with self.transaction() as db:
            db.executescript(
                """
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS users(
                    email TEXT PRIMARY KEY COLLATE NOCASE,
                    password TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS messages(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sender TEXT NOT NULL,
                    recipient TEXT NOT NULL COLLATE NOCASE,
                    subject TEXT NOT NULL DEFAULT '',
                    body TEXT NOT NULL DEFAULT '',
                    raw BLOB NOT NULL,
                    created_at TEXT NOT NULL,
                    deleted INTEGER NOT NULL DEFAULT 0
                );
                CREATE INDEX IF NOT EXISTS idx_messages_recipient
                    ON messages(recipient, deleted, id);
                CREATE TABLE IF NOT EXISTS message_reads(
                    owner TEXT NOT NULL COLLATE NOCASE,
                    message_id INTEGER NOT NULL,
                    PRIMARY KEY(owner, message_id)
                );
                CREATE TABLE IF NOT EXISTS drafts(
                    owner TEXT NOT NULL COLLATE NOCASE,
                    id TEXT NOT NULL,
                    recipient TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    body TEXT NOT NULL,
                    links TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY(owner, id)
                );
                """
            )
            if "display_name" not in {row["name"] for row in db.execute("PRAGMA table_info(users)")}:
                db.execute("ALTER TABLE users ADD COLUMN display_name TEXT NOT NULL DEFAULT ''")
            # A deterministic local account makes the POP3 demo runnable on a
            # fresh checkout.  Students can add more users with ``user``.
            if db.execute("SELECT 1 FROM users WHERE email=?", ("student@localhost",)).fetchone() is None:
                db.execute(
                    "INSERT OR IGNORE INTO users(email,password,created_at) VALUES(?,?,?)",
                    ("student@localhost", password_hash("student"), now()),
                )

    def add_user(self, address: str, password: str) -> None:
        with self.transaction() as db:
            db.execute(
                "INSERT INTO users(email,password,created_at) VALUES(?,?,?) "
                "ON CONFLICT(email) DO UPDATE SET password=excluded.password",
                (address.strip().lower(), password_hash(password), now()),
            )

    def register_user(self, name: str, email: str, password: str) -> dict:
        address, name = validate_registration(name, email, password)
        try:
            with self.transaction() as db:
                db.execute("INSERT INTO users(email,password,created_at,display_name) VALUES(?,?,?,?)",
                           (address, password_hash(password), now(), name))
        except sqlite3.IntegrityError:
            raise DuplicateAccountError("Tài khoản đã tồn tại. Vui lòng đăng nhập hoặc chọn tên khác.") from None
        return {"email": address, "name": name}

    def user_exists(self, address: str) -> bool:
        with self.transaction() as db:
            return db.execute("SELECT 1 FROM users WHERE email=?", (address.strip().lower(),)).fetchone() is not None

    def profile(self, address: str) -> dict | None:
        with self.transaction() as db:
            row = db.execute("SELECT email,display_name FROM users WHERE email=?", (normalize_account(address),)).fetchone()
        return {"email": row["email"], "name": row["display_name"] or row["email"].split("@")[0]} if row else None

    def authenticate(self, address: str, password: str) -> bool:
        with self.transaction() as db:
            row = db.execute(
                "SELECT password FROM users WHERE email=?",
                (normalize_account(address),),
            ).fetchone()
            if not row or not password_matches(password, row["password"]):
                return False
            if not row["password"].startswith("pbkdf2_sha256$"):
                db.execute("UPDATE users SET password=? WHERE email=? AND password=?",
                           (password_hash(password), normalize_account(address), row["password"]))
        return True

    def deliver(self, sender: str, recipients: Iterable[str], raw: bytes) -> int:
        subject, text = parse_delivery(raw)
        count = 0
        stamp = now()
        with self.transaction() as db:
            for recipient in recipients:
                recipient = recipient.strip().lower()
                if not recipient:
                    continue
                db.execute(
                    "INSERT INTO messages(sender,recipient,subject,body,raw,created_at) "
                    "VALUES(?,?,?,?,?,?)",
                    (sender, recipient, subject, text, raw, stamp),
                )
                count += 1
        return count

    def messages(self, user: str) -> list[sqlite3.Row]:
        with self.transaction() as db:
            return list(
                db.execute(
                    "SELECT * FROM messages WHERE recipient=? AND deleted=0 ORDER BY id",
                    (user.strip().lower(),),
                )
            )

    def mark_deleted(self, message_id: int, user: str) -> None:
        self.mark_deleted_many(user, (message_id,))

    def mark_deleted_many(self, user, ids):
        with self.transaction() as db:
            db.executemany(
                "UPDATE messages SET deleted=1 WHERE id=? AND recipient=?",
                ((mid, normalize_account(user)) for mid in ids),
            )

    def mailbox_listing(self, user):
        with self.transaction() as db:
            return list(db.execute("SELECT id,length(raw) AS size FROM messages WHERE recipient=? AND deleted=0 ORDER BY id",
                                   (normalize_account(user),)))

    def message_raw(self, user, mid):
        with self.transaction() as db:
            row = db.execute("SELECT raw FROM messages WHERE recipient=? AND id=?", (normalize_account(user), mid)).fetchone()
        return row["raw"] if row else None

    def list_read(self, user):
        with self.transaction() as db:
            rows = db.execute("SELECT r.message_id FROM message_reads r JOIN messages m "
                              "ON m.id=r.message_id AND m.recipient=r.owner WHERE r.owner=? AND m.deleted=0",
                              (normalize_account(user),))
            return [f"lab3-{row['message_id']}" for row in rows]

    def set_read(self, user, uid, read=True):
        owner, mid = normalize_account(user), message_id(uid)
        with self.transaction() as db:
            if read:
                db.execute("INSERT OR IGNORE INTO message_reads(owner,message_id) "
                           "SELECT recipient,id FROM messages WHERE id=? AND recipient=? AND deleted=0", (mid, owner))
            else:
                db.execute("DELETE FROM message_reads WHERE owner=? AND message_id=?", (owner, mid))

    def list_drafts(self, user):
        with self.transaction() as db:
            rows = db.execute('SELECT id,recipient AS "to",subject,body,links,updated_at FROM drafts '
                              "WHERE owner=? ORDER BY updated_at DESC,id", (normalize_account(user),))
            return [dict(row) for row in rows]

    def save_draft(self, user, draft):
        key, to, subject, body, links = draft_values(draft)
        stamp = datetime.now(timezone.utc).isoformat(timespec="microseconds")
        with self.transaction() as db:
            db.execute("INSERT INTO drafts(owner,id,recipient,subject,body,links,updated_at) VALUES(?,?,?,?,?,?,?) "
                       "ON CONFLICT(owner,id) DO UPDATE SET recipient=excluded.recipient,subject=excluded.subject,"
                       "body=excluded.body,links=excluded.links,updated_at=excluded.updated_at",
                       (normalize_account(user), key, to, subject, body, links, stamp))
        return {"id": key, "to": to, "subject": subject, "body": body, "links": links, "updated_at": stamp}

    def delete_draft(self, user, key):
        with self.transaction() as db:
            result = db.execute("DELETE FROM drafts WHERE owner=? AND id=?", (normalize_account(user), draft_id(key)))
            return result.rowcount > 0


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _send_line(writer, text: str) -> None:
    writer.write((text + "\r\n").encode("utf-8"))
    writer.flush()


class SMTPHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        store: MailStore = self.server.store  # type: ignore[attr-defined]
        _send_line(self.wfile, "220 localhost ESMTP Lab3-Mail")
        sender: str | None = None
        recipients: list[str] = []
        authenticated: str | None = None
        while True:
            line = self.rfile.readline(8192)
            if not line:
                return
            command, _, argument = line.decode("utf-8", "replace").rstrip("\r\n").partition(" ")
            verb = command.upper()
            argument = argument.strip()
            if verb in ("EHLO", "HELO"):
                _send_line(self.wfile, "250-localhost")
                _send_line(self.wfile, "250-AUTH LOGIN PLAIN")
                _send_line(self.wfile, "250 SIZE 10485760")
            elif verb == "AUTH":
                # AUTH PLAIN [base64] and AUTH LOGIN are sufficient for local
                # smtplib clients.  We deliberately keep the exchange simple.
                import base64
                try:
                    mode, _, token = argument.partition(" ")
                    if mode.upper() == "PLAIN":
                        if not token:
                            _send_line(self.wfile, "334 ")
                        decoded = base64.b64decode(token or self.rfile.readline().strip()).decode()
                        _, user, password = decoded.split("\x00", 2)
                    elif mode.upper() == "LOGIN":
                        # smtplib may send the username as an initial response
                        # or wait for the username challenge.
                        if token:
                            user = base64.b64decode(token).decode()
                        else:
                            _send_line(self.wfile, "334 VXNlcm5hbWU6")
                            user = base64.b64decode(self.rfile.readline().strip()).decode()
                        _send_line(self.wfile, "334 UGFzc3dvcmQ6")
                        password = base64.b64decode(self.rfile.readline().strip()).decode()
                    else:
                        raise ValueError("unsupported auth")
                    authenticated = normalize_account(user) if store.authenticate(user, password) else None
                    _send_line(self.wfile, "235 2.7.0 Authentication successful" if authenticated else "535 5.7.8 Authentication failed")
                except (MailStorageError, sqlite3.Error):
                    authenticated = None
                    _send_line(self.wfile, "454 4.7.0 Authentication temporarily unavailable")
                except (ValueError, UnicodeError):
                    authenticated = None
                    _send_line(self.wfile, "535 5.7.8 Authentication failed")
            elif verb == "MAIL" and argument.upper().startswith("FROM:"):
                path = argument[5:].strip()
                address = re.match(r"<([^>]*)>", path)
                sender = (address.group(1) if address else path.partition(" ")[0]).strip().lower() or None
                recipients = []
                _send_line(self.wfile, "250 2.1.0 Sender OK" if sender else "501 5.1.7 Invalid sender address")
            elif verb == "RCPT" and argument.upper().startswith("TO:"):
                if sender is None:
                    _send_line(self.wfile, "503 5.5.1 Need MAIL FROM first")
                else:
                    recipient = argument[3:].strip().split(">", 1)[0].lstrip("<").lower()
                    try:
                        exists = store.user_exists(recipient)
                    except (MailStorageError, sqlite3.Error):
                        _send_line(self.wfile, "451 4.3.0 Local storage temporarily unavailable")
                        continue
                    local_domain = recipient.rsplit("@", 1)[-1] in ("localhost", "yamail.local")
                    if not re.fullmatch(r"[^\s<>@]+@[^\s<>@]+", recipient):
                        _send_line(self.wfile, "550 5.1.3 Invalid recipient address")
                        continue
                    if not exists and local_domain:
                        _send_line(self.wfile, "550 5.1.1 Unknown local recipient")
                        continue
                    if not exists:
                        relay = self.server.relay
                        if relay is None or not relay.status()["configured"]:
                            _send_line(self.wfile, "550 5.7.1 External relay is not configured; ask the server administrator")
                            continue
                        if not authenticated or authenticated.rsplit("@", 1)[-1] not in ("localhost", "yamail.local"):
                            _send_line(self.wfile, "530 5.7.0 Authenticate a local Ya-mail account before external delivery")
                            continue
                        if sender != authenticated:
                            _send_line(self.wfile, "550 5.7.1 Envelope sender must match the authenticated Ya-mail account")
                            continue
                    if recipient not in recipients:
                        recipients.append(recipient)
                    _send_line(self.wfile, "250 2.1.5 Recipient OK")
            elif verb == "DATA":
                if not sender or not recipients:
                    _send_line(self.wfile, "503 5.5.1 Need sender and recipient")
                    continue
                _send_line(self.wfile, "354 End data with <CR><LF>.<CR><LF>")
                chunks = []
                size = 0
                while True:
                    data_line = self.rfile.readline(10 * 1024 * 1024)
                    if not data_line:
                        return  # An interrupted DATA transaction is never delivered.
                    if data_line in (b".\r\n", b".\n"):
                        break
                    if data_line.startswith(b".."):
                        data_line = data_line[1:]
                    size += len(data_line)
                    if size <= 10 * 1024 * 1024:
                        chunks.append(data_line)
                if size > 10 * 1024 * 1024:
                    _send_line(self.wfile, "552 5.3.4 Message exceeds 10 MiB limit")
                    sender, recipients = None, []
                    continue
                raw = b"".join(chunks)
                try:
                    try:
                        local = [recipient for recipient in recipients if store.user_exists(recipient)]
                    except (MailStorageError, sqlite3.Error):
                        raise RelayError(451, "4.3.0 Local storage temporarily unavailable") from None
                    external = [recipient for recipient in recipients if recipient not in local]
                    # Complete external submission before creating local copies,
                    # so a relay failure cannot leave a successful local send.
                    refused = {}
                    if external:
                        if sender != authenticated:
                            raise RelayError(550, "5.7.1 Envelope sender must match authenticated account")
                        relay_refused = self.server.relay.send(authenticated, external, raw) or {}
                        refused = {address: response[0] for address, response in relay_refused.items()}
                    relayed = len(external) - len(refused)
                    try:
                        accepted = store.deliver(sender, local, raw) if local else 0
                    except (MailStorageError, sqlite3.Error, LookupError, ValueError):
                        if not relayed:
                            raise RelayError(451, "4.3.0 Local delivery failed") from None
                        accepted = 0
                        refused.update(dict.fromkeys(local, 451))
                    suffix = f"; relay accepted {relayed} external recipient(s)" if relayed else ""
                    if refused:
                        suffix += "; yamail-refused=" + json.dumps(refused, separators=(",", ":"))
                    _send_line(self.wfile, f"250 2.0.0 Message accepted for {accepted} local mailbox(es){suffix}")
                except RelayError as exc:
                    _send_line(self.wfile, f"{exc.code} {exc}")
                sender, recipients = None, []
            elif verb == "RSET":
                sender, recipients = None, []
                _send_line(self.wfile, "250 2.0.0 Reset")
            elif verb == "NOOP":
                _send_line(self.wfile, "250 2.0.0 OK")
            elif verb == "QUIT":
                _send_line(self.wfile, "221 2.0.0 Bye")
                return
            else:
                _send_line(self.wfile, "502 5.5.2 Command not implemented")


class POP3Handler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        store: MailStore = self.server.store  # type: ignore[attr-defined]
        _send_line(self.wfile, "+OK localhost POP3 Lab3-Mail")
        user: str | None = None
        logged_in = False
        marked: set[int] = set()
        mailbox = []
        def message_at(argument):
            number = int(argument)
            if number < 1 or number > len(mailbox):
                raise IndexError()
            row = mailbox[number - 1]
            if row["id"] in marked:
                raise IndexError()
            return row
        while True:
            line = self.rfile.readline(8192)
            if not line:
                return
            command, _, argument = line.decode("utf-8", "replace").rstrip("\r\n").partition(" ")
            verb = command.upper()
            if verb != "PASS":
                argument = argument.strip()
            if verb == "USER":
                if logged_in:
                    _send_line(self.wfile, "-ERR Already authenticated")
                    continue
                user = normalize_account(argument)
                _send_line(self.wfile, "+OK User accepted")
            elif verb == "PASS":
                if logged_in:
                    _send_line(self.wfile, "-ERR Already authenticated")
                    continue
                try:
                    verified = user and store.authenticate(user, argument)
                    if verified:
                        mailbox = store.mailbox_listing(user)
                except (MailStorageError, sqlite3.Error):
                    _send_line(self.wfile, "-ERR Local storage temporarily unavailable")
                    continue
                if verified:
                    logged_in = True
                    _send_line(self.wfile, "+OK Mailbox locked and ready")
                else:
                    _send_line(self.wfile, "-ERR Authentication failed")
            elif verb == "STAT" and logged_in:
                rows = [r for r in mailbox if r["id"] not in marked]
                _send_line(self.wfile, f"+OK {len(rows)} {sum(r['size'] for r in rows)}")
            elif verb in ("LIST", "UIDL") and logged_in:
                if argument:
                    try:
                        row = message_at(argument)
                        value = row['size'] if verb == "LIST" else f"lab3-{row['id']}"
                        _send_line(self.wfile, f"+OK {argument} {value}")
                    except (ValueError, IndexError):
                        _send_line(self.wfile, "-ERR No such message")
                else:
                    _send_line(self.wfile, "+OK scan listing follows")
                    for i, row in enumerate(mailbox, 1):
                        if row['id'] in marked:
                            continue
                        value = row['size'] if verb == "LIST" else f"lab3-{row['id']}"
                        _send_line(self.wfile, f"{i} {value}")
                    _send_line(self.wfile, ".")
            elif verb == "RETR" and logged_in:
                try:
                    row = message_at(argument)
                    raw = store.message_raw(user, row["id"])
                    if raw is None:
                        raise IndexError()
                    _send_line(self.wfile, f"+OK {len(raw)} octets")
                    for data_line in raw.splitlines():
                        if data_line.startswith(b"."):
                            data_line = b"." + data_line
                        self.wfile.write(data_line + b"\r\n")
                    _send_line(self.wfile, ".")
                except (ValueError, IndexError):
                    _send_line(self.wfile, "-ERR No such message")
                except (MailStorageError, sqlite3.Error):
                    _send_line(self.wfile, "-ERR Local storage temporarily unavailable")
            elif verb == "DELE" and logged_in:
                try:
                    row = message_at(argument)
                    marked.add(int(row["id"]))
                    _send_line(self.wfile, "+OK Message marked for deletion")
                except (ValueError, IndexError):
                    _send_line(self.wfile, "-ERR No such message")
            elif verb == "NOOP" and logged_in:
                _send_line(self.wfile, "+OK")
            elif verb == "RSET" and logged_in:
                marked.clear()
                _send_line(self.wfile, "+OK Deletion marks cleared")
            elif verb == "QUIT":
                if logged_in and user and marked:
                    try:
                        store.mark_deleted_many(user, marked)
                    except (MailStorageError, sqlite3.Error):
                        _send_line(self.wfile, "-ERR Deletion could not be saved")
                        return
                _send_line(self.wfile, "+OK Bye")
                return
            else:
                _send_line(self.wfile, "-ERR Authenticate first or unsupported command")


class ThreadingSMTPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, handler, store, relay=None):
        super().__init__(address, handler)
        self.store = store
        self.relay = relay


class ThreadingPOP3Server(ThreadingSMTPServer):
    pass


class MailServers:
    def __init__(self, store: MailStore, smtp_host="127.0.0.1", smtp_port=2525, pop3_host="127.0.0.1", pop3_port=8110, account_port=None, relay=None):
        self.relay = relay if relay is not None else GmailRelay()
        self.smtp = ThreadingSMTPServer((smtp_host, smtp_port), SMTPHandler, store, self.relay)
        self.pop3 = ThreadingPOP3Server((pop3_host, pop3_port), POP3Handler, store)
        from account_service import AccountServer
        self.account = AccountServer(account_port, store, self.relay) if account_port is not None else None
        self.servers = [self.smtp, self.pop3] + ([self.account] if self.account else [])
        self.threads: list[threading.Thread] = []

    def start(self) -> None:
        for server in self.servers:
            thread = threading.Thread(target=server.serve_forever, name=f"{server.server_address[1]}-server", daemon=True)
            thread.start()
            self.threads.append(thread)

    def close(self) -> None:
        for server in self.servers:
            server.shutdown()
            server.server_close()


def send_local(host: str, port: int, sender: str, recipients: list[str], subject: str, body: str, username: str | None = None, password: str | None = None) -> None:
    message = EmailMessage()
    message["From"], message["To"], message["Subject"] = sender, ", ".join(recipients), subject
    message.set_content(body)
    with smtplib.SMTP(host, port, timeout=10) as smtp:
        smtp.ehlo()
        if username and password:
            smtp.login(username, password)
        smtp.send_message(message)


def fetch_local(host: str, port: int, username: str, password: str) -> list[bytes]:
    mailbox = poplib.POP3(host, port, timeout=10)
    try:
        mailbox.user(username); mailbox.pass_(password)
        _, rows, _ = mailbox.list()
        return [b"\r\n".join(mailbox.retr(int(row.split()[0]))[1]) + b"\r\n" for row in rows]
    finally:
        mailbox.quit()


def send_gmail(sender: str, password: str, recipients: list[str], subject: str, body: str) -> None:
    """Gmail submission path using an app password (never a normal password)."""
    message = EmailMessage(); message["From"] = sender; message["To"] = ", ".join(recipients); message["Subject"] = subject; message.set_content(body)
    context = ssl.create_default_context()
    with smtplib.SMTP_SSL("smtp.gmail.com", 465, context=context, timeout=20) as smtp:
        smtp.login(sender, password); smtp.send_message(message)


def fetch_gmail(username: str, app_password: str, delete: bool = False) -> list[bytes]:
    """Retrieve Gmail INBOX over POP3S.  ``delete`` is opt-in."""
    context = ssl.create_default_context(); mailbox = poplib.POP3_SSL("pop.gmail.com", 995, context=context, timeout=20)
    try:
        mailbox.user(username); mailbox.pass_(app_password)
        _, rows, _ = mailbox.list(); result = []
        for row in rows:
            number = int(row.split()[0]); result.append(b"\r\n".join(mailbox.retr(number)[1]) + b"\r\n")
            if delete: mailbox.dele(number)
        return result
    finally:
        mailbox.quit()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Lab3 local SMTP/POP3 server and Gmail client")
    parser.add_argument("--db", default=str(DEFAULT_DB), help="SQLite database file")
    parser.add_argument("--backend", choices=("sqlite", "mysql"), default=os.getenv("LAB3_MAIL_BACKEND", "sqlite"),
                        help="Server database engine")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("init-db")
    user = sub.add_parser("user"); user.add_argument("email"); user.add_argument("password")
    server = sub.add_parser("server"); server.add_argument("--smtp-port", type=int, default=2525); server.add_argument("--pop3-port", type=int, default=8110); server.add_argument("--account-port", type=int, default=8033)
    send = sub.add_parser("send"); send.add_argument("--from", dest="sender", required=True); send.add_argument("--to", action="append", required=True); send.add_argument("--subject", default="Lab3 test"); send.add_argument("--body", default="Hello from Lab3-Mail"); send.add_argument("--port", type=int, default=2525)
    fetch = sub.add_parser("fetch"); fetch.add_argument("--user", default="student@localhost"); fetch.add_argument("--password", default="student"); fetch.add_argument("--port", type=int, default=8110)
    args = parser.parse_args(argv)
    store = None
    if args.command in ("init-db", "user", "server"):
        try:
            if args.backend == "mysql":
                from mysql_store import MySQLMailStore
                store = MySQLMailStore()
            else:
                store = MailStore(args.db)
        except (MailStorageError, RuntimeError, sqlite3.Error):
            print(f"Could not initialize {args.backend} storage. Check database settings and dependencies.", file=sys.stderr)
            return 1
    if args.command == "user": store.add_user(args.email, args.password); print(f"saved user {args.email}")
    elif args.command == "send": send_local("127.0.0.1", args.port, args.sender, args.to, args.subject, args.body); print("message sent")
    elif args.command == "fetch":
        for i, message in enumerate(fetch_local("127.0.0.1", args.port, args.user, args.password), 1): print(f"--- message {i} ---\n{message.decode('utf-8', 'replace')}")
    elif args.command == "server":
        servers = MailServers(store, smtp_port=args.smtp_port, pop3_port=args.pop3_port, account_port=args.account_port); servers.start(); print(f"SMTP listening on 127.0.0.1:{args.smtp_port}; POP3 on 127.0.0.1:{args.pop3_port}; account API on 127.0.0.1:{args.account_port}; storage: {args.backend}")
        try:
            threading.Event().wait()
        except KeyboardInterrupt:
            servers.close()
    return 0


if __name__ == "__main__":
    # Repository modules must share our exception types even in script mode.
    sys.modules.setdefault("mail_service", sys.modules[__name__])
    raise SystemExit(main())
