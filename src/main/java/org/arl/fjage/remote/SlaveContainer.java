/******************************************************************************

Copyright (c) 2015-2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.util.*;
import java.io.IOException;
import org.arl.fjage.*;
import org.arl.fjage.connectors.*;
import org.arl.fjage.auth.AuthFailureException;

/**
 * Slave container attached to a master container. Agents in linked
 * master and slave containers function as if they were in the same container,
 * i.e., are able to communicate with each other through messaging, topics and
 * directory services.
 *
 * @author Mandar Chitre
 */
public class SlaveContainer extends RemoteContainer {

  ////////////// Private attributes

  private static final long TIMEOUT = 2000;

  private ConnectionHandler master;
  private String hostname, settings;
  private int port, baud;
  private boolean quit = false;
  private String watchListCache = null;

  ////////////// Constructors

  /**
   * Creates a slave container connecting over a TCP socket.
   *
   * @param platform platform on which the container runs.
   * @param hostname hostname of the master container.
   * @param port port on which the master container's TCP server runs.
   */
  public SlaveContainer(Platform platform, String hostname, int port) throws IOException {
    super(platform);
    this.hostname = hostname;
    this.port = port;
    this.baud = -1;
    connectToMaster();
  }

  /**
   * Creates a named slave container connecting over a TCP socket.
   *
   * @param platform platform on which the container runs.
   * @param name name of the container.
   * @param hostname hostname of the master container.
   * @param port port on which the master container's TCP server runs.
   */
  public SlaveContainer(Platform platform, String name, String hostname, int port) throws IOException {
    super(platform, name);
    this.hostname = hostname;
    this.port = port;
    this.baud = -1;
    connectToMaster();
  }

  /**
   * Creates a slave container connecting over a RS232 port.
   *
   * @param platform platform on which the container runs.
   * @param devname device name of the RS232 port.
   * @param baud baud rate for the RS232 port.
   * @param settings RS232 settings (null for defaults, or "N81" for no parity, 8 bits, 1 stop bit).
   */
  public SlaveContainer(Platform platform, String devname, int baud, String settings) throws IOException {
    super(platform);
    this.hostname = devname;
    this.port = -1;
    this.baud = baud;
    this.settings = settings;
    connectToMaster();
  }

  /**
   * Creates a named slave container connecting over a RS232 port.
   *
   * The RS232 port is assumed to be working with 8 data bits, 1 stop bit and no parity.
   *
   * @param platform platform on which the container runs.
   * @param name name of the container.
   * @param devname device name of the RS232 port.
   * @param baud baud rate for the RS232 port.
   * @param settings RS232 settings (null for defaults, or "N81" for no parity, 8 bits, 1 stop bit).
   */
  public SlaveContainer(Platform platform, String name, String devname, int baud, String settings) throws IOException {
    super(platform, name);
    this.hostname = devname;
    this.port = -1;
    this.baud = baud;
    this.settings = settings;
    connectToMaster();
  }

  /////////////// slave-specific methods

  /**
   * Authenticate to master container.
   *
   * @param creds credentials to authenticate with.
   * @return true if authenticated, false otherwise.
   */
  public boolean authenticate(String creds) {
    if (master == null) return false;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.AUTH;
    rq.creds = creds;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    JsonMessage rsp = master.printlnAndGetResponse(json, rq.id, TIMEOUT);
    return rsp != null && rsp.auth != null && rsp.auth;
  }

  /**
   * Checks for authentication failure on send.
   * <p>
   * INTERNAL USE ONLY.
   */
  public void checkAuthFailure(String id) {
    if (master.checkAuthFailure(id)) throw new AuthFailureException();
  }

  /////////////// Container interface methods to override

  @Override
  protected boolean isDuplicate(AgentID aid) {
    if (super.isDuplicate(aid)) return true;
    if (master == null) return false;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.CONTAINS_AGENT;
    rq.agentID = aid;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    JsonMessage rsp = master.printlnAndGetResponse(json, rq.id, TIMEOUT);
    return rsp != null && rsp.answer != null && rsp.answer;
  }

  @Override
  public boolean send(Message m) {
    return send(m, true);
  }

  @Override
  public boolean send(Message m, boolean relay) {
    if (!running) return false;
    if (master == null) return false;
    AgentID aid = m.getRecipient();
    if (aid == null) return false;
    if (aid.isTopic()) {
      if (!relay) return super.send(m, false);
      JsonMessage rq = new JsonMessage();
      rq.action = Action.SEND;
      rq.id = m.getMessageID();
      rq.message = m;
      rq.relay = true;
      String json = rq.toJson();
      master.println(json);
      return true;
    } else {
      if (super.send(m, false)) return true;
      if (!relay) return false;
      JsonMessage rq = new JsonMessage();
      rq.action = Action.SEND;
      rq.id = m.getMessageID();
      rq.message = m;
      rq.relay = true;
      String json = rq.toJson();
      master.println(json);
      return true;
    }
  }

  @Override
  public AgentID[] getAgents() {
    if (master == null) return null;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.AGENTS;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    JsonMessage rsp = master.printlnAndGetResponse(json, rq.id, TIMEOUT);
    if (rsp == null) return null;
    if (rsp.auth != null && rsp.auth == false) throw new AuthFailureException();
    return rsp.agentIDs;
  }

  @Override
  public String[] getServices() {
    if (master == null) return null;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.SERVICES;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    JsonMessage rsp = master.printlnAndGetResponse(json, rq.id, TIMEOUT);
    if (rsp == null) return null;
    if (rsp.auth != null && rsp.auth == false) throw new AuthFailureException();
    return rsp.services;
  }

  @Override
  public AgentID agentForService(String service) {
    if (master == null) return null;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.AGENT_FOR_SERVICE;
    rq.service = service;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    JsonMessage rsp = master.printlnAndGetResponse(json, rq.id, TIMEOUT);
    if (rsp == null) return null;
    if (rsp.auth != null && rsp.auth == false) throw new AuthFailureException();
    return rsp.agentID;
  }

  @Override
  public AgentID[] agentsForService(String service) {
    if (master == null) return null;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.AGENTS_FOR_SERVICE;
    rq.service = service;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    JsonMessage rsp = master.printlnAndGetResponse(json, rq.id, TIMEOUT);
    if (rsp == null) return null;
    if (rsp.auth != null && rsp.auth == false) throw new AuthFailureException();
    return rsp.agentIDs;
  }

  @Override
  AgentID[] getLocalAgents() {
    return super.getAgents();
  }
  @Override
  String[] getLocalServices() {
    return super.getServices();
  }

  @Override
  AgentID localAgentForService(String service) {
    return super.agentForService(service);
  }

  @Override
  AgentID[] localAgentsForService(String service) {
    return super.agentsForService(service);
  }

  @Override
  public void shutdown() {
    quit = true;
    if (master != null) master.close();
    super.shutdown();
  }

  @Override
  public String getState() {
    if (!running) return "Not running";
    if (master == null) return "Running, connecting to "+hostname+(port>=0?":"+port:"@"+baud)+"...";
    return "Running, connected to "+hostname+(port>=0?":"+port:"@"+baud);
  }

  @Override
  public String toString() {
    String s = getClass().getName()+"@"+name;
    s += "/slave/"+platform;
    return s;
  }

  @Override
  public void connectionClosed(ConnectionHandler handler) {
    // do nothing
  }

  /////////////// Observers

  public AgentID add(String name, Agent agent) {
    AgentID aid = super.add(name, agent);
    if (aid != null) updateWatchList();
    return aid;
  }

  public boolean kill(AgentID aid) {
    boolean rv = super.kill(aid);
    if (rv) updateWatchList();
    return rv;
  }

  public boolean subscribe(AgentID aid, AgentID topic) {
    boolean rv = super.subscribe(aid, topic);
    if (rv) updateWatchList();
    return rv;
  }

  public boolean unsubscribe(AgentID aid, AgentID topic) {
    boolean rv = super.unsubscribe(aid, topic);
    if (rv) updateWatchList();
    return rv;
  }

  public void unsubscribe(AgentID aid) {
    super.unsubscribe(aid);
    updateWatchList();
  }

  /////////////// Private stuff

  private void tryConnecting() throws IOException {
    Connector conn;
    if (port >= 0) conn = new TcpConnector(hostname, port);
    else conn = new SerialPortConnector(hostname, baud, settings);
    master = new ConnectionHandler(conn, SlaveContainer.this);
  }

  private void connectToMaster() throws IOException {
    tryConnecting();
    new Thread(getClass().getSimpleName()+">"+hostname) {
      @Override
      public void run() {
        try {
          while (!quit) {
            try {
              tryConnecting();
              log.info("Connected to "+hostname+":"+(port>=0?":"+port:"@"+baud));
              master.start();
              master.join();
              log.info("Connection to "+hostname+(port>=0?":"+port:"@"+baud)+" lost");
              synchronized (SlaveContainer.this) {
                master = null;
              }
            } catch (IOException ex) {
              // do nothing
            }
            if (!quit) Thread.sleep(1000);
          }
        } catch (InterruptedException ex) {
          log.warning("Connection manager interrupted!");
        }
      }
    }.start();
  }

  private synchronized void updateWatchList() {
    if (master == null) return;
    List<AgentID> watchList = new ArrayList<>();
    for (AgentID aid: getLocalAgents())
      watchList.add(aid);
    for (AgentID aid: topics.keySet())
      if (topics.get(aid).size() > 0)
        watchList.add(aid);
    JsonMessage rq = new JsonMessage();
    rq.action = Action.WANTS_MESSAGES_FOR;
    rq.agentIDs = new AgentID[watchList.size()];
    rq.agentIDs = watchList.toArray(rq.agentIDs);
    String json = rq.toJson();
    if (watchListCache == null || !watchListCache.equals(json)) {
      master.println(json);
      watchListCache = json;
    }
  }

}
