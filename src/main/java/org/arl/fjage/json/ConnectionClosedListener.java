/******************************************************************************

Copyright (c) 2015, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.json;

/**
 * Listener interface for closed TCP connections.
 */
interface ConnectionClosedListener {
  public void connectionClosed(ConnectionHandler handler);
}
