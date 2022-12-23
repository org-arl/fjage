/******************************************************************************

Copyright (c) 2015-2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.arl.fjage.AgentID;
import org.arl.fjage.auth.*;
import org.arl.fjage.connectors.*;

/**
 * Handles a JSON/TCP connection with remote container.
 */
class ConnectionHandler extends Thread {

  private final String ALIVE = "{\"alive\": true}";
  private final String SIGN_OFF = "{\"alive\": false}";
  private final int TIMEOUT = 60000;
  private final int FAILED_SIZE = 256;

  private Connector conn;
  private DataOutputStream out;
  private Map<String,Object> pending = Collections.synchronizedMap(new HashMap<String,Object>());
  private Deque<String> failed = new ArrayDeque<String>(FAILED_SIZE);
  private Logger log = Logger.getLogger(getClass().getName());
  private RemoteContainer container;
  private boolean alive, keepAlive, closeOnDead;
  private ExecutorService pool = Executors.newSingleThreadExecutor();
  private Set<AgentID> watchList = new HashSet<>();
  private String clientName = "-";
  private Firewall fw;

  public ConnectionHandler(Connector conn, RemoteContainer container) {
    this.conn = conn;
    this.container = container;
    this.fw = new AllowAll();
    setName(conn.toString());
    alive = false;
    keepAlive = true;
    closeOnDead = (conn instanceof TcpConnector) && (container instanceof MasterContainer);
  }

  public ConnectionHandler(Connector conn, RemoteContainer container, Firewall fw) {
    this.conn = conn;
    this.container = container;
    this.fw = fw;
    setName(conn.toString());
    alive = false;
    keepAlive = true;
    closeOnDead = (conn instanceof TcpConnector) && (container instanceof MasterContainer);
  }

  /**
   * Checks if the connection is alive.
   *
   * @return true if the connection is alive, false otherwise.
   */
  public boolean isConnectionAlive() {
    if (conn instanceof WebSocketConnector) return true;
    return alive;
  }

  @Override
  public void run() {
    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    out = new DataOutputStream(conn.getOutputStream());
    if (keepAlive) {
      if (closeOnDead) {
        (new Thread(getName()+":init") {
          @Override
          public void run() {
            println(ALIVE);
            try {
              Thread.sleep(TIMEOUT);
            } catch (InterruptedException ex) {
              // do nothing
            }
            if (!alive) {
              log.fine("Connection dead");
              close();
            }
          }
        }).start();
      } else {
        println(ALIVE);
      }
    }
    while (conn != null) {
      String s = null;
      try {
        s = in.readLine();
      } catch(IOException ex) {
        // do nothing
      }
      if (s == null) break;
      log.fine(this.getName() +" <<< "+s);
      if (keepAlive) {
        // additional alive/sign-off logic needed on serial ports to avoid waiting for slaves when none present
        if (!alive) {
          alive = true;
          log.fine("Connection alive");
        } else if (s.equals(SIGN_OFF)) {
          alive = false;
          log.fine("Peer signed off");
          continue;
        }
        if (s.equals(ALIVE)) {
          if (container instanceof SlaveContainer) println(ALIVE);
          continue;
        }
      }
      // handle JSON messages
      if (s.length() < 2) continue;
      try {
        JsonMessage rq = JsonMessage.fromJson(s);
        if (rq.action == null) {
          if (rq.id != null) {
            // response to some request
            Object obj = pending.get(rq.id);
            if (obj != null) {
              pending.put(rq.id, rq);
              synchronized(obj) {
                obj.notify();
              }
            } else if (rq.auth != null) {
              synchronized(failed) {
                while (failed.size() >= FAILED_SIZE)
                  failed.poll();
                failed.offer(rq.id);
              }
            }
          }
        } else {
          // new request
          if (rq.action == Action.AUTH) {
            if (rq.name != null) clientName = rq.name;
            if (rq.creds != null) {
              boolean b = fw.authenticate(rq.creds);
              respondAuth(rq, b);
            }
          }
          else if (fw.permit(rq)) pool.execute(new RemoteTask(rq));
          else respondAuth(rq, false);
        }
      } catch(Exception ex) {
        log.warning("Bad JSON request: "+ex.toString() + " in " + s);
      }
    }
    fw.signoff();
    close();
    pool.shutdown();
  }

  @Override
  public String toString() {
    if (conn == null) return super.toString() + " <" + clientName + ">";
    return conn.toString() + " <" + clientName + ">";
  }

  private void respondAuth(JsonMessage rq, boolean auth) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.auth = auth;
    println(rsp.toJson());
  }

  private void respond(JsonMessage rq, boolean answer) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.answer = answer;
    println(rsp.toJson());
  }

  private void respond(JsonMessage rq, AgentID aid) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.agentID = aid;
    println(rsp.toJson());
  }

  private void respond(JsonMessage rq, AgentID[] aid) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.agentIDs = aid;
    rsp.agentTypes = aid == null ? new String[0] : new String[aid.length];
    for (int i = 0; i < rsp.agentTypes.length; i++)
      rsp.agentTypes[i] = aid[i].getType();
    println(rsp.toJson());
  }

  private void respond(JsonMessage rq, String[] svc) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.services = svc;
    println(rsp.toJson());
  }

  synchronized void println(String s) {
    if (out == null) return;
    try {
      out.write((s+"\n").getBytes(StandardCharsets.UTF_8));
      log.fine(this.getName() +" >>> "+s);
      conn.waitOutputCompletion(1000);
    } catch(IOException ex) {
      if (!s.equals(SIGN_OFF)) {
        log.warning("Write failed: "+ex.toString());
        close();
      }
    }
  }

  void printlnQueued(String s) {
    if (pool != null) pool.execute(() -> println(s));
  }

  JsonMessage printlnAndGetResponse(String s, String id, long timeout) {
    if (conn == null) return null;
    if (keepAlive && !alive && container instanceof MasterContainer) return null;
    pending.put(id, id);
    try {
      synchronized(id) {
        println(s);
        id.wait(timeout);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    Object rv = pending.get(id);
    pending.remove(id);
    if (rv instanceof JsonMessage) return (JsonMessage)rv;
    if (keepAlive && alive) {
      alive = false;
      log.fine("Connection dead");
      if (closeOnDead) close();
    }
    return null;
  }

  synchronized void close() {
    if (conn == null) return;
    if (keepAlive && container instanceof SlaveContainer) println(SIGN_OFF);
    conn.close();
    conn = null;
    out = null;
    container.connectionClosed(this);
  }

  boolean isClosed() {
    return conn == null;
  }

  boolean wantsMessagesFor(AgentID aid) {
    if (!fw.permit(aid)) return false;
    synchronized(watchList) {
      if (watchList.isEmpty()) return true;
      return watchList.contains(aid);
    }
  }

  boolean checkAuthFailure(String id) {
    synchronized(failed) {
      return failed.contains(id);
    }
  }

  //////// Private inner class representing task to run

  private class RemoteTask implements Runnable {

    private JsonMessage rq;

    RemoteTask(JsonMessage rq) {
      this.rq = rq;
    }

    @Override
    public void run() {
      switch (rq.action) {
        case AGENTS:
          respond(rq, container.getLocalAgents());
          break;
        case CONTAINS_AGENT:
          respond(rq, rq.agentID != null && container.containsAgent(rq.agentID));
          break;
        case SERVICES:
          respond(rq, container.getLocalServices());
          break;
        case AGENT_FOR_SERVICE:
          respond(rq, rq.service != null ? container.localAgentForService(rq.service) : null);
          break;
        case AGENTS_FOR_SERVICE:
          respond(rq, rq.service != null ? container.localAgentsForService(rq.service) : null);
          break;
        case SEND:
          if (rq.relay != null) container.send(rq.message, rq.relay);
          else container.send(rq.message);
          break;
        case SHUTDOWN:
          container.shutdown();
          break;
        case WANTS_MESSAGES_FOR:
          synchronized(watchList) {
            watchList.clear();
            for (AgentID aid: rq.agentIDs)
              watchList.add(aid);
          }
          break;
      }
    }

  } // inner class

}
