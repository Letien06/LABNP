# Lab 3 – Mail (SMTP + POP3)

The Java desktop client now sends through SMTP and reads/deletes through POP3.
`mail_service.py` runs both protocols on localhost and persists users, messages,
read status and drafts in MySQL or SQLite. `mail_bridge.py` connects the Swing interface to Python's
standard SMTP/POP3/MIME libraries. The original Java/UDP implementation is retained
as an optional legacy mode.

## Dùng MySQL với Workbench

1. Bật MySQL Server. Tại thư mục `Lab3-Mail`, cài thư viện kết nối:

   ```powershell
   python -m pip install -r requirements-mysql.txt
   ```

2. Đóng Ya-mail để ngừng ghi vào SQLite, rồi chạy:

   ```powershell
   .\setup-mysql.ps1 -MigrateSqlite
   ```

   Nhập mật khẩu quản trị MySQL (mặc định `root`) và chọn mật khẩu riêng cho
   tài khoản `lab3_mail`. Nhập trực tiếp vào hai ô được che ký tự. Nếu tài
   khoản đã tồn tại, nhập mật khẩu hiện tại của nó. Dùng `-AdminUser` hoặc
   `-Port` nếu MySQL của bạn khác mặc định.
   Bỏ `-MigrateSqlite` nếu tạo database mới trên máy chưa có `mail.db`.
3. Mở MySQL Workbench, tạo kết nối **Standard TCP/IP** với host `127.0.0.1`,
   port `3306` và username `lab3_mail`. Chọn schema `lab3_yamail`, mở
   **Tables**. Bảng `messages` chứa thư, `users` chứa tài khoản,
   `mailbox_read` chứa dấu đã đọc và `drafts` chứa thư nháp.
4. Chạy `.\run-client.ps1`. Khi dòng khởi động ghi `Ya-mail storage: MySql`,
   đăng nhập bằng tài khoản Ya-mail. Gửi một thư thử rồi tải lại bảng
   `messages` trong Workbench để thấy dòng mới. Khi trình bày, có thể dùng
   truy vấn chỉ lấy các cột cần xem:

   ```sql
   SELECT email, display_name, created_at FROM lab3_yamail.users;
   SELECT id, sender, recipient, subject, created_at, deleted
   FROM lab3_yamail.messages ORDER BY id DESC LIMIT 20;
   ```

Setup sao lưu SQLite vào `.local/backup/` bằng SQLite Backup API, bao gồm
các thay đổi đã commit trong WAL. Chuyển dữ liệu trên bản sao, giữ ID thư
POP3, mật khẩu, thư gốc và trạng thái. Chạy lại với cùng snapshot sẽ bỏ qua
phần nhập. Nếu đích đã có dữ liệu khác, công cụ dừng để tránh ghi đè.
Cấu hình ứng dụng nằm trong `.local/`; mật khẩu `lab3_mail` được Windows
mã hóa cho người dùng hiện tại. Tài khoản MySQL riêng với tài khoản Ya-mail.

## Open the desktop application

Run `./run-client.ps1`. It compiles the Java client, starts the local server,
and opens Ya-mail. Select **Đăng ký Ya-mail**, enter a display name, account name
and password, then sign in. A name such as `minh` becomes `minh@yamail.local`.
The demo account `student@localhost` / `student` is created on a fresh local database.
Use `-DatabaseBackend Sqlite` to open the existing local SQLite database
explicitly. After MySQL setup, the launcher selects MySQL automatically.
The server stops
when the application closes. JDK 23+, Python 3.12+, and the Maven dependencies
listed in `client/pom.xml` are required. On a fresh machine, install Maven and
run the setup commands shown by the launcher if dependencies are missing.

Use `./run-client.ps1 -NoServer` for an already running local server, or
`./run-client.ps1 -Gmail` for Gmail's TLS endpoints. The Gmail login password
field takes an app password. Earlier local sent copies for external messages
were checked in SQLite. This release's tests use local accounts and do not
verify external receipt or direct Gmail POP3 retrieval.

Local GUI send, inbox/read, server-persisted Sent copies, forwarding, links,
search, and deletion are available. Sent copies use a separate ordinary SMTP
delivery to the sender's mailbox with an `X-Lab3-Folder: sent` header. Gmail's
Sent folder is not exposed by POP3. For Ya-mail, the account service saves read
status and drafts in the same database as SMTP/POP3. **Làm mới** runs in the
background. The client checks UIDL and retrieves only uncached messages while
the account is open. Choose **Thư nháp** in the sidebar to continue a saved
draft. Search by subject, sender, recipient, or message body. Press Ctrl+N
to compose or Ctrl+F to focus search.

## Send from Ya-mail to an external address

Start Ya-mail with `./run-client.ps1` and sign in with a Ya-mail account. Click
your avatar in the top right and choose **Gmail gửi đi**. Enter the Gmail address
and Google App Password, then click **Lưu kết nối**. Ya-mail will use the linked
Gmail address as the sender for external mail. Open **Gmail gửi đi** again to
see or change the sender. The server keeps the relay settings for the current session.

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

Run `.\test.ps1` (or `test.cmd`) for UIDL cache tests, real local SMTP/POP3,
and restart tests for read status and drafts on a temporary SQLite database.
For the MySQL integration test, set `LAB3_MYSQL_HOST`, `LAB3_MYSQL_PORT`,
`LAB3_MYSQL_USER`, `LAB3_MYSQL_PASSWORD` and `LAB3_MYSQL_DATABASE`, then
run `.\test.ps1 -MySql`. The database name must start with `lab3_test_` and
the schema must be empty. Tests use example accounts and localhost mail.

## Existing project

The inherited UDP server is retained under `server/` for comparison with the
original assignment. To select it, launch `MailClientApplication` with the JVM
property `-Dlab3.legacyUdp=true`. Its MySQL schema is retained as well.

### Modern Java desktop mail client

The Java client uses a Gmail-inspired shell: Ya-mail branding and search, an
inbox/sent sidebar with a compose button, a selected-message reader, and a
compose window with aligned fields and a dedicated send action.

Build and launch the `client` Maven module from an IDE or Maven installation. It uses the dependencies declared in `client/pom.xml`.
