package org.arl.fjage.connectors;

import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebSocket(maxIdleTime = Integer.MAX_VALUE, batchMode = BatchMode.OFF)
public class WebSocketConnector implements Connector{

    private String name = "ws://[closed]";
    private final String context;
    private Session session;
    private RemoteEndpoint remote;
    private ConnectionListener listener;
    protected Logger log = Logger.getLogger(getClass().getName());

    private final PseudoInputStream pin = new PseudoInputStream();
    private final PseudoOutputStream pout = new PseudoOutputStream();
    private OutputThread outThread = null;

    public WebSocketConnector(String context) {
        this.context = context;
    }

    @OnWebSocketClose
    public void onWebSocketClose(int statusCode, String reason)  {
        this.session = null;
        this.remote = null;
        pin.close();
        pout.close();
        outThread.close();
        log.finer("WebSocket Connector closed: " + statusCode + " " + reason);
        name = "websocket://[closed]";
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session session) {
        this.session = session;
        this.remote = this.session.getRemote();
        log.finer("WebSocket Connector connected: " + session.getRemoteAddress().getHostString() + ":" + session.getRemoteAddress().getPort());
        name = "ws://" + this.remote.getInetSocketAddress().getAddress().getHostAddress() + context + ":" + this.remote.getInetSocketAddress().getPort();
        if (listener != null) listener.connected(this);
        outThread = new OutputThread();
        outThread.start();
    }

    @OnWebSocketError
    public void onWebSocketError(Throwable cause)  {
        if (cause instanceof org.eclipse.jetty.io.EofException) {
            log.info(cause.toString());
            return;
        }
        log.log(Level.WARNING, "WebSocket error: ", cause);
    }

    @OnWebSocketMessage
    public void onWebSocketText(String message) {
        byte[] buf = message.getBytes();
        synchronized (pin) {
            // TODO: Check if we need any filters here.
            // The WebSocketHubConnector filters for the likes of ^D
            try {
                pin.write(buf);
            } catch (Throwable ignored){
                // ignore exception
            }
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
        return true;
    }

    @Override
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    @Override
    public String[] connections() {
        if (session == null || !session.isOpen()) return new String[]{};
        return new String[] { session.getRemoteAddress().getHostString()+":"+session.getRemoteAddress().getPort() };
    }

    @Override
    public void close() {
        if (this.session != null && this.session.isOpen()) this.session.close();
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
                s = pout.readLine();
                if (s == null) break;
                write(s);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }

        void close() {
            try {
                if (pout != null) {
                    pout.close();
                    join();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void write(String s){
        try {
            if (session != null && session.isOpen()) {
                Future<Void> f = session.getRemote().sendStringByFuture(s);
                try {
                    f.get(2, TimeUnit.SECONDS);
                } catch (TimeoutException e){
                    log.fine("Sending timed out. Closing connection to " + session.getRemoteAddress());
                    session.disconnect();
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof java.nio.channels.ClosedChannelException) {
                        log.info("Unexpected "+ e.getCause().toString() + " while sending to " + session.getRemoteAddress() + ".");
                    }
                    else {
                        log.log(Level.WARNING, "Error sending websocket message: ", e);
                    }
                } catch (Exception e){
                    log.log(Level.WARNING, "Error sending websocket message: ", e);
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Error sending websocket message: ", e);
        }
    }

}
