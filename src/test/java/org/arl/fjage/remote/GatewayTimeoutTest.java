/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicReference;
import org.arl.fjage.*;
import org.junit.After;
import org.junit.Test;

/**
 * Regression tests for issue #437: Gateway.receive() called from a non-gateway
 * thread must honor its timeout even if the internal receive behavior throws or
 * never runs, and must return promptly when the gateway is closed mid-receive.
 */
public class GatewayTimeoutTest {

  private static final long TIMEOUT = 10000;

  private Platform platform;
  private MasterContainer master;
  private Gateway gw;

  @After
  public void shutdown() {
    if (gw != null) gw.close();
    if (master != null) master.shutdown();
    if (platform != null) platform.shutdown();
    gw = null;
    master = null;
    platform = null;
  }

  @Test(timeout = 30000)
  public void receiveWithThrowingFilterHonorsTimeout() throws Exception {
    setup();
    gw = new Gateway("localhost", master.getPort());
    addSender(gw.getAgentID());
    long start = System.currentTimeMillis();
    Message rsp = gw.receive(m -> { throw new RuntimeException("bad filter"); }, 1000);
    long elapsed = System.currentTimeMillis() - start;
    assertNull("Receive with throwing filter should return null", rsp);
    assertTrue("Receive took " + elapsed + " ms, expected ~1000 ms", elapsed < 5000);
  }

  @Test(timeout = 30000)
  public void receiveWhenAgentNeverRunsHonorsTimeout() {
    platform = new RealTimePlatform();
    Container container = new Container(platform);
    gw = new Gateway(container);
    // platform never started, so the gateway agent never runs the receive behavior
    long start = System.currentTimeMillis();
    Message rsp = gw.receive(500);
    long elapsed = System.currentTimeMillis() - start;
    assertNull("Receive should return null when gateway agent never runs", rsp);
    assertTrue("Receive took " + elapsed + " ms, expected ~1500 ms", elapsed < 5000);
  }

  @Test(timeout = 30000)
  public void closeDuringBlockingReceiveReturnsPromptly() throws Exception {
    setup();
    gw = new Gateway("localhost", master.getPort());
    Thread t = new Thread(() -> gw.receive(Gateway.BLOCKING));
    t.start();
    Thread.sleep(300);
    gw.close();
    gw = null;
    t.join(TIMEOUT);
    assertFalse("Blocking receive did not return after gateway was closed", t.isAlive());
  }

  @Test(timeout = 30000)
  public void interruptCancelsBlockingReceive() throws Exception {
    setup();
    gw = new Gateway("localhost", master.getPort());
    final AtomicReference<Message> rsp = new AtomicReference<>();
    Thread t = new Thread(() -> rsp.set(gw.receive(60000)));
    t.start();
    Thread.sleep(300);
    t.interrupt();
    t.join(TIMEOUT);
    assertFalse("Interrupted receive did not return", t.isAlive());
    assertNull(rsp.get());
  }

  @Test(timeout = 30000)
  public void receiveDeliversMessageBeforeTimeout() throws Exception {
    setup();
    gw = new Gateway("localhost", master.getPort());
    addSender(gw.getAgentID());
    long start = System.currentTimeMillis();
    Message rsp = gw.receive(5000);
    long elapsed = System.currentTimeMillis() - start;
    assertNotNull("Expected a message before the timeout", rsp);
    assertTrue("Receive took " + elapsed + " ms, expected well under 5000 ms", elapsed < 3000);
  }

  //////// helpers

  private void setup() {
    platform = new RealTimePlatform();
    master = new MasterContainer(platform);
    platform.start();
  }

  /** Adds an agent on the master that periodically sends a message to the given agent. */
  private void addSender(final AgentID aid) {
    master.add("sender", new Agent() {
      @Override
      public void init() {
        add(new TickerBehavior(100) {
          @Override
          public void onTick() {
            send(new Message(aid, Performative.INFORM));
          }
        });
      }
    });
  }

}
