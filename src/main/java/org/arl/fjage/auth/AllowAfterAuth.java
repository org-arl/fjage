/******************************************************************************

Copyright (c) 2021, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.auth;

import java.util.function.Supplier;

/**
 * A permissive firewall that allows all traffic to pass through, but requires
 * an authentication message to be first sent with any credentials.
 * <p>
 * This is primarily useful for testing.
 */
public class AllowAfterAuth extends AbstractFirewall {

  /**
   * AllowAfterAuth supplier.
   */
  public static final Supplier<Firewall> SUPPLIER = AllowAfterAuth::new;

  @Override
  public boolean authenticate(String creds) {
    if (creds == null) auth = false;
    else {
      log.fine("Authentication successful ["+creds+"]");
      auth = true;
    }
    return auth;
  }

  @Override
  public void signoff() {
    // do nothing
  }

}
