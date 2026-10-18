/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import org.arl.fjage.*;
import org.arl.fjage.remote.Gateway;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;

/**
 * Checks that delays, timeouts and timed behaviors measure elapsed time, and are
 * not affected by steps in the platform wall clock (issue #473).
 */
public class ClockStepTest {

  private static final long HOUR = 3_600_000;

  private SteppedClockPlatform platform;
  private Container container;

  @Before
  public void setUp() {
    platform = new SteppedClockPlatform();
    container = new Container(platform);
  }

  @After
  public void tearDown() {
    platform.shutdown();
  }

  @Test
  public void testWakerIgnoresForwardStep() throws InterruptedException {
    CountDownLatch woke = new CountDownLatch(1);
    AtomicLong elapsed = new AtomicLong();
    container.add("a", new Agent() {
      @Override
      protected void init() {
        long t0 = System.nanoTime();
        add(new WakerBehavior(1000, () -> {
          elapsed.set(msSince(t0));
          woke.countDown();
        }));
        // step the wall clock forward, then wake the agent with an unrelated message
        add(new WakerBehavior(100, () -> {
          platform.step(HOUR);
          send(new Message(getAgentID()));
        }));
      }
    });
    platform.start();
    assertTrue("waker did not fire", woke.await(5, SECONDS));
    assertTrue("waker fired after " + elapsed.get() + " ms", elapsed.get() >= 950);
  }

  @Test
  public void testDelayIgnoresBackwardStep() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicLong elapsed = new AtomicLong();
    container.add("a", new Agent() {
      @Override
      protected void init() {
        add(new OneShotBehavior(() -> {
          platform.stepLater(100, -HOUR);
          long t0 = System.nanoTime();
          delay(500);
          elapsed.set(msSince(t0));
          done.countDown();
        }));
      }
    });
    platform.start();
    assertTrue("delay() did not return", done.await(5, SECONDS));
    assertTrue("delay() took " + elapsed.get() + " ms", elapsed.get() >= 450);
  }

  @Test
  public void testRequestTimeoutIgnoresBackwardStep() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicLong elapsed = new AtomicLong();
    AtomicReference<Message> rsp = new AtomicReference<>();
    container.add("sink", new Agent());
    container.add("a", new Agent() {
      @Override
      protected void init() {
        add(new OneShotBehavior(() -> {
          platform.stepLater(100, -HOUR);
          long t0 = System.nanoTime();
          rsp.set(request(new Message(agent("sink"), Performative.REQUEST), 500));
          elapsed.set(msSince(t0));
          done.countDown();
        }));
      }
    });
    platform.start();
    assertTrue("request() did not time out", done.await(5, SECONDS));
    assertNull(rsp.get());
    assertTrue("request() took " + elapsed.get() + " ms", elapsed.get() >= 450);
  }

  @Test
  public void testFutureTimeoutIgnoresBackwardStep() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicLong elapsed = new AtomicLong();
    AtomicBoolean timedOut = new AtomicBoolean();
    container.add("sink", new Agent());
    container.add("a", new Agent() {
      @Override
      protected void init() {
        add(new OneShotBehavior(() -> {
          platform.stepLater(100, -HOUR);
          long t0 = System.nanoTime();
          try {
            prepareRequest(new Message(agent("sink"), Performative.REQUEST)).send().get(500, MILLISECONDS);
          } catch (TimeoutException ex) {
            timedOut.set(true);
          } catch (Exception ex) {
            // leave timedOut unset
          }
          elapsed.set(msSince(t0));
          done.countDown();
        }));
      }
    });
    platform.start();
    assertTrue("Future.get() did not time out", done.await(5, SECONDS));
    assertTrue(timedOut.get());
    assertTrue("Future.get() took " + elapsed.get() + " ms", elapsed.get() >= 450);
  }

  @Test
  public void testTickerIgnoresBackwardStep() throws InterruptedException {
    AtomicInteger ticks = new AtomicInteger();
    container.add("a", new Agent() {
      @Override
      protected void init() {
        add(new TickerBehavior(100, ticks::incrementAndGet));
        add(new WakerBehavior(250, () -> platform.step(-HOUR)));
      }
    });
    platform.start();
    Thread.sleep(1000);
    assertTrue("only " + ticks.get() + " ticks in 1 s", ticks.get() >= 7);
  }

  @Test
  public void testRequestTimeoutCallbackIgnoresBackwardStep() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicLong elapsed = new AtomicLong();
    container.add("sink", new Agent());
    container.add("a", new Agent() {
      @Override
      protected void init() {
        add(new OneShotBehavior(() -> {
          platform.stepLater(100, -HOUR);
          long t0 = System.nanoTime();
          prepareRequest(new Message(agent("sink"), Performative.REQUEST))
              .onTimeout(500, () -> {
                elapsed.set(msSince(t0));
                done.countDown();
              })
              .send();
        }));
      }
    });
    platform.start();
    assertTrue("onTimeout() did not fire", done.await(5, SECONDS));
    assertTrue("onTimeout() fired after " + elapsed.get() + " ms", elapsed.get() >= 450);
  }

  @Test
  public void testBackoffIgnoresBackwardStep() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicLong elapsed = new AtomicLong();
    container.add("a", new Agent() {
      @Override
      protected void init() {
        long t0 = System.nanoTime();
        add(new BackoffBehavior(500, () -> {
          elapsed.set(msSince(t0));
          done.countDown();
        }));
        add(new WakerBehavior(100, () -> platform.step(-HOUR)));
      }
    });
    platform.start();
    assertTrue("backoff did not expire", done.await(5, SECONDS));
    assertTrue("backoff expired after " + elapsed.get() + " ms", elapsed.get() >= 450);
  }

  @Test
  public void testPoissonIgnoresForwardStep() throws InterruptedException {
    AtomicInteger ticks = new AtomicInteger();
    container.add("a", new Agent() {
      @Override
      protected void init() {
        // mean interval of an hour, so a tick within the test is very unlikely
        add(new PoissonBehavior(HOUR, ticks::incrementAndGet));
        // step the wall clock far forward, then wake the agent with an unrelated message
        add(new WakerBehavior(100, () -> {
          platform.step(1000 * HOUR);
          send(new Message(getAgentID()));
        }));
      }
    });
    platform.start();
    Thread.sleep(500);
    assertEquals("Poisson behavior ticked early", 0, ticks.get());
  }

  @Test(timeout = 5000)
  public void testGatewayReceiveIgnoresBackwardStep() {
    Gateway gw = new Gateway(container);
    try {
      platform.start();
      platform.stepLater(100, -HOUR);
      long t0 = System.nanoTime();
      assertNull(gw.receive(500));
      long elapsed = msSince(t0);
      assertTrue("Gateway.receive() took " + elapsed + " ms", elapsed >= 450);
    } finally {
      gw.close();
    }
  }

  @Test
  public void testFutureSubMillisecondTimeout() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicLong elapsed = new AtomicLong(-1);
    AtomicBoolean timedOut = new AtomicBoolean();
    // simulated time is exact, so a sub-millisecond wait shows up as one simulator tick
    DiscreteEventSimulator sim = new DiscreteEventSimulator();
    Container c = new Container(sim);
    c.add("sink", new Agent());
    c.add("a", new Agent() {
      @Override
      protected void init() {
        add(new OneShotBehavior(() -> {
          long t0 = currentTimeMillis();
          try {
            prepareRequest(new Message(agent("sink"), Performative.REQUEST)).send().get(500, MICROSECONDS);
          } catch (TimeoutException ex) {
            timedOut.set(true);
          } catch (Exception ex) {
            // leave timedOut unset
          }
          elapsed.set(currentTimeMillis() - t0);
          done.countDown();
        }));
      }
    });
    sim.start();
    try {
      assertTrue("Future.get() did not time out", done.await(5, SECONDS));
      assertTrue(timedOut.get());
      assertEquals("Future.get() did not wait", 1, elapsed.get());
    } finally {
      sim.shutdown();
    }
  }

  @Test
  public void testVeryLongTimeout() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Message> msg = new AtomicReference<>();
    container.add("a", new Agent() {
      @Override
      protected void init() {
        add(new OneShotBehavior(() -> {
          msg.set(receive(m -> m.getPerformative() == Performative.INFORM, Long.MAX_VALUE));
          done.countDown();
        }));
      }
    });
    // an unrelated message wakes the receiver before the one it waits for
    container.add("b", new Agent() {
      @Override
      protected void init() {
        add(new WakerBehavior(100, () -> send(new Message(agent("a"), Performative.REQUEST))));
        add(new WakerBehavior(200, () -> send(new Message(agent("a"), Performative.INFORM))));
      }
    });
    platform.start();
    assertTrue("receive() did not return", done.await(5, SECONDS));
    assertNotNull("receive() returned before the message arrived", msg.get());
  }

  @Test
  public void testSimulatorNanoTimeUnits() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    AtomicLong millis = new AtomicLong();
    AtomicLong nanos = new AtomicLong();
    DiscreteEventSimulator sim = new DiscreteEventSimulator();
    new Container(sim).add("a", new Agent() {
      @Override
      protected void init() {
        add(new WakerBehavior(1234, () -> {
          millis.set(currentTimeMillis());
          nanos.set(nanoTime());
          done.countDown();
        }));
      }
    });
    sim.start();
    try {
      assertTrue(done.await(5, SECONDS));
      assertEquals(1234, millis.get());
      assertEquals(1234_000_000L, nanos.get());
    } finally {
      sim.shutdown();
    }
  }

  private static long msSince(long t0) {
    return (System.nanoTime() - t0) / 1_000_000;
  }

  /**
   * Real-time platform whose wall clock can be stepped, while nanoTime() stays monotonic.
   */
  private static class SteppedClockPlatform extends Platform {

    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, r -> {
      Thread t = new Thread(r, "stepped-clock-timer");
      t.setDaemon(true);
      return t;
    });
    private volatile long offset = 0;

    SteppedClockPlatform() {
      timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /** Steps the wall clock by the given number of milliseconds. */
    void step(long millis) {
      offset += millis;
    }

    /** Steps the wall clock after a delay. */
    void stepLater(long delay, long millis) {
      schedule(new TimerTask() {
        @Override
        public void run() {
          step(millis);
        }
      }, delay);
    }

    @Override
    public long currentTimeMillis() {
      return System.currentTimeMillis() + offset;
    }

    @Override
    public long nanoTime() {
      return System.nanoTime();
    }

    @Override
    public void schedule(TimerTask task, long millis) {
      try {
        timer.schedule(task, Math.max(0, millis), MILLISECONDS);
      } catch (RejectedExecutionException ex) {
        // platform has been shut down
      }
    }

    @Override
    public void idle() {
      // do nothing
    }

    @Override
    public void delay(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void shutdown() {
      super.shutdown();
      timer.shutdown();
    }
  }
}
