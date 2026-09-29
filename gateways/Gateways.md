# fjåge Gateway API specification

All fjåge Gateway implementations should provide the following classes and methods where the language permits. In languages such as C that do not support classes natively, the corresponding methods may be functions on appropriate structures.

## Gateway class

### JSON messages

A fjåge Gateway connects to a fjåge master container and sends and receives messages to and from it. Each gateway contains a gateway agent that handles messages sent to the gateway. A gateway agent must handle messages with the following actions:

- `action: agents`: reply with gateway agent information in the format `{agentIDs: [<>], agentTypes: [<>]}`.
- `action: agentForService`: reply if the gateway agent supports the service, in the format `{agentID: <>}`.
- `action: agentsForService`: reply if the gateway agent supports the service, in the format `{agentIDs: [<>]}`.
- `action: services`: reply with a list of services provided by the gateway agent, empty by default, in the format `{services: []}`.
- `action: containsAgent`: reply with `true` if the gateway agent has the same `agentID` as the one in the message, in the format `{answer: <true/false>}`.
- `action: send`: parse and process the message according to the gateway logic.
- `action: shutdown`: close and stop the gateway.

All gateway agents must use names prefixed with `gateway-`. This prefix is reserved for gateway agents; non-gateway agents must not use it.

A master container may classify a connection presenting exactly one `gateway-`-prefixed agent as a lightweight gateway connection. Once classified:

- The master will not direct directory queries (`agents`, `services`, `agentForService`, `agentsForService`, `containsAgent`) at the connection.
- The gateway agent's name is included in the master's directory listings from the cached classification, so it remains visible to other agents.
- Gateway agents must not register services; the master will not route service queries to gateway connections, so any services registered by a gateway agent would not be discoverable.

If a gateway's agent name collides with an existing agent known to the master, the master sends `action: shutdown` on that connection and closes it. A compliant gateway must terminate on receiving `shutdown` and must not reconnect with the same agent name. The master logs a warning identifying the collision.

### `Gateway()` :: String hostname, Int port, (String settings) -> Gateway

- Creates a gateway connected to the master container specified by the arguments.
- Must accept `String hostname, Int port` arguments for TCP connections.
- May support `String devname, Int baud, String settings` arguments if serial connections are supported.
- Must return `null` or throw an exception if the initial connection to the master container fails.
- May reconnect automatically if an established connection to the master container fails.
- Must NOT set the message's `sentAt` field. The container populates this field when it receives the message.
- Must respond to JSON message `{"alive": true}` from the master container with `{"alive": true}` as soon as possible to indicate that the gateway is alive.

### `getAgentID()` :: Void -> AgentID

- Returns the _AgentID_ associated with the gateway.
- May be implemented as a property `agentID` on the _Gateway_.

### `close()` :: Void -> Void

- Closes the _Gateway_.
- Must send a `{"alive": false}` message to the master container before closing.

### `send()` :: Message -> Boolean

- Sends a _Message_ to the recipient indicated in the message.

### `receive()` :: (Object filter), (Int timeout) -> Message

- Returns a _Message_ received by the gateway agent.
- May accept optional filter and timeout arguments.
- May support a `Message` subclass, or the language-equivalent type, as a filter for messages of a specific class.
- May support a `String id` as a filter for a response to a specific message `id`.
- May support a `Callback` as a user-defined filter function.
- Must not **block** if timeout is 0.
- Must **block** indefinitely if timeout is -1.
- Must **block** for timeout milliseconds otherwise.
- Must default timeout to 0 milliseconds if not specified.

### `request()` :: Message, (Int timeout) -> Message

- Sends a request and waits for a response.
- Must not **block** if timeout is 0.
- Must **block** indefinitely if timeout is -1.
- Must **block** for timeout milliseconds otherwise.
- Must default timeout to 1000 milliseconds if not specified.
- The default timeout may be configurable at the gateway level.

### `agents()` :: (Int timeout) -> [AgentID]

- Finds all agents visible through the gateway.
- Must default timeout to 6000 milliseconds if not specified.
- Returns an array/list.

### `containsAgent()` :: AgentID, (Int timeout) -> Boolean

- Checks if an agent is visible through the gateway.
- Must default timeout to 6000 milliseconds if not specified.

### `services()` :: (Int timeout) -> [String]

- Finds all services visible through the gateway.
- Must default timeout to 6000 milliseconds if not specified.
- Returns an array/list.

### `topic()` :: (AgentID/String topic), (String topic2) -> AgentID

- Returns an object representing a named notification topic for an agent.
- Convenience method that creates an _AgentID_ with a reference to this _Gateway_ object.
- Optional if the language doesn't support self-referencing.
- May ignore the second argument if the first argument is a `String`.
- Must create a topic if the first argument is a `String`.
- Must create an agent topic if the first argument is an `AgentID`.
- Must create a named topic for an agent if the first argument is an `AgentID` and the second argument is a `String`.

### `agent()` :: String -> AgentID

- Returns an object representing a named agent.
- Convenience method that creates an _AgentID_ with a reference to this _Gateway_ object from a `String`.
- Optional if the language doesn't support self-referencing.

### `subscribe()` :: AgentID -> Boolean

- Subscribes the gateway to receive all messages sent to the given topic.

### `unsubscribe()` :: AgentID -> Boolean

- Unsubscribes the gateway from a given topic.

### `agentForService()` :: String service, (Int timeout) -> AgentID

- Finds an agent that provides a named service.
- Must default timeout to 6000 milliseconds if not specified.

### `agentsForService()` :: String service, (Int timeout) -> [AgentID]

- Finds all agents that provide a named service.
- Must default timeout to 6000 milliseconds if not specified.
- Returns an array/list.

### `flush()` :: Void -> Void

- Flushes the gateway's incoming queue.

### `registerMessage()` :: String className, Class messageClass -> Class

- Registers a `Message` subclass for serialization and deserialization under a **fully qualified** class name.
- Should be a static method of `Gateway` in languages that support static methods.
- Serializing an instance of the registered class must set the JSON `clazz` field to `className`.
- Deserializing JSON whose `clazz` field matches `className` must create an instance of `messageClass`.
- Should return the registered class in languages that can return classes.
- Languages that support annotations or decorators may provide `@message` instead of, or in addition to, `registerMessage()`.
- Implementations MAY also support lookup by unqualified class name during deserialization.
- When deserializing a received JSON message, the gateway MUST first look for a registered class whose name matches the JSON `clazz` field.
- If no registered class matches, the gateway MAY use the language's reflection capabilities to find the class by name.
- If the class is still unknown, the gateway MAY use either of these language-dependent fallbacks:
    - Create a generic `Message` and add every field from the JSON message to it, in languages that support arbitrary object fields.
    - Create a `GenericMessage` with a `Map<String, Object>` field containing the fields from the JSON message.
- Whichever fallback is used, the message MUST retain the original `clazz` value as its class name. Re-serializing the message MUST emit the same `clazz` value.
- In languages that support subclassing, `registerMessage()` and equivalent annotations or decorators MAY accept a parent class when `messageClass` cannot represent that relationship itself.

## AgentID class

### `AgentID()` :: String name, (Boolean isTopic) -> AgentID

- Creates an AgentID for an agent or topic.
- Must set `Boolean isTopic` to `false` if unspecified.

### `getName()` :: Void -> String

- Gets the name of the agent or topic.
- May be implemented as a `name` property on the _AgentID_ object.
- May be used to generate a JSON string for serialization.

### `isTopic()` :: Void -> Boolean

- Returns true if the AgentID represents a topic.
- May be implemented as an `isTopic` property on the _AgentID_ object.

### `send()` :: Message -> Void

- Sends a message to the agent represented by this ID.
- Convenience method that sends a _Message_ to the agent represented by this ID.
- Optional if the language doesn't support self-referencing.

### `request()` :: Message -> Message

- Sends a request to the agent represented by this ID and waits up to one second for a response.
- Convenience method that sends a _Message_ to the agent represented by this ID and waits for a response.
- Optional if the language doesn't support self-referencing.

### `<<` :: Message -> Message

- Sends a request to the agent represented by this ID and waits up to one second for a response.
- Optional if the language doesn't support operator overloading.
- Overloads the left shift operator.
- Convenience method that sends a _Message_ to the agent represented by this ID and waits for a response.
- Optional if the language doesn't support self-referencing.

### `get()` :: String name, (Int index) -> Object

- Gets a parameter value from the agent that the AgentID refers to.
- Convenience method that replaces sending a ParameterReq to get a parameter from an agent.
- If `name` is `null`, must return all parameters on the agent, as `ParameterReq` does.
- May be implemented as a property getter on the AgentID object, with the parameter name as the property name and the index as the array index, for example `agent.property[index]`.

### `set()` :: String name, Object value, (Int index) -> Object

- Sets a parameter value on the agent that the AgentID refers to.
- Convenience method that replaces sending a ParameterReq to set a parameter on an agent.
- May be implemented as a property setter on the AgentID object, with the parameter name as the property name and the index as the array index, for example `agent.property[index] = value`.

### Notes

- When serializing an AgentID that represents a topic, its name must be prefixed with `#`.

## MessageClass class, deprecated

### `MessageClass()` :: String -> Class

- Creates an unqualified message class based on a fully qualified name.
- Deprecated. Existing implementations may retain it for compatibility, but new custom message classes must use `registerMessage()` or `@message` where available.

## Message class

### `Message()` :: (Message inReplyTo), (Performative perf) -> Message

- Creates a response message.
- Custom message types should subclass _Message_ and use `registerMessage()` or `@message` where available, so the gateway can serialize and deserialize them with the correct type.

## JSON protocol

- Gateways must support encoding and decoding messages to and from the [fjåge JSON Protocol](https://org-arl.github.io/fjage/protocol.html).

### Custom JSON fields

- Must add a `boolean` field with the suffix `__isComplex` and value `true` if the message contains an array of complex numbers. For example, if `signal` is a complex array, add `signal__isComplex = true` to the JSON message. This applies only to languages that support complex numbers natively.

- fjåge containers may encode numerical arrays in a compressed [base64](https://en.wikipedia.org/wiki/Base64) format. Gateways must support decoding the [compressed base64 representation](https://org-arl.github.io/fjage/protocol.html#json-message-without-base64-encoding-to-transmit-a-signal) of numerical arrays. For example, a numerical array normally encoded in JSON as follows:

```json
"paramValues": {
    "org.arl.unet.nodeinfo.NodeInfoParam.location": [100, 200]
}
```

It may instead use the following JSON structure:

```json
"paramValues": {
    "org.arl.unet.nodeinfo.NodeInfoParam.location": {
        "clazz": "[D",
        "data": "AAAAAAAA8D8AAAAAAAAAQDMzMzMzMwtA"
    }
}
```

The `clazz` field should identify the type of the encoded base64 array.

```
"[B": byte array (Int8)
"[S": short array (Int16)
"[I": integer array (Int32)
"[J": long array (Int64)
"[F": float array (Float32)
"[D": double array (Float64)
```

- Gateways may support encoding numerical arrays in the compressed base64 format if required.

- An `AgentID` must be encoded as a string. If it refers to a topic, the string must be prefixed with `#`. When decoding, fields such as `message.sender` and `message.recipient` may be decoded into AgentID objects if the language supports them.

## Predefined messages

A fjåge Gateway may export predefined message types for the messages defined by fjåge. These are:

- `org.arl.fjage.shell.ShellExecReq`
- `org.arl.fjage.shell.GetFileReq`
- `org.arl.fjage.shell.PutFileReq`
- `org.arl.fjage.shell.DeleteFileReq`
- `org.arl.fjage.param.ParameterReq`
- `org.arl.fjage.shell.GetFileRsp`
- `org.arl.fjage.param.ParameterRsp`
