Messaging
=========

.. highlight:: groovy

Sending and receiving messages
------------------------------

Agents interact with each other using messages. A `Message`_ is usally tagged with a `Performative`_ that defines the purpose of the message, and is uniquely identified by a message identifier. A message is also usually associated with a sender and a recipient `AgentID`. If a message is sent in reply to another message, the original message's message identifier is included as a `inReplyTo` property of the reply message. This allows the sender to associate the reply with the original request/query message.

Although the `Message`_ class provides all the basic attributes of a message, it does not provide any fields to hold the message content. Typical messages will extend the `Message`_ class and add relevant content fields.

Request message::

    class WeatherForecastReq extends org.arl.fjage.Message {
      WeatherForecastReq() {
        super(Performative.REQUEST)
      }
      String city, country
    }

Response message::

    class WeatherForecast extends org.arl.fjage.Message {
      WeatherForecast() {
        super(Performative.INFORM)
      }
      WeatherForecast(Message req) {
        super(req, Performative.INFORM)   // create a response with inReplyTo = req
        city = req.city
        country = req.country
      }
      String city, country
      float minTemp, maxTemp, probRain
    }

A client agent may send a weather forecast request to another agent named "WeatherStation"::

    send new WeatherForecastReq(city: 'London', country: 'UK', recipient: agent('WeatherStation'))

The "WeatherStation" agent would receive the request and send back a reply. Although messages may be received using an agent's `receive()` method, the preferred way to process messages is using the :ref:`msgbehavior`::

    class MyWeatherStation extends org.arl.fjage.Agent {
      void init() {
        add new MessageBehavior(WeatherForecastReq, { req ->
          log.info "Weather forecast request for ${req.city}, ${req.country}"
          def rsp = new WeatherForecast(req)
          rsp.minTemp = 10
          rsp.maxTemp = 25
          rsp.probRain = 0.25
          send rsp
        })
      }
    }

The client agent would then receive the message, either through a message behavior or by explicitly calling `receive()`. An easier alternative is to send a request and wait for the associated response via the `request()` method::

    def req = new WeatherForecastReq(city: 'London', country: 'UK', recipient: agent('WeatherStation'))
    def rsp = request req, 1000         // 1000 ms timeout for reply
    println "The lowest temperature today is ${rsp?rsp.minTemp:'unknown'}"

Generic messages
----------------

Although it usually makes sense to create message classes for specific interactions, there are times when it can be useful to send a generic message with key-value pairs. This functionality is provided by the `GenericMessage`_ class, which provides a `java.util.Map` interface. In Groovy, this provides a nice syntax that allows the keys to work like dynamic attributes of the message. A weather forecast service implemented using generic messages is shown below.

Server code::

    import org.arl.fjage.*

    class MyWeatherStation extends Agent {
      void init() {
        add new MessageBehavior({ msg ->
          if (msg.performative == Performative.REQUEST && msg.type == 'WeatherForecast') {
            log.info "Weather forecast request for ${msg.city}, ${msg.country}"
            def rsp = new GenericMessage(msg, Performative.INFORM)
            rsp.minTemp = 10
            rsp.maxTemp = 25
            rsp.probRain = 0.25
            send rsp
          }
        })
      }
    }

Client code snippet::

    def req = new GenericMessage(agent('WeatherStation'), Performative.REQUEST)
    req.type = 'WeatherForecast'
    req.city = 'London'
    req.country = 'UK'
    def rsp = request req, 1000         // 1000 ms timeout for reply
    println "The lowest temperature today is ${rsp?rsp.minTemp:'unknown'}"

Alternate syntax
----------------

Let us assume we have an `AgentID` for the "WeatherStation"::

    def weatherStation = agent('WeatherStation')

It is sometimes nicer to be able to use a syntax like this::

    weatherStation.send new WeatherForecastReq(city: 'London', country: 'UK')

or::

    def rsp = weatherStation.request new WeatherForecastReq(city: 'London', country: 'UK')

or perhaps even::

    def rsp = weatherStation << new WeatherForecastReq(city: 'London', country: 'UK')

This alternate syntax sometimes yields more readable code, and is supported by fjåge. It is important, however, to remember that the message is sent in the context of the client agent that provided us with the `AgentID`. Any `AgentID` returned by an agent (by methods such as `agent()`, `agentForService()`, etc) is associated with or *owned by* that agent. When this `AgentID` is used with the above syntax, the message is actually sent using the associated agent.

.. note:: If you create an `AgentID` explicitly as `new AgentID('WeatherStation')`, it does not have an owner, and therefore cannot be used with this alternate syntax. It can, however, be used with the original syntax as a recipient for a message.

Publishing and subscribing
--------------------------

So far we have sent messages to recipients whose `AgentID` we know. There are times when we may want to publish a message without explicitly knowing who the recipients are. All agents *subscribing* to the *topic* that we publish on would then receive the published message.

This is supported by fjåge using the messaging constructs we have already encountered. Messages can be sent to topics in the same way that messages are sent to other agents. A topic is simply a special `AgentID`::

    def weatherChannel = topic('WeatherChannel')

Instead of using a `String` for the topic name, it is also possible (and usually recommended) to use Enums::

    enum Topics {
      WEATHER_CHANNEL,
      TSUNAMI_WARNING_CHANNEL
    }

and ::

    def weatherChannel = topic(Topics.WEATHER_CHANNEL)

Agents can subscribe to the topic of interest, typically in their `init()` method::

    subscribe weatherChannel

Messages can be sent to all agents subscribing to the topic::

    def forecast = new WeatherForecast(city: 'London', country: 'UK', minTemp: 10, maxTemp: 25, probRain: 0.25)
    weatherChannel.send forecast

Agents that no longer wish to receive messages on a topic may also unsubscribe from the topic::

    unsubscribe weatherChannel

Cloning messages
----------------

By default, a message delivered to another agent in the same container is the original object, and not a copy. This has some subtle but important implications. If an agent modifies a message after sending it, this can lead to unexpected behaviors.

Let's take an example::

    def msg = new GenericMessage()
    msg.text = 'Hello!'
    agent('Susan').send msg
    msg.text = 'Holla!'
    agent('Lola').send msg

If the message is delivered to Susan before the agent modifies the message, Susan gets a "Hello!" message and then Lola gets a "Holla!" message. If the message is modified after delivery to Susan, but before she has had a chance to read it, both Susan and Lola get a "Holla!" message. If the message is modified and sent to Lola before it is delivered to Susan, the recipient of the message changes, and two copies of "Holla!" get delivered to Lola and nothing gets delivered to Susan. As you can see, the behavior is indeterminate and a debugging nightmare!

Fortunately, there are several simple ways around this:

1. Do not modify a message once it is sent. The code would then look like this::

    def msg = new GenericMessage()
    msg.text = 'Hello!'
    agent('Susan').send msg
    msg = new GenericMessage()        // create a new message, don't modify the old one
    msg.text = 'Holla!'
    agent('Lola').send msg

2. Send a copy of the message, rather than the original. You can then freely modify the original::

    def msg = new GenericMessage()
    msg.text = 'Hello!'
    agent('Susan').send clone(msg)    // send a copy of the message
    msg.text = 'Holla!'
    agent('Lola').send msg

3. Ask the container to always send copies of messages rather than the original, and then you can use the original code without a problem::

    container.autoClone = true

The cloning of the message is accomplished using the `org.apache.commons.lang3.SerializationUtils` class. This performs a deep clone (clones all objects contained in the message) by serializing the entire message, and then deserializing it. This is very portable (as long as your message is `Serializable`), but somewhat slow. A faster deep cloning implementation is available from `com.rits.cloning.Cloner`, but it is less portable (it seems to have trouble dealing with some Groovy messages). If you wish to try this implementation for your application, ensure that you have the following jars in your classpath:

* `cloning-1.9.0.jar <https://repo1.maven.org/maven2/uk/com/robust-it/cloning/1.9.0/cloning-1.9.0.jar>`_
* `objenesis-1.2.jar <https://repo1.maven.org/maven2/org/objenesis/objenesis/1.2/objenesis-1.2.jar>`_

Then switch to using the fast cloner::

    container.cloner = Container.FAST_CLONER

.. Javadoc links
.. -------------
..
.. _Message: http://org-arl.github.io/fjage/javadoc/index.html?org/arl/fjage/Message.html
.. _Performative: http://org-arl.github.io/fjage/javadoc/index.html?org/arl/fjage/Performative.html
.. _GenericMessage: http://org-arl.github.io/fjage/javadoc/index.html?org/arl/fjage/GenericMessage.html
