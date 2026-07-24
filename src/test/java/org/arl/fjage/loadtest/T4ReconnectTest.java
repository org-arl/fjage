package org.arl.fjage.loadtest;

import static org.junit.Assert.*;

import java.util.*;

import org.arl.fjage.AgentID;
import org.junit.Test;

/**
 * T4 — Abrupt connection loss, reconnect, and flapping.
 *
 * Slave-1 is routed through a pass-through proxy. Mid-load the proxy RSTs the
 * connection (no SIGN_OFF) and refuses reconnects; the master must prune the handler,
 * agents sending to the dead slave must not hang, and healthy traffic must continue.
 * When the proxy allows connections again, the slave's 1 s reconnect loop must
 * restore both topic and unicast delivery. Then the connection is flapped repeatedly
 * to hunt for handler/thread leaks and deadlocks.
 */
public class T4ReconnectTest {

  private static final String[] BENIGN_THREADS = {"fjage-timer", ":init", "proxy:", "process reaper"};

  @Test(timeout = 300000)
  public void lossReconnectAndFlap() {
    LogCapture logs = new LogCapture();
    Set<String> baseline = TestUtil.threadSnapshot();
    final MultiContainerFixture fx = MultiContainerFixture.create(3, new boolean[]{false, true, false});
    final ThrottlingTcpProxy proxy = fx.proxies[1];
    try {
      final LoadAgents.SubscriberAgent[] sub = new LoadAgents.SubscriberAgent[3];
      for (int i = 0; i < 3; i++) {
        sub[i] = new LoadAgents.SubscriberAgent();
        fx.slaves[i].add("sub_" + i, sub[i]);
      }
      final LoadAgents.ReceiverAgent rxS1 = new LoadAgents.ReceiverAgent();
      fx.slaves[1].add("rx_s1", rxS1);

      java.util.concurrent.atomic.AtomicBoolean go = new java.util.concurrent.atomic.AtomicBoolean(false);
      final LoadAgents.ContinuousSender pubM = new LoadAgents.ContinuousSender(null, 20, go);
      fx.master.add("pub_m", pubM);
      final LoadAgents.ContinuousSender uniM = new LoadAgents.ContinuousSender(
          Collections.singletonList(new AgentID("rx_s1")), 20, go);
      fx.master.add("uni_m", uniM);

      fx.startSlaves();
      fx.awaitAgentsVisible(Arrays.asList("sub_0", "sub_1", "sub_2", "rx_s1"), 30000);
      TestUtil.sleep(1000);   // let watch lists settle before opening the gate
      go.set(true);

      // baseline: everything flows
      TestUtil.waitUntil("baseline traffic to slave-1", () -> rxS1.stats.total() > 20 && sub[1].stats.total() > 20, 15000);

      // ---- abrupt loss ----
      proxy.setRefuse(true);
      proxy.dropConnections();

      TestUtil.waitUntil("master prunes dead handler", () -> {
        fx.master.getAgents();   // nudge for lazy cleanup on master branch
        return fx.master.getConnectionHandlers().length == 2;
      }, 20000);

      // senders must not hang; healthy slaves must keep receiving
      int pubSeqAtLoss = pubM.seq.get();
      long sub0AtLoss = sub[0].stats.total();
      TestUtil.sleep(2000);
      assertTrue("publisher agent stuck after slave death", pubM.seq.get() > pubSeqAtLoss + 20);
      assertTrue("unicast sender agent stuck after slave death", uniM.seq.get() > 0);
      assertTrue("healthy slave stopped receiving after peer death",
          sub[0].stats.total() > sub0AtLoss + 20);

      long rxS1AtLoss = rxS1.stats.total();
      long sub1AtLoss = sub[1].stats.total();

      // ---- reconnect ----
      proxy.setRefuse(false);
      TestUtil.waitUntil("slave-1 reconnects", () -> fx.master.getConnectionHandlers().length == 3, 30000);
      TestUtil.waitUntil("topic delivery to slave-1 resumes", () -> sub[1].stats.total() > sub1AtLoss + 20, 20000);
      TestUtil.waitUntil("unicast delivery to slave-1 resumes", () -> rxS1.stats.total() > rxS1AtLoss + 20, 20000);
      System.out.println("=== T4: single loss/reconnect OK; now flapping ===");

      // ---- flapping ----
      List<String> flapLog = new ArrayList<>();
      for (int f = 0; f < 8; f++) {
        long subBefore = sub[1].stats.total(), rxBefore = rxS1.stats.total();
        proxy.setRefuse(true);
        proxy.dropConnections();
        TestUtil.sleep(1200);
        proxy.setRefuse(false);
        TestUtil.waitUntil("slave-1 reconnects after flap " + f, () -> {
          fx.master.getAgents();   // nudge
          return fx.master.getConnectionHandlers().length == 3;
        }, 30000);
        final long c = sub[1].stats.total();
        TestUtil.waitUntil("delivery resumes after flap " + f, () -> sub[1].stats.total() > c + 5, 20000);
        flapLog.add("flap " + f + ": sub_1 +" + (sub[1].stats.total() - subBefore)
            + " rx_s1 +" + (rxS1.stats.total() - rxBefore) + " pubSeq=" + pubM.seq.get());
      }
      for (String s : flapLog) System.out.println("  " + s);

      pubM.stopSending();
      uniM.stopSending();
      TestUtil.sleep(1000);

      int published = pubM.seq.get();
      int uniSent = uniM.seq.get();
      System.out.println("=== T4 results ===");
      System.out.println("published=" + published + " sub_0/1/2 received=" + sub[0].stats.total()
          + "/" + sub[1].stats.total() + "/" + sub[2].stats.total()
          + " (loss on slave-1 during outages is expected: fire-and-forget)");
      System.out.println("unicast sent to slave-1: " + uniSent + " received: " + rxS1.stats.total());
      System.out.println("dups: sub_1=" + sub[1].stats.dups.get() + " rx_s1=" + rxS1.stats.dups.get());
      System.out.println("sub_0 missing seqs: " + TestUtil.ranges(sub[0].stats.missingFrom("pub_m", published)));
      System.out.println("sub_2 missing seqs: " + TestUtil.ranges(sub[2].stats.missingFrom("pub_m", published)));
      System.out.println("sub_1 missing seqs: " + TestUtil.ranges(sub[1].stats.missingFrom("pub_m", published)));
      System.out.println("severes: " + logs.severes());

      // healthy slaves must have complete delivery (they never lost their connection)
      assertEquals("healthy slave-0 lost topic messages", published, sub[0].stats.countFrom("pub_m"));
      assertEquals("healthy slave-2 lost topic messages", published, sub[2].stats.countFrom("pub_m"));
      assertEquals("duplicates on flapped slave", 0, sub[1].stats.dups.get() + rxS1.stats.dups.get());
      assertEquals("handler count wrong after flapping", 3, fx.master.getConnectionHandlers().length);
      assertTrue("SEVERE logs: " + logs.severes(), logs.severes().isEmpty());
    } finally {
      fx.teardown();
      logs.close();
    }

    // thread-leak check after teardown (filtering known-benign daemons)
    TestUtil.sleep(3000);
    List<String> leaks = new ArrayList<>();
    for (String t : TestUtil.newThreadsSince(baseline)) {
      boolean benign = false;
      for (String b : BENIGN_THREADS)
        if (t.contains(b)) {
          benign = true;
          break;
        }
      if (!benign) leaks.add(t);
    }
    System.out.println("non-benign leaked threads: " + leaks);
    assertTrue("leaked threads after flapping: " + leaks, leaks.isEmpty());
  }
}
