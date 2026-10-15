/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.rewrite.handler.RewritePatternRule;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WebServerTest {

  private WebServer svr = null;

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

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

  // POSTs a single multipart file part, and returns the response status
  private static int upload(WebServer svr, String path, String filename, String content) throws IOException {
    String boundary = "fjage-test-boundary";
    URL url = new URL("http://127.0.0.1:"+svr.getPort()+path);
    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
    String body = "--"+boundary+"\r\n"
      + "Content-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\n"
      + "Content-Type: text/plain\r\n\r\n"
      + content+"\r\n"
      + "--"+boundary+"--\r\n";
    try (OutputStream os = conn.getOutputStream()) {
      os.write(body.getBytes(StandardCharsets.UTF_8));
    }
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
    assertEquals(200, statusOf(svr, "/ws"));
    assertEquals(200, statusOf(svr, "/ws/"));
  }

  //////////// a server that cannot bind serves nothing, so say why

  @Test
  public void portAlreadyInUseIsReported() throws IOException {
    int port;
    try (ServerSocket blocker = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      port = blocker.getLocalPort();
      try {
        svr = WebServer.getInstance(port);
        fail("web server should not start on a port already in use");
      } catch (UncheckedIOException ex) {
        assertTrue(ex.getMessage(), ex.getMessage().contains("port "+port+" is already in use"));
      }
      assertFalse(WebServer.hasInstance(port));
    }
    // once the port is free, a new attempt succeeds
    svr = WebServer.getInstance(port);
    assertEquals(404, statusOf(svr, "/"));
  }

  //////////// the server runs from creation, so rules and contexts may be added any time

  @Test
  public void ruleAddedToRunningServerIsApplied() throws IOException {
    WebServer svr = newServer();
    assertNotNull(svr.addHandler("/new", okHandler()));
    assertEquals(404, statusOf(svr, "/old"));
    RewritePatternRule rule = new RewritePatternRule();
    rule.setPattern("/old");
    rule.setReplacement("/new");
    assertTrue(svr.addRule(rule));
    assertEquals(200, statusOf(svr, "/old"));
  }

  @Test
  public void staticContextStillRedirectsBarePath() throws IOException {
    WebServer svr = newServer();
    // any resource directory on the test classpath will do
    assertFalse(svr.addStatic("/static", "org/arl/fjage/shell").isEmpty());
    assertEquals(302, statusOf(svr, "/static"));
  }

  //////////// uploaded files keep their submitted names, but stay inside the upload directory

  @Test
  public void uploadKeepsSubmittedFileName() throws IOException {
    WebServer svr = newServer();
    File dir = tmp.newFolder("up");
    assertTrue(svr.addUpload("/up", dir));
    for (String name : new String[] { "it's-$x.txt", "my file (1).txt", "ü.txt" }) {
      assertEquals(name, 200, upload(svr, "/up/x", name, name));
      File f = new File(dir, name);
      assertTrue(name, f.isFile());
      assertEquals(name, new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }
  }

  @Test
  public void uploadRejectsNamesOutsideDirectory() throws IOException {
    WebServer svr = newServer();
    File dir = tmp.newFolder("up");
    assertTrue(svr.addUpload("/up", dir));
    for (String name : new String[] { "../escape.txt", "sub/x.txt", "..", "." }) {
      assertEquals(name, 400, upload(svr, "/up/x", name, "x"));
    }
    assertArrayEquals(new String[0], dir.list());
    assertArrayEquals(new String[] { "up" }, tmp.getRoot().list());
  }

}
