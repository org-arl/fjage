/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.time.Instant;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Proxy log handler to allow discrete time stamps in logs when the discrete
 * event platform is used.
 *
 * @author  Mandar Chitre
 */
public class LogHandlerProxy extends Handler {

  ////////// Private attributes

  private Handler delegate;
  private TimestampProvider timesrc;

  ////////// Interface methods

  /**
   * Creates a proxy log handler with a given delegate using timestamps from the
   * specified TimestampProvider.
   *
   * @param delegate log handler delegate.
   * @param timesrc TimestampProvider to use for timestamps.
   */
  public LogHandlerProxy(Handler delegate, TimestampProvider timesrc) {
    this.delegate = delegate;
    this.timesrc = timesrc;
  }

  /**
   * Sets the current TimestampProvider used for log timestamp.
   *
   * @param timesrc TimestampProvider to use for timestamps, null to leave timestamps unchanged.
   */
  public void setTimestampProvider(TimestampProvider timesrc) {
    this.timesrc = timesrc;
  }

  /**
   * Publish log record with modified timestamp.
   *
   * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
   */
  @Override
  public void publish(LogRecord rec) {
    if (timesrc != null) rec.setInstant(Instant.ofEpochMilli(timesrc.currentTimeMillis()));
    delegate.publish(rec);
  }

  /**
   * Close log output stream.
   *
   * @see java.util.logging.Handler#close()
   */
  @Override
  public void close() {
    delegate.close();
  }

  /**
   * Flush log output stream.
   *
   * @see java.util.logging.Handler#flush()
   */
  @Override
  public void flush() {
    delegate.flush();
  }

  /**
   * Installs this handler for all handlers in the root logger.
   *
   * @param timesrc TimestampProvider to use for timestamps.
   * @param log logger to install on, null for root logger.
   */
  public static void install(TimestampProvider timesrc, Logger log) {
    if (log == null) log = Logger.getLogger("");
    Handler[] h = log.getHandlers();
    if (h.length == 0) {
      // no handler installed, need to move up the parent chain to find appropriate handler to proxy
      Logger log1 = log;
      while (h.length == 0) {
        log1 = log1.getParent();
        if (log1 == null) return;
        h = log1.getHandlers();
      }
      for (Handler h1: h) {
        LogHandlerProxy h2 = new LogHandlerProxy(h1, timesrc);
        log.setUseParentHandlers(false);
        log.addHandler(h2);
      }
    } else {
      // handler installed, remove it and replace with proxy
      for (Handler h1: h)
        if (h1 instanceof LogHandlerProxy) ((LogHandlerProxy)h1).setTimestampProvider(timesrc);
        else {
          LogHandlerProxy h2 = new LogHandlerProxy(h1, timesrc);
          log.removeHandler(h1);
          log.addHandler(h2);
        }
    }
  }

}

