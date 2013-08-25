Getting Started
===============

.. highlight:: sh

Quick start
-----------

We assume that you are working on a UNIX (Linux or Mac OS X) system with network access. If you are working on a Windows platform, you will need `Cygwin <http://www.cygwin.com/>`_ installed for the shell script to work. We also assume that you have Java Development Kit (JDK) 1.6 or higher installed. If you don't have one, you'll need to `install <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`_ one.

Create a new folder for your first fjåge project. Lets call it `MyFjageProject`. Open a terminal window in this folder and download the `fjage_quickstart.sh <https://raw.github.com/org-arl/fjage/master/src/sphinx/fjage_quickstart.sh>`_ script in that folder::

    curl -O https://raw.github.com/org-arl/fjage/master/src/sphinx/fjage_quickstart.sh

To get your project ready, just run the script::

    sh fjage_quickstart.sh

The script downloads all necessary libraries (jar files) and some template startup scripts to get you going. You may now delete off the `fjage_quickstart.sh` file, if you like::

    rm fjage_quickstart.sh

Your directory structure should now look something like this:

.. parsed-literal::

    fjage.sh
    logs/
    build/libs/
      fjage-|version|.jar
      groovy-all-2.1.3.jar
      commons-lang3-3.1.jar
      jline-2.10.jar
    etc/
      initrc.groovy

.. note:: The `build/libs` folder contains all the necessary libraries. The `etc` folder contains startup files. `initrc.groovy` in the initialization script where you create your agents and configure them. `fjage.sh` is your startup shell script that simply sets up the classpath and boots up fjåge with the `initrc.groovy` script. The organization of the directory structure and names of the files are all customizable by editing `fjage.sh` and `initrc.groovy`.

To check that your fjåge installation is correctly working, type `./fjage.sh`. That should simply give you an interactive fjåge Groovy shell with a `>` prompt. Type `ps` to see a list of running agents. There should be only one `shell` agent created by the default `initrc.groovy` script. Type `shutdown` or press control-D to terminate fjåge.

.. code-block:: console

    bash$ ./fjage.sh
    > ps
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    > shutdown

    bash$

Hello world agent
-----------------

As any good tutorial does, we start with the proverbial *hello world* agent. The agent isn't going to do much, other than print the words "Hello world!!!" in the logs.

Create a file called `hello.groovy` in your project folder and put the following contents in it:

.. code-block:: groovy

    import org.arl.fjage.*

    class HelloWorldAgent extends Agent {
      void init() {
        add new OneShotBehavior({
          println 'Hello world!!!'
        })
      }
    }

    container.add 'hello', new HelloWorldAgent()

This Groovy script creates an agent with `AgentID` `hello` of class `HelloWorldAgent`. The `init()` method of the agent is called once the agent is loaded. In this method, a one-shot behavior is added to the agent. One-shot behaviors are fired only once, as soon as possible; in our case, this is as soon as the agent is running. The one-shot behavior prints "Hello world!!!". The output of the agent is not directly displayed on the console, but instead sent to the log file, as we will see shortly.

To run the agent, start fjåge and run the script by typing `run 'hello'` or simply `<hello` (the *less than* sign '<' is a shortcut for `run '...'`). This will return you to the interactive shell prompt. To check that your agent is indeed running, type `ps`. You may then shutdown fjåge as before and check the log file for your output:

.. code-block:: console

    bash$ ./fjage.sh 
    > <hello
    > ps
    hello: HelloWorldAgent - IDLE
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    > shutdown

    bash$ cat logs/log-0.txt | grep HelloWorldAgent@
    1377443280802|INFO|HelloWorldAgent@18:println|Hello world!!!
    bash$ 

The default fjåge log file format is pipe-separated, where the first column is the timestamp in milliseconds, the second column is the log level, the third column is the agent class name + threadID + method name, and the last column is the log message. You may change the format if you like by loading a custom logging configuration by specifying a `java.util.logging.config.file` system property while starting the JVM (see `Java logging <http://docs.oracle.com/javase/7/docs/technotes/guides/logging/overview.html>`_).

Congratulations!!! You have just developed your first Groovy fjåge agent!

.. note:: Stack traces for any exceptions caused by any agent will be dumped to the log file. This can be invaluable during debugging.

Packaging agents
----------------

The method shown above defined the agent class in a Groovy script that was executed from the interactive shell. If the Groovy script is modified, the agent can be reloaded by killing it and running the script again:

.. code-block:: console

    bash$ ./fjage.sh
    > <hello
    > ps
    hello: HelloWorldAgent - IDLE
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    > container.kill agent('hello');
    > ps
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    > <hello
    > ps
    hello: HelloWorldAgent - IDLE
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    >

This is useful for testing. However, in a production system, you usually want to define agents in their own files, compile them and package them into a jar on the classpath. To do this, you would create a source file `HelloWorldAgent.groovy` with the class definition:

.. code-block:: groovy

    import org.arl.fjage.*

    class HelloWorldAgent extends Agent {
      void init() {
        add new OneShotBehavior({
          println 'Hello world!!!'
        })
      }
    }

or `HelloWorldAgent.java` with the class definition:

.. code-block:: java

    import org.arl.fjage.*;

    public class HelloWorldAgent extends Agent {
      public void init() {
        add(new OneShotBehavior() {
          public void action() {
            println("Hello world!!!");
          }
        });
      }
    }

You would then compile it into a `HelloWorldAgent.class` file using the `groovyc` compiler (or `javac` compiler) and perhaps package it into a jar file. You would then put this jar file or the class file on the classpath.

The `fjage.sh` startup script includes all jar files from the `build/libs` folder into the classpath. So you could simply copy your jar file into the `build/libs` folder and then run `fjage.sh`. You can then load the agent on the interactive shell:

.. code-block:: console

    bash$ ./fjage.sh
    > ps
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    > container.add 'hello', new HelloWorldAgent();
    > ps
    hello: HelloWorldAgent - IDLE
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    >

If you wanted the agent to be automatically loaded, you can put the `container.add 'hello', new HelloWorldAgent()` statement in the `initrc.groovy` startup script.

Typical bootup for Groovy applications
--------------------------------------

In order to fully understand how fjåge works, it is useful to look at a slightly simplified version of the bootup sequence of our hello world fjåge application. When we run `fjage.sh`, the shell script creates a CLASSPATH to include all jar files in the `build/libs` folder and then starts the JVM::

    java -cp "$CLASSPATH" org.arl.fjage.shell.GroovyBoot etc/initrc.groovy

This command invokes the `main()` static method on the `org.arl.fjage.shell.GroovyBoot` class. The initialization script `etc/initrc.groovy` is passed as a command line argument to the `main()`.

Let us next take a look at a simplified code extract from the `org.arl.fjage.shell.GroovyBoot.main()` method:

.. code-block:: java

    public static void main(String[] args) throws Exception {
      GroovyExtensions.enable();
      engine = new GroovyScriptEngine();
      for (String a: args) {
        engine.exec(new File(a), null);
        engine.waitUntilCompletion();
      }
      engine.shutdown();
    }

.. note:: `GroovyBoot` also supports resource URLs of the form `res://path/to/package/script.groovy` to execute initialization Groovy scripts loaded from Java resources (potentially inside jar files).

This code enables Groovy extensions in fjåge to add syntactic sugar for ease of writing Groovy agents, and then sequentially executes every initialization Groovy script given on the command line. In our case, this causes the `etc/initrc.groovy` to be executed:

.. code-block:: groovy

    import org.arl.fjage.*
    import org.arl.fjage.shell.*

    platform = new RealTimePlatform()
    container = new Container(platform)
    shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
    container.add 'shell', shell
    // add other agents to the container here
    platform.start()

The script imports the fjage packages. A real-time platform and a container is created, and a `shell` agent is configured and added to the container. The `shell` agent is set to provide the interactive shell on the console, and use Groovy for scripting. Finally, the platform is started. Now we have a fjåge container running with a single `shell` agent that provides an interactive shell on the console.

Any other agents that we may wish to start can be included in the `etc/initrc.groovy` script, just before starting the platform.

Bootup for Java applications
----------------------------

If you wanted a pure-Java project, you would forego the scripting ability (since that requires Groovy) and simply setup the platform and container directly from the `main()` program. For example:

.. code-block:: java

    import org.arl.fjage.*;

    public class MyProject {
      public static void main(String[] args) throws Exception {
        Platform platform = new RealTimePlatform();
        Container container = new Container(platform);
        // add your agents to the container here
        // e.g. container.add("hello", new HelloWorldAgent());
        platform.start();
      }
    }

As simple as that!
