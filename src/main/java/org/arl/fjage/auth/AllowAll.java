/******************************************************************************

Copyright (c) 2021, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.auth;

import org.arl.fjage.AgentID;
import org.arl.fjage.remote.JsonMessage;

import java.util.function.Supplier;

/**
 * A permissive firewall that allows all traffic to pass through.
 */
public class AllowAll implements Firewall {

  /**
   * AllowAll supplier.
   */
  public static final Supplier<Firewall> SUPPLIER = AllowAll::new;

  @Override
  public boolean authenticate(String creds) {
    return true;
  }

  @Override
  public boolean permit(JsonMessage rq) {
    return true;
  }

  @Override
  public boolean permit(AgentID aid) {
    return true;
  }

  @Override
  public void signoff() {
    // do nothing
  }

}
