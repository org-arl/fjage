/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.util.*;
import java.io.*;
import javax.servlet.http.*;
import javax.servlet.ServletException;
import org.eclipse.jetty.util.log.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.servlet.ServletContextHandler;

/**
 * Web server instance manager.
 */
public class WebServer {

  //////// static attributes and methods

  private static Map<Integer,WebServer> servers = new HashMap<Integer,WebServer>();
  private static java.util.logging.Logger log = java.util.logging.Logger.getLogger(WebServer.class.getName());

  static {
    // disable Jetty logging (except warnings)
    System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
    Log.setLog(new Logger() {
      @Override public String getName()                         { return "[jetty]"; }
      @Override public Logger getLogger(String name)            { return this;      }
      @Override public boolean isDebugEnabled()                 { return false;     }
      @Override public void warn(String msg, Object... args)    { log.warning(msg);          }
      @Override public void warn(Throwable t)                   { log.warning(t.toString()); }
      @Override public void warn(String msg, Throwable thrown)  { log.warning(msg);          }
      @Override public void info(String msg, Object... args)    { }
      @Override public void info(Throwable thrown)              { }
      @Override public void info(String msg, Throwable thrown)  { }
      @Override public void setDebugEnabled(boolean enabled)    { }
      @Override public void debug(String msg, Object... args)   { }
      @Override public void debug(String msg, long x)           { }
      @Override public void debug(Throwable thrown)             { }
      @Override public void debug(String msg, Throwable thrown) { }
      @Override public void ignore(Throwable ignored)           { }
    });
  }

  /**
   * Gets an instance of a web server running on the specified port. If an instance is not
   * already available, a new one is created.
   *
   * @param port HTTP port number.
   */
  public static WebServer getInstance(int port) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      if (svr == null) svr = new WebServer(port);
      return svr;
    }
  }

  //////// instance attributes and methods

  protected Server server;
  protected HandlerCollection contexts;
  protected Map<String,ContextHandler> staticContexts = new HashMap<String,ContextHandler>();
  protected boolean started;
  protected int port;

  protected WebServer(int port) {
    this.port = port;
    server = new Server(port);
    if (port > 0) servers.put(port, this);
    contexts = new HandlerCollection(true);
    server.setHandler(contexts);
    started = false;
  }

  /**
   * Gets the port number that the web server is running on.
   *
   * @return TCP port number.
   */
  public int getPort() {
    return port;
  }

  /**
   * Starts the web server.
   */
  public void start() {
    if (started) return;
    try {
      server.start();
      server.setStopAtShutdown(true);
      log.info("Started web server on port "+port);
      started = true;
    } catch (Exception ex) {
      log.warning(ex.toString());
    }
  }

  /**
   * Stops the web server. Once this method is called, the server cannot be restarted.
   */
  public void stop() {
    if (server == null) return;
    try {
      log.info("Stopping web server");
      server.stop();
      started = false;
    } catch (Exception ex) {
      log.warning(ex.toString());
    }
    server = null;
    contexts = null;
  }


  /**
   * Adds a context handler to the server. Context handlers should be added before the web
   * server is started.
   *
   * @param handler context handler.
   */
  public void add(ContextHandler handler) {
    log.info("Adding web context: "+handler.getContextPath());
    contexts.addHandler(handler);
    try {
      handler.start();
    } catch (Exception ex) {
      log.warning("Unable to start context "+handler.getContextPath()+": "+ex.toString());
    }
  }

  /**
   * Removes a context handler.
   *
   * @param handler context handler to remove.
   */
  public void remove(ContextHandler handler) {
    log.info("Removing web context: "+handler.getContextPath());
    try {
      handler.stop();
    } catch (Exception ex) {
      log.warning("Unable to stop context "+handler.getContextPath()+": "+ex.toString());
    }
    contexts.removeHandler(handler);
    if (contexts.getHandlers().length == 0) stop();
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param resource resource path.
   */
  public void add(String context, String resource) {
    ContextHandler handler = new ContextHandler(context);
    handler.setHandler(new AbstractHandler() {
      @Override
      public void handle(String target, Request baseRequest, HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {
        if (target.equals("/")) target = "/index.html";
        log.info("GET "+resource+target);
        InputStream in = getClass().getResourceAsStream(resource+target);
        if (in == null) return;
        String s = new Scanner(in, "UTF8").useDelimiter("\\Z").next();
        if (target.endsWith("html")) response.setContentType("text/html;charset=utf-8");
        else if (target.endsWith("css")) response.setContentType("text/css;charset=utf-8");
        else if (target.endsWith("js")) response.setContentType("application/javascript;charset=utf-8");
        else response.setContentType("text/plain;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        response.getWriter().println(s);
      }
    });
    staticContexts.put(context, handler);
    add(handler);
  }

  /**
   * Removes a context serving static documents.
   *
   * @param context context path.
   */
  public void remove(String context) {
    ContextHandler handler = staticContexts.get(context);
    if (handler == null) return;
    staticContexts.remove(context);
    remove(handler);
  }

}
