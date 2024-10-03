package org.arl.fjage.connectors;

import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

@WebSocket(maxIdleTime = Integer.MAX_VALUE, batchMode = BatchMode.OFF)
public class WebSocketConnector implements Connector {

    private ConnectionListener listener;
    private Session session;
    private OutputThread outThread = null;
    private final String name;

    private final PseudoInputStream pin = new PseudoInputStream();
    private final PseudoOutputStream pout = new PseudoOutputStream();

    private final Logger log = Logger.getLogger(getClass().getName());

    public WebSocketConnector(ConnectionListener listener, String hostpath) {
        this.listener = listener;
        this.name = "ws://" + hostpath;
        outThread = new OutputThread();
        outThread.start();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        log.fine("New connection from "+session.getRemoteAddress());
        this.session = session;
        listener.connected(this);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        log.fine("Connection from "+session.getRemoteAddress()+" closed");
        session = null;
    }

    @OnWebSocketError
    public void onError(Throwable t) {
        log.warning(t.toString());
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        try {
            pout.write(message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warning(e.toString());
        }
    }

    void write(String s) {
        try {
            if (session != null && session.isOpen()) {
                Future<Void> f = session.getRemote().sendStringByFuture(s);
                try {
                    f.get(500, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e){
                    log.fine("Sending timed out. Closing connection to " + session.getRemoteAddress());
                    session.disconnect();
                } catch (Exception e){
                    log.warning(e.toString());
                }
            }
        } catch (Exception e) {
            log.warning(e.toString());
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
    public void close() {
        if(session != null) {
            session.close();
        }
    }

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

    @Override
    public String toString() {
        if (session == null) {
            return name + " (disconnected)";
        }else {
            return name + "/" + session.getRemoteAddress();
        }
    }
}
