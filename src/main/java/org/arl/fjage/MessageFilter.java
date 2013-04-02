/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * An interface for a message filter. A message filter allows selection
 * of a subset of messages from a set of messages. Message filters are
 * typically used to select messages meeting a certain criteria from the
 * incoming message queue.
 *
 * @author  Mandar Chitre
 */
public interface MessageFilter {

  /**
   * Returns true if a message matches the criteria, false otherwise. An
   * implementing class must override this method to provide the appropriate
   * selection criteria for the message filter.
   *
   * @param m message to check.
   * @return true if the message matches the criteria, false otherwise.
   */
  public boolean matches(Message m);

}

