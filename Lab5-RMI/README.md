# Lab 5 – Java RMI replicated CRUD service

This project demonstrates a two-node Java 17 RMI service. Node **A** is the deterministic write primary; node **B** serves reads and forwards every write to A. A writes are durable before returning and are placed in a persistent outbox. A background retry worker sends the outbox to B. Replication is idempotent (`mutationId` is the request id), so retries and duplicate client requests are safe.

## Build

From this directory run `build.cmd` (JDK 17+). It compiles the API, servers, client, and integration test into `out/`.

## Run two nodes

For the required classroom topology, run A and B on **two separate physical computers**, with the dashboard clients on **virtual machines** that can reach both server IP addresses.

On physical computer A (the primary):

```
set REPLICA_HOST=192.168.56.12
set REPLICA_PORT=1100
run-node.cmd A
```

On physical computer B (the replica):

```
set PRIMARY_HOST=192.168.56.11
set PRIMARY_PORT=1099
run-node.cmd B
```

Open TCP ports **1099 and 1199 on A**, and **1100 and 1200 on B**, in the firewall (the first is the RMI registry; the second is the fixed exported service endpoint). Set `java.rmi.server.hostname` through the `--host` option when a machine has multiple interfaces. Both machines need the same compiled `out/` classes. Use a separate `--data` directory for each node to retain state across restarts.

## Client dashboard CRUD

`run-client.cmd <host> <port>` now opens the modern Record Dashboard. It shows the connected primary/replica node, pending replication count, CRUD form, record table and operation status.

```cmd
run-client.cmd 127.0.0.1 1099
```

Use `--cli` as the third argument when you need the original terminal client:

```cmd
run-client.cmd 127.0.0.1 1099 --cli
```

The CLI commands are:

```
create book-1 Distributed Systems
get book-1
update book-1 Replicated RMI
list
delete book-1
quit
```

Writes sent to B are forwarded to A. Reads on B become current after the outbox worker catches up.

## Integration test

`test.cmd` first prints **Compile check: PASS**, then runs `lab5rmi.IntegrationTest`. It exercises forwarding, CRUD, request idempotency, asynchronous replication, and restart catch-up. A locked-down environment that rejects local RMI sockets is reported as a runtime skip while preserving the compile result; run on a normal JDK host/VM for full execution.

## Protocol and durability notes

- `lab5rmi.api.RecordService` is the shared remote interface; all DTOs implement `Serializable`.
- The store is an atomically replaced `state.bin` containing records, request results, applied mutation ids, and the replication outbox.
- A write uses a monotonically increasing version. B ignores already applied or older versions, making retries idempotent.
- If B is down, A continues accepting durable writes. When B starts, the persisted outbox is retried automatically (startup catch-up).

## Feature verification evidence

`.qa/run-feature-checks.ps1` runs two separate local server JVMs, 41 assertions covering RMI CRUD/replication, forced process restarts, durable retry, and dashboard actions. The script and its Java helper source are included; generated data, logs and screenshots are excluded from the repository. Local verification does not establish the required two-physical-computer deployment.
