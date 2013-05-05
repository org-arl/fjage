/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;
import java.lang.reflect.*;

/**
 * Container to manage agent lifecycle. Agents in a container are able to
 * discover services provided by each other, and send or receive messages to
 * each other or common topics. Containers run on a platform. For an example
 * of how containers with agents are created, see {@link RealTimePlatform}.
 * <p>
 * By default, messages sent between agents are not cloned. The developer must
 * ensure that a message or any object contained in the message is not modified
 * after it has been sent or received. During deployment, this default behavior
 * can be changed using {@link #setAutoClone(boolean)}.
 *
 * @author  Mandar Chitre
 */
public class Container {

  //////////// Public constants

  public static final String SERIAL_CLONER = "org.apache.commons.lang3.SerializationUtils";
  public static final String FAST_CLONER = "com.rits.cloning.Cloner";

  //////////// Private attributes

  protected String name;
  protected Platform platform;
  protected Map<AgentID,Agent> agents = Collections.synchronizedMap(new HashMap<AgentID,Agent>());
  protected Map<AgentID,Set<Agent>> topics = new HashMap<AgentID,Set<Agent>>();
  protected Map<String,Set<AgentID>> services = new HashMap<String,Set<AgentID>>();
  protected Logger log = Logger.getLogger(getClass().getName());
  protected boolean running = false;
  protected Object cloner;
  protected Method doClone;
  protected boolean autoclone = false;
  protected Set<AgentID> idle = new HashSet<AgentID>();

  //////////// Interface methods

  /**
   * Creates a container.
   *
   * @param platform platform on which the container runs.
   */
  public Container(Platform platform) {
    name = Integer.toHexString(hashCode());
    this.platform = platform;
    LogHandlerProxy.install(platform, log);
    setCloner(SERIAL_CLONER);
    platform.addContainer(this);
  }

  /**
   * Creates a named container.
   *
   * @param platform platform on which the container runs.
   * @param name name of the container.
   */
  public Container(Platform platform, String name) {
    this.name = name;
    this.platform = platform;
    LogHandlerProxy.install(platform, log);
    setCloner(SERIAL_CLONER);
    platform.addContainer(this);
  }

  /**
   * Gets the platform on which the container runs.
   *
   * @return the platform.
   */
  public Platform getPlatform() {
    return platform;
  }

  /**
   * Sets the name of the container.
   *
   * @param name name of the container.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the name of the container.
   *
   * @return name of the container.
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the cloner to use for deep cloning. Two cloners are currently supported:
   * <ul>
   * <li>SERIAL_CLONER -- Cloner using serialization (default)
   *     (requires <a href="http://commons.apache.org/lang/">Apache commons-lang</a> v3)
   * <li>FAST_CLONER -- Fast but Groovy incompatible cloner
   *     (requires <a href="http://code.google.com/p/cloning/">Java deep cloning library</a>)
   * </ul>
   *
   * @param name name of the cloner to use.
   */
  public void setCloner(String name) {
    try {
      if (name.equals(SERIAL_CLONER)) {
        cloner = null;
        doClone = Class.forName(SERIAL_CLONER).getDeclaredMethod("clone", Serializable.class);
      } else if (name.equals(FAST_CLONER)) {
        Class<?> cls = Class.forName(FAST_CLONER);
        cloner = cls.newInstance();
        doClone = cls.getMethod("deepClone", Object.class);
      } else {
        cloner = null;
        doClone = null;
        throw new FjageError("Unknown cloner name, cloning disabled");
      }
    } catch (Exception ex) {
      log.warning("Cloner creation failed: "+ex.toString());
      cloner = null;
      doClone = null;
      throw new FjageError("Cloner creation failed, cloning disabled");
    }
  }

  /**
   * Deep clones an object. This is typically used to explicitly clone a message for
   * modification when autocloning is not enabled.
   *
   * @param obj object to clone.
   * @return cloned object.
   */
  @SuppressWarnings("unchecked")
  public <T extends Serializable> T clone(T obj) {
    if (doClone == null) throw new FjageError("Cloner unavailable");
    try {
      return (T)doClone.invoke(cloner, obj);
    } catch (Exception ex) {
      log.warning("Cloning failed: "+ex.toString());
      throw new FjageError("Cloning failed");
    }
  }

  /**
   * Enables or disables autocloning. Autocloning is disabled by default.
   *
   * @param b true to enable autocloning, false to disable it.
   */
  public void setAutoClone(boolean b) {
    autoclone = b;
  }

  /**
   * Returns whether autoclone is enabled.
   *
   * @return true if autocloning enabled, false otherwise.
   */
  public boolean getAutoClone() {
    return autoclone;
  }

  /**
   * Adds an agent to the container.
   *
   * @param name name of the agent.
   * @param agent the agent object.
   * @return an agent id if successful, null on failure.
   */
  public AgentID add(String name, Agent agent) {
    if (name == null || name.length() == 0) {
      log.warning("Undefined agent name");
      return null;
    }
    AgentID aid = new AgentID(name);
    if (isDuplicate(aid)) {
      log.warning("Duplicate agent name");
      return null;
    }
    agent.bind(aid, this);
    agents.put(aid, agent);
    AgentLocalRandom.bind(agent);
    if (running) {
      Thread t = new Thread(agent);
      t.setName(name);
      t.setDaemon(false);
      AgentLocalRandom.bind(agent, t);
      t.start();
    }
    return aid;
  }

  /**
   * Adds an agent to the container. The name of the agent is automatically
   * chosen.
   *
   * @param agent the agent object.
   * @return an agent id if successful, null on failure.
   */
  public AgentID add(Agent agent) {
    return add(agent.getClass().getName()+"@"+agent.hashCode(), agent);
  }

  /**
   * Checks if an agent exists in the container.
   *
   * @param aid agent id to check.
   * @return true if the agent exists, false otherwise.
   */
  public boolean containsAgent(AgentID aid) {
    return agents.containsKey(aid);
  }

  /**
   * Checks if an agent can be located. If agent in not found in current
   * container, any available remote containers are checked too.
   *
   * @param aid agent id to locate.
   * @return true if the agent can be located, false otherwise.
   */
  public boolean canLocateAgent(AgentID aid) {
    return isDuplicate(aid);
  }

  /**
   * Gets an agent given its id.
   *
   * @param aid agent id.
   * @return agent associated with the id if exists, false if no such agent exists.
   */
  public Agent getAgent(AgentID aid) {
    return agents.get(aid);
  }

  /**
   * Gets all agents in the container.
   *
   * @return an array of agents.
   */
  public Agent[] getAgents() {
    return agents.values().toArray(new Agent[agents.size()]);
  }

  /**
   * Terminates an agent.
   *
   * @param aid id of agent to terminate.
   * @return true if successful, false on failure.
   */
  public boolean kill(AgentID aid) {
    log.fine("Agent "+aid+" killed");
    synchronized (this) {
      Agent agent = agents.get(aid);
      if (agent == null) return false;
      agent.stop();
      agents.remove(aid);
      idle.remove(aid);
      unsubscribe(aid);
      deregister(aid);
      notify();   // if we are waiting for shutdown
    }
    return true;
  }

  /**
   * Terminates an agent.
   *
   * @param name name of agent to terminate.
   * @return true if successful, false on failure.
   */
  public boolean kill(String name) {
    return kill(new AgentID(name));
  }

  /**
   * Sends a message. The message is sent to the recipient specified in the
   * message. In case of associated remote containers, the message is only
   * delivered to agents in this container.
   *
   * @param m message to deliver
   * @return true if delivered, false otherwise.
   */
  public boolean send(Message m) {
    return send(m, false);
  }

  /**
   * Sends a message. The message is sent to the recipient specified in the
   * message.
   *
   * @param m message to deliver
   * @param relay enable relaying to associated remote containers.
   * @return true if delivered, false otherwise.
   */
  public boolean send(Message m, boolean relay) {
    if (!running) return false;
    if (relay) log.warning("Container does not support relaying");
    AgentID aid = m.getRecipient();
    if (aid == null) return false;
    if (aid.isTopic()) {
      synchronized (this) {
        Set<Agent> subscribers = topics.get(aid);
        if (subscribers != null) {
          for (Agent a: subscribers)
            a.deliver(m);
        }
      }
    } else {
      Agent a = getAgent(aid);
      if (a == null) return false;
      a.deliver(m);
    }
    return true;
  }

  /**
   * Subscribes an agent to messages sent to a topic.
   *
   * @param aid id of agent to subscribe.
   * @param topic topic to subscribe to.
   * @return true on success, false on failure.
   */
  public synchronized boolean subscribe(AgentID aid, AgentID topic) {
    if (!topic.isTopic()) {
      log.warning("Unable to subscribe to non-topic "+topic);
      return false;
    }
    Agent agent = agents.get(aid);
    if (agent == null) {
      log.warning("Unable to subscribe unknown agent "+aid+" to topic "+topic);
      return false;
    }
    Set<Agent> subscribers = topics.get(topic);
    if (subscribers == null) {
      subscribers = new HashSet<Agent>();
      topics.put(topic, subscribers);
    }
    subscribers.add(agent);
    return true;
  }

  /**
   * Unsubscribes an agent from a topic.
   *
   * @param aid id of agent to unsubscribe.
   * @param topic topic to unsubscribe from.
   * @return true on success, false on failure.
   */
  public synchronized boolean unsubscribe(AgentID aid, AgentID topic) {
    if (!topic.isTopic()) {
      log.warning("Unable to unsubscribe from non-topic "+topic);
      return false;
    }
    Agent agent = agents.get(aid);
    if (agent == null) return false;
    Set<Agent> subscribers = topics.get(topic);
    if (subscribers == null) return false;
    return subscribers.remove(agent);
  }

  /**
   * Unsubscribes an agent from all topics.
   *
   * @param aid id of agent to unsubscribe.
   */
  public synchronized void unsubscribe(AgentID aid) {
    Agent agent = agents.get(aid);
    if (agent == null) return;
    for (AgentID topic: topics.keySet()) {
      Set<Agent> subscribers = topics.get(topic);
      subscribers.remove(agent);
    }
  }

  /**
   * Registers an agent in the directory service as a provider of a named service.
   *
   * @param aid id of agent providing the service.
   * @param service name of the service.
   * @return true on success, false on failure.
   */
  public synchronized boolean register(AgentID aid, String service) {
    Set<AgentID> providers = services.get(service);
    if (providers == null) {
      providers = new HashSet<AgentID>();
      services.put(service, providers);
    }
    providers.add(aid);
    return true;
  }

  /**
   * Finds an agent providing a named service.
   *
   * @param service name of the service.
   * @return agent id for service provider, null if none found.
   */
  public synchronized AgentID agentForService(String service) {
    Set<AgentID> providers = services.get(service);
    if (providers == null || providers.size() == 0) return null;
    return providers.iterator().next();
  }

  /**
   * Finds all agents providing a named service.
   *
   * @param service name of the service.
   * @return an array of agent ids for service providers, null if none found.
   */
  public synchronized AgentID[] agentsForService(String service) {
    Set<AgentID> providers = services.get(service);
    if (providers == null || providers.size() == 0) return null;
    return providers.toArray(new AgentID[providers.size()]);
  }

  /**
   * Deregisters an agent as a provider of a specific service.
   *
   * @param aid id of agent to deregister.
   * @param service name of the service to deregister.
   * @return true on success, false on failure.
   */
  public synchronized boolean deregister(AgentID aid, String service) {
    Set<AgentID> providers = services.get(service);
    if (providers == null) return false;
    return providers.remove(aid);
  }

  /**
   * Deregisters an agent as a provider of all services.
   *
   * @param aid id of agent to deregister.
   */
  public synchronized void deregister(AgentID aid) {
    for (String service: services.keySet()) {
      Set<AgentID> providers = services.get(service);
      providers.remove(aid);
    }
  }

  /**
   * Initialize the container and all agents in it.
   * This should be called before start().
   */
  public void init() {
    if (!running) {
      log.info("Initializing agents...");
      synchronized (agents) {
        SortedSet<AgentID> keys = new TreeSet<AgentID>(agents.keySet());
        for (AgentID aid: keys) {
          Agent a = agents.get(aid);
          Thread t = new Thread(a);
          t.setName(a.getName());
          t.setDaemon(false);
          AgentLocalRandom.bind(a, t);
          t.start();
        }
      }
      log.fine("Waiting for agents...");
      do {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ex) {
          // do nothing
        }
      } while (!isIdle());
      log.info("Agents ready...");
    }
  }

  /**
   * Starts the container and all agents in it.
   * This should be called after init().
   */
  public void start() {
    if (!running) {
      log.info("Starting container...");
      running = true;
      for (Agent a: agents.values()) {
        if (a.getState() != AgentState.IDLE)
          throw new FjageError("Container start() called without init()");
        a.wake();
      }
    }
  }

  /**
   * Terminates the container and all agents in it.
   */
  public void shutdown() {
    if (!running) return;
    while (true) {
      try {
        log.info("Initiating shutdown...");
        for (Agent a: agents.values())
          a.stop();
        log.fine("Waiting for agents to shutdown...");
        synchronized (this) {
          while (!agents.isEmpty()) {
            try {
              wait();   // wait for all agents to kill themselves
            } catch (InterruptedException ex) {
              // do nothing, try again
            }
          }
        }
        log.info("All agents have shutdown");
        agents.clear();
        idle.clear();
        running = false;
        return;
      } catch (ConcurrentModificationException ex) {
        // do nothing, try again
      }
    }
  }

  /**
   * Checks if the container is currently running.
   *
   * @return true if the container is running, false otherwise.
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * Checks if all agents in the container are idle.
   *
   * @return true if all agents are idle, false otherwise.
   */
  public boolean isIdle() {
    int nAgents = agents.size();
    synchronized (idle) {
      return nAgents == idle.size();
    }
  }

  /////////////// Standard Java methods to customize

  /**
   * Gets a string representation of the container.
   *
   * @return string representation of the container.
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    String s = getClass().getName()+"@"+name;
    s += "/"+platform;
    return s;
  }
  
  //////////////// Package private methods
  
  /**
   * Deep clones an object if autoclone is enabled.
   *
   * @param obj object to clone.
   * @return cloned object, if autoclone is enabled, original object otherwise.
   */
  <T extends Serializable> T autoclone(T obj) {
    if (autoclone) return clone(obj);
    return obj;
  }

  //////////////// Private methods

  /**
   * Checks if an agent id already exists in the container.
   *
   * @param aid the agent id to check.
   * @return true if it exists, false otherwise.
   */
  protected boolean isDuplicate(AgentID aid) {
    return agents.containsKey(aid);
  }

  /**
   * Called by agent to report when its idle.
   *
   * @param agent agent that is idle.
   */
  void reportIdle(AgentID aid) {
    synchronized (idle) {
      idle.add(aid);
    }
    if (running && isIdle()) platform.idle();
  }

  /**
   * Called by agent to report when its busy.
   *
   * @param agent agent that is busy.
   */
  void reportBusy(AgentID aid) {
    synchronized (idle) {
      idle.remove(aid);
    }
  }

}
