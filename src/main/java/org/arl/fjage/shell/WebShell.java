/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.io.*;
import java.util.*;
import java.net.URLDecoder;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.EventSource;
import org.eclipse.jetty.servlets.EventSource.Emitter;
import org.eclipse.jetty.servlets.EventSourceServlet;

/**
 * Web-based command shell.
 *
 * @author Mandar Chitre
 */
public class WebShell implements Shell {

  ////////// Constants

  private final static String WEB_HTML = "/org/arl/fjage/web/webshell.html";

  ////////// Private attributes

  private ScriptEngine engine = null;
  private java.util.logging.Logger log = java.util.logging.Logger.getLogger(getClass().getName());
  private Server server = null;
  private List<Emitter> out = new ArrayList<Emitter>();
  private int port;

  ////////// Methods

  /**
   * Creates a web command shell running on a specified port.
   *
   * @param port TCP port number.
   */
  public WebShell(int port) {
    this.port = port;
  }

  @Override
  public void bind(ScriptEngine engine) {
    this.engine = engine;
  }

  @Override
  public void start() {
    // disable Jetty logging (except warnings)
    System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
    Log.setLog(new Logger() {
      @Override public String getName()                         { return "[jetty]"; }
      @Override public Logger getLogger(String name)            { return this;      }
      @Override public boolean isDebugEnabled()                 { return false;     }
      @Override public void warn(String msg, Object... args)    { log.warning("[jetty] "+msg);          }
      @Override public void warn(Throwable t)                   { log.warning("[jetty] "+t.toString()); }
      @Override public void warn(String msg, Throwable thrown)  { log.warning("[jetty] "+msg);          }
      @Override public void info(String msg, Object... args)    { }
      @Override public void info(Throwable thrown)              { }
      @Override public void info(String msg, Throwable thrown)  { }
      @Override public void setDebugEnabled(boolean enabled)    { }
      @Override public void debug(String msg, Object... args)   { }
      @Override public void debug(Throwable thrown)             { }
      @Override public void debug(String msg, Throwable thrown) { }
      @Override public void ignore(Throwable ignored)           { }
    });
    // setup Jetty server
    server = new Server(port);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    server.setHandler(context);
    context.addServlet(new ServletHolder(new EventSourceServlet() {
      protected EventSource newEventSource(final HttpServletRequest req) {
        return new EventSource() {
          private Emitter emitter;
          public void onOpen(Emitter emitter) {
            log.info("New web connection");
            this.emitter = emitter;
            out.add(emitter);
            try {
              emitter.comment("\n");
            } catch (IOException ex) {
              log.warning(ex.toString());
            }
          }
          public void onClose() {
            log.info("Web connection closed");
            if (emitter != null) {
              out.remove(emitter);
              emitter = null;
            }
          }
        };
      }
    }), "/out");
    context.addServlet(new ServletHolder(new HttpServlet() {
      public void doGet(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_OK);
        try {
          String s = URLDecoder.decode(request.getQueryString(), "UTF-8");
          if (s.equals("__ABORT__")) {
            if (engine.isBusy()) {
              log.info("ABORT");
              engine.abort();
              println("ABORT", OutputType.ERROR);
            }
          } else if (s.length() > 0) {
            if (engine.isBusy()) println("BUSY", OutputType.ERROR);
            else {
              println("&gt; "+s, OutputType.INPUT);
              boolean ok = engine.exec(s, WebShell.this);
              if (!ok) println("ERROR", OutputType.ERROR);
            }
          }
        } catch (UnsupportedEncodingException ex) {
          log.warning(ex.toString());
        } catch (IOException ex) {
          log.warning(ex.toString());
        }
      }
    }), "/exec");
    context.addServlet(new ServletHolder(new HttpServlet() {
      public void doGet(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        try {
          copy(getClass().getResourceAsStream(WEB_HTML), response.getOutputStream());
        } catch (IOException ex) {
          log.warning(ex.toString());
        }
      }
    }), "/");
    try {
      server.start();
    } catch (Exception ex) {
      log.severe("Unable to start web server: " + ex.toString());
      server = null;
    }
  }

  @Override
  public void shutdown() {
    if (server != null) {
      try {
        server.stop();
      } catch (Exception ex) {
        log.warning("Unable to stop web server: " + ex.toString());
      }
      server = null;
    }
  }

  @Override
  public void println(Object obj, OutputType type) {
    if (obj == null) return;
    String s = obj.toString();
    s = s.replace("\n","<br/>");
    switch(type) {
      case INPUT:
        s = "<font color='#ffff00'>"+s+"</font>";
        break;
      case OUTPUT:
        break;
      case ERROR:
        s = "<font color='#ff0000'>"+s+"</font>";
        break;
      case NOTIFY:
        s = "<font color='#8080ff'>"+s+"</font>";
        break;
      default:
        return;
    }
    for (Emitter e: out) {
      try {
        e.data(s+"\n");
      } catch (IOException ex) {
        log.warning(ex.toString());
      }
    }
  }

  private void copy(InputStream in, OutputStream out) throws IOException {
    int len;
    byte[] buffer = new byte[1024];
    while ((len = in.read(buffer)) != -1) {
      out.write(buffer, 0, len);
    }
  }

}
