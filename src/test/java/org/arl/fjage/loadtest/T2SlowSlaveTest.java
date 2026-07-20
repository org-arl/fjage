package org.arl.fjage.loadtest;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;

import org.arl.fjage.AgentID;
import org.junit.Test;

/**
 * T2 — Slow slave.
 *
 * Slave-2 is routed through a throttling proxy. While topic + unicast traffic runs,
 * the proxy pauses reading (TCP backpressure). Measures the impact on:
 *  - topic delivery to the HEALTHY slaves (head-of-line blocking in the master's
 *    relay loop — the PR changed relay from queued-async to synchronous),
 *  - the publisher agent's send() call duration (agent-thread blocking),
 *  - a unicast prober to a healthy slave (separate handler, expected unaffected),
 *  - master directory ops (getAgents) while a handler's socket is backed up.
 *
 * Hard assertions are liveness/recovery only; latency numbers are reported for
 * PR-vs-master comparison.
 */
public class T2SlowSlaveTest {

  @Test(timeout = 240000)
  public void slowSlave() throws Exception {
    LogCapture logs = new LogCapture();
    final MultiContainerFixture fx = MultiContainerFixture.create(3, new boolean[]{false, false, true});
    final ThrottlingTcpProxy proxy = fx.proxies[2];
    ExecutorService exec = Executors.newCachedThreadPool();
    try {
      final LoadAgents.SubscriberAgent[] sub = new LoadAgents.SubscriberAgent[3];
      for (int i = 0; i < 3; i++) {
        sub[i] = new LoadAgents.SubscriberAgent();
        fx.slaves[i].add("sub-" + i, sub[i]);
      }
      LoadAgents.ReceiverAgent rxS0 = new LoadAgents.ReceiverAgent();
      fx.slaves[0].add("rx-s0", rxS0);
      LoadAgents.ReceiverAgent rxS2 = new LoadAgents.ReceiverAgent();
      fx.slaves[2].add("rx-s2", rxS2);

      // publisher to topic (hits all slaves incl. slow one), unicast prober to healthy
      // slave-0, and a bulk unicast feeder to slave-2 to fill its socket buffers fast
      java.util.concurrent.atomic.AtomicBoolean go = new java.util.concurrent.atomic.AtomicBoolean(false);
      LoadAgents.ContinuousSender pubM = new LoadAgents.ContinuousSender(null, 5, go);
      fx.master.add("pub-m", pubM);
      LoadAgents.ContinuousSender probeM = new LoadAgents.ContinuousSender(
          Collections.singletonList(new AgentID("rx-s0")), 10, go);
      fx.master.add("probe-m", probeM);
      LoadAgents.ContinuousSender bulkM = new LoadAgents.ContinuousSender(
          Collections.singletonList(new AgentID("rx-s2")), 2, go);
      fx.master.add("bulk-m", bulkM);

      fx.startSlaves();
      fx.awaitAgentsVisible(Arrays.asList("sub-0", "sub-1", "sub-2", "rx-s0", "rx-s2"), 30000);
      TestUtil.sleep(1000);   // let watch lists settle before opening the gate
      go.set(true);

      // ---- phase A: baseline 5 s ----
      TestUtil.sleep(5000);
      long subA0 = sub[0].stats.total();
      double probeP99A = TestUtil.p99ms(rxS0.stats.latenciesNs);
      sub[0].stats.latenciesNs.clear();
      sub[1].stats.latenciesNs.clear();
      rxS0.stats.latenciesNs.clear();
      pubM.sendDurationsNs.clear();
      int pubSeqAtB = pubM.seq.get();

      // ---- phase B: slave-2 stops reading for 10 s ----
      proxy.pause();

      // measure a directory op while the slow handler's socket backs up
      TestUtil.sleep(1000);
      Future<Long> dirOp = exec.submit(new Callable<Long>() {
        @Override
        public Long call() {
          long t0 = System.currentTimeMillis();
          fx.master.getAgents();
          return System.currentTimeMillis() - t0;
        }
      });

      TestUtil.sleep(9000);
      long subB0 = sub[0].stats.total() - subA0;
      double subP99B0 = TestUtil.p99ms(sub[0].stats.latenciesNs);
      double probeP99B = TestUtil.p99ms(rxS0.stats.latenciesNs);
      double pubSendMaxB = maxMs(pubM.sendDurationsNs);
      int pubTicksDuringB = pubM.seq.get() - pubSeqAtB;

      // ---- phase C: recover ----
      proxy.unpause();
      long dirOpMs = dirOp.get(60, TimeUnit.SECONDS);   // liveness: must complete

      TestUtil.sleep(3000);
      pubM.stopSending();
      probeM.stopSending();
      bulkM.stopSending();
      TestUtil.sleep(500);
      final int published = pubM.seq.get();

      // all subscribers (incl. the slow one) must eventually receive everything published
      boolean recovered = TestUtil.tryWaitUntil(() -> {
        for (LoadAgents.SubscriberAgent s : sub)
          if (s.stats.countFrom("pub-m") < published) return false;
        return true;
      }, 30000);

      System.out.println("=== T2 results ===");
      System.out.println("phase A (5 s baseline): sub-0 topic rate=" + (subA0 / 5) + " msg/s, unicast probe p99=" + probeP99A + " ms");
      System.out.println("phase B (slave-2 stalled 10 s):");
      System.out.println("  sub-0 (healthy) received " + subB0 + " topic msgs during stall (head-of-line indicator)");
      System.out.println("  sub-0 topic p99=" + subP99B0 + " ms");
      System.out.println("  publisher ticks during stall: " + pubTicksDuringB + ", max send() call=" + pubSendMaxB + " ms (agent-thread blocking)");
      System.out.println("  unicast probe to healthy slave p99=" + probeP99B + " ms");
      System.out.println("  master.getAgents() issued during stall took " + dirOpMs + " ms");
      System.out.println("recovery: published=" + published
          + " sub totals=" + sub[0].stats.countFrom("pub-m") + "/" + sub[1].stats.countFrom("pub-m") + "/" + sub[2].stats.countFrom("pub-m")
          + " recovered=" + recovered);
      for (int i = 0; i < 3; i++) {
        List<Integer> miss = sub[i].stats.missingFrom("pub-m", published);
        System.out.println("  sub-" + i + " missing " + miss.size() + " seqs: " + TestUtil.ranges(miss));
      }
      System.out.println("post-recovery max send() durations (complete data): pub=" + maxMs(pubM.sendDurationsNs)
          + " ms, bulk=" + maxMs(bulkM.sendDurationsNs) + " ms, probe=" + maxMs(probeM.sendDurationsNs) + " ms");
      System.out.println("severes: " + logs.severes());

      assertTrue("subscribers did not recover all topic messages after unpause", recovered);
      assertEquals("duplicates on slow slave", 0, sub[2].stats.dups.get());
      assertTrue("SEVERE logs: " + logs.severes(), logs.severes().isEmpty());
    } finally {
      exec.shutdownNow();
      fx.teardown();
      logs.close();
    }
  }

  private static double maxMs(Collection<Long> ns) {
    long max = 0;
    for (long v : ns) if (v > max) max = v;
    return max / 1e6;
  }
}
