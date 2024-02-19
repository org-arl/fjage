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
import java.util.function.Supplier;

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

  private TcpServer listener = null;
  private List<ConnectionHandler> slaves = new ArrayList<ConnectionHandler>();
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
    if (listener == null) return -1;
    return listener.getPort();
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
    synchronized(slaves) {
      slaves.add(t);
    }
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
    synchronized(slaves) {
      slaves.add(t);
    }
    t.start();
  }

  /**
   * Gets a list of connector URLs that slaves can use to access the master container.
   */
  public String[] getConnectors() {
    synchronized(slaves) {
      String[] url = new String[1+slaves.size()];
      url[0] = listener.toString();
      for (int i = 0; i < slaves.size(); i++)
        url[i+1] = slaves.get(i).toString();
      return url;
    }
  }

  /////////////// Container interface methods to override

  @Override
  protected boolean isDuplicate(AgentID aid) {
    if (super.isDuplicate(aid)) return true;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.CONTAINS_AGENT;
    rq.agentID = aid;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    if (needsCleanup) cleanupSlaves();
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves) {
        JsonMessage rsp = slave.printlnAndGetResponse(json, rq.id, TIMEOUT);
        if (rsp != null && rsp.answer) return true;
      }
    }
    return false;
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
    JsonMessage rq = new JsonMessage();
    rq.action = Action.SEND;
    rq.message = m;
    rq.relay = false;
    String json = rq.toJson();
    if (needsCleanup) cleanupSlaves();
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves)
        if (slave.wantsMessagesFor(aid)) slave.printlnQueued(json);
    }
    return true;
  }

  @Override
  public AgentID[] getAgents() {
    AgentID[] aids = super.getAgents();
    List<AgentID> rv = new ArrayList<AgentID>(Arrays.asList(aids));
    JsonMessage rq = new JsonMessage();
    rq.action = Action.AGENTS;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    if (needsCleanup) cleanupSlaves();
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves) {
        JsonMessage rsp = slave.printlnAndGetResponse(json, rq.id, TIMEOUT);
        if (rsp != null && rsp.agentIDs != null) {
          if (rsp.agentTypes != null && rsp.agentTypes.length == rsp.agentIDs.length) {
            for (int i = 0; i < rsp.agentIDs.length; i++)
              rsp.agentIDs[i].setType(rsp.agentTypes[i]);
          }
          rv.addAll(Arrays.asList(rsp.agentIDs));
        }
      }
    }
    return rv.toArray(new AgentID[0]);
  }

  @Override
  public String[] getServices() {
    String[] svc = super.getServices();
    Set<String> rv = new HashSet<String>(Arrays.asList(svc));
    JsonMessage rq = new JsonMessage();
    rq.action = Action.SERVICES;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    if (needsCleanup) cleanupSlaves();
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves) {
        JsonMessage rsp = slave.printlnAndGetResponse(json, rq.id, TIMEOUT);
        if (rsp != null && rsp.services != null) {
          rv.addAll(Arrays.asList(rsp.services));
        }
      }
    }
    return rv.toArray(new String[0]);
  }

  @Override
  public AgentID agentForService(String service) {
    AgentID aid = super.agentForService(service);
    if (aid != null) return aid;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.AGENT_FOR_SERVICE;
    rq.service = service;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    if (needsCleanup) cleanupSlaves();
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves) {
        JsonMessage rsp = slave.printlnAndGetResponse(json, rq.id, TIMEOUT);
        if (rsp != null && rsp.agentID != null && rsp.agentID.getName().length() > 0) return rsp.agentID;
      }
    }
    return null;
  }

  @Override
  public AgentID[] agentsForService(String service) {
    List<AgentID> rv = new ArrayList<AgentID>();
    AgentID[] aids = super.agentsForService(service);
    if (aids != null)
      rv.addAll(Arrays.asList(aids));
    JsonMessage rq = new JsonMessage();
    rq.action = Action.AGENTS_FOR_SERVICE;
    rq.service = service;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    if (needsCleanup) cleanupSlaves();
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves) {
        JsonMessage rsp = slave.printlnAndGetResponse(json, rq.id, TIMEOUT);
        if (rsp != null && rsp.agentIDs != null) {
          rv.addAll(Arrays.asList(rsp.agentIDs));
        }
      }
    }
    return rv.toArray(new AgentID[0]);
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
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves) {
        if (!slave.isAlive()) slave.start();
      }
    }
    if (!slaves.isEmpty()) {
      log.fine("Waiting for slaves...");
      boolean allAlive = false;
      for (int i = 0; !allAlive && i < TIMEOUT/100; i++) {
        try {
          Thread.sleep(100);
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        allAlive = true;
        synchronized(slaves) {
          for (ConnectionHandler slave: slaves) {
            if (!slave.isConnectionAlive()) allAlive = false;
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
    JsonMessage rq = new JsonMessage();
    rq.action = Action.SHUTDOWN;
    String json = rq.toJson();
    synchronized(slaves) {
      for (ConnectionHandler slave: slaves) {
        slave.println(json);
        slave.close();
      }
      slaves.clear();
      needsCleanup = false;
    }
    if (listener != null) {
      listener.close();
      listener = null;
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

  /////////////// ConnectionListener interface method

  @Override
  public void connected(Connector conn) {
    log.info("Incoming connection "+conn.toString());
    ConnectionHandler t = new ConnectionHandler(conn, MasterContainer.this, fwSupplier.get());
    synchronized(slaves) {
      slaves.add(t);
    }
    if (inited) t.start();
  }

  /////////////// Private stuff

  private void openTcpServer(int port) {
    listener = new TcpServer(port, this);
    log.info("Listening on port "+listener.getPort());
  }

  private void cleanupSlaves() {
    synchronized(slaves) {
      slaves.removeIf(ConnectionHandler::isClosed);
    }
    needsCleanup = false;
  }

}
