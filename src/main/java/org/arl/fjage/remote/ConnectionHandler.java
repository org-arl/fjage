/******************************************************************************

Copyright (c) 2015-2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.arl.fjage.*;
import org.arl.fjage.connectors.*;

/**
 * Handles a JSON/TCP connection with remote container.
 */
class ConnectionHandler extends Thread {

  private final String ALIVE = "{'alive': true}";
  private final String SIGN_OFF = "{'alive': false}";

  private Connector conn;
  private DataOutputStream out;
  private Map<String,Object> pending = Collections.synchronizedMap(new HashMap<String,Object>());
  private Logger log = Logger.getLogger(getClass().getName());
  private RemoteContainer container;
  private boolean alive, keepAlive;

  public ConnectionHandler(Connector conn, RemoteContainer container) {
    this.conn = conn;
    this.container = container;
    setName(conn.toString());
    alive = false;
    keepAlive = true; //conn instanceof SerialPortConnector;
  }

  @Override
  public void run() {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    out = new DataOutputStream(conn.getOutputStream());
    if (keepAlive) println(ALIVE);
    while (conn != null) {
      String s = null;
      try {
        s = in.readLine();
      } catch(IOException ex) {
        // do nothing
      }
      if (s == null) break;
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
            }
          }
        } else {
          // new request
          pool.execute(new RemoteTask(rq));
        }
      } catch(Exception ex) {
        log.warning("Bad JSON request: "+ex.toString());
      }
    }
    close();
    pool.shutdown();
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
      out.writeBytes(s+"\n");
      if (conn instanceof SerialPortConnector) {
        while (((SerialPortConnector)conn).getSerialPort().bytesAwaitingWrite​() > 0) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException ex) {
            // do nothing
          }
        }
      }
    } catch(IOException ex) {
      log.warning("Write failed: "+ex.toString());
      close();
    }
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
      // do nothing
    }
    Object rv = pending.get(id);
    pending.remove(id);
    if (rv instanceof JsonMessage) return (JsonMessage)rv;
    if (keepAlive && alive) {
      alive = false;
      log.fine("Connection dead");
    }
    return null;
  }

  void close() {
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
          respond(rq, rq.agentID != null ? container.containsAgent(rq.agentID) : false);
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
      }
    }

  } // inner class

}
