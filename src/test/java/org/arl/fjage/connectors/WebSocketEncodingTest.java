package org.arl.fjage.connectors;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.Test;

public class WebSocketEncodingTest {

  public static class Endpoint extends TestWebSocketClient {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    @Override protected void onMessage(String text) { messages.add(text); }
  }

  @Test(timeout = 30000)
  public void hubKeepsSplitCharactersInBothOutputModes() throws Exception {
    for (boolean lineMode : new boolean[] {false, true}) {
      int port;
      try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
      WebSocketHubConnector hub = new WebSocketHubConnector(port, "/utf8", lineMode);
      Endpoint endpoint = new Endpoint();
      try {
        endpoint.connect(URI.create("ws://localhost:" + port + "/utf8"));
        byte[] bytes = "caf\u00e9 \u4e2d\u6587 \ud83d\ude42\n".getBytes(StandardCharsets.UTF_8);
        endpoint.send(new String(bytes, StandardCharsets.UTF_8));
        byte[] incoming = new byte[bytes.length];
        int offset = 0;
        while (offset < incoming.length) {
          int count = hub.getInputStream().read(incoming, offset, incoming.length - offset);
          assertTrue(count > 0);
          offset += count;
        }
        assertArrayEquals(bytes, incoming);

        // Split the two-byte character after its first byte.
        hub.getOutputStream().write(bytes, 0, 4);
        StringBuilder output = new StringBuilder();
        if (!lineMode) {
          String prefix = endpoint.messages.poll(5, TimeUnit.SECONDS);
          assertEquals("caf", prefix);
          output.append(prefix);
        }
        for (int i = 4; i < bytes.length; i++) hub.getOutputStream().write(bytes[i]);
        while (output.indexOf("\n") < 0) {
          String chunk = endpoint.messages.poll(5, TimeUnit.SECONDS);
          assertNotNull("Output did not complete", chunk);
          output.append(chunk);
        }
        assertEquals(new String(bytes, StandardCharsets.UTF_8), output.toString());
      } finally {
        endpoint.close();
        hub.close();
        if (WebServer.hasInstance(port)) WebServer.getInstance(port).stop();
      }
    }
  }
}
