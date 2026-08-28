/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.observer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.arl.fjage.Agent;
import org.arl.fjage.AgentID;
import org.arl.fjage.Container;
import org.arl.fjage.Message;
import org.arl.fjage.MessageListener;
import org.arl.fjage.Platform;
import org.arl.fjage.TickerBehavior;
import org.arl.fjage.connectors.Connector;
import org.arl.fjage.connectors.WebServer;
import org.arl.fjage.connectors.WebSocketHubConnector;
import org.arl.fjage.param.ParameterMessageBehavior;
import org.arl.fjage.remote.Action;
import org.arl.fjage.remote.JsonMessage;
import org.eclipse.jetty.server.handler.ContextHandler;

/**
 * An agent that observes all messages passing through its container, and
 * publishes them over a web socket for consumption by a web based debugging
 * interface.
 * <p>
 * To use, simply add the agent to a container:
 * <pre>
 * container.add("observer", new Observer());
 * </pre>
 * The web interface is served by the application's own web server if it has
 * one, and on {@link #DEFAULT_PORT} otherwise. Pass a port explicitly to
 * override that:
 * <pre>
 * container.add("observer", new Observer(8082));
 * </pre>
 * The URL to browse to is logged when the agent starts, and is readable as the
 * {@code url} parameter of the agent.
 * <p>
 * The agent installs a {@link MessageListener} on the container. The listener
 * never consumes a message, and never throws, and so observation is
 * transparent to the agents being observed. Since the listener is called
 * before delivery is attempted, the observer sees messages that are
 * <i>sent</i>, not messages that are successfully delivered. A message sent to
 * a topic is seen once, addressed to the topic, and not once per subscriber.
 * <p>
 * An observer only sees traffic in its own container. In a distributed setup,
 * add one observer per container.
 * <p>
 * Messages are serialized using the same JSON representation that remote
 * containers and gateways use, wrapped in an envelope carrying a sequence
 * number and timestamps:
 * <pre>
 * {"time": 1767225600000, "ptime": 12345, "seq": 7,
 *  "action": "send", "message": {"clazz": "...", "data": {...}}}
 * </pre>
 * One JSON object is published per line.
 * <p>
 * The agent also tracks the endpoints (agents and topics) it has seen traffic
 * to or from, and publishes that list to the web interface, which uses it to
 * let the user choose what to display. Endpoints are discovered before any
 * filtering is applied, so the list is complete even when messages are being
 * dropped.
 * <p>
 * Since fjåge containers can be chatty, messages may be filtered in the
 * container, before serialization, using an {@link ObserverFilter}. The filter
 * may be set programmatically, or from the web interface. It is common to all
 * connected web clients.
 *
 * @author  Mandar Chitre
 */
public class Observer extends Agent implements MessageListener {

  /**
   * Port used for the web interface when no port is specified and the
   * application does not already run a web server.
   */
  public static final int DEFAULT_PORT = 8081;

  /** Port setting denoting that the application's web server is to be used. */
  public static final int AUTO_PORT = -1;

  /** Default context path for the web interface. */
  public static final String DEFAULT_CONTEXT = "/observer";

  /** Default maximum number of messages published per second. */
  public static final int DEFAULT_MAX_RATE = 500;

  protected static final String RESOURCE = "/org/arl/fjage/observer/web";
  protected static final long STATS_INTERVAL = 2000;

  protected int port;
  protected final String context;
  protected final Gson gson = new Gson();
  protected final Object wlock = new Object();
  protected final Map<String,Endpoint> endpoints = new ConcurrentHashMap<>();

  protected WebServer server = null;
  protected WebSocketHubConnector conn = null;
  protected OutputStream out = null;
  protected List<ContextHandler> handlers = null;
  protected Thread ctrlThread = null;
  protected Platform pf = null;

  protected volatile boolean enabled = true;
  protected volatile int maxRate = DEFAULT_MAX_RATE;
  protected volatile ObserverFilter filter = new ObserverFilter().compile();

  protected final AtomicLong count = new AtomicLong();
  protected final AtomicLong dropped = new AtomicLong();
  protected long seq = 0;
  protected long windowStart = 0;
  protected int windowCount = 0;
  protected long lastStats = 0;
  protected long lastStatsCount = -1;
  protected long lastStatsDropped = -1;

  /**
   * Creates an observer agent serving a web interface on the same port as the
   * application's web server, if it has one, and on {@link #DEFAULT_PORT}
   * otherwise. The port is resolved when the agent starts, so that it does not
   * matter whether the application's web server is created before or after the
   * observer is added to the container.
   */
  public Observer() {
    this(AUTO_PORT, DEFAULT_CONTEXT);
  }

  /**
   * Creates an observer agent serving a web interface on a specified port.
   *
   * @param port port to serve the web interface on, or {@link #AUTO_PORT} to
   *             use the application's web server.
   */
  public Observer(int port) {
    this(port, DEFAULT_CONTEXT);
  }

  /**
   * Creates an observer agent serving a web interface on a specified port
   * and context path.
   *
   * @param port port to serve the web interface on, or {@link #AUTO_PORT} to
   *             use the application's web server.
   * @param context context path to serve the web interface at.
   */
  public Observer(int port, String context) {
    this.port = port;
    this.context = context;
  }

  @Override
  public void init() {
    Container c = getContainer();
    if (c != null) pf = c.getPlatform();
    if (port == AUTO_PORT) port = appPort();
    server = WebServer.getInstance(port);
    handlers = server.addStatic(context, RESOURCE, WebServer.NOCACHE);
    if (handlers == null || handlers.isEmpty())
      log.warning("Observer web interface resources not found at "+RESOURCE);
    conn = new WebSocketHubConnector(port, context+"/ws");
    out = conn.getOutputStream();
    conn.setConnectionListener(connector -> {
      // bring a newly connected client up to date
      publishState();
      publishEndpoints(null);
    });
    startControlThread();
    if (c != null) c.addListener(this);
    add(new ParameterMessageBehavior(ObserverParam.class));
    add(new TickerBehavior(STATS_INTERVAL) {
      @Override
      public void onTick() {
        publishStats();
      }
    });
    log.info("Observer web interface at "+getUrl());
  }

  /**
   * Port of the application's web server, so that the observer can share it
   * rather than open a port of its own. If more than one is running, the
   * lowest numbered one is used, and if none is, {@link #DEFAULT_PORT}.
   */
  protected int appPort() {
    int p = AUTO_PORT;
    for (WebServer s: WebServer.getInstances()) {
      int q = s.getPort();
      if (q > 0 && (p == AUTO_PORT || q < p)) p = q;
    }
    if (p == AUTO_PORT) return DEFAULT_PORT;
    log.fine("Observer sharing the web server on port "+p);
    return p;
  }

  @Override
  public void shutdown() {
    Container c = getContainer();
    if (c != null) c.removeListener(this);
    if (ctrlThread != null) {
      ctrlThread.interrupt();
      ctrlThread = null;
    }
    if (conn != null) {
      conn.close();
      conn = null;
      out = null;
    }
    if (server != null && handlers != null) {
      for (ContextHandler h: handlers)
        server.removeStatic(h);
      handlers = null;
      server = null;
    }
  }

  //////////// message observation

  /**
   * Called by the container for every message sent. Never consumes a message,
   * and never throws.
   */
  @Override
  public boolean onReceive(Message msg) {
    try {
      if (!enabled) return false;
      OutputStream os = out;
      if (os == null) return false;
      // endpoints are discovered before filtering, so that the web interface
      // can offer an endpoint that is currently being excluded
      discover(msg);
      ObserverFilter f = filter;
      if (f != null && !f.matches(msg)) return false;
      long t = System.currentTimeMillis();
      synchronized (wlock) {
        if (!allow(t)) {
          dropped.incrementAndGet();
          return false;
        }
        count.incrementAndGet();
        publish(serialize(msg, t, ++seq));
      }
    } catch (Throwable ex) {
      // an exception here would break message delivery for the whole
      // container, since Container.send() does not guard listener calls
      log.log(Level.WARNING, "Observer error: "+ ex, ex);
    }
    return false;
  }

  /**
   * An endpoint the observer has seen traffic to or from, and how much of it.
   * The count is what tells a user which endpoints are worth looking at, in a
   * container with more agents than fit on a screen.
   */
  protected static class Endpoint {

    protected final boolean topic;
    protected final AtomicLong count = new AtomicLong();

    protected Endpoint(boolean topic) {
      this.topic = topic;
    }

  }

  /**
   * Notes the endpoints involved in a message, publishing any newly seen ones.
   * This is cheap, since it does not serialize anything.
   */
  protected void discover(Message msg) {
    List<String> fresh = null;
    AgentID[] aids = new AgentID[] { msg.getSender(), msg.getRecipient() };
    String last = null;
    for (AgentID aid: aids) {
      String name = endpoint(aid);
      if (name == null || name.equals(last)) continue;    // count a self-message once
      last = name;
      Endpoint ep = endpoints.get(name);
      if (ep == null) {
        Endpoint prev = endpoints.putIfAbsent(name, ep = new Endpoint(aid.isTopic()));
        if (prev != null) ep = prev;
        else {
          if (fresh == null) fresh = new ArrayList<>(2);
          fresh.add(name);
        }
      }
      ep.count.incrementAndGet();
    }
    if (fresh != null) publishEndpoints(fresh);
  }

  /**
   * Serializes a message, wrapped in an observer envelope. The message is
   * serialized inline, on the calling agent's thread, since messages are
   * delivered by reference within a container, and may be mutated by the
   * recipient as soon as this method returns.
   */
  protected String serialize(Message msg, long t, long n) {
    JsonMessage jm = new JsonMessage();
    jm.action = Action.SEND;
    jm.message = msg;
    String s = jm.toJson();
    if (s == null || s.length() < 2 || s.charAt(0) != '{') return null;
    // only numeric fields are spliced in, so there is no escaping to get wrong
    StringBuilder sb = new StringBuilder(s.length()+64);
    sb.append("{\"time\":").append(t);
    if (pf != null) sb.append(",\"ptime\":").append(pf.currentTimeMillis());
    sb.append(",\"seq\":").append(n).append(',');
    sb.append(s, 1, s.length());
    return sb.toString();
  }

  /** Rate limiter, called with wlock held. */
  protected boolean allow(long t) {
    if (maxRate <= 0) return true;
    if (t - windowStart >= 1000) {
      windowStart = t;
      windowCount = 0;
    }
    if (windowCount >= maxRate) return false;
    windowCount++;
    return true;
  }

  /** Publishes a line to all connected web clients, called with wlock held. */
  protected void publish(String json) throws IOException {
    OutputStream os = out;
    if (json == null || os == null) return;
    os.write(json.getBytes(StandardCharsets.UTF_8));
    os.write('\n');
  }

  /** Publishes a line to all connected web clients, swallowing any error. */
  protected void publishSafely(String json) {
    try {
      synchronized (wlock) {
        publish(json);
      }
    } catch (Throwable ex) {
      log.log(Level.WARNING, "Observer error: "+ ex, ex);
    }
  }

  /**
   * Publishes the endpoints seen so far, or a subset of newly seen ones.
   *
   * @param subset endpoint names to publish, null to publish the full list.
   */
  protected void publishEndpoints(List<String> subset) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"action\":\"endpoints\",\"full\":").append(subset == null);
    sb.append(",\"endpoints\":[");
    boolean first = true;
    for (Map.Entry<String,Endpoint> e: endpoints.entrySet()) {
      if (subset != null && !subset.contains(e.getKey())) continue;
      if (!first) sb.append(',');
      first = false;
      sb.append("{\"name\":").append(gson.toJson(e.getKey()));
      sb.append(",\"topic\":").append(e.getValue().topic);
      sb.append(",\"count\":").append(e.getValue().count.get()).append('}');
    }
    sb.append("]}");
    publishSafely(sb.toString());
  }

  protected void publishState() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"action\":\"state\",\"enabled\":").append(enabled);
    sb.append(",\"maxRate\":").append(maxRate);
    sb.append(",\"count\":").append(count.get());
    sb.append(",\"dropped\":").append(dropped.get());
    sb.append(",\"filter\":").append(gson.toJson(filter));
    // an observer sees only its own container, and saying which one heads off
    // the "why can't I see agent X" question in a distributed setup
    Container c = getContainer();
    sb.append(",\"container\":").append(gson.toJson(c == null ? null : c.getName()));
    sb.append('}');
    publishSafely(sb.toString());
  }

  protected void publishStats() {
    long t = System.currentTimeMillis();
    if (t - lastStats < STATS_INTERVAL) return;     // virtual time may run fast
    lastStats = t;
    long n = count.get();
    long d = dropped.get();
    if (n == lastStatsCount && d == lastStatsDropped) return;
    lastStatsCount = n;
    lastStatsDropped = d;
    publishSafely("{\"action\":\"stats\",\"count\":"+n+",\"dropped\":"+d+"}");
    publishEndpoints(null);     // refresh the per-endpoint counts
  }

  //////////// control channel

  /**
   * Web clients share a single input stream on the hub connector, and there is
   * no way to reply to an individual client. Control commands are therefore
   * applied globally, and the resulting state is published to all clients.
   */
  protected void startControlThread() {
    ctrlThread = new Thread(() -> {
      try {
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String s;
        while ((s = in.readLine()) != null) {
          s = s.trim();
          if (s.isEmpty()) continue;
          try {
            handleControl(s);
          } catch (Throwable ex) {
            log.log(Level.WARNING, "Bad observer command: "+s, ex);
          }
        }
      } catch (Throwable ex) {
        if (conn != null) log.log(Level.WARNING, "Observer control thread died: "+ ex, ex);
      }
    }, getClass().getSimpleName()+":ctrl");
    ctrlThread.setDaemon(true);
    ctrlThread.start();
  }

  protected void handleControl(String s) {
    JsonElement e = JsonParser.parseString(s);
    if (e == null || !e.isJsonObject()) return;
    JsonObject obj = e.getAsJsonObject();
    JsonElement a = obj.get("action");
    if (a == null) return;
    String action = a.getAsString();
    if ("filter".equals(action)) {
      JsonElement f = obj.get("filter");
      ObserverFilter nf = f == null || !f.isJsonObject() ? new ObserverFilter()
                                                         : gson.fromJson(f, ObserverFilter.class);
      if (nf == null) nf = new ObserverFilter();
      filter = nf.compile();
      log.fine("Observer filter updated: "+filter);
    } else if ("enable".equals(action)) {
      JsonElement v = obj.get("enabled");
      enabled = v == null || v.getAsBoolean();
    } else if ("maxRate".equals(action)) {
      JsonElement v = obj.get("maxRate");
      if (v != null) maxRate = v.getAsInt();
    } else if ("state".equals(action)) {
      publishEndpoints(null);
    } else if ("clearEndpoints".equals(action)) {
      clearEndpoints();
    } else {
      return;
    }
    publishState();
  }

  //////////// parameters

  public boolean getEnabled() {
    return enabled;
  }

  public boolean setEnabled(boolean b) {
    enabled = b;
    publishState();
    return enabled;
  }

  public long getCount() {
    return count.get();
  }

  public long getDropped() {
    return dropped.get();
  }

  public int getMaxRate() {
    return maxRate;
  }

  public int setMaxRate(int r) {
    maxRate = r;
    publishState();
    return maxRate;
  }

  public int getConnections() {
    Connector c = conn;
    return c == null ? 0 : c.connections().length;
  }

  public String getUrl() {
    String host = "localhost";
    try {
      host = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException ex) {
      // use localhost
    }
    return "http://"+host+":"+port+context+"/";
  }

  //////////// filter API

  /**
   * Gets the filter currently applied to observed messages.
   *
   * @return current filter.
   */
  public ObserverFilter getFilter() {
    return filter;
  }

  /**
   * Sets the filter to apply to observed messages, in the container, before
   * serialization.
   *
   * @param f filter to apply, null to observe all messages.
   */
  public void setFilter(ObserverFilter f) {
    filter = f == null ? new ObserverFilter().compile() : f.compile();
    publishState();
  }

  /**
   * Gets the endpoints (agents and topics) that the observer has seen traffic
   * to or from.
   *
   * @return endpoint names, topics prefixed with {@code #}.
   */
  public String[] getEndpoints() {
    return endpoints.keySet().toArray(new String[0]);
  }

  /**
   * Forgets the endpoints seen so far, and tells the web interface to do the
   * same. Since the list only ever grows, this is how to get rid of endpoints
   * that are no longer around, such as gateways that have long since
   * disconnected. Endpoints still in use reappear as soon as they send or
   * receive anything.
   */
  public void clearEndpoints() {
    endpoints.clear();
    publishEndpoints(null);
  }

  /**
   * Name of an endpoint, as it appears on the wire. Topics are prefixed with
   * {@code #}, matching fjåge's JSON encoding of an {@link AgentID}.
   *
   * @param aid agent id of an agent or a topic.
   * @return endpoint name, or null if the agent id is null.
   */
  static String endpoint(AgentID aid) {
    if (aid == null) return null;
    String name = aid.getName();
    if (name == null) return null;
    return aid.isTopic() ? "#"+name : name;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()+"["+getUrl()+"]";
  }

}
