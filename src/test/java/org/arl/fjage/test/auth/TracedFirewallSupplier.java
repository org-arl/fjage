/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test.auth;

import org.arl.fjage.AgentID;
import org.arl.fjage.auth.Firewall;
import org.arl.fjage.remote.JsonMessage;

import java.util.function.Supplier;
import java.util.logging.Logger;

public class TracedFirewallSupplier
    implements Supplier<Firewall> {

  private final Supplier<Firewall> fwSupplier;
  private final Logger log = Logger.getLogger(getClass().getName());

  public TracedFirewallSupplier(Supplier<Firewall> fwSupplier) {
    super();

    this.fwSupplier = fwSupplier;
  }

  @Override
  public Firewall get() {
    return new TracedFirewall(fwSupplier.get());
  }

  private class TracedFirewall
      implements Firewall {

    private final Firewall fw;

    public TracedFirewall(Firewall fw) {
      super();

      this.fw = fw;
    }

    @Override
    public boolean authenticate(String creds) {
      final boolean auth = fw.authenticate(creds);
      log.info(String.format("[%s] authenticate(%s)=%s", fw, creds, auth));
      return auth;
    }

    @Override
    public boolean permit(JsonMessage rq) {
      final boolean permitted = fw.permit(rq);
      log.info(String.format("[%s] permit(%s)=%s", fw, rq.toJson(), permitted));
      return permitted;
    }

    @Override
    public boolean permit(AgentID aid) {
      final boolean permitted = fw.permit(aid);
      log.info(String.format("[%s] permit(%s)=%s", fw, aid, permitted));
      return permitted;
    }

    @Override
    public void signoff() {
      fw.signoff();
      log.info(String.format("[%s] signoff()", fw));
    }
  }
}
