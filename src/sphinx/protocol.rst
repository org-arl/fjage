JSON Protocol Specifications
============================

.. highlight:: json

Transport and framing
---------------------

fjåge uses `JSON <https://en.wikipedia.org/wiki/JSON>`_ for communication between remote containers.  The JSON objects are exchanged over some low level networked connection like `TCP <https://en.wikipedia.org/wiki/Transmission_Control_Protocol>`_. Rest of this section defines the protocol of the JSON objects exchanged between the containers.

fjåge containers are connected to each other over some form of networked transport. Typically this is a TCP connection, however, the JSON protocol does not assume any specific behavior of the underlying transport connection. Other transports like Serial-UART may also be used.

Over the networked transport, the containers communicate using `line delimited JSON messages <https://en.wikipedia.org/wiki/JSON_streaming#Line_delimited_JSON>`_. These JSON objects are framed by a newline characters (\\n or \\r or \\r\\n). Each such frame contain a single JSON object which adheres to the JSON as defined in `RFC7159 <https://tools.ietf.org/html/rfc7159>`_, and does not support unescaped new-line characters inside a JSON object. The prettified JSON objects with new-lines are shown in this sections as examples to understand and should be "JSONified" before being used.

JSON object format
------------------

Basics
^^^^^^

fjåge's JSON protocol objects are typically shallow JSON objects. The first level of attributes are typically used by the containers to hold metadata and perform housekeeping tasks such as agent directory service. The attribute message in the JSON object contains the actual message that is exchanged between agents residing in different containers.  We describe the JSON message format below which when sent the task requested and respond with relevant notification JSON message, which the developer must look for and parse carefully. Next, we describe in detail the JSON message format which fjåge understands.

JSON message request/response attributes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

A JsonMessage class is defined in fjåge which support a list of attributes. The attributes supported at the top level of the JSON object are listed below:

* `id` : **String**  - A `UUID <https://en.wikipedia.org/wiki/Universally_unique_identifier>`_ assigned to to each object.

* `action` : **String**  - Denotes the main action the object is supposed to perform. Valid JSON message actions supported are listed here:

  * `agents` - Request for a list of all agents running on the target container.
  * `containsAgent` -  Request to check if a specific container has an agent with a given AgentID running.
  * `services` - Request for a list of all services running on the target container.
  * `agentForService` - Request for AgentID of an agent that is providing a specific service.
  * `agentsForService` - Request for AgentID of all agents that is providing a specific service.
  * `send` - Request to send a payload to the target container.
  * `shutdown` - Request to shutdown the target container.

* `inResponseTo` : **String** - This attribute contains the action to which this object is a response to. A response object will have the exact same id as the original action object.

* `agentID` : **String** - An AgentID. This attribute is populated in objects which are responses to objects requesting the ID of an agent providing a specific service `"action" : "agentForService"`. This field may also be used in objects with `"action" : "containsAgent"` to check if an agent with the given AgentID is running on a target container.

* `agentIDs`: **Array** - This attribute is populated in objects which are responses to objects requesting the IDs of agents providing a specific service with `"action" : "agentsForService"`, or objects which are responses to objects requesting a list of all agents running in a container.

* `agentTypes`: **Array** - This attribute is optionally populated in objects which are responses to objects requesting a list of all agents running in a container. If populated, it contains a list of agent types running in the container, with a one-to-one mapping to the agent IDs in the `"agentIDs"` attribute.

* `service` : **String** - Used in conjunction with `"action" : "agentForService"` and `"action" : "agentsForService"` to query for agent(s) providing this specific service.

* `services`: **Array** - This attribute is populated in objects which are responses to objects requesting the services available with `"action" : "services"`.

* `answer` : **Boolean** - This attribute is populated in objects which are responses to query objects with `"action" : "containsAgent"`.

* `relay` : **Boolean**  - This attribute defines if the target container should relay (forward) the message to other containers it is connected to or not.

* `message` : **Object**  -  This holds two main attributes and is responsible for carrying the main payload. The first field is `clazz` and the second `data`. Note that the ordering of `clazz` and `data`  fields is crucial. The developer must make sure that the `clazz` field comes ahead of  `data` field. The structure and format of this object is discussed here:

  * `clazz` : **String** - A string identifier that identifies the type of the message. This is usually a fully qualified Java class name of the class of that type of message.
  * `data` : **Object** - The main payload containing data and message attributes. This holds the contents of the payload in objects with `"action" : "send"`. The structure and format of this object is discussed here:

    * `data` : **Object** - The main payload containing the data and type of data. **NOTE**: While sending a JSON message, the developer can choose to either follow this format as converting the data to Base64 and specifying the equivalent `clazz` or the data can be directly inserted as an array of numbers without specifying the `clazz` or `data` fields as explained later in the examples section.
      - `clazz` : **String** - This attribute contains the string to identify the type of data being carried by the JSON object. The types that are identified and supported are: `"[F"` - Float, `"[I"` - Integer, `"[D"` - Double, `"[J"` - Long, `"[B"` - Bytestring
      - `data` or `signal` : **Base64 String** - The data is encoded as a Base64 string and populated in this attribute. Either the `data` or `signal` attribute is used depending on the message that is being received or sent.

    * `msgID` : **String** - A `UUID <https://en.wikipedia.org/wiki/Universally_unique_identifier>`_ assigned to each message.
    * `perf` : **String** -  Performative. Defines the purpose of the message. Valid performatives are:

      - `REQUEST` -  Request an action to be performed.
      - `AGREE` - Agree to performing the requested action.
      - `REFUSE` - Refuse to perform the requested action.
      - `FAILURE` - Notification of failure to perform a requested or agreed action.
      - `INFORM` - Notification of an event.
      - `CONFIRM` - Confirm that the answer to a query is true.
      - `DISCONFIRM` - Confirm that the answer to a query is false.
      - `QUERY_IF` - Query if some statement is true or false.
      - `NOT_UNDERSTOOD` - Notification that a message was not understood.
      - `CFP` - Call for proposal.
      - `PROPOSE` - Response for CFP.
      - `CANCEL` - Cancel pending request.

    * `recipient` : **String** - An AgentID of the fjåge agent this message is being addressed to.
    * `sender` : **String** - An AgentID of the fjåge agent this message is being sent by.
    * `inReplyTo` : **String** - A UUID. Included in a reply to another object to indicate that this object is a reply to an object with this id.

Note that not all the above attributes need to be populated in a JSON message. The attributes depend on the task that needs to be executed by the agent running in the target container. Also, the message attribute may have additional attributes depending on the exact message that is being constructed.

Next, we describe some of the basic examples in order to let the developer understand what JSON messages to send and how to construct them for different use cases.

Examples
--------

fjåge has very few built-in messages, since messages are usually introduced by the application built on top of fjåge. We therefore use examples from `UnetStack <http://www.unetstack.net/>`_, a specific application using fjåge, to show how the protocol works. To understand the fields in the messages, the reader is referred to `UnetStack API documentation <https://unetstack.net/javadoc/3.0/>`_.

Simple JSON message to transmit a CONTROL frame:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The JSON message of a `TxFrameReq` message to transmit a `CONTROL` frame is as shown below. The `message` attribute contains the attributes specific to `TxFrameReq` message::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.phy.TxFrameReq",
        "data": {
          "type": 1,
          "data": [ 1, 2, 3],
          "msgID": "a2fbff38-a0fb-4e3a-bf22-ae6cf4642e6b",
          "perf": "REQUEST",
          "recipient": "phy",
          "sender": "MyCustomInterface"
        }
      }
    }

A JSON message sent by UnetStack in response to the JSON message sent is as given below::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.phy.TxFrameNtf",
        "data": {
          "txTime": 3329986666,
          "type": 1,
          "msgID": "dc227a96-4d6e-4b64-9d55-bb108ea338b0",
          "perf": "INFORM",
          "recipient": "MyCustomInterface",
          "sender": "phy",
          "inReplyTo": "a2fbff38-a0fb-4e3a-bf22-ae6cf4642e6b"
        }
      },
      "relay": false
    }

Note that there is a attribute `inReplyTo` populated in the response received which indicates that this JSON message was in reply to the JSON message with exact same `msgID`.

JSON message with byte array to transmit a Datagram:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Now, let us try to send a string `Hello World!` as a bytestring. This demonstrates the use of the `clazz` attribute in the `data` payload::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.DatagramReq",
        "data": {
          "data": {
            "clazz": "[B",
            "data": "aGVsbG8gd29ybGQh"
          },
          "msgID": "8152310b-155d-4303-9621-c610e036b373",
          "perf": "REQUEST",
          "recipient": "phy",
          "sender": "MyCustomInterface"
        }
      }
    }

JSON response sent by UnetStack::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.phy.TxFrameNtf",
        "data": {
          "txTime": 4550354666,
          "type": 1,
          "msgID": "fde91abf-68ac-4a93-b2ae-27d1cee01869",
          "perf": "INFORM",
          "recipient": "MyCustomInterface",
          "sender": "phy",
          "inReplyTo": "8152310b-155d-4303-9621-c610e036b373"
        }
      },
      "relay": false
    }

JSON message with float array to record a baseband signal:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Another example of a data payload with a floating point array is shown in this section. In order to record a baseband signal with 100 baseband samples, we send the following JSON message::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.bb.RecordBasebandSignalReq",
        "data": {
          "recLen": 100,
          "msgID": "28db3bd4-ad14-4d86-b4a0-a2d8ebb3cb65",
          "perf": "REQUEST",
          "recipient": "phy",
          "sender": "MyCustomInterface"
        }
      }
    }

The specific attribute such as `recLen` is message specific which in this case is `RecordBasebandSignalReq` and the relevant supported attributes can be found online at the `UnetStack API documentation`_.

In response to the the JSON message to record baseband samples, the UnetStack sends a JSON message equivalent to the `RxBasebandSignalNtf` message containing the recorded data and is as shown here::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.bb.RxBasebandSignalNtf",
        "data": {
          "rxTime": 4905996833,
          "rssi": -43.190178,
          "adc": 1,
          "signal": {
            "clazz": "[F",
            "data": "vC2OBzxUeQs6EtF4O/ATCLyZgk27pg6hvHqGsTunBuC74ZmUOm/YVrwfOrY7pBx+PEGvhLwbr4q7sk/xvDaq4rtrNG87675VPDtOFjtaAPG6x2lQulmJIDqUZdG8PNRSu+uZGrx4Q4S7ngawu5e0Kju79bC7gT2iOpQ29LvDqvu71/10vI/ndryL2rm8QPqau9ZmrzuRM7c7iNw0PALqJDwFH8+7jePovBiQYrwIpAY8CokaPFNJZDwp/KM7Huoau9u1bDrIGEi6fkjSPAafjTsxGsc7mFnyO4J5D7t9dC66q6MYua+lXjwawm47EzIoufw6ibwQvGS73fHmu47QZTs0Ihs6pwgWO+OLPbuAs9Q7n3duO0e6nzwfAUc8DkaMOur0+zuu/eq5sOUAOs9hMjvMOI+8JT/VO7hmaTqB8lo8SCOZO/dsHzvVkYy7A1s/OxyyTjqff5c7xVBZO3Jh6zsu1l07N1nAO6ljuTvKbu073bpNOlKv2LkAWaC7RBxSOs6XzDujzn06ySzaOwl4ervmgLY6xnA4uq8cUzr5a7Q6XTNnuyhHRTqaGXK7cjwxulmxJbwfxmS70Lu0u1UUZzsKTn27DJXpuk5nPrwj/4+69s6MutZNiDtgdnC7H4Icu8RV0ruvX2y8HjuwuoGU3rvxp4+6iMx2Ou3fsDqvFyc78w5wOuQNwzsrQkM7KgS1OzyTCjuVmvy7ocCnusWG5rv5opU5tc3iuuou/DrEvQa6yUQGOq6sYjsBG0w7fkHyu5A0pTooWri7DpmKur+gyjqvUm+7aDZ3u5P/5jr7IL26vDzSO5oyDDobQxS6tijpOn8iSLmH8OU6mg1KO7LFdLsvemo7KST5up0Kuru2gLe7IXBku+uJULv8Oxu7qNlpu0tVrLs9JHo7ivuWu6YTELrMF3m7hyl0uCyDwDq/OLM7duBXOsxH9DsaGWw75rZoO7rtxDtoJVE6ojTSuwUWuzrrdNw7G5+xPBYPAzq4FRk74HW1OvPaXDuWm3o6siqhOy1MMjqLVtq56BNTujAJ47p7NSw62FKGO2CzBzsAKak="
          },
          "fc": 12000,
          "fs": 12000,
          "channels": 1,
          "preamble": 0,
          "msgID": "7720595f-3512-4f12-8168-6b55da613766",
          "perf": "INFORM",
          "recipient": "MyCustomInterface",
          "sender": "phy",
          "inReplyTo": "28db3bd4-ad14-4d86-b4a0-a2d8ebb3cb65"
        }
      },
      "relay": false
    }

Again it can be observed from the `inReplyTo` attribute that the above JSON message is in reply to the JSON message corresponding to the `RecordBasebandSignalReq`. Also note that this JSON message contains the data recorded as a base64 encoded string and the `clazz` attribute indicates that the actual values are floats. The developer/user can utilize this information and decode  the recoded data from this `data` attribute to a usable format. The other `attributes` that are added to the JSON message in response can be found in the UnetStack online documentation for the `RxbasebandSignalNtf` message.

JSON message without base64 encoding to transmit a signal:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

In general, arrays can be encoded with or without base64 encoding. The base64 encoding is default, as it compresses the message, but it's use is optional. In this section, we show an example without base64 encoding.

A signal can be transmitted using `TxbasebandSignalReq` message in one of the two ways:
  * Without base64 encoding
  * With base64 encoding

JSON message without base64 encoded signal::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.bb.TxBasebandSignalReq",
        "data": {
          "signal": [
            1,
            1,
            1
          ],
          "preamble": 1,
          "msgID": "24078a7f-0054-42c9-a578-99eb7f4c0c07",
          "perf": "REQUEST",
          "recipient": "phy",
          "sender": "MyCustomInterface"
        }
      },
      "relay": true
    }

Equivalent JSON message with base64 encoded signal::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.bb.TxBasebandSignalReq",
        "data": {
          "signal": {
            "clazz": "[F",
            "data": "P4AAAD+AAAA/gAAA"
          },
          "preamble": 1,
          "msgID": "7774ae54-cb34-44c5-b5d0-4de12e2afcba",
          "perf": "REQUEST",
          "recipient": "phy",
          "sender": "MyCustomInterface"
        }
      }
    }

A `TxFrameNtf` is sent in response by UnetStack, the equivalent JSON message of which is as shown below::

    {
      "action": "send",
      "message": {
        "clazz": "org.arl.unet.phy.TxFrameNtf",
        "data": {
          "txTime": 6903128000,
          "type": 0,
          "msgID": "586fb281-8891-4308-8130-74563a8a7365",
          "perf": "INFORM",
          "recipient": "MyCustomInterface",
          "sender": "phy",
          "inReplyTo": "24078a7f-0054-42c9-a578-99eb7f4c0c07"
        }
      },
      "relay": false
    }

The above response is shown when the signal is transmitted without the base64 encoding of the signal. The reader can compare the `msgID` and `inReplyTo` attributes of the corresponding message.
