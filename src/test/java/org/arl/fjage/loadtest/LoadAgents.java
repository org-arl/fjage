package org.arl.fjage.loadtest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.arl.fjage.*;

/**
 * Test agents and messages for the PR#398 multi-container load harness.
 */
public class LoadAgents {

  public static final String TOPIC = "loadtopic";
  public static final String SERVICE = "loadtest-svc";

  /** Sequenced message carrying source name, per-(source,target) sequence number and send timestamp. */
  public static class SeqMsg extends Message {
    private static final long serialVersionUID = 1L;
    public String src;
    public int seq;
    public long tns;

    public SeqMsg() {
      super();
    }

    public SeqMsg(AgentID to, String src, int seq) {
      super(to, Performative.INFORM);
      this.src = src;
      this.seq = seq;
      this.tns = System.nanoTime();
    }
  }

  /** Thread-safe receive-side statistics. */
  public static class Stats {
    public final Map<String, Set<Integer>> bySrc = new ConcurrentHashMap<String, Set<Integer>>();
    public final AtomicInteger dups = new AtomicInteger();
    public final Queue<Long> latenciesNs = new ConcurrentLinkedQueue<Long>();

    void record(SeqMsg m) {
      Set<Integer> seen = bySrc.get(m.src);
      if (seen == null) {
        seen = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
        Set<Integer> prev = ((ConcurrentHashMap<String, Set<Integer>>) bySrc).putIfAbsent(m.src, seen);
        if (prev != null) seen = prev;
      }
      if (!seen.add(m.seq)) dups.incrementAndGet();
      latenciesNs.add(System.nanoTime() - m.tns);
    }

    public int total() {
      int n = 0;
      for (Set<Integer> s : bySrc.values()) n += s.size();
      return n;
    }

    public int countFrom(String src) {
      Set<Integer> s = bySrc.get(src);
      return s == null ? 0 : s.size();
    }

    public List<Integer> missingFrom(String src, int expected) {
      List<Integer> missing = new ArrayList<Integer>();
      Set<Integer> s = bySrc.get(src);
      for (int i = 0; i < expected; i++)
        if (s == null || !s.contains(i)) missing.add(i);
      return missing;
    }

    /** Highest seq seen from any source — used as a progress indicator. */
    public int maxSeq() {
      int max = -1;
      for (Set<Integer> s : bySrc.values())
        for (int v : s) if (v > max) max = v;
      return max;
    }
  }

  /** Receives SeqMsg unicast and records stats; registers the load-test service. */
  public static class ReceiverAgent extends Agent {
    public final Stats stats = new Stats();

    @Override
    public void init() {
      register(SERVICE);
      add(new MessageBehavior(SeqMsg.class, new java.util.function.Consumer<Message>() {
        @Override
        public void accept(Message m) {
          stats.record((SeqMsg) m);
        }
      }));
    }
  }

  /** Subscribes to the shared topic and records stats. */
  public static class SubscriberAgent extends Agent {
    public final Stats stats = new Stats();

    @Override
    public void init() {
      subscribe(topic(TOPIC));
      add(new MessageBehavior(SeqMsg.class, new java.util.function.Consumer<Message>() {
        @Override
        public void accept(Message m) {
          stats.record((SeqMsg) m);
        }
      }));
    }
  }

  /** Burst sender: after the go-gate opens, sends perTarget sequenced messages to each target. */
  public static class SenderAgent extends Agent {
    private final List<AgentID> targets;
    private final int perTarget;
    private final AtomicBoolean go;
    private final int paceEveryN;   // insert 1 ms delay every N messages; 0 = full speed
    public final AtomicInteger sent = new AtomicInteger();
    public volatile boolean done = false;

    public SenderAgent(List<AgentID> targets, int perTarget, AtomicBoolean go, int paceEveryN) {
      this.targets = targets;
      this.perTarget = perTarget;
      this.go = go;
      this.paceEveryN = paceEveryN;
    }

    @Override
    public void init() {
      add(new OneShotBehavior(new Runnable() {
        @Override
        public void run() {
          while (!go.get()) delay(20);
          String me = getName();
          for (int seq = 0; seq < perTarget; seq++) {
            for (AgentID t : targets) {
              send(new SeqMsg(t, me, seq));
              int n = sent.incrementAndGet();
              if (paceEveryN > 0 && n % paceEveryN == 0) delay(1);
            }
          }
          done = true;
        }
      }));
    }
  }

  /** Burst publisher: after the go-gate opens, publishes count sequenced messages to the shared topic. */
  public static class PublisherAgent extends Agent {
    private final int count;
    private final AtomicBoolean go;
    private final int paceEveryN;
    public volatile boolean done = false;

    public PublisherAgent(int count, AtomicBoolean go, int paceEveryN) {
      this.count = count;
      this.go = go;
      this.paceEveryN = paceEveryN;
    }

    @Override
    public void init() {
      add(new OneShotBehavior(new Runnable() {
        @Override
        public void run() {
          while (!go.get()) delay(20);
          AgentID t = topic(TOPIC);
          String me = getName();
          for (int seq = 0; seq < count; seq++) {
            send(new SeqMsg(t, me, seq));
            if (paceEveryN > 0 && (seq + 1) % paceEveryN == 0) delay(1);
          }
          done = true;
        }
      }));
    }
  }

  /**
   * Continuous sender driven by a ticker: sends one SeqMsg per tick to each target
   * (or to the topic if targets is null). Records the duration of each send() call,
   * which reveals agent-thread blocking on slow connections.
   */
  public static class ContinuousSender extends Agent {
    private final List<AgentID> targets;   // null => publish to TOPIC
    private final long periodMs;
    private final AtomicBoolean go;        // null => always on
    public final AtomicInteger seq = new AtomicInteger();
    public final Queue<Long> sendDurationsNs = new ConcurrentLinkedQueue<Long>();
    private TickerBehavior ticker;

    public ContinuousSender(List<AgentID> targets, long periodMs) {
      this(targets, periodMs, null);
    }

    public ContinuousSender(List<AgentID> targets, long periodMs, AtomicBoolean go) {
      this.targets = targets;
      this.periodMs = periodMs;
      this.go = go;
    }

    @Override
    public void init() {
      ticker = new TickerBehavior(periodMs) {
        @Override
        public void onTick() {
          if (go != null && !go.get()) return;   // gated: don't send before the test is ready
          String me = getName();
          int s = seq.getAndIncrement();
          List<AgentID> dst = targets;
          if (dst == null) dst = Collections.singletonList(topic(TOPIC));
          for (AgentID t : dst) {
            long t0 = System.nanoTime();
            send(new SeqMsg(t, me, s));
            sendDurationsNs.add(System.nanoTime() - t0);
          }
        }
      };
      add(ticker);
    }

    public void stopSending() {
      if (ticker != null) ticker.stop();
    }
  }
}
