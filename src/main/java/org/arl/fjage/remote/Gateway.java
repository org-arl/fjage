/******************************************************************************

Copyright (c) 2016-2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import java.io.IOException;
import org.arl.fjage.*;

/**
 * Gateway to communicate with agents from Java classes. Only agents in a master
 * or slave container can be accessed using this gateway.
 *
 * @author  Mandar Chitre
 */
public class Gateway implements Messenger {

  /////////////////////// Constants

  /**
   * Represents a non-blocking timeout of 0 seconds. Can be used with all
   * Agent methods that take a timeout parameter.
   */
  public static final long NON_BLOCKING = 0;

  /**
   * Represents a blocking timeout of infinite time. Can be used with all
   * Agent methods that take a timeout parameter.
   */
  public static final long BLOCKING = -1;

  //////////// Private attributes

  protected Container container = null;
  protected Agent agent = null;
  protected boolean shutdownContainer = true;

  protected Gateway() {
    // empty constructor to allow extending gateway
  }

  /////////// Interface methods

  /**
   * Creates a gateway connecting to a specified master container over TCP/IP. The platform specified
   * in this call should not be started previously, and will be automatically started
   * by the gateway.
   *
   * @param platform platform to use
   * @param hostname hostname to connect to.
   * @param port TCP port to connect to.
   */
  public Gateway(Platform platform, String hostname, int port) throws IOException {
    container = new SlaveContainer(platform, "Gateway@"+hashCode(), hostname, port);
    init();
    platform.start();
  }

  /**
   * Creates a gateway connecting to a specified master container over TCP/IP.
   *
   * @param hostname hostname to connect to.
   * @param port TCP port to connect to.
   */
  public Gateway(String hostname, int port) throws IOException {
    Platform platform = new RealTimePlatform();
    container = new SlaveContainer(platform, "Gateway@"+hashCode(), hostname, port);
    init();
    platform.start();
  }

  /**
   * Creates a gateway connecting to a specified master container over RS232. The platform specified
   * in this call should not be started previously, and will be automatically started
   * by the gateway.
   *
   * @param platform platform to use
   * @param devname device name of the RS232 port.
   * @param baud baud rate for the RS232 port.
   * @param settings RS232 settings (null for defaults, or "N81" for no parity, 8 bits, 1 stop bit).
   */
  public Gateway(Platform platform, String devname, int baud, String settings) throws IOException {
    container = new SlaveContainer(platform, "Gateway@"+hashCode(), devname, baud, settings);
    init();
    platform.start();
  }

  /**
   * Creates a gateway connecting to a specified master container over RS232.
   *
   * @param devname device name of the RS232 port.
   * @param baud baud rate for the RS232 port.
   * @param settings RS232 settings (null for defaults, or "N81" for no parity, 8 bits, 1 stop bit).
   */
  public Gateway(String devname, int baud, String settings) throws IOException {
    Platform platform = new RealTimePlatform();
    container = new SlaveContainer(platform, "Gateway@"+hashCode(), devname, baud, settings);
    init();
    platform.start();
  }

  /**
   * Creates a gateway based on an exsiting container.
   */
  public Gateway(Container container) {
    this.container = container;
    shutdownContainer = false;
    init();
  }

  protected void init() {
    agent = new Agent() {
      private Message rsp;
      private Object sync = new Object();
      @Override
      public Message receive(final MessageFilter filter, long timeout) {
        if (Thread.currentThread().getId() == tid) return super.receive(filter, timeout);
        synchronized (sync) {
          rsp = null;
          try {
            add(new OneShotBehavior() {
              @Override
              public void action() {
                rsp = receive(filter, timeout);
                synchronized (sync) {
                  sync.notify();
                }
              }
            });
            sync.wait();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            thread.interrupt();
          }
          return rsp;
        }
      }
    };
    container.add(getAgentID().getName(), agent);
  }

  /**
   * Gets the container for the gateway.
   */
  public Container getContainer() {
    return container;
  }

  /**
   * Gets the platform for the gateway.
   */
  public Platform getPlatform() {
    return container.getPlatform();
  }

  /**
   * Gets the agent ID associated with the gateway.
   *
   * @return agent ID
   */
  public AgentID getAgentID() {
    return new AgentID("GatewayAgent@"+hashCode());
  }

  /**
   * Flushes the incoming message queue.
   */
  public void flush() {
    while (receive() != null);
  }

  /**
   * Closes the gateway. The gateway functionality may not longer be accessed after
   * this method is called.
   */
  public void close() {
    if (container != null) {
      if (shutdownContainer) container.shutdown();
      else container.kill(getAgentID());
    }
    agent = null;
    container = null;
  }

  @Override
  public boolean send(final Message m) {
    if (agent == null) return false;
    agent.add(new OneShotBehavior() {
      @Override
      public void action() {
        agent.send(m);
      }
    });
    return true;
  }

  @Override
  public synchronized Message receive(final MessageFilter filter, long timeout) {
    if (agent == null) return null;
    return agent.receive(filter, timeout);
  }

  public Message receive(final MessageFilter filter) {
    return receive(filter, NON_BLOCKING);
  }

  @Override
  public Message receive(long timeout) {
    return receive((MessageFilter)null, timeout);
  }

  @Override
  public final Message receive() {
    return receive((MessageFilter)null, NON_BLOCKING);
  }

  @Override
  public Message receive(final Class<?> cls, long timeout) {
    return receive(m -> cls.isInstance(m), timeout);
  }

  @Override
  public Message receive(final Class<?> cls) {
    return receive(cls, NON_BLOCKING);
  }

  @Override
  public Message receive(final Message m, long timeout) {
    return receive(new MessageFilter() {
      private String mid = m.getMessageID();
      @Override
      public boolean matches(Message m) {
        String s = m.getInReplyTo();
        if (s == null) return false;
        return s.equals(mid);
      }
    }, timeout);
  }

  @Override
  public Message receive(final Message m) {
    return receive(m, NON_BLOCKING);
  }

  @Override
  public Message request(Message msg, long timeout) {
    send(msg);
    return receive(msg, timeout);
  }

  @Override
  public Message request(Message msg) {
    return request(msg, 1000);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param topic name of the topic.
   * @return object representing the topic.
   */
  public AgentID topic(String topic) {
    if (agent == null) return null;
    AgentID t = agent.topic(topic);
    if (t == null) return null;
    return new AgentID(t, this);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param topic name of the topic.
   * @return object representing the topic.
   */
  public AgentID topic(Enum<?> topic) {
    if (agent == null) return null;
    AgentID t = agent.topic(topic);
    if (t == null) return null;
    return new AgentID(t, this);
  }

  /**
   * Returns an object representing the notification topic for an agent.
   *
   * @param topic agent to get notification topic for.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID topic) {
    if (agent == null) return null;
    AgentID t = agent.topic(topic);
    if (t == null) return null;
    return new AgentID(t, this);
  }

  /**
   * Returns an object representing a named notification topic for an agent.
   *
   * @param aid agent to get notification topic for.
   * @param topic name for the notification topic.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID aid, String topic) {
    if (agent == null) return null;
    AgentID t = agent.topic(aid, topic);
    if (t == null) return null;
    return new AgentID(t, this);
  }

  /**
   * Returns an object representing a named notification topic for an agent.
   *
   * @param aid agent to get notification topic for.
   * @param topic name for the notification topic.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID aid, Enum<?> topic) {
    if (agent == null) return null;
    AgentID t = agent.topic(aid, topic);
    if (t == null) return null;
    return new AgentID(t, this);
  }

  /**
   * Returns an object representing a named agent.
   *
   * @param name name of the agent.
   * @return object representing the agent.
   */
  public AgentID agent(String name) {
    return new AgentID(name, this);
  }

  /**
   * Subscribes the gateway to receive all messages sent to the given topic.
   *
   * @param topic the topic to subscribe to.
   * @return true if the subscription is successful, false otherwise.
   */
  public boolean subscribe(AgentID topic) {
    if (agent == null) return false;
    return agent.subscribe(topic);
  }

  /**
   * Unsubscribes the gateway from a given topic.
   *
   * @param topic the topic to unsubscribe.
   * @return true if the unsubscription is successful, false otherwise.
   */
  public boolean unsubscribe(AgentID topic) {
    if (agent == null) return false;
    return agent.unsubscribe(topic);
  }

  /**
   * Finds an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param service the named service of interest.
   * @return an agent id for an agent that provides the service.
   */
  public AgentID agentForService(String service) {
    if (container == null) return null;
    AgentID t = container.agentForService(service);
    if (t == null) return null;
    return new AgentID(t, this);
  }

  /**
   * Finds an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param service the named service of interest.
   * @return an agent id for an agent that provides the service.
   */
  public AgentID agentForService(Enum<?> service) {
    if (container == null) return null;
    AgentID t = container.agentForService(service.getClass().getName()+"."+service.toString());
    if (t == null) return null;
    return new AgentID(t, this);
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(String service) {
    if (container == null) return null;
    AgentID[] t = container.agentsForService(service);
    if (t == null) return null;
    for (int i = 0; i < t.length; i++)
      t[i] = new AgentID(t[i], this);
    return t;
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(Enum<?> service) {
    if (container == null) return null;
    AgentID[] t = container.agentsForService(service.getClass().getName()+"."+service.toString());
    if (t == null) return null;
    for (int i = 0; i < t.length; i++)
      t[i] = new AgentID(t[i], this);
    return t;
  }

  ////////////// Private methods

  @Override
  public void finalize() {
    close();
  }

}

