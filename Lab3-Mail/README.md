# Lab 3 – Mail (SMTP + POP3)

The Java desktop client now sends through SMTP and reads/deletes through POP3.
`mail_service.py` runs both protocols on localhost and persists users and messages
in SQLite (`mail.db`). `mail_bridge.py` connects the Swing interface to Python's
standard SMTP/POP3/MIME libraries. The original Java/UDP implementation is retained
as an optional legacy mode.

## Open the desktop application

Run `./run-client.ps1`. It compiles the Java client, starts the local server,
and opens Ya-mail. Select **Đăng ký Ya-mail**, enter a display name, account name
and password, then sign in. A name such as `minh` becomes `minh@yamail.local`.
The demo account `student@localhost` / `student` still works. The server stops
when the application closes. JDK 23+, Python 3.12+, and the Maven dependencies
listed in `client/pom.xml` are required. On a fresh machine, install Maven and
run the setup commands shown by the launcher if dependencies are missing.

Use `./run-client.ps1 -NoServer` for an already running local server, or
`./run-client.ps1 -Gmail` for Gmail's TLS endpoints. The Gmail login password
field takes an app password. Local sent copies for external messages have been
checked in SQLite; receipt at the external mailbox and direct Gmail POP3
retrieval have not been independently verified.

Local GUI send, inbox/read, server-persisted Sent copies, forwarding, links,
search, and deletion are available. Sent copies use a separate ordinary SMTP
delivery to the sender's mailbox with an `X-Lab3-Folder: sent` header. Gmail's
Sent folder is not exposed by POP3. Read flags currently last for the client
session. The loopback account service saves new accounts in the same SQLite
database that SMTP and POP3 use. The `user` command below can provision accounts.

## Send from Ya-mail to an external address

Start Ya-mail with `./run-client.ps1` and sign in with a Ya-mail account. Open
the compose window and select **Kết nối Gmail** to enter the Gmail address
that will send outbound mail and its Google App Password. After saving, you can
send the draft to an external recipient. The recipient will see the Gmail account
as the sender. Use **Đổi Gmail gửi đi** to change the Gmail account. The server
keeps the relay settings for the current session.

You can also configure the relay from PowerShell when starting Ya-mail:

```powershell
.\run-client.ps1 -ConfigureGmailRelay
```

The launcher prompts for the Gmail address and masks the password as you type.
With no relay configured, the local server delivers to Ya-mail mailboxes only.
Google requires [2-Step Verification for App Passwords](https://support.google.com/accounts/answer/185833).

## Local demonstration

```powershell
python mail_service.py init-db
python mail_service.py user alice@localhost secret
python mail_service.py server --smtp-port 2525 --pop3-port 8110
```

In a second terminal:

```powershell
python mail_service.py send --from alice@localhost --to student@localhost --subject "Test" --body "SMTP -> SQLite -> POP3"
python mail_service.py fetch --user student@localhost --password student
```

The SMTP server accepts `EHLO`, `AUTH LOGIN/PLAIN`, `MAIL FROM`, `RCPT TO`,
`DATA`, `RSET` and `QUIT`.  The POP3 server implements the authorization,
transaction and update phases (`USER`, `PASS`, `STAT`, `LIST`, `RETR`, `DELE`,
`UIDL`, `RSET`, `QUIT`). Message numbers stay fixed during a POP3 transaction.
Messages are only marked deleted after a POP3 `QUIT`, matching POP3
semantics.  The schema includes `users` and `messages` tables and is created on
first start.

## Gmail path

`send_gmail()` and `fetch_gmail()` in `mail_service.py` use Gmail's current
TLS endpoints (`smtp.gmail.com:465` and `pop.gmail.com:995`).  Enable POP in
the Gmail account and create an **app password**; do not put credentials in
source control.  Example from a Python shell:

```python
from mail_service import send_gmail, fetch_gmail
send_gmail("me@gmail.com", os.environ["GMAIL_APP_PASSWORD"], ["me@gmail.com"], "Lab3", "hello")
messages = fetch_gmail("me@gmail.com", os.environ["GMAIL_APP_PASSWORD"])
```

The Gmail functions are deliberately opt-in; normal grading and local tests
use the isolated localhost servers.

## Checks

Run `python .qa/feature_test.py` for the 20 real socket/database checks, including
authentication failures, stable deletion numbers, RSET, rollback on disconnect,
dot-stuffing, UIDL deletion, and persistence across server restart. Generated
screenshots, logs and mailbox databases are excluded from the public repository.

Run `.\test.ps1` (or `test.cmd`).  It compiles the Python modules and runs
SQLite plus SMTP/POP3 smoke checks.  If the host policy forbids listening on a
local TCP socket, the script still verifies database persistence and reports
the socket portion as skipped.

## Existing project

The inherited UDP server is retained under `server/` for comparison with the
original assignment. To select it, launch `MailClientApplication` with the JVM
property `-Dlab3.legacyUdp=true`. Its MySQL schema is retained as well.

### Modern Java desktop mail client

The Java client uses a Gmail-inspired shell: Ya-mail branding and search, an
inbox/sent sidebar with a compose button, a selected-message reader, and a
compose window with aligned fields and a dedicated send action.

Build and launch the `client` Maven module from an IDE or Maven installation. It uses the dependencies declared in `client/pom.xml`.
