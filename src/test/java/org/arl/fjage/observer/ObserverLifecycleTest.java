/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.observer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.arl.fjage.AgentID;
import org.arl.fjage.Container;
import org.arl.fjage.Platform;
import org.arl.fjage.RealTimePlatform;
import org.arl.fjage.connectors.WebServer;
import org.junit.Test;

/**
 * Checks that an observer installs and removes itself cleanly, so that killing
 * it leaves no listener behind and frees its port for a new one.
 */
public class ObserverLifecycleTest {

  @Test
  public void testInstallAndRemove() throws Exception {
    Logger.getLogger("org.arl.fjage").setLevel(Level.WARNING);
    int port = freePort();
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    try {
      assertEquals(0, listenerCount(container));
      container.add("observer", new ObserverAgent(port, "/observer"));
      platform.start();
      waitFor(container, 1);
      assertEquals("listener not installed", 1, listenerCount(container));

      container.kill(new AgentID("observer"));
      waitFor(container, 0);
      assertEquals("listener not removed on kill", 0, listenerCount(container));

      // the port is free again, so a replacement observer can take it over
      container.add("observer2", new ObserverAgent(port, "/observer"));
      waitFor(container, 1);
      assertEquals("replacement observer did not install", 1, listenerCount(container));
    } finally {
      platform.shutdown();
    }
  }

  private void waitFor(Container c, int n) throws Exception {
    long t = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < t && listenerCount(c) != n) Thread.sleep(20);
  }

  @SuppressWarnings("unchecked")
  private int listenerCount(Container c) throws Exception {
    Field f = Container.class.getDeclaredField("listeners");
    f.setAccessible(true);
    Set<Object> listeners = (Set<Object>)f.get(c);
    synchronized (listeners) {
      return listeners.size();
    }
  }

  private static int freePort() throws Exception {
    ServerSocket s = new ServerSocket(0);
    try {
      return s.getLocalPort();
    } finally {
      s.close();
    }
  }

  @Test
  public void testAutoPortFollowsTheApplicationWebServer() throws Exception {
    Logger.getLogger("org.arl.fjage").setLevel(Level.WARNING);
    int port = freePort();
    WebServer app = WebServer.getInstance(port);          // the application's web server
    Platform platform = new RealTimePlatform();
    Container container = new Container(platform);
    try {
      ObserverAgent observer = new ObserverAgent();        // no port given
      container.add("observer", observer);
      platform.start();
      long t = System.currentTimeMillis() + 5000;
      while (System.currentTimeMillis() < t && !observer.getUrl().contains(":"+port+"/")) Thread.sleep(20);
      assertTrue("observer did not follow the application web server: "+observer.getUrl(),
                 observer.getUrl().contains(":"+port+"/observer/"));
    } finally {
      platform.shutdown();
      app.stop();
    }
  }

  @Test
  public void testEndpointNaming() {
    assertTrue(ObserverAgent.endpoint(null) == null);
    assertEquals("a", ObserverAgent.endpoint(new org.arl.fjage.AgentID("a")));
    assertEquals("#t", ObserverAgent.endpoint(new org.arl.fjage.AgentID("t", true)));
  }

}
