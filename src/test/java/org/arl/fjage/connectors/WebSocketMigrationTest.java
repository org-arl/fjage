package org.arl.fjage.connectors;

import static org.junit.Assert.*;

import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import org.junit.After;
import org.junit.Test;

/** Wire-level checks using the JDK client, independently of Jetty's client API. */
public class WebSocketMigrationTest {
  private int port;

  @After
  public void stop() {
    if (port > 0 && WebServer.hasInstance(port)) WebServer.getInstance(port).stop();
  }

  private void allocatePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
  }

  private static class Peer implements WebSocket.Listener {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    final CompletableFuture<Integer> closed = new CompletableFuture<>();
    final StringBuilder text = new StringBuilder();

    @Override
    public void onOpen(WebSocket socket) { socket.request(1); }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      text.append(data);
      if (last) {
        messages.add(text.toString());
        text.setLength(0);
      }
      socket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
      closed.complete(status);
      return null;
    }

    @Override
    public void onError(WebSocket socket, Throwable failure) { closed.completeExceptionally(failure); }
  }

  private WebSocket connect(String path, Peer peer) throws Exception {
    return HttpClient.newHttpClient().newWebSocketBuilder()
        .buildAsync(URI.create("ws://127.0.0.1:"+port+path), peer).get(5, TimeUnit.SECONDS);
  }

  @Test(timeout = 15000)
  public void hubBroadcastsAndProcessesRepeatedUnicodeMessages() throws Exception {
    allocatePort();
    WebSocketHubConnector hub = new WebSocketHubConnector(port, "/ws", true);
    Peer first = new Peer();
    Peer second = new Peer();
    WebSocket a = connect("/ws", first);
    WebSocket b = connect("/ws/", second);
    try {
      assertEquals(2, hub.connections().length);
      a.sendPing(ByteBuffer.wrap(new byte[] {1, 2, 3})).get(5, TimeUnit.SECONDS);
      a.sendPong(ByteBuffer.wrap(new byte[] {4})).get(5, TimeUnit.SECONDS);
      for (int i = 0; i < 20; i++) {
        String text = "message "+i+" \u4e16\u754c\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        a.sendText(text, true).get(5, TimeUnit.SECONDS);
        assertArrayEquals(bytes, CompletableFuture.supplyAsync(() -> {
          try { return hub.getInputStream().readNBytes(bytes.length); }
          catch (Exception ex) { throw new CompletionException(ex); }
        }).get(5, TimeUnit.SECONDS));
        hub.getOutputStream().write(bytes);
        assertTrue(hub.waitOutputCompletion(5000));
        assertEquals(text, first.messages.poll(5, TimeUnit.SECONDS));
        assertEquals(text, second.messages.poll(5, TimeUnit.SECONDS));
      }
      a.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
      assertEquals(Integer.valueOf(1000), first.closed.get(5, TimeUnit.SECONDS));
      b.sendText("after close\n", true).get(5, TimeUnit.SECONDS);
      hub.getOutputStream().write("still open\n".getBytes(StandardCharsets.UTF_8));
      assertEquals("still open\n", second.messages.poll(5, TimeUnit.SECONDS));
    } finally {
      a.abort();
      b.abort();
      hub.close();
    }
    assertFalse(WebServer.getInstance(port).hasHandler("/ws"));
  }

  @Test(timeout = 10000)
  public void hubPreservesCharactersAcrossStreamWrites() throws Exception {
    allocatePort();
    WebSocketHubConnector hub = new WebSocketHubConnector(port, "/ws");
    Peer peer = new Peer();
    WebSocket socket = connect("/ws", peer);
    try {
      byte[] bytes = "\u4e16".getBytes(StandardCharsets.UTF_8);
      hub.getOutputStream().write(bytes, 0, 1);
      assertNull("incomplete UTF-8 character must not be emitted", peer.messages.poll(100, TimeUnit.MILLISECONDS));
      assertFalse(hub.waitOutputCompletion(20));
      hub.getOutputStream().write(bytes, 1, bytes.length-1);
      assertEquals("\u4e16", peer.messages.poll(5, TimeUnit.SECONDS));
      assertTrue(hub.waitOutputCompletion(5000));
    } finally {
      socket.abort();
      hub.close();
    }
  }

  @Test(timeout = 10000)
  public void serverConnectorExchangesTextAndClosesCleanly() throws Exception {
    allocatePort();
    BlockingQueue<Connector> connections = new LinkedBlockingQueue<>();
    WebSocketServer server = new WebSocketServer(port, "/ws", connections::add);
    Peer peer = new Peer();
    WebSocket socket = connect("/ws", peer);
    Connector connector = connections.poll(5, TimeUnit.SECONDS);
    assertNotNull(connector);
    try {
      socket.sendText("hello \u4e16\u754c\n", true).get(5, TimeUnit.SECONDS);
      byte[] bytes = "hello \u4e16\u754c\n".getBytes(StandardCharsets.UTF_8);
      assertArrayEquals(bytes, CompletableFuture.supplyAsync(() -> {
        try { return connector.getInputStream().readNBytes(bytes.length); }
        catch (Exception ex) { throw new CompletionException(ex); }
      }).get(5, TimeUnit.SECONDS));
      connector.getOutputStream().write(bytes);
      assertEquals("hello \u4e16\u754c\n", peer.messages.poll(5, TimeUnit.SECONDS));
      connector.close();
      assertEquals(Integer.valueOf(1000), peer.closed.get(5, TimeUnit.SECONDS));
    } finally {
      socket.abort();
      connector.close();
      server.close();
    }
  }

  @Test(timeout = 10000)
  public void configuredMessageLimitIsEnforced() throws Exception {
    allocatePort();
    WebSocketHubConnector hub = new WebSocketHubConnector(port, "/ws", 8);
    Peer peer = new Peer();
    WebSocket socket = connect("/ws", peer);
    try {
      socket.sendText("x".repeat(32), true).get(5, TimeUnit.SECONDS);
      assertEquals(Integer.valueOf(1009), peer.closed.get(5, TimeUnit.SECONDS));
    } finally {
      socket.abort();
      hub.close();
    }
  }
}
