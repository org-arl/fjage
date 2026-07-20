package org.arl.fjage.loadtest;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.arl.fjage.AgentID;
import org.arl.fjage.Container;
import org.junit.Test;

/**
 * T1 — Core load / message-loss test.
 *
 * 1 master + 3 slaves. 4 senders + 4 receivers per container; every sender unicasts
 * sequenced messages to one receiver on each *other* container. Concurrently, each
 * container hosts a publisher pushing sequenced messages to a shared topic with one
 * subscriber per container, while hammer threads pound the directory APIs.
 *
 * Asserts: zero unicast loss, zero duplicates, complete topic delivery, stable
 * directory results, no SEVERE logs.
 */
public class T1LoadTest {

  private static final int PAIRS_PER_CONTAINER = 4;
  private static final int PER_TARGET = 350;      // msgs per (sender, target) pair
  private static final int TOPIC_MSGS = 500;      // msgs per publisher
  private static final int NC = 4;                // containers (1 master + 3 slaves)

  @Test(timeout = 300000)
  public void loadTest() throws Exception {
    LogCapture logs = new LogCapture();
    Set<String> threadBaseline = TestUtil.threadSnapshot();
    MultiContainerFixture fx = MultiContainerFixture.create(3);
    try {
      Container[] cs = fx.containers();
      AtomicBoolean go = new AtomicBoolean(false);

      // receivers and subscribers
      LoadAgents.ReceiverAgent[][] rx = new LoadAgents.ReceiverAgent[NC][PAIRS_PER_CONTAINER];
      LoadAgents.SubscriberAgent[] sub = new LoadAgents.SubscriberAgent[NC];
      List<String> allNames = new ArrayList<String>();
      for (int c = 0; c < NC; c++) {
        for (int i = 0; i < PAIRS_PER_CONTAINER; i++) {
          rx[c][i] = new LoadAgents.ReceiverAgent();
          cs[c].add("rx-" + c + "-" + i, rx[c][i]);
          allNames.add("rx-" + c + "-" + i);
        }
        sub[c] = new LoadAgents.SubscriberAgent();
        cs[c].add("sub-" + c, sub[c]);
        allNames.add("sub-" + c);
      }

      // senders and publishers
      LoadAgents.SenderAgent[][] tx = new LoadAgents.SenderAgent[NC][PAIRS_PER_CONTAINER];
      LoadAgents.PublisherAgent[] pub = new LoadAgents.PublisherAgent[NC];
      for (int c = 0; c < NC; c++) {
        for (int i = 0; i < PAIRS_PER_CONTAINER; i++) {
          List<AgentID> targets = new ArrayList<AgentID>();
          for (int o = 1; o < NC; o++)
            targets.add(new AgentID("rx-" + ((c + o) % NC) + "-" + i));
          tx[c][i] = new LoadAgents.SenderAgent(targets, PER_TARGET, go, 50);
          cs[c].add("tx-" + c + "-" + i, tx[c][i]);
        }
        pub[c] = new LoadAgents.PublisherAgent(TOPIC_MSGS, go, 20);
        cs[c].add("pub-" + c, pub[c]);
      }

      fx.startSlaves();
      fx.awaitAgentsVisible(allNames, 30000);

      // directory hammer threads
      final AtomicBoolean hammer = new AtomicBoolean(true);
      final AtomicInteger dirAnomalies = new AtomicInteger();
      final AtomicInteger dirOps = new AtomicInteger();
      final Queue<String> anomalyDetails = new ConcurrentLinkedQueue<String>();
      final int expectedSvc = NC * PAIRS_PER_CONTAINER;   // all receivers register the service
      List<Thread> hammers = new ArrayList<Thread>();
      for (int h = 0; h < 2; h++) {
        Thread t = new Thread("hammer-master-" + h) {
          @Override
          public void run() {
            while (hammer.get()) {
              AgentID[] agents = fx.master.getAgents();
              dirOps.incrementAndGet();
              if (agents == null || agents.length < allNames.size()) {
                dirAnomalies.incrementAndGet();
                anomalyDetails.add("master.getAgents -> " + (agents == null ? "null" : agents.length));
              }
              AgentID[] svc = fx.master.agentsForService(LoadAgents.SERVICE);
              dirOps.incrementAndGet();
              if (svc == null || svc.length != expectedSvc) {
                dirAnomalies.incrementAndGet();
                anomalyDetails.add("master.agentsForService -> " + (svc == null ? "null" : svc.length));
              }
              TestUtil.sleep(100);
            }
          }
        };
        t.setDaemon(true);
        t.start();
        hammers.add(t);
      }
      for (int h = 0; h < 2; h++) {
        final int si = h;
        Thread t = new Thread("hammer-slave-" + h) {
          @Override
          public void run() {
            while (hammer.get()) {
              AgentID[] agents = fx.slaves[si].getAgents();   // slave -> master round-trip
              dirOps.incrementAndGet();
              if (agents == null) {
                dirAnomalies.incrementAndGet();
                anomalyDetails.add("slave" + si + ".getAgents -> null");
              }
              TestUtil.sleep(100);
            }
          }
        };
        t.setDaemon(true);
        t.start();
        hammers.add(t);
      }

      // open the gate and run the load
      long t0 = System.currentTimeMillis();
      go.set(true);

      TestUtil.waitUntil("all senders/publishers done", () -> {
        for (int c = 0; c < NC; c++) {
          if (!pub[c].done) return false;
          for (int i = 0; i < PAIRS_PER_CONTAINER; i++)
            if (!tx[c][i].done) return false;
        }
        return true;
      }, 120000);
      long sendDur = System.currentTimeMillis() - t0;

      // drain: every receiver expects (NC-1) senders x PER_TARGET; every subscriber NC x TOPIC_MSGS
      final int expectRx = (NC - 1) * PER_TARGET;
      final int expectSub = NC * TOPIC_MSGS;
      boolean drained = TestUtil.tryWaitUntil(() -> {
        for (int c = 0; c < NC; c++) {
          if (sub[c].stats.total() < expectSub) return false;
          for (int i = 0; i < PAIRS_PER_CONTAINER; i++)
            if (rx[c][i].stats.total() < expectRx) return false;
        }
        return true;
      }, 60000);
      long totalDur = System.currentTimeMillis() - t0;
      hammer.set(false);

      // collect and report
      int unicastSent = 0, unicastRecd = 0, dups = 0, topicRecd = 0;
      List<Long> allLat = new ArrayList<Long>();
      StringBuilder lossReport = new StringBuilder();
      for (int c = 0; c < NC; c++) {
        topicRecd += sub[c].stats.total();
        dups += sub[c].stats.dups.get();
        for (int i = 0; i < PAIRS_PER_CONTAINER; i++) {
          unicastSent += tx[c][i].sent.get();
          unicastRecd += rx[c][i].stats.total();
          dups += rx[c][i].stats.dups.get();
          allLat.addAll(rx[c][i].stats.latenciesNs);
          for (Map.Entry<String, Set<Integer>> e : rx[c][i].stats.bySrc.entrySet()) {
            if (e.getValue().size() < PER_TARGET)
              lossReport.append("  rx-").append(c).append("-").append(i)
                  .append(" from ").append(e.getKey()).append(": got ")
                  .append(e.getValue().size()).append("/").append(PER_TARGET).append("\n");
          }
        }
      }
      int expectedUnicastTotal = NC * PAIRS_PER_CONTAINER * (NC - 1) * PER_TARGET;

      System.out.println("=== T1 results ===");
      System.out.println("send phase: " + sendDur + " ms, total (incl. drain): " + totalDur + " ms");
      System.out.println("unicast: sent=" + unicastSent + " received=" + unicastRecd
          + " expected=" + expectedUnicastTotal + " dups=" + dups);
      System.out.println("topic: received=" + topicRecd + " expected=" + (NC * expectSub));
      System.out.println("throughput: " + String.format("%.0f", 1000.0 * (unicastRecd + topicRecd) / totalDur) + " msg/s");
      System.out.println("latency: " + TestUtil.latencyStats(allLat));
      System.out.println("directory ops: " + dirOps.get() + ", anomalies: " + dirAnomalies.get());
      for (String a : anomalyDetails) System.out.println("  anomaly: " + a);
      if (lossReport.length() > 0) System.out.println("losses:\n" + lossReport);
      System.out.println("warnings: " + logs.warnings().size() + ", severes: " + logs.severes());

      assertTrue("drain incomplete:\n" + lossReport, drained);
      assertEquals("unicast messages lost", expectedUnicastTotal, unicastRecd);
      assertEquals("duplicate messages detected", 0, dups);
      assertEquals("topic messages lost", NC * expectSub, topicRecd);
      assertEquals("directory anomalies: " + anomalyDetails, 0, dirAnomalies.get());
      assertTrue("SEVERE logs: " + logs.severes(), logs.severes().isEmpty());
    } finally {
      fx.teardown();
      logs.close();
      TestUtil.sleep(1000);
      System.out.println("threads new since baseline after teardown: " + TestUtil.newThreadsSince(threadBaseline));
    }
  }
}
