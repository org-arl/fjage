/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.arl.fjage.Agent;
import org.arl.fjage.Container;
import org.arl.fjage.Message;
import org.arl.fjage.OneShotBehavior;
import org.arl.fjage.Platform;
import org.arl.fjage.RealTimePlatform;
import org.arl.fjage.param.NamedParameter;
import org.arl.fjage.param.Parameter;
import org.arl.fjage.param.ParameterMessageBehavior;
import org.arl.fjage.param.ParameterReq;
import org.arl.fjage.param.ParameterRsp;
import org.junit.Test;

/**
 * Tests that a parameter enum constant that is unknown to this version does not
 * abort deserialization of the whole message (issue #441).
 */
public class EnumTypeAdapterTest {

  public enum TestParam implements Parameter {
    alpha, beta
  }

  private static final String BETA = TestParam.class.getName().replace('$','.')+".beta";
  private static final String GAMMA = TestParam.class.getName().replace('$','.')+".gamma";

  /**
   * Serializes a request for alpha and beta, then renames beta to gamma, to simulate a
   * peer that was built against a version in which the parameter still existed.
   */
  private static String requestWithUnknownParam() {
    ParameterReq req = new ParameterReq();
    req.get(TestParam.alpha);
    req.get(TestParam.beta);
    JsonMessage jm = new JsonMessage();
    jm.action = Action.SEND;
    jm.message = req;
    String json = jm.toJson();
    assertTrue(json.contains(BETA));
    return json.replace(BETA, GAMMA);
  }

  @Test
  public void knownEnumConstantRoundTrips() {
    ParameterReq req = new ParameterReq();
    req.get(TestParam.alpha);
    JsonMessage jm = new JsonMessage();
    jm.action = Action.SEND;
    jm.message = req;
    JsonMessage rx = JsonMessage.fromJson(jm.toJson());
    assertNotNull(rx.message);
    List<ParameterReq.Entry> requests = ((ParameterReq)rx.message).requests();
    assertEquals(1, requests.size());
    assertEquals(TestParam.alpha, requests.get(0).param);
  }

  @Test
  public void unknownEnumConstantDoesNotAbortDeserialization() {
    JsonMessage rx = JsonMessage.fromJson(requestWithUnknownParam());
    assertNotNull(rx.message);
    assertTrue(rx.message instanceof ParameterReq);
    List<ParameterReq.Entry> requests = ((ParameterReq)rx.message).requests();
    assertEquals(2, requests.size());
    assertEquals(TestParam.alpha, requests.get(0).param);
    // the unknown constant degrades to a named parameter, keeping its qualified name so
    // that it cannot be mistaken for an unrelated parameter of the same short name
    assertTrue(requests.get(1).param instanceof NamedParameter);
    assertEquals(GAMMA, requests.get(1).param.toString());
  }

  @Test
  public void unknownEnumConstantStillYieldsParameterResponse() {
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ParamAgent server = new ParamAgent();
    ParamClient client = new ParamClient((ParameterReq)JsonMessage.fromJson(requestWithUnknownParam()).message);
    container.add("S", server);
    container.add("C", client);
    platform.start();
    platform.delay(2000);
    platform.shutdown();
    ParameterRsp rsp = client.rsp;
    assertNotNull("no response to a request naming an unknown parameter", rsp);
    assertEquals(42, rsp.get(TestParam.alpha));
    assertTrue(rsp.parameters().contains(TestParam.alpha));
    assertFalse(rsp.parameters().contains(new NamedParameter(GAMMA)));
  }

  // must be public, so that the parameter behavior can reflect on its public fields
  public static class ParamAgent extends Agent {
    public int alpha = 42;
    public int beta = 7;
    @Override
    public void init() {
      add(new ParameterMessageBehavior(TestParam.class));
    }
  }

  private static class ParamClient extends Agent {
    private final ParameterReq req;
    public volatile ParameterRsp rsp = null;
    ParamClient(ParameterReq req) {
      this.req = req;
    }
    @Override
    public void init() {
      add(new OneShotBehavior() {
        @Override
        public void action() {
          req.setRecipient(agent.agent("S"));
          Message m = agent.request(req, 1000);
          if (m instanceof ParameterRsp) rsp = (ParameterRsp)m;
        }
      });
    }
  }

}
