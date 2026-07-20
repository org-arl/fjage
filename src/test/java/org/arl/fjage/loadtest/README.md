# Multi-container load & fault-injection tests

A stress harness for the remote-container stack (master/slave containers over TCP JSON,
plus the WebSocket path). Unlike the unit tests, these scenarios run 1 master + 3 slave
containers in a single JVM over real localhost sockets, push sustained message load
across them, and inject faults (slow peers, unresponsive peers, dropped connections,
mid-load shutdowns) while checking for message loss, duplicates, agent-thread blocking,
handler/thread leaks, and deadlocks.

These tests are **not run** by `./gradlew test` or CI (they take ~15–20 min total and
some intentionally reproduce open findings). They compile with the normal test source
set, but only execute on demand:

```sh
./gradlew loadTest                                            # all scenarios
./gradlew loadTest --tests 'org.arl.fjage.loadtest.T1LoadTest'  # one scenario
```

## Scenarios

| Test | What it does | Key assertions |
|------|--------------|----------------|
| `T1LoadTest` | 4 containers × (senders, receivers, publisher, subscriber) exchanging ~20k unicast + topic messages while directory ops hammer concurrently | zero loss, zero dups, no SEVERE logs |
| `T2SlowSlaveTest` | one slave stops reading for 10 s (TCP backpressure via proxy) under topic + unicast load | full recovery after unpause; reports head-of-line blocking, send() call blocking, and directory-op latency |
| `T3MisbehavingPeerTest` | peers that (a) handshake then ignore requests, (b) send malformed JSON, (c) connect but never speak or read | directory ops return within timeout, relay to healthy slaves unaffected, dead connection pruned, no agent-thread wedge |
| `T4ReconnectTest` | abrupt connection loss (RST, no SIGN_OFF), reconnect, then 8× flapping under load | handler pruned eagerly, healthy slaves see zero loss, no dups, no handler/thread leaks |
| `T5ShutdownRaceTest` | 12 cycles of master-first / slave-first shutdown mid-load | clean teardown every cycle, no thread leaks |
| `T6WebSocketTest` | WS client exchanges 8k messages with the master, then disconnects abruptly mid-stream | zero loss both directions, throughput well above the old 10 ms/line cap, no errors on abrupt close |
| `T7StreamStressTest` | unit-level: concurrent writers/readers on `BlockingByteQueue` / pseudo-streams with racing `close()`/`clear()` | readers always unblock and see EOF (-1), no busy-spin, no hang |

## Harness pieces

- `MultiContainerFixture` — starts the master + N slave platforms, optionally routing
  chosen slaves through a proxy; readiness and teardown helpers.
- `ThrottlingTcpProxy` — localhost TCP forwarder with runtime pause (backpressure),
  rate limiting, connection refusal, and RST-style drops.
- `FakeSlave` — raw TCP client speaking the newline-JSON protocol under script control
  (can skip the ALIVE handshake or ignore requests).
- `LoadAgents` — sequenced-message sender/receiver/publisher/subscriber agents with
  thread-safe loss/dup/latency stats; `ContinuousSender` records per-`send()` durations
  to expose agent-thread blocking.
- `TestUtil`, `LogCapture` — wait/poll helpers, thread-leak snapshots, JUL capture
  (tests fail on unexpected SEVERE logs).

Some scenarios encode expected behavior that may fail while a known issue is open —
a failure here is a reproduction, not necessarily a broken test. Timings assume a
reasonably fast machine; the fault-injection tests use real timeouts (reconnect
backoff, ALIVE watchdog) and cannot be shortened much further.
