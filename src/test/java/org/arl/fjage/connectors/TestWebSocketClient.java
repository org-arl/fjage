package org.arl.fjage.connectors;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** JDK test client that assembles fragmented text and serializes outgoing sends. */
public abstract class TestWebSocketClient implements WebSocket.Listener, AutoCloseable {
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  protected volatile WebSocket session;
  private final StringBuilder text = new StringBuilder();
  private CompletableFuture<WebSocket> pending = CompletableFuture.completedFuture(null);

  public WebSocket connect(URI uri) throws Exception {
    return HTTP.newWebSocketBuilder().buildAsync(uri, this).get(5, TimeUnit.SECONDS);
  }

  @Override
  public void onOpen(WebSocket socket) {
    session = socket;
    socket.request(1);
  }

  @Override
  public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
    text.append(data);
    if (last) {
      String message = text.toString();
      text.setLength(0);
      onMessage(message);
    }
    socket.request(1);
    return null;
  }

  protected abstract void onMessage(String message);

  public synchronized CompletableFuture<WebSocket> sendAsync(String message) {
    pending = pending.thenCompose(ignored -> session.sendText(message, true));
    return pending;
  }

  public void send(String message) throws Exception {
    sendAsync(message).get(5, TimeUnit.SECONDS);
  }

  @Override public void close() {
    WebSocket current = session;
    if (current != null) current.abort();
  }
}
