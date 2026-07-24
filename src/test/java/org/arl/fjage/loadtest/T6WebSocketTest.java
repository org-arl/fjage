package org.arl.fjage.loadtest;

import static org.junit.Assert.*;

import java.net.ServerSocket;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.arl.fjage.AgentID;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Test;

/**
 * T6 — WebSocket path under load.
 *
 * Opens the master's WebSocket server and drives it with a Jetty WS client acting as
 * a gateway-style peer. Verifies bidirectional bulk transfer with no lost or garbled
 * frames, measures master→client throughput (the PR removed a 10 ms/line sleep that
 * capped it at ~100 msg/s), and checks that an abrupt WS disconnect mid-stream does
 * not NPE or wedge the master.
 */
public class T6WebSocketTest {

  private static final String ALIVE = "{\"alive\": true}";
  private static final Pattern SEQ = Pattern.compile("\"seq\"\\s*:\\s*(\\d+)");
  private static final int C2M = 5000;   // client -> master messages
  private static final int M2C = 3000;   // master -> client messages

  /** Gateway-style WS endpoint: collects lines, auto-answers keep-alive. */
  public static class WsEndpoint extends WebSocketAdapter {
    final Queue<String> lines = new ConcurrentLinkedQueue<>();
    final CountDownLatch connected = new CountDownLatch(1);
    final AtomicInteger malformed = new AtomicInteger();

    @Override
    public void onWebSocketConnect(Session sess) {
      super.onWebSocketConnect(sess);
      connected.countDown();
    }

    @Override
    public void onWebSocketText(String message) {
      for (String line : message.split("\n")) {
        if (line.isEmpty()) continue;
        lines.add(line);
        try {
          com.google.gson.JsonParser.parseString(line);
        } catch (Exception ex) {
          malformed.incrementAndGet();
        }
        if (line.trim().equals(ALIVE)) sendLine(ALIVE);
      }
    }

    void sendLine(String s) {
      try {
        getRemote().sendString(s + "\n");
      } catch (Exception ex) {
        // connection closed
      }
    }

    int countSeqsFrom(String src) {
      Set<Integer> seen = new HashSet<>();
      for (String l : lines) {
        if (!l.contains("\"" + src + "\"")) continue;
        Matcher m = SEQ.matcher(l);
        if (m.find()) seen.add(Integer.parseInt(m.group(1)));
      }
      return seen.size();
    }
  }

  @Test(timeout = 300000)
  public void webSocketGatewayLoad() throws Exception {
    LogCapture logs = new LogCapture();
    final MultiContainerFixture fx = MultiContainerFixture.create(0);   // master only
    WebSocketClient client = new WebSocketClient();
    try {
      LoadAgents.ReceiverAgent rxM = new LoadAgents.ReceiverAgent();
      fx.master.add("rx_m", rxM);

      AtomicBoolean go1 = new AtomicBoolean(false);
      LoadAgents.SenderAgent txWs = new LoadAgents.SenderAgent(
          Collections.singletonList(new AgentID("wsrx")), M2C, go1, 0);
      fx.master.add("tx_ws", txWs);
      AtomicBoolean go2 = new AtomicBoolean(false);
      LoadAgents.SenderAgent txWs2 = new LoadAgents.SenderAgent(
          Collections.singletonList(new AgentID("wsrx")), M2C, go2, 0);
      fx.master.add("tx_ws2", txWs2);

      int wsPort;
      ServerSocket ss = new ServerSocket(0);
      wsPort = ss.getLocalPort();
      ss.close();
      assertTrue("failed to open WS server", fx.master.openWebSocketServer(wsPort, "/ws"));

      client.start();
      WsEndpoint ep = new WsEndpoint();
      Session session = client.connect(ep, URI.create("ws://127.0.0.1:" + wsPort + "/ws"),
          new ClientUpgradeRequest()).get(10, TimeUnit.SECONDS);
      assertTrue(ep.connected.await(5, TimeUnit.SECONDS));

      // register interest in "wsrx" so the master relays to us
      ep.sendLine("{\"action\": \"wantsMessagesFor\", \"agentIDs\": [\"wsrx\"]}");
      TestUtil.waitUntil("ws handler alive on master", () -> fx.countAliveHandlers() == 1, 10000);

      // ---- client -> master storm ----
      long t0 = System.currentTimeMillis();
      for (int i = 0; i < C2M; i++) {
        String json = "{\"action\": \"send\", \"relay\": true, \"message\": {"
            + "\"clazz\": \"org.arl.fjage.loadtest.LoadAgents$SeqMsg\", \"data\": {"
            + "\"src\": \"wsc\", \"seq\": " + i + ", \"tns\": " + System.nanoTime()
            + ", \"msgID\": \"ws-" + i + "\", \"perf\": \"INFORM\","
            + " \"recipient\": \"rx_m\", \"sender\": \"wsc\"}}}";
        session.getRemote().sendString(json + "\n");
      }
      TestUtil.waitUntil("master received all WS messages", () -> rxM.stats.countFrom("wsc") >= C2M, 60000);
      long c2mMs = System.currentTimeMillis() - t0;
      assertEquals("duplicates on client->master path", 0, rxM.stats.dups.get());

      // ---- master -> client storm (throughput: PR removed the 10 ms/line cap) ----
      t0 = System.currentTimeMillis();
      go1.set(true);
      TestUtil.waitUntil("client received all master messages", () -> ep.countSeqsFrom("tx_ws") >= M2C, 120000);
      long m2cMs = System.currentTimeMillis() - t0;
      double m2cRate = 1000.0 * M2C / m2cMs;

      assertEquals("malformed/garbled WS frames", 0, ep.malformed.get());

      System.out.println("=== T6 results ===");
      System.out.println("client->master: " + C2M + " msgs in " + c2mMs + " ms ("
          + String.format("%.0f", 1000.0 * C2M / c2mMs) + " msg/s), lost=0 dups=0");
      System.out.println("master->client: " + M2C + " msgs in " + m2cMs + " ms ("
          + String.format("%.0f", m2cRate) + " msg/s)");

      // the removed 10 ms/line sleep capped this direction at ~100 msg/s; with the fix
      // this must be comfortably faster (master-branch comparison runs will fail here)
      assertTrue("master->client throughput " + String.format("%.0f", m2cRate)
          + " msg/s suggests the per-line throttle is still in effect", m2cRate > 300);

      // ---- abrupt disconnect mid-stream ----
      go2.set(true);          // second burst starts flowing
      TestUtil.sleep(200);
      session.disconnect();   // harsh close, no goodbye
      TestUtil.sleep(2000);

      assertNotNull("master directory wedged after WS disconnect", fx.master.getAgents());
      TestUtil.waitUntil("ws handler cleaned up after disconnect", () -> {
        fx.master.getAgents();
        return fx.master.getConnectionHandlers().length == 0;
      }, 20000);
      assertTrue("SEVERE logs: " + logs.severes(), logs.severes().isEmpty());
      System.out.println("abrupt disconnect mid-stream: clean, severes=0");
    } finally {
      try {
        client.stop();
      } catch (Exception ex) {
        // best effort
      }
      fx.teardown();
      logs.close();
    }
  }
}
