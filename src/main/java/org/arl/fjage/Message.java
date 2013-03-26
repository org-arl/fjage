/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.io.Serializable;

/**
 * Base class for messages transmitted by one agent to another. This class provides
 * the basic attributes of messages and is typically extended by application-specific
 * message classes. To ensure that messages can be sent between agents running
 * on remote containers, all attributes of a message must be serializable.
 *
 * @author  Mandar Chitre
 * @version $Revision: 9127 $, $Date: 2012-06-17 16:34:15 +0800 (Sun, 17 Jun 2012) $
 */
public class Message implements Serializable {

  private static final long serialVersionUID = 1L;

  //////////// Private attributes

  private static int count = 0;

  protected String msgID = getClass().getName()+":"+hashCode()+":"+(++count);
  protected Performative perf;
  protected AgentID recepient;
  protected AgentID sender = null;
  protected String inReplyTo = null;

  //////////// Interface methods

  /**
   * Creates an empty message.
   */
  public Message() {
    perf = null;
    recepient = null;
  }

  /**
   * Creates a new message.
   *
   * @param recepient agent id of recipient agent or topic.
   * @param perf performative.
   */
  public Message(AgentID recepient, Performative perf) {
    this.perf = perf;
    this.recepient = recepient;
  }

  /**
   * Creates a response message.
   *
   * @param inReplyTo message to which this response corresponds to.
   * @param perf performative.
   */
  public Message(Message inReplyTo, Performative perf) {
    this.perf = perf;
    this.recepient = inReplyTo.sender;
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
    recepient = aid;
  }

  /**
   * Gets the recipient of this message.
   *
   * @return recipient agent id.
   */
  public AgentID getRecipient() {
    return recepient;
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
   * Gets the unique identifier for this message.
   *
   * @return message identifier.
   */
  public String getMessageID() {
    return msgID;
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
    Class<?> cls = getClass();
    if (cls.equals(Message.class)) return perf.toString();
    return perf.toString() + ": " + cls.getSimpleName();
  }

  //////////// Package private methods

  void setSender(AgentID aid) {
    sender = aid;
  }

}

