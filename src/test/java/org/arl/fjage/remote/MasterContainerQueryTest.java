/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import org.arl.fjage.*;
import org.junit.After;
import org.junit.Test;

public class MasterContainerQueryTest {

  private Platform platform;
  private MasterContainer master;
  private final List<DelayedSlaveContainer> slaves = new ArrayList<>();

  @After
  public void shutdown() {
    if (master != null) master.shutdown();
    for (DelayedSlaveContainer slave: slaves)
      slave.shutdown();
    if (platform != null) platform.shutdown();
  }

  @Test
  public void duplicateLookupReturnsAfterFastMatchingSlave() throws Exception {
    setup();
    CountDownLatch slowEntered = new CountDownLatch(1);
    DelayedSlaveContainer slow = addSlave(2500, slowEntered, null);
    DelayedSlaveContainer fast = addSlave(0, null, slowEntered);
    fast.add("remote", new Agent());
    start();

    long started = System.nanoTime();
    assertTrue(master.canLocateAgent(new AgentID("remote")));
    long elapsed = System.nanoTime() - started;

    assertTrue("Slow slave was not queried", slowEntered.await(1, TimeUnit.SECONDS));
    assertTrue("Duplicate lookup waited for slow slave", elapsed < TimeUnit.MILLISECONDS.toNanos(1500));
  }

  @Test
  public void serviceLookupReturnsAfterFastProvider() throws Exception {
    setup();
    CountDownLatch slowEntered = new CountDownLatch(1);
    DelayedSlaveContainer slow = addSlave(2500, slowEntered, null);
    DelayedSlaveContainer fast = addSlave(0, null, slowEntered);
    AgentID provider = new AgentID("provider");
    fast.register(provider, "service");
    start();

    long started = System.nanoTime();
    assertEquals(provider, master.agentForService("service"));
    long elapsed = System.nanoTime() - started;

    assertTrue("Slow slave was not queried", slowEntered.await(1, TimeUnit.SECONDS));
    assertTrue("Service lookup waited for slow slave", elapsed < TimeUnit.MILLISECONDS.toNanos(1500));
  }

  @Test
  public void serviceAggregationUsesCompletedResponses() throws Exception {
    setup();
    CountDownLatch slowEntered = new CountDownLatch(1);
    DelayedSlaveContainer slow = addSlave(500, slowEntered, null);
    DelayedSlaveContainer fast = addSlave(0, null, slowEntered);
    slow.register(new AgentID("slow"), "slow-service");
    fast.register(new AgentID("fast"), "fast-service");
    start();

    String[] services = master.getServices();

    Set<String> serviceSet = new HashSet<>(Arrays.asList(services));
    assertEquals(new HashSet<>(Arrays.asList("fast-service", "slow-service")), serviceSet);
  }

  @Test
  public void aggregateQueriesUseOneSixSecondBudget() throws Exception {
    setup();
    DelayedSlaveContainer slow = addSlave(7000, null, null);
    DelayedSlaveContainer fast = addSlave(0, null, null);
    slow.register(new AgentID("slow"), "slow-service");
    fast.register(new AgentID("fast"), "fast-service");
    start();

    long started = System.nanoTime();
    String[] services = master.getServices();
    long elapsed = System.nanoTime() - started;

    assertTrue(Arrays.asList(services).contains("fast-service"));
    assertFalse(Arrays.asList(services).contains("slow-service"));
    assertTrue("Aggregate query exceeded total timeout", elapsed < TimeUnit.MILLISECONDS.toNanos(6800));
  }

  @Test
  public void slowSlaveDoesNotWedgeConcurrentAggregateQueries() throws Exception {
    setup();
    addSlave(7000, null, null);
    DelayedSlaveContainer client = addSlave(0, null, null);
    AgentID provider = client.add("provider", new Agent());
    client.register(provider, "service");
    start();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<AgentID[]> agents = executor.submit(client::getAgents);
      Future<AgentID[]> serviceAgents = executor.submit(() -> client.agentsForService("service"));

      assertTrue("Slave AGENTS query timed out", Arrays.asList(agents.get(6, TimeUnit.SECONDS)).contains(provider));
      assertTrue("Slave AGENTS_FOR_SERVICE query timed out", Arrays.asList(serviceAgents.get(6, TimeUnit.SECONDS)).contains(provider));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void concurrentDirectoryQueriesShareConnectionsSafely() throws Exception {
    setup();
    DelayedSlaveContainer slave = addSlave(0, null, null);
    AgentID provider = new AgentID("provider");
    slave.register(provider, "service");
    start();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<AgentID> first = executor.submit(() -> master.agentForService("service"));
      Future<AgentID> second = executor.submit(() -> master.agentForService("service"));
      assertEquals(provider, first.get(2, TimeUnit.SECONDS));
      assertEquals(provider, second.get(2, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void relaySendsPreserveOrderForEachSlave() throws Exception {
    setup();
    DelayedSlaveContainer slave = addSlave(0, null, null);
    RecordingAgent receiver = new RecordingAgent(3);
    AgentID receiverID = slave.add("receiver", receiver);
    start();

    assertTrue(master.send(new RelayMessage(receiverID, 1)));
    assertTrue(master.send(new RelayMessage(receiverID, 2)));
    assertTrue(master.send(new RelayMessage(receiverID, 3)));

    assertTrue("Relayed messages were not received", receiver.received.await(2, TimeUnit.SECONDS));
    assertEquals(Arrays.asList(1, 2, 3), receiver.sequence);
  }

  @Test
  public void closingHandlerWakesPendingQuery() throws Exception {
    setup();
    CountDownLatch entered = new CountDownLatch(1);
    addSlave(7000, entered, null);
    start();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<String[]> query = executor.submit(() -> master.getServices());
      assertTrue("Slave was not queried", entered.await(1, TimeUnit.SECONDS));
      master.getConnectionHandlers()[0].close();
      assertNotNull(query.get(1, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private void setup() {
    platform = new RealTimePlatform();
    master = new MasterContainer(platform);
  }

  private DelayedSlaveContainer addSlave(long delay, CountDownLatch entered, CountDownLatch waitFor) {
    DelayedSlaveContainer slave = new DelayedSlaveContainer(platform, "localhost", master.getPort(), delay, entered, waitFor);
    slaves.add(slave);
    return slave;
  }

  private void start() {
    platform.start();
  }

  private static class DelayedSlaveContainer extends SlaveContainer {

    private final long delay;
    private final CountDownLatch entered;
    private final CountDownLatch waitFor;

    DelayedSlaveContainer(Platform platform, String hostname, int port, long delay, CountDownLatch entered, CountDownLatch waitFor) {
      super(platform, hostname, port);
      this.delay = delay;
      this.entered = entered;
      this.waitFor = waitFor;
    }

    @Override
    public boolean containsAgent(AgentID aid) {
      delay();
      return super.containsAgent(aid);
    }

    @Override
    AgentID[] getLocalAgents() {
      delay();
      return super.getLocalAgents();
    }

    @Override
    String[] getLocalServices() {
      delay();
      return super.getLocalServices();
    }

    @Override
    AgentID localAgentForService(String service) {
      delay();
      return super.localAgentForService(service);
    }

    @Override
    AgentID[] localAgentsForService(String service) {
      delay();
      return super.localAgentsForService(service);
    }

    private void delay() {
      if (entered != null) entered.countDown();
      if (waitFor != null) {
        try {
          waitFor.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return;
        }
      }
      if (delay <= 0) return;
      try {
        Thread.sleep(delay);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

  }

  private static class RelayMessage extends Message {

    private static final long serialVersionUID = 1L;

    final int sequence;

    RelayMessage(AgentID recipient, int sequence) {
      super(recipient);
      this.sequence = sequence;
    }

  }

  private static class RecordingAgent extends Agent {

    final CountDownLatch received;
    final List<Integer> sequence = Collections.synchronizedList(new ArrayList<Integer>());

    RecordingAgent(int messages) {
      received = new CountDownLatch(messages);
    }

    @Override
    public void init() {
      add(new MessageBehavior(RelayMessage.class) {
        @Override
        public void onReceive(Message msg) {
          sequence.add(((RelayMessage) msg).sequence);
          received.countDown();
        }
      });
    }

  }

}