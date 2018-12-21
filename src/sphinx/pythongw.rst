Python Gateway
==============

.. highlight:: python

Introduction
------------
This python package provides a `Gateway` class to interact with the fjåge agents. The fjåge agents reside in one or more containers that provide agent management, directory and messaging services. Various containers may run on the same node or on different nodes in a network. fjåge uses JSON for communication between remote containers. The JSON objects are exchanged over some low level networked connection protocol like TCP. This python `Gateway` class allows the developers to interact with fjåge agents using an interface implemented in python. The python APIs use the package `fjagepy`.

The first step is to install the `fjagepy` package using::

    pip install fjagepy

Import all the modules from `fjagepy` package::

    from fjagepy import *

Importing message classes
-------------------------

As part of the `fjagepy` package a `MessageClass` method is provided which can be used by the user to dynamically create specific message classes. An example of such is shown::

    ShellExecReq = MessageClass('org.arl.fjage.shell.ShellExecReq')

The `ShellExecReq` class can now be used to instantiate new objects like::

    msg = ShellExecReq()

The fully qualified class name as a string must be provided as an argument to this method. The fully qualified class names that are already supported by fjåge are documented `here <http://org-arl.github.io/fjage/javadoc/>`_. 

Open a connection
-----------------

If a fjage server is running, we can create a connection using `Gateway` class::

    gw = Gateway(hostname, port)

where `hostname` and `port` is the IP address and the port number of the device on which the fjåge server is running. The `gw` object is created which can be used to call the methods of `Gateway` class.

Sending and receiving messages 
------------------------------

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
    shell = gw.agentForService("org.arl.fjage.shell.Services.SHELL")
    req = ShellExecReq(recipient=shell, cmd = 'ps')
    rsp = gw.request(req, 1000)
    print(rsp)
    gw.close()

.. note:: An alternative way to add the recipient is req = ShellExecReq(cmd = 'ps'); req.recipient = shell.name. The difference here is the way the recipient is added.

First the connection is opened to the fjåge server. Next, we need to construct a message which can be used to request doing a task which in this case is executing a shell comamnd. Now in order to do that we need `ShellExecReq` message class. The `ShellExecReq` class is used to run various shell commands using python gateway. Therefore, in the sencond line of the code we create a `ShellExecReq` message class. Next, we need to send this message to an agent which supports serving the `ShellExecReq` messages. We find that by looking for an agent which provides `SHELL` services. This is achieved in the third step using `agentForService` method of the gateway class. Next step is to create a message to send. The `ShellExecReq` message is created. The recipient of this message is the `shell` agent running in the master container and therefore the receipient for this message is set to `shell`. The command that we want to run is `ps`. The 'ps' command on the 'shell' agent running on master container will list all the active agents running. 

Generic messages
----------------

As the use case of `GenericMessage` is already explained before, we will illustrate it's use using the python gateway API::

    gw = Gateway(hostname, port)
    shell = gw.agentForService("org.arl.fjage.shell.Services.SHELL")
    gmsg = GenericMessage(recipient=shell, text='hello', data=np.random.randint(0,9,(100)))
    gw.send(gmsg)

The shell agent running on the server side will receive this generic message sent through gateway::

.. code-block:: groovy

    rgmsg = receive(GenericMessage, 1000)
    println rgmsg.text
    println rgmsg.data


Publishing and subscribing
--------------------------

We know that there are times when we may want to publish a message without explicitly knowing who the recipients are. All agents subscribing to the topic that we publish on would then receive the published message. For example::

    gw.topic('abc')

returns an object representing the named topic. A user can subscribe to this topic using::

    gw.subscribe(gw.topic('abc'))

But if we are interested in receiving all the messages sent from a particular agent whose `AgentID` we know (for example `shell`), then::

    shell = gw.agentForService("org.arl.fjage.shell.Services.SHELL")
    gw.subscribe(shell)

will allow to receive the published messages by `shell` agent.


Close a connection:
-------------------

In order to close the connection to the fjåge server, we can call the `close` method provided by the `Gateway` class::

    gw.close()












