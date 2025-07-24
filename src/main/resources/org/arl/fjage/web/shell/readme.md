# Fjåge Web Shell

This is an implementation of a web-based shell for Fjåge, allowing users to interact with the system through a web interface. The shell can be served statically or embedded in other web applications.

## Features
- Interactive terminal interface using xterm.js
- Supports both light and dark color schemes
- Simple API for integration with other web applications

## API

A very simple API is provided for the shell to interact with the host application (if any). The host application is expected to embed the shell in an `<iframe>`.

- The Web shell will set the `window.shellready` variable to `true` when it is connected and ready to use.
- The host application can set the `data-prefers-color-scheme` attribute on the `<iframe>` element to specify the default color scheme for the shell. The shell will automatically switch to the specified color scheme at startup.
- The host application can also send a message to the shell using `window.parent.postMessage({scheme: 'dark'/'light'})` to change the color scheme dynamically. The shell will listen for these messages and update the color scheme accordingly.

> NOTE: The web platform is particular about Same Origin Policy, so the host application must be served from the same origin as the shell will have more freedom to interact with the shell as compared to a cross-origin application.

## Usage

The shell and all it's dependencies are bundled as resources in the fjåge JAR file. To use the shell, you will need to serve the static HTML/CSS/JS files from the JAR and also provide a WebSocket endpoint for the terminal to connect to.

```groovy
WebServer.getInstance(8080).addStatic("/", "/org/arl/fjage/web")          // Serve static files from the JAR
Connector conn = new WebSocketHubConnector(8080, "/shell/ws")             // WebSocket endpoint for the terminal
shell = new ShellAgent(new ConsoleShell(conn), new GroovyScriptEngine())  // Create a ShellAgent with a ConsoleShell
container.openWebSocketServer(8080, "/ws")                                // Open WebSocket server for Connector
```

The served shell can be accessed at `http://localhost:8080/shell/index.html`.

This can also be integrated into other web applications by embedding the shell in an `<iframe>` or similar element. The application will need to use the [API](#api) to interact with the shell and configure it as needed.