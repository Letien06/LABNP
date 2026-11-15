"""Initialize a dedicated Ya-mail schema on the local MySQL instance."""
from __future__ import annotations

import base64
import json
import os
import sqlite3
import sys
from contextlib import closing
from uuid import uuid4

from mail_service import DEFAULT_DB, MailStorageError
from migrate_mysql import migrate
from mysql_store import MySQLMailStore


HOST = "127.0.0.1"
DATABASE = "lab3_yamail"
APP_USER = "lab3_mail"
APP_ACCOUNT = "'lab3_mail'@'127.0.0.1'"


def backup_sqlite(source_path):
    backup_dir = source_path.parent / ".local" / "backup"
    with closing(sqlite3.connect(source_path.resolve().as_uri() + "?mode=ro", uri=True)) as source:
        backup_dir.mkdir(parents=True, exist_ok=True)
        backup_path = backup_dir / f"mail-{uuid4().hex}.db"
        with closing(sqlite3.connect(backup_path)) as destination:
            source.backup(destination)
    print(f"SQLite backup saved to .local/backup/{backup_path.name}.")
    return backup_path


def setup(credentials, connector):
    options = {
        "host": HOST,
        "port": credentials["port"],
        "connection_timeout": 10,
        "charset": "utf8mb4",
    }
    with closing(connector.connect(
        **options, user=credentials["admin_user"], password=credentials["admin_password"],
    )) as admin:
        with closing(admin.cursor(buffered=True)) as cursor:
            # An existing account keeps its password and authentication plugin.
            cursor.execute(f"CREATE USER IF NOT EXISTS {APP_ACCOUNT} IDENTIFIED BY %s",
                           (credentials["app_password"],))
            try:
                with closing(connector.connect(
                    **options, user=APP_USER, password=credentials["app_password"],
                )) as app:
                    with closing(app.cursor()) as app_cursor:
                        app_cursor.execute("SELECT CURRENT_USER()")
                        if app_cursor.fetchone()[0] != f"{APP_USER}@{HOST}":
                            raise ValueError("MySQL đã chọn một tài khoản khác với lab3_mail@127.0.0.1.")
            except connector.Error:
                raise ValueError("Không thể đăng nhập bằng tài khoản lab3_mail. Nếu tài khoản đã tồn tại, hãy dùng đúng mật khẩu hiện tại.") from None
            cursor.execute(
                f"CREATE DATABASE IF NOT EXISTS `{DATABASE}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"
            )
            cursor.execute(
                f"GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES ON `{DATABASE}`.* TO {APP_ACCOUNT}"
            )
    os.environ.update(
        LAB3_MYSQL_HOST=HOST,
        LAB3_MYSQL_PORT=str(options["port"]),
        LAB3_MYSQL_DATABASE=DATABASE,
        LAB3_MYSQL_USER=APP_USER,
        LAB3_MYSQL_PASSWORD=credentials["app_password"],
    )
    if credentials["migrate"]:
        users, messages, imported = migrate(backup_sqlite(DEFAULT_DB))
        status = "Đã nhập" if imported else "Bản chụp đã được nhập trước đó"
        print(f"{status} ({users} tài khoản, {messages} thư).")
    else:
        MySQLMailStore(seed_demo=False)


def main() -> int:
    try:
        import mysql.connector as connector
    except ImportError:
        print("Install the project dependencies first with python -m pip install -r requirements-mysql.txt.", file=sys.stderr)
        return 1
    try:
        credentials = json.loads(base64.b64decode(sys.stdin.buffer.readline(), validate=False))
    except (ValueError, UnicodeError):
        print("Run this setup through setup-mysql.ps1.", file=sys.stderr)
        return 1
    try:
        setup(credentials, connector)
    except (connector.Error, MailStorageError):
        print("MySQL setup failed. Check that the local service is running and the administrator can create schemas and grant privileges.", file=sys.stderr)
        return 1
    except ValueError as exc:
        print(exc, file=sys.stderr)
        return 1
    except (OSError, KeyError, TypeError, sqlite3.Error):
        print("MySQL setup could not read its configuration or SQLite source.", file=sys.stderr)
        return 1
    print("MySQL setup verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
