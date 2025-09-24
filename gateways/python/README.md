# fjåge Gateway in Python (fjagepy)

[![PyPI version](https://img.shields.io/pypi/v/fjagepy.svg)](https://pypi.org/project/fjagepy/)
[![Python 3.9+](https://img.shields.io/badge/python-3.9+-blue.svg)](https://www.python.org/downloads/)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](../../LICENSE)

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

# Connect to fjåge container
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

### iPython / Jupyter Notebook Support

In Jupyter notebooks, AgentIDs and Messages are displayed in a human-readable format

```
In [1]: from fjagepy import *

In [2]: gw = Gateway("localhost", 5081)

In [3]: gw.agentForService(Services.SHELL)
Out[3]:
<<< websh >>>

Interactive Groovy shell

[org.arl.fjage.shell.ShellParam]
  language => Groovy
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

Tests require a running fjåge container on `localhost:5081`. You can start one using:

```bash
# In the main fjåge directory
./gradlew --info -PmanualPyTest=true test --tests="org.arl.fjage.test.fjagepyTest"
```

## Examples

See the `examples/` directory an example of how to use the fjagepy gateway.

## Documentation

To build the documentation locally:

```bash
# Install documentation dependencies
pip install -e ".[docs]"
# Build docs
sphinx-build -b html docs/ ../../docs/pydocs/
# View in browser
open ../../docs/pydocs/index.html
```

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

This project is licensed under the BSD 3-Clause License. See the [LICENSE](../../LICENSE) file for details.

## Support

- **Documentation**: [fjåge Documentation](https://fjage.readthedocs.io/)
- **Issues**: [GitHub Issues](https://github.com/org-arl/fjage/issues)
- **Discussions**: [GitHub Discussions](https://github.com/org-arl/fjage/discussions)

## Related Projects

- **fjåge**: [Main fjåge framework](https://github.com/org-arl/fjage)
- **fjåge.js**: [JavaScript gateway](https://github.com/org-arl/fjage/tree/master/gateways/js)
- **Fjåge.jl**: [Julia gateway](https://github.com/org-arl/Fjage.jl)
- **fjåge-c**: [C gateway](https://github.com/org-arl/fjage/tree/master/gateways/c)