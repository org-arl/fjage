package org.arl.fjage.connectors;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

/** Shared native WebSocket operations for connector writer threads. */
final class WebSocketSupport {
  private WebSocketSupport() {}

  static String address(Session session) {
    var address = session.getRemoteSocketAddress();
    if (address instanceof InetSocketAddress inet) return inet.getHostString()+":"+inet.getPort();
    return String.valueOf(address);
  }

  /** Waits on the connector's writer thread to preserve ordering and bound send time. */
  static boolean sendText(Session session, String text, Logger log) {
    if (session == null || !session.isOpen()) return false;
    try {
      Callback.Completable.with(callback -> session.sendText(text, callback)).get(2, TimeUnit.SECONDS);
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      session.disconnect();
    } catch (TimeoutException ex) {
      log.fine("Sending timed out. Closing connection to "+session.getRemoteSocketAddress());
      session.disconnect();
    } catch (ExecutionException ex) {
      if (!(ex.getCause() instanceof java.nio.channels.ClosedChannelException))
        log.log(Level.WARNING, "Error sending websocket message", ex.getCause());
      session.disconnect();
    } catch (RuntimeException ex) {
      log.log(Level.WARNING, "Error sending websocket message", ex);
      session.disconnect();
    }
    return false;
  }
}
