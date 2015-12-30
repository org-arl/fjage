/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.json;

import java.io.*;
import java.net.*;
import java.util.*;
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
  private Container container;

  public ConnectionHandler(Socket sock, Container container) {
    this.sock = sock;
    this.container = container;
    setName(sock.getRemoteSocketAddress().toString());
  }

  @Override
  public void run() {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
      out = new DataOutputStream(sock.getOutputStream());
      while (true) {
        String s = in.readLine();
        if (s == null) break;
        // log.fine("<<< "+s);
        try {
          JsonMessage rq = JsonMessage.fromJson(s);
          if (rq.action == null) {
            if (rq.id != null) {
              // response to some request to slave
              Object obj = pending.get(rq.id);
              if (obj != null) {
                pending.put(rq.id, rq);
                synchronized(obj) {
                  obj.notify();
                }
              }
            }
          } else {
            // request from slave
            switch (rq.action) {
              case CONTAINS_AGENT:
                respond(rq, container.containsAgent(rq.agentID));
                break;
              case REGISTER:
                container.register(rq.agentID, rq.service);
                break;
              case DEREGISTER:
                if  (rq.service != null) container.deregister(rq.agentID, rq.service);
                else container.deregister(rq.agentID);
                break;
              case AGENT_FOR_SERVICE:
                respond(rq, container.agentForService(rq.service));
                break;
              case AGENTS_FOR_SERVICE:
                respond(rq, container.agentsForService(rq.service));
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
        } catch(Exception ex) {
          log.warning("Bad JSON request: "+ex.toString());
        }
      }
    } catch(IOException ex) {
      // do nothing
    }
    close();
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

  public synchronized void println(String s) {
    if (out == null) return;
    try {
      out.writeBytes(s+"\n");
      // log.fine(">>> "+s);
    } catch(IOException ex) {
      log.warning("Write failed: "+ex.toString());
      close();
    }
  }

  public JsonMessage getResponse(String id, long timeout) {
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

  public void close() {
    if (sock == null) return;
    try {
      sock.close();
      sock = null;
      out = null;
      if (container instanceof ConnectionClosedListener) ((ConnectionClosedListener)container).connectionClosed(this);
    } catch (IOException ex) {
      // do nothing
    }
  }

  public boolean isClosed() {
    return sock == null;
  }

}
