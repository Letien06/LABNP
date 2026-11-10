"""Loopback account registration and administrator-only relay configuration."""
from __future__ import annotations

import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class AccountServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, port, store, relay):
        super().__init__(("127.0.0.1", port), AccountHandler)
        self.store = store
        self.relay = relay
        self.admin_token = os.environ.get("YAMAIL_ADMIN_TOKEN", "")


class AccountHandler(BaseHTTPRequestHandler):
    server_version = "YaMailAccounts/1.0"

    def log_message(self, format, *args):
        # Avoid request details or credentials reaching application logs.
        pass

    def reply(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, {"ok": True})
        elif self.path == "/relay":
            self.reply(200, {"ok": True, **self.server.relay.status()})
        else:
            self.reply(404, {"ok": False, "error": "Không tìm thấy chức năng."})

    def do_POST(self):
        if self.path not in ("/register", "/relay"):
            self.reply(404, {"ok": False, "error": "Không tìm thấy chức năng."})
            return
        if self.path == "/relay":
            expected = self.server.admin_token
            supplied = self.headers.get("X-YaMail-Admin-Token", "")
            if not expected or not hmac.compare_digest(supplied.encode(), expected.encode()):
                self.reply(403, {"ok": False, "error": "Chỉ quản trị viên máy chủ được cấu hình Gmail chuyển tiếp."})
                return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 16384:
                raise ValueError("Dữ liệu yêu cầu không hợp lệ hoặc quá lớn.")
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            if not isinstance(payload, dict):
                raise ValueError("Dữ liệu yêu cầu phải là một đối tượng JSON.")
            if self.path == "/register":
                profile = self.server.store.register_user(payload.get("name"), payload.get("email"), payload.get("password"))
                self.reply(201, {"ok": True, **profile})
            else:
                status = self.server.relay.configure(payload.get("user"), payload.get("password"))
                self.reply(200, {"ok": True, **status})
        except (UnicodeError, json.JSONDecodeError):
            self.reply(400, {"ok": False, "error": "Dữ liệu yêu cầu phải là JSON mã hóa UTF-8 hợp lệ."})
        except ValueError as exc:
            self.reply(409 if getattr(exc, "duplicate_account", False) else 400,
                       {"ok": False, "error": str(exc)})
        except Exception:
            self.reply(500, {"ok": False, "error": "Không thể xử lý yêu cầu lúc này."})
