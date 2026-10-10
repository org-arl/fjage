package org.arl.fjage.connectors;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.Callback;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketConnector extends Session.Listener.AbstractAutoDemanding implements Connector {

    private volatile String name = "ws://[closed]";
    private final String context;
    private volatile ConnectionListener listener;
    private volatile boolean closed;
    protected Logger log = Logger.getLogger(getClass().getName());

    private final PseudoInputStream pin = new PseudoInputStream();
    private final WebSocketOutput pout;

    public WebSocketConnector(String context) {
        this.context = context;
        pout = new WebSocketOutput(context, true,
            text -> WebSocketSupport.sendText(getSession(), text, log), this::close, log);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason, Callback callback) {
        close();
        log.finer("WebSocket Connector closed: " + statusCode + " " + reason);
        callback.succeed();
    }

    @Override
    public void onWebSocketOpen(Session session) {
        synchronized (this) {
            if (!closed) {
                super.onWebSocketOpen(session);
                name = "ws://" + WebSocketSupport.address(session) + context;
                pout.start();
            }
        }
        if (closed) {
            session.disconnect();
            return;
        }
        ConnectionListener current = listener;
        try {
            if (current != null) current.connected(this);
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "WebSocket connection listener failed", ex);
            session.disconnect();
            close();
        }
    }

    @Override
    public void onWebSocketError(Throwable cause)  {
        if (cause instanceof org.eclipse.jetty.io.EofException) {
            log.info(cause.toString());
            return;
        }
        log.log(Level.WARNING, "WebSocket error: ", cause);
    }

    @Override
    public void onWebSocketText(String message) {
        try {
            pin.write(message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            Session current = getSession();
            if (current != null) current.disconnect();
            close();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public InputStream getInputStream() {
        return pin;
    }

    @Override
    public OutputStream getOutputStream() {
        return pout;
    }

    @Override
    public boolean isReliable() {
        return true;
    }

    @Override
    public boolean waitOutputCompletion(long timeout) {
        return pout.awaitCompletion(timeout);
    }

    @Override
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    @Override
    public String[] connections() {
        Session current = getSession();
        return current == null || !current.isOpen() ? new String[0] : new String[] { WebSocketSupport.address(current) };
    }

    @Override
    public void close() {
        Session current;
        synchronized (this) {
            if (closed) return;
            closed = true;
            current = getSession();
            name = "ws://[closed]";
        }
        pin.close();
        pout.close();
        if (current != null && current.isOpen()) current.close(1000, null, Callback.NOOP);
    }

    @Override
    public String toString() {
        return name;
    }

}
