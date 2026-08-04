/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.observer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.arl.fjage.AgentID;
import org.arl.fjage.GenericMessage;
import org.arl.fjage.Message;
import org.arl.fjage.Performative;
import org.arl.fjage.param.ParameterReq;
import org.junit.Test;

public class ObserverFilterTest {

  private Message msg(String from, String to, boolean topic) {
    Message m = new GenericMessage(new AgentID(to, topic), Performative.INFORM);
    m.setSender(new AgentID(from));
    return m;
  }

  @Test
  public void testAllowsEverythingByDefault() {
    ObserverFilter f = new ObserverFilter().compile();
    assertTrue(f.matches(msg("a", "b", false)));
    assertTrue(f.matches(msg("a", "chatter", true)));
  }

  @Test
  public void testClazzInclude() {
    ObserverFilter f = new ObserverFilter();
    f.clazz = "GenericMessage";
    f.compile();
    assertTrue(f.matches(msg("a", "b", false)));
    assertFalse(f.matches(new ParameterReq(new AgentID("b"))));
  }

  @Test
  public void testClazzExclude() {
    ObserverFilter f = new ObserverFilter();
    f.excludeClazz = "Parameter";
    f.compile();
    assertTrue(f.matches(msg("a", "b", false)));
    assertFalse(f.matches(new ParameterReq(new AgentID("b"))));
  }

  @Test
  public void testExcludeEndpointMatchesEitherEnd() {
    ObserverFilter f = new ObserverFilter().exclude("b");
    assertFalse(f.matches(msg("a", "b", false)));
    assertFalse(f.matches(msg("b", "a", false)));
    assertTrue(f.matches(msg("a", "c", false)));
  }

  @Test
  public void testExcludeTopicNeedsHashPrefix() {
    ObserverFilter f = new ObserverFilter().exclude("#chatter");
    assertFalse(f.matches(msg("a", "chatter", true)));
    assertTrue(f.matches(msg("a", "chatter", false)));    // an agent named "chatter"
  }

  @Test
  public void testBadRegexIsIgnoredNotThrown() {
    ObserverFilter f = new ObserverFilter();
    f.clazz = "([unclosed";
    f.excludeClazz = "*bad";
    f.compile();
    assertTrue(f.matches(msg("a", "b", false)));          // both rules ignored
  }

  @Test
  public void testCompilesLazilyIfNotCompiled() {
    ObserverFilter f = new ObserverFilter();
    f.excludeClazz = "Generic";
    assertFalse(f.matches(msg("a", "b", false)));         // compile() not called
  }

  @Test
  public void testExcludeNullClearsRules() {
    ObserverFilter f = new ObserverFilter().exclude("b").exclude((String[])null);
    assertTrue(f.matches(msg("a", "b", false)));
  }

}
