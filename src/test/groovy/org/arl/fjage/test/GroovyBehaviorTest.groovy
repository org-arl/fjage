package org.arl.fjage.test

import org.arl.fjage.*
import org.junit.Assert
import org.junit.Test

import java.time.Duration

class GroovyBehaviorTest
    extends AbstractBehaviorTest {

  @Test
  void testOneShot() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new OneShotBehavior({
          emitTestEvent("EVENT1")
        }))
        add(new OneShotBehavior() {

          @Override
          void action() {
            emitTestEvent("EVENT2")
          }
        })
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testCyclic() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        final AbstractBehaviorTest.IntHolder countHolder = new AbstractBehaviorTest.IntHolder()
        add(new CyclicBehavior({
          if (countHolder.getValue() < 10) {
            emitTestEvent("EVENT1")
            countHolder.increment()
          } else {
            stop()
          }
        }))

        add(new CyclicBehavior() {

          private int count = 0

          @Override
          void action() {
            if (count < 10) {
              emitTestEvent("EVENT2")
              count++
            } else {
              stop()
            }
          }
        })
      }
    })

    // ---- expect ----
    expectEventCount("EVENT1", 10)
    expectEventCount("EVENT2", 10)

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testWaker() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        emitTestEvent("START")
        add(new WakerBehavior(5000, {
          emitTestEvent("EVENT1")
        }))
        add(new WakerBehavior(5000) {

          @Override
          void onWake() {
            emitTestEvent("EVENT2")
          }
        })
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("START")
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")
    expectElapsedTimeBetweenEvents("START", "EVENT1", 5000, 5500)
    expectElapsedTimeBetweenEvents("START", "EVENT2", 5000, 5500)

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testTicker() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        emitTestEvent("START")
        add(new TickerBehavior(5000, {
          emitTestEvent("EVENT1")
        }))
        add(new TickerBehavior(5000) {

          @Override
          void onTick() {
            emitTestEvent("EVENT2")
          }
        })
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("START")
    expectElapsedTimeBetweenEvents("START", "EVENT1", 5000, 5500)
    expectElapsedTimeBetweenEvents("START", "EVENT2", 5000, 5500)
    expectElapsedTimeBetweenEvents("EVENT1", 5000, 5500)
    expectElapsedTimeBetweenEvents("EVENT2", 5000, 5500)

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testBackoff() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        emitTestEvent("START")

        // TODO lambdas/closures hide the backoff() method

        add(new BackoffBehavior(5000) {

          private int count = 0

          @Override
          void onExpiry() {
            if (count == 0) {
              emitTestEvent("EVENT2.1")
              backoff(30000)
              count++
            } else if (count == 1) {
              emitTestEvent("EVENT2.2")
              stop()
              count++
            }
          }
        })
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("START")
    expectOneAndOnlyOneEvent("EVENT2.1")
    expectOneAndOnlyOneEvent("EVENT2.2")
    expectElapsedTimeBetweenEvents("START", "EVENT2.1", 5000, 5500)
    expectElapsedTimeBetweenEvents("EVENT2.1", "EVENT2.2", 30000, 30500)

    // ---- run ----
    run(Duration.ofMinutes(5))
  }

  @Test
  void testPoisson() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new PoissonBehavior(5000, {
          emitTestEvent("EVENT1")
        }))
        add(new PoissonBehavior(5000) {

          @Override
          void onTick() {
            emitTestEvent("EVENT2")
          }
        })
      }
    })

    // ---- expect ----
    expectAverageElapsedTimeBetweenEvents("EVENT1", 4000, 6000)
    expectAverageElapsedTimeBetweenEvents("EVENT2", 4000, 6000)

    // ---- run ----
    run(Duration.ofMinutes(60))
  }

  @Test
  void testMessage() {
    // ---- given ----
    final AgentID recipient1 = getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new MessageBehavior(AbstractBehaviorTest.BehaviorTestMessage.class, { message ->
          if (message instanceof AbstractBehaviorTest.BehaviorTestMessage) {
            final AbstractBehaviorTest.BehaviorTestMessage testMessage = (AbstractBehaviorTest.BehaviorTestMessage) message
            Assert.assertEquals("TEST1", testMessage.getData())
            emitTestEvent("EVENT1")
          }
        }))
      }
    })
    final AgentID recipient2 = getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init()

        add(new MessageBehavior(AbstractBehaviorTest.BehaviorTestMessage.class) {

          @Override
          void onReceive(Message message) {
            if (message instanceof AbstractBehaviorTest.BehaviorTestMessage) {
              final AbstractBehaviorTest.BehaviorTestMessage testMessage = (AbstractBehaviorTest.BehaviorTestMessage) message
              Assert.assertEquals("TEST2", testMessage.getData())
              emitTestEvent("EVENT2")
            }
          }
        })
      }
    })
    getContainer().add(new Agent() {
      @Override
      protected void init() {
        super.init()

        add(new WakerBehavior(5000, {
          final AbstractBehaviorTest.BehaviorTestMessage testMessage1 = new AbstractBehaviorTest.BehaviorTestMessage("TEST1")
          testMessage1.setRecipient(recipient1)
          send(testMessage1)

          final AbstractBehaviorTest.BehaviorTestMessage testMessage2 = new AbstractBehaviorTest.BehaviorTestMessage("TEST2")
          testMessage2.setRecipient(recipient2)
          send(testMessage2)
        }))
      }
    })

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1")
    expectOneAndOnlyOneEvent("EVENT2")

    // ---- run ----
    run(Duration.ofMinutes(5))
  }
}