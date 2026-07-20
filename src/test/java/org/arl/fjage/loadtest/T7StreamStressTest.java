package org.arl.fjage.loadtest;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.arl.fjage.connectors.BlockingByteQueue;
import org.arl.fjage.connectors.PseudoInputStream;
import org.arl.fjage.connectors.PseudoOutputStream;
import org.junit.Test;

/**
 * T7 — Unit-level stress on the new close semantics of BlockingByteQueue and the
 * pseudo-streams: concurrent writers + a blocked reader with close()/clear() injected
 * at random points. Readers must always unblock; post-close writes must fail; no hangs.
 */
public class T7StreamStressTest {

  private static final int ROUNDS = 200;
  private static final int WRITERS = 4;

  @Test(timeout = 120000)
  public void queueCloseStress() throws Exception {
    final Random rnd = new Random(42);
    ExecutorService pool = Executors.newCachedThreadPool();
    try {
      for (int round = 0; round < ROUNDS; round++) {
        final BlockingByteQueue q = new BlockingByteQueue();
        final CountDownLatch done = new CountDownLatch(WRITERS + 1);
        final AtomicInteger postCloseWrites = new AtomicInteger();
        final AtomicInteger readerLastReturn = new AtomicInteger(Integer.MIN_VALUE);

        // reader: keeps reading until end-of-stream; a proper end-of-stream is -1.
        // (exit on 0 too, without asserting yet, so a 0-return bug cannot busy-spin)
        pool.execute(new Runnable() {
          @Override
          public void run() {
            byte[] buf = new byte[512];
            int n;
            while ((n = q.read(buf)) > 0) {
              // consume
            }
            readerLastReturn.set(n);
            done.countDown();
          }
        });

        // writers: write chunks until write() reports closed
        for (int w = 0; w < WRITERS; w++) {
          pool.execute(new Runnable() {
            @Override
            public void run() {
              byte[] chunk = new byte[256];
              while (true) {
                if (!q.write(chunk)) {
                  postCloseWrites.incrementAndGet();
                  break;
                }
              }
              done.countDown();
            }
          });
        }

        Thread.sleep(rnd.nextInt(5));
        if (rnd.nextBoolean()) q.clear();   // clear must not wedge anyone
        q.close();

        assertTrue("round " + round + ": reader/writers did not unblock after close",
            done.await(5, TimeUnit.SECONDS));
        assertEquals("round " + round + ": read(byte[]) on a closed drained queue must"
            + " return -1 (end of stream), not 0 — callers looping on >= 0 busy-spin",
            -1, readerLastReturn.get());
        assertEquals("round " + round + ": all writers must observe closed state",
            WRITERS, postCloseWrites.get());
        assertEquals(-1, q.read());
        assertEquals(-1, q.available());
        assertFalse(q.write(1));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test(timeout = 60000)
  public void pseudoStreamCloseStress() throws Exception {
    ExecutorService pool = Executors.newCachedThreadPool();
    try {
      for (int round = 0; round < 100; round++) {
        final PseudoInputStream pin = new PseudoInputStream();
        final PseudoOutputStream pout = new PseudoOutputStream();
        final CountDownLatch done = new CountDownLatch(3);
        final AtomicInteger pinLastReturn = new AtomicInteger(Integer.MIN_VALUE);

        pool.execute(new Runnable() {
          @Override
          public void run() {
            byte[] buf = new byte[128];
            int n;
            while ((n = pin.read(buf)) > 0) {
              // consume
            }
            pinLastReturn.set(n);
            done.countDown();
          }
        });
        pool.execute(new Runnable() {
          @Override
          public void run() {
            while (pout.readLine() != null) {
              // consume
            }
            done.countDown();
          }
        });
        pool.execute(new Runnable() {
          @Override
          public void run() {
            try {
              while (true) {
                pin.write("0123456789abcdef\n".getBytes());
                pout.write("0123456789abcdef\n".getBytes());
              }
            } catch (IOException expected) {
              // one of the streams closed under us
            }
            done.countDown();
          }
        });

        Thread.sleep(round % 5);
        pin.close();
        pout.close();

        assertTrue("round " + round + ": stream users did not unblock after close",
            done.await(5, TimeUnit.SECONDS));
        assertEquals("round " + round + ": PseudoInputStream.read(byte[]) on closed stream"
            + " must return -1 per its documented contract, not 0",
            -1, pinLastReturn.get());

        // writes after close must fail
        try {
          pin.write('x');
          fail("write to closed PseudoInputStream should throw");
        } catch (IOException expected) {
          // ok
        }
        try {
          pout.write('x');
          fail("write to closed PseudoOutputStream should throw");
        } catch (IOException expected) {
          // ok
        }
      }
    } finally {
      List<Runnable> stragglers = pool.shutdownNow();
      assertTrue("straggler tasks: " + stragglers.size(), stragglers.isEmpty());
    }
  }
}
