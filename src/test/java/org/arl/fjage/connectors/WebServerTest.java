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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.File;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.eclipse.jetty.rewrite.handler.RewritePatternRule;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.junit.After;
import org.junit.Test;

public class WebServerTest {

  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private WebServer svr = null;

  @Rule
  public final TemporaryFolder files = new TemporaryFolder();

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

  private static Handler okHandler() {
    return new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        Content.Sink.write(response, true, "ok", callback);
        return true;
      }
    };
  }

  private static int statusOf(WebServer svr, String path) throws IOException {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+svr.getPort()+path))
        .timeout(Duration.ofSeconds(5)).build();
    try {
      return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException(ex);
    }
  }

  //////////// Empty context registration

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

  private HttpResponse<byte[]> request(String path, String method, byte[] body, String... headers) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+svr.getPort()+path))
        .timeout(Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.ofByteArray(body));
    for (int i = 0; i < headers.length; i += 2) request.header(headers[i], headers[i+1]);
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  private HttpResponse<byte[]> get(String path, String... headers) throws Exception {
    return request(path, "GET", new byte[0], headers);
  }

  @Test
  public void staticFilesSupportCacheValidationRangesAndHead() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath();
    Files.writeString(dir.resolve("index.html"), "welcome");
    Files.writeString(dir.resolve("module.mjs"), "export const value = 17;");
    assertFalse(svr.addStatic("/static", dir.toFile()).isEmpty());
    HttpResponse<byte[]> response = get("/static/");
    assertEquals(200, response.statusCode());
    assertEquals("welcome", new String(response.body(), StandardCharsets.UTF_8));
    assertEquals(WebServer.CACHE, response.headers().firstValue("Cache-Control").orElseThrow());
    String etag = response.headers().firstValue("ETag").orElseThrow();
    assertEquals(304, get("/static/", "If-None-Match", etag).statusCode());
    response = get("/static/index.html", "Range", "bytes=1-3");
    assertEquals(206, response.statusCode());
    assertEquals("elc", new String(response.body(), StandardCharsets.UTF_8));
    assertEquals(0, request("/static/index.html", "HEAD", new byte[0]).body().length);
    assertTrue(get("/static/module.mjs").headers().firstValue("Content-Type").orElseThrow().contains("javascript"));
    assertEquals(404, get("/static/missing").statusCode());
  }

  @Test
  public void gzipPreservesStaticContent() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath();
    String text = "Jetty 12 and Java 17\n".repeat(1000);
    Files.writeString(dir.resolve("large.txt"), text);
    svr.addStatic("/static", dir.toFile());
    HttpResponse<byte[]> response = get("/static/large.txt", "Accept-Encoding", "gzip");
    assertEquals(200, response.statusCode());
    assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElseThrow());
    try (var input = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
      assertEquals(text, new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  public void classpathJarResourcesAreServedAndRemoved() throws Exception {
    newServer();
    // JUnit is on the test classpath as a JAR, rather than an expanded resource directory.
    var handlers = svr.addStatic("/jar", "org/junit");
    assertFalse(handlers.isEmpty());
    HttpResponse<byte[]> response = get("/jar/Test.class");
    assertEquals(200, response.statusCode());
    assertEquals(0xca, response.body()[0] & 0xff);
    assertEquals(0xfe, response.body()[1] & 0xff);
    for (ContextHandler handler : handlers) assertTrue(svr.removeStatic(handler));
    assertEquals(404, get("/jar/Test.class").statusCode());
  }

  @Test
  public void directoryListingsEscapeNamesAndHideDotFiles() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath();
    String name = File.separatorChar == '\\' ? "escaped'name.txt" : "quoted\"name.txt";
    Files.writeString(dir.resolve(name), "data");
    Path hidden = Files.writeString(dir.resolve(".hidden"), "secret");
    if (File.separatorChar == '\\') Files.setAttribute(hidden, "dos:hidden", true);
    svr.addStatic("/files", dir.toFile(), new WebServer.WebServerOptions().directoryListed(true));
    HttpResponse<byte[]> response = get("/files/", "Content-Type", "application/json");
    assertEquals(200, response.statusCode());
    var entries = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonArray();
    assertEquals(1, entries.size());
    assertEquals(name, entries.get(0).getAsJsonObject().get("name").getAsString());
    String text = new String(get("/files/", "Content-Type", "text/plain").body(), StandardCharsets.UTF_8);
    assertTrue(text.contains(name+" 4 "));
    assertFalse(text.contains(".hidden"));
  }

  private static byte[] multipart(String filename, String content) {
    return ("--fjage-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\n"
        +"Content-Type: application/octet-stream\r\n\r\n"+content+"\r\n--fjage-boundary--\r\n").getBytes(StandardCharsets.UTF_8);
  }

  private HttpResponse<byte[]> upload(byte[] body) throws Exception {
    return request("/upload", "POST", body, "Content-Type", "multipart/form-data; boundary=fjage-boundary");
  }

  @Test
  public void uploadsSpillToDiskAndCleanTemporaryFiles() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath().resolve("uploads");
    Path tmp = files.newFolder().toPath();
    assertTrue(svr.addUpload("/upload", dir.toFile(), tmp.toString(), 1024, 2048, 4));
    HttpResponse<byte[]> response = upload(multipart("../sample.bin", "hello \u4e16\u754c"));
    assertEquals(200, response.statusCode());
    assertEquals("hello \u4e16\u754c", Files.readString(dir.resolve("..%2Fsample.bin")));
    try (var paths = Files.list(tmp)) { assertEquals(0, paths.count()); }
    assertEquals(405, get("/upload").statusCode());
  }

  @Test
  public void oversizedAndMalformedUploadsLeaveNoTemporaryFiles() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath();
    Path tmp = files.newFolder().toPath();
    assertTrue(svr.addUpload("/upload", dir.toFile(), tmp.toString(), 32, 256, 4));
    assertEquals(400, upload(multipart("sample.bin", "x".repeat(64))).statusCode());
    assertEquals(400, upload(multipart("sample.bin", "x".repeat(512))).statusCode());
    assertEquals(400, upload("malformed".getBytes(StandardCharsets.UTF_8)).statusCode());
    try (var paths = Files.list(tmp)) { assertEquals(0, paths.count()); }
    try (var paths = Files.list(dir)) { assertEquals(0, paths.count()); }
    assertEquals(200, upload(multipart("sample.bin", "valid")).statusCode());
  }

  @Test
  public void asynchronousHandlerCompletesResponse() throws Exception {
    newServer();
    svr.addHandler("/async", new Handler.Abstract.NonBlocking() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        CompletableFuture.runAsync(() -> Content.Sink.write(response, true, "complete", callback));
        return true;
      }
    });
    assertEquals("complete", new String(get("/async").body(), StandardCharsets.UTF_8));
  }

  @Test
  public void errorHandlerAppliesToExistingAndFutureContexts() throws Exception {
    newServer();
    Handler existing = new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        Response.writeError(request, response, callback, 503);
        return true;
      }
    };
    svr.addHandler("/existing", existing);
    svr.setErrorHandler(new ErrorHandler() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        Content.Sink.write(response, true, "custom error", callback);
        return true;
      }
    });
    svr.addHandler("/future", new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        Response.writeError(request, response, callback, 503);
        return true;
      }
    });
    for (String path : new String[] {"/existing", "/future"}) {
      HttpResponse<byte[]> response = get(path);
      assertEquals(503, response.statusCode());
      assertEquals("custom error", new String(response.body(), StandardCharsets.UTF_8));
    }
  }

  @Test
  public void failedHandlerRegistrationReleasesContext() throws Exception {
    newServer();
    Handler broken = new Handler.Abstract() {
      @Override
      protected void doStart() throws Exception { throw new IOException("cannot start"); }
      @Override
      public boolean handle(Request request, Response response, Callback callback) { return false; }
    };
    assertEquals(null, svr.addHandler("/broken", broken));
    assertFalse(svr.hasHandler("/broken"));
    assertTrue(svr.contexts.getBeans(ContextHandler.class).isEmpty());
    assertNotNull(svr.addHandler("/broken", okHandler()));
    assertEquals(200, get("/broken").statusCode());
  }

  @Test
  public void failedFileWriteCompletesResponseAndCleansUpload() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath();
    Path tmp = files.newFolder().toPath();
    assertTrue(svr.addUpload("/upload", dir.toFile(), tmp.toString(), 1024, 2048, 4));
    Files.createDirectory(dir.resolve("sample.bin"));
    assertEquals(500, upload(multipart("sample.bin", "uploaded data")).statusCode());
    try (var paths = Files.list(tmp)) { assertEquals(0, paths.count()); }
    assertEquals(200, upload(multipart("other.bin", "valid")).statusCode());
  }

  @Test(timeout = 10000)
  public void disconnectedUploadCleansTemporaryFiles() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath();
    Path tmp = files.newFolder().toPath();
    assertTrue(svr.addUpload("/upload", dir.toFile(), tmp.toString(), 1024, 2048, 4));
    try (Socket socket = new Socket("127.0.0.1", svr.getPort())) {
      String headers = "POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1024\r\n"
          +"Content-Type: multipart/form-data; boundary=fjage-boundary\r\n\r\n";
      String partial = "--fjage-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.bin\"\r\n\r\n"
          +"x".repeat(64)+"\r\n";
      socket.getOutputStream().write((headers+partial).getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().flush();
      long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
      boolean spilled = false;
      while (System.nanoTime() < deadline) {
        try (var paths = Files.list(tmp)) { spilled = paths.findAny().isPresent(); }
        if (spilled) break;
        Thread.sleep(10);
      }
      assertTrue("upload must spill before disconnect", spilled);
    }
    long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    long remaining;
    do {
      try (var paths = Files.list(tmp)) { remaining = paths.count(); }
      if (remaining == 0) break;
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    assertEquals(0, remaining);
    try (var paths = Files.list(dir)) { assertEquals(0, paths.count()); }
  }

  @Test
  public void directoryListingUsesTheContextAliasPolicy() throws Exception {
    newServer();
    Path dir = files.newFolder().toPath();
    Path outside = files.newFolder().toPath();
    Files.writeString(outside.resolve("secret.txt"), "private");
    Files.createSymbolicLink(dir.resolve("alias"), outside);
    var contexts = svr.addStatic("/files", dir.toFile(), new WebServer.WebServerOptions().directoryListed(true));
    for (ContextHandler context : contexts) context.clearAliasChecks();
    for (String type : new String[] {"text/plain", "application/json"}) {
      HttpResponse<byte[]> response = get("/files/alias/", "Content-Type", type);
      assertTrue(response.statusCode() >= 400);
      assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains("secret.txt"));
    }
  }

  @Test
  public void interruptedRegistrationCompletesBeforeReturning() throws Exception {
    newServer();
    ContextHandler context;
    Thread.currentThread().interrupt();
    try {
      context = svr.addHandler("/interrupted", okHandler());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
    assertNotNull(context);
    assertEquals(200, get("/interrupted").statusCode());
    svr.stop();
    assertTrue(context.isStopped());
  }

  @Test(timeout = 10000)
  public void removingHandlerCanCloseDependentEndpoint() throws Exception {
    newServer();
    WebSocketServer child = new WebSocketServer(svr.getPort(), "/child", connector -> {});
    Handler parent = new Handler.Abstract() {
      @Override public boolean handle(Request request, Response response, Callback callback) { return false; }
      @Override protected void doStop() throws Exception {
        child.close();
        super.doStop();
      }
    };
    ContextHandler context = svr.addHandler("/parent", parent);
    assertTrue(svr.removeHandler(context));
    assertFalse(svr.hasHandler("/child"));
    assertNotNull(svr.addHandler("/next", okHandler()));
  }

  @Test
  public void zeroUploadLimitsRemainUnlimited() throws Exception {
    newServer();
    assertTrue(svr.addUpload("/upload", files.newFolder(), 0, 0, 1));
    assertEquals(200, upload(multipart("file.txt", "content")).statusCode());
  }

  @Test
  public void shutdownStopsMultipleServers() throws Exception {
    WebServer first = newServer();
    WebServer second = WebServer.getInstance(freePort());
    int firstPort = first.getPort();
    int secondPort = second.getPort();
    try {
      WebServer.shutdown();
      assertFalse(WebServer.hasInstance(firstPort));
      assertFalse(WebServer.hasInstance(secondPort));
    } finally { second.stop(); }
  }

  @Test(timeout = 10000)
  public void lifecycleCleanupCanWaitForRegistrationFromAnotherThread() throws Exception {
    newServer();
    Handler parent = new Handler.Abstract() {
      @Override public boolean handle(Request request, Response response, Callback callback) { return false; }
      @Override protected void doStop() throws Exception {
        CompletableFuture.supplyAsync(() -> svr.addHandler("/child", okHandler())).get(5, TimeUnit.SECONDS);
        super.doStop();
      }
    };
    ContextHandler context = svr.addHandler("/parent", parent);
    assertTrue(svr.removeHandler(context));
    assertTrue(svr.hasHandler("/child"));
  }

  @Test
  public void failedStopStillDestroysDetachedHandler() throws Exception {
    newServer();
    java.util.concurrent.atomic.AtomicBoolean destroyed = new java.util.concurrent.atomic.AtomicBoolean();
    Handler parent = new Handler.Abstract() {
      @Override public boolean handle(Request request, Response response, Callback callback) { return false; }
      @Override protected void doStop() throws Exception { throw new IOException("stop failed"); }
      @Override public void destroy() { destroyed.set(true); super.destroy(); }
    };
    ContextHandler context = svr.addHandler("/parent", parent);
    assertFalse(svr.removeHandler(context));
    assertTrue(destroyed.get());
    assertFalse(svr.hasHandler("/parent"));
  }

  @Test(timeout = 10000)
  public void shutdownAllowsLifecycleCleanupToReadTheRegistry() throws Exception {
    newServer();
    int port = svr.getPort();
    java.util.concurrent.atomic.AtomicBoolean queried = new java.util.concurrent.atomic.AtomicBoolean();
    svr.addHandler("/parent", new Handler.Abstract() {
      @Override public boolean handle(Request request, Response response, Callback callback) { return false; }
      @Override protected void doStop() throws Exception {
        CompletableFuture.supplyAsync(() -> WebServer.hasInstance(port)).get(5, TimeUnit.SECONDS);
        queried.set(true);
        super.doStop();
      }
    });
    WebServer.shutdown();
    assertTrue(queried.get());
    assertFalse(WebServer.hasInstance(port));
  }

  @Test(timeout = 15000)
  public void registrationDuringShutdownCleansUpTheUnpublishedHandler() throws Exception {
    newServer();
    var owner = svr.server;
    var starting = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var destroyed = new java.util.concurrent.atomic.AtomicBoolean();
    Handler handler = new Handler.Abstract() {
      @Override public boolean handle(Request request, Response response, Callback callback) { return false; }
      @Override protected void doStart() throws Exception {
        starting.countDown();
        if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Start was not released");
        super.doStart();
      }
      @Override public void destroy() { destroyed.set(true); super.destroy(); }
    };
    var adding = CompletableFuture.supplyAsync(() -> svr.addHandler("/late", handler));
    assertTrue(starting.await(5, TimeUnit.SECONDS));
    var stopping = CompletableFuture.runAsync(svr::stop);
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!owner.isStopping() && System.nanoTime() < deadline) Thread.sleep(10);
      assertTrue(owner.isStopping());
    } finally { release.countDown(); }
    assertEquals(null, adding.get(5, TimeUnit.SECONDS));
    stopping.get(5, TimeUnit.SECONDS);
    assertTrue(destroyed.get());
  }

  @Test
  public void rejectedJarRegistrationClosesItsFilesystem() throws Exception {
    newServer();
    svr.stop();
    var factory = org.eclipse.jetty.util.resource.ResourceFactory.unregisterResourceFactory("jar");
    var filesystems = new java.util.ArrayList<java.nio.file.FileSystem>();
    org.eclipse.jetty.util.resource.ResourceFactory.registerResourceFactory("jar", uri -> {
      var resource = factory.newResource(uri);
      filesystems.add(resource.getPath().getFileSystem());
      return resource;
    });
    try {
      assertTrue(svr.addStatic("/jar", "org/junit").isEmpty());
      assertFalse(filesystems.isEmpty());
      for (var filesystem : filesystems) assertFalse(filesystem.isOpen());
    } finally {
      org.eclipse.jetty.util.resource.ResourceFactory.registerResourceFactory("jar", factory);
    }
  }

}
