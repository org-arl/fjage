package org.arl.fjage.remote;

import static org.junit.Assert.*;

import java.net.*;
import java.util.concurrent.*;
import org.arl.fjage.*;
import org.arl.fjage.connectors.WebServer;
import org.arl.fjage.connectors.TestWebSocketClient;
import org.junit.Test;

public class UnicodeConnectionTest {

  private static final String TEXT = "caf\u00e9 \u4e2d\u6587 \ud83d\ude42";

  private static MasterContainer echoServer(Platform platform) {
    MasterContainer master = new MasterContainer(platform);
    master.add("echo", new Agent() {
      @Override public void init() {
        add(new MessageBehavior() {
          @Override public void onReceive(Message request) {
            GenericMessage response = new GenericMessage(request, Performative.INFORM);
            response.put("text", ((GenericMessage)request).get("text"));
            send(response);
          }
        });
      }
    });
    platform.start();
    return master;
  }

  @Test(timeout = 15000)
  public void tcpJsonRoundTrip() throws Exception {
    Platform platform = new RealTimePlatform();
    MasterContainer master = echoServer(platform);
    try (Gateway gateway = new Gateway("localhost", master.getPort())) {
      GenericMessage request = new GenericMessage(new AgentID("echo"), Performative.REQUEST);
      request.put("text", TEXT);
      Message response = gateway.request(request, 5000);
      assertTrue(response instanceof GenericMessage);
      assertEquals(TEXT, ((GenericMessage)response).get("text"));
    } finally {
      platform.shutdown();
    }
  }

  public static class Endpoint extends TestWebSocketClient {
    final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    @Override protected void onMessage(String text) {
      for (String line : text.split("\n")) {
        JsonMessage message = JsonMessage.fromJson(line);
        if (message.message != null) messages.add(message.message);
        if (message.action == Action.AGENTS) {
          JsonMessage response = new JsonMessage();
          response.id = message.id;
          response.inResponseTo = Action.AGENTS;
          response.agentIDs = new AgentID[] {new AgentID("gateway-unicode")};
          sendAsync(response.toJson() + "\n");
        }
      }
    }
  }

  @Test(timeout = 15000)
  public void webSocketJsonRoundTrip() throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
    Platform platform = new RealTimePlatform();
    MasterContainer master = echoServer(platform);
    Endpoint endpoint = new Endpoint();
    try {
      assertTrue(master.openWebSocketServer(port, "/json"));
      endpoint.connect(URI.create("ws://localhost:" + port + "/json"));
      GenericMessage request = new GenericMessage(new AgentID("echo"), Performative.REQUEST);
      request.setSender(new AgentID("gateway-unicode"));
      request.put("text", TEXT);
      JsonMessage envelope = new JsonMessage();
      envelope.action = Action.SEND;
      envelope.message = request;
      endpoint.send(envelope.toJson() + "\n");
      Message response = endpoint.messages.poll(5, TimeUnit.SECONDS);
      assertTrue(response instanceof GenericMessage);
      assertEquals(TEXT, ((GenericMessage)response).get("text"));
    } finally {
      endpoint.close();
      platform.shutdown();
      if (WebServer.hasInstance(port)) WebServer.getInstance(port).stop();
    }
  }
}
