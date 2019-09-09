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
    ps - list all the agents
    services - lists all services provided by agents
    who - display list of variables in workspace
    run - run a Groovy script
    println - display message on console
    delay - delay execution by the specified number of milliseconds
    shutdown - shutdown the local platform
    logLevel - set loglevel (optionally for a named logger)
    subscribe - subscribe to notifications from a named topic
    unsubscribe - unsubscribe from notifications for a named topic
    export - add specified package/classes to list of imports
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
    shell
    >

GUI shell using Java Swing
--------------------------

The `SwingShell` GUI has been deprecated and no longer available in fjåge 1.5 and above. Use the web-based shell instead.

Web-based shell
---------------

A web-based shell is available for users to access using a browser. An `initrc.groovy` enabling the web shell on port 8080 would look like this::

    import org.arl.fjage.*
    import org.arl.fjage.shell.*
    import org.arl.fjage.connectors.*

    platform = new RealTimePlatform()
    container = new Container(platform)
    WebServer.getInstance(8080).add("/", "/org/arl/fjage/web")
    Connector conn = new WebSocketConnector(8080, "/shell/ws")
    shell = new ShellAgent(new ConsoleShell(conn), new GroovyScriptEngine())
    container.add 'shell', shell
    // add other agents to the container here
    platform.start()

The shell can be accessed by accessing http://localhost:8080 once fjåge is running.

.. tip:: The web-based shell uses the Jetty web server. For this to work, the Jetty classes need to be in the classpath. This is automatically done for you if you use the Maven repository to download fjåge and its dependencies. If you used the quickstart script to start using fjåge, you may have to manually download the Jetty web server jars into the `build/lib` folder.

Shell extensions
----------------

Shell extensions are classes that extend the `org.arl.fjage.shell.ShellExtension` interface, and can be executed in a shell using the agent's `addInitrc()` method or using `run()`. This interface is simply a tag, and does not contain any methods. All public static methods and attributes (except those that contain "`__`" in the name) of the extension class are imported into the shell as commands and constants.

If the extension has a `public static void __init__(ScriptEngine engine)` method, it is executed at startup. If the extension has a public static string attribute called `__doc__` , it is loaded into the documentation system. The documentation system interprets it's inputs as Markdown help snippets. A first level heading provides a top level description for the extension. Individual commands and attributes should be described in sections with second level headings.

An simple Groovy extension example is shown below::

    class DemoShellExt implements org.arl.fjage.shell.ShellExtension {

    static final public String __doc__ = '''\
    # demo - demo shell extension

    This shell extension imports all classes from the package
    "my.special.package" into the shell. In addition, it adds
    a command "hello", which is described below:

    ## hello - say hello to the world

    Usage:
      hello             // say hello
      hello()           // say hello

    Example:
    > hello
    Hello world!!!
    '''

        static void __init__(ScriptEngine engine) {
            engine.importClasses('my.special.package.*')
        }

        static String hello() {
            return 'Hello world!!!'
        }

    }
