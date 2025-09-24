# fjåge Gateway in Python (fjagepy)

[![PyPI version](https://img.shields.io/pypi/v/fjagepy.svg)](https://pypi.org/project/fjagepy/)
[![Python 3.9+](https://img.shields.io/badge/python-3.9+-blue.svg)](https://www.python.org/downloads/)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENSE)

`fjagepy` is a Python gateway for the [fjåge](https://github.com/org-arl/fjage) multi-agent system framework. It enables Python applications to seamlessly communicate with fjåge agents over TCP connections, providing a Pythonic interface to the fjåge ecosystem.

## Features

- **Agent Communication**: Send and receive messages to/from fjåge agents
- **Request-Response Pattern**: Built-in support for synchronous request-response interactions
- **Message Filtering**: Flexible message filtering by type, sender, or custom predicates
- **Agent Discovery**: Find and communicate with agents by name or service
- **NumPy Integration**: Automatic serialization/deserialization of NumPy arrays
- **Connection Management**: Automatic reconnection and robust error handling
- **Type Safety**: Full type hints for better IDE support and code reliability

## Installation

### From PyPI

```bash
pip install fjagepy
```

### From Source

```bash
git clone https://github.com/org-arl/fjage.git
cd fjage/gateways/python
pip install -e .
```

### Development Installation

For development with testing dependencies:

```bash
pip install -e ".[dev]"
```

## Quick Start

### Basic Connection and Communication

```python
from fjagepy import Gateway, Message, AgentID

# Connect to fjåge platform
gw = Gateway('localhost', 1100)

# Get reference to an agent
shell = gw.agent('shell')

# Send a message
msg = Message()
msg.data = "Hello from Python!"
shell << msg

# Close connection when done
gw.close()
```

### Request-Response Pattern

```python
from fjagepy import Gateway, ShellExecReq

# Connect and get shell agent
gw = Gateway()
shell = gw.agent('shell')

# Create and send a shell command request
req = ShellExecReq()
req.command = 'ps'
req.ans = True

# Send request and wait for response
rsp = shell.request(req, timeout=5000)  # 5 second timeout
if rsp:
    print(f"Command output: {rsp.output}")

gw.close()
```

### Message Filtering and Reception

```python
from fjagepy import Gateway, Message

gw = Gateway()

# Receive any message
msg = gw.receive(timeout=1000)

# Receive messages of specific type
from fjagepy import ParameterRsp
param_msg = gw.receive(ParameterRsp, timeout=1000)

# Receive with custom filter
def my_filter(msg):
    return hasattr(msg, 'data') and 'important' in str(msg.data)

important_msg = gw.receive(my_filter, timeout=1000)

gw.close()
```

## API Reference

### Gateway Class

The main entry point for fjåge communication.

#### Constructor

```python
Gateway(hostname='localhost', port=1100, connector=TCPConnector,
        reconnect=True, timeout=10000)
```

**Parameters:**
- `hostname` (str): Hostname of the fjåge platform (default: 'localhost')
- `port` (int): Port number of the fjåge platform (default: 1100)
- `connector` (type): Connector class to use (default: TCPConnector)
- `reconnect` (bool): Enable automatic reconnection (default: True)
- `timeout` (int): Default timeout in milliseconds (default: 10000)

#### Key Methods

##### `agent(name: str) -> AgentProxy`
Get a proxy to communicate with a named agent.

```python
shell = gw.agent('shell')
modem = gw.agent('modem')
```

##### `send(msg: Message) -> None`
Send a message through the gateway.

```python
msg = Message()
msg.recipient = AgentID('target-agent')
gw.send(msg)
```

##### `receive(filter=None, timeout=None) -> Message`
Receive a message matching the filter criteria.

```python
# Receive any message
msg = gw.receive()

# Receive specific message type
rsp = gw.receive(ParameterRsp)

# Receive with timeout
msg = gw.receive(timeout=5000)

# Receive with custom filter
msg = gw.receive(lambda m: m.sender.name == 'modem')
```

##### `request(msg: Message, timeout=None) -> Message`
Send a request and wait for response.

```python
req = ParameterReq()
req.param = 'address'
rsp = gw.request(req, timeout=3000)
```

##### `services() -> List[str]`
Get list of available services.

```python
available_services = gw.services()
print(f"Available services: {available_services}")
```

##### `agents_for_service(service: str) -> List[AgentID]`
Find agents providing a specific service.

```python
phy_agents = gw.agents_for_service('org.arl.unet.Services.PHYSICAL')
```

##### `close() -> None`
Close the gateway connection.

```python
gw.close()
```

### Message Classes

#### Base Message Class

```python
from fjagepy import Message, Performative

msg = Message()
msg.recipient = AgentID('target')
msg.perf = Performative.REQUEST
msg.data = "Custom data"
```

#### Built-in Message Types

**Parameter Operations:**
```python
from fjagepy import ParameterReq, ParameterRsp

# Get parameter
req = ParameterReq()
req.param = 'address'
req.index = -1  # All indices

# Set parameter
req = ParameterReq()
req.param = 'power'
req.value = -10
```

**File Operations:**
```python
from fjagepy import PutFileReq, GetFileReq

# Upload file
put_req = PutFileReq()
put_req.filename = 'config.txt'
put_req.contents = file_contents

# Download file
get_req = GetFileReq()
get_req.filename = 'data.log'
```

**Shell Commands:**
```python
from fjagepy import ShellExecReq

req = ShellExecReq()
req.command = 'help'
req.ans = True  # Request response
```

#### Custom Message Types

Create custom message types using `MessageClass`:

```python
from fjagepy import MessageClass

# Define custom message type
MyCustomMessage = MessageClass("com.example.MyCustomMessage")

# Use like any other message
msg = MyCustomMessage()
msg.customField = "value"
msg.recipient = gw.agent('target')
gw.send(msg)
```

### Agent Proxy

Agent proxies provide convenient access to specific agents:

```python
# Get agent proxy
shell = gw.agent('shell')

# Send message to agent
shell << message

# Send request to agent
response = shell.request(request_msg, timeout=5000)

# Check if agent exists
if shell.exists():
    print("Shell agent is available")
```

### NumPy Integration

fjagepy automatically handles NumPy arrays in messages:

```python
import numpy as np
from fjagepy import Message

# Create message with NumPy array
msg = Message()
msg.data = np.array([1.0, 2.0, 3.0])
msg.complex_data = np.array([1+2j, 3+4j])  # Complex arrays supported

# Arrays are automatically serialized/deserialized
agent << msg
```

## Advanced Usage

### Custom Connectors

Implement custom connectors by extending the `Connector` base class:

```python
from fjagepy import Connector

class MyCustomConnector(Connector):
    def connect(self):
        # Implementation
        pass

    def disconnect(self):
        # Implementation
        pass

    def send(self, msg: str):
        # Implementation
        pass

# Use custom connector
gw = Gateway(connector=MyCustomConnector)
```

### Logging and Debugging

This library uses the standard [Python logging](https://docs.python.org/3/library/logging.html) system.

By default, the library does not emit logs to the console.
To see logs, configure Python’s logging in your application:

```python
import logging

# Show all logs on stdout
logging.basicConfig(level=logging.DEBUG)

# Or, enable only logs from this library
logging.getLogger("fjagepy").setLevel(logging.DEBUG)
```

For troubleshooting, you can also send logs to a file:
```python
logging.basicConfig(filename="debug.log", level=logging.DEBUG)
```

### Context Manager Support

Use Gateway as a context manager for automatic cleanup:

```python
from fjagepy import Gateway

with Gateway('localhost', 5081) as gw:
    shell = gw.agent('shell')
    req = ShellExecReq()
    req.command = 'help'
    req.ans = True
    rsp = shell.request(req)
    print(rsp.output)
# Gateway automatically closed
```

## Testing

Run the test suite:

```bash
# Install test dependencies
pip install -e ".[dev]"

# Run tests
pytest

# Run specific test file
pytest test/test_gateway.py

# Run with coverage
pytest --cov=fjagepy
```

### Test Requirements

Tests require a running fjåge platform on `localhost:5081`. You can start one using:

```bash
# In the main fjåge directory
./gradlew --info -PmanualPyTest=true test --tests="org.arl.fjage.test.fjagepyTest"
```

## Examples

See the `examples/` directory an example of how to use the fjagepy gateway.

## Compatibility

- **Python**: 3.9 or higher
- **fjåge**: Compatible with fjåge 1.7.x and 2.x
- **NumPy**: Optional dependency for array support
- **Operating Systems**: Linux, macOS, Windows

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Run the test suite (`pytest`)
6. Commit your changes (`git commit -m 'Add amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

## License

This project is licensed under the BSD 3-Clause License. See the [LICENSE](LICENSE) file for details.

## Support

- **Documentation**: [fjåge Documentation](https://fjage.readthedocs.io/)
- **Issues**: [GitHub Issues](https://github.com/org-arl/fjage/issues)
- **Discussions**: [GitHub Discussions](https://github.com/org-arl/fjage/discussions)

## Related Projects

- **fjåge**: [Main fjåge framework](https://github.com/org-arl/fjage)
- **fjåge.js**: [JavaScript gateway](https://github.com/org-arl/fjage/tree/master/gateways/js)
- **Fjåge.jl**: [Julia gateway](https://github.com/org-arl/Fjage.jl)
- **fjåge-c**: [C gateway](https://github.com/org-arl/fjage/tree/master/gateways/c)