/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.Rule;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Web server instance manager.
 */
public class WebServer {

  //////// constants

  public static final String NOCACHE = "no-cache, no-store, must-revalidate";
  public static final String CACHE = "public, max-age=31536000";

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
      @Override public void warn(String msg, Object... args)    {
        if (msg.contains("{}")) for (Object a: args) msg = msg.replaceFirst("\\{}", a.toString());
        log.warning(msg);
      }
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
   * Gets an instance of a web server running on the specified port. If an instance is not
   * already available, a new one is created.
   *
   * @param port HTTP port number.
   * @param ip IP address to bind HTTP server to.
   */
  public static WebServer getInstance(int port, String ip) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      if (svr == null || svr.server == null) svr = new WebServer(port, ip);
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
   * Gets all web server instances running.
   *
   * @return array of web server instances.
   */
  public static WebServer[] getInstances() {
    synchronized (servers) {
      return servers.values().toArray(new WebServer[0]);
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
  protected RewriteHandler rewrite;
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
    rewrite = new RewriteHandler();
    rewrite.setRewriteRequestURI(true);
    rewrite.setRewritePathInfo(true);
    contexts = new ContextHandlerCollection();
    HandlerCollection handlerCollection = new HandlerCollection();
    GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setIncludedMimeTypes("text/html", "text/plain", "text/xml", "text/css", "application/javascript", "text/javascript");
    handlerCollection.setHandlers(new Handler[] { contexts, new DefaultHandler() });
    gzipHandler.setHandler(rewrite);
    rewrite.setHandler(handlerCollection);
    server.setHandler(gzipHandler);
    ThreadPool pool = server.getThreadPool();
    if (pool instanceof QueuedThreadPool) ((QueuedThreadPool)pool).setDaemon(true);
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
    if (contexts.getHandlers() == null || contexts.getHandlers().length <= staticContexts.size()) stop();
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param resource resource path.
   * @param cacheControl cache control header.
   */
  public void add(String context, String resource, String cacheControl) {
    if(resource.startsWith("/")) resource = resource.substring(1);
    ArrayList<URL> res = new ArrayList<>();
    try {
      res = Collections.list(getClass().getClassLoader().getResources(resource));
    }catch (IOException ex){
      // do nothing
    }
    for (URL r : res){
      String staticWebResDir = r.toExternalForm();
      ContextHandler handler = new ContextHandler(context);
      ResourceHandler resHandler = new ResourceHandler();
      resHandler.setResourceBase(staticWebResDir);
      resHandler.setWelcomeFiles(new String[]{ "index.html" });
      resHandler.setDirectoriesListed(false);
      resHandler.setCacheControl(cacheControl);
      handler.setHandler(resHandler);
      staticContexts.put(context, handler);
      add(handler);
    }
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param resource resource path.
   */
  public void add(String context, String resource) {
    add(context, resource, "public, max-age=31536000");
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param dir filesystem path of directory to serve files from.
   * @param cacheControl cache control header.
   */
  public void add(String context, File dir, String cacheControl) {
    try {
      ContextHandler handler = new ContextHandler(context);
      ResourceHandler resHandler = new ResourceHandler();
      resHandler.setResourceBase(dir.getCanonicalPath());
      resHandler.setWelcomeFiles(new String[]{ "index.html" });
      resHandler.setDirectoriesListed(false);
      resHandler.setCacheControl(cacheControl);
      handler.setHandler(resHandler);
      staticContexts.put(context, handler);
      add(handler);
    }catch (IOException ex){
      log.warning("Unable to find the directory : " + dir.toString());
      return;
    }
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param dir filesystem path of directory to serve files from.
   */
  public void add(String context, File dir) {
    add(context, dir, "public, max-age=31536000");
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

  /**
   * Adds a rule to rewrite handler.
   *
   * @param rule rewrite rule.
   */
  public void addRule(Rule rule) {
    log.fine("Adding rewrite rule: "+rule);
    try {
      rewrite.addRule(rule);
    } catch (Exception ex) {
      log.warning("Unable to add rule "+rule+": "+ex.toString());
    }
  }

}
