fjåge Gateway API Spec
===============

All fjåge Gateway implementations should implement the following classes and methods to the best of their abilities. For languages (like C), which may not support classes natively, the corresponding methods may be implemented as functions on appropriate structures.


## Gateway Class

### `Gateway()` :: String hostname, Int port, (String settings) -> Gateway
- Creates a gateway connecting to a specified master container specified by the arguments.
- Must accept `String hostname, Int port` arguments for TCP connections.
- May support `String devname, Int baud, String settings` arguments if Serial connections are supported

### `getAgentID()` :: Void -> AgentID
-  Returns the _AgentID_ associated with the gateway.
-  May be implemented as a property `agentID` on the _Gateway_.

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
- Sends a request and waits for a response
- Must not **block** if timeout is 0.
- Must **block** indefinitely if timeout is -1.
- Must **block** for timeout milliseconds otherwise.
- Must default timeout to 1000 millisecond if not specified.

### `topic()` :: (AgentID/String topic), (String topic2) -> AgentID
- Returns an object representing a named notification topic for an agent.
- Convenience method to create an _AgentID_ with a reference to this _Gateway_ object.
- Optional if the language doesn't support self-referencing.
- May ignore the second argument if the first argument is a String.
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
- Finds all agents that provides a named service.
- Returns an array/list.

### `flush()` :: Void -> Void
- Flushes the incoming queue in the Gateway



## AgentID Class

### `AgentID()` :: String name, (Boolean isTopic) -> AgentID
- Create an agent id for an agent or a topic.
- Must set `Boolean isTopic` to False if unspecified.

### `getName()` :: Void -> String
- Gets the name of the agent or topic.
- May be implemented as a `name` property on the _AgentID_ object.

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



## MessageClass Class

### `MessageClass()` :: String -> Class
- Creates a unqualified message class based on a fully qualified name.



## Message Class

### `Message()`:: (Message inReplyTo), (Performative perf) -> Message
- Creates a response message.
- Commonly not used directly, but extended using the _MessageClass_ function to create custom messages.
