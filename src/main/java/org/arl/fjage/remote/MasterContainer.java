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

import java.util.concurrent.CopyOnWriteArrayList;
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
  private boolean needsCleanup = false;
  private Supplier<Firewall> fwSupplier = AllowAll.SUPPLIER;

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
    if (needsCleanup) cleanupSlaves();
    return slaves.parallelStream().anyMatch(slave -> {
      JsonMessage rq = JsonMessage.createActionRequest(Action.CONTAINS_AGENT);
      rq.agentID = aid;
      JsonMessage rsp = slave.request(rq, TIMEOUT);
      return rsp != null && Boolean.TRUE.equals(rsp.answer);
    });
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
    if (needsCleanup) cleanupSlaves();
    slaves.parallelStream().filter(slave -> slave.wantsMessagesFor(aid)).forEach(slave -> slave.sendQueued(json));
    return true;
  }

  @Override
  public AgentID[] getAgents() {
    AgentID[] aids = super.getAgents();
    JsonMessage rq = JsonMessage.createActionRequest(Action.AGENTS);
    if (needsCleanup) cleanupSlaves();
    AgentID[] remoteAids = slaves.parallelStream()
      .map(slave -> {
        return slave.request(rq, TIMEOUT);
      })
      .filter(rsp -> rsp != null && rsp.agentIDs != null)
      .flatMap(rsp -> {
        if (rsp.agentTypes != null && rsp.agentTypes.length == rsp.agentIDs.length) {
          for (int i = 0; i < rsp.agentIDs.length; i++)
            rsp.agentIDs[i].setType(rsp.agentTypes[i]);
        }
        return Arrays.stream(rsp.agentIDs);
      })
      .toArray(AgentID[]::new);
    return Stream.concat(Arrays.stream(aids), Arrays.stream(remoteAids)).toArray(AgentID[]::new);
  }

  @Override
  public String[] getServices() {
    String[] svcs = super.getServices();
    JsonMessage rq = JsonMessage.createActionRequest(Action.SERVICES);
    if (needsCleanup) cleanupSlaves();
    String[] remoteSvcs = slaves.parallelStream()
      .map(slave -> slave.request(rq, TIMEOUT))
      .filter(rsp -> rsp != null && rsp.services != null)
      .flatMap(rsp -> Arrays.stream(rsp.services))
      .toArray(String[]::new);
    return Stream.concat(Arrays.stream(svcs), Arrays.stream(remoteSvcs)).toArray(String[]::new);
  }

  @Override
  public AgentID agentForService(String service) {
    AgentID aid = super.agentForService(service);
    if (aid != null) return aid;
    JsonMessage rq = JsonMessage.createActionRequest(Action.AGENT_FOR_SERVICE);
    if (needsCleanup) cleanupSlaves();
    Optional<AgentID> remoteAid = slaves.parallelStream()
      .map(slave -> slave.request(rq, TIMEOUT))
      .filter(rsp -> rsp != null && rsp.agentID != null && !rsp.agentID.getName().isEmpty())
      .map(rsp -> rsp.agentID)
      .findFirst();
    if (remoteAid.isPresent()) return remoteAid.get();
    return null;
  }

  @Override
  public AgentID[] agentsForService(String service) {
    AgentID[] aids = super.agentsForService(service);
    JsonMessage rq = JsonMessage.createActionRequest(Action.AGENTS_FOR_SERVICE);
    rq.service = service;
    if (needsCleanup) cleanupSlaves();
    AgentID[] remoteAids = slaves.parallelStream()
      .map(slave -> slave.request(rq, TIMEOUT))
      .filter(rsp -> rsp != null && rsp.agentIDs != null)
      .flatMap(rsp -> Arrays.stream(rsp.agentIDs))
      .toArray(AgentID[]::new);
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
    slaves.parallelStream().filter(slave -> !slave.isAlive()).forEach(ConnectionHandler::start);
    if (!slaves.isEmpty()) {
      log.fine("Waiting for slaves...");
      boolean allAlive = false;
      for (int i = 0; !allAlive && i < TIMEOUT/100; i++) {
        try {
          Thread.sleep(100);
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        allAlive = slaves.parallelStream().allMatch(ConnectionHandler::isAlive);
      }
      if (allAlive) log.fine("All slaves are alive");
      else log.warning("Some slaves timed out!");
    }
  }

  @Override
  public void shutdown() {
    if (!running) return;
    String json = JsonMessage.createActionRequest(Action.SHUTDOWN).toJson();
    slaves.parallelStream().forEach(slave -> {
      slave.send(json);
      slave.close();
    });
    slaves.clear();
    needsCleanup = false;
    if (tcpListener != null) {
      tcpListener.close();
      tcpListener = null;
    }
    if (websocketListener != null){
      websocketListener.close();
      websocketListener = null;
    }
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
    needsCleanup = true;
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

  private void cleanupSlaves() {
    slaves.removeIf(ConnectionHandler::isClosed);
    needsCleanup = false;
  }

}
