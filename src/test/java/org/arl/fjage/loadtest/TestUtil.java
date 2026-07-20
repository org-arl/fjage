package org.arl.fjage.loadtest;

import java.util.*;

/**
 * Polling and thread-accounting helpers for the PR#398 load-test harness.
 */
public class TestUtil {

  public interface Check {
    boolean ok();
  }

  /** Polls until the condition holds, or throws AssertionError after timeoutMs. */
  public static void waitUntil(String what, Check c, long timeoutMs) {
    if (!tryWaitUntil(c, timeoutMs))
      throw new AssertionError("Timed out after " + timeoutMs + " ms waiting for: " + what);
  }

  /** Polls until the condition holds; returns false on timeout. */
  public static boolean tryWaitUntil(Check c, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (c.ok()) return true;
      try {
        Thread.sleep(50);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return c.ok();
      }
    }
    return c.ok();
  }

  public static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /** Snapshot of live thread identities (name#id). */
  public static Set<String> threadSnapshot() {
    Set<String> names = new TreeSet<String>();
    for (Thread t : Thread.getAllStackTraces().keySet())
      if (t.isAlive()) names.add(t.getName() + "#" + t.getId());
    return names;
  }

  /** Threads alive now that were not in the baseline snapshot. */
  public static List<String> newThreadsSince(Set<String> baseline) {
    List<String> leaked = new ArrayList<String>();
    for (String name : threadSnapshot())
      if (!baseline.contains(name)) leaked.add(name);
    return leaked;
  }

  /** Simple latency stats over a collection of nanosecond samples. */
  public static String latencyStats(Collection<Long> ns) {
    if (ns.isEmpty()) return "no samples";
    List<Long> sorted = new ArrayList<Long>(ns);
    Collections.sort(sorted);
    return String.format("n=%d p50=%.1fms p95=%.1fms p99=%.1fms max=%.1fms",
        sorted.size(),
        sorted.get(sorted.size() / 2) / 1e6,
        sorted.get((int) (sorted.size() * 0.95)) / 1e6,
        sorted.get((int) (sorted.size() * 0.99)) / 1e6,
        sorted.get(sorted.size() - 1) / 1e6);
  }

  /** Compresses a sorted list of ints into range notation, e.g. "5-8,12,40-45". */
  public static String ranges(List<Integer> vals) {
    if (vals.isEmpty()) return "none";
    StringBuilder sb = new StringBuilder();
    int start = vals.get(0), prev = start;
    for (int i = 1; i <= vals.size(); i++) {
      int v = (i < vals.size()) ? vals.get(i) : Integer.MIN_VALUE;
      if (v != prev + 1) {
        if (sb.length() > 0) sb.append(',');
        sb.append(start == prev ? String.valueOf(start) : start + "-" + prev);
        start = v;
      }
      prev = v;
    }
    return sb.toString();
  }

  public static double p99ms(Collection<Long> ns) {
    if (ns.isEmpty()) return -1;
    List<Long> sorted = new ArrayList<Long>(ns);
    Collections.sort(sorted);
    return sorted.get((int) (sorted.size() * 0.99)) / 1e6;
  }
}
