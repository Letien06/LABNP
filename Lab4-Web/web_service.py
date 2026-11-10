"""HTTP server and client used by Lab 4.

The original JavaFX browser is kept in ``src/``.  This companion module is a
small, runnable reference implementation that makes each required HTTP
operation explicit and testable from a terminal: GET, HEAD, POST, history and
safe file download.  The server exposes several service types (HTML, JSON,
and file download) so it is useful for packet captures and demonstrations.
"""
from __future__ import annotations

import argparse
import html
import http.client
import json
import mimetypes
import os
import posixpath
import shutil
import threading
import urllib.parse
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ROOT = Path(__file__).resolve().parent
PUBLIC = ROOT / "public"
DOWNLOADS = ROOT / "downloads"
HISTORY = ROOT / "history.json"
PUBLIC.mkdir(exist_ok=True)
DOWNLOADS.mkdir(exist_ok=True)
SAMPLE = PUBLIC / "sample.txt"
if not SAMPLE.exists():
    SAMPLE.write_text("Lab4-Web download sample\n", encoding="utf-8")


def json_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, indent=2).encode("utf-8")


class LabRequestHandler(BaseHTTPRequestHandler):
    server_version = "Lab4Web/1.0"

    def log_message(self, fmt: str, *args) -> None:
        # Keep the standard access log useful while making it easy to redirect
        # in an automated test.
        print(f"[{self.log_date_time_string()}] {self.address_string()} {fmt % args}")

    def _write(self, status: int, body: bytes = b"", content_type: str = "text/plain; charset=utf-8", extra: dict[str, str] | None = None, head: bool = False) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        if extra:
            for key, value in extra.items(): self.send_header(key, value)
        self.end_headers()
        if not head:
            self.wfile.write(body)

    def _route(self) -> tuple[str, dict[str, list[str]]]:
        parsed = urllib.parse.urlsplit(self.path)
        return parsed.path, urllib.parse.parse_qs(parsed.query)

    def _read_body(self) -> bytes:
        try: length = int(self.headers.get("Content-Length", "0"))
        except ValueError: length = 0
        if length < 0 or length > 2 * 1024 * 1024:
            raise ValueError("request body is too large")
        return self.rfile.read(length)

    def _json(self, value: object, status: int = 200, head: bool = False) -> None:
        self._write(status, json_bytes(value), "application/json; charset=utf-8", head=head)

    def do_GET(self) -> None:
        self._dispatch("GET")

    def do_HEAD(self) -> None:
        self._dispatch("HEAD")

    def do_POST(self) -> None:
        try:
            body = self._read_body()
        except ValueError as exc:
            self._json({"error": str(exc)}, 413)
            return
        self._dispatch("POST", body)

    def _dispatch(self, method: str, body: bytes = b"") -> None:
        path, query = self._route(); head = method == "HEAD"
        if method == "POST":
            if path == "/api/echo":
                content_type = self.headers.get("Content-Type", "")
                if "application/json" in content_type:
                    try: payload = json.loads(body.decode("utf-8")) if body else None
                    except json.JSONDecodeError: self._json({"error": "invalid JSON"}, 400); return
                elif "application/x-www-form-urlencoded" in content_type:
                    payload = urllib.parse.parse_qs(body.decode("utf-8"))
                else:
                    payload = body.decode("utf-8", "replace")
                self._json({"service": "echo", "method": method, "payload": payload, "bytes": len(body)})
                return
            if path == "/api/upload":
                name = query.get("name", ["upload.bin"])[0]
                safe = Path(name).name or "upload.bin"
                destination = DOWNLOADS / safe; destination.write_bytes(body)
                self._json({"service": "upload", "name": safe, "bytes": len(body), "download": f"/download/{urllib.parse.quote(safe)}"}, 201)
                return
            self._json({"error": "POST route not found"}, 404); return

        if path in ("/", "/index.html"):
            page = """<!doctype html><html><head><meta charset='utf-8'><title>Lab4 Web</title></head>
            <body><h1>Lab4 Web services</h1><ul>
            <li><a href='/api/time'>JSON time service</a></li>
            <li><a href='/api/echo?message=hello'>JSON echo service</a></li>
            <li><a href='/download/sample.txt'>Download sample</a></li>
            </ul><p>Use the client to send GET, HEAD and POST requests.</p></body></html>""".encode()
            self._write(200, page, "text/html; charset=utf-8", head=head); return
        if path == "/api/time":
            self._json({"service": "time", "utc": datetime.now(timezone.utc).isoformat()}, head=head); return
        if path == "/api/echo":
            self._json({"service": "echo", "method": method, "query": query}, head=head); return
        if path.startswith("/download/"):
            name = Path(urllib.parse.unquote(path.removeprefix("/download/"))).name
            target = (DOWNLOADS / name) if (DOWNLOADS / name).exists() else (PUBLIC / name)
            if not target.is_file(): self._json({"error": "file not found"}, 404, head=head); return
            payload = target.read_bytes(); content_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
            self._write(200, payload, content_type, {"Content-Disposition": f'attachment; filename="{target.name}"'}, head=head); return
        self._json({"error": "route not found", "path": path}, 404, head=head)


class LabWebServer:
    def __init__(self, host: str = "127.0.0.1", port: int = 8080):
        self.httpd = ThreadingHTTPServer((host, port), LabRequestHandler)
        self.thread: threading.Thread | None = None

    @property
    def address(self): return self.httpd.server_address

    def start(self) -> None:
        self.thread = threading.Thread(target=self.httpd.serve_forever, name="lab4-web", daemon=True); self.thread.start()

    def close(self) -> None:
        self.httpd.shutdown(); self.httpd.server_close()


class WebClient:
    def __init__(self, history_file: str | os.PathLike[str] = HISTORY):
        self.history_file = Path(history_file)

    def _record(self, method: str, url: str, status: int, size: int) -> None:
        records = []
        if self.history_file.exists():
            try: records = json.loads(self.history_file.read_text(encoding="utf-8"))
            except (ValueError, OSError): records = []
        records.append({"time": datetime.now(timezone.utc).isoformat(timespec="seconds"), "method": method, "url": url, "status": status, "bytes": size})
        self.history_file.write_text(json.dumps(records[-100:], indent=2), encoding="utf-8")

    def request(self, method: str, url: str, data: bytes | str | dict | None = None, headers: dict[str, str] | None = None) -> tuple[int, dict[str, str], bytes]:
        parsed = urllib.parse.urlsplit(url if "://" in url else "http://" + url)
        if parsed.scheme not in ("http",): raise ValueError("only HTTP URLs are supported by this lab client")
        conn = http.client.HTTPConnection(parsed.hostname, parsed.port or 80, timeout=10)
        payload = None
        request_headers = {"User-Agent": "Lab4-WebClient/1.0"}; request_headers.update(headers or {})
        if data is not None:
            if isinstance(data, dict):
                payload = json.dumps(data).encode(); request_headers.setdefault("Content-Type", "application/json")
            elif isinstance(data, str): payload = data.encode()
            else: payload = data
            request_headers["Content-Length"] = str(len(payload))
        path = urllib.parse.urlunsplit(("", "", parsed.path or "/", parsed.query, ""))
        conn.request(method.upper(), path, body=payload, headers=request_headers)
        response = conn.getresponse(); result = response.read(); status = response.status; response_headers = dict(response.getheaders()); conn.close()
        self._record(method.upper(), url, status, len(result)); return status, response_headers, result

    def get(self, url: str): return self.request("GET", url)
    def head(self, url: str): return self.request("HEAD", url)
    def post(self, url: str, data): return self.request("POST", url, data)

    def download(self, url: str, destination: str | os.PathLike[str]) -> Path:
        status, _, payload = self.get(url)
        if status != 200: raise RuntimeError(f"download failed: HTTP {status}")
        target = Path(destination); target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(payload); return target

    def history(self) -> list[dict]:
        if not self.history_file.exists(): return []
        return json.loads(self.history_file.read_text(encoding="utf-8"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Lab4 HTTP server/client")
    sub = parser.add_subparsers(dest="command", required=True)
    serve = sub.add_parser("serve"); serve.add_argument("--host", default="127.0.0.1"); serve.add_argument("--port", type=int, default=8080)
    for command in ("get", "head"): p = sub.add_parser(command); p.add_argument("url")
    post = sub.add_parser("post"); post.add_argument("url"); post.add_argument("data", help="JSON object or plain text")
    download = sub.add_parser("download"); download.add_argument("url"); download.add_argument("destination")
    sub.add_parser("history")
    args = parser.parse_args(argv); client = WebClient()
    if args.command == "serve":
        server = LabWebServer(args.host, args.port); server.start(); print(f"Lab4-Web listening on http://{args.host}:{args.port}")
        try: threading.Event().wait()
        except KeyboardInterrupt: server.close()
    elif args.command in ("get", "head"):
        status, headers, body = client.request(args.command.upper(), args.url); print(f"HTTP {status}"); print(json.dumps(headers, indent=2)); print(body.decode("utf-8", "replace"))
    elif args.command == "post":
        try: value = json.loads(args.data)
        except json.JSONDecodeError: value = args.data
        status, _, body = client.post(args.url, value); print(f"HTTP {status}\n{body.decode('utf-8', 'replace')}")
    elif args.command == "download": print(client.download(args.url, args.destination))
    elif args.command == "history": print(json.dumps(client.history(), indent=2))
    return 0


if __name__ == "__main__": raise SystemExit(main())
