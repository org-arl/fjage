/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import org.arl.fjage.*;
import org.arl.fjage.auth.*;
import org.arl.fjage.remote.Action;
import org.arl.fjage.remote.JsonMessage;
import org.arl.fjage.test.auth.SimpleFirewallSupplier;
import org.junit.*;
import org.junit.rules.TestName;

import java.util.logging.Logger;

public class SimpleFirewallTests {

  private static final String AGENT1_NAME = "agent1";
  private static final String AGENT2_NAME = "agent2";
  private static final String AGENT3_NAME = "agent3";
  private static final AgentID AGENT1 = new AgentID(AGENT1_NAME);
  private static final AgentID AGENT2 = new AgentID(AGENT2_NAME);
  private static final AgentID AGENT3 = new AgentID(AGENT3_NAME);

  @Rule
  public TestName testName = new TestName();
  private final Logger log = Logger.getLogger(getClass().getName());
  private final SimpleFirewallSupplier simpleFirewallSupplier = new SimpleFirewallSupplier()
      .addUserConfiguration("credentials1", userConfiguration -> userConfiguration
          .allowedAgentNames("agent1")
      )
      .addUserConfiguration("credentials2", userConfiguration -> userConfiguration
          .allowedAgentNames("agent2")
      );

  @Before
  public void beforeTesting() {
    LogFormatter.install(null);
    log.info(String.format("==== BEGIN %s ====", testName.getMethodName()));
  }

  @After
  public void afterTesting() {
    log.info(String.format("==== END %s ====", testName.getMethodName()));
  }

  @Test
  public void distinctInstances() {
    final Firewall fw1 = simpleFirewallSupplier.get();
    final Firewall fw2 = simpleFirewallSupplier.get();
    final Firewall fw3 = simpleFirewallSupplier.get();
    Assert.assertNotEquals(fw1, fw2);
    Assert.assertNotEquals(fw1, fw3);
    Assert.assertNotEquals(fw2, fw3);
  }

  @Test
  public void authentication1() {
    final Firewall fw = simpleFirewallSupplier.get();

    // fw is not authenticated
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT3)));

    // authenticate fw
    Assert.assertEquals(true, fw.authenticate("credentials1"));
    Assert.assertEquals(true, fw.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT3)));
  }

  @Test
  public void authentication2() {
    final Firewall fw = simpleFirewallSupplier.get();

    // fw is not authenticated
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT3)));

    // authenticate fw
    Assert.assertEquals(true, fw.authenticate("credentials2"));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(true, fw.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT3)));
  }

  @Test
  public void badAuthentication() {
    final Firewall fw = simpleFirewallSupplier.get();

    // fw is not authenticated
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT3)));

    // authenticate fw (with unknown credentials)
    Assert.assertEquals(false, fw.authenticate("credentials0"));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw.permit(newSendJsonMessage(AGENT3)));
  }

  @Test
  public void distinctStates() {
    final Firewall fw1 = simpleFirewallSupplier.get();
    Assert.assertEquals(true, fw1.authenticate("credentials1"));

    final Firewall fw2 = simpleFirewallSupplier.get();
    Assert.assertEquals(true, fw2.authenticate("credentials2"));

    final Firewall fw3 = simpleFirewallSupplier.get();
    Assert.assertEquals(false, fw3.authenticate("credentials0"));

    Assert.assertEquals(true, fw1.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(false, fw1.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw1.permit(newSendJsonMessage(AGENT3)));

    Assert.assertEquals(false, fw2.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(true, fw2.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw2.permit(newSendJsonMessage(AGENT3)));

    Assert.assertEquals(false, fw3.permit(newSendJsonMessage(AGENT1)));
    Assert.assertEquals(false, fw3.permit(newSendJsonMessage(AGENT2)));
    Assert.assertEquals(false, fw3.permit(newSendJsonMessage(AGENT3)));
  }

  private JsonMessage newSendJsonMessage(AgentID recipient) {
    final JsonMessage jsonMessage = new JsonMessage();
    jsonMessage.action = Action.SEND;
    jsonMessage.message = new Message(recipient);
    return jsonMessage;
  }
}
