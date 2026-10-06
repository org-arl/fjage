/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import org.arl.fjage.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that JSON conversion works when called from code that lacks
 * suppressAccessChecks, such as a sandboxed script running under a
 * SecurityManager.
 */
public class JsonMessageSecurityTest {

  // message types used only here, so Gson builds their adapters during the test
  public static class OutMsg extends Message {
    private static final long serialVersionUID = 1L;
    private String secret = "out";
  }

  public static class InMsg extends Message {
    private static final long serialVersionUID = 1L;
    private String secret;
  }

  private static final AccessControlContext UNTRUSTED = new AccessControlContext(new ProtectionDomain[] {
    new ProtectionDomain(null, new Permissions())
  });

  private Policy policy;

  @Before
  public void setUp() {
    policy = Policy.getPolicy();
    Policy.setPolicy(new Policy() {
      @Override
      public boolean implies(ProtectionDomain domain, Permission permission) {
        return true;
      }
    });
    System.setSecurityManager(new SecurityManager());
  }

  @After
  public void tearDown() {
    System.setSecurityManager(null);
    Policy.setPolicy(policy);
  }

  // runs the action with a caller on the stack that has no permissions
  private static <T> T untrusted(PrivilegedAction<T> action) {
    return AccessController.doPrivileged(action, UNTRUSTED);
  }

  @Test
  public void toJsonFromUntrustedCaller() {
    JsonMessage jmsg = new JsonMessage();
    jmsg.action = Action.SEND;
    jmsg.message = new OutMsg();
    String json = untrusted(jmsg::toJson);
    assertTrue(json, json.contains("\"secret\":\"out\""));
  }

  @Test
  public void fromJsonFromUntrustedCaller() {
    String json = "{\"action\":\"send\",\"message\":{\"clazz\":\"" + InMsg.class.getName()
      + "\",\"data\":{\"msgID\":\"1\",\"perf\":\"INFORM\",\"secret\":\"in\"}}}";
    JsonMessage jmsg = untrusted(() -> JsonMessage.fromJson(json));
    assertEquals("in", ((InMsg) jmsg.message).secret);
  }

}
