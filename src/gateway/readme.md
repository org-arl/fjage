fjåge Gateway API Spec
===============

All fjåge Gateway implementations have should implement the following Classes and methods to the best of their abilities. For languages (like C), which may not support classes natively, the corresponding methods may be implemented as functions on appropriate structures.


## Gateway Class

### `Gateway()` :: String hostname, Int port, (String settings) -> Gateway
- Creates a gateway connecting to a specified master container specified by the arguments.
- Must accept `String hostname, Int port` arguments for TCP connections.
- May support `String devname, Int baud, String settings` arguments if Serial connections are supported

#### Status
- Compliant : Julia, C
- Non-compliant : Python (remove name), JS (split url into hostname/port)



### `getAgentID()` :: Void -> AgentID
-  Returns the _AgentID_ associated with the gateway.
-  May be implemented as a property `agentID` on the _Gateway_.

#### Status
- Compliant : Julia, Python, C
- Non-compliant : JS (should return an Object)



### `close()` :: Void -> Void
- Closes the _Gateway_.
- Must send a `{"alive": false}` message to the master container before closing.

#### Status
- Compliant : Julia, Python
- Non-compliant : C (returns an int, doesn't send `{"alive": false}`), JS (returns a boolean)



### `send()` :: Message -> Boolean
- Sends a _Message_ to the recipient indicated in the message.

#### Status
- Compliant : Julia, C
- Non-compliant : Python (remove relay as argument), JS (remove relay as argument, return boolean)



### `receive()` :: (Object filter), (Int timeout) -> Message
- Returns a _Message_ received by the agent.
- May accept optional filter and timeout arguments.
- May support filter of type `MessageClass class` to filter for message of a specific class.
- May support filter of type `String id` to filter for a response to a specific message `id`.
- May support filter of type `Callback` to let user implement a filter function.
- Must not **block** if timeout is 0.
- Must **block** indefinitely if timeout is -1.
- Must **block** for timeout milliseconds otherwise.
- Must default timeout to 1000 millisecond if not speficied.

#### Status
- Compliant : Python
- Non-compliant : C (Needs to not block timeout = 0, this needs to be documented in .h, needs to block for timeout = -1), JS(default timeout should be 0, timeout = -1), Julia (needs to support timeout = -1), Julia (Filter without timeout), C (doesn't support callback type), JS (doesn't support callback type)



### `request()` :: Message, (Int timeout) -> Message
- Sends a request and waits for a response
- Must not **block** if timeout is 0.
- Must **block** indefinitely if timeout is -1.
- Must **block** for timeout milliseconds otherwise.
- Must default timeout to 1000 millisecond if not speficied.

#### Status
- Compliant : Julia, Python, C
- Non-compliant : JS (timeout should be 1000)



### `topic()` :: (AgentID/String topic), (String topic2) -> AgentID
- Returns an object representing a named notification topic for an agent.
- Convinience method to create an _AgentID_ with a reference to this _Gateway_ object.
- Optional if language doesn't support self referencing.
- May ignore the second argument if first argument is a String.
- Must create an topic if the first argument is a `String`.
- Must create an agent topic if first argument is an `AgentID`.
- Must create an named topic for an Agent if first argument is an `AgentID` and second argument is a `String`.

#### Status
- Compliant : Julia, Python, JS, C (no-self referencing)



### `agent()` :: String -> AgentID
- Returns an object representing a named agent.
- Convinience method to create an _AgentID_ with a reference to this _Gateway_ object from a `String`.
- Optional if language doesn't support self referencing.

#### Status
- Compliant: Julia, JS, C (no-self referencing)
- Non-compliant : Python (missing)



### `subscribe()` :: AgentID -> Boolean
- Subscribes the gateway to receive all messages sent to the given topic.

#### Status
- Compliant: Python, C
- Non-compliant : Julia (doesn't return anything), JS (Shouldn't support String param)



### `unsubscribe()` :: AgentID -> Boolean
- Unsubscribes the gateway from a given topic.

#### Status
- Compliant: Python, C
- Non-compliant : JS (Shouldn't support String param)



### `agentForService()` :: String -> AgentID
- Finds an agent that provides a named service.

#### Status
- Compliant: C
- Non-compliant : Python (no need timeout as param), JS (timeout should be 1 sec)


### `agentsForService()` :: String -> [AgentID]
- Finds all agents that provides a named service.
- Returns an array/list.

#### Status
- Compliant: C
- Non-compliant : Python (no need timeout as param), JS (timeout should be 1 sec)



### `flush()` :: Void -> Void
- Flushes the incoming queue in the Gateway

#### Status
- Compliant: Python, Julia
- Non-compliant : Java, JS, C (needs to implement)





## AgentID Class

### `AgentID()` :: String name, (Boolean isTopic) -> AgentID
- Create an agent id for an agent or a topic.
- Must set `Boolean isTopic` to False if unspecified.

#### Status
- Compliant: Julia
- Non-compliant : Python (needs to change gateway to a named argument, change name of gateway property to owner), C (combine fjage_aid_create and fjage_aid_topic), JS(change name of gateway property to owner)



### `getName()` :: Void -> String
- Gets the name of the agent or topic.
- May be implemented as a `name` property on the _AgentID_ object.

#### Status
- Compliant: Julia, JS, Python
- Non-compliant : C (doesn't exist)



### `isTopic()`:: Void -> Bool
- Returns true if the agent id represents a topic.
- May be implemented as a `isTopic` property on the _AgentID_ object.

#### Status
- Compliant: Julia, JS, Python
- Non-compliant : C (doesn't exist)



### `send()` :: Message -> Void
- Sends a message to the agent represented by this id.
- Convinience method to send a _Message_ to an Agent represented by this id.
- Optional if language doesn't support self referencing.

#### Status
- Compliant: C (no-self referencing), Julia, Python, JS


### `request()` :: Message -> Message
- Sends a request to the agent represented by this id and waits for a return message for 1 second.
- Convinience method to send a _Message_ to an Agent represented by this id and wait for a response.
- Optional if language doesn't support self referencing.

#### Status
- Compliant: C (no-self referencing), Julia, Python, JS


### `<<` :: Message -> Message
- Sends a request to the agent represented by this id and waits for a return message for 1 second.
- Optional if the language doesn't support operator overloading.
- Overloads the left shift operator.
- Convinience method to send a _Message_ to an Agent represented by this id and wait for a response.
- Optional if language doesn't support self referencing.

#### Status
- Compliant: C(no operator overloading allowed), JS (no operator overloading allowed), Julia,
- Non-compliant : Python (need to implement at fjage level)



## MessageClass Class

### `MessageClass()` :: String -> Class
- Creates a unqualified message class based on a fully qualified name.

#### Status
- Compliant: Python, Julia,
- Non-compliant : Java, JS, C (need to implement)



## Message Class

### `Message()`:: (Message inReplyTo), (Performative perf) -> Message
- Creates a response message.
- Commonly not used directly, but extended using the _MessageClass_ function to create custom messages.


#### Status
- Non-compliant : JS (needs to take in optional inReplyTo and perf) , C (needs to take in optional inReplyTo), Python (needs to take in optional inReplyTo and perf ), Julia (needs to take in optional inReplyTo)


# Notes

## Unet Libraries
- No UnetGateway
- Must have Parameter Getter/Setter
- Must have Index Parameter Getter/Setter
- Must have Socket interface







