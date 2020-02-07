Javascipt Gateway
=================

Introduction
------------

The Javascript Gateway allows web applications to access fjåge agents. While the Javascript API is very similar to the Python Gateway API, there are a few additional steps required to setup the web services needed for the Javascript API to work. Being limited by the single threaded browser model, the Javascript API uses promises and callbacks to deliver results from APIs that may incur latency.

Enable the web sockets connector
--------------------------------

First the web sockets connector has to be enabled (let's say on port 8080), so that fjåge can be accessed over web sockets from a browser:

.. code-block:: groovy

    import org.arl.fjage.*
    import org.arl.fjage.shell.*
    import org.arl.fjage.connectors.*

    platform = new RealTimePlatform()
    container = new Container(platform)
    def websvr = org.arl.fjage.connectors.WebServer.getInstance(8080)
    websvr.add('/fjage', '/org/arl/fjage/web')
    // add any other contexts needed to serve your application here
    container.addConnector(new WebSocketConnector(8080, "/ws", true))
    container.add 'shell', shell
    // add other agents to the container here
    platform.start()

Use the Javascript module
-------------------------

It is easiest to illustrate the use of the Javascript API though a simple code example:

.. code-block:: javascript

    import { Gateway, MessageClass, Performative } from '/fjage/fjage.js';

    var gw = new Gateway();

    MessageClass('org.arl.fjage.shell.ShellExecReq');

    gw.agentForService('org.arl.fjage.shell.Services.SHELL').then((aid) => {
        shell = aid;
        gw.subscribe(gw.topic(shell));
        makeRq(shell);
    }).catch((ex) => {
        console.log('Could not find SHELL: '+ex);
    });

    gw.addMessageListener((msg) => {
        console.log(msg);
        return false;
    });

    function makeRq(shell) {
        let req = new ShellExecReq();
        req.recipient = shell;
        req.cmd = 'ps';
        gw.request(req).then((msg) => {
            console.log(msg);
        }).catch((ex) => {
            console.log('Could not execute command: '+ex);
        });
    }

This code first opens a gateway through the web socket interface back to the web server that served this Javascript. It then imports the `org.arl.fjage.shell.ShellExecReq` message class, and looks for an agent providing the `org.arl.fjage.shell.Services.SHELL` service. If found, it subscribes to messages from that service and calls `makeRq()` to make a command execution request to the agent providing that service. The request is to execute a command `"ps"` and simply log the response to the browser's console.

.. The user should refer to the `detailed API description <http://org-arl.github.com/fjage/jsdoc/>`_ for the Javascript API for more information.
