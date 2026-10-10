package org.arl.fjage.connectors;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.ServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.WebSocketCreator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.logging.Logger;

/**
 * A Web Socket Server. Uses the WebServer to create a jetty WebSocket Server
 * at the given context and port. The server will accept incoming connections and
 * create a new WebSocketConnector and passes them to the listener.
 *
 */

public class WebSocketServer implements WebSocketCreator, AutoCloseable {

    protected int port;
    protected String context;
    protected volatile ConnectionListener listener;
    protected WebServer server;
    protected ContextHandler handler;
    protected Logger log = Logger.getLogger(getClass().getName());

    /**
     * Create a WebSocket server and add it to a web server running on a given
     * port. If a web server isn't already created, this will start the web server.
     *
     * @throws UncheckedIOException if the web server cannot be started, or the context is already in use.
     */
    public WebSocketServer(int port, String context, ConnectionListener listener, int maxMsgSize) {
        this.context = context;
        this.port = port;
        this.listener = listener;
        server = WebServer.getInstance(port);
        handler = server.addWebSocket(context, this, maxMsgSize);
        if (handler == null) {
            String msg = "Unable to add WebSocket handler at :"+port+context;
            throw new UncheckedIOException(msg, new IOException(msg));
        }
    }

    public WebSocketServer(int port, String context, ConnectionListener listener) {
        this(port, context, listener, -1);
    }

    @Override
    public Object createWebSocket(ServerUpgradeRequest request, ServerUpgradeResponse response, Callback callback) {
        WebSocketConnector ws = new WebSocketConnector(context);
        ws.setConnectionListener(listener);
        return ws;
    }

    public String getPort() {
        return port+"";
    }

    public String getContext() {
        return context;
    }

    @Override
    public void close() {
        WebServer current;
        ContextHandler contextHandler;
        synchronized (this) {
            current = server;
            contextHandler = handler;
            server = null;
            handler = null;
        }
        if (current != null) current.removeHandler(contextHandler);
    }
}
