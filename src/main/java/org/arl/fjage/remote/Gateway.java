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
public class Gateway {

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

  private SlaveContainer container;
  private Agent agent;

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
  public Gateway(Platform platform, String devname, int baud, String settings) {
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
  public Gateway(String devname, int baud, String settings) {
    Platform platform = new RealTimePlatform();
    container = new SlaveContainer(platform, "Gateway@"+hashCode(), devname, baud, settings);
    init();
    platform.start();
  }

  private void init() {
    agent = new Agent() {
      private Message rsp;
      private Object sync = new Object();
      @Override
      public Message receive(final MessageFilter filter, final long timeout) {
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
            // ignore exception
          }
          return rsp;
        }
      }
    };
    container.add(getAgentID().getName(), agent);
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
   * Closes the gateway. The gateway functionality may not longer be accessed after
   * this method is called.
   */
  public void shutdown() {
    if (container != null) container.shutdown();
    agent = null;
    container = null;
  }

  /**
   * Sends a message to the recipient indicated in the message. The recipient
   * may be an agent or a topic.
   *
   * @param m message to be sent.
   */
  public void send(final Message m) {
    agent.add(new OneShotBehavior() {
      @Override
      public void action() {
        agent.send(m);
      }
    });
  }

  /**
   * Returns a message received by the gateway and matching the given filter.
   * This method blocks until timeout if no message available.
   *
   * @param filter message filter.
   * @param timeout timeout in milliseconds.
   * @return received message matching the filter, null on timeout.
   */
  public synchronized Message receive(final MessageFilter filter, final long timeout) {
    return agent.receive(filter, timeout);
  }

  /**
   * Returns a message received by the gateway. This method is non-blocking.
   *
   * @return received message, null if none available.
   */
  public final Message receive() {
    return receive((MessageFilter)null, 0);
  }

  /**
   * Returns a message received by the agent. This method blocks until timeout if no
   * message available.
   *
   * @param timeout timeout in milliseconds.
   * @return received message, null on timeout.
   */
  public Message receive(long timeout) {
    return receive((MessageFilter)null, timeout);
  }

  /**
   * Returns a message of a given class received by the gateway. This method is non-blocking.
   *
   * @param cls the class of the message of interest.
   * @return received message of the given class, null if none available.
   */
  public Message receive(final Class<?> cls) {
    return receive(cls, 0);
  }

  /**
   * Returns a message of a given class received by the gateway. This method blocks until
   * timeout if no message available.
   *
   * @param cls the class of the message of interest.
   * @param timeout timeout in milliseconds.
   * @return received message of the given class, null on timeout.
   */
  public Message receive(final Class<?> cls, long timeout) {
    return receive(new MessageFilter() {
      @Override
      public boolean matches(Message m) {
        return cls.isInstance(m);
      }
    }, timeout);
  }

  /**
   * Returns a response message received by the gateway. This method is non-blocking.
   *
   * @param m original message to which a response is expected.
   * @return received response message, null if none available.
   */
  public Message receive(final Message m) {
    return receive(m, 0);
  }

  /**
   * Returns a response message received by the gateway. This method blocks until
   * timeout if no message available.
   *
   * @param m original message to which a response is expected.
   * @param timeout timeout in milliseconds.
   * @return received response message, null on timeout.
   */
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

  /**
   * Sends a request and waits for a response. This method blocks until timeout
   * if no response is received.
   *
   * @param msg message to send.
   * @param timeout timeout in milliseconds.
   * @return received response message, null on timeout.
   */
  public Message request(Message msg, long timeout) {
    send(msg);
    return receive(msg, timeout);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param topic name of the topic.
   * @return object representing the topic.
   */
  public AgentID topic(String topic) {
    return agent.topic(topic);
  }

  /**
   * Returns an object representing the named topic.
   *
   * @param topic name of the topic.
   * @return object representing the topic.
   */
  public AgentID topic(Enum<?> topic) {
    return agent.topic(topic);
  }

  /**
   * Returns an object representing the notification topic for an agent.
   *
   * @param topic agent to get notification topic for.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID topic) {
    return agent.topic(topic);
  }

  /**
   * Returns an object representing a named notification topic for an agent.
   *
   * @param aid agent to get notification topic for.
   * @param topic name for the notification topic.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID aid, String topic) {
    return agent.topic(aid, topic);
  }

  /**
   * Returns an object representing a named notification topic for an agent.
   *
   * @param aid agent to get notification topic for.
   * @param topic name for the notification topic.
   * @return object representing the topic.
   */
  public AgentID topic(AgentID aid, Enum<?> topic) {
    return agent.topic(aid, topic);
  }

  /**
   * Subscribes the gateway to receive all messages sent to the given topic.
   *
   * @param topic the topic to subscribe to.
   * @return true if the subscription is successful, false otherwise.
   */
  public boolean subscribe(AgentID topic) {
    return agent.subscribe(topic);
  }

  /**
   * Unsubscribes the gateway from a given topic.
   *
   * @param topic the topic to unsubscribe.
   * @return true if the unsubscription is successful, false otherwise.
   */
  public boolean unsubscribe(AgentID topic) {
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
    return container.agentForService(service);
  }

  /**
   * Finds an agent that provides a named service. If multiple agents are registered
   * to provide a given service, any of the agents' id may be returned.
   *
   * @param service the named service of interest.
   * @return an agent id for an agent that provides the service.
   */
  public AgentID agentForService(Enum<?> service) {
    return container.agentForService(service.getClass().getName()+"."+service.toString());
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(String service) {
    return container.agentsForService(service);
  }

  /**
   * Finds all agents that provides a named service.
   *
   * @param service the named service of interest.
   * @return an array of agent ids representing all agent that provide the service.
   */
  public AgentID[] agentsForService(Enum<?> service) {
    return container.agentsForService(service.getClass().getName()+"."+service.toString());
  }

  ////////////// Private methods

  @Override
  public void finalize() {
    shutdown();
  }

}

