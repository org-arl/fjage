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
import java.util.logging.Logger;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
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

  ////////// Private attributes

  private ScriptEngine engine = null;
  private Logger log = Logger.getLogger(getClass().getName());
  private WebServer server = null;
  private List<Emitter> out = new ArrayList<Emitter>();
  private ServletContextHandler context;
  private boolean bare = false;
  private String style = readResource("/org/arl/fjage/web/webshell.css");
  private String body = readResource("/org/arl/fjage/web/webshell.html");

  ////////// Methods

  /**
   * Creates a web command shell running on a specified port and context path.
   *
   * @param port TCP port number (0 to automatically select port).
   * @param path context path.
   */
  public WebShell(int port, String path) {
    server = WebServer.getInstance(port);
    init(path);
    server.add(context);
  }

  /**
   * Creates a web command shell running on a specified port and root context.
   *
   * @param port TCP port number (0 to automatically select port).
   */
  public WebShell(int port) {
    server = WebServer.getInstance(port);
    init("/");
    server.add(context);
  }

  /**
   * Creates a web command shell without a web server. The shell's context handler should later
   * be added to an existing web server.
   *
   * @param path context path.
   */
  public WebShell(String path) {
    init(path);
  }

  /**
   * Gets the context handler to add to an existing web server.
   * @return context handler.
   */
  public ServletContextHandler getContextHandler() {
    return context;
  }

  /**
   * Gets the current CSS stylesheet.
   *
   * @return CSS code.
   */
  public String getCSS() {
    return style;
  }

  /**
   * Sets the CSS stylesheet. The stylesheet may be null, if no stylesheet desired. If the
   * full HTML is specified using setHtml() then the stylesheet is ignored.
   *
   * @param style CSS code.
   */
  public void setCSS(String style) {
    this.style = style;
  }

  /**
   * Gets the current HTML code.
   *
   * @return HTML code.
   */
  public String getHtml() {
    return body;
  }

  /**
   * Sets the HTML code to be served.
   *
   * @param body HTML body code.
   */
  public void setHtmlBody(String body) {
    this.body = body;
    bare = false;
  }

  /**
   * Sets the HTML code to be served.
   *
   * @param body HTML body code.
   */
  public void setHtml(String body) {
    this.body = body;
    bare = true;
  }

  /**
   * Checks if the HTML code is to be served "as is", or as part of the body.
   *
   * @return true if the code is to be served "as is", false otherwise.
   */
  public boolean isBare() {
    return bare;
  }

  /**
   * Gets the TCP port of the web server. This should only be called after the
   * web server is started.
   *
   * @return the TCP port of the web server.
   */
  public int getPort() {
    return server.getPort();
  }

  @Override
  public void bind(ScriptEngine engine) {
    this.engine = engine;
  }

  @Override
  public void start() {
    server.start();
  }

  @Override
  public void shutdown() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  @Override
  public void println(Object obj, OutputType type) {
    if (obj == null) return;
    String s = obj.toString();
    s = s.replace("&","&amp;").replace(">","&gt;").replace("<","&lt;").replace(" ","&nbsp;").replace("\n","<br/>");
    switch(type) {
      case INPUT:
        s = "<span class='input'>"+s+"</span>";
        break;
      case OUTPUT:
        s = "<span class='output'>"+s+"</span>";
        break;
      case ERROR:
        s = "<span class='error'>"+s+"</span>";
        break;
      case NOTIFY:
        s = "<span class='ntf'>"+s+"</span>";
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

  //////// Private methods

  private void init(String path) {
    context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath(path);
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
              println("> "+s, OutputType.INPUT);
              boolean ok = engine.exec(s, WebShell.this);
              if (!ok) println("ERROR", OutputType.ERROR);
            }
          }
        } catch (UnsupportedEncodingException ex) {
          log.warning(ex.toString());
        }
      }
    }), "/exec");
    context.addServlet(new ServletHolder(new HttpServlet() {
      public void doGet(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        try {
          Writer w = response.getWriter();
          if (!bare) {
            w.write("<!DOCTYPE html>\n<html>\n<head>\n<meta charset='utf-8'/>\n");
            if (style != null) w.write("<style>\n"+style+"</style>\n");
            w.write("</head>\n<body>\n");
          }
          if (body != null) w.write(body);
          if (!bare) w.write("</body>\n</html>\n");
        } catch (IOException ex) {
          log.warning(ex.toString());
        }
      }
    }), "/");
  }

  private String readResource(String resource) {
    InputStream in = getClass().getResourceAsStream(resource);
    return new Scanner(in, "UTF8").useDelimiter("\\Z").next();
  }

}
