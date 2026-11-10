# Lab 2 – FTP Server with Embedded Database

Java 17 implementation of a small RFC 959-style FTP service. The server persists users, file metadata and transfer history in an embedded file database (`data/ftp.db`) and keeps all uploaded files below `data/root`.

## Build

```powershell
mvn -q package  # or .\scripts\build.ps1 without Maven
```

No third-party runtime dependency is required.

## Run server

```powershell
.\scripts\run-server.ps1 -Port 2121
# default account: student / vku2026
```

## Run FileZilla-style client

```powershell
.\scripts\run-client.ps1 -Host 127.0.0.1 -Port 2121
```

The desktop client has a quick-connect bar, local/remote file panes, sortable tables, upload/download actions, progress state and a transfer log. It uses the existing passive FTP protocol and embedded audit database. Remote browsing currently starts at the server root because the compact FTP client API does not yet expose remote `CWD` navigation.

Use `-Cli` for the original terminal client:

```powershell
.\scripts\run-client.ps1 -Cli -Host 127.0.0.1 -Port 2121
```

Commands: `USER`, `PASS`, `PWD`, `CWD`, `LIST`, `RETR <file>`, `STOR <file>`, `QUIT`, plus `HELP`.

The client uses passive mode automatically. A standard FTP client can connect with user `student` and password `vku2026`.

## Database

`data/ftp.db` is a human-readable, append-safe embedded database with three tables:

* `users`: username, password hash, home directory, enabled flag.
* `files`: relative path, size, modified time, owner.
* `transfers`: user, operation, path, byte count, status, timestamp.

The server creates the file and default account on first start. Delete `data/ftp.db` and `data/root` to reset a demo.

## Integration test

```powershell
.\scripts\test.ps1
```

The test starts an ephemeral server, authenticates, executes `LIST`, uploads and downloads a file, then checks the database transfer audit.
