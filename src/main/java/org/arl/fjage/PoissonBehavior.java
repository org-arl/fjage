/******************************************************************************

Copyright (c) 2013, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage;

/**
 * A behavior that simulates a Poisson arrival process. The {@link #onTick()}
 * method of this behavior is called with exponentially distributed random
 * interarrival time.
 *
 * @author  Mandar Chitre
 */
public class PoissonBehavior extends Behavior {

  //////////// Private attributes

  private int ticks;
  private final long expDelay;
  private long wakeupTime;
  private boolean quit;

  //////////// Interface methods

  /**
   * Creates a behavior that simulates a Poisson arrival process with a
   * specified average interarrival time. The equivalent arrival rate is given
   * by the reciprocal of the average interarrival time.
   *
   * @param millis average interarrival time in milliseconds.
   */
  public PoissonBehavior(long millis) {
    expDelay = millis;
    ticks = 0;
    quit = false;
  }

  /**
   * Creates a behavior that simulates a Poisson arrival process with a
   * specified average interarrival time. The equivalent arrival rate is given
   * by the reciprocal of the average interarrival time.
   *
   * @param millis average interarrival time in milliseconds.
   * @param runnable Runnable to run.
   */
  public PoissonBehavior(long millis, Runnable runnable) {
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

  //////////// Method to be overridden by subclass

  /**
   * This method is called for each arrival. The method is usually overridden by a
   * behavior.
   */
  public void onTick() {
    super.action();
  }

  //////////// Overridden methods

  /**
   * Computes the wakeup time for the first execution of this behavior.
   *
   * @see org.arl.fjage.Behavior#onStart()
   */
  @Override
  public void onStart() {
    long delayToNext = Math.round(AgentLocalRandom.current().nextExp()*expDelay);
    wakeupTime = agent.currentTimeMillis() + delayToNext;
    block(delayToNext);
  }

  /**
   * This method calls {@link #onTick()} for each Poisson arrival.
   *
   * @see org.arl.fjage.Behavior#action()
   */
  @Override
  public final void action() {
    if (quit) return;
    long dt = wakeupTime - agent.currentTimeMillis();
    if (dt > 0) block(dt);
    else {
      ticks++;
      onTick();
      long delayToNext = Math.round(AgentLocalRandom.current().nextExp()*expDelay);
      wakeupTime = agent.currentTimeMillis() + delayToNext;
    }
  }

  /**
   * Returns true once {@link #stop()} is called, false otherwise.
   *
   * @return true once {@link #stop()} is called, false otherwise.
   * @see org.arl.fjage.Behavior#done()
   */
  @Override
  public boolean done() {
    return quit;
  }

  /**
   * Resets the behavior to its initial state, allowing it to be used again.
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
   * Creates a new PoissonBehavior which runs the specified Runnable on each arrival.
   *
   * @param millis Average inter-arrival time in milliseconds.
   * @param runnable Runnable to run.
   * @return PoissonBehavior
   */
  public static PoissonBehavior create(long millis, final Runnable runnable) {
    return new PoissonBehavior(millis) {

      @Override
      public void onTick() {
        runnable.run();
      }
    };
  }
}
