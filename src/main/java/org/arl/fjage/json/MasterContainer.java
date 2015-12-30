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
import org.arl.fjage.*;

/**
 * Master container supporting multiple remote slave containers. Agents in linked
 * master and slave containers function as if they were in the same container,
 * i.e., are able to communicate with each other through messaging, topics and
 * directory services.
 *
 * @author Mandar Chitre
 */
public class MasterContainer extends Container {

  ////////////// Private attributes

  private ServerSocket listener;
  private List<SlaveProxy> slaves = new ArrayList<SlaveProxy>();

  ////////////// Constructors

  /**
   * Creates a master container, runs its TCP server on an automatically selected port.
   * 
   * @param platform platform on which the container runs.
   */
  public MasterContainer(Platform platform) throws IOException {
    super(platform);
    openSocket(0);
  }

  /**
   * Creates a master container, runs its TCP server on a specified port.
   * 
   * @param platform platform on which the container runs.
   * @param port port on which the container's TCP server runs.
   */
  public MasterContainer(Platform platform, int port) throws IOException {
    super(platform);
    openSocket(port);
  }

  /**
   * Creates a named master container, runs its TCP server on an automatically selected port.
   * 
   * @param platform platform on which the container runs.
   * @param name name of the container.
   */
  public MasterContainer(Platform platform, String name) throws IOException {
    super(platform, name);
    openSocket(0);
  }

  /**
   * Creates a named master container, runs its TCP server on a specified port.
   * 
   * @param platform platform on which the container runs.
   * @param name of the container.
   * @param port port on which the container's TCP server runs.
   */
  public MasterContainer(Platform platform, String name, int port) throws IOException {
    super(platform, name);
    openSocket(port);
  }

  /////////////// Container interface methods to override
  
  @Override
  protected boolean isDuplicate(AgentID aid) {
    if (super.isDuplicate(aid)) return true;
    // TODO check with slaves
    return false;
  }

  @Override
  public boolean send(Message m) {
    return send(m, true);
  }

  @Override
  public boolean send(Message m, boolean relay) {
    if (!running) return false;
    AgentID aid = m.getRecipient();
    if (aid == null) return false;
    if (super.send(m, false) && !aid.isTopic()) return true;
    if (!relay) return false;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.SEND;
    rq.message = m;
    rq.relay = false;
    String json = rq.toJson();
    synchronized(slaves) {
      for (SlaveProxy slave: slaves)
        slave.println(json);
    }
    return true;
  }

  @Override
  public void shutdown() {
    if (!running) return;
    JsonMessage rq = new JsonMessage();
    rq.action = Action.SHUTDOWN;
    String json = rq.toJson();
    synchronized(slaves) {
      while (!slaves.isEmpty()) {
        SlaveProxy slave = slaves.get(0);
        slave.println(json);
        slave.close();
      }
    }
    try {
      if (listener != null) listener.close();
      listener = null;
    } catch (IOException ex) {
      log.warning(ex.toString());
    }
    super.shutdown();
  }

  @Override
  public String toString() {
    String s = getClass().getName()+"@"+name;
    s += "/master/"+platform;
    return s;
  }

  /////////////// Private stuff
  
  private class SlaveProxy extends Thread {
    private Socket sock;
    private DataOutputStream out;

    public SlaveProxy(Socket sock) {
      this.sock = sock;
    }

    @Override
    public void run() {
      String name = sock.getRemoteSocketAddress().toString();
      log.info("Incoming connection from "+name);
      try {
        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        out = new DataOutputStream(sock.getOutputStream());
        while (true) {
          String s = in.readLine();
          if (s != null) {
            try {
              JsonMessage rq = JsonMessage.fromJson(s);
              switch (rq.action) {
                case CONTAINS_AGENT:
                  respond(rq, containsAgent(rq.agentID));
                  break;
                case REGISTER:
                  register(rq.agentID, rq.service);
                  break;
                case DEREGISTER:
                  if  (rq.service != null) deregister(rq.agentID, rq.service);
                  else deregister(rq.agentID);
                  break;
                case AGENT_FOR_SERVICE:
                  respond(rq, agentForService(rq.service));
                  break;
                case AGENTS_FOR_SERVICE:
                  respond(rq, agentsForService(rq.service));
                  break;
                case SEND:
                  if (rq.relay != null) send(rq.message, rq.relay);
                  else send(rq.message);
                  break;
                case SHUTDOWN:
                  shutdown();
                  break;
              }
            } catch(Exception ex) {
              log.warning("Bad JSON request: "+ex.toString());
            }
          }
        }
      } catch(IOException ex) {
        log.info("Connection to "+name+" closed");
      }
      close();
    }

    private void respond(JsonMessage rq, Object answer) {
      JsonMessage rsp = new JsonMessage();
      rsp.inResponseTo = rq.action;
      rsp.id = rq.id;
      rsp.response = answer;
      println(rsp.toJson());
    }

    public synchronized void println(String s) {
      if (out == null) return;
      try {
        out.writeBytes(s+"\n");
      } catch(IOException ex) {
        log.warning("Write failed: "+ex.toString());
        // TODO remove from slaves (be careful that this may be called from iterators!)
      }
    }

    public void close() {
      synchronized(slaves) {
        slaves.remove(this);
      }
      try {
        if (sock != null) sock.close();
        sock = null;
        out = null;
      } catch (IOException ex) {
        // do nothing
      }
    }
  }

  private void openSocket(int port) throws IOException {
    listener = new ServerSocket(port);
    log.info("Listening on "+listener.getLocalSocketAddress());
    new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            Socket conn = listener.accept();
            SlaveProxy t = new SlaveProxy(conn);
            synchronized(slaves) {
              slaves.add(t);
            }
            t.start();
          }
        } catch (IOException ex) {
          log.info("Stopped listening");
        }
      }
    }.start();
  }

}
