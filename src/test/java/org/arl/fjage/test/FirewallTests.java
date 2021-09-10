/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import org.arl.fjage.*;
import org.arl.fjage.auth.*;
import org.arl.fjage.param.Parameter;
import org.arl.fjage.remote.Gateway;
import org.arl.fjage.remote.MasterContainer;
import org.arl.fjage.test.auth.SimpleFirewallSupplier;
import org.arl.fjage.test.auth.TracedFirewallSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class FirewallTests {

  @Rule
  public TestName testName = new TestName();
  private final Logger log = Logger.getLogger(getClass().getName());

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
  public void testFirewall1() throws IOException {
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, AllowAll.SUPPLIER);
    ServerAgent server = new ServerAgent();
    master.add("S", server);
    platform.start();
    Gateway gw = new Gateway("localhost", master.getPort());
    AgentID s = gw.agentForService("server");
    assertNotNull(s);
    Message req = new RequestMessage(server.getAgentID());
    Message rsp = gw.request(req, 1000);
    assertNotNull(rsp);
    gw.close();
    platform.shutdown();
    assertEquals(1, server.requests);
  }

  @Test
  public void testFirewall2() throws IOException {
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, DenyAll.SUPPLIER);
    ServerAgent server = new ServerAgent();
    master.add("S", server);
    platform.start();
    Gateway gw = new Gateway("localhost", master.getPort());
    try {
      gw.agentForService("server");
      fail("Should have thrown AuthFailureException");
    } catch (AuthFailureException ex) {
      // all good
    }
    Message req = new RequestMessage(server.getAgentID());
    try {
      gw.request(req, 1000);
      fail("Should have thrown AuthFailureException");
    } catch (AuthFailureException ex) {
      // all good
    }
    gw.close();
    platform.shutdown();
    assertEquals(0, server.requests);
  }

  @Test
  public void testFirewall3() throws IOException {
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, AllowAfterAuth.SUPPLIER);
    ServerAgent server = new ServerAgent();
    master.add("S", server);
    platform.start();
    while (!master.isRunning()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        // ignore
      }
    }
    Gateway gw = new Gateway("localhost", master.getPort());
    try {
      gw.agentForService("server");
      fail("Should have thrown AuthFailureException");
    } catch (AuthFailureException ex) {
      // all good
    }
    gw.authenticate("somecreds");
    AgentID s = gw.agentForService("server");
    assertNotNull(s);
    Message req = new RequestMessage(server.getAgentID());
    Message rsp = gw.request(req, 1000);
    assertNotNull(rsp);
    gw.close();
    platform.shutdown();
    assertEquals(1, server.requests);
  }

  @Test
  public void testFirewall4() throws IOException {
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, AllowAfterAuth.SUPPLIER);
    platform.start();
    Gateway gw = new Gateway("localhost", master.getPort());
    gw.subscribe(new AgentID("test1", true));
    master.send(new NuisanceMessage(new AgentID("test1", true)));
    Message msg = gw.receive(1000);
    assertNull(msg);
    gw.authenticate("somecreds");
    gw.subscribe(new AgentID("test1", true));
    master.send(new NuisanceMessage(new AgentID("test1", true)));
    msg = gw.receive(1000);
    assertNotNull(msg);
    assertSame(msg.getClass(), NuisanceMessage.class);
    gw.close();
    platform.shutdown();
  }

  @Test
  public void testSimpleFirewall1() throws IOException {
    final SimpleFirewallSupplier simpleFirewallSupplier = new SimpleFirewallSupplier()
        .addUserConfiguration("somecreds", userConfiguration -> userConfiguration
            .allowedServiceNames("server")
            .allowedAgentNames("server1")
            .allowedTopicNames("server1__ntf")
        );
    final Supplier<Firewall> fwSupplier = new TracedFirewallSupplier(simpleFirewallSupplier);

    final Platform platform = new RealTimePlatform();
    final MasterContainer master = new MasterContainer(platform, fwSupplier);

    final ServerAgent serverAgent1 = new ServerAgent();
    master.add("server1", serverAgent1);

    final ServerAgent2 serverAgent2 = new ServerAgent2();
    master.add("server2", serverAgent2);

    platform.start();

    while (!master.isRunning()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        // ignore
      }
    }

    final Gateway gw = new Gateway("localhost", master.getPort());

    try {
      gw.agentForService("server");
      fail("Should have thrown AuthFailureException");
    } catch (AuthFailureException ex) {
      // all good
    }

    gw.authenticate("somecreds");

    {
      final AgentID serverAgentId = gw.agentForService("server");
      assertNotNull(serverAgentId);
      gw.subscribe(gw.topic(serverAgentId));
      final Message req = new RequestMessage(serverAgentId);
      final Message rsp = gw.request(req, 1000);
      assertNotNull(rsp);
    }

    try {
      final AgentID server2AgentId = gw.agentForService("server2");
      fail("Should have thrown AuthFailureException");
    } catch (AuthFailureException ex) {
      // all good
    }

    {
      final Message msg = gw.receive(1000);
      assertNotNull(msg);
    }

    while (!master.isRunning()) {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException ex) {
        // ignore
      }
    }

    gw.close();
    platform.shutdown();
    assertEquals(1, serverAgent1.requests);
  }

  private static class RequestMessage extends Message {
    private static final long serialVersionUID = 1L;
    public int x;

    public RequestMessage(AgentID recipient) {
      super(recipient, Performative.REQUEST);
    }
  }

  private static class ResponseMessage extends Message {
    private static final long serialVersionUID = 1L;
    public int x, y;

    public ResponseMessage(Message request) {
      super(request, Performative.INFORM);
    }
  }

  private static class NuisanceMessage extends Message {
    private static final long serialVersionUID = 1L;

    public NuisanceMessage(AgentID recipient) {
      super(recipient, Performative.INFORM);
    }
  }

  public enum Params implements Parameter {
    x, y, s
  }

  private static class ServerAgent extends Agent {
    public int requests = 0, nuisance = 0;

    @Override
    public void init() {
      register("server");
      subscribe(topic("noise"));
      add(new MessageBehavior(RequestMessage.class) {
        @Override
        public void onReceive(Message msg) {
          requests++;
          RequestMessage req = (RequestMessage) msg;
          ResponseMessage rsp = new ResponseMessage(req);
          rsp.x = req.x;
          rsp.y = 2 * req.x + 1;
          agent.send(rsp);
        }
      });
      add(new MessageBehavior(NuisanceMessage.class) {
        @Override
        public void onReceive(Message msg) {
          nuisance++;
        }
      });
      final AgentID topic = topic();
      add(new TickerBehavior(1000, () -> send(new NuisanceMessage(topic))));
    }
  }

  private static class ServerAgent2 extends Agent {
    public int requests = 0, nuisance = 0;

    @Override
    public void init() {
      register("server2");
      subscribe(topic("noise"));
      add(new MessageBehavior(RequestMessage.class) {
        @Override
        public void onReceive(Message msg) {
          requests++;
          RequestMessage req = (RequestMessage) msg;
          ResponseMessage rsp = new ResponseMessage(req);
          rsp.x = req.x;
          rsp.y = 2 * req.x + 1;
          agent.send(rsp);
        }
      });
      add(new MessageBehavior(NuisanceMessage.class) {
        @Override
        public void onReceive(Message msg) {
          nuisance++;
        }
      });
    }
  }
}
