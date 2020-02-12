/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * A behavior that is executed every specified period. The {@link #onTick()}
 * method of this behavior is called at intervals defined by the period.
 *
 * @author  Mandar Chitre
 */
public class TickerBehavior extends Behavior {

  ////////// Private attributes

  private int ticks;
  private long period;
  private long wakeupTime;
  private boolean quit;

  ////////// Interface methods

  /**
   * Creates a behavior that is executed every specified period.
   *
   * @param millis period in milliseconds.
   */
  public TickerBehavior(long millis) {
    period = millis;
    ticks = 0;
    quit = false;
  }

  /**
   * Creates a behavior that is executed every specified period.
   *
   * @param millis period in milliseconds.
   * @param runnable Runnable to run.
   */
  public TickerBehavior(long millis, Runnable runnable) {
    this(millis);
    if (runnable != null) {
      this.action = param -> runnable.run();
    }
  }

  /**
   * Terminates the behavior.
   */
  public final void stop() {
    quit = true;
  }

  /**
   * Returns the number of times the {@link #onTick()} method of this behavior
   * has been called (including any ongoing call).
   *
   * @return the number of times the {@link #onTick()} method has been called.
   */
  public final int getTickCount() {
    return ticks;
  }

  ////////// Method to be overridden by subclass

  /**
   * This method is called once every specified period. The method is usually
   * overridden by a behavior.
   */
  public void onTick() {
    super.action();
  }

  ////////// Overridden methods

  /**
   * Computes the wakeup time for the first execution of this behavior.
   *
   * @see org.arl.fjage.Behavior#onStart()
   */
  @Override
  public void onStart() {
    wakeupTime = agent.currentTimeMillis() + period;
    block(period);
  }

  /**
   * This method calls {@link #onTick()} once every specified period.
   *
   * @see org.arl.fjage.Behavior#action()
   */
  @Override
  public final void action() {
    if (quit) return;
    long t = agent.currentTimeMillis();
    long dt = wakeupTime - t;
    if (dt > 0) block(dt);
    else {
      ticks++;
      onTick();
      wakeupTime += period;
      if (wakeupTime < t) wakeupTime = t + period;
    }
  }

  /**
   * Returns true once {@link #stop()} is called, false otherwise.
   *
   * @return true once {@link #stop()} is called, false otherwise.
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
    ticks = 0;
    quit = false;
  }

  @Override
  public int getPriority() {
    return Integer.MIN_VALUE;
  }

  /**
   * Creates a new TickerBehavior which runs the specified Runnable every specified period.
   *
   * @param millis Period in milliseconds.
   * @param runnable Runnable to run.
   * @return TickerBehavior
   */
  public static TickerBehavior create(long millis, final Runnable runnable) {
    return new TickerBehavior(millis) {

      @Override
      public void onTick() {
        runnable.run();
      }
    };
  }
}
