Python Gateway
==============

.. highlight:: python

Introduction
------------

This Python package provides a `Gateway` class to interact with the fjåge agents. The fjåge agents reside in one or more containers that provide agent management, directory and messaging services. Various containers may run on the same node or on different nodes in a network. This Python `Gateway` class allows the external Python scripts to interact with fjåge agents using an interface implemented in Python. The Python APIs use the package `fjagepy`.

The first step is to install the `fjagepy` package using::

    pip install fjagepy

Import all the necessary symbols from `fjagepy` package::

    from fjagepy import Gateway, AgentID, Message, MessageClass, GenericMessage, Performative

or just::

    from fjagepy import *

Import message classes
----------------------

Since the Java/Groovy message classes are not directly available in Python, we use the `MessageClass` utility to dynamically create specified message classes. An example of such is shown::

    ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq')

The `ShellExecReq` class can now be used to instantiate new objects like::

    msg = ShellExecReq()

The fully qualified class name as a string must be provided as an argument to this method. The fully qualified class names that are already supported by fjåge are documented `here <http://org-arl.github.io/fjage/javadoc/>`_.

Open a connection
-----------------

If a fjage server is running, we can create a connection using `Gateway` class::

    gw = Gateway(hostname, port)

where `hostname` and `port` is the IP address and the port number of the device on which the fjåge server is running. The `gw` object is created which can be used to call the methods of `Gateway` class.

Send and receive messages
-------------------------

We have seen earlier that the agents interact with each other using messages. The python gateway can similarly send and receive messages to the agents running on containers running on diffeent machines. An example of request and response message are as shown below:

Request message::

    msg = Message()
    msg.recipient = 'abc'
    gw.send(msg)

where `'abc'` is the name of the agent you are trying to send the message to.

Another alternative to send a message is following::

    msg = Message(recipient = 'abc')
    rsp = gw.request(msg, timeout)

In the above code snippet, a request method is used to send a message and receive the response back. Different responses that can be received are documented `here <http://org-arl.github.io/fjage/javadoc/>`_.

`msg` is an instance of `Message` class and in the ablove example, the intended recipient is set to an agent with name 'abc'. The constructed message `msg` can sent to the agents running on master container using `gw.send(msg)`.

A simple example of executing a shell command from remote connection opened using Gateway class is as shown below::

    gw = Gateway(hostname, port)
    ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq')
    shell = gw.agentForService('org.arl.fjage.shell.Services.SHELL')
    req = ShellExecReq(recipient=shell, cmd = 'ps')
    rsp = gw.request(req, 1000)
    print(rsp)
    gw.close()

In the code above, we first open a connection to the fjåge server. Next, we import the `ShellExecReq` message that we will require later. We want to send this message to an agent which supports the `SHELL` service (honoring the `ShellExecReq` messages). The `agentForService` method of the `Gateway` class allows us to look up that agent. Next, we construct the `ShellExecReq` message to request execution of a shell command (in this case `ps`). The `request` method then sends the message and waits for a response, which we then print and close the connection.

Generic messages
----------------

As the use case of `GenericMessage` is already explained before, we will illustrate it's use using the Python gateway API::

    gw = Gateway(hostname, port)
    shell = gw.agentForService('org.arl.fjage.shell.Services.SHELL')
    gmsg = GenericMessage(recipient=shell, text='hello', data=np.random.randint(0,9,(100)))
    gw.send(gmsg)

The shell agent running on the server side will receive this generic message sent through gateway::

    rgmsg = receive(GenericMessage, 1000)
    println rgmsg.text
    println rgmsg.data


Publish and subscribe
---------------------

We know that there are times when we may want to publish a message without explicitly knowing who the recipients are. All agents subscribing to the topic that we publish on would then receive the published message. For example::

    gw.topic('abc')

returns an object representing the named topic. A user can subscribe to this topic using::

    gw.subscribe(gw.topic('abc'))

But if we are interested in receiving all the messages sent from a particular agent whose `AgentID` we know (for example `shell`), then::

    shell = gw.agentForService('org.arl.fjage.shell.Services.SHELL')
    gw.subscribe(shell)

will allow to receive the published messages by `shell` agent.


Close a connection:
-------------------

In order to close the connection to the fjåge server, we can call the `close` method provided by the `Gateway` class::

    gw.close()
