/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * A behavior that is executed once after a specified delay. Once the specified
 * delay expires, the {@link #onWake()} method of this behavior is called.
 *
 * @author  Mandar Chitre
 */
public class WakerBehavior extends Behavior {

  ///////////// Private attributes

  private final long timeout;
  private long deadline;              // in nanoTime() units
  private boolean started;
  private volatile boolean quit;

  ///////////// Public interface

  /**
   * Creates a behavior which is executed once after a specified delay.
   *
   * @param millis delay in milliseconds.
   */
  public WakerBehavior(long millis) {
    timeout = millis;
    quit = false;
  }

  /**
   * Creates a behavior which is executed once after a specified delay.
   *
   * @param millis delay in milliseconds.
   * @param runnable Runnable to run.
   */
  public WakerBehavior(long millis, Runnable runnable) {
    this(millis);
    if (runnable != null) {
      this.action = param -> runnable.run();
    }
  }

  /**
   * Returns the wakeup time for this behavior. On a real-time platform, the
   * time is the epoch time, while a discrete event simulator gives the wakeup
   * time in simulated time. The delay itself is measured as elapsed time, so the
   * wakeup time moves with any change to the wall clock.
   *
   * @return the wakeup time in milliseconds, or 0 if the behavior has not started.
   * @see Platform#currentTimeMillis()
   */
  public final long getWakeupTime() {
    Agent a = agent;
    if (a == null || !started) return 0;
    return a.currentTimeMillis() + Math.floorDiv(deadline - a.nanoTime(), 1_000_000L);
  }

  /**
   * Terminates the behavior.
   */
  public final void stop() {
    quit = true;
  }

  //////////// Method to be overridden by subclass

  /**
   * This method is called once the specified delay for this behavior expires.
   * A behavior usually overrides this.
   */
  public void onWake() {
    super.action();
  }

  //////////// Overridden methods

  /**
   * Computes the wakeup time for this behavior.
   *
   * @see org.arl.fjage.Behavior#onStart()
   */
  @Override
  public final void onStart() {
    deadline = agent.deadlineAfter(timeout);
    started = true;
    block(timeout);
  }

  /**
   * This method calls {@link #onWake()} when the specified delay for this
   * behavior expires.
   *
   * @see org.arl.fjage.Behavior#action()
   */
  @Override
  public final void action() {
    if (quit) return;
    long dt = agent.millisUntil(deadline);
    if (dt > 0) block(dt);
    else {
      onWake();
      quit = true;
    }
  }

  /**
   * Returns false until the delay expires, and true once the delay has
   * expired and the {@link #onWake()} method has been called.
   *
   * @return false when the delay has not expired, false after it has expired.
   * @see org.arl.fjage.Behavior#done()
   */
  @Override
  public final boolean done() {
    return quit;
  }

  /**
   * Resets the behavior, allowing it to be used again.
   *
   * @see org.arl.fjage.Behavior#reset()
   */
  @Override
  public void reset() {
    super.reset();
    started = false;
    quit = false;
  }

  @Override
  public int getPriority() {
    return Integer.MIN_VALUE;
  }

  /**
   * Creates a new WakerBehavior which runs the specified Runnable once the specified delay expires.
   *
   * @param millis Delay in milliseconds.
   * @param runnable Runnable to run.
   * @return WakerBehavior
   */
  public static WakerBehavior create(long millis, final Runnable runnable) {
    return new WakerBehavior(millis) {

      @Override
      public void onWake() {
        runnable.run();
      }
    };
  }
}
