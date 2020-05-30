package org.arl.fjage.test;

import org.arl.fjage.Agent;
import org.arl.fjage.OneShotBehavior;
import org.arl.fjage.TickerBehavior;
import org.arl.fjage.WakerBehavior;
import org.junit.Test;

import java.time.Duration;

class GroovyBehaviorTest
    extends AbstractSimulatorTest {

  @Test
  void testOneShot() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        add(new OneShotBehavior({
          emitTestEvent("EVENT1");
        }));
        add(new OneShotBehavior() {

          @Override
          void action() {
            emitTestEvent("EVENT2");
          }
        });
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("EVENT1");
    expectOneAndOnlyOneEvent("EVENT2");

    // ---- run ----
    run(Duration.ofMinutes(5));
  }

  @Test
  void testWaker() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        emitTestEvent("START");
        add(new WakerBehavior(5000, {
          emitTestEvent("EVENT1");
        }));
        add(new WakerBehavior(5000) {

          @Override
          void onWake() {
            emitTestEvent("EVENT2");
          }
        });
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("START");
    expectOneAndOnlyOneEvent("EVENT1");
    expectOneAndOnlyOneEvent("EVENT2");
    expectElapsedTimeBetweenEvents("START", "EVENT1", 5000, 5500);
    expectElapsedTimeBetweenEvents("START", "EVENT2", 5000, 5500);

    // ---- run ----
    run(Duration.ofMinutes(5));
  }

  @Test
  void testTicker() {
    // ---- given ----
    getContainer().add(new Agent() {

      @Override
      protected void init() {
        super.init();

        emitTestEvent("START");
        add(new TickerBehavior(5000, {
          emitTestEvent("EVENT1");
        }));
        add(new TickerBehavior(5000) {

          @Override
          void onTick() {
            emitTestEvent("EVENT2");
          }
        });
      }
    });

    // ---- expect ----
    expectOneAndOnlyOneEvent("START");
    expectElapsedTimeBetweenEvents("START", "EVENT1", 5000, 5500);
    expectElapsedTimeBetweenEvents("START", "EVENT2", 5000, 5500);
    expectElapsedTimeBetweenEvents("EVENT1", 5000, 5500);
    expectElapsedTimeBetweenEvents("EVENT2", 5000, 5500);

    // ---- run ----
    run(Duration.ofMinutes(5));
  }
}