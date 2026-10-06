/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.ServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.WebSocketCreator;
import org.eclipse.jetty.server.handler.ContextHandler;

/**
 * Web socket connector.
 */
public class WebSocketHubConnector implements Connector, WebSocketCreator {

  protected String name;
  protected boolean linemode = false;
  protected WebServer server;
  protected ContextHandler handler;
  protected List<WSHandler> wsHandlers = new CopyOnWriteArrayList<>();
  protected OutputThread outThread = null;
  protected final PseudoInputStream pin = new PseudoInputStream();
  protected PseudoOutputStream pout = new PseudoOutputStream();
  protected ConnectionListener listener = null;
  protected Logger log = Logger.getLogger(getClass().getName());

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   *
   * @throws UncheckedIOException if the web server cannot be started, or the context is already in use.
   */
  public WebSocketHubConnector(int port, String context) {
    init(port, context, -1);
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   *
   * @throws UncheckedIOException if the web server cannot be started, or the context is already in use.
   */
  public WebSocketHubConnector(int port, String context, boolean linemode) {
    this.linemode = linemode;
    init(port, context, -1);
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   *
   * @throws UncheckedIOException if the web server cannot be started, or the context is already in use.
   */
  public WebSocketHubConnector(int port, String context, int maxMsgSize) {
    init(port, context, maxMsgSize);
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   *
   * @throws UncheckedIOException if the web server cannot be started, or the context is already in use.
   */
  public WebSocketHubConnector(int port, String context, boolean linemode, int maxMsgSize) {
    this.linemode = linemode;
    init(port, context, maxMsgSize);
  }

  protected void init(int port, String context, int maxMsgSize) {
    try {
      name = "ws://"+InetAddress.getLocalHost().getHostAddress()+":"+port+context;
    } catch (UnknownHostException ex) {
      name = "ws://0.0.0.0:"+port+context;
    }
    server = WebServer.getInstance(port);
    log.info ("Adding WebSocket handler at :"+port + context);
    handler = server.addWebSocket(context, this, maxMsgSize);
    if (handler == null) {
      String msg = "Unable to add WebSocket handler at :"+port+context;
      throw new UncheckedIOException(msg, new IOException(msg));
    }
    outThread = new OutputThread();
    outThread.start();
  }

  @Override
  public Object createWebSocket(ServerUpgradeRequest req, ServerUpgradeResponse resp, org.eclipse.jetty.util.Callback callback) {
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
  public String[] connections() {
    return wsHandlers.stream().map(h -> h.session).filter(s -> s != null && s.isOpen())
        .map(WebSocketSupport::address).toArray(String[]::new);
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
    for (WSHandler endpoint : wsHandlers) {
      Session current = endpoint.session;
      if (current != null) current.disconnect();
    }
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
      // Keep decoder state when a UTF-8 character spans queue reads.
      Reader reader = new InputStreamReader(new InputStream() {
        @Override
        public int read() {
          return pout.read();
        }

        @Override
        public int read(byte[] buf, int ofs, int len) {
          return pout.read(buf, ofs, len);
        }
      }, StandardCharsets.UTF_8);
      char[] buf = new char[4096];
      try {
        while (true) {
          String s;
          if (linemode) {
            s = pout.readLine(StandardCharsets.UTF_8);
            if (s == null) break;
          } else {
            int n = reader.read(buf);
            if (n < 0) break;
            s = new String(buf, 0, n);
          }
          for (WSHandler t: wsHandlers)
            t.write(s);
        }
      } catch (IOException ex) {
        log.log(Level.WARNING, "WebSocket output read failure", ex);
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

  public class WSHandler implements Session.Listener {

    volatile Session session = null;
    WebSocketHubConnector conn;

    public WSHandler(WebSocketHubConnector conn) {
      this.conn = conn;
    }

    @Override
    public void onWebSocketOpen(Session session) {
      log.fine("New connection from "+session.getRemoteSocketAddress());
      this.session = session;
      wsHandlers.add(this);
      if (listener != null) listener.connected(conn);
      session.demand();
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      log.fine("WebSocket connection closed: "+statusCode+" "+reason);
      session = null;
      wsHandlers.remove(this);
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
      synchronized (conn.pin) {
        for (int c : buf) {
          if (c < 0) c += 256;
          if (c == 4) continue;     // ignore ^D
          try {
            conn.pin.write(c);
          } catch (IOException ex) {
            // do nothing
          }
        }
      }
      Session current = session;
      if (current != null && current.isOpen()) current.demand();
    }

    void write(String s) {
      WebSocketSupport.sendText(session, s, log);
    }
  }
}
