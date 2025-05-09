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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
public class WebSocketHubConnector implements Connector, WebSocketCreator {

  protected String name;
  protected boolean linemode = false;
  protected WebServer server;
  protected ContextHandler handler;
  protected List<WSHandler> wsHandlers = new CopyOnWriteArrayList<WSHandler>();
  protected OutputThread outThread = null;
  protected final PseudoInputStream pin = new PseudoInputStream();
  protected PseudoOutputStream pout = new PseudoOutputStream();
  protected ConnectionListener listener = null;
  protected Logger log = Logger.getLogger(getClass().getName());

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
  public String[] connections() {
    // return all active (check if wsHandlers.session.isOpen()) connections in the format "ip:port"
    return wsHandlers.stream().filter(h -> h.session != null && h.session.isOpen())
        .map(h -> h.session.getRemoteAddress().getHostName()+":"+h.session.getRemoteAddress().getPort())
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
    public void onError(Throwable t) {
      log.warning(t.toString());
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
      byte[] buf = message.getBytes();
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
    }

    void write(String s) {
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
}
