# Lab 4 – Web (HTTP client/server)

The inherited JavaFX browser remains under `src/` and demonstrates
navigation, GET/HEAD/POST, history, and direct HTTP file download actions. `web_service.py` is a
dependency-free HTTP reference implementation that is easier to run and to
inspect during a packet capture.

## Run the server

```powershell
python web_service.py serve --port 8080
```

Routes:

| Route | Method | Purpose |
|---|---|---|
| `/` | GET/HEAD | HTML landing page |
| `/api/time` | GET/HEAD | JSON time service |
| `/api/echo?message=...` | GET/HEAD | JSON query echo service |
| `/api/echo` | POST | Echo JSON, form, or text body |
| `/api/upload?name=file.bin` | POST | Store bytes for later download |
| `/download/<name>` | GET/HEAD | Download a safe file path |

This covers more than one service type (HTML, JSON APIs, and binary/text file
transfer).  The server uses a threaded HTTP/1.0-compatible implementation and
returns `Content-Length`, `Content-Type`, and download disposition headers.

## Exercise the client

```powershell
python web_service.py get http://127.0.0.1:8080/
python web_service.py head http://127.0.0.1:8080/api/time
python web_service.py post http://127.0.0.1:8080/api/echo '{"topic":"networking"}'
python web_service.py download http://127.0.0.1:8080/download/sample.txt .\sample-copy.txt
python web_service.py history
```

`WebClient` also exposes `get`, `head`, `post`, and `download` methods for
tests or a notebook.  Every request is appended to `history.json` (the most
recent 100 records are retained), providing persistent browser-style history.

Run `.\test.ps1` (or `test.cmd`) to compile the modules and exercise the
HTTP routes, history, and download.  The smoke test reports a clear skip when
the host policy prevents binding a local listening socket.

## Modern JavaFX browser UI

The JavaFX client now opens a Chrome/Cốc Cốc-inspired shell: title and tab strip, back/forward/reload controls, rounded address/search field, HTTP method actions, history/download controls, zoom controls and status bar. The WebView and existing `ClientController` actions remain wired to the same GET/HEAD/POST/download behavior.

Run it with the JavaFX Maven plugin:

```powershell
.\mvnw.cmd -q javafx:run
```

For a direct launch from the Vietnamese workspace path, start the local server
and use the PowerShell launcher in another terminal:

```powershell
python web_service.py serve --port 8080
.\scripts\run-client.ps1 -HomeUrl http://127.0.0.1:8080/
```

`scripts/test-features.ps1` builds and exercises the actual JavaFX controller
against a real isolated HTTP server, including HEAD/POST rendering, navigation,
history, download byte checks and zoom. The required test sources are included
in `.qa/`; generated screenshots and logs are excluded from the repository.

## Original JavaFX project

The original browser sources and FXML are retained in this folder for the
visual client assignment.  Its JavaFX build may require a local JDK/JavaFX
toolchain; the Python service is the portable command-line demonstration.
