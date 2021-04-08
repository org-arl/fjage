/******************************************************************************

Copyright (c) 2021, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.auth;

import org.arl.fjage.AgentID;
import org.arl.fjage.connectors.Connector;
import org.arl.fjage.remote.JsonMessage;

/**
 * Firewall interface for API access to fjage master containers.
 */
public interface Firewall {

  /**
   * Authenticates peer using specified credentials.
   * <p>
   * When a firewall is first bound to a connection handler, this method is
   * called with null credentials to bind a connector to the firewall. The
   * method is also called with null credentials when a connection is closed.
   *
   * @param conn connection to peer.
   * @param creds credentials, or null if logging out.
   * @return true if authenticated successfully, false otherwise.
   */
  public boolean authenticate(Connector conn, String creds);

  /**
   * Checks whether a JSON message can be accepted over this connection.
   *
   * @param rq incoming JSON request.
   * @return true to accept, false to reject.
   */
  public boolean permit(JsonMessage rq);

  /**
   * Checks whether a message intended for the specified agent/topic may be sent
   * over this connection.
   *
   * @param aid recipient agent/topic for the message.
   * @return true to permit, false to reject.
   */
  public boolean permit(AgentID aid);

}
