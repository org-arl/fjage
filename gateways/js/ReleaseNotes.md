# Release notes

This file covers the fjage.js JavaScript gateway, published on npm as `fjage`. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- `Gateway.registerMessage(className, cls)` registers a `Message` subclass under its fully qualified Java class name. Registered classes get that name as their JSON `clazz`, are created when matching messages arrive, and can be used as `receive()` filters.
- `DeleteFileReq` message class.
- Built-in message classes (`ParameterReq`, `ParameterRsp`, `PutFileReq`, `GetFileReq`, `GetFileRsp`, `ShellExecReq`) are now real `Message` subclasses with their fields declared and given defaults.

### Changed

- **Breaking:** the `Message` constructor and the built-in message classes no longer accept a fields object. The only arguments are an optional message being replied to and an optional performative, and passing anything other than a `Message` as the first argument throws a `TypeError`. Set fields after construction.
- Incoming messages are matched to registered classes by fully qualified name. The unqualified name is used only when the incoming `clazz` is itself unqualified. Unregistered messages are inflated as `Message` and keep their original `clazz`.
- An unregistered `Message` subclass is serialized with its JavaScript class name as `clazz` instead of its parent's name.
- Any message class whose name ends in `Req` defaults to the `REQUEST` performative, including classes registered with `Gateway.registerMessage()`.

### Deprecated

- `MessageClass()`. Use `Gateway.registerMessage()` with a `Message` subclass instead.

### Removed

- **Breaking:** the `returnNullOnFailedResponse` option. `AgentID.get()`, `AgentID.set()`, `containsAgent()`, `agentForService()` and `agentsForService()` now always reject when the request fails or times out.
- **Breaking:** the browser gateway cache. `new Gateway()` used to return the existing instance for a URL that already had one, ignoring the new options, and `window.fjage.gateways` was filled in. Each `new Gateway()` now opens its own connection, in browsers and in Node.js.

## [2.4.0] - 2026-07-24

### Added

- `Gateway.services()` lists the services available in the container. `JSONMessage.createServices()` builds the matching request.
- `directoryTimeout` gateway option (default 6000 ms). It sets the default timeout for `agents()`, `services()`, `containsAgent()`, `agentForService()` and `agentsForService()`.

### Changed

- The `timeout` gateway option now defaults to 1000 ms (was 10000 ms) and only sets the default timeout for `request()` and for `AgentID.request()`, `get()` and `set()`. Directory queries use `directoryTimeout`. AgentIDs without an owning gateway also default to 1000 ms.

## [2.3.1] - 2026-03-26

### Changed

- The gateway no longer sends `{"alive": true}` on its own when it connects. It replies with `{"alive": true}` when the master container sends one.
- `close()` sends `{"alive": false}` and closes the connection only if the gateway is connected. Before, a gateway that was still connecting was closed as soon as the connection opened.

## [2.3.0] - 2026-03-20

### Added

- Message classes for the fjåge shell agent: `PutFileReq`, `GetFileReq`, `GetFileRsp` and `ShellExecReq`.

## [2.2.2] - 2025-09-10

### Fixed

- Replies to `containsAgent` queries from the master container now include `"answer": false`. Before, the field was dropped when the answer was false.

## [2.2.1] - 2025-09-09

### Changed

- `Message.msgID` and the IDs of JSON protocol requests are now UUIDv7 strings instead of random 32-character hex strings. This needs `crypto.getRandomValues()`.
- Gateway internals are now underscore-prefixed. `pending`, `subscriptions`, `listeners`, `eventListeners` and `queue` became `_pending_actions`, `_subscriptions`, `_pending_receives`, `_eventListeners` and `_queue`. Code that read these fields must change.

## [2.2.0] - 2025-08-01

### Added

- `JSONMessage` class and `ParameterRsp` message class are exported.
- `agents()`, `containsAgent()`, `agentForService()` and `agentsForService()` take an optional `timeout` argument.
- `types` field in `package.json`, so TypeScript finds the bundled type declarations.
- The gateway answers `services` queries from the master container.

### Changed

- `sender` and `recipient` on messages are now `AgentID` objects instead of strings, on both incoming and outgoing messages.
- The default `timeout` gateway option went from 1000 ms to 10000 ms. Directory queries now wait for `timeout` instead of 8 × `timeout`. `request()` and `AgentID.request()` default to the gateway timeout instead of 1000 ms, and `AgentID.get()`/`set()` instead of 5000 ms.
- `agentsForService()` returns `null` instead of `[]` when the request fails and `returnNullOnFailedResponse` is set.
- `containsAgent()` returns `null` instead of throwing when the request fails and `returnNullOnFailedResponse` is set.
- `Message.toString()` now returns `PERFORMATIVE: ClassName` instead of listing the message fields.
- Fields whose names start with `_` are no longer serialized (before, only fields starting with `__` were skipped).
- `Message._serialize()`, `_deserialize()` and `_inflate()` are replaced by `Message.toJSON()` and `Message.fromJSON()`.
- The `rxp` event now passes a `JSONMessage` instead of a plain object.
- The source is split into modules. All public classes are still exported from the package entry point.

### Fixed

- `AgentID.set()` no longer changes the caller's `params` and `values` arrays, and throws if their lengths differ. `AgentID.get()` no longer changes `params`.
- In Node.js, the gateway no longer overwrites `global.atob`.

## [2.1.0] - 2025-05-14

### Added

- `cancelPendingOnDisconnect` gateway option (default `false`). When set and the connection drops, a pending `receive()` resolves with `null` and the message queue is flushed.

### Changed

- Message listeners added with `addMessageListener()` can no longer consume messages. Every listener sees every message and its return value is ignored. Messages still go to pending `receive()` calls and then to the queue.

## [2.0.0] - 2025-04-29

### Changed

- **Breaking:** the gateway sends `wantsMessagesFor` with its own ID and its subscribed topics when it connects and whenever subscriptions change. The master container then forwards only those messages instead of all traffic. This needs fjåge 2.0.0 or later, so fjage.js 2.x does not work with fjåge 1.x.

## [1.13.9] - 2025-04-15

### Fixed

- `AgentID.get()` with an array of parameters matches response values by exact parameter name, ignoring any qualifying prefix. Before, it matched by suffix, so `power` could pick up `txpower`.

## [1.13.8] - 2025-04-15

### Fixed

- `AgentID.set()` with an array of parameters matches response values by exact parameter name, ignoring any qualifying prefix, instead of by suffix.

## [1.13.7] - 2025-01-31

### Changed

- Instances of message classes created with `MessageClass()` whose names end in `Req` default to the `REQUEST` performative.

## [1.13.6] - 2025-01-31

### Changed

- Gateway agent names are now `gateway-XXXX` instead of `WebGW-XXXX` or `NodeGW-XXXX`.

### Fixed

- Gateway options that are `undefined` or empty strings fall back to the defaults again (a regression in 1.13.1).

## [1.13.5] - 2024-10-17

### Fixed

- Type annotations: `Performative` is typed as an enum, the `Message` fields are documented, and the second argument to `topic()` is marked optional.

## [1.13.4] - 2024-10-17

### Fixed

- Pinned the `browser-or-node` dependency to 2.0.0. The `^3.0.0` range in 1.13.3 broke the package.

## [1.13.3] - 2024-10-16

### Fixed

- TypeScript declarations are generated next to the ESM build (`dist/esm`), so TypeScript can find them.

### Changed

- Runtime dependency `browser-or-node` raised to `^3.0.0`.

## [1.13.2] - 2024-10-16

No changes to the gateway. This republishes 1.13.1 after a release-process fix.

## [1.13.1] - 2024-10-16

### Added

- TypeScript declaration files, generated from JSDoc annotations.
- `ParameterReq` is exported.

### Changed

- New messages default to the `INFORM` performative instead of an empty string.
- `AgentID.send()` and `AgentID.request()` throw if the AgentID has no owning gateway.
- `subscribe()` returns `true`.
- Gateway options are merged with `Object.assign`, so `undefined` or empty-string options no longer fall back to defaults.

### Removed

- The deprecated positional `Gateway(hostname, port, pathname, timeout)` constructor. Pass an options object.

### Fixed

- Base64-encoded long arrays (`[J`) decode correctly, as `BigInt` values.

## [1.13.0] - 2024-09-13

### Added

- `Gateway.connected` property that tracks the connection state.

## [1.12.2] - 2024-04-02

### Fixed

- `receive()` filters: class constructors are matched with `instanceof` instead of being called as predicate functions.

## [1.12.1] - 2024-02-21

### Fixed

- `keepAlive: false` is honoured again. 1.11.2 ignored it.
- Gateway options that are `undefined` or empty strings fall back to the defaults, for example an empty `window.location.port`.

## [1.12.0] - 2024-01-26

### Added

- `Gateway.agents()` lists all agents in the container.
- `Gateway.containsAgent()` checks whether an agent exists in the container.
- `returnNullOnFailedResponse` gateway option (default `true`). When `false`, `AgentID.get()`, `AgentID.set()`, `agentForService()` and `agentsForService()` reject with an error when there is no valid response, instead of returning `null` or `[]`.

### Changed

- `agentForService()` returns `null` instead of `undefined` when no agent provides the service.
- When getting or setting several parameters, a parameter missing from the response comes back as `undefined` instead of `null`.

### Fixed

- Errors while deserializing an incoming message are caught and logged instead of thrown.

## [1.11.2] - 2023-12-11

### Fixed

- The Node.js TCP connection reconnects after it drops. Before, it never tried to reconnect.
- The TCP connection removes its socket listeners when closed, so closing it does not start a reconnect.

## [1.11.1] - 2023-12-11

### Fixed

- Listeners added with `addConnListener()` are called again when the connection opens or closes. They were never called in 1.10.x.
- An exception thrown in an event listener, message listener or `receive()` filter is caught and logged, and no longer breaks message delivery.

## [1.10.3] - 2022-07-22

### Fixed

- `AgentID.get()` and `AgentID.set()` with several parameters put the first one in `param`/`value` and the rest in `requests`, as fjåge expects ([#246](https://github.com/org-arl/fjage/issues/246)).

## [1.10.2] - 2022-07-21

### Fixed

- Browser gateways get a random suffix in their agent name again. Before, every browser gateway was named `WebGW-`.

## [1.10.1] - 2022-06-10

First final release on npm. Before this, fjage.js was a single browser script bundled in the fjåge jar. This release covers the reorganisation into a standalone gateway, including the 1.9.1-rc pre-releases.

### Added

- npm package `fjage` with ES module, CommonJS and UMD (plain and minified) builds.
- Node.js support. Under Node.js the gateway connects to the master container over TCP (default `localhost:1100`). In browsers it uses WebSockets.
- Options-object constructor `new Gateway({hostname, port, pathname, keepAlive, queueSize, timeout})`.
- `keepAlive` option to turn off automatic reconnection, and `queueSize` option for the receive queue (default 128).

### Changed

- **Breaking:** `MessageClass()` no longer defines message classes as globals on `window`. Classes are kept in a registry on `MessageClass` and incoming messages are matched against it. Calling `MessageClass()` again with the same name returns the existing class.
- **Breaking:** `window.fjage.MessageClass` and `window.fjage.getGateway()` are removed.
- `receive()`, `AgentID.request()`, `AgentID.get()` and `AgentID.set()` are `async` functions.
- `AgentID.set()` no longer logs a warning when the value returned differs from the value requested. Parameter and JSON parse failures are no longer logged.

### Deprecated

- The positional `Gateway(hostname, port, pathname, timeout)` constructor. It still works but logs a warning.

### Fixed

- `receive()` with a message class filter now matches classes created by `MessageClass()` with another message class as the parent.
