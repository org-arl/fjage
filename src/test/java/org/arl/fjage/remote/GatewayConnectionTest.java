/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.arl.fjage.*;
import org.junit.After;
import org.junit.Test;

public class GatewayConnectionTest {

  private static final long TIMEOUT = 10000;

  private Platform platform;
  private MasterContainer master;
  private final List<Closeable> cleanup = new ArrayList<>();

  @After
  public void shutdown() throws IOException {
    for (Closeable c: cleanup) c.close();
    cleanup.clear();
    if (master != null) master.shutdown();
    if (platform != null) platform.shutdown();
    master = null;
    platform = null;
  }

  @Test
  public void gatewayConnectionIsClassified() throws Exception {
    setup();
    FakeGateway gw = connectFakeGateway("gateway-test1");
    ConnectionHandler handler = waitForClassification("gateway-test1");
    assertNotNull("Connection was not classified as gateway", handler);
    assertEquals("gateway-test1", handler.getGatewayAgent().getName());
    assertTrue("Classification probe (agents) was not received", gw.agentsRequests.get() >= 1);
  }

  @Test
  public void directoryQueriesAreSuppressedAfterClassification() throws Exception {
    setup();
    FakeGateway gw = connectFakeGateway("gateway-test2");
    assertNotNull(waitForClassification("gateway-test2"));
    int requestsAfterClassification = gw.directoryRequests.get();
    master.getServices();
    master.agentForService("some-service");
    master.agentsForService("some-service");
    master.getAgents();
    assertTrue(master.canLocateAgent(new AgentID("nonexistent-agent")) == false);
    Thread.sleep(250);    // allow any stray queries to arrive
    assertEquals("Gateway received directory queries after classification",
      requestsAfterClassification, gw.directoryRequests.get());
  }

  @Test
  public void gatewayAgentVisibleInDirectoryFromCache() throws Exception {
    setup();
    connectFakeGateway("gateway-test3");
    assertNotNull(waitForClassification("gateway-test3"));
    List<String> names = new ArrayList<>();
    for (AgentID aid: master.getAgents()) names.add(aid.getName());
    assertTrue("Gateway agent missing from getAgents()", names.contains("gateway-test3"));
    long started = System.nanoTime();
    assertTrue("canLocateAgent failed for gateway agent", master.canLocateAgent(new AgentID("gateway-test3")));
    long elapsed = System.nanoTime() - started;
    assertTrue("Gateway agent lookup was not answered locally", elapsed < TimeUnit.MILLISECONDS.toNanos(1000));
  }

  @Test
  public void collisionWithLocalAgentShutsDownGateway() throws Exception {
    setup();
    master.add("gateway-dup", new Agent());
    FakeGateway gw = connectFakeGateway("gateway-dup");
    assertTrue("Gateway was not asked to shut down on name collision",
      gw.shutdownReceived.await(TIMEOUT, TimeUnit.MILLISECONDS));
    assertTrue("Gateway connection was not closed on name collision",
      waitUntil(() -> !gw.isConnected()));
  }

  @Test
  public void collisionWithAnotherGatewayShutsDownNewcomer() throws Exception {
    setup();
    FakeGateway first = connectFakeGateway("gateway-dup2");
    assertNotNull(waitForClassification("gateway-dup2"));
    FakeGateway second = connectFakeGateway("gateway-dup2");
    assertTrue("Second gateway was not asked to shut down on name collision",
      second.shutdownReceived.await(TIMEOUT, TimeUnit.MILLISECONDS));
    assertTrue("Second gateway connection was not closed", waitUntil(() -> !second.isConnected()));
    Thread.sleep(250);
    assertTrue("First gateway lost its connection", first.isConnected());
  }

  @Test
  public void emptySlaveIsNotClassifiedAsGateway() throws Exception {
    setup();
    SlaveContainer slave = new SlaveContainer(platform, "localhost", master.getPort());
    platform.start();
    // slave is empty at probe time: must be classified as a regular slave, not a gateway
    assertTrue("Slave connection not established", waitUntil(() -> master.getConnectionHandlers().length == 1));
    ConnectionHandler handler = master.getConnectionHandlers()[0];
    master.getAgents();     // triggers classification from AGENTS traffic if probe was missed
    assertFalse("Empty slave misclassified as gateway", handler.isGateway());
    // agents added later must be visible through directory queries
    slave.add("late-agent", new Agent());
    assertTrue("Agent added after classification not visible", waitUntil(() -> {
      for (AgentID aid: master.getAgents())
        if (aid.getName().equals("late-agent")) return true;
      return false;
    }));
    slave.shutdown();
  }

  @Test
  public void realGatewayClassifiedAndStillReceivesMessages() throws Exception {
    setup();
    master.add("echo", new Agent() {
      @Override
      public void init() {
        add(new MessageBehavior() {
          @Override
          public void onReceive(Message msg) {
            send(new Message(msg, Performative.INFORM));
          }
        });
      }
    });
    platform.start();
    Gateway gw = new Gateway("localhost", master.getPort());
    try {
      assertTrue("Real gateway was not classified", waitUntil(() -> {
        for (ConnectionHandler h: master.getConnectionHandlers())
          if (h.isGateway()) return true;
        return false;
      }));
      Message rsp = gw.request(new Message(new AgentID("echo"), Performative.REQUEST), 5000);
      assertNotNull("Gateway request got no response after classification", rsp);
      assertEquals(Performative.INFORM, rsp.getPerformative());
    } finally {
      gw.close();
    }
  }

  //////// helpers

  private void setup() {
    platform = new RealTimePlatform();
    master = new MasterContainer(platform);
  }

  private FakeGateway connectFakeGateway(String name) throws IOException {
    if (!platform.isRunning()) platform.start();
    FakeGateway gw = new FakeGateway(master.getPort(), name);
    cleanup.add(gw);
    return gw;
  }

  private ConnectionHandler waitForClassification(String name) throws InterruptedException {
    long deadline = System.currentTimeMillis() + TIMEOUT;
    while (System.currentTimeMillis() < deadline) {
      for (ConnectionHandler h: master.getConnectionHandlers()) {
        AgentID gw = h.getGatewayAgent();
        if (gw != null && gw.getName().equals(name)) return h;
      }
      Thread.sleep(50);
    }
    return null;
  }

  private boolean waitUntil(java.util.concurrent.Callable<Boolean> condition) throws Exception {
    long deadline = System.currentTimeMillis() + TIMEOUT;
    while (System.currentTimeMillis() < deadline) {
      if (Boolean.TRUE.equals(condition.call())) return true;
      Thread.sleep(50);
    }
    return false;
  }

  /**
   * Raw TCP client presenting itself as a gateway (single agent with a chosen name),
   * so that classification and collision behavior can be tested with deterministic names.
   */
  private static class FakeGateway implements Closeable {

    private static final String ALIVE = "{\"alive\": true}";
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ACTION = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");

    private final Socket sock;
    private final OutputStream out;
    private final Thread reader;
    private final String name;

    final Queue<String> received = new ConcurrentLinkedQueue<>();
    final AtomicInteger agentsRequests = new AtomicInteger();
    final AtomicInteger directoryRequests = new AtomicInteger();
    final CountDownLatch shutdownReceived = new CountDownLatch(1);

    FakeGateway(int port, String name) throws IOException {
      this.name = name;
      sock = new Socket("localhost", port);
      sock.setTcpNoDelay(true);
      out = sock.getOutputStream();
      final BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
      reader = new Thread("fakegateway-reader") {
        @Override
        public void run() {
          try {
            String line;
            while ((line = in.readLine()) != null) {
              received.add(line);
              handle(line);
            }
          } catch (IOException ex) {
            // connection closed
          }
        }
      };
      reader.setDaemon(true);
      reader.start();
      sendLine(ALIVE);
    }

    private void handle(String line) {
      if (line.equals(ALIVE)) {
        sendLine(ALIVE);
        return;
      }
      Matcher am = ACTION.matcher(line);
      if (!am.find()) return;
      String action = am.group(1);
      if (action.equals("shutdown")) {
        shutdownReceived.countDown();
        return;
      }
      Matcher im = ID.matcher(line);
      String id = im.find() ? im.group(1) : null;
      if (id == null) return;
      if (action.equals("agents")) {
        agentsRequests.incrementAndGet();
        directoryRequests.incrementAndGet();
        sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"agents\", \"agentIDs\": [\"" + name + "\"], \"agentTypes\": [\"org.arl.fjage.Agent\"]}");
      } else if (action.equals("containsAgent")) {
        directoryRequests.incrementAndGet();
        sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"containsAgent\", \"answer\": false}");
      } else if (action.equals("services")) {
        directoryRequests.incrementAndGet();
        sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"services\", \"services\": []}");
      } else if (action.equals("agentForService")) {
        directoryRequests.incrementAndGet();
        sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"agentForService\"}");
      } else if (action.equals("agentsForService")) {
        directoryRequests.incrementAndGet();
        sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"agentsForService\", \"agentIDs\": []}");
      }
    }

    private synchronized void sendLine(String s) {
      try {
        out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
      } catch (IOException ex) {
        // connection closed
      }
    }

    boolean isConnected() {
      return sock.isConnected() && !sock.isClosed() && reader.isAlive();
    }

    @Override
    public void close() throws IOException {
      sock.close();
    }

  }

}
