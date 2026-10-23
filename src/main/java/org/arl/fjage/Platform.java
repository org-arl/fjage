/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Base class for platforms on which agent containers run. The platform provides
 * basic system functionality such as timing, network, etc.
 *
 * @author  Mandar Chitre
 */
public abstract class Platform implements TimestampProvider {

  /////////// Private attributes

  protected List<Container> containers = new CopyOnWriteArrayList<>();
  protected boolean running = false;
  private String hostname = null;
  private final int port = 1099;
  private NetworkInterface nif = null;

  ////////// Interface methods for platforms to implement

  /**
   * Gets the current platform time in milliseconds. For real-time platforms,
   * this time is epoch time. For simulation platforms, this time is simulation
   * time.
   * <p>
   * On real-time platforms, this clock may be stepped forwards or backwards when
   * the system clock is adjusted. Use it for timestamps, and use {@link #nanoTime()}
   * to measure elapsed time or to compute deadlines.
   *
   * @return time in milliseconds.
   */
  @Override
  public abstract long currentTimeMillis();

  /**
   * Gets the current platform time in nanoseconds, for measuring elapsed time.
   * For real-time platforms, this time is monotonic and has an arbitrary origin,
   * so only differences between two values are meaningful. For simulation
   * platforms, this time is simulation time. The time is nanosecond precision
   * but not necessarily nanosecond accuracy.
   * <p>
   * Timeouts and timed behaviors measure elapsed time using this clock, so it must
   * advance at the same rate as delays given to {@link Platform#schedule}. Compare
   * values by subtraction (e.g. {@code t1 - t0 > 0}), as they may overflow.
   *
   * @return time in nanoseconds.
   */
  @Override
  public abstract long nanoTime();

  /**
   * Schedules a task to be executed after a given delay. A non-positive delay
   * causes the task to be executed as soon as possible.
   *
   * @param task task to be executed.
   * @param millis delay in milliseconds after which to execute the task.
   */
  public abstract void schedule(TimerTask task, long millis);

  /**
   * Internal method called by a container when all agents are idle.
   */
  public abstract void idle();

  /**
   * Delays execution by a specified number of milliseconds of platform time.
   * <p>
   * This method should not be called by an agent directly, as it may deadlock.
   * An agent should use {@link Agent#delay(long)} instead.
   *
   * @param millis number of milliseconds to delay execution.
   */
  public abstract void delay(long millis);

  ////////// Interface methods

  /**
   * Adds a container to the platform. This method is typically called automatically
   * by containers when they are created.
   *
   * @param container the container.
   */
  public void addContainer(Container container) {
    if (running) throw new FjageException("Cannot add container to running platform");
    containers.add(container);
  }

  /**
   * Gets all the containers on the platform.
   *
   * @return an array of containers.
   */
  public Container[] getContainers() {
    return containers.toArray(new Container[0]);
  }

  /**
   * Starts all containers on the platform.
   */
  public void start() {
    for (Container c: containers)
      c.init();
    for (Container c: containers)
      c.start();
    running = true;
  }

  /**
   * Terminates all containers on the platform.
   */
  public void shutdown() {
    Thread t = new Thread(() -> {
      for (Container c: containers) {
        if (c != null) c.shutdown();
      }
      running = false;
    });
    t.start();
  }

  /**
   * Sets the hostname for the platform.
   *
   * @param hostname name of the host.
   * @see #getHostname()
   */
  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  /**
   * Gets the hostname for the platform. If the hostname is not set or set to
   * null, it is automatically determined from the Internet hostname for the
   * machine.
   *
   * @return the name of the host.
   */
  public String getHostname() {
    if (hostname != null) return hostname;
    if (nif != null) {
      return Collections.list(nif.getInetAddresses()).stream()
          .filter(a -> !(a instanceof Inet6Address))
          .findFirst()
          .map(InetAddress::getHostAddress)
          .orElse("localhost");
    }
    try {
      InetAddress addr = InetAddress.getLocalHost();
      if (addr == null) return "localhost";
      return addr.getHostAddress();
    } catch (UnknownHostException ex) {
      return "localhost";
    }
  }

  /**
   * Gets a network interface that the platform is bound to.
   *
   * @return bound network interface, null if no binding.
   */
  public NetworkInterface getNetworkInterface() {
    return nif;
  }

  /**
   * Sets the network interface to bind to.
   *
   * @param name name of the network interface.
   */
  public void setNetworkInterface(String name) throws SocketException {
    nif = NetworkInterface.getByName(name);
  }

  /**
   * Sets the network interface to bind to.
   *
   * @param nif network interface.
   */
  public void setNetworkInterface(NetworkInterface nif) {
    this.nif = nif;
  }

  /**
   * Check if any container on the platform is running.
   *
   * @return true if a container is running, false otherwise.
   */
  public boolean isRunning() {
    for (Container c: containers) {
      if (c != null && c.isRunning()) return true;
    }
    return false;
  }

  /**
   * Check if all containers on the platform are idle.
   *
   * @return true if all containers are idle, false otherwise.
   */
  public boolean isIdle() {
    for (Container c: containers) {
      if (c != null && !c.isIdle()) return false;
    }
    return true;
  }

  /**
   * Get build version information from JAR.
   *
   * @return build version information string.
   */
  public static String getBuildVersion() {
    try {
      Class<?> cls = Platform.class;
      URL res = cls.getResource(cls.getSimpleName() + ".class");
      if (res == null) return "(unknown)";
      JarURLConnection conn = (JarURLConnection) res.openConnection();
      Manifest mf = conn.getManifest();
      Attributes a = mf.getMainAttributes();
      return "fjage-"+a.getValue("Build-Version")+"/"+a.getValue("Build-Timestamp");
    } catch (Exception ex) {
      return "(unknown)";
    }
  }

}
