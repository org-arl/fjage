Distributed Agents
==================

.. highlight:: groovy

Master and slave containers
---------------------------

Once we have developed our agents, it is easy to deploy them on multiple nodes as necessary. To do so, we require one `MasterContainer`_ in our application, and any number of `SlaveContainer`_. The master container must be started first, and the slave containers connect to it.

To start a master container, we simply replace `Container` with `MasterContainer` in the `initrc.groovy`::

    import org.arl.fjage.*
    import org.arl.fjage.rmi.*

    GroovyAgentExtensions.enable()
    platform = new RealTimePlatform()
    container = new MasterContainer(platform, name)
    println "Master container started at ${container.getURL()}"
    // add agents to the container here
    platform.start()

Specifying the `name` for the master container is optional, but recommended. Any `String` can be used as the container name.

To start slave containers, we need to specify the Java RMI URL of the master container. Typically, the master container URL is of the form `//hostname:port/fjage/name` and the `port` usually is 1099. ::

    import org.arl.fjage.*
    import org.arl.fjage.rmi.*

    GroovyAgentExtensions.enable()
    platform = new RealTimePlatform()
    container = new SlaveContainer(platform, masterURL)
    // add agents to the container here
    platform.start()

That's it! We can deploy agents on any of the containers in the system, and they can interact with agents from other containers transparently.

Remote console
--------------

It is often useful to connect a console shell to a running fjåge application to monitor, interrogate or modify it. To do this, we ensure that the application is running in a master container (and possible some slave containers). We then create a `rconsole.sh`:

.. code-block:: sh

    #!/bin/sh

    CLASSPATH=`find build/libs -name *.jar -exec /bin/echo -n :'{}' \;`
    java -cp "$CLASSPATH" -Djava.util.logging.config.file=etc/logging.properties org.arl.fjage.shell.GroovyBoot -Durl="$1" etc/initrc-rconsole.groovy

and `etc/initrc-rconsole.groovy`::

    import org.arl.fjage.*
    import org.arl.fjage.rmi.*
    import org.arl.fjage.shell.*

    String url =  System.properties.getProperty('url')
    if (url == null || url.length() == 0) {
      System.out.println 'Master container URL not specified'
      System.exit(1)
    }
    platform = new RealTimePlatform()
    container = new SlaveContainer(platform, url)
    shell = new ShellAgent(new ConsoleShell(), new GroovyScriptEngine())
    container.add 'rshell', shell
    platform.start()

The shell script passes the URL specified on the command line to the initialization Groovy script, that connects to the master container at that URL and offers a local console shell for the user to interact. Assuming you have a fjåge application called "myApp" running locally, you can connect to it:

.. code-block:: sh

    ./rconsole.sh //localhost:1099/fjage/myApp

Interacting with agents using a Gateway
---------------------------------------

Only agents may access messaging and related functionality provided by fjåge. For example, non-agent Java or Groovy threads cannot send messages to, or receive messages. To aid interaction of such threads with agents, fjåge provides a `Gateway`_ class. This class provides agent-like functionality to non-agent threads by creating a proxy agent in a slave container that has access to this functionality. Using the `Gateway` is fairly simple::

    Platform platform = new RealTimePlatform()
    Gateway gw = Gateway(platform, masterURL)
    def weatherStation = gw.agentForService Services.WEATHER_FORECAST_SERVICE
    def rsp = gw.request new WeatherForecastReq(city: 'London', country: 'UK', recipient: weatherStation)
    println "The lowest temperature today is ${rsp?rsp.minTemp:'unknown'}"
    gw.shutdown()

.. Javadoc links
.. -------------
..
.. _MasterContainer: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/rmi/MasterContainer.html
.. _SlaveContainer: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/rmi/SlaveContainer.html
.. _Gateway: http://org-arl.github.com/fjage/javadoc/index.html?org/arl/fjage/rmi/Gateway.html
