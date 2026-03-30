package org.arl.fjage.remote;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

import org.arl.fjage.*;
import org.arl.fjage.param.*;
import org.arl.fjage.remote.JsonMessage;
import org.arl.fjage.connectors.*;

/**
 * An agent that serves as a tunnel for messages between two fjage platforms.
 * <p>
 * A tunnel can be configured as a server or a client. A server tunnel listens on
 * a specified TCP port for incoming connections from client tunnels. A client
 * tunnel connects to a server tunnel at a specified IP address and TCP port.
 * <p>
 * Once a connection is established, the tunnel agent forwards selected messages
 * between the two platforms. The `agents` parameter specifies the list of remote
 * agents or topics forwarded through the tunnel.
 */
public class Tunnel extends Agent implements ConnectionListener, MessageListener {

  //// private attributes

  protected final static long MONITOR_PERIOD = 5000;

  protected String ip;
  protected int port;

  protected TcpServer server = null;
  protected List<AgentID> agents = new ArrayList<>();
  protected List<Connector> connectors = new ArrayList<>();
  protected ExecutorService executor = null;
  protected int connID = 0;
  protected Map<Integer,Connector> connIDs = new HashMap<>();

  //// constructors

  public Tunnel(int port) {
    this.ip = null;
    this.port = port;
  }

  public Tunnel(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  //// documentation

  public final static String __doc__ =
    "# @@ - tunnel\n\n" +
    "Tunnels messages between two fjage platforms over a TCP/IP link.\n\n" +
    "## Parameters:\n\n" +
    "### @@.agents - list of remote agents/topics visible through the tunnel\n\n" +
    "Example:\n  @@.agents = [agent('remoteAgent'), topic('remoteTopic')]\n\n" +
    "### @@.ip - IP address of the server to connect to (null for servers)\n" +
    "### @@.port - TCP port number\n";

  //// agent methods

  @Override
  public void init() {
    register(org.arl.fjage.shell.Services.DOCUMENTATION);
    executor = Executors.newCachedThreadPool();
    add(new ParameterMessageBehavior(TunnelParam.class));
    if (ip == null) {
      server = new TcpServer(port, this);
      if (port == 0) {
        int p = server.getPort();
        if (p > 0) port = p;
        else log.warning("Failed to get TCP server port number");
      }
    } else add(new TickerBehavior(MONITOR_PERIOD) {
      @Override
      public void onTick() {
        synchronized (connectors) {
          if (connectors.isEmpty()) connect();
        }
      }
    });
    getContainer().addListener(this);
    log.info("Agent "+getName()+" init");
  }

  @Override
  public void shutdown() {
    log.info("Agent "+getName()+" shutdown");
    getContainer().removeListener(this);
    executor.shutdownNow();
    if (server != null) {
      server.close();
      server = null;
    }
    synchronized (connectors) {
      for (Connector c : connectors) c.close();
      connectors.clear();
      connIDs.clear();
    }
  }

  //// connection & message management

  @Override
  public void connected(Connector connector) {
    log.info("Incoming connection: "+connector.getName());
    synchronized (connectors) {
      connectors.add(connector);
      connIDs.put(++connID, connector);
      monitor(connID, connector);
    }
  }

  protected void connect() {
    try {
      Connector c = new TcpConnector(ip, port);
      log.info("Connected to "+ip+":"+port);
      synchronized (connectors) {
        connectors.add(c);
        connIDs.put(++connID, c);
        monitor(connID, c);
      }
    } catch (IOException ex) {
      log.fine("Failed to connect to "+ip+":"+port+": "+ex.getMessage());
    }
  }

  @Override
  public boolean onReceive(Message msg) {
    synchronized (connectors) {
      if (connectors.isEmpty()) return false;
    }
    AgentID rcpt = msg.getRecipient();
    AgentID sender = msg.getSender();
    if (rcpt == null || sender == null) return false;
    if (sender.getName().contains("@")) return false;
    boolean shouldForward = false;
    synchronized (agents) {
      shouldForward = agents.contains(rcpt);
    }
    if (shouldForward) {
      JsonMessage jmsg = new JsonMessage();
      jmsg.message = msg;
      String json = jmsg.toJson();
      log.fine("* << "+json);
      synchronized (connectors) {
        for (Connector c: connectors)
          sendToRemote(c, json.getBytes(StandardCharsets.UTF_8));
      }
      if (!rcpt.isTopic()) return true;
    } else if (!rcpt.isTopic() && rcpt.getName().contains("@")) {
      int id = 0;
      Connector c = null;
      String rname = null;
      try {
        String s = rcpt.getName();
        int i = s.lastIndexOf('@');
        id = Integer.parseInt(s.substring(i+1));
        rname = s.substring(0, i);
        synchronized (connectors) {
          c = connIDs.get(id);
        }
      } catch (NumberFormatException ex) {
        return false;
      }
      if (c != null) {
        msg.setRecipient(new AgentID(rname));
        JsonMessage jmsg = new JsonMessage();
        jmsg.message = msg;
        String json = jmsg.toJson();
        log.fine(id+" << "+json);
        sendToRemote(c, json.getBytes());
        return true;
      }
    }
    return false;
  }

  protected void monitor(int id, Connector c) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        String cname = c.getName();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()), StandardCharsets.UTF_8)) {
          String line;
          while ((line = in.readLine()) != null) {
            log.fine(id+" >> "+line);
            JsonMessage jmsg = JsonMessage.fromJson(line);
            if (jmsg == null || jmsg.message == null) continue;
            AgentID sender = jmsg.message.getSender();
            if (sender == null) continue;
            jmsg.message.setSender(new AgentID(sender.getName() + "@" + id));
            getContainer().send(jmsg.message);
          }
        } catch (IOException ex) {
          log.fine("Read from "+cname+" failed: "+ex.getMessage());
        }
        removeConnector(c);
      }
    });
  }

  protected void sendToRemote(Connector c, byte[] data) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          OutputStream out = c.getOutputStream();
          if (out == null) removeConnector(c);
          else synchronized (out) {
            out.write(data);
            out.write('\n');
            out.flush();
          }
        } catch (IOException ex) {
          log.warning("Write to "+c.getName()+" failed: "+ex.getMessage());
          removeConnector(c);
        }
      }
    });
  }

  protected void removeConnector(Connector c) {
    synchronized (connectors) {
      connectors.remove(c);
      connIDs.values().removeIf(v -> v.equals(c));
    }
    c.close();
  }

  //// parameters

  /**
   * Get IP address for the tunnel. In case of a client tunnel, this is the IP
   * address of the server to connect to. In case of a server tunnel, this is
   * set to null.
   *
   * @return IP address for the tunnel, or null if this is a server tunnel.
   */
  public String getIp() {
    return ip;
  }

  /**
   * Get TCP port number for the tunnel. In case of a client tunnel, this is the
   * TCP port number of the server to connect to. In case of a server tunnel,
   * this is the TCP port number to listen on for incoming client connections.
   *
   * @return TCP port number for the tunnel.
   */
  public int getPort() {
    return port;
  }

  /**
   * Get the list of remote agents or topics visible through the tunnel.
   *
   * @return List of remote agents/topics visible through the tunnel.
   */
  public List<AgentID> getAgents() {
    synchronized (agents) {
      return agents;
    }
  }

  /**
   * Set the list of remote agents or topics visible through the tunnel.
   *
   * @param agents List of remote agents/topics visible through the tunnel.
   */
  public void setAgents(List<AgentID> agents) {
    synchronized (agents) {
      this.agents.clear();
      if (agents == null) return;
      for (AgentID aid : agents)
        if (aid != null) this.agents.add(new AgentID(aid.getName(), aid.isTopic()));
    }
  }

  /**
   * Get title of the tunnel agent.
   *
   * @return title.
   */
  public String getTitle() {
    return "Tunnel";
  }

  /**
   * Get description of the tunnel agent.
   *
   * @return description.
   */
  public String getDescription() {
    String s = " (no connections)";
    synchronized (connectors) {
      int n = connectors.size();
      if (n == 1) s = " (1 connection)";
      else if (n > 1) s = " ("+n+" connections)";
    }
    if (ip != null) return "Tunnel to " + ip + ":" + port + s;
    return "Tunnel listening on port " + port + s;
  }

}
