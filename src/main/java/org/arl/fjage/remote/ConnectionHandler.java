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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.arl.fjage.AgentID;
import org.arl.fjage.auth.*;
import org.arl.fjage.connectors.*;

/**
 * Handles a JSON/TCP connection with remote container.
 */
public class ConnectionHandler extends Thread {

  private final String ALIVE = "{\"alive\": true}";
  private final String SIGN_OFF = "{\"alive\": false}";
  private final int TIMEOUT = 60000;
  private final int FAILED_SIZE = 256;

  private volatile Connector conn;
  private volatile DataOutputStream out;
  private final ConcurrentMap<String,PendingRequest> pending = new ConcurrentHashMap<>();
  private final Deque<String> failed = new ArrayDeque<>(FAILED_SIZE);
  private final Logger log = Logger.getLogger(getClass().getName());
  private final RemoteContainer container;
  private volatile boolean alive;
  private final boolean closeOnDead;
  private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService directoryExecutor = Executors.newCachedThreadPool();
  private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService aliveCheckExecutor = Executors.newSingleThreadScheduledExecutor();
  private final Set<AgentID> watchList = new HashSet<>();
  private String clientName = "-";
  private final Firewall fw;
  private volatile long lastRxTime;

  public ConnectionHandler(Connector conn, RemoteContainer container, Firewall fw) {
    this.conn = conn;
    this.container = container;
    this.fw = fw;
    setName(conn.toString());
    alive = false;
    closeOnDead = ((conn instanceof TcpConnector) || (conn instanceof WebSocketConnector)) && (container instanceof MasterContainer);
  }

  public ConnectionHandler(Connector conn, RemoteContainer container) {
    this(conn, container, new AllowAll());
  }

  /**
   * Checks if the connection is alive.
   *
   * @return true if the connection is alive, false otherwise.
   */
  public boolean isConnectionAlive() {
    if (conn instanceof WebSocketHubConnector) return true;
    return alive;
  }

  @Override
  public void run() {
    Connector c = conn;
    if (c == null) return;
    BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
    out = new DataOutputStream(c.getOutputStream());
    send(ALIVE);
    if (closeOnDead) {
      lastRxTime = System.currentTimeMillis();
      try {
        aliveCheckExecutor.scheduleAtFixedRate(() -> {
          if (System.currentTimeMillis() - lastRxTime > TIMEOUT) {
            alive = false;
            log.fine("Connection dead");
            close();
          } else send(ALIVE);
        }, TIMEOUT/4, TIMEOUT/4, TimeUnit.MILLISECONDS);
      } catch (RejectedExecutionException ex) {
        // Connection is closing.
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
      lastRxTime = System.currentTimeMillis();
      if (s.equals(SIGN_OFF)) {
        alive = false;
        log.fine("Peer signed off");
        continue;
      }
      if (!alive) {
        alive = true;
        log.fine("Connection alive");
      }
      if (s.equals(ALIVE)) {
        if (container instanceof SlaveContainer) send(ALIVE);
        continue;
      }
      // handle JSON messages
      if (s.length() < 2) continue;
      try {
        JsonMessage rq = JsonMessage.fromJson(s);
        if (rq.action == null) {
          if (rq.id != null) {
            // response to some request
            PendingRequest request = pending.get(rq.id);
            if (request != null) {
              synchronized(request) {
                request.response = rq;
                request.notifyAll();
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
          else if (fw.permit(rq)) {
            if (isDirectoryAction(rq.action)) directoryExecutor.execute(new RemoteTask(rq));
            else taskExecutor.execute(new RemoteTask(rq));
          }
          else respondAuth(rq, false);
        }
      } catch(Exception ex) {
        log.log(Level.WARNING, "Failed to process message: "+s, ex);
      }
    }
    fw.signoff();
    close();
  }

  @Override
  public String toString() {
    if (conn == null) return super.toString() + " <" + clientName + ">";
    return conn + " <" + clientName + ">";
  }

  private void respondAuth(JsonMessage rq, boolean auth) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.auth = auth;
    send(rsp.toJson());
  }

  private void respond(JsonMessage rq, boolean answer) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.answer = answer;
    send(rsp.toJson());
  }

  private void respond(JsonMessage rq, AgentID aid) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.agentID = aid;
    send(rsp.toJson());
  }

  private void respond(JsonMessage rq, AgentID[] aid) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.agentIDs = aid;
    rsp.agentTypes = aid == null ? new String[0] : new String[aid.length];
    for (int i = 0; i < rsp.agentTypes.length; i++)
      rsp.agentTypes[i] = aid[i].getType();
    send(rsp.toJson());
  }

  private void respond(JsonMessage rq, String[] svc) {
    JsonMessage rsp = new JsonMessage();
    rsp.inResponseTo = rq.action;
    rsp.id = rq.id;
    rsp.services = svc;
    send(rsp.toJson());
  }

  synchronized void send(String s) {
    if (out == null) return;
    try {
      out.write((s+"\n").getBytes(StandardCharsets.UTF_8));
      log.fine(this.getName() +" >>> "+s);
      conn.waitOutputCompletion(1000);
    } catch(IOException ex) {
      if (!s.equals(SIGN_OFF)) {
        log.log(Level.WARNING, "Failed to send message: "+s, ex);
        close();
      }
    }
  }

  void sendAsync(String s) {
    if (conn == null) return;
    if (!alive && container instanceof MasterContainer) return;
    try {
      sendExecutor.execute(() -> {
        if (conn == null) return;
        if (!alive && container instanceof MasterContainer) return;
        send(s);
      });
    } catch (RejectedExecutionException ex) {
      // Connection is closing.
    }
  }

  JsonMessage request(JsonMessage msg, long timeout) {
    if (conn == null) return null;
    if (!alive && container instanceof MasterContainer) return null;
    PendingRequest request = new PendingRequest();
    pending.put(msg.id, request);
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
    send(msg.toJson());
    synchronized(request) {
      if (conn == null) {
        pending.remove(msg.id, request);
        return null;
      }
      long remaining = deadline - System.nanoTime();
      while (remaining > 0 && request.response == null && !request.closed) {
        try {
          TimeUnit.NANOSECONDS.timedWait(request, remaining);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        }
        remaining = deadline - System.nanoTime();
      }
    }
    pending.remove(msg.id, request);
    if (request.response != null) return request.response;
    if (!request.closed && deadline - System.nanoTime() <= 0) {
      if (closeOnDead) {
        // probe an unresponsive peer; the watchdog closes it if it stays silent
        send(ALIVE);
      } else if (alive && container instanceof MasterContainer && !(conn instanceof WebSocketHubConnector)) {
        // no watchdog on this point-to-point connection (e.g. serial port), so fail fast on
        // timeout to avoid blocking subsequent queries when the peer has silently vanished.
        alive = false;
        log.fine("Connection dead");
      }
    }
    return null;
  }

  synchronized void close() {
    if (conn == null) return;
    if (container instanceof SlaveContainer) send(SIGN_OFF);
    conn.close();
    conn = null;
    out = null;
    aliveCheckExecutor.shutdownNow();
    sendExecutor.shutdownNow();
    taskExecutor.shutdownNow();
    for (PendingRequest request: pending.values()) {
      synchronized(request) {
        request.closed = true;
        request.notifyAll();
      }
    }
    pending.clear();
    directoryExecutor.shutdownNow();
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

  private boolean isDirectoryAction(Action action) {
    return action == Action.AGENTS || action == Action.CONTAINS_AGENT || action == Action.SERVICES ||
      action == Action.AGENT_FOR_SERVICE || action == Action.AGENTS_FOR_SERVICE;
  }

  private static class PendingRequest {

    JsonMessage response;
    boolean closed;

  }

  //////// Private inner class representing task to run

  private class RemoteTask implements Runnable {

    private final JsonMessage rq;

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
            Collections.addAll(watchList, rq.agentIDs);
          }
          break;
        default:
          log.fine("Unknown action: "+rq.action);
      }
    }

  } // inner class

}
