/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import org.arl.fjage.Message;

/**
 * An interface for a client interested in monitoring messages.
 *
 * @author  Mandar Chitre
 */
public interface MessageListener {

  /**
   * This method is called for each message to be conveyed to the listener.
   * <p>
   * If this method returns true, the message is assumed to be consumed
   * and is immediately discarded.
   *
   * @param msg received message.
   * @return true if the message is consumed, false otherwise.
   */
  public boolean onReceive(Message msg);

}
