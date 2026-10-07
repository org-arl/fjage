# Release notes

All notable changes to the fjåge C gateway (`fjage.h` / `fjage.c`) are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The C gateway has no version number of its own and is not published as a separate package. The C gateway lived in `src/main/c` until it was moved to `gateways/c` in October 2019.

## [Unreleased]

### Fixed

- Parsing an empty JSON line from the master container no longer reads out of bounds.

## 2026-07-24

### Changed

- `fjage_agent_for_service()` and `fjage_agents_for_service()` wait up to 6 s for the directory response, up from 1 s, and return immediately (NULL / 0) if interrupted with `fjage_interrupt()`.
- README links to the new developer's guide location.

## 2026-07-08

### Added

- `fjage_msg_add_int_array_2d()` and `fjage_msg_get_int_array_2d()` for 2D integer arrays (JSON list of lists), passed as a flattened row-major array plus per-row lengths.

## 2026-06-22

### Fixed

- Build on Windows: the debug stack trace handler (`execinfo.h`, `STDERR_FILENO`) is now excluded on `_WIN32`.

## 2025-09-10

### Changed

- Message IDs and request IDs are now UUIDv7 (time-ordered) instead of random UUIDv4, matching the Java implementation and other gateways.

## 2025-04-29

### Changed

- Test build requires CMake 3.10 or newer.

##  2025-03-18

### Changed

- Library build requires CMake 3.10 or newer.

### Fixed

- The random number generator used for message IDs is seeded with the current time when a gateway is opened, so separate processes no longer produce the same sequence of message IDs.

##  2025-01-04

### Changed

- TCP gateways now name their agent `gateway-XXXXXXXX` instead of `CGateway-XXXXXXXX`. RS232 gateways still use `CGateway-XXXXXXXX`.

##  2024-12-05

### Fixed

- Test suite uses the `command` field of `ShellExecReq` instead of the private `cmd` field.

##  2024-07-01

### Changed

- Test suite reports its overall pass/fail status to the fjåge test harness with a `TestCompleteNtf` message.

##  2023-11-20

### Changed

- Gateway agent name changed from `CGatewayAgent@XXXXXXXX` to `CGateway-XXXXXXXX`.

##  2023-09-01

### Changed

- **Breaking:** `fjage_param_get_string()` now copies the value into a caller-supplied buffer: the signature changed from `const char* fjage_param_get_string(gw, aid, param, ndx)` to `int fjage_param_get_string(gw, aid, param, ndx, strval, len)`. It returns the number of characters copied, the full string length if `strval` is NULL, or -1 on error.

### Fixed

- `fjage_param_get_string()` no longer returns a pointer into a response message that has already been freed.

##  2022-10-21

### Added

- CMake build for static and shared libraries (`libfjage.a`, `libfjage.so` / `libfjage.dylib`, `fjage.dll` with a generated export header on Windows).
- `DEBUG=1` build option that installs a `SIGSEGV` handler printing a stack trace (non-Windows).

### Changed

- **Breaking:** Sources moved to `gateways/c/src` and the test program to `gateways/c/tests`.
- **Breaking:** The Makefile is now a wrapper around CMake: `make` builds the library, `make test` builds the test program, and `make runtest` runs it.
- Opening a TCP gateway on non-Windows platforms ignores `SIGPIPE` process-wide, so writes to a closed socket return an error instead of killing the process.

### Fixed

- `fjage_close()` frees messages still waiting in the receive queue.

## 2021-09-05

### Changed

- Byte, int and float arrays are sent as base64 objects tagged with their Java class (`[B`, `[I`, `[F`), so the master container decodes them as arrays rather than strings.
- Array getters accept both plain JSON arrays and base64-encoded arrays.
- Makefile uses `-D_DEFAULT_SOURCE` instead of the deprecated `-D_BSD_SOURCE`.

### Fixed

- Getters could read a value (such as a nested `data` field) from the wrong place when a message contained nested objects.

## 2021-04-08

### Added

- `fjage_msg_add_int_array()` and `fjage_msg_get_int_array()`.

### Fixed

- Message queue lookups in `fjage_receive()` / `fjage_receive_any()` failed to wrap around the ring buffer correctly.
- Memory leak in the byte and float array getters; empty base64 values now return -1.

## 2020-08-21

### Fixed

- Windows: `ioctlsocket()` is called with a `u_long` argument.

## 2020-04-01

### Added

- Windows support (Winsock). RS232 functions are not available on Windows.
- Parameter helpers that send a `ParameterReq` and wait up to 1 s for the reply: `fjage_param_get_int/long/float/bool/string()` and `fjage_param_set_int/long/float/bool/string()`.
- Build-time choice of `poll()`, `select()` or `ioctl()` to wait for incoming data, for platforms where `poll()` is broken.
- Windows build and test instructions in the README.

### Fixed

- Parsing of received messages whose fields contain nested objects or arrays.
- `fjage_msg_get_string()` returns NULL for JSON `null` values.

## 2019-10-17

### Changed

- C gateway moved from `src/main/c` to `gateways/c`, with a new README.
- `fjage_interrupt()` returns `int` (0 on success, -1 on error) instead of `void`.
- `fjage_rs232_wakeup()` returns -1 if the wakeup character cannot be written.

### Fixed

- Compiler warnings in `fjage.c`.

## 19-09-10

### Added

- `fjage_receive_any()` to receive the first message matching any of a list of message classes.
- `fjage_rs232_wakeup()` to wake a device by sending a character over RS232.

### Changed

- The gateway tells the master container which agent IDs and topics it wants (`wantsMessagesFor`), so it no longer receives every message on the bus.

### Fixed

- `fjage_receive()` checks already-queued messages before blocking on the connection.
- An interrupt raised while `fjage_receive()` is waiting is no longer discarded between reads; stale interrupts are cleared once at the start of each receive or directory query.

## 2019-05-28

### Fixed

- Linux build: Makefile defines `_BSD_SOURCE`.

## 19-01-15

### Added

- `fjage_rs232_open()` to connect to a master container over a serial port. The test program takes an optional device argument (`make test DEVICE=...`).

### Fixed

- Incoming messages longer than the fixed receive buffer are no longer truncated; the buffer grows as needed.
- Crash when a reply without an `id` arrived during a directory query, and stale interrupts carried over between calls.
- Build on Linux (`h_addr` compatibility define).

## 2018-04-19

First fjåge release to include the C gateway, in `src/main/c`. The first C API version (1.0) was completed in commit `9507568a` on 2018-04-17; fjåge 1.4.2 contains it plus one later fix so that large messages (such as big float arrays) are written fully when the socket accepts only part of the data.

### Added

- C API (`fjage.h`) to connect to a fjåge master container over TCP (`fjage_tcp_open()`, `fjage_close()`).
- Topic and agent subscriptions, `fjage_agent_for_service()` and `fjage_agents_for_service()`.
- `fjage_send()`, `fjage_receive()` (filter by class or reply ID) and `fjage_request()`, with `fjage_interrupt()` to abort a blocking call from another thread.
- Message creation and getters for string, int, long, float, bool, and base64-encoded byte and float arrays.
- Makefile building `libfjage.a` and a test program. The API is documented as not thread-safe.
