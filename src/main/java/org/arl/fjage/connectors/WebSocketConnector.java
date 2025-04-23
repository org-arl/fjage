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
    private Session session;
    private RemoteEndpoint remote;
    private ConnectionListener listener;
    protected Logger log = Logger.getLogger(getClass().getName());

    private final PseudoInputStream pin = new PseudoInputStream();
    private final PseudoOutputStream pout = new PseudoOutputStream();
    private OutputThread outThread = null;

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketConnector(int port, String context, boolean linemode) {
    init(port, context, -1);
    this.linemode = linemode;
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketConnector(int port, String context, int maxMsgSize) {
    init(port, context, maxMsgSize);
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketConnector(int port, String context, boolean linemode, int maxMsgSize) {
    init(port, context, maxMsgSize);
    this.linemode = linemode;
  }

  protected void init(int port, String context, int maxMsgSize) {
    try {
      name = "ws://"+InetAddress.getLocalHost().getHostAddress()+":"+port+context;
    } catch (UnknownHostException ex) {
      name = "ws://0.0.0.0:"+port+context;
    }
    server = WebServer.getInstance(port);
    handler = new ContextHandler(context);
    handler = server.addHandler(context, new WebSocketHandler() {
      @Override
      public void configure(WebSocketServletFactory factory) {
        factory.setCreator(WebSocketConnector.this);
        if (maxMsgSize > 0) factory.getPolicy().setMaxTextMessageSize(maxMsgSize);
      }
    });
    server.start();
    outThread = new OutputThread();
    outThread.start();
  }

  @Override
  public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
    return new WSHandler(this);
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
  public void setConnectionListener(ConnectionListener listener) {
    this.listener = listener;
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
  public void close() {
    outThread.close();
    outThread = null;
    server.removeHandler(handler);
    server = null;
    handler = null;
    pin.close();
    pout.close();
  }

  @Override
  public String toString() {
    return name;
  }

  // thread to monitor incoming data on output stream and write to TCP clients

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
        if (linemode) {
          s = pout.readLine();
          if (s == null) break;
        } else {
          byte[] buf = pout.readAvailable();
          if (buf == null) break;
          s = new String(buf);
        }
        for (WSHandler t: wsHandlers)
          t.write(s);
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

  // POJO for each web socket connection

  @WebSocket(maxIdleTime = Integer.MAX_VALUE, batchMode = BatchMode.OFF)
  public class WSHandler {

    Session session = null;
    WebSocketConnector conn;

    public WSHandler(WebSocketConnector conn) {
      this.conn = conn;
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session session) {
        this.session = session;
        this.remote = this.session.getRemote();
        log.finer("WebSocket Connector connected: " + session.getRemoteAddress().getHostName() + ":" + session.getRemoteAddress().getPort());
        name = "ws://" + this.remote.getInetSocketAddress().getAddress().getHostAddress() + ":" + this.remote.getInetSocketAddress().getPort();
        if (listener != null) listener.connected(this);
        outThread = new OutputThread();
        outThread.start();
    }

    @OnWebSocketError
    public void onWebSocketError(Throwable cause)  {
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
                } catch (Exception e){
                    log.log(Level.WARNING, "Error sending websocket message: ", e);
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Error sending websocket message: ", e);
        }
    }

}
