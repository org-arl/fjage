/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
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

  @Test
  public void rawModePassesAllBytes() throws Exception {
    TcpHubConnector hub = new TcpHubConnector(0, false);
    try (Socket client = new Socket("127.0.0.1", hub.getPort())) {
      byte[] sent = new byte[256];
      for (int i = 0; i < sent.length; i++)
        sent[i] = (byte)i;
      client.getOutputStream().write(sent);
      assertArrayEquals(sent, readHub(hub, sent.length));
    } finally {
      hub.close();
    }
  }

  @Test
  public void telnetModeFiltersControlBytes() throws Exception {
    TcpHubConnector hub = new TcpHubConnector(0, true);
    try (Socket client = new Socket("127.0.0.1", hub.getPort())) {
      byte[] negotiation = {
        (byte)255, (byte)251, 1,
        (byte)255, (byte)251, 3,
        (byte)255, (byte)252, 34,
        27, 91, 63, 50, 48, 48, 52, 104
      };
      assertArrayEquals(negotiation, readSocket(client, negotiation.length));
      byte[] sent = {
        'a', '\r', 0, 'b',
        (byte)255, (byte)253, 1,    // IAC DO ECHO
        (byte)255, (byte)255,       // escaped 255
        'c', 'd'
      };
      client.getOutputStream().write(sent);
      byte[] expected = { 'a', '\r', 'b', (byte)255, 'c', 'd' };
      assertArrayEquals(expected, readHub(hub, expected.length));
    } finally {
      hub.close();
    }
  }

  @Test
  public void telnetModeClosesOnCtrlD() throws Exception {
    TcpHubConnector hub = new TcpHubConnector(0, true);
    try (Socket client = new Socket("127.0.0.1", hub.getPort())) {
      client.setSoTimeout(TIMEOUT);
      readSocket(client, 17);
      client.getOutputStream().write(4);
      assertEquals(-1, client.getInputStream().read());
    } finally {
      hub.close();
    }
  }

  private static final int TIMEOUT = 5000;

  private static byte[] readHub(TcpHubConnector hub, int n) throws Exception {
    InputStream in = hub.getInputStream();
    long deadline = System.currentTimeMillis() + TIMEOUT;
    ByteArrayOutputStream received = new ByteArrayOutputStream();
    while (received.size() < n) {
      if (in.available() > 0) received.write(in.read());
      else if (System.currentTimeMillis() > deadline) break;
      else Thread.sleep(10);
    }
    Thread.sleep(100);
    assertEquals("unexpected extra bytes", 0, in.available());
    return received.toByteArray();
  }

  private static byte[] readSocket(Socket client, int n) throws Exception {
    client.setSoTimeout(TIMEOUT);
    byte[] buf = new byte[n];
    new DataInputStream(client.getInputStream()).readFully(buf);
    return buf;
  }

}
