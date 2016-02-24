/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.util.*;
import org.eclipse.jetty.util.log.*;
import org.eclipse.jetty.server.Server;
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
      @Override public void debug(Throwable thrown)             { }
      @Override public void debug(String msg, Throwable thrown) { }
      @Override public void ignore(Throwable ignored)           { }
    });
  }

  /**
   * Gets an instance of a web server running on the specified port. If an instance is not
   * already available, a new one is created.
   *
   * @param port HTTP port number, or 0 to randomly assign one.
   */
  public static WebServer getInstance(int port) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      if (svr == null) svr = new WebServer(port);
      return svr;
    }
  }

  //////// instance attributes and methods

  private Server server;
  private ContextHandlerCollection contexts;
  private boolean started;
  private int port;

  private WebServer(int port) {
    server = new Server(port);
    if (port > 0) servers.put(port, this);
    contexts = new ContextHandlerCollection();
    server.setHandler(contexts);
    started = false;
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
      port = server.getConnectors()[0].getLocalPort();
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

}
