/******************************************************************************

Copyright (c) 2015-2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.util.*;
import org.arl.fjage.*;
import org.arl.fjage.auth.*;
import org.arl.fjage.connectors.*;

import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Master container supporting multiple remote slave containers. Agents in linked
 * master and slave containers function as if they were in the same container,
 * i.e., are able to communicate with each other through messaging, topics and
 * directory services.
 *
 * @author Mandar Chitre
 */
public class MasterContainer extends RemoteContainer implements ConnectionListener {

  ////////////// Private attributes

  private static final long TIMEOUT = 15000;

  private TcpServer tcpListener = null;
  private WebSocketServer websocketListener = null;
  private final CopyOnWriteArrayList<ConnectionHandler> slaves = new CopyOnWriteArrayList<>();
  private Supplier<Firewall> fwSupplier = AllowAll.SUPPLIER;
  private final ExecutorService executor = Executors.newFixedThreadPool(10);

  ////////////// Constructors

  /**
   * Creates a master container, runs its TCP server on an automatically selected port.
   *
   * @param platform platform on which the container runs.
   */
  public MasterContainer(Platform platform) {
    super(platform);
    openTcpServer(0);
  }

  /**
   * Creates a master container, runs its TCP server on an automatically selected port,
   * with a specified firewall supplier.
   *
   * @param platform platform on which the container runs.
   * @param fwSupplier firewall supplier to use.
   */
  public MasterContainer(Platform platform, Supplier<Firewall> fwSupplier) {
    super(platform);
    this.fwSupplier = fwSupplier;
    openTcpServer(0);
  }

  /**
   * Creates a master container, runs its TCP server on a specified port.
   *
   * @param platform platform on which the container runs.
   * @param port port on which the container's TCP server runs.
   */
  public MasterContainer(Platform platform, int port) {
    super(platform);
    openTcpServer(port);
  }

  /**
   * Creates a master container, runs its TCP server on a specified port,
   * with a specified firewall supplier.
   *
   * @param platform platform on which the container runs.
   * @param port port on which the container's TCP server runs.
   * @param fwSupplier firewall supplier to use.
   */
  public MasterContainer(Platform platform, int port, Supplier<Firewall> fwSupplier) {
    super(platform);
    this.fwSupplier = fwSupplier;
    openTcpServer(port);
  }

  /**
   * Creates a named master container, runs its TCP server on an automatically selected port.
   *
   * @param platform platform on which the container runs.
   * @param name name of the container.
   */
  public MasterContainer(Platform platform, String name) {
    super(platform, name);
    openTcpServer(0);
  }

  /**
   * Creates a named master container, runs its TCP server on an automatically selected port,
   * with a specified firewall supplier.
   *
   * @param platform platform on which the container runs.
   * @param name name of the container.
   * @param fwSupplier firewall supplier to use.
   */
  public MasterContainer(Platform platform, String name, Supplier<Firewall> fwSupplier) {
    super(platform, name);
    this.fwSupplier = fwSupplier;
    openTcpServer(0);
  }

  /**
   * Creates a named master container, runs its TCP server on a specified port.
   *
   * @param platform platform on which the container runs.
   * @param name of the container.
   * @param port port on which the container's TCP server runs.
   */
  public MasterContainer(Platform platform, String name, int port) {
    super(platform, name);
    openTcpServer(port);
  }

  /**
   * Creates a named master container, runs its TCP server on a specified port,
   * with a specified firewall supplier.
   *
   * @param platform platform on which the container runs.
   * @param name of the container.
   * @param port port on which the container's TCP server runs.
   * @param fwSupplier firewall supplier to use.
   */
  public MasterContainer(Platform platform, String name, int port, Supplier<Firewall> fwSupplier) {
    super(platform, name);
    this.fwSupplier = fwSupplier;
    openTcpServer(port);
  }

  /**
   * Gets the TCP port on which the master container listens for connections.
   *
   * @return port on which the container's TCP server runs, -1 if none.
   */
  public int getPort() {
    if (tcpListener == null) return -1;
    return tcpListener.getPort();
  }

  /**
   * Adds a connector over which the master container listens. The connector uses
   * a firewall acquired from the firewall supplier configured for the container.
   *
   * @param conn connector.
   */
  public void addConnector(Connector conn) {
    log.info("Listening on "+conn.getName());
    ConnectionHandler t = new ConnectionHandler(conn, MasterContainer.this, fwSupplier.get());
    slaves.add(t);
    t.start();
  }

  /**
   * Adds a connector over which the master container listens, but with a custom
   * firewall for that connector.
   *
   * @param conn connector.
   * @param fw firewall.
   */
  public void addConnector(Connector conn, Firewall fw) {
    log.info("Listening on "+conn.getName());
    ConnectionHandler t = new ConnectionHandler(conn, MasterContainer.this, fw);
    slaves.add(t);
    t.start();
  }

  /**
   * Gets a list of connector URLs that slaves can use to access the master container.
   */
  public String[] getConnectors() {
    List<String> urls = new ArrayList<>();
    if (tcpListener != null) urls.add(tcpListener.toString());
    slaves.forEach(slave -> urls.add(slave.toString()));
    return urls.toArray(new String[0]);
  }

  /**
   * Gets a list of connection handlers for the slaves connected to this master container.
   *
   * @return array of connection handlers.
   */
  public ConnectionHandler[] getConnectionHandlers(){
    return slaves.toArray(new ConnectionHandler[0]);
  }

  /////////////// Container interface methods to override
  @Override
  protected boolean isDuplicate(AgentID aid) {
    if (super.isDuplicate(aid)) return true;
    return firstMatch(slaves, slave -> {
      JsonMessage rq = JsonMessage.createActionRequest(Action.CONTAINS_AGENT);
      rq.agentID = aid;
      return slave.requestAsync(rq, TIMEOUT);
    }, rsp -> rsp != null && Boolean.TRUE.equals(rsp.answer)).isPresent();
  }

  @Override
  public boolean send(Message m) {
    return send(m, true);
  }

  @Override
  public boolean send(Message m, boolean relay) {
    boolean sent = super.send(m, false);
    AgentID aid = m.getRecipient();
    if (aid == null) return false;
    if (sent && !aid.isTopic()) return true;
    if (!relay) return false;
    JsonMessage rq = JsonMessage.createActionRequest(Action.SEND);
    rq.message = m;
    rq.relay = false;
    String json = rq.toJson();
    runAll(slaves, slave -> {
      if (slave.wantsMessagesFor(aid)) slave.send(json);
    }, TIMEOUT);
    return true;
  }

  @Override
  public AgentID[] getAgents() {
    AgentID[] aids = super.getAgents();
    AgentID[] remoteAids = concat(slaves, slave -> {
      JsonMessage rq = JsonMessage.createActionRequest(Action.AGENTS);
      return slave.requestAsync(rq, TIMEOUT).thenApply(rsp -> {
        if (rsp == null || rsp.agentIDs == null) return new AgentID[0];
        if (rsp.agentTypes != null && rsp.agentTypes.length == rsp.agentIDs.length) {
          for (int i = 0; i < rsp.agentIDs.length; i++)
            rsp.agentIDs[i].setType(rsp.agentTypes[i]);
        }
        return rsp.agentIDs;
      });
    }, AgentID[]::new);
    return Stream.concat(Arrays.stream(aids), Arrays.stream(remoteAids)).toArray(AgentID[]::new);
  }

  @Override
  public String[] getServices() {
    String[] services = super.getServices();
    String[] remoteServices = concat(slaves, slave -> {
      JsonMessage rq = JsonMessage.createActionRequest(Action.SERVICES);
      return slave.requestAsync(rq, TIMEOUT).thenApply(rsp -> rsp == null || rsp.services == null ? new String[0] : rsp.services);
    }, String[]::new);
    Set<String> allServices = new LinkedHashSet<>(Arrays.asList(services));
    allServices.addAll(Arrays.asList(remoteServices));
    return allServices.toArray(new String[0]);
  }

  @Override
  public AgentID agentForService(String service) {
    AgentID aid = super.agentForService(service);
    if (aid != null) return aid;
    return firstMatch(slaves, slave -> {
      JsonMessage rq = JsonMessage.createActionRequest(Action.AGENT_FOR_SERVICE);
      rq.service = service;
      return slave.requestAsync(rq, TIMEOUT);
    }, rsp -> rsp != null && rsp.agentID != null && !rsp.agentID.getName().isEmpty())
      .map(rsp -> rsp.agentID)
      .orElse(null);
  }

  /**
   * Gets the agent IDs of all agents that provide a given service.s
   * @param service name of the service.
   * @return array of agent IDs, empty if no agents provide the service.
   */
  @Override
  public AgentID[] agentsForService(String service) {
    AgentID[] aids = super.agentsForService(service);
    if (aids == null) aids = new AgentID[0];
    AgentID[] remoteAids = concat(slaves, slave -> {
      JsonMessage rq = JsonMessage.createActionRequest(Action.AGENTS_FOR_SERVICE);
      rq.service = service;
      return slave.requestAsync(rq, TIMEOUT).thenApply(rsp -> rsp == null || rsp.agentIDs == null ? new AgentID[0] : rsp.agentIDs);
    }, AgentID[]::new);
    return Stream.concat(Arrays.stream(aids), Arrays.stream(remoteAids)).toArray(AgentID[]::new);
  }

  @Override
  AgentID[] getLocalAgents() {
    return getAgents();
  }

  @Override
  String[] getLocalServices() {
    return getServices();
  }

  @Override
  AgentID localAgentForService(String service) {
    return agentForService(service);
  }

  @Override
  AgentID[] localAgentsForService(String service) {
    return agentsForService(service);
  }

  @Override
  protected void initComplete() {
    runAll(slaves, slave -> {
      if (!slave.isAlive()) slave.start();
    }, TIMEOUT);
    if (!slaves.isEmpty()) {
      log.fine("Waiting for slaves...");
      boolean allAlive = false;
      // Bound startup sync to a short local wait; request timeouts are handled later.
      for (int i = 0; !allAlive && i < 50; i++) {
        try {
          Thread.sleep(100);
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        allAlive = true;
        for (ConnectionHandler slave: slaves) {
          if (!slave.isConnectionAlive()) {
            allAlive = false;
            break;
          }
        }
      }
      if (allAlive) log.fine("All slaves are alive");
      else log.warning("Some slaves timed out!");
    }
  }

  @Override
  public void shutdown() {
    if (!running) return;
    String json = JsonMessage.createActionRequest(Action.SHUTDOWN).toJson();
    runAll(slaves, slave -> {
      slave.send(json);
      slave.close();
    }, TIMEOUT);
    slaves.clear();
    if (tcpListener != null) {
      tcpListener.close();
      tcpListener = null;
    }
    if (websocketListener != null){
      websocketListener.close();
      websocketListener = null;
    }
    executor.shutdownNow();
    super.shutdown();
  }

  @Override
  public String toString() {
    String s = getClass().getName()+"@"+name;
    s += "/master/"+platform;
    return s;
  }

  @Override
  public void connectionClosed(ConnectionHandler handler) {
    log.info("Connection "+handler.getName()+" closed");
    slaves.remove(handler);
  }

  public boolean openWebSocketServer( int port, String context) {
    return openWebSocketServer(port, context, -1);
  }

  public boolean openWebSocketServer( int port, String context, int maxMsgSize) {
    if (websocketListener != null) {
      log.warning("WebSocket server already running at :" + websocketListener.getPort() + websocketListener.getContext());
      return false;
    }
    if (!context.startsWith("/")) throw new IllegalArgumentException("Context must start with '/'");
    websocketListener = new WebSocketServer(port, context, this, maxMsgSize);
    log.info("WebSocketServer running at :" + websocketListener.getPort() + websocketListener.getContext());
    return true;
  }

  public boolean closeWebSocketServer() {
    if (websocketListener == null) {
      log.warning("WebSocket server not running");
      return false;
    }
    websocketListener.close();
    websocketListener = null;
    return true;
  }

  /////////////// ConnectionListener interface method

  @Override
  public void connected(Connector conn) {
    log.info("Incoming connection "+conn.toString());
    ConnectionHandler t = new ConnectionHandler(conn, MasterContainer.this, fwSupplier.get());
    slaves.add(t);
    if (inited) t.start();
  }

  /////////////// Private stuff

  private void openTcpServer(int port) {
    tcpListener = new TcpServer(port, this);
    log.info("Listening on port "+ tcpListener.getPort());
  }

  /**
   * Utility method to run a function on all connection handlers, with a timeout for each.
   *
   * @param handlers collection of connection handlers.
   * @param fn function to run on each handler.
   * @param timeoutMs timeout in milliseconds for each handler.
   */
  private void runAll(Collection<ConnectionHandler> handlers, java.util.function.Consumer<ConnectionHandler> fn, long timeoutMs) {
    List<Callable<Void>> tasks = new ArrayList<>(handlers.size());
    for (ConnectionHandler handler: handlers) {
      tasks.add(() -> {
        fn.accept(handler);
        return null;
      });
    }
    try {
      List<Future<Void>> futures = executor.invokeAll(tasks, timeoutMs, TimeUnit.MILLISECONDS);
      for (Future<Void> future: futures) {
        if (future.isCancelled()) continue;
        try {
          future.get();
        } catch (CancellationException | ExecutionException ex) {
          // ignore failures to preserve existing best-effort behavior
        }
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }


  /**
   * Utility method to apply a function to a collection of connection handlers, and return the first result that matches a given predicate.
   *
   * @param <T> type of the result.
   * @param handlers collection of connection handlers.
   * @param fn function to apply to each handler, which returns a future of the result.
   * @param predicate predicate to test the results.
   * @return an optional containing the first matching result, or empty if none match or all operations fail/timeout.
   */
  private <T> Optional<T> firstMatch(Collection<ConnectionHandler> handlers,
      java.util.function.Function<ConnectionHandler, CompletableFuture<T>> fn,
      Predicate<T> predicate) {
    if (handlers.isEmpty()) return Optional.empty();
    List<CompletableFuture<T>> futures = new ArrayList<>(handlers.size());
    CompletableFuture<T> result = new CompletableFuture<>();
    AtomicInteger remaining = new AtomicInteger(handlers.size());
    for (ConnectionHandler handler: handlers) {
      CompletableFuture<T> future = fn.apply(handler).exceptionally(ex -> null);
      futures.add(future);
      future.thenAccept(rsp -> {
        if (predicate.test(rsp)) result.complete(rsp);
      });
      future.whenComplete((rsp, ex) -> {
        if (remaining.decrementAndGet() == 0) result.complete(null);
      });
    }
    T match = result.join();
    return Optional.ofNullable(match);
  }

  /**
   * Utility method to apply a function to a collection of connection handlers, which returns arrays of results, and concatenate those arrays into a single array.
   *
   * @param <T> type of the result.
   * @param handlers collection of connection handlers.
   * @param fn function to apply to each handler, which returns a future of an array of results.
   * @param factory function to create an array of the appropriate type and size for the concatenated results.
   * @return an array containing the concatenated results from all handlers, with null entries for handlers that fail or time out.
   */
  private <T> T[] concat(Collection<ConnectionHandler> handlers,
      java.util.function.Function<ConnectionHandler, CompletableFuture<T[]>> fn,
      IntFunction<T[]> factory) {
    List<CompletableFuture<T[]>> futures = new ArrayList<>(handlers.size());
    for (ConnectionHandler handler: handlers) {
      futures.add(fn.apply(handler).exceptionally(ex -> null));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
    int total = 0;
    List<T[]> parts = new ArrayList<>(futures.size());
    for (CompletableFuture<T[]> future: futures) {
      T[] arr = future.getNow(null);
      parts.add(arr);
      if (arr != null) total += arr.length;
    }
    T[] result = factory.apply(total);
    int pos = 0;
    for (T[] arr: parts) {
      if (arr == null) continue;
      System.arraycopy(arr, 0, result, pos, arr.length);
      pos += arr.length;
    }
    return result;
  }

}
