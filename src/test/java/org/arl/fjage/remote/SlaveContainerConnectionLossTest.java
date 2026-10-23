/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.arl.fjage.*;
import org.arl.fjage.connectors.TcpConnector;
import org.arl.fjage.loadtest.ThrottlingTcpProxy;
import org.junit.After;
import org.junit.Test;

/**
 * Connection-loss tests for issues #438 and #517.
 */
public class SlaveContainerConnectionLossTest {

  private static final long TIMEOUT = 10000;

  private Platform platform;
  private MasterContainer master;
  private ThrottlingTcpProxy proxy;
  private Gateway gw;

  @After
  public void shutdown() {
    if (gw != null) gw.close();
    if (proxy != null) proxy.shutdownProxy();
    if (master != null) master.shutdown();
    if (platform != null) platform.shutdown();
    gw = null;
    proxy = null;
    master = null;
    platform = null;
  }

  @Test(timeout = 30000)
  public void requestAfterConnectionLossReturnsNull() throws Exception {
    setup();
    gw = new Gateway("localhost", proxy.getPort());
    SlaveContainer slave = (SlaveContainer)gw.getContainer();
    dropMaster();
    slave.checkAuthFailure("some-message-id");   // must not throw NPE
    Message rsp = gw.request(new Message(new AgentID("echo"), Performative.REQUEST), 1000);
    assertNull("Request after connection loss should return null", rsp);
  }

  @Test(timeout = 30000)
  public void requestDuringConnectionLossReturnsNull() throws Exception {
    setup();
    gw = new Gateway("localhost", proxy.getPort());
    final AtomicReference<Message> rsp = new AtomicReference<>();
    final AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread t = new Thread(() -> {
      try {
        rsp.set(gw.request(new Message(new AgentID("echo"), Performative.REQUEST), 3000));
      } catch (Throwable ex) {
        thrown.set(ex);
      }
    });
    t.start();
    Thread.sleep(300);
    dropMaster();
    t.join(TIMEOUT);
    assertFalse("Request did not complete after connection loss", t.isAlive());
    assertNull("Request threw " + thrown.get() + " on connection loss", thrown.get());
    assertNull("Request during connection loss should return null", rsp.get());
  }

  @Test(timeout = 30000)
  public void queriesAndSendsDuringMasterChangesDoNotThrow() throws Exception {
    setup();
    gw = new Gateway("localhost", proxy.getPort());
    SlaveContainer slave = (SlaveContainer)gw.getContainer();
    slave.shutdown();

    // Exercise send paths with the connection manager stopped.
    Field running = Container.class.getDeclaredField("running");
    running.setAccessible(true);
    running.setBoolean(slave, true);
    Field connection = SlaveContainer.class.getDeclaredField("master");
    connection.setAccessible(true);
    CountingHandler handler = new CountingHandler(slave, master.getPort());
    handler.close();

    Message unicast = new Message(new AgentID("remote"));
    Message topic = new Message(new AgentID("topic", true));
    Callable<?>[] calls = {
      slave::getAgents, slave::getServices,
      () -> slave.agentForService("service"),
      () -> slave.agentsForService("service"),
      () -> slave.send(unicast, true),
      () -> slave.send(topic, true)
    };
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      for (int i = 0; i < calls.length; i++) {
        connection.set(slave, null);
        assertEquals(i < 4 ? null : Boolean.FALSE, calls[i].call());
        connection.set(slave, handler);
        assertEquals(i < 4 ? null : Boolean.TRUE, calls[i].call());
        handler.calls.set(0);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger changes = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        Future<?> writer = executor.submit(() -> {
          started.countDown();
          while (!stop.get() && !Thread.currentThread().isInterrupted()) {
            connection.set(slave, handler);
            connection.set(slave, null);
            changes.incrementAndGet();
          }
          return null;
        });
        try {
          assertTrue("Writer did not start", started.await(1, TimeUnit.SECONDS));
          for (int j = 0; j < 200000; j++) {
            Object result = calls[i].call();
            if (i < 4) assertNull(result);
          }
        } finally {
          stop.set(true);
          writer.get(1, TimeUnit.SECONDS);
        }
        assertTrue("Master did not change for call " + i, changes.get() > 0);
        assertTrue("Handler was not used for call " + i, handler.calls.get() > 0);
      }
    } finally {
      executor.shutdownNow();
      connection.set(slave, null);
      handler.close();
      assertTrue("Writer did not stop", executor.awaitTermination(1, TimeUnit.SECONDS));
    }
  }

  @Test(timeout = 30000)
  public void directoryQueryDuringConnectionLossReturnsNull() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    platform = new RealTimePlatform();
    master = new MasterContainer(platform) {
      @Override
      String[] getLocalServices() {
        entered.countDown();
        try {
          release.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        return super.getLocalServices();
      }
    };
    proxy = new ThrottlingTcpProxy("localhost", master.getPort());
    proxy.start();
    platform.start();
    gw = new Gateway("localhost", proxy.getPort());
    SlaveContainer slave = (SlaveContainer)gw.getContainer();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<String[]> query = executor.submit(slave::getServices);
      assertTrue("Directory query did not arrive", entered.await(TIMEOUT, TimeUnit.MILLISECONDS));
      proxy.setRefuse(true);
      proxy.dropConnections();
      assertNull(query.get(1, TimeUnit.SECONDS));
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue("Query did not stop", executor.awaitTermination(1, TimeUnit.SECONDS));
    }
  }

  //////// helpers

  private void setup() throws Exception {
    platform = new RealTimePlatform();
    master = new MasterContainer(platform);
    proxy = new ThrottlingTcpProxy("localhost", master.getPort());
    proxy.start();
    platform.start();
  }

  /**
   * Abruptly drops the TCP connection between the slave and the master, and refuses
   * reconnection attempts, simulating a network failure (not a graceful shutdown).
   */
  private void dropMaster() throws Exception {
    SlaveContainer slave = (SlaveContainer)gw.getContainer();
    proxy.setRefuse(true);
    proxy.dropConnections();
    assertTrue("Slave did not notice connection loss",
      waitUntil(() -> slave.getState().contains("connecting")));
  }

  private boolean waitUntil(java.util.concurrent.Callable<Boolean> condition) throws Exception {
    long deadline = System.currentTimeMillis() + TIMEOUT;
    while (System.currentTimeMillis() < deadline) {
      if (Boolean.TRUE.equals(condition.call())) return true;
      Thread.sleep(50);
    }
    return false;
  }

  private static class CountingHandler extends ConnectionHandler {

    final AtomicInteger calls = new AtomicInteger();

    CountingHandler(SlaveContainer slave, int port) throws IOException {
      super(new TcpConnector("localhost", port), slave);
    }

    @Override
    JsonMessage request(JsonMessage message, long timeout) {
      calls.incrementAndGet();
      return super.request(message, timeout);
    }

    @Override
    synchronized void send(String message) {
      calls.incrementAndGet();
      super.send(message);
    }
  }

}
