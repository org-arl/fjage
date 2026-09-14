/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import org.arl.fjage.RealTimePlatform;
import org.junit.Test;

import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RealTimePlatformTest {

  @Test
  public void testScheduledTaskExceptionIsLoggedAndDoesNotStopTimer() throws InterruptedException {
    Logger log = Logger.getLogger(RealTimePlatform.class.getName());
    Level oldLevel = log.getLevel();
    AtomicReference<LogRecord> captured = new AtomicReference<>();
    CountDownLatch logged = new CountDownLatch(1);
    Handler handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        captured.set(record);
        logged.countDown();
      }

      @Override
      public void flush() {
        // do nothing
      }

      @Override
      public void close() {
        // do nothing
      }
    };
    log.setLevel(Level.ALL);
    log.addHandler(handler);

    try {
      RealTimePlatform platform = new RealTimePlatform();
      IllegalStateException failure = new IllegalStateException("scheduled failure");
      CountDownLatch continued = new CountDownLatch(1);
      platform.schedule(new TimerTask() {
        @Override
        public void run() {
          throw failure;
        }
      }, 0);
      platform.schedule(new TimerTask() {
        @Override
        public void run() {
          continued.countDown();
        }
      }, 0);

      assertTrue("scheduled task failure was not logged", logged.await(1, TimeUnit.SECONDS));
      assertSame(failure, captured.get().getThrown());
      assertTrue("scheduler stopped after task failure", continued.await(1, TimeUnit.SECONDS));
    } finally {
      log.removeHandler(handler);
      log.setLevel(oldLevel);
    }
  }
}
