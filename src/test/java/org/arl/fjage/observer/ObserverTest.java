/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.observer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

import org.arl.fjage.Agent;
import org.arl.fjage.AgentID;
import org.arl.fjage.Container;
import org.arl.fjage.GenericMessage;
import org.arl.fjage.Message;
import org.arl.fjage.MessageBehavior;
import org.arl.fjage.Performative;
import org.arl.fjage.Platform;
import org.arl.fjage.RealTimePlatform;
import org.arl.fjage.TickerBehavior;
import org.arl.fjage.connectors.WebServer;
import org.arl.fjage.param.ParameterReq;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Boots a container with an observer and two chatty agents, and drives the
 * observer over its web socket.
 */
public class ObserverTest {

  private static final String CONTEXT = "/observer";
  private static final AtomicInteger delivered = new AtomicInteger(0);

  private static Platform platform;
  private static Container container;
  private static Observer observer;
  private static int port;

  private ObserverTestClient client;

  @BeforeClass
  public static void setUpClass() throws Exception {
    Logger.getLogger("org.arl.fjage").setLevel(Level.WARNING);
    port = freePort();
    platform = new RealTimePlatform();
    container = new Container(platform);
    observer = new Observer(port, CONTEXT);
    container.add("observer", observer);
    container.add("pinger", new Pinger());
    container.add("ponger", new Ponger());
    platform.start();
  }

  @AfterClass
  public static void tearDownClass() {
    platform.shutdown();
    // an observer never stops its web server, since it may be sharing it with
    // the application, so the test stops the one it caused rather than leave it
    // registered for other test classes to trip over
    if (WebServer.hasInstance(port)) WebServer.getInstance(port).stop();
  }

  @Before
  public void setUp() throws Exception {
    // the container filter is global, so start every test from a clean one
    observer.setFilter(new ObserverFilter());
    observer.setEnabled(true);
    client = new ObserverTestClient(port, CONTEXT);
  }

  @After
  public void tearDown() {
    if (client != null) client.close();
  }

  //////////// tests

  @Test
  public void testEnvelope() throws Exception {
    List<JsonObject> evs = client.await("send", 5, 5000);
    assertTrue("expected messages to be observed", evs.size() >= 5);
    long last = -1;
    for (JsonObject o: evs) {
      assertTrue("missing time", o.has("time"));
      assertTrue("missing ptime", o.has("ptime"));
      assertTrue("missing seq", o.has("seq"));
      long seq = o.get("seq").getAsLong();
      assertTrue("seq not monotonic", seq > last);
      last = seq;
      JsonObject m = o.getAsJsonObject("message");
      assertNotNull("missing message", m);
      assertTrue("missing clazz", m.has("clazz"));
      JsonObject d = m.getAsJsonObject("data");
      assertNotNull("missing message data", d);
      assertTrue("missing recipient", d.has("recipient"));
    }
  }

  @Test
  public void testObservationIsTransparent() throws Exception {
    // the pinger's requests are answered, so delivery works with the listener
    // installed, and the observer sees the replies too
    int before = delivered.get();
    List<JsonObject> evs = client.await("send", 10, 5000);
    assertTrue("messages were not delivered while observing", delivered.get() > before);
    boolean agreed = false;
    for (JsonObject o: evs) {
      JsonObject d = o.getAsJsonObject("message").getAsJsonObject("data");
      if (d.has("perf") && "AGREE".equals(d.get("perf").getAsString())) agreed = true;
    }
    assertTrue("no reply observed", agreed);
  }

  @Test
  public void testEndpointsDiscovered() throws Exception {
    client.send("{\"action\":\"state\"}");
    client.await("send", 5, 5000);
    Set<String> names = new HashSet<String>();
    Set<String> topics = new HashSet<String>();
    for (JsonObject o: client.events("endpoints")) {
      for (Object e: o.getAsJsonArray("endpoints")) {
        JsonObject ep = (JsonObject)e;
        String n = ep.get("name").getAsString();
        names.add(n);
        if (ep.get("topic").getAsBoolean()) topics.add(n);
      }
    }
    assertTrue("pinger not discovered: "+names, names.contains("pinger"));
    assertTrue("ponger not discovered: "+names, names.contains("ponger"));
    assertTrue("topic not discovered: "+names, names.contains("#chatter"));
    assertTrue("topic not flagged: "+topics, topics.contains("#chatter"));
  }

  @Test
  public void testClassExcludedInContainer() throws Exception {
    client.send("{\"action\":\"filter\",\"filter\":{\"excludeClazz\":\"Parameter\"}}");
    awaitFilterApplied();
    client.clear();
    List<JsonObject> evs = client.await("send", 10, 5000);
    assertTrue("expected messages to be observed", evs.size() >= 10);
    for (JsonObject o: evs)
      assertFalse("a ParameterReq got through the class filter",
                  o.getAsJsonObject("message").get("clazz").getAsString().contains("Parameter"));
  }

  @Test
  public void testEndpointExcludedInContainer() throws Exception {
    client.send("{\"action\":\"filter\",\"filter\":{\"excludeEndpoints\":[\"ponger\"]}}");
    awaitFilterApplied();
    client.clear();
    List<JsonObject> evs = client.await("send", 5, 5000);
    assertTrue("expected topic messages to still be observed", evs.size() >= 5);
    for (JsonObject o: evs) {
      JsonObject d = o.getAsJsonObject("message").getAsJsonObject("data");
      assertFalse("ponger traffic got through", ends(d).contains("ponger"));
    }
    // but the endpoint remains listed, so it can be un-excluded from the UI
    client.clear();
    client.send("{\"action\":\"state\"}");
    List<JsonObject> eps = client.await("endpoints", 1, 2000);
    assertTrue("no endpoint list published", eps.size() >= 1);
    Set<String> names = new HashSet<String>();
    for (JsonObject o: eps)
      for (Object e: o.getAsJsonArray("endpoints"))
        names.add(((JsonObject)e).get("name").getAsString());
    assertTrue("an excluded endpoint disappeared from the list: "+names, names.contains("ponger"));
  }

  @Test
  public void testClearEndpoints() throws Exception {
    client.await("send", 5, 5000);
    assertTrue("nothing discovered to clear", observer.getEndpoints().length > 0);
    client.clear();
    client.send("{\"action\":\"clearEndpoints\"}");
    // the observer publishes the now empty list, flagged as a full list, so
    // that clients replace rather than merge
    List<JsonObject> eps = client.await("endpoints", 1, 5000);
    assertTrue("no endpoint list published", eps.size() >= 1);
    JsonObject first = eps.get(0);
    assertTrue("not flagged as a full list", first.get("full").getAsBoolean());
    assertEquals(0, first.getAsJsonArray("endpoints").size());
    // endpoints still in use come straight back
    Set<String> back = new HashSet<String>();
    long t = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < t && !back.contains("ponger")) {
      for (String s: observer.getEndpoints()) back.add(s);
      Thread.sleep(20);
    }
    assertTrue("a live endpoint did not reappear: "+back, back.contains("ponger"));
  }

  @Test
  public void testFilterStateIsEchoed() throws Exception {
    client.send("{\"action\":\"filter\",\"filter\":{\"excludeClazz\":\"Parameter\","
                +"\"excludeEndpoints\":[\"ponger\"]}}");
    // the observer echoes the applied filter to every client, so that all open
    // UIs agree about what is being dropped
    JsonObject f = null;
    long t = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < t && f == null) {
      for (JsonObject o: client.events("state")) {
        JsonObject g = o.getAsJsonObject("filter");
        if (g != null && g.has("excludeClazz")) f = g;
      }
      if (f == null) Thread.sleep(20);
    }
    assertNotNull("filter state not echoed", f);
    assertEquals("Parameter", f.get("excludeClazz").getAsString());
    assertEquals(1, f.getAsJsonArray("excludeEndpoints").size());
  }

  @Test
  public void testBadRegexDoesNotBreakObservation() throws Exception {
    client.send("{\"action\":\"filter\",\"filter\":{\"excludeClazz\":\"([unclosed\"}}");
    awaitFilterApplied();
    client.clear();
    assertTrue("observation stopped after a bad regex",
               client.await("send", 5, 5000).size() >= 5);
  }

  @Test
  public void testDisableStopsTheStream() throws Exception {
    client.send("{\"action\":\"enable\",\"enabled\":false}");
    client.await("state", 1, 2000);
    Thread.sleep(300);            // let anything already in flight arrive
    client.clear();
    Thread.sleep(700);
    assertEquals(0, client.events("send").size());
    client.send("{\"action\":\"enable\",\"enabled\":true}");
    assertTrue("observation did not resume", client.await("send", 3, 5000).size() >= 3);
  }

  @Test
  public void testRateLimit() throws Exception {
    observer.setMaxRate(2);
    try {
      client.clear();
      Thread.sleep(1500);
      int n = client.events("send").size();
      assertTrue("rate limit not applied, got "+n+" messages", n <= 6);
      assertTrue("nothing dropped", observer.getDropped() > 0);
    } finally {
      observer.setMaxRate(Observer.DEFAULT_MAX_RATE);
    }
  }

  //////////// helpers

  /**
   * Waits until the observer has adopted a filter sent over the control
   * channel. The control channel is read on its own thread, so a command is
   * not applied by the time it is written.
   */
  private void awaitFilterApplied() throws InterruptedException {
    long t = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < t) {
      ObserverFilter f = observer.getFilter();
      if (f.excludeClazz != null || f.clazz != null
          || (f.excludeEndpoints != null && !f.excludeEndpoints.isEmpty())) return;
      Thread.sleep(20);
    }
  }

  private List<String> ends(JsonObject data) {
    List<String> list = new ArrayList<String>();
    if (data.has("sender")) list.add(data.get("sender").getAsString());
    if (data.has("recipient")) list.add(data.get("recipient").getAsString());
    return list;
  }

  private static int freePort() throws Exception {
    ServerSocket s = new ServerSocket(0);
    try {
      return s.getLocalPort();
    } finally {
      s.close();
    }
  }

  //////////// test agents

  private static class Pinger extends Agent {
    @Override
    public void init() {
      add(new TickerBehavior(100) {
        @Override
        public void onTick() {
          GenericMessage m = new GenericMessage(new AgentID("ponger"), Performative.REQUEST);
          m.put("n", 42);
          send(m);
          GenericMessage t = new GenericMessage(new AgentID("chatter", true), Performative.INFORM);
          t.put("hello", "world");
          send(t);
          send(new ParameterReq(new AgentID("ponger")));
        }
      });
    }
  }

  private static class Ponger extends Agent {
    @Override
    public void init() {
      add(new MessageBehavior() {
        @Override
        public void onReceive(Message msg) {
          delivered.incrementAndGet();
          if (msg.getPerformative() == Performative.REQUEST)
            send(new GenericMessage(msg, Performative.AGREE));
        }
      });
    }
  }

}
