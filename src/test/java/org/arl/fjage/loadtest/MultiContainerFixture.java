package org.arl.fjage.loadtest;

import java.util.*;

import org.arl.fjage.*;
import org.arl.fjage.remote.*;

/**
 * Spins up 1 MasterContainer + N SlaveContainers, each on its own RealTimePlatform,
 * connected over real localhost TCP. Slaves can optionally be routed through a proxy
 * by supplying a per-slave port override.
 */
public class MultiContainerFixture {

  public RealTimePlatform masterPlatform;
  public MasterContainer master;
  public RealTimePlatform[] slavePlatforms;
  public SlaveContainer[] slaves;
  public ThrottlingTcpProxy[] proxies;   // per-slave; null entry = direct connection

  private MultiContainerFixture() {
  }

  /**
   * Creates the master (started, so its TCP port is live) and the slave containers
   * (constructed but their platforms not yet started). Add agents, then call
   * startSlaves().
   *
   * @param nSlaves number of slave containers.
   * @param viaProxy per-slave flag: route this slave through a ThrottlingTcpProxy; null = all direct.
   */
  public static MultiContainerFixture create(int nSlaves, boolean[] viaProxy) {
    MultiContainerFixture fx = new MultiContainerFixture();
    fx.masterPlatform = new RealTimePlatform();
    fx.master = new MasterContainer(fx.masterPlatform);
    fx.masterPlatform.start();
    TestUtil.waitUntil("master container running", () -> fx.master.isRunning(), 10000);
    if (fx.master.getPort() <= 0) throw new AssertionError("master has no TCP port");
    fx.slavePlatforms = new RealTimePlatform[nSlaves];
    fx.slaves = new SlaveContainer[nSlaves];
    fx.proxies = new ThrottlingTcpProxy[nSlaves];
    for (int i = 0; i < nSlaves; i++) {
      int port = fx.master.getPort();
      if (viaProxy != null && viaProxy[i]) {
        try {
          fx.proxies[i] = new ThrottlingTcpProxy("localhost", port);
        } catch (java.io.IOException ex) {
          throw new AssertionError("cannot open proxy", ex);
        }
        fx.proxies[i].start();
        port = fx.proxies[i].getPort();
      }
      fx.slavePlatforms[i] = new RealTimePlatform();
      fx.slaves[i] = new SlaveContainer(fx.slavePlatforms[i], "localhost", port);
    }
    return fx;
  }

  public static MultiContainerFixture create(int nSlaves) {
    return create(nSlaves, null);
  }

  /** All containers, master first. */
  public Container[] containers() {
    Container[] all = new Container[1 + slaves.length];
    all[0] = master;
    System.arraycopy(slaves, 0, all, 1, slaves.length);
    return all;
  }

  /** Starts all slave platforms (in parallel — SlaveContainer init blocks until connected). */
  public void startSlaves() {
    List<Thread> starters = new ArrayList<Thread>();
    for (int i = 0; i < slavePlatforms.length; i++) {
      final RealTimePlatform p = slavePlatforms[i];
      Thread t = new Thread("slave-starter-" + i) {
        @Override
        public void run() {
          p.start();
        }
      };
      t.start();
      starters.add(t);
    }
    for (Thread t : starters) {
      try {
        t.join(30000);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    awaitSlavesConnected(slaves.length, 20000);
  }

  /** Waits until the master sees exactly n live connection handlers. */
  public void awaitSlavesConnected(final int n, long timeoutMs) {
    TestUtil.waitUntil("master sees " + n + " live slave connections", () -> countAliveHandlers() == n, timeoutMs);
  }

  public int countAliveHandlers() {
    int alive = 0;
    for (ConnectionHandler h : master.getConnectionHandlers())
      if (h.isConnectionAlive()) alive++;
    return alive;
  }

  /** Waits until the master's directory can see all the named agents (across containers). */
  public void awaitAgentsVisible(final Collection<String> names, long timeoutMs) {
    TestUtil.waitUntil("master directory sees " + names.size() + " agents", () -> {
      AgentID[] aids = master.getAgents();
      if (aids == null) return false;
      Set<String> present = new HashSet<String>();
      for (AgentID a : aids) present.add(a.getName());
      return present.containsAll(names);
    }, timeoutMs);
  }

  /** Shuts down all platforms and waits for containers to stop. */
  public void teardown() {
    for (RealTimePlatform p : slavePlatforms) {
      try {
        p.shutdown();
      } catch (Exception ex) {
        // best effort
      }
    }
    for (ThrottlingTcpProxy p : proxies)
      if (p != null) p.shutdownProxy();
    try {
      masterPlatform.shutdown();
    } catch (Exception ex) {
      // best effort
    }
    TestUtil.tryWaitUntil(() -> {
      for (Container c : containers())
        if (c.isRunning()) return false;
      return true;
    }, 15000);
  }
}
