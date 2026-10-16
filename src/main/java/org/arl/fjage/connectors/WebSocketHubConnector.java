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
  protected final PseudoInputStream pin = new PseudoInputStream();
  protected PseudoOutputStream pout;
  protected volatile ConnectionListener listener = null;
  private volatile boolean closed;
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
    var output = new WebSocketOutput(name, linemode, text -> {
      for (WSHandler endpoint : wsHandlers) endpoint.write(text);
      return !Thread.currentThread().isInterrupted();
    }, this::close, log);
    pout = output;
    handler = server.addWebSocket(context, this, maxMsgSize);
    if (handler == null) {
      pin.close();
      output.close();
      String msg = "Unable to add WebSocket handler at :"+port+context;
      throw new UncheckedIOException(msg, new IOException(msg));
    }
    synchronized (this) {
      if (closed) output.close();
      else output.start();
    }
  }

  @Override
  public Object createWebSocket(ServerUpgradeRequest req, ServerUpgradeResponse resp, org.eclipse.jetty.util.Callback callback) {
    return closed ? null : new WSHandler(this);
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
    return wsHandlers.stream().map(WSHandler::getSession).filter(s -> s != null && s.isOpen())
        .map(WebSocketSupport::address).toArray(String[]::new);
  }

  @Override
  public boolean isReliable() {
    return true;
  }

  @Override
  public boolean waitOutputCompletion(long timeout) {
    return ((WebSocketOutput)pout).awaitCompletion(timeout);
  }

  @Override
  public void close() {
    WebServer current;
    ContextHandler context;
    synchronized (this) {
      if (closed) return;
      closed = true;
      current = server;
      context = handler;
      server = null;
      handler = null;
    }
    pin.close();
    pout.close();
    for (WSHandler endpoint : wsHandlers) {
      Session session = endpoint.getSession();
      if (session != null) session.disconnect();
    }
    if (current != null) current.removeHandler(context);
  }

  @Override
  public String toString() {
    return name;
  }

  public class WSHandler extends Session.Listener.AbstractAutoDemanding {

    WebSocketHubConnector conn;

    public WSHandler(WebSocketHubConnector conn) {
      this.conn = conn;
    }

    @Override
    public void onWebSocketOpen(Session session) {
      log.fine("New connection from "+session.getRemoteSocketAddress());
      super.onWebSocketOpen(session);
      wsHandlers.add(this);
      if (closed) {
        wsHandlers.remove(this);
        session.disconnect();
        return;
      }
      ConnectionListener current = listener;
      try {
        if (current != null) current.connected(conn);
      } catch (RuntimeException ex) {
        log.log(Level.WARNING, "WebSocket connection listener failed", ex);
        wsHandlers.remove(this);
        session.disconnect();
      }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason, Callback callback) {
      log.fine("WebSocket connection closed: "+statusCode+" "+reason);
      wsHandlers.remove(this);
      callback.succeed();
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
      int length = 0;
      for (byte c : buf) if (c != 4) buf[length++] = c; // Ignore ^D.
      try {
        conn.pin.write(buf, 0, length);
      } catch (IOException ex) {
        Session current = getSession();
        if (current != null) current.disconnect();
        return;
      }
    }

    void write(String s) {
      WebSocketSupport.sendText(getSession(), s, log);
    }
  }
}
