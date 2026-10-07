# Release notes

This file covers the fjåge Python gateway, published on PyPI as `fjagepy`.

## [Unreleased]

### Added

- `DeleteFileReq` message class.
- `Gateway.register_message(class_name, message_class)` to register a `Message` subclass under a fully qualified wire class name.
- `serial` optional dependency, so `pip install fjagepy[serial]` installs pyserial for `SerialConnector`.

### Changed

- `@message` must be given a fully qualified class name, e.g. `@message('org.example.MyMsg')`. The bare `@message` form is no longer accepted.
- Incoming messages inflate into a registered class only when their fully qualified `clazz` matches. There is no longer a fallback that matches on the short class name or on names defined in the `fjagepy.Message` module.
- `Message` subclasses that are not registered now serialize with their Python class name as `clazz` instead of `org.arl.fjage.Message`.

### Deprecated

- `MessageClass()` now emits a `DeprecationWarning`. Use `@message` or `Gateway.register_message()` instead.

### Fixed

- Messages of unregistered classes keep their original wire class name and fields when inflated.
- Registered classes serialize with the name they were registered under.

## [3.0.0] - 2026-08-07

### Added

- `SerialConnector` for talking to a container over a serial port (or any pyserial URL). Needs `pyserial`.
- `Connector` is now exported from the package, for writing custom transports.

### Changed

- **Breaking:** The `Gateway` `connector` argument now takes a `Connector` instance instead of a class, e.g. `Gateway(connector=SerialConnector('/dev/ttyUSB0', 115200))`. `hostname`, `port` and `reconnect` are ignored when a connector is given.
- **Breaking:** `TCPConnector` takes `TCPConnector(hostname='localhost', port=1100, reconnect_delay=5)` instead of keyword-only `host`/`port`/`reconnect_delay`, and the `host` attribute is now `hostname`.
- `TCPConnector` and `SerialConnector` validate their constructor arguments more strictly (booleans are rejected as ports, `reconnect_delay` must be finite and at least -1).

### Fixed

- The package imports on Python 3.9 again; type annotations using `X | Y` are no longer evaluated at import time.
- `str(gateway)` shows the connector details and connection state instead of the literal text `Gateway(self.__details__())`.

## [2.2.0] - 2026-07-24

### Added

- `Gateway.services()` lists all services provided by agents in the container.
- `directory_timeout` argument on `Gateway` (default 6000 ms), used by `agents()`, `services()`, `containsAgent()`, `agentForService()` and `agentsForService()`.

### Changed

- `receive()` is non-blocking by default (`timeout=0`). It previously waited for the gateway's request timeout.
- The default request timeout dropped from 10000 ms to 1000 ms, for both `Gateway` and unowned `AgentID`s.

## [2.1.2] - 2026-04-27

### Fixed

- Importing `fjagepy` no longer fails when numpy is not installed (broken since 2.1.0).

## [2.1.1] - 2026-04-23

### Added

- `py.typed` marker, so type checkers use the package's inline type hints.

### Changed

- `AgentID` defines `__getattr__`/`__setattr__` in the class body, so type checkers accept parameter access such as `agent.param`.

## [2.1.0] - 2026-04-21

### Added

- `@message` decorator to register a `Message` subclass, optionally with a fully qualified Java class name, so incoming messages inflate into that class.
- `PutFileReq`, `GetFileReq`, `ShellExecReq` and `GetFileRsp` are now proper classes with typed constructor arguments, and `ParameterReq`/`ParameterRsp` are registered the same way.
- `sentAt` attribute on `Message`.
- Fields annotated as `Optional[AgentID]` on a registered class inflate as `AgentID` objects.

### Changed

- `Connector` subclasses are constructed with keyword arguments (`host`, `port`, `reconnect_delay`) and must implement `__details__()`.
- The reconnect delay for TCP connections is now 5 s (was 2 s).
- Any message whose class name ends in `Req` defaults to the `REQUEST` performative.
- Field names that clash with Python keywords (e.g. `from_`) are mapped to the keyword name on any message, both when set and when serialized. Other names ending in `_` are left alone.

### Fixed

- Odd-length arrays flagged as complex no longer fail to inflate.

## [2.0.2] - 2026-03-26

### Changed

- The gateway no longer sends `{"alive": true}` on connect. It answers `{"alive": true}` pings from the container and sends `{"alive": false}` before disconnecting in `close()`, following the updated gateway spec.
- `str(msg)` now shows the message class, performative and fields (e.g. `ShellExecReq:REQUEST[command=ps]`) instead of only the performative and class, and IPython uses the same text.
- Package metadata now includes the license, keywords and project URLs.

### Fixed

- No longer needs Python 3.11; the use of `typing.Self` was removed.

## [2.0.0] - 2025-09-30

### Added

- Pluggable transports through a `Connector` base class, with `TCPConnector` as the default.
- `Gateway` takes `connector`, `reconnect` and `timeout` (default 10000 ms) arguments, and `hostname` defaults to `localhost`. Request and directory calls take an optional `timeout` that defaults to the gateway's.
- `Gateway.agents()` and `Gateway.containsAgent()`.
- snake_case aliases: `agent_id()`, `is_connected()`, `contains_agent()`, `agent_for_service()`, `agents_for_service()`.
- `Gateway` works as a context manager and is safe to use from multiple threads.
- `AgentID.get(index=-1)` returns all parameters of an agent as a dict. `AgentID` objects compare equal by name and topic, and can be hashed.
- `Services.DOCUMENTATION`.
- IPython pretty-printing for `Gateway`.
- Message IDs are UUIDv7.

### Changed

- **Breaking:** The package was rewritten as separate modules (`Gateway`, `AgentID`, `Message`, ...), built from `pyproject.toml`, and requires Python 3.9 or newer.
- **Breaking:** numpy is no longer a dependency. numpy arrays are still serialized if numpy is installed, but received complex arrays come back as Python lists of `complex` instead of numpy arrays.
- **Breaking:** `Performative` is now an `Enum`.
- **Breaking:** `AgentID(name, topic=False, owner=None)`: `is_topic` is now a method, and `get_name()` was added. `str(aid)` now returns `AgentID(name=..., topic=..., owner=...)`.
- **Breaking:** `Message(in_reply_to_msg=None, perf=INFORM)` takes the original message and sets `recipient` and `inReplyTo` from it, instead of an `inReplyTo` ID. `sender` and `recipient` on received messages are `AgentID` objects.
- **Breaking:** `send()` returns `None` and raises `ValueError` for anything that is not a `Message`. Sending through an `AgentID` with no owner raises `RuntimeError`.
- **Breaking:** `receive()` and `request()` default to the gateway timeout (10 s) instead of non-blocking and 1000 ms respectively. The `filter` argument of `receive()` is now `msg_filter` (`filter=` is still accepted).
- The gateway's agent name is now `gateway-<uuid>` instead of `PythonGW-<uuid>`.
- `receive()` without a filter returns the oldest queued message; it used to return the newest. The receive queue holds at most 512 messages and drops the oldest when full.
- `agent[i]` returns a new `AgentID` instead of changing the index on the shared one.
- Setting a parameter no longer warns when the agent reports a different value.

### Deprecated

- `getAgentID()` and `isConnected()`, in favour of `agent_id()` and `is_connected()`.

### Removed

- **Breaking:** The `perf` argument of `MessageClass()`.
- **Breaking:** `GenericMessage` is no longer exported from the package.

### Fixed

- `str()` of a `ParameterReq` no longer raises (broken since 1.7.3).

## [1.7.5] - 2023-02-24

### Added

- `AgentID` values inside parameter responses inflate to `AgentID` objects.

### Fixed

- `str()` of an `AgentID` without an owning gateway returns just the name instead of raising.

## [1.7.4] - 2023-02-03

### Changed

- Packaging: the license file is bundled and Python 3.7 to 3.11 are listed as supported.

### Fixed

- `Gateway.__del__` no longer raises if the gateway failed before its logger was set up.

## [1.7.3] - 2023-02-01

### Added

- `GetFileRsp` message class.

### Changed

- `ParameterReq` puts its first `get()`/`set()` in the top-level `param`/`value` fields, as the Java `ParameterReq` does; any further ones go in `requests`.
- `send()` returns `False` when the connection to the master container is down, instead of blocking while it reconnects.

### Fixed

- Messages of classes unknown to fjagepy are delivered as generic `Message` objects again; in 1.7.1 and 1.7.2 they were dropped.
- Indexed parameter access (`agent[i].param`) resets the index afterwards, so later non-indexed access does not reuse it.

## [1.7.2] - 2021-05-05

### Changed

- A blocking `receive()` waits until a matching message arrives again, instead of returning after the first wake-up. Setting `gw.cancel = True` ends the wait.

### Fixed

- Getting or setting a parameter through `AgentID` attributes no longer raises `KeyError` when the `ParameterRsp` lacks `param` or `value`.

## [1.7.1] - 2020-10-09

### Changed

- A blocking `receive()` returns after the first wake-up, so it can be interrupted.

### Fixed

- A message that fails to deserialize is no longer added to the receive queue.
- Reconnection failures are logged.

## [1.7.0] - 2020-04-05

### Added

- Agent parameters can be read and written as `AgentID` attributes (`agent.param`, `agent.param = value`, `agent[index].param`).
- `ParameterReq` (with `get()`/`set()` chaining) and `ParameterRsp` (with `get()`/`parameters()`).
- In IPython, an `AgentID` prints the agent's parameters grouped by section, with read-only ones marked.
- `Services.SHELL` and the shell messages `PutFileReq`, `GetFileReq` and `ShellExecReq`.
- `str(aid)` includes the host and port of the gateway connection.

## [1.6.1] - 2019-09-24

### Added

- `performative` and `messageID` as aliases for `perf` and `msgID` on messages.

### Fixed

- `Gateway.agent()` was missing `self` and could not be called.
- `subscribe()` returns `True` when already subscribed, and unsubscribing from a topic that was not subscribed quietly returns `False`.

## [1.6] - 2019-09-09

### Added

- `Gateway.agent(name)` returns an `AgentID` for a named agent.
- `Gateway.isConnected()`.
- `aid << msg` as shorthand for `aid.request(msg)`.
- `Message` accepts `inReplyTo` and `perf` in its constructor and defaults to `INFORM`. `MessageClass()` accepts `parent` and `perf`, and classes whose name ends in `Req` default to `REQUEST`.
- IPython pretty-printing for messages.

### Changed

- **Breaking:** `AgentID(name, is_topic=False, owner=None)` replaces `AgentID(gw, name, is_topic)` and rejects empty names or names starting with `#`. `getAgentID()` returns an `AgentID` instead of a string.
- The `port` argument of `Gateway()` defaults to 1100.
- The gateway tells the master which agent and topics it wants messages for (`wantsMessagesFor`), so only those are forwarded. It also sends `alive` notifications on connect and close.
- Received messages are deserialized on arrival, and `receive()` with a class filter also matches subclasses.
- `str(msg)` follows the Java style, showing signals and data as sample and byte counts.
- Arrays flagged with `__isComplex` inflate to numpy complex arrays.
- `AgentID` values in messages serialize as agent names (`#name` for topics).
- The gateway reconnects whenever the connection to the master drops, and logs through `logging` instead of printing.

### Removed

- **Breaking:** The `name` argument of `Gateway()`.
- **Breaking:** `Gateway.shutdown()`; use `close()`.
- **Breaking:** The `relay` argument of `send()` and the `timeout` argument of `agentForService()`/`agentsForService()`.

### Fixed

- `unsubscribe()` no longer renames the `AgentID` passed to it.
- Fixes to dynamic message class loading, base64 array decoding and reading array-valued parameters.

## [1.5.2] - 2018-12-24

### Fixed

- The `fjagepy` package is included in the distribution again.

## [1.5.1] - 2018-12-24

### Fixed

- numpy is declared as an install requirement.
- The PyPI project description renders correctly.

Known issue: this release was built without the `fjagepy` package and contains no code. Use 1.5.2.

## [1.5] - 2018-12-24

### Added

- First PyPI release of the Python gateway for connecting to a fjåge master container over TCP.
- `Gateway` with `send()`, `receive()` (filter by reply, class or predicate, with timeouts), `request()`, `topic()`, `subscribe()`/`unsubscribe()`, `agentForService()`/`agentsForService()`, `getAgentID()`, `flush()`, `close()` and `shutdown()`.
- `AgentID` with `send()` and `request()`.
- `Message`, `GenericMessage` and `MessageClass()` for creating message classes from Java class names.
- numpy arrays (including complex signals) are serialized, and base64-encoded Java arrays are decoded.
- Automatic reconnection when the master container goes away.
