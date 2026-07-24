package org.arl.fjage.loadtest;

import static org.junit.Assert.*;

import java.util.*;

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
 *  - a unicast prober to a healthy slave (separate handler, expected unaffected).
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
    try {
      final LoadAgents.SubscriberAgent[] sub = new LoadAgents.SubscriberAgent[3];
      for (int i = 0; i < 3; i++) {
        sub[i] = new LoadAgents.SubscriberAgent();
        fx.slaves[i].add("sub_" + i, sub[i]);
      }
      LoadAgents.ReceiverAgent rxS0 = new LoadAgents.ReceiverAgent();
      fx.slaves[0].add("rx_s0", rxS0);
      LoadAgents.ReceiverAgent rxS2 = new LoadAgents.ReceiverAgent();
      fx.slaves[2].add("rx_s2", rxS2);

      // publisher to topic (hits all slaves incl. slow one), unicast prober to healthy
      // slave-0, and a bulk unicast feeder to slave-2 to fill its socket buffers fast
      java.util.concurrent.atomic.AtomicBoolean go = new java.util.concurrent.atomic.AtomicBoolean(false);
      LoadAgents.ContinuousSender pubM = new LoadAgents.ContinuousSender(null, 5, go);
      fx.master.add("pub_m", pubM);
      LoadAgents.ContinuousSender probeM = new LoadAgents.ContinuousSender(
          Collections.singletonList(new AgentID("rx_s0")), 10, go);
      fx.master.add("probe_m", probeM);
      LoadAgents.ContinuousSender bulkM = new LoadAgents.ContinuousSender(
          Collections.singletonList(new AgentID("rx_s2")), 2, go);
      fx.master.add("bulk_m", bulkM);

      fx.startSlaves();
      fx.awaitAgentsVisible(Arrays.asList("sub_0", "sub_1", "sub_2", "rx_s0", "rx_s2"), 30000);
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
      // ---- phase B: bounded directory delay ----
      proxy.pause();
      TestUtil.sleep(250);
      Future<Long> dirOp = exec.submit(() -> {
        long t0 = System.currentTimeMillis();
        fx.master.getAgents();
        return System.currentTimeMillis() - t0;
      });
      TestUtil.sleep(1000);
      proxy.unpause();
      long dirOpMs = dirOp.get(10, TimeUnit.SECONDS);
      assertTrue("master.getAgents exceeded 5 s query timeout: " + dirOpMs + " ms", dirOpMs < 5000);

      // ---- phase C: slave-2 stops reading for 10 s ----
      sub[0].stats.latenciesNs.clear();
      sub[1].stats.latenciesNs.clear();
      rxS0.stats.latenciesNs.clear();
      pubM.sendDurationsNs.clear();
      long subAtC = sub[0].stats.total();
      int pubSeqAtC = pubM.seq.get();
      proxy.pause();
      TestUtil.sleep(10000);
      long subC0 = sub[0].stats.total() - subAtC;
      double subP99B0 = TestUtil.p99ms(sub[0].stats.latenciesNs);
      double probeP99B = TestUtil.p99ms(rxS0.stats.latenciesNs);
      double pubSendMaxB = maxMs(pubM.sendDurationsNs);
      int pubTicksDuringC = pubM.seq.get() - pubSeqAtC;

      // ---- phase D: recover ----
      proxy.unpause();

      TestUtil.sleep(3000);
      pubM.stopSending();
      probeM.stopSending();
      bulkM.stopSending();
      TestUtil.sleep(500);
      final int published = pubM.seq.get();

      // all subscribers (incl. the slow one) must eventually receive everything published
      boolean recovered = TestUtil.tryWaitUntil(() -> {
        for (LoadAgents.SubscriberAgent s : sub)
          if (s.stats.countFrom("pub_m") < published) return false;
        return true;
      }, 30000);

      System.out.println("=== T2 results ===");
      System.out.println("phase A (5 s baseline): sub_0 topic rate=" + (subA0 / 5) + " msg/s, unicast probe p99=" + probeP99A + " ms");
      System.out.println("phase B (slave-2 paused 1.25 s): master.getAgents() took " + dirOpMs + " ms");
      System.out.println("phase C (slave-2 stalled 10 s):");
      System.out.println("  sub_0 (healthy) received " + subC0 + " topic msgs during stall (head-of-line indicator)");
      System.out.println("  sub_0 topic p99=" + subP99B0 + " ms");
      System.out.println("  publisher ticks during stall: " + pubTicksDuringC + ", max send() call=" + pubSendMaxB + " ms (agent-thread blocking)");
      System.out.println("  unicast probe to healthy slave p99=" + probeP99B + " ms");
      System.out.println("recovery: published=" + published
          + " sub totals=" + sub[0].stats.countFrom("pub_m") + "/" + sub[1].stats.countFrom("pub_m") + "/" + sub[2].stats.countFrom("pub_m")
          + " recovered=" + recovered);
      for (int i = 0; i < 3; i++) {
        List<Integer> miss = sub[i].stats.missingFrom("pub_m", published);
        System.out.println("  sub_" + i + " missing " + miss.size() + " seqs: " + TestUtil.ranges(miss));
      }
      System.out.println("post-recovery max send() durations (complete data): pub=" + maxMs(pubM.sendDurationsNs)
          + " ms, bulk=" + maxMs(bulkM.sendDurationsNs) + " ms, probe=" + maxMs(probeM.sendDurationsNs) + " ms");
      System.out.println("severes: " + logs.severes());

      assertTrue("subscribers did not recover all topic messages after unpause", recovered);
      assertEquals("duplicates on slow slave", 0, sub[2].stats.dups.get());
      assertTrue("SEVERE logs: " + logs.severes(), logs.severes().isEmpty());
    } finally {
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
