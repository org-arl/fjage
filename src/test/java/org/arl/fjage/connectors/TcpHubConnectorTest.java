/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.Test;

public class TcpHubConnectorTest {

  @Test(expected = UncheckedIOException.class)
  public void busyPortFailsToBind() throws Exception {
    try (ServerSocket blocker = new ServerSocket(0)) {
      new TcpHubConnector(blocker.getLocalPort());
    }
  }

  @Test(expected = UncheckedIOException.class)
  public void portInUseOnOneAddressFailsToBind() throws Exception {
    // e.g. a web server listening only on 127.0.0.1
    try (ServerSocket blocker = new ServerSocket()) {
      blocker.setReuseAddress(true);
      blocker.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      new TcpHubConnector(blocker.getLocalPort());
    }
  }

  @Test
  public void secondHubOnSamePortFailsAndFirstStillWorks() throws Exception {
    TcpHubConnector first = new TcpHubConnector(0);
    try {
      int port = first.getPort();
      assertTrue("port should be known after construction", port > 0);
      try {
        new TcpHubConnector(port);
        fail("second hub on the same port should not bind");
      } catch (UncheckedIOException ex) {
        // expected
      }
      try (Socket client = new Socket("127.0.0.1", port)) {
        assertTrue(client.isConnected());
      }
    } finally {
      first.close();
    }
  }

  @Test
  public void portIsFreeAfterClose() throws Exception {
    TcpHubConnector hub = new TcpHubConnector(0);
    int port = hub.getPort();
    hub.close();
    TcpHubConnector again = new TcpHubConnector(port);
    again.close();
  }

}
