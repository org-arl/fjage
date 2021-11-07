/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.io.Serializable;
import org.arl.fjage.param.*;

/**
 * An identifier for an agent or a topic.
 *
 * @author  Mandar Chitre
 */
public class AgentID implements Serializable, Comparable<AgentID> {

  private static final long serialVersionUID = 1L;

  /////////////// Private attributes

  private String name;
  private boolean isTopic;
  private transient Messenger owner;
  private transient String type;

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
  public AgentID(String name, Messenger owner) {
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
  public AgentID(String name, boolean isTopic, Messenger owner) {
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
  public AgentID(AgentID aid, Messenger owner) {
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
  public Messenger getOwner() {
    return owner;
  }

  /**
   * Gets the type of the agent, if available. For Java or Groovy agents,
   * the type is the fully qualified class name of the agent.
   *
   * @return type of the agent if available, null otherwise.
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the type of the agent. For Java or Groovy agents,
   * the type is the fully qualified class name of the agent.
   *
   * @param type type of the agent.
   */
  public void setType(String type) {
    this.type = type;
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
   * Sends a parameter request to the agent represented by this id and
   * returns the parameter value from the agent, or null if unavailable.
   *
   * @param param parameter name or enum.
   */
  public Object get(Parameter param) {
    return get(param, -1);
  }

  /**
   * Sends a parameter request to the agent represented by this id and
   * returns the parameter value from the agent, or null if unavailable.
   *
   * @param param parameter name or enum.
   * @param ndx index of parameter (-1 for non-indexed parameters).
   */
  public Object get(Parameter param, int ndx) {
    if (param == null) return null;
    ParameterReq req = new ParameterReq(this).get(param);
    if (ndx >= 0) req.setIndex(ndx);
    Message rsp = request(req);
    if (rsp == null) return null;
    if (!(rsp instanceof ParameterRsp)) return null;
    return ((ParameterRsp)rsp).get(param);
  }

  /**
   * Sends a parameter request to the agent represented by this id to
   * change the parameter value from the agent.
   *
   * @param param parameter name or enum.
   * @param value value of the parameter.
   * @return new value of the parameter, or null if unavailable/failed.
   */
  public Object set(Parameter param, Object value) {
    return set(param, value, -1);
  }

  /**
   * Sends a parameter request to the agent represented by this id to
   * change the parameter value from the agent.
   *
   * @param param parameter name or enum.
   * @param value value of the parameter.
   * @param ndx index of parameter (-1 for non-indexed parameters).
   * @return new value of the parameter, or null if unavailable/failed.
   */
  public Object set(Parameter param, Object value, int ndx) {
    if (param == null) return null;
    ParameterReq req = new ParameterReq(this).set(param, value);
    if (ndx >= 0) req.setIndex(ndx);
    Message rsp = request(req);
    if (rsp == null) return null;
    if (!(rsp instanceof ParameterRsp)) return null;
    return ((ParameterRsp)rsp).get(param);
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
    return isTopic == a.isTopic;
  }

  /**
   * Compares two agent ids to determine an ordering. If the agent ids are identical,
   * this method returns 0. If they are not identical, the ordering is deterministic,
   * but arbitrary.
   *
   * @param aid agent id to compare to.
   * @return 0 if equal, +1 if larger and -1 if smaller.
   */
  @Override
  public int compareTo(AgentID aid) {
    if (equals(aid)) return 0;
    if (hashCode() < aid.hashCode()) return -1;
    return 1;
  }

}

