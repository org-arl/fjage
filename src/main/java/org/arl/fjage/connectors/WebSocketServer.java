package org.arl.fjage.connectors;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

public class WebSocketServer implements Closeable {

    protected int port;
    protected WebServer server = null;
    protected ConnectionListener listener;
    protected String name = null;
    protected Logger log = Logger.getLogger(getClass().getName());

    /**
     * Create a TCP server running on a specified port.
     *
     * @param port TCP port number (0 to autoselect).
     */
    public WebSocketServer(int port, String context, WebServer server, ConnectionListener listener) {
        this.port = port;
        this.server = server;
        this.listener = listener;
        try {
            name = "ws://"+ InetAddress.getLocalHost().getHostAddress()+":"+port+context;
        } catch (UnknownHostException ex) {
            name = "ws://0.0.0.0:"+port+context;
        }
    }

    @Override
    public void close() throws IOException {
//        server.remove();
    }
}
