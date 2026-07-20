package org.arl.fjage.loadtest;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A raw TCP client speaking the fjage newline-JSON protocol under scripted control.
 * Used to simulate a slave that misbehaves: ignores requests, sends malformed JSON,
 * or never completes the ALIVE handshake.
 */
public class FakeSlave implements Closeable {

  public static final String ALIVE = "{\"alive\": true}";

  private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern ACTION = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");

  private final Socket sock;
  private final OutputStream out;
  private final Thread reader;

  public volatile boolean respondToAlive;
  public volatile boolean respondToRequests;
  public final Queue<String> received = new ConcurrentLinkedQueue<String>();
  public final AtomicInteger requestsSeen = new AtomicInteger();

  public FakeSlave(int port, boolean respondToAlive, boolean respondToRequests) throws IOException {
    this.respondToAlive = respondToAlive;
    this.respondToRequests = respondToRequests;
    sock = new Socket("localhost", port);
    sock.setTcpNoDelay(true);
    out = sock.getOutputStream();
    final BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
    reader = new Thread("fakeslave-reader") {
      @Override
      public void run() {
        try {
          String line;
          while ((line = in.readLine()) != null) {
            received.add(line);
            handle(line);
          }
        } catch (IOException ex) {
          // connection closed
        }
      }
    };
    reader.setDaemon(true);
    reader.start();
  }

  private void handle(String line) {
    if (line.equals(ALIVE)) {
      if (respondToAlive) sendLine(ALIVE);
      return;
    }
    Matcher am = ACTION.matcher(line);
    if (!am.find()) return;
    String action = am.group(1);
    Matcher im = ID.matcher(line);
    String id = im.find() ? im.group(1) : null;
    if (id == null) return;
    requestsSeen.incrementAndGet();
    if (!respondToRequests) return;
    // minimal well-formed empty responses per action
    if (action.equals("agents"))
      sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"agents\", \"agentIDs\": [], \"agentTypes\": []}");
    else if (action.equals("containsAgent"))
      sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"containsAgent\", \"answer\": false}");
    else if (action.equals("services"))
      sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"services\", \"services\": []}");
    else if (action.equals("agentForService"))
      sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"agentForService\"}");
    else if (action.equals("agentsForService"))
      sendLine("{\"id\": \"" + id + "\", \"inResponseTo\": \"agentsForService\", \"agentIDs\": []}");
  }

  public synchronized void sendLine(String s) {
    try {
      out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (IOException ex) {
      // connection closed
    }
  }

  public boolean isConnected() {
    return sock.isConnected() && !sock.isClosed() && reader.isAlive();
  }

  @Override
  public void close() throws IOException {
    sock.close();
  }
}
