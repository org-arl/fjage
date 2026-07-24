package org.arl.fjage.loadtest;

import static org.junit.Assert.*;

import java.util.*;

import org.arl.fjage.AgentID;
import org.arl.fjage.Container;
import org.junit.Test;

/**
 * T5 — Shutdown races.
 *
 * Repeatedly spins up 1 master + 3 slaves, starts continuous load, then shuts down
 * mid-flight — master first on odd iterations, a slave first on even ones. Asserts
 * that every container stops within a bound (no shutdown deadlock) and that repeated
 * cycles do not accumulate non-benign threads. Exercises the PR's new
 * SlaveContainer.shutdown() connectionManager interrupt/join and pending.clear().
 */
public class T5ShutdownRaceTest {

  private static final int ITERATIONS = 12;
  private static final String[] BENIGN_THREADS = {"fjage-timer", ":init", "proxy:"};

  @Test(timeout = 600000)
  public void shutdownUnderLoad() {
    LogCapture logs = new LogCapture();
    Set<String> baseline = TestUtil.threadSnapshot();
    Random rnd = new Random(7);
    try {
      for (int iter = 0; iter < ITERATIONS; iter++) {
        final MultiContainerFixture fx = MultiContainerFixture.create(3);
        LoadAgents.SubscriberAgent[] sub = new LoadAgents.SubscriberAgent[3];
        for (int i = 0; i < 3; i++) {
          sub[i] = new LoadAgents.SubscriberAgent();
          fx.slaves[i].add("sub_" + i, sub[i]);
        }
        LoadAgents.ReceiverAgent rxS0 = new LoadAgents.ReceiverAgent();
        fx.slaves[0].add("rx_s0", rxS0);
        LoadAgents.ContinuousSender pubM = new LoadAgents.ContinuousSender(null, 5);
        fx.master.add("pub_m", pubM);
        LoadAgents.ContinuousSender uniM = new LoadAgents.ContinuousSender(
            Collections.singletonList(new AgentID("rx_s0")), 5);
        fx.master.add("uni_m", uniM);
        fx.startSlaves();

        // let load flow for a random slice, then kill mid-flight
        TestUtil.waitUntil("iter " + iter + " traffic flowing", () -> rxS0.stats.total() > 10, 15000);
        TestUtil.sleep(500 + rnd.nextInt(1500));

        if (iter % 2 == 1) {
          fx.masterPlatform.shutdown();   // master dies first, slaves see connection loss
          TestUtil.sleep(300);
        } else {
          fx.slavePlatforms[0].shutdown();   // one slave dies first, master must cope
          TestUtil.sleep(300);
        }
        fx.teardown();

        for (Container c : fx.containers())
          assertFalse("iter " + iter + ": container did not stop: " + c, c.isRunning());
        System.out.println("iter " + iter + " ok (mode=" + (iter % 2 == 1 ? "master-first" : "slave-first")
            + ", rx=" + rxS0.stats.total() + ", sub0=" + sub[0].stats.total() + ")");
      }
    } finally {
      logs.close();
    }

    TestUtil.sleep(3000);
    List<String> leaks = new ArrayList<>();
    int timers = 0;
    for (String t : TestUtil.newThreadsSince(baseline)) {
      if (t.contains("fjage-timer")) {
        timers++;
        continue;
      }
      boolean benign = false;
      for (String b : BENIGN_THREADS)
        if (t.contains(b)) {
          benign = true;
          break;
        }
      if (!benign) leaks.add(t);
    }
    System.out.println("=== T5 results ===");
    System.out.println(ITERATIONS + " shutdown cycles; fjage-timer threads leaked: " + timers
        + " (pre-existing, ~4/cycle expected), non-benign leaks: " + leaks);
    System.out.println("severes: " + logs.severes());
    assertTrue("non-benign thread leaks across shutdown cycles: " + leaks, leaks.isEmpty());
  }
}
