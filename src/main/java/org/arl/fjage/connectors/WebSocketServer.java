package org.arl.fjage.connectors;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.logging.Logger;

/**
 * A Web Socket Server. Uses the WebServer to create a jetty WebSocket Server
 * at the given context and port. The server will accept incoming connections and
 * create a new WebSocketConnector and passes them to the listener.
 *
 */

public class WebSocketServer implements WebSocketCreator {

    protected int port;
    protected String context;
    protected ServerSocket sock = null;
    protected ConnectionListener listener;
    protected WebServer server;
    protected ContextHandler handler;
    protected Logger log = Logger.getLogger(getClass().getName());

    /**
     * @throws UncheckedIOException if the web server cannot start or the context is already in use.
     */
    public WebSocketServer(int port, String context, ConnectionListener listener, int maxMsgSize) {
        this.context = context;
        this.port = port;
        this.listener = listener;
        server = WebServer.getInstance(port);
        handler = server.addHandler(context, new WebSocketHandler() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                if (maxMsgSize > 0) factory.getPolicy().setMaxTextMessageSize(maxMsgSize);
                factory.setCreator(WebSocketServer.this);
            }
        });
        if (handler == null) {
            String msg = "Unable to add WebSocket handler at :"+port+context;
            throw new UncheckedIOException(msg, new IOException(msg));
        }
        if (!server.start()) {
            server.removeHandler(handler);
            String msg = "Unable to start web server on port "+port;
            throw new UncheckedIOException(msg, new IOException(msg));
        }
    }

    public WebSocketServer(int port, String context, ConnectionListener listener) {
        this(port, context, listener, -1);
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest, ServletUpgradeResponse servletUpgradeResponse) {
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

    public void close() {
        server.removeHandler(handler);
        handler = null;
        server = null;
    }
}
