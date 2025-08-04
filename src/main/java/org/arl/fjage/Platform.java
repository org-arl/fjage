/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TimerTask;
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

  protected List<Container> containers = new ArrayList<Container>();
  protected boolean running = false;
  private String hostname = null;
  private final int port = 1099;
  private NetworkInterface nif = null;

  ////////// Interface methods for platforms to implement

  /**
   * Gets the current platform time in milliseconds. For real-time platforms,
   * this time is epoch time. For simulation platforms, this time is simulation
   * time.
   *
   * @return time in milliseconds.
   */
  @Override
  public abstract long currentTimeMillis();

  /**
   * Gets the current platform time in nanoseconds. For real-time platforms,
   * this time is epoch time. For simulation platforms, this time is simulation
   * time. This time is nanosecond precision, but not necessarily nanosecond accuracy.
   *
   * @return time in nanoseconds.
   */
  @Override
  public abstract long nanoTime();

  /**
   * Schedules a task to be executed at a given platform time.
   *
   * @param task task to be executed.
   * @param millis time at which to execute the task.
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
    try {
      InetAddress addr;
      if (nif == null) addr = InetAddress.getLocalHost();
      else {
        Enumeration<InetAddress> alist = nif.getInetAddresses();
        addr = alist.nextElement();
        while (addr instanceof Inet6Address)
          addr = alist.nextElement();
      }
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
      JarURLConnection conn = (JarURLConnection) res.openConnection();
      Manifest mf = conn.getManifest();
      Attributes a = mf.getMainAttributes();
      return "fjage-"+a.getValue("Build-Version")+"/"+a.getValue("Build-Timestamp");
    } catch (Exception ex) {
      return "(unknown)";
    }
  }

}
