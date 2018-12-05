/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.websocket.servlet.*;
import org.eclipse.jetty.websocket.api.annotations.*;

/**
 * Web socket connector.
 */
public class WebSocketConnector implements Connector, WebSocketCreator {

  protected String name;
  protected boolean linemode = false;
  protected WebServer server;
  protected ContextHandler handler;
  protected List<WSHandler> wsHandlers = Collections.synchronizedList(new ArrayList<WSHandler>());
  protected OutputThread outThread = null;
  protected PseudoInputStream pin = new PseudoInputStream();
  protected PseudoOutputStream pout = new PseudoOutputStream();
  protected Logger log = Logger.getLogger(getClass().getName());

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketConnector(int port, String context) {
    init(port, context);
  }

  /**
   * Create a web socket connector and add it to a web server running on a
   * given port. If a web server isn't already created, this will start the
   * web server.
   */
  public WebSocketConnector(int port, String context, boolean linemode) {
    init(port, context);
    this.linemode = linemode;
  }

  protected void init(int port, String context) {
    name = "ws:["+port+":"+context+"]";
    server = WebServer.getInstance(port);
    handler = new ContextHandler(context);
    handler.setHandler(new WebSocketHandler() {
      @Override
      public void configure(WebSocketServletFactory factory) {
        factory.setCreator(WebSocketConnector.this);
        factory.getPolicy().setMaxBinaryMessageSize(50000000);
        factory.getPolicy().setMaxBinaryMessageBufferSize(50000000);
        factory.getPolicy().setMaxTextMessageSize​(50000000);
        factory.getPolicy().setMaxTextMessageBufferSize​(50000000);
        factory.getPolicy().setInputBufferSize(50000000);
      }
    });
    server.add(handler);
    server.start();
    outThread = new OutputThread();
    outThread.start();
  }

  @Override
  public Object createWebSocket​(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
    return new WSHandler();
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
  public void close() {
    outThread.close();
    outThread = null;
    server.remove(handler);
    server = null;
    handler = null;
    pin.close();
    pout.close();
    pin = null;
    pout = null;
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
        synchronized(wsHandlers) {
          for (WSHandler t: wsHandlers)
            t.write(s);
        }
      }
    }

    void close() {
      if (pout != null) pout.close();
    }

  }

  // servlets to manage web socket connections

  @WebSocket(maxIdleTime = Integer.MAX_VALUE)
  public class WSHandler {

    Session session = null;

    @OnWebSocketConnect
    public void onConnect(Session session) {
      log.info("New connection from "+session.getRemoteAddress());
      this.session = session;
      wsHandlers.add(this);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
      log.info("Connection from "+session.getRemoteAddress()+" closed");
      session = null;
      wsHandlers.remove(this);
    }

    @OnWebSocketError
    public void onError(Throwable t) {
      log.warning(t.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
      byte[] buf = message.getBytes();
      for (int i = 0; i < buf.length; i++) {
        int c = buf[i];
        if (c < 0) c += 256;
        if (c == 4) continue;     // ignore ^D
        try {
          pin.write(c);
        } catch (IOException ex) {
          // do nothing
        }
      }
    }

    void write(String s) {
      try {
        if (session != null) {
          session.getRemote().sendString(s);
        }
      } catch (IOException e) {
        log.warning(e.getMessage());
      }
    }

  }

}
