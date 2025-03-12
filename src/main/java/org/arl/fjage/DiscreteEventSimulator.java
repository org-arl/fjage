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
  private final Queue<DiscreteEvent> events = new PriorityBlockingQueue<DiscreteEvent>();
  private final Logger log = Logger.getLogger(getClass().getName());
  private Thread thread = null;
  private float speed = Float.NaN;

  /////////// Implementation methods

  /**
   * Creates a DiscreteEventSimulator that runs as fast as possible.
   */
  public DiscreteEventSimulator() {
    LogHandlerProxy.install(this, log);
  }

  /**
   * Creates a DiscreteEventSimulator that runs approximately at speed x realtime,
   * ignoring processing time.
   *
   * @param speed speed up with respect to real time.
   */
  public DiscreteEventSimulator(float speed) {
    LogHandlerProxy.install(this, log);
    this.speed = speed;
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
      synchronized (sync) {
        addEvent(new DiscreteEvent(time, t, new TimerTask() {
          @Override
          public void run() {
            synchronized (sync) {
              sync.notify();
            }
          }
        }, true));
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
            if (!events.isEmpty()) events.poll().task.run();
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
        if (e != null) {
          long dt = e.time - time;
          time = e.time;
          if (dt > 0 && !Float.isNaN(speed)) {
            long t = Math.round(dt/speed);
            try {
              Thread.sleep(t);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
          }
        }
        else {
          log.fine("No more events pending, initiating shutdown");
          shutdown();
        }
      }
    } catch (Exception ex) {
      log.log(Level.SEVERE, "Simulator error", ex);
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
