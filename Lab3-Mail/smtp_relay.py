"""Optional Gmail relay. Credentials live only in this process's memory."""
from __future__ import annotations

import email.policy
import os
import re
import smtplib
import ssl
import threading
import time
from email.parser import BytesParser
from email.utils import formataddr


class RelayError(Exception):
    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code


class GmailRelay:
    host = "smtp.gmail.com"
    port = 465

    def __init__(self):
        self._lock = threading.Lock()
        self._user = ""
        self._password = ""
        user = os.environ.get("YAMAIL_RELAY_USER", "")
        password = os.environ.get("YAMAIL_RELAY_PASSWORD", "")
        if user and password:
            self._user, self._password = self._credentials(user, password)

    @staticmethod
    def _credentials(user: str, password: str) -> tuple[str, str]:
        if not isinstance(user, str) or not re.fullmatch(r"[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:gmail|googlemail)\.com", user.strip(), re.I):
            raise ValueError("Địa chỉ máy chuyển tiếp phải là tài khoản Gmail.")
        if not isinstance(password, str) or not password.strip() or len(password) > 1024:
            raise ValueError("Cần nhập mật khẩu ứng dụng Gmail.")
        return user.strip().lower(), password.strip().replace(" ", "")

    def configure(self, user: str, password: str) -> dict:
        user, password = self._credentials(user, password)
        if not all("!" <= character <= "~" for character in password):
            raise ValueError("Mật khẩu ứng dụng Gmail chứa ký tự không hợp lệ. Hãy sao chép lại từ tài khoản Google.")
        deadline = time.monotonic() + 20
        smtp = None
        try:
            smtp = smtplib.SMTP_SSL(self.host, self.port, timeout=5, context=ssl.create_default_context())
            smtp.login(user, password)
        except smtplib.SMTPAuthenticationError as exc:
            if 400 <= exc.smtp_code < 500:
                raise ValueError("Gmail tạm thời từ chối đăng nhập. Hãy thử lại sau.") from None
            raise ValueError("Gmail từ chối đăng nhập. Hãy kiểm tra địa chỉ Gmail và mật khẩu ứng dụng do Google cấp.") from None
        except ssl.SSLError:
            raise ValueError("Không thiết lập được kết nối TLS với Gmail. Hãy kiểm tra chứng chỉ và kết nối mạng của máy chủ.") from None
        except TimeoutError:
            raise ValueError("Gmail phản hồi quá chậm. Hãy kiểm tra kết nối mạng rồi thử lại.") from None
        except (OSError, ValueError):
            raise ValueError("Không kết nối được với SMTP Gmail. Hãy kiểm tra kết nối mạng của máy chủ và cổng 465 rồi thử lại.") from None
        finally:
            if smtp is not None:
                try:
                    smtp.close()
                except OSError:
                    pass
        with self._lock:
            if time.monotonic() >= deadline:
                raise ValueError("Gmail phản hồi quá chậm. Hãy kiểm tra kết nối mạng rồi thử lại.")
            self._user, self._password = user, password
            return self._status()

    def status(self) -> dict:
        with self._lock:
            return self._status()

    def _status(self) -> dict:
        return {"configured": bool(self._user and self._password), "sender": self._user,
                "host": self.host, "port": self.port}

    def send(self, local_user: str, recipients: list[str], raw: bytes) -> dict:
        with self._lock:
            user, password = self._user, self._password
        if not user or not password:
            raise RelayError(550, "5.7.1 External relay is not configured")
        smtp = None
        submitting = False
        try:
            message = BytesParser(policy=email.policy.SMTP).parsebytes(raw)
            # Gmail owns the external envelope and visible sender.
            for header in set(message.keys()):
                if header.lower().startswith("resent-") or header.lower() in (
                    "from", "sender", "reply-to", "return-path", "x-yamail-user", "dkim-signature", "authentication-results"
                ):
                    del message[header]
            message["From"] = formataddr((f"{local_user} qua Ya-mail", user))
            message["Reply-To"] = user
            message["X-YaMail-User"] = local_user
            smtp = smtplib.SMTP_SSL(self.host, self.port, timeout=20,
                                   context=ssl.create_default_context())
            smtp.login(user, password)
            submitting = True
            refused = smtp.send_message(message, from_addr=user, to_addrs=recipients)
            # A lost QUIT reply cannot reverse a successful DATA reply.
            try:
                smtp.quit()
            except (OSError, smtplib.SMTPException):
                pass
            return refused
        except smtplib.SMTPAuthenticationError:
            raise RelayError(550, "5.7.8 Gmail relay authentication failed; check its app password") from None
        except smtplib.SMTPRecipientsRefused as exc:
            if any(400 <= response[0] < 500 for response in exc.recipients.values()):
                raise RelayError(451, "4.1.0 Gmail relay temporarily refused recipients") from None
            raise RelayError(550, "5.1.1 Gmail relay refused recipients") from None
        except smtplib.SMTPResponseException as exc:
            code = 550 if 500 <= exc.smtp_code < 600 else 451
            raise RelayError(code, "4.3.0 Gmail relay rejected the message" if code == 451 else "5.7.1 Gmail relay rejected the message") from None
        except (smtplib.SMTPServerDisconnected, OSError):
            if submitting:
                raise RelayError(451, "4.4.2 Gmail relay submission unconfirmed") from None
            raise RelayError(451, "4.4.1 Gmail relay is unavailable; try again later") from None
        except smtplib.SMTPException:
            raise RelayError(451, "4.4.1 Gmail relay is unavailable; try again later") from None
        except (ValueError, UnicodeError):
            raise RelayError(550, "5.6.0 Gmail relay could not encode the message") from None
        finally:
            if smtp is not None:
                try:
                    smtp.close()
                except OSError:
                    pass
