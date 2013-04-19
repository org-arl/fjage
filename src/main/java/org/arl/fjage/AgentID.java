/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.io.Serializable;

/**
 * An identifier for an agent or a topic.
 *
 * @author  Mandar Chitre
 */
public class AgentID implements Serializable {

  private static final long serialVersionUID = 1L;

  /////////////// Private attributes

  private String name;
  private boolean isTopic;
  private transient Agent owner;

  /////////////// Interface methods

  /**
   * Constructor to create an agent id given the agent's name.
   *
   * @param name name of the agent.
   */
  public AgentID(String name) {
    this.name = name;
    isTopic = false;
    owner = null;
  }

  /**
   * Constructor to create an agent id for an agent or a topic.
   *
   * @param name name of the agent or topic.
   * @param isTopic true if the agent id is to represent a topic,
   *                false if it is to represent an agent.
   */
  public AgentID(String name, boolean isTopic) {
    this.name = name;
    this.isTopic = isTopic;
    owner = null;
  }

  /**
   * Constructor to create an owned agent id given the agent's name.
   *
   * @param name name of the agent.
   * @param owner owner agent.
   */
  public AgentID(String name, Agent owner) {
    this.name = name;
    isTopic = false;
    this.owner = owner;
  }

  /**
   * Constructor to create an owned agent id for an agent or a topic.
   *
   * @param name name of the agent or topic.
   * @param isTopic true if the agent id is to represent a topic,
   *                false if it is to represent an agent.
   * @param owner owner agent.
   */
  public AgentID(String name, boolean isTopic, Agent owner) {
    this.name = name;
    this.isTopic = isTopic;
    this.owner = owner;
  }

  /**
   * Constructor to create an owned agent id from another agent id.
   *
   * @param aid agent id to inherit.
   * @param owner owner agent.
   */
  public AgentID(AgentID aid, Agent owner) {
    this.name = aid.name;
    this.isTopic = aid.isTopic;
    this.owner = owner;
  }

  /**
   * Returns true if the agent id represents a topic.
   *
   * @return true if the agent id represents a topic,
   *         false if it represents an agent.
   */
  public boolean isTopic() {
    return isTopic;
  }

  /**
   * Gets the name of the agent or topic.
   *
   * @return name of agent or topic.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the owner of the agent id. This is usually the agent that
   * created the agent id, and will be used to send messages to this
   * agent id.
   * 
   * @return owner agent.
   */
  public Agent getOwner() {
    return owner;
  }
  
  /////////////// Proxy interface methods

  /**
   * Sends a message to the agent represented by this id.
   * 
   * @param msg message to send.
   */
  public void send(Message msg) {
    msg.setRecipient(this);
    owner.send(msg);
  }

  /**
   * Sends a request to the agent represented by this id and waits for
   * a return message for 1 second.
   * 
   * @param msg request to send.
   * @return response.
   */
  public Message request(Message msg) {
    msg.setRecipient(this);
    return owner.request(msg, 1000);
  }
  
  /**
   * Sends a request to the agent represented by this id and waits for
   * a return message for a specified timeout.
   * 
   * @param msg request to send.
   * @param timeout timeout in milliseconds.
   * @return response.
   */
  public Message request(Message msg, long timeout) {
    msg.setRecipient(this);
    return owner.request(msg, timeout);
  }

  /**
   * Sends a request to the agent represented by this id and waits for
   * a return message for 1 second. This method is used in Groovy agents
   * for a simpler notation of the form:
   * <pre>
   * aid << request
   * </pre>
   * 
   * @param msg request to send.
   * @return response.
   */
  public Message leftShift(Message msg) {
    return request(msg);
  }

  /////////////// Standard Java methods to customize

  /**
   * Gets a string representation of the agent id.
   *
   * @return string representation of the agent id.
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * Computes a hashcode for the agent id. If two agent ids are equal, their
   * hashcodes are equal.
   *
   * @return the hashcode.
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return ((isTopic?"!":"[")+name).hashCode();
  }

  /**
   * Compares this agent id with another object. Two agent ids are considered
   * to be equal if both represent the same type of id, i.e. agent or topic,
   * and the names are the same. An agent id is considered to be equal to a
   * string if its name matches the string. An agent id does not match any
   * other type of object.
   *
   * @return true if the object is considered equal to the agent id, false otherwise.
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof String) return name.equals(obj);
    if (!(obj instanceof AgentID)) return false;
    AgentID a = (AgentID)obj;
    if (!name.equals(a.name)) return false;
    if (isTopic != a.isTopic) return false;
    return true;
  }

}

