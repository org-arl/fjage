/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.websocket.servlet.*;
import org.eclipse.jetty.websocket.api.annotations.*;

/**
 * Web socket connector.
 */
public class WebSocketHubConnector implements FrameConnector, WebSocketCreator {

  protected String name;
  protected boolean linemode = false;
  protected WebServer server;
  protected ContextHandler handler;
  protected List<WSHandler> wsHandlers = new CopyOnWriteArrayList<WSHandler>();
  protected ConnectionListener listener = null;
  protected Logger log = Logger.getLogger(getClass().getName());
  private FrameListener frameListener;

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketHubConnector(int port, String context) {
    init(port, context, -1);
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketHubConnector(int port, String context, boolean linemode) {
    init(port, context, -1);
    this.linemode = linemode;
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketHubConnector(int port, String context, int maxMsgSize) {
    init(port, context, maxMsgSize);
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketHubConnector(int port, String context, boolean linemode, int maxMsgSize) {
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
    log.info ("Adding WebSocket handler at :"+port + context);
    handler = server.addHandler(context, new WebSocketHandler() {
      @Override
      public void configure(WebSocketServletFactory factory) {
        factory.setCreator(WebSocketHubConnector.this);
        if (maxMsgSize > 0) factory.getPolicy().setMaxTextMessageSize(maxMsgSize);
      }
    });
    server.start();
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
  public void setConnectionListener(ConnectionListener listener) {
    this.listener = listener;
  }

  @Override
  public String[] connections() {
    // return all active (check if wsHandlers.session.isOpen()) connections in the format "ip:port"
    return wsHandlers.stream().filter(h -> h.session != null && h.session.isOpen())
        .map(h -> h.session.getRemoteAddress().getHostString()+":"+h.session.getRemoteAddress().getPort())
        .toArray(String[]::new);
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
    server.removeHandler(handler);
    server = null;
    handler = null;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public void sendFrame(Object frame) {
    if (frame instanceof String) {
      String s = (String) frame;
      for (WSHandler t : wsHandlers)
        t.write(s);
    } else {
      log.warning("Unsupported frame type: " + frame.getClass().getName());
    }
  }

  @Override
  public void setFrameListener(FrameListener listener) {
    this.frameListener = listener;
  }

  // POJO for each web socket connection

  @WebSocket(maxIdleTime = Integer.MAX_VALUE, batchMode = BatchMode.OFF)
  public class WSHandler {

    Session session = null;
    WebSocketHubConnector conn;

    public WSHandler(WebSocketHubConnector conn) {
      this.conn = conn;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
      log.fine("New connection from "+session.getRemoteAddress());
      this.session = session;
      wsHandlers.add(this);
      if (listener != null) listener.connected(conn);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
      log.fine("Connection from "+session.getRemoteAddress()+" closed");
      session = null;
      wsHandlers.remove(this);
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
    public void onMessage(String message) {
      if (frameListener != null) frameListener.onReceive(message);
    }

    void write(String s) {
      try {
        Session currentSession = session;
        if (currentSession != null && currentSession.isOpen()) {
          currentSession.getRemote().sendString(s, new WriteCallback() {
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
  }
}
