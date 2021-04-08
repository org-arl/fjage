/******************************************************************************

Copyright (c) 2021, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.auth;

import java.util.logging.Logger;
import org.arl.fjage.AgentID;
import org.arl.fjage.remote.JsonMessage;

/**
 * An abstract firewall class that allows all traffic to pass through once
 * authenticated. Authentication is implemented by a class that extends this
 * class by implemeting the {@link Firewall#authenticate(Connector,String)} method.
 */
public abstract class AbstractFirewall implements Firewall {

  protected Logger log = Logger.getLogger(getClass().getName());
  protected boolean auth = false;

  @Override
  public boolean permit(JsonMessage rq) {
    return auth;
  }

  @Override
  public boolean permit(AgentID aid) {
    return auth;
  }

}
