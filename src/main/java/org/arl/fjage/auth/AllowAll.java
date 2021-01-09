/******************************************************************************

Copyright (c) 2021, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.auth;

import org.arl.fjage.connectors.Connector;

/**
 * A permissive firewall that allows all traffic to pass through.
 */
public class AllowAll extends AbstractFirewall {

  @Override
  public boolean authenticate(Connector conn, String creds) {
    if (creds != null) log.fine("Authentication successful [AllowAll]");
    auth = true;
    return auth;
  }

}
