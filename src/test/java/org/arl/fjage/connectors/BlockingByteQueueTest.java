/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.connectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class BlockingByteQueueTest {

  @Test
  public void largeQueueWritesCopyTheSourceBuffer() {
    byte[] expected = new byte[BlockingByteQueue.BLOCK_SIZE + 1];
    Arrays.fill(expected, (byte)42);
    for (boolean slice : new boolean[] {false, true}) {
      BlockingByteQueue queue = new BlockingByteQueue();
      byte[] source = expected.clone();
      assertTrue(slice ? queue.write(source, 0, source.length) : queue.write(source));
      Arrays.fill(source, (byte)0);
      assertArrayEquals(expected, queue.readAvailable());
    }
  }

  @Test
  public void largeInputStreamWritesCopyTheSourceBuffer() throws IOException {
    byte[] expected = new byte[BlockingByteQueue.BLOCK_SIZE + 1];
    Arrays.fill(expected, (byte)42);
    byte[] source = expected.clone();
    try (PseudoInputStream stream = new PseudoInputStream()) {
      stream.write(source);
      Arrays.fill(source, (byte)0);
      byte[] actual = new byte[expected.length];
      assertEquals(actual.length, stream.read(actual));
      assertArrayEquals(expected, actual);
    }
  }

  @Test
  public void largeOutputStreamWritesCopyTheSourceBuffer() throws IOException {
    byte[] expected = new byte[BlockingByteQueue.BLOCK_SIZE + 1];
    Arrays.fill(expected, (byte)42);
    for (boolean slice : new boolean[] {false, true}) {
      byte[] source = expected.clone();
      try (PseudoOutputStream stream = new PseudoOutputStream()) {
        if (slice) stream.write(source, 0, source.length);
        else stream.write(source);
        Arrays.fill(source, (byte)0);
        assertArrayEquals(expected, stream.readAvailable());
      }
    }
  }

  @Test
  public void slicesCrossQueueBlocksWithoutChangingSurroundingBytes() {
    for (int prefix : new int[] {0, 1, 16383, 16384}) {
      for (int length : new int[] {1, 16383, 16384, 16385, 49157}) {
        BlockingByteQueue queue = new BlockingByteQueue();
        byte[] source = new byte[length + 4];
        for (int i = 0; i < source.length; i++) source[i] = (byte)i;
        byte[] first = new byte[prefix];
        Arrays.fill(first, (byte)77);
        assertTrue(queue.write(first));
        assertTrue(queue.write(source, 2, length));
        byte[] actual = new byte[prefix + length + 6];
        Arrays.fill(actual, (byte)99);
        assertEquals(prefix + length, queue.read(actual, 3, prefix + length));
        assertArrayEquals(first, Arrays.copyOfRange(actual, 3, 3 + prefix));
        assertArrayEquals(Arrays.copyOfRange(source, 2, 2 + length),
          Arrays.copyOfRange(actual, 3 + prefix, actual.length - 3));
        assertEquals(99, actual[0]);
        assertEquals(99, actual[actual.length - 1]);
        assertEquals(0, queue.available());
      }
    }
  }

  @Test
  public void invalidSlicesLeaveQueuedBytesUntouched() {
    BlockingByteQueue queue = new BlockingByteQueue();
    queue.write(new byte[] {10, 20, 30});
    for (int[] range : new int[][] {{-1, 1}, {0, -1}, {3, 1}, {1, Integer.MAX_VALUE}}) {
      try {
        queue.read(new byte[3], range[0], range[1]);
        fail("Invalid read should fail");
      } catch (IndexOutOfBoundsException expected) {}
      try {
        queue.write(new byte[3], range[0], range[1]);
        fail("Invalid write should fail");
      } catch (IndexOutOfBoundsException expected) {}
      assertEquals(3, queue.available());
    }
    try {
      queue.read(null, 0, 0);
      fail("Null read should fail");
    } catch (NullPointerException expected) {}
    try {
      queue.write(null, 0, 0);
      fail("Null write should fail");
    } catch (NullPointerException expected) {}
    assertEquals(10, queue.read());
    assertEquals(20, queue.read());
    assertEquals(30, queue.read());
  }

  @Test
  public void emptyOperationsNeverBlockAndSlicesReturnEof() throws Exception {
    PseudoInputStream in = new PseudoInputStream();
    byte[] bytes = new byte[4];
    assertEquals(0, in.read(bytes, 2, 0));
    in.write(new byte[] {1, 2});
    assertEquals(2, in.read(bytes, 1, 3));
    assertArrayEquals(new byte[] {0, 1, 2, 0}, bytes);
    in.close();
    assertEquals(-1, in.read(bytes, 1, 2));
    assertEquals(0, in.read(bytes, 4, 0));
    assertEquals(0, in.read(new byte[0]));

    PseudoOutputStream out = new PseudoOutputStream();
    out.write(bytes, 1, 2);
    assertArrayEquals(new byte[] {1, 2}, out.readAvailable());
    out.close();
    out.write(bytes, 4, 0);
    out.write(new byte[0]);
  }

  @Test
  public void clearAndCloseDiscardDataAndInterruptRestoresFlag() throws Exception {
    BlockingByteQueue queue = new BlockingByteQueue();
    queue.write(new byte[20000]);
    queue.clear();
    queue.write(new byte[] {42});
    assertEquals(42, queue.read());
    AtomicReference<Boolean> interrupted = new AtomicReference<>();
    Thread reader = new Thread(() -> {
      assertEquals(-1, queue.read(new byte[5], 1, 3));
      interrupted.set(Thread.currentThread().isInterrupted());
    });
    reader.start();
    try {
      assertTrue("Reader did not block", waitForState(reader, Thread.State.WAITING));
      reader.interrupt();
      reader.join(1000);
      assertFalse(reader.isAlive());
      assertEquals(Boolean.TRUE, interrupted.get());
    } finally {
      queue.close();
      reader.join(1000);
    }
    BlockingByteQueue discarded = new BlockingByteQueue();
    discarded.write(new byte[20000]);
    discarded.close();
    assertEquals(-1, discarded.read(new byte[3], 1, 2));
  }

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
  @Test
  public void boundedWritesRejectAtomicallyAndResumeAfterConsumption() {
    BlockingByteQueue queue = new BlockingByteQueue(4);
    assertTrue(queue.write(new byte[] {1, 2, 3}));
    assertFalse(queue.write(new byte[] {4, 5}));
    assertEquals(3, queue.available());
    assertTrue(queue.write(4));
    assertFalse(queue.write(5));
    assertEquals(1, queue.read());
    assertTrue(queue.write(5));
    assertArrayEquals(new byte[] {2, 3, 4, 5}, queue.readAvailable());
  }

  @Test(timeout = 3000)
  public void delimitedRecordsCannotGrowBeyondTheByteLimit() throws Exception {
    BlockingByteQueue queue = new BlockingByteQueue(4);
    queue.write(new byte[] {1, 2, 3, 4});
    AtomicReference<byte[]> result = new AtomicReference<>();
    Thread reader = new Thread(() -> result.set(queue.readDelimited((byte)'\n')));
    reader.start();
    try {
      assertTrue(waitForState(reader, Thread.State.WAITING));
      assertTrue(queue.write(5));
      reader.join(1000);
      assertFalse(reader.isAlive());
      assertNull(result.get());
      assertEquals(-1, queue.available());
    } finally {
      queue.close();
      reader.join(1000);
    }
  }

}
