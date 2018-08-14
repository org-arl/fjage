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
import com.fazecast.jSerialComm.SerialPort;

/**
 * Handles a JSON/TCP connection with remote container.
 */
class ConnectionHandler extends Thread {

  private final String ALIVE = "{}";
  private final String SIGN_OFF = "//";

  private Socket sock;
  private SerialPort com;
  private DataOutputStream out;
  private Map<String,Object> pending = Collections.synchronizedMap(new HashMap<String,Object>());
  private Logger log = Logger.getLogger(getClass().getName());
  private RemoteContainer container;
  private String name;
  private boolean alive;

  public ConnectionHandler(Socket sock, RemoteContainer container) {
    this.sock = sock;
    this.com = null;
    this.container = container;
    name = sock.getRemoteSocketAddress().toString();
    setName(name);
  }

  public ConnectionHandler(SerialPort com, RemoteContainer container) {
    this.sock = null;
    this.com = com;
    this.container = container;
    name = com.getDescriptivePortName();
    setName(name);
    com.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
    alive = false;
  }

  @Override
  public void run() {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(sock != null ? sock.getInputStream() : com.getInputStream()));
      out = new DataOutputStream(sock != null ? sock.getOutputStream() : com.getOutputStream());
      if (com != null) println(ALIVE);
      while (sock != null || com != null) {
        String s = null;
        try {
          s = in.readLine();
        } catch(IOException ex) {
          // do nothing
        }
        if (s == null) break;
        log.fine(name+" <<< "+s);
        if (com != null) {
          // additional alive/sign-off logic on RS232 ports to avoid waiting for RS232 slaves when none present
          if (!alive) {
            alive = true;
            log.fine("RS232 connection alive");
          } else if (s.equals(SIGN_OFF)) {
            alive = false;
            log.fine("RS232 peer signed off");
            continue;
          }
          if (s.equals(ALIVE)) {
            if (container instanceof SlaveContainer) println(ALIVE);
            continue;
          }
        }
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
    } catch(IOException ex) {
      log.warning(ex.toString());
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
      log.fine(name+" >>> "+s);
      while (com != null && com.bytesAwaitingWrite​() > 0) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ex) {
          // do nothing
        }
      }
    } catch(IOException ex) {
      log.warning("Write failed: "+ex.toString());
      if (sock != null) close();
    }
  }

  JsonMessage printlnAndGetResponse(String s, String id, long timeout) {
    if (sock == null && com == null) return null;
    if (com != null && !alive && container instanceof MasterContainer) return null;
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
    if (com != null && alive) {
      alive = false;
      log.fine("RS232 connection dead");
    }
    return null;
  }

  void close() {
    if (sock == null && com == null) return;
    try {
      if (sock != null) {
        sock.close();
        sock = null;
      }
      if (com != null) {
        if (container instanceof SlaveContainer) println(SIGN_OFF);
        com.closePort();
        com = null;
      }
      out = null;
      container.connectionClosed(this);
    } catch (IOException ex) {
      // do nothing
    }
  }

  boolean isClosed() {
    return sock == null && com == null;
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
