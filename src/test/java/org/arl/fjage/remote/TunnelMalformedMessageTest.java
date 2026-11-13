/******************************************************************************

Copyright (c) 2026, Chinmay Pendharkar

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.remote;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.arl.fjage.Container;
import org.arl.fjage.Message;
import org.arl.fjage.RealTimePlatform;
import org.arl.fjage.connectors.ConnectionListener;
import org.arl.fjage.connectors.Connector;
import org.junit.Test;

public class TunnelMalformedMessageTest {

  private static class RecordingContainer extends Container {
    final List<Message> messages = new ArrayList<>();

    RecordingContainer() {
      super(new RealTimePlatform());
    }

    @Override
    public boolean send(Message message) {
      messages.add(message);
      return true;
    }
  }

  private static class TestConnector implements Connector {
    private final InputStream input;
    boolean closed;

    TestConnector(InputStream input) {
      this.input = input;
    }

    @Override public String getName() { return "in-memory tunnel"; }
    @Override public InputStream getInputStream() { return input; }
    @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
    @Override public boolean isReliable() { return true; }
    @Override public boolean waitOutputCompletion(long timeout) { return true; }
    @Override public void setConnectionListener(ConnectionListener listener) { }
    @Override public String[] connections() { return new String[0]; }
    @Override public void close() { closed = true; }
  }

  private static class TestTunnel extends Tunnel {
    final RecordingContainer container = new RecordingContainer();

    TestTunnel() {
      super(0);
      readExecutor = Executors.newSingleThreadExecutor();
      log.setLevel(Level.OFF);
    }

    @Override public Container getContainer() { return container; }

    @Override
    protected void removeConnector(Connector connector) {
      connectors.remove(connector);
      connector.close();
    }

    void read(TestConnector connector) throws InterruptedException {
      connectors.add(connector);
      try {
        monitor(7, connector);
        readExecutor.shutdown();
        assertTrue("Tunnel reader did not finish", readExecutor.awaitTermination(5, TimeUnit.SECONDS));
      } finally {
        readExecutor.shutdownNow();
      }
    }
  }

  @Test
  public void malformedLinesDoNotDiscardLaterMessages() throws InterruptedException {
    String valid = "{\"message\":{\"clazz\":\"org.arl.fjage.Message\",\"data\":{"
      + "\"msgID\":\"valid-id\",\"sender\":\"remote\",\"recipient\":\"local\"}}}";
    TestConnector connector = new TestConnector(new ByteArrayInputStream(
      ("{broken\n" + valid + "\n{\"message\":{\"clazz\":\"org.arl.fjage.Message\","
        + "\"data\":{\"perf\":{}}}}\n" + valid + "\n").getBytes(StandardCharsets.UTF_8)));
    TestTunnel tunnel = new TestTunnel();
    tunnel.read(connector);
    assertEquals(2, tunnel.container.messages.size());
    for (Message message : tunnel.container.messages) {
      assertEquals("valid-id", message.getMessageID());
      assertEquals("remote@7", message.getSender().getName());
      assertEquals("local", message.getRecipient().getName());
    }
    assertTrue("EOF should close the connection", connector.closed);
    assertTrue(tunnel.connectors.isEmpty());
  }

  @Test
  public void transportFailureStillClosesConnection() throws InterruptedException {
    TestConnector connector = new TestConnector(new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("transport failed");
      }
    });
    TestTunnel tunnel = new TestTunnel();
    tunnel.read(connector);
    assertTrue(connector.closed);
    assertTrue(tunnel.connectors.isEmpty());
    assertTrue(tunnel.container.messages.isEmpty());
  }
}
