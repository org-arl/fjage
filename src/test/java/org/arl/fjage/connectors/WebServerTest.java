/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.junit.After;
import org.junit.Test;

public class WebServerTest {

  private WebServer svr = null;

  @After
  public void teardown() {
    if (svr != null) {
      svr.stop();
      svr = null;
    }
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  private WebServer newServer() throws IOException {
    svr = WebServer.getInstance(freePort());
    return svr;
  }

  private static AbstractHandler okHandler() {
    return new AbstractHandler() {
      @Override
      public void handle(String target, Request baseRequest, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().print("ok");
        baseRequest.setHandled(true);
      }
    };
  }

  private static int statusOf(WebServer svr, String path) throws IOException {
    URL url = new URL("http://127.0.0.1:"+svr.getPort()+path);
    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    conn.setInstanceFollowRedirects(false);
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);
    try {
      return conn.getResponseCode();
    } finally {
      conn.disconnect();
    }
  }

  //////////// Jetty returns a null handler array when no handlers are registered

  @Test
  public void hasHandlerOnEmptyServerReturnsFalse() throws IOException {
    WebServer svr = newServer();
    assertFalse(svr.hasHandler("/nosuch"));
    assertFalse(svr.hasStatic("/nosuch"));
  }

  @Test
  public void hasHandlerAfterRemovingLastHandlerReturnsFalse() throws IOException {
    WebServer svr = newServer();
    ContextHandler h = svr.addHandler("/ws", okHandler());
    assertNotNull(h);
    assertTrue(svr.hasHandler("/ws"));
    assertTrue(svr.removeHandler(h));
    assertFalse(svr.hasHandler("/ws"));
  }

  @Test
  public void setErrorHandlerOnEmptyServerDoesNotThrow() throws IOException {
    WebServer svr = newServer();
    svr.setErrorHandler(new ErrorHandler());
    assertFalse(svr.setErrorHandler("/nosuch", new ErrorHandler()));
  }

  @Test
  public void setErrorHandlerAppliesToRegisteredContext() throws IOException {
    WebServer svr = newServer();
    assertNotNull(svr.addHandler("/ws", okHandler()));
    assertTrue(svr.setErrorHandler("/ws", new ErrorHandler()));
    assertFalse(svr.setErrorHandler("/other", new ErrorHandler()));
  }

  //////////// web socket clients cannot follow a redirect on an upgrade request

  @Test
  public void handlerContextIsServedOnBarePath() throws IOException {
    WebServer svr = newServer();
    assertNotNull(svr.addHandler("/ws", okHandler()));
    svr.start();
    assertEquals(200, statusOf(svr, "/ws"));
    assertEquals(200, statusOf(svr, "/ws/"));
  }

  @Test
  public void staticContextStillRedirectsBarePath() throws IOException {
    WebServer svr = newServer();
    // any resource directory on the test classpath will do
    assertFalse(svr.addStatic("/static", "org/arl/fjage/shell").isEmpty());
    svr.start();
    assertEquals(302, statusOf(svr, "/static"));
  }

}
