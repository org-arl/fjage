/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.observer;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;

/**
 * A minimal web socket client for the observer, used by the tests. It collects
 * the events published by an observer, reassembling them on newline, since the
 * hub connector coalesces writes and frames do not align with events.
 */
@WebSocket
public class ObserverTestClient {

  private final List<JsonObject> events = new CopyOnWriteArrayList<JsonObject>();
  private final WebSocketClient client = new WebSocketClient();
  private final StringBuilder buf = new StringBuilder();
  private Session session = null;

  /** Connects to an observer. The trailing slash on the context path matters. */
  public ObserverTestClient(int port, String context) throws Exception {
    client.start();
    session = client.connect(this, new URI("ws://localhost:"+port+context+"/ws/"))
                    .get();
  }

  @OnWebSocketMessage
  public void onMessage(String s) {
    synchronized (buf) {
      buf.append(s);
      int i;
      while ((i = buf.indexOf("\n")) >= 0) {
        String line = buf.substring(0, i).trim();
        buf.delete(0, i+1);
        if (line.isEmpty()) continue;
        try {
          events.add(JsonParser.parseString(line).getAsJsonObject());
        } catch (Exception ex) {
          throw new RuntimeException("observer published a bad line: "+line, ex);
        }
      }
    }
  }

  /** Sends a control command. */
  public void send(String json) throws Exception {
    session.getRemote().sendString(json+"\n");
  }

  /** Discards everything collected so far. */
  public void clear() {
    events.clear();
  }

  /** All events collected so far. */
  public List<JsonObject> events() {
    return new ArrayList<JsonObject>(events);
  }

  /** Events collected so far with a given action. */
  public List<JsonObject> events(String action) {
    List<JsonObject> list = new ArrayList<JsonObject>();
    for (JsonObject o: events())
      if (o.has("action") && action.equals(o.get("action").getAsString())) list.add(o);
    return list;
  }

  /** Waits until at least n events with a given action have been collected. */
  public List<JsonObject> await(String action, int n, long timeout) throws InterruptedException {
    long t = System.currentTimeMillis() + timeout;
    while (System.currentTimeMillis() < t) {
      List<JsonObject> list = events(action);
      if (list.size() >= n) return list;
      Thread.sleep(20);
    }
    return events(action);
  }

  public void close() {
    try {
      if (session != null) session.close();
      client.stop();
    } catch (Exception ex) {
      // nothing useful to do in a test teardown
    }
  }

}
