/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import org.arl.fjage.*;
import org.arl.fjage.remote.Gateway;
import org.arl.fjage.remote.MasterContainer;
import org.arl.fjage.remote.SlaveContainer;
import org.arl.fjage.persistence.Store;
import org.arl.fjage.auth.*;
import org.arl.fjage.shell.*;
import org.arl.fjage.param.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class BasicTests {

  private static final int TICKS = 100;
  private static final int DELAY = 1000;

  private Random rnd = new Random();
  private Logger log = Logger.getLogger(getClass().getName());

  @Before
  public void beforeTesting() {
    LogFormatter.install(null);
  }

  @Test
  public void testRT() {
    log.info("testRT");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ClientAgent client = new ClientAgent();
    ServerAgent server = new ServerAgent();
    container.add("C", client);
    container.add("S", server);
    platform.start();
    while (!client.done)
      platform.delay(DELAY);
    platform.shutdown();
    assertEquals(0, client.bad);
    assertEquals(client.good, client.requests);
    assertEquals(client.requests, server.requests);
    assertEquals(client.nuisance, server.nuisance);
  }

  @Test
  public void testSim() {
    log.info("testSim");
    Platform platform = new DiscreteEventSimulator();
    Container container = new Container(platform);
    ClientAgent client = new ClientAgent();
    ServerAgent server = new ServerAgent();
    container.add("C", client);
    container.add("S", server);
    platform.start();
    while (!client.done)
      platform.delay(DELAY);
    platform.shutdown();
    assertEquals(0, client.bad);
    assertEquals(client.good, client.requests);
    assertEquals(client.requests, server.requests);
    assertEquals(client.nuisance, server.nuisance);
  }

  @Test
  public void testRemote1() throws IOException {
    log.info("testRemote1");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    Container slave = new SlaveContainer(platform, "localhost", master.getPort());
    ClientAgent client = new ClientAgent();
    ServerAgent server = new ServerAgent();
    slave.add("C", client);
    master.add("S", server);
    platform.start();
    AgentID c = new AgentID("C");
    AgentID s = new AgentID("S");
    assertTrue(master.containsAgent(s));
    assertFalse(master.containsAgent(c));
    assertTrue(master.canLocateAgent(c));
    assertTrue(slave.containsAgent(c));
    assertFalse(slave.containsAgent(s));
    assertTrue(slave.canLocateAgent(s));
    while (!client.done)
      platform.delay(DELAY);
    platform.delay(DELAY);
    platform.shutdown();
    assertEquals(0, client.bad);
    assertEquals(client.good, client.requests);
    assertEquals(client.requests, server.requests);
    assertEquals(client.nuisance, server.nuisance);
  }

  @Test
  public void testRemote2() throws IOException {
    log.info("testRemote2");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    Container slave = new SlaveContainer(platform, "localhost", master.getPort());
    ClientAgent client = new ClientAgent();
    ServerAgent server = new ServerAgent();
    master.add("C", client);
    slave.add("S", server);
    platform.start();
    while (!client.done)
      platform.delay(DELAY);
    platform.delay(DELAY);
    platform.shutdown();
    assertEquals(0, client.bad);
    assertEquals(client.good, client.requests);
    assertEquals(client.requests, server.requests);
    assertEquals(client.nuisance, server.nuisance);
  }

  @Test
  public void testRemote3() throws IOException {
    log.info("testRemote3");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    Container slave = new SlaveContainer(platform, "localhost", master.getPort());
    ClientAgent client = new ClientAgent();
    ServerAgent server = new ServerAgent();
    slave.add("C", client);
    slave.add("S", server);
    platform.start();
    while (!client.done)
      platform.delay(DELAY);
    platform.delay(DELAY);
    platform.shutdown();
    assertEquals(0, client.bad);
    assertEquals(client.good, client.requests);
    assertEquals(client.requests, server.requests);
    assertEquals(client.nuisance, server.nuisance);
  }

  @Test
  public void testGateway() throws IOException {
    log.info("testGateway");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform);
    ServerAgent server = new ServerAgent();
    master.add("S", server);
    platform.start();
    Gateway gw = new Gateway("localhost", master.getPort());
    Message rsp = gw.receive(100);
    assertNull(rsp);
    AgentID s = gw.agentForService("server");
    assertEquals(server.getAgentID(), s);
    Message req = new RequestMessage(s);
    gw.send(req);
    rsp = gw.receive(100);
    assertNotNull(rsp);
    assertSame(rsp.getClass(), ResponseMessage.class);
    req = new RequestMessage(server.getAgentID());
    rsp = gw.request(req, 100);
    assertNotNull(rsp);
    assertSame(rsp.getClass(), ResponseMessage.class);
    req = new NuisanceMessage(server.getAgentID());
    rsp = gw.request(req, 100);
    assertNull(rsp);
    gw.close();
    platform.shutdown();
  }

  @Test
  public void testFirewall1() throws IOException {
    log.info("testFirewall1");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, new AllowAll());
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
    log.info("testFirewall2");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, new DenyAll());
    ServerAgent server = new ServerAgent();
    master.add("S", server);
    platform.start();
    Gateway gw = new Gateway("localhost", master.getPort());
    AgentID s = gw.agentForService("server");
    assertNull(s);
    Message req = new RequestMessage(server.getAgentID());
    Message rsp = gw.request(req, 1000);
    assertNull(rsp);
    gw.close();
    platform.shutdown();
    assertEquals(0, server.requests);
  }

  @Test
  public void testFirewall3() throws IOException {
    log.info("testFirewall3");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, new AllowAfterAuth());
    ServerAgent server = new ServerAgent();
    master.add("S", server);
    platform.start();
    Gateway gw = new Gateway("localhost", master.getPort());
    AgentID s = gw.agentForService("server");
    assertNull(s);
    gw.authenticate("somecreds");
    s = gw.agentForService("server");
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
    log.info("testFirewall4");
    Platform platform = new RealTimePlatform();
    MasterContainer master = new MasterContainer(platform, new AllowAfterAuth());
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
  public void testFSM() {
    log.info("testFSM");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    Agent agent = new Agent();
    container.add(agent);
    FSMBehavior fsm = new FSMBehavior();
    final List<Integer> list = new ArrayList<Integer>();
    fsm.add(new FSMBehavior.State("tick") {
      @Override
      public void onEnter() {
        block(100);
      }
      @Override
      public void action() {
        setNextState("tock");
      }
    });
    fsm.add(new FSMBehavior.State("tock") {
      int n = 0;
      @Override
      public void onEnter() {
        list.add(n);
        block(50);
      }
      @Override
      public void action() {
        n++;
        if (n > 5) terminate();
        else setNextState("tick");
      }
    });
    agent.add(fsm);
    platform.start();
    while (!fsm.done())
      platform.delay(DELAY);
    platform.shutdown();
    assertEquals(6, list.size());
  }

  @Test
  public void testTickers() {
    log.info("testTickers");
    final int nAgents = 10;
    final int tickDelay = 100;
    final int ticks = 6000;
    Platform platform = new DiscreteEventSimulator();
    Container container = new Container(platform);
    TickerBehavior[] tb = new TickerBehavior[nAgents];
    for (int i = 0; i < nAgents; i++) {
      Agent agent = new Agent();
      container.add(agent);
      tb[i] = new TickerBehavior(tickDelay) {
        long last = -1;
        @Override
        public void onTick() {
          if (getTickCount() >= ticks) stop();
          if (last >= 0 && agent.currentTimeMillis() != last+tickDelay) {
            log.warning("broken / ticks = "+getTickCount()+", expected = "+(last+tickDelay)+", now = "+agent.currentTimeMillis());
            stop();
          }
          last = agent.currentTimeMillis();
        }
      };
      agent.add(tb[i]);
    }
    platform.start();
    platform.delay(tickDelay*ticks+1000);
    platform.shutdown();
    for (int i = 0; i < nAgents; i++)
      assertEquals("ticks = " + tb[i].getTickCount() + ", expected " + ticks, tb[i].getTickCount(), ticks);
  }

  @Test
  public void testSerialCloner() {
    log.info("testSerialCloner");
    Platform platform = new DiscreteEventSimulator();
    Container container = new Container(platform);
    container.setCloner(Container.SERIAL_CLONER);
    RequestMessage s1 = new RequestMessage(null);
    s1.x = 77;
    RequestMessage s2 = container.clone(s1);
    assertNotSame(s1, s2);
    assertEquals(s1.x, s2.x);
  }

  @Test
  public void testFastCloner() {
    log.info("testFastCloner");
    Platform platform = new DiscreteEventSimulator();
    Container container = new Container(platform);
    container.setCloner(Container.FAST_CLONER);
    RequestMessage s1 = new RequestMessage(null);
    s1.x = 77;
    RequestMessage s2 = container.clone(s1);
    assertNotSame(s1, s2);
    assertEquals(s1.x, s2.x);
  }

  @Test
  public void testPersistence() {
    log.info("testPersistence");
    ShellTestAgent agent = new ShellTestAgent();
    Store.setRoot(new File("build/test/fjstore"));
    Store.setClassLoader(getClass().getClassLoader());
    Store store = Store.getInstance(agent);
    store.delete();
    store = Store.getInstance(agent);
    store.put(new Bean1(1));
    store.put(new Bean1(2));
    store.put(new Bean1(3));
    store.put(new Bean1(1));
    store.put(new Bean2(7));
    store.put(new Bean2(8));
    store.put(new Bean2(9));
    store.put(new Bean2(10));
    store.put(new Bean2(7));
    List<Bean1> recs1 = store.getByType(Bean1.class);
    assertEquals(3, recs1.size());
    List<Bean2> recs2 = store.getByType(Bean2.class);
    assertEquals(4, recs2.size());
    Bean2 b2 = store.getById(Bean2.class, "7");
    assertEquals(7, b2.x);
    store.remove(b2);
    b2 = store.getById(Bean2.class, "7");
    assertNull(b2);
    assertNotNull(store.getById(Bean2.class, "8"));
    store.removeById(Bean2.class, "8");
    assertNull(store.getById(Bean2.class, "8"));
    assertNotNull(store.getById(Bean2.class, "9"));
    store.removeByType(Bean1.class);
    recs1 = store.getByType(Bean1.class);
    assertEquals(0, recs1.size());
    assertNotNull(store.getById(Bean2.class, "9"));
    assertTrue(store.size() > 0);
    store.delete();
    store = Store.getInstance(agent);
    assertNull(store.getById(Bean2.class, "9"));
    assertEquals(0, store.size());
    store.close();
  }

  @Test
  public void testShell() {
    log.info("testShell");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    container.add("shell", new ShellAgent(new EchoScriptEngine()));
    ShellTestAgent agent = new ShellTestAgent();
    container.add("test", agent);
    platform.start();
    while (!agent.done)
      platform.delay(1000);
    platform.shutdown();
    assertTrue(agent.exec);
    assertTrue(agent.put1);
    assertTrue(agent.put2);
    assertTrue(agent.put3);
    assertTrue(agent.put4);
    assertTrue(agent.get);
    assertTrue(agent.get2);
    assertTrue(agent.get3);
    assertTrue(agent.get4);
    assertTrue(agent.dir);
    assertTrue(agent.del);
  }

  @Test
  public void testListener() {
    log.info("testListener");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ServerAgent server = new ServerAgent();
    ClientAgent2 client = new ClientAgent2();
    container.add("S", server);
    container.add("C", client);
    MyMessageListener listener = new MyMessageListener();
    assertTrue(container.addListener(listener));
    platform.start();
    platform.delay(1000);
    assertTrue(container.removeListener(listener));
    assertFalse(container.removeListener(listener));
    int n1 = listener.n;
    assertTrue(n1 > 0);
    assertTrue(n1 == server.nuisance);
    assertTrue(n1 == client.nuisance);
    server.nuisance = 0;
    client.nuisance = 0;
    platform.delay(1000);
    platform.shutdown();
    assertTrue(n1 == listener.n);
    assertTrue(server.nuisance == client.nuisance);
  }

  @Test
  public void testListener2() {
    log.info("testListener2");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ServerAgent server = new ServerAgent();
    ClientAgent2 client = new ClientAgent2();
    container.add("S", server);
    container.add("C", client);
    MyMessageListener listener = new MyMessageListener();
    listener.eat = true;
    assertTrue(container.addListener(listener));
    platform.start();
    platform.delay(1000);
    assertTrue(container.removeListener(listener));
    assertFalse(container.removeListener(listener));
    int n1 = listener.n;
    assertTrue(n1 > 0);
    assertTrue(n1 == client.nuisance);
    assertTrue(server.nuisance == 0);
    server.nuisance = 0;
    client.nuisance = 0;
    platform.delay(1000);
    platform.shutdown();
    assertTrue(n1 == listener.n);
    assertTrue(server.nuisance > 0);
  }

  @Test
  public void testParam1() {
    log.info("testParam1");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ParamServerAgent server = new ParamServerAgent(true);
    ParamClientAgent client1 = new ParamClientAgent(true);
    ParamClientAgent client2 = new ParamClientAgent(true);
    ParamClientAgent client3 = new ParamClientAgent(true);
    container.add("S", server);
    container.add("C1", client1);
    container.add("C2", client2);
    container.add("C3", client3);
    platform.start();
    platform.delay(10000);
    platform.shutdown();
    platform.delay(2000);
    log.info("Successful: "+(client1.count + client2.count + client3.count));
    log.info("Warnings: "+(client1.warnings + client2.warnings + client3.warnings));
    log.info("Errors: "+(server.errors + client1.errors + client2.errors + client3.errors));
    assertTrue(server.errors == 0);
    assertTrue(client1.errors == 0);
    assertTrue(client2.errors == 0);
    assertTrue(client3.errors == 0);
    assertTrue(client1.warnings < 3);
    assertTrue(client2.warnings < 3);
    assertTrue(client3.warnings < 3);
    assertTrue(client1.count + client2.count + client3.count > 100);
  }

  @Test
  public void testParam2() {
    log.info("testParam2");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ParamServerAgent server = new ParamServerAgent(false);
    ParamClientAgent client1 = new ParamClientAgent(false);
    ParamClientAgent client2 = new ParamClientAgent(false);
    ParamClientAgent client3 = new ParamClientAgent(false);
    container.add("S", server);
    container.add("C1", client1);
    container.add("C2", client2);
    container.add("C3", client3);
    platform.start();
    platform.delay(10000);
    platform.shutdown();
    platform.delay(2000);
    log.info("Successful: "+(client1.count + client2.count + client3.count));
    log.info("Warnings: "+(client1.warnings + client2.warnings + client3.warnings));
    log.info("Errors: "+(server.errors + client1.errors + client2.errors + client3.errors));
    assertTrue(server.errors == 0);
    assertTrue(client1.errors == 0);
    assertTrue(client2.errors == 0);
    assertTrue(client3.errors == 0);
    assertTrue(client1.warnings < 3);
    assertTrue(client2.warnings < 3);
    assertTrue(client3.warnings < 3);
    assertTrue(client1.count + client2.count + client3.count > 100);
  }

  @Test
  public void testAIDParam() {
    log.info("testAIDParam");
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    ParamServerAgent server = new ParamServerAgent(false);
    AIDParamClientAgent client1 = new AIDParamClientAgent();
    AIDParamClientAgent client2 = new AIDParamClientAgent();
    AIDParamClientAgent client3 = new AIDParamClientAgent();
    container.add("S", server);
    container.add("C1", client1);
    container.add("C2", client2);
    container.add("C3", client3);
    platform.start();
    platform.delay(10000);
    platform.shutdown();
    platform.delay(2000);
    log.info("Successful: "+(client1.count + client2.count + client3.count));
    log.info("Warnings: "+(client1.warnings + client2.warnings + client3.warnings));
    log.info("Errors: "+(server.errors + client1.errors + client2.errors + client3.errors));
    assertTrue(server.errors == 0);
    assertTrue(client1.errors == 0);
    assertTrue(client2.errors == 0);
    assertTrue(client3.errors == 0);
    assertTrue(client1.warnings < 3);
    assertTrue(client2.warnings < 3);
    assertTrue(client3.warnings < 3);
    assertTrue(client1.count + client2.count + client3.count > 100);
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

  public class ParamServerAgent extends Agent {
    public int errors = 0;
    public int x = 1;
    public float getY() { return 2; }
    public String getS() { return "xxx"; }
    public int setX(int x) { return x+1; }
    public float getY(int ndx) { return ndx+42; }
    public int setX(int ndx, int x) { return x+42; }
    public ParamServerAgent(boolean yor) {
      super();
      setYieldDuringReceive(yor);
    }
    @Override
    public void init() {
      register("server");
      add(new ParameterMessageBehavior(Params.class));
      add(new MessageBehavior() {
        @Override
        public void onReceive(Message msg) {
          if (msg instanceof ParameterReq) {
            log.warning("Got unexpected req: "+msg);
            errors++;
          }
          else if (msg instanceof RequestMessage) send(new ResponseMessage(msg));
        }
      });
      if (getYieldDuringReceive()) {
        add(new TickerBehavior(1000) {
          @Override
          public void onTick() {
            Message m1 = new Message();
            Message m2 = receive(m1, 500);
            if (m2 != null) {
              log.warning("Unexpected message: "+m2.toString());
              errors++;
            }
          }
        });
      }
    }
  }

  private class ParamClientAgent extends Agent {
    public int errors = 0;
    public int warnings = 0;
    public int count = 0;
    public ParamClientAgent(boolean yor) {
      super();
      setYieldDuringReceive(yor);
    }
    @Override
    public void init() {
      delay(200);
      add(new PoissonBehavior(100) {
        @Override
        public void onTick() {
          AgentID server = agent.agentForService("server");
          if (server == null) {
            log.warning("Unable to find server");
            errors++;
            return;
          }
          count++;
          Message req = new ParameterReq().get(Params.x);
          req.setRecipient(server);
          Message rsp = request(req, 1000);
          if (rsp == null) {
            log.warning("Unable to get x");
            warnings++;
            return;
          } else {
            Integer x = (Integer)((ParameterRsp)rsp).get(Params.x);
            if (x == null || x != 1) {
              log.warning("Bad value of x: "+x);
              errors++;
            }
          }
          req = new ParameterReq().set(Params.x, 3);
          req.setRecipient(server);
          rsp = request(req, 1000);
          if (rsp == null) {
            log.warning("Unable to set x");
            warnings++;
            return;
          } else {
            Integer x = (Integer)((ParameterRsp)rsp).get(Params.x);
            if (x == null || x != 4) {
              log.warning("Bad value of set(x): "+x);
              errors++;
            }
          }
          req = new ParameterReq().get(Params.y).get(Params.s);
          req.setRecipient(server);
          rsp = request(req, 1000);
          if (rsp == null) {
            log.warning("Unable to get y and s");
            warnings++;
            return;
          } else {
            Float y = (Float)((ParameterRsp)rsp).get(Params.y);
            if (y == null || y != 2) {
              log.warning("Bad value of y: "+y);
              errors++;
            }
            String s = (String)((ParameterRsp)rsp).get(Params.s);
            if (!"xxx".equals(s)) {
              log.warning("Bad value of s: "+s);
              errors++;
            }
          }
          req = new ParameterReq().get(Params.y);
          ((ParameterReq)req).setIndex(7);
          req.setRecipient(server);
          rsp = request(req, 1000);
          if (rsp == null) {
            log.warning("Unable to get y[7]");
            warnings++;
            return;
          } else {
            Float y = (Float)((ParameterRsp)rsp).get(Params.y);
            if (y == null || y != 49) {
              log.warning("Bad value of y[7]: "+y);
              errors++;
            }
          }
          req = new ParameterReq().set(Params.x, 2);
          ((ParameterReq)req).setIndex(7);
          req.setRecipient(server);
          rsp = request(req, 1000);
          if (rsp == null) {
            log.warning("Unable to set x[7]");
            warnings++;
            return;
          } else {
            Integer x = (Integer)((ParameterRsp)rsp).get(Params.x);
            if (x == null || x != 44) {
              log.warning("Bad value of x[7]: "+x);
              errors++;
            }
          }
          req = new ParameterReq();
          req.setRecipient(server);
          rsp = request(req, 1000);
          if (rsp == null) {
            log.warning("Unable to get x, y and s");
            warnings++;
            return;
          } else {
            Integer x = (Integer)((ParameterRsp)rsp).get(Params.x);
            if (x == null || x != 1) {
              log.warning("Bad value of x: "+x);
              errors++;
            }
            Float y = (Float)((ParameterRsp)rsp).get(Params.y);
            if (y == null || y != 2) {
              log.warning("Bad value of y: "+y);
              errors++;
            }
            String s = (String)((ParameterRsp)rsp).get(Params.s);
            if (!"xxx".equals(s)) {
              log.warning("Bad value of s: "+s);
              errors++;
            }
          }
          req = new RequestMessage(server);
          rsp = request(req, 1000);
          if (rsp == null) {
            log.warning("No response from server");
            warnings++;
            return;
          } else if (!(rsp instanceof ResponseMessage)) {
            log.warning("Bad response: "+rsp);
            errors++;
          }
        }
      });
    }
  }

  private class AIDParamClientAgent extends Agent {
    public int errors = 0;
    public int warnings = 0;
    public int count = 0;
    public AIDParamClientAgent() {
      super();
    }
    @Override
    public void init() {
      delay(1000);
      add(new PoissonBehavior(200) {
        @Override
        public void onTick() {
          AgentID server = agent.agentForService("server");
          if (server == null) {
            log.warning("Unable to find server");
            errors++;
            return;
          }
          count++;
          Integer x = (Integer)server.get(Params.x);
          if (x == null) {
            log.warning("Unable to get x");
            warnings++;
            return;
          } else if (x != 1) {
            log.warning("Bad value of x: "+x);
            errors++;
          }
          x = (Integer)server.set(Params.x, 3);
          if (x == null) {
            log.warning("Unable to set x");
            warnings++;
            return;
          } else if (x != 4) {
            log.warning("Bad value of set(x): "+x);
            errors++;
          }
          Float y = (Float)server.get(Params.y, 7);
          if (y == null) {
            log.warning("Unable to get y[7]");
            warnings++;
            return;
          } else if (y != 49) {
            log.warning("Bad value of y[7]: "+y);
            errors++;
          }
          x = (Integer)server.set(Params.x, 3, 7);
          if (x == null) {
            log.warning("Unable to set x[7]");
            warnings++;
            return;
          } else if (x != 45) {
            log.warning("Bad value of x[7]: "+x);
            errors++;
          }
        }
      });
    }
  }

  private class ServerAgent extends Agent {
    public int requests = 0, nuisance = 0;
    @Override
    public void init() {
      register("server");
      subscribe(topic("noise"));
      add(new MessageBehavior(RequestMessage.class) {
        @Override
        public void onReceive(Message msg) {
          requests++;
          RequestMessage req = (RequestMessage)msg;
          ResponseMessage rsp = new ResponseMessage(req);
          rsp.x = req.x;
          rsp.y = 2*req.x + 1;
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

  private class ClientAgent extends Agent {
    public boolean done = false;
    public int requests = 0, nuisance = 0, good = 0, bad = 0;
    @Override
    public void init() {
      add(new TickerBehavior(10) {
        @Override
        public void onTick() {
          if (getTickCount() > TICKS) {
            stop();
            done = true;
            return;
          }
          if (rnd.nextBoolean()) {
            requests++;
            AgentID server = agent.agentForService("server");
            RequestMessage req = new RequestMessage(server);
            req.x = requests;
            agent.send(req);
          } else {
            nuisance++;
            AgentID server = topic("noise");
            NuisanceMessage n = new NuisanceMessage(server);
            agent.send(n);
          }
        }
      });
      add(new MessageBehavior() {
        @Override
        public void onReceive(Message msg) {
          if (msg instanceof ResponseMessage) {
            ResponseMessage rsp = (ResponseMessage)msg;
            if (2*rsp.x + 1 == rsp.y) good++;
            else bad++;
          } else bad++;
        }
      });
    }
  }

  private class ClientAgent2 extends Agent {
    public int nuisance = 0;
    @Override
    public void init() {
      add(new TickerBehavior(100) {
        @Override
        public void onTick() {
          if (nuisance >= 5) return;
          nuisance++;
          AgentID server = topic("noise");
          NuisanceMessage n = new NuisanceMessage(server);
          agent.send(n);
        }
      });
    }
  }

  private class MyMessageListener implements MessageListener {
    public int n = 0;
    public boolean eat = false;
    @Override
    public boolean onReceive(Message msg) {
      n++;
      return eat;
    }
  }

  private class ShellTestAgent extends Agent {
    private final String DIRNAME = "/tmp";
    private final String FILENAME = "fjage-test.txt";
    public boolean exec = false, put1 = false, put2 = false, put3 = false, put4 = false, get = false, get2 = false, get3 = false, get4 = false, del = false, dir = false, done = false;
    @Override
    public void init() {
      add(new OneShotBehavior() {
        /*
          Test 1: Create a file with dummy data.
          Test 2: Retrieve the entire file created in Test 1.
          Test 3: Retrieve the part of a file created in Test 1.
          Test 4: Retrieve the part of a file created in Test 1 with ofs = 0.
          Test 5: Retrieve the part of a file created in Test 1 but with an ofs outside the size of the file.
          Test 6: Append more data to the file created in Test 1.
          Test 7: Overwrite data to the file created in Test 1.
          Test 8: Truncate the file created in Test 1.
          Test 9: Get a listing of all files in the directory, ensure the file created in Test 1 exists.
          Test 10: Delete file created in Test 1.
         */
        @Override
        public void action() {
          AgentID shell = new AgentID("shell");
          Message req = new ShellExecReq(shell, "boo");
          Message rsp = request(req);
          if (rsp != null && rsp.getPerformative() == Performative.AGREE) exec = true;
          byte[] bytes = "this is a test".getBytes();
          req = new PutFileReq(shell, DIRNAME+File.separator+FILENAME, bytes);
          rsp = request(req);
          log.info("put1 rsp: "+rsp);
          if (rsp != null && rsp.getPerformative() == Performative.AGREE) {
            File f = new File(DIRNAME+File.separator+FILENAME);
            if (f.exists()) put1 = true;
          }
          req = new GetFileReq(shell, DIRNAME+File.separator+FILENAME);
          rsp = request(req);
          log.info("get rsp: "+rsp);
          if (rsp instanceof GetFileRsp) {
            byte[] contents = ((GetFileRsp)rsp).getContents();
            log.info("get data len: "+contents.length);
            if (contents.length == bytes.length && ((GetFileRsp)rsp).getOffset() == 0) {
              log.info("get data: "+new String(contents));
              get = true;
              for (int i = 0; i < contents.length; i++)
                if (contents[i] != bytes[i]) {
                  get = false;
                  break;
                }
            }
          }
          req = new GetFileReq(shell, DIRNAME+File.separator+FILENAME, 5, 4);
          rsp = request(req);
          log.info("get2 rsp: "+rsp);
          if (rsp instanceof GetFileRsp) {
            byte[] contents = ((GetFileRsp)rsp).getContents();
            log.info("get data len: "+contents.length);
            if (contents.length == 4 && ((GetFileRsp)rsp).getOffset() == 5) {
              log.info("get data: "+new String(contents));
              get2 = true;
              for (int i = 0; i < contents.length; i++)
                if (contents[i] != bytes[5 + i]) {
                  get2 = false;
                  break;
                }
            }
          }
          req = new GetFileReq(shell, DIRNAME+File.separator+FILENAME, 9, 0);
          rsp = request(req);
          log.info("get3 rsp: "+rsp);
          if (rsp instanceof GetFileRsp) {
            byte[] contents = ((GetFileRsp)rsp).getContents();
            log.info("get data len: "+contents.length);
            if (contents.length == bytes.length-9 && ((GetFileRsp)rsp).getOffset() == 9) {
              log.info("get data: "+new String(contents));
              get3 = true;
              for (int i = 0; i < contents.length; i++)
                if (contents[i] != bytes[9 + i]) {
                  get3 = false;
                  break;
                }
            }
          }
          req = new GetFileReq(shell, DIRNAME+File.separator+FILENAME, 27, 1);
          rsp = request(req);
          log.info("get4 rsp: "+rsp);
          if (rsp != null && rsp.getPerformative() == Performative.REFUSE) get4 = true;

          req = new PutFileReq(shell, DIRNAME+File.separator+FILENAME, bytes, bytes.length);
          rsp = request(req);
          log.info("put2 rsp: "+rsp);
          if (rsp != null && rsp.getPerformative() == Performative.AGREE) {
            File f = new File(DIRNAME+File.separator+FILENAME);
            if (f.exists() && f.length() == bytes.length*2) put2 = true;
          }

          req = new PutFileReq(shell, DIRNAME+File.separator+FILENAME, bytes, (bytes.length*2)-2);
          rsp = request(req);
          log.info("put3 rsp: "+rsp);
          if (rsp != null && rsp.getPerformative() == Performative.AGREE) {
            File f = new File(DIRNAME+File.separator+FILENAME);
            log.info("put3 length " + f.length() + " : " + ((bytes.length*3)-2));
            if (f.exists() && f.length() == (bytes.length*3)-2) put3 = true;
          }

          req = new PutFileReq(shell, DIRNAME+File.separator+FILENAME, null, bytes.length);
          rsp = request(req);
          log.info("put4 rsp: "+rsp);
          if (rsp != null && rsp.getPerformative() == Performative.AGREE) {
            File f = new File(DIRNAME+File.separator+FILENAME);
            if (f.exists() && f.length() == bytes.length) put4 = true;
          }

          req = new GetFileReq(shell, DIRNAME);
          rsp = request(req);
          log.info("get dir rsp: "+rsp);
          if (rsp instanceof GetFileRsp) {
            String contents = new String(((GetFileRsp)rsp).getContents());
            String[] lines = contents.split("\\r?\\n");
            for (String s: lines) {
              log.info("DIR: "+s);
              if (s.startsWith(FILENAME+"\t")) dir = true;
            }
          }
          req = new PutFileReq(shell, DIRNAME+File.separator+FILENAME, null);
          rsp = request(req);
          log.info("del rsp: "+rsp);
          if (rsp != null && rsp.getPerformative() == Performative.AGREE) {
            File f = new File(DIRNAME+File.separator+FILENAME);
            if (!f.exists()) del = true;
          }
          done = true;
        }
      });
    }
  }

  public static class Bean1 implements java.io.Serializable {
    private static final long serialVersionUID = -1;
    public int x;
    public Bean1(int x) {
      this.x = x;
    }
  }

  public static class Bean2 implements java.io.Serializable {
    private static final long serialVersionUID = -1;
    public int x;
    public Bean2(int x) {
      this.x = x;
    }
    public String getId() {
      return String.valueOf(x);
    }
  }

}
