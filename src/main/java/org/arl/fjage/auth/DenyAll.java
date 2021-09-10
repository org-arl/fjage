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
 * A reluctant firewall that denies all traffic to pass through.
 */
public class DenyAll implements Firewall {

  /**
   * DenyAll supplier.
   */
  public static final Supplier<Firewall> SUPPLIER = DenyAll::new;

  @Override
  public boolean authenticate(String creds) {
    return false;
  }

  @Override
  public boolean permit(JsonMessage rq) {
    return false;
  }

  @Override
  public boolean permit(AgentID aid) {
    return false;
  }

  @Override
  public void signoff() {
    // do nothing
  }

}
