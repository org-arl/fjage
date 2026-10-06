package org.arl.fjage.connectors;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.Callback;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketConnector implements Connector, Session.Listener {

    private volatile String name = "ws://[closed]";
    private final String context;
    private volatile Session session;
    private ConnectionListener listener;
    protected Logger log = Logger.getLogger(getClass().getName());

    private final PseudoInputStream pin = new PseudoInputStream();
    private final PseudoOutputStream pout = new PseudoOutputStream();
    private OutputThread outThread = null;

    public WebSocketConnector(String context) {
        this.context = context;
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason)  {
        this.session = null;
        pin.close();
        pout.close();
        log.finer("WebSocket Connector closed: " + statusCode + " " + reason);
        name = "websocket://[closed]";
    }

    @Override
    public void onWebSocketOpen(Session session) {
        this.session = session;
        log.finer("WebSocket Connector connected: " + session.getRemoteSocketAddress());
        name = "ws://" + WebSocketSupport.address(session) + context;
        outThread = new OutputThread();
        outThread.start();
        if (listener != null) listener.connected(this);
        session.demand();
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
        byte[] buf = message.getBytes(StandardCharsets.UTF_8);
        synchronized (pin) {
            // TODO: Check if we need any filters here.
            // The WebSocketHubConnector filters for the likes of ^D
            try {
                pin.write(buf);
            } catch (IOException ignored){
                // ignore exception
            }
        }
        Session current = session;
        if (current != null && current.isOpen()) current.demand();
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
        return true;
    }

    @Override
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    @Override
    public String[] connections() {
        Session current = session;
        return current == null || !current.isOpen() ? new String[0] : new String[] { WebSocketSupport.address(current) };
    }

    @Override
    public void close() {
        Session current = session;
        if (current != null && current.isOpen()) current.close(1000, null, Callback.NOOP);
        pin.close();
        pout.close();
    }

    @Override
    public String toString() {
        return name;
    }

    /// internal classes and helpers

    private class OutputThread extends Thread {

        OutputThread() {
            setName(getClass().getSimpleName()+":"+name);
            setDaemon(true);
            setPriority(MIN_PRIORITY);
        }

        @Override
        public void run() {
            while (true) {
                String s;
                s = pout.readLine(StandardCharsets.UTF_8);
                if (s == null) break;
                write(s);
            }
        }

    }

    private void write(String s) {
        WebSocketSupport.sendText(session, s, log);
    }
}
