Getting Started
===============

Quick start
-----------

We assume that you are working on a UNIX (Linux or Mac OS X) system with network access. If you are working on a Windows platform, you may need `Cygwin <http://www.cygwin.com/>`_ installed.

.. note:: Although one would expect Java to work across platforms, fjåge has not been fully tested under Windows. If you encounter any problems, please report them via the `issue tracking system <http://github.com/org-arl/fjage/issues>`_.

Create a new folder for your first fjåge project. Lets call it `MyFjageProject`. Open a terminal window in this folder and download the `fjage_quickstart.sh <https://raw.github.com/org-arl/fjage/master/src/sphinx/fjage_quickstart.sh>`_ script in that folder::

    curl -O https://raw.github.com/org-arl/fjage/master/src/sphinx/fjage_quickstart.sh

To get your project ready, just run the script::

    sh fjage_quickstart.sh

The script downloads all necessary libraries (JAR files) and some template startup scripts to get you going. You may now delete off the `fjage_quickstart.sh` file, if you like::

    rm fjage_quickstart.sh

Your directory structure should now look something like this::

    fjage.sh
    logs/
    build/libs
      cloning-1.9.0.jar
      commons-lang3-3.1.jar
      fjage-1.0.1.jar
      groovy-all-2.1.2.jar
      jline-2.10.jar
      objenesis-1.2.jar
    etc/
      initrc.groovy
      logging.properties
      fjageshrc.groovy

The `build/libs` folder contains all the necessary libraries. The `etc` folder contains startup files. `initrc.groovy` in the initialization script where you create your agents and configure them. `logging.properties` is a standard `Java logging <http://docs.oracle.com/javase/7/docs/technotes/guides/logging/overview.html>`_ configuration file that controls the logs from your project. `fjageshrc.groovy` is an initialization script for the interactive shell. `fjage.sh` is your startup shell script that simply sets up the CLASSPATH and boots up fjåge with the `initrc.groovy` script. The organization of the directory structure and names of the files are all customizable by editing `fjage.sh` and `initrc.groovy`.

To check that your fjåge installation is correctly working, type `./fjage.sh`. That should simply give you an interactive fjåge Groovy shell with a `$` prompt. Type `ps` to see a list of running agents. There should be only one `shell` agent created by the default `initrc.groovy` script. Type `shutdown` or press control-D to terminate fjåge. ::

    bash$ ./fjage.sh
    $ ps
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    $ shutdown

    bash$

Hello world agent
-----------------

As any good tutorial does, we start with the proverbial *hello world* agent. The agent isn't going to do much, other than print the words "Hello world!!!" in the logs.

Create a file called `hello.groovy` in your project folder and put the following contents in it::

    import org.arl.fjage.*

    class HelloWorldAgent extends Agent {
      void init() {
        add oneShotBehavior {
          println 'Hello world!!!'
        }
      }
    }

    container.add 'hello', new HelloWorldAgent()

This Groovy script creates an agent with AgentID `hello` of class `HelloWorldAgent`. The `init()` method of the agent is called once the agent is loaded. In this method, a one-shot behavior is added to the agent. One-shot behaviors are fired only once, as soon as possible; in our case, this is as soon as the agent is running. The one-shot behavior prints "Hello world!!!". The output of the agent is not directly displayed on the console, but instead sent to the log file, as we will see shortly.

To run the agent, start fjåge and run the script by typing `run 'hello'` or simply `<hello`. This will return you to the interactive shell prompt. To check that your agent is indeed running, type `ps`. You may then shutdown fjåge as before and check the log file for your output::

    bash$ ./fjage.sh 
    $ <hello
    $ ps
    hello: HelloWorldAgent - IDLE
    shell: org.arl.fjage.shell.ShellAgent - IDLE
    $ shutdown

    bash$ cat logs/log-0.txt | grep HelloWorldAgent@
    1365092640082|INFO|HelloWorldAgent@18|Hello world!!!
    bash$ 

The default fjåge log file format is pipe-separated, where the first column is the timestamp in milliseconds, the second column is the log level, the third column is the agent class name and threadID, and the last column is the log message. You may change the format if you like by editing the `logging.properties` file in the `etc` folder.

Congratulations!!! You have just developed your first Groovy fjåge agent!
