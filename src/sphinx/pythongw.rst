Python Gateway
==============

.. highlight:: python

Introduction
------------
This python package provides a `Gateway` class to interact with the fjåge agents. The fjåge agents reside in one or more containers that provide agent management, directory and messaging services. Various containers may run on the same node or on different nodes in a network. fjåge uses JSON for communication between remote containers. The JSON objects are exchanged over some low level networked connection protocol like TCP. This python `Gateway` class allows the developers to interact with fjåge agents using an interface implemented in python. The python APIs use the package `fjagepy`.

Import all the modules from fjagepy package::

    from fjagepy import *

Create a connection using `Gateway` class::

    gw = org_arl_fjage_remote.Gateway(hostname, port)

The `gw` object is created which can be used to call the methods of `Gateway` class.

Sending and receiving messages::

    msg = org_fjage_arl.Message()
    msg.recipient = 'abc'
    gw.send(msg)

`msg` is an instance of `fjagepy.org_arl_fjage.Message` class and in the ablove example, the intended recipient is set to an agent with name 'abc'. The constructed message `msg` can sent to the agents running on master container using `gw.send(msg)`.

Executing shell commands::

    req = org_arl_fjage_shell.ShellExecReq()
    req.recipient = gw.agentForService("org.arl.fjage.shell.Services.SHELL")
    req.cmd = 'ps'
    gw.send(req)

The 'ps' command on the 'shell' agent running on master container will list all the active agents running. The `ShellExecReq` class can be used to run various shell commands using python gateway.




