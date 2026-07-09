package org.arl.fjage.connectors;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link WebSocketWriter} using fake Jetty {@link Session}/{@link RemoteEndpoint}
 * implementations (no real sockets), covering the three guarantees of the write pump:
 * single in-flight ordered delivery, bounded-queue overflow disconnect, and the write-stall watchdog.
 */
public class WebSocketWriterTest {

  private static final Logger log = Logger.getLogger(WebSocketWriterTest.class.getName());

  /**
   * Fake endpoint that records sent messages and holds their callbacks so the test can
   * release them one at a time, modelling Jetty's one-outstanding-write-per-endpoint contract.
   */
  private static class FakeWs {
    final List<String> sent = new ArrayList<>();
    final Deque<WriteCallback> pending = new ArrayDeque<>();
    final AtomicInteger inFlight = new AtomicInteger();
    int maxInFlight = 0;
    final CountDownLatch disconnected = new CountDownLatch(1);

    final Session session;

    FakeWs() {
      RemoteEndpoint remote = (RemoteEndpoint) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[]{RemoteEndpoint.class}, this::remoteInvoke);
      this.session = (Session) Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[]{Session.class},
          (proxy, method, args) -> sessionInvoke(remote, method, args));
    }

    private synchronized Object remoteInvoke(Object proxy, Method method, Object[] args) {
      if ("sendString".equals(method.getName()) && args != null && args.length == 2) {
        sent.add((String) args[0]);
        pending.add((WriteCallback) args[1]);
        maxInFlight = Math.max(maxInFlight, inFlight.incrementAndGet());
        return null;
      }
      return defaultValue(method);
    }

    private Object sessionInvoke(RemoteEndpoint remote, Method method, Object[] args) {
      switch (method.getName()) {
        case "getRemote": return remote;
        case "isOpen": return Boolean.TRUE;
        case "getRemoteAddress": return new InetSocketAddress("127.0.0.1", 12345);
        case "disconnect": disconnected.countDown(); return null;
        default: return defaultValue(method);
      }
    }

    /** Complete the oldest outstanding write, mirroring Jetty's async writeSuccess callback. */
    void releaseOne() {
      WriteCallback cb;
      synchronized (this) {
        cb = pending.poll();
        if (cb == null) return;
        inFlight.decrementAndGet();
      }
      cb.writeSuccess();
    }
  }

  private static Object defaultValue(Method method) {
    Class<?> t = method.getReturnType();
    if (t == boolean.class) return Boolean.FALSE;
    if (t == int.class) return 0;
    if (t == long.class) return 0L;
    if (t.isPrimitive()) return 0;
    return null;
  }

  @Test(timeout = 5000)
  public void deliversInOrderWithOneWriteInFlight() {
    FakeWs ws = new FakeWs();
    WebSocketWriter writer = new WebSocketWriter(ws.session, null, log, 1024, 30000);

    int n = 50;
    for (int i = 0; i < n; i++) writer.enqueue("line-" + i);
    // drain: each release lets the pump fire the next write
    for (int i = 0; i < n; i++) ws.releaseOne();

    assertEquals("all messages delivered", n, ws.sent.size());
    for (int i = 0; i < n; i++) assertEquals("line-" + i, ws.sent.get(i));
    assertEquals("never more than one write in flight", 1, ws.maxInFlight);
  }

  @Test(timeout = 5000)
  public void overflowDisconnectsClient() throws Exception {
    FakeWs ws = new FakeWs();
    int maxQueue = 8;
    WebSocketWriter writer = new WebSocketWriter(ws.session, null, log, maxQueue, 30000);

    // never release callbacks: first write stays in flight, queue fills, then overflows
    for (int i = 0; i < maxQueue + 5; i++) writer.enqueue("m" + i);

    assertTrue("client disconnected on overflow", ws.disconnected.await(2, TimeUnit.SECONDS));
    // after force-close, further enqueues are silently dropped (no growth, no exception)
    writer.enqueue("after-close");
  }

  @Test(timeout = 5000)
  public void stalledWriteTripsWatchdog() throws Exception {
    FakeWs ws = new FakeWs();
    // short timeout; the single in-flight write never completes
    WebSocketWriter writer = new WebSocketWriter(ws.session, null, log, 1024, 200);

    writer.enqueue("stalls");
    assertTrue("watchdog disconnected the stalled client", ws.disconnected.await(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 5000)
  public void closeIsIdempotentAndStopsDelivery() {
    FakeWs ws = new FakeWs();
    WebSocketWriter writer = new WebSocketWriter(ws.session, null, log, 1024, 30000);
    writer.close();
    writer.close();
    writer.enqueue("ignored");
    assertEquals("no writes after close", 0, ws.sent.size());
  }
}
