/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.*;
import java.net.*;
import java.util.*;
import org.arl.fjage.*;

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

  private static final long TIMEOUT = 1000;

  private ConnectionHandler master;
  private String hostname;
  private int port;
  private Map<String,Object> pending = Collections.synchronizedMap(new HashMap<String,Object>());
  private boolean quit = false;

  ////////////// Constructors

  /**
   * Creates a slave container.
   * 
   * @param platform platform on which the container runs.
   * @param hostname hostname of the master container.
   * @param port port on which the master container's TCP server runs.
   */
  public SlaveContainer(Platform platform, String hostname, int port) {
    super(platform);
    this.hostname = hostname;
    this.port = port;
    connectToMaster();
  }
  
  /**
   * Creates a named slave container.
   * 
   * @param platform platform on which the container runs.
   * @param name name of the container.
   * @param hostname hostname of the master container.
   * @param port port on which the master container's TCP server runs.
   */
  public SlaveContainer(Platform platform, String name, String hostname, int port) {
    super(platform, name);
    this.hostname = hostname;
    this.port = port;
    connectToMaster();
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
    master.println(json);
    JsonMessage rsp = master.getResponse(rq.id, TIMEOUT);
    if (rsp != null && rsp.answer) return true;
    return false;
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
    master.println(json);
    JsonMessage rsp = master.getResponse(rq.id, TIMEOUT);
    if (rsp == null) return null;
    return rsp.agentIDs;
  }

  @Override
  public String[] getServices() {
    if (master == null) return null;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.SERVICES;
    rq.id = UUID.randomUUID().toString();
    String json = rq.toJson();
    master.println(json);
    JsonMessage rsp = master.getResponse(rq.id, TIMEOUT);
    if (rsp == null) return null;
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
    master.println(json);
    JsonMessage rsp = master.getResponse(rq.id, TIMEOUT);
    if (rsp == null) return null;
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
    master.println(json);
    JsonMessage rsp = master.getResponse(rq.id, TIMEOUT);
    if (rsp == null) return null;
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
    if (master == null) return "Running, connecting to "+hostname+":"+port+"...";
    return "Running, connected to "+hostname+":"+port;
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

  /////////////// Private stuff

  private void connectToMaster() {
    new Thread() {
      @Override
      public void run() {
        try {
          while (!quit) {
            try {
              log.info("Connecting to "+hostname+":"+port);
              Socket conn = new Socket(hostname, port);
              master = new ConnectionHandler(conn, SlaveContainer.this);
              master.start();
              master.join();
              log.info("Connection to "+hostname+" lost");
              master = null;
            } catch (IOException ex) {
              log.warning("Connection failed: "+ex.toString());
            }
            Thread.sleep(1000);
          }
        } catch (InterruptedException ex) {
          log.severe("Connection manager interrupted!");
        }
      }
    }.start();
  }

}
