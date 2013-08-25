The Shell Agent
===============

.. highlight:: groovy

We have already used the console shell provided by fjåge many times. This shell is implemented by the `ShellAgent` class as we have seen before in the `initrc.groovy` scripts. Let's take a slightly deeper look at the shell agent in this chapter.

Shell commands
--------------

The default shell provided by fjåge is a Groovy shell, and can execute any valid Groovy code. A few standard commands, variables and closures are made available. Just typing `help` will provide a list of commands that are available:

.. code-block:: console

    bash$ ./fjage.sh 
    > help
    help [topic] - provide help on a specified topic
    ps - list all the agents running on the local container
    who - display list of variables in workspace
    run - run a Groovy script
    println - display message on console
    delay - delay execution by the specified number of milliseconds
    shutdown - shutdown the local platform
    logLevel - set loglevel (optionally for a named logger)
    subscribe - subscribe to notifications from a named topic
    unsubscribe - unsubscribe from notifications for a named topic
    shellImport - add specified package/classes to list of imports
    agent - return an agent id for the named agent
    agentForService - find an agent id providing the specified service
    agentsForService - get a list of all agent ids providing the specified service
    send - send the given message
    request - send the given request and wait for a response
    receive - wait for a message
    > 

Further help on an individual topic can be obtained by typing `help` followed by the topic name. You are encouraged to explore the help.

The commands in the shell are executed in the context of the `ShellAgent` (e.g. messages send are send using this agent). Any messages received by the `ShellAgent` are simply displayed.

.. tip:: If you wish to add your own closures or variables, you can do so by customizing initialization script. Initialization scripts can be added to the `ShellAgent` using the `addInitrc` method.

Remote shell over TCP/IP
------------------------

If we wanted to provide a remote shell that users could `telnet` into, rather than a console shell, we would replace `ConsoleShell` with a `TcpShell` and specify a TCP/IP port number that is to provide the interactive shell. Here's what the resulting `initrc.groovy` would look like::

    import org.arl.fjage.*
    import org.arl.fjage.shell.*

    platform = new RealTimePlatform()
    container = new Container(platform)
    shell = new ShellAgent(new TcpShell(8001), new GroovyScriptEngine())
    container.add 'shell', shell
    // add other agents to the container here
    platform.start()

We could then access the shell using `telnet`:

.. code-block:: console

    bash$ telnet localhost 8001
    Trying localhost...
    Connected to localhost
    Escape character is '^]'.
    > ps
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    >
