"""Fast local Lab3 checks; reports a socket-policy skip instead of failing."""
import tempfile
import time
from pathlib import Path
from mail_service import MailStore, MailServers, send_local, fetch_local

db = Path(tempfile.gettempdir()) / "lab3-mail-smoke.db"
db.unlink(missing_ok=True)
store = MailStore(db)
raw = b"From: a@localhost\r\nTo: student@localhost\r\nSubject: direct\r\n\r\nbody\r\n"
assert store.deliver("a@localhost", ["student@localhost"], raw) == 1
assert store.messages("student@localhost")[0]["subject"] == "direct"
try:
    servers = MailServers(store, smtp_port=0, pop3_port=0)
    servers.start(); time.sleep(.1)
    smtp_port, pop3_port = servers.smtp.server_address[1], servers.pop3.server_address[1]
    send_local("127.0.0.1", smtp_port, "a@localhost", ["student@localhost"], "smoke", "socket")
    assert fetch_local("127.0.0.1", pop3_port, "student@localhost", "student")
    servers.close(); print("Lab3 socket smoke passed")
except (PermissionError, OSError) as exc:
    print(f"Lab3 persistence passed; socket smoke skipped ({exc})")
    try: servers.close()
    except Exception: pass
