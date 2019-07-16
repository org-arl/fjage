/******************************************************************************

Copyright (c) 2019, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * Interface implemented by classes that provide basic messaging services.
 */
public interface Messenger {

  /**
   * Sends a message to the recipient indicated in the message. The recipient
   * may be either an agent or a topic.
   *
   * @param m message to be sent.
   * @return true if message accepted for delivery, false on failure.
   */
  public boolean send(final Message m);

  /**
   * Returns a message received by the gateway. This method is non-blocking.
   *
   * @return received message, null if none available.
   */
  public Message receive();

  /**
   * Returns a message received by the agent. This method blocks until timeout if no
   * message available.
   *
   * @param timeout timeout in milliseconds.
   * @return received message, null on timeout.
   */
  public Message receive(long timeout);

  /**
   * Returns a message of a given class received by the gateway. This method is non-blocking.
   *
   * @param cls the class of the message of interest.
   * @return received message of the given class, null if none available.
   */
  public Message receive(final Class<?> cls);

  /**
   * Returns a message of a given class received by the gateway. This method blocks until
   * timeout if no message available.
   *
   * @param cls the class of the message of interest.
   * @param timeout timeout in milliseconds.
   * @return received message of the given class, null on timeout.
   */
  public Message receive(final Class<?> cls, long timeout);

  /**
   * Returns a response message received by the gateway. This method is non-blocking.
   *
   * @param m original message to which a response is expected.
   * @return received response message, null if none available.
   */
  public Message receive(final Message m);

  /**
   * Returns a response message received by the gateway. This method blocks until
   * timeout if no message available.
   *
   * @param m original message to which a response is expected.
   * @param timeout timeout in milliseconds.
   * @return received response message, null on timeout.
   */
  public Message receive(final Message m, long timeout);

  /**
   * Returns a message received by the gateway and matching the given filter.
   * This method is non-blocking.
   *
   * @param filter message filter.
   * @return received message matching the filter, null on timeout.
   */
  public Message receive(final MessageFilter filter);

  /**
   * Returns a message received by the gateway and matching the given filter.
   * This method blocks until timeout if no message available.
   *
   * @param filter message filter.
   * @param timeout timeout in milliseconds.
   * @return received message matching the filter, null on timeout.
   */
  public Message receive(final MessageFilter filter, final long timeout);

  /**
   * Sends a request and waits for a response. This method blocks until timeout
   * if no response is received.
   *
   * @param msg message to send.
   * @param timeout timeout in milliseconds.
   * @return received response message, null on timeout.
   */
  public Message request(final Message msg, long timeout);

  /**
   * Sends a request and waits for a response. This method blocks until a default
   * timeout, if no response is received.
   *
   * @param msg message to send.
   * @return received response message, null on timeout.
   */
  public Message request(final Message msg);

}
