/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.util.*;
import java.io.*;
import java.net.InetSocketAddress;
import javax.servlet.http.*;
import javax.servlet.ServletException;
import org.eclipse.jetty.util.log.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;

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
      if (svr == null || svr.server == null) svr = new WebServer(port);
      return svr;
    }
  }

  /**
   * Checks if an instance of a web server is running on the specified port.
   *
   * @param port HTTP port number.
   * @return true if running, false otherwise.
   */
  public static boolean hasInstance(int port) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      return svr != null;
    }
  }

  /**
   * Shutdown all web servers.
   */
  public static void shutdown() {
    synchronized (servers) {
      for (WebServer svr: servers.values())
        svr.stop();
    }
  }

  //////// instance attributes and methods

  protected Server server;
  protected ContextHandlerCollection contexts;
  protected Map<String,ContextHandler> staticContexts = new HashMap<String,ContextHandler>();
  protected boolean started;
  protected int port;

  protected WebServer(int port) {
    this(port, "127.0.0.1");
  }

  protected WebServer(int port, String ip) {
    this.port = port;
    server = new Server(InetSocketAddress.createUnresolved(ip, port));
    server.setStopAtShutdown(true);
    if (port > 0) servers.put(port, this);
    contexts = new ContextHandlerCollection();
    HandlerCollection handlerCollection = new HandlerCollection();
    GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setIncludedMimeTypes("text/html", "text/plain", "text/xml", "text/css", "application/javascript", "text/javascript");
    handlerCollection.setHandlers(new Handler[] { contexts, new DefaultHandler() });
    gzipHandler.setHandler(handlerCollection);
    server.setHandler(gzipHandler);
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
    if (port > 0) servers.remove(port);
  }


  /**
   * Adds a context handler to the server. Context handlerCollection should be added before the web
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
    if (contexts.getHandlers().length <= staticContexts.size()) stop();
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param resource resource path.
   */
  public void add(String context, String resource) {
    String staticWebResDir = getClass().getResource(resource).toExternalForm();
    ContextHandler handler = new ContextHandler(context);
    ResourceHandler resHandler = new ResourceHandler();
    resHandler.setResourceBase(staticWebResDir);
    resHandler.setWelcomeFiles(new String[]{ "index.html" });
    resHandler.setDirectoriesListed(false);
    resHandler.setCacheControl("public, max-age=31536000");
    handler.setHandler(resHandler);
    staticContexts.put(context, handler);
    add(handler);
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param dir filesystem path of directory to serve files from.
   */
  public void add(String context, File dir) {
    ContextHandler handler = new ContextHandler(context);
    ResourceHandler resHandler = new ResourceHandler();
    resHandler.setResourceBase(dir.getAbsolutePath());
    resHandler.setWelcomeFiles(new String[]{ "index.html" });
    resHandler.setDirectoriesListed(false);
    resHandler.setCacheControl("public, max-age=31536000");
    handler.setHandler(resHandler);
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

  /**
   * Checks if a context is already configured to serve documents.
   *
   * @param context context path.
   * @return true if configured, false otherwise
   */
  public boolean hasContext(String context) {
    return staticContexts.get(context) != null;
  }

}
