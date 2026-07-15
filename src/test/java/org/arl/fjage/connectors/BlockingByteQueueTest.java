/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class BlockingByteQueueTest {

  @Test
  public void closeUnblocksOutputStreamLineReader() throws Exception {
    PseudoOutputStream stream = new PseudoOutputStream();
    CountDownLatch reading = new CountDownLatch(1);
    AtomicReference<String> result = new AtomicReference<String>();
    Thread reader = new Thread(() -> {
      reading.countDown();
      result.set(stream.readLine());
    });
    reader.start();
    assertTrue("Reader did not start", reading.await(1, TimeUnit.SECONDS));
    assertTrue("Reader did not block", waitForState(reader, Thread.State.WAITING));

    stream.close();
    reader.join(1000);

    assertFalse("Reader remained blocked after stream close", reader.isAlive());
    assertNull(result.get());
    assertEquals(-1, stream.available());
  }

  @Test
  public void closedQueueReturnsEndOfStreamAndRejectsWrites() throws Exception {
    BlockingByteQueue queue = new BlockingByteQueue();
    queue.close();

    assertEquals(-1, queue.read());
    assertNull(queue.readAvailable());
    assertNull(queue.readDelimited((byte) '\n'));
    assertEquals(-1, queue.available());
    assertFalse(queue.write('x'));

    PseudoInputStream stream = new PseudoInputStream();
    stream.close();
    try {
      stream.write('x');
      fail("Writing to a closed stream should fail");
    } catch (IOException expected) {
      // expected
    }
  }

  private boolean waitForState(Thread thread, Thread.State state) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (System.nanoTime() < deadline) {
      if (thread.getState() == state) return true;
      Thread.yield();
    }
    return false;
  }
}