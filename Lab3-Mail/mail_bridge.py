"""JSON stdin/stdout bridge: the Swing client uses real SMTP and POP3 sockets.

Credentials travel through stdin, never command arguments or diagnostic output.
The local Sent folder consists of ordinary SMTP copies in the sender's mailbox.
"""
import base64
import email.policy
import html
import http.client
import json
import os
import poplib
import smtplib
import ssl
import sys
from email.message import EmailMessage
from email.parser import BytesParser
from email.utils import formatdate, make_msgid


LOCAL_SMTP_ERRORS = {
    b"5.7.1 External relay is not configured; ask the server administrator":
        "Máy chủ Ya-mail chưa cấu hình Gmail chuyển tiếp. Mở Kết nối Gmail trong cửa sổ soạn thư để cấu hình Gmail gửi đi.",
    b"5.7.1 External relay is not configured":
        "Máy chủ Ya-mail chưa cấu hình Gmail chuyển tiếp. Mở Kết nối Gmail trong cửa sổ soạn thư để cấu hình Gmail gửi đi.",
    b"5.7.8 Gmail relay authentication failed; check its app password":
        "Gmail từ chối đăng nhập cho máy chủ chuyển tiếp. Hãy nhờ quản trị viên kiểm tra mật khẩu ứng dụng Gmail.",
    b"4.4.2 Gmail relay submission unconfirmed":
        "Mất kết nối với Gmail; chưa xác nhận được Gmail đã nhận thư hay chưa. Kiểm tra trước khi gửi lại.",
}


class LocalSMTP(smtplib.SMTP):
    data_reply = b""

    def data(self, message):
        code, reply = super().data(message)
        self.data_reply = reply
        return code, reply

    def login(self, user, password):
        self.ehlo_or_helo_if_needed()
        payload = base64.b64encode(f"\0{user}\0{password}".encode("utf-8")).decode("ascii")
        code, reply = self.docmd("AUTH", "PLAIN " + payload)
        if code not in (235, 503):
            raise smtplib.SMTPAuthenticationError(code, reply)
        return code, reply


def local_address(value):
    address = str(value).strip().lower()
    return address if "@" in address else address + "@yamail.local"


def account_request(data, path, payload=None, admin=False):
    """Account administration stays on loopback and never uses a web proxy."""
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if admin:
        token = os.environ.get("YAMAIL_ADMIN_TOKEN", "")
        if not token:
            raise ValueError("Chưa có quyền cấu hình SMTP relay trên máy chủ (YAMAIL_ADMIN_TOKEN).")
        headers["X-YaMail-Admin-Token"] = token
    connection = http.client.HTTPConnection("127.0.0.1", int(data.get("accountPort", 8033)), timeout=30 if admin else 10)
    try:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        connection.request("POST" if payload is not None else "GET", path, body, headers)
        response = connection.getresponse()
        raw = response.read(65537)
        if len(raw) > 65536:
            raise ValueError("Phản hồi từ máy chủ tài khoản quá lớn.")
        try:
            result = json.loads(raw.decode("utf-8"))
        except (UnicodeError, json.JSONDecodeError):
            raise ValueError("Máy chủ tài khoản trả về dữ liệu không hợp lệ.") from None
        if not isinstance(result, dict):
            raise ValueError("Máy chủ tài khoản trả về dữ liệu không hợp lệ.")
        if not 200 <= response.status < 300 or result.get("ok") is not True:
            raise ValueError(safe_error(ValueError(str(result.get("error", "Yêu cầu tài khoản không thành công."))), data))
        return result
    except (OSError, http.client.HTTPException):
        raise ValueError("Không kết nối được máy chủ tài khoản Ya-mail. Hãy khởi động máy chủ rồi thử lại.") from None
    finally:
        connection.close()


def safe_error(exc, data):
    """Never publish authentication payloads or untrusted SMTP response text."""
    local_error = None
    if data.get("provider") != "gmail":
        if isinstance(exc, smtplib.SMTPResponseException):
            local_error = LOCAL_SMTP_ERRORS.get(exc.smtp_error)
        elif isinstance(exc, smtplib.SMTPRecipientsRefused):
            local_error = next((LOCAL_SMTP_ERRORS[reply] for _, reply in exc.recipients.values()
                                if reply in LOCAL_SMTP_ERRORS), None)
    if isinstance(exc, smtplib.SMTPAuthenticationError):
        message = "Đăng nhập SMTP thất bại. Hãy kiểm tra tài khoản và mật khẩu."
    elif isinstance(exc, smtplib.SMTPResponseException):
        message = "Máy chủ SMTP từ chối yêu cầu (mã %s)." % exc.smtp_code
    elif isinstance(exc, smtplib.SMTPRecipientsRefused):
        message = "Máy chủ SMTP từ chối tất cả người nhận; thư chưa được chấp nhận."
    elif isinstance(exc, poplib.error_proto):
        message = "Máy chủ POP3 từ chối yêu cầu. Hãy kiểm tra tài khoản và mật khẩu."
    else:
        message = str(exc)
    message = local_error or message
    for secret in (data.get("password"), data.get("relayPassword"), data.get("adminToken"), os.environ.get("YAMAIL_ADMIN_TOKEN")):
        if secret:
            message = message.replace(str(secret), "[ẩn]")
    return message


def refused_rows(refused):
    # Server response text can echo credentials; only expose address and code.
    return [{"email": str(address).replace("\r", "").replace("\n", ""), "code": int(status[0])}
            for address, status in refused.items()]


def run(data):
    gmail = data.get("provider") == "gmail"
    action = data["action"]
    if action == "register":
        if gmail:
            raise ValueError("Ya-mail không tạo tài khoản Google. Hãy đăng ký Gmail tại Google hoặc chọn tài khoản Ya-mail.")
        address = local_address(data.get("email", data.get("user", "")))
        result = account_request(data, "/register", {"name": data.get("name", ""), "email": address, "password": data.get("password", "")})
        return {"user": result["email"], "email": result["email"], "name": result.get("name", "")}
    if action in ("relay_status", "configure_relay"):
        if action == "configure_relay":
            result = account_request(data, "/relay", {"user": data.get("relayUser", ""), "password": data.get("relayPassword", "")}, admin=True)
        else:
            result = account_request(data, "/relay")
        return {key: result[key] for key in ("configured", "sender", "host", "port") if key in result}
    user = str(data["user"]).strip() if gmail else local_address(data["user"])
    password = data["password"]
    host = data.get("host", "127.0.0.1")

    def pop():
        box = (poplib.POP3_SSL("pop.gmail.com", 995, timeout=20, context=ssl.create_default_context())
               if gmail else poplib.POP3(host, int(data.get("popPort", 8110)), timeout=10))
        try:
            box.user(user)
            box.pass_(password)
            return box
        except Exception:
            box.close()
            raise

    if action == "login":
        box = pop()
        box.quit()
        return {"user": user, "email": user}
    if action in ("load", "delete"):
        box = pop()
        try:
            _, lines, _ = box.uidl()
            rows = []
            for line in lines:
                number, uid = line.decode().split()
                if action == "delete":
                    if uid == data["uid"]:
                        box.dele(int(number))
                        box.quit()
                        box = None
                        return {}
                    continue
                _, body, _ = box.retr(int(number))
                msg = BytesParser(policy=email.policy.default).parsebytes(b"\r\n".join(body))
                part = msg.get_body(preferencelist=("plain", "html"))
                text = str(part.get_content()) if part else ""
                # Render plain text safely in the existing Swing HTML reader.
                if part and part.get_content_type() == "text/html":
                    import re
                    text = html.unescape(re.sub("<[^>]*>", "", text))
                rows.append(dict(id=int(number), uid=uid, **{"from": str(msg.get("From", "")),
                    "to": str(msg.get("To", "")), "subject": html.escape(str(msg.get("Subject", ""))),
                    "body": html.escape(text).replace("\n", "<br/>"), "links": "", "contentHtml": "",
                    "seen": False, "createdAt": str(msg.get("Date", "")), "transferFrom": "",
                    "sent": msg.get("X-Lab3-Folder", "") == "sent"}))
            if action == "delete":
                raise ValueError("Message is no longer in the mailbox; refresh the inbox.")
            return {"messages": rows}
        finally:
            if box:
                box.quit()
    if action == "send":
        recipients = [x.strip() for x in data["to"].split(",") if x.strip()]
        if not recipients or any("@" not in x or "\n" in x or "\r" in x for x in recipients):
            raise ValueError("Enter complete recipient addresses, separated by commas.")
        msg = EmailMessage()
        msg["From"], msg["To"], msg["Subject"] = user, ", ".join(recipients), data["subject"]
        msg["Date"], msg["Message-ID"] = formatdate(localtime=True), make_msgid()
        text = data["body"]
        if data.get("links"):
            text += "\n\nAttached links:\n" + data["links"]
        msg.set_content(text)
        smtp = (smtplib.SMTP_SSL("smtp.gmail.com", 465, context=ssl.create_default_context(), timeout=20)
                if gmail else LocalSMTP(host, int(data.get("smtpPort", 2525)), timeout=55))
        accepted = []
        refused = {}
        warnings = []
        archived = None if gmail else False
        relay_accepted = None
        try:
            smtp.login(user, password)
            try:
                refused = smtp.send_message(msg, from_addr=user, to_addrs=recipients)
            except smtplib.SMTPRecipientsRefused as exc:
                return {"ok": False, "submitted": False, "error": safe_error(exc, data), "refusedRecipients": refused_rows(exc.recipients)}
            except smtplib.SMTPResponseException:
                # SMTPResponseException also inherits OSError, but this reply
                # gives us a definite status from the server.
                raise
            except (smtplib.SMTPServerDisconnected, OSError):
                return {"ok": False, "submitted": False, "submissionUnknown": True,
                        "error": "Mất kết nối khi gửi; chưa xác nhận được máy chủ đã nhận thư hay chưa. Kiểm tra hộp thư trước khi gửi lại."}
            if not gmail:
                reply, _, partial = smtp.data_reply.partition(b"; yamail-refused=")
                relay_accepted = True if b"; relay accepted " in reply else None
                if partial:
                    delivery_refusals = json.loads(partial)
                    refused.update({address: (delivery_refusals[address.lower()], b"")
                                    for address in recipients if address.lower() in delivery_refusals})
            accepted = [address for address in recipients if address not in refused]
            if refused:
                warnings.append("Máy chủ đã chấp nhận thư cho một số người nhận; các địa chỉ bị từ chối chưa được gửi. Chỉ gửi lại cho những địa chỉ bị từ chối.")
                for detail in dict.fromkeys(LOCAL_SMTP_ERRORS[reply] for _, reply in refused.values()
                                            if not gmail and reply in LOCAL_SMTP_ERRORS):
                    warnings.append(detail)
            if not gmail:
                msg["X-Lab3-Folder"] = "sent"
                try:
                    archive_refused = smtp.send_message(msg, from_addr=user, to_addrs=[user])
                    archived = not archive_refused
                except Exception:
                    archived = False
                if not archived:
                    warnings.append("Máy chủ đã chấp nhận thư, nhưng chưa lưu được bản sao vào Đã gửi. Không gửi lại toàn bộ thư.")
        finally:
            try:
                smtp.quit()
            except Exception:
                smtp.close()
                if accepted:
                    warnings.append("Máy chủ đã chấp nhận thư trước khi kết nối đóng. Không cần gửi lại.")
        result = {"submitted": True, "acceptedRecipients": accepted, "refusedRecipients": refused_rows(refused),
                  "sentArchived": archived, "relayAccepted": relay_accepted}
        if warnings:
            result["warning"] = " ".join(warnings)
        return result
    raise ValueError("Unsupported mail action")


if __name__ == "__main__":
    data = {}
    try:
        data = json.loads(sys.stdin.buffer.read().decode("utf-8"))
        result = run(data)
        print(json.dumps({"ok": True, **result}, ensure_ascii=True))
    except Exception as exc:
        # Never echo input/credentials; protocol errors only contain server status.
        print(json.dumps({"ok": False, "error": safe_error(exc, data if isinstance(data, dict) else {})}, ensure_ascii=True))
