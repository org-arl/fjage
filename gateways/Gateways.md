# fjåge Gateway API Spec

All fjåge Gateway implementations should implement the following classes and methods to the best of their abilities. For languages (like C), which may not support classes natively, the corresponding methods may be implemented as functions on appropriate structures.

## Gateway Class

### JSON messages

A fjåge Gateway connects to a fjåge master container and sends/receives messages to/from the master container. Each Gateway contains a Gateway Agent, which handles all the messages that are sent to the Gateway. A Gateway Agent must handle messages with these actions :

- `action: agents` : reply with the information about the Gateway Agent in the format `{agentIDs: [<>], agentTypes: [<>]}`.
- `action: agentForService` : reply if the Gateway Agent supports the service in the format `{agentID: <>}`.
- `action: agentsForService` : reply if the Gateway Agent supports the service in the format `{agentIDs: [<>]}`.
- `action: services` : reply with a list (empty by default) of services provided by the Gateway Agent in the format `{services: []}`
- `action: containsAgent` : reply `true` if the Gateway Agent as the same `agentID` as in the message, in the format `{answer: <true/false>}`
- `action: send` : parse and process the messsage as per the Gateway logic.
- `action: shutdown` : close and stop the Gateway

All gateway agents should use names prefixed with `gateway-`.

### `Gateway()` :: String hostname, Int port, (String settings) -> Gateway

- Creates a gateway connecting to a specified master container specified by the arguments.
- Must accept `String hostname, Int port` arguments for TCP connections.
- May support `String devname, Int baud, String settings` arguments if Serial connections are supported.
- Must return a `null` if the connection to master container fails on the first attempt.
- May support auto-reconnect, where, once a connection with the master container is established if the connection fails, the Gateway tries to reconnect automatically.
- Must NOT set the `sentAt` field of the message. This field will be populated by the Container when the message is received.

### `getAgentID()` :: Void -> AgentID

- Returns the _AgentID_ associated with the gateway.
- May be implemented as a property `agentID` on the _Gateway_.

### `close()` :: Void -> Void

- Closes the _Gateway_.
- Must send a `{"alive": false}` message to the master container before closing.

### `send()` :: Message -> Boolean

- Sends a _Message_ to the recipient indicated in the message.

### `receive()` :: (Object filter), (Int timeout) -> Message

- Returns a _Message_ received by the agent.
- May accept optional filter and timeout arguments.
- May support filter of type `MessageClass class` to filter for a message of a specific class.
- May support filter of type `String id` to filter for a response to a specific message `id`.
- May support filter of type `Callback` to let the user implement a filter function.
- Must not **block** if timeout is 0.
- Must **block** indefinitely if timeout is -1.
- Must **block** for timeout milliseconds otherwise.
- Must default timeout to 1000 millisecond if not specified.

### `request()` :: Message, (Int timeout) -> Message

- Sends a request and waits for a response.
- Must not **block** if timeout is 0.
- Must **block** indefinitely if timeout is -1.
- Must **block** for timeout milliseconds otherwise.
- Must default timeout to 1000 millisecond if not specified.

### `topic()` :: (AgentID/String topic), (String topic2) -> AgentID

- Returns an object representing a named notification topic for an agent.
- Convenience method to create an _AgentID_ with a reference to this _Gateway_ object.
- Optional if the language doesn't support self-referencing.
- May ignore the second argument if the first argument is a `String`.
- Must create a topic if the first argument is a `String`.
- Must create an agent topic if the first argument is an `AgentID`.
- Must create a named topic for an Agent if the first argument is an `AgentID` and second argument is a `String`.

### `agent()` :: String -> AgentID

- Returns an object representing a named agent.
- Convenience method to create an _AgentID_ with a reference to this _Gateway_ object from a `String`.
- Optional if the language doesn't support self-referencing.

### `subscribe()` :: AgentID -> Boolean

- Subscribes the gateway to receive all messages sent to the given topic.

### `unsubscribe()` :: AgentID -> Boolean

- Unsubscribes the gateway from a given topic.

### `agentForService()` :: String -> AgentID

- Finds an agent that provides a named service.

### `agentsForService()` :: String -> [AgentID]

- Find all agents that provides a named service.
- Returns an array/list.

### `flush()` :: Void -> Void

- Flushes the incoming queue in the `Gateway`.

## AgentID Class

### `AgentID()` :: String name, (Boolean isTopic) -> AgentID

- Create an agent id for an agent or a topic.
- Must set `Boolean isTopic` to `False` if unspecified.

### `getName()` :: Void -> String

- Gets the name of the agent or topic.
- May be implemented as a `name` property on the _AgentID_ object.
- Can be used to generate a JSON string for serialization.

### `isTopic()`:: Void -> Bool

- Returns true if the agent id represents a topic.
- May be implemented as a `isTopic` property on the _AgentID_ object.

### `send()` :: Message -> Void

- Sends a message to the agent represented by this id.
- Convenience method to send a _Message_ to an Agent represented by this id.
- Optional if the language doesn't support self-referencing.

### `request()` :: Message -> Message

- Sends a request to the agent represented by this id and waits for a return message for 1 second.
- Convenience method to send a _Message_ to an Agent represented by this id and wait for a response.
- Optional if the language doesn't support self-referencing.

### `<<` :: Message -> Message

- Sends a request to the agent represented by this id and waits for a return message for 1 second.
- Optional if the language doesn't support operator overloading.
- Overloads the left shift operator.
- Convenience method to send a _Message_ to an Agent represented by this id and wait for a response.
- Optional if the language doesn't support self-referencing.

### `get()` :: String name, (int index) -> Object

- Gets the value of a parameter on the Agent that the AgentID refers to.
- Convenience method to replace doing a ParameterReq to get a parameter from an Agent.
- If name is `null`, the all the parameters on the Agent must be returned (similar to ParameterReq's behavior).
- May be implemented as a getter for a property the AgentID object, with the parameter name being the property name, and the index being the array index (for e.g. `agent.property[index]`), if the language supports it.

### `set()` :: String name, Object value, (int index),  -> Object

- Sets the value of a parameter on the Agent that the AgentID refers to.
- Convenience method to replace doing a ParameterReq to set a parameter on an Agent.
- May be implemented as a setter for a property the AgentID object, with the parameter name being the property name, and the index being the array index (for e.g. `agent.property[index] = value`), if the language supports it.

### Notes

- When serializing an AgentID, a `#` must be prepended to the AgentID name if the AgentID is a topic.

## MessageClass Class

### `MessageClass()` :: String -> Class

- Creates a unqualified message class based on a fully qualified name.

## Message Class

### `Message()`:: (Message inReplyTo), (Performative perf) -> Message

- Creates a response message.
- Commonly not used directly, but extended using the _MessageClass_ function to create custom messages.

## JSON Protocol

- Gateways must support encoding and decoding Messages to and from the [fjåge JSON Protocol](https://fjage.readthedocs.io/en/latest/protocol.html).

### Custom JSON fields

- Must add a `boolean` true field with a suffix `__isComplex` if the message contains any arrays of complex numbers. For example, if a field `signal` is a complex array, a field `signal__isComplex = true` is added to the JSON message. This is only applicable for languages that support complex numbers natively.

- Numerical arrays may be encoded by Fjåge Containers in a compressed [base64](https://en.wikipedia.org/wiki/Base64) format. The Gateways must support decoding the [compressed base64 representation](https://fjage.readthedocs.io/en/latest/protocol.html#json-message-without-base64-encoding-to-transmit-a-signal) of numerical arrays. For example, a numerical array which would normally be encoded in JSON as follows :

```json
"paramValues": {
    "org.arl.unet.nodeinfo.NodeInfoParam.location": [100, 200]
}
```

It may be encoded using the following JSON structure :

```json
"paramValues": {
    "org.arl.unet.nodeinfo.NodeInfoParam.location": {
        "clazz": "[D",
        "data": "AAAAAAAA8D8AAAAAAAAAQDMzMzMzMwtA"
    }
}
```

The `clazz` field should be set based on the type of the base64 array is being encoded.

```
"[B" : byte array (Int8)
"[S" : short array (Int16)
"[I" : integer array (Int32)
"[J" : long array (Int64)
"[F" : float array (Float32)
"[D" : double array (Float64)
```

- The Gateways may support encoding numerical arrays in the compressed base64 format if required.

- An `AgentID` must be encoded as a String, if the `AgentID` refers to a topic, then the string should be prefixed with a `#`. When decoding, fields like `message.sender` and `message.recipient` maybe decoded into AgentID data types if they're available in the language.

## Pre-defined Messages

A fjåge Gateway may export pre-defined Message Types for the Messages defined by fjåge. These are :

- `org.arl.fjage.shell.ShellExecReq`
- `org.arl.fjage.shell.GetFileReq`
- `org.arl.fjage.shell.PutFileReq`
- `org.arl.fjage.param.ParameterReq`
- `org.arl.fjage.shell.GetFileRsp`
- `org.arl.fjage.param.ParameterRsp`
