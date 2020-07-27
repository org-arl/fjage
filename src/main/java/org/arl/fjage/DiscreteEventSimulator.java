/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discrete event simulation platform.  This platform is useful to run high-speed
 * discrete time simulations. Time is compressed and expanded as necessary to
 * simulate the behavior of the system quickly, assuming that the computations
 * and other operations take insignificant amount of time.
 * <p>
 * Typical use of this platform is shown below:
 * <pre>
 * import org.arl.fjage.*;
 *
 * Platform platform = new DiscreteEventSimulator();
 * Container container = new Container(platform);
 * container.add("myAgent", new myAgent());         // add appropriate agents
 * platform.start();
 * </pre>
 *
 * @author  Mandar Chitre
 */
public final class DiscreteEventSimulator extends Platform implements Runnable {

  /////////// Private attributes

  private volatile long time = 0;
  private Queue<DiscreteEvent> events = new PriorityBlockingQueue<DiscreteEvent>();
  private Logger log = Logger.getLogger(getClass().getName());
  private Thread thread = null;

  /////////// Implementation methods

  public DiscreteEventSimulator() {
    LogHandlerProxy.install(this, log);
  }

  @Deprecated
  @Override
  public void setPort(int port) {
    throw new UnsupportedOperationException(getClass().getName()+" does not support RMI");
  }

  @Deprecated
  @Override
  public int getPort() {
    throw new UnsupportedOperationException(getClass().getName()+" does not support RMI");
  }

  @Override
  public long currentTimeMillis() {
    return time;
  }

  @Override
  public long nanoTime() {
    return time*1000;
  }

  @Override
  public void delay(long millis) {
    if (millis <= 0) return;
    final Object sync = new Object();
    long t = time + millis;
    long dt = millis;
    while (dt > 0) {
      addEvent(new DiscreteEvent(time, t, new TimerTask() {
        @Override
        public void run() {
          synchronized (sync) {
            sync.notify();
          }
        }
      }, true));
      synchronized (sync) {
        try {
          sync.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      dt = t - time;
    }
  }

  @Override
  public void schedule(TimerTask task, long millis) {
    if (millis <= 0) task.run();
    else addEvent(new DiscreteEvent(time, time+millis, task));
  }

  @Override
  public void idle() {
    log.fine("Container went idle");
    synchronized (this) {
      notify();
    }
  }

  @Override
  public void start() {
    super.start();
    thread = new Thread(this);
    thread.setName(getClass().getSimpleName());
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void shutdown() {
    super.shutdown();
    synchronized (events) {
      events.clear();
    }
    synchronized (this) {
      notify();
    }
  }

  /**
   * Thread implementation.
   *
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    try {
      DiscreteEvent e = events.peek();
      while (running) {
        while (e != null && e.time <= time) {
          log.fine("Fire "+e);
          synchronized (events) {
            if (events.size() > 0) events.poll().task.run();
          }
          e = events.peek();
        }
        Thread.yield();
        synchronized (this) {
          while (running && !isIdle()) {
            try {
              log.fine("Waiting for agents");
              wait();
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
          }
        }
        e = events.peek();
        if (e != null) time = e.time;
        else {
          log.fine("No more events pending, initiating shutdown");
          shutdown();
        }
      }
    } catch (Exception ex) {
      log.log(Level.SEVERE, "Exception: ", ex);
    }
    log.info("Simulator shutdown");
  }

  /////////// Private methods

  private void addEvent(DiscreteEvent event) {
    log.fine("Adding "+event);
    events.add(event);
    if (thread != null && thread.getState() == Thread.State.WAITING) {
      synchronized (this) {
        notify();
      }
    }
  }

}
