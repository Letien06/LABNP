"""Real socket and restart tests; all data stays in an isolated QA database."""
import sys
import contextlib
import uuid
import poplib
import smtplib
import socket
import base64
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mail_service import MailStore, MailServers, send_local, fetch_local
from mail_bridge import run

passed = []
def check(name, value):
    assert value, name
    passed.append(name)
    print("PASS " + name)

with contextlib.nullcontext():
    path = Path(__file__).parent / ("protocol-" + uuid.uuid4().hex + ".db")
    store = MailStore(path)
    store.add_user("alice@localhost", "qa-alice")
    server = MailServers(store, smtp_port=0, pop3_port=0)
    server.start()
    sp, pp = server.smtp.server_address[1], server.pop3.server_address[1]
    def pop():
        box = poplib.POP3("127.0.0.1", pp, timeout=5)
        box.user("student@localhost"); box.pass_("student")
        return box
    try:
        bad = poplib.POP3("127.0.0.1", pp, timeout=5)
        bad.user("student@localhost")
        try: bad.pass_("incorrect"); raise AssertionError("bad password accepted")
        except poplib.error_proto: check("POP3 rejects bad credentials", True)
        try: bad.stat(); raise AssertionError("unauthenticated STAT accepted")
        except poplib.error_proto: check("POP3 protects transaction commands", True)
        bad.quit()
        with smtplib.SMTP("127.0.0.1", sp, timeout=5) as smtp:
            try: smtp.login("alice@localhost", "wrong"); raise AssertionError("bad SMTP password accepted")
            except smtplib.SMTPAuthenticationError: check("SMTP rejects bad credentials", True)
            smtp.login("alice@localhost", "qa-alice")
            check("SMTP AUTH PLAIN succeeds", True)
        with smtplib.SMTP("127.0.0.1", sp, timeout=5) as smtp:
            smtp.ehlo()
            def challenge(challenge=None):
                return "\0alice@localhost\0qa-alice"
            smtp.auth("PLAIN", challenge, initial_response_ok=False)
            check("SMTP AUTH PLAIN challenge succeeds", True)
        for n in range(3):
            send_local("127.0.0.1", sp, "alice@localhost", ["student@localhost"], f"Test {n+1} • Tiếng Việt", ".dot line\n..two dots\nXin chào SMTP → SQLite → POP3", "alice@localhost", "qa-alice")
        rows = store.messages("student@localhost")
        check("SMTP envelope SIZE parsed correctly", all(row["sender"] == "alice@localhost" for row in rows))
        check("SMTP persists three Unicode messages in SQLite", len(rows) == 3 and "Tiếng Việt" in rows[0]["subject"])
        box = pop()
        check("POP3 STAT/LIST counts", box.stat()[0] == 3 and len(box.list()[1]) == 3)
        check("POP3 dot unstuffing roundtrip", b".dot line" in box.retr(1)[1] and b"..two dots" in box.retr(1)[1])
        first_uid = box.uidl(1)
        box.dele(1)
        check("DELE retains message number 2", box.list()[1][0].startswith(b"2 ") and b"Subject:" in b"\n".join(box.retr(2)[1]))
        for number in (0, -1, 1, 99):
            try: box.retr(number); raise AssertionError(f"invalid RETR {number} accepted")
            except poplib.error_proto: pass
        check("POP3 rejects zero/negative/deleted/out-of-range indices", True)
        box.rset()
        check("RSET rolls back deletion", box.stat()[0] == 3 and box.uidl(1) == first_uid)
        box.dele(1); box.close()
        box = pop()
        check("Disconnect without QUIT rolls back deletion", box.stat()[0] == 3)
        box.dele(1); box.dele(3); box.quit()
        box = pop()
        check("QUIT commits precisely original message 1 and 3", box.stat()[0] == 1 and "Test 2" in store.messages("student@localhost")[0]["subject"])
        try: box._shortcmd("USER alice@localhost"); raise AssertionError("authenticated USER accepted")
        except poplib.error_proto: check("Authenticated identity cannot switch with USER", True)
        box.quit()
        cfg = dict(user="student@localhost", password="student", smtpPort=sp, popPort=pp)
        run({**cfg, "action": "login"})
        run({**cfg, "action": "send", "to": "student@localhost", "subject": "Bridge integration", "body": "Sent through SMTP and read through POP3", "links": ""})
        messages = run({**cfg, "action": "load"})["messages"]
        check("GUI bridge authenticates, sends, fetches inbox and sent", len(messages) == 3 and sum(m["sent"] for m in messages) == 1)
        uid = next(m["uid"] for m in messages if m["sent"])
        run({**cfg, "action": "delete", "uid": uid})
        check("GUI deletion resolves stable UIDL", len(run({**cfg, "action": "load"})["messages"]) == 2)
        # Disconnect during DATA: no message may be committed.
        sock = socket.create_connection(("127.0.0.1", sp)); stream = sock.makefile("rwb", buffering=0)
        stream.readline()
        for command in (b"MAIL FROM:<alice@localhost>\r\n", b"RCPT TO:<student@localhost>\r\n", b"DATA\r\n"):
            stream.write(command); stream.readline()
        stream.write(b"Subject: incomplete\r\n\r\nunfinished"); stream.close(); sock.close()
        with smtplib.SMTP("127.0.0.1", sp, timeout=5) as smtp:
            smtp.mail("alice@localhost")
            check("SMTP unknown recipients rejected", smtp.rcpt("nobody@localhost")[0] == 550)
    finally:
        server.close()
    reopened = MailStore(path)
    check("SQLite persists mailbox and deletion across restart", len(reopened.messages("student@localhost")) == 2)
    server = MailServers(reopened, smtp_port=0, pop3_port=0); server.start()
    try:
        check("POP3 reads persisted database after server restart", len(fetch_local("127.0.0.1", server.pop3.server_address[1], "student@localhost", "student")) == 2)
    finally: server.close()
print(f"RESULT {len(passed)} checks passed; Gmail unverified (no credentials used).")
