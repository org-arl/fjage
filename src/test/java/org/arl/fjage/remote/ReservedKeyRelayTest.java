/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import org.arl.fjage.*;
import org.junit.Test;

public class ReservedKeyRelayTest {

  private static GenericMessage reserved(AgentID recipient) {
    GenericMessage msg = new GenericMessage(recipient, Performative.INFORM);
    msg.put("sentAt", 1);
    return msg;
  }

  @Test(timeout = 15000)
  public void relayOfReservedKeyIsSkipped() throws Exception {
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    platform.start();
    AgentID agent = new AgentID("nowhere");
    AgentID topic = new AgentID("events", true);
    try (Gateway gw = new Gateway("localhost", master.getPort())) {
      Container slave = gw.getContainer();
      assertFalse(master.send(reserved(agent)));
      assertTrue(master.send(reserved(topic)));   // delivered locally
      assertFalse(slave.send(reserved(agent)));
      assertFalse(slave.send(reserved(topic)));
    } finally {
      platform.shutdown();
    }
  }

}
