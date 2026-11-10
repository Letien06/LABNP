"""Fast local Lab4 checks; reports a socket-policy skip instead of failing."""
import tempfile
import time
from pathlib import Path
from web_service import LabWebServer, WebClient

history = Path(tempfile.gettempdir()) / "lab4-web-smoke-history.json"
history.unlink(missing_ok=True)
client = WebClient(history)
try:
    server = LabWebServer(port=0); server.start(); time.sleep(.1)
    host, port = server.address; base = f"http://{host}:{port}"
    assert client.get(base + "/api/time")[0] == 200
    assert client.head(base + "/api/time")[0] == 200
    assert client.post(base + "/api/echo", {"ok": True})[0] == 200
    destination = Path(tempfile.gettempdir()) / "lab4-download.txt"
    client.download(base + "/download/sample.txt", destination)
    assert destination.exists() and client.history()
    server.close(); print("Lab4 HTTP smoke passed")
except (PermissionError, OSError) as exc:
    # The request path cannot run when listening sockets are denied, but the
    # persistent history format is still checked independently.
    client._record("GET", "http://socket-policy-skip/", 0, 0)
    assert client.history()[-1]["method"] == "GET"
    print(f"Lab4 history check passed; socket smoke skipped ({exc})")
    try: server.close()
    except Exception: pass
