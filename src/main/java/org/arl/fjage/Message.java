/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.io.Serializable;
import java.util.UUID;

/**
 * Base class for messages transmitted by one agent to another. This class provides
 * the basic attributes of messages and is typically extended by application-specific
 * message classes. To ensure that messages can be sent between agents running
 * on remote containers, all attributes of a message must be serializable.
 *
 * @author  Mandar Chitre
 */
public class Message implements Serializable {

  private static final long serialVersionUID = 1L;

  //////////// Private attributes

  protected String msgID = UUID.randomUUID().toString();
  protected Performative perf;
  protected AgentID recipient;
  protected AgentID sender = null;
  protected String inReplyTo = null;

  //////////// Interface methods

  /**
   * Creates an empty message.
   */
  public Message() {
    perf = null;
    recipient = null;
  }

  /**
   * Creates a new message.
   *
   * @param perf performative.
   */
  public Message(Performative perf) {
    this.perf = perf;
    recipient = null;
  }

  /**
   * Creates a new message.
   *
   * @param recipient agent id of recipient agent or topic.
   */
  public Message(AgentID recipient) {
    perf = null;
    this.recipient = recipient;
  }

  /**
   * Creates a new message.
   *
   * @param recipient agent id of recipient agent or topic.
   * @param perf performative.
   */
  public Message(AgentID recipient, Performative perf) {
    this.perf = perf;
    this.recipient = recipient;
  }

  /**
   * Creates a response message.
   *
   * @param inReplyTo message to which this response corresponds to.
   */
  public Message(Message inReplyTo) {
    perf = null;
    this.recipient = inReplyTo.sender;
    this.inReplyTo = inReplyTo.msgID;
  }

  /**
   * Creates a response message.
   *
   * @param inReplyTo message to which this response corresponds to.
   * @param perf performative.
   */
  public Message(Message inReplyTo, Performative perf) {
    this.perf = perf;
    this.recipient = inReplyTo.sender;
    this.inReplyTo = inReplyTo.msgID;
  }

  /**
   * Sets the performative for this message.
   *
   * @param perf performative.
   */
  public void setPerformative(Performative perf) {
    this.perf = perf;
  }

  /**
   * Gets the performative for this message.
   *
   * @return performative.
   */
  public Performative getPerformative() {
    return perf;
  }

  /**
   * Sets the recipient of this message.
   *
   * @param aid recipient agent id.
   */
  public void setRecipient(AgentID aid) {
    recipient = aid;
  }

  /**
   * Gets the recipient of this message.
   *
   * @return recipient agent id.
   */
  public AgentID getRecipient() {
    return recipient;
  }

  /**
   * Sets the sender of this message.
   *
   * @param aid sender agent id.
   */
  public void setSender(AgentID aid) {
    sender = aid;
  }

  /**
   * Gets the sender of this message.
   *
   * @return sender agent id.
   */
  public AgentID getSender() {
    return sender;
  }

  /**
   * Sets the unique identifier for this message.
   *
   * @param id message identifier.
   */
  public void setMessageID(String id) {
    msgID = id;
  }

  /**
   * Gets the unique identifier for this message.
   *
   * @return message identifier.
   */
  public String getMessageID() {
    return msgID;
  }

  /**
   * Sets the message id of the associated request message.
   *
   * @param id message id of request message.
   */
  public void setInReplyTo(String id) {
    inReplyTo = id;
  }

  /**
   * Gets the message id of the associated request message.
   *
   * @return message id of request message, null if this is not a response.
   */
  public String getInReplyTo() {
    return inReplyTo;
  }

  /////////////// Standard Java methods to customize

  /**
   * Gets a string representation of the message.
   *
   * @return string representation.
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    String p = perf != null ? perf.toString() : "MESSAGE";
    Class<?> cls = getClass();
    if (cls.equals(Message.class)) return p;
    return p + ": " + cls.getSimpleName();
  }

}
