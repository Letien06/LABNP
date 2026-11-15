"""Exerciser for mail storage, incremental POP refresh and account state."""
import argparse
import hashlib
import os
import tempfile
from pathlib import Path
from uuid import uuid4

from mail_bridge import run
from mail_service import MailServers, MailStore


def configuration(servers, user="student@localhost", password="student"):
    return dict(user=user, password=password, smtpPort=servers.smtp.server_address[1],
                popPort=servers.pop3.server_address[1], accountPort=servers.account.server_address[1])


def verify(store, reopen):
    alice = "alice-test@yamail.local"
    servers = MailServers(store, smtp_port=0, pop3_port=0, account_port=0)
    servers.start()
    try:
        student = configuration(servers)
        assert run({**student, "action": "register", "email": alice, "name": "Tiếng Việt", "password": "Synthetic-1"})["email"] == alice
        author = configuration(servers, alice, "Synthetic-1")
        for body in ("Thư đầu 📨", "Thư sau"):
            assert run({**author, "action": "send", "to": student["user"], "subject": body, "body": body})["submitted"]
        loaded = run({**student, "action": "load", "knownUids": []})
        assert len(loaded["messages"]) == 2
        uid, uid2 = (row["uid"] for row in loaded["mailbox"])
        known = [uid, uid2]
        run({**student, "action": "set_read", "uid": uid, "read": True})
        run({**author, "action": "set_read", "uid": uid, "read": True})
        assert uid not in run({**author, "action": "list_read"})["uids"]
        incremental = run({**student, "action": "load", "knownUids": known})
        assert incremental["messages"] == [] and incremental["readUids"] == [uid]
        run({**author, "action": "send", "to": student["user"], "subject": "New", "body": "new"})
        delta = run({**student, "action": "load", "knownUids": known})
        assert len(delta["mailbox"]) == 3 and [row["subject"] for row in delta["messages"]] == ["New"]

        draft = {"id": str(uuid4()), "to": alice, "subject": "Nháp ✉", "body": "Tiếng Việt", "links": "https://example.org"}
        assert run({**student, **draft, "action": "save_draft"})["draft"]["subject"] == draft["subject"]
        assert run({**author, "action": "list_drafts"})["drafts"] == []
        assert run({**author, "action": "delete_draft", "id": draft["id"]})["deleted"] is False
        try:
            run({**student, "action": "list_drafts", "password": "wrong-password"})
        except ValueError:
            pass
        else:
            raise AssertionError("unauthenticated draft access")
    finally:
        servers.close()

    restarted = MailServers(reopen(), smtp_port=0, pop3_port=0, account_port=0)
    restarted.start()
    try:
        student = configuration(restarted)
        assert run({**student, "action": "list_read"})["uids"] == [uid]
        run({**student, "action": "set_read", "uid": uid, "read": False})
        assert run({**student, "action": "list_read"})["uids"] == []
        saved = run({**student, "action": "list_drafts"})["drafts"]
        assert len(saved) == 1 and all(saved[0][field] == draft[field] for field in draft)
        assert run({**student, "action": "delete_draft", "id": draft["id"]})["deleted"] is True
        run({**student, "action": "delete", "uid": uid})
        summary = run({**student, "action": "load", "knownUids": [uid, uid2]})
        assert uid not in [row["uid"] for row in summary["mailbox"]]
        assert summary["mailbox"][0] == {"id": 1, "uid": uid2}
    finally:
        restarted.close()


def verify_migration(path):
    from migrate_mysql import migrate
    from mysql_store import MySQLMailStore

    if not os.getenv("LAB3_MYSQL_DATABASE", "").startswith("lab3_test_"):
        raise ValueError("Set LAB3_MYSQL_DATABASE to a fresh lab3_test_ schema.")
    old = MailStore(path)
    old.add_user("migration@localhost", "Synthetic-2")
    raw = "Subject: Migration\r\n\r\nbytes".encode()
    old.deliver("migration@localhost", ["migration@localhost"], raw)
    old.deliver("migration@localhost", ["migration@localhost"], raw.replace(b"bytes", b"deleted"))
    old.mark_deleted(2, "migration@localhost")
    old.set_read("migration@localhost", "lab3-1", True)
    draft = {"id": str(uuid4()), "body": "Bản nháp từ SQLite 🌱"}
    old.save_draft("migration@localhost", draft)
    before = hashlib.sha256(path.read_bytes()).digest()
    assert migrate(path) == (2, 2, True)
    assert migrate(path) == (2, 2, False)
    assert hashlib.sha256(path.read_bytes()).digest() == before
    new = MySQLMailStore(seed_demo=False)
    assert new.authenticate("migration@localhost", "Synthetic-2")
    assert new.mailbox_listing("migration@localhost") == [{"id": 1, "size": len(raw)}]
    assert new.message_raw("migration@localhost", 1) == raw
    assert new.list_read("migration@localhost") == ["lab3-1"]
    assert new.list_drafts("migration@localhost")[0]["body"] == draft["body"]
    assert new.deliver("migration@localhost", ["migration@localhost"], raw) == 1
    assert new.mailbox_listing("migration@localhost")[-1]["id"] > 2
    assert not new.messages("student@localhost")
    old.save_draft("migration@localhost", {**draft, "body": "changed after migration"})
    try:
        migrate(path)
    except ValueError:
        pass
    else:
        raise AssertionError("changed migration should be refused")
    print("PASS SQLite → MySQL migration preserves IDs, raw bytes, deletion, credentials and state; safe rerun/conflict")
    return new, MySQLMailStore


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql", action="store_true")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="yamail-check-") as directory:
        path = Path(directory) / "synthetic.db"
        if args.mysql:
            store, reopen = verify_migration(path)
        else:
            store, reopen = MailStore(path), lambda: MailStore(path)
        verify(store, reopen)
    print("PASS SMTP/POP3, incremental UIDL, account isolation, read/unread and draft restart")


if __name__ == "__main__":
    main()
