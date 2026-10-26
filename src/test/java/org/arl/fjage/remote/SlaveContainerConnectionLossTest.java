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
import org.arl.fjage.loadtest.ThrottlingTcpProxy;
import org.junit.After;
import org.junit.Test;

/**
 * Regression tests for issue #438: connection loss to the master container must
 * surface as null responses through the Gateway API, never as a raw NPE from
 * SlaveContainer dereferencing a nulled master connection.
 */
public class SlaveContainerConnectionLossTest {

  private static final long TIMEOUT = 10000;

  private Platform platform;
  private MasterContainer master;
  private ThrottlingTcpProxy proxy;
  private Gateway gw;

  @After
  public void shutdown() {
    if (gw != null) gw.close();
    if (proxy != null) proxy.shutdownProxy();
    if (master != null) master.shutdown();
    if (platform != null) platform.shutdown();
    gw = null;
    proxy = null;
    master = null;
    platform = null;
  }

  @Test(timeout = 30000)
  public void requestAfterConnectionLossReturnsNull() throws Exception {
    setup();
    gw = new Gateway("localhost", proxy.getPort());
    SlaveContainer slave = (SlaveContainer)gw.getContainer();
    dropMaster();
    slave.checkAuthFailure("some-message-id");   // must not throw NPE
    Message rsp = gw.request(new Message(new AgentID("echo"), Performative.REQUEST), 1000);
    assertNull("Request after connection loss should return null", rsp);
  }

  @Test(timeout = 30000)
  public void requestDuringConnectionLossReturnsNull() throws Exception {
    setup();
    gw = new Gateway("localhost", proxy.getPort());
    final AtomicReference<Message> rsp = new AtomicReference<>();
    final AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread t = new Thread(() -> {
      try {
        rsp.set(gw.request(new Message(new AgentID("echo"), Performative.REQUEST), 3000));
      } catch (Throwable ex) {
        thrown.set(ex);
      }
    });
    t.start();
    Thread.sleep(300);
    dropMaster();
    t.join(TIMEOUT);
    assertFalse("Request did not complete after connection loss", t.isAlive());
    assertNull("Request threw " + thrown.get() + " on connection loss", thrown.get());
    assertNull("Request during connection loss should return null", rsp.get());
  }

  //////// helpers

  private void setup() throws Exception {
    platform = new RealTimePlatform();
    master = new MasterContainer(platform);
    proxy = new ThrottlingTcpProxy("localhost", master.getPort());
    proxy.start();
    platform.start();
  }

  /**
   * Abruptly drops the TCP connection between the slave and the master, and refuses
   * reconnection attempts, simulating a network failure (not a graceful shutdown).
   */
  private void dropMaster() throws Exception {
    SlaveContainer slave = (SlaveContainer)gw.getContainer();
    proxy.setRefuse(true);
    proxy.dropConnections();
    assertTrue("Slave did not notice connection loss",
      waitUntil(() -> slave.getState().contains("connecting")));
  }

  private boolean waitUntil(java.util.concurrent.Callable<Boolean> condition) throws Exception {
    long deadline = System.currentTimeMillis() + TIMEOUT;
    while (System.currentTimeMillis() < deadline) {
      if (Boolean.TRUE.equals(condition.call())) return true;
      Thread.sleep(50);
    }
    return false;
  }

}
