package org.arl.fjage.connectors;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.Test;

public class WebSocketConnectorTest {
  private final AtomicBoolean open = new AtomicBoolean(true);
  private final AtomicInteger closes = new AtomicInteger();
  private final LinkedBlockingQueue<Callback> sends = new LinkedBlockingQueue<>();

  private Session session() {
    return (Session)Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[] {Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "isOpen" -> open.get();
          case "getRemoteSocketAddress" -> new InetSocketAddress("127.0.0.1", 12345);
          case "sendText" -> {
            sends.add((Callback)args[1]);
            yield null;
          }
          case "close" -> {
            closes.incrementAndGet();
            open.set(false);
            ((Callback)args[2]).succeed();
            yield null;
          }
          case "disconnect" -> {
            open.set(false);
            yield null;
          }
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  @Test(timeout = 5000)
  public void completionWaitsForACompleteLineAndItsSend() throws Exception {
    WebSocketConnector connector = new WebSocketConnector("/ws");
    connector.onWebSocketOpen(session());
    try {
      connector.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));
      assertFalse(connector.waitOutputCompletion(20));
      connector.getOutputStream().write('\n');
      Callback callback = sends.poll(2, TimeUnit.SECONDS);
      assertNotNull(callback);
      assertFalse(connector.waitOutputCompletion(20));
      callback.succeed();
      assertTrue(connector.waitOutputCompletion(2000));
    } finally { connector.close(); }
  }

  @Test(timeout = 5000)
  public void failedSendUnblocksCompletionAndInput() throws Exception {
    WebSocketConnector connector = new WebSocketConnector("/ws");
    connector.onWebSocketOpen(session());
    connector.getOutputStream().write("hello\n".getBytes(StandardCharsets.UTF_8));
    Callback callback = sends.poll(2, TimeUnit.SECONDS);
    assertNotNull(callback);
    callback.fail(new ClosedChannelException());
    assertFalse(connector.waitOutputCompletion(2000));
    assertEquals(-1, connector.getInputStream().read());
    assertFalse(open.get());
  }

  @Test
  public void closeBeforeOpenRejectsTheSession() {
    WebSocketConnector connector = new WebSocketConnector("/ws");
    connector.setConnectionListener(ignored -> fail("closed connector notified listener"));
    connector.close();
    connector.onWebSocketOpen(session());
    assertFalse(open.get());
    assertEquals(0, connector.connections().length);
  }

  @Test
  public void listenerCanCloseTheConnector() {
    WebSocketConnector connector = new WebSocketConnector("/ws");
    connector.setConnectionListener(Connector::close);
    connector.onWebSocketOpen(session());
    connector.close();
    assertEquals(1, closes.get());
    assertEquals("ws://[closed]", connector.getName());
  }

  @Test
  public void listenerFailureClosesTheConnector() throws Exception {
    WebSocketConnector connector = new WebSocketConnector("/ws");
    connector.setConnectionListener(ignored -> { throw new IllegalStateException("listener failed"); });
    connector.onWebSocketOpen(session());
    assertFalse(open.get());
    assertEquals(-1, connector.getInputStream().read());
  }

  @Test
  public void serverCloseRejectsAnAlreadyCreatedEndpoint() {
    var server = new WebSocketServer(0, "/ws", ignored -> fail("closed server notified listener"));
    WebServer owner = server.server;
    try {
      var connector = (WebSocketConnector)server.createWebSocket(null, null, null);
      server.close();
      assertNull(server.createWebSocket(null, null, null));
      connector.onWebSocketOpen(session());
      assertFalse(open.get());
      assertEquals(1, closes.get());
    } finally { server.close(); owner.stop(); }
  }
}
