/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.After;
import org.junit.Test;

public class WebSocketHubConnectorTest {

  private int port = -1;

  @After
  public void teardown() {
    if (port > 0 && WebServer.hasInstance(port)) WebServer.getInstance(port).stop();
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  @Test
  public void busyPortFails() throws Exception {
    try (ServerSocket blocker = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      port = blocker.getLocalPort();
      try {
        new WebSocketHubConnector(port, "/ws");
        fail("connector on a busy port should not be created");
      } catch (UncheckedIOException ex) {
        // expected
      }
      assertFalse("failed connector should not leave its handler behind", WebServer.getInstance(port).hasHandler("/ws"));
    }
  }

  @Test
  public void differentContextsShareAPort() throws Exception {
    port = freePort();
    WebSocketHubConnector a = new WebSocketHubConnector(port, "/a");
    WebSocketHubConnector b = new WebSocketHubConnector(port, "/b");
    WebServer server = WebServer.getInstance(port);
    assertTrue(server.hasHandler("/a"));
    assertTrue(server.hasHandler("/b"));
    a.close();
    b.close();
  }

  @Test
  public void duplicateContextFailsAndFirstKeepsWorking() throws Exception {
    port = freePort();
    WebSocketHubConnector first = new WebSocketHubConnector(port, "/ws");
    try {
      new WebSocketHubConnector(port, "/ws");
      fail("second connector on the same context should not be created");
    } catch (UncheckedIOException ex) {
      // expected
    }
    WebServer server = WebServer.getInstance(port);
    assertTrue(server.start());
    assertTrue(server.hasHandler("/ws"));
    first.close();
    assertFalse(server.hasHandler("/ws"));
  }

}
