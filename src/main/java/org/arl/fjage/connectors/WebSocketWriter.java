/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;

/**
 * Non-blocking, bounded, callback-driven outbound write pump for a single
 * WebSocket session.
 *
 * Jetty permits only one outstanding asynchronous write per endpoint, so this
 * class serializes sends: messages are queued and exactly one {@code sendString}
 * is kept in flight at a time, draining the next only after the previous one's
 * callback fires. This guarantees ordered, loss-free delivery (no
 * {@code WritePendingException}).
 *
 * The bounded queue provides backpressure: a slow or dead client whose backlog
 * exceeds {@code maxQueue} is disconnected rather than buffered without limit.
 * A per-write watchdog, armed only while a send is in flight, disconnects a
 * client whose write does not complete within {@code writeTimeout} (the client
 * has stopped draining its socket). Neither mechanism ever blocks the producer,
 * so one slow/dead client cannot stall a thread shared with other clients.
 */
class WebSocketWriter {

  static final int DEFAULT_MAX_QUEUE = 1024;
  static final long DEFAULT_WRITE_TIMEOUT = 30000;

  private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "fjage-ws-write-watchdog");
    t.setDaemon(true);
    return t;
  });

  private final Session session;
  private final Runnable onClose;
  private final int maxQueue;
  private final long writeTimeout;
  private final Logger log;

  private final Object lock = new Object();
  private final Deque<String> queue = new ArrayDeque<>();
  private boolean writing = false;
  private boolean closed = false;
  private ScheduledFuture<?> watchdogTask = null;

  WebSocketWriter(Session session, Runnable onClose, Logger log) {
    this(session, onClose, log, DEFAULT_MAX_QUEUE, DEFAULT_WRITE_TIMEOUT);
  }

  WebSocketWriter(Session session, Runnable onClose, Logger log, int maxQueue, long writeTimeout) {
    this.session = session;
    this.onClose = onClose;
    this.log = log;
    this.maxQueue = maxQueue;
    this.writeTimeout = writeTimeout;
  }

  /**
   * Queue a message for delivery. Non-blocking. If the queue is full (the client
   * is not draining), the connection is force-closed.
   */
  void enqueue(String s) {
    synchronized (lock) {
      if (closed) return;
      if (queue.size() >= maxQueue) {
        log.warning("WebSocket write queue overflow (>=" + maxQueue + "), disconnecting " + remoteAddress());
        forceClose();
        return;
      }
      queue.add(s);
    }
    kick();
  }

  // start the next write if none is in flight; sendString is called outside the lock
  private void kick() {
    String s;
    synchronized (lock) {
      if (writing || closed || queue.isEmpty()) return;
      s = queue.poll();
      writing = true;
      watchdogTask = WATCHDOG.schedule(this::onWriteTimeout, writeTimeout, TimeUnit.MILLISECONDS);
    }
    try {
      session.getRemote().sendString(s, new WriteCallback() {
        @Override
        public void writeSuccess() {
          synchronized (lock) {
            cancelWatchdog();
            writing = false;
          }
          kick();
        }

        @Override
        public void writeFailed(Throwable cause) {
          if (cause instanceof java.nio.channels.ClosedChannelException) {
            log.info("Unexpected " + cause + " while sending to " + remoteAddress() + ".");
          } else {
            log.log(Level.WARNING, "Error sending websocket message: ", cause);
          }
          forceCloseLocked();
        }
      });
    } catch (Throwable t) {
      log.log(Level.WARNING, "Error sending websocket message: ", t);
      forceCloseLocked();
    }
  }

  private void onWriteTimeout() {
    log.warning("WebSocket write stalled (>" + writeTimeout + " ms), disconnecting " + remoteAddress());
    forceCloseLocked();
  }

  // acquire the lock, tear down state, then disconnect outside the lock
  private void forceCloseLocked() {
    synchronized (lock) {
      forceClose();
    }
  }

  // must be called while holding the lock; performs the disconnect outside it
  private void forceClose() {
    if (closed) return;
    closed = true;
    cancelWatchdog();
    queue.clear();
    writing = false;
    // disconnect outside the lock to avoid onClose -> close() re-entrancy under the lock
    WATCHDOG.execute(() -> {
      try {
        if (session.isOpen()) session.disconnect();
      } catch (Exception e) {
        log.log(Level.FINE, "Error disconnecting websocket: ", e);
      }
      if (onClose != null) onClose.run();
    });
  }

  /**
   * Idempotently release resources. Called from the connector's onClose handler.
   */
  void close() {
    synchronized (lock) {
      if (closed) return;
      closed = true;
      cancelWatchdog();
      queue.clear();
      writing = false;
    }
  }

  private void cancelWatchdog() {
    if (watchdogTask != null) {
      watchdogTask.cancel(false);
      watchdogTask = null;
    }
  }

  private String remoteAddress() {
    try {
      return String.valueOf(session.getRemoteAddress());
    } catch (Exception e) {
      return "[unknown]";
    }
  }
}
