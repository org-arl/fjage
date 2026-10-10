package org.arl.fjage.connectors;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.Test;

public class WebSocketSupportTest {
  private final AtomicBoolean disconnected = new AtomicBoolean();
  private final CountDownLatch sending = new CountDownLatch(1);
  private final Logger log = Logger.getLogger(getClass().getName());

  private Session session(Consumer<Callback> complete) {
    return (Session)Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[] {Session.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "isOpen" -> !disconnected.get();
          case "getRemoteSocketAddress" -> new InetSocketAddress("127.0.0.1", 12345);
          case "disconnect" -> {
            disconnected.set(true);
            yield null;
          }
          case "sendText" -> {
            assertEquals("text", args[0]);
            sending.countDown();
            complete.accept((Callback)args[1]);
            yield null;
          }
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  @Test
  public void successfulCallbackKeepsConnectionOpen() {
    WebSocketSupport.sendText(session(Callback::succeed), "text", log);
    assertFalse(disconnected.get());
  }

  @Test
  public void failedCallbackDisconnects() {
    WebSocketSupport.sendText(session(callback -> callback.fail(new ClosedChannelException())), "text", log);
    assertTrue(disconnected.get());
  }

  @Test(timeout = 5000)
  public void missingCallbackTimesOutAndDisconnects() {
    WebSocketSupport.sendText(session(callback -> {}), "text", log);
    assertTrue(disconnected.get());
  }

  @Test(timeout = 5000)
  public void interruptedWriterDisconnectsAndKeepsInterrupt() throws Exception {
    AtomicBoolean interrupted = new AtomicBoolean();
    Session session = session(callback -> {});
    Thread writer = new Thread(() -> {
      WebSocketSupport.sendText(session, "text", log);
      interrupted.set(Thread.currentThread().isInterrupted());
    });
    writer.start();
    assertTrue(sending.await(2, TimeUnit.SECONDS));
    writer.interrupt();
    writer.join(2000);
    assertFalse(writer.isAlive());
    assertTrue(disconnected.get());
    assertTrue(interrupted.get());
  }
}
