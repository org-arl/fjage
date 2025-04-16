# Fjåge Connectors

Connectors are used by Fjåge container to connect to using various data transport service. It's an abstraction layer that allows Fjåge to be used with different transport services easily.

## Uses
Currently Fjåge connectors are used for

- [MasterContainer](../MasterContainer.java) to receive connections from Slave/Gateway containers.
- [ShellAgents](../shell/ShellAgent.java) to receive connections for remote shell access.

## Types

Currently, there are two types of connectors supported by Fjåge

- "Normal" Connector - This is the standard connector, where each Connector is connected to a single remote on its specific data transport service. This is the default connector type. For example, a [TcpConnector](TcpConnector.java) is a normal connector that connects to a single TCP socket connection.
- Hub Connector - This type of connector is used to connect to multiple remotes and aggregate the data from them. This is useful for connecting to multiple remotes using a single connector. For example, a [TcpHubConnector](TcpHubConnector.java) is a hub connector that connects to multiple TCP socket connections. This is useful for the Shell Agent where the input streams and output streams between all remote shells consoles and the ShellAgent need to be combined.