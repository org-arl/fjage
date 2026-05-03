package org.arl.fjage.test;

import org.arl.fjage.Agent;
import org.arl.fjage.AgentID;
import org.arl.fjage.LogFormatter;
import org.arl.fjage.Message;
import org.arl.fjage.MessageBehavior;
import org.arl.fjage.Performative;
import org.arl.fjage.Platform;
import org.arl.fjage.RealTimePlatform;
import org.arl.fjage.connectors.ConnectionListener;
import org.arl.fjage.connectors.Connector;
import org.arl.fjage.remote.MasterContainer;
import org.arl.fjage.remote.SlaveContainer;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AsyncTests {

  private static final long SETUP_TIMEOUT = 5000;
  private static final long MESSAGE_TIMEOUT = 2000;
  private static final long LOOKUP_TIMEOUT = 20000;

  @Before
  public void beforeTesting() {
    LogFormatter.install(null);
  }

  @Test(timeout = 30000)
  public void testMasterSendWhileAgentsForServiceWaitsOnUnresponsiveHandler() throws Exception {
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    SlaveContainer goodSlave = new SlaveContainer(platform, "localhost", master.getPort());
    ServiceAgent goodAgent = new ServiceAgent();
    goodSlave.add("server", goodAgent);
    BadConnector badConnector = new BadConnector("bad-connector");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<AgentID[]> blockedLookup = null;
    try {
      platform.start();

      AgentID goodAgentID = waitForService(master, "server", SETUP_TIMEOUT);
      assertNotNull(goodAgentID);

      master.addConnector(badConnector);
      waitForHandlerAlive(master, "bad-connector", SETUP_TIMEOUT);

      blockedLookup = executor.submit(() -> master.agentsForService("server"));
      Future<AgentID[]> lookup = blockedLookup;

      Thread.sleep(200);
      assertFalse(lookup.isDone());

      assertTrue(master.send(new NuisanceMessage(goodAgentID)));
      assertTrue(goodAgent.awaitNuisance(MESSAGE_TIMEOUT));
      assertFalse(lookup.isDone());

      AgentID[] result = lookup.get(LOOKUP_TIMEOUT, TimeUnit.MILLISECONDS);
      assertTrue(Arrays.stream(result).anyMatch(aid -> aid != null && goodAgentID.getName().equals(aid.getName())));
      assertEquals(1, goodAgent.getNuisanceCount());
    } finally {
      if (blockedLookup != null) blockedLookup.cancel(true);
      executor.shutdownNow();
      badConnector.close();
      platform.shutdown();
    }
  }

  @Test(timeout = 30000)
  public void testMasterAgentForServiceReturnsResponsiveMatchWithoutWaitingForUnresponsiveHandler() throws Exception {
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    SlaveContainer goodSlave = new SlaveContainer(platform, "localhost", master.getPort());
    ServiceAgent goodAgent = new ServiceAgent();
    AgentID goodAgentID = goodSlave.add("server", goodAgent);
    BadConnector badConnector = new BadConnector("bad-connector");
    try {
      platform.start();

      waitForService(master, "server", SETUP_TIMEOUT);

      master.addConnector(badConnector);
      waitForHandlerAlive(master, "bad-connector", SETUP_TIMEOUT);

      long start = System.nanoTime();
      AgentID result = master.agentForService("server");
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertNotNull(result);
      assertEquals(goodAgentID.getName(), result.getName());
      assertTrue("agentForService() waited " + elapsedMs + " ms for an unresponsive handler",
        elapsedMs < MESSAGE_TIMEOUT);
    } finally {
      badConnector.close();
      platform.shutdown();
    }
  }

  @Test(timeout = 30000)
  public void testMasterRemoteHelpersAggregateAcrossMasterAndResponsiveSlaves() throws Exception {
    final String sharedService = "shared";
    final String masterOnlyService = "masterOnly";
    final String slaveOneOnlyService = "slaveOneOnly";
    final String slaveTwoOnlyService = "slaveTwoOnly";

    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    AgentID masterAgentID = master.add("masterServer", new ServiceAgent(sharedService, masterOnlyService));
    SlaveContainer slaveOne = new SlaveContainer(platform, "localhost", master.getPort());
    AgentID slaveOneAgentID = slaveOne.add("slaveOneServer", new ServiceAgent(sharedService, slaveOneOnlyService));
    SlaveContainer slaveTwo = new SlaveContainer(platform, "localhost", master.getPort());
    AgentID slaveTwoAgentID = slaveTwo.add("slaveTwoServer", new ServiceAgent(sharedService, slaveTwoOnlyService));
    try {
      platform.start();

      waitUntil(() -> master.agentsForService(sharedService).length == 3, SETUP_TIMEOUT);

      assertAgentNamesContain(master.getAgents(), masterAgentID, slaveOneAgentID, slaveTwoAgentID);
      assertServicesContain(master.getServices(), sharedService, masterOnlyService, slaveOneOnlyService, slaveTwoOnlyService);

      AgentID[] sharedAgents = master.agentsForService(sharedService);
      assertEquals(3, sharedAgents.length);
      assertAgentNamesContain(sharedAgents, masterAgentID, slaveOneAgentID, slaveTwoAgentID);
    } finally {
      platform.shutdown();
    }
  }

  private void assertAgentNamesContain(AgentID[] actualAgents, AgentID... expectedAgents) {
    Set<String> agentNames = new LinkedHashSet<>();
    for (AgentID aid: actualAgents) {
      if (aid != null) agentNames.add(aid.getName());
    }
    for (AgentID expected: expectedAgents) {
      assertTrue("Missing agent: " + expected.getName(), agentNames.contains(expected.getName()));
    }
  }

  private void assertServicesContain(String[] actualServices, String... expectedServices) {
    Set<String> services = new LinkedHashSet<>(Arrays.asList(actualServices));
    for (String service: expectedServices) {
      assertTrue("Missing service: " + service, services.contains(service));
    }
  }

  private AgentID waitForService(MasterContainer master, String service, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      AgentID aid = master.agentForService(service);
      if (aid != null) return aid;
      Thread.sleep(50);
    }
    fail("Timed out waiting for service: " + service);
    return null;
  }

  private void waitForHandlerAlive(MasterContainer master, String handlerName, long timeoutMs) throws InterruptedException {
    waitUntil(() -> Arrays.stream(master.getConnectionHandlers())
      .anyMatch(handler -> handler.getName().contains(handlerName) && handler.isConnectionAlive()), timeoutMs);
  }

  private void waitUntil(Check condition, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (condition.get()) return;
      Thread.sleep(50);
    }
    throw new AssertionError("Condition not satisfied within timeout");
  }

  @FunctionalInterface
  private interface Check {
    boolean get();
  }

  private static class ServiceAgent extends Agent {
    private final String[] services;
    private final CountDownLatch nuisanceReceived = new CountDownLatch(1);
    private final AtomicInteger nuisanceCount = new AtomicInteger();

    ServiceAgent() {
      this("server");
    }

    ServiceAgent(String... services) {
      this.services = services == null || services.length == 0 ? new String[] {"server"} : services.clone();
    }

    @Override
    public void init() {
      for (String service: services) register(service);
      add(new MessageBehavior(NuisanceMessage.class) {
        @Override
        public void onReceive(Message msg) {
          nuisanceCount.incrementAndGet();
          nuisanceReceived.countDown();
        }
      });
    }

    boolean awaitNuisance(long timeoutMs) throws InterruptedException {
      return nuisanceReceived.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    int getNuisanceCount() {
      return nuisanceCount.get();
    }
  }

  private static class NuisanceMessage extends Message {
    private static final long serialVersionUID = 1L;

    NuisanceMessage(AgentID recipient) {
      super(recipient, Performative.INFORM);
    }
  }

  /**
   * A deliberately bad connector that simulates an unresponsive connection handler. It provides a valid input stream that can be read from, but never responds to output writes or connection status checks. This allows us to test that the master container's lookup and send operations are not blocked by an unresponsive handler.
   */
  private static class BadConnector implements Connector {
    private final String name;
    private final PipedInputStream input = new PipedInputStream();
    private final PipedOutputStream inputWriter;
    private final OutputStream output = new ByteArrayOutputStream();

    BadConnector(String name) throws IOException {
      this.name = name;
      this.inputWriter = new PipedOutputStream(input);
      inputWriter.write("{\"alive\": true}\n".getBytes());
      inputWriter.flush();
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public InputStream getInputStream() {
      return input;
    }

    @Override
    public OutputStream getOutputStream() {
      return output;
    }

    @Override
    public boolean isReliable() {
      return true;
    }

    @Override
    public boolean waitOutputCompletion(long timeout) {
      return true;
    }

    @Override
    public void setConnectionListener(ConnectionListener listener) {
      // no-op for tests
    }

    @Override
    public String[] connections() {
      return new String[0];
    }

    @Override
    public void close() {
      try {
        inputWriter.close();
      } catch (IOException ignored) {
        // do nothing
      }
      try {
        input.close();
      } catch (IOException ignored) {
        // do nothing
      }
    }

    @Override
    public String toString() {
      return name;
    }
  }
}