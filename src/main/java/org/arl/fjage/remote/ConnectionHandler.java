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
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.arl.fjage.*;

/**
 * Handles a JSON/TCP connection with remote container.
 */
class ConnectionHandler extends Thread {

  private Socket sock;
  private DataOutputStream out;
  private Map<String,Object> pending = Collections.synchronizedMap(new HashMap<String,Object>());
  private Logger log = Logger.getLogger(getClass().getName());
  private RemoteContainer container;
  private String name;

  public ConnectionHandler(Socket sock, RemoteContainer container) {
    this.sock = sock;
    this.container = container;
    name = sock.getRemoteSocketAddress().toString();
    setName(name);
  }

  @Override
  public void run() {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
      out = new DataOutputStream(sock.getOutputStream());
      while (true) {
        String s = in.readLine();
        if (s == null) break;
        log.fine(name+" <<< "+s);
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
      // do nothing
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
    } catch(IOException ex) {
      log.warning("Write failed: "+ex.toString());
      close();
    }
  }

  JsonMessage getResponse(String id, long timeout) {
    if (sock == null) return null;
    pending.put(id, id);
    try {
      synchronized(id) {
        id.wait(timeout);
      }
    } catch (InterruptedException ex) {
      // do nothing
    }
    Object rv = pending.get(id);
    pending.remove(id);
    if (rv instanceof JsonMessage) return (JsonMessage)rv;
    return null;
  }

  void close() {
    if (sock == null) return;
    try {
      sock.close();
      sock = null;
      out = null;
      container.connectionClosed(this);
    } catch (IOException ex) {
      // do nothing
    }
  }

  boolean isClosed() {
    return sock == null;
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
