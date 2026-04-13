package org.arl.fjage.connectors;

import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebSocket(maxIdleTime = Integer.MAX_VALUE, batchMode = BatchMode.OFF)
public class WebSocketConnector implements FrameConnector{

    private String name = "ws://[closed]";
    private final String context;
    private Session session;
    private RemoteEndpoint remote;
    private ConnectionListener listener;
    protected Logger log = Logger.getLogger(getClass().getName());

    private FrameListener frameListener;

    public WebSocketConnector(String context) {
        this.context = context;
    }

    @OnWebSocketClose
    public void onWebSocketClose(int statusCode, String reason)  {
        this.session = null;
        this.remote = null;
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
        if (frameListener != null) frameListener.onReceive(message);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void sendFrame(Object frame) {
        if (frame instanceof String) {
            try {
                Session currentSession = session;
                if (currentSession != null && currentSession.isOpen()) {
                    currentSession.getRemote().sendString((String) frame, new WriteCallback() {
                        @Override
                        public void writeFailed(Throwable cause) {
                            try {
                                if (cause instanceof java.nio.channels.ClosedChannelException) {
                                    log.info("Unexpected " + cause.toString() + " while sending to " + currentSession.getRemoteAddress() + ".");
                                } else {
                                    log.log(Level.WARNING, "Error sending websocket message: ", cause);
                                }
                                if (currentSession.isOpen()) currentSession.disconnect();
                            } catch (Exception e) {
                                log.log(Level.WARNING, "Error handling websocket send failure: ", e);
                            }
                        }

                        @Override
                        public void writeSuccess() {
                            // nothing to do
                        }
                    });
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Error sending websocket message: ", e);
            }
        }
        else log.warning("Unsupported frame type: " + frame.getClass().getName());
    }

    @Override
    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
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
    }

    @Override
    public String toString() {
        return name;
    }

}
