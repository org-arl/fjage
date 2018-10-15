/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

/**
 * Interface to be implemented by clients wishing to listen to incoming
 * TCP connections.
 */
public interface ConnectionListener {
  public void connected(TcpConnector connector);
}
